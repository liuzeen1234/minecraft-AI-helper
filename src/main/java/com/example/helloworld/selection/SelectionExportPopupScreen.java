package com.example.helloworld.selection;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * P2 - 导出选区选择页面：点击"导出选区"后跳转到此页面，
 * 提供"导出.txt"和"导出.nbt"两个选项。
 */
public class SelectionExportPopupScreen extends Screen {

    private final Screen parent;
    private final SelectionAnalyzer.AnalysisResult result;

    // 弹窗尺寸
    private static final int POPUP_WIDTH = 260;
    private static final int POPUP_HEIGHT = 110;

    public SelectionExportPopupScreen(Screen parent, SelectionAnalyzer.AnalysisResult result) {
        super(Text.literal("导出选区"));
        this.parent = parent;
        this.result = result;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int popLeft = cx - POPUP_WIDTH / 2;
        int popTop = cy - POPUP_HEIGHT / 2;

        int btnW = POPUP_WIDTH - 40;
        int btnLeft = popLeft + 20;

        // "导出.txt" 按钮 → 跳转到 P4 (ExportTxtScreen)
        int txtBtnY = popTop + 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§a导出 .txt"), button -> {
            this.client.setScreen(new ExportTxtScreen(this, result));
        }).dimensions(btnLeft, txtBtnY, btnW, 20).build());

        // "导出.nbt" 按钮 → 跳转到 P3 (ExportNbtScreen)
        int nbtBtnY = txtBtnY + 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§b导出 .nbt"), button -> {
            this.client.setScreen(new ExportNbtScreen(this, result));
        }).dimensions(btnLeft, nbtBtnY, btnW, 20).build());

        // 返回按钮
        int cancelY = nbtBtnY + 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(cx - 40, cancelY, 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int popLeft = cx - POPUP_WIDTH / 2;
        int popTop = cy - POPUP_HEIGHT / 2;

        // 弹窗背景
        context.fill(popLeft - 2, popTop - 2, popLeft + POPUP_WIDTH + 2, popTop + POPUP_HEIGHT + 2, 0xFFAAAAAA);
        context.fill(popLeft, popTop, popLeft + POPUP_WIDTH, popTop + POPUP_HEIGHT, 0xFF000000);

        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, popTop + 10, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
