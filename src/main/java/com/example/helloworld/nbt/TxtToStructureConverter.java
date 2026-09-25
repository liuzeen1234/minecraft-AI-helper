package com.example.helloworld.nbt;

import com.example.helloworld.blueprint.BlueprintData;
import com.example.helloworld.blueprint.BlueprintParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.StringNbtReader;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 MCBLUEPRINT v2（.txt）文本解析结果 {@link BlueprintData} 转换为统一的
 * {@link NbtStructureParser.StructureData}，使 TXT 能够复用现有的
 * NBT/Litematic 写出器（{@link NbtStructureWriter} / Litematica 写出器）。
 *
 * 与 {@link NbtToTxtConverter}（StructureData → TXT）互为逆操作。
 *
 * 仅支持 V2 格式（本 mod 唯一在用的 TXT 蓝图格式）；V1（字符网格）蓝图
 * 语义上不含显式坐标/尺寸，不适合作为结构文件互转的输入，遇到 V1 会抛出异常。
 */
public final class TxtToStructureConverter {

    private TxtToStructureConverter() {}

    /** 读取 .txt 文件并转换为 StructureData。 */
    public static NbtStructureParser.StructureData convert(File file) throws Exception {
        String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        BlueprintData blueprint = BlueprintParser.parse(text);
        return convert(blueprint, file.getName());
    }

    /** 将已解析的 {@link BlueprintData}（须为 V2）转换为 StructureData。 */
    public static NbtStructureParser.StructureData convert(BlueprintData blueprint, String fileName) {
        if (!blueprint.isV2()) {
            throw new IllegalArgumentException("仅支持 MCBLUEPRINT v2 格式的 TXT 转换为结构文件（V1 字符网格格式不含显式坐标/尺寸）");
        }

        NbtStructureParser.StructureData data = new NbtStructureParser.StructureData();
        data.fileName = fileName;
        data.sizeX = blueprint.getSizeX();
        data.sizeY = blueprint.getSizeY();
        data.sizeZ = blueprint.getSizeZ();
        data.dataVersion = 0; // 未知，写出端会回退到默认版本

        // palette 去重：按 "blockId + properties" 作为 key
        Map<String, Integer> paletteIndex = new LinkedHashMap<>();

        for (BlueprintData.BlockEntry3D entry : blueprint.getBlocks3d()) {
            String blockId = withNamespace(entry.getBlockId());
            Map<String, String> properties = entry.getProperties() != null
                    ? entry.getProperties() : Map.of();

            String key = paletteKey(blockId, properties);
            int index = paletteIndex.computeIfAbsent(key, k -> {
                NbtStructureParser.PaletteEntry pe = new NbtStructureParser.PaletteEntry();
                pe.blockName = blockId;
                pe.properties = new LinkedHashMap<>(properties);
                data.palette.add(pe);
                return data.palette.size() - 1;
            });

            NbtStructureParser.BlockEntry be = new NbtStructureParser.BlockEntry();
            be.x = entry.getX();
            be.y = entry.getY();
            be.z = entry.getZ();
            be.paletteIndex = index;
            be.blockEntityNbt = buildBlockEntityNbt(entry);
            data.blocks.add(be);
        }

        return data;
    }

    /** 若方块行携带容器物品或告示牌文字，拼出对应的方块实体 NBT；否则返回 null。 */
    private static NbtCompound buildBlockEntityNbt(BlueprintData.BlockEntry3D entry) {
        NbtCompound nbt = null;

        if (entry.hasItems()) {
            nbt = new NbtCompound();
            NbtList items = new NbtList();
            for (BlueprintData.ItemEntry item : entry.getItems()) {
                NbtCompound itemNbt = new NbtCompound();
                itemNbt.putByte("Slot", (byte) item.getSlot());
                itemNbt.putString("id", withNamespace(item.getItemId()));
                itemNbt.putByte("Count", (byte) item.getCount());
                if (item.getNbtString() != null && !item.getNbtString().isBlank()) {
                    try {
                        itemNbt.put("tag", StringNbtReader.parse(item.getNbtString()));
                    } catch (Exception ignored) {
                        // 忽略无法解析的 SNBT，物品其余字段仍保留
                    }
                }
                items.add(itemNbt);
            }
            nbt.put("Items", items);
        }

        if (entry.hasSignText()) {
            if (nbt == null) nbt = new NbtCompound();
            BlueprintData.SignTextEntry signText = entry.getSignText();
            nbt.put("front_text", buildSignTextCompound(signText.getFrontLines()));
            nbt.put("back_text", buildSignTextCompound(signText.getBackLines()));
        }

        return nbt;
    }

    private static NbtCompound buildSignTextCompound(List<String> lines) {
        NbtCompound textNbt = new NbtCompound();
        NbtList messages = new NbtList();
        for (int i = 0; i < 4; i++) {
            String line = i < lines.size() ? lines.get(i) : "";
            String json = line.isEmpty() ? "{\"text\":\"\"}" : "{\"text\":\"" + escapeJson(line) + "\"}";
            messages.add(NbtString.of(json));
        }
        textNbt.put("messages", messages);
        return textNbt;
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String withNamespace(String id) {
        if (id == null || id.isEmpty()) return "minecraft:air";
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static String paletteKey(String blockName, Map<String, String> properties) {
        return blockName + new java.util.TreeMap<>(properties);
    }
}
