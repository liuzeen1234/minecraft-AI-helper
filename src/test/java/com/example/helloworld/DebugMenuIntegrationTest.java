package com.example.helloworld;

import com.debugmenu.api.DebugMenuApi;
import com.debugmenu.api.DebugToggleEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class DebugMenuIntegrationTest {

    @Test
    void registersAllTogglesWithoutDuplicatingOrResettingState() {
        boolean force = AICommandExecutor.isForceMultiToolTesting();
        boolean print = AICommandExecutor.isPrintToolResultsToChat();
        boolean image = AICommandExecutor.isImageDiagEnabled();
        try {
            DebugMenuIntegration.register();
            assertEquals("AI Builder", DebugMenuApi.getModDisplayName(HelloWorldMod.MOD_ID));
            verifyToggle(DebugMenuIntegration.FORCE_MULTI_TOOL_KEY,
                    AICommandExecutor::isForceMultiToolTesting,
                    AICommandExecutor::setForceMultiToolTesting);
            verifyToggle(DebugMenuIntegration.PRINT_TOOL_RESULT_KEY,
                    AICommandExecutor::isPrintToolResultsToChat,
                    AICommandExecutor::setPrintToolResultsToChat);
            verifyToggle(DebugMenuIntegration.IMAGE_DIAG_KEY,
                    AICommandExecutor::isImageDiagEnabled,
                    AICommandExecutor::setImageDiagEnabled);
            AICommandExecutor.setImageDiagEnabled(true);
            DebugMenuIntegration.register();
            assertTrue(AICommandExecutor.isImageDiagEnabled());
            assertEquals(3, DebugMenuApi.getEntries().stream()
                    .filter(entry -> HelloWorldMod.MOD_ID.equals(entry.getModId())).count());
            assertTrue(DebugMenuApi.getOptionEntries().stream()
                    .noneMatch(entry -> HelloWorldMod.MOD_ID.equals(entry.getModId())));
        } finally {
            AICommandExecutor.setForceMultiToolTesting(force);
            AICommandExecutor.setPrintToolResultsToChat(print);
            AICommandExecutor.setImageDiagEnabled(image);
        }
    }

    @Test
    void titleUsesCurrentTranslationWhenMenuReadsIt() {
        DebugMenuIntegration.register();
        DebugToggleEntry entry = DebugMenuApi.getEntry(DebugMenuIntegration.IMAGE_DIAG_KEY);
        try (var translations = mockStatic(I18n.class)) {
            translations.when(() -> I18n.tr("debug.image_diag.title"))
                    .thenReturn("图片传输诊断日志(测试)");
            assertEquals("图片传输诊断日志(测试)", entry.getDisplayName());
            translations.when(() -> I18n.tr("debug.image_diag.title"))
                    .thenReturn("Image Transfer Diagnostics (Test)");
            assertEquals("Image Transfer Diagnostics (Test)", entry.getDisplayName());
        }
    }

    private void verifyToggle(String key, java.util.function.Supplier<Boolean> getter,
                              java.util.function.Consumer<Boolean> setter) {
        DebugToggleEntry entry = DebugMenuApi.getEntry(key);
        assertNotNull(entry, key);
        assertEquals(HelloWorldMod.MOD_ID, entry.getModId());
        assertTrue(entry.isVisible());
        setter.accept(false);
        assertFalse(entry.isEnabled());
        entry.setEnabled(true);
        assertTrue(getter.get());
        entry.toggle();
        assertFalse(getter.get());
        setter.accept(true);
        assertTrue(entry.isEnabled());
    }
}
