package com.example.helloworld;

import net.minecraft.server.network.ServerPlayerEntity;
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

/**
 * 跟踪 "AI 通过 {@code execute_command} 建议、并已预填到玩家聊天框" 的原版命令，
 * 在玩家真正按回车发送并执行后，把命令的执行反馈文本自动 "续跑" 回喂给 AI 对话。
 *
 * <p>背景：{@link AICommandExecutor#executeMinecraftCommand} 只是把命令文本发到玩家客户端
 * 预填进聊天框，服务端此前完全不知道玩家是否真的发送了、执行结果是什么。多轮工具循环
 * （见 {@code HelloWorldMod.runToolLoop}）因此会立刻拿到 "已建议命令" 这句提示当作结果继续，
 * AI 无法据此得知命令是否成功、产生了什么反馈。
 *
 * <p>本类补上这一环：
 * <ol>
 *   <li>{@link AICommandExecutor} 发送建议命令时调用 {@link #register}，登记一条待执行建议
 *       （按玩家 UUID + 命令的第一个单词/子命令名索引），并保存 "拿到反馈后要怎样续跑" 的回调。</li>
 *   <li>玩家在原版聊天框按回车执行命令时，{@code ServerPlayNetworkHandlerMixin} 在
 *       {@code onCommandExecution} 开始处调用 {@link #beginCapture} 打开一个 "反馈捕获窗口"，
 *       {@code ServerCommandSourceMixin} 在命令执行期间调用 {@link #captureFeedback} 把
 *       {@code sendFeedback}/{@code sendError} 的文本收集起来，命令执行返回后由
 *       {@code onCommandExecution} 尾部调用 {@link #endCaptureAndResolve} 关闭窗口。</li>
 *   <li>若这条命令正是之前登记过的建议，则把收集到的反馈文本交给回调触发续跑（回喂 AI）。</li>
 * </ol>
 *
 * <p>线程模型：命令执行发生在服务端主线程（网络线程调度到主线程），所以捕获窗口用
 * {@code ThreadLocal} 保证不跨玩家/线程串味。续跑回调本身可能要调用 AI（网络阻塞），
 * 因此回调实现方（{@code HelloWorldMod}）负责切到独立线程，本类不做线程切换。
 *
 * <p>超时兜底：登记后若在 {@link #TIMEOUT_SECONDS} 秒内玩家一直没有执行该命令（例如把预填内容
 * 删掉不发、或改发了别的命令），则自动放弃该条建议并触发一次 "玩家未执行" 的续跑，避免对话
 * 永久卡在等待命令执行上。
 */
public final class PendingCommandSuggestion {

    private static final Logger LOGGER = LoggerFactory.getLogger("PendingCommandSuggestion");

    /** 建议命令等待玩家执行的超时时长（秒）：超时后视为玩家未执行，触发一次未执行续跑。 */
    public static final int TIMEOUT_SECONDS = 60;

    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ai-command-suggestion-timeout");
                t.setDaemon(true);
                return t;
            });

    /**
     * 待执行的建议命令：按 "玩家 UUID + 命令的第一个单词（子命令名）" 索引。
     * 用命令首词作为匹配 key，是为了让玩家在预填基础上微调参数后仍能对上号——例如建议
     * {@code /time set day}、玩家实际发送 {@code /time set night}，只要首词仍是 {@code time}
     * 就算命中并续跑。这样比全文精确匹配更宽松，更贴合"玩家看一眼后按需改参数再执行"的实际用法。
     * 代价是：同一玩家若同时挂着多条首词相同的建议，后登记的会覆盖先登记的（见 {@link #register}）。
     */
    private static final Map<String, Suggestion> PENDING = new ConcurrentHashMap<>();

    /** 拿到命令执行反馈后要执行的续跑动作。 */
    @FunctionalInterface
    public interface ResumeCallback {
        /**
         * @param feedbackText 命令执行期间收集到的反馈/错误文本（多行合并）；玩家未执行超时时为 null
         * @param success      命令是否被玩家执行（true=已执行并收集到反馈或无反馈，false=超时未执行）
         */
        void resume(String feedbackText, boolean success);
    }

    private static final class Suggestion {
        final UUID playerUuid;
        /** 命令首词（子命令名），作为匹配用的归一化 key。 */
        final String commandWord;
        /** 建议的完整命令文本（不含前导 /），仅用于日志展示。 */
        final String fullCommand;
        final ResumeCallback callback;
        final ScheduledFuture<?> timeoutTask;
        volatile boolean resolved = false;

        Suggestion(UUID playerUuid, String commandWord, String fullCommand, ResumeCallback callback,
                   ScheduledFuture<?> timeoutTask) {
            this.playerUuid = playerUuid;
            this.commandWord = commandWord;
            this.fullCommand = fullCommand;
            this.callback = callback;
            this.timeoutTask = timeoutTask;
        }

        synchronized boolean markResolved() {
            if (resolved) return false;
            resolved = true;
            return true;
        }
    }

    private PendingCommandSuggestion() { }

    /**
     * 把命令文本归一化：去掉前导 {@code /}、去掉首尾空白、把中间连续空白压成单个空格。
     * 这样 "预填的 {@code /time set day}" 与玩家实际发送的 "{@code time set day}"（原版聊天框发送命令时不含 /）
     * 能对齐后再取首词比较。
     */
    private static String normalize(String command) {
        if (command == null) return "";
        String c = command.trim();
        if (c.startsWith("/")) c = c.substring(1);
        return c.trim().replaceAll("\\s+", " ");
    }

    /**
     * 取命令的第一个单词（子命令名，如 {@code /time set day} 的 {@code time}），作为匹配 key。
     * 按首词匹配可以让玩家在预填基础上改参数后仍命中（只要子命令名不变）。
     */
    private static String commandWord(String command) {
        String normalized = normalize(command);
        if (normalized.isEmpty()) return "";
        int sp = normalized.indexOf(' ');
        return sp < 0 ? normalized : normalized.substring(0, sp);
    }

    private static String key(UUID playerUuid, String commandWord) {
        return playerUuid + "\u0000" + commandWord;
    }

    /**
     * 登记一条 AI 建议的命令，等待玩家执行。若同一玩家对同一命令已存在未处理的登记，会先取消旧的。
     *
     * @param player   触发本次 AI 请求的玩家
     * @param command  建议的命令文本（可带或不带前导 /）
     * @param callback 玩家执行该命令、拿到反馈后（或超时未执行时）要执行的续跑动作
     */
    public static void register(ServerPlayerEntity player, String command, ResumeCallback callback) {
        if (player == null || command == null || callback == null) return;
        UUID uuid = player.getUuid();
        String word = commandWord(command);
        if (word.isEmpty()) return;
        String fullCommand = normalize(command);

        String k = key(uuid, word);
        // 先移除同 key 旧登记（同一玩家挂着首词相同的旧建议时，后者覆盖前者，避免旧回调与超时任务泄漏）
        Suggestion old = PENDING.remove(k);
        if (old != null) {
            old.timeoutTask.cancel(false);
        }

        ScheduledFuture<?> timeoutTask = TIMEOUT_EXECUTOR.schedule(
                () -> onTimeout(k), TIMEOUT_SECONDS, TimeUnit.SECONDS);
        PENDING.put(k, new Suggestion(uuid, word, fullCommand, callback, timeoutTask));
        LOGGER.info("登记 AI 建议命令等待玩家执行: player={}, command=/{}（匹配首词: {}）",
                player.getName().getString(), fullCommand, word);
    }

    // ========== 反馈捕获窗口（命令执行期间，主线程 ThreadLocal） ==========

    /** 当前线程正在执行的命令的反馈捕获状态；null 表示当前没有打开捕获窗口。 */
    private static final ThreadLocal<Capture> ACTIVE_CAPTURE = new ThreadLocal<>();

    private static final class Capture {
        final UUID playerUuid;
        /** 命令首词（子命令名），用于关闭窗口时按 key 找回登记的建议。 */
        final String commandWord;
        final List<String> feedbackLines = new ArrayList<>();
        Capture(UUID playerUuid, String commandWord) {
            this.playerUuid = playerUuid;
            this.commandWord = commandWord;
        }
    }

    /**
     * 玩家开始执行一条命令时打开捕获窗口（由 {@code ServerPlayNetworkHandlerMixin} 在
     * {@code onCommandExecution} 开始处调用）。只有当这条命令确实是之前登记过的待执行建议时，
     * 才真正打开窗口（否则不产生任何开销，也不会误捕获无关命令的反馈）。
     *
     * @param player  执行命令的玩家
     * @param command 玩家发送的命令文本（原版聊天框发送命令时通常不含前导 /）
     */
    public static void beginCapture(ServerPlayerEntity player, String command) {
        if (player == null) return;
        String word = commandWord(command);
        if (word.isEmpty()) return;
        UUID uuid = player.getUuid();
        // 仅当这条命令的首词是我们登记过的建议时才捕获，避免对所有命令都产生记录开销
        if (!PENDING.containsKey(key(uuid, word))) {
            return;
        }
        ACTIVE_CAPTURE.set(new Capture(uuid, word));
    }

    /**
     * 命令执行期间收集一行反馈/错误文本（由 {@code ServerCommandSourceMixin} 在
     * {@code sendFeedback}/{@code sendError} 里调用）。仅在有打开的捕获窗口且属于同一玩家时收集。
     *
     * @param sourcePlayer 产生该反馈的命令源对应的玩家（可能为 null，如命令方块/控制台）
     * @param text         反馈文本（已转成纯字符串）
     */
    public static void captureFeedback(ServerPlayerEntity sourcePlayer, String text) {
        Capture capture = ACTIVE_CAPTURE.get();
        if (capture == null || text == null || text.isBlank()) return;
        // 命令重定向/execute 等可能切换命令源，只收集与发起玩家一致的反馈
        if (sourcePlayer != null && !sourcePlayer.getUuid().equals(capture.playerUuid)) return;
        capture.feedbackLines.add(text.trim());
    }

    /**
     * 玩家的这条命令执行完毕，关闭捕获窗口。若这条命令正是登记过的建议，则触发续跑：
     * 把收集到的反馈文本交给回调，回喂给 AI。
     *
     * <p>由 {@code ServerPlayNetworkHandlerMixin} 在 {@code onCommandExecution} 尾部调用
     * （无论命令成功还是失败都要调用，以保证窗口一定关闭、ThreadLocal 一定清理）。
     */
    public static void endCaptureAndResolve() {
        Capture capture = ACTIVE_CAPTURE.get();
        if (capture == null) return;
        ACTIVE_CAPTURE.remove();

        String k = key(capture.playerUuid, capture.commandWord);
        Suggestion suggestion = PENDING.get(k);
        if (suggestion == null) return; // 可能已被超时或其它路径处理
        if (!suggestion.markResolved()) return;
        PENDING.remove(k);
        suggestion.timeoutTask.cancel(false);

        String feedbackText = capture.feedbackLines.isEmpty()
                ? "" // 命令执行成功但没有产生任何文本反馈（如部分静默命令）
                : String.join("\n", capture.feedbackLines);
        LOGGER.info("AI 建议命令已由玩家执行，收集到反馈 {} 行，触发续跑: command=/{}",
                capture.feedbackLines.size(), suggestion.fullCommand);
        try {
            suggestion.callback.resume(feedbackText, true);
        } catch (Exception e) {
            LOGGER.error("AI 建议命令执行反馈续跑回调失败: command=/{}", suggestion.fullCommand, e);
        }
    }

    private static void onTimeout(String k) {
        Suggestion suggestion = PENDING.get(k);
        if (suggestion == null) return;
        if (!suggestion.markResolved()) return;
        PENDING.remove(k);
        LOGGER.info("AI 建议命令超时未被玩家执行，触发未执行续跑: command=/{}", suggestion.fullCommand);
        try {
            suggestion.callback.resume(null, false);
        } catch (Exception e) {
            LOGGER.error("AI 建议命令超时续跑回调失败: command=/{}", suggestion.fullCommand, e);
        }
    }

    /**
     * 调试/测试用：强制让指定玩家当前所有待执行的建议命令立即按 "超时未执行" 处理，
     * 复用与真实超时完全相同的逻辑（触发未执行续跑）。
     *
     * @param playerUuid 目标玩家 UUID
     * @return 被强制触发的建议数量
     */
    public static int forceTimeoutForPlayer(UUID playerUuid) {
        List<String> matchedKeys = new ArrayList<>();
        for (Map.Entry<String, Suggestion> e : PENDING.entrySet()) {
            if (e.getValue().playerUuid.equals(playerUuid)) {
                matchedKeys.add(e.getKey());
            }
        }
        int count = 0;
        for (String k : matchedKeys) {
            Suggestion suggestion = PENDING.get(k);
            if (suggestion == null) continue;
            suggestion.timeoutTask.cancel(false);
            onTimeout(k);
            count++;
        }
        return count;
    }
}
