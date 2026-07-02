package com.example.helloworld.blueprint;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 根据蓝图数据在游戏世界中建造建筑。
 * 以玩家当前位置为原点，蓝图第1层第1行第1列对应玩家脚下位置。
 * 蓝图的行方向(row)对应 Z+（南），列方向(col)对应 X+（东）。
 *
 * 分两阶段放置：先放实体方块，再放附着方块（按钮、火把等），
 * 对未指定朝向的附着方块自动推断朝向。
 */
public class BlueprintBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger("BlueprintBuilder");

    // 需要附着在其他方块上的方块类型（需要延迟放置并自动推断朝向）
    private static final Set<String> ATTACHABLE_BLOCKS = Set.of(
            "wall_torch", "stone_button", "oak_button", "spruce_button",
            "birch_button", "jungle_button", "acacia_button", "dark_oak_button",
            "mangrove_button", "cherry_button", "bamboo_button", "crimson_button",
            "warped_button", "polished_blackstone_button"
    );

    /**
     * 在玩家位置建造蓝图建筑。自动识别 V1/V2 格式。
     * @return 放置的方块数量
     */
    public static int build(BlueprintData blueprint, ServerPlayerEntity player, ServerWorld world) {
        if (blueprint.isV2()) {
            return buildV2(blueprint, player.getBlockPos(), world);
        }
        return buildV1(blueprint, player, world);
    }

    /**
     * V2 格式建造：直接按显式坐标放置方块，所有 block state 属性完整还原。
     * 分两阶段：先放实体方块，再放附着方块（按钮、火把等）。
     * 如果方块带有 items 数据，放置后写入容器物品。
     */
    private static int buildV2(BlueprintData blueprint, BlockPos origin, ServerWorld world) {
        List<BlueprintData.BlockEntry3D> blocks = blueprint.getBlocks3d();
        int placedCount = 0;
        List<BlueprintData.BlockEntry3D> deferred = new ArrayList<>();

        // 第一阶段：放置实体方块，收集附着方块
        for (BlueprintData.BlockEntry3D block : blocks) {
            BlockPos pos = new BlockPos(
                    origin.getX() + block.getX(),
                    origin.getY() + block.getY(),
                    origin.getZ() + block.getZ());

            if (ATTACHABLE_BLOCKS.contains(block.getBlockId())) {
                deferred.add(block);
                continue;
            }

            BlockState state = resolveBlockStateV2(block);
            if (state != null) {
                world.setBlockState(pos, state);
                placedCount++;
                // 写入容器物品
                if (block.hasItems()) {
                    applyContainerItems(world, pos, block.getItems());
                }
            }
        }

        // 第二阶段：放置附着方块
        for (BlueprintData.BlockEntry3D block : deferred) {
            BlockPos pos = new BlockPos(
                    origin.getX() + block.getX(),
                    origin.getY() + block.getY(),
                    origin.getZ() + block.getZ());

            BlockState state = resolveBlockStateV2(block);
            if (state != null) {
                world.setBlockState(pos, state);
                placedCount++;
            }
        }

        LOGGER.info("V2 蓝图 '{}' 建造完成，放置 {} 个方块", blueprint.getName(), placedCount);
        return placedCount;
    }

    /**
     * 将物品列表写入指定位置的容器方块实体（箱子、桶、漏斗、熔炉等）。
     */
    private static void applyContainerItems(ServerWorld world, BlockPos pos, List<BlueprintData.ItemEntry> items) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) {
            LOGGER.debug("位置 {} 没有方块实体，跳过物品写入", pos.toShortString());
            return;
        }
        if (!(be instanceof Inventory inv)) {
            LOGGER.debug("位置 {} 的方块实体不是容器，跳过物品写入", pos.toShortString());
            return;
        }

        for (BlueprintData.ItemEntry item : items) {
            String itemId = item.getItemId();
            if (!itemId.contains(":")) {
                itemId = "minecraft:" + itemId;
            }
            Item mcItem = Registries.ITEM.get(new Identifier(itemId));
            if (mcItem == net.minecraft.item.Items.AIR) {
                LOGGER.warn("未知物品ID: {}", item.getItemId());
                continue;
            }

            ItemStack stack = new ItemStack(mcItem, item.getCount());

            // 如果有 NBT 数据，解析并应用
            if (item.getNbtString() != null && !item.getNbtString().isEmpty()) {
                try {
                    NbtCompound nbt = StringNbtReader.parse(item.getNbtString());
                    stack.setNbt(nbt);
                } catch (Exception e) {
                    LOGGER.warn("物品 NBT 解析失败 ({}): {}", item.getItemId(), e.getMessage());
                }
            }

            int slot = item.getSlot();
            if (slot >= 0 && slot < inv.size()) {
                inv.setStack(slot, stack);
            } else {
                LOGGER.warn("物品槽位 {} 超出容器范围 (容器大小: {})", slot, inv.size());
            }
        }
        be.markDirty();
    }

    /**
     * 根据 V2 BlockEntry3D 解析 BlockState。
     * V2 格式中属性已经是标准 block state 键值对，直接应用，无需 _rot 转换。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState resolveBlockStateV2(BlueprintData.BlockEntry3D entry) {
        String blockId = entry.getBlockId();
        Identifier id = new Identifier("minecraft", blockId);
        Block block = Registries.BLOCK.get(id);

        if (block == Blocks.AIR && !"air".equals(blockId)) {
            LOGGER.warn("未知方块ID: {}", blockId);
            return null;
        }

        BlockState state = block.getDefaultState();
        for (Map.Entry<String, String> prop : entry.getProperties().entrySet()) {
            state = applyProperty(state, prop.getKey(), prop.getValue());
        }
        return state;
    }

    // =========================================================================
    // V1 建造逻辑（原有逻辑，重命名为 buildV1）
    // =========================================================================

    private static int buildV1(BlueprintData blueprint, ServerPlayerEntity player, ServerWorld world) {
        BlockPos origin = player.getBlockPos();
        Map<Character, BlueprintData.BlockEntry> legend = blueprint.getLegend();
        List<char[][]> layers = blueprint.getLayers();

        int placedCount = 0;
        // 延迟放置的附着方块
        List<DeferredBlock> deferred = new ArrayList<>();
        // 床的 head 部分位置记录（跳过放置，由 foot 自动生成）
        Set<String> bedHeadPositions = new HashSet<>();
        // 床的 foot 部分记录（延迟放置，需要推断 facing）
        List<DeferredBlock> bedFootBlocks = new ArrayList<>();

        // 预扫描：找出所有床的 head 和 foot 位置
        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            char[][] grid = layers.get(layerIdx);
            for (int row = 0; row < grid.length; row++) {
                for (int col = 0; col < grid[row].length; col++) {
                    char symbol = grid[row][col];
                    if (symbol == ' ') continue;
                    BlueprintData.BlockEntry entry = legend.get(symbol);
                    if (entry == null) continue;
                    if (entry.getBlockId().endsWith("_bed")) {
                        String posKey = layerIdx + "," + row + "," + col;
                        if (entry.getProperties().containsKey("part")
                                && "head".equals(entry.getProperties().get("part"))) {
                            bedHeadPositions.add(posKey);
                        }
                    }
                }
            }
        }

        // 第一阶段：放置所有实体方块，收集附着方块
        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            char[][] grid = layers.get(layerIdx);
            int y = origin.getY() + layerIdx;

            for (int row = 0; row < grid.length; row++) {
                for (int col = 0; col < grid[row].length; col++) {
                    char symbol = grid[row][col];
                    if (symbol == ' ') continue;

                    BlueprintData.BlockEntry entry = legend.get(symbol);
                    if (entry == null) {
                        LOGGER.warn("未知图例符号: '{}' (层{}, 行{}, 列{})", symbol, layerIdx + 1, row, col);
                        continue;
                    }

                    int x = origin.getX() + col;
                    int z = origin.getZ() + row;
                    BlockPos pos = new BlockPos(x, y, z);

                    // 床的 head 部分跳过（由 foot 放置时自动生成）
                    if (entry.getBlockId().endsWith("_bed")) {
                        String posKey = layerIdx + "," + row + "," + col;
                        if (bedHeadPositions.contains(posKey)) {
                            continue; // 跳过 head
                        }
                        // foot 部分延迟放置
                        bedFootBlocks.add(new DeferredBlock(pos, entry, layerIdx, row, col));
                        continue;
                    }

                    if (needsAutoFacing(entry)) {
                        deferred.add(new DeferredBlock(pos, entry, layerIdx, row, col));
                    } else {
                        BlockState state = resolveBlockState(entry);
                        if (state != null) {
                            world.setBlockState(pos, state);
                            placedCount++;
                        }
                    }
                }
            }
        }

        // 第二阶段：放置附着方块，自动推断朝向
        for (DeferredBlock db : deferred) {
            BlockState state = resolveBlockState(db.entry);
            if (state == null) continue;

            // 如果方块没有指定 facing，自动推断
            if (!db.entry.getProperties().containsKey("facing")) {
                String facing = inferFacing(db.pos, db.entry, world, layers, legend, origin, db.layerIdx, db.row, db.col);
                if (facing != null) {
                    state = applyProperty(state, "facing", facing);
                }
                // 按钮默认 face=wall（贴墙放置）
                if (db.entry.getBlockId().endsWith("_button")) {
                    state = applyProperty(state, "face", "wall");
                }
            }

            world.setBlockState(db.pos, state);
            placedCount++;
        }

        // 第三阶段：放置床（根据 foot 和 head 的相对位置推断 facing）
        for (DeferredBlock db : bedFootBlocks) {
            String facing = inferBedFacing(db, layers, legend, bedHeadPositions);
            BlockState state = resolveBlockState(db.entry);
            if (state == null) continue;

            // 设置 part=foot 和推断的 facing
            state = applyProperty(state, "part", "foot");
            if (facing != null) {
                state = applyProperty(state, "facing", facing);
            }
            world.setBlockState(db.pos, state);
            placedCount++;

            // 在 facing 方向放置 head 部分
            if (facing != null) {
                BlockPos headPos = db.pos.offset(directionFromString(facing));
                BlockState headState = resolveBlockState(db.entry);
                if (headState != null) {
                    headState = applyProperty(headState, "part", "head");
                    headState = applyProperty(headState, "facing", facing);
                    world.setBlockState(headPos, headState);
                    placedCount++;
                }
            }
        }

        return placedCount;
    }

    /**
     * 根据蓝图中 foot 和 head 的相对位置推断床的 facing 方向。
     * facing = 从 foot 指向 head 的方向。
     */
    private static String inferBedFacing(DeferredBlock footBlock, List<char[][]> layers,
                                          Map<Character, BlueprintData.BlockEntry> legend,
                                          Set<String> bedHeadPositions) {
        int layer = footBlock.layerIdx;
        int row = footBlock.row;
        int col = footBlock.col;

        // 检查四个方向是否有 bed head
        // 北(row-1)=north, 南(row+1)=south, 西(col-1)=west, 东(col+1)=east
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        String[] directions = {"north", "south", "west", "east"};

        for (int i = 0; i < 4; i++) {
            String key = layer + "," + (row + offsets[i][0]) + "," + (col + offsets[i][1]);
            if (bedHeadPositions.contains(key)) {
                return directions[i];
            }
        }

        // 没找到 head，使用图例中的 facing
        Map<String, String> props = footBlock.entry.getProperties();
        return props.getOrDefault("facing", "north");
    }

    private static net.minecraft.util.math.Direction directionFromString(String facing) {
        return switch (facing) {
            case "north" -> net.minecraft.util.math.Direction.NORTH;
            case "south" -> net.minecraft.util.math.Direction.SOUTH;
            case "west" -> net.minecraft.util.math.Direction.WEST;
            case "east" -> net.minecraft.util.math.Direction.EAST;
            default -> net.minecraft.util.math.Direction.NORTH;
        };
    }

    /**
     * 判断方块是否需要自动推断朝向。
     * 条件：是附着类方块，且图例中没有指定 facing 属性。
     */
    private static boolean needsAutoFacing(BlueprintData.BlockEntry entry) {
        return ATTACHABLE_BLOCKS.contains(entry.getBlockId()) && !entry.getProperties().containsKey("facing");
    }

    /**
     * 根据蓝图中周围方块推断附着方块的 facing 方向。
     * facing 表示方块面朝的方向（即从附着墙面伸出的方向）。
     *
     * 检查四个水平方向，找到有实体方块的那一面，facing 就是从那面墙伸出来的反方向。
     * 例如：北边有墙 → facing=south（从北墙伸出来朝南）
     */
    private static String inferFacing(BlockPos pos, BlueprintData.BlockEntry entry,
                                       ServerWorld world, List<char[][]> layers,
                                       Map<Character, BlueprintData.BlockEntry> legend,
                                       BlockPos origin, int layerIdx, int row, int col) {
        char[][] grid = layers.get(layerIdx);

        // 检查四个方向：北(row-1)、南(row+1)、西(col-1)、东(col+1)
        // 如果该方向有实体方块，则 facing 为反方向
        String[] directions = {"south", "north", "east", "west"};
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}; // row, col 偏移

        for (int i = 0; i < 4; i++) {
            int nr = row + offsets[i][0];
            int nc = col + offsets[i][1];

            if (nr >= 0 && nr < grid.length && nc >= 0 && nc < grid[nr].length) {
                char neighbor = grid[nr][nc];
                if (neighbor != ' ') {
                    BlueprintData.BlockEntry neighborEntry = legend.get(neighbor);
                    if (neighborEntry != null && isSolidBlock(neighborEntry.getBlockId())) {
                        return directions[i];
                    }
                }
            }
        }

        // 没找到相邻实体方块，检查世界中已放置的方块
        BlockPos north = pos.north();
        BlockPos south = pos.south();
        BlockPos west = pos.west();
        BlockPos east = pos.east();

        if (isSolidInWorld(world, north)) return "south";
        if (isSolidInWorld(world, south)) return "north";
        if (isSolidInWorld(world, west)) return "east";
        if (isSolidInWorld(world, east)) return "west";

        return null;
    }

    /**
     * 判断方块ID是否为实体方块（可以附着其他方块的）
     */
    private static boolean isSolidBlock(String blockId) {
        // 排除已知的非实体方块
        return !blockId.contains("torch") && !blockId.contains("button")
                && !blockId.contains("door") && !blockId.contains("fence")
                && !blockId.contains("slab") && !blockId.equals("air");
    }

    private static boolean isSolidInWorld(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.isSolidBlock(world, pos);
    }

    /**
     * 根据 BlockEntry 解析出带属性的 BlockState。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState resolveBlockState(BlueprintData.BlockEntry entry) {
        String blockId = entry.getBlockId();
        Identifier id = new Identifier("minecraft", blockId);
        Block block = Registries.BLOCK.get(id);

        if (block == Blocks.AIR && !"air".equals(blockId)) {
            LOGGER.warn("未知方块ID: {}", blockId);
            return null;
        }

        BlockState state = block.getDefaultState();

        // 处理 _rot → facing 转换（根据方块类型使用不同的映射）
        Map<String, String> props = new LinkedHashMap<>(entry.getProperties());
        if (props.containsKey("_rot")) {
            int rot = Integer.parseInt(props.remove("_rot"));
            String facing = rotToFacing(blockId, rot);
            if (facing != null) {
                props.put("facing", facing);
            }
        }

        for (Map.Entry<String, String> prop : props.entrySet()) {
            state = applyProperty(state, prop.getKey(), prop.getValue());
        }

        return state;
    }

    /**
     * 根据方块类型和 rot 角度计算 facing 方向。
     * rot 是相对于方块默认 facing 的旋转角度。
     *
     * 旋转方向表（顺时针）：north → east → south → west → north
     *
     * 不同方块的默认 facing：
     *   楼梯 (stairs)：默认 facing=north → rot 顺时针旋转后再翻转东西
     *   墙上火把 (wall_torch)：默认 facing=north
     *   按钮 (button)：默认 facing=north
     *   玻璃板 (glass_pane)：无 facing，使用 east/west 连接属性
     *   门 (door)：默认 facing=north
     *   其他：默认 facing=north
     */
    private static String rotToFacing(String blockId, int rot) {
        String result;
        // 楼梯的 rot 映射
        // facing 表示全面朝向（同时也是台阶低端方向）
        // 从蓝图验证：rot0(>)=east, rot90(<)=west, rot180(s)=south, rot270(^)=north
        if (blockId.contains("stairs")) {
            result = switch (rot % 360) {
                case 0 -> "west";
                case 90 -> "east";
                case 180 -> "north";
                case 270 -> "south";
                default -> "north";
            };
        } else if (blockId.contains("wall_torch")) {
            result = switch (rot % 360) {
                case 0 -> "south";
                case 90 -> "east";
                case 180 -> "north";
                case 270 -> "west";
                default -> "north";
            };
        } else if (blockId.contains("glass_pane")) {
            return null;
        } else {
            result = switch (rot % 360) {
                case 0 -> "south";
                case 90 -> "east";
                case 180 -> "north";
                case 270 -> "west";
                default -> "north";
            };
        }
        LOGGER.info("rotToFacing: block={}, rot={}, facing={}", blockId, rot, result);
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState applyProperty(BlockState state, String propName, String propValue) {
        Property<?> property = findProperty(state, propName);
        if (property != null) {
            Optional<?> value = property.parse(propValue);
            if (value.isPresent()) {
                return state.with((Property) property, (Comparable) value.get());
            } else {
                LOGGER.warn("无法解析属性值: {}={}", propName, propValue);
            }
        }
        return state;
    }

    private static Property<?> findProperty(BlockState state, String name) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals(name)) {
                return prop;
            }
        }
        return null;
    }

    /**
     * 延迟放置的方块记录
     */
    private record DeferredBlock(BlockPos pos, BlueprintData.BlockEntry entry, int layerIdx, int row, int col) {}
}
