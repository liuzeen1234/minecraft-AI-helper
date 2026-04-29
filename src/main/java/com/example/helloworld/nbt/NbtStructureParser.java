package com.example.helloworld.nbt;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 解析 Minecraft 原版结构 NBT 文件（由结构方块保存的 .nbt 文件）。
 *
 * NBT 结构文件的格式：
 * - size: [x, y, z] 结构尺寸
 * - palette: 方块状态调色板列表（单调色板格式）
 * - palettes: 多调色板列表（多调色板格式，如 shipwreck 等原版结构使用）
 * - blocks: 方块列表，每个包含 pos、state（调色板索引）、nbt（可选的方块实体数据）
 * - entities: 实体列表（可选）
 * - DataVersion: 数据版本号
 */
public class NbtStructureParser {

    private static final Logger LOGGER = LoggerFactory.getLogger("NbtStructureParser");

    /**
     * 解析结果：包含结构的所有信息
     */
    public static class StructureData {
        public int sizeX, sizeY, sizeZ;
        public List<PaletteEntry> palette = new ArrayList<>();
        public List<BlockEntry> blocks = new ArrayList<>();
        public int dataVersion;
        public String fileName;

        @Override
        public String toString() {
            return "Structure[" + fileName + "] size=" + sizeX + "x" + sizeY + "x" + sizeZ
                    + " palette=" + palette.size() + " blocks=" + blocks.size();
        }
    }

    /**
     * 调色板条目：一个方块状态
     */
    public static class PaletteEntry {
        public String blockName;
        public Map<String, String> properties = new HashMap<>();

        @Override
        public String toString() {
            if (properties.isEmpty()) return blockName;
            return blockName + properties;
        }
    }

    /**
     * 方块条目：位置 + 调色板索引 + 可选的 NBT 数据
     */
    public static class BlockEntry {
        public int x, y, z;
        public int paletteIndex;
        public NbtCompound blockEntityNbt; // 可能为 null

        public BlockPos getPos() {
            return new BlockPos(x, y, z);
        }
    }

    /**
     * 从文件解析 NBT 结构
     */
    public static StructureData parse(File file) throws Exception {
        NbtCompound root;
        try (FileInputStream fis = new FileInputStream(file)) {
            root = NbtIo.readCompressed(fis, NbtSizeTracker.ofUnlimitedBytes());
        }
        return parseNbt(root, file.getName());
    }

    /**
     * 从路径解析 NBT 结构
     */
    public static StructureData parse(Path path) throws Exception {
        return parse(path.toFile());
    }

    /**
     * 解析 NbtCompound 为 StructureData
     */
    public static StructureData parseNbt(NbtCompound root, String fileName) {
        StructureData data = new StructureData();
        data.fileName = fileName;

        // 解析尺寸
        if (root.contains("size", NbtElement.LIST_TYPE)) {
            NbtList sizeList = root.getList("size", NbtElement.INT_TYPE);
            data.sizeX = sizeList.getInt(0);
            data.sizeY = sizeList.getInt(1);
            data.sizeZ = sizeList.getInt(2);
        }

        // 解析数据版本
        if (root.contains("DataVersion")) {
            data.dataVersion = root.getInt("DataVersion");
        }

        // 解析调色板（支持 palette 单调色板和 palettes 多调色板两种格式）
        NbtList paletteList = null;
        if (root.contains("palette", NbtElement.LIST_TYPE)) {
            // 单调色板格式：palette 直接是一个 Compound 列表
            paletteList = root.getList("palette", NbtElement.COMPOUND_TYPE);
        } else if (root.contains("palettes", NbtElement.LIST_TYPE)) {
            // 多调色板格式：palettes 是一个列表的列表，取第一个调色板
            NbtList palettesList = root.getList("palettes", NbtElement.LIST_TYPE);
            if (!palettesList.isEmpty()) {
                // palettes 中的每个元素本身是一个 List<Compound>
                paletteList = palettesList.getList(0);
                LOGGER.info("使用多调色板格式 (palettes)，共 {} 个变体，取第一个 ({} 种方块)",
                        palettesList.size(), paletteList.size());
            }
        }

        if (paletteList != null) {
            for (int i = 0; i < paletteList.size(); i++) {
                NbtCompound entry = paletteList.getCompound(i);
                PaletteEntry pe = new PaletteEntry();
                pe.blockName = entry.getString("Name");

                if (entry.contains("Properties", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound props = entry.getCompound("Properties");
                    for (String key : props.getKeys()) {
                        pe.properties.put(key, props.getString(key));
                    }
                }
                data.palette.add(pe);
            }
        }

        // 解析方块列表
        if (root.contains("blocks", NbtElement.LIST_TYPE)) {
            NbtList blocksList = root.getList("blocks", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < blocksList.size(); i++) {
                NbtCompound blockNbt = blocksList.getCompound(i);
                BlockEntry be = new BlockEntry();

                NbtList pos = blockNbt.getList("pos", NbtElement.INT_TYPE);
                be.x = pos.getInt(0);
                be.y = pos.getInt(1);
                be.z = pos.getInt(2);

                be.paletteIndex = blockNbt.getInt("state");

                if (blockNbt.contains("nbt", NbtElement.COMPOUND_TYPE)) {
                    be.blockEntityNbt = blockNbt.getCompound("nbt");
                }

                data.blocks.add(be);
            }
        }

        LOGGER.info("解析完成: {} ({}x{}x{}, {} 个方块, {} 种方块类型)",
                fileName, data.sizeX, data.sizeY, data.sizeZ,
                data.blocks.size(), data.palette.size());

        return data;
    }

    /**
     * 递归扫描目录下所有 .nbt 文件并解析（包括子文件夹）
     */
    public static List<StructureData> parseAll(Path directory) {
        List<StructureData> results = new ArrayList<>();
        File dir = directory.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            LOGGER.warn("NBT 目录不存在: {}", directory);
            return results;
        }

        try (Stream<Path> walk = Files.walk(directory)) {
            List<Path> nbtFiles = walk
                    .filter(p -> p.toString().endsWith(".nbt"))
                    .filter(Files::isRegularFile)
                    .toList();

            for (Path p : nbtFiles) {
                try {
                    StructureData data = parse(p.toFile());
                    // 用相对路径作为文件名，方便识别子文件夹来源
                    data.fileName = directory.relativize(p).toString().replace('\\', '/');
                    results.add(data);
                } catch (Exception e) {
                    LOGGER.error("解析 NBT 文件失败: {}", p.getFileName(), e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("扫描 NBT 目录失败: {}", directory, e);
        }

        return results;
    }

    /**
     * 生成结构的可读摘要
     */
    public static String getSummary(StructureData data) {
        StringBuilder sb = new StringBuilder();
        sb.append("§e结构: §f").append(data.fileName).append("\n");
        sb.append("§e尺寸: §f").append(data.sizeX).append(" x ").append(data.sizeY).append(" x ").append(data.sizeZ).append("\n");
        sb.append("§e方块数: §f").append(data.blocks.size()).append("\n");
        sb.append("§e方块类型 (").append(data.palette.size()).append("):\n");

        // 统计每种方块的数量
        Map<Integer, Integer> counts = new HashMap<>();
        for (BlockEntry block : data.blocks) {
            counts.merge(block.paletteIndex, 1, Integer::sum);
        }

        for (int i = 0; i < data.palette.size(); i++) {
            PaletteEntry pe = data.palette.get(i);
            int count = counts.getOrDefault(i, 0);
            // 跳过空气
            if (pe.blockName.equals("minecraft:air")) continue;
            sb.append("§7  - §f").append(pe.blockName);
            if (!pe.properties.isEmpty()) {
                sb.append(" §8").append(pe.properties);
            }
            sb.append(" §7x").append(count).append("\n");
        }

        return sb.toString();
    }
}
