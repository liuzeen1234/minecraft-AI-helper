package com.example.helloworld.selection;

import com.example.helloworld.HelloWorldMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 选区分析与导出界面：显示选区内方块统计信息，支持导出为蓝图文件。
 */
public class SelectionExportScreen extends Screen {

    private final Screen parent;
    private final SelectionAnalyzer.AnalysisResult result;
    private TextFieldWidget nameField;
    private TextFieldWidget pathField;

    // 分页显示方块列表
    private int scrollOffset = 0;
    private static final int ITEMS_PER_PAGE = 8;
    private List<Map.Entry<String, Integer>> blockList;

    // NBT 保存路径下的可用文件夹
    private static final Path NBTS_DIR = Path.of("../nbts");
    private List<String> availableFolders = new ArrayList<>();

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
        scanAvailableFolders();

        // 保存路径输入
        int pathY = this.height - 104;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("📁"), button -> cycleFolder())
                .dimensions(leftX, pathY, 20, 18).build());
        pathField = new TextFieldWidget(this.textRenderer, leftX + 22, pathY, totalW - 22, 18, Text.literal("路径"));
        pathField.setText("");
        pathField.setPlaceholder(Text.literal("§7保存路径（留空=nbts根目录）"));
        pathField.setMaxLength(128);
        this.addDrawableChild(pathField);

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
        String subPath = pathField.getText().trim();

        // 通过网络包请求服务端导出（服务端可完整读取 BlockEntity 数据）
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(result.min().getX());
        buf.writeInt(result.min().getY());
        buf.writeInt(result.min().getZ());
        buf.writeInt(result.max().getX());
        buf.writeInt(result.max().getY());
        buf.writeInt(result.max().getZ());
        buf.writeString(name);
        buf.writeString(subPath);
        ClientPlayNetworking.send(HelloWorldMod.EXPORT_NBT_PACKET, buf);

        String displayPath = subPath.isEmpty() ? name + ".nbt" : subPath + "/" + name + ".nbt";
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(
                    Text.literal("§7[选区] 正在服务端导出 NBT 到 " + displayPath + " ..."), false);
        }
    }

    /** 扫描 nbts 目录下的所有子文件夹 */
    private void scanAvailableFolders() {
        availableFolders.clear();
        availableFolders.add(""); // 根目录
        Path dir = NBTS_DIR;
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isDirectory)
                .filter(p -> !p.equals(dir))
                .forEach(p -> availableFolders.add(dir.relativize(p).toString().replace('\\', '/')));
        } catch (IOException ignored) {}
        Collections.sort(availableFolders);
    }

    /** 点击文件夹按钮循环切换可用路径 */
    private void cycleFolder() {
        String current = pathField.getText().trim();
        int idx = availableFolders.indexOf(current);
        int next = (idx + 1) % availableFolders.size();
        pathField.setText(availableFolders.get(next));
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
        int listMaxY = this.height - 110; // 路径输入框上方留出空间
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
