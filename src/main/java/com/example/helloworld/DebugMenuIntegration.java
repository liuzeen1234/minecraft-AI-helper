package com.example.helloworld;

import com.debugmenu.api.DebugMenuApi;
import com.debugmenu.api.DebugOptionEntry;

import java.util.List;

/**
 * 与 debug_menu Mod 的集成（软依赖）。
 *
 * <p>把 AI-helper 的调试开关（多状态/二态 {@link DebugOptionEntry}）注册进
 * debug_menu 的调试菜单。
 *
 * <p><b>语言切换：</b>显示语言现在完全跟随 Minecraft 当前游戏语言（见
 * {@link I18n#isEnglish()}），不再支持手动切换，因此本类不再注册语言切换选项。
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
     * 把 AI-helper 的调试开关注册进 debug_menu。
     * 仅应在 debug-menu 已加载时调用。
     */
    public static void register() {
        // 登记菜单分组标题的显示名（分组键仍是 modId "helloworld"，此处仅改标题显示）。
        DebugMenuApi.setModDisplayName(HelloWorldMod.MOD_ID, "AI Builder");

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
}
