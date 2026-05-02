package com.example.helloworld.blueprint;

import com.example.helloworld.HelloWorldMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * TXT 结构设计图浏览器界面，与 NbtBrowserScreen 类似。
 * 左侧：文件夹/文件列表（可搜索、可滚动、可进入子文件夹）
 * 右侧：选中文件的详细信息（蓝图名称、层数、图例数等）
 * 底部：操作按钮
 */
public class TxtBrowserScreen extends Screen {

    private static final Path TXTS_DIR = Path.of("../txts");

    private final Screen parent;

    /** 列表项：可以是文件夹或文件 */
    private static class ListEntry {
        final String name;       // 显示名称
        final String fullPath;   // 相对于 TXTS_DIR 的完整路径
        final boolean isFolder;

        ListEntry(String name, String fullPath, boolean isFolder) {
            this.name = name;
            this.fullPath = fullPath;
            this.isFolder = isFolder;
        }
    }

    // 当前浏览的子目录（相对于 TXTS_DIR，空字符串表示根目录）
    private String currentDir = "";

    // 当前目录下的条目
    private List<ListEntry> currentEntries = new ArrayList<>();
    // 过滤后的条目
    private List<ListEntry> filteredEntries = new ArrayList<>();

    private TextFieldWidget searchField;
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private List<String> detailLines = new ArrayList<>();

    // 删除确认状态
    private boolean confirmingDelete = false;
    private ButtonWidget deleteButton;

    // 布局
    private int listLeft, listTop, listWidth, listHeight;
    private int detailLeft, detailTop, detailWidth, detailHeight;
    private static final int ITEM_HEIGHT = 14;

    public TxtBrowserScreen(Screen parent) {
        super(Text.literal("TXT 结构设计图浏览器"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int margin = 10;
        int topBarHeight = 30;
        int bottomBarHeight = 30;

        listLeft = margin;
        listTop = margin + topBarHeight;
        listWidth = (this.width - margin * 3) / 2;
        listHeight = this.height - listTop - bottomBarHeight - margin;

        detailLeft = listLeft + listWidth + margin;
        detailTop = listTop;
        detailWidth = this.width - detailLeft - margin;
        detailHeight = listHeight;

        // 搜索框
        searchField = new TextFieldWidget(this.textRenderer, listLeft, margin + 6, listWidth - 2, 18, Text.literal("搜索..."));
        searchField.setPlaceholder(Text.literal("§7搜索文件名..."));
        searchField.setMaxLength(100);
        searchField.setChangedListener(this::onSearchChanged);
        this.addDrawableChild(searchField);

        // 底部按钮
        int btnY = this.height - bottomBarHeight - margin + 5;
        int btnWidth = 80;
        int btnSpacing = 6;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("放置结构"),
                button -> placeSelected())
                .dimensions(listLeft, btnY, btnWidth, 20)
                .build()
        );

        deleteButton = ButtonWidget.builder(
                Text.literal("删除"),
                button -> onDeleteClicked())
                .dimensions(listLeft + btnWidth + btnSpacing, btnY, 60, 20)
                .build();
        this.addDrawableChild(deleteButton);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("刷新"),
                button -> refreshCurrentDir())
                .dimensions(listLeft + btnWidth + btnSpacing + 60 + btnSpacing, btnY, 60, 20)
                .build()
        );

        int manageBtnX = listLeft + btnWidth + btnSpacing + 60 + btnSpacing + 60 + btnSpacing;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("管理文件"),
                button -> openTxtsFolder())
                .dimensions(manageBtnX, btnY, 70, 20)
                .build()
        );

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                button -> this.client.setScreen(this.parent))
                .dimensions(this.width - margin - btnWidth, btnY, btnWidth, 20)
                .build()
        );

        refreshCurrentDir();
    }

    /** 扫描当前目录，列出子文件夹和 .txt 文件 */
    private void refreshCurrentDir() {
        currentEntries.clear();
        Path dirPath = currentDir.isEmpty() ? TXTS_DIR : TXTS_DIR.resolve(currentDir);
        File dir = dirPath.toFile();

        if (!dir.exists() || !dir.isDirectory()) {
            applyFilter();
            return;
        }

        File[] children = dir.listFiles();
        if (children == null) {
            applyFilter();
            return;
        }

        List<ListEntry> folders = new ArrayList<>();
        List<ListEntry> files = new ArrayList<>();

        for (File child : children) {
            String relativePath = currentDir.isEmpty()
                    ? child.getName()
                    : currentDir + "/" + child.getName();

            if (child.isDirectory()) {
                long txtCount = countTxtFiles(child);
                String label = txtCount > 0
                        ? child.getName() + "/ §7(" + txtCount + ")"
                        : child.getName() + "/ §7(空)";
                folders.add(new ListEntry(label, relativePath, true));
            } else if (child.getName().endsWith(".txt")) {
                files.add(new ListEntry(child.getName(), relativePath, false));
            }
        }

        folders.sort(Comparator.comparing(e -> e.name.toLowerCase()));
        files.sort(Comparator.comparing(e -> e.name.toLowerCase()));

        currentEntries.addAll(folders);
        currentEntries.addAll(files);
        applyFilter();
    }

    private long countTxtFiles(File dir) {
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            return walk.filter(Files::isRegularFile)
                       .filter(p -> p.toString().endsWith(".txt"))
                       .count();
        } catch (IOException e) {
            return 0;
        }
    }

    private void onSearchChanged(String text) {
        applyFilter();
    }

    private void applyFilter() {
        String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        filteredEntries.clear();

        for (ListEntry entry : currentEntries) {
            if (query.isEmpty() || entry.name.toLowerCase().contains(query)) {
                filteredEntries.add(entry);
            }
        }

        selectedIndex = filteredEntries.isEmpty() ? -1 : 0;
        scrollOffset = 0;
        updateDetail();
    }

    private void updateDetail() {
        detailLines.clear();
        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) {
            detailLines.add("§7未选择文件");
            return;
        }

        ListEntry entry = filteredEntries.get(selectedIndex);

        if (entry.isFolder) {
            detailLines.add("§e文件夹:");
            detailLines.add("§f  " + entry.fullPath);
            detailLines.add("");
            Path folderPath = TXTS_DIR.resolve(entry.fullPath);
            try (Stream<Path> walk = Files.walk(folderPath)) {
                long count = walk.filter(Files::isRegularFile)
                                 .filter(p -> p.toString().endsWith(".txt"))
                                 .count();
                detailLines.add("§e包含: §f" + count + " 个 TXT 文件");
            } catch (IOException e) {
                detailLines.add("§c无法读取文件夹");
            }
            detailLines.add("");
            detailLines.add("§7双击或按 Enter 进入文件夹");
            return;
        }

        File file = TXTS_DIR.resolve(entry.fullPath).toFile();
        if (!file.exists()) {
            detailLines.add("§c文件不存在");
            return;
        }

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            BlueprintData data = BlueprintParser.parse(content);

            detailLines.add("§e名称:");
            detailLines.add("§f  " + data.getName());
            detailLines.add("");
            detailLines.add("§e文件路径:");
            detailLines.add("§f  " + entry.fullPath);
            detailLines.add("");
            detailLines.add("§e文件大小: §f" + file.length() + " bytes");

            if (data.isV2()) {
                // ---- V2 格式详情 ----
                detailLines.add("§e格式: §fMCBLUEPRINT v2");
                detailLines.add("§e尺寸: §f" + data.getSizeX() + " x " + data.getSizeY() + " x " + data.getSizeZ());
                detailLines.add("§e方块总数: §f" + data.getBlocks3d().size());

                // 统计不同方块种类
                java.util.Set<String> blockTypes = new java.util.LinkedHashSet<>();
                for (BlueprintData.BlockEntry3D b : data.getBlocks3d()) {
                    blockTypes.add(b.getBlockId());
                }
                detailLines.add("§e方块种类: §f" + blockTypes.size() + " 种");

                detailLines.add("");
                detailLines.add("§e方块列表:");
                int shown = 0;
                for (String blockId : blockTypes) {
                    detailLines.add("§7  §f" + blockId.replace("_", " "));
                    if (++shown >= 20) {
                        detailLines.add("§7  ...");
                        break;
                    }
                }
            } else {
                // ---- V1 格式详情 ----
                detailLines.add("§e格式: §f旧版字符网格");
                detailLines.add("§e层数: §f" + data.getLayers().size());
                detailLines.add("§e图例数: §f" + data.getLegend().size() + " 种方块");

                // 计算总方块数（非空格字符）
                int totalBlocks = 0;
                for (char[][] layer : data.getLayers()) {
                    for (char[] row : layer) {
                        for (char c : row) {
                            if (c != ' ' && data.getLegend().containsKey(c)) {
                                totalBlocks++;
                            }
                        }
                    }
                }
                detailLines.add("§e方块总数: §f" + totalBlocks);

                // 显示尺寸（宽x高x深）
                if (!data.getLayers().isEmpty()) {
                    char[][] firstLayer = data.getLayers().get(0);
                    int depth = firstLayer.length;
                    int width = depth > 0 ? firstLayer[0].length : 0;
                    int height = data.getLayers().size();
                    detailLines.add("§e尺寸: §f" + width + " x " + height + " x " + depth);
                }

                detailLines.add("");
                detailLines.add("§e图例:");
                for (Map.Entry<Character, BlueprintData.BlockEntry> legendEntry : data.getLegend().entrySet()) {
                    String blockName = legendEntry.getValue().getBlockId().replace("_", " ");
                    detailLines.add("§7  '" + legendEntry.getKey() + "' §8= §f" + blockName);
                    if (detailLines.size() > 25) {
                        detailLines.add("§7  ...");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            detailLines.add("§c解析失败: " + e.getMessage());
        }
    }

    /** 进入选中的文件夹 */
    private void enterFolder() {
        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) return;
        ListEntry entry = filteredEntries.get(selectedIndex);
        if (!entry.isFolder) return;

        currentDir = entry.fullPath;
        searchField.setText("");
        refreshCurrentDir();
    }

    /** 返回上级目录 */
    private void goUp() {
        if (currentDir.isEmpty()) return;
        int lastSlash = currentDir.lastIndexOf('/');
        currentDir = lastSlash > 0 ? currentDir.substring(0, lastSlash) : "";
        searchField.setText("");
        refreshCurrentDir();
    }

    /** 删除按钮点击处理：第一次点击进入确认状态，第二次点击执行删除 */
    private void onDeleteClicked() {
        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) return;

        if (confirmingDelete) {
            deleteSelected();
            confirmingDelete = false;
            deleteButton.setMessage(Text.literal("删除"));
        } else {
            confirmingDelete = true;
            deleteButton.setMessage(Text.literal("§c确认删除?"));
        }
    }

    /** 重置删除确认状态 */
    private void resetDeleteConfirm() {
        if (confirmingDelete) {
            confirmingDelete = false;
            deleteButton.setMessage(Text.literal("删除"));
        }
    }

    /** 执行删除选中的文件或文件夹 */
    private void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) return;
        ListEntry entry = filteredEntries.get(selectedIndex);
        Path targetPath = TXTS_DIR.resolve(entry.fullPath);

        try {
            if (entry.isFolder) {
                deleteDirectoryRecursively(targetPath);
            } else {
                Files.deleteIfExists(targetPath);
            }
            refreshCurrentDir();
        } catch (IOException e) {
            detailLines.clear();
            detailLines.add("§c删除失败: " + e.getMessage());
        }
    }

    /** 递归删除目录及其内容 */
    private void deleteDirectoryRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        }
    }

    /** 用系统文件管理器打开 txts 文件夹 */
    private void openTxtsFolder() {
        try {
            File folder = TXTS_DIR.toAbsolutePath().normalize().toFile();
            if (!folder.exists()) {
                folder.mkdirs();
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder);
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"explorer", folder.getAbsolutePath()});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", folder.getAbsolutePath()});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", folder.getAbsolutePath()});
                }
            }
        } catch (IOException e) {
            detailLines.clear();
            detailLines.add("§c无法打开文件管理器: " + e.getMessage());
        }
    }

    private void placeSelected() {
        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) return;
        ListEntry entry = filteredEntries.get(selectedIndex);
        if (entry.isFolder) {
            enterFolder();
            return;
        }

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(entry.fullPath);
        ClientPlayNetworking.send(HelloWorldMod.PLACE_TXT_PACKET, buf);
        this.client.setScreen(null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // 标题 + 当前路径
        String titleText = "TXT 结构设计图浏览器";
        if (!currentDir.isEmpty()) {
            titleText += " §7- " + currentDir;
        }
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(titleText), this.width / 2, 4, 0xFFFFFF);

        // 左侧列表背景
        context.fill(listLeft - 1, listTop - 1, listLeft + listWidth + 1, listTop + listHeight + 1, 0xFFA0A0A0);
        context.fill(listLeft, listTop, listLeft + listWidth, listTop + listHeight, 0xFF000000);

        // 右侧详情背景
        context.fill(detailLeft - 1, detailTop - 1, detailLeft + detailWidth + 1, detailTop + detailHeight + 1, 0xFFA0A0A0);
        context.fill(detailLeft, detailTop, detailLeft + detailWidth, detailTop + detailHeight, 0xFF000000);

        // 渲染 ".." 返回上级（如果不在根目录）
        int renderStartY = listTop;
        boolean hasParent = !currentDir.isEmpty();

        if (hasParent && scrollOffset == 0) {
            int itemY = renderStartY;
            boolean hovered = mouseX >= listLeft && mouseX < listLeft + listWidth
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (hovered) {
                context.fill(listLeft, itemY, listLeft + listWidth, itemY + ITEM_HEIGHT, 0xFF303050);
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal("§e↑ .. (返回上级)"),
                    listLeft + 4, itemY + 3, 0xFFFFFF);
            renderStartY += ITEM_HEIGHT;
        }

        // 渲染文件/文件夹列表
        int visibleCount = listHeight / ITEM_HEIGHT;
        int startIdx = hasParent ? Math.max(0, scrollOffset - 1) : scrollOffset;
        int yOffset = hasParent && scrollOffset == 0 ? 1 : 0;

        for (int i = 0; i + yOffset < visibleCount && (startIdx + i) < filteredEntries.size(); i++) {
            int entryIdx = startIdx + i;
            ListEntry entry = filteredEntries.get(entryIdx);
            int itemY = listTop + (i + yOffset) * ITEM_HEIGHT;

            if (itemY + ITEM_HEIGHT > listTop + listHeight) break;

            // 选中高亮
            if (entryIdx == selectedIndex) {
                context.fill(listLeft, itemY, listLeft + listWidth, itemY + ITEM_HEIGHT, 0xFF406040);
            }
            // 鼠标悬停
            else if (mouseX >= listLeft && mouseX < listLeft + listWidth
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                context.fill(listLeft, itemY, listLeft + listWidth, itemY + ITEM_HEIGHT, 0xFF304030);
            }

            // 图标 + 名称（文件夹用橙色，TXT 文件用绿色）
            String prefix = entry.isFolder ? "§6\u25B6 " : "§a  ";
            String displayName = entry.name;
            int maxTextWidth = listWidth - 12;
            if (this.textRenderer.getWidth(prefix + displayName) > maxTextWidth) {
                while (this.textRenderer.getWidth(prefix + displayName + "...") > maxTextWidth && displayName.length() > 3) {
                    displayName = displayName.substring(0, displayName.length() - 1);
                }
                displayName = displayName + "...";
            }

            context.drawTextWithShadow(this.textRenderer, Text.literal(prefix + displayName),
                    listLeft + 4, itemY + 3, 0xFFFFFF);
        }

        // 滚动条
        int totalItems = filteredEntries.size() + (hasParent ? 1 : 0);
        if (totalItems > visibleCount) {
            int scrollBarHeight = Math.max(10, listHeight * visibleCount / totalItems);
            int maxScroll = totalItems - visibleCount;
            int scrollBarY = listTop + (listHeight - scrollBarHeight) * scrollOffset / Math.max(1, maxScroll);
            int scrollBarX = listLeft + listWidth - 4;
            context.fill(scrollBarX, listTop, scrollBarX + 4, listTop + listHeight, 0xFF202020);
            context.fill(scrollBarX, scrollBarY, scrollBarX + 4, scrollBarY + scrollBarHeight, 0xFF808080);
        }

        // 详情面板
        int detailY = detailTop + 4;
        for (String line : detailLines) {
            if (detailY + 10 > detailTop + detailHeight) break;
            context.drawTextWithShadow(this.textRenderer, Text.literal(line), detailLeft + 6, detailY, 0xFFFFFF);
            detailY += 11;
        }

        // 文件计数
        String countText = "§7" + filteredEntries.size() + " 项";
        context.drawTextWithShadow(this.textRenderer, Text.literal(countText),
                listLeft, listTop + listHeight + 3, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= listLeft && mouseX < listLeft + listWidth
                && mouseY >= listTop && mouseY < listTop + listHeight) {

            boolean hasParent = !currentDir.isEmpty();
            int visibleCount = listHeight / ITEM_HEIGHT;

            // 点击 ".." 返回上级
            if (hasParent && scrollOffset == 0 && mouseY < listTop + ITEM_HEIGHT) {
                goUp();
                return true;
            }

            // 计算点击的条目索引
            int yOffset = hasParent && scrollOffset == 0 ? 1 : 0;
            int startIdx = hasParent ? Math.max(0, scrollOffset - 1) : scrollOffset;
            int slot = (int) ((mouseY - listTop) / ITEM_HEIGHT) - yOffset;
            int clickedIdx = startIdx + slot;

            if (clickedIdx >= 0 && clickedIdx < filteredEntries.size()) {
                // 双击进入文件夹
                if (clickedIdx == selectedIndex && filteredEntries.get(clickedIdx).isFolder) {
                    enterFolder();
                    return true;
                }
                if (clickedIdx != selectedIndex) resetDeleteConfirm();
                selectedIndex = clickedIdx;
                updateDetail();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= listLeft && mouseX < listLeft + listWidth
                && mouseY >= listTop && mouseY < listTop + listHeight) {
            boolean hasParent = !currentDir.isEmpty();
            int totalItems = filteredEntries.size() + (hasParent ? 1 : 0);
            int visibleCount = listHeight / ITEM_HEIGHT;
            int maxScroll = Math.max(0, totalItems - visibleCount);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 上下键
        if (keyCode == 265) { // UP
            if (selectedIndex > 0) {
                selectedIndex--;
                resetDeleteConfirm();
                ensureVisible();
                updateDetail();
            }
            return true;
        }
        if (keyCode == 264) { // DOWN
            if (selectedIndex < filteredEntries.size() - 1) {
                selectedIndex++;
                resetDeleteConfirm();
                ensureVisible();
                updateDetail();
            }
            return true;
        }
        // Delete 键触发删除
        if (keyCode == 261 && !searchField.isFocused()) {
            onDeleteClicked();
            return true;
        }
        // Enter：文件夹则进入，文件则放置
        if (keyCode == 257 && !searchField.isFocused()) {
            if (selectedIndex >= 0 && selectedIndex < filteredEntries.size()) {
                ListEntry entry = filteredEntries.get(selectedIndex);
                if (entry.isFolder) {
                    enterFolder();
                } else {
                    placeSelected();
                }
                return true;
            }
        }
        // Backspace（非搜索框聚焦时）返回上级
        if (keyCode == 259 && !searchField.isFocused() && !currentDir.isEmpty()) {
            goUp();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void ensureVisible() {
        boolean hasParent = !currentDir.isEmpty();
        int visibleCount = listHeight / ITEM_HEIGHT;
        int offset = hasParent ? 1 : 0;
        if (selectedIndex < scrollOffset - offset) {
            scrollOffset = Math.max(0, selectedIndex);
        } else if (selectedIndex >= scrollOffset + visibleCount - offset) {
            scrollOffset = selectedIndex - visibleCount + offset + 1;
        }
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
