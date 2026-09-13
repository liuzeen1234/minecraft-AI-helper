package com.example.helloworld;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 {@link DebugMenuIntegration} 的语言码 ↔ 菜单显示名映射逻辑。
 *
 * <p>这两个方法是 debug_menu 语言开关的核心：getter 把配置里的语言码映射为菜单里显示的
 * 选项名，setter 把玩家在菜单里选中的选项名映射回语言码写入配置。映射错误会导致菜单显示
 * 的当前语言与实际配置不一致，或切换后写入错误的语言码。
 *
 * <p>这些方法不依赖 Minecraft / debug-menu 运行时类，可在纯 JVM 下单测。
 */
class DebugMenuIntegrationTest {

    // ========== 语言码 -> 显示名 ==========

    @Test
    void testZhCodeMapsToChineseOption() {
        assertEquals(DebugMenuIntegration.OPTION_ZH,
                DebugMenuIntegration.languageCodeToOption("zh_cn"));
    }

    @Test
    void testEnCodeMapsToEnglishOption() {
        assertEquals(DebugMenuIntegration.OPTION_EN,
                DebugMenuIntegration.languageCodeToOption("en_us"));
    }

    @Test
    void testEnPrefixVariantsMapToEnglish() {
        // 只要以 en 开头都视为英文（大小写不敏感）
        assertEquals(DebugMenuIntegration.OPTION_EN,
                DebugMenuIntegration.languageCodeToOption("en_gb"));
        assertEquals(DebugMenuIntegration.OPTION_EN,
                DebugMenuIntegration.languageCodeToOption("EN_US"));
    }

    @Test
    void testNullCodeFallsBackToChinese() {
        assertEquals(DebugMenuIntegration.OPTION_ZH,
                DebugMenuIntegration.languageCodeToOption(null));
    }

    @Test
    void testUnknownCodeFallsBackToChinese() {
        // 未知/其他语言码（如日文）在 AI-helper 只支持中英双语的现状下按中文处理
        assertEquals(DebugMenuIntegration.OPTION_ZH,
                DebugMenuIntegration.languageCodeToOption("ja_jp"));
    }

    // ========== 显示名 -> 语言码 ==========

    @Test
    void testChineseOptionMapsToZhCode() {
        assertEquals("zh_cn",
                DebugMenuIntegration.optionToLanguageCode(DebugMenuIntegration.OPTION_ZH));
    }

    @Test
    void testEnglishOptionMapsToEnCode() {
        assertEquals("en_us",
                DebugMenuIntegration.optionToLanguageCode(DebugMenuIntegration.OPTION_EN));
    }

    @Test
    void testUnknownOptionFallsBackToZhCode() {
        assertEquals("zh_cn",
                DebugMenuIntegration.optionToLanguageCode("Français"));
        assertEquals("zh_cn",
                DebugMenuIntegration.optionToLanguageCode(null));
    }

    // ========== 往返一致性 ==========

    @Test
    void testRoundTripZh() {
        String code = "zh_cn";
        String option = DebugMenuIntegration.languageCodeToOption(code);
        assertEquals(code, DebugMenuIntegration.optionToLanguageCode(option));
    }

    @Test
    void testRoundTripEn() {
        String code = "en_us";
        String option = DebugMenuIntegration.languageCodeToOption(code);
        assertEquals(code, DebugMenuIntegration.optionToLanguageCode(option));
    }

    // ========== 选项列表 ==========

    @Test
    void testOptionsContainBothLanguages() {
        assertTrue(DebugMenuIntegration.OPTIONS.contains(DebugMenuIntegration.OPTION_ZH));
        assertTrue(DebugMenuIntegration.OPTIONS.contains(DebugMenuIntegration.OPTION_EN));
        assertEquals(2, DebugMenuIntegration.OPTIONS.size());
    }

    // ========== 日志级别：Level -> 显示名 ==========

    @Test
    void testErrorLevelMapsToErrorOption() {
        assertEquals(DebugMenuIntegration.LEVEL_ERROR,
                DebugMenuIntegration.levelToOption(org.apache.logging.log4j.Level.ERROR));
    }

    @Test
    void testFatalLevelMapsToErrorOption() {
        // FATAL 比 ERROR 更严重，在只有四档的菜单里归入 ERROR
        assertEquals(DebugMenuIntegration.LEVEL_ERROR,
                DebugMenuIntegration.levelToOption(org.apache.logging.log4j.Level.FATAL));
    }

    @Test
    void testWarnLevelMapsToWarnOption() {
        assertEquals(DebugMenuIntegration.LEVEL_WARN,
                DebugMenuIntegration.levelToOption(org.apache.logging.log4j.Level.WARN));
    }

    @Test
    void testInfoLevelMapsToInfoOption() {
        assertEquals(DebugMenuIntegration.LEVEL_INFO,
                DebugMenuIntegration.levelToOption(org.apache.logging.log4j.Level.INFO));
    }

    @Test
    void testDebugLevelMapsToDebugOption() {
        assertEquals(DebugMenuIntegration.LEVEL_DEBUG,
                DebugMenuIntegration.levelToOption(org.apache.logging.log4j.Level.DEBUG));
    }

    @Test
    void testTraceLevelMapsToDebugOption() {
        // TRACE 比 DEBUG 更细，归入菜单最细档 DEBUG
        assertEquals(DebugMenuIntegration.LEVEL_DEBUG,
                DebugMenuIntegration.levelToOption(org.apache.logging.log4j.Level.TRACE));
    }

    @Test
    void testNullLevelFallsBackToErrorOption() {
        assertEquals(DebugMenuIntegration.LEVEL_ERROR,
                DebugMenuIntegration.levelToOption(null));
    }

    // ========== 日志级别：显示名 -> Level ==========

    @Test
    void testErrorOptionMapsToErrorLevel() {
        assertEquals(org.apache.logging.log4j.Level.ERROR,
                DebugMenuIntegration.optionToLevel(DebugMenuIntegration.LEVEL_ERROR));
    }

    @Test
    void testWarnOptionMapsToWarnLevel() {
        assertEquals(org.apache.logging.log4j.Level.WARN,
                DebugMenuIntegration.optionToLevel(DebugMenuIntegration.LEVEL_WARN));
    }

    @Test
    void testInfoOptionMapsToInfoLevel() {
        assertEquals(org.apache.logging.log4j.Level.INFO,
                DebugMenuIntegration.optionToLevel(DebugMenuIntegration.LEVEL_INFO));
    }

    @Test
    void testDebugOptionMapsToDebugLevel() {
        assertEquals(org.apache.logging.log4j.Level.DEBUG,
                DebugMenuIntegration.optionToLevel(DebugMenuIntegration.LEVEL_DEBUG));
    }

    @Test
    void testUnknownOptionFallsBackToErrorLevel() {
        assertEquals(org.apache.logging.log4j.Level.ERROR,
                DebugMenuIntegration.optionToLevel("VERBOSE"));
        assertEquals(org.apache.logging.log4j.Level.ERROR,
                DebugMenuIntegration.optionToLevel(null));
    }

    // ========== 日志级别：往返一致性 ==========

    @Test
    void testLevelRoundTrip() {
        for (org.apache.logging.log4j.Level level : new org.apache.logging.log4j.Level[]{
                org.apache.logging.log4j.Level.ERROR,
                org.apache.logging.log4j.Level.WARN,
                org.apache.logging.log4j.Level.INFO,
                org.apache.logging.log4j.Level.DEBUG}) {
            String option = DebugMenuIntegration.levelToOption(level);
            assertEquals(level, DebugMenuIntegration.optionToLevel(option),
                    "级别 " + level + " 往返后应保持一致");
        }
    }

    @Test
    void testLevelOptionsContainAllFour() {
        assertEquals(4, DebugMenuIntegration.LEVEL_OPTIONS.size());
        assertTrue(DebugMenuIntegration.LEVEL_OPTIONS.contains(DebugMenuIntegration.LEVEL_ERROR));
        assertTrue(DebugMenuIntegration.LEVEL_OPTIONS.contains(DebugMenuIntegration.LEVEL_WARN));
        assertTrue(DebugMenuIntegration.LEVEL_OPTIONS.contains(DebugMenuIntegration.LEVEL_INFO));
        assertTrue(DebugMenuIntegration.LEVEL_OPTIONS.contains(DebugMenuIntegration.LEVEL_DEBUG));
    }
}
