package com.example.helloworld.nbt;

import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link LitematicParser} 对 .litematic 文件的解析准确性。
 *
 * <p>覆盖两个层面：
 * <ol>
 *   <li>手工构造已知内容的 NBT（含 Version=7，即 Litematica 新版格式使用的版本号），
 *       断言解析结果与预期完全一致，证明解析器不依赖顶层 Version 字段、
 *       仅依赖 Regions/BlockStatePalette/BlockStates 等实际数据结构；</li>
 *   <li>解析工作区内真实存在的 .litematic 文件，确认不抛异常且基础字段合理。</li>
 * </ol>
 */
class LitematicParserTest {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------------
    // 手工构造：单区域，2x2x1，已知调色板与方块布局
    // ------------------------------------------------------------------

    /**
     * 构造一个 2(x) x 2(y) x 1(z) 的区域：
     *   (0,0,0)=stone  (1,0,0)=air
     *   (0,1,0)=oak_planks  (1,1,0)=stone
     * 顶层显式写 Version=7 / SubVersion=1 / MinecraftDataVersion=3700，
     * 模拟新版 Litematica 客户端导出的格式版本号。
     */
    private File writeKnownLitematic(String fileName) throws Exception {
        NbtCompound root = new NbtCompound();
        root.putInt("Version", 7);
        root.putInt("SubVersion", 1);
        root.putInt("MinecraftDataVersion", 3700);

        NbtCompound region = new NbtCompound();

        NbtCompound position = new NbtCompound();
        position.putInt("x", 0);
        position.putInt("y", 0);
        position.putInt("z", 0);
        region.put("Position", position);

        NbtCompound size = new NbtCompound();
        size.putInt("x", 2);
        size.putInt("y", 2);
        size.putInt("z", 1);
        region.put("Size", size);

        // 调色板：0=air 1=stone 2=oak_planks
        NbtList palette = new NbtList();
        NbtCompound air = new NbtCompound();
        air.putString("Name", "minecraft:air");
        palette.add(air);
        NbtCompound stone = new NbtCompound();
        stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        NbtCompound planks = new NbtCompound();
        planks.putString("Name", "minecraft:oak_planks");
        palette.add(planks);
        region.put("BlockStatePalette", palette);

        // 索引布局（region 局部坐标，ind = y*absW*absL + z*absW + x）：
        // absW=2, absL=1
        // (0,0,0)=1(stone) (1,0,0)=0(air) (0,1,0)=2(planks) (1,1,0)=1(stone)
        int[] indices = new int[]{1, 0, 2, 1};
        int nbits = 2; // palette.size()=3 -> bitsNeeded=2, max(_,2)=2
        long[] packed = packIndices(indices, nbits);
        region.putLongArray("BlockStates", packed);

        // 一个方块实体：在 (0,1,0)（planks 位置，仅用于验证坐标对应，无实际语义）
        NbtList tileEntities = new NbtList();
        NbtCompound te = new NbtCompound();
        te.putInt("x", 0);
        te.putInt("y", 1);
        te.putInt("z", 0);
        te.putString("id", "minecraft:test_marker");
        tileEntities.add(te);
        region.put("TileEntities", tileEntities);

        region.put("Entities", new NbtList());

        NbtCompound regions = new NbtCompound();
        regions.put("main", region);
        root.put("Regions", regions);

        File file = tempDir.resolve(fileName).toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            NbtIo.writeCompressed(root, fos);
        }
        return file;
    }

    /** 按 LitematicaBitArray 的打包规则（每元素 nbits 位，小端位偏移，可跨 long）手动打包，作为独立的 ground truth。 */
    private static long[] packIndices(int[] indices, int nbits) {
        long[] packed = new long[(int) (((long) indices.length * nbits + 63) / 64)];
        long mask = (1L << nbits) - 1L;
        for (int i = 0; i < indices.length; i++) {
            long value = indices[i] & mask;
            long bitOffset = (long) i * nbits;
            int longIndex = (int) (bitOffset >>> 6);
            int offsetInLong = (int) (bitOffset & 63);
            packed[longIndex] |= value << offsetInLong;
            if (offsetInLong + nbits > 64) {
                packed[longIndex + 1] |= value >>> (64 - offsetInLong);
            }
        }
        return packed;
    }

    @Test
    void parse_readsKnownLayoutRegardlessOfVersionField() throws Exception {
        File file = writeKnownLitematic("known.litematic");

        NbtStructureParser.StructureData data = LitematicParser.parse(file);

        assertEquals(2, data.sizeX);
        assertEquals(2, data.sizeY);
        assertEquals(1, data.sizeZ);
        assertEquals(3, data.palette.size(), "调色板应包含 air/stone/oak_planks 三种");
        assertEquals(4, data.blocks.size(), "2x2x1=4 个方块位置都应被记录（含 air）");
    }

    @Test
    void parse_blockPositionsAndPaletteMatchKnownLayout() throws Exception {
        File file = writeKnownLitematic("known2.litematic");
        NbtStructureParser.StructureData data = LitematicParser.parse(file);

        String blockAt00 = blockNameAt(data, 0, 0, 0);
        String blockAt10 = blockNameAt(data, 1, 0, 0);
        String blockAt01 = blockNameAt(data, 0, 1, 0);
        String blockAt11 = blockNameAt(data, 1, 1, 0);

        assertEquals("minecraft:stone", blockAt00);
        assertEquals("minecraft:air", blockAt10);
        assertEquals("minecraft:oak_planks", blockAt01);
        assertEquals("minecraft:stone", blockAt11);
    }

    @Test
    void parse_attachesTileEntityAtCorrectPosition() throws Exception {
        File file = writeKnownLitematic("known3.litematic");
        NbtStructureParser.StructureData data = LitematicParser.parse(file);

        NbtStructureParser.BlockEntry target = null;
        for (NbtStructureParser.BlockEntry be : data.blocks) {
            if (be.x == 0 && be.y == 1 && be.z == 0) {
                target = be;
                break;
            }
        }
        assertNotNull(target, "应存在坐标 (0,1,0) 的方块条目");
        assertNotNull(target.blockEntityNbt, "该坐标应附带方块实体 NBT");
        assertEquals("minecraft:test_marker", target.blockEntityNbt.getString("id"));
    }

    @Test
    void parse_readsMinecraftDataVersionFromRoot() throws Exception {
        File file = writeKnownLitematic("known4.litematic");
        NbtStructureParser.StructureData data = LitematicParser.parse(file);

        assertEquals(3700, data.dataVersion);
    }

    private static String blockNameAt(NbtStructureParser.StructureData data, int x, int y, int z) {
        for (NbtStructureParser.BlockEntry be : data.blocks) {
            if (be.x == x && be.y == y && be.z == z) {
                return data.palette.get(be.paletteIndex).blockName;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 真实文件：解析工作区内已存在的 .litematic，确认不抛异常且字段合理
    // ------------------------------------------------------------------

    @Test
    void parse_realWorldFileParsesWithoutException() throws Exception {
        Path realFile = Path.of(
                "run/ai-helper/structures/litematic/100k竖式生炸抗弱抗卸载刷石机v2.2（cha）.litematic");
        assumeFileExists(realFile);

        NbtStructureParser.StructureData data = LitematicParser.parse(realFile.toFile());

        assertNotNull(data);
        assertTrue(data.sizeX > 0 && data.sizeY > 0 && data.sizeZ > 0,
                "解析出的尺寸应为正数: " + data);
    }

    @Test
    void parse_realWorldFile_cathedral() throws Exception {
        Path realFile = Path.of("run/ai-helper/structures/litematic/cathedral-final-v1.0.litematic");
        assumeFileExists(realFile);

        NbtStructureParser.StructureData data = LitematicParser.parse(realFile.toFile());

        assertNotNull(data);
        assertTrue(data.sizeX > 0 && data.sizeY > 0 && data.sizeZ > 0);
        assertFalse(data.palette.isEmpty(), "真实结构文件应包含非空调色板");
        assertFalse(data.blocks.isEmpty(), "真实结构文件应包含方块数据");
    }

    /** 若目标文件在当前工作区不存在（例如未同步该样本），跳过而非失败。 */
    private static void assumeFileExists(Path path) {
        org.junit.jupiter.api.Assumptions.assumeTrue(path.toFile().exists(),
                "测试样本文件不存在，跳过: " + path);
    }
}
