package com.example.helloworld.nbt;

import com.example.helloworld.blueprint.BlueprintData;
import com.example.helloworld.blueprint.BlueprintParser;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
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

    // ========== litematic/nbt → txt 兼容：容器物品 + 告示牌文字 ==========

    /** 构造一个含箱子（带物品）的结构：1x1x1，箱子在 (0,0,0)，含 2 个格子的物品。 */
    private NbtStructureParser.StructureData buildStructureWithChest() {
        NbtStructureParser.StructureData data = new NbtStructureParser.StructureData();
        data.fileName = "chest.nbt";
        data.sizeX = 1; data.sizeY = 1; data.sizeZ = 1;

        NbtStructureParser.PaletteEntry chest = new NbtStructureParser.PaletteEntry();
        chest.blockName = "minecraft:chest";
        data.palette.add(chest); // index 0

        NbtStructureParser.BlockEntry be = new NbtStructureParser.BlockEntry();
        be.x = 0; be.y = 0; be.z = 0; be.paletteIndex = 0;

        NbtCompound itemsNbt = new NbtCompound();
        NbtList items = new NbtList();

        NbtCompound item0 = new NbtCompound();
        item0.put("Slot", NbtByte.of((byte) 0));
        item0.putString("id", "minecraft:diamond");
        item0.put("Count", NbtByte.of((byte) 5));
        items.add(item0);

        NbtCompound item1 = new NbtCompound();
        item1.put("Slot", NbtByte.of((byte) 3));
        item1.putString("id", "minecraft:iron_ingot");
        item1.put("Count", NbtByte.of((byte) 12));
        NbtCompound tag = new NbtCompound();
        tag.putBoolean("Unbreakable", true);
        item1.put("tag", tag);
        items.add(item1);

        itemsNbt.put("Items", items);
        be.blockEntityNbt = itemsNbt;
        data.blocks.add(be);

        return data;
    }

    @Test
    void convert_includesContainerItems() {
        NbtStructureParser.StructureData data = buildStructureWithChest();
        String text = NbtToTxtConverter.convert(data, "chest_test");

        assertTrue(text.contains("items:"));
        assertTrue(text.contains("slot=0"));
        assertTrue(text.contains("minecraft:diamond"));
        assertTrue(text.contains("count=5"));
        assertTrue(text.contains("slot=3"));
        assertTrue(text.contains("minecraft:iron_ingot"));
        assertTrue(text.contains("count=12"));
        assertTrue(text.contains("nbt="));
        assertTrue(text.contains("Unbreakable"));
    }

    @Test
    void convert_containerItemsParseBackViaBlueprintParser() {
        NbtStructureParser.StructureData data = buildStructureWithChest();
        String text = NbtToTxtConverter.convert(data, "chest_test");

        BlueprintData parsed = BlueprintParser.parse(text);
        assertTrue(parsed.isV2());
        assertEquals(1, parsed.getBlocks3d().size());

        BlueprintData.BlockEntry3D chestEntry = parsed.getBlocks3d().get(0);
        assertTrue(chestEntry.hasItems());
        assertEquals(2, chestEntry.getItems().size());

        BlueprintData.ItemEntry diamond = chestEntry.getItems().get(0);
        assertEquals(0, diamond.getSlot());
        // BlueprintParser 解析物品行时会剥离 minecraft: 前缀（与方块 id 处理一致）
        assertEquals("diamond", diamond.getItemId());
        assertEquals(5, diamond.getCount());

        BlueprintData.ItemEntry iron = chestEntry.getItems().get(1);
        assertEquals(3, iron.getSlot());
        assertEquals("iron_ingot", iron.getItemId());
        assertEquals(12, iron.getCount());
        assertNotNull(iron.getNbtString());
        assertTrue(iron.getNbtString().contains("Unbreakable"));
    }

    @Test
    void convert_skipsEmptyContainer() {
        NbtStructureParser.StructureData data = new NbtStructureParser.StructureData();
        data.sizeX = 1; data.sizeY = 1; data.sizeZ = 1;
        NbtStructureParser.PaletteEntry chest = new NbtStructureParser.PaletteEntry();
        chest.blockName = "minecraft:chest";
        data.palette.add(chest);

        NbtStructureParser.BlockEntry be = new NbtStructureParser.BlockEntry();
        be.x = 0; be.y = 0; be.z = 0; be.paletteIndex = 0;
        NbtCompound itemsNbt = new NbtCompound();
        itemsNbt.put("Items", new NbtList()); // 空列表
        be.blockEntityNbt = itemsNbt;
        data.blocks.add(be);

        String text = NbtToTxtConverter.convert(data, "empty_chest");
        assertFalse(text.contains("items:"));
    }

    /** 构造一个含新版（1.20+）告示牌文字的结构。 */
    private NbtStructureParser.StructureData buildStructureWithSign(boolean legacyFormat) {
        NbtStructureParser.StructureData data = new NbtStructureParser.StructureData();
        data.sizeX = 1; data.sizeY = 1; data.sizeZ = 1;

        NbtStructureParser.PaletteEntry sign = new NbtStructureParser.PaletteEntry();
        sign.blockName = "minecraft:oak_sign";
        data.palette.add(sign);

        NbtStructureParser.BlockEntry be = new NbtStructureParser.BlockEntry();
        be.x = 0; be.y = 0; be.z = 0; be.paletteIndex = 0;

        NbtCompound signNbt = new NbtCompound();
        if (legacyFormat) {
            signNbt.putString("Text1", "{\"text\":\"Hello\"}");
            signNbt.putString("Text2", "{\"text\":\"World\"}");
            signNbt.putString("Text3", "{\"text\":\"\"}");
            signNbt.putString("Text4", "{\"text\":\"\"}");
        } else {
            NbtCompound frontText = new NbtCompound();
            NbtList frontMessages = new NbtList();
            frontMessages.add(NbtString.of("{\"text\":\"Front1\"}"));
            frontMessages.add(NbtString.of("{\"text\":\"Front2\"}"));
            frontMessages.add(NbtString.of("{\"text\":\"\"}"));
            frontMessages.add(NbtString.of("{\"text\":\"\"}"));
            frontText.put("messages", frontMessages);
            signNbt.put("front_text", frontText);

            NbtCompound backText = new NbtCompound();
            NbtList backMessages = new NbtList();
            backMessages.add(NbtString.of("{\"text\":\"Back1\"}"));
            backMessages.add(NbtString.of("{\"text\":\"\"}"));
            backMessages.add(NbtString.of("{\"text\":\"\"}"));
            backMessages.add(NbtString.of("{\"text\":\"\"}"));
            backText.put("messages", backMessages);
            signNbt.put("back_text", backText);
        }
        be.blockEntityNbt = signNbt;
        data.blocks.add(be);

        return data;
    }

    @Test
    void convert_includesSignTextNewFormat() {
        NbtStructureParser.StructureData data = buildStructureWithSign(false);
        String text = NbtToTxtConverter.convert(data, "sign_test");

        assertTrue(text.contains("sign_text:"));
        assertTrue(text.contains("front:"));
        assertTrue(text.contains("Front1"));
        assertTrue(text.contains("Front2"));
        assertTrue(text.contains("back:"));
        assertTrue(text.contains("Back1"));
    }

    @Test
    void convert_includesSignTextLegacyFormat() {
        NbtStructureParser.StructureData data = buildStructureWithSign(true);
        String text = NbtToTxtConverter.convert(data, "sign_legacy_test");

        assertTrue(text.contains("sign_text:"));
        assertTrue(text.contains("Hello"));
        assertTrue(text.contains("World"));
    }

    @Test
    void convert_signTextParsesBackViaBlueprintParser() {
        NbtStructureParser.StructureData data = buildStructureWithSign(false);
        String text = NbtToTxtConverter.convert(data, "sign_test");

        BlueprintData parsed = BlueprintParser.parse(text);
        assertEquals(1, parsed.getBlocks3d().size());

        BlueprintData.BlockEntry3D signEntry = parsed.getBlocks3d().get(0);
        assertTrue(signEntry.hasSignText());
        assertEquals("Front1", signEntry.getSignText().getFrontLines().get(0));
        assertEquals("Front2", signEntry.getSignText().getFrontLines().get(1));
        assertEquals("Back1", signEntry.getSignText().getBackLines().get(0));
    }

    @Test
    void convert_signTextWithBlankMiddleLineDoesNotTruncateBackText() {
        // front 第2、3行为空、第4行有字；back 第1行为空、第2行有字。
        // 这类中间出现空文字行的告示牌曾因 BlueprintParser 误判空行结束段而丢失后续文字。
        NbtStructureParser.StructureData data = new NbtStructureParser.StructureData();
        data.sizeX = 1; data.sizeY = 1; data.sizeZ = 1;
        NbtStructureParser.PaletteEntry sign = new NbtStructureParser.PaletteEntry();
        sign.blockName = "minecraft:oak_sign";
        data.palette.add(sign);

        NbtStructureParser.BlockEntry be = new NbtStructureParser.BlockEntry();
        be.x = 0; be.y = 0; be.z = 0; be.paletteIndex = 0;

        NbtCompound signNbt = new NbtCompound();
        NbtCompound frontText = new NbtCompound();
        NbtList frontMessages = new NbtList();
        frontMessages.add(NbtString.of("{\"text\":\"Line1\"}"));
        frontMessages.add(NbtString.of("{\"text\":\"\"}"));
        frontMessages.add(NbtString.of("{\"text\":\"\"}"));
        frontMessages.add(NbtString.of("{\"text\":\"Line4\"}"));
        frontText.put("messages", frontMessages);
        signNbt.put("front_text", frontText);

        NbtCompound backText = new NbtCompound();
        NbtList backMessages = new NbtList();
        backMessages.add(NbtString.of("{\"text\":\"\"}"));
        backMessages.add(NbtString.of("{\"text\":\"BackLine2\"}"));
        backMessages.add(NbtString.of("{\"text\":\"\"}"));
        backMessages.add(NbtString.of("{\"text\":\"\"}"));
        backText.put("messages", backMessages);
        signNbt.put("back_text", backText);

        be.blockEntityNbt = signNbt;
        data.blocks.add(be);

        String text = NbtToTxtConverter.convert(data, "blank_middle_line");
        BlueprintData parsed = BlueprintParser.parse(text);
        assertEquals(1, parsed.getBlocks3d().size());

        BlueprintData.BlockEntry3D signEntry = parsed.getBlocks3d().get(0);
        assertTrue(signEntry.hasSignText());
        assertEquals("Line1", signEntry.getSignText().getFrontLines().get(0));
        assertEquals("", signEntry.getSignText().getFrontLines().get(1));
        assertEquals("", signEntry.getSignText().getFrontLines().get(2));
        assertEquals("Line4", signEntry.getSignText().getFrontLines().get(3));
        assertEquals("", signEntry.getSignText().getBackLines().get(0));
        assertEquals("BackLine2", signEntry.getSignText().getBackLines().get(1));
    }

    @Test
    void convert_skipsSignWithoutText() {
        NbtStructureParser.StructureData data = new NbtStructureParser.StructureData();
        data.sizeX = 1; data.sizeY = 1; data.sizeZ = 1;
        NbtStructureParser.PaletteEntry sign = new NbtStructureParser.PaletteEntry();
        sign.blockName = "minecraft:oak_sign";
        data.palette.add(sign);

        NbtStructureParser.BlockEntry be = new NbtStructureParser.BlockEntry();
        be.x = 0; be.y = 0; be.z = 0; be.paletteIndex = 0;
        NbtCompound signNbt = new NbtCompound();
        NbtCompound frontText = new NbtCompound();
        NbtList frontMessages = new NbtList();
        for (int i = 0; i < 4; i++) frontMessages.add(NbtString.of("{\"text\":\"\"}"));
        frontText.put("messages", frontMessages);
        signNbt.put("front_text", frontText);
        be.blockEntityNbt = signNbt;
        data.blocks.add(be);

        String text = NbtToTxtConverter.convert(data, "blank_sign");
        assertFalse(text.contains("sign_text:"));
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
