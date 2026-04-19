package com.example.helloworld.selection;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * 选区工具界面：输入两个坐标，确认后高亮。
 * 退出时自动保留输入框内容（草稿）。
 */
public class SelectionScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget x1Field, y1Field, z1Field;
    private TextFieldWidget x2Field, y2Field, z2Field;

    public SelectionScreen(Screen parent) {
        super(Text.literal("选区工具"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int fieldW = 50;
        int gap = 8;
        int totalW = fieldW * 3 + gap * 2;
        int leftX = cx - totalW / 2;
        SelectionManager mgr = SelectionManager.getInstance();

        // --- 坐标 1 ---
        int fieldY1 = 60;
        x1Field = new TextFieldWidget(this.textRenderer, leftX, fieldY1, fieldW, 18, Text.literal("X1"));
        y1Field = new TextFieldWidget(this.textRenderer, leftX + fieldW + gap, fieldY1, fieldW, 18, Text.literal("Y1"));
        z1Field = new TextFieldWidget(this.textRenderer, leftX + (fieldW + gap) * 2, fieldY1, fieldW, 18, Text.literal("Z1"));

        // 恢复草稿或已确认坐标
        if (mgr.hasDraft()) {
            x1Field.setText(mgr.getDraftX1());
            y1Field.setText(mgr.getDraftY1());
            z1Field.setText(mgr.getDraftZ1());
        } else if (mgr.getPos1() != null) {
            x1Field.setText(String.valueOf(mgr.getPos1().getX()));
            y1Field.setText(String.valueOf(mgr.getPos1().getY()));
            z1Field.setText(String.valueOf(mgr.getPos1().getZ()));
        }
        this.addDrawableChild(x1Field);
        this.addDrawableChild(y1Field);
        this.addDrawableChild(z1Field);

        // --- 坐标 2 ---
        int fieldY2 = fieldY1 + 36;
        x2Field = new TextFieldWidget(this.textRenderer, leftX, fieldY2, fieldW, 18, Text.literal("X2"));
        y2Field = new TextFieldWidget(this.textRenderer, leftX + fieldW + gap, fieldY2, fieldW, 18, Text.literal("Y2"));
        z2Field = new TextFieldWidget(this.textRenderer, leftX + (fieldW + gap) * 2, fieldY2, fieldW, 18, Text.literal("Z2"));

        if (mgr.hasDraft()) {
            x2Field.setText(mgr.getDraftX2());
            y2Field.setText(mgr.getDraftY2());
            z2Field.setText(mgr.getDraftZ2());
        } else if (mgr.getPos2() != null) {
            x2Field.setText(String.valueOf(mgr.getPos2().getX()));
            y2Field.setText(String.valueOf(mgr.getPos2().getY()));
            z2Field.setText(String.valueOf(mgr.getPos2().getZ()));
        }
        this.addDrawableChild(x2Field);
        this.addDrawableChild(y2Field);
        this.addDrawableChild(z2Field);

        // --- 快捷按钮：当前位置 ---
        int quickY = fieldY2 + 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("坐标1=当前位置"), button -> {
            if (this.client != null && this.client.player != null) {
                BlockPos pos = this.client.player.getBlockPos();
                x1Field.setText(String.valueOf(pos.getX()));
                y1Field.setText(String.valueOf(pos.getY()));
                z1Field.setText(String.valueOf(pos.getZ()));
            }
        }).dimensions(cx - totalW / 2, quickY, totalW / 2 - 2, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("坐标2=当前位置"), button -> {
            if (this.client != null && this.client.player != null) {
                BlockPos pos = this.client.player.getBlockPos();
                x2Field.setText(String.valueOf(pos.getX()));
                y2Field.setText(String.valueOf(pos.getY()));
                z2Field.setText(String.valueOf(pos.getZ()));
            }
        }).dimensions(cx + 2, quickY, totalW / 2 - 2, 20).build());

        // --- 确认 / 清除 ---
        int actionY = quickY + 24;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§a确认选区"), button -> applySelection())
                .dimensions(cx - totalW / 2, actionY, totalW / 2 - 2, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("§c清除选区"), button -> {
            mgr.clear();
            mgr.clearDraft();
            x1Field.setText(""); y1Field.setText(""); z1Field.setText("");
            x2Field.setText(""); y2Field.setText(""); z2Field.setText("");
        }).dimensions(cx + 2, actionY, totalW / 2 - 2, 20).build());

        // --- 分析导出 ---
        int analyzeBtnY = actionY + 30;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§b分析/导出选区 →"), button -> analyzeAndExport())
                .dimensions(cx - totalW / 2, analyzeBtnY, totalW, 20).build());

        // --- 返回 ---
        this.addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> goBack())
                .dimensions(cx - 50, analyzeBtnY + 28, 100, 20).build());
    }

    /** 退出前保存草稿 */
    private void saveDraft() {
        SelectionManager.getInstance().saveDraft(
            x1Field.getText(), y1Field.getText(), z1Field.getText(),
            x2Field.getText(), y2Field.getText(), z2Field.getText()
        );
    }

    private void goBack() {
        saveDraft();
        this.client.setScreen(this.parent);
    }

    private void analyzeAndExport() {
        SelectionManager mgr = SelectionManager.getInstance();
        if (!mgr.isComplete()) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal("§c[选区] 请先确认选区再分析"), false);
            }
            return;
        }
        SelectionAnalyzer.AnalysisResult result = SelectionAnalyzer.analyze(mgr.getPos1(), mgr.getPos2());
        if (result == null) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal("§c[选区] 分析失败，无法读取世界数据"), false);
            }
            return;
        }
        saveDraft();
        this.client.setScreen(new SelectionExportScreen(this, result));
    }

    private void applySelection() {
        try {
            int x1 = Integer.parseInt(x1Field.getText().trim());
            int y1 = Integer.parseInt(y1Field.getText().trim());
            int z1 = Integer.parseInt(z1Field.getText().trim());
            int x2 = Integer.parseInt(x2Field.getText().trim());
            int y2 = Integer.parseInt(y2Field.getText().trim());
            int z2 = Integer.parseInt(z2Field.getText().trim());

            SelectionManager mgr = SelectionManager.getInstance();
            mgr.setPos1(new BlockPos(x1, y1, z1));
            mgr.setPos2(new BlockPos(x2, y2, z2));
            mgr.clearDraft();

            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                    Text.literal("§a[选区] 已设置: (" + x1 + "," + y1 + "," + z1 + ") → (" + x2 + "," + y2 + "," + z2 + ")"),
                    false);
            }
            this.client.setScreen(this.parent);
        } catch (NumberFormatException e) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal("§c[选区] 请输入有效的整数坐标"), false);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 34, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "坐标 1 (X  Y  Z):", cx - 84, 48, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer, "坐标 2 (X  Y  Z):", cx - 84, 48 + 36, 0xAAAAAA);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        saveDraft();
        this.client.setScreen(this.parent);
    }
}
