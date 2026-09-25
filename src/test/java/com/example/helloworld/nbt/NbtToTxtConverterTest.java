package com.example.helloworld.nbt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 NbtToTxtConverter：将 NbtStructureParser.StructureData 转换为
 * MCBLUEPRINT v2 格式的 txt 文本，以及批量转换目录的行为。
 */
class NbtToTxtConverterTest {

    @TempDir
    Path tempDir;

    /** 构造一个简单的结构数据：1x2x1，底层是石头，顶层是空气（应被跳过）。 */
    private NbtStructureParser.StructureData buildSimpleStructure() {
        NbtStructureParser.StructureData data = new NbtStructureParser.StructureData();
        data.fileName = "test.nbt";
        data.sizeX = 1;
        data.sizeY = 2;
        data.sizeZ = 1;

        NbtStructureParser.PaletteEntry stone = new NbtStructureParser.PaletteEntry();
        stone.blockName = "minecraft:stone";

        NbtStructureParser.PaletteEntry air = new NbtStructureParser.PaletteEntry();
        air.blockName = "minecraft:air";

        NbtStructureParser.PaletteEntry stairs = new NbtStructureParser.PaletteEntry();
        stairs.blockName = "minecraft:stone_stairs";
        stairs.properties.put("facing", "north");
        stairs.properties.put("waterlogged", "false");

        data.palette.add(stone);   // index 0
        data.palette.add(air);     // index 1
        data.palette.add(stairs);  // index 2

        NbtStructureParser.BlockEntry b1 = new NbtStructureParser.BlockEntry();
        b1.x = 0; b1.y = 0; b1.z = 0; b1.paletteIndex = 0; // stone
        data.blocks.add(b1);

        NbtStructureParser.BlockEntry b2 = new NbtStructureParser.BlockEntry();
        b2.x = 0; b2.y = 1; b2.z = 0; b2.paletteIndex = 1; // air, 应被跳过
        data.blocks.add(b2);

        NbtStructureParser.BlockEntry b3 = new NbtStructureParser.BlockEntry();
        b3.x = 0; b3.y = 0; b3.z = 0; b3.paletteIndex = 2; // 覆盖同一格：楼梯（带属性）
        data.blocks.add(b3);

        return data;
    }

    @Test
    void convert_includesHeaderAndSize() {
        NbtStructureParser.StructureData data = buildSimpleStructure();
        String text = NbtToTxtConverter.convert(data, "myname");

        assertTrue(text.contains("# MCBLUEPRINT v2"));
        assertTrue(text.contains("# name: myname"));
        assertTrue(text.contains("# size: 1x2x1"));
    }

    @Test
    void convert_skipsAirBlocks() {
        NbtStructureParser.StructureData data = buildSimpleStructure();
        String text = NbtToTxtConverter.convert(data, "myname");

        assertFalse(text.contains("minecraft:air"));
    }

    @Test
    void convert_includesBlockPropertiesInline() {
        NbtStructureParser.StructureData data = buildSimpleStructure();
        String text = NbtToTxtConverter.convert(data, "myname");

        assertTrue(text.contains("minecraft:stone_stairs"));
        assertTrue(text.contains("facing=north"));
        assertTrue(text.contains("waterlogged=false"));
    }

    @Test
    void convertDirectory_convertsAllNbtFilesRecursively() throws Exception {
        Path srcDir = tempDir.resolve("src");
        Path subDir = srcDir.resolve("sub");
        Files.createDirectories(subDir);

        writeMinimalNbt(srcDir.resolve("a.nbt"));
        writeMinimalNbt(subDir.resolve("b.nbt"));
        // 非结构文件应被忽略
        Files.writeString(srcDir.resolve("readme.txt"), "not a structure");

        Path targetDir = tempDir.resolve("out");
        int count = NbtToTxtConverter.convertDirectory(srcDir, targetDir);

        assertEquals(2, count);
        assertTrue(Files.exists(targetDir.resolve("a.txt")));
        assertTrue(Files.exists(targetDir.resolve("sub").resolve("b.txt")));
    }

    @Test
    void convertToFile_writesTxtWithSameBaseName() throws Exception {
        Path nbtFile = tempDir.resolve("mystructure.nbt");
        writeMinimalNbt(nbtFile);

        Path targetDir = tempDir.resolve("out");
        Path written = NbtToTxtConverter.convertToFile(nbtFile.toFile(), targetDir);

        assertEquals("mystructure.txt", written.getFileName().toString());
        String content = Files.readString(written, StandardCharsets.UTF_8);
        assertTrue(content.contains("# name: mystructure"));
    }

    /** 写一个最小可解析的原版结构 NBT 文件（含 size/palette/blocks），用于文件级测试。 */
    private void writeMinimalNbt(Path path) throws IOException {
        net.minecraft.nbt.NbtCompound root = new net.minecraft.nbt.NbtCompound();

        net.minecraft.nbt.NbtList size = new net.minecraft.nbt.NbtList();
        size.add(net.minecraft.nbt.NbtInt.of(1));
        size.add(net.minecraft.nbt.NbtInt.of(1));
        size.add(net.minecraft.nbt.NbtInt.of(1));
        root.put("size", size);

        net.minecraft.nbt.NbtCompound stoneState = new net.minecraft.nbt.NbtCompound();
        stoneState.putString("Name", "minecraft:stone");
        net.minecraft.nbt.NbtList palette = new net.minecraft.nbt.NbtList();
        palette.add(stoneState);
        root.put("palette", palette);

        net.minecraft.nbt.NbtCompound block = new net.minecraft.nbt.NbtCompound();
        net.minecraft.nbt.NbtList pos = new net.minecraft.nbt.NbtList();
        pos.add(net.minecraft.nbt.NbtInt.of(0));
        pos.add(net.minecraft.nbt.NbtInt.of(0));
        pos.add(net.minecraft.nbt.NbtInt.of(0));
        block.put("pos", pos);
        block.putInt("state", 0);
        net.minecraft.nbt.NbtList blocks = new net.minecraft.nbt.NbtList();
        blocks.add(block);
        root.put("blocks", blocks);

        root.putInt("DataVersion", 3465);

        Files.createDirectories(path.getParent());
        try (var fos = new java.io.FileOutputStream(path.toFile())) {
            net.minecraft.nbt.NbtIo.writeCompressed(root, fos);
        }
    }
}
