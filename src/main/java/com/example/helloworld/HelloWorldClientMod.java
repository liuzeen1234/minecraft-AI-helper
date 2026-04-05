package com.example.helloworld;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.io.File;

public class HelloWorldClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // 监听服务端发来的截图指令
        ClientPlayNetworking.registerGlobalReceiver(HelloWorldMod.TAKE_SCREENSHOT_PACKET, (client, handler, buf, responseSender) -> {
            String message = buf.readString();

            // 截图必须在渲染线程执行
            client.execute(() -> {
                // 截图保存到 screenshots/lze 文件夹
                File screenshotDir = new File(client.runDirectory, "screenshots/lze");
                if (!screenshotDir.exists()) {
                    screenshotDir.mkdirs();
                }

                ScreenshotRecorder.saveScreenshot(
                    screenshotDir,
                    client.getFramebuffer(),
                    (text) -> client.inGameHud.getChatHud().addMessage(text)
                );
            });
        });
    }
}
