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
        // 6 个按钮，每个高 20px，间距 4px，总高 = 6*20 + 5*4 = 140px，垂直居中
        int startX = this.width / 2 - 100;
        int btnH = 20;
        int gap = 4;
        int startY = this.height / 2 - 70;

        // AI 聊天设置（二级菜单入口）
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("AI 聊天设置"),
                button -> this.client.setScreen(new AiChatSettingsScreen(this)))
                .dimensions(startX, startY, 200, btnH)
                .build()
        );

        // AI 聊天按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("AI 聊天"),
                button -> this.client.setScreen(new AiChatScreen(this)))
                .dimensions(startX, startY + (btnH + gap), 200, btnH)
                .build()
        );

        // 选区工具按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("选区工具"),
                button -> this.client.setScreen(new com.example.helloworld.selection.SelectionScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 2, 200, btnH)
                .build()
        );

        // NBT 结构浏览器按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("加载结构 (NBT)"),
                button -> this.client.setScreen(new com.example.helloworld.nbt.NbtBrowserScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 3, 200, btnH)
                .build()
        );

        // TXT 结构设计图浏览器按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("加载结构 (TXT)"),
                button -> this.client.setScreen(new com.example.helloworld.blueprint.TxtBrowserScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 4, 200, btnH)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                button -> this.client.setScreen(this.parent))
                .dimensions(startX, startY + (btnH + gap) * 5, 200, btnH)
                .build()
        );
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
