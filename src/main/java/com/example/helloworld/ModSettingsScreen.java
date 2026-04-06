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
        // 截图开关按钮
        this.addDrawableChild(ButtonWidget.builder(
                getScreenshotButtonText(),
                button -> {
                    config.setScreenshotEnabled(!config.isScreenshotEnabled());
                    button.setMessage(getScreenshotButtonText());
                })
                .dimensions(this.width / 2 - 100, this.height / 2 - 34, 200, 20)
                .build()
        );

        // 上下文记忆开关按钮
        this.addDrawableChild(ButtonWidget.builder(
                getContextButtonText(),
                button -> {
                    config.setContextEnabled(!config.isContextEnabled());
                    button.setMessage(getContextButtonText());
                })
                .dimensions(this.width / 2 - 100, this.height / 2 - 10, 200, 20)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                button -> this.client.setScreen(this.parent))
                .dimensions(this.width / 2 - 100, this.height / 2 + 14, 200, 20)
                .build()
        );
    }

    private Text getScreenshotButtonText() {
        return Text.literal("AI 聊天截图: " + (config.isScreenshotEnabled() ? "§a开启" : "§c关闭"));
    }

    private Text getContextButtonText() {
        return Text.literal("多轮对话记忆: " + (config.isContextEnabled() ? "§a开启" : "§c关闭"));
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
