package com.example.helloworld;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 AICommandExecutor 的解析逻辑：
 * - JSON 字段提取
 * - [ACTION] / [BLUEPRINT] / [SEARCH] / [FETCH] 标签识别
 * - processResponse 在无玩家时的行为
 */
class AICommandExecutorTest {

    // ========== 多轮工具循环：游戏操作标签检测 ==========

    @Test
    void testContainsGameActionTags_Action() {
        String r = "好的 [ACTION]{\"type\":\"place_block\",\"block\":\"stone\"}[/ACTION]";
        assertTrue(AICommandExecutor.containsGameActionTags(r));
    }

    @Test
    void testContainsGameActionTags_Blueprint() {
        String r = "开始建造 [BLUEPRINT]\n# MCBLUEPRINT v2\n[/BLUEPRINT]";
        assertTrue(AICommandExecutor.containsGameActionTags(r));
    }

    @Test
    void testContainsGameActionTags_UnclosedBlueprint() {
        // token 截断导致未闭合，也应识别为请求了游戏操作
        String r = "开始建造 [BLUEPRINT]\n# MCBLUEPRINT v2\n0,0,0 stone";
        assertTrue(AICommandExecutor.containsGameActionTags(r));
    }

    @Test
    void testContainsGameActionTags_None() {
        assertFalse(AICommandExecutor.containsGameActionTags("普通聊天回复，没有任何标签。"));
        assertFalse(AICommandExecutor.containsGameActionTags(""));
        assertFalse(AICommandExecutor.containsGameActionTags(null));
        // 仅联网标签不算游戏操作
        assertFalse(AICommandExecutor.containsGameActionTags("[SEARCH]红石[/SEARCH]"));
    }

    // ========== JSON 解析测试 ==========

    @Test
    void testExtractJsonString_BasicField() {
        String json = "{\"type\":\"place_block\",\"block\":\"oak_planks\"}";
        assertEquals("place_block", AICommandExecutor.extractJsonString(json, "type"));
        assertEquals("oak_planks", AICommandExecutor.extractJsonString(json, "block"));
    }

    @Test
    void testExtractJsonString_WithSpaces() {
        String json = "{\"type\" : \"fill_blocks\" , \"block\" : \"stone\"}";
        assertEquals("fill_blocks", AICommandExecutor.extractJsonString(json, "type"));
        assertEquals("stone", AICommandExecutor.extractJsonString(json, "block"));
    }

    @Test
    void testExtractJsonString_MissingField() {
        String json = "{\"type\":\"place_block\"}";
        assertNull(AICommandExecutor.extractJsonString(json, "block"));
        assertNull(AICommandExecutor.extractJsonString(json, "nonexistent"));
    }

    @Test
    void testExtractJsonInt_BasicField() {
        String json = "{\"forward\":10,\"right\":-5,\"up\":3}";
        assertEquals(10, AICommandExecutor.extractJsonInt(json, "forward", 0));
        assertEquals(-5, AICommandExecutor.extractJsonInt(json, "right", 0));
        assertEquals(3, AICommandExecutor.extractJsonInt(json, "up", 0));
    }

    @Test
    void testExtractJsonInt_WithSpaces() {
        String json = "{\"x1\" : 100, \"y1\" : -64, \"z1\" : 200}";
        assertEquals(100, AICommandExecutor.extractJsonInt(json, "x1", 0));
        assertEquals(-64, AICommandExecutor.extractJsonInt(json, "y1", 0));
        assertEquals(200, AICommandExecutor.extractJsonInt(json, "z1", 0));
    }

    @Test
    void testExtractJsonInt_MissingField_ReturnsDefault() {
        String json = "{\"forward\":10}";
        assertEquals(0, AICommandExecutor.extractJsonInt(json, "right", 0));
        assertEquals(99, AICommandExecutor.extractJsonInt(json, "missing", 99));
    }

    @Test
    void testExtractJsonInt_NegativeNumbers() {
        String json = "{\"forward_from\":-3,\"forward_to\":5}";
        assertEquals(-3, AICommandExecutor.extractJsonInt(json, "forward_from", 0));
        assertEquals(5, AICommandExecutor.extractJsonInt(json, "forward_to", 0));
    }

    // ========== processResponse 标签解析测试 ==========

    @Test
    void testProcessResponse_NullPlayer_ReturnsOriginal() {
        String response = "Hello! [ACTION]{\"type\":\"place_block\"}[/ACTION]";
        // player 为 null 时应直接返回原文
        String result = AICommandExecutor.processResponse(response, null);
        assertEquals(response, result);
    }

    @Test
    void testProcessResponse_NoTags_ReturnsCleanText() {
        // 没有任何标签的纯文本回复
        String response = "这是一个普通的回复，没有任何指令。";
        String result = AICommandExecutor.processResponse(response, null);
        assertEquals(response, result);
    }

    // ========== 标签提取逻辑测试（不依赖 Minecraft 运行时） ==========

    @Test
    void testActionTagDetection() {
        String response = "我来帮你放一个方块 [ACTION]{\"type\":\"place_block\",\"block\":\"stone\",\"forward\":5}[/ACTION] 好了！";

        // 验证 ACTION 标签能被正确识别
        assertTrue(response.contains("[ACTION]"));
        assertTrue(response.contains("[/ACTION]"));

        // 提取 JSON 内容
        int start = response.indexOf("[ACTION]") + 8;
        int end = response.indexOf("[/ACTION]");
        String json = response.substring(start, end).trim();

        assertEquals("place_block", AICommandExecutor.extractJsonString(json, "type"));
        assertEquals("stone", AICommandExecutor.extractJsonString(json, "block"));
        assertEquals(5, AICommandExecutor.extractJsonInt(json, "forward", 0));
    }

    @Test
    void testBlueprintTagDetection() {
        String response = "这是一个小房子：\n[BLUEPRINT]\n# MCBLUEPRINT v2\n# name: house\n\n## BLOCKS\n\n0,0,0   stone\n[/BLUEPRINT]\n完成！";

        assertTrue(response.contains("[BLUEPRINT]"));
        assertTrue(response.contains("[/BLUEPRINT]"));

        int start = response.indexOf("[BLUEPRINT]") + 11;
        int end = response.indexOf("[/BLUEPRINT]");
        String blueprintText = response.substring(start, end).trim();

        assertTrue(blueprintText.contains("# MCBLUEPRINT v2"));
        assertTrue(blueprintText.contains("stone"));
    }

    @Test
    void testMultipleActionTags() {
        String response = "[ACTION]{\"type\":\"set_time\",\"value\":\"day\"}[/ACTION] "
                + "[ACTION]{\"type\":\"set_weather\",\"value\":\"clear\"}[/ACTION]";

        // 应该能找到两个 ACTION 标签
        int count = 0;
        int idx = 0;
        while ((idx = response.indexOf("[ACTION]", idx)) != -1) {
            count++;
            idx++;
        }
        assertEquals(2, count);
    }

    @Test
    void testTruncatedBlueprint_Detection() {
        // 模拟 AI 输出被截断（没有 [/BLUEPRINT] 闭合标签）
        String response = "建造中...\n[BLUEPRINT]\n# MCBLUEPRINT v2\n# name: big_house\n\n## BLOCKS\n\n0,0,0   stone\n1,0,0   stone\n2,0,";

        // 没有闭合标签
        assertFalse(response.contains("[/BLUEPRINT]"));
        assertTrue(response.contains("[BLUEPRINT]"));

        // 应该能检测到未闭合的蓝图
        int blueprintStart = response.indexOf("[BLUEPRINT]");
        assertTrue(blueprintStart >= 0);
    }

    // ========== fill_blocks 参数解析测试 ==========

    @Test
    void testFillBlocksParams_AbsoluteCoords() {
        String json = "{\"type\":\"fill_blocks\",\"block\":\"stone\",\"x1\":0,\"y1\":64,\"z1\":0,\"x2\":10,\"y2\":70,\"z2\":10}";

        assertEquals("fill_blocks", AICommandExecutor.extractJsonString(json, "type"));
        assertEquals("stone", AICommandExecutor.extractJsonString(json, "block"));
        assertEquals(0, AICommandExecutor.extractJsonInt(json, "x1", Integer.MIN_VALUE));
        assertEquals(64, AICommandExecutor.extractJsonInt(json, "y1", Integer.MIN_VALUE));
        assertEquals(10, AICommandExecutor.extractJsonInt(json, "x2", Integer.MIN_VALUE));
        assertEquals(70, AICommandExecutor.extractJsonInt(json, "y2", Integer.MIN_VALUE));
    }

    @Test
    void testFillBlocksParams_RelativeCoords() {
        String json = "{\"type\":\"fill_blocks\",\"block\":\"oak_planks\",\"forward_from\":0,\"forward_to\":5,\"right_from\":-2,\"right_to\":2,\"up_from\":0,\"up_to\":3}";

        assertEquals("fill_blocks", AICommandExecutor.extractJsonString(json, "type"));
        assertEquals(0, AICommandExecutor.extractJsonInt(json, "forward_from", 99));
        assertEquals(5, AICommandExecutor.extractJsonInt(json, "forward_to", 99));
        assertEquals(-2, AICommandExecutor.extractJsonInt(json, "right_from", 99));
        assertEquals(2, AICommandExecutor.extractJsonInt(json, "right_to", 99));
    }

    // ========== give_item 参数解析测试 ==========

    @Test
    void testGiveItemParams() {
        String json = "{\"type\":\"give_item\",\"item\":\"diamond_sword\",\"count\":1}";
        assertEquals("give_item", AICommandExecutor.extractJsonString(json, "type"));
        assertEquals("diamond_sword", AICommandExecutor.extractJsonString(json, "item"));
        assertEquals(1, AICommandExecutor.extractJsonInt(json, "count", 1));
    }

    // ========== summon 参数解析测试 ==========

    @Test
    void testSummonParams() {
        String json = "{\"type\":\"summon\",\"entity\":\"zombie\",\"forward\":5,\"right\":0,\"up\":0,\"count\":3}";
        assertEquals("summon", AICommandExecutor.extractJsonString(json, "type"));
        assertEquals("zombie", AICommandExecutor.extractJsonString(json, "entity"));
        assertEquals(3, AICommandExecutor.extractJsonInt(json, "count", 1));
    }

    // ========== set_time / set_weather 参数解析测试 ==========

    @Test
    void testSetTimeParams() {
        String json = "{\"type\":\"set_time\",\"value\":\"day\"}";
        assertEquals("set_time", AICommandExecutor.extractJsonString(json, "type"));
        assertEquals("day", AICommandExecutor.extractJsonString(json, "value"));
    }

    @Test
    void testSetWeatherParams() {
        String json = "{\"type\":\"set_weather\",\"value\":\"thunder\"}";
        assertEquals("set_weather", AICommandExecutor.extractJsonString(json, "type"));
        assertEquals("thunder", AICommandExecutor.extractJsonString(json, "value"));
    }

    // ========== getSystemPrompt 测试 ==========

    @Test
    void testGetSystemPrompt_ContainsAllInstructions() {
        String prompt = AICommandExecutor.getSystemPrompt();

        assertNotNull(prompt);
        assertFalse(prompt.isEmpty());

        // 应包含所有指令类型的说明
        assertTrue(prompt.contains("place_block"));
        assertTrue(prompt.contains("fill_blocks"));
        assertTrue(prompt.contains("give_item"));
        assertTrue(prompt.contains("set_time"));
        assertTrue(prompt.contains("set_weather"));
        assertTrue(prompt.contains("summon"));
        assertTrue(prompt.contains("clear_area"));
        assertTrue(prompt.contains("[BLUEPRINT]"));
        assertTrue(prompt.contains("[ACTION]"));
        assertTrue(prompt.contains("[SEARCH]"));
        assertTrue(prompt.contains("[FETCH]"));
        assertTrue(prompt.contains("MCBLUEPRINT v2"));
        // V3（世界绝对坐标）说明也应包含在系统提示词中，供 AI 在查完地形后直接复用绝对坐标建造
        assertTrue(prompt.contains("MCBLUEPRINT v3"));
    }

    @Test
    void testGetSystemPrompt_VanillaCommandsEnabled_ContainsExecuteCommand() {
        String prompt = AICommandExecutor.getSystemPrompt(true);
        assertTrue(prompt.contains("execute_command"));
    }

    @Test
    void testGetSystemPrompt_VanillaCommandsDisabled_OmitsExecuteCommand() {
        String prompt = AICommandExecutor.getSystemPrompt(false);
        assertFalse(prompt.contains("execute_command"));
        // 关闭时其他指令说明应仍然存在，不受影响
        assertTrue(prompt.contains("place_block"));
        assertTrue(prompt.contains("[BLUEPRINT]"));
    }

    @Test
    void testGetSystemPrompt_NoArgDefaultsToEnabled() {
        // 无参重载版本应等价于 getSystemPrompt(true)，保持向后兼容
        assertEquals(AICommandExecutor.getSystemPrompt(true), AICommandExecutor.getSystemPrompt());
    }

    @Test
    void testGetSystemPrompt_AdvisesSingleToolPerRoundUnlessNecessary() {
        String prompt = AICommandExecutor.getSystemPrompt();
        assertTrue(prompt.contains("非必要时不要在同一轮回复中使用多个工具标签"));
        assertTrue(prompt.contains("优先调用一个工具、等待结果、再按结果决定下一步"));
    }

    // ========== 标签“串味”回归测试：正文里提到标签名不应污染真正调用的参数 ==========

    @Test
    void testExtractQueryRegion_CleanTag() {
        String response = "帮你查一下周围地形：\n[QUERY_REGION]around 10[/QUERY_REGION]";
        assertEquals("around 10", AICommandExecutor.extractQueryRegionSpec(response));
    }

    @Test
    void testExtractQueryRegion_MentionedTagNameInProse_DoesNotContaminate() {
        // 复现历史 bug：AI 在正文里解释性地写出一个未闭合的 [QUERY_REGION]，
        // 后面才是真正的调用。提取应只拿到干净的 "around 10"，而不是把中间的中文说明吞进去。
        String response = "我用 [QUERY_REGION]around 8 上一条也发了，但结果没回来，我再单独发一次干净的查询：\n\n"
                + "[QUERY_REGION]around 10[/QUERY_REGION]";
        assertEquals("around 10", AICommandExecutor.extractQueryRegionSpec(response));
    }

    @Test
    void testExtractQueryRegion_AbsoluteCoords_WithProseMention() {
        String response = "我要用 [QUERY_REGION] 查询这块地形。\n[QUERY_REGION]100,60,-200 120,80,-180[/QUERY_REGION]";
        assertEquals("100,60,-200 120,80,-180", AICommandExecutor.extractQueryRegionSpec(response));
    }

    @Test
    void testExtractCameraShot_MentionedTagNameInProse_DoesNotContaminate() {
        String response = "我打算用 [CAMERA_SHOT] 拍一张验收图，正对刚建好的平台：\n"
                + "[CAMERA_SHOT]-644,102,429,-90,20[/CAMERA_SHOT]";
        assertEquals("-644,102,429,-90,20", AICommandExecutor.extractCameraShotSpec(response));
    }

    @Test
    void testExtractQueryRegion_NoRealCall_ReturnsNull() {
        // 正文里只是提到标签名，没有真正闭合的调用，不应误触发。
        String response = "你可以用 [QUERY_REGION] 这个工具来查询地形，需要我查吗？";
        assertNull(AICommandExecutor.extractQueryRegionSpec(response));
    }
}
