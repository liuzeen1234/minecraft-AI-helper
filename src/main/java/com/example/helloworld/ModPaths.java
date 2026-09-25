package com.example.helloworld;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模组文件路径统一管理。
 * 所有模组产生的文件都放在游戏目录下的 ai-helper/ 文件夹中，
 * 与 mods/、config/ 等文件夹同级，方便用户管理。
 *
 * 结构：
 *   .minecraft/ai-helper/
 *   ├── config/          配置文件
 *   ├── structures/      结构文件根目录
 *   │   ├── txts/        .txt 蓝图文件
 *   │   ├── nbts/        .nbt 结构文件
 *   │   └── litematic/   .litematic 结构文件（Litematica 格式）
 *   ├── knowledge/       RAG 知识库文档（Markdown，首次启动从模组资源释放）
 *   ├── screenshots/     AI 截图临时文件
 *   └── ...              其他模组数据
 */
public class ModPaths {

    private static final String MOD_DIR_NAME = "ai-helper";

    /** 结构文件根目录名，下含 txts/ 与 nbts/ 子目录。 */
    private static final String STRUCTURES_DIR_NAME = "structures";

    /** 知识库目录名，下含知识库子目录（如 basic_info_en）。 */
    private static final String KNOWLEDGE_DIR_NAME = "knowledge";

    /**
     * 获取模组根目录：gameDir/ai-helper/
     */
    public static Path getModDir() {
        return FabricLoader.getInstance().getGameDir().resolve(MOD_DIR_NAME);
    }

    /**
     * 获取结构文件根目录：gameDir/ai-helper/structures/
     * 下含 txts/、nbts/ 与 litematic/ 子目录。
     */
    public static Path getStructuresDir() {
        return getModDir().resolve(STRUCTURES_DIR_NAME);
    }

    /**
     * 获取 txt 蓝图目录：gameDir/ai-helper/structures/txts/
     */
    public static Path getTxtsDir() {
        return getStructuresDir().resolve("txts");
    }

    /**
     * 获取 nbt 结构目录：gameDir/ai-helper/structures/nbts/
     */
    public static Path getNbtsDir() {
        return getStructuresDir().resolve("nbts");
    }

    /**
     * 获取 litematic 结构目录：gameDir/ai-helper/structures/litematic/
     */
    public static Path getLitematicDir() {
        return getStructuresDir().resolve("litematic");
    }

    /**
     * 获取 config 目录：gameDir/ai-helper/config/
     */
    public static Path getConfigDir() {
        return getModDir().resolve("config");
    }

    /**
     * 获取知识库根目录：gameDir/ai-helper/knowledge/
     * 下含知识库子目录（如 basic_info_en/），用户可自行增删 Markdown 文档。
     */
    public static Path getKnowledgeDir() {
        return getModDir().resolve(KNOWLEDGE_DIR_NAME);
    }

    /**
     * 获取配置文件路径：gameDir/ai-helper/config/ai-builder.properties
     */
    public static Path getConfigFile() {
        return getConfigDir().resolve("ai-builder.properties");
    }

    /**
     * 获取截图目录：gameDir/ai-helper/screenshots/
     */
    public static Path getScreenshotsDir() {
        return getModDir().resolve("screenshots");
    }

    /**
     * 确保目录存在，不存在则创建。
     */
    public static Path ensureDir(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    /**
     * 文件系统中真正不允许出现在文件名里的字符（Windows 最严格，取其并集以保证跨平台安全）：
     * {@code \ / : * ? " < > |} 以及 ASCII 控制字符（0x00-0x1F）。
     * 中文、日文、韩文等 Unicode 文字、空格、括号等符号都是合法的，不应被替换。
     */
    private static final java.util.regex.Pattern ILLEGAL_FILENAME_CHARS =
            java.util.regex.Pattern.compile("[\\\\/:*?\"<>|\\x00-\\x1F]");

    /** 文件名（不含扩展名）的最大长度，避免过长文件名在某些文件系统上报错。 */
    private static final int MAX_FILENAME_LENGTH = 100;

    /**
     * 清理用户输入的文件名：只移除文件系统真正不允许的非法字符（如 {@code / \ : * ? " < > |}），
     * 保留中文、日文等 Unicode 文字、空格及大部分常见符号，避免把中文或特殊符号误替换为下划线。
     *
     * @param name       原始文件名（不含扩展名）
     * @param fallback   清理后为空时使用的默认名称
     * @return 清理后的安全文件名
     */
    public static String sanitizeFileName(String name, String fallback) {
        if (name == null) {
            return fallback;
        }
        String cleaned = ILLEGAL_FILENAME_CHARS.matcher(name.trim()).replaceAll("_");
        // 折叠连续空白为单个空格，避免异常排版
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        // 去除首尾的点号（Windows 上末尾的点会被自动去除，可能引发歧义）
        cleaned = cleaned.replaceAll("^\\.+|\\.+$", "");
        if (cleaned.length() > MAX_FILENAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_FILENAME_LENGTH);
        }
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
