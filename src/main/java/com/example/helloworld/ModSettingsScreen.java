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
        super(Text.literal("AI 模组设置"));
        this.parent = parent;
        this.config = HelloWorldMod.getConfig();
    }

    @Override
    protected void init() {
        // 7 个按钮，每个高 20px，间距 4px，总高 164px，垂直居中起始 Y = height/2 - 82
        int startX = this.width / 2 - 100;
        int startY = this.height / 2 - 82;
        int btnH = 20;
        int gap = 4;

        // 截图开关按钮
        this.addDrawableChild(ButtonWidget.builder(
                getScreenshotButtonText(),
                button -> {
                    config.setScreenshotEnabled(!config.isScreenshotEnabled());
                    button.setMessage(getScreenshotButtonText());
                })
                .dimensions(startX, startY, 200, btnH)
                .build()
        );

        // 上下文记忆开关按钮
        this.addDrawableChild(ButtonWidget.builder(
                getContextButtonText(),
                button -> {
                    config.setContextEnabled(!config.isContextEnabled());
                    button.setMessage(getContextButtonText());
                })
                .dimensions(startX, startY + (btnH + gap), 200, btnH)
                .build()
        );

        // 联网搜索开关按钮
        this.addDrawableChild(ButtonWidget.builder(
                getWebSearchButtonText(),
                button -> {
                    config.setWebSearchEnabled(!config.isWebSearchEnabled());
                    button.setMessage(getWebSearchButtonText());
                })
                .dimensions(startX, startY + (btnH + gap) * 2, 200, btnH)
                .build()
        );

        // 选区工具按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("选区工具"),
                button -> this.client.setScreen(new com.example.helloworld.selection.SelectionScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 3, 200, btnH)
                .build()
        );

        // NBT 结构浏览器按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("加载结构 (NBT)"),
                button -> this.client.setScreen(new com.example.helloworld.nbt.NbtBrowserScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 4, 200, btnH)
                .build()
        );

        // TXT 结构设计图浏览器按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("加载结构 (TXT)"),
                button -> this.client.setScreen(new com.example.helloworld.blueprint.TxtBrowserScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 5, 200, btnH)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                button -> this.client.setScreen(this.parent))
                .dimensions(startX, startY + (btnH + gap) * 6, 200, btnH)
                .build()
        );
    }

    private Text getScreenshotButtonText() {
        return Text.literal("AI 聊天截图: " + (config.isScreenshotEnabled() ? "§a开启" : "§c关闭"));
    }

    private Text getContextButtonText() {
        return Text.literal("多轮对话记忆: " + (config.isContextEnabled() ? "§a开启" : "§c关闭"));
    }

    private Text getWebSearchButtonText() {
        return Text.literal("联网搜索: " + (config.isWebSearchEnabled() ? "§a开启" : "§c关闭"));
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
