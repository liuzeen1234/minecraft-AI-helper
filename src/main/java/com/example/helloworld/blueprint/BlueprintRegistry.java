package com.example.helloworld.blueprint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 蓝图注册表，从 architect-docs/ 目录加载所有 .txt 蓝图文件。
 * 支持按建筑名称模糊匹配。
 */
public class BlueprintRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("BlueprintRegistry");
    private static final String BLUEPRINT_DIR = "architect-docs";

    // key = 蓝图名称（小写），value = 解析后的蓝图数据
    private final Map<String, BlueprintData> blueprints = new LinkedHashMap<>();

    /**
     * 扫描 architect-docs/ 目录，加载所有 .txt 蓝图文件。
     * Minecraft 运行时工作目录是 run/，所以需要向上一级找项目根目录。
     */
    public void loadAll() {
        blueprints.clear();

        // 尝试多个可能的路径：当前目录、上一级目录（run/ 的情况）
        Path dir = Paths.get(BLUEPRINT_DIR);
        if (!Files.isDirectory(dir)) {
            dir = Paths.get("..").resolve(BLUEPRINT_DIR);
        }
        if (!Files.isDirectory(dir)) {
            LOGGER.warn("蓝图目录不存在: {}", Paths.get(BLUEPRINT_DIR).toAbsolutePath());
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path file : stream) {
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    BlueprintData data = BlueprintParser.parse(content);
                    String key = data.getName().toLowerCase().trim();
                    blueprints.put(key, data);
                    LOGGER.info("已加载蓝图: '{}' (来自 {})", data.getName(), file.getFileName());
                } catch (Exception e) {
                    LOGGER.error("解析蓝图文件失败: {}", file, e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("扫描蓝图目录失败", e);
        }

        LOGGER.info("共加载 {} 个蓝图", blueprints.size());
    }

    /**
     * 按名称查找蓝图，支持精确匹配和包含匹配。
     */
    public BlueprintData find(String query) {
        String q = query.toLowerCase().trim();

        // 精确匹配
        if (blueprints.containsKey(q)) {
            return blueprints.get(q);
        }

        // 包含匹配
        for (Map.Entry<String, BlueprintData> entry : blueprints.entrySet()) {
            if (entry.getKey().contains(q) || q.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 获取所有已加载的蓝图名称。
     */
    public java.util.Set<String> getNames() {
        return blueprints.keySet();
    }

    public int size() {
        return blueprints.size();
    }
}
