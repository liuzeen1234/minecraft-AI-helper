package com.example.helloworld.selection;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 选区分析与导出界面：显示选区内方块统计信息，支持导出为蓝图文件。
 */
public class SelectionExportScreen extends Screen {

    private final Screen parent;
    private final SelectionAnalyzer.AnalysisResult result;
    private TextFieldWidget nameField;

    // 分页显示方块列表
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 8;
    private List<Map.Entry<String, Integer>> blockList;

    public SelectionExportScreen(Screen parent, SelectionAnalyzer.AnalysisResult result) {
        super(Text.literal("选区分析"));
        this.parent = parent;
        this.result = result;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int totalW = 220;
        int leftX = cx - totalW / 2;

        blockList = new ArrayList<>(result.blockCounts().entrySet());

        // 蓝图名称输入
        int nameY = this.height - 80;
        nameField = new TextFieldWidget(this.textRenderer, leftX, nameY, totalW, 18, Text.literal("名称"));
        nameField.setText("exported_blueprint");
        nameField.setMaxLength(64);
        this.addDrawableChild(nameField);

        // 导出按钮行
        int exportY = nameY + 22;
        int halfW = totalW / 2 - 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§a导出蓝图"), button -> doExport())
                .dimensions(leftX, exportY, halfW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§b导出NBT"), button -> doExportNbt())
                .dimensions(cx + 2, exportY, halfW, 20).build());

        // 翻页按钮
        int pageY = nameY - 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("▲ 上翻"), button -> {
            scrollOffset = Math.max(0, scrollOffset - ITEMS_PER_PAGE);
        }).dimensions(leftX, pageY, totalW / 2 - 2, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("▼ 下翻"), button -> {
            if (scrollOffset + ITEMS_PER_PAGE < blockList.size()) {
                scrollOffset += ITEMS_PER_PAGE;
            }
        }).dimensions(cx + 2, pageY, totalW / 2 - 2, 20).build());

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> {
            this.client.setScreen(this.parent);
        }).dimensions(cx - 50, this.height - 30, 100, 20).build());
    }

    private void doExport() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "exported_blueprint";

        String blueprintText = SelectionAnalyzer.exportBlueprint(result, name);

        // 保存到 architect-docs/ 目录
        Path dir = Paths.get("architect-docs");
        // 运行时可能在 run/ 目录下
        if (!Files.isDirectory(dir)) {
            dir = Paths.get("..").resolve("architect-docs");
        }
        if (!Files.isDirectory(dir)) {
            try { Files.createDirectories(dir); } catch (IOException ignored) {}
        }

        String fileName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".txt";
        Path filePath = dir.resolve(fileName);

        try {
            Files.writeString(filePath, blueprintText, StandardCharsets.UTF_8);
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                        Text.literal("§a[选区] 蓝图已导出: " + filePath.toAbsolutePath()), false);
            }
        } catch (IOException e) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                        Text.literal("§c[选区] 导出失败: " + e.getMessage()), false);
            }
        }
    }

    private void doExportNbt() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "exported_blueprint";

        // 保存到 nbts/ 目录
        Path dir = Paths.get("nbts");
        if (!Files.isDirectory(dir)) {
            dir = Paths.get("..").resolve("nbts");
        }
        if (!Files.isDirectory(dir)) {
            try { Files.createDirectories(dir); } catch (IOException ignored) {}
        }

        String fileName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".nbt";
        File file = dir.resolve(fileName).toFile();

        try {
            SelectionAnalyzer.exportNbt(result, file);
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                        Text.literal("§a[选区] NBT 已导出: " + file.getAbsolutePath()), false);
            }
        } catch (IOException e) {
            if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(
                        Text.literal("§c[选区] NBT 导出失败: " + e.getMessage()), false);
            }
        }
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
        context.drawTextWithShadow(this.textRenderer, "--- 方块统计 ---", leftX, listY, 0xFFFF55);
        listY += 12;

        int end = Math.min(scrollOffset + ITEMS_PER_PAGE, blockList.size());
        for (int i = scrollOffset; i < end; i++) {
            Map.Entry<String, Integer> entry = blockList.get(i);
            String line = String.format("%-30s x%d", entry.getKey(), entry.getValue());
            // 截断过长的行
            if (line.length() > 40) line = line.substring(0, 40);
            context.drawTextWithShadow(this.textRenderer, line, leftX, listY, 0xDDDDDD);
            listY += 11;
        }

        // 页码
        int totalPages = (blockList.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE;
        int currentPage = scrollOffset / ITEMS_PER_PAGE + 1;
        if (totalPages > 1) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    String.format("第 %d/%d 页", currentPage, totalPages),
                    cx, listY + 4, 0x888888);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
