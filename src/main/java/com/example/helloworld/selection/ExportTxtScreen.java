package com.example.helloworld.selection;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
 * P4 - 导出 TXT 页面：填写保存路径和文件名，点击"导出.txt"执行导出。
 */
public class ExportTxtScreen extends Screen {

    private final Screen parent;
    private final SelectionAnalyzer.AnalysisResult result;

    private TextFieldWidget pathField;
    private TextFieldWidget nameField;

    private static final Path TXTS_DIR = Path.of("../txts");
    private List<String> availableFolders = new ArrayList<>();

    // 弹窗尺寸
    private static final int POPUP_WIDTH = 260;
    private static final int POPUP_HEIGHT = 130;

    public ExportTxtScreen(Screen parent, SelectionAnalyzer.AnalysisResult result) {
        super(Text.literal("导出 .txt"));
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
        int pathY = popTop + 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("📁"), button -> cycleFolder())
                .dimensions(fieldLeft, pathY, 20, 18).build());
        pathField = new TextFieldWidget(this.textRenderer, fieldLeft + 22, pathY, fieldW - 22, 18, Text.literal("路径"));
        pathField.setText("");
        pathField.setPlaceholder(Text.literal("§7保存路径（留空=txts根目录）"));
        pathField.setMaxLength(128);
        this.addDrawableChild(pathField);

        // 文件名输入
        int nameY = pathY + 24;
        nameField = new TextFieldWidget(this.textRenderer, fieldLeft, nameY, fieldW, 18, Text.literal("名称"));
        nameField.setText("exported_blueprint");
        nameField.setMaxLength(64);
        this.addDrawableChild(nameField);

        // 导出按钮
        int btnY = nameY + 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§a导出 .txt"), button -> doExportTxt())
                .dimensions(fieldLeft, btnY, fieldW, 20).build());

        // 返回按钮
        int cancelY = btnY + 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("返回"), button -> close())
                .dimensions(cx - 40, cancelY, 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int popLeft = cx - POPUP_WIDTH / 2;
        int popTop = cy - POPUP_HEIGHT / 2;

        // 弹窗背景
        context.fill(popLeft - 2, popTop - 2, popLeft + POPUP_WIDTH + 2, popTop + POPUP_HEIGHT + 2, 0xFFAAAAAA);
        context.fill(popLeft, popTop, popLeft + POPUP_WIDTH, popTop + POPUP_HEIGHT, 0xFF000000);

        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, popTop + 10, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    private void doExportTxt() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "exported_blueprint";
        String subPath = pathField.getText().trim();

        String blueprintText = SelectionAnalyzer.exportBlueprint(result, name);

        // 确定保存目录
        Path dir = Paths.get("txts");
        if (!Files.isDirectory(dir)) {
            dir = Paths.get("..").resolve("txts");
        }
        if (!Files.isDirectory(dir)) {
            try { Files.createDirectories(dir); } catch (IOException ignored) {}
        }

        // 如果指定了子目录，追加到路径
        if (!subPath.isEmpty()) {
            dir = dir.resolve(subPath);
            if (!Files.isDirectory(dir)) {
                try { Files.createDirectories(dir); } catch (IOException ignored) {}
            }
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

    private void scanAvailableFolders() {
        availableFolders.clear();
        availableFolders.add("");
        Path dir = TXTS_DIR;
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
