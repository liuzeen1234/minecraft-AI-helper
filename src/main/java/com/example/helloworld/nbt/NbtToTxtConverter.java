package com.example.helloworld.nbt;

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
 * 输出格式与 {@code SelectionAnalyzer#exportBlueprintV2} 一致：
 *   # MCBLUEPRINT v2
 *   # name: xxx
 *   # size: WxHxD
 *   # origin: 0,0,0
 *   ## BLOCKS
 *   x,y,z   block_id   [key=value ...]
 *
 * 空气方块（minecraft:air / cave_air / void_air）不写入，以节省 token。
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
            }
            sb.append("\n");
        }

        return sb.toString();
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
