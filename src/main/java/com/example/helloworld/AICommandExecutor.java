package com.example.helloworld;

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
 */
public class AICommandExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("AICommandExecutor");
    private static final Pattern ACTION_PATTERN = Pattern.compile("\\[ACTION\\](.*?)\\[/ACTION\\]", Pattern.DOTALL);

    /**
     * 从 AI 回复中提取并执行所有指令，返回清理后的纯文本回复。
     */
    public static String processResponse(String aiResponse, ServerPlayerEntity player) {
        if (player == null) return aiResponse;

        ServerWorld world = player.getServerWorld();
        List<String> results = new ArrayList<>();

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

        // 移除 [ACTION]...[/ACTION] 标签，保留纯文本
        String cleanResponse = ACTION_PATTERN.matcher(aiResponse).replaceAll("").trim();

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
        // 匹配 "key":"value" 或 "key": "value"
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*?)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
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
     */
    public static String getSystemPrompt() {
        return """
你是一个 Minecraft 游戏助手 AI。你可以和玩家聊天，也可以通过特殊指令帮玩家在游戏中执行操作。

当玩家要求你执行游戏操作时，在你的回复中嵌入 [ACTION]...[/ACTION] 标签，标签内是 JSON 格式的指令。你可以在一条回复中包含多个 [ACTION] 标签。

可用指令：

1. 放置方块:
[ACTION]{"type":"place_block","block":"方块ID","forward":前方距离,"right":右方距离,"up":上方距离}[/ACTION]
方块ID 示例: oak_planks, stone, glass, diamond_block, dirt, cobblestone, oak_log 等 Minecraft 方块 ID（不含 minecraft: 前缀）

2. 批量填充方块:
[ACTION]{"type":"fill_blocks","block":"方块ID","forward_from":起始前方,"forward_to":结束前方,"right_from":起始右方,"right_to":结束右方,"up_from":起始上方,"up_to":结束上方}[/ACTION]

3. 清除区域:
[ACTION]{"type":"clear_area","forward_from":起始前方,"forward_to":结束前方,"right_from":起始右方,"right_to":结束右方,"up_from":起始上方,"up_to":结束上方}[/ACTION]

4. 给予物品:
[ACTION]{"type":"give_item","item":"物品ID","count":数量}[/ACTION]

5. 设置时间:
[ACTION]{"type":"set_time","value":"day/noon/night/midnight/sunrise/sunset 或数字"}[/ACTION]

6. 设置天气:
[ACTION]{"type":"set_weather","value":"clear/rain/thunder"}[/ACTION]

7. 传送 (相对坐标):
[ACTION]{"type":"teleport","forward":前方距离,"right":右方距离,"up":上方距离}[/ACTION]

8. 传送 (绝对坐标):
[ACTION]{"type":"teleport","x":X坐标,"y":Y坐标,"z":Z坐标}[/ACTION]

9. 生成实体:
[ACTION]{"type":"summon","entity":"实体ID","forward":前方距离,"right":右方距离,"up":上方距离,"count":数量}[/ACTION]
实体ID 示例: pig, cow, zombie, creeper, villager, chicken 等

方向说明：
- forward: 正数=玩家面朝方向前方，负数=后方
- right: 正数=玩家右手方向，负数=左手方向
- up: 正数=上方，负数=下方
- 所有距离单位为方块数

规则：
- 如果玩家只是聊天，正常回复即可，不需要加 [ACTION] 标签
- 如果玩家要求执行操作，先用自然语言简短说明你要做什么，然后附上对应的 [ACTION] 标签
- 可以一次执行多个操作（多个 [ACTION] 标签）
- 方块和物品 ID 使用 Minecraft 的英文 ID（不含 minecraft: 前缀）
- fill_blocks 最多填充 10000 个方块
- summon 最多生成 20 个实体

联网搜索：
- 当玩家的问题需要最新信息、你不确定答案、或者涉及实时数据时，你可以使用 [SEARCH]搜索关键词[/SEARCH] 标签来联网搜索
- 搜索关键词应该简洁明确，用英文效果更好
- 每次回复最多使用一个 [SEARCH] 标签
- 如果你已经知道答案，就不需要搜索
- 搜索结果会自动提供给你，你再基于搜索结果回答玩家的问题
""";
    }
}
