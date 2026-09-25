package com.example.helloworld.structure;

import com.example.helloworld.nbt.LitematicParser;
import com.example.helloworld.nbt.NbtStructureParser;
import com.example.helloworld.nbt.NbtStructureWriter;
import com.example.helloworld.nbt.NbtToTxtConverter;
import com.example.helloworld.nbt.TxtToStructureConverter;
import com.example.helloworld.selection.LitematicNbtWriter;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 统一的结构文件格式转换入口，支持 NBT / Litematic / TXT（MCBLUEPRINT v2）
 * 三种格式之间任意方向的互相转换。
 *
 * 所有转换都以 {@link NbtStructureParser.StructureData} 作为中间表示：
 *   读入：NBT/Litematic 走 {@link NbtStructureParser#parseAny}，
 *         TXT 走 {@link TxtToStructureConverter}（内部复用 BlueprintParser）；
 *   写出：NBT 走 {@link NbtStructureWriter}，
 *         Litematic 走 {@link LitematicNbtWriter}，
 *         TXT 走 {@link NbtToTxtConverter}。
 */
public final class StructureFormatConverter {

    /** 支持的结构文件格式（也用作转换目标格式下拉的选项）。 */
    public enum Format {
        NBT("nbt"),
        LITEMATIC("litematic"),
        TXT("txt");

        public final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        /** 根据文件扩展名推断格式，找不到匹配返回 null。 */
        public static Format fromFile(File file) {
            String name = file.getName().toLowerCase();
            for (Format f : values()) {
                if (name.endsWith("." + f.extension)) return f;
            }
            return null;
        }
    }

    private StructureFormatConverter() {}

    /**
     * 将源文件转换为目标格式，写入 targetDir 下与源文件同名（换目标扩展名）的文件。
     *
     * @return 写入的目标文件路径
     */
    public static Path convert(File sourceFile, Format targetFormat, Path targetDir) throws Exception {
        Format sourceFormat = Format.fromFile(sourceFile);
        if (sourceFormat == null) {
            throw new IllegalArgumentException("无法识别的源文件格式: " + sourceFile.getName());
        }

        String baseName = stripExtension(sourceFile.getName());

        if (!Files.isDirectory(targetDir)) {
            Files.createDirectories(targetDir);
        }

        // 源格式与目标格式相同：直接复制，不做无意义的编解码往返
        if (sourceFormat == targetFormat) {
            Path targetFile = targetDir.resolve(baseName + "." + targetFormat.extension);
            Files.copy(sourceFile.toPath(), targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return targetFile;
        }

        NbtStructureParser.StructureData data = readAny(sourceFile, sourceFormat);
        return writeAs(data, baseName, targetFormat, targetDir);
    }

    /** 按格式读取源文件为统一的 StructureData 中间表示。 */
    private static NbtStructureParser.StructureData readAny(File file, Format format) throws Exception {
        return switch (format) {
            case NBT -> NbtStructureParser.parse(file);
            case LITEMATIC -> LitematicParser.parse(file);
            case TXT -> TxtToStructureConverter.convert(file);
        };
    }

    /** 将 StructureData 写出为指定格式的文件。 */
    private static Path writeAs(NbtStructureParser.StructureData data, String baseName,
                                 Format targetFormat, Path targetDir) throws Exception {
        return switch (targetFormat) {
            case NBT -> NbtStructureWriter.writeToFile(data, targetDir, baseName);
            case LITEMATIC -> writeLitematic(data, baseName, targetDir);
            case TXT -> writeTxt(data, baseName, targetDir);
        };
    }

    private static Path writeLitematic(NbtStructureParser.StructureData data, String baseName, Path targetDir) throws Exception {
        NbtCompound root = LitematicNbtWriter.create(data, baseName, "AI Builder");
        Path targetFile = targetDir.resolve(baseName + ".litematic");
        try (FileOutputStream fos = new FileOutputStream(targetFile.toFile())) {
            NbtIo.writeCompressed(root, fos);
        }
        return targetFile;
    }

    private static Path writeTxt(NbtStructureParser.StructureData data, String baseName, Path targetDir) throws Exception {
        String text = NbtToTxtConverter.convert(data, baseName);
        Path targetFile = targetDir.resolve(baseName + ".txt");
        Files.writeString(targetFile, text, StandardCharsets.UTF_8);
        return targetFile;
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
