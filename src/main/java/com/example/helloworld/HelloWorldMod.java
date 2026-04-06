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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HelloWorldMod implements ModInitializer {

    public static final String MOD_ID = "helloworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // 服务端 -> 客户端：通知截图
    public static final Identifier TAKE_SCREENSHOT_PACKET = new Identifier(MOD_ID, "take_screenshot");
    // 客户端 -> 服务端：回传截图数据
    public static final Identifier SCREENSHOT_RESPONSE_PACKET = new Identifier(MOD_ID, "screenshot_response");

    private static final ModConfig CONFIG = new ModConfig();

    public static ModConfig getConfig() {
        return CONFIG;
    }

    // 对话历史记录（多轮上下文）
    private final List<String> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 20; // 最多保留 20 条消息（10轮对话）

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public void onInitialize() {
        LOGGER.info("Hello World Mod 已加载!");
        CONFIG.load();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            player.sendMessage(Text.literal("Hello World! 输入 /lze <问题> 来和 AI 对话"), false);
        });

        // 注册接收客户端截图完成通知的处理器
        ServerPlayNetworking.registerGlobalReceiver(SCREENSHOT_RESPONSE_PACKET, (server, player, handler, buf, responseSender) -> {
            String message = buf.readString();
            String screenshotPath = buf.readString();

            // 从文件读取图片并转 base64
            String base64Image = "";
            try {
                byte[] imageBytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(screenshotPath));
                base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
            } catch (Exception e) {
                LOGGER.error("读取截图文件失败: {}", screenshotPath, e);
            }
            final String finalBase64Image = base64Image;

            server.execute(() -> {
                ServerCommandSource source = player.getCommandSource();
                source.sendFeedback(() -> Text.literal("§7[AI] 正在思考..."), false);

                CompletableFuture.runAsync(() -> {
                    try {
                        String response = callKimiApi(message, finalBase64Image);
                        server.execute(() -> sendLongMessage(source, response));
                    } catch (Exception e) {
                        LOGGER.error("调用 AI API 失败", e);
                        server.execute(() -> {
                            source.sendFeedback(() -> Text.literal("§c[AI] 请求失败: " + e.getMessage()), false);
                        });
                    }
                });
            });
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("lze")
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(this::executeLze)
                )
            );

            // /lzeconfig 查看和修改 AI 配置
            dispatcher.register(CommandManager.literal("lzeconfig")
                // /lzeconfig show - 查看当前配置
                .then(CommandManager.literal("show")
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        src.sendFeedback(() -> Text.literal("§e[配置] api_base_url = §f" + CONFIG.getApiBaseUrl()), false);
                        src.sendFeedback(() -> Text.literal("§e[配置] api_key = §f" + maskKey(CONFIG.getApiKey())), false);
                        src.sendFeedback(() -> Text.literal("§e[配置] model = §f" + CONFIG.getModel()), false);
                        return 1;
                    })
                )
                // /lzeconfig api_base_url <value>
                .then(CommandManager.literal("api_base_url")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            CONFIG.setApiBaseUrl(value);
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[配置] api_base_url 已更新为: §f" + value), false);
                            return 1;
                        })
                    )
                )
                // /lzeconfig api_key <value>
                .then(CommandManager.literal("api_key")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            CONFIG.setApiKey(value);
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[配置] api_key 已更新"), false);
                            return 1;
                        })
                    )
                )
                // /lzeconfig model <value>
                .then(CommandManager.literal("model")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            CONFIG.setModel(value);
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[配置] model 已更新为: §f" + value), false);
                            return 1;
                        })
                    )
                )
                // /lzeconfig reload - 重新加载配置文件
                .then(CommandManager.literal("reload")
                    .executes(ctx -> {
                        CONFIG.load();
                        ctx.getSource().sendFeedback(() -> Text.literal("§a[配置] 配置已重新加载"), false);
                        return 1;
                    })
                )
            );

            // /lzenew - 清空对话历史，开启新话题
            dispatcher.register(CommandManager.literal("lzenew")
                .executes(ctx -> {
                    conversationHistory.clear();
                    ctx.getSource().sendFeedback(() -> Text.literal("§a[AI] 对话历史已清空，开始新话题"), false);
                    return 1;
                })
            );
        });
    }

    private int executeLze(CommandContext<ServerCommandSource> context) {
        String message = StringArgumentType.getString(context, "message");
        ServerCommandSource source = context.getSource();

        // 通知客户端截图，客户端截完图会把图片数据和消息一起发回来
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(message);
            ServerPlayNetworking.send(player, TAKE_SCREENSHOT_PACKET, buf);
        }

        return 1;
    }

    private String callKimiApi(String userMessage, String base64Image) throws Exception {
        String escapedMessage = userMessage
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        // 构建当前用户消息
        String currentUserMessage;
        if (base64Image != null && !base64Image.isEmpty()) {
            currentUserMessage = """
                        {
                            "role": "user",
                            "content": [
                                {
                                    "type": "image",
                                    "source": {
                                        "type": "base64",
                                        "media_type": "image/png",
                                        "data": "%s"
                                    }
                                },
                                {
                                    "type": "text",
                                    "text": "%s"
                                }
                            ]
                        }""".formatted(base64Image, escapedMessage);
        } else {
            currentUserMessage = """
                        {
                            "role": "user",
                            "content": "%s"
                        }""".formatted(escapedMessage);
        }

        // 构建 messages 数组
        StringBuilder messagesBuilder = new StringBuilder();
        messagesBuilder.append("[");

        if (CONFIG.isContextEnabled() && !conversationHistory.isEmpty()) {
            // 加入历史消息
            for (int i = 0; i < conversationHistory.size(); i++) {
                messagesBuilder.append(conversationHistory.get(i));
                messagesBuilder.append(",");
            }
        }

        // 加入当前消息
        messagesBuilder.append(currentUserMessage);
        messagesBuilder.append("]");

        String requestBody = """
                {
                    "model": "%s",
                    "max_tokens": 1024,
                    "messages": %s
                }
                """.formatted(CONFIG.getModel(), messagesBuilder.toString());

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

        String content = extractContent(response.body());

        // 保存到对话历史
        if (CONFIG.isContextEnabled()) {
            conversationHistory.add(currentUserMessage);

            String escapedContent = content
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
            conversationHistory.add("""
                        {
                            "role": "assistant",
                            "content": "%s"
                        }""".formatted(escapedContent));

            // 限制历史大小
            while (conversationHistory.size() > MAX_HISTORY_SIZE) {
                conversationHistory.remove(0);
                conversationHistory.remove(0); // 成对移除
            }
        }

        return content;
    }

    private String extractContent(String jsonResponse) {
        String marker = "\"text\":\"";
        int start = jsonResponse.indexOf(marker);
        if (start == -1) {
            LOGGER.warn("无法解析 API 响应: {}", jsonResponse);
            return "无法解析 AI 响应";
        }
        start += marker.length();

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

    private void sendLongMessage(ServerCommandSource source, String message) {
        String prefix = "§a[AI] §r";
        String[] lines = message.split("\n");

        for (String line : lines) {
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

    private static String maskKey(String key) {
        if (key == null || key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
