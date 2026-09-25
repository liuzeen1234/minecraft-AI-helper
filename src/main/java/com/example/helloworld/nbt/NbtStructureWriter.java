package com.example.helloworld.nbt;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 将统一的 {@link NbtStructureParser.StructureData} 写出为原版结构方块 .nbt 格式。
 *
 * 与 {@link NbtStructureParser#parseNbt} 互为逆操作，输出结构：
 *   size: [x, y, z]
 *   palette: [{Name, Properties?}, ...]
 *   blocks: [{pos:[x,y,z], state: index, nbt?: 方块实体}, ...]
 *   entities: [{pos:[x,y,z], blockPos:[x,y,z], nbt: 实体数据}, ...]
 *   DataVersion: int
 *
 * 只依赖 {@link NbtStructureParser.StructureData}，不需要运行时 World，可离线使用，
 * 因此可以作为 Litematic/TXT → NBT 转换的写出端。
 */
public final class NbtStructureWriter {

    /** 未指定来源数据版本时使用的兜底值（对应 1.20.4）。 */
    private static final int DEFAULT_DATA_VERSION = 3700;

    private NbtStructureWriter() {}

    /** 将结构数据转换为原版结构 NBT 的根 Compound。 */
    public static NbtCompound create(NbtStructureParser.StructureData data) {
        if (data == null || data.sizeX <= 0 || data.sizeY <= 0 || data.sizeZ <= 0) {
            throw new IllegalArgumentException("导出 NBT 结构需要合法的正尺寸");
        }

        NbtCompound root = new NbtCompound();

        NbtList size = new NbtList();
        size.add(NbtInt.of(data.sizeX));
        size.add(NbtInt.of(data.sizeY));
        size.add(NbtInt.of(data.sizeZ));
        root.put("size", size);

        root.put("palette", createPalette(data));
        root.put("blocks", createBlocks(data));
        root.put("entities", createEntities(data));
        root.putInt("DataVersion", data.dataVersion > 0 ? data.dataVersion : DEFAULT_DATA_VERSION);

        return root;
    }

    private static NbtList createPalette(NbtStructureParser.StructureData data) {
        NbtList palette = new NbtList();
        for (NbtStructureParser.PaletteEntry entry : data.palette) {
            NbtCompound state = new NbtCompound();
            String blockName = entry.blockName == null || entry.blockName.isBlank()
                    ? "minecraft:air" : entry.blockName;
            state.putString("Name", blockName);
            if (entry.properties != null && !entry.properties.isEmpty()) {
                NbtCompound props = new NbtCompound();
                // 保持稳定顺序，避免多次导出结果不一致
                Map<String, String> sorted = new HashMap<>(entry.properties);
                sorted.forEach(props::putString);
                state.put("Properties", props);
            }
            palette.add(state);
        }
        return palette;
    }

    private static NbtList createBlocks(NbtStructureParser.StructureData data) {
        NbtList blocks = new NbtList();
        for (NbtStructureParser.BlockEntry block : data.blocks) {
            NbtCompound blockNbt = new NbtCompound();

            NbtList pos = new NbtList();
            pos.add(NbtInt.of(block.x));
            pos.add(NbtInt.of(block.y));
            pos.add(NbtInt.of(block.z));
            blockNbt.put("pos", pos);

            blockNbt.putInt("state", block.paletteIndex);

            if (block.blockEntityNbt != null) {
                blockNbt.put("nbt", block.blockEntityNbt.copy());
            }

            blocks.add(blockNbt);
        }
        return blocks;
    }

    private static NbtList createEntities(NbtStructureParser.StructureData data) {
        NbtList entities = new NbtList();
        for (NbtStructureParser.EntityEntry entry : data.entities) {
            if (entry.entityNbt == null) continue;

            NbtCompound entityNbt = new NbtCompound();

            NbtList pos = new NbtList();
            pos.add(NbtDouble.of(entry.posX));
            pos.add(NbtDouble.of(entry.posY));
            pos.add(NbtDouble.of(entry.posZ));
            entityNbt.put("pos", pos);

            NbtList blockPos = new NbtList();
            blockPos.add(NbtInt.of(entry.blockPosX));
            blockPos.add(NbtInt.of(entry.blockPosY));
            blockPos.add(NbtInt.of(entry.blockPosZ));
            entityNbt.put("blockPos", blockPos);

            entityNbt.put("nbt", entry.entityNbt.copy());
            entities.add(entityNbt);
        }
        return entities;
    }

    /**
     * 将结构数据写入 .nbt 文件（gzip 压缩，与结构方块保存格式一致）。
     *
     * @return 写入的文件路径
     */
    public static Path writeToFile(NbtStructureParser.StructureData data, Path targetDir, String baseName) throws IOException {
        NbtCompound root = create(data);

        if (!Files.isDirectory(targetDir)) {
            Files.createDirectories(targetDir);
        }
        Path targetFile = targetDir.resolve(baseName + ".nbt");
        try (FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {
            NbtIo.writeCompressed(root, fos);
        }
        return targetFile;
    }

    /** {@link #writeToFile(NbtStructureParser.StructureData, Path, String)} 的 {@link File} 版本重载。 */
    public static File writeToFile(NbtStructureParser.StructureData data, File targetDir, String baseName) throws IOException {
        return writeToFile(data, targetDir.toPath(), baseName).toFile();
    }
}
