package com.example.helloworld;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * AI 聊天设置二级页面，包含聊天相关的开关和 API 设置入口。
 */
public class AiChatSettingsScreen extends Screen {

    private final Screen parent;
    private final ModConfig config;

    public AiChatSettingsScreen(Screen parent) {
        super(Text.literal(I18n.tr("settings.chat.title")));
        this.parent = parent;
        this.config = HelloWorldMod.getConfig();
    }

    @Override
    protected void init() {
        int startX = this.width / 2 - 100;
        int btnH = 20;
        int gap = 4;
        int startY = this.height / 2 - 70;

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

        // 流式输出开关按钮
        this.addDrawableChild(ButtonWidget.builder(
                getStreamOutputButtonText(),
                button -> {
                    config.setStreamOutputEnabled(!config.isStreamOutputEnabled());
                    button.setMessage(getStreamOutputButtonText());
                })
                .dimensions(startX, startY + (btnH + gap) * 3, 200, btnH)
                .build()
        );

        // AI API 设置按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("settings.chat.api_settings")),
                button -> this.client.setScreen(new AiApiSettingsScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 4, 200, btnH)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("button.back")),
                button -> this.client.setScreen(this.parent))
                .dimensions(startX, startY + (btnH + gap) * 5, 200, btnH)
                .build()
        );
    }

    private Text getScreenshotButtonText() {
        String state = config.isScreenshotEnabled() ? I18n.tr("settings.chat.on") : I18n.tr("settings.chat.off");
        return Text.literal(I18n.tr("settings.chat.screenshot", state));
    }

    private Text getContextButtonText() {
        String state = config.isContextEnabled() ? I18n.tr("settings.chat.on") : I18n.tr("settings.chat.off");
        return Text.literal(I18n.tr("settings.chat.context", state));
    }

    private Text getWebSearchButtonText() {
        String state = config.isWebSearchEnabled() ? I18n.tr("settings.chat.on") : I18n.tr("settings.chat.off");
        return Text.literal(I18n.tr("settings.chat.web_search", state));
    }

    private Text getStreamOutputButtonText() {
        String state = config.isStreamOutputEnabled() ? I18n.tr("settings.chat.on") : I18n.tr("settings.chat.off");
        return Text.literal(I18n.tr("settings.chat.stream_output", state));
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
