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

    private final BlueprintRegistry blueprintRegistry = new BlueprintRegistry();

    @Override
    public void onInitialize() {
        LOGGER.info("Hello World Mod 已加载!");
        CONFIG.load();
        blueprintRegistry.loadAll();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            player.sendMessage(Text.literal("Hello World! 输入 /ai <问题> 来和 AI 对话"), false);
        });

        // 注册接收客户端 NBT 放置请求的处理器
        ServerPlayNetworking.registerGlobalReceiver(PLACE_NBT_PACKET, (server, player, handler, buf, responseSender) -> {
            String filename = buf.readString();
            server.execute(() -> {
                try {
                    java.io.File file = com.example.helloworld.nbt.NbtCommands.resolveNbtFile(filename);
                    if (file == null || !file.exists()) {
                        player.sendMessage(Text.literal("§c[NBT] 文件不存在: " + filename), false);
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
                    player.sendMessage(Text.literal("§c[NBT] 放置失败: " + e.getMessage()), false);
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
                        player.sendMessage(Text.literal("§c[TXT] 文件不存在: " + relativePath), false);
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
                    player.sendMessage(Text.literal("§c[TXT] 放置失败: " + e.getMessage()), false);
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
                    player.sendMessage(Text.literal("§a[AI] 对话历史已清空"), false);
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
                CompletableFuture.runAsync(() -> {
                    try {
                        String response = callKimiApi(fullMessage, "");

                        // 检查是否需要抓取网页
                        String fetchUrl = extractFetchUrl(response);
                        if (fetchUrl != null) {
                            String pageContent = webFetchService.fetch(fetchUrl);
                            if (pageContent != null) {
                                String fetchContext = "以下是网页 " + fetchUrl + " 的内容:\n\n" + pageContent
                                        + "\n\n请根据以上网页内容回答玩家之前的问题或执行操作。不要再使用 [FETCH] 标签。";
                                response = callKimiApi(fetchContext, "");
                            } else {
                                response = response.replaceAll("\\[FETCH\\].*?\\[/FETCH\\]", "").trim();
                                if (response.isEmpty()) response = "网页抓取失败了，请检查 URL 是否正确。";
                            }
                        } else {
                            // 检查是否需要联网搜索
                            String searchQuery = extractSearchQuery(response);
                            if (searchQuery != null && CONFIG.isWebSearchEnabled()
                                    && CONFIG.getTavilyApiKey() != null && !CONFIG.getTavilyApiKey().isEmpty()) {
                                String searchResults = webSearchService.search(searchQuery, CONFIG.getTavilyApiKey());
                                if (searchResults != null) {
                                    String searchContext = "以下是联网搜索「" + searchQuery + "」的结果:\n\n" + searchResults
                                            + "\n\n请根据以上搜索结果回答玩家之前的问题。不要再使用 [SEARCH] 标签。";
                                    response = callKimiApi(searchContext, "");
                                } else {
                                    response = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
                                    if (response.isEmpty()) response = "搜索失败了，请稍后再试。";
                                }
                            } else {
                                response = response.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
                            }
                        }

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
                        LOGGER.error("聊天界面 AI 请求失败", e);
                        server.execute(() -> {
                            PacketByteBuf respBuf = PacketByteBufs.create();
                            respBuf.writeString("§c请求失败: " + e.getMessage());
                            ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                        });
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
                        src.sendFeedback(() -> Text.literal("§e[配置] api_base_url = §f" + CONFIG.getApiBaseUrl()), false);
                        src.sendFeedback(() -> Text.literal("§e[配置] api_key = §f" + maskKey(CONFIG.getApiKey())), false);
                        src.sendFeedback(() -> Text.literal("§e[配置] model = §f" + CONFIG.getModel()), false);
                        src.sendFeedback(() -> Text.literal("§e[配置] web_search = §f" + (CONFIG.isWebSearchEnabled() ? "开启" : "关闭")), false);
                        src.sendFeedback(() -> Text.literal("§e[配置] tavily_api_key = §f" + maskKey(CONFIG.getTavilyApiKey())), false);
                        return 1;
                    })
                )
                // /aiconfig api_base_url <value>
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
                // /aiconfig api_key <value>
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
                // /aiconfig model <value>
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
                // /aiconfig web_search <on/off>
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
                // /aiconfig tavily_api_key <value>
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
                // /aiconfig reload - 重新加载配置文件
                .then(CommandManager.literal("reload")
                    .executes(ctx -> {
                        CONFIG.load();
                        ctx.getSource().sendFeedback(() -> Text.literal("§a[配置] 配置已重新加载"), false);
                        return 1;
                    })
                )
            );

            // /ainew - 清空对话历史，开启新话题
            dispatcher.register(CommandManager.literal("ainew")
                .executes(ctx -> {
                    conversationHistory.clear();
                    ctx.getSource().sendFeedback(() -> Text.literal("§a[AI] 对话历史已清空，开始新话题"), false);
                    return 1;
                })
            );

            // /aipos - 显示当前坐标
            dispatcher.register(CommandManager.literal("aipos")
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                    if (p == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal("§c[坐标] 只有玩家才能使用此命令"), false);
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
                        ctx.getSource().sendFeedback(() -> Text.literal("§a[日志] 聊天框日志显示已开启"), false);
                        return 1;
                    })
                )
                // /ailog off
                .then(CommandManager.literal("off")
                    .executes(ctx -> {
                        InGameLogAppender.setEnabled(false);
                        ctx.getSource().sendFeedback(() -> Text.literal("§c[日志] 聊天框日志显示已关闭"), false);
                        return 1;
                    })
                )
                // /ailog level <error|warn|info|debug>
                .then(CommandManager.literal("level")
                    .then(CommandManager.literal("error")
                        .executes(ctx -> {
                            InGameLogAppender.setMinLevel(org.apache.logging.log4j.Level.ERROR);
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[日志] 最低显示级别: §cERROR"), false);
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("warn")
                        .executes(ctx -> {
                            InGameLogAppender.setMinLevel(org.apache.logging.log4j.Level.WARN);
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[日志] 最低显示级别: §eWARN"), false);
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("info")
                        .executes(ctx -> {
                            InGameLogAppender.setMinLevel(org.apache.logging.log4j.Level.INFO);
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[日志] 最低显示级别: §fINFO"), false);
                            return 1;
                        })
                    )
                    .then(CommandManager.literal("debug")
                        .executes(ctx -> {
                            InGameLogAppender.setMinLevel(org.apache.logging.log4j.Level.DEBUG);
                            ctx.getSource().sendFeedback(() -> Text.literal("§a[日志] 最低显示级别: §7DEBUG"), false);
                            return 1;
                        })
                    )
                )
            );

            // /aitest - 故意触发测试日志，验证聊天框日志显示
            dispatcher.register(CommandManager.literal("aitest")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal("§e[测试] 正在生成测试日志..."), false);
                    LOGGER.warn("这是一条测试 WARN 日志 - 来自 /aitest 命令");
                    LOGGER.error("这是一条测试 ERROR 日志 - 来自 /aitest 命令");
                    LOGGER.error("模拟异常: NullPointerException at FakeClass.fakeMethod(FakeClass.java:42)");
                    LOGGER.info("这是一条测试 INFO 日志（默认级别下不会显示在聊天框）");
                    ctx.getSource().sendFeedback(() -> Text.literal("§a[测试] 已生成 2 条 WARN/ERROR + 1 条 INFO 日志，检查聊天框!"), false);
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
            source.sendFeedback(() -> Text.literal("§c只有玩家可以执行此命令"), false);
            return 0;
        }

        BlueprintData blueprint = blueprintRegistry.find(name);
        if (blueprint == null) {
            source.sendFeedback(() -> Text.literal("§c未找到蓝图: " + name), false);
            source.sendFeedback(() -> Text.literal("§e使用 /ai blueprints 查看可用蓝图"), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§e[建造] 开始建造: " + blueprint.getName() + " ..."), false);

        CompletableFuture.runAsync(() -> {
            try {
                int count = BlueprintBuilder.build(blueprint, player, player.getServerWorld());
                player.getServer().execute(() -> {
                    source.sendFeedback(() -> Text.literal("§a[建造] " + blueprint.getName() + " 建造完成! 共放置 " + count + " 个方块"), false);
                });
            } catch (Exception e) {
                LOGGER.error("建造蓝图失败", e);
                player.getServer().execute(() -> {
                    source.sendFeedback(() -> Text.literal("§c[建造] 建造失败: " + e.getMessage()), false);
                });
            }
        });

        return 1;
    }

    private int listBlueprints(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (blueprintRegistry.size() == 0) {
            source.sendFeedback(() -> Text.literal("§e没有已加载的蓝图。将 .txt 蓝图文件放入 txts/ 目录"), false);
        } else {
            source.sendFeedback(() -> Text.literal("§e已加载 " + blueprintRegistry.size() + " 个蓝图:"), false);
            for (String name : blueprintRegistry.getNames()) {
                source.sendFeedback(() -> Text.literal("§a  - " + name), false);
            }
        }
        return 1;
    }

    private int reloadBlueprints(CommandContext<ServerCommandSource> context) {
        blueprintRegistry.loadAll();
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> Text.literal("§a[蓝图] 已重新加载 " + blueprintRegistry.size() + " 个蓝图"), false);
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
            context.getSource().sendFeedback(() -> Text.literal("§e楼梯 " + (idx + 1) + ": facing=" + facing + " (位置偏东 " + (idx * 2) + ")"), false);
        }
        context.getSource().sendFeedback(() -> Text.literal("§a已在北方3格处放置4个楼梯，从左到右: north, south, east, west"), false);
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
