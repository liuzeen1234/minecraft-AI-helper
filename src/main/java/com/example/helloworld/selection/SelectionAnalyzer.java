package com.example.helloworld.selection;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * 分析选区内所有方块的种类、状态和相对位置，
 * 并可导出为兼容 BlueprintParser 的蓝图文本格式。
 */
public class SelectionAnalyzer {

    /** 分析结果中的单个方块信息 */
    public record BlockInfo(String blockId, Map<String, String> properties, int relX, int relY, int relZ) {}

    /** 完整的分析结果 */
    public record AnalysisResult(
            BlockPos min, BlockPos max,
            int sizeX, int sizeY, int sizeZ,
            int totalBlocks, int airBlocks,
            Map<String, Integer> blockCounts,
            List<BlockInfo> blocks
    ) {}

    /**
     * 扫描选区内所有方块，返回分析结果。
     */
    public static AnalysisResult analyze(BlockPos pos1, BlockPos pos2) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return null;

        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        Map<String, Integer> blockCounts = new TreeMap<>();
        List<BlockInfo> blocks = new ArrayList<>();
        int airBlocks = 0;
        int totalBlocks = 0;

        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    totalBlocks++;

                    if (state.isAir()) {
                        airBlocks++;
                        continue;
                    }

                    String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();
                    Map<String, String> props = new LinkedHashMap<>();
                    for (Property<?> prop : state.getProperties()) {
                        props.put(prop.getName(), getPropertyValueString(state, prop));
                    }

                    blockCounts.merge(blockId, 1, Integer::sum);
                    blocks.add(new BlockInfo(blockId, props,
                            x - min.getX(), y - min.getY(), z - min.getZ()));
                }
            }
        }

        return new AnalysisResult(min, max, sizeX, sizeY, sizeZ,
                totalBlocks, airBlocks, blockCounts, blocks);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyValueString(BlockState state, Property<T> prop) {
        return prop.name(state.get(prop));
    }

    /**
     * 将分析结果导出为兼容 BlueprintParser 的蓝图文本。
     * 格式与 architect-docs/ 下的 .txt 文件一致。
     */
    public static String exportBlueprint(AnalysisResult result, String name) {
        if (result == null || result.blocks.isEmpty()) return "";

        // 为每种独特的 blockId+properties 组合分配一个符号字符
        Map<String, Character> legendMap = new LinkedHashMap<>();
        Map<String, String> legendDefinitions = new LinkedHashMap<>();
        char nextSymbol = 'A';

        // 构建 3D 网格: [y][z][x]
        char[][][] grid = new char[result.sizeY][result.sizeZ][result.sizeX];
        for (char[][] layer : grid)
            for (char[] row : layer)
                Arrays.fill(row, ' ');

        for (BlockInfo block : result.blocks) {
            String key = buildLegendKey(block.blockId, block.properties);
            if (!legendMap.containsKey(key)) {
                char symbol = nextSymbol;
                // 跳过空格和管道符等特殊字符
                while (symbol == ' ' || symbol == '|' || symbol == '=' || symbol == '{' || symbol == '}') {
                    symbol++;
                }
                nextSymbol = (char) (symbol + 1);
                // 如果大写字母用完，切换到小写
                if (nextSymbol > 'Z' && nextSymbol < 'a') nextSymbol = 'a';
                // 如果小写也用完，用数字
                if (nextSymbol > 'z' && nextSymbol < '0') nextSymbol = '0';

                legendMap.put(key, symbol);
                legendDefinitions.put(key, buildLegendDefinition(block.blockId, block.properties));
            }
            grid[block.relY][block.relZ][block.relX] = legendMap.get(key);
        }

        // 生成蓝图文本
        StringBuilder sb = new StringBuilder();
        sb.append("==== 平面结构图 ====\n");
        sb.append("{{layered blueprint|name=\n");
        sb.append(name).append("|default=第1层\n");

        // 图例
        for (Map.Entry<String, Character> entry : legendMap.entrySet()) {
            sb.append("|").append(entry.getValue()).append("=")
              .append(legendDefinitions.get(entry.getKey())).append("\n");
        }

        // 各层
        for (int y = 0; y < result.sizeY; y++) {
            sb.append("\n|----第").append(y + 1).append("层|\n\n");
            for (int z = 0; z < result.sizeZ; z++) {
                sb.append(new String(grid[y][z]));
                // 去掉行尾空格
                int len = sb.length();
                while (len > 0 && sb.charAt(len - 1) == ' ') len--;
                sb.setLength(len);
                sb.append("\n");
            }
        }

        sb.append("\n}}\n");
        return sb.toString();
    }

    /**
     * 构建图例的唯一键（blockId + 所有有意义的属性）
     */
    private static String buildLegendKey(String blockId, Map<String, String> properties) {
        if (properties.isEmpty()) return blockId;
        StringBuilder sb = new StringBuilder(blockId);
        for (Map.Entry<String, String> e : properties.entrySet()) {
            // 跳过一些不影响外观的属性
            if (shouldSkipProperty(e.getKey())) continue;
            sb.append("|").append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * 构建图例定义字符串，兼容 BlueprintParser 的格式。
     */
    private static String buildLegendDefinition(String blockId, Map<String, String> properties) {
        String displayName = blockIdToDisplayName(blockId);
        StringBuilder sb = new StringBuilder(displayName);

        // 处理 facing → -rot 转换
        String facing = properties.get("facing");
        if (facing != null) {
            int rot = facingToRot(facing);
            if (isWallMounted(blockId)) {
                sb.append("-rot-").append(rot);
            } else {
                sb.append("-rot").append(rot);
            }
        }

        // 处理 half 属性
        String half = properties.get("half");
        if (half != null) {
            // 门用 bottom/top 表示 lower/upper
            if (blockId.endsWith("_door")) {
                String mapped = half.equals("lower") ? "bottom" : "top";
                sb.append("+").append(mapped);
            } else {
                sb.append("+").append(half);
            }
        }

        // 处理 part 属性（床）
        String part = properties.get("part");
        if (part != null) {
            if (half != null) {
                sb.append(" ").append(part);
            } else {
                sb.append("+").append(part);
            }
        }

        // 处理 axis 属性（原木）
        String axis = properties.get("axis");
        if (axis != null && (blockId.contains("_log") || blockId.contains("_wood"))) {
            String axisName = switch (axis) {
                case "x" -> "east";
                case "z" -> "north";
                default -> "top";
            };
            sb.append("+").append(axisName);
        }

        return sb.toString();
    }

    private static boolean shouldSkipProperty(String propName) {
        // 跳过不影响蓝图还原的属性
        return switch (propName) {
            case "waterlogged", "powered", "lit", "open",
                 "in_wall", "extended", "short", "locked",
                 "has_bottle_0", "has_bottle_1", "has_bottle_2",
                 "signal_fire", "age", "level", "moisture",
                 "snowy", "persistent", "distance", "charges" -> true;
            default -> false;
        };
    }

    private static int facingToRot(String facing) {
        return switch (facing) {
            case "south" -> 0;
            case "west" -> 90;
            case "north" -> 180;
            case "east" -> 270;
            default -> 0;
        };
    }

    private static boolean isWallMounted(String blockId) {
        return blockId.equals("wall_torch") || blockId.equals("soul_wall_torch")
                || blockId.equals("redstone_wall_torch");
    }

    /**
     * 方块ID转显示名称: smooth_sandstone_stairs → Smooth Sandstone Stairs
     */
    private static String blockIdToDisplayName(String blockId) {
        String[] parts = blockId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
