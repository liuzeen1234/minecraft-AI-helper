package com.example.helloworld.blueprint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析蓝图文本文件，支持 {{layered blueprint}} 格式。
 *
 * 图例格式：|字符=方块名称  或  |字符=方块名称+属性  或  |字符=方块名称-属性
 * 属性约定：
 *   +bottom / +top          → half=bottom / half=top（台阶、门）
 *   -rot0 / -rot90 / -rot180 / -rot270  → facing 方向（楼梯等）
 *   -rot-0 / -rot-90 等     → 墙上火把等的 facing
 *
 * 层分隔：|----第N层|
 * 每层是一个字符网格，空格表示空气，不放置方块。
 */
public class BlueprintParser {

    private static final Logger LOGGER = LoggerFactory.getLogger("BlueprintParser");

    // 匹配层标题
    private static final Pattern LAYER_PATTERN = Pattern.compile("\\|----(.+?)\\|");
    // 匹配图例行
    private static final Pattern LEGEND_PATTERN = Pattern.compile("^\\|(.?)=(.+)$");

    public static BlueprintData parse(String text) {
        String name = extractName(text);
        Map<Character, BlueprintData.BlockEntry> legend = new LinkedHashMap<>();
        List<char[][]> layers = new ArrayList<>();

        String[] lines = text.split("\n");
        boolean inLayer = false;
        List<String> currentLayerRows = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine.replace("\r", "");

            // 跳过元数据行（|name=..., |default=... 等）
            if (isMetadataLine(line)) {
                continue;
            }

            // 图例行
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

            // 层分隔行
            Matcher layerMatcher = LAYER_PATTERN.matcher(line);
            if (layerMatcher.find()) {
                // 保存上一层
                if (!currentLayerRows.isEmpty()) {
                    layers.add(rowsToGrid(currentLayerRows));
                    currentLayerRows.clear();
                }
                inLayer = true;
                continue;
            }

            // 结束标记：}} 可能后面跟着 <noinclude> 等 wiki 标签
            if (line.trim().startsWith("}}")) {
                if (!currentLayerRows.isEmpty()) {
                    layers.add(rowsToGrid(currentLayerRows));
                    currentLayerRows.clear();
                }
                inLayer = false;
                continue;
            }

            // 层内容行：保留全空格行（它们是蓝图的一部分），只跳过真正的空行
            if (inLayer) {
                currentLayerRows.add(line);
            }
        }

        // 处理最后一层
        if (!currentLayerRows.isEmpty()) {
            layers.add(rowsToGrid(currentLayerRows));
        }

        LOGGER.info("解析蓝图 '{}': {} 个图例, {} 层", name, legend.size(), layers.size());

        // 标准化：所有层统一为相同的行数（以第1层为准）
        if (!layers.isEmpty()) {
            normalizeLayers(layers);
        }

        return new BlueprintData(name, legend, layers);
    }

    /**
     * 标准化所有层的行数，以第1层为基准。
     * 对于行数超过基准的层，优先从尾部去掉全空格行，再从头部去掉。
     * 对于行数不足的层，在尾部补全空格行。
     */
    private static void normalizeLayers(List<char[][]> layers) {
        int standardRows = layers.get(0).length;
        int standardCols = layers.get(0).length > 0 ? layers.get(0)[0].length : 0;

        for (int i = 1; i < layers.size(); i++) {
            char[][] grid = layers.get(i);
            if (grid.length == standardRows) continue;

            if (grid.length > standardRows) {
                // 需要裁剪：找到有内容的行的范围，然后取 standardRows 行使内容对齐
                int firstContentRow = -1;
                int lastContentRow = -1;
                for (int r = 0; r < grid.length; r++) {
                    if (hasContent(grid[r])) {
                        if (firstContentRow == -1) firstContentRow = r;
                        lastContentRow = r;
                    }
                }

                // 计算在第1层中，内容行的典型起始位置
                int contentRows = (firstContentRow == -1) ? 0 : (lastContentRow - firstContentRow + 1);
                // 从 firstContentRow 开始，往前取尽可能多的行，使总行数为 standardRows
                int startRow = Math.max(0, firstContentRow);
                // 确保内容行都在范围内
                if (startRow + standardRows - 1 < lastContentRow) {
                    startRow = lastContentRow - standardRows + 1;
                }
                startRow = Math.max(0, Math.min(startRow, grid.length - standardRows));

                char[][] trimmed = new char[standardRows][];
                System.arraycopy(grid, startRow, trimmed, 0, standardRows);
                layers.set(i, trimmed);
            } else {
                // 行数不足，尾部补空格行
                char[][] padded = new char[standardRows][];
                System.arraycopy(grid, 0, padded, 0, grid.length);
                for (int r = grid.length; r < standardRows; r++) {
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
        // 格式1: {{layered blueprint|name=XXX|default=...  (name 和 {{ 同行或下一行)
        // 格式2: |name=XXX'''  (name 单独一行)
        // 先尝试匹配 |name= 行
        Pattern p1 = Pattern.compile("\\|name=\\s*(.+?)(?:\\||\n|''')", Pattern.DOTALL);
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            return cleanWikiMarkup(m1.group(1).trim());
        }
        // 兼容旧格式: {{layered blueprint|name=XXX|
        Pattern p2 = Pattern.compile("\\{\\{layered blueprint\\|name=\\s*(.+?)\\|", Pattern.DOTALL);
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            return cleanWikiMarkup(m2.group(1).trim());
        }
        return "unknown";
    }

    /**
     * 清理 wiki 标记（如 '''粗体'''）
     */
    private static String cleanWikiMarkup(String text) {
        return text.replaceAll("'{2,}", "").trim();
    }

    /**
     * 判断是否为元数据行（如 |name=..., |default=...），不是图例定义。
     */
    private static boolean isMetadataLine(String line) {
        String lower = line.trim().toLowerCase();
        return lower.startsWith("|name=") || lower.startsWith("|default=");
    }

    /**
     * 解析方块定义字符串。支持多种格式：
     *   "Smooth Sandstone Stairs-rot90"
     *   "Jungle Door+bottom"
     *   "White Bed+top foot-rot270'''"
     *   "glass Pane-rot90"
     */
    static BlueprintData.BlockEntry parseBlockEntry(String definition) {
        Map<String, String> properties = new LinkedHashMap<>();
        // 清理 wiki 标记
        String blockName = cleanWikiMarkup(definition);

        // 处理 -rot 属性（旋转/朝向），支持 -rot90, -rot-90 等格式
        // 必须在处理 + 之前提取，因为 + 后面的内容可能也包含 -rot
        Pattern rotPattern = Pattern.compile("-rot-?(\\d+)");

        // 先处理 +属性 后面的部分（如 "+top foot-rot270"）
        int plusIdx = blockName.indexOf('+');
        if (plusIdx > 0) {
            String afterPlus = blockName.substring(plusIdx + 1).trim();
            blockName = blockName.substring(0, plusIdx).trim();

            // afterPlus 可能包含多个空格分隔的属性，如 "top foot-rot270"
            // 先提取 -rot，保存原始角度值
            Matcher rotInAttr = rotPattern.matcher(afterPlus);
            if (rotInAttr.find()) {
                int degrees = Integer.parseInt(rotInAttr.group(1));
                properties.put("_rot", String.valueOf(degrees));
                afterPlus = afterPlus.substring(0, rotInAttr.start()).trim()
                        + afterPlus.substring(rotInAttr.end()).trim();
                afterPlus = afterPlus.trim();
            }

            // 解析剩余的属性词
            String[] attrParts = afterPlus.split("\\s+");
            for (String attr : attrParts) {
                if (attr.isEmpty()) continue;
                switch (attr.toLowerCase()) {
                    case "bottom", "top" -> properties.put("half", attr.toLowerCase());
                    case "lower", "upper" -> properties.put("half", attr.toLowerCase());
                    case "foot" -> properties.put("part", "foot");
                    case "head" -> properties.put("part", "head");
                    default -> {
                        // 未知属性，尝试作为 key=value 或忽略
                        LOGGER.debug("未知属性: {}", attr);
                    }
                }
            }
        }

        // 处理方块名中的 -rot（没有 + 的情况，如 "Oak Stairs-rot90"）
        Matcher rotMatcher = rotPattern.matcher(blockName);
        if (rotMatcher.find()) {
            int degrees = Integer.parseInt(rotMatcher.group(1));
            properties.put("_rot", String.valueOf(degrees));
            blockName = blockName.substring(0, rotMatcher.start()).trim();
        }

        String blockId = nameToBlockId(blockName.trim());

        // 门方块的 half 属性使用 lower/upper 而不是 bottom/top
        if (blockId.endsWith("_door") && properties.containsKey("half")) {
            String half = properties.get("half");
            if (half.equals("bottom")) properties.put("half", "lower");
            else if (half.equals("top")) properties.put("half", "upper");
        }

        // 床没有 half 属性
        if (blockId.endsWith("_bed")) {
            properties.remove("half");
        }

        // 原木类方块的 +top 表示 axis=y
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

        // torch 带旋转属性时应该是 wall_torch
        if (blockId.equals("torch") && properties.containsKey("_rot")) {
            blockId = "wall_torch";
        }

        // 对于需要 facing 的方块，如果没有指定 _rot，默认为 rot0
        if (!properties.containsKey("_rot") && needsDefaultRot(blockId)) {
            properties.put("_rot", "0");
        }

        return new BlueprintData.BlockEntry(blockId, properties);
    }

    /**
     * 判断方块是否需要默认的 rot 值（即有 facing 属性的方块）。
     * 楼梯等方块即使没有 -rot 后缀，也需要设置 _rot=0 来触发正确的 facing 映射。
     */
    private static boolean needsDefaultRot(String blockId) {
        return blockId.contains("stairs");
    }

    /**
     * 将显示名称转换为 Minecraft 方块 ID。
     * 如 "Smooth Sandstone Stairs" → "smooth_sandstone_stairs"
     */
    static String nameToBlockId(String displayName) {
        return displayName.trim().toLowerCase().replace(" ", "_");
    }

    private static char[][] rowsToGrid(List<String> rows) {
        // 只去掉首尾长度为0的真正空行（层标题后的空换行），
        // 保留全空格行（它们是蓝图网格的一部分，表示该行没有方块）
        while (!rows.isEmpty() && rows.get(0).isEmpty()) {
            rows.remove(0);
        }
        while (!rows.isEmpty() && rows.get(rows.size() - 1).isEmpty()) {
            rows.remove(rows.size() - 1);
        }

        if (rows.isEmpty()) return new char[0][0];

        // 找到最大宽度
        int maxWidth = 0;
        for (String row : rows) {
            maxWidth = Math.max(maxWidth, row.length());
        }

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
