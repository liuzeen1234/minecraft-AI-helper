package com.example.helloworld.nbt;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 将 NBT 结构文件（.nbt / .litematic）转换为 MCBLUEPRINT v2 格式的 .txt 文本。
 *
 * 输出格式与 {@code SelectionAnalyzer#exportBlueprintV2} / {@code ServerSelectionExporter} 一致：
 *   # MCBLUEPRINT v2
 *   # name: xxx
 *   # size: WxHxD
 *   # origin: 0,0,0
 *   ## BLOCKS
 *   x,y,z   block_id   [key=value ...]
 *     items:
 *       slot=N  item_id  count=C  [nbt={...}]
 *     sign_text:
 *       front:
 *         line1
 *       back:
 *         line1
 *
 * 空气方块（minecraft:air / cave_air / void_air）不写入，以节省 token。
 * 容器方块实体的物品内容（Items 列表）与告示牌文字（新版 front_text/back_text，
 * 或旧版 Text1~Text4）会从 {@code BlockEntry#blockEntityNbt} 中提取并写成
 * {@code items:} / {@code sign_text:} 段，使转换结果能被 {@code BlueprintParser}
 * 完整解析回容器物品与告示牌文字（与 NBT/Litematica 放置管线的还原效果一致）。
 */
public class NbtToTxtConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger("NbtToTxtConverter");

    private NbtToTxtConverter() {}

    /**
     * 将单个 NBT 结构文件转换为 MCBLUEPRINT v2 文本。
     *
     * @param file NBT 结构文件（.nbt 或 .litematic）
     * @param name 写入文件头的结构名（通常取文件名去掉扩展名）
     */
    public static String convert(File file, String name) throws Exception {
        NbtStructureParser.StructureData data = NbtStructureParser.parseAny(file);
        return convert(data, name);
    }

    /** 将已解析的结构数据转换为 MCBLUEPRINT v2 文本。 */
    public static String convert(NbtStructureParser.StructureData data, String name) {
        StringBuilder sb = new StringBuilder();

        sb.append("# MCBLUEPRINT v2\n");
        sb.append("# name: ").append(name).append("\n");
        sb.append("# size: ").append(data.sizeX).append("x")
          .append(data.sizeY).append("x").append(data.sizeZ).append("\n");
        sb.append("# origin: 0,0,0\n");
        sb.append("# 坐标原点在结构西北角最低层，x向东，y向上，z向南\n");
        sb.append("# 格式：x,y,z  block_id  [key=value ...]\n");
        sb.append("\n");
        sb.append("## BLOCKS\n");
        sb.append("\n");

        // 按 y 层分组，层内按 z 再按 x 排序，便于阅读
        Map<Integer, List<NbtStructureParser.BlockEntry>> byLayer = new TreeMap<>();
        for (NbtStructureParser.BlockEntry block : data.blocks) {
            NbtStructureParser.PaletteEntry pe = paletteOf(data, block);
            if (pe == null || isAir(pe.blockName)) continue;
            byLayer.computeIfAbsent(block.y, k -> new ArrayList<>()).add(block);
        }

        for (Map.Entry<Integer, List<NbtStructureParser.BlockEntry>> layerEntry : byLayer.entrySet()) {
            int y = layerEntry.getKey();
            sb.append("# --- 第 ").append(y + 1).append(" 层 (y=").append(y).append(") ---\n");

            List<NbtStructureParser.BlockEntry> layerBlocks = layerEntry.getValue();
            layerBlocks.sort(Comparator.comparingInt((NbtStructureParser.BlockEntry b) -> b.z)
                    .thenComparingInt(b -> b.x));

            for (NbtStructureParser.BlockEntry block : layerBlocks) {
                NbtStructureParser.PaletteEntry pe = paletteOf(data, block);
                sb.append(block.x).append(",")
                  .append(block.y).append(",")
                  .append(block.z).append("   ")
                  .append(pe.blockName);

                if (!pe.properties.isEmpty()) {
                    for (Map.Entry<String, String> prop : pe.properties.entrySet()) {
                        sb.append("   ").append(prop.getKey()).append("=").append(prop.getValue());
                    }
                }
                sb.append("\n");

                appendItems(sb, block.blockEntityNbt);
                appendSignText(sb, block.blockEntityNbt);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 若方块实体 NBT 中含 {@code Items} 列表（箱子、桶、漏斗、熔炉等容器），
     * 写出 {@code items:} 段，格式与 BlueprintParser 的 V2_ITEM_PATTERN 匹配：
     *   slot=N  item_id  count=C  [nbt={...}]
     */
    private static void appendItems(StringBuilder sb, NbtCompound blockEntityNbt) {
        if (blockEntityNbt == null || !blockEntityNbt.contains("Items", NbtElement.LIST_TYPE)) return;

        NbtList itemsList = blockEntityNbt.getList("Items", NbtElement.COMPOUND_TYPE);
        if (itemsList.isEmpty()) return;

        List<String> itemLines = new ArrayList<>();
        for (int i = 0; i < itemsList.size(); i++) {
            NbtCompound itemNbt = itemsList.getCompound(i);
            int slot = itemNbt.contains("Slot") ? (itemNbt.getByte("Slot") & 0xFF) : i;
            String itemId = itemNbt.contains("id") ? itemNbt.getString("id") : null;
            if (itemId == null || itemId.isEmpty() || "minecraft:air".equals(itemId)) continue;
            int count = itemNbt.contains("Count") ? itemNbt.getByte("Count") & 0xFF : 1;

            StringBuilder line = new StringBuilder();
            line.append("    slot=").append(slot)
                .append("  ").append(itemId)
                .append("  count=").append(count);
            if (itemNbt.contains("tag", NbtElement.COMPOUND_TYPE)) {
                line.append("  nbt=").append(itemNbt.getCompound("tag").toString());
            }
            itemLines.add(line.toString());
        }

        if (itemLines.isEmpty()) return;
        sb.append("  items:\n");
        for (String line : itemLines) {
            sb.append(line).append("\n");
        }
    }

    /**
     * 若方块实体 NBT 是告示牌（含 front_text/back_text 新格式，或 Text1~Text4 旧格式），
     * 写出 {@code sign_text:} 段，格式与 BlueprintParser 解析要求一致
     * （front:/back: 各缩进4空格，文字行缩进6空格，front/back 各固定4行）。
     */
    private static void appendSignText(StringBuilder sb, NbtCompound blockEntityNbt) {
        if (blockEntityNbt == null) return;

        List<String> frontLines;
        List<String> backLines;

        if (blockEntityNbt.contains("front_text", NbtElement.COMPOUND_TYPE)) {
            frontLines = readSignMessages(blockEntityNbt.getCompound("front_text"));
            backLines = blockEntityNbt.contains("back_text", NbtElement.COMPOUND_TYPE)
                    ? readSignMessages(blockEntityNbt.getCompound("back_text"))
                    : emptySignLines();
        } else if (blockEntityNbt.contains("Text1") || blockEntityNbt.contains("Text2")
                || blockEntityNbt.contains("Text3") || blockEntityNbt.contains("Text4")) {
            // 旧版格式（1.19 及以前）：Text1~Text4 为 JSON 字符串，只有正面文字
            frontLines = new ArrayList<>(4);
            for (int i = 1; i <= 4; i++) {
                String key = "Text" + i;
                String raw = blockEntityNbt.contains(key, NbtElement.STRING_TYPE)
                        ? blockEntityNbt.getString(key) : "";
                frontLines.add(extractPlainText(raw));
            }
            backLines = emptySignLines();
        } else {
            return; // 不是告示牌，或没有文字数据
        }

        boolean hasText = false;
        for (String line : frontLines) if (!line.isEmpty()) { hasText = true; break; }
        if (!hasText) for (String line : backLines) if (!line.isEmpty()) { hasText = true; break; }
        if (!hasText) return;

        sb.append("  sign_text:\n");
        sb.append("    front:\n");
        for (String line : frontLines) {
            sb.append("      ").append(line).append("\n");
        }
        sb.append("    back:\n");
        for (String line : backLines) {
            sb.append("      ").append(line).append("\n");
        }
    }

    /** 从 front_text/back_text compound 的 messages 列表提取 4 行纯文本（去除 JSON 包装）。 */
    private static List<String> readSignMessages(NbtCompound textNbt) {
        List<String> lines = new ArrayList<>(4);
        NbtList messages = textNbt.contains("messages", NbtElement.LIST_TYPE)
                ? textNbt.getList("messages", NbtElement.STRING_TYPE) : new NbtList();
        for (int i = 0; i < 4; i++) {
            String raw = i < messages.size() ? messages.getString(i) : "";
            lines.add(extractPlainText(raw));
        }
        return lines;
    }

    private static List<String> emptySignLines() {
        List<String> lines = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) lines.add("");
        return lines;
    }

    // 匹配 {"text":"..."} 形式 JSON 文本组件中的 text 字段（含转义字符）
    private static final java.util.regex.Pattern SIGN_TEXT_JSON_PATTERN =
            java.util.regex.Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    /**
     * 从告示牌文字的 JSON 文本组件（如 {"text":"hello"}）中提取纯文本。
     * 只处理简单的 {"text":"..."} 形式（本 mod 写入告示牌时使用的格式），
     * 足以覆盖常见场景；若不是合法 JSON 文本组件则原样返回（容错，避免转换中断）。
     */
    private static String extractPlainText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        java.util.regex.Matcher m = SIGN_TEXT_JSON_PATTERN.matcher(raw);
        if (m.find()) {
            return m.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n");
        }
        // 不是 JSON 文本组件（可能是纯文本或复杂 Text 结构），原样返回
        return raw;
    }

    private static NbtStructureParser.PaletteEntry paletteOf(NbtStructureParser.StructureData data,
                                                               NbtStructureParser.BlockEntry block) {
        if (block.paletteIndex < 0 || block.paletteIndex >= data.palette.size()) return null;
        return data.palette.get(block.paletteIndex);
    }

    private static boolean isAir(String blockName) {
        return "minecraft:air".equals(blockName)
                || "minecraft:cave_air".equals(blockName)
                || "minecraft:void_air".equals(blockName);
    }

    /**
     * 转换单个文件并写入目标目录，文件名与源文件同名（扩展名换成 .txt）。
     *
     * @return 写入的 txt 文件路径
     */
    public static Path convertToFile(File sourceFile, Path targetDir) throws Exception {
        String baseName = stripExtension(sourceFile.getName());
        String text = convert(sourceFile, baseName);

        if (!Files.isDirectory(targetDir)) {
            Files.createDirectories(targetDir);
        }
        Path targetFile = targetDir.resolve(baseName + ".txt");
        Files.writeString(targetFile, text, StandardCharsets.UTF_8);
        return targetFile;
    }

    /**
     * 批量转换目录下所有 .nbt / .litematic 文件（递归子目录），
     * 输出的 txt 文件保持相同的相对目录结构，写入 targetDir 下。
     *
     * @return 成功转换的文件数量
     */
    public static int convertDirectory(Path sourceDir, Path targetDir) throws IOException {
        int count = 0;
        List<Path> structureFiles = new ArrayList<>();
        try (var walk = Files.walk(sourceDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> {
                    String n = p.toString().toLowerCase();
                    return n.endsWith(".nbt") || n.endsWith(".litematic");
                })
                .forEach(structureFiles::add);
        }

        for (Path src : structureFiles) {
            try {
                Path relativeDir = sourceDir.relativize(src.getParent());
                Path outDir = targetDir.resolve(relativeDir);
                convertToFile(src.toFile(), outDir);
                count++;
            } catch (Exception e) {
                LOGGER.error("转换失败: {}", src, e);
            }
        }
        return count;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
