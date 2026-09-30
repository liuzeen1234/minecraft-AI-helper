package com.example.helloworld.nbt;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * NBT / Litematica 结构文件的路径解析工具方法。
 *
 * <p>原先这里注册的 {@code /ainbt} 命令（list / info / all / place）已移除，结构的浏览与放置
 * 改由图形化结构浏览器（{@code K → 加载结构}，见 {@code StructureBrowserScreen}）完成。
 * 本类仅保留供其它模块（如 {@code HelloWorldMod} 放置逻辑）调用的文件解析工具方法。
 */
public class NbtCommands {

    private static final Path NBTS_DIR = com.example.helloworld.ModPaths.getNbtsDir();
    private static final Path LITEMATIC_DIR = com.example.helloworld.ModPaths.getLitematicDir();

    /**
     * 解析用户输入的文件名，支持以下格式：
     *   - roof                          → 先找 nbts/roof.nbt，再递归搜索子文件夹
     *   - woodland_mansion/roof         → nbts/woodland_mansion/roof.nbt
     *   - woodland_mansion roof         → 空格转为 /，等同上面
     *   - woodland_mansion/roof.nbt     → 直接使用
     */
    public static File resolveNbtFile(String input) {
        return resolveInDir(NBTS_DIR, input, ".nbt");
    }

    /**
     * 在 litematic/ 目录下解析用户输入的 .litematic 文件名，规则同 {@link #resolveNbtFile}。
     */
    public static File resolveLitematicFile(String input) {
        return resolveInDir(LITEMATIC_DIR, input, ".litematic");
    }

    /**
     * 统一入口：先在 nbts/ 找 .nbt，找不到再在 litematic/ 找 .litematic。
     */
    public static File resolveStructureFile(String input) {
        File nbt = resolveNbtFile(input);
        if (nbt != null && nbt.exists()) return nbt;
        return resolveLitematicFile(input);
    }

    /**
     * 在指定根目录下解析文件名，支持：
     *   - name              → root/name{ext}，找不到再递归按文件名搜索
     *   - sub/name          → root/sub/name{ext}
     *   - sub name          → 空格转为 /
     *   - sub/name{ext}     → 直接使用
     *
     * @param root 搜索根目录（NBTS_DIR 或 LITEMATIC_DIR）
     * @param ext  目标扩展名（含点，如 ".nbt" / ".litematic"）
     */
    private static File resolveInDir(Path root, String input, String ext) {
        if (!Files.isDirectory(root)) {
            return null;
        }
        // 空格转为路径分隔符，支持 "sub name" 写法
        String normalized = input.trim().replace(' ', '/');
        boolean hasExt = normalized.toLowerCase().endsWith(ext);
        String withExt = hasExt ? normalized : normalized + ext;

        // 1. 先尝试精确路径
        File file = root.resolve(withExt).toFile();
        if (file.exists()) return file;

        // 2. 回退：递归搜索文件名匹配的文件
        String baseName = withExt.contains("/")
                ? withExt.substring(withExt.lastIndexOf('/') + 1)
                : withExt;
        String baseLower = baseName.toLowerCase();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().equals(baseLower))
                    .findFirst()
                    .map(Path::toFile)
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
