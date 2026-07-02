package com.example.helloworld.selection;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 服务端选区导出器：在服务端扫描选区方块并导出为 NBT 文件。
 * 因为在服务端执行，可以完整读取 BlockEntity 数据（箱子内容、告示牌文字、熔炉物品等）。
 */
public class ServerSelectionExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerSelectionExporter");

    /**
     * 在服务端扫描选区并导出为 NBT 文件（含完整 BlockEntity 数据）。
     */
    @SuppressWarnings("unchecked")
    public static void exportNbt(ServerWorld world, BlockPos pos1, BlockPos pos2, String name) throws IOException {
        exportNbt(world, pos1, pos2, name, "");
    }

    /**
     * 在服务端扫描选区并导出为 NBT 文件（含完整 BlockEntity 数据），可指定子目录。
     */
    @SuppressWarnings("unchecked")
    public static void exportNbt(ServerWorld world, BlockPos pos1, BlockPos pos2, String name, String subPath) throws IOException {
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

        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", 3465);

        // size
        NbtList sizeList = new NbtList();
        sizeList.add(NbtInt.of(sizeX));
        sizeList.add(NbtInt.of(sizeY));
        sizeList.add(NbtInt.of(sizeZ));
        root.put("size", sizeList);

        // 构建调色板和方块列表
        Map<String, Integer> paletteIndexMap = new LinkedHashMap<>();
        NbtList paletteList = new NbtList();
        NbtList blocksList = new NbtList();

        // 空气占索引 0
        NbtCompound airEntry = new NbtCompound();
        airEntry.putString("Name", "minecraft:air");
        paletteList.add(airEntry);
        paletteIndexMap.put("minecraft:air", 0);

        int blockEntityCount = 0;

        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (state.isAir()) continue;

                    String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                    Map<String, String> props = new LinkedHashMap<>();
                    for (Property<?> prop : state.getProperties()) {
                        props.put(prop.getName(), getPropertyValueString(state, prop));
                    }

                    // 构建调色板键
                    String paletteKey = buildPaletteKey(blockId, props);
                    if (!paletteIndexMap.containsKey(paletteKey)) {
                        int idx = paletteList.size();
                        paletteIndexMap.put(paletteKey, idx);

                        NbtCompound pe = new NbtCompound();
                        pe.putString("Name", blockId);
                        if (!props.isEmpty()) {
                            NbtCompound propsNbt = new NbtCompound();
                            for (Map.Entry<String, String> e : props.entrySet()) {
                                propsNbt.putString(e.getKey(), e.getValue());
                            }
                            pe.put("Properties", propsNbt);
                        }
                        paletteList.add(pe);
                    }

                    int stateIdx = paletteIndexMap.get(paletteKey);

                    NbtCompound blockNbt = new NbtCompound();
                    NbtList posNbt = new NbtList();
                    posNbt.add(NbtInt.of(x - min.getX()));
                    posNbt.add(NbtInt.of(y - min.getY()));
                    posNbt.add(NbtInt.of(z - min.getZ()));
                    blockNbt.put("pos", posNbt);
                    blockNbt.putInt("state", stateIdx);

                    // 读取 BlockEntity 数据（箱子内容、告示牌文字、熔炉物品等）
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    if (blockEntity != null) {
                        NbtCompound beNbt = blockEntity.createNbt();
                        // 移除坐标信息（放置时会重新设置）
                        beNbt.remove("x");
                        beNbt.remove("y");
                        beNbt.remove("z");
                        // 保留 id 以便识别类型
                        blockNbt.put("nbt", beNbt);
                        blockEntityCount++;
                    }

                    blocksList.add(blockNbt);
                }
            }
        }

        root.put("palette", paletteList);
        root.put("blocks", blocksList);

        // 扫描选区内的装饰类实体（画、物品展示框、盔甲架、矿车、船等），排除生物和玩家
        NbtList entitiesList = new NbtList();
        Box selectionBox = new Box(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0, max.getY() + 1.0, max.getZ() + 1.0);

        List<Entity> entities = world.getEntitiesByClass(Entity.class, selectionBox,
                ServerSelectionExporter::isExportableEntity);

        for (Entity entity : entities) {
            NbtCompound entityEntry = new NbtCompound();

            // pos: 相对精确坐标 (double)
            NbtList posTag = new NbtList();
            posTag.add(NbtDouble.of(entity.getX() - min.getX()));
            posTag.add(NbtDouble.of(entity.getY() - min.getY()));
            posTag.add(NbtDouble.of(entity.getZ() - min.getZ()));
            entityEntry.put("pos", posTag);

            // blockPos: 相对方块坐标 (int)
            NbtList blockPosTag = new NbtList();
            blockPosTag.add(NbtInt.of(entity.getBlockPos().getX() - min.getX()));
            blockPosTag.add(NbtInt.of(entity.getBlockPos().getY() - min.getY()));
            blockPosTag.add(NbtInt.of(entity.getBlockPos().getZ() - min.getZ()));
            entityEntry.put("blockPos", blockPosTag);

            // nbt: 实体完整数据
            NbtCompound nbt = new NbtCompound();
            entity.writeNbt(nbt);
            // 保留实体类型 id
            nbt.putString("id", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
            // 移除绝对坐标和 UUID（放置时重新计算）
            nbt.remove("Pos");
            nbt.remove("UUID");
            entityEntry.put("nbt", nbt);

            entitiesList.add(entityEntry);
        }
        root.put("entities", entitiesList);

        // 写入文件
        Path dir = com.example.helloworld.ModPaths.getNbtsDir();
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }

        // 如果指定了子目录，追加到路径
        if (subPath != null && !subPath.isEmpty()) {
            dir = dir.resolve(subPath);
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
        }

        String fileName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".nbt";
        File file = dir.resolve(fileName).toFile();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            NbtIo.writeCompressed(root, fos);
        }

        LOGGER.info("服务端导出 NBT 完成: {} ({}x{}x{}, {} 个方块, {} 个方块实体, {} 个实体)",
                fileName, sizeX, sizeY, sizeZ, blocksList.size(), blockEntityCount, entitiesList.size());
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyValueString(BlockState state, Property<T> prop) {
        return prop.name(state.get(prop));
    }

    private static String buildPaletteKey(String fullBlockId, Map<String, String> properties) {
        if (properties.isEmpty()) return fullBlockId;
        StringBuilder sb = new StringBuilder(fullBlockId);
        for (Map.Entry<String, String> e : properties.entrySet()) {
            sb.append("|").append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    /**
     * 判断一个实体是否应被导出（只保留装饰/载具类，排除生物和玩家）。
     * 保留：画、物品展示框、荧光物品展示框、盔甲架、矿车、船等。
     * 排除：玩家、所有 MobEntity（僵尸、骷髅、村民、动物等）、抛射物。
     */
    private static boolean isExportableEntity(Entity entity) {
        if (entity instanceof PlayerEntity) return false;
        if (entity instanceof MobEntity) return false;
        if (entity instanceof ProjectileEntity) return false;

        // 明确保留的类型
        if (entity instanceof AbstractDecorationEntity) return true;  // 画、物品展示框
        if (entity instanceof ArmorStandEntity) return true;           // 盔甲架
        if (entity instanceof AbstractMinecartEntity) return true;     // 矿车
        if (entity instanceof BoatEntity) return true;                 // 船

        // 其他非生物实体也保留（如末影水晶、展示实体等）
        return true;
    }

    // =========================================================================
    // TXT 导出（含容器内容物）
    // =========================================================================

    /**
     * 在服务端扫描选区并导出为 TXT 文件（MCBLUEPRINT v2 格式，含容器内容物）。
     */
    public static void exportTxt(ServerWorld world, BlockPos pos1, BlockPos pos2, String name, String subPath) throws IOException {
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

        StringBuilder sb = new StringBuilder();

        // 文件头
        sb.append("# MCBLUEPRINT v2\n");
        sb.append("# name: ").append(name).append("\n");
        sb.append("# size: ").append(sizeX).append("x").append(sizeY).append("x").append(sizeZ).append("\n");
        sb.append("# origin: 0,0,0\n");
        sb.append("# 坐标原点在结构西北角最低层，x向东，y向上，z向南\n");
        sb.append("# 格式：x,y,z  block_id  [key=value ...]\n");
        sb.append("\n");
        sb.append("## BLOCKS\n");
        sb.append("\n");

        int blockCount = 0;
        int containerCount = 0;

        // 按 y 层分组输出
        for (int y = min.getY(); y <= max.getY(); y++) {
            int relY = y - min.getY();
            boolean layerHeaderWritten = false;

            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (state.isAir()) continue;

                    // 写层头（延迟到有非空气方块时才写）
                    if (!layerHeaderWritten) {
                        sb.append("# --- 第 ").append(relY + 1).append(" 层 (y=").append(relY).append(") ---\n");
                        layerHeaderWritten = true;
                    }

                    int relX = x - min.getX();
                    int relZ = z - min.getZ();
                    String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();

                    sb.append(relX).append(",").append(relY).append(",").append(relZ);
                    sb.append("   ").append(blockId);

                    // 输出所有属性
                    for (Property<?> prop : state.getProperties()) {
                        sb.append("   ").append(prop.getName()).append("=").append(getPropertyValueString(state, prop));
                    }
                    sb.append("\n");
                    blockCount++;

                    // 检查容器内容物
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    if (blockEntity instanceof Inventory inv && inv.size() > 0) {
                        List<String> itemLines = new ArrayList<>();
                        for (int slot = 0; slot < inv.size(); slot++) {
                            ItemStack stack = inv.getStack(slot);
                            if (stack.isEmpty()) continue;
                            String itemId = Registries.ITEM.getId(stack.getItem()).getPath();
                            StringBuilder itemLine = new StringBuilder();
                            itemLine.append("    slot=").append(slot);
                            itemLine.append("  ").append(itemId);
                            itemLine.append("  count=").append(stack.getCount());
                            if (stack.hasNbt()) {
                                itemLine.append("  nbt=").append(stack.getNbt().toString());
                            }
                            itemLines.add(itemLine.toString());
                        }
                        if (!itemLines.isEmpty()) {
                            sb.append("  items:\n");
                            for (String itemLine : itemLines) {
                                sb.append(itemLine).append("\n");
                            }
                            containerCount++;
                        }
                    }
                }
            }

            if (layerHeaderWritten) {
                sb.append("\n");
            }
        }

        // 写入文件
        Path dir = com.example.helloworld.ModPaths.getTxtsDir();
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }

        // 如果指定了子目录，追加到路径
        if (subPath != null && !subPath.isEmpty()) {
            dir = dir.resolve(subPath);
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
        }

        String fileName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".txt";
        Path filePath = dir.resolve(fileName);
        Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);

        LOGGER.info("服务端导出 TXT 完成: {} ({}x{}x{}, {} 个方块, {} 个容器)",
                fileName, sizeX, sizeY, sizeZ, blockCount, containerCount);
    }
}
