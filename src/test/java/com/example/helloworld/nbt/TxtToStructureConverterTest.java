package com.example.helloworld.nbt;

import com.example.helloworld.blueprint.BlueprintData;
import com.example.helloworld.blueprint.BlueprintParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 TxtToStructureConverter：将 BlueprintData（V2/V3）转换为 StructureData。
 *
 * 重点验证 V3（世界绝对坐标）输入会被按最小坐标归一化为从 0 起的相对坐标
 * （即 V3 → V2 的坐标语义转换），而 V2 输入的坐标保持原样不变。
 */
class TxtToStructureConverterTest {

    @Test
    void convert_v2_keepsCoordinatesAsIs() {
        String v2 = """
                # MCBLUEPRINT v2
                # name: v2_test
                
                ## BLOCKS
                
                0,0,0   stone
                1,0,0   dirt
                """;
        BlueprintData blueprint = BlueprintParser.parse(v2);

        NbtStructureParser.StructureData data = TxtToStructureConverter.convert(blueprint, "v2_test.txt");

        assertEquals(2, data.blocks.size());
        assertEquals(0, data.blocks.get(0).x);
        assertEquals(0, data.blocks.get(0).y);
        assertEquals(0, data.blocks.get(0).z);
        assertEquals(1, data.blocks.get(1).x);
    }

    @Test
    void convert_v3_normalizesToMinOrigin() {
        // 世界绝对坐标：x 从 100 到 102，y 固定 64，z 从 -200 到 -199
        String v3 = """
                # MCBLUEPRINT v3
                # name: v3_test
                
                ## BLOCKS
                
                100,64,-200   stone
                101,64,-200   dirt
                102,64,-199   glass
                """;
        BlueprintData blueprint = BlueprintParser.parse(v3);
        assertTrue(blueprint.isV3());

        NbtStructureParser.StructureData data = TxtToStructureConverter.convert(blueprint, "v3_test.txt");

        assertEquals(3, data.blocks.size());
        // 归一化后坐标应从 0 起：min(100,64,-200) 被减掉
        assertEquals(0, data.blocks.get(0).x);
        assertEquals(0, data.blocks.get(0).y);
        assertEquals(0, data.blocks.get(0).z);

        assertEquals(1, data.blocks.get(1).x);
        assertEquals(0, data.blocks.get(1).y);
        assertEquals(0, data.blocks.get(1).z);

        assertEquals(2, data.blocks.get(2).x);
        assertEquals(0, data.blocks.get(2).y);
        assertEquals(1, data.blocks.get(2).z);

        // 归一化后不应再有负坐标
        for (NbtStructureParser.BlockEntry b : data.blocks) {
            assertTrue(b.x >= 0 && b.y >= 0 && b.z >= 0, "归一化后坐标不应为负");
        }
    }

    @Test
    void convert_v1_throwsException() {
        String v1 = "|name=test\n|S=Stone\n|----第1层|\nS\n}}\n";
        BlueprintData blueprint = BlueprintParser.parse(v1);
        assertFalse(blueprint.isBlockList());

        assertThrows(IllegalArgumentException.class,
                () -> TxtToStructureConverter.convert(blueprint, "v1_test.txt"));
    }
}
