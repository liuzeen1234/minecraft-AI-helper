package com.example.helloworld.structure;

import com.example.helloworld.nbt.NbtStructureParser;
import com.example.helloworld.nbt.NbtStructureWriter;
import com.example.helloworld.selection.LitematicNbtWriter;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 StructureFormatConverter：验证 NBT / Litematic / TXT 三种结构文件格式
 * 之间任意方向的互相转换都能正确读写，并保留方块数据。
 */
class StructureFormatConverterTest {

    @TempDir
    Path tempDir;

    /** 构造一个简单的 2x1x1 结构：(0,0,0) 石头，(1,0,0) 木板。 */
    private NbtStructureParser.StructureData buildSimpleStructure() {
        NbtStructureParser.StructureData data = new NbtStructureParser.StructureData();
        data.fileName = "sample";
        data.sizeX = 2;
        data.sizeY = 1;
        data.sizeZ = 1;

        NbtStructureParser.PaletteEntry stone = new NbtStructureParser.PaletteEntry();
        stone.blockName = "minecraft:stone";
        NbtStructureParser.PaletteEntry planks = new NbtStructureParser.PaletteEntry();
        planks.blockName = "minecraft:oak_planks";

        data.palette.add(stone);  // index 0
        data.palette.add(planks); // index 1

        NbtStructureParser.BlockEntry b1 = new NbtStructureParser.BlockEntry();
        b1.x = 0; b1.y = 0; b1.z = 0; b1.paletteIndex = 0;
        data.blocks.add(b1);

        NbtStructureParser.BlockEntry b2 = new NbtStructureParser.BlockEntry();
        b2.x = 1; b2.y = 0; b2.z = 0; b2.paletteIndex = 1;
        data.blocks.add(b2);

        return data;
    }

    private File writeNbtFile(NbtStructureParser.StructureData data, String name) throws Exception {
        return NbtStructureWriter.writeToFile(data, tempDir, name).toFile();
    }

    private File writeLitematicFile(NbtStructureParser.StructureData data, String name) throws Exception {
        NbtCompound root = LitematicNbtWriter.create(data, name, "test");
        File file = tempDir.resolve(name + ".litematic").toFile();
        try (FileOutputStream fos = new FileOutputStream(file)) {
            NbtIo.writeCompressed(root, fos);
        }
        return file;
    }

    private File writeTxtFile(String name) throws Exception {
        String txt = "# MCBLUEPRINT v2\n"
                + "# name: " + name + "\n"
                + "# size: 2x1x1\n"
                + "# origin: 0,0,0\n"
                + "\n## BLOCKS\n\n"
                + "0,0,0   minecraft:stone\n"
                + "1,0,0   minecraft:oak_planks\n";
        File file = tempDir.resolve(name + ".txt").toFile();
        Files.writeString(file.toPath(), txt, StandardCharsets.UTF_8);
        return file;
    }

    private void assertHasStoneAndPlanks(NbtStructureParser.StructureData data) {
        assertEquals(2, data.sizeX);
        assertEquals(1, data.sizeY);
        assertEquals(1, data.sizeZ);

        boolean hasStone = false, hasPlanks = false;
        for (NbtStructureParser.BlockEntry b : data.blocks) {
            String name = data.palette.get(b.paletteIndex).blockName;
            if ("minecraft:stone".equals(name) && b.x == 0) hasStone = true;
            if ("minecraft:oak_planks".equals(name) && b.x == 1) hasPlanks = true;
        }
        assertTrue(hasStone, "应包含 (0,0,0) 处的石头");
        assertTrue(hasPlanks, "应包含 (1,0,0) 处的木板");
    }

    @Test
    void nbtToLitematic() throws Exception {
        File source = writeNbtFile(buildSimpleStructure(), "src_nbt");
        Path outDir = tempDir.resolve("out1");

        Path result = StructureFormatConverter.convert(source, StructureFormatConverter.Format.LITEMATIC, outDir);
        assertTrue(result.toString().endsWith(".litematic"));
        assertTrue(Files.exists(result));

        NbtStructureParser.StructureData parsed = com.example.helloworld.nbt.LitematicParser.parse(result.toFile());
        assertHasStoneAndPlanks(parsed);
    }

    @Test
    void nbtToTxt() throws Exception {
        File source = writeNbtFile(buildSimpleStructure(), "src_nbt2");
        Path outDir = tempDir.resolve("out2");

        Path result = StructureFormatConverter.convert(source, StructureFormatConverter.Format.TXT, outDir);
        assertTrue(result.toString().endsWith(".txt"));
        String text = Files.readString(result);
        assertTrue(text.contains("minecraft:stone"));
        assertTrue(text.contains("minecraft:oak_planks"));
    }

    @Test
    void litematicToNbt() throws Exception {
        File source = writeLitematicFile(buildSimpleStructure(), "src_lite");
        Path outDir = tempDir.resolve("out3");

        Path result = StructureFormatConverter.convert(source, StructureFormatConverter.Format.NBT, outDir);
        assertTrue(result.toString().endsWith(".nbt"));

        NbtStructureParser.StructureData parsed = NbtStructureParser.parse(result.toFile());
        assertHasStoneAndPlanks(parsed);
    }

    @Test
    void litematicToTxt() throws Exception {
        File source = writeLitematicFile(buildSimpleStructure(), "src_lite2");
        Path outDir = tempDir.resolve("out4");

        Path result = StructureFormatConverter.convert(source, StructureFormatConverter.Format.TXT, outDir);
        String text = Files.readString(result);
        assertTrue(text.contains("minecraft:stone"));
        assertTrue(text.contains("minecraft:oak_planks"));
    }

    @Test
    void txtToNbt() throws Exception {
        File source = writeTxtFile("src_txt");
        Path outDir = tempDir.resolve("out5");

        Path result = StructureFormatConverter.convert(source, StructureFormatConverter.Format.NBT, outDir);
        assertTrue(result.toString().endsWith(".nbt"));

        NbtStructureParser.StructureData parsed = NbtStructureParser.parse(result.toFile());
        assertHasStoneAndPlanks(parsed);
    }

    @Test
    void txtToLitematic() throws Exception {
        File source = writeTxtFile("src_txt2");
        Path outDir = tempDir.resolve("out6");

        Path result = StructureFormatConverter.convert(source, StructureFormatConverter.Format.LITEMATIC, outDir);
        assertTrue(result.toString().endsWith(".litematic"));

        NbtStructureParser.StructureData parsed = com.example.helloworld.nbt.LitematicParser.parse(result.toFile());
        assertHasStoneAndPlanks(parsed);
    }

    @Test
    void sameFormatCopiesFileUnchanged() throws Exception {
        File source = writeNbtFile(buildSimpleStructure(), "src_same");
        Path outDir = tempDir.resolve("out7");

        Path result = StructureFormatConverter.convert(source, StructureFormatConverter.Format.NBT, outDir);
        assertTrue(Files.exists(result));
        assertArrayEquals(Files.readAllBytes(source.toPath()), Files.readAllBytes(result));
    }
}
