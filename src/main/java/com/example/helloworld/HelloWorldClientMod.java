package com.example.helloworld;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class HelloWorldClientMod implements ClientModInitializer {

    // 延迟截图用的状态
    private String pendingMessage = null;
    private int delayTicks = 0;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.TAKE_SCREENSHOT_PACKET, (client, handler, buf, responseSender) -> {
            String message = buf.readString();
            // 收到截图指令后，等 2 个 tick 再截图（等聊天框关闭）
            client.execute(() -> {
                pendingMessage = message;
                delayTicks = 2;
            });
        });

        // 每个客户端 tick 检查是否需要截图
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
        File screenshotDir = new File(client.runDirectory, "screenshots/lze");
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs();
        }
        ScreenshotRecorder.saveScreenshot(
            screenshotDir,
            client.getFramebuffer(),
            (text) -> client.inGameHud.getChatHud().addMessage(text)
        );

        File aiScreenshot = new File(client.runDirectory, "screenshots/lze/ai_temp.png");
        saveScaledScreenshot(client.getFramebuffer(), aiScreenshot);

        PacketByteBuf responseBuf = PacketByteBufs.create();
        responseBuf.writeString(message);
        responseBuf.writeString(aiScreenshot.getAbsolutePath());
        ClientPlayNetworking.send(HelloWorldMod.SCREENSHOT_RESPONSE_PACKET, responseBuf);
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
