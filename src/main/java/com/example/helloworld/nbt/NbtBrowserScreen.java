package com.example.helloworld.nbt;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * NBT 结构浏览器界面，类似 Litematica 的原理图管理器。
 * 左侧：文件夹/文件列表（可搜索、可滚动、可进入子文件夹）
 * 右侧：选中文件的详细信息
 * 底部：操作按钮
 */
public class NbtBrowserScreen extends Screen {

    private static final Path NBTS_DIR = com.example.helloworld.ModPaths.getNbtsDir();

    private final Screen parent;

    /** 列表项：可以是文件夹或文件 */
    private static class ListEntry {
        final String name;       // 显示名称
        final String fullPath;   // 相对于 NBTS_DIR 的完整路径（文件夹不含尾 /）
        final boolean isFolder;

        ListEntry(String name, String fullPath, boolean isFolder) {
            this.name = name;
            this.fullPath = fullPath;
            this.isFolder = isFolder;
        }
    }

    // 当前浏览的子目录（相对于 NBTS_DIR，空字符串表示根目录）
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

    public NbtBrowserScreen(Screen parent) {
        super(Text.literal(com.example.helloworld.I18n.get("NBT 结构浏览器", "NBT Structure Browser")));
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
        searchField = new TextFieldWidget(this.textRenderer, listLeft, margin + 6, listWidth - 2, 18, Text.literal(com.example.helloworld.I18n.get("搜索...", "Search...")));
        searchField.setPlaceholder(Text.literal(com.example.helloworld.I18n.get("§7搜索文件名...", "§7Search filename...")));
        searchField.setMaxLength(100);
        searchField.setChangedListener(this::onSearchChanged);
        this.addDrawableChild(searchField);

        // 底部按钮
        int btnY = this.height - bottomBarHeight - margin + 5;
        int btnWidth = 80;
        int btnSpacing = 6;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.get("放置结构", "Place")),
                button -> placeSelected())
                .dimensions(listLeft, btnY, btnWidth, 20)
                .build()
        );

        deleteButton = ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.get("删除", "Delete")),
                button -> onDeleteClicked())
                .dimensions(listLeft + btnWidth + btnSpacing, btnY, 60, 20)
                .build();
        this.addDrawableChild(deleteButton);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.get("刷新", "Refresh")),
                button -> refreshCurrentDir())
                .dimensions(listLeft + btnWidth + btnSpacing + 60 + btnSpacing, btnY, 60, 20)
                .build()
        );

        int createFolderX = listLeft + btnWidth + btnSpacing + 60 + btnSpacing + 60 + btnSpacing;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.get("管理文件", "Files")),
                button -> openNbtsFolder())
                .dimensions(createFolderX, btnY, 70, 20)
                .build()
        );

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.get("返回", "Back")),
                button -> this.client.setScreen(this.parent))
                .dimensions(this.width - margin - btnWidth, btnY, btnWidth, 20)
                .build()
        );

        refreshCurrentDir();
    }

    /** 扫描当前目录，列出子文件夹和 .nbt 文件 */
    private void refreshCurrentDir() {
        currentEntries.clear();
        Path dirPath = currentDir.isEmpty() ? NBTS_DIR : NBTS_DIR.resolve(currentDir);
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

        // 先收集文件夹
        List<ListEntry> folders = new ArrayList<>();
        List<ListEntry> files = new ArrayList<>();

        for (File child : children) {
            String relativePath = currentDir.isEmpty()
                    ? child.getName()
                    : currentDir + "/" + child.getName();

            if (child.isDirectory()) {
                // 统计文件夹里的 .nbt 文件数量
                long nbtCount = countNbtFiles(child);
                // 显示所有文件夹（包括空文件夹，方便用户管理）
                String label = nbtCount > 0
                        ? child.getName() + "/ §7(" + nbtCount + ")"
                        : child.getName() + "/ §7(空)";
                folders.add(new ListEntry(label, relativePath, true));
            } else if (child.getName().endsWith(".nbt")) {
                files.add(new ListEntry(child.getName(), relativePath, false));
            }
        }

        folders.sort(Comparator.comparing(e -> e.name.toLowerCase()));
        files.sort(Comparator.comparing(e -> e.name.toLowerCase()));

        currentEntries.addAll(folders);
        currentEntries.addAll(files);
        applyFilter();
    }

    private long countNbtFiles(File dir) {
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            return walk.filter(Files::isRegularFile)
                       .filter(p -> p.toString().endsWith(".nbt"))
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
            detailLines.add("§7" + com.example.helloworld.I18n.get("未选择文件", "No file selected"));
            return;
        }

        ListEntry entry = filteredEntries.get(selectedIndex);

        if (entry.isFolder) {
            detailLines.add("§e" + com.example.helloworld.I18n.get("文件夹:", "Folder:"));
            detailLines.add("§f  " + entry.fullPath);
            detailLines.add("");
            // 统计文件夹内容
            Path folderPath = NBTS_DIR.resolve(entry.fullPath);
            try (Stream<Path> walk = Files.walk(folderPath)) {
                long count = walk.filter(Files::isRegularFile)
                                 .filter(p -> p.toString().endsWith(".nbt"))
                                 .count();
                detailLines.add("§e" + com.example.helloworld.I18n.get("包含: §f" + count + " 个 NBT 文件", "Contains: §f" + count + " NBT files"));
            } catch (IOException e) {
                detailLines.add("§c" + com.example.helloworld.I18n.get("无法读取文件夹", "Cannot read folder"));
            }
            detailLines.add("");
            detailLines.add("§7" + com.example.helloworld.I18n.get("双击或按 Enter 进入文件夹", "Double-click or press Enter to open"));
            return;
        }

        File file = NBTS_DIR.resolve(entry.fullPath).toFile();
        if (!file.exists()) {
            detailLines.add("§c" + com.example.helloworld.I18n.get("文件不存在", "File not found"));
            return;
        }

        try {
            NbtStructureParser.StructureData data = NbtStructureParser.parse(file);
            detailLines.add("§e" + com.example.helloworld.I18n.get("名称:", "Name:"));
            detailLines.add("§f  " + entry.fullPath);
            detailLines.add("");
            detailLines.add("§e" + com.example.helloworld.I18n.get("尺寸: ", "Size: ") + "§f" + data.sizeX + " x " + data.sizeY + " x " + data.sizeZ);
            detailLines.add("§e" + com.example.helloworld.I18n.get("方块数: ", "Blocks: ") + "§f" + data.blocks.size());
            detailLines.add("§e" + com.example.helloworld.I18n.get("方块类型: ", "Block types: ") + "§f" + data.palette.size() + com.example.helloworld.I18n.get(" 种", ""));
            detailLines.add("§e" + com.example.helloworld.I18n.get("文件大小: ", "File size: ") + "§f" + file.length() + " bytes");
            detailLines.add("§e" + com.example.helloworld.I18n.get("数据版本: ", "Data version: ") + "§f" + data.dataVersion);
            detailLines.add("");
            detailLines.add("§e" + com.example.helloworld.I18n.get("方块列表:", "Block list:"));

            Map<Integer, Integer> counts = new HashMap<>();
            for (NbtStructureParser.BlockEntry block : data.blocks) {
                counts.merge(block.paletteIndex, 1, Integer::sum);
            }
            for (int i = 0; i < data.palette.size(); i++) {
                NbtStructureParser.PaletteEntry pe = data.palette.get(i);
                if (pe.blockName.equals("minecraft:air")) continue;
                int count = counts.getOrDefault(i, 0);
                String name = pe.blockName.replace("minecraft:", "");
                detailLines.add("§7  " + name + " §8x" + count);
            }
        } catch (Exception e) {
            detailLines.add("§c" + com.example.helloworld.I18n.get("解析失败: ", "Parse failed: ") + e.getMessage());
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
            deleteButton.setMessage(Text.literal(com.example.helloworld.I18n.get("删除", "Delete")));
        } else {
            confirmingDelete = true;
            ListEntry entry = filteredEntries.get(selectedIndex);
            String typeName = entry.isFolder ? com.example.helloworld.I18n.get("文件夹", "folder") : com.example.helloworld.I18n.get("文件", "file");
            deleteButton.setMessage(Text.literal(com.example.helloworld.I18n.get("§c确认删除?", "§cConfirm?")));
        }
    }

    /** 重置删除确认状态 */
    private void resetDeleteConfirm() {
        if (confirmingDelete) {
            confirmingDelete = false;
            deleteButton.setMessage(Text.literal(com.example.helloworld.I18n.get("删除", "Delete")));
        }
    }

    /** 执行删除选中的文件或文件夹 */
    private void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) return;
        ListEntry entry = filteredEntries.get(selectedIndex);
        Path targetPath = NBTS_DIR.resolve(entry.fullPath);

        try {
            if (entry.isFolder) {
                deleteDirectoryRecursively(targetPath);
            } else {
                Files.deleteIfExists(targetPath);
            }
            refreshCurrentDir();
        } catch (IOException e) {
            detailLines.clear();
            detailLines.add("§c" + com.example.helloworld.I18n.get("删除失败: ", "Delete failed: ") + e.getMessage());
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

    /** 开始新建文件夹：显示输入框 */
    /** 用系统文件管理器打开 nbts 文件夹 */
    private void openNbtsFolder() {
        try {
            File folder = NBTS_DIR.toAbsolutePath().normalize().toFile();
            if (!folder.exists()) {
                folder.mkdirs();
            }
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder);
            } else {
                // 备用方案：直接调用系统命令
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
            detailLines.add("§c" + com.example.helloworld.I18n.get("无法打开文件管理器: ", "Cannot open file manager: ") + e.getMessage());
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
        ClientPlayNetworking.send(HelloWorldMod.PLACE_NBT_PACKET, buf);
        this.client.setScreen(null);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        // 标题 + 当前路径
        String titleText = com.example.helloworld.I18n.get("NBT 结构浏览器", "NBT Structure Browser");
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
        int itemIndex = 0;
        boolean hasParent = !currentDir.isEmpty();

        if (hasParent && scrollOffset == 0) {
            // 渲染 ".." 条目
            int itemY = renderStartY;
            boolean hovered = mouseX >= listLeft && mouseX < listLeft + listWidth
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT;
            if (hovered) {
                context.fill(listLeft, itemY, listLeft + listWidth, itemY + ITEM_HEIGHT, 0xFF303050);
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal("§e↑ .. (" + com.example.helloworld.I18n.get("返回上级", "Go up") + ")"),
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
                context.fill(listLeft, itemY, listLeft + listWidth, itemY + ITEM_HEIGHT, 0xFF4040A0);
            }
            // 鼠标悬停
            else if (mouseX >= listLeft && mouseX < listLeft + listWidth
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                context.fill(listLeft, itemY, listLeft + listWidth, itemY + ITEM_HEIGHT, 0xFF303050);
            }

            // 图标 + 名称
            String prefix = entry.isFolder ? "§6\u25B6 " : "§b  ";
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
        String countText = "§7" + filteredEntries.size() + " " + com.example.helloworld.I18n.get("项", "items");
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
        if (keyCode == 261 && !searchField.isFocused()) { // DELETE key
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
