package com.example.helloworld.selection;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 选区分析与导出界面：显示选区内方块统计信息，支持导出为蓝图文件。
 */
public class SelectionExportScreen extends Screen {

    private final Screen parent;
    private final SelectionAnalyzer.AnalysisResult result;

    // 分页显示方块列表
    private int scrollOffset = 0;
    private List<Map.Entry<String, Integer>> blockList;

    // 导出时是否包含实体（默认开）
    private boolean includeEntities = true;

    // 被忽略（不记录）的方块种类 id 集合，导出时会被过滤掉
    private final Set<String> ignoredBlocks = new HashSet<>();

    // 列表渲染布局参数（render 与 mouseClicked 共享，保证命中检测与绘制一致）
    private static final int LIST_TOTAL_W = 220;
    private static final int ROW_HEIGHT = 11;
    private static final int TOGGLE_W = 9;   // +/- 符号点击区域宽度
    // 列表首行的 y（render 中计算：infoY(26) + 42 + 12）
    private static final int LIST_FIRST_ROW_Y = 26 + 42 + 12;

    public SelectionExportScreen(Screen parent, SelectionAnalyzer.AnalysisResult result) {
        super(Text.literal(com.example.helloworld.I18n.tr("selection.export.title")));
        this.parent = parent;
        this.result = result;
        // 默认不保存空气方块：将 air/cave_air/void_air 预置为忽略。
        // 它们仍以普通条目出现在方块列表中，用户可点 +/- 切换是否保存。
        this.ignoredBlocks.add("air");
        this.ignoredBlocks.add("cave_air");
        this.ignoredBlocks.add("void_air");
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int totalW = 220;
        int leftX = cx - totalW / 2;

        blockList = new ArrayList<>(result.blockCounts().entrySet());

        // 导出选区按钮（点击后弹出导出菜单）
        int exportBtnY = this.height - 80;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(com.example.helloworld.I18n.tr("selection.export.export_button")), button -> {
            this.client.setScreen(new SelectionExportPopupScreen(this, result, includeEntities, new HashSet<>(ignoredBlocks)));
        }).dimensions(leftX, exportBtnY, totalW, 20).build());

        // 包含实体开关
        int entityToggleY = exportBtnY + 24;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(getEntityToggleText()), button -> {
            includeEntities = !includeEntities;
            button.setMessage(Text.literal(getEntityToggleText()));
        }).dimensions(leftX, entityToggleY, totalW, 20).build());

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(Text.literal(com.example.helloworld.I18n.tr("button.back")), button -> {
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
                com.example.helloworld.I18n.tr("selection.export.size", result.sizeX(), result.sizeY(), result.sizeZ()),
                leftX, infoY, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                com.example.helloworld.I18n.tr("selection.export.counts",
                        result.totalBlocks(), result.airBlocks(),
                        result.totalBlocks() - result.airBlocks()),
                leftX, infoY + 12, 0xAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                com.example.helloworld.I18n.tr("selection.export.types", result.blockCounts().size()),
                leftX, infoY + 24, 0xAAAAAA);
        // 忽略提示：显示已忽略的种类数
        if (!ignoredBlocks.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                    com.example.helloworld.I18n.tr("selection.export.ignored_count", ignoredBlocks.size()),
                    leftX + 120, infoY + 24, 0xFF7777);
        }

        // 方块列表
        int listY = infoY + 42;
        int listMaxY = this.height - 88; // 导出按钮上方留出空间
        context.drawTextWithShadow(this.textRenderer, com.example.helloworld.I18n.tr("selection.export.block_stats"), leftX, listY, 0xFFFF55);
        listY += 12;

        // +/- 切换符号所在的 x（行右侧）
        int toggleX = leftX + totalW - TOGGLE_W;

        int maxVisible = Math.max(1, (listMaxY - listY) / ROW_HEIGHT);
        int end = Math.min(scrollOffset + maxVisible, blockList.size());
        for (int i = scrollOffset; i < end; i++) {
            if (listY + ROW_HEIGHT > listMaxY) break;
            Map.Entry<String, Integer> entry = blockList.get(i);
            String blockId = entry.getKey();
            boolean ignored = ignoredBlocks.contains(blockId);

            String line = String.format("%-30s x%d", blockId, entry.getValue());
            // 截断过长的行，给右侧符号留出空间
            if (line.length() > 34) line = line.substring(0, 34);
            // 被忽略的方块用灰色暗显，正常记录的用亮色
            int textColor = ignored ? 0x888888 : 0xDDDDDD;
            context.drawTextWithShadow(this.textRenderer, line, leftX, listY, textColor);

            // 右侧切换符号：会被记录 -> 红色减号（按下后不记录）；已忽略 -> 绿色加号
            String symbol = ignored ? "+" : "-";
            int symbolColor = ignored ? 0x55FF55 : 0xFF5555;
            context.drawTextWithShadow(this.textRenderer, symbol, toggleX, listY, symbolColor);

            listY += ROW_HEIGHT;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && blockList != null) {
            int cx = this.width / 2;
            int leftX = cx - LIST_TOTAL_W / 2;
            int toggleX = leftX + LIST_TOTAL_W - TOGGLE_W;
            int listMaxY = this.height - 88;

            // 命中检测：判断点击落在哪一可见行的右侧符号区域
            if (mouseX >= toggleX && mouseX <= toggleX + TOGGLE_W) {
                int maxVisible = Math.max(1, (listMaxY - LIST_FIRST_ROW_Y) / ROW_HEIGHT);
                int end = Math.min(scrollOffset + maxVisible, blockList.size());
                for (int i = scrollOffset; i < end; i++) {
                    int rowY = LIST_FIRST_ROW_Y + (i - scrollOffset) * ROW_HEIGHT;
                    if (rowY + ROW_HEIGHT > listMaxY) break;
                    if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                        String blockId = blockList.get(i).getKey();
                        if (!ignoredBlocks.remove(blockId)) {
                            ignoredBlocks.add(blockId);
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    /**
     * 获取实体开关按钮的显示文本。
     */
    private String getEntityToggleText() {
        if (includeEntities) {
            return com.example.helloworld.I18n.tr("selection.export.entities.on");
        } else {
            return com.example.helloworld.I18n.tr("selection.export.entities.off");
        }
    }

    /**
     * 获取当前实体导出设置。
     */
    public boolean isIncludeEntities() {
        return includeEntities;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
