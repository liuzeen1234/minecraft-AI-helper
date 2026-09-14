package com.example.helloworld.structure;

import com.example.helloworld.HelloWorldMod;
import com.example.helloworld.blueprint.BlueprintData;
import com.example.helloworld.blueprint.BlueprintParser;
import com.example.helloworld.nbt.NbtStructureParser;
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
 * 统一结构浏览器：根目录为 structures/，其下含 nbts/ 与 txts/ 子文件夹。
 * 在同一界面中浏览、搜索、进入子文件夹，选中 .nbt 或 .txt 文件后放置。
 * 放置时根据文件扩展名自动选择对应的网络包（.nbt → PLACE_NBT，.txt → PLACE_TXT），
 * 并把路径转换为相对于对应子目录（nbts/ 或 txts/）的相对路径再发送给服务端。
 */
public class StructureBrowserScreen extends Screen {

    private static final Path STRUCTURES_DIR = com.example.helloworld.ModPaths.getStructuresDir();

    private final Screen parent;

    /** 列表项：可以是文件夹或文件 */
    private static class ListEntry {
        final String name;       // 显示名称
        final String fullPath;   // 相对于 STRUCTURES_DIR 的完整路径（文件夹不含尾 /）
        final boolean isFolder;

        ListEntry(String name, String fullPath, boolean isFolder) {
            this.name = name;
            this.fullPath = fullPath;
            this.isFolder = isFolder;
        }

        boolean isNbt() { return !isFolder && name.toLowerCase().endsWith(".nbt"); }
        boolean isTxt() { return !isFolder && name.toLowerCase().endsWith(".txt"); }
    }

    // 当前浏览的子目录（相对于 STRUCTURES_DIR，空字符串表示根目录）
    private String currentDir = "";

    private List<ListEntry> currentEntries = new ArrayList<>();
    private List<ListEntry> filteredEntries = new ArrayList<>();

    private TextFieldWidget searchField;
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private List<String> detailLines = new ArrayList<>();

    private boolean confirmingDelete = false;
    private ButtonWidget deleteButton;

    private int listLeft, listTop, listWidth, listHeight;
    private int detailLeft, detailTop, detailWidth, detailHeight;
    private static final int ITEM_HEIGHT = 14;

    public StructureBrowserScreen(Screen parent) {
        super(Text.literal(com.example.helloworld.I18n.tr("structurebrowser.title")));
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
        searchField = new TextFieldWidget(this.textRenderer, listLeft, margin + 6, listWidth - 2, 18, Text.literal(com.example.helloworld.I18n.tr("structurebrowser.search")));
        searchField.setPlaceholder(Text.literal(com.example.helloworld.I18n.tr("structurebrowser.search.placeholder")));
        searchField.setMaxLength(100);
        searchField.setChangedListener(this::onSearchChanged);
        this.addDrawableChild(searchField);

        // 底部按钮
        int btnY = this.height - bottomBarHeight - margin + 5;
        int btnWidth = 80;
        int btnSpacing = 6;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.tr("structurebrowser.button.place")),
                button -> placeSelected())
                .dimensions(listLeft, btnY, btnWidth, 20)
                .build()
        );

        deleteButton = ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.tr("structurebrowser.button.delete")),
                button -> onDeleteClicked())
                .dimensions(listLeft + btnWidth + btnSpacing, btnY, 60, 20)
                .build();
        this.addDrawableChild(deleteButton);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.tr("structurebrowser.button.refresh")),
                button -> refreshCurrentDir())
                .dimensions(listLeft + btnWidth + btnSpacing + 60 + btnSpacing, btnY, 60, 20)
                .build()
        );

        int manageBtnX = listLeft + btnWidth + btnSpacing + 60 + btnSpacing + 60 + btnSpacing;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.tr("structurebrowser.button.files")),
                button -> openStructuresFolder())
                .dimensions(manageBtnX, btnY, 70, 20)
                .build()
        );

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.tr("structurebrowser.button.back")),
                button -> this.client.setScreen(this.parent))
                .dimensions(this.width - margin - btnWidth, btnY, btnWidth, 20)
                .build()
        );

        refreshCurrentDir();
    }

    /** 扫描当前目录，列出子文件夹以及 .nbt / .txt 文件 */
    private void refreshCurrentDir() {
        currentEntries.clear();
        Path dirPath = currentDir.isEmpty() ? STRUCTURES_DIR : STRUCTURES_DIR.resolve(currentDir);
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
                long count = countStructureFiles(child);
                String label = count > 0
                        ? child.getName() + "/ §7(" + count + ")"
                        : child.getName() + "/ §7(空)";
                folders.add(new ListEntry(label, relativePath, true));
            } else {
                String lower = child.getName().toLowerCase();
                if (lower.endsWith(".nbt") || lower.endsWith(".txt")) {
                    files.add(new ListEntry(child.getName(), relativePath, false));
                }
            }
        }

        folders.sort(Comparator.comparing(e -> e.name.toLowerCase()));
        files.sort(Comparator.comparing(e -> e.name.toLowerCase()));

        currentEntries.addAll(folders);
        currentEntries.addAll(files);
        applyFilter();
    }

    private long countStructureFiles(File dir) {
        try (Stream<Path> walk = Files.walk(dir.toPath())) {
            return walk.filter(Files::isRegularFile)
                       .filter(p -> {
                           String n = p.toString().toLowerCase();
                           return n.endsWith(".nbt") || n.endsWith(".txt");
                       })
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
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.noselection"));
            return;
        }

        ListEntry entry = filteredEntries.get(selectedIndex);

        if (entry.isFolder) {
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.folder"));
            detailLines.add("§f  " + entry.fullPath);
            detailLines.add("");
            Path folderPath = STRUCTURES_DIR.resolve(entry.fullPath);
            try (Stream<Path> walk = Files.walk(folderPath)) {
                long count = walk.filter(Files::isRegularFile)
                                 .filter(p -> {
                                     String n = p.toString().toLowerCase();
                                     return n.endsWith(".nbt") || n.endsWith(".txt");
                                 })
                                 .count();
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.contains", count));
            } catch (IOException e) {
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.folder.unreadable"));
            }
            detailLines.add("");
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.folder.hint"));
            return;
        }

        File file = STRUCTURES_DIR.resolve(entry.fullPath).toFile();
        if (!file.exists()) {
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.filenotfound"));
            return;
        }

        if (entry.isNbt()) {
            updateNbtDetail(entry, file);
        } else {
            updateTxtDetail(entry, file);
        }
    }

    private void updateNbtDetail(ListEntry entry, File file) {
        try {
            NbtStructureParser.StructureData data = NbtStructureParser.parse(file);
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.type.nbt"));
            detailLines.add("§f  " + entry.fullPath);
            detailLines.add("");
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.size", data.sizeX, data.sizeY, data.sizeZ));
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.blocks", data.blocks.size()));
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.blocktypes", data.palette.size()));
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.filesize", file.length()));
            detailLines.add("");
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.blocklist"));

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
                if (detailLines.size() > 28) {
                    detailLines.add("§7  ...");
                    break;
                }
            }
        } catch (Exception e) {
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.parsefailed", e.getMessage()));
        }
    }

    private void updateTxtDetail(ListEntry entry, File file) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            BlueprintData data = BlueprintParser.parse(content);

            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.type.txt"));
            detailLines.add("§f  " + data.getName());
            detailLines.add("");
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.filepath"));
            detailLines.add("§f  " + entry.fullPath);
            detailLines.add("");
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.filesize", file.length()));

            if (data.isV2()) {
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.format.v2"));
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.size", data.getSizeX(), data.getSizeY(), data.getSizeZ()));
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.totalblocks", data.getBlocks3d().size()));

                Set<String> blockTypes = new LinkedHashSet<>();
                for (BlueprintData.BlockEntry3D b : data.getBlocks3d()) {
                    blockTypes.add(b.getBlockId());
                }
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.blocktypes", blockTypes.size()));
                detailLines.add("");
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.blocklist"));
                int shown = 0;
                for (String blockId : blockTypes) {
                    detailLines.add("§7  §f" + blockId.replace("_", " "));
                    if (++shown >= 20) {
                        detailLines.add("§7  ...");
                        break;
                    }
                }
            } else {
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.format.v1"));
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.layers", data.getLayers().size()));
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.legendcount", data.getLegend().size()));

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
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.totalblocks", totalBlocks));

                if (!data.getLayers().isEmpty()) {
                    char[][] firstLayer = data.getLayers().get(0);
                    int depth = firstLayer.length;
                    int width = depth > 0 ? firstLayer[0].length : 0;
                    int height = data.getLayers().size();
                    detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.size", width, height, depth));
                }

                detailLines.add("");
                detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.legend"));
                for (Map.Entry<Character, BlueprintData.BlockEntry> legendEntry : data.getLegend().entrySet()) {
                    String blockName = legendEntry.getValue().getBlockId().replace("_", " ");
                    detailLines.add("§7  '" + legendEntry.getKey() + "' §8= §f" + blockName);
                    if (detailLines.size() > 28) {
                        detailLines.add("§7  ...");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.parsefailed", e.getMessage()));
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

    private void onDeleteClicked() {
        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) return;

        if (confirmingDelete) {
            deleteSelected();
            confirmingDelete = false;
            deleteButton.setMessage(Text.literal(com.example.helloworld.I18n.tr("structurebrowser.button.delete")));
        } else {
            confirmingDelete = true;
            deleteButton.setMessage(Text.literal(com.example.helloworld.I18n.tr("structurebrowser.button.confirmdelete")));
        }
    }

    private void resetDeleteConfirm() {
        if (confirmingDelete) {
            confirmingDelete = false;
            deleteButton.setMessage(Text.literal(com.example.helloworld.I18n.tr("structurebrowser.button.delete")));
        }
    }

    private void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) return;
        ListEntry entry = filteredEntries.get(selectedIndex);
        Path targetPath = STRUCTURES_DIR.resolve(entry.fullPath);

        try {
            if (entry.isFolder) {
                deleteDirectoryRecursively(targetPath);
            } else {
                Files.deleteIfExists(targetPath);
            }
            refreshCurrentDir();
        } catch (IOException e) {
            detailLines.clear();
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.deletefailed", e.getMessage()));
        }
    }

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

    /** 用系统文件管理器打开 structures 文件夹 */
    private void openStructuresFolder() {
        try {
            File folder = STRUCTURES_DIR.toAbsolutePath().normalize().toFile();
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
            detailLines.add(com.example.helloworld.I18n.tr("structurebrowser.detail.openfoldererror", e.getMessage()));
        }
    }

    /**
     * 放置选中的结构。文件夹则进入；文件则根据扩展名选择对应的网络包。
     * 由于服务端分别以 nbts/ 和 txts/ 为根解析路径，这里需要把相对于 structures/
     * 的路径去掉前导的 nbts/ 或 txts/ 段后再发送。
     */
    private void placeSelected() {
        if (selectedIndex < 0 || selectedIndex >= filteredEntries.size()) return;
        ListEntry entry = filteredEntries.get(selectedIndex);
        if (entry.isFolder) {
            enterFolder();
            return;
        }

        if (entry.isNbt()) {
            String relative = stripPrefix(entry.fullPath, "nbts/");
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(relative);
            ClientPlayNetworking.send(HelloWorldMod.PLACE_NBT_PACKET, buf);
            this.client.setScreen(null);
        } else if (entry.isTxt()) {
            String relative = stripPrefix(entry.fullPath, "txts/");
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(relative);
            ClientPlayNetworking.send(HelloWorldMod.PLACE_TXT_PACKET, buf);
            this.client.setScreen(null);
        }
    }

    /** 去掉路径开头的指定段（大小写不敏感），若不存在则原样返回。 */
    private static String stripPrefix(String path, String prefix) {
        String normalized = path.replace('\\', '/');
        if (normalized.toLowerCase().startsWith(prefix.toLowerCase())) {
            return normalized.substring(prefix.length());
        }
        return normalized;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        String titleText = com.example.helloworld.I18n.tr("structurebrowser.title");
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
            context.drawTextWithShadow(this.textRenderer, Text.literal(com.example.helloworld.I18n.tr("structurebrowser.list.goup")),
                    listLeft + 4, itemY + 3, 0xFFFFFF);
            renderStartY += ITEM_HEIGHT;
        }

        int visibleCount = listHeight / ITEM_HEIGHT;
        int startIdx = hasParent ? Math.max(0, scrollOffset - 1) : scrollOffset;
        int yOffset = hasParent && scrollOffset == 0 ? 1 : 0;

        for (int i = 0; i + yOffset < visibleCount && (startIdx + i) < filteredEntries.size(); i++) {
            int entryIdx = startIdx + i;
            ListEntry entry = filteredEntries.get(entryIdx);
            int itemY = listTop + (i + yOffset) * ITEM_HEIGHT;

            if (itemY + ITEM_HEIGHT > listTop + listHeight) break;

            if (entryIdx == selectedIndex) {
                context.fill(listLeft, itemY, listLeft + listWidth, itemY + ITEM_HEIGHT, 0xFF406040);
            } else if (mouseX >= listLeft && mouseX < listLeft + listWidth
                    && mouseY >= itemY && mouseY < itemY + ITEM_HEIGHT) {
                context.fill(listLeft, itemY, listLeft + listWidth, itemY + ITEM_HEIGHT, 0xFF304030);
            }

            // 图标 + 名称：文件夹橙色、NBT 青色、TXT 绿色
            String prefix;
            if (entry.isFolder) {
                prefix = "§6\u25B6 ";
            } else if (entry.isNbt()) {
                prefix = "§b  ";
            } else {
                prefix = "§a  ";
            }
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
        String countText = com.example.helloworld.I18n.tr("structurebrowser.list.count", filteredEntries.size());
        context.drawTextWithShadow(this.textRenderer, Text.literal(countText),
                listLeft, listTop + listHeight + 3, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= listLeft && mouseX < listLeft + listWidth
                && mouseY >= listTop && mouseY < listTop + listHeight) {

            boolean hasParent = !currentDir.isEmpty();

            if (hasParent && scrollOffset == 0 && mouseY < listTop + ITEM_HEIGHT) {
                goUp();
                return true;
            }

            int yOffset = hasParent && scrollOffset == 0 ? 1 : 0;
            int startIdx = hasParent ? Math.max(0, scrollOffset - 1) : scrollOffset;
            int slot = (int) ((mouseY - listTop) / ITEM_HEIGHT) - yOffset;
            int clickedIdx = startIdx + slot;

            if (clickedIdx >= 0 && clickedIdx < filteredEntries.size()) {
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
        if (keyCode == 261 && !searchField.isFocused()) { // DELETE
            onDeleteClicked();
            return true;
        }
        if (keyCode == 257 && !searchField.isFocused()) { // ENTER
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
        if (keyCode == 259 && !searchField.isFocused() && !currentDir.isEmpty()) { // BACKSPACE
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
