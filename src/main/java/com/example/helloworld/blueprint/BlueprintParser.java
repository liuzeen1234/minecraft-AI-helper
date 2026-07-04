package com.example.helloworld.blueprint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析蓝图文本文件，自动识别两种格式：
 *
 * V1 格式（旧格式，向后兼容）：
 *   {{layered blueprint}} 字符网格，图例用单字符映射方块。
 *   图例格式：|字符=方块名称[-rot角度][+属性]
 *   层分隔：|----第N层|
 *
 * V2 格式（新格式）：
 *   首行为 "# MCBLUEPRINT v2"
 *   方块行格式：x,y,z   block_id   [key=value ...]
 *   支持所有原版 block state 属性，坐标显式指定，无字符数量限制。
 */
public class BlueprintParser {

    private static final Logger LOGGER = LoggerFactory.getLogger("BlueprintParser");

    // V1 格式匹配
    private static final Pattern LAYER_PATTERN = Pattern.compile("\\|----(.+?)\\|");
    private static final Pattern LEGEND_PATTERN = Pattern.compile("^\\|(.?)=(.+)$");

    // V2 格式匹配
    // 方块行：x,y,z  block_id  [key=value ...]
    private static final Pattern V2_BLOCK_PATTERN =
            Pattern.compile("^(\\d+),(\\d+),(\\d+)\\s+(\\S+)(.*)$");
    // 头部元数据
    private static final Pattern V2_SIZE_PATTERN =
            Pattern.compile("#\\s*size:\\s*(\\d+)\\s*x\\s*(\\d+)\\s*x\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern V2_NAME_PATTERN =
            Pattern.compile("#\\s*name:\\s*(.+)", Pattern.CASE_INSENSITIVE);

    // -------------------------------------------------------------------------
    // 入口：自动检测格式
    // -------------------------------------------------------------------------

    public static BlueprintData parse(String text) {
        String trimmed = text.stripLeading();
        if (trimmed.startsWith("# MCBLUEPRINT v2") || trimmed.startsWith("#MCBLUEPRINT v2")) {
            return parseV2(text);
        }
        return parseV1(text);
    }

    // =========================================================================
    // V2 解析
    // =========================================================================

    // 物品行匹配：slot=N  item_id  count=C  [nbt={...}]
    private static final Pattern V2_ITEM_PATTERN =
            Pattern.compile("^slot=(\\d+)\\s+(\\S+)\\s+count=(\\d+)(.*)$");

    private static BlueprintData parseV2(String text) {
        String name = "unknown";
        int sizeX = 0, sizeY = 0, sizeZ = 0;
        List<BlueprintData.BlockEntry3D> blocks = new ArrayList<>();

        String[] lines = text.split("\n");
        // 临时变量：当前方块的待收集物品列表
        int pendingX = 0, pendingY = 0, pendingZ = 0;
        String pendingBlockId = null;
        Map<String, String> pendingProps = null;
        List<BlueprintData.ItemEntry> pendingItems = null;
        boolean inItemsSection = false;
        // 告示牌文字解析状态
        boolean inSignTextSection = false;
        boolean inSignFront = false;
        boolean inSignBack = false;
        List<String> pendingSignFrontLines = null;
        List<String> pendingSignBackLines = null;

        for (String rawLine : lines) {
            String lineNoR = rawLine.replace("\r", "");
            String line = lineNoR.trim();

            // 跳过空行（结束 items/sign_text 段）
            if (line.isEmpty()) {
                if (inItemsSection) {
                    // 空行结束当前 items 段，提交方块
                    blocks.add(new BlueprintData.BlockEntry3D(pendingX, pendingY, pendingZ,
                            pendingBlockId, pendingProps, pendingItems));
                    inItemsSection = false;
                    pendingBlockId = null;
                    pendingItems = null;
                } else if (inSignTextSection) {
                    // 空行结束当前 sign_text 段，提交方块
                    BlueprintData.SignTextEntry signText = new BlueprintData.SignTextEntry(
                            padSignLines(pendingSignFrontLines), padSignLines(pendingSignBackLines));
                    blocks.add(new BlueprintData.BlockEntry3D(pendingX, pendingY, pendingZ,
                            pendingBlockId, pendingProps, Collections.emptyList(), signText));
                    inSignTextSection = false;
                    inSignFront = false;
                    inSignBack = false;
                    pendingBlockId = null;
                    pendingSignFrontLines = null;
                    pendingSignBackLines = null;
                }
                continue;
            }

            // 检查是否是 items: 标记行（以空格开头）
            if (line.equals("items:") && lineNoR.startsWith("  ")) {
                inItemsSection = true;
                pendingItems = new ArrayList<>();
                continue;
            }

            // 检查是否是 sign_text: 标记行（以空格开头）
            if (line.equals("sign_text:") && lineNoR.startsWith("  ")) {
                inSignTextSection = true;
                inSignFront = false;
                inSignBack = false;
                pendingSignFrontLines = new ArrayList<>();
                pendingSignBackLines = new ArrayList<>();
                continue;
            }

            // 解析 sign_text 内的 front:/back: 标记和文字行
            if (inSignTextSection) {
                if (line.equals("front:")) {
                    inSignFront = true;
                    inSignBack = false;
                    continue;
                }
                if (line.equals("back:")) {
                    inSignBack = true;
                    inSignFront = false;
                    continue;
                }
                // 缩进6空格的文字行
                if (lineNoR.startsWith("      ")) {
                    String textContent = lineNoR.substring(6); // 去掉6空格缩进
                    if (inSignFront) {
                        pendingSignFrontLines.add(textContent);
                    } else if (inSignBack) {
                        pendingSignBackLines.add(textContent);
                    }
                    continue;
                }
                // 不是合法的 sign_text 内容行，结束 sign_text 段
                BlueprintData.SignTextEntry signText = new BlueprintData.SignTextEntry(
                        padSignLines(pendingSignFrontLines), padSignLines(pendingSignBackLines));
                blocks.add(new BlueprintData.BlockEntry3D(pendingX, pendingY, pendingZ,
                        pendingBlockId, pendingProps, Collections.emptyList(), signText));
                inSignTextSection = false;
                inSignFront = false;
                inSignBack = false;
                pendingBlockId = null;
                pendingSignFrontLines = null;
                pendingSignBackLines = null;
                // 继续处理当前行（fall through）
            }

            // 如果正在 items 段中，解析物品行（缩进4空格）
            if (inItemsSection) {
                Matcher itemMatcher = V2_ITEM_PATTERN.matcher(line);
                if (itemMatcher.matches()) {
                    int slot = Integer.parseInt(itemMatcher.group(1));
                    String itemId = itemMatcher.group(2).trim();
                    if (itemId.startsWith("minecraft:")) {
                        itemId = itemId.substring("minecraft:".length());
                    }
                    int count = Integer.parseInt(itemMatcher.group(3));
                    String remainder = itemMatcher.group(4).trim();
                    String nbtStr = null;
                    if (remainder.startsWith("nbt=")) {
                        nbtStr = remainder.substring(4).trim();
                    }
                    pendingItems.add(new BlueprintData.ItemEntry(slot, itemId, count, nbtStr));
                    continue;
                }
                // 如果不是物品行，说明 items 段结束
                blocks.add(new BlueprintData.BlockEntry3D(pendingX, pendingY, pendingZ,
                        pendingBlockId, pendingProps, pendingItems));
                inItemsSection = false;
                pendingBlockId = null;
                pendingItems = null;
                // 继续处理当前行（fall through）
            }

            // 注释行：提取元数据
            if (line.startsWith("#")) {
                Matcher nameMatcher = V2_NAME_PATTERN.matcher(line);
                if (nameMatcher.find()) {
                    name = nameMatcher.group(1).trim();
                    continue;
                }
                Matcher sizeMatcher = V2_SIZE_PATTERN.matcher(line);
                if (sizeMatcher.find()) {
                    sizeX = Integer.parseInt(sizeMatcher.group(1));
                    sizeY = Integer.parseInt(sizeMatcher.group(2));
                    sizeZ = Integer.parseInt(sizeMatcher.group(3));
                }
                continue;
            }

            // 节标题（## BLOCKS 等）
            if (line.startsWith("##")) continue;

            // 方块行
            Matcher blockMatcher = V2_BLOCK_PATTERN.matcher(line);
            if (blockMatcher.matches()) {
                // 如果前一个方块还在等待（没有 items/sign_text 段），先提交
                if (pendingBlockId != null) {
                    blocks.add(new BlueprintData.BlockEntry3D(pendingX, pendingY, pendingZ,
                            pendingBlockId, pendingProps));
                    pendingBlockId = null;
                }

                int x = Integer.parseInt(blockMatcher.group(1));
                int y = Integer.parseInt(blockMatcher.group(2));
                int z = Integer.parseInt(blockMatcher.group(3));
                String blockId = blockMatcher.group(4).trim();
                // 去掉 minecraft: 前缀
                if (blockId.startsWith("minecraft:")) {
                    blockId = blockId.substring("minecraft:".length());
                }
                String propsStr = blockMatcher.group(5).trim();
                Map<String, String> properties = parseV2Properties(propsStr);

                // 跳过空气
                if (blockId.equals("air")) continue;

                // 暂存，等看下一行是否是 items:/sign_text:
                pendingX = x;
                pendingY = y;
                pendingZ = z;
                pendingBlockId = blockId;
                pendingProps = properties;
            }
        }

        // 文件结束，提交最后一个方块
        if (pendingBlockId != null) {
            if (inItemsSection && pendingItems != null) {
                blocks.add(new BlueprintData.BlockEntry3D(pendingX, pendingY, pendingZ,
                        pendingBlockId, pendingProps, pendingItems));
            } else if (inSignTextSection) {
                BlueprintData.SignTextEntry signText = new BlueprintData.SignTextEntry(
                        padSignLines(pendingSignFrontLines), padSignLines(pendingSignBackLines));
                blocks.add(new BlueprintData.BlockEntry3D(pendingX, pendingY, pendingZ,
                        pendingBlockId, pendingProps, Collections.emptyList(), signText));
            } else {
                blocks.add(new BlueprintData.BlockEntry3D(pendingX, pendingY, pendingZ,
                        pendingBlockId, pendingProps));
            }
        }

        // 如果文件中没有 size 头，从方块坐标推算
        if (sizeX == 0 && sizeY == 0 && sizeZ == 0 && !blocks.isEmpty()) {
            for (BlueprintData.BlockEntry3D b : blocks) {
                sizeX = Math.max(sizeX, b.getX() + 1);
                sizeY = Math.max(sizeY, b.getY() + 1);
                sizeZ = Math.max(sizeZ, b.getZ() + 1);
            }
        }

        LOGGER.info("解析 V2 蓝图 '{}': {} 个方块, 尺寸 {}x{}x{}", name, blocks.size(), sizeX, sizeY, sizeZ);
        return new BlueprintData(name, blocks, sizeX, sizeY, sizeZ);
    }

    /**
     * 将告示牌文字行列表补齐/截断到恰好 4 行。
     */
    private static List<String> padSignLines(List<String> lines) {
        if (lines == null) lines = new ArrayList<>();
        while (lines.size() < 4) lines.add("");
        return lines.subList(0, 4);
    }

    /**
     * 解析 V2 属性字符串，格式：key=value  key=value ...
     * 行内注释（# 开头）会被忽略。
     */
    private static Map<String, String> parseV2Properties(String propsStr) {
        Map<String, String> props = new LinkedHashMap<>();
        if (propsStr.isEmpty()) return props;

        // 去掉行内注释
        int commentIdx = propsStr.indexOf('#');
        if (commentIdx >= 0) propsStr = propsStr.substring(0, commentIdx).trim();

        String[] parts = propsStr.split("\\s+");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            if (eq > 0) {
                String key = part.substring(0, eq).trim();
                String value = part.substring(eq + 1).trim();
                props.put(key, value);
            }
        }
        return props;
    }

    // =========================================================================
    // V1 解析（原有逻辑，保持不变）
    // =========================================================================

    private static BlueprintData parseV1(String text) {
        String name = extractName(text);
        Map<Character, BlueprintData.BlockEntry> legend = new LinkedHashMap<>();
        List<char[][]> layers = new ArrayList<>();

        String[] lines = text.split("\n");
        boolean inLayer = false;
        List<String> currentLayerRows = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine.replace("\r", "");

            if (isMetadataLine(line)) continue;

            Matcher legendMatcher = LEGEND_PATTERN.matcher(line);
            if (legendMatcher.matches()) {
                String charStr = legendMatcher.group(1);
                String definition = legendMatcher.group(2).trim();
                if (!charStr.isEmpty()) {
                    char symbol = charStr.charAt(0);
                    legend.put(symbol, parseBlockEntry(definition));
                }
                continue;
            }

            Matcher layerMatcher = LAYER_PATTERN.matcher(line);
            if (layerMatcher.find()) {
                if (!currentLayerRows.isEmpty()) {
                    layers.add(rowsToGrid(currentLayerRows));
                    currentLayerRows.clear();
                }
                inLayer = true;
                continue;
            }

            if (line.trim().startsWith("}}")) {
                if (!currentLayerRows.isEmpty()) {
                    layers.add(rowsToGrid(currentLayerRows));
                    currentLayerRows.clear();
                }
                inLayer = false;
                continue;
            }

            if (inLayer) {
                currentLayerRows.add(line);
            }
        }

        if (!currentLayerRows.isEmpty()) {
            layers.add(rowsToGrid(currentLayerRows));
        }

        LOGGER.info("解析 V1 蓝图 '{}': {} 个图例, {} 层", name, legend.size(), layers.size());

        if (!layers.isEmpty()) {
            while (!layers.isEmpty() && layers.get(0).length == 0) {
                layers.remove(0);
            }
            if (!layers.isEmpty()) {
                normalizeLayers(layers);
            }
        }

        return new BlueprintData(name, legend, layers);
    }

    private static void normalizeLayers(List<char[][]> layers) {
        int standardRows = 0;
        for (char[][] grid : layers) {
            int effectiveRows = grid.length;
            while (effectiveRows > 0 && !hasContent(grid[effectiveRows - 1])) {
                effectiveRows--;
            }
            standardRows = Math.max(standardRows, effectiveRows);
        }

        if (standardRows == 0) return;

        int standardCols = 0;
        for (char[][] grid : layers) {
            for (char[] row : grid) {
                standardCols = Math.max(standardCols, row.length);
            }
        }

        for (int i = 0; i < layers.size(); i++) {
            char[][] grid = layers.get(i);
            int effectiveRows = grid.length;
            while (effectiveRows > 0 && !hasContent(grid[effectiveRows - 1])) {
                effectiveRows--;
            }

            if (effectiveRows == standardRows && grid.length == standardRows) continue;

            if (effectiveRows >= standardRows) {
                char[][] trimmed = new char[standardRows][];
                System.arraycopy(grid, 0, trimmed, 0, standardRows);
                layers.set(i, trimmed);
            } else {
                char[][] padded = new char[standardRows][];
                System.arraycopy(grid, 0, padded, 0, effectiveRows);
                for (int r = effectiveRows; r < standardRows; r++) {
                    padded[r] = new char[standardCols];
                    java.util.Arrays.fill(padded[r], ' ');
                }
                layers.set(i, padded);
            }
        }
    }

    private static boolean hasContent(char[] row) {
        for (char c : row) {
            if (c != ' ') return true;
        }
        return false;
    }

    private static String extractName(String text) {
        Pattern p1 = Pattern.compile("\\|name=\\s*(.+?)(?:\\||\n|''')", Pattern.DOTALL);
        Matcher m1 = p1.matcher(text);
        if (m1.find()) return cleanWikiMarkup(m1.group(1).trim());

        Pattern p2 = Pattern.compile("\\{\\{layered blueprint\\|name=\\s*(.+?)\\|", Pattern.DOTALL);
        Matcher m2 = p2.matcher(text);
        if (m2.find()) return cleanWikiMarkup(m2.group(1).trim());

        return "unknown";
    }

    private static String cleanWikiMarkup(String text) {
        return text.replaceAll("'{2,}", "").trim();
    }

    private static boolean isMetadataLine(String line) {
        String lower = line.trim().toLowerCase();
        return lower.startsWith("|name=") || lower.startsWith("|default=");
    }

    /**
     * 解析 V1 方块定义字符串。支持多种格式：
     *   "Smooth Sandstone Stairs-rot90"
     *   "Jungle Door+bottom"
     *   "White Bed+top foot-rot270'''"
     */
    static BlueprintData.BlockEntry parseBlockEntry(String definition) {
        Map<String, String> properties = new LinkedHashMap<>();
        String blockName = cleanWikiMarkup(definition);

        Pattern rotPattern = Pattern.compile("-rot-?(\\d+)");

        int plusIdx = blockName.indexOf('+');
        if (plusIdx > 0) {
            String afterPlus = blockName.substring(plusIdx + 1).trim();
            blockName = blockName.substring(0, plusIdx).trim();

            Matcher rotInAttr = rotPattern.matcher(afterPlus);
            if (rotInAttr.find()) {
                int degrees = Integer.parseInt(rotInAttr.group(1));
                properties.put("_rot", String.valueOf(degrees));
                afterPlus = afterPlus.substring(0, rotInAttr.start()).trim()
                        + afterPlus.substring(rotInAttr.end()).trim();
                afterPlus = afterPlus.trim();
            }

            String[] attrParts = afterPlus.split("\\s+");
            for (String attr : attrParts) {
                if (attr.isEmpty()) continue;
                switch (attr.toLowerCase()) {
                    case "bottom", "top" -> properties.put("half", attr.toLowerCase());
                    case "lower", "upper" -> properties.put("half", attr.toLowerCase());
                    case "foot" -> properties.put("part", "foot");
                    case "head" -> properties.put("part", "head");
                    default -> LOGGER.debug("未知属性: {}", attr);
                }
            }
        }

        Matcher rotMatcher = rotPattern.matcher(blockName);
        if (rotMatcher.find()) {
            int degrees = Integer.parseInt(rotMatcher.group(1));
            properties.put("_rot", String.valueOf(degrees));
            blockName = blockName.substring(0, rotMatcher.start()).trim();
        }

        String blockId = nameToBlockId(blockName.trim());

        if (blockId.endsWith("_door") && properties.containsKey("half")) {
            String half = properties.get("half");
            if (half.equals("bottom")) properties.put("half", "lower");
            else if (half.equals("top")) properties.put("half", "upper");
        }

        if (blockId.endsWith("_bed")) {
            properties.remove("half");
        }

        if ((blockId.contains("_log") || blockId.contains("_wood"))
                && properties.containsKey("half")) {
            String half = properties.remove("half");
            String axis = switch (half) {
                case "top" -> "y";
                case "east" -> "x";
                case "north" -> "z";
                default -> "y";
            };
            properties.put("axis", axis);
        }

        if (blockId.equals("torch") && properties.containsKey("_rot")) {
            blockId = "wall_torch";
        }

        if (!properties.containsKey("_rot") && needsDefaultRot(blockId)) {
            properties.put("_rot", "0");
        }

        return new BlueprintData.BlockEntry(blockId, properties);
    }

    private static boolean needsDefaultRot(String blockId) {
        return blockId.contains("stairs");
    }

    static String nameToBlockId(String displayName) {
        return displayName.trim().toLowerCase().replace(" ", "_");
    }

    private static char[][] rowsToGrid(List<String> rows) {
        while (!rows.isEmpty() && rows.get(0).isEmpty()) rows.remove(0);
        while (!rows.isEmpty() && rows.get(rows.size() - 1).isEmpty()) rows.remove(rows.size() - 1);

        if (rows.isEmpty()) return new char[0][0];

        int maxWidth = 0;
        for (String row : rows) maxWidth = Math.max(maxWidth, row.length());

        char[][] grid = new char[rows.size()][maxWidth];
        for (int r = 0; r < rows.size(); r++) {
            String row = rows.get(r);
            for (int c = 0; c < maxWidth; c++) {
                grid[r][c] = c < row.length() ? row.charAt(c) : ' ';
            }
        }
        return grid;
    }
}
