package com.example.helloworld.selection;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 选区分析与导出界面：显示选区内方块统计信息，支持导出为蓝图文件。
 */
public class SelectionExportScreen extends Screen {

    private final Screen parent;
    private final SelectionAnalyzer.AnalysisResult result;

    // 分页显示方块列表
    private int scrollOffset = 0;
    private List<Map.Entry<String, Integer>> blockList;

    public SelectionExportScreen(Screen parent, SelectionAnalyzer.AnalysisResult result) {
        super(Text.literal(com.example.helloworld.I18n.get("选区分析", "Selection Analysis")));
        this.parent = parent;
        this.result = result;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int totalW = 220;
        int leftX = cx - totalW / 2;

        blockList = new ArrayList<>(result.blockCounts().entrySet());

        // 导出选区按钮（点击后弹出导出菜单）
        int exportBtnY = this.height - 58;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(com.example.helloworld.I18n.get("§e导出选区", "§eExport")), button -> {
            this.client.setScreen(new SelectionExportPopupScreen(this, result));
        }).dimensions(leftX, exportBtnY, totalW, 20).build());

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(Text.literal(com.example.helloworld.I18n.get("返回", "Back")), button -> {
            this.client.setScreen(this.parent);
        }).dimensions(cx - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int totalW = 220;
        int leftX = cx - totalW / 2;

        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 10, 0xFFFFFF);

        // 基本信息
        int infoY = 26;
        context.drawTextWithShadow(this.textRenderer,
                String.format("选区大小: %d × %d × %d", result.sizeX(), result.sizeY(), result.sizeZ()),
                leftX, infoY, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                String.format("方块总数: %d  空气: %d  实体: %d",
                        result.totalBlocks(), result.airBlocks(),
                        result.totalBlocks() - result.airBlocks()),
                leftX, infoY + 12, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                String.format("方块种类: %d", result.blockCounts().size()),
                leftX, infoY + 24, 0xAAAAAA);

        // 方块列表
        int listY = infoY + 42;
        int listMaxY = this.height - 66; // 导出按钮上方留出空间
        context.drawTextWithShadow(this.textRenderer, "--- 方块统计 ---", leftX, listY, 0xFFFF55);
        listY += 12;

        int maxVisible = Math.max(1, (listMaxY - listY) / 11);
        int end = Math.min(scrollOffset + maxVisible, blockList.size());
        for (int i = scrollOffset; i < end; i++) {
            if (listY + 11 > listMaxY) break;
            Map.Entry<String, Integer> entry = blockList.get(i);
            String line = String.format("%-30s x%d", entry.getKey(), entry.getValue());
            // 截断过长的行
            if (line.length() > 40) line = line.substring(0, 40);
            context.drawTextWithShadow(this.textRenderer, line, leftX, listY, 0xDDDDDD);
            listY += 11;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, blockList.size() - 1);
        if (verticalAmount > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (verticalAmount < 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }
        return true;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
