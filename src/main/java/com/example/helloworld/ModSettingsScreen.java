package com.example.helloworld;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 模组设置页面，包含截图开关。
 * 按钮大小与游戏菜单"选项..."按钮一致（200x20）。
 */
public class ModSettingsScreen extends Screen {

    private final Screen parent;
    private final ModConfig config;

    public ModSettingsScreen(Screen parent) {
        super(Text.literal("LZE 模组设置"));
        this.parent = parent;
        this.config = HelloWorldMod.getConfig();
    }

    @Override
    protected void init() {
        // 和"选项..."按钮一样大小: 200x20，居中放置
        this.addDrawableChild(ButtonWidget.builder(
                getScreenshotButtonText(),
                button -> {
                    config.setScreenshotEnabled(!config.isScreenshotEnabled());
                    button.setMessage(getScreenshotButtonText());
                })
                .dimensions(this.width / 2 - 100, this.height / 2 - 10, 200, 20)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                button -> this.client.setScreen(this.parent))
                .dimensions(this.width / 2 - 100, this.height / 2 + 24, 200, 20)
                .build()
        );
    }

    private Text getScreenshotButtonText() {
        return Text.literal("AI 聊天截图: " + (config.isScreenshotEnabled() ? "§a开启" : "§c关闭"));
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
