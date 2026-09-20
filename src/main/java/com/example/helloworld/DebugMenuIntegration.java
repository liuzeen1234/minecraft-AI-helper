package com.example.helloworld;

import com.debugmenu.api.DebugMenuApi;
import com.debugmenu.api.DebugOptionEntry;

import java.util.List;

/**
 * 与 debug_menu Mod 的集成（软依赖）。
 *
 * <p>把 AI-helper 的“语言切换”作为一个多状态开关（{@link DebugOptionEntry}）注册进
 * debug_menu 的调试菜单。玩家在 debug_menu 菜单里循环切换时，直接写入
 * {@link ModConfig#setLanguage(String)}，而 {@link I18n#tr} 是实时读取该字段的，
 * 因此界面语言会立即跟随切换。
 *
 * <p><b>软依赖约定：</b>本类会引用 {@code com.debugmenu.api.*}，只应在确认
 * debug-menu 已加载后再调用（见 {@code HelloWorldClientMod} 里的
 * {@code FabricLoader.isModLoaded("debug-menu")} 守卫）。将集成逻辑单独放在此类，
 * 是为了保证未安装 debug-menu 时不会触发这些类的加载（避免 NoClassDefFoundError）。
 *
 * <p><b>日志转发：</b>“聊天框日志显示”开关与“日志最低级别”选项已迁移到 debug_menu
 * 模组自身（见 debug_menu 的 {@code DebugMenuClient} 与 {@code com.debugmenu.log.InGameLogAppender}），
 * 不再由 AI-helper 注册。
 *
 * <p><b>测试日志：</b>原 {@code /aitest} 命令的“生成测试日志”功能已整体内置到 debug_menu
 * 模组自身（见 debug_menu 的 {@code DebugMenuClient} 里的“生成测试日志”按钮），不再由
 * AI-helper 注册。
 */
public final class DebugMenuIntegration {

    private DebugMenuIntegration() {}

    /** debug_menu 中该条目的唯一标识。 */
    static final String OPTION_KEY = "helloworld:language";

    /** 选项显示名——简体中文。 */
    static final String OPTION_ZH = "简体中文";
    /** 选项显示名——English。 */
    static final String OPTION_EN = "English";

    /** 供菜单渲染的选项顺序（即循环顺序）。 */
    static final List<String> OPTIONS = List.of(OPTION_ZH, OPTION_EN);

    /** 「强制多轮工具调用」调试开关的唯一标识。 */
    static final String FORCE_MULTI_TOOL_KEY = "helloworld:force_multi_tool";

    /** 「打印工具返回值到聊天框」调试开关的唯一标识。 */
    static final String PRINT_TOOL_RESULT_KEY = "helloworld:print_tool_result";

    /** 开关显示名——开。 */
    static final String TOGGLE_ON = "ON";
    /** 开关显示名——关。 */
    static final String TOGGLE_OFF = "OFF";

    /** 二态开关的选项顺序。 */
    static final List<String> TOGGLE_OPTIONS = List.of(TOGGLE_OFF, TOGGLE_ON);

    /**
     * 把语言切换注册进 debug_menu。
     * 仅应在 debug-menu 已加载时调用。
     */
    public static void register() {
        // 登记菜单分组标题的显示名（分组键仍是 modId "helloworld"，此处仅改标题显示）。
        DebugMenuApi.setModDisplayName(HelloWorldMod.MOD_ID, "AI Builder");

        DebugMenuApi.registerOption(new DebugOptionEntry(
                HelloWorldMod.MOD_ID,       // modId：与 AI-helper 自身一致，便于在菜单里分组
                OPTION_KEY,
                I18n.tr("screen.language.title"),
                OPTIONS,
                DebugMenuIntegration::currentOptionName,
                DebugMenuIntegration::applyOptionName
        ));
        HelloWorldMod.LOGGER.info("[debug-menu] 已注册语言切换选项: {}", OPTION_KEY);

        // 「强制多轮工具调用」调试开关：开启后系统提示词要求 AI 至少调用 2 次工具。
        DebugMenuApi.registerOption(new DebugOptionEntry(
                HelloWorldMod.MOD_ID,
                FORCE_MULTI_TOOL_KEY,
                I18n.tr("debug.force_multi_tool.title"),
                TOGGLE_OPTIONS,
                DebugMenuIntegration::currentForceMultiToolName,
                DebugMenuIntegration::applyForceMultiToolName
        ));
        HelloWorldMod.LOGGER.info("[debug-menu] 已注册强制多轮工具开关: {}", FORCE_MULTI_TOOL_KEY);

        // 「打印工具返回值到聊天框」调试开关：开启后每轮工具调用的返回值会发到聊天框。
        DebugMenuApi.registerOption(new DebugOptionEntry(
                HelloWorldMod.MOD_ID,
                PRINT_TOOL_RESULT_KEY,
                I18n.tr("debug.print_tool_result.title"),
                TOGGLE_OPTIONS,
                DebugMenuIntegration::currentPrintToolResultName,
                DebugMenuIntegration::applyPrintToolResultName
        ));
        HelloWorldMod.LOGGER.info("[debug-menu] 已注册打印工具返回值开关: {}", PRINT_TOOL_RESULT_KEY);
    }

    /** getter：把当前「打印工具返回值」开关状态映射为菜单显示名。 */
    private static String currentPrintToolResultName() {
        return AICommandExecutor.isPrintToolResultsToChat() ? TOGGLE_ON : TOGGLE_OFF;
    }

    /** setter：把菜单显示名映射回布尔并写入运行时状态。 */
    private static void applyPrintToolResultName(String optionName) {
        AICommandExecutor.setPrintToolResultsToChat(TOGGLE_ON.equals(optionName));
    }

    /** getter：把当前强制多轮工具开关状态映射为菜单显示名。 */
    private static String currentForceMultiToolName() {
        return AICommandExecutor.isForceMultiToolTesting() ? TOGGLE_ON : TOGGLE_OFF;
    }

    /** setter：把菜单显示名映射回布尔并写入运行时状态。 */
    private static void applyForceMultiToolName(String optionName) {
        AICommandExecutor.setForceMultiToolTesting(TOGGLE_ON.equals(optionName));
    }

    /**
     * getter：把当前配置里的语言码映射为菜单显示名。
     */
    private static String currentOptionName() {
        ModConfig config = HelloWorldMod.getConfig();
        String lang = config != null ? config.getLanguage() : null;
        return languageCodeToOption(lang);
    }

    /**
     * setter：把菜单显示名映射回语言码并写入配置。
     */
    private static void applyOptionName(String optionName) {
        ModConfig config = HelloWorldMod.getConfig();
        if (config == null) {
            return;
        }
        config.setLanguage(optionToLanguageCode(optionName));
    }

    // ==================== 纯映射逻辑（可单测，不依赖 MC / debug-menu 类） ====================

    /**
     * 语言码 -> 菜单显示名。
     *
     * <p>以 "en" 开头视为英文，其余（含 null / 未知）一律视为中文，
     * 与 AI-helper 只支持中英双语的现状一致。
     *
     * @param languageCode 如 "zh_cn" / "en_us"，可为 null
     * @return {@link #OPTION_EN} 或 {@link #OPTION_ZH}
     */
    static String languageCodeToOption(String languageCode) {
        if (languageCode != null && languageCode.toLowerCase().startsWith("en")) {
            return OPTION_EN;
        }
        return OPTION_ZH;
    }

    /**
     * 菜单显示名 -> 语言码。
     *
     * @param optionName {@link #OPTION_EN} 或 {@link #OPTION_ZH}；未知值按中文处理
     * @return "en_us" 或 "zh_cn"
     */
    static String optionToLanguageCode(String optionName) {
        return OPTION_EN.equals(optionName) ? "en_us" : "zh_cn";
    }
}
