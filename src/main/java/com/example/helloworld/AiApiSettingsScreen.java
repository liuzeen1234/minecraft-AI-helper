package com.example.helloworld;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * AI API 设置页面，可以修改 api_base_url、api_key、model。
 */
public class AiApiSettingsScreen extends Screen {

    private final Screen parent;
    private final ModConfig config;
    
    private TextFieldWidget apiUrlField;
    private TextFieldWidget apiKeyField;
    private TextFieldWidget modelField;
    private TextFieldWidget tavilyApiKeyField;
    
    private static final int FIELD_WIDTH = 500;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;

    public AiApiSettingsScreen(Screen parent) {
        super(Text.literal(I18n.get("AI API 设置", "AI API Settings")));
        this.parent = parent;
        this.config = HelloWorldMod.getConfig();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 90;  // 稍微向上移动一点
        // 自适应宽度：取 FIELD_WIDTH 和 (屏幕宽度 - 40) 的较小值，确保不超出屏幕
        int actualFieldWidth = Math.min(FIELD_WIDTH, this.width - 40);
        int fieldX = centerX - actualFieldWidth / 2;
        
        // API Base URL 输入框
        apiUrlField = new TextFieldWidget(this.textRenderer, fieldX, startY, actualFieldWidth, FIELD_HEIGHT, Text.literal("API URL"));
        apiUrlField.setMaxLength(512);  // 必须在 setText 之前设置，否则文本会被默认的 32 字符限制截断
        apiUrlField.setText(config.getApiBaseUrl());
        this.addDrawableChild(apiUrlField);
        
        // API Key 输入框
        apiKeyField = new TextFieldWidget(this.textRenderer, fieldX, startY + 35, actualFieldWidth, FIELD_HEIGHT, Text.literal("API Key"));
        apiKeyField.setMaxLength(256);
        apiKeyField.setText(config.getApiKey());
        this.addDrawableChild(apiKeyField);
        
        // Model 输入框
        modelField = new TextFieldWidget(this.textRenderer, fieldX, startY + 70, actualFieldWidth, FIELD_HEIGHT, Text.literal("Model"));
        modelField.setMaxLength(128);
        modelField.setText(config.getModel());
        this.addDrawableChild(modelField);
        
        // Tavily API Key 输入框
        tavilyApiKeyField = new TextFieldWidget(this.textRenderer, fieldX, startY + 105, actualFieldWidth, FIELD_HEIGHT, Text.literal("Tavily API Key"));
        tavilyApiKeyField.setMaxLength(256);
        tavilyApiKeyField.setText(config.getTavilyApiKey());
        this.addDrawableChild(tavilyApiKeyField);
        
        // 保存按钮
        int buttonY = startY + 145;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("保存", "Save")),
                button -> saveSettings())
                .dimensions(centerX - BUTTON_WIDTH - 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        );
        
        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("返回", "Back")),
                button -> this.client.setScreen(this.parent))
                .dimensions(centerX + 5, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build()
        );
    }
    
    private String validationStatusMessage = null;
    private int validationStatusColor = 0xFFFFFF;

    private void saveSettings() {
        String apiUrl = apiUrlField.getText().trim();
        String apiKey = apiKeyField.getText().trim();
        String model = modelField.getText().trim();
        String tavilyApiKey = tavilyApiKeyField.getText().trim();
        
        if (!apiUrl.isEmpty()) {
            config.setApiBaseUrl(apiUrl);
        }
        if (!apiKey.isEmpty()) {
            config.setApiKey(apiKey);
        }
        if (!model.isEmpty()) {
            config.setModel(model);
        }
        config.setTavilyApiKey(tavilyApiKey);
        
        HelloWorldMod.LOGGER.info("AI API 设置已保存: model={}, url={}", model, apiUrl);

        // 保存后验证 API Key
        validationStatusMessage = I18n.get("§7正在验证 API 连接...", "§7Validating API connection...");
        validationStatusColor = 0xAAAAAA;
        
        ApiKeyValidator.validateAsync(
                apiUrl.isEmpty() ? config.getApiBaseUrl() : apiUrl,
                apiKey.isEmpty() ? config.getApiKey() : apiKey,
                model.isEmpty() ? config.getModel() : model
        ).thenAccept(result -> {
            // 在渲染线程中更新状态
            validationStatusMessage = ApiKeyValidator.getResultMessage(result);
            if (result == ApiKeyValidator.ValidationResult.VALID) {
                validationStatusColor = 0x55FF55;
            } else if (result == ApiKeyValidator.ValidationResult.NETWORK_ERROR || result == ApiKeyValidator.ValidationResult.UNKNOWN_ERROR) {
                validationStatusColor = 0xFFFF55;
            } else {
                validationStatusColor = 0xFF5555;
            }
        });
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        
        int centerX = this.width / 2;
        int startY = this.height / 2 - 90;
        int actualFieldWidth = Math.min(FIELD_WIDTH, this.width - 40);
        int labelX = centerX - actualFieldWidth / 2;
        
        // 绘制标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, startY - 25, 0xFFFFFF);
        
        // 绘制标签
        context.drawTextWithShadow(this.textRenderer, "API Base URL:", labelX, startY - 10, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, "API Key:", labelX, startY + 25, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, "Model:", labelX, startY + 60, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, "Tavily API Key:", labelX, startY + 95, 0xAAAAAA);
        
        // 绘制验证状态消息
        if (validationStatusMessage != null) {
            // 去掉颜色代码后绘制（使用纯文本）
            String cleanMessage = validationStatusMessage.replaceAll("§[0-9a-fk-or]", "");
            int buttonY = startY + 145;
            context.drawCenteredTextWithShadow(this.textRenderer, cleanMessage, centerX, buttonY + 25, validationStatusColor);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
