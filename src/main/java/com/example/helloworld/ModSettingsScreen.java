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
        super(Text.literal(I18n.get("AI 模组设置", "AI Mod Settings")));
        this.parent = parent;
        this.config = HelloWorldMod.getConfig();
    }

    @Override
    protected void init() {
        // 8 个按钮，每个高 20px，间距 4px，总高 = 8*20 + 7*4 = 188px，垂直居中
        int startX = this.width / 2 - 100;
        int btnH = 20;
        int gap = 4;
        int startY = this.height / 2 - 94;

        // AI 聊天设置（二级菜单入口）
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("AI 聊天设置", "AI Chat Settings")),
                button -> this.client.setScreen(new AiChatSettingsScreen(this)))
                .dimensions(startX, startY, 200, btnH)
                .build()
        );

        // AI 聊天按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("AI 聊天", "AI Chat")),
                button -> this.client.setScreen(new AiChatScreen(this)))
                .dimensions(startX, startY + (btnH + gap), 200, btnH)
                .build()
        );

        // 选区工具按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("选区工具", "Selection Tool")),
                button -> this.client.setScreen(new com.example.helloworld.selection.SelectionScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 2, 200, btnH)
                .build()
        );

        // NBT 结构浏览器按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("加载结构 (NBT)", "Load Structure (NBT)")),
                button -> this.client.setScreen(new com.example.helloworld.nbt.NbtBrowserScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 3, 200, btnH)
                .build()
        );

        // TXT 结构设计图浏览器按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("加载结构 (TXT)", "Load Structure (TXT)")),
                button -> this.client.setScreen(new com.example.helloworld.blueprint.TxtBrowserScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 4, 200, btnH)
                .build()
        );

        // Mod 语言设置按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("Mod 语言设置", "Mod Language")),
                button -> this.client.setScreen(new LanguageSettingsScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 5, 200, btnH)
                .build()
        );

        // 用户手册按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("用户手册", "User Manual")),
                button -> this.client.setScreen(new UserManualScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 6, 200, btnH)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("返回", "Back")),
                button -> this.client.setScreen(this.parent))
                .dimensions(startX, startY + (btnH + gap) * 7, 200, btnH)
                .build()
        );
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
