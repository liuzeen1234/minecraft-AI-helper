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

    private final WebSearchService webSearchService = new WebSearchService(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    );

    private final WebFetchService webFetchService = new WebFetchService(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    );

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
            if (screenshotPath != null && !screenshotPath.isEmpty()) {
                java.nio.file.Path imgPath = java.nio.file.Path.of(screenshotPath);
                // 等待文件写入完成，最多重试 5 次，每次间隔 200ms
                for (int attempt = 0; attempt < 5; attempt++) {
                    try {
                        if (java.nio.file.Files.exists(imgPath) && java.nio.file.Files.size(imgPath) > 0) {
                            byte[] imageBytes = java.nio.file.Files.readAllBytes(imgPath);
                            base64Image = java.util.Base64.getEncoder().encodeToString(imageBytes);
                            break;
                        }
                    } catch (java.nio.file.AccessDeniedException e) {
                        LOGGER.warn("截图文件被占用，重试中... ({})", attempt + 1);
                    } catch (Exception e) {
                        LOGGER.error("读取截图文件失败: {}", screenshotPath, e);
                        break;
                    }
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
            }
            final String finalBase64Image = base64Image;

            server.execute(() -> {
                ServerCommandSource source = player.getCommandSource();

                // 在聊天框回显玩家输入的消息
                String playerName = player.getName().getString();
                source.sendFeedback(() -> Text.literal("§f<" + playerName + "> " + message), false);

                source.sendFeedback(() -> Text.literal("§7[AI] 正在思考..."), false);

                CompletableFuture.runAsync(() -> {
                    try {
                        String response = callKimiApi(message, finalBase64Image);

                        // 检查 AI 是否请求抓取网页
                        String fetchUrl = extractFetchUrl(response);
                        if (fetchUrl != null) {
                            server.execute(() -> {
                                source.sendFeedback(() -> Text.literal("§7[AI] 正在抓取网页: " + fetchUrl), false);
                            });

                            String pageContent = webFetchService.fetch(fetchUrl);
                            if (pageContent != null) {
                                String fetchContext = "以下是网页 " + fetchUrl + " 的内容:\n\n" + pageContent
                                        + "\n\n请根据以上网页内容回答玩家之前的问题或执行操作。不要再使用 [FETCH] 标签。";
                                String finalResponse = callKimiApi(fetchContext, "");
                                server.execute(() -> {
                                    String processed = AICommandExecutor.processResponse(finalResponse, player);
                                    sendLongMessage(source, processed);
                                });
                            } else {
                                String cleanResponse = response.replaceAll("\\[FETCH\\].*?\\[/FETCH\\]", "").trim();
                                if (cleanResponse.isEmpty()) {
                                    cleanResponse = "网页抓取失败了，请检查 URL 是否正确。";
                                }
                                String finalClean = cleanResponse;
                                server.execute(() -> {
                                    String processed = AICommandExecutor.processResponse(finalClean, player);
                                    sendLongMessage(source, processed);
                                });
                            }
                        }
                        // 检查 AI 是否请求联网搜索
                        else {
                            String searchQuery = extractSearchQuery(response);
                            if (searchQuery != null && CONFIG.isWebSearchEnabled()
                                    && CONFIG.getTavilyApiKey() != null && !CONFIG.getTavilyApiKey().isEmpty()) {
                                server.execute(() -> {
                                    source.sendFeedback(() -> Text.literal("§7[AI] 正在联网搜索: " + searchQuery), false);
                                });

                                String searchResults = webSearchService.search(searchQuery, CONFIG.getTavilyApiKey());
                                if (searchResults != null) {
                                    String searchContext = "以下是联网搜索「" + searchQuery + "」的结果:\n\n" + searchResults
                                            + "\n\n请根据以上搜索结果回答玩家之前的问题。不要再使用 [SEARCH] 标签。";
                                    String finalResponse = callKimiApi(searchContext, "");
                                    server.execute(() -> {
                                        String processed = AICommandExecutor.processResponse(finalResponse, player);
                                        sendLongMessage(source, processed);
                                    });
                                } else {
                                    String cleanResponse = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
                                    if (cleanResponse.isEmpty()) {
                                        cleanResponse = "搜索失败了，请稍后再试。";
                                    }
                                    String finalClean = cleanResponse;
                                    server.execute(() -> {
                                        String processed = AICommandExecutor.processResponse(finalClean, player);
                                        sendLongMessage(source, processed);
                                    });
                                }
                            } else {
                                // 不需要搜索也不需要抓取，直接处理回复
                                String cleanResponse = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
                                server.execute(() -> {
                                    String processed = AICommandExecutor.processResponse(cleanResponse, player);
                                    sendLongMessage(source, processed);
                                });
                            }
                        }
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
                        src.sendFeedback(() -> Text.literal("§e[配置] web_search = §f" + (CONFIG.isWebSearchEnabled() ? "开启" : "关闭")), false);
                        src.sendFeedback(() -> Text.literal("§e[配置] tavily_api_key = §f" + maskKey(CONFIG.getTavilyApiKey())), false);
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
                // /lzeconfig web_search <on/off>
                .then(CommandManager.literal("web_search")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            boolean enabled = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
                            CONFIG.setWebSearchEnabled(enabled);
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[配置] 联网搜索已" + (enabled ? "开启" : "关闭")), false);
                            return 1;
                        })
                    )
                )
                // /lzeconfig tavily_api_key <value>
                .then(CommandManager.literal("tavily_api_key")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            CONFIG.setTavilyApiKey(value);
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[配置] tavily_api_key 已更新"), false);
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

        // system prompt 用于告诉 AI 可用的游戏指令
        String systemPrompt = AICommandExecutor.getSystemPrompt()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        String requestBody = """
                {
                    "model": "%s",
                    "max_tokens": 8192,
                    "system": "%s",
                    "messages": %s
                }
                """.formatted(CONFIG.getModel(), systemPrompt, messagesBuilder.toString());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CONFIG.getApiBaseUrl()))
                .header("Content-Type", "application/json")
                .header("x-api-key", CONFIG.getApiKey())
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(180))
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

    /**
     * 从 AI 回复中提取 [FETCH]...[/FETCH] 标签内的 URL。
     */
    private String extractFetchUrl(String response) {
        int start = response.indexOf("[FETCH]");
        int end = response.indexOf("[/FETCH]");
        if (start != -1 && end != -1 && end > start) {
            String url = response.substring(start + 7, end).trim();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
        }
        return null;
    }

    /**
     * 从 AI 回复中提取 [SEARCH]...[/SEARCH] 标签内的搜索关键词。
     */
    private String extractSearchQuery(String response) {
        int start = response.indexOf("[SEARCH]");
        int end = response.indexOf("[/SEARCH]");
        if (start != -1 && end != -1 && end > start) {
            String query = response.substring(start + 8, end).trim();
            return query.isEmpty() ? null : query;
        }
        return null;
    }
}
