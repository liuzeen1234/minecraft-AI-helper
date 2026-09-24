package com.example.helloworld;

/**
 * 说明：AI-helper 的显示语言现在完全跟随 Minecraft 当前游戏语言（见 {@link I18n#isEnglish()}），
 * 不再支持通过 debug_menu 手动切换。原本用于测试语言码 ↔ 菜单显示名映射逻辑
 * （{@code languageCodeToOption}/{@code optionToLanguageCode}/{@code OPTION_ZH}/
 * {@code OPTION_EN}/{@code OPTIONS}）的测试已随该功能一并移除。
 *
 * <p>{@link DebugMenuIntegration} 里仍保留的“强制多轮工具调用”“打印工具返回值到聊天框”
 * 两个调试开关与语言无关，未在此文件中覆盖（当前无对应单测）。
 *
 * <p>说明：日志级别 ↔ 显示名的映射逻辑已随“日志转发到聊天框”功能迁移到 debug-menu 模组
 * （见 debug_menu 的 DebugMenuClient / com.debugmenu.log.InGameLogAppender），对应测试
 * 也随之移除，不再由 AI-helper 维护。
 *
 * <p>说明：“生成测试日志”按钮（原 /aitest）已整体内置到 debug_menu 模组（见其 DebugMenuClient），
 * 不再由 AI-helper 注册，对应门控逻辑与测试也随之移除。
 */
class DebugMenuIntegrationTest {
}
