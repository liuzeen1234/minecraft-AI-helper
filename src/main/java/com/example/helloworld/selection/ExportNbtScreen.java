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
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * P3 - 导出 NBT 页面：填写保存路径和文件名，点击"导出.nbt"执行导出。
 */
public class ExportNbtScreen extends Screen {

    private final Screen parent;
    private final SelectionAnalyzer.AnalysisResult result;

    private TextFieldWidget pathField;
    private TextFieldWidget nameField;

    private static final Path NBTS_DIR = Path.of("../nbts");

    // 弹窗尺寸
    private static final int POPUP_WIDTH = 260;
    private static final int POPUP_HEIGHT = 130;

    public ExportNbtScreen(Screen parent, SelectionAnalyzer.AnalysisResult result) {
        super(Text.literal("导出 .nbt"));
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
        pathField = new TextFieldWidget(this.textRenderer, fieldLeft + 22, pathY, fieldW - 22, 18, Text.literal("路径"));
        pathField.setText("");
        pathField.setPlaceholder(Text.literal("§7保存路径（留空=nbts根目录）"));
        pathField.setMaxLength(512);
        this.addDrawableChild(pathField);

        // 文件名输入
        int nameY = pathY + 24;
        nameField = new TextFieldWidget(this.textRenderer, fieldLeft, nameY, fieldW, 18, Text.literal("名称"));
        nameField.setText("exported_structure");
        nameField.setMaxLength(64);
        this.addDrawableChild(nameField);

        // 导出按钮
        int btnY = nameY + 26;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§b导出 .nbt"), button -> doExportNbt())
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

    private void doExportNbt() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = "exported_structure";
        String pathText = pathField.getText().trim();

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(result.min().getX());
        buf.writeInt(result.min().getY());
        buf.writeInt(result.min().getZ());
        buf.writeInt(result.max().getX());
        buf.writeInt(result.max().getY());
        buf.writeInt(result.max().getZ());
        buf.writeString(name);
        buf.writeString(pathText);
        ClientPlayNetworking.send(HelloWorldMod.EXPORT_NBT_PACKET, buf);

        String displayPath = pathText.isEmpty()
                ? name + ".nbt"
                : pathText + "/" + name + ".nbt";
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(
                    Text.literal("§7[选区] 正在服务端导出 NBT 到 " + displayPath + " ..."), false);
        }
        close();
    }

    /**
     * 打开原生文件夹选择对话框，选中后将路径填入 pathField。
     */
    private void openFolderChooser() {
        // 默认打开 nbts 目录
        Path defaultDir = NBTS_DIR.toAbsolutePath().normalize();
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
