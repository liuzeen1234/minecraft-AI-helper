package com.example.helloworld.blueprint;

import java.util.List;
import java.util.Map;

/**
 * 解析后的蓝图数据。
 *
 * 支持两种格式：
 *   V1：{{layered blueprint}} 字符网格格式（旧格式，向后兼容）
 *   V2：MCBLUEPRINT v2 逐方块显式坐标格式（新格式）
 */
public class BlueprintData {

    private final String name;

    // ---- V1 格式字段 ----
    private final Map<Character, BlockEntry> legend;
    private final List<char[][]> layers;

    // ---- V2 格式字段 ----
    private final List<BlockEntry3D> blocks3d;
    private final int sizeX, sizeY, sizeZ;

    /** 构造 V1 蓝图 */
    public BlueprintData(String name, Map<Character, BlockEntry> legend, List<char[][]> layers) {
        this.name = name;
        this.legend = legend;
        this.layers = layers;
        this.blocks3d = null;
        this.sizeX = this.sizeY = this.sizeZ = 0;
    }

    /** 构造 V2 蓝图 */
    public BlueprintData(String name, List<BlockEntry3D> blocks3d, int sizeX, int sizeY, int sizeZ) {
        this.name = name;
        this.blocks3d = blocks3d;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.legend = null;
        this.layers = null;
    }

    public String getName() { return name; }

    /** 是否为 V2 格式 */
    public boolean isV2() { return blocks3d != null; }

    // V1 访问器
    public Map<Character, BlockEntry> getLegend() { return legend; }
    public List<char[][]> getLayers() { return layers; }

    // V2 访问器
    public List<BlockEntry3D> getBlocks3d() { return blocks3d; }
    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }

    // -------------------------------------------------------------------------
    // V1 方块条目：方块ID + 可选属性（如朝向、半砖位置等）
    // -------------------------------------------------------------------------
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

    // -------------------------------------------------------------------------
    // V2 方块条目：显式坐标 + 方块ID + 完整 block state 属性
    // -------------------------------------------------------------------------
    public static class BlockEntry3D {
        private final int x, y, z;
        private final String blockId;
        private final Map<String, String> properties;

        public BlockEntry3D(int x, int y, int z, String blockId, Map<String, String> properties) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            this.properties = properties;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public String getBlockId() { return blockId; }
        public Map<String, String> getProperties() { return properties; }
    }
}
