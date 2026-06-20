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
import net.minecraft.text.Text;
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
    private static final Pattern ACTION_PATTERN = Pattern.compile("\\[ACTION\\](.*?)\\[/ACTION\\]", Pattern.DOTALL);
    private static final Pattern BLUEPRINT_PATTERN = Pattern.compile("\\[BLUEPRINT\\](.*?)\\[/BLUEPRINT\\]", Pattern.DOTALL);
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
        if (player == null) return aiResponse;

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
                String result = executeBlueprint(blueprintText, player, world);
                results.add(result);
            } catch (Exception e) {
                LOGGER.error("执行蓝图放置失败", e);
                results.add("§c蓝图放置失败: " + e.getMessage());
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
                    String result = executeBlueprint(blueprintText, player, world);
                    results.add(result);
                    results.add("§e注意: AI 输出被截断，蓝图可能不完整。可以尝试让 AI 继续生成剩余部分。");
                } catch (Exception e) {
                    LOGGER.error("执行截断蓝图放置失败", e);
                    results.add("§c截断蓝图放置失败: " + e.getMessage());
                }
            }
        }

        // 3. 处理 [ACTION]...[/ACTION] 单条指令
        Matcher matcher = ACTION_PATTERN.matcher(aiResponse);
        while (matcher.find()) {
            String json = matcher.group(1).trim();
            try {
                String result = executeAction(json, player, world);
                results.add(result);
            } catch (Exception e) {
                LOGGER.error("执行 AI 指令失败: {}", json, e);
                results.add("§c指令执行失败: " + e.getMessage());
            }
        }

        // 移除标签，保留纯文本
        String cleanResponse = BLUEPRINT_PATTERN.matcher(aiResponse).replaceAll("").trim();
        cleanResponse = ACTION_PATTERN.matcher(cleanResponse).replaceAll("").trim();
        if (blueprintTruncated) {
            // 移除未闭合的 [BLUEPRINT] 及其后面所有内容
            cleanResponse = BLUEPRINT_UNCLOSED_PATTERN.matcher(cleanResponse).replaceAll("").trim();
        }

        // 如果有执行结果，附加到回复末尾
        if (!results.isEmpty()) {
            StringBuilder sb = new StringBuilder(cleanResponse);
            if (!cleanResponse.isEmpty()) sb.append("\n");
            sb.append("§e--- 指令执行结果 ---");
            for (String r : results) {
                sb.append("\n").append(r);
            }
            return sb.toString();
        }

        return cleanResponse;
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
     * 执行蓝图放置：解析 V2 格式文本，使用 BlueprintBuilder 在玩家位置建造。
     */
    private static String executeBlueprint(String blueprintText, ServerPlayerEntity player, ServerWorld world) {
        // 确保文本以 V2 头部开始，如果 AI 没写头部则自动补上
        String text = blueprintText.stripLeading();
        if (!text.startsWith("# MCBLUEPRINT v2") && !text.startsWith("#MCBLUEPRINT v2")) {
            text = "# MCBLUEPRINT v2\n" + text;
        }

        BlueprintData data = BlueprintParser.parse(text);
        if (data == null) {
            return "§c蓝图解析失败";
        }

        int count = BlueprintBuilder.build(data, player, world);
        BlockPos origin = player.getBlockPos();

        // 自动保存蓝图为 txt 文件到 txts/ 文件夹
        String savedPath = saveBlueprintToTxt(text, data.getName());
        String saveMsg = savedPath != null ? " §7(已保存: " + savedPath + ")" : "";

        return "§a蓝图 '" + data.getName() + "' 放置完成! 共 " + count + " 个方块 (原点: "
                + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")" + saveMsg;
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

            // 清理文件名：移除非法字符，用下划线替代空格
            String safeName = name.replaceAll("[^a-zA-Z0-9_\\-\\u4e00-\\u9fff]", "_")
                    .replaceAll("_+", "_")
                    .replaceAll("^_|_$", "");
            if (safeName.isEmpty()) {
                safeName = "blueprint";
            }

            // 如果文件已存在，追加数字后缀
            Path targetFile = txtsDir.resolve(safeName + ".txt");
            int counter = 1;
            while (Files.exists(targetFile)) {
                targetFile = txtsDir.resolve(safeName + "_" + counter + ".txt");
                counter++;
            }

            Files.writeString(targetFile, blueprintText);
            LOGGER.info("蓝图已保存到: {}", targetFile);
            return targetFile.toString();
        } catch (IOException e) {
            LOGGER.error("保存蓝图 txt 文件失败: {}", name, e);
            return null;
        }
    }

    private static String executeAction(String json, ServerPlayerEntity player, ServerWorld world) {
        // 简易 JSON 解析（避免引入额外依赖）
        String type = extractJsonString(json, "type");
        if (type == null) return "§c未知指令类型";

        return switch (type) {
            case "place_block" -> executePlaceBlock(json, player, world);
            case "fill_blocks" -> executeFillBlocks(json, player, world);
            case "give_item" -> executeGiveItem(json, player);
            case "set_time" -> executeSetTime(json, world);
            case "set_weather" -> executeSetWeather(json, world);
            case "teleport" -> executeTeleport(json, player);
            case "summon" -> executeSummon(json, player, world);
            case "clear_area" -> executeClearArea(json, player, world);
            case "execute_command" -> executeMinecraftCommand(json, player);
            default -> "§c未知指令类型: " + type;
        };
    }

    // ========== 放置单个方块 ==========
    private static String executePlaceBlock(String json, ServerPlayerEntity player, ServerWorld world) {
        String blockName = extractJsonString(json, "block");
        if (blockName == null) return "§c缺少 block 参数";

        Block block = getBlock(blockName);
        if (block == null) return "§c未知方块: " + blockName;

        BlockPos pos = calculateRelativePos(json, player);
        world.setBlockState(pos, block.getDefaultState());
        return "§a已放置 " + blockName + " 在 " + formatPos(pos);
    }

    // ========== 批量填充方块 ==========
    private static String executeFillBlocks(String json, ServerPlayerEntity player, ServerWorld world) {
        String blockName = extractJsonString(json, "block");
        if (blockName == null) return "§c缺少 block 参数";

        Block block = getBlock(blockName);
        if (block == null) return "§c未知方块: " + blockName;

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

        // 安全限制：最多 10000 个方块
        int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 10000) return "§c填充范围过大 (" + volume + " 方块)，最多 10000";

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
        return "§a已填充 " + count + " 个 " + blockName;
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

        int volume = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > 10000) return "§c清除范围过大 (" + volume + " 方块)，最多 10000";

        int count = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
                    count++;
                }
            }
        }
        return "§a已清除 " + count + " 个方块";
    }

    // ========== 给予物品 ==========
    private static String executeGiveItem(String json, ServerPlayerEntity player) {
        String itemName = extractJsonString(json, "item");
        if (itemName == null) return "§c缺少 item 参数";

        int count = extractJsonInt(json, "count", 1);
        count = Math.max(1, Math.min(count, 64));

        Identifier id = new Identifier("minecraft", itemName);
        Optional<Item> itemOpt = Registries.ITEM.getOrEmpty(id);
        if (itemOpt.isEmpty()) return "§c未知物品: " + itemName;

        ItemStack stack = new ItemStack(itemOpt.get(), count);
        player.getInventory().insertStack(stack);
        return "§a已给予 " + count + " 个 " + itemName;
    }

    // ========== 设置时间 ==========
    private static String executeSetTime(String json, ServerWorld world) {
        String timeStr = extractJsonString(json, "value");
        if (timeStr == null) return "§c缺少 value 参数";

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

        if (time < 0) return "§c无效的时间值: " + timeStr;
        world.setTimeOfDay(time);
        return "§a已设置时间为 " + timeStr + " (" + time + ")";
    }

    // ========== 设置天气 ==========
    private static String executeSetWeather(String json, ServerWorld world) {
        String weather = extractJsonString(json, "value");
        if (weather == null) return "§c缺少 value 参数";

        int duration = 6000; // 默认 5 分钟
        switch (weather.toLowerCase()) {
            case "clear" -> {
                world.setWeather(duration, 0, false, false);
                return "§a已设置天气为晴天";
            }
            case "rain" -> {
                world.setWeather(0, duration, true, false);
                return "§a已设置天气为下雨";
            }
            case "thunder" -> {
                world.setWeather(0, duration, true, true);
                return "§a已设置天气为雷暴";
            }
            default -> { return "§c未知天气: " + weather + " (可选: clear/rain/thunder)"; }
        }
    }

    // ========== 传送 ==========
    private static String executeTeleport(String json, ServerPlayerEntity player) {
        // 支持绝对坐标或相对坐标
        int absX = extractJsonInt(json, "x", Integer.MIN_VALUE);
        int absY = extractJsonInt(json, "y", Integer.MIN_VALUE);
        int absZ = extractJsonInt(json, "z", Integer.MIN_VALUE);

        if (absX != Integer.MIN_VALUE && absY != Integer.MIN_VALUE && absZ != Integer.MIN_VALUE) {
            player.teleport(absX + 0.5, absY, absZ + 0.5);
            return "§a已传送到 " + absX + ", " + absY + ", " + absZ;
        }

        // 相对坐标
        int forward = extractJsonInt(json, "forward", 0);
        int right = extractJsonInt(json, "right", 0);
        int up = extractJsonInt(json, "up", 0);
        BlockPos pos = calculatePos(player, forward, right, up);
        player.teleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return "§a已传送到 " + formatPos(pos);
    }

    // ========== 生成实体 ==========
    private static String executeSummon(String json, ServerPlayerEntity player, ServerWorld world) {
        String entityName = extractJsonString(json, "entity");
        if (entityName == null) return "§c缺少 entity 参数";

        int count = extractJsonInt(json, "count", 1);
        count = Math.max(1, Math.min(count, 20));

        Identifier id = new Identifier("minecraft", entityName);
        Optional<EntityType<?>> entityTypeOpt = Registries.ENTITY_TYPE.getOrEmpty(id);
        if (entityTypeOpt.isEmpty()) return "§c未知实体: " + entityName;

        BlockPos pos = calculateRelativePos(json, player);
        EntityType<?> entityType = entityTypeOpt.get();

        for (int i = 0; i < count; i++) {
            entityType.spawn(world, pos, net.minecraft.entity.SpawnReason.COMMAND);
        }
        return "§a已在 " + formatPos(pos) + " 生成 " + count + " 个 " + entityName;
    }

    // ========== 执行 Minecraft 原版命令 ==========
    private static final java.util.Set<String> COMMAND_BLACKLIST = java.util.Set.of(
            "stop", "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip",
            "whitelist", "save-all", "save-off", "save-on", "kick"
    );

    private static String executeMinecraftCommand(String json, ServerPlayerEntity player) {
        String command = extractJsonString(json, "command");
        if (command == null || command.isBlank()) return "§c缺少 command 参数";

        // 去掉开头的 /
        if (command.startsWith("/")) command = command.substring(1);

        // 安全检查：黑名单命令
        String rootCommand = command.split("\\s+")[0].toLowerCase();
        if (COMMAND_BLACKLIST.contains(rootCommand)) {
            return "§c安全限制: 不允许执行 /" + rootCommand + " 命令";
        }

        try {
            // 以 OP 权限等级 (level 2) 执行命令
            var source = player.getCommandSource().withLevel(2);
            player.getServer().getCommandManager().executeWithPrefix(source, command);
            return "§a已执行命令: /" + command;
        } catch (Exception e) {
            LOGGER.error("执行命令失败: /{}", command, e);
            return "§c命令执行失败: /" + command + " - " + e.getMessage();
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
     */
    public static String getSystemPrompt() {
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
             + "- 坐标从 0 开始：x=东西(列), y=上下(层), z=南北(行)\n"
             + "- 方块ID 使用 Minecraft 英文 ID（不含 minecraft: 前缀）\n"
             + "- 属性用空格分隔的 key=value 对，如 facing=north waterlogged=false\n"
             + "- 空气方块不需要写（自动跳过）\n"
             + "- 以 # 开头的行是注释，会被忽略\n"
             + "- 建议用 \"# --- 第 N 层 (y=N) ---\" 注释分隔每层，方便阅读\n\n"
             + "常用方块属性示例：\n"
             + "- 楼梯: facing=north/south/east/west  half=bottom/top  shape=straight\n"
             + "- 台阶: type=bottom/top/double  waterlogged=false\n"
             + "- 门: facing=north/south/east/west  half=lower/upper  hinge=left/right  open=false\n"
             + "- 墙上火把: wall_torch facing=north/south/east/west\n"
             + "- 原木: axis=x/y/z\n"
             + "- 栅栏门: facing=north/south/east/west  in_wall=false  open=false\n"
             + "- 床: facing=north/south/east/west  occupied=false  part=head/foot\n"
             + "- 箱子: facing=north/south/east/west  type=single  waterlogged=false\n"
             + "- 按钮: face=floor/wall/ceiling  facing=north/south/east/west\n"
             + "- 活塞: facing=north/south/east/west/up/down\n"
             + "- 观察者: facing=north/south/east/west/up/down\n\n"
             + "========== 红石方块完整参考 ==========\n\n"
             + "【红石粉】redstone_wire\n"
             + "- power=0~15（信号强度，0=无信号，15=最强）\n"
             + "- north/south/east/west=none/side/up（每个方向的连接状态）\n"
             + "- 放置时连接状态会自动计算，通常只需写 redstone_wire 即可\n"
             + "- 信号每经过1格红石粉衰减1级（最远传输15格）\n\n"
             + "【红石火把】redstone_torch / redstone_wall_torch\n"
             + "- redstone_torch: lit=true/false（是否点亮，默认true）\n"
             + "- redstone_wall_torch: facing=north/south/east/west  lit=true/false\n"
             + "- 红石火把提供15级信号，可为上方方块充能\n"
             + "- 附着方块被充能时火把会熄灭（NOT门原理）\n\n"
             + "【红石中继器】repeater\n"
             + "- facing=north/south/east/west（输出方向=facing指向的方向）\n"
             + "- delay=1/2/3/4（延迟档位，1=0.1s, 2=0.2s, 3=0.3s, 4=0.4s）\n"
             + "- locked=true/false（是否被侧面中继器锁定）\n"
             + "- powered=true/false（是否正在输出信号）\n"
             + "- 中继器将信号恢复为15级，只单向传递信号\n"
             + "- facing表示信号输出方向：facing=north表示信号向北输出，从南侧输入\n\n"
             + "【红石比较器】comparator\n"
             + "- facing=north/south/east/west（输出方向=facing指向的方向）\n"
             + "- mode=compare/subtract（比较模式/减法模式）\n"
             + "- powered=true/false（前端火把是否亮起表示有输出）\n"
             + "- compare模式：后方信号>=侧方信号时输出后方信号强度\n"
             + "- subtract模式：输出=后方信号-侧方信号（最低为0）\n"
             + "- 可从容器（箱子、漏斗等）读取物品填充度\n\n"
             + "【活塞/粘性活塞】piston / sticky_piston\n"
             + "- facing=north/south/east/west/up/down（推出方向）\n"
             + "- extended=true/false（是否已伸出）\n"
             + "- 蓝图中通常放置未激活状态：extended=false\n"
             + "- 活塞头（伸出时）：piston_head facing=方向 short=true/false type=normal/sticky\n"
             + "- 活塞最多推动12个方块\n\n"
             + "【侦测器】observer\n"
             + "- facing=north/south/east/west/up/down（检测面朝向）\n"
             + "- powered=true/false\n"
             + "- 检测面前方块状态变化时发出1红石刻脉冲\n"
             + "- 信号从背面输出（与facing相反的方向）\n\n"
             + "【投掷器】dispenser\n"
             + "- facing=north/south/east/west/up/down（发射方向）\n"
             + "- triggered=true/false\n\n"
             + "【发射器】dropper\n"
             + "- facing=north/south/east/west/up/down（输出方向）\n"
             + "- triggered=true/false\n\n"
             + "【漏斗】hopper\n"
             + "- facing=north/south/east/west/down（输出方向，不能朝上）\n"
             + "- enabled=true/false（true=正常工作，false=被红石锁定）\n\n"
             + "【红石灯】redstone_lamp\n"
             + "- lit=true/false（蓝图中放置为lit=false，通电后自动亮）\n\n"
             + "【TNT】tnt\n"
             + "- unstable=true/false（不稳定状态，通常不用设置）\n\n"
             + "【拉杆】lever\n"
             + "- face=floor/wall/ceiling（安装面）\n"
             + "- facing=north/south/east/west（朝向，face=wall时有效）\n"
             + "- powered=true/false（是否被拉下）\n\n"
             + "【压力板】stone_pressure_plate / oak_pressure_plate 等\n"
             + "- powered=true/false\n\n"
             + "【绊线钩】tripwire_hook\n"
             + "- facing=north/south/east/west（面朝方向）\n"
             + "- attached=true/false（是否连接绊线）\n"
             + "- powered=true/false\n\n"
             + "【绊线】tripwire\n"
             + "- north/south/east/west=true/false  attached=true/false  powered=true/false  disarmed=true/false\n\n"
             + "【活板门】oak_trapdoor / iron_trapdoor 等\n"
             + "- facing=north/south/east/west  half=top/bottom  open=true/false\n"
             + "- powered=true/false  waterlogged=true/false\n\n"
             + "【红石方块】redstone_block\n"
             + "- 无属性，始终输出15级信号\n\n"
             + "【标靶】target\n"
             + "- power=0~15（被弹射物击中时短暂产生信号）\n\n"
             + "【音符盒】note_block\n"
             + "- instrument=harp/basedrum/snare/hat/bass/...  note=0~24  powered=true/false\n\n"
             + "【阳光传感器】daylight_detector\n"
             + "- inverted=true/false  power=0~15\n\n"
             + "【铁轨类】rail / powered_rail / detector_rail / activator_rail\n"
             + "- rail: shape=north_south/east_west/ascending_north/ascending_south/ascending_east/ascending_west/north_east/north_west/south_east/south_west  waterlogged=false\n"
             + "- powered_rail: shape=north_south/east_west/ascending_*  powered=true/false  waterlogged=false\n"
             + "- detector_rail: shape=north_south/east_west/ascending_*  powered=true/false  waterlogged=false\n"
             + "- activator_rail: shape=north_south/east_west/ascending_*  powered=true/false  waterlogged=false\n\n"
             + "========== 红石放置规则与技巧 ==========\n\n"
             + "方块充能规则：\n"
             + "- 强充能：红石火把可强充能其上方方块；中继器/比较器可强充能其输出方向的方块\n"
             + "- 弱充能：红石粉可弱充能其下方方块\n"
             + "- 强充能方块可给相邻红石粉供电，弱充能方块不能\n"
             + "- 透明方块（玻璃、台阶等）不能被充能\n\n"
             + "信号传递方向：\n"
             + "- 红石粉：水平传播，可上下1格高差连接（需实体方块支撑）\n"
             + "- 中继器：单向传递，facing指向的方向为输出方向\n"
             + "- 比较器：单向传递，facing指向的方向为输出方向，侧面可输入\n"
             + "- 红石火把：向上方方块和周围红石粉供电（不向下）\n\n"
             + "红石时序（1红石刻 = 0.1秒 = 2游戏刻）：\n"
             + "- 红石火把翻转：1红石刻延迟\n"
             + "- 中继器：1~4红石刻延迟（对应delay=1~4）\n"
             + "- 比较器：1红石刻延迟\n"
             + "- 活塞伸出：0延迟（瞬时）；缩回：1红石刻\n"
             + "- 侦测器脉冲：2游戏刻（1红石刻）\n\n"
             + "蓝图中红石放置顺序建议：\n"
             + "- 先放实体方块（石头、混凝土等作为底座和支撑）\n"
             + "- 再放红石元件（中继器、比较器、红石火把等）\n"
             + "- 最后放红石粉（会自动连接相邻元件）\n"
             + "- 活塞放置为 extended=false 状态\n"
             + "- 红石灯放置为 lit=false（通电后自动亮起）\n"
             + "- 拉杆/按钮放置为 powered=false（玩家手动激活）\n\n"
             + "常见红石电路模式：\n"
             + "- NOT门：红石火把附着在被输入信号充能的方块上\n"
             + "- AND门：两路信号分别经红石火把取反后汇合到同一方块再取反\n"
             + "- OR门：两路红石粉直接汇合\n"
             + "- 脉冲延长器：多个中继器串联（每个增加1~4红石刻）\n"
             + "- T触发器：粘性活塞推拉红石块交替导通\n"
             + "- 时钟电路：中继器环形连接（最短2中继器=4红石刻周期）\n"
             + "- 单脉冲（边沿检测）：信号经中继器延迟后与原信号做减法\n\n"
             + "复杂红石机器处理规则：\n"
             + "- 以下类型的红石机器结构复杂，不要凭想象建造，必须先用 [SEARCH] 搜索最新设计：\n"
             + "  世界吞噬者(World Eater)、TNT复制机(TNT Duper)、飞行机器(Flying Machine)、\n"
             + "  全自动农场、刷铁机、刷怪塔、自动分类机、活塞门(Piston Door)、\n"
             + "  隐藏楼梯、自动酿造机、炮(TNT Cannon)等\n"
             + "- 搜索时使用英文关键词效果更好，如 \"minecraft world eater schematic 1.20\"\n"
             + "- 红石电路相关搜索优先加 site:minecraft.wiki，如 \"minecraft world eater site:minecraft.wiki\"\n"
             + "- 如果搜索结果不理想，直接用 [FETCH] 抓取 Minecraft Wiki 页面获取准确信息：\n"
             + "  常用页面：https://minecraft.wiki/w/Redstone_mechanics/Circuit（红石电路大全）\n"
             + "            https://minecraft.wiki/w/Tutorial:Advanced_redstone_circuits（进阶电路）\n"
             + "            https://minecraft.wiki/w/Tutorials/Redstone（红石教程）\n"
             + "- 如果搜索结果包含方块坐标列表或 litematic/schematic 数据，根据其转换为蓝图格式\n"
             + "- 如果搜索不到精确的方块级设计，诚实告诉玩家此机器过于复杂无法准确还原，\n"
             + "  建议玩家提供 schematic 文件或参考教程链接（你可以用 [FETCH] 抓取）\n"
             + "- 对于简单红石电路（门灯、暗门、简单时钟等基础逻辑）可以直接建造\n\n"
             + "蓝图示例 — 3x3 小石屋（1层高，带门）：\n"
             + "[BLUEPRINT]\n"
             + "# MCBLUEPRINT v2\n"
             + "# name: small_stone_house\n\n"
             + "## BLOCKS\n\n"
             + "# --- 第 1 层 (y=0) 地板和墙壁 ---\n"
             + "0,0,0   stone_bricks\n"
             + "1,0,0   stone_bricks\n"
             + "2,0,0   stone_bricks\n"
             + "3,0,0   stone_bricks\n"
             + "4,0,0   stone_bricks\n"
             + "0,0,1   stone_bricks\n"
             + "4,0,1   stone_bricks\n"
             + "0,0,2   stone_bricks\n"
             + "2,0,2   oak_door   facing=south   half=lower   hinge=left   open=false\n"
             + "4,0,2   stone_bricks\n"
             + "0,0,3   stone_bricks\n"
             + "4,0,3   stone_bricks\n"
             + "0,0,4   stone_bricks\n"
             + "1,0,4   stone_bricks\n"
             + "2,0,4   stone_bricks\n"
             + "3,0,4   stone_bricks\n"
             + "4,0,4   stone_bricks\n\n"
             + "# --- 第 2 层 (y=1) 上层墙壁 ---\n"
             + "0,1,0   stone_bricks\n"
             + "1,1,0   glass_pane\n"
             + "2,1,0   stone_bricks\n"
             + "3,1,0   glass_pane\n"
             + "4,1,0   stone_bricks\n"
             + "0,1,1   stone_bricks\n"
             + "4,1,1   stone_bricks\n"
             + "0,1,2   stone_bricks\n"
             + "2,1,2   oak_door   facing=south   half=upper   hinge=left   open=false\n"
             + "4,1,2   stone_bricks\n"
             + "0,1,3   stone_bricks\n"
             + "4,1,3   stone_bricks\n"
             + "0,1,4   stone_bricks\n"
             + "1,1,4   glass_pane\n"
             + "2,1,4   stone_bricks\n"
             + "3,1,4   glass_pane\n"
             + "4,1,4   stone_bricks\n\n"
             + "# --- 第 3 层 (y=2) 屋顶 ---\n"
             + "0,2,0   oak_slab   type=bottom\n"
             + "1,2,0   oak_slab   type=bottom\n"
             + "2,2,0   oak_slab   type=bottom\n"
             + "3,2,0   oak_slab   type=bottom\n"
             + "4,2,0   oak_slab   type=bottom\n"
             + "0,2,1   oak_planks\n"
             + "1,2,1   oak_planks\n"
             + "2,2,1   oak_planks\n"
             + "3,2,1   oak_planks\n"
             + "4,2,1   oak_planks\n"
             + "0,2,2   oak_planks\n"
             + "1,2,2   oak_planks\n"
             + "2,2,2   oak_planks\n"
             + "3,2,2   oak_planks\n"
             + "4,2,2   oak_planks\n"
             + "0,2,3   oak_planks\n"
             + "1,2,3   oak_planks\n"
             + "2,2,3   oak_planks\n"
             + "3,2,3   oak_planks\n"
             + "4,2,3   oak_planks\n"
             + "0,2,4   oak_planks\n"
             + "1,2,4   oak_planks\n"
             + "2,2,4   oak_planks\n"
             + "3,2,4   oak_planks\n"
             + "4,2,4   oak_planks\n"
             + "[/BLUEPRINT]\n\n"
             + "========== 单条指令（简单操作）==========\n\n"
             + "对于简单操作（放单个方块、填充简单区域、给物品、传送等），使用 [ACTION]...[/ACTION] 标签：\n\n"
             + "1. 放置方块:\n"
             + "[ACTION]{\"type\":\"place_block\",\"block\":\"方块ID\",\"forward\":前方距离,\"right\":右方距离,\"up\":上方距离}[/ACTION]\n\n"
             + "2. 批量填充方块:\n"
             + "[ACTION]{\"type\":\"fill_blocks\",\"block\":\"方块ID\",\"forward_from\":起始前方,\"forward_to\":结束前方,\"right_from\":起始右方,\"right_to\":结束右方,\"up_from\":起始上方,\"up_to\":结束上方}[/ACTION]\n\n"
             + "3. 清除区域:\n"
             + "[ACTION]{\"type\":\"clear_area\",\"forward_from\":起始前方,\"forward_to\":结束前方,\"right_from\":起始右方,\"right_to\":结束右方,\"up_from\":起始上方,\"up_to\":结束上方}[/ACTION]\n\n"
             + "4. 给予物品:\n"
             + "[ACTION]{\"type\":\"give_item\",\"item\":\"物品ID\",\"count\":数量}[/ACTION]\n\n"
             + "5. 设置时间:\n"
             + "[ACTION]{\"type\":\"set_time\",\"value\":\"day/noon/night/midnight/sunrise/sunset 或数字\"}[/ACTION]\n\n"
             + "6. 设置天气:\n"
             + "[ACTION]{\"type\":\"set_weather\",\"value\":\"clear/rain/thunder\"}[/ACTION]\n\n"
             + "7. 传送 (相对坐标):\n"
             + "[ACTION]{\"type\":\"teleport\",\"forward\":前方距离,\"right\":右方距离,\"up\":上方距离}[/ACTION]\n\n"
             + "8. 传送 (绝对坐标):\n"
             + "[ACTION]{\"type\":\"teleport\",\"x\":X坐标,\"y\":Y坐标,\"z\":Z坐标}[/ACTION]\n\n"
             + "9. 生成实体:\n"
             + "[ACTION]{\"type\":\"summon\",\"entity\":\"实体ID\",\"forward\":前方距离,\"right\":右方距离,\"up\":上方距离,\"count\":数量}[/ACTION]\n\n"
             + "10. 执行任意 Minecraft 命令（万能后备）:\n"
             + "[ACTION]{\"type\":\"execute_command\",\"command\":\"/命令内容\"}[/ACTION]\n"
             + "示例:\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/effect give @s speed 60 2\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/gamemode creative\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/enchant @s sharpness 5\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/kill @e[type=zombie,distance=..30]\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/playsound minecraft:entity.ender_dragon.growl master @a\"}[/ACTION]\n"
             + "  [ACTION]{\"type\":\"execute_command\",\"command\":\"/setblock ~ ~1 ~ minecraft:chest\"}[/ACTION]\n\n"
             + "========== 使用规则 ==========\n\n"
             + "方向说明（仅 [ACTION] 使用）：\n"
             + "- forward: 正数=玩家面朝方向前方，负数=后方\n"
             + "- right: 正数=玩家右手方向，负数=左手方向\n"
             + "- up: 正数=上方，负数=下方\n\n"
             + "蓝图坐标说明（[BLUEPRINT] 使用）：\n"
             + "- x: 东西方向（向东递增），对应玩家位置的东偏移\n"
             + "- y: 上下方向（向上递增），对应玩家位置的高度偏移\n"
             + "- z: 南北方向（向南递增），对应玩家位置的南偏移\n"
             + "- 原点 (0,0,0) 对应玩家脚下位置\n\n"
             + "选择指令的原则：\n"
             + "- 建造建筑、房屋、结构等多方块建筑 → 使用 [BLUEPRINT] 蓝图格式（推荐）\n"
             + "- 放置单个方块、填充简单区域 → 使用 [ACTION] 指令\n"
             + "- 给物品、传送、设置时间天气、生成实体 → 使用对应的具体 [ACTION] 指令类型\n"
             + "- 上述指令无法满足的操作（如 /effect、/enchant、/gamemode、/kill、/scoreboard、/particle、/title、/playsound、/data 等）→ 使用 execute_command\n"
             + "- 优先使用具体的 ACTION 类型，只有它们不支持时才用 execute_command\n"
             + "- execute_command 中的命令格式与 Minecraft 原版命令完全一致，前面加 / 即可\n"
             + "- 如果玩家只是聊天，正常回复即可，不需要加任何标签\n\n"
             + "其他规则：\n"
             + "- 可以一次执行多个操作（多个标签）\n"
             + "- 方块和物品 ID 使用 Minecraft 的英文 ID（不含 minecraft: 前缀）\n"
             + "- fill_blocks 最多填充 10000 个方块\n"
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
             + "- [FETCH] 和 [SEARCH] 不要在同一条回复中同时使用\n"
             + getLanguageInstruction();
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
}
