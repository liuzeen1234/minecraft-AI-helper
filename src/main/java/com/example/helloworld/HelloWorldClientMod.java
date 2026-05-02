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

    // 按键绑定：打开设置页面
    private static KeyBinding openSettingsKey;

    @Override
    public void onInitializeClient() {
        // 注册选区渲染器
        com.example.helloworld.selection.SelectionRenderer.register();

        // 安装日志转发到聊天框的 Appender
        InGameLogAppender.install();

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

        // 注册接收服务端 NBT 导出结果通知
        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.EXPORT_NBT_RESULT_PACKET, (client, handler, buf, responseSender) -> {
            String resultMsg = buf.readString();
            client.execute(() -> {
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal(resultMsg), false);
                }
            });
        });

        // 每个客户端 tick 检查是否需要截图 & 刷新日志到聊天框
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 按键打开设置页面
            while (openSettingsKey.wasPressed()) {
                client.setScreen(new ModSettingsScreen(client.currentScreen));
            }

            // 将捕获的日志消息发送到聊天框
            InGameLogAppender.flushToChat();

            if (pendingMessage != null && delayTicks > 0) {
                delayTicks--;
                if (delayTicks == 0) {
                    String message = pendingMessage;
                    pendingMessage = null;
                    doScreenshotAndSend(client, message);
                }
            }
        });
    }

    private void doScreenshotAndSend(MinecraftClient client, String message) {
        boolean screenshotEnabled = HelloWorldMod.getConfig().isScreenshotEnabled();

        if (screenshotEnabled) {
            File screenshotDir = new File(client.runDirectory, "screenshots/ai");
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }
            ScreenshotRecorder.saveScreenshot(
                screenshotDir,
                client.getFramebuffer(),
                (text) -> client.inGameHud.getChatHud().addMessage(text)
            );

            File aiScreenshot = new File(client.runDirectory, "screenshots/ai/ai_temp.png");
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
