package com.example.helloworld;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/** Routes a single key tap through Minecraft's normal keyboard handler. */
public final class ClickKeyCommand {
    private static final Map<String, Integer> KEYS = createKeys();
    private static final ArrayDeque<Integer> PENDING = new ArrayDeque<>();
    private static Integer activeKey;

    private ClickKeyCommand() {}

    private static Map<String, Integer> createKeys() {
        Map<String, Integer> keys = new TreeMap<>();
        for (Field field : GLFW.class.getFields()) {
            String name = field.getName();
            if (!name.startsWith("GLFW_KEY_") || name.equals("GLFW_KEY_UNKNOWN")
                    || name.equals("GLFW_KEY_LAST")) continue;
            try {
                keys.put(name.substring("GLFW_KEY_".length()), field.getInt(null));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read GLFW key constants", e);
            }
        }
        keys.put("CTRL", GLFW.GLFW_KEY_LEFT_CONTROL);
        keys.put("SHIFT", GLFW.GLFW_KEY_LEFT_SHIFT);
        keys.put("ALT", GLFW.GLFW_KEY_LEFT_ALT);
        keys.put("ESC", GLFW.GLFW_KEY_ESCAPE);
        keys.put("RETURN", GLFW.GLFW_KEY_ENTER);
        return Map.copyOf(keys);
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("click")
                        .then(argument("key", StringArgumentType.word())
                                .suggests((context, builder) -> CommandSource.suggestMatching(
                                        KEYS.keySet().stream().sorted(), builder))
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "key")
                                            .toUpperCase(Locale.ROOT);
                                    Integer key = KEYS.get(name);
                                    if (key == null) {
                                        context.getSource().sendError(Text.literal(
                                                "未知按键：" + name + "。例如：K、F5、SPACE、LEFT_CONTROL。"));
                                        return 0;
                                    }
                                    PENDING.addLast(key);
                                    return 1;
                                }))));

        // ChatScreen closes after command execution. Wait until then before dispatching.
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) {
                PENDING.clear();
                return;
            }
            if (client.currentScreen == null && activeKey == null && !PENDING.isEmpty()) {
                activeKey = PENDING.removeFirst();
                dispatch(client, activeKey, GLFW.GLFW_PRESS);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (activeKey != null) {
                try {
                    dispatch(client, activeKey, GLFW.GLFW_RELEASE);
                } finally {
                    activeKey = null;
                }
            }
        });
    }

    private static void dispatch(MinecraftClient client, int key, int action) {
        client.keyboard.onKey(client.getWindow().getHandle(), key, 0, action, 0);
    }
}
