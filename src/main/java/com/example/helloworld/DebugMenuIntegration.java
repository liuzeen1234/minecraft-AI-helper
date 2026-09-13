package com.example.helloworld;

import com.debugmenu.api.DebugMenuApi;
import com.debugmenu.api.DebugOptionEntry;
import com.debugmenu.api.DebugToggleEntry;
import org.apache.logging.log4j.Level;

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
 */
public final class DebugMenuIntegration {

    private DebugMenuIntegration() {}

    /** debug_menu 中该条目的唯一标识。 */
    static final String OPTION_KEY = "helloworld:language";

    /** debug_menu 中“聊天框日志显示”开关的唯一标识。 */
    static final String LOG_TOGGLE_KEY = "helloworld:log_display";

    /** debug_menu 中“日志最低级别”选项的唯一标识。 */
    static final String LOG_LEVEL_KEY = "helloworld:log_level";

    /** 选项显示名——简体中文。 */
    static final String OPTION_ZH = "简体中文";
    /** 选项显示名——English。 */
    static final String OPTION_EN = "English";

    /** 供菜单渲染的选项顺序（即循环顺序）。 */
    static final List<String> OPTIONS = List.of(OPTION_ZH, OPTION_EN);

    // 日志级别在菜单中的显示名（也是循环顺序：由粗到细）。
    static final String LEVEL_ERROR = "ERROR";
    static final String LEVEL_WARN = "WARN";
    static final String LEVEL_INFO = "INFO";
    static final String LEVEL_DEBUG = "DEBUG";

    /** 供菜单渲染的日志级别选项顺序。 */
    static final List<String> LEVEL_OPTIONS = List.of(LEVEL_ERROR, LEVEL_WARN, LEVEL_INFO, LEVEL_DEBUG);

    /**
     * 把语言切换、日志显示开关、日志级别选项注册进 debug_menu。
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

        // 聊天框日志显示开关（对应 /ailog on|off）
        DebugMenuApi.register(new DebugToggleEntry(
                HelloWorldMod.MOD_ID,
                LOG_TOGGLE_KEY,
                I18n.tr("debugmenu.log.toggle"),
                InGameLogAppender::isEnabled,
                InGameLogAppender::setEnabled
        ));
        HelloWorldMod.LOGGER.info("[debug-menu] 已注册日志显示开关: {}", LOG_TOGGLE_KEY);

        // 日志最低级别选项（对应 /ailog level <error|warn|info|debug>）
        // 作为“聊天框日志显示”开关的二级选项：仅当该开关开启时才在菜单里显示。
        DebugMenuApi.registerOption(new DebugOptionEntry(
                HelloWorldMod.MOD_ID,
                LOG_LEVEL_KEY,
                I18n.tr("debugmenu.log.level"),
                LEVEL_OPTIONS,
                DebugMenuIntegration::currentLogLevelName,
                DebugMenuIntegration::applyLogLevelName,
                DebugMenuApi.visibleWhenEnabled(LOG_TOGGLE_KEY)
        ));
        HelloWorldMod.LOGGER.info("[debug-menu] 已注册日志级别二级选项: {}（依赖 {}）", LOG_LEVEL_KEY, LOG_TOGGLE_KEY);
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

    // ==================== 日志级别桥接 ====================

    /**
     * getter：把 {@link InGameLogAppender} 当前最低级别映射为菜单显示名。
     */
    private static String currentLogLevelName() {
        return levelToOption(InGameLogAppender.getMinLevel());
    }

    /**
     * setter：把菜单显示名映射回 Log4j2 级别并应用到 {@link InGameLogAppender}。
     */
    private static void applyLogLevelName(String optionName) {
        InGameLogAppender.setMinLevel(optionToLevel(optionName));
    }

    // ==================== 纯映射逻辑（可单测，不依赖 MC / debug-menu 类） ====================

    /**
     * Log4j2 级别 -> 菜单显示名。
     *
     * <p>只区分 ERROR / WARN / INFO / DEBUG 四档，与 {@code /ailog level} 支持的档位一致。
     * FATAL 归入 ERROR，TRACE 及其余更细/未知级别归入 DEBUG（与该选项的最细档对齐）。
     *
     * @param level Log4j2 级别，可为 null（按最粗的 ERROR 处理）
     * @return {@link #LEVEL_ERROR} / {@link #LEVEL_WARN} / {@link #LEVEL_INFO} / {@link #LEVEL_DEBUG}
     */
    static String levelToOption(Level level) {
        if (level == null || level == Level.ERROR || level == Level.FATAL) {
            return LEVEL_ERROR;
        }
        if (level == Level.WARN) {
            return LEVEL_WARN;
        }
        if (level == Level.INFO) {
            return LEVEL_INFO;
        }
        return LEVEL_DEBUG;
    }

    /**
     * 菜单显示名 -> Log4j2 级别。
     *
     * @param optionName {@link #LEVEL_WARN} / {@link #LEVEL_INFO} / {@link #LEVEL_DEBUG}；
     *                   其余（含 null / 未知）一律按 ERROR 处理
     * @return 对应的 Log4j2 {@link Level}
     */
    static Level optionToLevel(String optionName) {
        if (LEVEL_WARN.equals(optionName)) {
            return Level.WARN;
        }
        if (LEVEL_INFO.equals(optionName)) {
            return Level.INFO;
        }
        if (LEVEL_DEBUG.equals(optionName)) {
            return Level.DEBUG;
        }
        return Level.ERROR;
    }
}
