package com.example.helloworld.selection;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * 已保存选区列表界面。点击条目可加载到选区管理器并高亮。
 */
public class SelectionMemoryScreen extends Screen {

    private final Screen parent;
    private int scrollOffset = 0;
    private static final int PAGE_SIZE = 6;
    private static final int ROW_HEIGHT = 22;

    public SelectionMemoryScreen(Screen parent) {
        super(Text.literal(com.example.helloworld.I18n.get("已保存的选区", "Saved Selections")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<SelectionMemory.Entry> entries = SelectionMemory.load();
        int cx = this.width / 2;
        int listW = 260;
        int listLeft = cx - listW / 2;
        int startY = 50;

        // 列表条目
        int end = Math.min(entries.size(), scrollOffset + PAGE_SIZE);
        for (int i = scrollOffset; i < end; i++) {
            SelectionMemory.Entry entry = entries.get(i);
            int row = i - scrollOffset;
            int rowY = startY + row * ROW_HEIGHT;
            final int idx = i;

            // 加载按钮：名称 + 坐标摘要
            BlockPos p1 = entry.pos1();
            BlockPos p2 = entry.pos2();
            String label = "§f" + entry.name() + " §7(" + p1.getX() + "," + p1.getY() + "," + p1.getZ()
                    + ")→(" + p2.getX() + "," + p2.getY() + "," + p2.getZ() + ")";
            this.addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                SelectionManager mgr = SelectionManager.getInstance();
                mgr.setPos1(entry.pos1());
                mgr.setPos2(entry.pos2());
                mgr.clearDraft();
                if (this.client != null && this.client.player != null) {
                    this.client.player.sendMessage(
                        Text.literal("§a[选区] 已加载: " + entry.name()), false);
                }
            }).dimensions(listLeft, rowY, listW - 24, 20).build());

            // 删除按钮
            this.addDrawableChild(ButtonWidget.builder(Text.literal("§cx"), button -> {
                SelectionMemory.removeEntry(idx);
                if (scrollOffset > 0 && scrollOffset >= SelectionMemory.load().size()) scrollOffset--;
                rebuildUI();
            }).dimensions(listLeft + listW - 20, rowY, 20, 20).build());
        }

        // 翻页
        int navY = startY + PAGE_SIZE * ROW_HEIGHT + 4;
        if (entries.size() > PAGE_SIZE) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal(com.example.helloworld.I18n.get("▲ 上一页", "▲ Prev")), button -> {
                if (scrollOffset > 0) { scrollOffset -= PAGE_SIZE; if (scrollOffset < 0) scrollOffset = 0; rebuildUI(); }
            }).dimensions(cx - 104, navY, 100, 20).build());

            this.addDrawableChild(ButtonWidget.builder(Text.literal(com.example.helloworld.I18n.get("▼ 下一页", "▼ Next")), button -> {
                if (scrollOffset + PAGE_SIZE < entries.size()) { scrollOffset += PAGE_SIZE; rebuildUI(); }
            }).dimensions(cx + 4, navY, 100, 20).build());
        }

        // 返回
        this.addDrawableChild(ButtonWidget.builder(Text.literal(com.example.helloworld.I18n.get("返回", "Back")), button -> this.client.setScreen(this.parent))
                .dimensions(cx - 50, this.height - 30, 100, 20).build());
    }

    private void rebuildUI() {
        this.clearChildren();
        this.init();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 28, 0xFFFFFF);

        List<SelectionMemory.Entry> entries = SelectionMemory.load();
        if (entries.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, com.example.helloworld.I18n.get("§7暂无保存的选区", "§7No saved selections"), cx, this.height / 2, 0x888888);
        } else {
            String info = com.example.helloworld.I18n.get(
                "共 " + entries.size() + " 条  (第 " + (scrollOffset + 1) + "-" + Math.min(scrollOffset + PAGE_SIZE, entries.size()) + " 条)",
                "Total " + entries.size() + "  (" + (scrollOffset + 1) + "-" + Math.min(scrollOffset + PAGE_SIZE, entries.size()) + ")"
            );
            context.drawCenteredTextWithShadow(this.textRenderer, info, cx, 40, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
