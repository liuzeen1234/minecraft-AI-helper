package com.example.helloworld.blueprint;

import java.util.Collections;
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
    // 容器物品条目：slot + 物品ID + 数量 + 可选 NBT（SNBT 格式）
    // -------------------------------------------------------------------------
    public static class ItemEntry {
        private final int slot;
        private final String itemId;
        private final int count;
        private final String nbtString; // 可选，SNBT 格式，可能为 null

        public ItemEntry(int slot, String itemId, int count, String nbtString) {
            this.slot = slot;
            this.itemId = itemId;
            this.count = count;
            this.nbtString = nbtString;
        }

        public int getSlot() { return slot; }
        public String getItemId() { return itemId; }
        public int getCount() { return count; }
        public String getNbtString() { return nbtString; }
    }

    // -------------------------------------------------------------------------
    // 告示牌文字条目：front/back 各4行文字
    // -------------------------------------------------------------------------
    public static class SignTextEntry {
        private final List<String> frontLines; // 正面4行
        private final List<String> backLines;  // 背面4行

        public SignTextEntry(List<String> frontLines, List<String> backLines) {
            this.frontLines = frontLines != null ? frontLines : List.of("", "", "", "");
            this.backLines = backLines != null ? backLines : List.of("", "", "", "");
        }

        public List<String> getFrontLines() { return frontLines; }
        public List<String> getBackLines() { return backLines; }

        /** 检查是否有非空文本 */
        public boolean hasText() {
            for (String line : frontLines) if (!line.isEmpty()) return true;
            for (String line : backLines) if (!line.isEmpty()) return true;
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // V2 方块条目：显式坐标 + 方块ID + 完整 block state 属性 + 可选容器物品 + 可选告示牌文字
    // -------------------------------------------------------------------------
    public static class BlockEntry3D {
        private final int x, y, z;
        private final String blockId;
        private final Map<String, String> properties;
        private final List<ItemEntry> items; // 容器内容物，无则为空列表
        private final SignTextEntry signText; // 告示牌文字，无则为 null

        public BlockEntry3D(int x, int y, int z, String blockId, Map<String, String> properties) {
            this(x, y, z, blockId, properties, Collections.emptyList(), null);
        }

        public BlockEntry3D(int x, int y, int z, String blockId, Map<String, String> properties, List<ItemEntry> items) {
            this(x, y, z, blockId, properties, items, null);
        }

        public BlockEntry3D(int x, int y, int z, String blockId, Map<String, String> properties, List<ItemEntry> items, SignTextEntry signText) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = blockId;
            this.properties = properties;
            this.items = items != null ? items : Collections.emptyList();
            this.signText = signText;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }
        public String getBlockId() { return blockId; }
        public Map<String, String> getProperties() { return properties; }
        public List<ItemEntry> getItems() { return items; }
        public boolean hasItems() { return !items.isEmpty(); }
        public SignTextEntry getSignText() { return signText; }
        public boolean hasSignText() { return signText != null && signText.hasText(); }
    }
}
