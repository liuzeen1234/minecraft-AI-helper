package com.example.helloworld;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class HelloWorldMod implements ModInitializer {

    public static final String MOD_ID = "helloworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Identifier TAKE_SCREENSHOT_PACKET = new Identifier(MOD_ID, "take_screenshot");

    private static final ModConfig CONFIG = new ModConfig();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public void onInitialize() {
        LOGGER.info("Hello World Mod 已加载!");

        // 加载配置
        CONFIG.load();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            player.sendMessage(Text.literal("Hello World! 输入 /lze <问题> 来和 AI 对话"), false);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("lze")
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(this::executeLze)
                )
            );
        });
    }

    private int executeLze(CommandContext<ServerCommandSource> context) {
        String message = StringArgumentType.getString(context, "message");
        ServerCommandSource source = context.getSource();

        // 告诉玩家正在思考
        source.sendFeedback(() -> Text.literal("§7[AI] 正在思考..."), false);

        // 异步调用 API，避免阻塞服务端主线程
        CompletableFuture.runAsync(() -> {
            try {
                String response = callKimiApi(message);
                // 回到服务端主线程发送消息
                source.getServer().execute(() -> {
                    sendLongMessage(source, response);
                });
            } catch (Exception e) {
                LOGGER.error("调用 AI API 失败", e);
                source.getServer().execute(() -> {
                    source.sendFeedback(() -> Text.literal("§c[AI] 请求失败: " + e.getMessage()), false);
                });
            }
        });

        return 1;
    }

    private String callKimiApi(String userMessage) throws Exception {
        // 构建 Anthropic 兼容格式的请求体
        String escapedMessage = userMessage
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        String requestBody = """
                {
                    "model": "%s",
                    "max_tokens": 1024,
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ]
                }
                """.formatted(CONFIG.getModel(), escapedMessage);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CONFIG.getApiBaseUrl()))
                .header("Content-Type", "application/json")
                .header("x-api-key", CONFIG.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        return extractContent(response.body());
    }

    /**
     * 从 Anthropic 格式的响应中提取文本内容。
     * 响应格式: {"content":[{"type":"text","text":"..."}], ...}
     * 用简单字符串解析避免引入 JSON 库依赖。
     */
    private String extractContent(String jsonResponse) {
        // 找 "text":" 后面的内容
        String marker = "\"text\":\"";
        int start = jsonResponse.indexOf(marker);
        if (start == -1) {
            LOGGER.warn("无法解析 API 响应: {}", jsonResponse);
            return "无法解析 AI 响应";
        }
        start += marker.length();

        // 找到对应的结束引号（处理转义）
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < jsonResponse.length(); i++) {
            char c = jsonResponse.charAt(i);
            if (c == '\\' && i + 1 < jsonResponse.length()) {
                char next = jsonResponse.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    default -> sb.append(c);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 将长消息分段发送到聊天框（Minecraft 单条消息有长度限制）
     */
    private void sendLongMessage(ServerCommandSource source, String message) {
        String prefix = "§a[AI] §r";
        String[] lines = message.split("\n");

        for (String line : lines) {
            // 每行最多 200 字符，超过就分段
            while (line.length() > 200) {
                String part = line.substring(0, 200);
                String finalPart = part;
                source.sendFeedback(() -> Text.literal(prefix + finalPart), false);
                line = line.substring(200);
            }
            String finalLine = line;
            if (!finalLine.isEmpty()) {
                source.sendFeedback(() -> Text.literal(prefix + finalLine), false);
            }
        }
    }
}
