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
        super(Text.literal(I18n.get("AI 聊天设置", "AI Chat Settings")));
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
                Text.literal(I18n.get("AI API 设置", "AI API Settings")),
                button -> this.client.setScreen(new AiApiSettingsScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 4, 200, btnH)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("返回", "Back")),
                button -> this.client.setScreen(this.parent))
                .dimensions(startX, startY + (btnH + gap) * 5, 200, btnH)
                .build()
        );
    }

    private Text getScreenshotButtonText() {
        String on = I18n.get("§a开启", "§aON");
        String off = I18n.get("§c关闭", "§cOFF");
        String label = I18n.get("AI 聊天截图: ", "AI Chat Screenshot: ");
        return Text.literal(label + (config.isScreenshotEnabled() ? on : off));
    }

    private Text getContextButtonText() {
        String on = I18n.get("§a开启", "§aON");
        String off = I18n.get("§c关闭", "§cOFF");
        String label = I18n.get("多轮对话记忆: ", "Context Memory: ");
        return Text.literal(label + (config.isContextEnabled() ? on : off));
    }

    private Text getWebSearchButtonText() {
        String on = I18n.get("§a开启", "§aON");
        String off = I18n.get("§c关闭", "§cOFF");
        String label = I18n.get("联网搜索: ", "Web Search: ");
        return Text.literal(label + (config.isWebSearchEnabled() ? on : off));
    }

    private Text getStreamOutputButtonText() {
        String on = I18n.get("§a开启", "§aON");
        String off = I18n.get("§c关闭", "§cOFF");
        String label = I18n.get("流式输出(聊天框): ", "Stream Output (Chat): ");
        return Text.literal(label + (config.isStreamOutputEnabled() ? on : off));
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
