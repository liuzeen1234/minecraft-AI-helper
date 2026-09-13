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

    // 说明：日志级别 ↔ 显示名的映射逻辑已随“日志转发到聊天框”功能迁移到 debug-menu 模组
    //（见 debug_menu 的 DebugMenuClient / com.debugmenu.log.InGameLogAppender），
    // 对应的测试也随之移除，不再由 AI-helper 维护。
}
