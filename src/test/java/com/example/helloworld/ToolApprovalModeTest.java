package com.example.helloworld;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class ToolApprovalModeTest {
    @TempDir Path tempDir;

    @Test
    void approvalMatrixCoversEveryTool() {
        String[] readOnly = {"SEARCH", "FETCH", "QUERY_REGION", "KNOWLEDGE", "KNOWLEDGE_TREE", "KNOWLEDGE_FILE", "find_player"};
        String[] changes = {"BLUEPRINT", "CAMERA_SHOT", "place_block", "fill_blocks", "clear_area", "give_item", "set_time", "set_weather", "summon"};
        for (String tool : readOnly) {
            assertFalse(ToolApprovalMode.NEVER.requiresApproval(tool), tool);
            assertFalse(ToolApprovalMode.AS_NEEDED.requiresApproval(tool), tool);
            assertTrue(ToolApprovalMode.ALWAYS.requiresApproval(tool), tool);
        }
        for (String tool : changes) {
            assertFalse(ToolApprovalMode.NEVER.requiresApproval(tool), tool);
            assertTrue(ToolApprovalMode.AS_NEEDED.requiresApproval(tool), tool);
            assertTrue(ToolApprovalMode.ALWAYS.requiresApproval(tool), tool);
        }
        for (ToolApprovalMode mode : ToolApprovalMode.values()) {
            assertFalse(mode.requiresApproval("execute_command"), "Command suggestions require manual Enter");
        }
        assertTrue(ToolApprovalMode.AS_NEEDED.requiresApproval("future_tool"));
    }

    @Test
    void cycleAndMigration() {
        assertEquals(ToolApprovalMode.AS_NEEDED, ToolApprovalMode.NEVER.next());
        assertEquals(ToolApprovalMode.ALWAYS, ToolApprovalMode.AS_NEEDED.next());
        assertEquals(ToolApprovalMode.NEVER, ToolApprovalMode.ALWAYS.next());
        Properties props = new Properties();
        assertEquals(ToolApprovalMode.AS_NEEDED, ToolApprovalMode.fromProperties(props));
        props.setProperty("confirm_before_execute_enabled", "true");
        assertEquals(ToolApprovalMode.ALWAYS, ToolApprovalMode.fromProperties(props));
        props.setProperty("confirm_before_execute_enabled", "false");
        assertEquals(ToolApprovalMode.NEVER, ToolApprovalMode.fromProperties(props));
        props.setProperty("tool_approval_mode", " always ");
        assertEquals(ToolApprovalMode.ALWAYS, ToolApprovalMode.fromProperties(props));
        props.setProperty("tool_approval_mode", "invalid");
        assertEquals(ToolApprovalMode.AS_NEEDED, ToolApprovalMode.fromProperties(props));
    }

    @Test
    void configPersistsModeAndReloadsWithoutChangingRealGameConfig() throws Exception {
        Path file = tempDir.resolve("test.properties");
        Files.writeString(file, "confirm_before_execute_enabled=true\n");
        try (MockedStatic<ModPaths> paths = mockStatic(ModPaths.class)) {
            paths.when(ModPaths::getConfigFile).thenReturn(file);
            ModConfig config = new ModConfig();
            config.load();
            assertEquals(ToolApprovalMode.ALWAYS, config.getToolApprovalMode());
            for (ToolApprovalMode mode : ToolApprovalMode.values()) {
                config.setToolApprovalMode(mode);
                Properties stored = new Properties();
                try (InputStream in = Files.newInputStream(file)) { stored.load(in); }
                assertEquals(mode.configValue(), stored.getProperty("tool_approval_mode"));
                assertFalse(stored.containsKey("confirm_before_execute_enabled"));
                ModConfig reloaded = new ModConfig();
                reloaded.load();
                assertEquals(mode, reloaded.getToolApprovalMode());
            }
        }
    }
}
