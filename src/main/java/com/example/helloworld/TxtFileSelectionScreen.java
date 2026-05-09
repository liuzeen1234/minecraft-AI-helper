package com.example.helloworld;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 文件选择界面：让用户从 txts/ 文件夹中选择 .txt 文件作为引用。
 * 选中的文件内容会在下次发送消息时一起发送给 AI。
 */
public class TxtFileSelectionScreen extends Screen {

    private final Screen parent;
    private final Consumer<List<String>> onConfirm; // 回调：返回选中的文件相对路径列表
    private final Set<String> preSelected; // 已经选中的文件（从上次选择继承）

    private final List<String> availableFiles = new ArrayList<>(); // 可选的 txt 文件列表
    private final Set<String> selectedFiles = new HashSet<>(); // 当前选中的文件

    private int scrollOffset = 0;
    private int visibleItems = 0;

    // 布局
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private static final int ITEM_HEIGHT = 20;
    private static final int PADDING = 10;

    public TxtFileSelectionScreen(Screen parent, Set<String> preSelected, Consumer<List<String>> onConfirm) {
        super(Text.literal("选择引用文件"));
        this.parent = parent;
        this.onConfirm = onConfirm;
        this.preSelected = preSelected != null ? preSelected : new HashSet<>();
    }

    @Override
    protected void init() {
        // 扫描 txts 文件夹
        scanTxtFiles();

        // 恢复之前的选择
        selectedFiles.addAll(preSelected);
        // 移除不存在的文件
        selectedFiles.retainAll(availableFiles);

        // 布局计算
        int margin = 20;
        listLeft = margin;
        listRight = this.width - margin;
        listTop = 40;
        listBottom = this.height - 50;
        visibleItems = (listBottom - listTop) / ITEM_HEIGHT;

        // 确认按钮
        int buttonWidth = 80;
        int buttonY = this.height - 35;
        ButtonWidget confirmButton = ButtonWidget.builder(Text.literal("确认"), button -> {
            onConfirm.accept(new ArrayList<>(selectedFiles));
            this.close();
        }).dimensions(this.width / 2 - buttonWidth - 5, buttonY, buttonWidth, 20).build();
        this.addDrawableChild(confirmButton);

        // 取消按钮
        ButtonWidget cancelButton = ButtonWidget.builder(Text.literal("取消"), button -> {
            this.close();
        }).dimensions(this.width / 2 + 5, buttonY, buttonWidth, 20).build();
        this.addDrawableChild(cancelButton);

        // 全选按钮
        ButtonWidget selectAllButton = ButtonWidget.builder(Text.literal("全选"), button -> {
            selectedFiles.addAll(availableFiles);
        }).dimensions(this.width / 2 + buttonWidth + 15, buttonY, 50, 20).build();
        this.addDrawableChild(selectAllButton);

        // 清空按钮
        ButtonWidget clearAllButton = ButtonWidget.builder(Text.literal("清空"), button -> {
            selectedFiles.clear();
        }).dimensions(this.width / 2 - buttonWidth - 65, buttonY, 50, 20).build();
        this.addDrawableChild(clearAllButton);
    }

    private void scanTxtFiles() {
        availableFiles.clear();
        // 尝试找到 txts 目录
        Path txtsDir = findTxtsDir();
        if (txtsDir == null || !Files.isDirectory(txtsDir)) {
            return;
        }

        // 递归扫描 .txt 文件
        scanDirectory(txtsDir.toFile(), txtsDir.toFile(), availableFiles);
    }

    private Path findTxtsDir() {
        return com.example.helloworld.ModPaths.getTxtsDir();
    }

    private void scanDirectory(File root, File dir, List<String> result) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(root, file, result);
            } else if (file.getName().toLowerCase().endsWith(".txt")) {
                // 计算相对路径
                String relativePath = root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
                result.add(relativePath);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, "§e选择引用文件 (txts/)", this.width / 2, 15, 0xFFFFFF55);

        // 已选数量提示
        String countText = "§7已选择 §f" + selectedFiles.size() + " §7个文件";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(countText), this.width / 2, 28, 0xFFAAAAAA);

        // 列表背景
        context.fill(listLeft, listTop, listRight, listBottom, 0xCC000000);
        // 边框
        context.fill(listLeft, listTop, listRight, listTop + 1, 0xFF444444);
        context.fill(listLeft, listBottom - 1, listRight, listBottom, 0xFF444444);
        context.fill(listLeft, listTop, listLeft + 1, listBottom, 0xFF444444);
        context.fill(listRight - 1, listTop, listRight, listBottom, 0xFF444444);

        if (availableFiles.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "§c未找到 txts/ 文件夹或其中没有 .txt 文件",
                    this.width / 2, listTop + 30, 0xFFFF5555);
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        // 启用裁剪
        context.enableScissor(listLeft + 1, listTop + 1, listRight - 1, listBottom - 1);

        int endIndex = Math.min(scrollOffset + visibleItems, availableFiles.size());
        for (int i = scrollOffset; i < endIndex; i++) {
            String fileName = availableFiles.get(i);
            boolean isSelected = selectedFiles.contains(fileName);
            int itemY = listTop + (i - scrollOffset) * ITEM_HEIGHT;

            // 高亮悬停项
            if (mouseX >= listLeft && mouseX <= listRight && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                context.fill(listLeft + 1, itemY, listRight - 1, itemY + ITEM_HEIGHT, 0x44FFFFFF);
            }

            // 选中背景
            if (isSelected) {
                context.fill(listLeft + 1, itemY, listRight - 1, itemY + ITEM_HEIGHT, 0x4455FF55);
            }

            // 复选框
            String checkbox = isSelected ? "§a☑" : "§7☐";
            context.drawTextWithShadow(this.textRenderer, Text.literal(checkbox), listLeft + PADDING, itemY + 6, 0xFFFFFFFF);

            // 文件名
            String displayName = isSelected ? "§f" + fileName : "§7" + fileName;
            context.drawTextWithShadow(this.textRenderer, Text.literal(displayName), listLeft + PADDING + 14, itemY + 6, 0xFFFFFFFF);
        }

        context.disableScissor();

        // 滚动条
        if (availableFiles.size() > visibleItems) {
            int scrollBarHeight = listBottom - listTop - 4;
            int thumbHeight = Math.max(20, scrollBarHeight * visibleItems / availableFiles.size());
            int maxScroll = availableFiles.size() - visibleItems;
            int thumbY = listTop + 2 + (scrollBarHeight - thumbHeight) * scrollOffset / Math.max(1, maxScroll);
            int scrollBarX = listRight - 5;

            context.fill(scrollBarX, listTop + 2, scrollBarX + 3, listBottom - 2, 0x44FFFFFF);
            context.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbHeight, 0xAAFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY < listBottom) {
            int clickedIndex = scrollOffset + (int) ((mouseY - listTop) / ITEM_HEIGHT);
            if (clickedIndex >= 0 && clickedIndex < availableFiles.size()) {
                String fileName = availableFiles.get(clickedIndex);
                if (selectedFiles.contains(fileName)) {
                    selectedFiles.remove(fileName);
                } else {
                    selectedFiles.add(fileName);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, availableFiles.size() - visibleItems);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
