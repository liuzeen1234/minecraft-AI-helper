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
 *   ├── txts/      蓝图文本文件
 *   ├── nbts/      NBT 结构文件
 *   └── ...        其他模组数据
 */
public class ModPaths {

    private static final String MOD_DIR_NAME = "ai-helper";

    /**
     * 获取模组根目录：gameDir/ai-helper/
     */
    public static Path getModDir() {
        return FabricLoader.getInstance().getGameDir().resolve(MOD_DIR_NAME);
    }

    /**
     * 获取 txts 目录：gameDir/ai-helper/txts/
     */
    public static Path getTxtsDir() {
        return getModDir().resolve("txts");
    }

    /**
     * 获取 nbts 目录：gameDir/ai-helper/nbts/
     */
    public static Path getNbtsDir() {
        return getModDir().resolve("nbts");
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
