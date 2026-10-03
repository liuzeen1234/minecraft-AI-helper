package com.example.helloworld;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MarkerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class HelloWorldClientMod implements ClientModInitializer {

    // 延迟截图用的状态
    private String pendingMessage = null;
    private int delayTicks = 0;

    // 摄像机截图（AI 工具循环的 [CAMERA_SHOT] 标签）用的延迟状态
    private double pendingCamX, pendingCamY, pendingCamZ;
    private float pendingCamYaw, pendingCamPitch;
    private boolean pendingCameraShot = false;
    private int cameraShotDelayTicks = 0;
    /**
     * 切镜头与真正截图之间需要错开至少一个 tick：Minecraft 的渲染循环是
     * "本帧先跑完待处理的 tick，再画这一帧"，setCameraEntity 只影响*下一次*渲染，
     * 若在切镜头的同一个 tick 回调里立刻读 framebuffer，读到的仍是上一帧（旧镜头）的画面。
     * 因此拆成两阶段：{@link #cameraShotDelayTicks} 倒数结束后先切镜头并置位此标记，
     * 下一个 tick（此时新镜头那一帧已经渲染完成）才真正截图、恢复镜头、发包。
     */
    private boolean awaitingCameraRenderTick = false;

    // 按键绑定：打开设置页面
    private static KeyBinding openSettingsKey;

    @Override
    public void onInitializeClient() {
        // 软依赖 debug_menu：若已安装，则把调试开关注册进其调试菜单
        // （语言切换开关已随“显示语言完全跟随游戏语言”的改动一并移除）。
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("debug-menu")) {
            try {
                DebugMenuIntegration.register();
            } catch (Throwable t) {
                // 防御：debug-menu 版本不兼容等意外情况不应影响 AI-helper 启动
                HelloWorldMod.LOGGER.warn("[debug-menu] 注册调试开关失败，已跳过", t);
            }
        }

        // 注册选区渲染器
        com.example.helloworld.selection.SelectionRenderer.register();

        // 注册按键绑定 (默认 K 键)
        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.helloworld.settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.helloworld"
        ));

        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.TAKE_SCREENSHOT_PACKET, (client, handler, buf, responseSender) -> {
            String message = buf.readString();
            // 收到截图指令后，等 2 个 tick 再截图（等聊天框关闭）
            client.execute(() -> {
                pendingMessage = message;
                delayTicks = 2;
            });
        });

        // 注册接收服务端摄像机截图请求（AI 工具循环的 [CAMERA_SHOT] 标签）：
        // 收到坐标+角度后，等 2 个 tick 再截图（等待区块/光照渲染稳定）。
        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.REQUEST_CAMERA_SHOT_PACKET, (client, handler, buf, responseSender) -> {
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            client.execute(() -> {
                pendingCamX = x;
                pendingCamY = y;
                pendingCamZ = z;
                pendingCamYaw = yaw;
                pendingCamPitch = pitch;
                pendingCameraShot = true;
                cameraShotDelayTicks = 2;
            });
        });

        // 注册接收服务端 NBT 导出结果通知
        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.EXPORT_NBT_RESULT_PACKET, (client, handler, buf, responseSender) -> {
            String resultMsg = buf.readString();
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal(resultMsg), false);
                }
            });
        });

        // 注册接收服务端 Litematica 导出结果通知
        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.EXPORT_LITEMATIC_RESULT_PACKET, (client, handler, buf, responseSender) -> {
            String resultMsg = buf.readString();
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal(resultMsg), false);
                }
            });
        });

        // 注册接收服务端 TXT 导出结果通知
        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.EXPORT_TXT_RESULT_PACKET, (client, handler, buf, responseSender) -> {
            String resultMsg = buf.readString();
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal(resultMsg), false);
                }
            });
        });

        // 注册接收 AI 聊天界面回复
        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.CHAT_SCREEN_RESPONSE_PACKET, (client, handler, buf, responseSender) -> {
            String response = buf.readString();
            client.execute(() -> {
                // 将回复添加到聊天界面历史
                AiChatScreen.receiveResponse(response);
                // 如果当前打开的是聊天界面，通知它刷新
                if (client.currentScreen instanceof AiChatScreen chatScreen) {
                    chatScreen.setWaitingDone();
                } else {
                    // 聊天界面已关闭，将 AI 回复显示到游戏内聊天框
                    if (client.player != null) {
                        // 如果是终止消息，只显示简短提示，不需要完整回复格式
                        if (response.equals(HelloWorldMod.THINKING_CANCELLED_SENTINEL)) {
                            client.player.sendMessage(Text.literal(HelloWorldMod.thinkingCancelledDisplay()), false);
                            return;
                        }
                        // 截取前200字符避免聊天框溢出，完整内容可在 AI 聊天界面查看
                        String displayResponse = response.length() > 200
                                ? response.substring(0, 200) + "..."
                                : response;
                        // 按换行分割，逐行发送到聊天框
                        String[] lines = displayResponse.split("\n");
                        client.player.sendMessage(Text.literal(I18n.tr("client.ai.reply")), false);
                        for (String line : lines) {
                            if (!line.trim().isEmpty()) {
                                client.player.sendMessage(Text.literal("§f" + line), false);
                            }
                        }
                        client.player.sendMessage(Text.literal(I18n.tr("client.ai.reply.hint")), false);
                    }
                }
            });
        });

        // 注册接收服务端建议的命令：只把命令预填到聊天输入框，绝不自动发送/执行，
        // 必须由玩家自己看到内容后手动按回车确认，真正的执行和权限检查完全走原版聊天系统。
        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.SUGGEST_COMMAND_PACKET, (client, handler, buf, responseSender) -> {
            String command = buf.readString();
            client.execute(() -> {
                if (client.player == null) return;
                // 若当前正打开 AI 聊天界面等自定义屏幕，先关闭，避免遮挡聊天输入框
                client.setScreen(new net.minecraft.client.gui.screen.ChatScreen(command));
            });
        });

        // 注册接收 AI 聊天界面流式增量回复
        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.CHAT_SCREEN_STREAM_PACKET, (client, handler, buf, responseSender) -> {
            String delta = buf.readString();
            client.execute(() -> {
                // 将增量内容追加到聊天界面（如果打开的话）
                if (client.currentScreen instanceof AiChatScreen chatScreen) {
                    HelloWorldMod.LOGGER.debug("[流式客户端] 收到流式包, 长度={}", delta.length());
                    chatScreen.appendStreamDelta(delta);
                }
                // 聊天界面未打开时静默丢弃（/ai 命令已通过 player.sendMessage 显示）
            });
        });

        // 每个客户端 tick 检查是否需要截图 & 刷新日志到聊天框
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 注：显示语言现在完全由 I18n.isEnglish() 实时读取当前游戏语言决定，
            // 不再需要在此处做“启动时重置一次”的逻辑（见 I18n.java 的说明）。

            // 按键打开设置页面
            while (openSettingsKey.wasPressed()) {
                client.setScreen(new ModSettingsScreen(client.currentScreen));
            }

            // 处理 AI 聊天界面的延迟截图
            AiChatScreen.tickScreenshot();

            if (pendingMessage != null && delayTicks > 0) {
                delayTicks--;
                if (delayTicks == 0) {
                    String message = pendingMessage;
                    pendingMessage = null;
                    doScreenshotAndSend(client, message);
                }
            }

            if (awaitingCameraRenderTick) {
                // 上一 tick 已切好镜头，这一 tick 开始时新镜头那一帧已经渲染完成，可以安全截图了。
                awaitingCameraRenderTick = false;
                captureAndRestoreCamera(client);
            } else if (pendingCameraShot && cameraShotDelayTicks > 0) {
                cameraShotDelayTicks--;
                if (cameraShotDelayTicks == 0) {
                    pendingCameraShot = false;
                    switchCameraForShot(client, pendingCamX, pendingCamY, pendingCamZ, pendingCamYaw, pendingCamPitch);
                }
            }
        });
    }

    /** 截图期间临时顶替玩家视角的摄像机实体；截图完成后销毁引用并把视角切回下面记录的原视角。 */
    private MarkerEntity activeCameraEntity = null;
    /** 切换摄像机之前的原视角实体（通常是玩家本体），截图完成后恢复。 */
    private Entity originalCameraEntity = null;

    /**
     * 第一阶段：把客户端摄像机切到一个不加入世界实体列表的 {@link MarkerEntity} 上
     * （只用作渲染用的位置/朝向锚点，不影响玩家本体的实际位置/不会被其他玩家看到瞬移）。
     * 真正的截图推迟到下一个 tick（见 {@link #captureAndRestoreCamera}），
     * 因为 {@code setCameraEntity} 只影响下一次渲染，本 tick 内读 framebuffer 仍是旧画面。
     */
    private void switchCameraForShot(MinecraftClient client, double x, double y, double z, float yaw, float pitch) {
        // 摄像机截图（[CAMERA_SHOT]）用的是 camera_shot_enabled 开关，而非 AI 聊天截图的 screenshot_enabled。
        // 服务端已按 camera_shot_enabled 决定是否发起拍照请求，客户端这里必须用同一个开关判断，
        // 否则会出现"服务端已请求拍照、客户端却因误判 screenshot_enabled=false 立即回空串"导致截图失败。
        boolean cameraShotEnabled = HelloWorldMod.getConfig().isCameraShotEnabled();
        if (!cameraShotEnabled || client.player == null || client.world == null) {
            PacketByteBuf responseBuf = PacketByteBufs.create();
            responseBuf.writeString("");
            ClientPlayNetworking.send(HelloWorldMod.CAMERA_SHOT_RESPONSE_PACKET, responseBuf);
            return;
        }

        MarkerEntity camEntity = new MarkerEntity(EntityType.MARKER, client.world);
        camEntity.setPosition(x, y, z);
        camEntity.setYaw(yaw);
        camEntity.setPitch(pitch);
        // 消除跨 tick 插值：不设置的话摄像机会从上一帧位置慢慢"飘"过来，
        // 下一帧截图就会截到过渡中的画面。
        camEntity.prevX = x;
        camEntity.prevY = y;
        camEntity.prevZ = z;
        camEntity.prevYaw = yaw;
        camEntity.prevPitch = pitch;
        camEntity.lastRenderX = x;
        camEntity.lastRenderY = y;
        camEntity.lastRenderZ = z;

        originalCameraEntity = client.getCameraEntity();
        activeCameraEntity = camEntity;
        client.setCameraEntity(camEntity);
        awaitingCameraRenderTick = true;
    }

    /**
     * 第二阶段：新镜头那一帧已经渲染完成，读取 framebuffer 存盘，
     * 随后立即把视角恢复为原视角（通常是玩家本体），并发包回传结果。
     */
    private void captureAndRestoreCamera(MinecraftClient client) {
        try {
            File screenshotDir = ModPaths.getScreenshotsDir().toFile();
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }
            File camShot = new File(screenshotDir, "ai_camera_shot.png");
            saveScaledScreenshot(client.getFramebuffer(), camShot);

            PacketByteBuf responseBuf = PacketByteBufs.create();
            responseBuf.writeString(camShot.getAbsolutePath());
            ClientPlayNetworking.send(HelloWorldMod.CAMERA_SHOT_RESPONSE_PACKET, responseBuf);
        } catch (Exception e) {
            HelloWorldMod.LOGGER.error("摄像机截图失败", e);
            PacketByteBuf responseBuf = PacketByteBufs.create();
            responseBuf.writeString("");
            ClientPlayNetworking.send(HelloWorldMod.CAMERA_SHOT_RESPONSE_PACKET, responseBuf);
        } finally {
            // 恢复视角为原视角（若原本就是玩家本体则等价于恢复原状）
            client.setCameraEntity(originalCameraEntity != null ? originalCameraEntity : client.player);
            activeCameraEntity = null;
            originalCameraEntity = null;
        }
    }

    private void doScreenshotAndSend(MinecraftClient client, String message) {
        boolean screenshotEnabled = HelloWorldMod.getConfig().isScreenshotEnabled();

        if (screenshotEnabled) {
            File screenshotDir = ModPaths.getScreenshotsDir().toFile();
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }
            ScreenshotRecorder.saveScreenshot(
                screenshotDir,
                client.getFramebuffer(),
                (text) -> client.inGameHud.getChatHud().addMessage(text)
            );

            File aiScreenshot = new File(screenshotDir, "ai_temp.png");
            saveScaledScreenshot(client.getFramebuffer(), aiScreenshot);

            PacketByteBuf responseBuf = PacketByteBufs.create();
            responseBuf.writeString(message);
            responseBuf.writeString(aiScreenshot.getAbsolutePath());
            ClientPlayNetworking.send(HelloWorldMod.SCREENSHOT_RESPONSE_PACKET, responseBuf);
        } else {
            // 截图关闭时，发送空路径
            PacketByteBuf responseBuf = PacketByteBufs.create();
            responseBuf.writeString(message);
            responseBuf.writeString("");
            ClientPlayNetworking.send(HelloWorldMod.SCREENSHOT_RESPONSE_PACKET, responseBuf);
        }
    }

    /**
     * 从 Framebuffer 读取像素，缩放到 512px 宽度后保存为 PNG。
     */
    private void saveScaledScreenshot(Framebuffer framebuffer, File outputFile) {
        try {
            int width = framebuffer.textureWidth;
            int height = framebuffer.textureHeight;

            IntBuffer pixelBuffer = BufferUtils.createIntBuffer(width * height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.getColorAttachment());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);

            int[] pixels = new int[width * height];
            pixelBuffer.get(pixels);

            // OpenGL 纹理上下翻转
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[(height - 1 - y) * width + x];
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }

            // 缩放
            int maxWidth = 512;
            if (width > maxWidth) {
                int newHeight = (int) ((double) maxWidth / width * height);
                java.awt.Image scaled = image.getScaledInstance(maxWidth, newHeight, java.awt.Image.SCALE_SMOOTH);
                BufferedImage scaledImage = new BufferedImage(maxWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                scaledImage.getGraphics().drawImage(scaled, 0, 0, null);
                image = scaledImage;
            }

            ImageIO.write(image, "png", outputFile);
        } catch (Exception e) {
            HelloWorldMod.LOGGER.error("保存 AI 截图失败", e);
        }
    }
}
