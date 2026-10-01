package com.example.helloworld;

import java.util.Locale;
import java.util.Properties;

/** User approval policy; command suggestions remain manually executed from chat. */
public enum ToolApprovalMode {
    NEVER, AS_NEEDED, ALWAYS;

    public ToolApprovalMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public String configValue() { return name().toLowerCase(Locale.ROOT); }

    public boolean requiresApproval(String tool) {
        if ("execute_command".equals(tool)) return false;
        boolean readOnly = switch (tool) {
            case "SEARCH", "FETCH", "QUERY_REGION", "KNOWLEDGE", "KNOWLEDGE_TREE", "KNOWLEDGE_FILE", "find_player" -> true;
            default -> false;
        };
        return this == ALWAYS || (this == AS_NEEDED && !readOnly);
    }

    public static ToolApprovalMode fromProperties(Properties props) {
        String value = props.getProperty("tool_approval_mode");
        if (value != null) {
            try { return valueOf(value.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { return AS_NEEDED; }
        }
        String legacy = props.getProperty("confirm_before_execute_enabled");
        return legacy == null ? AS_NEEDED : Boolean.parseBoolean(legacy) ? ALWAYS : NEVER;
    }
}
