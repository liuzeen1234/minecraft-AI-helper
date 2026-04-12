package com.example.helloworld.blueprint;

import java.util.List;
import java.util.Map;

/**
 * 解析后的蓝图数据，包含建筑名称、图例映射和各层的方块网格。
 */
public class BlueprintData {

    private final String name;
    private final Map<Character, BlockEntry> legend;
    private final List<char[][]> layers;

    public BlueprintData(String name, Map<Character, BlockEntry> legend, List<char[][]> layers) {
        this.name = name;
        this.legend = legend;
        this.layers = layers;
    }

    public String getName() { return name; }
    public Map<Character, BlockEntry> getLegend() { return legend; }
    public List<char[][]> getLayers() { return layers; }

    /**
     * 单个方块条目：方块ID + 可选的属性（如朝向、半砖位置等）
     */
    public static class BlockEntry {
        private final String blockId;
        private final Map<String, String> properties;

        public BlockEntry(String blockId, Map<String, String> properties) {
            this.blockId = blockId;
            this.properties = properties;
        }

        public String getBlockId() { return blockId; }
        public Map<String, String> getProperties() { return properties; }
    }
}
