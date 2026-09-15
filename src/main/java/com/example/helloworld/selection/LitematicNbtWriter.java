package com.example.helloworld.selection;

import com.example.helloworld.nbt.NbtStructureParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the project's normalized structure data to Litematica's gzip-NBT schema.
 *
 * <p>The exported schematic contains one positive-size region rooted at (0, 0, 0).
 * Block states use Litematica's packed long-array representation and palette index zero
 * is always {@code minecraft:air}, as required by the format.</p>
 */
public final class LitematicNbtWriter {

    private static final int LITEMATIC_VERSION = 6;
    private static final int LITEMATIC_SUB_VERSION = 1;
    private static final int MINECRAFT_1_20_4_DATA_VERSION = 3700;
    private static final String REGION_NAME = "main";

    private LitematicNbtWriter() {
    }

    /**
     * Builds a standard Litematica root NBT compound from a normalized structure.
     *
     * @param data normalized structure data with zero-based block coordinates
     * @param name user-visible schematic name
     * @param author schematic author metadata
     * @return a root compound ready for {@code NbtIo.writeCompressed}
     */
    public static NbtCompound create(NbtStructureParser.StructureData data, String name, String author) {
        validateDimensions(data);

        long volume = (long) data.sizeX * data.sizeY * data.sizeZ;
        if (volume > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Selection is too large for Litematica export: " + volume + " blocks");
        }

        PaletteData paletteData = createPalette(data);
        int[] blockStateIndices = new int[(int) volume];
        for (NbtStructureParser.BlockEntry block : data.blocks) {
            if (!isWithinBounds(block.x, block.y, block.z, data)) {
                continue;
            }
            if (block.paletteIndex < 0 || block.paletteIndex >= paletteData.sourceToLitematic.length) {
                continue;
            }
            blockStateIndices[linearIndex(block.x, block.y, block.z, data.sizeX, data.sizeZ)] =
                    paletteData.sourceToLitematic[block.paletteIndex];
        }

        NbtCompound root = new NbtCompound();
        root.putInt("Version", LITEMATIC_VERSION);
        root.putInt("SubVersion", LITEMATIC_SUB_VERSION);
        root.putInt("MinecraftDataVersion", data.dataVersion > 0
                ? data.dataVersion
                : MINECRAFT_1_20_4_DATA_VERSION);
        root.put("Metadata", createMetadata(data, name, author, blockStateIndices));

        NbtCompound regions = new NbtCompound();
        regions.put(REGION_NAME, createRegion(data, paletteData.palette, blockStateIndices));
        root.put("Regions", regions);
        return root;
    }

    private static NbtCompound createMetadata(NbtStructureParser.StructureData data, String name, String author,
                                               int[] blockStateIndices) {
        long now = System.currentTimeMillis();
        int nonAirBlocks = 0;
        for (int stateIndex : blockStateIndices) {
            if (stateIndex != 0) {
                nonAirBlocks++;
            }
        }

        NbtCompound metadata = new NbtCompound();
        NbtCompound enclosingSize = new NbtCompound();
        enclosingSize.putInt("x", data.sizeX);
        enclosingSize.putInt("y", data.sizeY);
        enclosingSize.putInt("z", data.sizeZ);
        metadata.put("EnclosingSize", enclosingSize);
        metadata.putString("Name", name);
        metadata.putString("Author", author);
        metadata.putString("Description", "Exported by AI Builder");
        metadata.putString("Software", "AI Builder");
        metadata.putInt("RegionCount", 1);
        metadata.putLong("TimeCreated", now);
        metadata.putLong("TimeModified", now);
        metadata.putInt("TotalBlocks", nonAirBlocks);
        metadata.putInt("TotalVolume", blockStateIndices.length);
        metadata.put("PreviewImageData", new NbtIntArray(new int[0]));
        return metadata;
    }

    private static NbtCompound createRegion(NbtStructureParser.StructureData data, NbtList palette,
                                             int[] blockStateIndices) {
        NbtCompound region = new NbtCompound();
        region.put("Position", coordinates(0, 0, 0));
        region.put("Size", coordinates(data.sizeX, data.sizeY, data.sizeZ));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", packBlockStates(blockStateIndices, palette.size()));
        region.put("TileEntities", createTileEntities(data));
        region.put("Entities", createEntities(data));
        region.put("PendingBlockTicks", new NbtList());
        region.put("PendingFluidTicks", new NbtList());
        return region;
    }

    private static PaletteData createPalette(NbtStructureParser.StructureData data) {
        NbtList palette = new NbtList();
        palette.add(blockState("minecraft:air", Map.of()));
        int[] sourceToLitematic = new int[data.palette.size()];
        Map<String, Integer> litematicIndexByState = new HashMap<>();
        litematicIndexByState.put(paletteKey("minecraft:air", Map.of()), 0);

        for (int sourceIndex = 0; sourceIndex < data.palette.size(); sourceIndex++) {
            NbtStructureParser.PaletteEntry source = data.palette.get(sourceIndex);
            String blockName = source.blockName == null || source.blockName.isBlank()
                    ? "minecraft:air"
                    : source.blockName;
            String key = paletteKey(blockName, source.properties);
            Integer litematicIndex = litematicIndexByState.get(key);
            if (litematicIndex == null) {
                litematicIndex = palette.size();
                palette.add(blockState(blockName, source.properties));
                litematicIndexByState.put(key, litematicIndex);
            }
            sourceToLitematic[sourceIndex] = litematicIndex;
        }
        return new PaletteData(palette, sourceToLitematic);
    }

    private static NbtCompound blockState(String blockName, Map<String, String> properties) {
        NbtCompound state = new NbtCompound();
        state.putString("Name", blockName);
        if (!properties.isEmpty()) {
            NbtCompound propertyNbt = new NbtCompound();
            properties.forEach(propertyNbt::putString);
            state.put("Properties", propertyNbt);
        }
        return state;
    }

    private static NbtList createTileEntities(NbtStructureParser.StructureData data) {
        NbtList tileEntities = new NbtList();
        for (NbtStructureParser.BlockEntry block : data.blocks) {
            if (block.blockEntityNbt == null || !isWithinBounds(block.x, block.y, block.z, data)) {
                continue;
            }
            NbtCompound tileEntity = block.blockEntityNbt.copy();
            tileEntity.putInt("x", block.x);
            tileEntity.putInt("y", block.y);
            tileEntity.putInt("z", block.z);
            tileEntities.add(tileEntity);
        }
        return tileEntities;
    }

    private static NbtList createEntities(NbtStructureParser.StructureData data) {
        NbtList entities = new NbtList();
        for (NbtStructureParser.EntityEntry source : data.entities) {
            if (source.entityNbt == null) {
                continue;
            }
            NbtCompound entity = source.entityNbt.copy();
            NbtList position = new NbtList();
            position.add(NbtDouble.of(source.posX));
            position.add(NbtDouble.of(source.posY));
            position.add(NbtDouble.of(source.posZ));
            entity.put("Pos", position);
            if (entity.contains("TileX", NbtElement.INT_TYPE)) {
                entity.putInt("TileX", source.blockPosX);
                entity.putInt("TileY", source.blockPosY);
                entity.putInt("TileZ", source.blockPosZ);
            }
            entities.add(entity);
        }
        return entities;
    }

    private static long[] packBlockStates(int[] blockStateIndices, int paletteSize) {
        int bitsPerBlock = Math.max(bitsNeeded(paletteSize), 2);
        long[] packed = new long[(int) (((long) blockStateIndices.length * bitsPerBlock + 63) / 64)];
        long valueMask = (1L << bitsPerBlock) - 1L;

        for (int index = 0; index < blockStateIndices.length; index++) {
            long value = blockStateIndices[index] & valueMask;
            long bitOffset = (long) index * bitsPerBlock;
            int longIndex = (int) (bitOffset >>> 6);
            int offsetInLong = (int) (bitOffset & 63);
            packed[longIndex] |= value << offsetInLong;
            if (offsetInLong + bitsPerBlock > 64) {
                packed[longIndex + 1] |= value >>> (64 - offsetInLong);
            }
        }
        return packed;
    }

    private static int bitsNeeded(int paletteSize) {
        return paletteSize <= 1 ? 1 : 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
    }

    private static NbtCompound coordinates(int x, int y, int z) {
        NbtCompound coordinates = new NbtCompound();
        coordinates.putInt("x", x);
        coordinates.putInt("y", y);
        coordinates.putInt("z", z);
        return coordinates;
    }

    private static boolean isWithinBounds(int x, int y, int z, NbtStructureParser.StructureData data) {
        return x >= 0 && x < data.sizeX && y >= 0 && y < data.sizeY && z >= 0 && z < data.sizeZ;
    }

    private static int linearIndex(int x, int y, int z, int width, int length) {
        return y * width * length + z * width + x;
    }

    private static String paletteKey(String blockName, Map<String, String> properties) {
        return blockName + new java.util.TreeMap<>(properties);
    }

    private static void validateDimensions(NbtStructureParser.StructureData data) {
        if (data == null || data.sizeX <= 0 || data.sizeY <= 0 || data.sizeZ <= 0) {
            throw new IllegalArgumentException("Litematica export requires positive structure dimensions");
        }
    }

    private record PaletteData(NbtList palette, int[] sourceToLitematic) {
    }
}
