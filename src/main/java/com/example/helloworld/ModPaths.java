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
}
