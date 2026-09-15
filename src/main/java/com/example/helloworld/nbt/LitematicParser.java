package com.example.helloworld.nbt;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析 Litematica 模组的 .litematic 结构文件。
 *
 * <p>.litematic 同样是 gzip 压缩的 NBT，但内部结构与原版结构 .nbt 完全不同：
 * <pre>
 * root
 * ├── MinecraftDataVersion : int
 * ├── Version              : int
 * ├── Metadata             : { Name, Description, Author, EnclosingSize{x,y,z}, ... }
 * └── Regions              : {
 *       "&lt;区域名&gt;" : {
 *          Position          : { x, y, z }      区域原点相对结构原点的偏移
 *          Size              : { x, y, z }      可为负数（表示向负方向延伸）
 *          BlockStatePalette : [ { Name, Properties }, ... ]
 *          BlockStates       : long[]           位压缩的调色板索引数组
 *          TileEntities      : [ { x, y, z, ... } ]   方块实体（区域局部坐标）
 *          Entities          : [ { Pos:[x,y,z], ... } ] 实体（区域局部坐标）
 *       }, ...
 *     }
 * </pre>
 *
 * <p>本解析器将上述数据转换为 {@link NbtStructureParser.StructureData}，
 * 从而可以直接复用现有的 {@link NbtStructurePlacer#place} 放置逻辑。
 *
 * <p>位解包算法参考 Litematica 官方格式（与 litemapy 的 {@code LitematicaBitArray} 一致）：
 * 每个方块占用 {@code nbits = max(ceil(log2(paletteSize)), 2)} 位，
 * 区域内索引 {@code ind = y*abs(w*l) + z*abs(w) + x}。
 */
public class LitematicParser {

    private static final Logger LOGGER = LoggerFactory.getLogger("LitematicParser");

    /** 从文件解析 .litematic 结构。 */
    public static NbtStructureParser.StructureData parse(File file) throws Exception {
        NbtCompound root;
        try (FileInputStream fis = new FileInputStream(file)) {
            root = NbtIo.readCompressed(fis, NbtSizeTracker.ofUnlimitedBytes());
        }
        return parseNbt(root, file.getName());
    }

    /** 从路径解析 .litematic 结构。 */
    public static NbtStructureParser.StructureData parse(Path path) throws Exception {
        return parse(path.toFile());
    }

    /**
     * 将 Litematica 根 NBT 转换为通用的 {@link NbtStructureParser.StructureData}。
     *
     * <p>Litematica 可以包含多个区域（Regions），每个区域有各自的原点偏移与调色板。
     * 这里把所有区域的方块统一到同一套调色板与坐标系中：
     * 先计算所有区域在结构坐标系下的最小角，作为整体原点（归一化为非负相对坐标），
     * 再把每个区域的方块平移到该坐标系。
     */
    public static NbtStructureParser.StructureData parseNbt(NbtCompound root, String fileName) {
        NbtStructureParser.StructureData data = new NbtStructureParser.StructureData();
        data.fileName = fileName;

        if (root.contains("MinecraftDataVersion")) {
            data.dataVersion = root.getInt("MinecraftDataVersion");
        }

        if (!root.contains("Regions", NbtElement.COMPOUND_TYPE)) {
            LOGGER.warn(".litematic 文件缺少 Regions: {}", fileName);
            return data;
        }
        NbtCompound regions = root.getCompound("Regions");

        // 全局调色板去重：blockName+properties 序列化后的字符串 -> 在 data.palette 中的索引
        java.util.Map<String, Integer> paletteKeyToIndex = new java.util.HashMap<>();

        // 第一遍：计算所有区域在结构坐标系中的整体最小角，用于归一化到非负坐标
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        List<RegionBounds> boundsList = new ArrayList<>();
        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompound(regionName);
            int px = readAxis(region, "Position", "x");
            int py = readAxis(region, "Position", "y");
            int pz = readAxis(region, "Position", "z");
            int sx = readAxis(region, "Size", "x");
            int sy = readAxis(region, "Size", "y");
            int sz = readAxis(region, "Size", "z");

            // Size 可能为负：负数表示区域向坐标负方向延伸。
            // 计算该区域在结构坐标系下占据的坐标区间 [lo, hi]。
            int loX = Math.min(px, px + sx + (sx < 0 ? 1 : -1));
            int hiX = Math.max(px, px + sx + (sx < 0 ? 1 : -1));
            int loY = Math.min(py, py + sy + (sy < 0 ? 1 : -1));
            int hiY = Math.max(py, py + sy + (sy < 0 ? 1 : -1));
            int loZ = Math.min(pz, pz + sz + (sz < 0 ? 1 : -1));
            int hiZ = Math.max(pz, pz + sz + (sz < 0 ? 1 : -1));

            minX = Math.min(minX, loX); maxX = Math.max(maxX, hiX);
            minY = Math.min(minY, loY); maxY = Math.max(maxY, hiY);
            minZ = Math.min(minZ, loZ); maxZ = Math.max(maxZ, hiZ);

            RegionBounds rb = new RegionBounds();
            rb.name = regionName;
            rb.px = px; rb.py = py; rb.pz = pz;
            rb.sx = sx; rb.sy = sy; rb.sz = sz;
            rb.loX = loX; rb.loY = loY; rb.loZ = loZ;
            boundsList.add(rb);
        }

        if (boundsList.isEmpty()) {
            return data;
        }

        data.sizeX = maxX - minX + 1;
        data.sizeY = maxY - minY + 1;
        data.sizeZ = maxZ - minZ + 1;

        // 第二遍：解析每个区域的调色板与方块，平移到归一化坐标系
        for (RegionBounds rb : boundsList) {
            NbtCompound region = regions.getCompound(rb.name);

            // --- 调色板 ---
            List<NbtStructureParser.PaletteEntry> localPalette = new ArrayList<>();
            NbtList paletteList = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < paletteList.size(); i++) {
                NbtCompound entry = paletteList.getCompound(i);
                NbtStructureParser.PaletteEntry pe = new NbtStructureParser.PaletteEntry();
                pe.blockName = entry.getString("Name");
                if (entry.contains("Properties", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound props = entry.getCompound("Properties");
                    for (String key : props.getKeys()) {
                        pe.properties.put(key, props.getString(key));
                    }
                }
                localPalette.add(pe);
            }

            // 将局部调色板映射到全局调色板索引（去重）
            int[] localToGlobal = new int[localPalette.size()];
            for (int i = 0; i < localPalette.size(); i++) {
                NbtStructureParser.PaletteEntry pe = localPalette.get(i);
                String key = paletteKey(pe);
                Integer gi = paletteKeyToIndex.get(key);
                if (gi == null) {
                    gi = data.palette.size();
                    data.palette.add(pe);
                    paletteKeyToIndex.put(key, gi);
                }
                localToGlobal[i] = gi;
            }

            // --- 方块状态位数组 ---
            int absW = Math.abs(rb.sx);
            int absH = Math.abs(rb.sy);
            int absL = Math.abs(rb.sz);
            long volume = (long) absW * absH * absL;

            if (volume > 0 && region.contains("BlockStates", NbtElement.LONG_ARRAY_TYPE)
                    && !localPalette.isEmpty()) {
                long[] blockStates = region.getLongArray("BlockStates");
                int nbits = Math.max(bitsNeeded(localPalette.size()), 2);
                LitematicaBitArray bitArray = new LitematicaBitArray(volume, nbits, blockStates);

                for (int x = 0; x < absW; x++) {
                    for (int y = 0; y < absH; y++) {
                        for (int z = 0; z < absL; z++) {
                            long ind = (long) y * absW * absL + (long) z * absW + x;
                            int localIndex = bitArray.get(ind);
                            if (localIndex < 0 || localIndex >= localToGlobal.length) {
                                continue;
                            }

                            // 区域局部坐标 (x,y,z) -> 结构坐标系
                            // Size 为负时，坐标从 Position 向负方向增长
                            int structX = rb.px + (rb.sx < 0 ? -x : x);
                            int structY = rb.py + (rb.sy < 0 ? -y : y);
                            int structZ = rb.pz + (rb.sz < 0 ? -z : z);

                            NbtStructureParser.BlockEntry be = new NbtStructureParser.BlockEntry();
                            be.x = structX - minX;
                            be.y = structY - minY;
                            be.z = structZ - minZ;
                            be.paletteIndex = localToGlobal[localIndex];
                            data.blocks.add(be);
                        }
                    }
                }
            }

            // --- 方块实体（TileEntities） ---
            // Litematica 的 TileEntity NBT 直接携带 x/y/z（区域局部坐标）与方块实体数据字段。
            if (region.contains("TileEntities", NbtElement.LIST_TYPE)) {
                NbtList tileEntities = region.getList("TileEntities", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < tileEntities.size(); i++) {
                    NbtCompound te = tileEntities.getCompound(i);
                    int lx = te.getInt("x");
                    int ly = te.getInt("y");
                    int lz = te.getInt("z");

                    int structX = rb.px + lx;
                    int structY = rb.py + ly;
                    int structZ = rb.pz + lz;
                    int relX = structX - minX;
                    int relY = structY - minY;
                    int relZ = structZ - minZ;

                    // 找到该坐标对应的方块条目，附加方块实体 NBT
                    NbtStructureParser.BlockEntry target = findBlockAt(data, relX, relY, relZ);
                    if (target != null) {
                        target.blockEntityNbt = te.copy();
                    }
                }
            }

            // --- 实体（Entities） ---
            if (region.contains("Entities", NbtElement.LIST_TYPE)) {
                NbtList entities = region.getList("Entities", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < entities.size(); i++) {
                    NbtCompound ent = entities.getCompound(i);
                    NbtStructureParser.EntityEntry ee = new NbtStructureParser.EntityEntry();

                    // 实体的 Pos 是区域局部精确坐标（double）
                    double ex = 0, ey = 0, ez = 0;
                    if (ent.contains("Pos", NbtElement.LIST_TYPE)) {
                        NbtList pos = ent.getList("Pos", NbtElement.DOUBLE_TYPE);
                        ex = pos.getDouble(0);
                        ey = pos.getDouble(1);
                        ez = pos.getDouble(2);
                    }
                    // 转换到归一化相对坐标：区域原点 + 局部坐标 - 整体最小角
                    ee.posX = rb.px + ex - minX;
                    ee.posY = rb.py + ey - minY;
                    ee.posZ = rb.pz + ez - minZ;
                    ee.blockPosX = (int) Math.floor(ee.posX);
                    ee.blockPosY = (int) Math.floor(ee.posY);
                    ee.blockPosZ = (int) Math.floor(ee.posZ);

                    // Litematica 的实体 NBT 使用 "id" 字段标识类型，可直接复用
                    ee.entityNbt = ent.copy();
                    data.entities.add(ee);
                }
            }
        }

        LOGGER.info("解析 .litematic 完成: {} ({}x{}x{}, {} 个方块, {} 种方块类型, {} 个实体, {} 个区域)",
                fileName, data.sizeX, data.sizeY, data.sizeZ,
                data.blocks.size(), data.palette.size(), data.entities.size(), boundsList.size());

        return data;
    }

    /** 在已解析的方块列表中查找指定相对坐标的方块条目（用于附加方块实体 NBT）。 */
    private static NbtStructureParser.BlockEntry findBlockAt(NbtStructureParser.StructureData data, int x, int y, int z) {
        // 从后往前找（同一坐标通常唯一，最近添加的更可能匹配）
        for (int i = data.blocks.size() - 1; i >= 0; i--) {
            NbtStructureParser.BlockEntry be = data.blocks.get(i);
            if (be.x == x && be.y == y && be.z == z) {
                return be;
            }
        }
        return null;
    }

    /** 读取 region 下某个 compound（Position/Size）的某个轴分量。 */
    private static int readAxis(NbtCompound region, String compoundName, String axis) {
        if (region.contains(compoundName, NbtElement.COMPOUND_TYPE)) {
            return region.getCompound(compoundName).getInt(axis);
        }
        return 0;
    }

    /** 计算表示 paletteSize 个不同索引所需的最少位数（至少 1，实际使用时再取 max(_,2)）。 */
    private static int bitsNeeded(int paletteSize) {
        if (paletteSize <= 1) return 1;
        return 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
    }

    /** 生成调色板去重键。 */
    private static String paletteKey(NbtStructureParser.PaletteEntry pe) {
        if (pe.properties.isEmpty()) return pe.blockName;
        // TreeMap 保证属性顺序一致，避免相同状态因顺序不同被判为不同
        return pe.blockName + new java.util.TreeMap<>(pe.properties);
    }

    private static class RegionBounds {
        String name;
        int px, py, pz;    // Position
        int sx, sy, sz;    // Size（可为负）
        int loX, loY, loZ; // 该区域在结构坐标系下的最小角
    }

    /**
     * Litematica 位压缩数组的只读解包实现。
     *
     * <p>与 litemapy 的 {@code LitematicaBitArray.__getitem__} 一致：
     * 每个元素占 {@code nbits} 位，可能跨越两个 long。
     * long 按无符号处理。
     */
    private static class LitematicaBitArray {
        private final long[] longArray;
        private final int nbits;
        private final long maxEntryValue;
        private final long size;

        LitematicaBitArray(long size, int nbits, long[] longArray) {
            this.size = size;
            this.nbits = nbits;
            this.longArray = longArray;
            this.maxEntryValue = (1L << nbits) - 1L;
        }

        int get(long index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("Invalid index " + index);
            }
            long startOffset = index * nbits;
            int startArrIndex = (int) (startOffset >> 6);
            int endArrIndex = (int) (((index + 1) * nbits - 1) >> 6);
            int startBitOffset = (int) (startOffset & 0x3F);

            if (startArrIndex == endArrIndex) {
                return (int) ((longArray[startArrIndex] >>> startBitOffset) & maxEntryValue);
            } else {
                int endOffset = 64 - startBitOffset;
                long value = (longArray[startArrIndex] >>> startBitOffset)
                        | (longArray[endArrIndex] << endOffset);
                return (int) (value & maxEntryValue);
            }
        }
    }
}
