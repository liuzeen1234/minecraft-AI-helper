package com.example.helloworld;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Mod 语言设置页面，可手动切换中文 / English。
 *
 * <p>显示语言以 {@link ModConfig#getLanguage()} 字段为准，本页面可在本次运行内临时切换。
 * 注意：每次游戏重启时该字段会被重置为当前游戏语言（见 HelloWorldClientMod）。
 */
public class LanguageSettingsScreen extends Screen {

    private final Screen parent;
    private final ModConfig config;

    public LanguageSettingsScreen(Screen parent) {
        super(Text.literal(I18n.tr("screen.language.title")));
        this.parent = parent;
        this.config = HelloWorldMod.getConfig();
    }

    @Override
    protected void init() {
        int startX = this.width / 2 - 100;
        int btnH = 20;
        int gap = 4;
        int startY = this.height / 2 - 24;

        // 中文按钮
        this.addDrawableChild(ButtonWidget.builder(
                getChineseButtonText(),
                button -> {
                    config.setLanguage("zh_cn");
                    this.client.setScreen(new LanguageSettingsScreen(this.parent));
                })
                .dimensions(startX, startY, 200, btnH)
                .build()
        );

        // English 按钮
        this.addDrawableChild(ButtonWidget.builder(
                getEnglishButtonText(),
                button -> {
                    config.setLanguage("en_us");
                    this.client.setScreen(new LanguageSettingsScreen(this.parent));
                })
                .dimensions(startX, startY + (btnH + gap), 200, btnH)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("button.back")),
                button -> this.client.setScreen(this.parent))
                .dimensions(startX, startY + (btnH + gap) * 2, 200, btnH)
                .build()
        );
    }

    private Text getChineseButtonText() {
        boolean selected = "zh_cn".equals(config.getLanguage());
        return Text.literal("中文" + (selected ? " §a✔" : ""));
    }

    private Text getEnglishButtonText() {
        boolean selected = "en_us".equals(config.getLanguage());
        return Text.literal("English" + (selected ? " §a✔" : ""));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);
        // 提示：重启会重置为游戏语言
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§7" + I18n.tr("screen.language.hint")),
                this.width / 2, this.height / 2 + 46, 0x888888);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
