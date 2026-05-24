package com.example.helloworld;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户手册界面，显示内嵌的说明书内容。
 * 支持滚动浏览，简单的 Markdown 渲染（标题加粗/变色、列表缩进等）。
 */
public class UserManualScreen extends Screen {

    private final Screen parent;
    private List<ManualLine> lines = new ArrayList<>();
    private int scrollOffset = 0;
    private int contentAreaTop;
    private int contentAreaBottom;
    private static final int LINE_HEIGHT = 11;
    private static final int PADDING = 12;

    /** 渲染用的行数据 */
    private static class ManualLine {
        final String text;
        final int color;
        final boolean bold;
        final int indent;

        ManualLine(String text, int color, boolean bold, int indent) {
            this.text = text;
            this.color = color;
            this.bold = bold;
            this.indent = indent;
        }
    }

    public UserManualScreen(Screen parent) {
        super(Text.literal(I18n.get("用户手册", "User Manual")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        contentAreaTop = 30;
        contentAreaBottom = this.height - 32;

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.get("返回", "Back")),
                button -> this.client.setScreen(this.parent))
                .dimensions(this.width / 2 - 50, this.height - 26, 100, 20)
                .build()
        );

        // 加载手册内容
        loadManual();
    }

    private void loadManual() {
        lines.clear();
        try {
            // 根据 Mod 语言设置加载对应版本的手册
            String lang = HelloWorldMod.getConfig().getLanguage();
            String manualFile = "en_us".equals(lang)
                    ? "/assets/helloworld/manual_en.txt"
                    : "/assets/helloworld/manual_zh.txt";

            InputStream is = getClass().getResourceAsStream(manualFile);
            if (is == null) {
                lines.add(new ManualLine(I18n.get("无法加载用户手册", "Failed to load user manual"), 0xFF5555, false, 0));
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String rawLine;
            int maxWidth = this.width - PADDING * 2 - 10;

            while ((rawLine = reader.readLine()) != null) {
                parseLine(rawLine, maxWidth);
            }
            reader.close();
        } catch (Exception e) {
            lines.add(new ManualLine(I18n.get("加载手册出错: ", "Error loading manual: ") + e.getMessage(), 0xFF5555, false, 0));
        }
    }

    private void parseLine(String raw, int maxWidth) {
        // 空行
        if (raw.trim().isEmpty()) {
            lines.add(new ManualLine("", 0xFFFFFF, false, 0));
            return;
        }

        int color = 0xDDDDDD; // 默认正文颜色
        boolean bold = false;
        int indent = 0;
        String text = raw;

        // Markdown 标题
        if (raw.startsWith("# ")) {
            text = raw.substring(2);
            color = 0x55FFFF; // 一级标题：青色
            bold = true;
        } else if (raw.startsWith("## ")) {
            text = raw.substring(3);
            color = 0x55FF55; // 二级标题：绿色
            bold = true;
        } else if (raw.startsWith("### ")) {
            text = raw.substring(4);
            color = 0xFFFF55; // 三级标题：黄色
            bold = true;
        } else if (raw.startsWith("---")) {
            // 分隔线
            text = "────────────────────────────────";
            color = 0x555555;
        } else if (raw.startsWith("- ") || raw.startsWith("* ")) {
            // 列表项
            text = "  • " + raw.substring(2);
            indent = 4;
        } else if (raw.startsWith("  - ") || raw.startsWith("  * ")) {
            // 二级列表项
            text = "    ◦ " + raw.substring(4);
            indent = 8;
        } else if (raw.startsWith("> ")) {
            // 引用
            text = "  │ " + raw.substring(2);
            color = 0xAAAAAA;
        } else if (raw.startsWith("| ")) {
            // 表格行，简单显示
            color = 0xBBBBBB;
        } else if (raw.startsWith("```")) {
            // 代码块标记
            text = "  ─── " + (raw.length() > 3 ? raw.substring(3) : "") + " ───";
            color = 0x888888;
        }

        // 去除 Markdown 内联格式标记（**粗体**、`代码`）
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("`(.+?)`", "$1");

        // 自动换行
        wrapText(text, color, bold, indent, maxWidth);
    }

    private void wrapText(String text, int color, boolean bold, int indent, int maxWidth) {
        if (this.textRenderer == null) {
            lines.add(new ManualLine(text, color, bold, indent));
            return;
        }

        int availableWidth = maxWidth - indent;
        if (availableWidth <= 0) availableWidth = maxWidth;

        // 如果文本宽度不超过可用宽度，直接添加
        if (this.textRenderer.getWidth(text) <= availableWidth) {
            lines.add(new ManualLine(text, color, bold, indent));
            return;
        }

        // 需要换行
        StringBuilder current = new StringBuilder();
        for (String word : text.split("(?<=\\s)|(?=\\s)")) {
            if (this.textRenderer.getWidth(current.toString() + word) > availableWidth) {
                if (current.length() > 0) {
                    lines.add(new ManualLine(current.toString(), color, bold, indent));
                    current = new StringBuilder();
                }
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(new ManualLine(current.toString(), color, bold, indent));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);

        // 内容区域裁剪
        int visibleLines = (contentAreaBottom - contentAreaTop) / LINE_HEIGHT;
        int totalLines = lines.size();
        int maxScroll = Math.max(0, totalLines - visibleLines);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // 绘制内容
        int y = contentAreaTop;
        for (int i = scrollOffset; i < totalLines && y < contentAreaBottom; i++) {
            ManualLine line = lines.get(i);
            int x = PADDING + line.indent;
            context.drawTextWithShadow(this.textRenderer, line.text, x, y, line.color);
            y += LINE_HEIGHT;
        }

        // 滚动条
        if (totalLines > visibleLines) {
            int scrollBarHeight = contentAreaBottom - contentAreaTop;
            int thumbHeight = Math.max(20, scrollBarHeight * visibleLines / totalLines);
            int thumbY = contentAreaTop + (scrollBarHeight - thumbHeight) * scrollOffset / maxScroll;
            int scrollBarX = this.width - 6;
            context.fill(scrollBarX, contentAreaTop, scrollBarX + 4, contentAreaBottom, 0x33FFFFFF);
            context.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xAAFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset -= (int) verticalAmount * 3;
        int visibleLines = (contentAreaBottom - contentAreaTop) / LINE_HEIGHT;
        int maxScroll = Math.max(0, lines.size() - visibleLines);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int visibleLines = (contentAreaBottom - contentAreaTop) / LINE_HEIGHT;
        int maxScroll = Math.max(0, lines.size() - visibleLines);

        if (keyCode == 264) { // Down
            scrollOffset = Math.min(scrollOffset + 3, maxScroll);
            return true;
        } else if (keyCode == 265) { // Up
            scrollOffset = Math.max(scrollOffset - 3, 0);
            return true;
        } else if (keyCode == 267) { // Page Down
            scrollOffset = Math.min(scrollOffset + visibleLines, maxScroll);
            return true;
        } else if (keyCode == 266) { // Page Up
            scrollOffset = Math.max(scrollOffset - visibleLines, 0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
