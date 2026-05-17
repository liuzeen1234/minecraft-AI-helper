package com.example.helloworld;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Mod 语言设置页面，可选择中文或英文。
 */
public class LanguageSettingsScreen extends Screen {

    private final Screen parent;
    private final ModConfig config;

    public LanguageSettingsScreen(Screen parent) {
        super(Text.literal(I18n.get("Mod 语言设置", "Mod Language")));
        this.parent = parent;
        this.config = HelloWorldMod.getConfig();
    }

    @Override
    protected void init() {
        int startX = this.width / 2 - 100;
        int btnH = 20;
        int gap = 4;
        // 3 个按钮，总高 = 3*20 + 2*4 = 68，垂直居中
        int startY = this.height / 2 - 34;

        // 中文按钮
        this.addDrawableChild(ButtonWidget.builder(
                getChineseButtonText(),
                button -> {
                    config.setLanguage("zh_cn");
                    // 重新打开当前界面以刷新所有文本
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
                    // 重新打开当前界面以刷新所有文本
                    this.client.setScreen(new LanguageSettingsScreen(this.parent));
                })
                .dimensions(startX, startY + (btnH + gap), 200, btnH)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("返回", "Back")),
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
    public void close() {
        this.client.setScreen(this.parent);
    }
}
