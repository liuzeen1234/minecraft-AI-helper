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
        super(Text.literal(I18n.tr("settings.title")));
        this.parent = parent;
        this.config = HelloWorldMod.getConfig();
    }

    @Override
    protected void init() {
        // 7 个按钮，每个高 20px，间距 4px，总高 = 7*20 + 6*4 = 164px，垂直居中
        int startX = this.width / 2 - 100;
        int btnH = 20;
        int gap = 4;
        int startY = this.height / 2 - 82;

        // AI 聊天设置（二级菜单入口）
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("settings.chat_settings")),
                button -> this.client.setScreen(new AiChatSettingsScreen(this)))
                .dimensions(startX, startY, 200, btnH)
                .build()
        );

        // AI 聊天按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("settings.chat")),
                button -> this.client.setScreen(new AiChatScreen(this)))
                .dimensions(startX, startY + (btnH + gap), 200, btnH)
                .build()
        );

        // 选区工具按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("settings.selection_tool")),
                button -> this.client.setScreen(new com.example.helloworld.selection.SelectionScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 2, 200, btnH)
                .build()
        );

        // 加载结构按钮（打开统一结构浏览器，根目录为 structures/）
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("settings.load_structures")),
                button -> this.client.setScreen(new com.example.helloworld.structure.StructureBrowserScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 3, 200, btnH)
                .build()
        );

        // Mod 语言设置按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("screen.language.title")),
                button -> this.client.setScreen(new LanguageSettingsScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 4, 200, btnH)
                .build()
        );

        // 用户手册按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("settings.user_manual")),
                button -> this.client.setScreen(new UserManualScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 5, 200, btnH)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("button.back")),
                button -> this.client.setScreen(this.parent))
                .dimensions(startX, startY + (btnH + gap) * 6, 200, btnH)
                .build()
        );
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
