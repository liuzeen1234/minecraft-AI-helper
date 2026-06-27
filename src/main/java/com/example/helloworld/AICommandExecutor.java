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
             + "========== 红石方块完整参考（基于1.20.4 Minecraft Wiki） ==========\n\n"
             + "以下信息摘录自 https://minecraft.wiki/w/Redstone_circuits 及各方块Wiki页面，适用于Java Edition 1.20.4。\n\n"
             + "━━━━━━ 一、电源与传输类方块（Power & Transmission） ━━━━━━\n\n"
             + "【红石粉】redstone_wire（传输元件兼弱电源）\n"
             + "方块ID: redstone_wire（放置态）/ redstone（物品态）\n"
             + "Block States:\n"
             + "  - power=0~15（当前信号强度，0=无信号，15=满强度）\n"
             + "  - north=none/side/up（北侧连接：none=不连接，side=侧连接可代表向下，up=向上连接）\n"
             + "  - south=none/side/up（南侧连接状态）\n"
             + "  - east=none/side/up（东侧连接状态）\n"
             + "  - west=none/side/up（西侧连接状态）\n"
             + "详细行为（来源：minecraft.wiki/w/Redstone_Dust）：\n"
             + "  - 可放置在：实体方块顶面、荧石、倒置台阶、玻璃、倒置楼梯、漏斗顶面\n"
             + "  - 信号强度每经过1格红石粉衰减1级，最远传输15格\n"
             + "  - 红石粉自动连接相邻的红石元件和电源组件，连接状态由游戏自动计算\n"
             + "  - 无连接邻居时默认呈十字(+)形（Java版可右键切换为点(·)形，点形不向四周供电但充能下方方块）\n"
             + "  - 充能规则：红石粉弱充能其正下方的实体方块（弱充能方块不能为相邻红石粉供电，但能激活相邻机械元件）\n"
             + "  - 红石粉也弱充能其指向方向的方块\n"
             + "  - 高度差连接：可向上连接高1格实体方块上的红石粉，但两者之间方块须为空气/非实体方块；实体方块会切断垂直连接\n"
             + "  - 被活塞推动时直接破坏掉落，粘性活塞无法拉回\n"
             + "  - 放置建议：蓝图中通常只写 redstone_wire，power和连接状态由游戏自动设定\n\n"
             + "【红石火把】redstone_torch / redstone_wall_torch\n"
             + "方块ID: redstone_torch（立式）/ redstone_wall_torch（墙面附着）\n"
             + "Block States:\n"
             + "  - redstone_torch: lit=true/false（默认true=点亮）\n"
             + "  - redstone_wall_torch: facing=north/south/east/west, lit=true/false\n"
             + "    （facing=火把面朝方向，即附着墙面的反方向）\n"
             + "详细行为（来源：minecraft.wiki/w/Redstone_Torch）：\n"
             + "  - 输出恒定15级信号（点亮时）\n"
             + "  - 强充能其正上方的实体方块（该方块可为相邻红石粉供电）\n"
             + "  - 为周围相邻的红石粉/元件提供电力（水平4方向+上方，不向下方供电）\n"
             + "  - NOT门核心：当火把附着的方块被充能时→火把熄灭(lit=false)→输出停止\n"
             + "  - 翻转延迟：1红石刻（0.1秒/2游戏刻）\n"
             + "  - 烧毁保护：100游戏刻内翻转超过8次→短暂烧毁熄灭约60游戏刻（防止超高频振荡）\n"
             + "  - 光照等级：7（点亮时）\n"
             + "  - 可放在方块顶面（立式）或侧面（墙上火把），不可悬空\n\n"
             + "【红石块】redstone_block\n"
             + "方块ID: redstone_block\n"
             + "Block States: 无（无任何可变属性）\n"
             + "详细行为（来源：minecraft.wiki/w/Block_of_Redstone）：\n"
             + "  - 恒定电源：始终向6个相邻方向输出15级信号，不可关闭\n"
             + "  - 可激活相邻的红石粉、铁轨、活塞等所有红石元件\n"
             + "  - 不能被外部信号充能或去激活\n"
             + "  - 可被活塞/粘性活塞推拉（这是它与红石火把的关键区别！）\n"
             + "  - 常见用途：T触发器（粘性活塞推拉红石块交替供电）、飞行机器动力、永久电源\n\n"
             + "【红石中继器】repeater\n"
             + "方块ID: repeater\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west（输出端朝向=facing指向的方向）\n"
             + "  - delay=1/2/3/4（延迟1~4红石刻，即0.1~0.4秒）\n"
             + "  - locked=true/false（被侧面中继器/比较器锁定时=true）\n"
             + "  - powered=true/false（当前是否输出信号）\n"
             + "详细行为（来源：minecraft.wiki/w/Redstone_Repeater）：\n"
             + "  - 单向传递：从背面(facing反方向)输入 → 从正面(facing方向)输出\n"
             + "  - 信号恢复：不论输入强度多少(只要>0)，输出恒定15级满信号\n"
             + "  - 强充能其输出方向的实体方块\n"
             + "  - facing=north → 信号向北输出，从南侧输入\n"
             + "  - 4档延迟：右键循环(1→2→3→4→1)，每档=1红石刻(0.1s)\n"
             + "  - 锁定机制：侧面被另一个激活的中继器/比较器正对时→锁定当前输出状态不变\n"
             + "    锁定时显示小石柱（bedrock bar），解锁后恢复正常响应\n"
             + "  - 信号隔离：侧面信号不会进入（除锁定外），实现单向隔离/二极管功能\n"
             + "  - 只能放在实体方块/荧石/倒置台阶等顶面\n"
             + "  - 常见用途：信号延长(每15格需1个)、时钟电路、脉冲延迟、信号隔离、锁存器\n\n"
             + "【红石比较器】comparator\n"
             + "方块ID: comparator\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west（输出端朝向=facing指向的方向）\n"
             + "  - mode=compare/subtract（比较模式/减法模式，右键切换）\n"
             + "  - powered=true/false（前端火把亮=有输出信号）\n"
             + "详细行为（来源：minecraft.wiki/w/Redstone_Comparator）：\n"
             + "  - 三输入一输出：后方(主输入A)、左侧(B)、右侧(B)、前方(输出facing方向)\n"
             + "  - compare模式（前火把不凹陷）：若 A >= max(左,右) → 输出=A；否则输出=0\n"
             + "  - subtract模式（前火把凹陷/亮起）：输出 = A - max(左,右)，最低为0\n"
             + "  - 延迟：1红石刻(0.1秒)\n"
             + "  - 容器检测：读取后方容器物品填充比例→输出0~15信号\n"
             + "    公式：output = floor(1 + (物品数量总和/容器最大容量) × 14)；空=0，有物品≥1，满=15\n"
             + "  - 可检测容器：箱子、陷阱箱、木桶、潜影盒、漏斗、熔炉、烟熏炉、高炉、酿造台、堆肥桶\n"
             + "  - 特殊检测：唱片机(根据唱片种类输出1~15)、讲台(根据页码比例)、蜂箱(蜂蜜等级0~5)、蛋糕(剩余份数)\n"
             + "  - 强充能输出方向的实体方块\n"
             + "  - 只能放在实体方块顶面\n\n"
             + "━━━━━━ 二、机械类方块（Mechanism Components） ━━━━━━\n\n"
             + "【活塞/粘性活塞】piston / sticky_piston\n"
             + "方块ID: piston / sticky_piston\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west/up/down（活塞头推出方向）\n"
             + "  - extended=true/false（是否已伸出）\n"
             + "活塞头（伸出时自动生成）：piston_head\n"
             + "  - facing=方向  type=normal/sticky  short=true/false\n"
             + "详细行为（来源：minecraft.wiki/w/Piston）：\n"
             + "  - 收到红石信号时伸出活塞头，推动前方方块；信号消失时缩回\n"
             + "  - 最多推动12个方块（超过则不动）\n"
             + "  - 粘性活塞缩回时可拉回1个方块（普通活塞不可拉回）\n"
             + "  - 伸出延迟：0刻（瞬时，同一游戏刻）；缩回延迟：1红石刻\n"
             + "  - 不可推动的方块：黑曜石、基岩、附魔台、末地传送门框架、磨石附着方块(箱子打开时)等\n"
             + "  - 会被破坏的方块（推向时直接消失）：花、火把、红石粉、蛛网等非实体方块\n"
             + "  - 粘液块/蜂蜜块特性：推拉时会粘连相邻方块一起移动（最多12个总计）\n"
             + "  - 粘液块与蜂蜜块互不粘连（可用于复杂飞行机器设计）\n"
             + "  - 激活条件：活塞可被BUD激活（方块更新检测），是很多红石机器的核心\n"
             + "  - 蓝图放置：使用 extended=false（未伸出状态），游戏内通电后自动伸出\n\n"
             + "【侦测器】observer\n"
             + "方块ID: observer\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west/up/down（观察面/检测面朝向）\n"
             + "  - powered=true/false（是否正在输出脉冲）\n"
             + "详细行为（来源：minecraft.wiki/w/Observer）：\n"
             + "  - 检测面（有张'脸'的面=facing方向）前方方块的block state变化\n"
             + "  - 检测到变化时从背面（facing的反方向）输出1红石刻(2游戏刻)的脉冲\n"
             + "  - 输出为15级强信号，可强充能背面方块\n"
             + "  - 可检测的变化：方块放置/破坏、红石信号变化、作物生长、水流变化、活塞伸缩等\n"
             + "  - 两个侦测器面对面会互相触发→形成时钟电路（周期=2红石刻=0.2秒）\n"
             + "  - 可被活塞推动（常用于飞行机器设计中检测移动状态）\n"
             + "  - 不可被红石信号激活（只响应方块更新）\n\n"
             + "【投掷器】dispenser\n"
             + "方块ID: dispenser\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west/up/down（发射口朝向）\n"
             + "  - triggered=true/false（是否正在被激活）\n"
             + "详细行为（来源：minecraft.wiki/w/Dispenser）：\n"
             + "  - 收到红石脉冲(上升沿)时从9格容器中随机选1个物品发射/使用\n"
             + "  - 特殊物品行为：箭/烟花/雪球/喷溅药水→作为弹射物发射\n"
             + "    水桶/岩浆桶→放置流体；空桶→收集流体\n"
             + "    盔甲→给前方实体穿戴；骨粉→催熟作物；剪刀→剪羊毛\n"
             + "    打火石→点燃前方方块；TNT→放置并点燃\n"
             + "    船/矿车→放置实体；烟火→发射烟花火箭\n"
             + "  - 无特殊行为的物品直接作为掉落物弹出\n"
             + "  - facing方向由放置时玩家朝向决定（面朝玩家放置→facing=玩家面对方向的反方向）\n"
             + "  - 需要红石脉冲（上升沿）才能触发，持续信号只触发一次\n\n"
             + "【投射器】dropper\n"
             + "方块ID: dropper\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west/up/down（输出口朝向）\n"
             + "  - triggered=true/false（是否正在被激活）\n"
             + "详细行为（来源：minecraft.wiki/w/Dropper）：\n"
             + "  - 收到红石脉冲时从9格容器中随机选1个物品弹出或传输\n"
             + "  - 与投掷器区别：投射器不发射弹射物，所有物品都以掉落物形式弹出\n"
             + "  - 若facing方向有容器（箱子/漏斗/另一个投射器等）→直接将物品传入该容器\n"
             + "  - 常用于物品传输系统（投射器链）、物品电梯\n"
             + "  - facing方向=玩家放置时面对方向的反方向（与投掷器相同）\n\n"
             + "【漏斗】hopper\n"
             + "方块ID: hopper\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west/down（输出管道方向，注意：不能朝上！）\n"
             + "  - enabled=true/false（true=正常工作，false=被红石锁定停止传输）\n"
             + "详细行为（来源：minecraft.wiki/w/Hopper）：\n"
             + "  - 功能一：收集上方(不论facing方向)掉落物实体和上方容器中的物品\n"
             + "  - 功能二：将自身物品传入facing方向的容器\n"
             + "  - 传输速度：每4红石刻(0.4秒/8游戏刻)传输1个物品\n"
             + "  - 容量：5格物品栏\n"
             + "  - 红石锁定：收到红石信号时enabled=false，停止所有吸入和输出（物品保留在内部）\n"
             + "  - 可以从上方往漏斗中放入物品（即使被锁定也可以通过另一个漏斗向其输入？不对，锁定=完全停止）\n"
             + "  - 锁定时完全停止：不吸入、不输出、不收集上方掉落物\n"
             + "  - 可作为红石粉的放置面（顶部）\n"
             + "  - facing方向在放置时确定（对准哪个容器的面放置，管道就朝向那个容器）\n"
             + "  - 常见用途：自动收集系统、物品分类机、计时器（配合比较器读取填充度）\n\n"
             + "【红石灯】redstone_lamp\n"
             + "方块ID: redstone_lamp\n"
             + "Block States:\n"
             + "  - lit=true/false（是否被点亮）\n"
             + "详细行为（来源：minecraft.wiki/w/Redstone_Lamp）：\n"
             + "  - 收到红石信号时点亮(lit=true)，光照等级15（最大亮度）\n"
             + "  - 信号消失后有2红石刻(0.2秒)延迟才熄灭（利用此特性可做脉冲延长）\n"
             + "  - 是实体方块，可被充能、可作为红石粉放置面\n"
             + "  - 激活条件：红石灯在以下情况被点亮：\n"
             + "    ①直接相邻的红石粉指向它（弱充能即可）\n"
             + "    ②正上方有红石粉（红石粉弱充能正下方方块，红石灯作为实体方块可被弱充能激活）\n"
             + "    ③相邻方块被强充能（如中继器输出端的方块）\n"
             + "    ④直接相邻红石块、红石火把等电源\n"
             + "  - ⚠️ 蓝图放置注意：红石灯不能单纯放在红石线路上方期望被点亮！\n"
             + "    红石粉弱充能的是正下方方块，不是正上方方块。\n"
             + "    正确做法：将红石灯放在红石粉的正下方（与基座同层），\n"
             + "    让上方的红石粉弱充能下方的红石灯使其点亮。\n"
             + "    或者将红石灯直接放在中继器输出端旁边（被强充能的方块相邻）。\n"
             + "  - 蓝图放置：写 redstone_lamp（默认lit=false），通电后自动亮起\n\n"
             + "【TNT】tnt\n"
             + "方块ID: tnt\n"
             + "Block States:\n"
             + "  - unstable=true/false（不稳定状态，被任何方块更新即可点燃，通常为false）\n"
             + "详细行为（来源：minecraft.wiki/w/TNT）：\n"
             + "  - 被红石信号激活时：变成TNT实体（被点燃），40红石刻(4秒)后爆炸\n"
             + "  - 被点燃后会轻微弹起并受重力影响\n"
             + "  - 爆炸威力：4（可破坏大部分方块）\n"
             + "  - 被投掷器激活时同样点燃（投掷器facing方向前方的TNT）\n"
             + "  - 也可被火焰、岩浆、爆炸、燃烧的弹射物点燃\n"
             + "  - 蓝图中一般不设置 unstable=true（除非特殊陷阱设计）\n\n"
             + "━━━━━━ 三、输入/触发类方块（Input/Trigger Components） ━━━━━━\n\n"
             + "【拉杆】lever\n"
             + "方块ID: lever\n"
             + "Block States:\n"
             + "  - face=floor/wall/ceiling（安装面：地面/墙面/天花板）\n"
             + "  - facing=north/south/east/west（朝向，face=wall时表示拉杆面朝方向；face=floor/ceiling时表示拉杆指向）\n"
             + "  - powered=true/false（是否被拉下=输出信号）\n"
             + "详细行为（来源：minecraft.wiki/w/Lever）：\n"
             + "  - 拨动开关：右键切换powered状态，输出持续信号直到再次切换\n"
             + "  - 输出15级信号，可充能附着的方块（强充能）\n"
             + "  - 可安装在任何实体方块的6个面上\n"
             + "  - 蓝图放置：powered=false（默认关闭，玩家手动激活）\n\n"
             + "【按钮】stone_button / oak_button / 各材质_button\n"
             + "方块ID: stone_button, oak_button, spruce_button, birch_button, jungle_button,\n"
             + "         acacia_button, dark_oak_button, mangrove_button, cherry_button,\n"
             + "         bamboo_button, crimson_button, warped_button, polished_blackstone_button\n"
             + "Block States:\n"
             + "  - face=floor/wall/ceiling（安装面）\n"
             + "  - facing=north/south/east/west（按钮面朝方向）\n"
             + "  - powered=true/false（是否被按下）\n"
             + "详细行为（来源：minecraft.wiki/w/Button）：\n"
             + "  - 按下后输出15级脉冲信号：\n"
             + "    石质按钮：10红石刻(1秒)脉冲\n"
             + "    木质按钮：15红石刻(1.5秒)脉冲\n"
             + "  - 强充能附着的方块\n"
             + "  - 木质按钮可被箭矢触发，石质按钮只能手动\n"
             + "  - 可安装在实体方块的6个面上\n"
             + "  - 蓝图放置：powered=false\n\n"
             + "【压力板】各类压力板\n"
             + "方块ID: stone_pressure_plate, oak_pressure_plate, spruce/birch/jungle/acacia/\n"
             + "         dark_oak/mangrove/cherry/bamboo/crimson/warped_pressure_plate,\n"
             + "         polished_blackstone_pressure_plate\n"
             + "         light_weighted_pressure_plate（金质）, heavy_weighted_pressure_plate（铁质）\n"
             + "Block States:\n"
             + "  - 普通压力板: powered=true/false（是否有实体站在上面）\n"
             + "  - 测重压力板: power=0~15（根据上方实体数量输出不同信号强度）\n"
             + "详细行为（来源：minecraft.wiki/w/Pressure_Plate）：\n"
             + "  - 石质：只检测玩家和生物\n"
             + "  - 木质：检测所有实体（包括掉落物、箭矢、矿车等）\n"
             + "  - 测重压力板（金/铁）：根据上方实体数量输出power=0~15\n"
             + "    金质(light)：每1个实体=1信号强度（最大15）\n"
             + "    铁质(heavy)：每10个实体=1信号强度（最大15）\n"
             + "  - 强充能其下方方块\n"
             + "  - 激活后有5红石刻(0.5秒)最短保持时间\n"
             + "  - 只能放在实体方块顶面\n\n"
             + "【绊线钩】tripwire_hook\n"
             + "方块ID: tripwire_hook\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west（面朝方向=钩子伸出方向）\n"
             + "  - attached=true/false（是否连接到对面的绊线钩形成完整绊线）\n"
             + "  - powered=true/false（是否被触发）\n"
             + "详细行为（来源：minecraft.wiki/w/Tripwire_Hook）：\n"
             + "  - 必须成对使用：两个绊线钩面对面，中间用线(tripwire)连接（最远40格）\n"
             + "  - 有实体穿过线时触发，输出15级信号\n"
             + "  - 强充能附着的方块\n"
             + "  - 绊线被剪刀剪断不触发，被破坏则触发\n\n"
             + "【绊线】tripwire\n"
             + "方块ID: tripwire\n"
             + "Block States:\n"
             + "  - north/south/east/west=true/false（四方向连接状态）\n"
             + "  - attached=true/false（是否被绊线钩连接）\n"
             + "  - powered=true/false（是否被触发）\n"
             + "  - disarmed=true/false（是否被剪刀解除）\n\n"
             + "【标靶】target\n"
             + "方块ID: target\n"
             + "Block States:\n"
             + "  - power=0~15（当前输出信号强度）\n"
             + "详细行为（来源：minecraft.wiki/w/Target）：\n"
             + "  - 被弹射物(箭、三叉戟、雪球、鸡蛋等)击中时产生短暂红石信号\n"
             + "  - 信号强度取决于命中离中心的距离：正中心=15，越边缘越低\n"
             + "  - 箭矢信号持续：10红石刻(1秒)；其他弹射物：4红石刻(0.4秒)\n"
             + "  - 可以被活塞推动\n"
             + "  - 是实体方块，可被充能，可向各方向输出（全向输出）\n"
             + "  - 常用于远程触发机关、射击游戏\n\n"
             + "【阳光传感器】daylight_detector\n"
             + "方块ID: daylight_detector\n"
             + "Block States:\n"
             + "  - inverted=true/false（false=白天输出信号，true=夜晚输出信号，右键切换）\n"
             + "  - power=0~15（根据时间/天气输出的信号强度）\n"
             + "详细行为（来源：minecraft.wiki/w/Daylight_Detector）：\n"
             + "  - 正常模式：根据太阳高度输出信号，正午最强(15)，日出/日落渐弱，夜晚=0\n"
             + "  - 反转模式(右键)：夜晚输出信号，白天=0\n"
             + "  - 受天气影响：雨天/雷暴会降低输出\n"
             + "  - 必须能看到天空（上方不能有不透明方块遮挡）\n"
             + "  - 不能充能相邻方块，只能直接为红石粉/元件供电\n\n"
             + "【音符盒】note_block\n"
             + "方块ID: note_block\n"
             + "Block States:\n"
             + "  - instrument=harp/basedrum/snare/hat/bass/flute/bell/guitar/chime/xylophone/\n"
             + "              iron_xylophone/cow_bell/didgeridoo/bit/banjo/pling/zombie/skeleton/\n"
             + "              creeper/dragon/wither_skeleton/piglin/custom_head\n"
             + "  - note=0~24（音高，0=F#3，24=F#5，每级升半音）\n"
             + "  - powered=true/false（是否被红石激活）\n"
             + "详细行为（来源：minecraft.wiki/w/Note_Block）：\n"
             + "  - 收到红石脉冲(上升沿)时发出音符\n"
             + "  - 右键调音：每次+1音高（0→24后循环回0）\n"
             + "  - 乐器取决于音符盒下方方块材质：\n"
             + "    木质→bass/低音吉他，沙/砾→snare/军鼓，玻璃→hat/踩镲\n"
             + "    石质→basedrum/底鼓，金块→bell/铃铛，粘土→flute/长笛\n"
             + "    冰→chime/风铃，羊毛→guitar/吉他，骨块→xylophone/木琴\n"
             + "    铁块→iron_xylophone/铁琴，灵魂沙→cow_bell/牛铃\n"
             + "    南瓜→didgeridoo，绿宝石块→bit，干草捆→banjo，萤石→pling\n"
             + "    生物头颅→对应生物音效\n"
             + "  - 音符盒上方必须是空气才能发声\n"
             + "  - 输出：被激活时同时向上方输出1级红石脉冲（可被侦测器检测）\n\n"
             + "━━━━━━ 四、可激活方块与铁轨类 ━━━━━━\n\n"
             + "【活板门】oak_trapdoor / iron_trapdoor / 各材质_trapdoor\n"
             + "方块ID: oak_trapdoor, spruce_trapdoor, birch_trapdoor, jungle_trapdoor,\n"
             + "         acacia_trapdoor, dark_oak_trapdoor, mangrove_trapdoor, cherry_trapdoor,\n"
             + "         bamboo_trapdoor, crimson_trapdoor, warped_trapdoor, iron_trapdoor\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west（铰链所在面的方向）\n"
             + "  - half=top/bottom（安装在方块上半还是下半）\n"
             + "  - open=true/false（是否打开）\n"
             + "  - powered=true/false（是否被红石激活）\n"
             + "  - waterlogged=true/false（是否含水）\n"
             + "详细行为：\n"
             + "  - 木质活板门：可用红石信号或右键开关\n"
             + "  - 铁质活板门：只能用红石信号开关（不可右键）\n"
             + "  - 收到信号时open=true打开，信号消失时关闭\n\n"
             + "【门】oak_door / iron_door / 各材质_door\n"
             + "方块ID: oak_door, spruce_door, birch_door, jungle_door, acacia_door,\n"
             + "         dark_oak_door, mangrove_door, cherry_door, bamboo_door,\n"
             + "         crimson_door, warped_door, iron_door\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west（门面朝方向）\n"
             + "  - half=lower/upper（下半扇/上半扇，门占2格高）\n"
             + "  - hinge=left/right（铰链在左/右侧）\n"
             + "  - open=true/false（是否打开）\n"
             + "  - powered=true/false（是否被红石激活）\n"
             + "详细行为：\n"
             + "  - 木门可红石/右键开关；铁门只能红石\n"
             + "  - 蓝图需放上下两半扇：y坐标lower在下，upper在上\n\n"
             + "【栅栏门】oak_fence_gate / 各材质_fence_gate\n"
             + "方块ID: oak_fence_gate, spruce/birch/jungle/acacia/dark_oak/mangrove/cherry/\n"
             + "         bamboo/crimson/warped_fence_gate\n"
             + "Block States:\n"
             + "  - facing=north/south/east/west（门面朝方向）\n"
             + "  - in_wall=true/false（是否嵌在墙中，影响高度显示）\n"
             + "  - open=true/false（是否打开）\n"
             + "  - powered=true/false（是否被红石激活）\n"
             + "详细行为：\n"
             + "  - 可用红石信号或右键开关\n"
             + "  - in_wall=true时降低显示高度与墙壁齐平\n\n"
             + "【铁轨类】rail / powered_rail / detector_rail / activator_rail\n"
             + "方块ID: rail, powered_rail, detector_rail, activator_rail\n"
             + "Block States:\n"
             + "  - rail: shape=north_south/east_west/ascending_north/ascending_south/ascending_east/ascending_west/\n"
             + "          north_east/north_west/south_east/south_west  waterlogged=false\n"
             + "  - powered_rail: shape=north_south/east_west/ascending_*  powered=true/false  waterlogged=false\n"
             + "  - detector_rail: shape=north_south/east_west/ascending_*  powered=true/false  waterlogged=false\n"
             + "  - activator_rail: shape=north_south/east_west/ascending_*  powered=true/false  waterlogged=false\n"
             + "详细行为（来源：minecraft.wiki/w/Rail）：\n"
             + "  - 普通铁轨(rail)：可弯曲（有north_east等弯道形状），不响应红石\n"
             + "  - 充能铁轨(powered_rail)：\n"
             + "    powered=true→加速矿车（最高8m/s），powered=false→制动/停止矿车\n"
             + "    信号可沿铁轨传播最远9格（连续充能铁轨）\n"
             + "    不可弯曲\n"
             + "  - 探测铁轨(detector_rail)：\n"
             + "    矿车经过时输出15级信号（强充能下方方块）\n"
             + "    配合比较器可读取矿车内容物数量\n"
             + "    不可弯曲\n"
             + "  - 激活铁轨(activator_rail)：\n"
             + "    powered=true时激活经过的特殊矿车（TNT矿车→点燃，漏斗矿车→锁定/解锁）\n"
             + "    powered=true时甩出矿车中的乘客\n"
             + "    不可弯曲\n"
             + "  - 所有铁轨需放在实体方块顶面\n\n"
             + "【潜影盒/雕纹铜块/铜灯泡】（1.20.4+新增红石元件）\n\n"
             + "【合成器】crafter（1.21新增）\n"
             + "方块ID: crafter\n"
             + "Block States:\n"
             + "  - orientation=down_east/down_north/down_south/down_west/east_up/north_up/south_up/\n"
             + "               west_up/up_east/up_north/up_south/up_west（放置朝向）\n"
             + "  - crafting=true/false（是否正在执行合成）\n"
             + "  - triggered=true/false（是否收到红石信号）\n"
             + "详细行为（来源：minecraft.wiki/w/Crafter）：\n"
             + "  - 收到红石脉冲时自动执行一次合成（类似工作台但自动化）\n"
             + "  - 9格输入格可独立禁用（右键空格子→禁用该格，代表配方中的空位）\n"
             + "  - 合成产物从正面(orientation方向)弹出或送入facing方向的容器\n"
             + "  - 配合漏斗/投射器可实现全自动合成线\n"
             + "  - 每次脉冲合成一次，连续信号只触发一次\n\n"
             + "【铜灯泡】copper_bulb / exposed_copper_bulb / weathered_copper_bulb / oxidized_copper_bulb\n"
             + "方块ID: copper_bulb, exposed_copper_bulb, weathered_copper_bulb, oxidized_copper_bulb\n"
             + "         (以及对应waxed_版本)\n"
             + "Block States:\n"
             + "  - lit=true/false（是否点亮）\n"
             + "  - powered=true/false（是否收到红石信号）\n"
             + "详细行为（来源：minecraft.wiki/w/Copper_Bulb）：\n"
             + "  - T触发器行为：每次收到红石脉冲(上升沿)切换一次开关状态(亮→灭→亮)\n"
             + "  - 与红石灯不同：红石灯随信号亮灭，铜灯泡是脉冲切换式\n"
             + "  - 光照等级随氧化程度递减：铜=15，斜纹铜=12，风化铜=8，氧化铜=4\n"
             + "  - 可涂蜡防止氧化\n"
             + "  - 比较器可读取：lit=true时输出15\n\n"
             + "【潜影感测体/Sculk Sensor】sculk_sensor / calibrated_sculk_sensor\n"
             + "方块ID: sculk_sensor / calibrated_sculk_sensor\n"
             + "Block States:\n"
             + "  - sculk_sensor_phase=inactive/active/cooldown（检测阶段）\n"
             + "  - power=0~15（输出信号强度）\n"
             + "  - waterlogged=true/false\n"
             + "  - calibrated版增加: facing=north/south/east/west（校准输入面朝向）\n"
             + "详细行为（来源：minecraft.wiki/w/Sculk_Sensor）：\n"
             + "  - 检测8格范围内的振动事件（脚步、方块放置/破坏、弹射物等）\n"
             + "  - 校准版：16格范围，且可通过侧面红石信号筛选特定振动频率\n"
             + "  - 不同振动类型对应不同信号强度(1~15)：\n"
             + "    1=脚步，2=落地，3=物品使用，4=滑翔，5=坐骑，6=喷溅，7=射击\n"
             + "    8=实体受伤，9=方块破坏，10=装备，11=玩家交互，12=方块被激活\n"
             + "    13=方块放置，14=传送，15=实体死亡/爆炸\n"
             + "  - 检测后进入40刻冷却期(不接受新振动)\n"
             + "  - 潜行不触发，羊毛类方块可阻隔振动传播\n\n"
             + "━━━━━━ 五、红石信号机制详解（来源：minecraft.wiki/w/Redstone_signal）━━━━━━\n\n"
             + "方块充能规则（Power/Activation）：\n"
             + "  - 强充能(Strongly Powered)：\n"
             + "    红石火把强充能其正上方方块\n"
             + "    中继器/比较器输出端强充能facing方向的方块\n"
             + "    按钮/拉杆/压力板强充能附着的方块\n"
             + "    → 被强充能的方块可为相邻红石粉供电\n"
             + "  - 弱充能(Weakly Powered)：\n"
             + "    红石粉弱充能其指向的方块和正下方方块\n"
             + "    → 被弱充能的方块不能为红石粉供电，但能激活相邻机械元件和中继器/比较器\n"
             + "  - 不可充能方块（透明方块）：\n"
             + "    玻璃、冰、台阶(非双层)、楼梯、树叶、灵魂沙、活塞(伸出时)、\n"
             + "    附魔台、末影箱等透明/非实体方块不能被充能\n"
             + "  - 激活(Activation)：\n"
             + "    机械元件(活塞、门、铁轨等)在以下情况被激活：\n"
             + "    ①直接相邻的电源/红石粉指向它 ②所附着/相邻的方块被充能(强或弱)\n"
             + "    ③对于某些元件(门/铁轨)上下方块被充能也可激活\n\n"
             + "信号传递方向：\n"
             + "  - 红石粉：水平扩散（可上下1格高差），信号只向连接方向传播\n"
             + "  - 中继器：严格单向，从背面→正面(facing方向)，侧面完全隔离\n"
             + "  - 比较器：主方向单向(背面→正面)，侧面只作为第二输入源\n"
             + "  - 红石火把：向上方方块+周围4方向红石粉供电（不向正下方供电）\n"
             + "  - 红石块：全方向(6面)恒定供电\n"
             + "  - 侦测器：只从背面输出(facing反方向)\n\n"
             + "红石时序（Timing，1红石刻 = 0.1秒 = 2游戏刻 = 1/10秒）：\n"
             + "  - 红石火把翻转：1红石刻延迟（上升/下降沿各1刻）\n"
             + "  - 中继器：1~4红石刻延迟（对应delay=1~4）\n"
             + "  - 比较器：1红石刻延迟（不论模式）\n"
             + "  - 活塞伸出：0刻（瞬时）| 缩回：1.5红石刻(3游戏刻)\n"
             + "  - 粘性活塞拉回方块：额外需要1红石刻\n"
             + "  - 侦测器脉冲宽度：1红石刻(2游戏刻)\n"
             + "  - 红石灯熄灭延迟：2红石刻(4游戏刻)\n"
             + "  - 漏斗传输间隔：4红石刻(8游戏刻/每物品)\n"
             + "  - 投掷器/投射器：触发无延迟，但有4红石刻冷却\n"
             + "  - 按钮脉冲：石质=10红石刻(1秒)，木质=15红石刻(1.5秒)\n"
             + "  - 压力板最短保持：5红石刻(0.5秒)\n\n"
             + "蓝图中红石放置顺序建议：\n"
             + "  - 第一步：先放实体方块（石头、混凝土等作为底座、支撑和红石粉放置面）\n"
             + "  - 第二步：放置机械元件（活塞、投掷器、门、红石灯等被激活的目标）\n"
             + "  - 第三步：放置信号处理元件（中继器、比较器、红石火把）\n"
             + "  - 第四步：最后放红石粉（会自动连接相邻元件形成电路）\n"
             + "  - 第五步：放置输入元件（拉杆、按钮、压力板等触发源）\n"
             + "  - 活塞放置为 extended=false（通电后自动伸出）\n"
             + "  - 红石灯放置为 lit=false（通电后自动亮起）\n"
             + "  - 拉杆/按钮放置为 powered=false（玩家手动激活）\n"
             + "  - 铜灯泡放置为 lit=false, powered=false（首次脉冲后亮起）\n\n"
             + "━━━━━━ 六、常见红石电路模式参考 ━━━━━━\n\n"
             + "基础逻辑门：\n"
             + "  - NOT门（反相器）：红石火把附着在被输入信号充能的方块上，输入ON→输出OFF\n"
             + "  - OR门：两路或多路红石粉直接汇合到同一红石粉线\n"
             + "  - AND门：两路信号各经一个NOT门取反→汇入NOR门→再NOT取反\n"
             + "  - NOR门：两路汇合后接NOT门\n"
             + "  - XOR门：利用比较器减法模式实现（A-B或B-A任一>0则输出）\n"
             + "  - XNOR门：XOR输出接NOT门\n\n"
             + "脉冲电路：\n"
             + "  - 脉冲延长器：多个中继器串联（每个增加1~4红石刻延迟）\n"
             + "  - 单脉冲/边沿检测器：信号经中继器延迟后与原信号做比较器减法→产生短脉冲\n"
             + "  - 脉冲限制器：比较器从侧面输入限制主信号宽度\n"
             + "  - 单稳态电路：输出固定宽度的脉冲，不论输入持续多久\n\n"
             + "时钟电路：\n"
             + "  - 中继器环形时钟：N个中继器首尾相连（最短2个=4红石刻周期）\n"
             + "  - 侦测器对望时钟：两个侦测器面对面（2红石刻周期，最快时钟）\n"
             + "  - 漏斗时钟：两个漏斗互相输送物品，比较器检测计数\n"
             + "  - 活塞时钟：粘性活塞推拉方块循环\n\n"
             + "存储电路：\n"
             + "  - RS锁存器：两个NOR门交叉反馈（Set/Reset输入）\n"
             + "  - T触发器：粘性活塞推拉红石块交替供电（每个脉冲切换一次输出）\n"
             + "  - D锁存器：带使能端的数据存储\n"
             + "  - 计数器：利用漏斗物品数量+比较器实现\n\n"
             + "实用电路：\n"
             + "  - 自动农场核心：侦测器检测作物生长→活塞收割→漏斗收集\n"
             + "  - 物品分类机：漏斗+比较器+红石火把实现单种物品筛选\n"
             + "  - 隐藏门/活塞门：活塞阵列+延时控制依次收缩方块\n"
             + "  - 密码锁：多个拉杆组合+AND门验证\n"
             + "  - BUD(方块更新检测器)：利用活塞的准连接特性检测方块更新\n\n"
             + "复杂红石机器处理规则：\n"
             + "  - 以下类型的红石机器结构复杂，不要凭想象建造，必须先用 [SEARCH] 搜索最新设计：\n"
             + "    世界吞噬者(World Eater)、TNT复制机(TNT Duper)、飞行机器(Flying Machine)、\n"
             + "    全自动农场、刷铁机、刷怪塔、自动分类机、活塞门(3x3+)、\n"
             + "    隐藏楼梯、自动酿造机、炮(TNT Cannon)、红石电脑等\n"
             + "  - 搜索时使用英文关键词效果更好，如 \"minecraft world eater schematic 1.20\"\n"
             + "  - 红石电路相关搜索优先加 site:minecraft.wiki\n"
             + "  - 如果搜索结果不理想，直接用 [FETCH] 抓取 Minecraft Wiki 页面获取准确信息：\n"
             + "    常用页面：https://minecraft.wiki/w/Redstone_circuits（红石电路大全）\n"
             + "              https://minecraft.wiki/w/Tutorial:Advanced_redstone_circuits（进阶电路）\n"
             + "              https://minecraft.wiki/w/Tutorials/Redstone（红石教程）\n"
             + "  - 如果搜索结果包含方块坐标列表或litematic/schematic数据，根据其转换为蓝图格式\n"
             + "  - 如果搜索不到精确的方块级设计，诚实告诉玩家此机器过于复杂无法准确还原，\n"
             + "    建议玩家提供schematic文件或参考教程链接（你可以用 [FETCH] 抓取）\n"
             + "  - 对于简单红石电路（门灯、暗门、简单时钟、逻辑门组合等基础逻辑）可以直接建造\n\n"
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
