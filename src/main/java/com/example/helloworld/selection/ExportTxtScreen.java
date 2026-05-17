package com.example.helloworld.selection;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * P4 - 导出 TXT 页面：填写保存路径和文件名，点击"导出.txt"执行导出。
 */
public class ExportTxtScreen extends Screen {

    private final Screen parent;
    private final SelectionAnalyzer.AnalysisResult result;

    private TextFieldWidget pathField;
    private TextFieldWidget nameField;

    private static final Path TXTS_DIR = com.example.helloworld.ModPaths.getTxtsDir();

    // 弹窗尺寸
    private static final int POPUP_WIDTH = 260;
    private static final int POPUP_HEIGHT = 130;

    public ExportTxtScreen(Screen parent, SelectionAnalyzer.AnalysisResult result) {
        super(Text.literal(com.example.helloworld.I18n.get("导出 .txt", "Export .txt")));
        this.parent = parent;
        this.result = result;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int popLeft = cx - POPUP_WIDTH / 2;
        int popTop = cy - POPUP_HEIGHT / 2;

        int fieldW = POPUP_WIDTH - 40;
        int fieldLeft = popLeft + 20;

        // 保存路径输入
        int pathY = popTop + 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("📁"), button -> openFolderChooser())
                .dimensions(fieldLeft, pathY, 20, 18).build());
        pathField = new TextFieldWidget(this.textRenderer, fieldLeft + 22, pathY, fieldW - 22, 18, Text.literal(com.example.helloworld.I18n.get("路径", "Path")));
        pathField.setText("");
        pathField.setPlaceholder(Text.literal(com.example.helloworld.I18n.get("§7保存路径（留空=txts根目录）", "§7Save path (empty=txts root)")));
        pathField.setMaxLength(512);
        this.addDrawableChild(pathField);

        // 文件名输入
        int nameY = pathY + 24;
        nameField = new TextFieldWidget(this.textRenderer, fieldLeft, nameY, fieldW, 18, Text.literal(com.example.helloworld.I18n.get("名称", "Name")));
        nameField.setText("exported_blueprint");
        nameField.setMaxLength(64);
        this.addDrawableChild(nameField);

        // 导出按钮
        int btnY = nameY + 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(com.example.helloworld.I18n.get("§a导出 .txt", "§aExport .txt")), button -> doExportTxt())
                .dimensions(fieldLeft, btnY, fieldW, 20).build());

        // 返回按钮
        int cancelY = btnY + 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(com.example.helloworld.I18n.get("返回", "Back")), button -> close())
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
        String pathText = pathField.getText().trim();

        String blueprintText = SelectionAnalyzer.exportBlueprintV2(result, name);

        // 确定保存目录：如果用户通过文件夹选择器选了绝对路径就直接用，否则用默认 txts 目录
        Path dir;
        if (!pathText.isEmpty()) {
            dir = Path.of(pathText);
        } else {
            dir = com.example.helloworld.ModPaths.getTxtsDir();
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

    /**
     * 打开原生文件夹选择对话框，选中后将路径填入 pathField。
     */
    private void openFolderChooser() {
        // 默认打开 txts 目录
        Path defaultDir = TXTS_DIR.toAbsolutePath().normalize();
        if (!Files.isDirectory(defaultDir)) {
            defaultDir = Path.of(System.getProperty("user.home"));
        }
        String selected = TinyFileDialogs.tinyfd_selectFolderDialog(
                "选择保存文件夹", defaultDir.toString());
        if (selected != null) {
            pathField.setText(selected);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
