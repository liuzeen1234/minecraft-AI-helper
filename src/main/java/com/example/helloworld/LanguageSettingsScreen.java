package com.example.helloworld;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Mod 语言说明页面。
 *
 * <p>显示语言现在完全跟随 Minecraft 当前游戏语言（见 {@link I18n#isEnglish()}），
 * 不再支持在此手动切换。保留本页面（及其入口）仅用于向玩家说明这一行为，
 * 不提供任何切换控件。
 */
public class LanguageSettingsScreen extends Screen {

    private final Screen parent;

    public LanguageSettingsScreen(Screen parent) {
        super(Text.literal(I18n.tr("screen.language.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int startX = this.width / 2 - 100;
        int btnH = 20;
        int startY = this.height / 2 - 10;

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("button.back")),
                button -> this.client.setScreen(this.parent))
                .dimensions(startX, startY, 200, btnH)
                .build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 50, 0xFFFFFF);
        // 说明：显示语言完全跟随游戏语言，不支持手动切换
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§7" + I18n.tr("screen.language.hint")),
                this.width / 2, this.height / 2 - 30, 0x888888);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
