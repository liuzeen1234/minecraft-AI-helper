package com.example.helloworld;

import com.example.helloworld.blueprint.BlueprintBuilder;
import com.example.helloworld.blueprint.BlueprintData;
import com.example.helloworld.blueprint.BlueprintParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 AI 返回的结构化指令并在游戏中执行。
 *
 * AI 回复中可以嵌入如下格式的指令：
 * [ACTION]{"type":"place_block","block":"oak_planks","forward":10,"right":0,"up":0}[/ACTION]
 * [BLUEPRINT]...V2蓝图文本...[/BLUEPRINT]
 */
public class AICommandExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("AICommandExecutor");

    /**
     * 调试开关：强制多轮工具调用。开启后系统提示词会额外要求 AI 至少调用 2 次工具，
     * 用于手动测试多轮循环（agentic loop）。仅内存态，不写入配置文件，通过 debug-menu 切换。
     */
    private static volatile boolean forceMultiToolTesting = false;

    public static boolean isForceMultiToolTesting() { return forceMultiToolTesting; }

    public static void setForceMultiToolTesting(boolean enabled) {
        forceMultiToolTesting = enabled;
        LOGGER.info("[调试] 强制多轮工具调用 = {}", enabled);
    }

    /**
     * 调试开关：把 AI 每轮工具调用的返回值打印到聊天框。开启后，多轮工具循环
     * （agentic loop）里每次工具（联网搜索 / 抓取网页 / 游戏操作）产生的返回值
     * 都会以调试文本形式发送到玩家聊天框，便于观察 AI 实际拿到了什么。
     * 仅内存态，不写入配置文件，通过 debug-menu 切换。
     */
    private static volatile boolean printToolResultsToChat = false;

    public static boolean isPrintToolResultsToChat() { return printToolResultsToChat; }

    public static void setPrintToolResultsToChat(boolean enabled) {
        printToolResultsToChat = enabled;
        LOGGER.info("[调试] 打印工具返回值到聊天框 = {}", enabled);
    }

    private static final Pattern ACTION_PATTERN = Pattern.compile("\\[ACTION\\](.*?)\\[/ACTION\\]", Pattern.DOTALL);
    private static final Pattern BLUEPRINT_PATTERN = Pattern.compile("\\[BLUEPRINT\\](.*?)\\[/BLUEPRINT\\]", Pattern.DOTALL);
    // 地形查询工具：[QUERY_REGION]x1,y1,z1 x2,y2,z2[/QUERY_REGION] 或 [QUERY_REGION]around <半径>[/QUERY_REGION]
    private static final Pattern QUERY_REGION_PATTERN = Pattern.compile("\\[QUERY_REGION\\](.*?)\\[/QUERY_REGION\\]", Pattern.DOTALL);
    // 匹配未闭合的 [BLUEPRINT]（AI 输出被 token 截断时）
    private static final Pattern BLUEPRINT_UNCLOSED_PATTERN = Pattern.compile("\\[BLUEPRINT\\](.*)", Pattern.DOTALL);

    /**
     * 从 AI 回复中提取并执行所有指令，返回清理后的纯文本回复。
     * 支持两种格式：
     *   [ACTION]...[/ACTION] — 单条 JSON 指令（放置方块、给物品等）
     *   [BLUEPRINT]...[/BLUEPRINT] — V2 蓝图格式，批量放置结构
     * 当 [BLUEPRINT] 标签因 token 截断未闭合时，也会尝试解析已有部分。
     */
    public static String processResponse(String aiResponse, ServerPlayerEntity player) {
        ProcessResult r = process(aiResponse, player);
        return r.fullText;
    }

    /**
     * 判断 AI 回复中是否包含游戏操作类工具标签（[ACTION] 或 [BLUEPRINT]，含被截断的未闭合蓝图）。
     * 用于多轮工具循环判断本轮是否请求了游戏操作工具。
     */
    public static boolean containsGameActionTags(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) return false;
        if (ACTION_PATTERN.matcher(aiResponse).find()) return true;
        if (BLUEPRINT_PATTERN.matcher(aiResponse).find()) return true;
        // 未闭合的 [BLUEPRINT]（token 截断）
        return aiResponse.contains("[BLUEPRINT]");
    }

    /**
     * 执行结果承载对象。
     * cleanText  : 移除所有指令标签后的纯文本正文。
     * resultBlock: 指令执行结果块（含结果头 + 每条结果），无指令时为空字符串。
     * fullText   : cleanText 与 resultBlock 拼接后的完整文本。
     * hasPendingConfirmation: 本次处理中是否有操作被挂起等待玩家点击 [是]/[否] 确认。
     *   为 true 时，调用方（多轮工具循环等）必须停止继续推演/回喂 AI，等玩家确认后再继续，
     *   否则 AI 会基于"尚未真正发生"的挂起提示文本继续做下一步决策。
     */
    public static class ProcessResult {
        public final String cleanText;
        public final String resultBlock;
        public final String fullText;
        public final boolean hasPendingConfirmation;
        ProcessResult(String cleanText, String resultBlock, String fullText, boolean hasPendingConfirmation) {
            this.cleanText = cleanText;
            this.resultBlock = resultBlock;
            this.fullText = fullText;
            this.hasPendingConfirmation = hasPendingConfirmation;
        }
    }

    /**
     * 收集"当前这次 process() 调用中触发确认的操作"。由 executeBlueprintMaybeConfirm /
     * executeActionMaybeConfirm 在命中确认路径时追加一项，process() 结束时统一批量发起确认
     * 并清理。用 ThreadLocal 是因为服务端每次请求在各自线程处理，不会跨线程污染。
     */
    private static final ThreadLocal<List<PendingActionConfirmation.BatchItem>> PENDING_BATCH_ITEMS =
            ThreadLocal.withInitial(ArrayList::new);
    /** 与 {@link #PENDING_BATCH_ITEMS} 一一对应，记录每项的摘要和最终结果，用于批次完成后拼接续跑反馈文本。 */
    private static final ThreadLocal<List<BatchEntry>> PENDING_BATCH_ENTRIES = ThreadLocal.withInitial(ArrayList::new);

    /** 批次中一项的记录：摘要 + 是否被接受 + 真实执行结果文本（拒绝/超时时为 null）。 */
    private static final class BatchEntry {
        final String summary;
        boolean accepted = false;
        String actualResult = null;
        BatchEntry(String summary) { this.summary = summary; }
    }

    /**
     * 登记一个需要确认才能执行的操作。真正的执行逻辑通过 {@code executor} 提供
     * （在玩家点击"是"时才会被调用，返回真实的执行结果文本，用于回喂给 AI）。
     */
    private static void enqueueConfirmation(String summary, java.util.function.Supplier<String> executor) {
        BatchEntry entry = new BatchEntry(summary);
        PENDING_BATCH_ENTRIES.get().add(entry);
        PENDING_BATCH_ITEMS.get().add(new PendingActionConfirmation.BatchItem(summary, () -> {
            entry.accepted = true;
            entry.actualResult = executor.get();
        }));
    }

    /**
     * 解析并执行 AI 回复中的指令，返回拆分好的结果。不支持"确认后自动续跑"，
     * 用于展示环节（多轮工具循环之外的最终展示），等价于 {@code process(aiResponse, player, null)}。
     */
    public static ProcessResult process(String aiResponse, ServerPlayerEntity player) {
        return process(aiResponse, player, null);
    }

    /**
     * 解析并执行 AI 回复中的指令，返回拆分好的结果。
     * 流式模式下正文已实时显示，只需补发 {@link ProcessResult#resultBlock}，避免正文重复。
     *
     * @param onAllConfirmed 当本次调用中有操作触发了确认挂起时，这些操作全部被玩家处理完
     *                        （无论接受/拒绝/超时）后调用一次，参数为汇总的续跑反馈文本
     *                        （只包含被接受并成功执行的操作结果）。可为 null（不需要续跑）。
     */
    public static ProcessResult process(String aiResponse, ServerPlayerEntity player, java.util.function.Consumer<String> onAllConfirmed) {
        if (player == null) return new ProcessResult(aiResponse, "", aiResponse, false);

        PENDING_BATCH_ITEMS.get().clear();
        PENDING_BATCH_ENTRIES.get().clear();
        ServerWorld world = player.getServerWorld();
        List<String> results = new ArrayList<>();
        boolean foundBlueprint = false;
        boolean blueprintTruncated = false;

        // 1. 处理完整的 [BLUEPRINT]...[/BLUEPRINT] 蓝图放置
        Matcher blueprintMatcher = BLUEPRINT_PATTERN.matcher(aiResponse);
        while (blueprintMatcher.find()) {
            foundBlueprint = true;
            String blueprintText = blueprintMatcher.group(1).trim();
            try {
                String result = executeBlueprintMaybeConfirm(blueprintText, player, world);
                results.add(result);
            } catch (Exception e) {
                LOGGER.error("执行蓝图放置失败", e);
                results.add(I18n.tr("cmd.blueprint.failed", e.getMessage()));
            }
        }

        // 2. 如果没找到完整的 [BLUEPRINT]...[/BLUEPRINT]，检查是否有未闭合的（被截断）
        if (!foundBlueprint) {
            Matcher unclosedMatcher = BLUEPRINT_UNCLOSED_PATTERN.matcher(aiResponse);
            if (unclosedMatcher.find()) {
                foundBlueprint = true;
                blueprintTruncated = true;
                String blueprintText = unclosedMatcher.group(1).trim();
                // 去掉最后一行不完整的内容（截断行）
                blueprintText = trimLastIncompleteLine(blueprintText);
                try {
                    String result = executeBlueprintMaybeConfirm(blueprintText, player, world);
                    results.add(result);
                    results.add(I18n.tr("cmd.blueprint.truncated_note"));
                } catch (Exception e) {
                    LOGGER.error("执行截断蓝图放置失败", e);
                    results.add(I18n.tr("cmd.blueprint.truncated_failed", e.getMessage()));
                }
            }
        }

        // 3. 处理 [ACTION]...[/ACTION] 单条指令
        Matcher matcher = ACTION_PATTERN.matcher(aiResponse);
        while (matcher.find()) {
            String json = matcher.group(1).trim();
            try {
                String result = executeActionMaybeConfirm(json, player, world);
                results.add(result);
            } catch (Exception e) {
                LOGGER.error("执行 AI 指令失败: {}", json, e);
                results.add(I18n.tr("cmd.action.failed", e.getMessage()));
            }
        }

        // 移除标签，保留纯文本
        String cleanResponse = BLUEPRINT_PATTERN.matcher(aiResponse).replaceAll("").trim();
        cleanResponse = ACTION_PATTERN.matcher(cleanResponse).replaceAll("").trim();
        if (blueprintTruncated) {
            // 移除未闭合的 [BLUEPRINT] 及其后面所有内容
            cleanResponse = BLUEPRINT_UNCLOSED_PATTERN.matcher(cleanResponse).replaceAll("").trim();
        }

        List<PendingActionConfirmation.BatchItem> batchItems = PENDING_BATCH_ITEMS.get();
        boolean pendingConfirmation = !batchItems.isEmpty();

        if (pendingConfirmation) {
            // 统一批量发起确认：本轮所有需确认的操作合并成一条消息，逐条各自 [是]/[否]。
            // 全部处理完（无论接受/拒绝/超时）后，把被接受操作的真实执行结果拼成反馈文本回调出去，
            // 由调用方（多轮工具循环）决定是否续跑给 AI。
            List<BatchEntry> entries = new ArrayList<>(PENDING_BATCH_ENTRIES.get());
            List<PendingActionConfirmation.BatchItem> itemsCopy = new ArrayList<>(batchItems);
            PENDING_BATCH_ITEMS.remove();
            PENDING_BATCH_ENTRIES.remove();

            Runnable onBatchDone = () -> {
                if (onAllConfirmed == null) return;
                StringBuilder fb = new StringBuilder();
                for (BatchEntry entry : entries) {
                    if (entry.accepted && entry.actualResult != null) {
                        fb.append(entry.actualResult).append("\n");
                    } else {
                        fb.append(I18n.tr("confirm.summary.rejected_line", entry.summary)).append("\n");
                    }
                }
                onAllConfirmed.accept(fb.toString());
            };
            PendingActionConfirmation.requestBatch(player, itemsCopy, onBatchDone);
        } else {
            PENDING_BATCH_ITEMS.remove();
            PENDING_BATCH_ENTRIES.remove();
        }

        // 如果有执行结果，组装结果块
        if (!results.isEmpty()) {
            StringBuilder resultSb = new StringBuilder(I18n.tr("cmd.result.header"));
            for (String r : results) {
                resultSb.append("\n").append(r);
            }
            String resultBlock = resultSb.toString();

            StringBuilder full = new StringBuilder(cleanResponse);
            if (!cleanResponse.isEmpty()) full.append("\n");
            full.append(resultBlock);
            return new ProcessResult(cleanResponse, resultBlock, full.toString(), pendingConfirmation);
        }

        return new ProcessResult(cleanResponse, "", cleanResponse, pendingConfirmation);
    }

    /**
     * 去掉最后一行不完整的内容。
     * 当 AI 输出被 token 截断时，最后一行可能是不完整的方块数据（如 "7,10,"），
     * 需要去掉以避免解析错误。
     */
    private static String trimLastIncompleteLine(String text) {
        if (text.isEmpty()) return text;
        int lastNewline = text.lastIndexOf('\n');
        if (lastNewline < 0) return text; // 只有一行，保留
        String lastLine = text.substring(lastNewline + 1).trim();
        // 如果最后一行是空的或是注释，不需要裁剪
        if (lastLine.isEmpty() || lastLine.startsWith("#")) return text;
        // 检查最后一行是否是完整的方块行（至少要有 x,y,z 和 block_id）
        // 完整格式: "数字,数字,数字   方块ID ..."
        if (lastLine.matches("^\\d+,\\d+,\\d+\\s+\\S+.*$")) {
            return text; // 最后一行完整，保留
        }
        // 最后一行不完整，裁掉
        LOGGER.info("裁剪截断的最后一行: {}", lastLine);
        return text.substring(0, lastNewline);
    }

    /**
     * 蓝图放置的确认包装：先解析出蓝图数据以生成摘要（结构名 + 方块数），
     * 再根据"执行前需确认"开关决定直接建造，还是先发确认消息、挂起后由玩家点击 [是] 再建造。
     */
    private static String executeBlueprintMaybeConfirm(String blueprintText, ServerPlayerEntity player, ServerWorld world) {
        // 确保文本以 V2 头部开始，如果 AI 没写头部则自动补上
        String text = blueprintText.stripLeading();
        if (!text.startsWith("# MCBLUEPRINT v2") && !text.startsWith("#MCBLUEPRINT v2")) {
            text = "# MCBLUEPRINT v2\n" + text;
        }

        BlueprintData data = BlueprintParser.parse(text);
        if (data == null) {
            return I18n.tr("cmd.blueprint.parse_failed");
        }

        final String finalText = text;
        if (!HelloWorldMod.getConfig().isConfirmBeforeExecuteEnabled()) {
            return executeBlueprint(data, finalText, player, world);
        }

        int blockCount = data.getBlocks3d() != null ? data.getBlocks3d().size() : 0;
        String summary = I18n.tr("confirm.summary.blueprint", data.getName(), blockCount);
        // 登记到本次 process() 的确认批次，真正执行推迟到玩家点击 [是] 且整批确认完毕之后
        enqueueConfirmation(summary, () -> executeBlueprint(data, finalText, player, world));
        return I18n.tr("confirm.pending", summary, PendingActionConfirmation.TIMEOUT_SECONDS);
    }

    /**
     * 执行蓝图放置：使用 BlueprintBuilder 在玩家位置建造已解析好的蓝图数据。
     */
    private static String executeBlueprint(BlueprintData data, String text, ServerPlayerEntity player, ServerWorld world) {
        // 计算放置原点：若蓝图指定了自定义原点则使用之，否则默认为玩家脚下位置
        BlockPos origin = resolveOrigin(data, player);

        int count = BlueprintBuilder.build(data, player, world, origin);

        // 自动保存蓝图为 txt 文件到 txts/ 文件夹
        String savedPath = saveBlueprintToTxt(text, data.getName());
        String saveMsg = savedPath != null ? I18n.tr("cmd.blueprint.saved", savedPath) : "";

        String originStr = origin.getX() + ", " + origin.getY() + ", " + origin.getZ();
        return I18n.tr("cmd.blueprint.placed", data.getName(), count, originStr) + saveMsg;
    }

    /**
     * 根据蓝图的自定义原点信息计算实际放置原点。
     * - 未指定：返回玩家脚下位置（默认行为）。
     * - RELATIVE：基于玩家朝向的 forward/right/up 偏移。
     * - ABSOLUTE：世界绝对坐标。
     */
    private static BlockPos resolveOrigin(BlueprintData data, ServerPlayerEntity player) {
        if (!data.hasOrigin()) {
            return player.getBlockPos();
        }
        BlueprintData.OriginSpec spec = data.getOrigin();
        if (spec.getMode() == BlueprintData.OriginSpec.Mode.ABSOLUTE) {
            return new BlockPos(spec.getAbsX(), spec.getAbsY(), spec.getAbsZ());
        }
        // RELATIVE
        return calculatePos(player, spec.getForward(), spec.getRight(), spec.getUp());
    }

    /**
     * 将蓝图文本保存为 txt 文件到 txts/ 文件夹。
     * 文件名基于蓝图名称，如果已存在则追加数字后缀。
     *
     * @param blueprintText 完整的蓝图文本内容
     * @param name          蓝图名称（用于生成文件名）
     * @return 保存的文件路径（相对路径），失败返回 null
     */
    private static String saveBlueprintToTxt(String blueprintText, String name) {
        try {
            // AI 生成的蓝图统一放在 txts/ai-generated/ 子文件夹下
            Path txtsDir = ModPaths.getTxtsDir().resolve("ai-generated");
            if (!Files.exists(txtsDir)) {
                Files.createDirectories(txtsDir);
            }

            // 清理文件名：只移除文件系统真正不允许的非法字符，保留中文等 Unicode 文字
            String safeName = ModPaths.sanitizeFileName(name, "blueprint");

            // 如果文件已存在，追加数字后缀
            Path targetFile = txtsDir.resolve(safeName + ".txt");
            int counter = 1;
            while (Files.exists(targetFile)) {
                targetFile = txtsDir.resolve(safeName + "_" + counter + ".txt");
                counter++;
            }

            // 剥掉 AI 可能写死的 "# origin:" 头部，避免蓝图固定绝对/相对坐标，
            // 让放置端在放置时再决定原点（默认玩家当前位置）。
            String cleaned = stripOriginHeader(blueprintText);

            Files.writeString(targetFile, cleaned);
            LOGGER.info("蓝图已保存到: {}", targetFile);
            return targetFile.toString();
        } catch (IOException e) {
            LOGGER.error("保存蓝图 txt 文件失败: {}", name, e);
            return null;
        }
    }

    // 匹配整行的 "# origin: ..." 头部（行首可有空白，忽略大小写）
    private static final Pattern ORIGIN_HEADER_LINE_PATTERN =
            Pattern.compile("(?im)^[ \\t]*#\\s*origin\\s*:.*(?:\\r?\\n|$)");

    /**
     * 移除蓝图文本中所有 "# origin:" 头部行，使 AI 生成的蓝图不写死原点。
     * 放置时由放置界面/玩家位置决定原点。
     */
    private static String stripOriginHeader(String blueprintText) {
        if (blueprintText == null || blueprintText.isEmpty()) {
            return blueprintText;
        }
        return ORIGIN_HEADER_LINE_PATTERN.matcher(blueprintText).replaceAll("");
    }

    /**
     * ACTION 指令的确认包装：根据"执行前需确认"开关决定直接执行，还是先在聊天框
     * 发 [是]/[否] 确认消息、挂起后由玩家点击 [是] 再真正执行 {@link #executeAction}。
     * {@code execute_command} 本身已有"预填聊天框待玩家确认"的机制，为避免重复确认，此处不再二次拦截。
     */
    private static String executeActionMaybeConfirm(String json, ServerPlayerEntity player, ServerWorld world) {
        String type = extractJsonString(json, "type");
        if (type == null) return I18n.tr("cmd.action.unknown_type");

        if (!HelloWorldMod.getConfig().isConfirmBeforeExecuteEnabled() || "execute_command".equals(type)) {
            return executeAction(json, player, world);
        }

        String summary = buildActionSummary(type, json);
        if (summary == null) {
            // 未知指令类型等无需确认的情况，直接走原逻辑（会返回错误提示）
            return executeAction(json, player, world);
        }

        // 登记到本次 process() 的确认批次，真正执行推迟到玩家点击 [是] 且整批确认完毕之后
        enqueueConfirmation(summary, () -> executeAction(json, player, world));
        return I18n.tr("confirm.pending", summary, PendingActionConfirmation.TIMEOUT_SECONDS);
    }

    /**
     * 根据指令类型和参数生成给玩家展示的操作摘要（用于确认消息）。
     * 返回 null 表示该类型不在已知摘要范围内，调用方应回退到直接执行原逻辑（由 executeAction 给出错误提示）。
     */
    private static String buildActionSummary(String type, String json) {
        return switch (type) {
            case "place_block" -> I18n.tr("confirm.summary.place_block",
                    String.valueOf(extractJsonString(json, "block")));
            case "fill_blocks" -> I18n.tr("confirm.summary.fill_blocks",
                    String.valueOf(extractJsonString(json, "block")));
            case "give_item" -> I18n.tr("confirm.summary.give_item",
                    extractJsonInt(json, "count", 1), String.valueOf(extractJsonString(json, "item")));
            case "set_time" -> I18n.tr("confirm.summary.set_time",
                    String.valueOf(extractJsonString(json, "value")));
            case "set_weather" -> I18n.tr("confirm.summary.set_weather",
                    String.valueOf(extractJsonString(json, "value")));
            case "summon" -> I18n.tr("confirm.summary.summon",
                    extractJsonInt(json, "count", 1), String.valueOf(extractJsonString(json, "entity")));
            case "clear_area" -> I18n.tr("confirm.summary.clear_area");
            case "find_player" -> {
                String target = extractJsonString(json, "player");
                yield I18n.tr("confirm.summary.find_player",
                        (target == null || target.isBlank()) ? I18n.tr("confirm.summary.find_player.self") : target);
            }
            default -> null;
        };
    }

    private static String executeAction(String json, ServerPlayerEntity player, ServerWorld world) {
        // 简易 JSON 解析（避免引入额外依赖）
        String type = extractJsonString(json, "type");
        if (type == null) return I18n.tr("cmd.action.unknown_type");

        return switch (type) {
            case "place_block" -> executePlaceBlock(json, player, world);
            case "fill_blocks" -> executeFillBlocks(json, player, world);
            case "give_item" -> executeGiveItem(json, player);
            case "set_time" -> executeSetTime(json, world);
            case "set_weather" -> executeSetWeather(json, world);
            case "summon" -> executeSummon(json, player, world);
            case "clear_area" -> executeClearArea(json, player, world);
            case "find_player" -> executeFindPlayer(json, player);
            case "execute_command" -> executeMinecraftCommand(json, player);
            default -> I18n.tr("cmd.action.unknown_type_arg", type);
        };
    }

    // ========== 放置单个方块 ==========
    private static String executePlaceBlock(String json, ServerPlayerEntity player, ServerWorld world) {
        String blockName = extractJsonString(json, "block");
        if (blockName == null) return I18n.tr("cmd.arg.missing_block");

        Block block = getBlock(blockName);
        if (block == null) return I18n.tr("cmd.block.unknown", blockName);

        BlockPos pos = calculateRelativePos(json, player);
        world.setBlockState(pos, block.getDefaultState());
        return I18n.tr("cmd.block.placed", blockName, formatPos(pos));
    }

    // ========== 批量填充方块 ==========
    private static String executeFillBlocks(String json, ServerPlayerEntity player, ServerWorld world) {
        String blockName = extractJsonString(json, "block");
        if (blockName == null) return I18n.tr("cmd.arg.missing_block");

        Block block = getBlock(blockName);
        if (block == null) return I18n.tr("cmd.block.unknown", blockName);

        // 支持两种模式：相对坐标范围 或 绝对坐标范围
        int x1 = extractJsonInt(json, "x1", Integer.MIN_VALUE);
        int y1 = extractJsonInt(json, "y1", Integer.MIN_VALUE);
        int z1 = extractJsonInt(json, "z1", Integer.MIN_VALUE);
        int x2 = extractJsonInt(json, "x2", Integer.MIN_VALUE);
        int y2 = extractJsonInt(json, "y2", Integer.MIN_VALUE);
        int z2 = extractJsonInt(json, "z2", Integer.MIN_VALUE);

        BlockPos from, to;
        if (x1 != Integer.MIN_VALUE && y1 != Integer.MIN_VALUE && z1 != Integer.MIN_VALUE
                && x2 != Integer.MIN_VALUE && y2 != Integer.MIN_VALUE && z2 != Integer.MIN_VALUE) {
            // 绝对坐标
            from = new BlockPos(x1, y1, z1);
            to = new BlockPos(x2, y2, z2);
        } else {
            // 相对坐标范围
            int fwd1 = extractJsonInt(json, "forward_from", 0);
            int fwd2 = extractJsonInt(json, "forward_to", 0);
            int right1 = extractJsonInt(json, "right_from", 0);
            int right2 = extractJsonInt(json, "right_to", 0);
            int up1 = extractJsonInt(json, "up_from", 0);
            int up2 = extractJsonInt(json, "up_to", 0);
            from = calculatePos(player, fwd1, right1, up1);
            to = calculatePos(player, fwd2, right2, up2);
        }

        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());

        BlockState state = block.getDefaultState();
        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlockState(new BlockPos(x, y, z), state);
                    count++;
                }
            }
        }
        return I18n.tr("cmd.fill.done", count, blockName);
    }

    // ========== 清除区域（替换为空气） ==========
    private static String executeClearArea(String json, ServerPlayerEntity player, ServerWorld world) {
        int fwd1 = extractJsonInt(json, "forward_from", 0);
        int fwd2 = extractJsonInt(json, "forward_to", 0);
        int right1 = extractJsonInt(json, "right_from", 0);
        int right2 = extractJsonInt(json, "right_to", 0);
        int up1 = extractJsonInt(json, "up_from", 0);
        int up2 = extractJsonInt(json, "up_to", 0);

        BlockPos from = calculatePos(player, fwd1, right1, up1);
        BlockPos to = calculatePos(player, fwd2, right2, up2);

        int minX = Math.min(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxX = Math.max(from.getX(), to.getX());
        int maxY = Math.max(from.getY(), to.getY());
        int maxZ = Math.max(from.getZ(), to.getZ());

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
                    count++;
                }
            }
        }
        return I18n.tr("cmd.clear.done", count);
    }

    // ========== 给予物品 ==========
    private static String executeGiveItem(String json, ServerPlayerEntity player) {
        String itemName = extractJsonString(json, "item");
        if (itemName == null) return I18n.tr("cmd.arg.missing_item");

        int count = extractJsonInt(json, "count", 1);
        count = Math.max(1, Math.min(count, 64));

        Identifier id = new Identifier("minecraft", itemName);
        Optional<Item> itemOpt = Registries.ITEM.getOrEmpty(id);
        if (itemOpt.isEmpty()) return I18n.tr("cmd.item.unknown", itemName);

        ItemStack stack = new ItemStack(itemOpt.get(), count);
        player.getInventory().insertStack(stack);
        return I18n.tr("cmd.item.given", count, itemName);
    }

    // ========== 设置时间 ==========
    private static String executeSetTime(String json, ServerWorld world) {
        String timeStr = extractJsonString(json, "value");
        if (timeStr == null) return I18n.tr("cmd.arg.missing_value");

        long time = switch (timeStr.toLowerCase()) {
            case "day" -> 1000;
            case "noon" -> 6000;
            case "sunset" -> 12000;
            case "night" -> 13000;
            case "midnight" -> 18000;
            case "sunrise" -> 23000;
            default -> {
                try {
                    yield Long.parseLong(timeStr);
                } catch (NumberFormatException e) {
                    yield -1L;
                }
            }
        };

        if (time < 0) return I18n.tr("cmd.time.invalid", timeStr);
        world.setTimeOfDay(time);
        return I18n.tr("cmd.time.set", timeStr, time);
    }

    // ========== 设置天气 ==========
    private static String executeSetWeather(String json, ServerWorld world) {
        String weather = extractJsonString(json, "value");
        if (weather == null) return I18n.tr("cmd.arg.missing_value");

        int duration = 6000; // 默认 5 分钟
        switch (weather.toLowerCase()) {
            case "clear" -> {
                world.setWeather(duration, 0, false, false);
                return I18n.tr("cmd.weather.clear");
            }
            case "rain" -> {
                world.setWeather(0, duration, true, false);
                return I18n.tr("cmd.weather.rain");
            }
            case "thunder" -> {
                world.setWeather(0, duration, true, true);
                return I18n.tr("cmd.weather.thunder");
            }
            default -> { return I18n.tr("cmd.weather.unknown", weather); }
        }
    }

    // ========== 生成实体 ==========
    private static String executeSummon(String json, ServerPlayerEntity player, ServerWorld world) {
        String entityName = extractJsonString(json, "entity");
        if (entityName == null) return I18n.tr("cmd.arg.missing_entity");

        int count = extractJsonInt(json, "count", 1);
        count = Math.max(1, Math.min(count, 20));

        Identifier id = new Identifier("minecraft", entityName);
        Optional<EntityType<?>> entityTypeOpt = Registries.ENTITY_TYPE.getOrEmpty(id);
        if (entityTypeOpt.isEmpty()) return I18n.tr("cmd.entity.unknown", entityName);

        BlockPos pos = calculateRelativePos(json, player);
        EntityType<?> entityType = entityTypeOpt.get();

        for (int i = 0; i < count; i++) {
            entityType.spawn(world, pos, net.minecraft.entity.SpawnReason.COMMAND);
        }
        return I18n.tr("cmd.summon.done", formatPos(pos), count, entityName);
    }

    // ========== 查询玩家位置 ==========
    private static String executeFindPlayer(String json, ServerPlayerEntity player) {
        String targetName = extractJsonString(json, "player");

        // 未指定玩家名，或指定为自己 → 返回当前玩家位置
        if (targetName == null || targetName.isBlank()
                || targetName.equalsIgnoreCase(player.getName().getString())) {
            BlockPos self = player.getBlockPos();
            return I18n.tr("cmd.findplayer.self",
                    self.getX(), self.getY(), self.getZ(),
                    dimensionName(player.getServerWorld()));
        }

        // 在服务器所有在线玩家中查找目标（不区分大小写）
        ServerPlayerEntity target = null;
        for (ServerPlayerEntity p : player.getServer().getPlayerManager().getPlayerList()) {
            if (p.getName().getString().equalsIgnoreCase(targetName)) {
                target = p;
                break;
            }
        }

        if (target == null) {
            return I18n.tr("cmd.findplayer.not_found", targetName);
        }

        BlockPos pos = target.getBlockPos();
        return I18n.tr("cmd.findplayer.found",
                target.getName().getString(),
                pos.getX(), pos.getY(), pos.getZ(),
                dimensionName(target.getServerWorld()));
    }

    /**
     * 返回维度的友好名称（主世界/下界/末地或原始 ID）。
     */
    private static String dimensionName(ServerWorld world) {
        String id = world.getRegistryKey().getValue().toString();
        return switch (id) {
            case "minecraft:overworld" -> I18n.tr("cmd.dimension.overworld");
            case "minecraft:the_nether" -> I18n.tr("cmd.dimension.nether");
            case "minecraft:the_end" -> I18n.tr("cmd.dimension.end");
            default -> id;
        };
    }

    // ========== 建议 Minecraft 原版命令（不会自动执行） ==========
    // 安全设计：AI 不会、也不能直接以提升权限执行任意命令。这里只是将命令文本
    // 通过网络包发给触发本次请求的玩家客户端，由客户端把命令预填到聊天输入框，
    // 玩家看到具体内容后自行决定是否按下回车发送。真正的执行走 Minecraft 原生
    // 聊天/命令系统，权限检查也由原版按玩家自身权限等级处理，本 mod 不做任何提权。
    private static String executeMinecraftCommand(String json, ServerPlayerEntity player) {
        // 运行时二次防御：即使 AI 因记忆/越狱等原因仍生成了 execute_command，
        // 只要玩家关闭了"允许 AI 使用原版命令"开关，这里也直接拒绝，不发送任何命令建议。
        if (!HelloWorldMod.getConfig().isVanillaCommandsEnabled()) {
            return I18n.tr("cmd.command.disabled_by_setting");
        }

        String command = extractJsonString(json, "command");
        if (command == null || command.isBlank()) return I18n.tr("cmd.arg.missing_command");

        // 去掉开头的 /，统一补回，保证发到聊天框的内容是完整的 "/xxx" 形式
        if (command.startsWith("/")) command = command.substring(1);
        String fullCommand = "/" + command;

        try {
            net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            buf.writeString(fullCommand);
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    player, HelloWorldMod.SUGGEST_COMMAND_PACKET, buf);
            return I18n.tr("cmd.command.suggested", fullCommand);
        } catch (Exception e) {
            LOGGER.error("发送命令建议失败: {}", fullCommand, e);
            return I18n.tr("cmd.command.suggest_failed", fullCommand, e.getMessage());
        }
    }

    // ========== 工具方法 ==========

    /**
     * 根据 forward/right/up 相对坐标计算目标位置
     */
    private static BlockPos calculateRelativePos(String json, ServerPlayerEntity player) {
        int forward = extractJsonInt(json, "forward", 0);
        int right = extractJsonInt(json, "right", 0);
        int up = extractJsonInt(json, "up", 0);
        return calculatePos(player, forward, right, up);
    }

    /**
     * 根据玩家朝向计算相对位置。
     * forward = 玩家面朝方向，right = 玩家右手方向，up = 垂直方向
     */
    private static BlockPos calculatePos(ServerPlayerEntity player, int forward, int right, int up) {
        BlockPos base = player.getBlockPos();
        double yaw = Math.toRadians(player.getYaw());

        // 前方向量 (水平)
        double fwdX = -Math.sin(yaw);
        double fwdZ = Math.cos(yaw);

        // 右方向量 (水平，前方顺时针90度)
        double rightX = fwdZ;
        double rightZ = -fwdX;

        int dx = (int) Math.round(fwdX * forward + rightX * right);
        int dz = (int) Math.round(fwdZ * forward + rightZ * right);

        return new BlockPos(base.getX() + dx, base.getY() + up, base.getZ() + dz);
    }

    private static Block getBlock(String name) {
        Identifier id = new Identifier("minecraft", name);
        Block block = Registries.BLOCK.get(id);
        // Registries.BLOCK.get 对未知 ID 返回 AIR
        if (block == Blocks.AIR && !"air".equals(name)) {
            return null;
        }
        return block;
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    // ========== 简易 JSON 解析 ==========

    static String extractJsonString(String json, String key) {
        // 匹配 "key":"value" 或 "key": "value"，支持值中包含转义引号 \"
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            // 还原转义字符：\" → " 以及 \\ → \
            return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return null;
    }

    static int extractJsonInt(String json, String key, int defaultValue) {
        // 匹配 "key":123 或 "key": -5
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 生成 system prompt，告诉 AI 可以使用哪些指令。
     * 根据当前语言设置追加语言指令，使 AI 回复语言跟随 mod 语言。
     * 默认允许原版命令建议（execute_command），等价于 {@code getSystemPrompt(true)}。
     */
    public static String getSystemPrompt() {
        return getSystemPrompt(true);
    }

    /**
     * 生成 system prompt，告诉 AI 可以使用哪些指令。
     *
     * @param vanillaCommandsEnabled 是否允许 AI 使用 execute_command（建议原版命令）。
     *                                关闭时，system prompt 中完全不会出现 execute_command 相关说明，
     *                                AI 也就不会尝试生成这类指令，只能使用 mod 自带的具体功能（ACTION/BLUEPRINT 等）。
     */
    public static String getSystemPrompt(boolean vanillaCommandsEnabled) {
        String vanillaCommandSection = !vanillaCommandsEnabled ? "" :
               "11. 建议一条 Minecraft 命令（万能后备，不会自动执行）:\n"
             + "[ACTION]{\"type\":\"execute_command\",\"command\":\"/命令内容\"}[/ACTION]\n"
             + "重要: 这个指令【不会】自动执行。系统只会把命令文本预填到玩家的聊天输入框中，\n"
             + "玩家需要自己看一眼内容、自己按回车才会真正发送/执行。请把这当作\"建议一条命令让玩家确认\"，\n"
             + "而不是\"我可以直接操作游戏\"。因此每次只能建议一条命令（多条请分多轮，等玩家确认上一条后再继续），\n"
             + "并在文字里简单说明这条命令是做什么的，方便玩家判断要不要发送。\n"
             + "示例:\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/effect give @s speed 60 2\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/gamemode creative\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/enchant @s sharpness 5\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/kill @e[type=zombie,distance=..30]\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/playsound minecraft:entity.ender_dragon.growl master @a\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/setblock ~ ~1 ~ minecraft:chest\"}[/ACTION]\n\n";

        String vanillaCommandRules = !vanillaCommandsEnabled ?
               "- 上述指令无法满足的操作时，直接说明当前无法通过 mod 功能完成，不要编造或建议任何 /命令\n" :
               "- 上述指令无法满足的操作（如 /effect、/enchant、/gamemode、/kill、/scoreboard、/particle、/title、/playsound、/data 等）→ 使用 execute_command\n"
             + "- 优先使用具体的 ACTION 类型，只有它们不支持时才用 execute_command\n"
             + "- execute_command 中的命令格式与 Minecraft 原版命令完全一致，前面加 / 即可\n"
             + "- execute_command 只是把命令预填到玩家聊天框等待确认，不会自动执行，一次只建议一条\n";

        return "你是一个 Minecraft 游戏助手 AI。你可以和玩家聊天，也可以通过特殊指令帮玩家在游戏中执行操作。\n"
             + "当玩家要求你执行游戏操作时，在你的回复中嵌入指令标签。你可以在一条回复中包含多个标签。\n\n"
             + "========== 建筑放置（推荐方式）==========\n\n"
             + "当玩家要求建造建筑、结构、房屋等多方块结构时，使用 [BLUEPRINT]...[/BLUEPRINT] 标签，内容为 MCBLUEPRINT v2 格式：\n\n"
             + "[BLUEPRINT]\n"
             + "# MCBLUEPRINT v2\n"
             + "# name: 结构名称\n"
             + "# 坐标：x向东(列), y向上(层), z向南(行)\n\n"
             + "## BLOCKS\n\n"
             + "x,y,z   block_id   [key=value ...]\n"
             + "[/BLUEPRINT]\n\n"
             + "V2 蓝图格式规则：\n"
             + "- 首行必须是 \"# MCBLUEPRINT v2\"\n"
             + "- \"# name: xxx\" 指定结构名称\n"
             + "- 每个方块一行，格式：x,y,z   方块ID   [属性key=value ...]\n"
             + "- 坐标含义：x=东西(列), y=上下(层), z=南北(行)\n"
             + "- 坐标可以是负数：x/y/z 都支持负值（如 -2,0,-3），无需强制从 0 开始。\n"
             + "  当结构以某个中心点对称时，用负坐标更自然，例如以 (0,0,0) 为中心，\n"
             + "  向四周展开写 -N..N 的坐标；放置时会自动按最小坐标对齐，不会丢失负坐标部分。\n"
             + "- 方块ID 使用 Minecraft 英文 ID（不含 minecraft: 前缀）\n"
             + "- 属性用空格分隔的 key=value 对，如 facing=north waterlogged=false\n"
             + "- 空气方块的处理：如果某个格子你不想改动（保留原有地形），直接不写该坐标即可，不会覆盖。\n"
             + "- 【清除/替换地形】你也可以主动写 air 方块来清空目标格子——写一行 \"x,y,z   air\" 会把该位置替换成空气，\n"
             + "  即把原有的方块（泥土、石头、树木、水等）清除掉。这在需要平整地形、挖出空间、削掉挡路的山体或旧建筑时很有用。\n"
             + "  例如要在坡地上盖房，可先用 [QUERY_REGION] 查出高出的方块坐标，再在蓝图里对这些坐标写 air 把它们削平，然后放置结构。\n"
             + "  同理，也可用 air 在地下/山体中挖出房间或隧道的内部空腔。\n"
             + "  注意：写 air 是破坏性操作，会清掉原方块，请只对确实需要清理的格子写 air，不要整片区域无脑填 air。\n"
             + "- 以 # 开头的行是注释，会被忽略\n"
             + "- 建议用 \"# --- 第 N 层 (y=N) ---\" 注释分隔每层，方便阅读\n\n"
             + "自定义放置原点（可选，用 \"# origin:\" 头指定，放在 name 下方）：\n"
             + "- 不写 origin 时，默认原点 (0,0,0) 为玩家脚下位置\n"
             + "- 相对玩家朝向偏移（推荐，玩家转身时结构会跟随朝向摆放）：\n"
             + "    # origin: relative forward=10 right=2 up=0\n"
             + "    forward=玩家面朝方向前方(负=后方), right=玩家右手方向(负=左方), up=上方(负=下方)\n"
             + "    缺省的分量按 0 处理，relative 关键字可省略：# origin: forward=10\n"
             + "- 世界绝对坐标（把结构固定放在世界某处，与玩家位置无关）：\n"
             + "    # origin: absolute 100 64 -200\n"
             + "    也可写作 # origin: absolute x=100 y=64 z=-200\n"
             + "- 使用场景：玩家说\"在我前面20格盖\"用 relative forward=20；\n"
             + "  说\"在坐标100 64 -200盖\"用 absolute 100 64 -200；不确定就不写 origin\n"
             + "- 【推荐】只要你知道目标位置的世界绝对坐标（尤其是先用 [QUERY_REGION] 勘查过地形后），\n"
             + "  就优先使用 \"# origin: absolute x y z\" 来建造。绝对坐标能让结构精确落在你勘查过的位置——\n"
             + "  比如贴着真实地面、避开或衔接已有建筑、跨越坡地。这比 relative 更可控，也不会因玩家移动或转身而错位。\n"
             + "  典型流程：[QUERY_REGION] 查地形 → 从返回的方块绝对坐标确定地面高度和落点 → \n"
             + "  用蓝图坐标(0,0,0 为结构西北角最低层) + \"# origin: absolute\" 指定该落点，把结构精确放上去。\n\n"
             + "常用方块属性示例：\n"
             + "- 楼梯: facing=north/south/east/west  half=bottom/top  shape=straight\n"
             + "- 台阶: type=bottom/top/double  waterlogged=false\n"
             + "- 门: facing=north/south/east/west  half=lower/upper  hinge=left/right  open=false\n"
             + "- 墙上火把: wall_torch facing=north/south/east/west\n"
             + "- 原木: axis=x/y/z\n"
             + "- 栅栏门: facing=north/south/east/west  in_wall=false  open=false\n"
             + "- 床: facing=north/south/east/west  occupied=false  part=head/foot\n"
             + "  【床的摆放规则，务必遵守】床占两格：一格 part=foot（床尾），一格 part=head（床头）。\n"
             + "    facing 指向【床头(head)】所在的方向，即 head 位于 foot 沿 facing 方向的相邻那一格。\n"
             + "    两格的 facing 必须相同，且必须一格 head 一格 foot（不能两格都是 head 或都是 foot）。\n"
             + "    坐标对应关系（head 相对 foot 的偏移）：\n"
             + "      facing=south → head 在 foot 的 z+1（head 的 z 比 foot 大 1）\n"
             + "      facing=north → head 在 foot 的 z-1（head 的 z 比 foot 小 1）\n"
             + "      facing=east  → head 在 foot 的 x+1（head 的 x 比 foot 大 1）\n"
             + "      facing=west  → head 在 foot 的 x-1（head 的 x 比 foot 小 1）\n"
             + "    例：facing=east 时应写 (3,y,z) part=foot 与 (4,y,z) part=head；\n"
             + "        facing=south 时应写 (x,y,4) part=foot 与 (x,y,5) part=head。\n"
             + "    若 head/foot 的相对位置与 facing 不一致，床会渲染错误（断裂/朝向错乱），务必按上表核对。\n"
             + "- 箱子: facing=north/south/east/west  type=single  waterlogged=false\n"
             + "- 按钮: face=floor/wall/ceiling  facing=north/south/east/west\n"
             + "- 活塞: facing=north/south/east/west/up/down\n"
             + "- 观察者: facing=north/south/east/west/up/down\n\n"
             + "========== 红石知识（查知识库获取细节）==========\n\n"
             + "红石元件的方块状态、行为细节，以及逻辑门/脉冲电路/时钟电路/存储电路/常见电路模式的搭建方法，"
             + "已收录在下方\"知识库目录\"中（红石方块与红石元件、红石机械方块、红石电路设计与逻辑门等文档）。"
             + "需要这些内容时用 [KNOWLEDGE] 标签点名查阅，不要凭记忆瞎猜方块属性或电路结构。\n\n"
             + "复杂红石机器处理规则：\n"
             + "- 以下类型的红石机器结构复杂、版本差异大，知识库不一定覆盖，不要凭想象建造，必须先用 [SEARCH] 搜索最新设计：\n"
             + "  世界吞噬者(World Eater)、TNT复制机(TNT Duper)、飞行机器(Flying Machine)、\n"
             + "  全自动农场、刷铁机、刷怪塔、自动分类机、活塞门(3x3+)、\n"
             + "  隐藏楼梯、自动酿造机、炮(TNT Cannon)、红石电脑等\n"
             + "- 搜索时使用英文关键词效果更好，如 \"minecraft world eater schematic 1.20\"\n"
             + "- 红石电路相关搜索优先加 site:minecraft.wiki\n"
             + "- 如果搜索结果不理想，直接用 [FETCH] 抓取 Minecraft Wiki 页面获取准确信息：\n"
             + "  常用页面：https://minecraft.wiki/w/Redstone_circuits（红石电路大全）\n"
             + "            https://minecraft.wiki/w/Tutorial:Advanced_redstone_circuits（进阶电路）\n"
             + "            https://minecraft.wiki/w/Tutorials/Redstone（红石教程）\n"
             + "- 如果搜索结果包含方块坐标列表或litematic/schematic数据，根据其转换为蓝图格式\n"
             + "- 如果搜索不到精确的方块级设计，诚实告诉玩家此机器过于复杂无法准确还原，\n"
             + "  建议玩家提供schematic文件或参考教程链接（你可以用 [FETCH] 抓取）\n"
             + "- 对于简单红石电路（门灯、暗门、简单时钟、逻辑门组合等基础逻辑），先查知识库确认细节后可直接建造\n\n"
             + "蓝图中红石放置顺序建议：\n"
             + "- 第一步：先放实体方块（石头、混凝土等作为底座、支撑和红石粉放置面）\n"
             + "- 第二步：放置机械元件（活塞、投掷器、门、红石灯等被激活的目标）\n"
             + "- 第三步：放置信号处理元件（中继器、比较器、红石火把）\n"
             + "- 第四步：最后放红石粉（会自动连接相邻元件形成电路）\n"
             + "- 第五步：放置输入元件（拉杆、按钮、压力板等触发源）\n"
             + "- 活塞放置为 extended=false（通电后自动伸出）\n"
             + "- 红石灯放置为 lit=false（通电后自动亮起）\n"
             + "- 拉杆/按钮放置为 powered=false（玩家手动激活）\n"
             + "- 铜灯泡放置为 lit=false, powered=false（首次脉冲后亮起）\n\n"
             + "蓝图示例 — 7x7 村庄小屋（含家具，四面坡屋顶，正确楼梯朝向）：\n"
             + "[BLUEPRINT]\n"
             + "# MCBLUEPRINT v2\n"
             + "# name: example\n\n"
             + "## BLOCKS\n\n"
             + "# --- 第 1 层 (y=0) ---\n"
             + "1,0,1   stripped_oak_log   axis=y\n"
             + "2,0,1   cobblestone\n"
             + "3,0,1   cobblestone\n"
             + "4,0,1   cobblestone\n"
             + "5,0,1   stripped_oak_log   axis=y\n"
             + "1,0,2   cobblestone\n"
             + "2,0,2   oak_planks\n"
             + "3,0,2   oak_planks\n"
             + "4,0,2   oak_planks\n"
             + "5,0,2   cobblestone\n"
             + "0,0,3   cobblestone_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "1,0,3   cobblestone\n"
             + "2,0,3   oak_planks\n"
             + "3,0,3   oak_planks\n"
             + "4,0,3   oak_planks\n"
             + "5,0,3   cobblestone\n"
             + "1,0,4   cobblestone\n"
             + "2,0,4   oak_planks\n"
             + "3,0,4   oak_planks\n"
             + "4,0,4   oak_planks\n"
             + "5,0,4   cobblestone\n"
             + "1,0,5   stripped_oak_log   axis=y\n"
             + "2,0,5   cobblestone\n"
             + "3,0,5   cobblestone\n"
             + "4,0,5   cobblestone\n"
             + "5,0,5   stripped_oak_log   axis=y\n\n"
             + "# --- 第 2 层 (y=1) ---\n"
             + "1,1,1   stripped_oak_log   axis=y\n"
             + "2,1,1   cobblestone\n"
             + "3,1,1   cobblestone\n"
             + "4,1,1   cobblestone\n"
             + "5,1,1   stripped_oak_log   axis=y\n"
             + "1,1,2   cobblestone\n"
             + "3,1,2   white_bed   facing=east   occupied=false   part=foot\n"
             + "4,1,2   white_bed   facing=east   occupied=false   part=head\n"
             + "5,1,2   cobblestone\n"
             + "1,1,3   oak_door   facing=east   half=lower   hinge=right   open=false   powered=false\n"
             + "5,1,3   cobblestone\n"
             + "1,1,4   cobblestone\n"
             + "4,1,4   oak_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "5,1,4   cobblestone\n"
             + "1,1,5   stripped_oak_log   axis=y\n"
             + "2,1,5   cobblestone\n"
             + "3,1,5   cobblestone\n"
             + "4,1,5   cobblestone\n"
             + "5,1,5   stripped_oak_log   axis=y\n\n"
             + "# --- 第 3 层 (y=2) ---\n"
             + "1,2,1   stripped_oak_log   axis=y\n"
             + "2,2,1   cobblestone\n"
             + "3,2,1   glass_pane   east=true   north=false   south=false   waterlogged=false   west=true\n"
             + "4,2,1   cobblestone\n"
             + "5,2,1   stripped_oak_log   axis=y\n"
             + "0,2,2   wall_torch   facing=west\n"
             + "1,2,2   cobblestone\n"
             + "5,2,2   cobblestone\n"
             + "1,2,3   oak_door   facing=east   half=upper   hinge=right   open=false   powered=false\n"
             + "5,2,3   glass_pane   east=false   north=true   south=true   waterlogged=false   west=false\n"
             + "0,2,4   wall_torch   facing=west\n"
             + "1,2,4   cobblestone\n"
             + "5,2,4   cobblestone\n"
             + "1,2,5   stripped_oak_log   axis=y\n"
             + "2,2,5   cobblestone\n"
             + "3,2,5   glass_pane   east=true   north=false   south=false   waterlogged=false   west=true\n"
             + "4,2,5   cobblestone\n"
             + "5,2,5   stripped_oak_log   axis=y\n\n"
             + "# --- 第 4 层 (y=3) ---\n"
             + "1,3,1   stripped_oak_log   axis=y\n"
             + "2,3,1   cobblestone\n"
             + "3,3,1   cobblestone\n"
             + "4,3,1   cobblestone\n"
             + "5,3,1   stripped_oak_log   axis=y\n"
             + "1,3,2   cobblestone\n"
             + "5,3,2   cobblestone\n"
             + "1,3,3   cobblestone\n"
             + "4,3,3   wall_torch   facing=west\n"
             + "5,3,3   cobblestone\n"
             + "1,3,4   cobblestone\n"
             + "5,3,4   cobblestone\n"
             + "1,3,5   stripped_oak_log   axis=y\n"
             + "2,3,5   cobblestone\n"
             + "3,3,5   cobblestone\n"
             + "4,3,5   cobblestone\n"
             + "5,3,5   stripped_oak_log   axis=y\n\n"
             + "# --- 第 5 层 (y=4) 屋顶下层（四面坡，注意各面facing不同）---\n"
             + "0,4,0   oak_stairs   facing=south   half=bottom   shape=outer_left   waterlogged=false\n"
             + "1,4,0   oak_stairs   facing=south   half=bottom   shape=straight   waterlogged=false\n"
             + "2,4,0   oak_stairs   facing=south   half=bottom   shape=straight   waterlogged=false\n"
             + "3,4,0   oak_stairs   facing=south   half=bottom   shape=straight   waterlogged=false\n"
             + "4,4,0   oak_stairs   facing=south   half=bottom   shape=straight   waterlogged=false\n"
             + "5,4,0   oak_stairs   facing=south   half=bottom   shape=straight   waterlogged=false\n"
             + "6,4,0   oak_stairs   facing=west   half=bottom   shape=outer_left   waterlogged=false\n"
             + "0,4,1   oak_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "1,4,1   oak_planks\n"
             + "2,4,1   oak_planks\n"
             + "3,4,1   oak_planks\n"
             + "4,4,1   oak_planks\n"
             + "5,4,1   oak_planks\n"
             + "6,4,1   oak_stairs   facing=west   half=bottom   shape=straight   waterlogged=false\n"
             + "0,4,2   oak_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "1,4,2   oak_planks\n"
             + "5,4,2   oak_planks\n"
             + "6,4,2   oak_stairs   facing=west   half=bottom   shape=straight   waterlogged=false\n"
             + "0,4,3   oak_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "1,4,3   oak_planks\n"
             + "5,4,3   oak_planks\n"
             + "6,4,3   oak_stairs   facing=west   half=bottom   shape=straight   waterlogged=false\n"
             + "0,4,4   oak_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "1,4,4   oak_planks\n"
             + "5,4,4   oak_planks\n"
             + "6,4,4   oak_stairs   facing=west   half=bottom   shape=straight   waterlogged=false\n"
             + "0,4,5   oak_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "1,4,5   oak_planks\n"
             + "2,4,5   oak_planks\n"
             + "3,4,5   oak_planks\n"
             + "4,4,5   oak_planks\n"
             + "5,4,5   oak_planks\n"
             + "6,4,5   oak_stairs   facing=west   half=bottom   shape=straight   waterlogged=false\n"
             + "0,4,6   oak_stairs   facing=east   half=bottom   shape=outer_left   waterlogged=false\n"
             + "1,4,6   oak_stairs   facing=north   half=bottom   shape=straight   waterlogged=false\n"
             + "2,4,6   oak_stairs   facing=north   half=bottom   shape=straight   waterlogged=false\n"
             + "3,4,6   oak_stairs   facing=north   half=bottom   shape=straight   waterlogged=false\n"
             + "4,4,6   oak_stairs   facing=north   half=bottom   shape=straight   waterlogged=false\n"
             + "5,4,6   oak_stairs   facing=north   half=bottom   shape=straight   waterlogged=false\n"
             + "6,4,6   oak_stairs   facing=north   half=bottom   shape=outer_left   waterlogged=false\n\n"
             + "# --- 第 6 层 (y=5) 屋顶中层 ---\n"
             + "1,5,1   oak_stairs   facing=east   half=bottom   shape=outer_right   waterlogged=false\n"
             + "2,5,1   oak_stairs   facing=south   half=bottom   shape=straight   waterlogged=false\n"
             + "3,5,1   oak_stairs   facing=south   half=bottom   shape=straight   waterlogged=false\n"
             + "4,5,1   oak_stairs   facing=south   half=bottom   shape=straight   waterlogged=false\n"
             + "5,5,1   oak_stairs   facing=south   half=bottom   shape=outer_right   waterlogged=false\n"
             + "1,5,2   oak_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "2,5,2   oak_planks\n"
             + "3,5,2   oak_planks\n"
             + "4,5,2   oak_planks\n"
             + "5,5,2   oak_stairs   facing=west   half=bottom   shape=straight   waterlogged=false\n"
             + "1,5,3   oak_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "2,5,3   oak_planks\n"
             + "4,5,3   oak_planks\n"
             + "5,5,3   oak_stairs   facing=west   half=bottom   shape=straight   waterlogged=false\n"
             + "1,5,4   oak_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "2,5,4   oak_planks\n"
             + "3,5,4   oak_planks\n"
             + "4,5,4   oak_planks\n"
             + "5,5,4   oak_stairs   facing=west   half=bottom   shape=straight   waterlogged=false\n"
             + "1,5,5   oak_stairs   facing=east   half=bottom   shape=outer_left   waterlogged=false\n"
             + "2,5,5   oak_stairs   facing=north   half=bottom   shape=straight   waterlogged=false\n"
             + "3,5,5   oak_stairs   facing=north   half=bottom   shape=straight   waterlogged=false\n"
             + "4,5,5   oak_stairs   facing=north   half=bottom   shape=straight   waterlogged=false\n"
             + "5,5,5   oak_stairs   facing=north   half=bottom   shape=outer_left   waterlogged=false\n\n"
             + "# --- 第 7 层 (y=6) 屋顶顶层 ---\n"
             + "2,6,2   oak_stairs   facing=east   half=bottom   shape=outer_right   waterlogged=false\n"
             + "3,6,2   oak_stairs   facing=south   half=bottom   shape=straight   waterlogged=false\n"
             + "4,6,2   oak_stairs   facing=south   half=bottom   shape=outer_right   waterlogged=false\n"
             + "2,6,3   oak_stairs   facing=east   half=bottom   shape=straight   waterlogged=false\n"
             + "3,6,3   oak_planks\n"
             + "4,6,3   oak_stairs   facing=west   half=bottom   shape=straight   waterlogged=false\n"
             + "2,6,4   oak_stairs   facing=east   half=bottom   shape=outer_left   waterlogged=false\n"
             + "3,6,4   oak_stairs   facing=north   half=bottom   shape=straight   waterlogged=false\n"
             + "4,6,4   oak_stairs   facing=west   half=bottom   shape=outer_right   waterlogged=false\n"
             + "[/BLUEPRINT]\n\n"
             + "========== 建筑设计规则（重要）==========\n\n"
             + "【墙壁高度】\n"
             + "- 每层室内净高必须至少 3 格方块（门占2格，门上方至少1格墙壁）\n"
             + "- 正确的两层楼结构示例：y=0 地板，y=1~3 一层墙壁（3格高），y=4 天花板/二层地板，y=5~7 二层墙壁，y=8 屋顶基座\n"
             + "- 错误：墙壁只有1格高（玩家身高1.8格，根本无法站立）\n\n"
             + "【屋顶楼梯朝向】\n"
             + "- 楼梯方块的 facing 表示楼梯上升的方向（即楼梯高侧所朝的方向）\n"
             + "- 核心原则：屋顶楼梯应从屋檐向屋脊方向上升，facing 指向屋脊\n"
             + "- 绝对不能所有屋顶楼梯都用同一个 facing！那样看起来是一堆倒向同一方向的台阶\n\n"
             + "【室内楼梯（多层建筑必须有）】\n"
             + "- 两层或多层建筑必须包含室内楼梯连接各楼层\n"
             + "- 室内楼梯的建造方式：每上升1格(y+1)，同时沿水平方向前进1格(x+1 或 z+1)\n"
             + "- 楼梯方块的 facing 应朝向上升方向（例如向南上升则 facing=south）\n"
             + "- 楼梯上方需要留出至少2格空间（头部空间），必要时挖掉天花板方块\n"
             + "- 楼梯位置建议放在建筑内侧或角落，不要占据主要生活空间\n\n"
             + "【门的正确放置】\n"
             + "- 门占2格高：下半部分 half=lower 和上半部分 half=upper 必须成对出现\n"
             + "- lower 在 y=N，upper 必须在 y=N+1，相同的 x,z 坐标\n"
             + "- 两者的 facing、hinge、open 属性必须一致\n\n"
             + "========== 单条指令（简单操作）==========\n\n"
             + "对于简单操作（放单个方块、填充简单区域、给物品等），使用 [ACTION]...[/ACTION] 标签：\n\n"
             + "1. 放置方块:\n"
             + "[ACTION]{\"type\":\"place_block\",\"block\":\"方块ID\",\"forward\":前方距离,\"right\":右方距离,\"up\":上方距离}[/ACTION]\n\n"
             + "2. 批量填充方块:\n"
             + "[ACTION]{\"type\":\"fill_blocks\",\"block\":\"方块ID\",\"forward_from\":起始前方,\"forward_to\":结束前方,\"right_from\":起始右方,\"right_to\":结束右方,\"up_from\":起始上方,\"up_to\":结束上方}[/ACTION]\n"
             + "建议单次不超过约 10000 个方块，范围过大请拆分为多次调用，避免服务器卡顿。\n\n"
             + "3. 清除区域:\n"
             + "[ACTION]{\"type\":\"clear_area\",\"forward_from\":起始前方,\"forward_to\":结束前方,\"right_from\":起始右方,\"right_to\":结束右方,\"up_from\":起始上方,\"up_to\":结束上方}[/ACTION]\n"
             + "建议单次不超过约 10000 个方块，范围过大请拆分为多次调用，避免服务器卡顿。\n\n"
             + "4. 给予物品:\n"
             + "[ACTION]{\"type\":\"give_item\",\"item\":\"物品ID\",\"count\":数量}[/ACTION]\n\n"
             + "5. 设置时间:\n"
             + "[ACTION]{\"type\":\"set_time\",\"value\":\"day/noon/night/midnight/sunrise/sunset 或数字\"}[/ACTION]\n\n"
             + "6. 设置天气:\n"
             + "[ACTION]{\"type\":\"set_weather\",\"value\":\"clear/rain/thunder\"}[/ACTION]\n\n"
             + "7. 生成实体:\n"
             + "[ACTION]{\"type\":\"summon\",\"entity\":\"实体ID\",\"forward\":前方距离,\"right\":右方距离,\"up\":上方距离,\"count\":数量}[/ACTION]\n\n"
             + "8. 查询玩家位置:\n"
             + "[ACTION]{\"type\":\"find_player\",\"player\":\"玩家名\"}[/ACTION]\n"
             + "说明: player 为要查询的玩家名；省略 player 或填自己的名字则返回当前玩家的位置。\n"
             + "返回结果包含该玩家的 x/y/z 坐标和所在维度。\n"
             + "示例:\n"
             + "  [ACTION]{\"type\":\"find_player\",\"player\":\"Steve\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"find_player\"}[/ACTION]  (查询我自己)\n\n"
             + vanillaCommandSection
             + "========== 使用规则 ==========\n\n"
             + "方向说明（仅 [ACTION] 使用）：\n"
             + "- forward: 正数=玩家面朝方向前方，负数=后方\n"
             + "- right: 正数=玩家右手方向，负数=左手方向\n"
             + "- up: 正数=上方，负数=下方\n\n"
             + "蓝图坐标说明（[BLUEPRINT] 使用）：\n"
             + "- x: 东西方向（向东递增），对应玩家位置的东偏移\n"
             + "- y: 上下方向（向上递增），对应玩家位置的高度偏移\n"
             + "- z: 南北方向（向南递增），对应玩家位置的南偏移\n"
             + "- 原点 (0,0,0) 默认对应玩家脚下位置；可用 \"# origin:\" 头自定义原点（见上文蓝图格式规则）\n\n"
             + "选择指令的原则：\n"
             + "- 建造建筑、房屋、结构等多方块建筑 → 使用 [BLUEPRINT] 蓝图格式（推荐）\n"
             + "- 放置单个方块、填充简单区域 → 使用 [ACTION] 指令\n"
             + "- 给物品、传送、设置时间天气、生成实体 → 使用对应的具体 [ACTION] 指令类型\n"
             + vanillaCommandRules
             + "- 如果玩家只是聊天，正常回复即可，不需要加任何标签\n\n"
             + "其他规则：\n"
             + "- 可以一次执行多个操作（多个标签）\n"
             + "- 方块和物品 ID 使用 Minecraft 的英文 ID（不含 minecraft: 前缀）\n"
             + "- fill_blocks 建议单次不超过约 10000 个方块，范围过大请拆分为多次调用\n"
             + "- summon 最多生成 20 个实体\n"
             + "- 蓝图中的方块属性必须是 Minecraft 原版 block state 属性名和值\n\n"
             + "大型结构处理：\n"
             + "- 如果玩家引用了一个已有的结构文件（txt），直接使用文件中的蓝图数据生成 [BLUEPRINT] 即可\n"
             + "- 对于非常大的结构（超过约500个方块），蓝图可能无法在一次回复中输出完整\n"
             + "- 即使蓝图被截断（[/BLUEPRINT] 标签缺失），系统也会自动放置已生成的部分\n"
             + "- 被截断时，玩家可以要求你\"继续\"来生成剩余部分\n"
             + "- 对于特别大的结构，建议先输出说明文字，然后紧接着输出 [BLUEPRINT] 标签，不要在蓝图前写太多文字，以节省 token 空间\n"
             + "- 蓝图中不要写多余的注释，只保留层分隔注释即可，以节省空间\n\n"
             + "联网搜索：\n"
             + "- 当玩家的问题需要最新信息、你不确定答案、或者涉及实时数据时，你可以使用 [SEARCH]搜索关键词[/SEARCH] 标签来联网搜索\n"
             + "- 搜索关键词应该简洁明确，用英文效果更好\n"
             + "- 每次回复最多使用一个 [SEARCH] 标签\n"
             + "- 如果你已经知道答案，就不需要搜索\n"
             + "- 搜索结果会自动提供给你，你再基于搜索结果回答玩家的问题\n\n"
             + "网页抓取：\n"
             + "- 当玩家提供了具体的 URL 链接，或者你需要访问某个特定网页获取详细内容时，使用 [FETCH]网页URL[/FETCH] 标签\n"
             + "- URL 必须是完整的 http:// 或 https:// 开头的地址\n"
             + "- 每次回复最多使用一个 [FETCH] 标签\n"
             + "- 网页内容会自动提供给你，你再基于网页内容回答玩家的问题或执行操作\n"
             + "- 如果玩家要求你参考某个网页来搭建建筑，先用 [FETCH] 获取网页内容，系统会把内容返回给你，然后你再根据内容生成 [BLUEPRINT] 蓝图\n"
             + "- [FETCH] 和 [SEARCH] 不要在同一条回复中同时使用\n\n"
             + "地形查询（读取世界现有方块）：\n"
             + "- 当你需要了解某块区域的现有地形、地面高度、已有建筑或方块分布时，使用 [QUERY_REGION]...[/QUERY_REGION] 标签查询。\n"
             + "- 系统会扫描该区域并把其中所有非空气方块（含世界绝对坐标 x,y,z、方块 ID、block state 属性、容器物品、告示牌文字）以 MCBLUEPRINT v2 文本返回给你。\n"
             + "- 两种写法：\n"
             + "    1) 绝对坐标：[QUERY_REGION]x1,y1,z1 x2,y2,z2[/QUERY_REGION]\n"
             + "       两组坐标用空格分隔，每组内 x,y,z 用逗号分隔，表示区域的两个对角。\n"
             + "       例：[QUERY_REGION]100,60,-200 120,80,-180[/QUERY_REGION]\n"
             + "    2) 玩家周围：[QUERY_REGION]around 半径[/QUERY_REGION]\n"
             + "       以玩家所在方块为中心，向上下、四周各扩展\"半径\"格的立方体。\n"
             + "       例：[QUERY_REGION]around 16[/QUERY_REGION] 查询以玩家为中心 33x33x33 的区域。\n"
             + "- 单次查询区域体积建议不超过约 30000 个方块（长x宽x高）。范围过大会返回大量文本、"
             + "占用较多上下文，建议把大区域拆分成多个小块，分多次调用 [QUERY_REGION] 逐块查询。\n"
             + "- 建造前若不确定地形（如坡地、已有建筑、水面），先用 [QUERY_REGION] 查一下再决定放置位置和朝向。\n"
             + "- 查询结果里的坐标是世界绝对坐标，可直接用于后续 [ACTION] 绝对坐标操作或 [BLUEPRINT] 的 \"# origin: absolute\"。\n"
             + "- 重要：你通常不知道玩家所处的世界绝对坐标。因此当你还不知道具体坐标时，"
             + "请优先用 [QUERY_REGION]around 半径[/QUERY_REGION] 从玩家周围开始探查——它以玩家为中心，无需你提供坐标。\n"
             + "- 拿到 around 的查询结果后，其中每个方块都带有真实的世界绝对坐标；"
             + "你可以据此推断玩家附近的坐标范围，再用绝对坐标写法 [QUERY_REGION]x1,y1,z1 x2,y2,z2[/QUERY_REGION] 精确查询更远或更大的区域。\n"
             + "- 典型流程：先 around 探周围地形 → 分析 → 决定建造位置 → （必要时再精查目标区域）→ 用绝对坐标生成蓝图/操作。\n"
             + "- 每次回复最多使用一个 [QUERY_REGION] 标签。\n\n"
             + getKnowledgeBaseSection()
             + "建筑入口与地面衔接（重要，避免入口悬空或被埋）：\n"
             + "- 建造有门/主通道的建筑前，务必先用 [QUERY_REGION] 查清目标落点及其四周紧邻位置的真实地面高度（尤其入口朝向那一侧）。\n"
             + "- 地基要贴合真实地面：结构最低层(y对应的那一层)应落在实际地面上，不要整栋悬空，也不要半埋进土里。坡地/落差处可先用 air 削平或用方块补齐地基，使建筑坐落稳固。\n"
             + "- 入口平齐原则：建筑入口（门所在的那一格）的脚下地面，必须与门外紧挨着的那格外部地面处于同一高度，让玩家能平走进出，而不是要往上跳或往下掉。\n"
             + "- 若入口内外存在高度差（例如地基抬高、门槛比外部地面高，或建在坡上），必须用楼梯(stairs)或台阶(slab)从门口向外逐级下降，把入口和外部地面顺畅连接起来，形成可行走的台阶或坡道。\n"
             + "- 楼梯朝向要正确：踏面朝向应指向下坡方向（即从门口走出去、往下走的方向），facing 设为玩家走下台阶时面朝的方向，确保视觉与实际可走性一致（可参考上文的楼梯 facing/half/shape 说明）。\n"
             + "- 连接台阶本身也不能悬空：台阶下方若有空隙，要用方块把下方填实，保证每级台阶都踩得住。\n"
             + "- 小结：先查地面高度 → 地基贴合地面 → 入口与外部地面平齐 → 有落差就用楼梯/台阶向外衔接并填实下方，做到\"从外面能一步步顺畅走进门\"。\n"
             + getForceMultiToolInstruction()
             + getLanguageInstruction();
    }

    /**
     * 生成知识库查询工具（[KNOWLEDGE]）的说明段落，含知识库目录（标题/分类/关键词/摘要，不含正文）。
     * 与联网搜索、网页抓取、地形查询平级，AI 自行判断优先查知识库还是联网搜索。
     * 知识库功能关闭或当前语言下知识库为空时返回空串，不影响正常提示词。
     */
    private static String getKnowledgeBaseSection() {
        if (!HelloWorldMod.getConfig().isRagEnabled()) {
            return "";
        }
        String directory = new KnowledgeBase().buildDirectoryText();
        if (directory == null || directory.isBlank()) {
            return "";
        }
        if (I18n.isEnglish()) {
            return "Knowledge base lookup:\n"
                 + "- Below is the directory of the built-in Minecraft knowledge base (title, category, keywords, summary only — no full text yet).\n"
                 + "- When you need accurate details on blocks, mobs, commands, or game mechanics, request the full text of one or more documents "
                 + "by name using [KNOWLEDGE]doc_name_a,doc_name_b[/KNOWLEDGE] (comma-separated document names, use the exact name shown before the colon below).\n"
                 + "- The full text will be provided to you automatically; then answer the player or continue building based on it.\n"
                 + "- You may use [KNOWLEDGE] and [SEARCH]/[FETCH] in the same reply if needed, but at most one [KNOWLEDGE] tag per reply.\n"
                 + "- Prefer the knowledge base over web search when the topic is covered here — it is curated and reviewed for this Minecraft version.\n\n"
                 + "Knowledge base directory:\n" + directory + "\n";
        }
        return "知识库查询：\n"
             + "- 以下是内置 Minecraft 知识库的目录（仅含标题、分类、关键词、摘要，不含正文）。\n"
             + "- 当你需要准确了解方块、生物、命令或游戏机制的细节时，使用 [KNOWLEDGE]文档名A,文档名B[/KNOWLEDGE] 标签点名请求一篇或多篇文档的正文"
             + "（多个文档名用英文逗号分隔，文档名请使用下面目录中冒号前的准确名称）。\n"
             + "- 系统会自动把正文提供给你，然后你再基于正文内容回答玩家问题或继续建造。\n"
             + "- 如有需要，[KNOWLEDGE] 可以和 [SEARCH]/[FETCH] 出现在同一条回复里，但每次回复最多使用一个 [KNOWLEDGE] 标签。\n"
             + "- 知识库已覆盖的主题优先查知识库而不是联网搜索——它是为当前 Minecraft 版本整理校对过的资料。\n\n"
             + "知识库目录：\n" + directory + "\n";
    }

    /**
     * 调试用：当强制多轮工具调用开关开启时，追加"至少调用 2 次工具"的指令。
     * 关闭时返回空串，不影响正常提示词。
     */
    private static String getForceMultiToolInstruction() {
        if (!forceMultiToolTesting) {
            return "";
        }
        if (I18n.isEnglish()) {
            return "\n========== DEBUG: FORCE MULTI-TOOL ==========\n\n"
                 + "TESTING MODE: For this session you MUST call tools at least TWICE before giving your final answer. "
                 + "A tool call means emitting a [SEARCH], [FETCH], [ACTION], or [BLUEPRINT] tag. "
                 + "Split the task into multiple steps and use tools across at least two separate replies "
                 + "(e.g. search first, then act; or place part of a build, then continue). "
                 + "Do not finish with a plain-text-only answer until you have used tools at least twice.\n";
        }
        return "\n========== 调试：强制多轮工具 ==========\n\n"
             + "测试模式：本次对话中，你必须在给出最终答复前至少调用 2 次工具。"
             + "调用工具指的是输出 [SEARCH]、[FETCH]、[ACTION] 或 [BLUEPRINT] 标签。"
             + "请把任务拆成多个步骤，在至少两次不同的回复里分别使用工具"
             + "（例如：先搜索、再执行；或先放置一部分建筑、再继续）。"
             + "在你至少调用过 2 次工具之前，不要只用纯文本回复来结束任务。\n";
    }

    /**
     * 根据 mod 的语言设置生成对应的语言指令，告知 AI 用什么语言回复。
     */
    private static String getLanguageInstruction() {
        if (I18n.isEnglish()) {
            return "\n========== LANGUAGE ==========\n\n"
                 + "IMPORTANT: You MUST reply in English. The user has set the mod language to English.\n"
                 + "All your conversational text must be in English. Technical tags like [BLUEPRINT], [ACTION], [SEARCH], [FETCH] remain unchanged.\n";
        } else {
            return "\n========== 语言 ==========\n\n"
                 + "请使用中文回复玩家。所有对话文字使用中文。技术标签如 [BLUEPRINT]、[ACTION]、[SEARCH]、[FETCH] 保持不变。\n";
        }
    }

    // ================= 地形查询工具 [QUERY_REGION] =================

    /**
     * 判断 AI 回复中是否请求了地形查询工具（[QUERY_REGION]）。
     */
    public static boolean containsQueryRegionTag(String aiResponse) {
        if (aiResponse == null || aiResponse.isEmpty()) return false;
        return QUERY_REGION_PATTERN.matcher(aiResponse).find();
    }

    /**
     * 从 AI 回复中提取第一个 [QUERY_REGION] 标签的内容（去除首尾空白）。
     * 未找到时返回 null。
     */
    public static String extractQueryRegionSpec(String aiResponse) {
        if (aiResponse == null) return null;
        Matcher m = QUERY_REGION_PATTERN.matcher(aiResponse);
        if (m.find()) {
            String spec = m.group(1).trim();
            return spec.isEmpty() ? null : spec;
        }
        return null;
    }

    /**
     * 执行地形查询：解析 [QUERY_REGION] 标签内容为两个坐标点，扫描该区域并返回 txt 文本。
     *
     * 支持两种写法：
     *   绝对坐标： x1,y1,z1 x2,y2,z2   （两组坐标用空格分隔，每组内用逗号分隔）
     *   相对玩家： around &lt;半径&gt;  （以玩家所在方块为中心，向各方向扩展 半径 格的立方体）
     *
     * 需在服务端主线程调用（会读取世界方块）。
     *
     * @param spec   [QUERY_REGION] 标签内的文本
     * @param player 发起查询的玩家（用于 around 的中心点）
     * @param world  服务端世界
     * @return 扫描得到的 MCBLUEPRINT v2 文本，或以 "ERROR:" 开头的错误提示（供回喂给 AI）
     */
    public static String executeQueryRegion(String spec, ServerPlayerEntity player, ServerWorld world) {
        if (spec == null || spec.isBlank()) {
            return "ERROR: [QUERY_REGION] 内容为空。用法：绝对坐标 \"x1,y1,z1 x2,y2,z2\"，或相对玩家 \"around <半径>\"。";
        }

        BlockPos[] corners = parseRegionSpec(spec, player);
        if (corners == null) {
            return "ERROR: 无法解析查询区域 \"" + spec + "\"。用法：绝对坐标 \"x1,y1,z1 x2,y2,z2\"，或相对玩家 \"around <半径>\"。";
        }

        return com.example.helloworld.selection.ServerSelectionExporter.scanToText(world, corners[0], corners[1]);
    }

    /**
     * 解析 [QUERY_REGION] 标签内容为两个角坐标。无法解析时返回 null。
     * 包级可见，便于单元测试。
     */
    static BlockPos[] parseRegionSpec(String spec, ServerPlayerEntity player) {
        if (spec == null) return null;
        String s = spec.trim();

        // around <半径>：以玩家方块为中心的立方体
        Matcher around = Pattern.compile("(?i)^around\\s+(\\d+)$").matcher(s);
        if (around.find()) {
            if (player == null) return null;
            int r = Integer.parseInt(around.group(1));
            BlockPos c = player.getBlockPos();
            BlockPos p1 = new BlockPos(c.getX() - r, c.getY() - r, c.getZ() - r);
            BlockPos p2 = new BlockPos(c.getX() + r, c.getY() + r, c.getZ() + r);
            return new BlockPos[]{p1, p2};
        }

        // 绝对坐标：x1,y1,z1 x2,y2,z2  （允许多个空白分隔两组）
        String[] groups = s.split("\\s+");
        if (groups.length == 2) {
            Integer[] a = parseCoordTriple(groups[0]);
            Integer[] b = parseCoordTriple(groups[1]);
            if (a != null && b != null) {
                return new BlockPos[]{
                        new BlockPos(a[0], a[1], a[2]),
                        new BlockPos(b[0], b[1], b[2])
                };
            }
        }

        return null;
    }

    /** 把 "x,y,z" 解析为整数三元组，失败返回 null。 */
    private static Integer[] parseCoordTriple(String triple) {
        String[] parts = triple.split(",");
        if (parts.length != 3) return null;
        try {
            return new Integer[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
