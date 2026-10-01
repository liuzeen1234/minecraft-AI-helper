package com.example.helloworld;

import com.debugmenu.api.DebugMenuApi;
import com.debugmenu.api.DebugToggleEntry;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * AI-helper 与 debug-menu 的软依赖集成。
 *
 * <p>仅在客户端确认 debug-menu 已加载后调用，避免未安装时加载其 API 类。
 * 三个开关仅修改内存中的调试状态；日志显示、日志级别和测试日志由 debug-menu 自身管理。
 */
public final class DebugMenuIntegration {

    private DebugMenuIntegration() {}

    static final String FORCE_MULTI_TOOL_KEY = "helloworld:force_multi_tool";
    static final String PRINT_TOOL_RESULT_KEY = "helloworld:print_tool_result";
    static final String IMAGE_DIAG_KEY = "helloworld:image_diag";

    /** 注册调试开关；重复调用不会添加重复菜单项或重置开关状态。 */
    public static synchronized void register() {
        DebugMenuApi.setModDisplayName(HelloWorldMod.MOD_ID, "AI Builder");
        registerToggle(FORCE_MULTI_TOOL_KEY, "debug.force_multi_tool.title",
                AICommandExecutor::isForceMultiToolTesting,
                AICommandExecutor::setForceMultiToolTesting);
        registerToggle(PRINT_TOOL_RESULT_KEY, "debug.print_tool_result.title",
                AICommandExecutor::isPrintToolResultsToChat,
                AICommandExecutor::setPrintToolResultsToChat);
        registerToggle(IMAGE_DIAG_KEY, "debug.image_diag.title",
                AICommandExecutor::isImageDiagEnabled,
                AICommandExecutor::setImageDiagEnabled);
    }

    private static void registerToggle(String key, String titleKey,
                                       Supplier<Boolean> getter, Consumer<Boolean> setter) {
        if (DebugMenuApi.getEntry(key) != null) {
            return;
        }
        DebugMenuApi.register(new DebugToggleEntry(
                HelloWorldMod.MOD_ID, key, titleKey, getter, setter) {
            @Override
            public String getDisplayName() {
                // 在菜单读取标题时翻译，避免注册时固定语言。
                return I18n.tr(titleKey);
            }
        });
        HelloWorldMod.LOGGER.info("[debug-menu] 已注册调试开关: {}", key);
    }
}
