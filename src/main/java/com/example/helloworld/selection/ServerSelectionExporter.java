package com.example.helloworld.selection;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

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
        root.put("entities", new NbtList());

        // 写入文件
        Path dir = Paths.get("nbts");
        if (!Files.isDirectory(dir)) {
            dir = Paths.get("..").resolve("nbts");
        }
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

        LOGGER.info("服务端导出 NBT 完成: {} ({}x{}x{}, {} 个方块, {} 个方块实体)",
                fileName, sizeX, sizeY, sizeZ, blocksList.size(), blockEntityCount);
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
}
