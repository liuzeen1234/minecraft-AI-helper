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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 导出选区弹窗：点击"导出选区"按钮后弹出，包含保存路径、文件名、导出TXT/NBT按钮。
 */
public class SelectionExportPopupScreen extends Screen {

    private final Screen parent;
    private final SelectionAnalyzer.AnalysisResult result;

    private TextFieldWidget pathField;
    private TextFieldWidget nameField;

    private static final Path NBTS_DIR = Path.of("../nbts");
    private List<String> availableFolders = new ArrayList<>();

    // 弹窗尺寸
    private static final int POPUP_WIDTH = 260;
    private static final int POPUP_HEIGHT = 130;

    public SelectionExportPopupScreen(Screen parent, SelectionAnalyzer.AnalysisResult result) {
        super(Text.literal("导出选区"));
        this.parent = parent;
        this.result = result;
    }

    @Override
    protected void init() {
        scanAvailableFolders();

        int cx = this.width / 2;
        int cy = this.height / 2;
        int popLeft = cx - POPUP_WIDTH / 2;
        int popTop = cy - POPUP_HEIGHT / 2;

        int fieldW = POPUP_WIDTH - 40;
        int fieldLeft = popLeft + 20;

        // 保存路径输入
        int pathY = popTop + 24;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("📁"), button -> cycleFolder())
                .dimensions(fieldLeft, pathY, 20, 18).build());
        pathField = new TextFieldWidget(this.textRenderer, fieldLeft + 22, pathY, fieldW - 22, 18, Text.literal("路径"));
        pathField.setText("");
        pathField.setPlaceholder(Text.literal("§7保存路径（留空=nbts根目录）"));
        pathField.setMaxLength(128);
        this.addDrawableChild(pathField);

        // 文件名输入
        int nameY = pathY + 24;
        nameField = new TextFieldWidget(this.textRenderer, fieldLeft, nameY, fieldW, 18, Text.literal("名称"));
        nameField.setText("exported_blueprint");
        nameField.setMaxLength(64);
        this.addDrawableChild(nameField);

        // 导出按钮行
        int btnY = nameY + 26;
        int halfW = fieldW / 2 - 2;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§a导出 TXT"), button -> doExportTxt())
                .dimensions(fieldLeft, btnY, halfW, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§b导出 NBT"), button -> doExportNbt())
                .dimensions(fieldLeft + halfW + 4, btnY, halfW, 20).build());

        // 取消按钮
        int cancelY = btnY + 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("取消"), button -> close())
                .dimensions(cx - 40, cancelY, 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 半透明背景覆盖
        this.renderBackground(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int popLeft = cx - POPUP_WIDTH / 2;
        int popTop = cy - POPUP_HEIGHT / 2;

        // 弹窗背景
        context.fill(popLeft - 2, popTop - 2, popLeft + POPUP_WIDTH + 2, popTop + POPUP_HEIGHT + 2, 0xFFAAAAAA);
        context.fill(popLeft, popTop, popLeft + POPUP_WIDTH, popTop + POPUP_HEIGHT, 0xFF000000);

        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, popTop + 8, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    private void doExportTxt() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "exported_blueprint";

        String blueprintText = SelectionAnalyzer.exportBlueprint(result, name);

        // 保存到 txts/ 目录
        Path dir = Paths.get("txts");
        if (!Files.isDirectory(dir)) {
            dir = Paths.get("..").resolve("txts");
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
        close();
    }

    private void doExportNbt() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "exported_blueprint";
        String subPath = pathField.getText().trim();

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
        close();
    }

    private void scanAvailableFolders() {
        availableFolders.clear();
        availableFolders.add("");
        Path dir = NBTS_DIR;
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isDirectory)
                .filter(p -> !p.equals(dir))
                .forEach(p -> availableFolders.add(dir.relativize(p).toString().replace('\\', '/')));
        } catch (IOException ignored) {}
        Collections.sort(availableFolders);
    }

    private void cycleFolder() {
        String current = pathField.getText().trim();
        int idx = availableFolders.indexOf(current);
        int next = (idx + 1) % availableFolders.size();
        pathField.setText(availableFolders.get(next));
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
