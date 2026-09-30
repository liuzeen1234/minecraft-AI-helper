package com.example.helloworld;

import com.mojang.brigadier.arguments.DoubleArgumentType;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
    // 客户端 -> 服务端：请求放置 Litematica 结构
    public static final Identifier PLACE_LITEMATIC_PACKET = new Identifier(MOD_ID, "place_litematic");
    // 客户端 -> 服务端：请求放置 TXT 结构设计图
    public static final Identifier PLACE_TXT_PACKET = new Identifier(MOD_ID, "place_txt");
    // 客户端 -> 服务端：请求导出选区为 NBT（含 BlockEntity 数据）
    public static final Identifier EXPORT_NBT_PACKET = new Identifier(MOD_ID, "export_nbt");
    // 服务端 -> 客户端：导出完成通知
    public static final Identifier EXPORT_NBT_RESULT_PACKET = new Identifier(MOD_ID, "export_nbt_result");
    // 客户端 -> 服务端：请求导出选区为 Litematica（含 BlockEntity 数据）
    public static final Identifier EXPORT_LITEMATIC_PACKET = new Identifier(MOD_ID, "export_litematic");
    // 服务端 -> 客户端：Litematica 导出完成通知
    public static final Identifier EXPORT_LITEMATIC_RESULT_PACKET = new Identifier(MOD_ID, "export_litematic_result");
    // 客户端 -> 服务端：请求导出选区为 TXT（含容器内容物）
    public static final Identifier EXPORT_TXT_PACKET = new Identifier(MOD_ID, "export_txt");
    // 服务端 -> 客户端：TXT 导出完成通知
    public static final Identifier EXPORT_TXT_RESULT_PACKET = new Identifier(MOD_ID, "export_txt_result");
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
    // 服务端 -> 客户端：AI 建议的原版命令，预填到聊天输入框，需玩家自行确认发送（不会自动执行）
    public static final Identifier SUGGEST_COMMAND_PACKET = new Identifier(MOD_ID, "suggest_command");
    // 服务端 -> 客户端：请求在指定坐标/角度用摄像机截图（[CAMERA_SHOT] 工具与 /ai cam_test 测试命令共用）
    public static final Identifier REQUEST_CAMERA_SHOT_PACKET = new Identifier(MOD_ID, "request_camera_shot");
    // 客户端 -> 服务端：回传摄像机截图结果（文件路径，空字符串表示截图关闭或失败）
    public static final Identifier CAMERA_SHOT_RESPONSE_PACKET = new Identifier(MOD_ID, "camera_shot_response");

    /**
     * "思考已终止" 消息的稳定哨兵值（跨端网络协议 + 客户端逻辑判断使用）。
     * 该值本身不直接展示给玩家，语言无关；实际展示文本由 {@link #thinkingCancelledDisplay()} 按当前语言生成。
     */
    public static final String THINKING_CANCELLED_SENTINEL = "\u0000__AI_THINKING_CANCELLED__";

    /** 返回"思考已终止"的本地化显示文本（跟随 Minecraft 语言）。 */
    public static String thinkingCancelledDisplay() {
        return I18n.tr("server.thinking_cancelled");
    }

    private static final ModConfig CONFIG = new ModConfig();

    public static ModConfig getConfig() {
        return CONFIG;
    }

    // 对话历史记录（多轮上下文）
    private final List<String> conversationHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 20; // 最多保留 20 条消息（10轮对话）

    /**
     * 等待客户端摄像机截图回包的挂起 Future：key 为玩家 UUID，value 在
     * {@code CAMERA_SHOT_RESPONSE_PACKET} 处理器收到回包时被 complete（结果为截图文件路径，
     * 空字符串表示截图功能关闭或客户端截图失败）。同一玩家同一时间只应有一次拍照在途，
     * 与 {@link #pendingAiTask} 单任务模式一致。
     */
    private static final Map<UUID, CompletableFuture<String>> pendingCameraShots = new ConcurrentHashMap<>();

    /** 等待客户端摄像机截图回包的最长时长（秒）：超时后放弃等待，回喂 AI 一条失败提示。 */
    private static final int CAMERA_SHOT_TIMEOUT_SECONDS = 8;

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

    private final KnowledgeBase knowledgeBase = new KnowledgeBase();

    @Override
    public void onInitialize() {
        LOGGER.info("AI Builder 已加载!");
        CONFIG.load();
        I18n.load();
        blueprintRegistry.loadAll();
        KnowledgeBase.ensureDefaultDocsReleased();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            player.sendMessage(Text.literal(I18n.tr("server.welcome")), false);

            // 检查 API Key 有效性
            ApiKeyValidator.ValidationResult quickResult = ApiKeyValidator.quickCheck(CONFIG.getApiKey());
            if (quickResult != ApiKeyValidator.ValidationResult.VALID) {
                // 格式不对，直接提示
                server.execute(() -> player.sendMessage(Text.literal(ApiKeyValidator.getResultMessage(quickResult)), false));
            } else {
                // 格式正确，后台静默验证
                ApiKeyValidator.validateAsync(CONFIG.getApiBaseUrl(), CONFIG.getApiKey(), CONFIG.getModel())
                        .thenAccept(result -> {
                            if (result != ApiKeyValidator.ValidationResult.VALID) {
                                server.execute(() -> player.sendMessage(Text.literal(ApiKeyValidator.getResultMessage(result)), false));
                            }
                        });
            }

            // 检查该存档是否是第一次加载本 mod，如果是则提示用户查看手册
            server.execute(() -> {
                try {
                    java.nio.file.Path worldDir = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).getParent();
                    java.nio.file.Path markerFile = worldDir.resolve("ai-helper-initialized.marker");
                    if (!java.nio.file.Files.exists(markerFile)) {
                        // 第一次加载，发送用户手册提示
                        player.sendMessage(Text.literal(""), false);
                        player.sendMessage(Text.literal(I18n.tr("server.first_join.welcome")), false);
                        player.sendMessage(Text.literal(I18n.tr("server.first_join.manual_tip")), false);
                        player.sendMessage(Text.literal(I18n.tr("server.first_join.how_to_open")), false);
                        player.sendMessage(Text.literal(I18n.tr("server.first_join.quick_start")), false);
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
            // 可选：客户端指定的放置原点（放置界面传入）。缺省时回退为玩家当前位置。
            boolean hasOrigin = buf.isReadable();
            int ox = hasOrigin ? buf.readInt() : 0;
            int oy = hasOrigin ? buf.readInt() : 0;
            int oz = hasOrigin ? buf.readInt() : 0;
            server.execute(() -> {
                try {
                    java.io.File file = com.example.helloworld.nbt.NbtCommands.resolveNbtFile(filename);
                    if (file == null || !file.exists()) {
                        player.sendMessage(Text.literal(I18n.tr("server.nbt.file_notfound", filename)), false);
                        return;
                    }
                    com.example.helloworld.nbt.NbtStructureParser.StructureData data =
                            com.example.helloworld.nbt.NbtStructureParser.parseAny(file);
                    net.minecraft.util.math.BlockPos origin = hasOrigin
                            ? new net.minecraft.util.math.BlockPos(ox, oy, oz)
                            : player.getBlockPos();
                    int count = com.example.helloworld.nbt.NbtStructurePlacer.place(
                            data, player.getServerWorld(), origin);
                    player.sendMessage(Text.literal(I18n.tr("server.nbt.placed",
                            file.getName(), count, origin.getX(), origin.getY(), origin.getZ())
                    ), false);
                } catch (Exception e) {
                    LOGGER.error("放置 NBT 结构失败", e);
                    player.sendMessage(Text.literal(I18n.tr("server.nbt.place_failed", e.getMessage())), false);
                }
            });
        });

        // 注册接收客户端 Litematica 结构放置请求的处理器
        ServerPlayNetworking.registerGlobalReceiver(PLACE_LITEMATIC_PACKET, (server, player, handler, buf, responseSender) -> {
            String filename = buf.readString();
            boolean hasOrigin = buf.isReadable();
            int ox = hasOrigin ? buf.readInt() : 0;
            int oy = hasOrigin ? buf.readInt() : 0;
            int oz = hasOrigin ? buf.readInt() : 0;
            server.execute(() -> {
                try {
                    java.io.File file = com.example.helloworld.nbt.NbtCommands.resolveLitematicFile(filename);
                    if (file == null || !file.exists()) {
                        player.sendMessage(Text.literal(I18n.tr("server.nbt.file_notfound", filename)), false);
                        return;
                    }
                    com.example.helloworld.nbt.NbtStructureParser.StructureData data =
                            com.example.helloworld.nbt.LitematicParser.parse(file);
                    net.minecraft.util.math.BlockPos origin = hasOrigin
                            ? new net.minecraft.util.math.BlockPos(ox, oy, oz)
                            : player.getBlockPos();
                    int count = com.example.helloworld.nbt.NbtStructurePlacer.place(
                            data, player.getServerWorld(), origin);
                    player.sendMessage(Text.literal(I18n.tr("server.nbt.placed",
                            file.getName(), count, origin.getX(), origin.getY(), origin.getZ())
                    ), false);
                } catch (Exception e) {
                    LOGGER.error("放置 Litematica 结构失败", e);
                    player.sendMessage(Text.literal(I18n.tr("server.nbt.place_failed", e.getMessage())), false);
                }
            });
        });

        // 注册接收客户端 TXT 结构放置请求的处理器
        ServerPlayNetworking.registerGlobalReceiver(PLACE_TXT_PACKET, (server, player, handler, buf, responseSender) -> {
            String relativePath = buf.readString();
            boolean hasOrigin = buf.isReadable();
            int ox = hasOrigin ? buf.readInt() : 0;
            int oy = hasOrigin ? buf.readInt() : 0;
            int oz = hasOrigin ? buf.readInt() : 0;
            server.execute(() -> {
                try {
                    // 解析 ai-helper/txts/ 目录下的文件路径
                    java.nio.file.Path txtsDir = com.example.helloworld.ModPaths.getTxtsDir();
                    if (!java.nio.file.Files.isDirectory(txtsDir)) {
                        java.nio.file.Files.createDirectories(txtsDir);
                    }
                    java.io.File file = txtsDir.resolve(relativePath).toFile();
                    if (!file.exists()) {
                        player.sendMessage(Text.literal(I18n.tr("server.txt.file_notfound", relativePath)), false);
                        return;
                    }
                    String content = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                    com.example.helloworld.blueprint.BlueprintData data =
                            com.example.helloworld.blueprint.BlueprintParser.parse(content);
                    // V3 坐标本身即世界绝对坐标，忽略放置界面传入的自定义原点，固定为世界坐标原点
                    net.minecraft.util.math.BlockPos origin = data.isV3()
                            ? net.minecraft.util.math.BlockPos.ORIGIN
                            : (hasOrigin
                                    ? new net.minecraft.util.math.BlockPos(ox, oy, oz)
                                    : player.getBlockPos());
                    int count = com.example.helloworld.blueprint.BlueprintBuilder.build(
                            data, player, player.getServerWorld(), origin);
                    player.sendMessage(Text.literal(I18n.tr("server.txt.placed",
                            data.getName(), count, origin.getX(), origin.getY(), origin.getZ())
                    ), false);
                } catch (Exception e) {
                    LOGGER.error("放置 TXT 结构失败", e);
                    player.sendMessage(Text.literal(I18n.tr("server.txt.place_failed", e.getMessage())), false);
                }
            });
        });

        // 注册接收客户端导出 NBT 请求的处理器（服务端执行，可完整读取 BlockEntity）
        ServerPlayNetworking.registerGlobalReceiver(EXPORT_NBT_PACKET, (server, player, handler, buf, responseSender) -> {
            int x1 = buf.readInt(), y1 = buf.readInt(), z1 = buf.readInt();
            int x2 = buf.readInt(), y2 = buf.readInt(), z2 = buf.readInt();
            String fileName = buf.readString();
            String subPath = buf.isReadable() ? buf.readString() : "";
            boolean includeEntities = buf.isReadable() ? buf.readBoolean() : true;
            java.util.Set<String> ignoredBlocks = readIgnoredBlocks(buf);
            server.execute(() -> {
                try {
                    net.minecraft.server.world.ServerWorld world = player.getServerWorld();
                    net.minecraft.util.math.BlockPos pos1 = new net.minecraft.util.math.BlockPos(x1, y1, z1);
                    net.minecraft.util.math.BlockPos pos2 = new net.minecraft.util.math.BlockPos(x2, y2, z2);
                    com.example.helloworld.selection.ServerSelectionExporter.exportNbt(world, pos1, pos2, fileName, subPath, includeEntities, ignoredBlocks);
                    String displayPath = subPath.isEmpty() ? fileName + ".nbt" : subPath + "/" + fileName + ".nbt";
                    // 通知客户端导出完成
                    PacketByteBuf resultBuf = PacketByteBufs.create();
                    resultBuf.writeString(I18n.tr("server.export.nbt.done", displayPath));
                    ServerPlayNetworking.send(player, EXPORT_NBT_RESULT_PACKET, resultBuf);
                } catch (Exception e) {
                    LOGGER.error("服务端导出 NBT 失败", e);
                    PacketByteBuf resultBuf = PacketByteBufs.create();
                    resultBuf.writeString(I18n.tr("server.export.nbt.failed", e.getMessage()));
                    ServerPlayNetworking.send(player, EXPORT_NBT_RESULT_PACKET, resultBuf);
                }
            });
        });

        // 注册接收客户端导出 Litematica 请求的处理器
        ServerPlayNetworking.registerGlobalReceiver(EXPORT_LITEMATIC_PACKET, (server, player, handler, buf, responseSender) -> {
            int x1 = buf.readInt(), y1 = buf.readInt(), z1 = buf.readInt();
            int x2 = buf.readInt(), y2 = buf.readInt(), z2 = buf.readInt();
            String fileName = buf.readString();
            String subPath = buf.isReadable() ? buf.readString() : "";
            boolean includeEntities = buf.isReadable() ? buf.readBoolean() : true;
            java.util.Set<String> ignoredBlocks = readIgnoredBlocks(buf);
            server.execute(() -> {
                try {
                    net.minecraft.server.world.ServerWorld world = player.getServerWorld();
                    net.minecraft.util.math.BlockPos pos1 = new net.minecraft.util.math.BlockPos(x1, y1, z1);
                    net.minecraft.util.math.BlockPos pos2 = new net.minecraft.util.math.BlockPos(x2, y2, z2);
                    com.example.helloworld.selection.ServerSelectionExporter.exportLitematic(
                            world, pos1, pos2, fileName, subPath, includeEntities, ignoredBlocks);
                    String displayPath = subPath.isEmpty() ? fileName + ".litematic"
                            : subPath + "/" + fileName + ".litematic";
                    PacketByteBuf resultBuf = PacketByteBufs.create();
                    resultBuf.writeString(I18n.tr("server.export.litematic.done", displayPath));
                    ServerPlayNetworking.send(player, EXPORT_LITEMATIC_RESULT_PACKET, resultBuf);
                } catch (Exception e) {
                    LOGGER.error("服务端导出 Litematica 失败", e);
                    PacketByteBuf resultBuf = PacketByteBufs.create();
                    resultBuf.writeString(I18n.tr("server.export.litematic.failed", e.getMessage()));
                    ServerPlayNetworking.send(player, EXPORT_LITEMATIC_RESULT_PACKET, resultBuf);
                }
            });
        });

        // 注册接收客户端导出 TXT（含容器内容）请求的处理器
        ServerPlayNetworking.registerGlobalReceiver(EXPORT_TXT_PACKET, (server, player, handler, buf, responseSender) -> {
            int x1 = buf.readInt(), y1 = buf.readInt(), z1 = buf.readInt();
            int x2 = buf.readInt(), y2 = buf.readInt(), z2 = buf.readInt();
            String fileName = buf.readString();
            String subPath = buf.isReadable() ? buf.readString() : "";
            java.util.Set<String> ignoredBlocks = readIgnoredBlocks(buf);
            server.execute(() -> {
                try {
                    net.minecraft.server.world.ServerWorld world = player.getServerWorld();
                    net.minecraft.util.math.BlockPos pos1 = new net.minecraft.util.math.BlockPos(x1, y1, z1);
                    net.minecraft.util.math.BlockPos pos2 = new net.minecraft.util.math.BlockPos(x2, y2, z2);
                    com.example.helloworld.selection.ServerSelectionExporter.exportTxt(world, pos1, pos2, fileName, subPath, ignoredBlocks);
                    String displayPath = subPath.isEmpty() ? fileName + ".txt" : subPath + "/" + fileName + ".txt";
                    PacketByteBuf resultBuf = PacketByteBufs.create();
                    resultBuf.writeString(I18n.tr("server.export.txt.done", displayPath));
                    ServerPlayNetworking.send(player, EXPORT_TXT_RESULT_PACKET, resultBuf);
                } catch (Exception e) {
                    LOGGER.error("服务端导出 TXT 失败", e);
                    PacketByteBuf resultBuf = PacketByteBufs.create();
                    resultBuf.writeString(I18n.tr("server.export.txt.failed", e.getMessage()));
                    ServerPlayNetworking.send(player, EXPORT_TXT_RESULT_PACKET, resultBuf);
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
                    player.sendMessage(Text.literal(I18n.tr("server.ai.history_cleared")), false);
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
                            server.execute(() -> player.sendMessage(Text.literal(I18n.tr("server.ai.generating")), false));
                            response = callKimiApiStreaming(fullMessage, "", player, server);
                        } else {
                            response = callKimiApi(fullMessage, "");
                        }

                        // 检查是否已被取消
                        if (cancelRequested) {
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString(THINKING_CANCELLED_SENTINEL);
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                            return;
                        }

                        // 多轮工具调用循环：进度提示通过聊天界面流式包发送
                        boolean streamingChat = CONFIG.isStreamOutputEnabled();
                        ToolLoopNotifier chatNotifier = msg -> server.execute(() -> {
                            PacketByteBuf streamBuf = PacketByteBufs.create();
                            streamBuf.writeString(msg);
                            ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                        });
                        ToolLoopFinisher chatFinisher = outcome -> {
                            if (outcome.cancelled || outcome.error != null) {
                                if (outcome.error != null) {
                                    LOGGER.error("聊天界面 AI 请求失败", outcome.error);
                                }
                                server.execute(() -> {
                                    PacketByteBuf respBuf = PacketByteBufs.create();
                                    respBuf.writeString(outcome.error != null
                                            ? I18n.tr("server.request_failed", outcome.error.getMessage())
                                            : THINKING_CANCELLED_SENTINEL);
                                    ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                                });
                                return;
                            }
                            // runToolLoopInternal 已经执行完最后一轮回复里的所有 AI 指令，这里直接展示
                            String finalResponse = outcome.result.finalResponse;
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString(finalResponse);
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                        };
                        runToolLoop(response, streamingChat, player, server, streamingChat, chatNotifier, chatFinisher);
                    } catch (Exception e) {
                        if (cancelRequested) {
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString(THINKING_CANCELLED_SENTINEL);
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                            return;
                        }
                        LOGGER.error("聊天界面 AI 请求失败", e);
                        server.execute(() -> {
                            PacketByteBuf respBuf = PacketByteBufs.create();
                            respBuf.writeString(I18n.tr("server.request_failed", e.getMessage()));
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

            // 从文件路径读取图片并转 base64（与 SCREENSHOT_RESPONSE_PACKET 处理器共用同一读取逻辑）
            final String finalBase64Image = readImageAsBase64WithRetry(screenshotPath);

            server.execute(() -> {
                if ("__CLEAR_HISTORY__".equals(message)) {
                    conversationHistory.clear();
                    player.sendMessage(Text.literal(I18n.tr("server.ai.history_cleared")), false);
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
                            server.execute(() -> player.sendMessage(Text.literal(I18n.tr("server.ai.generating")), false));
                            response = callKimiApiStreaming(fullMessage, finalBase64, player, server);
                        } else {
                            response = callKimiApi(fullMessage, finalBase64);
                        }

                        if (cancelRequested) {
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString(THINKING_CANCELLED_SENTINEL);
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                            return;
                        }

                        // 多轮工具调用循环：进度提示通过聊天界面流式包发送
                        boolean streamingImg = CONFIG.isStreamOutputEnabled();
                        ToolLoopNotifier imgNotifier = msg -> server.execute(() -> {
                            PacketByteBuf streamBuf = PacketByteBufs.create();
                            streamBuf.writeString(msg);
                            ServerPlayNetworking.send(player, CHAT_SCREEN_STREAM_PACKET, streamBuf);
                        });
                        ToolLoopFinisher imgFinisher = outcome -> {
                            if (outcome.cancelled || outcome.error != null) {
                                if (outcome.error != null) {
                                    LOGGER.error("聊天界面 AI 请求失败", outcome.error);
                                }
                                server.execute(() -> {
                                    PacketByteBuf respBuf = PacketByteBufs.create();
                                    respBuf.writeString(outcome.error != null
                                            ? I18n.tr("server.request_failed", outcome.error.getMessage())
                                            : THINKING_CANCELLED_SENTINEL);
                                    ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                                });
                                return;
                            }
                            // runToolLoopInternal 已经执行完最后一轮回复里的所有 AI 指令，这里直接展示
                            String finalResponse = outcome.result.finalResponse;
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString(finalResponse);
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                        };
                        runToolLoop(response, streamingImg, player, server, streamingImg, imgNotifier, imgFinisher);
                    } catch (Exception e) {
                        if (cancelRequested) {
                            server.execute(() -> {
                                PacketByteBuf respBuf = PacketByteBufs.create();
                                respBuf.writeString(THINKING_CANCELLED_SENTINEL);
                                ServerPlayNetworking.send(player, CHAT_SCREEN_RESPONSE_PACKET, respBuf);
                            });
                            return;
                        }
                        LOGGER.error("聊天界面 AI 请求失败", e);
                        server.execute(() -> {
                            PacketByteBuf respBuf = PacketByteBufs.create();
                            respBuf.writeString(I18n.tr("server.request_failed", e.getMessage()));
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

            // 从文件读取图片并转 base64（与 CHAT_SCREEN_MSG_WITH_IMG_PACKET 处理器共用同一读取逻辑）
            final String finalBase64Image = readImageAsBase64WithRetry(screenshotPath);

            server.execute(() -> {
                ServerCommandSource source = player.getCommandSource();

                // 在聊天框回显玩家输入的消息
                String playerName = player.getName().getString();
                source.sendFeedback(() -> Text.literal("§f<" + playerName + "> " + message), false);

                source.sendFeedback(() -> Text.literal(I18n.tr("server.ai.thinking")), false);

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

                        // 多轮工具调用循环：进度提示通过命令反馈发送
                        boolean streamingCmd = CONFIG.isStreamOutputEnabled();
                        ToolLoopNotifier cmdNotifier = msg -> server.execute(() ->
                                source.sendFeedback(() -> Text.literal(stripColorCodes(msg).trim()), false));
                        ToolLoopFinisher cmdFinisher = outcome -> {
                            if (outcome.cancelled) return;
                            if (outcome.error != null) {
                                LOGGER.error("调用 AI API 失败", outcome.error);
                                LOGGER.error("[AI诊断] 异常链: {}", getExceptionChain(outcome.error));
                                server.execute(() -> source.sendFeedback(
                                        () -> Text.literal(I18n.tr("server.ai.request_failed", outcome.error.getMessage())), false));
                                return;
                            }
                            // runToolLoopInternal 已经执行完最后一轮回复里的所有 AI 指令，这里直接展示
                            final String fullText = outcome.result.finalResponse;
                            final String resultBlockOnly = outcome.result.resultBlock;
                            final boolean streamedAlready = outcome.result.streamedLastRound;
                            server.execute(() -> {
                                if (!streamedAlready) {
                                    sendLongMessage(source, fullText);
                                } else if (resultBlockOnly != null && !resultBlockOnly.isEmpty()) {
                                    // 流式模式下正文已显示，仅补发指令执行结果
                                    sendLongMessage(source, resultBlockOnly);
                                }
                            });
                        };
                        runToolLoop(response, wasStreamed, player, server, streamingCmd, cmdNotifier, cmdFinisher);
                    } catch (Exception e) {
                        LOGGER.error("调用 AI API 失败", e);
                        LOGGER.error("[AI诊断] 异常链: {}", getExceptionChain(e));
                        server.execute(() -> {
                            source.sendFeedback(() -> Text.literal(I18n.tr("server.ai.request_failed", e.getMessage())), false);
                        });
                    } finally {
                        pendingAiTask = null;
                    }
                });
            });
        });

        // 注册接收客户端摄像机截图结果的处理器（[CAMERA_SHOT] 工具 / /ai cam_test 测试命令共用）。
        // 若有对应的挂起 Future（AI 工具循环发起的请求），优先 complete 它，交给等待方处理；
        // 否则说明是 /ai cam_test 测试命令发起的（不等待结果），直接在聊天框回显路径。
        ServerPlayNetworking.registerGlobalReceiver(CAMERA_SHOT_RESPONSE_PACKET, (server, player, handler, buf, responseSender) -> {
            String screenshotPath = buf.readString();
            CompletableFuture<String> pending = pendingCameraShots.remove(player.getUuid());
            if (pending != null) {
                pending.complete(screenshotPath == null ? "" : screenshotPath);
                return;
            }
            server.execute(() -> {
                if (screenshotPath == null || screenshotPath.isEmpty()) {
                    player.sendMessage(Text.literal(I18n.tr("cmd.camera_shot.disabled")), false);
                } else {
                    player.sendMessage(Text.literal(I18n.tr("cmd.camera_shot.done", screenshotPath)), false);
                }
            });
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("ai")
                .then(CommandManager.literal("blueprints")
                    .executes(this::listBlueprints)
                )
                .then(CommandManager.literal("reload_blueprints")
                    .executes(this::reloadBlueprints)
                )
                // /ai confirm <requestId> - 确认指定的 AI 待确认操作请求（聊天框 [是] 按钮触发）。
                .then(CommandManager.literal("confirm")
                    .then(CommandManager.argument("requestId", StringArgumentType.word())
                        .executes(this::executeConfirmRequest)
                    )
                )
                // /ai reject [requestId] - 拒绝 AI 待确认的操作请求：
                //   带 requestId：拒绝该条（聊天框 [否] 按钮触发）；
                //   不带参数：拒绝当前玩家所有待确认请求。
                // 拒绝会触发拒绝回调并让对话续跑（AI 会收到"用户已拒绝"）。
                .then(CommandManager.literal("reject")
                    .then(CommandManager.argument("requestId", StringArgumentType.word())
                        .executes(this::executeRejectRequest)
                    )
                    .executes(this::executeReject)
                )
                // /ai cam_test <x> <y> <z> <yaw> <pitch> - 测试摄像机截图工具：
                // 直接调用 AICommandExecutor.executeCameraShotMaybeConfirm（与未来 [CAMERA_SHOT]
                // 标签共用同一入口），会按 confirm_before_execute_enabled 走确认流程。
                .then(CommandManager.literal("cam_test")
                    .then(CommandManager.argument("x", DoubleArgumentType.doubleArg())
                        .then(CommandManager.argument("y", DoubleArgumentType.doubleArg())
                            .then(CommandManager.argument("z", DoubleArgumentType.doubleArg())
                                .then(CommandManager.argument("yaw", DoubleArgumentType.doubleArg())
                                    .then(CommandManager.argument("pitch", DoubleArgumentType.doubleArg())
                                        .executes(this::executeCameraShotTest)
                                    )
                                )
                            )
                        )
                    )
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
                        src.sendFeedback(() -> Text.literal(I18n.tr("server.config.show.api_base_url", CONFIG.getApiBaseUrl())), false);
                        src.sendFeedback(() -> Text.literal(I18n.tr("server.config.show.api_key", maskKey(CONFIG.getApiKey()))), false);
                        src.sendFeedback(() -> Text.literal(I18n.tr("server.config.show.model", CONFIG.getModel())), false);
                        src.sendFeedback(() -> Text.literal(I18n.tr("server.config.show.web_search", CONFIG.isWebSearchEnabled() ? I18n.tr("server.config.on") : I18n.tr("server.config.off"))), false);
                        src.sendFeedback(() -> Text.literal(I18n.tr("server.config.show.tavily_api_key", maskKey(CONFIG.getTavilyApiKey()))), false);
                        return 1;
                    })
                )
                // /aiconfig api_base_url <value>
                .then(CommandManager.literal("api_base_url")
                    .then(CommandManager.argument("value", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String value = StringArgumentType.getString(ctx, "value");
                            CONFIG.setApiBaseUrl(value);
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.config.api_base_url.updated", value)), false);
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
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.config.api_key.updated")), false);
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
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.config.model.updated", value)), false);
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
                            ctx.getSource().sendFeedback(() -> Text.literal(enabled ? I18n.tr("server.config.web_search.enabled") : I18n.tr("server.config.web_search.disabled")), false);
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
                            ctx.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.config.tavily_api_key.updated")), false);
                            return 1;
                        })
                    )
                )
                // /aiconfig reload - 重新加载配置文件
                .then(CommandManager.literal("reload")
                    .executes(ctx -> {
                        CONFIG.load();
                        ctx.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.config.reloaded")), false);
                        return 1;
                    })
                )
            );

            // /ainew - 清空对话历史，开启新话题
            dispatcher.register(CommandManager.literal("ainew")
                .executes(ctx -> {
                    conversationHistory.clear();
                    ctx.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.ai.new_topic")), false);
                    return 1;
                })
            );

            // /aipos - 显示当前坐标
            dispatcher.register(CommandManager.literal("aipos")
                .executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayer();
                    if (p == null) {
                        ctx.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.pos.players_only")), false);
                        return 0;
                    }
                    double x = Math.round(p.getX() * 100.0) / 100.0;
                    double y = Math.round(p.getY() * 100.0) / 100.0;
                    double z = Math.round(p.getZ() * 100.0) / 100.0;
                    String dim = p.getWorld().getRegistryKey().getValue().toString();
                    ctx.getSource().sendFeedback(() -> Text.literal(
                        I18n.tr("server.pos.result", x, y, z, dim)
                    ), false);
                    return 1;
                })
            );

            // 注意：原 /aitest 命令（生成测试日志）已迁移到 debug-menu 调试菜单里的
            // “生成测试日志”按钮（见 DebugMenuIntegration#generateTestLogs）。

            // /aistop - 终止当前 AI 思考/生成
            dispatcher.register(CommandManager.literal("aistop")
                .executes(ctx -> {
                    cancelRequested = true;
                    CompletableFuture<?> task = pendingAiTask;
                    if (task != null) {
                        task.cancel(true);
                        ctx.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.ai.stopped")), false);
                    } else {
                        ctx.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.ai.no_request")), false);
                    }
                    return 1;
                })
            );

        });
    }

    private int listBlueprints(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        if (blueprintRegistry.size() == 0) {
            source.sendFeedback(() -> Text.literal(I18n.tr("server.blueprints.empty")), false);
        } else {
            source.sendFeedback(() -> Text.literal(I18n.tr("server.blueprints.loaded", blueprintRegistry.size())), false);
            for (String name : blueprintRegistry.getNames()) {
                source.sendFeedback(() -> Text.literal("§a  - " + name), false);
            }
        }
        return 1;
    }

    private int reloadBlueprints(CommandContext<ServerCommandSource> context) {
        blueprintRegistry.loadAll();
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> Text.literal(I18n.tr("server.blueprints.reloaded", blueprintRegistry.size())), false);
        return 1;
    }

    /**
     * 命令：{@code /ai reject}。
     * 拒绝当前玩家所有待确认的 AI 操作请求（{@link PendingActionConfirmation}），
     * 无需输入 requestId 或点击 [否] 按钮。走与真实超时完全相同的拒绝路径：触发拒绝回调、
     * 把"用户已拒绝"结果回喂给 AI 让对话续跑。
     */
    private int executeReject(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        // 同时覆盖两类挂起：待玩家点击 [是]/[否] 确认的操作，以及 execute_command 建议后
        // 等待玩家在聊天框执行的命令（后者拒绝会触发"未执行"续跑）。
        int count = PendingActionConfirmation.forceTimeoutForPlayer(player.getUuid())
                + PendingCommandSuggestion.forceTimeoutForPlayer(player.getUuid());
        if (count == 0) {
            context.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.reject.none")), false);
        } else {
            final int finalCount = count;
            context.getSource().sendFeedback(() -> Text.literal(I18n.tr("server.reject.done", finalCount)), false);
        }
        return 1;
    }

    /**
     * 命令：{@code /ai confirm <requestId>}。确认指定的 AI 待确认操作请求，
     * 由聊天框 [是] 按钮的 ClickEvent 触发。调用 {@link PendingActionConfirmation#resolve}。
     */
    private int executeConfirmRequest(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;
        String requestId = StringArgumentType.getString(context, "requestId");
        String feedback = PendingActionConfirmation.resolve(requestId, true, player);
        context.getSource().sendFeedback(() -> Text.literal(feedback), false);
        return 1;
    }

    /**
     * 命令：{@code /ai reject <requestId>}。拒绝指定的 AI 待确认操作请求，
     * 由聊天框 [否] 按钮的 ClickEvent 触发。调用 {@link PendingActionConfirmation#resolve}。
     */
    private int executeRejectRequest(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;
        String requestId = StringArgumentType.getString(context, "requestId");
        String feedback = PendingActionConfirmation.resolve(requestId, false, player);
        context.getSource().sendFeedback(() -> Text.literal(feedback), false);
        return 1;
    }

    /**
     * 测试命令：{@code /ai cam_test <x> <y> <z> <yaw> <pitch>}。
     * 直接调用 {@link AICommandExecutor#executeCameraShotMaybeConfirm}——与未来
     * {@code [CAMERA_SHOT]} 标签共用同一入口，会按 {@code confirm_before_execute_enabled}
     * 走确认流程（开启时先弹 [是]/[否]，玩家确认后才真正发包给客户端截图）。
     */
    private int executeCameraShotTest(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        double x = DoubleArgumentType.getDouble(context, "x");
        double y = DoubleArgumentType.getDouble(context, "y");
        double z = DoubleArgumentType.getDouble(context, "z");
        float yaw = (float) DoubleArgumentType.getDouble(context, "yaw");
        float pitch = (float) DoubleArgumentType.getDouble(context, "pitch");

        String feedback = AICommandExecutor.executeCameraShotMaybeConfirm(x, y, z, yaw, pitch, player);
        context.getSource().sendFeedback(() -> Text.literal(feedback), false);
        return 1;
    }

    /**
     * 给指定玩家的客户端发包，请求在指定坐标/角度用摄像机截图。
     * 截图是否开启（{@code screenshot_enabled}）由客户端处理器自行判断，
     * 关闭时客户端会回传空字符串路径。
     */
    public static void requestCameraShot(ServerPlayerEntity player, double x, double y, double z,
                                          float yaw, float pitch) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        ServerPlayNetworking.send(player, REQUEST_CAMERA_SHOT_PACKET, buf);
    }

    /**
     * 给指定玩家的客户端发包请求摄像机截图，并阻塞等待客户端回包（最多
     * {@link #CAMERA_SHOT_TIMEOUT_SECONDS} 秒），返回截图文件路径。
     *
     * <p>供 AI 工具循环使用（{@code [CAMERA_SHOT]} 标签）：调用方需在独立线程上调用本方法
     * （它会阻塞当前线程等待网络往返），不能在服务端主线程上调用。
     *
     * @return 截图文件路径；超时、客户端截图关闭或失败时返回空字符串
     */
    public static String requestCameraShotAndAwait(ServerPlayerEntity player, double x, double y, double z,
                                                     float yaw, float pitch) {
        UUID uuid = player.getUuid();
        CompletableFuture<String> future = new CompletableFuture<>();
        // 同一玩家若已有一次拍照在途，先取消旧的等待方（避免它永久卡住），让新请求覆盖
        CompletableFuture<String> previous = pendingCameraShots.put(uuid, future);
        if (previous != null) {
            previous.complete("");
        }
        requestCameraShot(player, x, y, z, yaw, pitch);
        try {
            return future.get(CAMERA_SHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOGGER.warn("等待摄像机截图回包超时或失败: {}", e.getMessage());
            return "";
        } finally {
            pendingCameraShots.remove(uuid, future);
        }
    }

    /**
     * 从截图文件路径读取图片并转 base64。文件可能刚被客户端写入还没落盘完成，
     * 最多重试 5 次、每次间隔 200ms（与现有 {@code SCREENSHOT_RESPONSE_PACKET} /
     * {@code CHAT_SCREEN_MSG_WITH_IMG_PACKET} 处理器里的读取逻辑一致，此处抽成共享方法）。
     *
     * @return base64 编码的图片内容；路径为空或读取失败时返回空字符串
     */
    private String readImageAsBase64WithRetry(String imagePath) {
        if (imagePath == null || imagePath.isEmpty()) return "";
        java.nio.file.Path imgPath = java.nio.file.Path.of(imagePath);
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                if (java.nio.file.Files.exists(imgPath) && java.nio.file.Files.size(imgPath) > 0) {
                    byte[] imageBytes = java.nio.file.Files.readAllBytes(imgPath);
                    return java.util.Base64.getEncoder().encodeToString(imageBytes);
                }
            } catch (java.nio.file.AccessDeniedException e) {
                LOGGER.warn("图片文件被占用，重试中... ({})", attempt + 1);
            } catch (Exception e) {
                LOGGER.error("读取图片文件失败: {}", imagePath, e);
                break;
            }
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }
        return "";
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

    /**
     * 转义字符串使其可安全放入 JSON 字符串字面量。
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 构建当前用户消息 JSON 对象，根据生效的 API 格式选择结构。
     * - OpenAI：图片使用 image_url + data URI（content 为数组）
     * - Anthropic：图片使用 image + source.base64（content 为数组）
     * 纯文本时两种格式一致：{"role":"user","content":"..."}
     */
    private String buildUserMessage(String escapedMessage, String base64Image) {
        boolean hasImage = base64Image != null && !base64Image.isEmpty();
        if (!hasImage) {
            return """
                        {
                            "role": "user",
                            "content": "%s"
                        }""".formatted(escapedMessage);
        }
        if (CONFIG.isOpenAiFormat()) {
            return """
                        {
                            "role": "user",
                            "content": [
                                {
                                    "type": "text",
                                    "text": "%s"
                                },
                                {
                                    "type": "image_url",
                                    "image_url": {
                                        "url": "data:image/png;base64,%s"
                                    }
                                }
                            ]
                        }""".formatted(escapedMessage, base64Image);
        }
        // Anthropic
        return """
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
    }

    /**
     * 构建 messages 数组 JSON。OpenAI 格式会将 system 作为首条消息加入。
     */
    private String buildMessagesArray(String currentUserMessage, String escapedSystemPrompt) {
        StringBuilder messagesBuilder = new StringBuilder();
        messagesBuilder.append("[");

        // OpenAI 格式：system 作为 messages 中的第一条
        if (CONFIG.isOpenAiFormat()) {
            messagesBuilder.append("""
                        {
                            "role": "system",
                            "content": "%s"
                        }""".formatted(escapedSystemPrompt));
            messagesBuilder.append(",");
        }

        if (CONFIG.isContextEnabled() && !conversationHistory.isEmpty()) {
            for (int i = 0; i < conversationHistory.size(); i++) {
                messagesBuilder.append(conversationHistory.get(i));
                messagesBuilder.append(",");
            }
        }

        messagesBuilder.append(currentUserMessage);
        messagesBuilder.append("]");
        return messagesBuilder.toString();
    }

    /**
     * 构建请求体，根据生效格式选择 OpenAI / Anthropic 结构。
     * Anthropic 使用顶层 system 字段；OpenAI 将 system 放入 messages（由 buildMessagesArray 处理）。
     */
    private String buildRequestBody(String messagesArray, String escapedSystemPrompt, boolean stream) {
        if (CONFIG.isOpenAiFormat()) {
            return """
                {
                    "model": "%s",
                    "max_tokens": 16384,
                    "stream": %s,
                    "messages": %s
                }
                """.formatted(CONFIG.getModel(), stream, messagesArray);
        }
        // Anthropic
        return """
                {
                    "model": "%s",
                    "max_tokens": 16384,
                    "stream": %s,
                    "system": "%s",
                    "messages": %s
                }
                """.formatted(CONFIG.getModel(), stream, escapedSystemPrompt, messagesArray);
    }

    /**
     * 为请求应用鉴权 headers，根据生效格式选择。
     */
    private HttpRequest.Builder applyAuthHeaders(HttpRequest.Builder builder) {
        builder.header("Content-Type", "application/json");
        if (CONFIG.isOpenAiFormat()) {
            builder.header("Authorization", "Bearer " + CONFIG.getApiKey());
        } else {
            builder.header("x-api-key", CONFIG.getApiKey());
            builder.header("anthropic-version", "2023-06-01");
        }
        return builder;
    }

    private String callKimiApi(String userMessage, String base64Image) throws Exception {
        String escapedMessage = escapeJson(userMessage);

        // 构建当前用户消息
        String currentUserMessage = buildUserMessage(escapedMessage, base64Image);

        // system prompt 用于告诉 AI 可用的游戏指令
        String systemPrompt = escapeJson(AICommandExecutor.getSystemPrompt(CONFIG.isVanillaCommandsEnabled(), CONFIG.isCameraShotEnabled()));

        // 构建 messages 数组（OpenAI 格式会自动加入 system 消息）
        String messagesArray = buildMessagesArray(currentUserMessage, systemPrompt);

        String requestBody = buildRequestBody(messagesArray, systemPrompt, false);

        HttpRequest request = applyAuthHeaders(HttpRequest.newBuilder()
                .uri(URI.create(CONFIG.getResolvedEndpoint())))
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
        String escapedMessage = escapeJson(userMessage);

        // 构建当前用户消息
        String currentUserMessage = buildUserMessage(escapedMessage, base64Image);

        String systemPrompt = escapeJson(AICommandExecutor.getSystemPrompt(CONFIG.isVanillaCommandsEnabled(), CONFIG.isCameraShotEnabled()));

        // 构建 messages 数组（OpenAI 格式会自动加入 system 消息）
        String messagesArray = buildMessagesArray(currentUserMessage, systemPrompt);

        // 启用 stream
        String requestBody = buildRequestBody(messagesArray, systemPrompt, true);

        HttpRequest request = applyAuthHeaders(HttpRequest.newBuilder()
                .uri(URI.create(CONFIG.getResolvedEndpoint())))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(180))
                .build();

        // 诊断日志：请求信息
        LOGGER.info("[AI诊断] 发送请求: URL={}, 格式={}, 请求体大小={}字节, HttpClient版本={}",
                CONFIG.getResolvedEndpoint(), CONFIG.getEffectiveApiFormat(), requestBody.length(), httpClient.version());
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
            int start = findStringValueStart(jsonData, "text", 0);
            if (start == -1) return null;
            return extractJsonStringValue(jsonData, start);
        }

        // OpenAI/Kimi 格式: 查找 "delta" 对象中的 "content" 字段
        int deltaIdx = jsonData.indexOf("\"delta\"");
        if (deltaIdx != -1) {
            int start = findStringValueStart(jsonData, "content", deltaIdx);
            if (start != -1) {
                return extractJsonStringValue(jsonData, start);
            }
            // 也尝试 "text" 字段
            start = findStringValueStart(jsonData, "text", deltaIdx);
            if (start != -1) {
                return extractJsonStringValue(jsonData, start);
            }
        }

        // 最后尝试：如果 JSON 中有 "choices" 和 "content"
        if (jsonData.contains("\"choices\"")) {
            int start = findStringValueStart(jsonData, "content", 0);
            if (start != -1) {
                return extractJsonStringValue(jsonData, start);
            }
        }

        // 兜底：如果包含 "text" 字段且不是 stop/start 事件
        if (!jsonData.contains("\"message_start\"") && !jsonData.contains("\"message_stop\"")
                && !jsonData.contains("\"content_block_start\"") && !jsonData.contains("\"content_block_stop\"")) {
            int start = findStringValueStart(jsonData, "text", 0);
            if (start != -1) {
                return extractJsonStringValue(jsonData, start);
            }
        }

        return null;
    }

    /**
     * 在 json 中从 fromIndex 起查找形如 "key" : "value" 的字段，返回 value 首字符的下标。
     * 容忍 key 与冒号、冒号与引号之间的任意空白（兼容 "content":"x" 与 "content": "x"）。
     * 若字段不存在或其值不是字符串，返回 -1。
     */
    private int findStringValueStart(String json, String key, int fromIndex) {
        String keyToken = "\"" + key + "\"";
        int idx = json.indexOf(keyToken, Math.max(0, fromIndex));
        if (idx == -1) return -1;
        int i = idx + keyToken.length();
        // 跳过空白
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != ':') return -1;
        i++;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length() || json.charAt(i) != '"') return -1; // 值不是字符串（可能是 null/对象）
        return i + 1;
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
        // OpenAI 格式：{"choices":[{"message":{"content":"..."}}]}
        if (CONFIG.isOpenAiFormat() || jsonResponse.contains("\"choices\"")) {
            int msgIdx = jsonResponse.indexOf("\"message\"");
            if (msgIdx != -1) {
                int contentStart = findStringValueStart(jsonResponse, "content", msgIdx);
                if (contentStart != -1) {
                    return extractJsonStringValue(jsonResponse, contentStart);
                }
            }
        }

        // Anthropic 格式：{"content":[{"type":"text","text":"..."}]}
        int start = findStringValueStart(jsonResponse, "text", 0);
        if (start == -1) {
            LOGGER.warn("无法解析 API 响应: {}", jsonResponse);
            return "无法解析 AI 响应";
        }
        return extractJsonStringValue(jsonResponse, start);
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
     * 从 AI 回复中提取 [KNOWLEDGE]文档A,文档B[/KNOWLEDGE] 标签内点名的知识库文档名列表。
     * 文档名以英文逗号分隔，忽略空白项。仅取第一个 [KNOWLEDGE] 标签（与 SEARCH/FETCH 规则一致）。
     */
    private List<String> extractKnowledgeDocNames(String response) {
        int start = response.indexOf("[KNOWLEDGE]");
        int end = response.indexOf("[/KNOWLEDGE]");
        if (start == -1 || end == -1 || end <= start) {
            return java.util.Collections.emptyList();
        }
        String raw = response.substring(start + "[KNOWLEDGE]".length(), end).trim();
        if (raw.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (String part : raw.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 判断 AI 回复中是否请求了知识库目录结构（[KNOWLEDGE_TREE][/KNOWLEDGE_TREE]，标签内不需要任何内容）。
     * 用于配合 [KNOWLEDGE_FILE] 使用：AI 先用这个工具看清知识库下的完整文件/文件夹结构，
     * 再用 [KNOWLEDGE_FILE] 点名读取某个具体文件的正文。
     */
    private boolean containsKnowledgeTreeTag(String response) {
        return response != null && response.contains("[KNOWLEDGE_TREE]");
    }

    /**
     * 从 AI 回复中提取 [KNOWLEDGE_FILE]相对路径[/KNOWLEDGE_FILE] 标签内点名的文件相对路径。
     * 路径应为 [KNOWLEDGE_TREE] 返回结果中出现的相对路径（相对知识库根目录，支持多级子目录）。
     * 仅取第一个 [KNOWLEDGE_FILE] 标签（与 SEARCH/FETCH/KNOWLEDGE 规则一致）。
     */
    private String extractKnowledgeFilePath(String response) {
        int start = response.indexOf("[KNOWLEDGE_FILE]");
        int end = response.indexOf("[/KNOWLEDGE_FILE]");
        if (start != -1 && end != -1 && end > start) {
            String path = response.substring(start + "[KNOWLEDGE_FILE]".length(), end).trim();
            return path.isEmpty() ? null : path;
        }
        return null;
    }

    // ================= 多轮工具调用循环 =================

    /**
     * 进度通知回调：在工具循环各阶段（正在搜索/抓取、开始新一轮等）向对应输出通道发送提示。
     */
    interface ToolLoopNotifier {
        void notify(String message);
    }

    /**
     * 多轮工具调用循环的执行结果。
     * finalResponse : 循环结束时的完整最终文本（已执行完所有 ACTION/BLUEPRINT，清理了联网标签，
     *                  等价于 cleanText 与 resultBlock 拼接后的结果，可直接展示）。
     * cleanText     : 纯正文部分（不含指令执行结果块）。
     * resultBlock   : 指令执行结果块（无指令执行时为空字符串）。
     * streamedLastRound : 最后一轮是否是通过流式输出产生的（用于调用端决定是否重复发送正文）。
     */
    static class ToolLoopResult {
        final String finalResponse;
        final String cleanText;
        final String resultBlock;
        final boolean streamedLastRound;
        ToolLoopResult(String finalResponse, boolean streamedLastRound) {
            this(finalResponse, finalResponse, "", streamedLastRound);
        }
        ToolLoopResult(String finalResponse, String cleanText, String resultBlock, boolean streamedLastRound) {
            this.finalResponse = finalResponse;
            this.cleanText = cleanText;
            this.resultBlock = resultBlock;
            this.streamedLastRound = streamedLastRound;
        }
    }

    /**
     * 多轮工具循环的最终产出：可能是正常结果、取消，或异常。
     * 三种输出方式统一走 {@link ToolLoopFinisher}，无论是同步结束，还是"信息获取型工具"
     * 经玩家确认后异步续跑结束，调用方的收尾逻辑都只需要写一次。
     */
    static final class ToolLoopOutcome {
        final ToolLoopResult result; // 正常结束时非 null
        final boolean cancelled;
        final Exception error; // 非 null 表示异步续跑过程中抛出了异常

        private ToolLoopOutcome(ToolLoopResult result, boolean cancelled, Exception error) {
            this.result = result;
            this.cancelled = cancelled;
            this.error = error;
        }

        static ToolLoopOutcome of(ToolLoopResult result) { return new ToolLoopOutcome(result, false, null); }
        static ToolLoopOutcome cancelled() { return new ToolLoopOutcome(null, true, null); }
        static ToolLoopOutcome failed(Exception e) { return new ToolLoopOutcome(null, false, e); }
    }

    /** 多轮工具循环的最终收尾回调：无论同步结束还是异步续跑结束，都通过它投递结果。 */
    interface ToolLoopFinisher {
        void finish(ToolLoopOutcome outcome);
    }

    /**
     * 运行多轮工具调用循环（agentic loop）。
     *
     * 每一轮：检查当前 AI 回复里是否请求了工具（FETCH / SEARCH / ACTION / BLUEPRINT / QUERY_REGION）。
     * - 若请求了工具且尚未达到 max_tool_rounds 上限：执行工具，把执行结果作为一段反馈文本回喂给 AI，
     *   再次调用 AI 得到下一轮回复，轮数 +1，继续循环。
     * - 若没有请求工具，或已达到上限：结束循环，把最后一轮回复通过 finisher 投递。
     *
     * 当"执行前需确认"开关开启时：
     * - 只读的信息获取型工具（联网搜索/抓取网页/地形查询）确认后会自动把结果回喂给 AI 并继续本次循环
     *   （对玩家来说体验等同于原来的自动执行，只是多了一步允许/拒绝）。
     * - 会改变游戏状态的操作型工具（ACTION/BLUEPRINT，如放置方块、建造蓝图等）确认后不会自动继续推演，
     *   只会把执行结果单独发到聊天框，循环到此结束，是否继续对话由玩家决定。
     *
     * max_tool_rounds = 0 时不进入循环，直接返回首轮回复（等价于旧的单层行为，联网工具仍走首轮里的一次处理）。
     * 中间轮次调用 AI 时不再携带截图（base64 恒为空）。
     *
     * @param initialResponse 首轮 AI 回复
     * @param initiallyStreamed 首轮是否为流式产生
     * @param player 玩家
     * @param server 服务器
     * @param streaming 是否使用流式输出
     * @param notifier 进度提示回调（可为 null）
     * @param finisher 最终结果收尾回调（同步结束或异步确认续跑结束都会调用一次，且只调用一次）
     */
    private void runToolLoop(String initialResponse, boolean initiallyStreamed,
                              ServerPlayerEntity player, net.minecraft.server.MinecraftServer server,
                              boolean streaming, ToolLoopNotifier notifier, ToolLoopFinisher finisher) {
        runToolLoop(initialResponse, initiallyStreamed, player, server, streaming, notifier, finisher, 0);
    }

    /**
     * @param startRound 循环起始已完成轮数（首次调用为 0；由确认流程续跑时传入
     *                    "确认前已完成的轮数"，避免每次经过确认续跑都把计数器重置回 0，
     *                    导致 max_tool_rounds 上限在开启"执行前需确认"时形同虚设）。
     */
    private void runToolLoop(String initialResponse, boolean initiallyStreamed,
                              ServerPlayerEntity player, net.minecraft.server.MinecraftServer server,
                              boolean streaming, ToolLoopNotifier notifier, ToolLoopFinisher finisher,
                              int startRound) {
        try {
            runToolLoopInternal(initialResponse, initiallyStreamed, player, server, streaming, notifier, finisher, startRound);
        } catch (Exception e) {
            finisher.finish(ToolLoopOutcome.failed(e));
        }
    }

    private void runToolLoopInternal(String initialResponse, boolean initiallyStreamed,
                                      ServerPlayerEntity player, net.minecraft.server.MinecraftServer server,
                                      boolean streaming, ToolLoopNotifier notifier, ToolLoopFinisher finisher,
                                      int startRound) throws Exception {
        String response = initialResponse;
        boolean streamedLastRound = initiallyStreamed;
        int maxRounds = CONFIG.getMaxToolRounds();

        // round 从 startRound 开始计数已执行的工具轮数（续跑场景需要延续之前的计数，
        // 否则每次经过确认流程续跑都会重置为 0，导致轮数上限失效）
        int round = startRound;
        while (true) {
            // 非确认路径下（confirm_before_execute_enabled 关闭）本轮 [CAMERA_SHOT] 拍到的图片
            // 暂存在这里，本轮末尾统一调用 callKimiApi/callKimiApiStreaming 时随反馈一起带上。
            // 每轮开始时重置，避免把上一轮的图片错误地带到本轮（确认路径不用这个变量，
            // 那条路径通过 resumeToolLoopAfterConfirmedInfoTool 的 base64Image 重载直接传递）。
            String pendingCameraShotImage = "";
            if (cancelRequested) { finisher.finish(ToolLoopOutcome.cancelled()); return; }

            // 达到上限（maxRounds=0 时立刻退出循环，不执行任何工具回喂）后，
            // 若本轮仍请求了工具，则只做最终展示处理（联网标签会被清理，游戏操作在调用端统一执行）。
            boolean canDoMoreRounds = maxRounds > 0 && round < maxRounds;

            // 检测本轮请求了哪种工具
            String fetchUrl = extractFetchUrl(response);
            String searchQuery = extractSearchQuery(response);
            boolean webSearchAvailable = searchQuery != null && CONFIG.isWebSearchEnabled()
                    && CONFIG.getTavilyApiKey() != null && !CONFIG.getTavilyApiKey().isEmpty();
            boolean hasGameAction = AICommandExecutor.containsGameActionTags(response);
            String regionQuerySpec = AICommandExecutor.extractQueryRegionSpec(response);
            // 运行时二次防御：即使 AI 因记忆/越狱等原因仍输出了 [CAMERA_SHOT]，只要玩家关闭了
            // "允许 AI 使用摄像机截图"开关，这里也直接忽略该标签，不发起任何拍照请求。
            String cameraShotSpec = CONFIG.isCameraShotEnabled() ? AICommandExecutor.extractCameraShotSpec(response) : null;
            List<String> knowledgeDocNames = extractKnowledgeDocNames(response);
            boolean knowledgeAvailable = !knowledgeDocNames.isEmpty() && CONFIG.isRagEnabled();
            boolean knowledgeTreeRequested = containsKnowledgeTreeTag(response) && CONFIG.isRagEnabled();
            String knowledgeFilePath = extractKnowledgeFilePath(response);
            boolean knowledgeFileRequested = knowledgeFilePath != null && CONFIG.isRagEnabled();

            boolean requestedTool = fetchUrl != null || webSearchAvailable || hasGameAction
                    || regionQuerySpec != null || cameraShotSpec != null || knowledgeAvailable
                    || knowledgeTreeRequested || knowledgeFileRequested;

            if (!requestedTool || !canDoMoreRounds) {
                // 没有可执行的工具，或已达上限：先清理联网标签（FETCH/SEARCH/QUERY_REGION），
                // 再执行最后一轮回复里可能残留的 ACTION/BLUEPRINT（若有），然后结束循环。
                // 这里即便触发确认，确认完成后也只是把结果展示出来，不再开启新一轮循环（已到终点）。
                String cleanedResponse = stripWebTags(response);
                final boolean finalStreamedLastRound = streamedLastRound;
                if (hasGameAction) {
                    java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                    final AICommandExecutor.ProcessResult[] prHolder = new AICommandExecutor.ProcessResult[1];
                    server.execute(() -> {
                        try {
                            prHolder[0] = AICommandExecutor.process(cleanedResponse, player, toolFeedback -> {
                                // 已到终点：确认完成后只展示"已到终点"文本本身不含指令标签，
                                // 真正的执行结果已经在 toolFeedback 里，直接作为结果块展示。
                                finisher.finish(ToolLoopOutcome.of(new ToolLoopResult(
                                        cleanedResponse.replaceAll("\\[ACTION\\].*?\\[/ACTION\\]", "").trim()
                                                + (toolFeedback.isBlank() ? "" : "\n" + I18n.tr("cmd.result.header") + "\n" + toolFeedback.trim()),
                                        cleanedResponse, toolFeedback, finalStreamedLastRound)));
                            });
                        } catch (Exception e) {
                            LOGGER.error("多轮循环终点执行游戏操作失败", e);
                            prHolder[0] = null;
                        } finally {
                            latch.countDown();
                        }
                    });
                    try {
                        latch.await();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        finisher.finish(ToolLoopOutcome.cancelled());
                        return;
                    }
                    if (cancelRequested) { finisher.finish(ToolLoopOutcome.cancelled()); return; }
                    if (prHolder[0] != null && prHolder[0].hasPendingConfirmation) {
                        // 挂起等待确认：先把"等待确认"提示展示出来，真正的展示在上面的续跑回调里发生
                        String pendingText = prHolder[0].resultBlock != null ? prHolder[0].resultBlock : "";
                        finisher.finish(ToolLoopOutcome.of(new ToolLoopResult(pendingText, false)));
                        return;
                    }
                    // 未触发确认（开关关闭，或本轮没有需要确认的操作）：沿用原有同步展示路径
                    if (prHolder[0] != null) {
                        finisher.finish(ToolLoopOutcome.of(new ToolLoopResult(
                                prHolder[0].fullText, prHolder[0].cleanText, prHolder[0].resultBlock, finalStreamedLastRound)));
                    } else {
                        finisher.finish(ToolLoopOutcome.of(new ToolLoopResult(cleanedResponse, finalStreamedLastRound)));
                    }
                    return;
                }
                finisher.finish(ToolLoopOutcome.of(new ToolLoopResult(cleanedResponse, streamedLastRound)));
                return;
            }

            // 组装本轮工具执行的反馈文本，回喂给 AI
            StringBuilder feedback = new StringBuilder();

            // 1) 抓取网页（优先于搜索，保持与旧逻辑一致）
            if (fetchUrl != null) {
                final String url = fetchUrl;
                if (CONFIG.isConfirmBeforeExecuteEnabled()) {
                    String summary = I18n.tr("confirm.summary.fetch", url);
                    final int roundForResume = round;
                    final boolean lastAllowedForResume = !(maxRounds > 0 && round + 1 < maxRounds);
                    PendingActionConfirmation.request(player, summary, () -> {
                        // 抓取网页 + 续跑时的 AI 调用都是网络阻塞操作，绝不能在确认包接收器切过来的
                        // 服务端主线程上同步执行，否则会卡住整个服务器 tick。丢到独立线程异步处理。
                        CompletableFuture.runAsync(() -> {
                            String pageContent = webFetchService.fetch(url);
                            String toolFeedback = pageContent != null
                                    ? "以下是网页 " + url + " 的内容:\n\n" + pageContent + "\n\n"
                                    : I18n.tr("server.fetch_failed") + "\n\n";
                            debugPrintToolResult(player, server, I18n.tr("debug.tool.fetch"),
                                    pageContent != null ? pageContent : I18n.tr("server.fetch_failed"));
                            resumeToolLoopAfterConfirmedInfoTool(toolFeedback, roundForResume, lastAllowedForResume,
                                    player, server, streaming, notifier, finisher);
                        });
                    }, buildInfoToolRejectResume(I18n.tr("debug.tool.fetch"), roundForResume, lastAllowedForResume,
                            server, streaming, notifier, finisher));
                    String pendingText = I18n.tr("confirm.pending", summary, PendingActionConfirmation.TIMEOUT_SECONDS);
                    finisher.finish(ToolLoopOutcome.of(new ToolLoopResult(pendingText, false)));
                    return;
                }
                if (notifier != null) notifier.notify("\n\n§7" + I18n.tr("server.fetching"));
                server.execute(() -> player.sendMessage(Text.literal(I18n.tr("server.ai.fetching_page", url)), false));
                String pageContent = webFetchService.fetch(url);
                if (cancelRequested) { finisher.finish(ToolLoopOutcome.cancelled()); return; }
                if (pageContent != null) {
                    if (notifier != null) notifier.notify("\n§7" + I18n.tr("server.fetch_done") + "\n\n");
                    feedback.append("以下是网页 ").append(url).append(" 的内容:\n\n")
                            .append(pageContent).append("\n\n");
                    debugPrintToolResult(player, server, I18n.tr("debug.tool.fetch"), pageContent);
                } else {
                    feedback.append(I18n.tr("server.fetch_failed")).append("\n\n");
                    debugPrintToolResult(player, server, I18n.tr("debug.tool.fetch"), I18n.tr("server.fetch_failed"));
                }
            } else if (webSearchAvailable) {
                // 2) 联网搜索
                final String query = searchQuery;
                if (CONFIG.isConfirmBeforeExecuteEnabled()) {
                    String summary = I18n.tr("confirm.summary.web_search", query);
                    final int roundForResume = round;
                    final boolean lastAllowedForResume = !(maxRounds > 0 && round + 1 < maxRounds);
                    PendingActionConfirmation.request(player, summary, () -> {
                        // 联网搜索 + 续跑时的 AI 调用都是网络阻塞操作，丢到独立线程异步处理，避免卡主线程。
                        CompletableFuture.runAsync(() -> {
                            String searchResults = webSearchService.search(query, CONFIG.getTavilyApiKey());
                            String toolFeedback = searchResults != null
                                    ? "以下是联网搜索「" + query + "」的结果:\n\n" + searchResults + "\n\n"
                                    : I18n.tr("server.search_failed") + "\n\n";
                            debugPrintToolResult(player, server, I18n.tr("debug.tool.search"),
                                    searchResults != null ? searchResults : I18n.tr("server.search_failed"));
                            resumeToolLoopAfterConfirmedInfoTool(toolFeedback, roundForResume, lastAllowedForResume,
                                    player, server, streaming, notifier, finisher);
                        });
                    }, buildInfoToolRejectResume(I18n.tr("debug.tool.search"), roundForResume, lastAllowedForResume,
                            server, streaming, notifier, finisher));
                    String pendingText = I18n.tr("confirm.pending", summary, PendingActionConfirmation.TIMEOUT_SECONDS);
                    finisher.finish(ToolLoopOutcome.of(new ToolLoopResult(pendingText, false)));
                    return;
                }
                if (notifier != null) notifier.notify("\n\n§7" + I18n.tr("server.searching"));
                server.execute(() -> player.sendMessage(Text.literal(I18n.tr("server.ai.searching_query", query)), false));
                String searchResults = webSearchService.search(query, CONFIG.getTavilyApiKey());
                if (cancelRequested) { finisher.finish(ToolLoopOutcome.cancelled()); return; }
                if (searchResults != null) {
                    if (notifier != null) notifier.notify("\n§7" + I18n.tr("server.search_done") + "\n\n");
                    feedback.append("以下是联网搜索「").append(query).append("」的结果:\n\n")
                            .append(searchResults).append("\n\n");
                    debugPrintToolResult(player, server, I18n.tr("debug.tool.search"), searchResults);
                } else {
                    feedback.append(I18n.tr("server.search_failed")).append("\n\n");
                    debugPrintToolResult(player, server, I18n.tr("debug.tool.search"), I18n.tr("server.search_failed"));
                }
            }

            // 3) 执行游戏操作（ACTION / BLUEPRINT），把执行结果回喂给 AI。
            // 这类操作会改变游戏状态。开启确认时：本轮所有需确认的操作合并成一批一次性发给玩家，
            // 全部处理完（无论接受/拒绝）后自动把结果续跑回喂给 AI，避免漏工具、避免对话卡死。
            if (hasGameAction) {
                final String[] resultHolder = new String[1];
                final boolean[] pendingHolder = new boolean[1];
                final String actionResponse = response;
                final int roundForResume = round;
                final boolean lastAllowedForResume = !(maxRounds > 0 && round + 1 < maxRounds);
                // 方块放置需在主线程执行
                final boolean[] commandSuggestionHolder = new boolean[1];
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                server.execute(() -> {
                    try {
                        AICommandExecutor.ProcessResult pr = AICommandExecutor.process(actionResponse, player,
                            toolFeedback -> {
                                // 批次内所有操作均已确认完毕：网络调用（续跑 AI）不能卡在这个回调所在的
                                // 服务端主线程（由确认包接收器 server.execute 切来）上，切到独立线程异步处理。
                                CompletableFuture.runAsync(() -> resumeToolLoopAfterConfirmedInfoTool(
                                        toolFeedback, roundForResume, lastAllowedForResume,
                                        player, server, streaming, notifier, finisher));
                            },
                            commandFeedback -> {
                                // execute_command 建议的命令被玩家执行（或超时未执行）后触发：
                                // 该回调由命令执行/超时线程（服务端主线程或超时线程）调用，续跑要调 AI（网络阻塞），
                                // 切到独立线程异步处理，避免卡住主线程 tick。
                                CompletableFuture.runAsync(() -> resumeToolLoopAfterConfirmedInfoTool(
                                        commandFeedback, roundForResume, lastAllowedForResume,
                                        player, server, streaming, notifier, finisher));
                            });
                        resultHolder[0] = pr.resultBlock;
                        pendingHolder[0] = pr.hasPendingConfirmation;
                        commandSuggestionHolder[0] = pr.hasPendingCommandSuggestion;
                    } catch (Exception e) {
                        LOGGER.error("多轮循环中执行游戏操作失败", e);
                        resultHolder[0] = I18n.tr("cmd.action.failed", e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
                try {
                    latch.await();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    finisher.finish(ToolLoopOutcome.cancelled());
                    return;
                }
                if (cancelRequested) { finisher.finish(ToolLoopOutcome.cancelled()); return; }

                // 有操作正在等待玩家点击 [是]/[否] 确认，或有 execute_command 建议的命令正在等待玩家执行：
                // 都必须在这里停止循环，不能把"等待确认/等待执行"这句提示当作已完成的结果继续喂给 AI 推演。
                // 真正的续跑会在玩家确认完毕、或玩家执行了建议命令（拿到反馈）后，由上面的回调自动触发。
                if (pendingHolder[0] || commandSuggestionHolder[0]) {
                    String pendingText = resultHolder[0] != null ? resultHolder[0] : "";
                    finisher.finish(ToolLoopOutcome.of(new ToolLoopResult(stripWebTags(pendingText), false)));
                    return;
                }

                String actionResult = resultHolder[0];
                if (actionResult != null && !actionResult.isEmpty()) {
                    feedback.append(I18n.tr("server.toolloop.action_result")).append("\n")
                            .append(stripColorCodes(actionResult)).append("\n\n");
                    debugPrintToolResult(player, server, I18n.tr("debug.tool.game_action"), actionResult);
                }
            }

            // 4) 地形查询（QUERY_REGION）：在主线程扫描世界，把地形 txt 回喂给 AI。
            // 这是只读的信息获取型工具，确认后会自动把结果续跑回喂给 AI（见下方 else 分支之外的确认逻辑）。
            if (regionQuerySpec != null) {
                final String spec = regionQuerySpec;

                // 地形查询也是 AI 使用 mod 自定义功能，需要按同一开关确认。开启时先发确认消息挂起，
                // 不执行查询也不继续回喂 AI，直到玩家点击 [是]/[否]；确认后自动续跑循环。
                if (CONFIG.isConfirmBeforeExecuteEnabled()) {
                    final boolean[] confirmedHolder = new boolean[1];
                    final String[] summaryHolder = new String[1];
                    final int roundForResume = round;
                    final boolean lastAllowedForResume = !(maxRounds > 0 && round + 1 < maxRounds);
                    java.util.concurrent.CountDownLatch confirmLatch = new java.util.concurrent.CountDownLatch(1);
                    server.execute(() -> {
                        try {
                            String summary = I18n.tr("confirm.summary.query_region", spec);
                            summaryHolder[0] = summary;
                            PendingActionConfirmation.request(player, summary, () -> {
                                // executeQueryRegion 读取世界方块，此刻在主线程（确认包接收器切来）上
                                // 同步执行是安全且快速的；但续跑要调用 AI（网络阻塞），必须切到独立线程，
                                // 避免卡住服务器主线程 tick。
                                String result = AICommandExecutor.executeQueryRegion(spec, player, player.getServerWorld());
                                String toolFeedback = "以下是你查询的区域 [" + spec + "] 的现有地形：\n\n" + result + "\n\n";
                                debugPrintToolResult(player, server, I18n.tr("debug.tool.query_region"), result);
                                CompletableFuture.runAsync(() -> resumeToolLoopAfterConfirmedInfoTool(
                                        toolFeedback, roundForResume, lastAllowedForResume,
                                        player, server, streaming, notifier, finisher));
                            }, buildInfoToolRejectResume(I18n.tr("debug.tool.query_region"), roundForResume,
                                    lastAllowedForResume, server, streaming, notifier, finisher));
                            confirmedHolder[0] = true;
                        } catch (Exception e) {
                            LOGGER.error("发送地形查询确认请求失败", e);
                        } finally {
                            confirmLatch.countDown();
                        }
                    });
                    try {
                        confirmLatch.await();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        finisher.finish(ToolLoopOutcome.cancelled());
                        return;
                    }
                    if (cancelRequested) { finisher.finish(ToolLoopOutcome.cancelled()); return; }
                    if (confirmedHolder[0]) {
                        String pendingText = I18n.tr("confirm.pending", summaryHolder[0], PendingActionConfirmation.TIMEOUT_SECONDS);
                        finisher.finish(ToolLoopOutcome.of(new ToolLoopResult(pendingText, false)));
                        return;
                    }
                    // 发送确认请求本身失败时，跳过本次查询，避免整轮卡死
                } else {
                    if (notifier != null) notifier.notify("\n\n§7" + I18n.tr("server.querying_region"));
                    final String[] regionHolder = new String[1];
                    java.util.concurrent.CountDownLatch regionLatch = new java.util.concurrent.CountDownLatch(1);
                    server.execute(() -> {
                        try {
                            regionHolder[0] = AICommandExecutor.executeQueryRegion(spec, player, player.getServerWorld());
                        } catch (Exception e) {
                            LOGGER.error("地形查询失败", e);
                            regionHolder[0] = "ERROR: 地形查询执行失败: " + e.getMessage();
                        } finally {
                            regionLatch.countDown();
                        }
                    });
                    try {
                        regionLatch.await();
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        finisher.finish(ToolLoopOutcome.cancelled());
                        return;
                    }
                    if (cancelRequested) { finisher.finish(ToolLoopOutcome.cancelled()); return; }
                    String regionResult = regionHolder[0];
                    if (notifier != null) notifier.notify("\n§7" + I18n.tr("server.query_region_done") + "\n\n");
                    feedback.append("以下是你查询的区域 [").append(spec).append("] 的现有地形：\n\n")
                            .append(regionResult).append("\n\n");
                    debugPrintToolResult(player, server, I18n.tr("debug.tool.query_region"), regionResult);
                }
            }

            // 4.5) 摄像机截图（[CAMERA_SHOT]）：给玩家客户端发包、等待截图回包，把结果作为
            // 图片随本轮反馈一起带给 AI，让它能"看到"刚拍的画面并继续对话（自查/纠错）。
            // 这个动作会短暂借用玩家视角，因此和 QUERY_REGION 一样按同一开关走单条确认。
            if (cameraShotSpec != null) {
                final String spec = cameraShotSpec;
                double[] parsed = AICommandExecutor.parseCameraShotSpec(spec);
                if (parsed == null) {
                    // 格式错误：不发起截图，直接把错误提示当文本反馈回喂给 AI，本轮循环正常继续
                    feedback.append("[CAMERA_SHOT] 格式错误: \"").append(spec)
                            .append("\"，应为 5 个逗号分隔的数字 x,y,z,yaw,pitch\n\n");
                } else {
                    final double camX = parsed[0], camY = parsed[1], camZ = parsed[2];
                    final float camYaw = (float) parsed[3], camPitch = (float) parsed[4];

                    if (CONFIG.isConfirmBeforeExecuteEnabled()) {
                        final String[] summaryHolder = new String[1];
                        final boolean[] confirmedHolder = new boolean[1];
                        final int roundForResume = round;
                        final boolean lastAllowedForResume = !(maxRounds > 0 && round + 1 < maxRounds);
                        java.util.concurrent.CountDownLatch confirmLatch = new java.util.concurrent.CountDownLatch(1);
                        server.execute(() -> {
                            try {
                                String summary = I18n.tr("confirm.summary.camera_shot", camX, camY, camZ, camYaw, camPitch);
                                summaryHolder[0] = summary;
                                PendingActionConfirmation.request(player, summary, () -> {
                                    // 拍照+读图是阻塞操作，续跑还要调用 AI（网络阻塞），
                                    // 都不能卡在确认包接收器切来的服务端主线程上，切到独立线程处理。
                                    CompletableFuture.runAsync(() -> {
                                        String path = requestCameraShotAndAwait(player, camX, camY, camZ, camYaw, camPitch);
                                        String base64Image = readImageAsBase64WithRetry(path);
                                        String toolFeedback = !base64Image.isEmpty()
                                                ? "已在坐标 (" + camX + ", " + camY + ", " + camZ + ") 朝向 (yaw=" + camYaw + ", pitch=" + camPitch
                                                        + ") 拍摄了一张截图，图片已附上，请观察画面内容。\n\n"
                                                : "ERROR: 摄像机截图失败或超时，请检查坐标是否在玩家客户端已加载的区域内，或稍后重试。\n\n";
                                        debugPrintToolResult(player, server, I18n.tr("debug.tool.camera_shot"),
                                                !base64Image.isEmpty() ? "[截图成功]" : "[截图失败]");
                                        resumeToolLoopAfterConfirmedInfoTool(toolFeedback, base64Image, roundForResume,
                                                lastAllowedForResume, player, server, streaming, notifier, finisher);
                                    });
                                }, buildInfoToolRejectResume(I18n.tr("debug.tool.camera_shot"), roundForResume,
                                        lastAllowedForResume, server, streaming, notifier, finisher));
                                confirmedHolder[0] = true;
                            } catch (Exception e) {
                                LOGGER.error("发送摄像机截图确认请求失败", e);
                            } finally {
                                confirmLatch.countDown();
                            }
                        });
                        try {
                            confirmLatch.await();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            finisher.finish(ToolLoopOutcome.cancelled());
                            return;
                        }
                        if (cancelRequested) { finisher.finish(ToolLoopOutcome.cancelled()); return; }
                        if (confirmedHolder[0]) {
                            String pendingText = I18n.tr("confirm.pending", summaryHolder[0], PendingActionConfirmation.TIMEOUT_SECONDS);
                            finisher.finish(ToolLoopOutcome.of(new ToolLoopResult(pendingText, false)));
                            return;
                        }
                        // 发送确认请求本身失败时，跳过本次拍照，避免整轮卡死
                    } else {
                        if (notifier != null) notifier.notify("\n\n§7" + I18n.tr("server.camera_shot.taking"));
                        String path = requestCameraShotAndAwait(player, camX, camY, camZ, camYaw, camPitch);
                        if (cancelRequested) { finisher.finish(ToolLoopOutcome.cancelled()); return; }
                        String base64Image = readImageAsBase64WithRetry(path);
                        if (notifier != null) notifier.notify("\n§7" + I18n.tr("server.camera_shot.done") + "\n\n");
                        if (!base64Image.isEmpty()) {
                            // 图片本身不能塞进纯文本反馈里——记录下来，本轮结束后随 reprompt 一起发送
                            pendingCameraShotImage = base64Image;
                            feedback.append("已在坐标 (").append(camX).append(", ").append(camY).append(", ").append(camZ)
                                    .append(") 朝向 (yaw=").append(camYaw).append(", pitch=").append(camPitch)
                                    .append(") 拍摄了一张截图，图片已附上，请观察画面内容。\n\n");
                        } else {
                            feedback.append("ERROR: 摄像机截图失败或超时，请检查坐标是否在玩家客户端已加载的区域内，或稍后重试。\n\n");
                        }
                        debugPrintToolResult(player, server, I18n.tr("debug.tool.camera_shot"),
                                !base64Image.isEmpty() ? "[截图成功]" : "[截图失败]");
                    }
                }
            }

            // 5) 查阅知识库（[KNOWLEDGE]）：本地读取 Markdown 文档正文，只读操作，速度快，
            // 不涉及网络或游戏世界状态，因此不走确认流程，直接在当前线程处理。
            if (knowledgeAvailable) {
                if (notifier != null) notifier.notify("\n\n§7" + I18n.tr("server.knowledge.querying"));
                String knowledgeContent = knowledgeBase.retrieve(
                        knowledgeDocNames, CONFIG.getRagMaxDocs(), CONFIG.getRagMaxChars());
                if (knowledgeContent != null) {
                    if (notifier != null) notifier.notify("\n§7" + I18n.tr("server.knowledge.done") + "\n\n");
                    feedback.append("以下是知识库中「").append(String.join(", ", knowledgeDocNames))
                            .append("」的内容:\n\n").append(knowledgeContent).append("\n\n");
                    debugPrintToolResult(player, server, I18n.tr("debug.tool.knowledge"), knowledgeContent);
                } else {
                    feedback.append(I18n.tr("server.knowledge.not_found")).append("\n\n");
                    debugPrintToolResult(player, server, I18n.tr("debug.tool.knowledge"), I18n.tr("server.knowledge.not_found"));
                }
            }

            // 6) 查看知识库目录结构（[KNOWLEDGE_TREE]）：本地读取文件树，只读操作，不涉及网络或游戏世界状态，
            // 因此不走确认流程，直接在当前线程处理。用于让 AI 先看清 knowledge/ 下的完整文件/文件夹结构，
            // 再用 [KNOWLEDGE_FILE] 点名读取具体文件正文。
            if (knowledgeTreeRequested) {
                if (notifier != null) notifier.notify("\n\n§7" + I18n.tr("server.knowledge_tree.querying"));
                String treeText = knowledgeBase.buildTreeText();
                if (treeText != null) {
                    if (notifier != null) notifier.notify("\n§7" + I18n.tr("server.knowledge_tree.done") + "\n\n");
                    feedback.append("以下是知识库目录结构:\n\n").append(treeText).append("\n\n");
                    debugPrintToolResult(player, server, I18n.tr("debug.tool.knowledge_tree"), treeText);
                } else {
                    feedback.append(I18n.tr("server.knowledge_tree.not_found")).append("\n\n");
                    debugPrintToolResult(player, server, I18n.tr("debug.tool.knowledge_tree"), I18n.tr("server.knowledge_tree.not_found"));
                }
            }

            // 7) 读取知识库中某个具体文件的正文（[KNOWLEDGE_FILE]）：本地读取任意文件原始文本，
            // 只读操作，不涉及网络或游戏世界状态，因此不走确认流程，直接在当前线程处理。
            if (knowledgeFileRequested) {
                final String filePath = knowledgeFilePath;
                if (notifier != null) notifier.notify("\n\n§7" + I18n.tr("server.knowledge_file.querying"));
                String fileContent = knowledgeBase.readFile(filePath, CONFIG.getRagMaxChars());
                if (notifier != null) notifier.notify("\n§7" + I18n.tr("server.knowledge_file.done") + "\n\n");
                feedback.append("以下是知识库文件「").append(filePath).append("」的内容:\n\n")
                        .append(fileContent).append("\n\n");
                debugPrintToolResult(player, server, I18n.tr("debug.tool.knowledge_file"), fileContent);
            }

            // 进入下一轮：把反馈回喂给 AI
            round++;
            boolean lastAllowedRound = !(maxRounds > 0 && round < maxRounds);
            String reprompt = feedback.toString().trim()
                    + "\n\n" + I18n.tr("server.toolloop.continue_hint");
            if (lastAllowedRound) {
                reprompt += "\n" + I18n.tr("server.toolloop.limit_hint");
            }

            if (notifier != null) {
                notifier.notify("\n§7" + I18n.tr("server.toolloop.round", round, maxRounds) + "\n\n");
            }

            // 中间轮次通常不携带截图；若本轮执行了 [CAMERA_SHOT]（非确认路径），
            // 把拍到的图片带上，让 AI 真正"看到"刚拍的画面。
            if (streaming) {
                response = callKimiApiStreaming(reprompt, pendingCameraShotImage, player, server);
                streamedLastRound = true;
            } else {
                response = callKimiApi(reprompt, pendingCameraShotImage);
                streamedLastRound = false;
            }
        }
    }

    /**
     * 为信息获取型工具（联网搜索/抓取网页/地形查询/摄像机截图）构造"被玩家拒绝或超时拒绝"时的回调。
     *
     * <p>没有这个回调时，这些工具的 {@code request} 只传了 onAccept，拒绝走默认空回调 → 对话不续跑、
     * AI 卡在等待中（连拍会直接中断）。这里让拒绝也续跑一次：把"用户拒绝了本次【XX】操作"喂回 AI，
     * 让它据此继续（换方案、拍下一张或直接答复），而不是无响应。
     *
     * <p>onReject 由 {@code resolve}（/ai reject 命令线程）或 {@code onTimeout}（超时线程 server.execute 主线程）
     * 调用，续跑要调 AI（网络阻塞），因此和 onAccept 一样必须切到独立线程，绝不能卡在调用线程上。
     *
     * @param toolName          展示给 AI 的工具名（如"摄像机截图"），拼进拒绝反馈文本
     * @param roundForResume    当前轮次，续跑时 +1 延续计数
     * @param lastAllowedForResume 是否为最后一轮（续跑时附加轮数上限提示）
     */
    private java.util.function.Consumer<ServerPlayerEntity> buildInfoToolRejectResume(
            String toolName, int roundForResume, boolean lastAllowedForResume,
            net.minecraft.server.MinecraftServer server, boolean streaming,
            ToolLoopNotifier notifier, ToolLoopFinisher finisher) {
        return player -> CompletableFuture.runAsync(() -> {
            String toolFeedback = I18n.tr("server.toolloop.user_rejected", toolName) + "\n\n";
            resumeToolLoopAfterConfirmedInfoTool(toolFeedback, roundForResume, lastAllowedForResume,
                    player, server, streaming, notifier, finisher);
        });
    }

    /**
     * 信息获取型工具（联网搜索/抓取网页/地形查询）经玩家确认后的续跑逻辑：
     * 把工具结果组装成反馈文本，重新调用 AI，再递归进入 {@link #runToolLoopInternal} 处理后续可能的工具调用。
     * 该方法运行在确认包接收器 server.execute 切来的服务端主线程上，调用 AI 是网络阻塞操作，
     * 但这本身就是玩家主动点击触发的一次性操作，阻塞时长可接受（与 /ai 命令同步等待 AI 回复的体验一致）。
     *
     * <p>等价于 {@code resumeToolLoopAfterConfirmedInfoTool(toolFeedback, "", ...)}，不带图片续跑。
     */
    private void resumeToolLoopAfterConfirmedInfoTool(String toolFeedback, int roundBeforeResume, boolean lastAllowedRound,
                                                        ServerPlayerEntity player, net.minecraft.server.MinecraftServer server,
                                                        boolean streaming, ToolLoopNotifier notifier, ToolLoopFinisher finisher) {
        resumeToolLoopAfterConfirmedInfoTool(toolFeedback, "", roundBeforeResume, lastAllowedRound,
                player, server, streaming, notifier, finisher);
    }

    /**
     * 同上，额外支持把一张图片（base64）随本轮反馈一起带给 AI——
     * 目前仅 {@code [CAMERA_SHOT]} 摄像机截图工具会用到：AI 建造后拍照自查，
     * 拍到的图片需要在这一轮续跑时真正传给视觉模型，而不是像其他信息获取型工具那样只传文本。
     *
     * @param base64Image 要随本轮续跑一起发送的图片（base64 编码），无图时传空字符串
     */
    private void resumeToolLoopAfterConfirmedInfoTool(String toolFeedback, String base64Image,
                                                        int roundBeforeResume, boolean lastAllowedRound,
                                                        ServerPlayerEntity player, net.minecraft.server.MinecraftServer server,
                                                        boolean streaming, ToolLoopNotifier notifier, ToolLoopFinisher finisher) {
        int round = roundBeforeResume + 1;
        String reprompt = toolFeedback.trim() + "\n\n" + I18n.tr("server.toolloop.continue_hint");
        if (lastAllowedRound) {
            reprompt += "\n" + I18n.tr("server.toolloop.limit_hint");
        }
        if (notifier != null) {
            notifier.notify("\n§7" + I18n.tr("server.toolloop.round", round, CONFIG.getMaxToolRounds()) + "\n\n");
        }
        try {
            String response;
            boolean streamedThisRound;
            if (streaming) {
                response = callKimiApiStreaming(reprompt, base64Image, player, server);
                streamedThisRound = true;
            } else {
                response = callKimiApi(reprompt, base64Image);
                streamedThisRound = false;
            }
            if (cancelRequested) { finisher.finish(ToolLoopOutcome.cancelled()); return; }
            // 传入 round 作为起始轮数，延续确认前已完成的计数，避免轮数上限被重置
            runToolLoop(response, streamedThisRound, player, server, streaming, notifier, finisher, round);
        } catch (Exception e) {
            LOGGER.error("确认后续跑多轮工具循环失败", e);
            finisher.finish(ToolLoopOutcome.failed(e));
        }
    }

    /**
     * 调试用：把某次工具调用的返回值打印到玩家聊天框。
     * 仅当 debug-menu 里的「打印工具返回值到聊天框」开关开启时才发送。
     *
     * @param player   接收消息的玩家
     * @param server   服务器实例（聊天消息需在主线程发送）
     * @param toolName 工具名（如"联网搜索""抓取网页""游戏操作"），用于标注来源
     * @param result   工具返回值文本
     */
    private static void debugPrintToolResult(ServerPlayerEntity player,
                                             net.minecraft.server.MinecraftServer server,
                                             String toolName, String result) {
        if (!AICommandExecutor.isPrintToolResultsToChat()) return;
        if (result == null || result.isEmpty()) return;
        String header = "§8[" + I18n.tr("debug.print_tool_result.prefix") + "] §7"
                + I18n.tr("debug.print_tool_result.entry", toolName);
        String body = "§7" + stripColorCodes(result);
        server.execute(() -> {
            player.sendMessage(Text.literal(header), false);
            player.sendMessage(Text.literal(body), false);
        });
    }

    /** 清理联网/查询类工具标签（FETCH / SEARCH / QUERY_REGION / CAMERA_SHOT / KNOWLEDGE / KNOWLEDGE_TREE / KNOWLEDGE_FILE），保留 ACTION / BLUEPRINT 供最终展示。 */
    private static String stripWebTags(String response) {
        if (response == null) return "";
        String r = response.replaceAll("\\[FETCH\\].*?\\[/FETCH\\]", "").trim();
        r = r.replaceAll("\\[SEARCH\\].*?\\[/SEARCH\\]", "").trim();
        r = r.replaceAll("(?s)\\[QUERY_REGION\\].*?\\[/QUERY_REGION\\]", "").trim();
        r = r.replaceAll("(?s)\\[CAMERA_SHOT\\].*?\\[/CAMERA_SHOT\\]", "").trim();
        r = r.replaceAll("\\[KNOWLEDGE_TREE\\].*?\\[/KNOWLEDGE_TREE\\]", "").trim();
        r = r.replaceAll("\\[KNOWLEDGE_FILE\\].*?\\[/KNOWLEDGE_FILE\\]", "").trim();
        r = r.replaceAll("\\[KNOWLEDGE\\].*?\\[/KNOWLEDGE\\]", "").trim();
        return r;
    }

    /** 去掉 Minecraft 颜色代码（§x），使回喂给 AI 的文本干净。 */
    private static String stripColorCodes(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * 服务端读取引用的 txt 文件内容。
     */
    /**
     * 从导出请求 packet 中读取"被忽略的方块种类"列表。
     * 兼容旧客户端：若 buf 中没有该字段则返回空集合。
     * 格式：int count 后跟 count 个 String（方块 id，不含 minecraft: 前缀）。
     */
    private static java.util.Set<String> readIgnoredBlocks(PacketByteBuf buf) {
        java.util.Set<String> ignored = new java.util.HashSet<>();
        if (!buf.isReadable()) {
            return ignored;
        }
        int count = buf.readInt();
        for (int i = 0; i < count && buf.isReadable(); i++) {
            ignored.add(buf.readString());
        }
        return ignored;
    }

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
