package com.example.helloworld;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * AI 聊天设置二级页面，包含聊天相关的开关和 API 设置入口。
 */
public class AiChatSettingsScreen extends Screen {

    private final Screen parent;
    private final ModConfig config;

    private TextFieldWidget maxToolRoundsField;
    // 输入框相对布局用的坐标缓存（供 render 绘制标签）
    private int maxRoundsLabelX;
    private int maxRoundsLabelY;
    private String maxRoundsStatus = null;
    private int maxRoundsStatusColor = 0xAAAAAA;

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

        // 最大工具调用轮数输入框（0=禁用多轮）。标签绘制在输入框上方，输入框宽度略小以容纳保存按钮。
        int roundsY = startY + (btnH + gap) * 4 + 8; // 上方留一点空间给标签
        maxRoundsLabelX = startX;
        maxRoundsLabelY = roundsY - 10;
        maxToolRoundsField = new TextFieldWidget(this.textRenderer, startX, roundsY, 150, btnH,
                Text.literal("max_tool_rounds"));
        maxToolRoundsField.setMaxLength(6);
        maxToolRoundsField.setText(String.valueOf(config.getMaxToolRounds()));
        this.addDrawableChild(maxToolRoundsField);

        // 保存按钮（在输入框右侧）
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("settings.api.save")),
                button -> saveMaxToolRounds())
                .dimensions(startX + 155, roundsY, 45, btnH)
                .build()
        );

        // AI API 设置按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("settings.chat.api_settings")),
                button -> this.client.setScreen(new AiApiSettingsScreen(this)))
                .dimensions(startX, startY + (btnH + gap) * 5 + 8, 200, btnH)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("button.back")),
                button -> {
                    saveMaxToolRounds();
                    this.client.setScreen(this.parent);
                })
                .dimensions(startX, startY + (btnH + gap) * 6 + 8, 200, btnH)
                .build()
        );
    }

    /** 解析输入框内容并保存 max_tool_rounds；非法输入给出提示且不覆盖旧值。 */
    private void saveMaxToolRounds() {
        if (maxToolRoundsField == null) return;
        String raw = maxToolRoundsField.getText().trim();
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) throw new NumberFormatException("negative");
            config.setMaxToolRounds(value);
            maxRoundsStatus = I18n.tr("settings.chat.max_tool_rounds.saved", value);
            maxRoundsStatusColor = 0x55FF55;
            // 归一化显示（clamp 后回填）
            maxToolRoundsField.setText(String.valueOf(config.getMaxToolRounds()));
        } catch (NumberFormatException e) {
            maxRoundsStatus = I18n.tr("settings.chat.max_tool_rounds.invalid");
            maxRoundsStatusColor = 0xFF5555;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        // 输入框上方的说明标签
        if (maxToolRoundsField != null) {
            context.drawTextWithShadow(this.textRenderer,
                    I18n.tr("settings.chat.max_tool_rounds"), maxRoundsLabelX, maxRoundsLabelY, 0xAAAAAA);
            if (maxRoundsStatus != null) {
                String clean = maxRoundsStatus.replaceAll("§[0-9a-fk-or]", "");
                context.drawTextWithShadow(this.textRenderer, clean,
                        maxRoundsLabelX, maxRoundsField_bottomY(), maxRoundsStatusColor);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private int maxRoundsField_bottomY() {
        return maxToolRoundsField.getY() + maxToolRoundsField.getHeight() + 2;
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
