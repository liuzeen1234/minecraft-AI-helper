package com.example.helloworld;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 管理"AI 执行命令前需玩家确认"的待确认请求。
 *
 * <p>当 {@link ModConfig#isConfirmBeforeExecuteEnabled()} 开启时，{@link AICommandExecutor}
 * 不会直接执行游戏操作，而是先通过本类在玩家聊天框发送一条带 [是]/[否] 可点击按钮的确认消息，
 * 并挂起真正的执行逻辑（以 {@link Runnable} 形式保存），等待玩家点击。
 *
 * <p>玩家点击按钮时会通过 ClickEvent 触发运行 {@code /aiconfirm <requestId> yes|no} 命令
 * （见 {@link HelloWorldMod} 中对该命令的注册），命令处理器调用 {@link #resolve(String, boolean, ServerPlayerEntity)}。
 *
 * <p>超过 {@link #TIMEOUT_SECONDS} 秒未确认的请求会自动视为"否"，避免请求无限挂起。
 */
public final class PendingActionConfirmation {

    private static final Logger LOGGER = LoggerFactory.getLogger("PendingActionConfirmation");

    /** 确认请求超时时长（秒）：超时后自动按"否"处理。 */
    public static final int TIMEOUT_SECONDS = 60;

    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ai-confirm-timeout");
                t.setDaemon(true);
                return t;
            });

    private static final AtomicLong REQUEST_SEQ = new AtomicLong(0);

    private static final Map<String, PendingActionConfirmation> PENDING = new ConcurrentHashMap<>();

    private final String requestId;
    private final UUID playerUuid;
    private final ServerPlayerEntity player;
    private final String summary;
    private final Runnable onAccept;
    private final Consumer<ServerPlayerEntity> onReject;
    private final ScheduledFuture<?> timeoutTask;
    private volatile boolean resolved = false;
    /** 批量确认时，每个成员 resolve 后触发一次，用于统计整批是否全部处理完毕。可为 null（单条确认不涉及批次）。 */
    private volatile Runnable onBatchMemberResolved;

    private PendingActionConfirmation(String requestId, ServerPlayerEntity player, String summary,
                                       Runnable onAccept, Consumer<ServerPlayerEntity> onReject) {
        this.requestId = requestId;
        this.playerUuid = player.getUuid();
        this.player = player;
        this.summary = summary;
        this.onAccept = onAccept;
        this.onReject = onReject;
        this.timeoutTask = TIMEOUT_EXECUTOR.schedule(this::onTimeout, TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 创建一个新的待确认请求，并把带 [是]/[否] 按钮的确认消息发送给玩家。
     *
     * @param player   触发本次操作的玩家
     * @param summary  展示给玩家的操作摘要（如 "放置方块 oak_planks" 或 "建造结构 xxx（共 12 个方块）"）
     * @param onAccept 玩家点击"是"后要执行的真正操作（调用方需自行确保在合适的线程执行世界操作）
     * @return 生成的 requestId，供调用方在返回文本中提示玩家
     */
    public static String request(ServerPlayerEntity player, String summary, Runnable onAccept) {
        return request(player, summary, onAccept, p -> { });
    }

    /**
     * 同上，额外支持在玩家拒绝或超时拒绝时执行回调（例如回复一句提示）。
     */
    public static String request(ServerPlayerEntity player, String summary, Runnable onAccept,
                                   Consumer<ServerPlayerEntity> onReject) {
        String requestId = "req" + REQUEST_SEQ.incrementAndGet();
        PendingActionConfirmation pending = new PendingActionConfirmation(
                requestId, player, summary, onAccept, onReject);
        PENDING.put(requestId, pending);
        player.sendMessage(buildConfirmMessage(requestId, summary), false);
        return requestId;
    }

    /** 一批确认请求中的单个成员：摘要 + 玩家点击"是"后要执行的真正操作。 */
    public static final class BatchItem {
        final String summary;
        final Runnable onAccept;
        public BatchItem(String summary, Runnable onAccept) {
            this.summary = summary;
            this.onAccept = onAccept;
        }
    }

    /**
     * 批量发起一组确认请求（同一轮 AI 回复里可能同时请求多个操作，如连续放置多个方块）。
     * 每个 item 各自独立发一条 [是]/[否] 确认消息，玩家可以逐条确认。
     * 当这一批全部被处理完（无论每条是接受、拒绝还是超时）后，调用一次 {@code onBatchDone}，
     * 用于触发"确认完成后自动把结果续跑回喂给 AI"，避免每条各自触发一次续跑导致 AI 被连续打断多次。
     *
     * @param player     触发本次操作的玩家
     * @param items      本批所有待确认的操作
     * @param onBatchDone 整批处理完毕后的回调（无论批内每条是否被接受）
     * @return 本批生成的所有 requestId
     */
    public static List<String> requestBatch(ServerPlayerEntity player, List<BatchItem> items, Runnable onBatchDone) {
        if (items.isEmpty()) {
            onBatchDone.run();
            return List.of();
        }
        AtomicInteger remaining = new AtomicInteger(items.size());
        Runnable onMemberResolved = () -> {
            if (remaining.decrementAndGet() == 0) {
                onBatchDone.run();
            }
        };

        List<String> requestIds = new ArrayList<>();
        for (BatchItem item : items) {
            String requestId = "req" + REQUEST_SEQ.incrementAndGet();
            PendingActionConfirmation pending = new PendingActionConfirmation(
                    requestId, player, item.summary, item.onAccept, p -> { });
            pending.onBatchMemberResolved = onMemberResolved;
            PENDING.put(requestId, pending);
            requestIds.add(requestId);
        }

        // 一批操作合并成一条消息展示，逐条附带各自的 [是]/[否] 按钮
        player.sendMessage(buildBatchConfirmMessage(requestIds, items), false);
        return requestIds;
    }

    /** 构造一批操作合并展示的确认消息：每条摘要单独一行，各自带 [是]/[否] 按钮。 */
    private static Text buildBatchConfirmMessage(List<String> requestIds, List<BatchItem> items) {
        MutableText combined = Text.literal(I18n.tr("confirm.batch_prompt", items.size()));
        for (int i = 0; i < items.size(); i++) {
            String requestId = requestIds.get(i);
            String summary = items.get(i).summary;
            combined = combined.append(Text.literal("\n  " + (i + 1) + ". " + summary + "  "))
                    .append(buildYesButton(requestId))
                    .append(Text.literal("  "))
                    .append(buildNoButton(requestId));
        }
        return combined;
    }

    /** 构造带 [是]/[否] 可点击按钮的确认消息。 */
    private static Text buildConfirmMessage(String requestId, String summary) {
        return Text.literal(I18n.tr("confirm.prompt", summary) + " ")
                .append(buildYesButton(requestId))
                .append(Text.literal("  "))
                .append(buildNoButton(requestId));
    }

    private static MutableText buildYesButton(String requestId) {
        return Text.literal(I18n.tr("confirm.yes_button"))
                .styled(style -> style
                        .withColor(Formatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/aiconfirm " + requestId + " yes"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.literal(I18n.tr("confirm.yes_hover")))));
    }

    private static MutableText buildNoButton(String requestId) {
        return Text.literal(I18n.tr("confirm.no_button"))
                .styled(style -> style
                        .withColor(Formatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                "/aiconfirm " + requestId + " no"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.literal(I18n.tr("confirm.no_hover")))));
    }

    /**
     * 处理玩家的确认结果（由 /aiconfirm 命令调用）。
     *
     * @param requestId 请求 ID
     * @param accepted  true=玩家点击了"是"，false=点击了"否"
     * @param player    发起 /aiconfirm 的玩家（用于校验是否为请求发起者本人，并用于回复消息）
     * @return 处理结果文本，用于命令反馈
     */
    public static String resolve(String requestId, boolean accepted, ServerPlayerEntity player) {
        PendingActionConfirmation pending = PENDING.get(requestId);
        if (pending == null) {
            return I18n.tr("confirm.not_found");
        }
        if (!pending.playerUuid.equals(player.getUuid())) {
            return I18n.tr("confirm.not_owner");
        }
        if (!pending.markResolved()) {
            return I18n.tr("confirm.already_resolved");
        }
        PENDING.remove(requestId);
        pending.timeoutTask.cancel(false);

        String feedback;
        if (accepted) {
            try {
                pending.onAccept.run();
                feedback = I18n.tr("confirm.accepted", pending.summary);
            } catch (Exception e) {
                LOGGER.error("执行已确认的 AI 操作失败: requestId={}", requestId, e);
                feedback = I18n.tr("confirm.execute_failed", e.getMessage());
            }
        } else {
            try {
                pending.onReject.accept(player);
            } catch (Exception e) {
                LOGGER.warn("执行拒绝回调失败: requestId={}", requestId, e);
            }
            feedback = I18n.tr("confirm.rejected", pending.summary);
        }
        if (pending.onBatchMemberResolved != null) {
            pending.onBatchMemberResolved.run();
        }
        return feedback;
    }

    private void onTimeout() {
        if (!markResolved()) {
            return;
        }
        PENDING.remove(requestId);
        LOGGER.info("AI 操作确认请求超时，自动拒绝: requestId={}, summary={}", requestId, summary);
        try {
            net.minecraft.server.MinecraftServer server = player.getServer();
            Runnable task = () -> {
                // 玩家可能已经下线，sendMessage 在这种情况下由 Minecraft 内部处理，不会抛异常
                player.sendMessage(Text.literal(I18n.tr("confirm.timeout", summary)), false);
                if (onReject != null) {
                    try {
                        onReject.accept(player);
                    } catch (Exception e) {
                        LOGGER.warn("执行超时拒绝回调失败: requestId={}", requestId, e);
                    }
                }
                if (onBatchMemberResolved != null) {
                    onBatchMemberResolved.run();
                }
            };
            if (server != null) {
                server.execute(task);
            } else {
                task.run();
            }
        } catch (Exception e) {
            LOGGER.warn("处理确认超时失败: requestId={}", requestId, e);
        }
    }

    private synchronized boolean markResolved() {
        if (resolved) return false;
        resolved = true;
        return true;
    }
}
