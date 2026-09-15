package com.example.helloworld.selection;

import com.example.helloworld.nbt.LitematicParser;
import com.example.helloworld.nbt.NbtStructureParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LitematicNbtWriterTest {

    @Test
    void createsStandardLitematicThatRoundTripsThroughParser() {
        NbtStructureParser.StructureData source = new NbtStructureParser.StructureData();
        source.sizeX = 2;
        source.sizeY = 2;
        source.sizeZ = 1;
        source.dataVersion = 3700;
        source.palette.add(palette("minecraft:stone"));
        source.palette.add(palette("minecraft:oak_log", "axis", "y"));

        NbtStructureParser.BlockEntry stone = block(0, 0, 0, 0);
        NbtCompound chestNbt = new NbtCompound();
        chestNbt.putString("id", "minecraft:chest");
        chestNbt.putInt("x", 999);
        chestNbt.putInt("y", 999);
        chestNbt.putInt("z", 999);
        stone.blockEntityNbt = chestNbt;
        source.blocks.add(stone);
        source.blocks.add(block(1, 1, 0, 1));

        NbtStructureParser.EntityEntry entity = new NbtStructureParser.EntityEntry();
        entity.posX = 0.5;
        entity.posY = 1.0;
        entity.posZ = 0.5;
        entity.blockPosX = 0;
        entity.blockPosY = 1;
        entity.blockPosZ = 0;
        entity.entityNbt = new NbtCompound();
        entity.entityNbt.putString("id", "minecraft:armor_stand");
        source.entities.add(entity);

        NbtCompound root = LitematicNbtWriter.create(source, "test_export", "Test User");

        assertEquals(6, root.getInt("Version"));
        assertEquals(1, root.getInt("SubVersion"));
        assertEquals(3700, root.getInt("MinecraftDataVersion"));
        assertEquals("test_export", root.getCompound("Metadata").getString("Name"));
        NbtCompound region = root.getCompound("Regions").getCompound("main");
        assertEquals("minecraft:air", region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE)
                .getCompound(0).getString("Name"));
        assertTrue(region.contains("PendingBlockTicks", NbtElement.LIST_TYPE));
        assertTrue(region.contains("PendingFluidTicks", NbtElement.LIST_TYPE));

        NbtStructureParser.StructureData parsed = LitematicParser.parseNbt(root, "test_export.litematic");
        assertEquals(2, parsed.sizeX);
        assertEquals(2, parsed.sizeY);
        assertEquals(1, parsed.sizeZ);
        assertEquals(4, parsed.blocks.size());
        assertEquals(1, parsed.entities.size());
        assertEquals(0.5, parsed.entities.get(0).posX);
        assertEquals(0, parsed.blocks.stream()
                .filter(block -> block.blockEntityNbt != null)
                .findFirst()
                .orElseThrow()
                .blockEntityNbt.getInt("x"));
    }

    private static NbtStructureParser.PaletteEntry palette(String name, String... properties) {
        NbtStructureParser.PaletteEntry entry = new NbtStructureParser.PaletteEntry();
        entry.blockName = name;
        for (int index = 0; index < properties.length; index += 2) {
            entry.properties.put(properties[index], properties[index + 1]);
        }
        return entry;
    }

    private static NbtStructureParser.BlockEntry block(int x, int y, int z, int paletteIndex) {
        NbtStructureParser.BlockEntry entry = new NbtStructureParser.BlockEntry();
        entry.x = x;
        entry.y = y;
        entry.z = z;
        entry.paletteIndex = paletteIndex;
        return entry;
    }
}
