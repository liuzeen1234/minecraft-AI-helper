package com.example.helloworld.blueprint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 BlueprintParser：V1 和 V2 格式的蓝图解析。
 */
class BlueprintParserTest {

    // ========== V2 格式测试 ==========

    @Test
    void testParseV2_BasicStructure() {
        String blueprint = """
                # MCBLUEPRINT v2
                # name: test_house
                # size: 3x2x3
                
                ## BLOCKS
                
                0,0,0   stone_bricks
                1,0,0   stone_bricks
                2,0,0   stone_bricks
                0,1,0   oak_planks
                1,1,0   glass_pane
                2,1,0   oak_planks
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        assertTrue(data.isV2());
        assertEquals("test_house", data.getName());
        assertEquals(6, data.getBlocks3d().size());
        assertEquals(3, data.getSizeX());
        assertEquals(2, data.getSizeY());
        assertEquals(3, data.getSizeZ());
    }

    @Test
    void testParseV2_WithProperties() {
        String blueprint = """
                # MCBLUEPRINT v2
                # name: stairs_test
                
                ## BLOCKS
                
                0,0,0   oak_stairs   facing=north   half=bottom   shape=straight
                1,0,0   oak_door   facing=south   half=lower   hinge=left   open=false
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        assertTrue(data.isV2());
        assertEquals(2, data.getBlocks3d().size());

        BlueprintData.BlockEntry3D stairs = data.getBlocks3d().get(0);
        assertEquals("oak_stairs", stairs.getBlockId());
        assertEquals("north", stairs.getProperties().get("facing"));
        assertEquals("bottom", stairs.getProperties().get("half"));
        assertEquals("straight", stairs.getProperties().get("shape"));

        BlueprintData.BlockEntry3D door = data.getBlocks3d().get(1);
        assertEquals("oak_door", door.getBlockId());
        assertEquals("south", door.getProperties().get("facing"));
        assertEquals("lower", door.getProperties().get("half"));
    }

    @Test
    void testParseV2_SkipsAirBlocks() {
        String blueprint = """
                # MCBLUEPRINT v2
                # name: air_test
                
                ## BLOCKS
                
                0,0,0   stone
                1,0,0   air
                2,0,0   stone
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        // air 应被跳过
        assertEquals(2, data.getBlocks3d().size());
    }

    @Test
    void testParseV2_StripsMinecraftPrefix() {
        String blueprint = """
                # MCBLUEPRINT v2
                # name: prefix_test
                
                ## BLOCKS
                
                0,0,0   minecraft:stone_bricks
                1,0,0   minecraft:oak_planks
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        assertEquals("stone_bricks", data.getBlocks3d().get(0).getBlockId());
        assertEquals("oak_planks", data.getBlocks3d().get(1).getBlockId());
    }

    @Test
    void testParseV2_InfersSizeFromBlocks() {
        // 没有 size 头部，应从方块坐标推算
        String blueprint = """
                # MCBLUEPRINT v2
                # name: infer_size
                
                ## BLOCKS
                
                0,0,0   stone
                4,2,3   stone
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        assertEquals(5, data.getSizeX()); // max x + 1
        assertEquals(3, data.getSizeY()); // max y + 1
        assertEquals(4, data.getSizeZ()); // max z + 1
    }

    @Test
    void testParseV2_IgnoresComments() {
        String blueprint = """
                # MCBLUEPRINT v2
                # name: comment_test
                # This is a comment
                # Another comment
                
                ## BLOCKS
                
                # --- 第 1 层 (y=0) ---
                0,0,0   stone
                # 这是注释
                1,0,0   stone
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        assertEquals(2, data.getBlocks3d().size());
    }

    @Test
    void testParseV2_InlineComments() {
        String blueprint = """
                # MCBLUEPRINT v2
                # name: inline_comment
                
                ## BLOCKS
                
                0,0,0   oak_stairs   facing=north   # 北向楼梯
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        assertEquals(1, data.getBlocks3d().size());
        BlueprintData.BlockEntry3D block = data.getBlocks3d().get(0);
        assertEquals("oak_stairs", block.getBlockId());
        assertEquals("north", block.getProperties().get("facing"));
        // 注释不应被当作属性
        assertFalse(block.getProperties().containsKey("#"));
    }

    @Test
    void testParseV2_EmptyBlueprint() {
        String blueprint = """
                # MCBLUEPRINT v2
                # name: empty
                
                ## BLOCKS
                
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        assertTrue(data.isV2());
        assertEquals("empty", data.getName());
        assertEquals(0, data.getBlocks3d().size());
    }

    // ========== V1 格式测试 ==========

    @Test
    void testParseV1_BasicLayeredBlueprint() {
        String blueprint = """
                |name=Simple Wall
                |S=Stone
                |----第1层|
                SSS
                S S
                SSS
                }}
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        assertFalse(data.isV2());
        assertEquals("Simple Wall", data.getName());
        assertNotNull(data.getLegend());
        assertTrue(data.getLegend().containsKey('S'));
        assertEquals("stone", data.getLegend().get('S').getBlockId());
        assertEquals(1, data.getLayers().size());
    }

    @Test
    void testParseV1_MultipleLayersAndLegend() {
        String blueprint = """
                |name=Two Story
                |S=Stone Bricks
                |W=Oak Planks
                |----第1层|
                SSS
                S S
                SSS
                |----第2层|
                WWW
                W W
                WWW
                }}
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        assertFalse(data.isV2());
        assertEquals("Two Story", data.getName());
        assertEquals(2, data.getLegend().size());
        assertEquals(2, data.getLayers().size());
        assertEquals("stone_bricks", data.getLegend().get('S').getBlockId());
        assertEquals("oak_planks", data.getLegend().get('W').getBlockId());
    }

    @Test
    void testParseV1_WithRotation() {
        String blueprint = """
                |name=Stairs
                |S=Smooth Sandstone Stairs-rot90
                |----第1层|
                S
                }}
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        BlueprintData.BlockEntry entry = data.getLegend().get('S');
        assertEquals("smooth_sandstone_stairs", entry.getBlockId());
        assertEquals("90", entry.getProperties().get("_rot"));
    }

    @Test
    void testParseV1_WithAttributes() {
        String blueprint = """
                |name=Door Test
                |D=Oak Door+bottom
                |U=Oak Door+top
                |----第1层|
                D
                |----第2层|
                U
                }}
                """;

        BlueprintData data = BlueprintParser.parse(blueprint);

        assertNotNull(data);
        BlueprintData.BlockEntry lower = data.getLegend().get('D');
        assertEquals("oak_door", lower.getBlockId());
        assertEquals("lower", lower.getProperties().get("half")); // bottom → lower for doors

        BlueprintData.BlockEntry upper = data.getLegend().get('U');
        assertEquals("oak_door", upper.getBlockId());
        assertEquals("upper", upper.getProperties().get("half")); // top → upper for doors
    }

    // ========== 格式检测测试 ==========

    @Test
    void testAutoDetectsV2Format() {
        String v2 = "# MCBLUEPRINT v2\n# name: test\n\n## BLOCKS\n\n0,0,0   stone\n";
        BlueprintData data = BlueprintParser.parse(v2);
        assertTrue(data.isV2());
    }

    @Test
    void testAutoDetectsV1Format() {
        String v1 = "|name=test\n|S=Stone\n|----第1层|\nS\n}}\n";
        BlueprintData data = BlueprintParser.parse(v1);
        assertFalse(data.isV2());
    }

    // ========== parseBlockEntry 测试 ==========

    @Test
    void testParseBlockEntry_SimpleBlock() {
        BlueprintData.BlockEntry entry = BlueprintParser.parseBlockEntry("Oak Planks");
        assertEquals("oak_planks", entry.getBlockId());
        assertTrue(entry.getProperties().isEmpty() || !entry.getProperties().containsKey("_rot"));
    }

    @Test
    void testParseBlockEntry_WithRotation() {
        BlueprintData.BlockEntry entry = BlueprintParser.parseBlockEntry("Oak Stairs-rot180");
        assertEquals("oak_stairs", entry.getBlockId());
        assertEquals("180", entry.getProperties().get("_rot"));
    }

    @Test
    void testParseBlockEntry_TorchBecomesWallTorch() {
        BlueprintData.BlockEntry entry = BlueprintParser.parseBlockEntry("Torch-rot90");
        assertEquals("wall_torch", entry.getBlockId());
        assertEquals("90", entry.getProperties().get("_rot"));
    }

    @Test
    void testParseBlockEntry_BedRemovesHalf() {
        BlueprintData.BlockEntry entry = BlueprintParser.parseBlockEntry("White Bed+top foot");
        assertEquals("white_bed", entry.getBlockId());
        // bed 应移除 half 属性
        assertFalse(entry.getProperties().containsKey("half"));
        assertEquals("foot", entry.getProperties().get("part"));
    }

    @Test
    void testNameToBlockId() {
        assertEquals("oak_planks", BlueprintParser.nameToBlockId("Oak Planks"));
        assertEquals("stone_bricks", BlueprintParser.nameToBlockId("Stone Bricks"));
        assertEquals("smooth_sandstone_stairs", BlueprintParser.nameToBlockId("Smooth Sandstone Stairs"));
    }
}
