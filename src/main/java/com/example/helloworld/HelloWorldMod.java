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

import com.example.helloworld.blueprint.BlueprintBuilder;
import com.example.helloworld.blueprint.BlueprintData;
import com.example.helloworld.blueprint.BlueprintRegistry;
import com.example.helloworld.nbt.NbtCommands;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class HelloWorldMod implements ModInitializer {

    public static final String MOD_ID = "helloworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // 服务端 -> 客户端：通知截图
    public static final Identifier TAKE_SCREENSHOT_PACKET = new Identifier(MOD_ID, "take_screenshot");
    // 客户端 -> 服务端：回传截图数据
    public static final Identifier SCREENSHOT_RESPONSE_PACKET = new Identifier(MOD_ID, "screenshot_response");
    // 客户端 -> 服务端：请求放置 NBT 结构
    public static final Identifier PLACE_NBT_PACKET = new Identifier(MOD_ID, "place_nbt");
    // 客户端 -> 服务端：请求放置 TXT 结构设计图
    public static final Identifier PLACE_TXT_PACKET = new Identifier(MOD_ID, "place_txt");
    // 客户端 -> 服务端：请求导出选区为 NBT（含 BlockEntity 数据）
    public static final Identifier EXPORT_NBT_PACKET = new Identifier(MOD_ID, "export_nbt");
    // 服务端 -> 客户端：导出完成通知
    public static final Identifier EXPORT_NBT_RESULT_PACKET = new Identifier(MOD_ID, "export_nbt_result");
    // 客户端 -> 服务端：聊天界面发送消息
    public static final Identifier CHAT_SCREEN_MESSAGE_PACKET = new Identifier(MOD_ID, "chat_screen_msg");
    // 服务端 -> 客户端：聊天界面回复
    public static final Identifier CHAT_SCREEN_RESPONSE_PACKET = new Identifier(MOD_ID, "chat_screen_resp");
    // 客户端 -> 服务端：取消正在进行的 AI 请求
    public static final Identifier CHAT_CANCEL_PACKET = new Identifier(MOD_ID, "chat_cancel");
    // 服务端 -> 客户端：聊天界面流式增量回复
    public static final Identifier CHAT_SCREEN_STREAM_PACKET = new Identifier(MOD_ID, "chat_screen_stream");
    // 客户端 -> 服务端：聊天界面发送消息（带截图）
    public static final Identifier CHAT_SCREEN_MSG_WITH_IMG_PACKET = new Identifier(MOD_ID, "chat_screen_msg_img");

    private static final ModConfig CONFIG = new ModConfig();

    public static ModConfig getConfig() {
        return CONFIG;
    }

    // 对话历史记录（多轮上下文）
    private final List<String> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 20; // 最多保留 20 条消息（10轮对话）

    // 当前正在执行的 AI 请求（用于取消）
    private volatile CompletableFuture<?> pendingAiTask = null;
    private volatile boolean cancelRequested = false;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final WebSearchService webSearchService = new WebSearchService(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    );

    private final WebFetchService webFetchService = new WebFetchService(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build()
    );

    private final BlueprintRegistry blueprintRegistry = new BlueprintRegistry();

    @Override
    public void onInitialize() {
        LOGGER.info("AI Builder 已加载!");
        CONFIG.load();
        blueprintRegistry.loadAll();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            player.sendMessage(Text.literal(I18n.get("AI Builder 已加载! 输入 /ai <问题> 来和 AI 对话", "AI Builder loaded! Type /ai <question> to chat with AI")), false);

            // 检查该存档是否是第一次加载本 mod，如果是则提示用户查看手册
            server.execute(() -> {
                try {
                    java.nio.file.Path worldDir = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).getParent();
                    java.nio.file.Path markerFile = worldDir.resolve("ai-helper-initialized.marker");
                    if (!java.nio.file.Files.exists(markerFile)) {
                        // 第一次加载，发送用户手册提示
                        player.sendMessage(Text.literal(""), false);
                        player.sendMessage(Text.literal(I18n.get(
                                "§e§l[AI Builder] §r§6欢迎首次使用 AI Builder 模组！",
                                "§e§l[AI Builder] §r§6Welcome to AI Builder mod for the first time!"
                        )), false);
                        player.sendMessage(Text.literal(I18n.get(
                                "§e建议您阅读用户手册以了解所有功能。",
                                "§eWe recommend reading the user manual to learn all features."
                        )), false);
                        player.sendMessage(Text.literal(I18n.get(
                                "§b打开方式: §f按 §aK 键§f 打开设置界面，点击 §a\"用户手册\"§f 按钮即可在游戏内查看。",
                                "§bHow to open: §fPress §aK key§f to open settings, then click §a\"User Manual\"§f button to view in-game."
                        )), false);
                        player.sendMessage(Text.literal(I18n.get(
                                "§b快速上手: §f按 §aK 键§f 打开设置界面 | 输入 §a/ai <问题>§f 与 AI 对话 | 输入 §a/nbt§f 管理结构文件",
                                "§bQuick start: §fPress §aK key§f to open settings | Type §a/ai <question>§f to chat with AI | Type §a/nbt§f to manage structures"
                        )), false);
                        player.sendMessage(Text.literal(""), false);

                        // 创建标记文件，下次不再提示
                        java.nio.file.Files.createDirectories(markerFile.getParent());
                        java.nio.file.Files.writeString(markerFile, "AI Builder mod initialized. Delete this file to see the welcome message again.");
                    }
                } catch (Exception e) {
                    LOGGER.warn("检查首次加载标记失败", e);
                }
            });
        });

        // 注册接收客户端 NBT 放置请求的处理器
        ServerPlayNetworking.registerGlobalReceiver(PLACE_NBT_PACKET, (server, player, handler, buf, responseSender) -> {
            String filename = buf.readString();
            server.execute(() -> {
                try {
                    java.io.File file = com.example.helloworld.nbt.NbtCommands.resolveNbtFile(filename);
                    if (file == null || !file.exists()) {
                        player.sendMessage(Text.literal(I18n.get("§c[NBT] 文件不存在: ", "§c[NBT] File not found: ") + filename), false);
                        return;
                    }
                    com.example.helloworld.nbt.NbtStructureParser.StructureData data =
                            com.example.helloworld.nbt.NbtStructureParser.parse(file);
                    net.minecraft.util.math.BlockPos origin = player.getBlockPos();
                    int count = com.example.helloworld.nbt.NbtStructurePlacer.place(
                            data, player.getServerWorld(), origin);
                    player.sendMessage(Text.literal(
                            "§a[NBT] " + file.getName() + " 放置完成! 共 " + count + " 个方块 (原点: "
                                    + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")"
                    ), false);
                } catch (Exception e) {
                    LOGGER.error("放置 NBT 结构失败", e);
                    player.sendMessage(Text.literal(I18n.get("§c[NBT] 放置失败: ", "§c[NBT] Place failed: ") + e.getMessage()), false);
                }
            });
        });

        // 注册接收客户端 TXT 结构放置请求的处理器
        ServerPlayNetworking.registerGlobalReceiver(PLACE_TXT_PACKET, (server, player, handler, buf, responseSender) -> {
            String relativePath = buf.readString();
            server.execute(() -> {
                try {
                    // 解析 ai-helper/txts/ 目录下的文件路径
                    java.nio.file.Path txtsDir = com.example.helloworld.ModPaths.getTxtsDir();
                    if (!java.nio.file.Files.isDirectory(txtsDir)) {
                        java.nio.file.Files.createDirectories(txtsDir);
                    }
                    java.io.File file = txtsDir.resolve(relativePath).toFile();
                    if (!file.exists()) {
                        player.sendMessage(Text.literal(I18n.get("§c[TXT] 文件不存在: ", "§c[TXT] File not found: ") + relativePath), false);
                        return;
                    }
                    String content = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                    com.example.helloworld.blueprint.BlueprintData data =
                            com.example.helloworld.blueprint.BlueprintParser.parse(content);
                    net.minecraft.util.math.BlockPos origin = player.getBlockPos();
                    int count = com.example.helloworld.blueprint.BlueprintBuilder.build(
                            data, player, player.getServerWorld());
                    player.sendMessage(Text.literal(
                            "§a[TXT] " + data.getName() + " 放置完成! 共 " + count + " 个方块 (原点: "
                                    + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")"
                    ), false);
                } catch (Exception e) {
                    LOGGER.error("放置 TXT 结构失败", e);
                    player.sendMessage(Text.literal(I18n.get("§c[TXT] 放置失败: ", "§c[TXT] Place failed: ") + e.getMessage()), false);
                }
            });
        });

        // 注册接收客户端导出 NBT 请求的处理器（服务端执行，可完整读取 BlockEntity）
        ServerPlayNetworking.registerGlobalReceiver(EXPORT_NBT_PACKET, (server, player, handler, buf, responseSender) -> {
            int x1 = buf.readInt(), y1 = buf.readInt(), z1 = buf.readInt();
            int x2 = buf.readInt(), y2 = buf.readInt(), z2 = buf.readInt();
            String fileName = buf.readString();
            String subPath = buf.isReadable() ? buf.readString() : "";
            server.execute(() -> {
                try {
                    net.minecraft.server.world.ServerWorld world = player.getServerWorld();
                    net.minecraft.util.math.BlockPos pos1 = new net.minecraft.util.math.BlockPos(x1, y1, z1);
                    net.minecraft.util.math.BlockPos pos2 = new net.minecraft.util.math.BlockPos(x2, y2, z2);
                    com.example.helloworld.selection.ServerSelectionExporter.exportNbt(world, pos1, pos2, fileName, subPath);
                    String displayPath = subPath.isEmpty() ? fileName + ".nbt" : subPath + "/" + fileName + ".nbt";
                    // 通知客户端导出完成
                    PacketByteBuf resultBuf = PacketByteBufs.create();
                    resultBuf.writeString("§a[选区] NBT 已导出（含方块实体数据）: " + displayPath);
                    ServerPlayNetworking.send(player, EXPORT_NBT_RESULT_PACKET, resultBuf);
                } catch (Exception e) {
                    LOGGER.error("服务端导出 NBT 失败", e);
                    PacketByteBuf resultBuf = PacketByteBufs.create();
                    resultBuf.writeString("§c[选区] NBT 导出失败: " + e.getMessage());
                    ServerPlayNetworking.send(player, EXPORT_NBT_RESULT_PACKET, resultBuf);
                }
            });
        });

        // 注册接收聊天界面消息的处理器
        ServerPlayNetworking.registerGlobalReceiver(CHAT_SCREEN_MESSAGE_PACKET, (server, player, handler, buf, responseSender) -> {
            String message = buf.readString();
            // 读取引用的文件列表
            int fileCount = buf.isReadable() ? buf.readInt() : 0;
            List<String> referencedFiles = new ArrayList<>();
            for (int i = 0; i < fileCount && buf.isReadable(); i++) {
                referencedFiles.add(buf.readString());
            }

            server.execute(() -> {
                if ("__CLEAR_HISTORY__".equals(message)) {
                    conversationHistory.clear();
                    player.sendMessage(Text.literal(I18n.get("§a[AI] 对话历史已清空", "§a[AI] Chat history cleared")), false);
                    return;
                }

                // 读取引用文件内容（服务端可以直接访问 txts 目录）
                String referenceContent = "";
                if (!referencedFiles.isEmpty()) {
                    referenceContent = loadReferencedFiles(referencedFiles);
                }

                // 构建完整消息
                final String fullMessage;
                if (!referenceContent.isEmpty()) {
                    fullMessage = message + "\n\n--- 以下是用户引用的结构文件内容 ---\n" + referenceContent;
                } else {
                    fullMessage = message;
                }

                // 异步调用 AI API
                final String finalRefContent = referenceContent;
                cancelRequested = false;
                pendingAiTask = CompletableFuture.runAsync(() -> {
                    try {
                        String response;
                        if (CONFIG.isStreamOutputEnabled()) {
                            // 流式模式：实时输出到聊天框
                            server.execute(() -> player.sendMessage(Text.literal(I18n.get("§7[AI] 开始回复...", "§7[AI] Generating...")), false));
                            response = callKimiApiStreaming(fullMessage, "", player, server);
                        } else {
                            response = callKimiApi(fullMessage, "");
                        }

                        // 检查是否已被取消
                        if (cancelRequested) {
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString("§7[思考已终止]");
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                            return;
                        }

                        // 检查是否需要抓取网页
                        String fetchUrl = extractFetchUrl(response);
                        if (fetchUrl != null) {
                            // 通知聊天界面正在抓取网页
                            server.execute(() -> {
                                PacketByteBuf streamBuf = PacketByteBufs.create();
                                streamBuf.writeString("\n\n§7" + I18n.get("正在抓取网页...", "Fetching page..."));
                                ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                                player.sendMessage(Text.literal(I18n.get("§7[AI] 正在抓取网页: ", "§7[AI] Fetching page: ") + fetchUrl), false);
                            });
                            String pageContent = webFetchService.fetch(fetchUrl);
                            if (cancelRequested) {
                                server.execute(() -> {
                                    PacketByteBuf respBuf = PacketByteBufs.create();
                                    respBuf.writeString("§7[思考已终止]");
                                    ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                                });
                                return;
                            }
                            if (pageContent != null) {
                                String fetchContext = "以下是网页 " + fetchUrl + " 的内容:\n\n" + pageContent
                                        + "\n\n请根据以上网页内容回答玩家之前的问题或执行操作。不要再使用 [FETCH] 标签。";
                                if (CONFIG.isStreamOutputEnabled()) {
                                    server.execute(() -> {
                                        PacketByteBuf streamBuf = PacketByteBufs.create();
                                        streamBuf.writeString("\n§7" + I18n.get("网页抓取完成，正在生成回复...", "Page fetched, generating reply...") + "\n\n");
                                        ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                                    });
                                    response = callKimiApiStreaming(fetchContext, "", player, server);
                                } else {
                                    response = callKimiApi(fetchContext, "");
                                }
                            } else {
                                response = response.replaceAll("\\[FETCH\\].*?\\[/FETCH\\]", "").trim();
                                if (response.isEmpty()) response = I18n.get("网页抓取失败了，请检查 URL 是否正确。", "Failed to fetch the page. Please check the URL.");
                            }
                        } else {
                            // 检查是否需要联网搜索
                            String searchQuery = extractSearchQuery(response);
                            if (searchQuery != null && CONFIG.isWebSearchEnabled()
                                    && CONFIG.getTavilyApiKey() != null && !CONFIG.getTavilyApiKey().isEmpty()) {
                                // 通知聊天界面正在搜索
                                server.execute(() -> {
                                    PacketByteBuf streamBuf = PacketByteBufs.create();
                                    streamBuf.writeString("\n\n§7" + I18n.get("正在联网搜索...", "Searching the web..."));
                                    ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                                    player.sendMessage(Text.literal(I18n.get("§7[AI] 正在联网搜索: ", "§7[AI] Searching: ") + searchQuery), false);
                                });
                                String searchResults = webSearchService.search(searchQuery, CONFIG.getTavilyApiKey());
                                if (cancelRequested) {
                                    server.execute(() -> {
                                        PacketByteBuf respBuf = PacketByteBufs.create();
                                        respBuf.writeString("§7[思考已终止]");
                                        ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                                    });
                                    return;
                                }
                                if (searchResults != null) {
                                    String searchContext = "以下是联网搜索「" + searchQuery + "」的结果:\n\n" + searchResults
                                            + "\n\n请根据以上搜索结果回答玩家之前的问题。不要再使用 [SEARCH] 标签。";
                                    if (CONFIG.isStreamOutputEnabled()) {
                                        server.execute(() -> {
                                            PacketByteBuf streamBuf = PacketByteBufs.create();
                                            streamBuf.writeString("\n§7" + I18n.get("搜索完成，正在生成回复...", "Search complete, generating reply...") + "\n\n");
                                            ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                                        });
                                        response = callKimiApiStreaming(searchContext, "", player, server);
                                    } else {
                                        response = callKimiApi(searchContext, "");
                                    }
                                } else {
                                    response = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
                                    if (response.isEmpty()) response = I18n.get("搜索失败了，请稍后再试。", "Search failed. Please try again later.");
                                }
                            } else {
                                response = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
                            }
                        }

                        // 再次检查取消
                        if (cancelRequested) {
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString("§7[思考已终止]");
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                            return;
                        }

                        // 清理响应中残留的标签
                        response = response.replaceAll("\\[FETCH\\].*?\\[/FETCH\\]", "").trim();
                        response = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();

                        // 执行 AI 指令
                        String processed = AICommandExecutor.processResponse(response, player);

                        // 发送回复到客户端聊天界面
                        String finalResponse = processed;
                        server.execute(() -> {
                            PacketByteBuf respBuf = PacketByteBufs.create();
                            respBuf.writeString(finalResponse);
                            ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                        });
                    } catch (Exception e) {
                        if (cancelRequested) {
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString("§7[思考已终止]");
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                            return;
                        }
                        LOGGER.error("聊天界面 AI 请求失败", e);
                        server.execute(() -> {
                            PacketByteBuf respBuf = PacketByteBufs.create();
                            respBuf.writeString("§c请求失败: " + e.getMessage());
                            ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                        });
                    } finally {
                        pendingAiTask = null;
                    }
                });
            });
        });

        // 注册取消 AI 请求的处理器
        ServerPlayNetworking.registerGlobalReceiver(CHAT_CANCEL_PACKET, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                cancelRequested = true;
                CompletableFuture<?> task = pendingAiTask;
                if (task != null) {
                    task.cancel(true);
                }
                LOGGER.info("玩家 {} 取消了 AI 请求", player.getName().getString());
            });
        });

        // 注册接收聊天界面带截图消息的处理器
        ServerPlayNetworking.registerGlobalReceiver(CHAT_SCREEN_MSG_WITH_IMG_PACKET, (server, player, handler, buf, responseSender) -> {
            String message = buf.readString();
            int fileCount = buf.isReadable() ? buf.readInt() : 0;
            List<String> referencedFiles = new ArrayList<>();
            for (int i = 0; i < fileCount && buf.isReadable(); i++) {
                referencedFiles.add(buf.readString());
            }
            String screenshotPath = buf.isReadable() ? buf.readString() : "";

            // 从文件路径读取图片并转 base64（与 SCREENSHOT_RESPONSE_PACKET 处理器一致）
            String base64Image = "";
            if (screenshotPath != null && !screenshotPath.isEmpty()) {
                java.nio.file.Path imgPath = java.nio.file.Path.of(screenshotPath);
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
                if ("__CLEAR_HISTORY__".equals(message)) {
                    conversationHistory.clear();
                    player.sendMessage(Text.literal(I18n.get("§a[AI] 对话历史已清空", "§a[AI] Chat history cleared")), false);
                    return;
                }

                // 读取引用文件内容
                String referenceContent = "";
                if (!referencedFiles.isEmpty()) {
                    referenceContent = loadReferencedFiles(referencedFiles);
                }

                final String fullMessage;
                if (!referenceContent.isEmpty()) {
                    fullMessage = message + "\n\n--- 以下是用户引用的结构文件内容 ---\n" + referenceContent;
                } else {
                    fullMessage = message;
                }

                final String finalBase64 = finalBase64Image;
                cancelRequested = false;
                pendingAiTask = CompletableFuture.runAsync(() -> {
                    try {
                        String response;
                        if (CONFIG.isStreamOutputEnabled()) {
                            server.execute(() -> player.sendMessage(Text.literal(I18n.get("§7[AI] 开始回复...", "§7[AI] Generating...")), false));
                            response = callKimiApiStreaming(fullMessage, finalBase64, player, server);
                        } else {
                            response = callKimiApi(fullMessage, finalBase64);
                        }

                        if (cancelRequested) {
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString("§7[思考已终止]");
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                            return;
                        }

                        // 检查是否需要抓取网页
                        String fetchUrl = extractFetchUrl(response);
                        if (fetchUrl != null) {
                            // 通知聊天界面正在抓取网页（替换流式内容中的 FETCH 标签显示）
                            server.execute(() -> {
                                PacketByteBuf streamBuf = PacketByteBufs.create();
                                streamBuf.writeString("\n\n§7" + I18n.get("正在抓取网页...", "Fetching page..."));
                                ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                                player.sendMessage(Text.literal(I18n.get("§7[AI] 正在抓取网页: ", "§7[AI] Fetching page: ") + fetchUrl), false);
                            });
                            String pageContent = webFetchService.fetch(fetchUrl);
                            if (cancelRequested) {
                                server.execute(() -> {
                                    PacketByteBuf respBuf = PacketByteBufs.create();
                                    respBuf.writeString("§7[思考已终止]");
                                    ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                                });
                                return;
                            }
                            if (pageContent != null) {
                                String fetchContext = "以下是网页 " + fetchUrl + " 的内容:\n\n" + pageContent
                                        + "\n\n请根据以上网页内容回答玩家之前的问题或执行操作。不要再使用 [FETCH] 标签。";
                                if (CONFIG.isStreamOutputEnabled()) {
                                    // 流式模式：用流式输出让用户实时看到回复
                                    server.execute(() -> {
                                        PacketByteBuf streamBuf = PacketByteBufs.create();
                                        streamBuf.writeString("\n§7" + I18n.get("网页抓取完成，正在生成回复...", "Page fetched, generating reply...") + "\n\n");
                                        ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                                    });
                                    response = callKimiApiStreaming(fetchContext, "", player, server);
                                } else {
                                    response = callKimiApi(fetchContext, "");
                                }
                            } else {
                                response = response.replaceAll("\\[FETCH\\].*?\\[/FETCH\\]", "").trim();
                                if (response.isEmpty()) response = I18n.get("网页抓取失败了，请检查 URL 是否正确。", "Failed to fetch the page. Please check the URL.");
                            }
                        } else {
                            String searchQuery = extractSearchQuery(response);
                            if (searchQuery != null && CONFIG.isWebSearchEnabled()
                                    && CONFIG.getTavilyApiKey() != null && !CONFIG.getTavilyApiKey().isEmpty()) {
                                // 通知聊天界面正在搜索
                                server.execute(() -> {
                                    PacketByteBuf streamBuf = PacketByteBufs.create();
                                    streamBuf.writeString("\n\n§7" + I18n.get("正在联网搜索...", "Searching the web..."));
                                    ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                                    player.sendMessage(Text.literal(I18n.get("§7[AI] 正在联网搜索: ", "§7[AI] Searching: ") + searchQuery), false);
                                });
                                String searchResults = webSearchService.search(searchQuery, CONFIG.getTavilyApiKey());
                                if (cancelRequested) {
                                    server.execute(() -> {
                                        PacketByteBuf respBuf = PacketByteBufs.create();
                                        respBuf.writeString("§7[思考已终止]");
                                        ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                                    });
                                    return;
                                }
                                if (searchResults != null) {
                                    String searchContext = "以下是联网搜索「" + searchQuery + "」的结果:\n\n" + searchResults
                                            + "\n\n请根据以上搜索结果回答玩家之前的问题。不要再使用 [SEARCH] 标签。";
                                    if (CONFIG.isStreamOutputEnabled()) {
                                        // 流式模式：用流式输出让用户实时看到回复
                                        server.execute(() -> {
                                            PacketByteBuf streamBuf = PacketByteBufs.create();
                                            streamBuf.writeString("\n§7" + I18n.get("搜索完成，正在生成回复...", "Search complete, generating reply...") + "\n\n");
                                            ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                                        });
                                        response = callKimiApiStreaming(searchContext, "", player, server);
                                    } else {
                                        response = callKimiApi(searchContext, "");
                                    }
                                } else {
                                    response = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
                                    if (response.isEmpty()) response = I18n.get("搜索失败了，请稍后再试。", "Search failed. Please try again later.");
                                }
                            } else {
                                response = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
                            }
                        }

                        if (cancelRequested) {
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString("§7[思考已终止]");
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                            return;
                        }

                        // 清理响应中残留的标签
                        response = response.replaceAll("\\[FETCH\\].*?\\[/FETCH\\]", "").trim();
                        response = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();

                        String processed = AICommandExecutor.processResponse(response, player);
                        String finalResponse = processed;
                        server.execute(() -> {
                            PacketByteBuf respBuf = PacketByteBufs.create();
                            respBuf.writeString(finalResponse);
                            ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                        });
                    } catch (Exception e) {
                        if (cancelRequested) {
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString("§7[思考已终止]");
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                            return;
                        }
                        LOGGER.error("聊天界面 AI 请求失败", e);
                        server.execute(() -> {
                            PacketByteBuf respBuf = PacketByteBufs.create();
                            respBuf.writeString("§c请求失败: " + e.getMessage());
                            ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                        });
                    } finally {
                        pendingAiTask = null;
                    }
                });
            });
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

                source.sendFeedback(() -> Text.literal(I18n.get("§7[AI] 正在思考...", "§7[AI] Thinking...")), false);

                cancelRequested = false;
                pendingAiTask = CompletableFuture.runAsync(() -> {
                    try {
                        String response;
                        boolean wasStreamed = false;
                        if (CONFIG.isStreamOutputEnabled()) {
                            response = callKimiApiStreaming(message, finalBase64Image, player, server);
                            wasStreamed = true;
                        } else {
                            response = callKimiApi(message, finalBase64Image);
                        }

                        // 如果已被取消，直接返回不做后续处理
                        if (cancelRequested) return;
                        String fetchUrl = extractFetchUrl(response);
                        if (fetchUrl != null) {
                            server.execute(() -> {
                                source.sendFeedback(() -> Text.literal(I18n.get("§7[AI] 正在抓取网页: ", "§7[AI] Fetching page: ") + fetchUrl), false);
                            });

                            String pageContent = webFetchService.fetch(fetchUrl);
                            if (pageContent != null) {
                                String fetchContext = "以下是网页 " + fetchUrl + " 的内容:\n\n" + pageContent
                                        + "\n\n请根据以上网页内容回答玩家之前的问题或执行操作。不要再使用 [FETCH] 标签。";
                                String finalResponse;
                                if (CONFIG.isStreamOutputEnabled()) {
                                    finalResponse = callKimiApiStreaming(fetchContext, "", player, server);
                                } else {
                                    finalResponse = callKimiApi(fetchContext, "");
                                }
                                server.execute(() -> {
                                    String processed = AICommandExecutor.processResponse(finalResponse, player);
                                    if (!CONFIG.isStreamOutputEnabled()) {
                                        sendLongMessage(source, processed);
                                    }
                                });
                            } else {
                                String cleanResponse = response.replaceAll("\\[FETCH\\].*?\\[/FETCH\\]", "").trim();
                                if (cleanResponse.isEmpty()) {
                                    cleanResponse = "网页抓取失败了，请检查 URL 是否正确。";
                                }
                                String finalClean = cleanResponse;
                                server.execute(() -> {
                                    String processed = AICommandExecutor.processResponse(finalClean, player);
                                    if (!CONFIG.isStreamOutputEnabled()) {
                                        sendLongMessage(source, processed);
                                    } else {
                                        // 流式模式下只显示错误信息
                                        source.sendFeedback(() -> Text.literal("§c[AI] " + finalClean), false);
                                    }
                                });
                            }
                        }
                        // 检查 AI 是否请求联网搜索
                        else {
                            String searchQuery = extractSearchQuery(response);
                            if (searchQuery != null && CONFIG.isWebSearchEnabled()
                                    && CONFIG.getTavilyApiKey() != null && !CONFIG.getTavilyApiKey().isEmpty()) {
                                server.execute(() -> {
                                    source.sendFeedback(() -> Text.literal(I18n.get("§7[AI] 正在联网搜索: ", "§7[AI] Searching: ") + searchQuery), false);
                                });

                                String searchResults = webSearchService.search(searchQuery, CONFIG.getTavilyApiKey());
                                if (searchResults != null) {
                                    String searchContext = "以下是联网搜索「" + searchQuery + "」的结果:\n\n" + searchResults
                                            + "\n\n请根据以上搜索结果回答玩家之前的问题。不要再使用 [SEARCH] 标签。";
                                    String finalResponse;
                                    if (CONFIG.isStreamOutputEnabled()) {
                                        finalResponse = callKimiApiStreaming(searchContext, "", player, server);
                                    } else {
                                        finalResponse = callKimiApi(searchContext, "");
                                    }
                                    server.execute(() -> {
                                        String processed = AICommandExecutor.processResponse(finalResponse, player);
                                        if (!CONFIG.isStreamOutputEnabled()) {
                                            sendLongMessage(source, processed);
                                        }
                                    });
                                } else {
                                    String cleanResponse = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
                                    if (cleanResponse.isEmpty()) {
                                        cleanResponse = "搜索失败了，请稍后再试。";
                                    }
                                    String finalClean = cleanResponse;
                                    server.execute(() -> {
                                        String processed = AICommandExecutor.processResponse(finalClean, player);
                                        if (!CONFIG.isStreamOutputEnabled()) {
                                            sendLongMessage(source, processed);
                                        } else {
                                            source.sendFeedback(() -> Text.literal("§c[AI] " + finalClean), false);
                                        }
                                    });
                                }
                            } else {
                                // 不需要搜索也不需要抓取，直接处理回复
                                String cleanResponse = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
                                final boolean streamedAlready = wasStreamed;
                                server.execute(() -> {
                                    String processed = AICommandExecutor.processResponse(cleanResponse, player);
                                    if (!streamedAlready) {
                                        sendLongMessage(source, processed);
                                    }
                                });
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("调用 AI API 失败", e);
                        LOGGER.error("[AI诊断] 异常链: {}", getExceptionChain(e));
                        server.execute(() -> {
                            source.sendFeedback(() -> Text.literal(I18n.get("§c[AI] 请求失败: ", "§c[AI] Request failed: ") + e.getMessage()), false);
                        });
                    } finally {
                        pendingAiTask = null;
                    }
                });
            });
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // 注册 NBT 解析命令
            NbtCommands.register(dispatcher);

            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.literal("build")
                    .then(CommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(this::executeBuild)
                    )
                )
                .then(CommandManager.literal("blueprints")
                    .executes(this::listBlueprints)
                )
                .then(CommandManager.literal("reload_blueprints")
                    .executes(this::reloadBlueprints)
                )
                .then(CommandManager.literal("test_stairs")
                    .executes(this::executeTestStairs)
                )
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(this::executeAi)
                )
            );

            // /aiconfig 查看和修改 AI 配置
            dispatcher.register(CommandManager.literal("aiconfig")
                // /aiconfig show - 查看当前配置
                .then(CommandManager.literal("show")
                    .executes(ctx -> {
                        ServerCommandSource src = ctx.getSource();
                        src.sendFeedback(() -> Text.literal("§e[" + I18n.get("配置", "Config") + "] api_base_url = §f" + CONFIG.getApiBaseUrl()), false);
                        src.sendFeedback(() -> Text.literal("§e[" + I18n.get("配置", "Config") + "] api_key = §f" + maskKey(CONFIG.getApiKey())), false);
                        src.sendFeedback(() -> Text.literal("§e[" + I18n.get("配置", "Config") + "] model = §f" + CONFIG.getModel()), false);
                        src.sendFeedback(() -> Text.literal("§e[" + I18n.get("配置", "Config") + "] web_search = §f" + (CONFIG.isWebSearchEnabled() ? I18n.get("开启", "ON") : I18n.get("关闭", "OFF"))), false);
                        src.sendFeedback(() -> Text.literal("§e[" + I18n.get("配置", "Config") + "] tavily_api_key = §f" + maskKey(CONFIG.getTavilyApiKey())), false);
                        return 1;
                    })
                )
                // /aiconfig api_base_url <value>
                .then(CommandManager.literal("api_base_url")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            CONFIG.setApiBaseUrl(value);
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[配置] api_base_url 已更新为: §f", "§a[Config] api_base_url updated to: §f") + value), false);
                            return 1;
                        })
                    )
                )
                // /aiconfig api_key <value>
                .then(CommandManager.literal("api_key")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            CONFIG.setApiKey(value);
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[配置] api_key 已更新", "§a[Config] api_key updated")), false);
                            return 1;
                        })
                    )
                )
                // /aiconfig model <value>
                .then(CommandManager.literal("model")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            CONFIG.setModel(value);
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[配置] model 已更新为: §f", "§a[Config] model updated to: §f") + value), false);
                            return 1;
                        })
                    )
                )
                // /aiconfig web_search <on/off>
                .then(CommandManager.literal("web_search")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            boolean enabled = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
                            CONFIG.setWebSearchEnabled(enabled);
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[配置] 联网搜索已" + (enabled ? "开启" : "关闭"), "§a[Config] Web search " + (enabled ? "enabled" : "disabled"))), false);
                            return 1;
                        })
                    )
                )
                // /aiconfig tavily_api_key <value>
                .then(CommandManager.literal("tavily_api_key")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            CONFIG.setTavilyApiKey(value);
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[配置] tavily_api_key 已更新", "§a[Config] tavily_api_key updated")), false);
                            return 1;
                        })
                    )
                )
                // /aiconfig reload - 重新加载配置文件
                .then(CommandManager.literal("reload")
                    .executes(ctx -> {
                        CONFIG.load();
                        ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[配置] 配置已重新加载", "§a[Config] Configuration reloaded")), false);
                        return 1;
                    })
                )
            );

            // /ainew - 清空对话历史，开启新话题
            dispatcher.register(CommandManager.literal("ainew")
                .executes(ctx -> {
                    conversationHistory.clear();
                    ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[AI] 对话历史已清空，开始新话题", "§a[AI] Chat history cleared, new topic started")), false);
                    return 1;
                })
            );

            // /aipos - 显示当前坐标
            dispatcher.register(CommandManager.literal("aipos")
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                    if (p == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§c[坐标] 只有玩家才能使用此命令", "§c[Pos] Only players can use this command")), false);
                        return 0;
                    }
                    double x = Math.round(p.getX() * 100.0) / 100.0;
                    double y = Math.round(p.getY() * 100.0) / 100.0;
                    double z = Math.round(p.getZ() * 100.0) / 100.0;
                    String dim = p.getWorld().getRegistryKey().getValue().toString();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§e[坐标] §fX: §a" + x + " §fY: §a" + y + " §fZ: §a" + z + " §f| 维度: §b" + dim
                    ), false);
                    return 1;
                })
            );

            // /ailog - 控制日志转发到聊天框
            dispatcher.register(CommandManager.literal("ailog")
                // /ailog - 切换开关
                .executes(ctx -> {
                    InGameLogAppender.toggleEnabled();
                    boolean on = InGameLogAppender.isEnabled();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        "§e[日志] 聊天框日志显示已" + (on ? "§a开启" : "§c关闭")
                    ), false);
                    return 1;
                })
                // /ailog on
                .then(CommandManager.literal("on")
                    .executes(ctx -> {
                        InGameLogAppender.setEnabled(true);
                        ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[日志] 聊天框日志显示已开启", "§a[Log] Chat log display enabled")), false);
                        return 1;
                    })
                )
                // /ailog off
                .then(CommandManager.literal("off")
                    .executes(ctx -> {
                        InGameLogAppender.setEnabled(false);
                        ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§c[日志] 聊天框日志显示已关闭", "§c[Log] Chat log display disabled")), false);
                        return 1;
                    })
                )
                // /ailog level <error|warn|info|debug>
                .then(CommandManager.literal("level")
                    .then(CommandManager.literal("error")
                        .executes(ctx -> {
                            InGameLogAppender.setMinLevel(org.apache.logging.log4j.Level.ERROR);
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[日志] 最低显示级别: §cERROR", "§a[Log] Min level: §cERROR")), false);
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("warn")
                        .executes(ctx -> {
                            InGameLogAppender.setMinLevel(org.apache.logging.log4j.Level.WARN);
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[日志] 最低显示级别: §eWARN", "§a[Log] Min level: §eWARN")), false);
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("info")
                        .executes(ctx -> {
                            InGameLogAppender.setMinLevel(org.apache.logging.log4j.Level.INFO);
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[日志] 最低显示级别: §fINFO", "§a[Log] Min level: §fINFO")), false);
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("debug")
                        .executes(ctx -> {
                            InGameLogAppender.setMinLevel(org.apache.logging.log4j.Level.DEBUG);
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[日志] 最低显示级别: §7DEBUG", "§a[Log] Min level: §7DEBUG")), false);
                            return 1;
                        })
                    )
                )
            );

            // /aitest - 故意触发测试日志，验证聊天框日志显示
            dispatcher.register(CommandManager.literal("aitest")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§e[测试] 正在生成测试日志...", "§e[Test] Generating test logs...")), false);
                    LOGGER.warn("这是一条测试 WARN 日志 - 来自 /aitest 命令");
                    LOGGER.error("这是一条测试 ERROR 日志 - 来自 /aitest 命令");
                    LOGGER.error("模拟异常: NullPointerException at FakeClass.fakeMethod(FakeClass.java:42)");
                    LOGGER.info("这是一条测试 INFO 日志（默认级别下不会显示在聊天框）");
                    ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§a[测试] 已生成 2 条 WARN/ERROR + 1 条 INFO 日志，检查聊天框!", "§a[Test] Generated 2 WARN/ERROR + 1 INFO logs, check chat!")), false);
                    return 1;
                })
            );

            // /aistop - 终止当前 AI 思考/生成
            dispatcher.register(CommandManager.literal("aistop")
                .executes(ctx -> {
                    cancelRequested = true;
                    CompletableFuture<?> task = pendingAiTask;
                    if (task != null) {
                        task.cancel(true);
                        ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§e[AI] 已终止 AI 回复", "§e[AI] AI response stopped")), false);
                    } else {
                        ctx.getSource().sendFeedback(() -> Text.literal(I18n.get("§7[AI] 当前没有正在进行的 AI 请求", "§7[AI] No AI request in progress")), false);
                    }
                    return 1;
                })
            );
        });
    }

    private int executeBuild(CommandContext<ServerCommandSource> context) {
        String name = StringArgumentType.getString(context, "name");
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendFeedback(() -> Text.literal(I18n.get("§c只有玩家可以执行此命令", "§cOnly players can execute this command")), false);
            return 0;
        }

        BlueprintData blueprint = blueprintRegistry.find(name);
        if (blueprint == null) {
            source.sendFeedback(() -> Text.literal(I18n.get("§c未找到蓝图: ", "§cBlueprint not found: ") + name), false);
            source.sendFeedback(() -> Text.literal(I18n.get("§e使用 /ai blueprints 查看可用蓝图", "§eUse /ai blueprints to see available blueprints")), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal(I18n.get("§e[建造] 开始建造: ", "§e[Build] Building: ") + blueprint.getName() + " ..."), false);

        CompletableFuture.runAsync(() -> {
            try {
                int count = BlueprintBuilder.build(blueprint, player, player.getServerWorld());
                player.getServer().execute(() -> {
                    source.sendFeedback(() -> Text.literal(I18n.get("§a[建造] " + blueprint.getName() + " 建造完成! 共放置 " + count + " 个方块", "§a[Build] " + blueprint.getName() + " complete! Placed " + count + " blocks")), false);
                });
            } catch (Exception e) {
                LOGGER.error("建造蓝图失败", e);
                player.getServer().execute(() -> {
                    source.sendFeedback(() -> Text.literal(I18n.get("§c[建造] 建造失败: ", "§c[Build] Build failed: ") + e.getMessage()), false);
                });
            }
        });

        return 1;
    }

    private int listBlueprints(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (blueprintRegistry.size() == 0) {
            source.sendFeedback(() -> Text.literal(I18n.get("§e没有已加载的蓝图。将 .txt 蓝图文件放入 txts/ 目录", "§eNo blueprints loaded. Place .txt blueprint files in txts/ directory")), false);
        } else {
            source.sendFeedback(() -> Text.literal(I18n.get("§e已加载 " + blueprintRegistry.size() + " 个蓝图:", "§eLoaded " + blueprintRegistry.size() + " blueprint(s):")), false);
            for (String name : blueprintRegistry.getNames()) {
                source.sendFeedback(() -> Text.literal("§a  - " + name), false);
            }
        }
        return 1;
    }

    private int reloadBlueprints(CommandContext<ServerCommandSource> context) {
        blueprintRegistry.loadAll();
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> Text.literal(I18n.get("§a[蓝图] 已重新加载 " + blueprintRegistry.size() + " 个蓝图", "§a[Blueprint] Reloaded " + blueprintRegistry.size() + " blueprint(s)")), false);
        return 1;
    }

    /**
     * 测试命令：在玩家前方放置4个楼梯，分别标注 facing 方向。
     * 用于确认 facing 属性的实际视觉效果。
     */
    private int executeTestStairs(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        net.minecraft.server.world.ServerWorld world = player.getServerWorld();
        net.minecraft.util.math.BlockPos base = player.getBlockPos().north(3);

        String[] facings = {"north", "south", "east", "west"};
        for (int i = 0; i < 4; i++) {
            net.minecraft.util.math.BlockPos pos = base.east(i * 2);
            net.minecraft.block.BlockState state = net.minecraft.block.Blocks.OAK_STAIRS.getDefaultState();
            // 设置 facing
            net.minecraft.state.property.Property<?> facingProp = null;
            for (var prop : state.getProperties()) {
                if (prop.getName().equals("facing")) {
                    facingProp = prop;
                    break;
                }
            }
            if (facingProp != null) {
                @SuppressWarnings({"unchecked", "rawtypes"})
                net.minecraft.block.BlockState finalState = state.with(
                    (net.minecraft.state.property.Property) facingProp,
                    (Comparable) facingProp.parse(facings[i]).get()
                );
                world.setBlockState(pos, finalState);
            }
            // 在楼梯上方放一个告示牌...算了，直接在聊天里告诉玩家
            String facing = facings[i];
            int idx = i;
            context.getSource().sendFeedback(() -> Text.literal(I18n.get("§e楼梯 " + (idx + 1) + ": facing=" + facing + " (位置偏东 " + (idx * 2) + ")", "§eStair " + (idx + 1) + ": facing=" + facing + " (east offset " + (idx * 2) + ")")), false);
        }
        context.getSource().sendFeedback(() -> Text.literal(I18n.get("§a已在北方3格处放置4个楼梯，从左到右: north, south, east, west", "§aPlaced 4 stairs 3 blocks north, left to right: north, south, east, west")), false);
        return 1;
    }

    private int executeAi(CommandContext<ServerCommandSource> context) {
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
                    "max_tokens": 16384,
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

    /**
     * 流式调用 AI API，逐段将内容发送到玩家聊天框。
     * 返回完整的响应文本（用于后续指令解析和聊天界面显示）。
     */
    private String callKimiApiStreaming(String userMessage, String base64Image, ServerPlayerEntity player,
                                        net.minecraft.server.MinecraftServer server) throws Exception {
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
            for (int i = 0; i < conversationHistory.size(); i++) {
                messagesBuilder.append(conversationHistory.get(i));
                messagesBuilder.append(",");
            }
        }

        messagesBuilder.append(currentUserMessage);
        messagesBuilder.append("]");

        String systemPrompt = AICommandExecutor.getSystemPrompt()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        // 启用 stream
        String requestBody = """
                {
                    "model": "%s",
                    "max_tokens": 16384,
                    "stream": true,
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

        // 诊断日志：请求信息
        LOGGER.info("[AI诊断] 发送请求: URL={}, 请求体大小={}字节, HttpClient版本={}", 
                CONFIG.getApiBaseUrl(), requestBody.length(), httpClient.version());
        LOGGER.info("[AI诊断] Java版本={}, OS={}", 
                System.getProperty("java.version"), System.getProperty("os.name"));

        long startTime = System.currentTimeMillis();
        HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofLines());
        long connectTime = System.currentTimeMillis() - startTime;

        // 诊断日志：响应信息
        LOGGER.info("[AI诊断] 收到响应: 状态码={}, HTTP版本={}, 连接耗时={}ms", 
                response.statusCode(), response.version(), connectTime);
        LOGGER.info("[AI诊断] 响应头: {}", response.headers().map());

        if (response.statusCode() != 200) {
            // 读取错误信息
            StringBuilder errorBody = new StringBuilder();
            response.body().forEach(errorBody::append);
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + errorBody);
        }

        // 逐行解析 SSE 流
        StringBuilder fullContent = new StringBuilder();
        StringBuilder lineBuffer = new StringBuilder(); // 按句缓冲
        final int FLUSH_THRESHOLD = 60; // 攒够约60字符发一条
        boolean firstDelta = true;
        int totalLinesRead = 0;
        int dataLinesRead = 0;

        java.util.Iterator<String> lines = response.body().iterator();
        try {
        while (lines.hasNext()) {
            if (cancelRequested) break;

            String line = lines.next();
            totalLinesRead++;

            // SSE 格式：data: {...} 或 data:{...}
            String data;
            if (line.startsWith("data: ")) {
                data = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                data = line.substring(5).trim();
            } else {
                continue;
            }

            dataLinesRead++;

            if (data.equals("[DONE]") || data.isEmpty()) {
                LOGGER.info("[AI诊断] 流正常结束: 收到{}, 总行数={}, 数据行数={}", 
                        data.equals("[DONE]") ? "[DONE]" : "空行", totalLinesRead, dataLinesRead);
                break;
            }

            // 记录第一条 SSE 数据用于调试
            if (firstDelta) {
                LOGGER.info("[流式] 首条SSE数据: {}", data.length() > 200 ? data.substring(0, 200) + "..." : data);
                firstDelta = false;
            }

            // 解析 SSE 事件中的 delta text
            String deltaText = extractStreamDelta(data);
            if (deltaText == null || deltaText.isEmpty()) continue;

            fullContent.append(deltaText);
            lineBuffer.append(deltaText);

            // 检查是否应该刷新到聊天框：遇到换行或缓冲超过阈值
            boolean shouldFlush = lineBuffer.toString().contains("\n")
                    || lineBuffer.length() >= FLUSH_THRESHOLD;

            if (shouldFlush) {
                String toSend = lineBuffer.toString();
                lineBuffer.setLength(0);

                // 发送流式增量到聊天界面
                final String streamDelta = toSend;
                LOGGER.debug("[流式服务端] 发送增量到聊天界面, 长度={}", streamDelta.length());
                server.execute(() -> {
                    PacketByteBuf streamBuf = PacketByteBufs.create();
                    streamBuf.writeString(streamDelta);
                    ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                });

                // 按换行分割发送到游戏内聊天框
                String[] segments = toSend.split("\n", -1);
                for (int i = 0; i < segments.length; i++) {
                    String seg = segments[i].trim();
                    if (!seg.isEmpty()) {
                        final String msgLine = seg;
                        server.execute(() -> {
                            player.sendMessage(Text.literal("§a[AI] §r" + msgLine), false);
                        });
                    }
                }
            }
        }
        } catch (java.io.UncheckedIOException streamEx) {
            // 流式读取过程中发生 EOF 或 IO 异常
            long elapsed = System.currentTimeMillis() - startTime;
            LOGGER.error("[AI诊断] 流式读取中断! 已读总行数={}, 数据行数={}, 已解析内容长度={}, 总耗时={}ms", 
                    totalLinesRead, dataLinesRead, fullContent.length(), elapsed);
            LOGGER.error("[AI诊断] 异常类型={}, 消息={}", streamEx.getClass().getName(), streamEx.getMessage());
            if (streamEx.getCause() != null) {
                LOGGER.error("[AI诊断] 根因: 类型={}, 消息={}", 
                        streamEx.getCause().getClass().getName(), streamEx.getCause().getMessage());
            }
            // 如果已经读到了部分内容，仍然返回（不抛异常）
            if (fullContent.length() > 0) {
                LOGGER.warn("[AI诊断] 流中断但已有部分内容({}字符)，将返回已读取的内容", fullContent.length());
            } else {
                throw streamEx;
            }
        }

        // 刷新剩余缓冲
        if (lineBuffer.length() > 0) {
            String remaining = lineBuffer.toString().trim();
            if (!remaining.isEmpty()) {
                // 发送流式增量到聊天界面
                final String streamDelta = remaining;
                LOGGER.debug("[流式服务端] 发送剩余缓冲到聊天界面, 长度={}", streamDelta.length());
                server.execute(() -> {
                    PacketByteBuf streamBuf = PacketByteBufs.create();
                    streamBuf.writeString(streamDelta);
                    ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                });

                String[] segments = remaining.split("\n", -1);
                for (String seg : segments) {
                    String trimmed = seg.trim();
                    if (!trimmed.isEmpty()) {
                        final String msgLine = trimmed;
                        server.execute(() -> {
                            player.sendMessage(Text.literal("§a[AI] §r" + msgLine), false);
                        });
                    }
                }
            }
        }

        String content = fullContent.toString();

        LOGGER.info("[流式] 完成，总内容长度: {}", content.length());
        if (content.isEmpty()) {
            LOGGER.warn("[流式] 未能解析到任何内容，可能是 SSE 格式不兼容");
        }

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

            while (conversationHistory.size() > MAX_HISTORY_SIZE) {
                conversationHistory.remove(0);
                conversationHistory.remove(0);
            }
        }

        return content;
    }

    /**
     * 获取异常链的简洁描述（用于诊断日志）
     */
    private String getExceptionChain(Throwable e) {
        StringBuilder chain = new StringBuilder();
        Throwable current = e;
        int depth = 0;
        while (current != null && depth < 5) {
            if (depth > 0) chain.append(" -> ");
            chain.append(current.getClass().getSimpleName()).append(": ").append(current.getMessage());
            current = current.getCause();
            depth++;
        }
        return chain.toString();
    }

    /**
     * 从 SSE data 行中提取 delta text 内容。
     * 兼容多种格式：
     * - Anthropic: {"type":"content_block_delta","delta":{"type":"text_delta","text":"..."}}
     * - OpenAI/Kimi: {"choices":[{"delta":{"content":"..."}}]}
     * - Kimi coding: 可能直接包含 "text" 字段
     */
    private String extractStreamDelta(String jsonData) {
        // Anthropic 格式
        if (jsonData.contains("\"content_block_delta\"")) {
            String marker = "\"text\":\"";
            int start = jsonData.indexOf(marker);
            if (start == -1) return null;
            start += marker.length();
            return extractJsonStringValue(jsonData, start);
        }

        // OpenAI/Kimi 格式: 查找 "delta" 对象中的 "content" 字段
        int deltaIdx = jsonData.indexOf("\"delta\"");
        if (deltaIdx != -1) {
            String contentMarker = "\"content\":\"";
            int contentIdx = jsonData.indexOf(contentMarker, deltaIdx);
            if (contentIdx != -1) {
                int start = contentIdx + contentMarker.length();
                return extractJsonStringValue(jsonData, start);
            }
            // 也尝试 "text" 字段
            String textMarker = "\"text\":\"";
            int textIdx = jsonData.indexOf(textMarker, deltaIdx);
            if (textIdx != -1) {
                int start = textIdx + textMarker.length();
                return extractJsonStringValue(jsonData, start);
            }
        }

        // 最后尝试：如果 JSON 中有 "choices" 和 "content"
        if (jsonData.contains("\"choices\"")) {
            String contentMarker = "\"content\":\"";
            int contentIdx = jsonData.indexOf(contentMarker);
            if (contentIdx != -1) {
                int start = contentIdx + contentMarker.length();
                return extractJsonStringValue(jsonData, start);
            }
        }

        // 兜底：如果包含 "text" 字段且不是 stop/start 事件
        if (!jsonData.contains("\"message_start\"") && !jsonData.contains("\"message_stop\"")
                && !jsonData.contains("\"content_block_start\"") && !jsonData.contains("\"content_block_stop\"")) {
            String textMarker = "\"text\":\"";
            int textIdx = jsonData.indexOf(textMarker);
            if (textIdx != -1) {
                int start = textIdx + textMarker.length();
                return extractJsonStringValue(jsonData, start);
            }
        }

        return null;
    }

    /**
     * 从 JSON 字符串的指定位置开始，提取一个 JSON string value（处理转义）。
     */
    private String extractJsonStringValue(String json, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"' -> { sb.append('"'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case '/' -> { sb.append('/'); i++; }
                    case 'u' -> {
                        // unicode escape: backslash u + XXXX
                        if (i + 5 < json.length()) {
                            String hex = json.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 5;
                            } catch (NumberFormatException e) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                    }
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

    /**
     * 服务端读取引用的 txt 文件内容。
     */
    private String loadReferencedFiles(List<String> fileNames) {
        java.nio.file.Path txtsDir = com.example.helloworld.ModPaths.getTxtsDir();
        if (!java.nio.file.Files.isDirectory(txtsDir)) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (String fileName : fileNames) {
            try {
                java.nio.file.Path filePath = txtsDir.resolve(fileName);
                if (java.nio.file.Files.exists(filePath)) {
                    String content = java.nio.file.Files.readString(filePath, java.nio.charset.StandardCharsets.UTF_8);
                    sb.append("=== 文件: ").append(fileName).append(" ===\n");
                    sb.append(content).append("\n\n");
                }
            } catch (Exception e) {
                LOGGER.warn("读取引用文件失败: {}", fileName, e);
                sb.append("=== 文件: ").append(fileName).append(" (读取失败) ===\n\n");
            }
        }
        return sb.toString();
    }
}
