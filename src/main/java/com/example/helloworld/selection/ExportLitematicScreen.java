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

/** Screen for exporting the current selection as a Litematica .litematic file. */
public class ExportLitematicScreen extends Screen {

    private static final Path LITEMATIC_DIR = com.example.helloworld.ModPaths.getLitematicDir();
    private static final int POPUP_WIDTH = 260;
    private static final int POPUP_HEIGHT = 130;

    private final Screen parent;
    private final SelectionAnalyzer.AnalysisResult result;
    private final boolean includeEntities;
    private final java.util.Set<String> ignoredBlocks; // 被忽略（不记录）的方块种类
    private TextFieldWidget pathField;
    private TextFieldWidget nameField;

    public ExportLitematicScreen(Screen parent, SelectionAnalyzer.AnalysisResult result, boolean includeEntities) {
        this(parent, result, includeEntities, java.util.Collections.emptySet());
    }

    public ExportLitematicScreen(Screen parent, SelectionAnalyzer.AnalysisResult result, boolean includeEntities,
                                 java.util.Set<String> ignoredBlocks) {
        super(Text.literal(com.example.helloworld.I18n.tr("export.litematic.title")));
        this.parent = parent;
        this.result = result;
        this.includeEntities = includeEntities;
        this.ignoredBlocks = ignoredBlocks == null ? java.util.Collections.emptySet() : ignoredBlocks;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int popupLeft = centerX - POPUP_WIDTH / 2;
        int popupTop = centerY - POPUP_HEIGHT / 2;
        int fieldWidth = POPUP_WIDTH - 40;
        int fieldLeft = popupLeft + 20;

        int pathY = popupTop + 28;
        this.addDrawableChild(ButtonWidget.builder(Text.literal("📁"), button -> openFolderChooser())
                .dimensions(fieldLeft, pathY, 20, 18).build());
        pathField = new TextFieldWidget(this.textRenderer, fieldLeft + 22, pathY, fieldWidth - 22, 18,
                Text.literal(com.example.helloworld.I18n.tr("export.litematic.path")));
        pathField.setPlaceholder(Text.literal(com.example.helloworld.I18n.tr("export.litematic.path.placeholder")));
        pathField.setMaxLength(512);
        this.addDrawableChild(pathField);

        int nameY = pathY + 24;
        nameField = new TextFieldWidget(this.textRenderer, fieldLeft, nameY, fieldWidth, 18,
                Text.literal(com.example.helloworld.I18n.tr("export.litematic.name")));
        nameField.setText("exported_structure");
        nameField.setMaxLength(64);
        this.addDrawableChild(nameField);

        int buttonY = nameY + 26;
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal(com.example.helloworld.I18n.tr("export.litematic.button")),
                        button -> exportLitematic())
                .dimensions(fieldLeft, buttonY, fieldWidth, 20).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal(com.example.helloworld.I18n.tr("button.back")),
                        button -> close())
                .dimensions(centerX - 40, buttonY + 26, 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int popupLeft = centerX - POPUP_WIDTH / 2;
        int popupTop = centerY - POPUP_HEIGHT / 2;
        context.fill(popupLeft - 2, popupTop - 2, popupLeft + POPUP_WIDTH + 2, popupTop + POPUP_HEIGHT + 2, 0xFFAAAAAA);
        context.fill(popupLeft, popupTop, popupLeft + POPUP_WIDTH, popupTop + POPUP_HEIGHT, 0xFF000000);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, popupTop + 10, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    private void exportLitematic() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            name = "exported_structure";
        }
        String path = pathField.getText().trim();
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeInt(result.min().getX());
        buffer.writeInt(result.min().getY());
        buffer.writeInt(result.min().getZ());
        buffer.writeInt(result.max().getX());
        buffer.writeInt(result.max().getY());
        buffer.writeInt(result.max().getZ());
        buffer.writeString(name);
        buffer.writeString(path);
        buffer.writeBoolean(includeEntities);
        buffer.writeInt(ignoredBlocks.size());
        for (String id : ignoredBlocks) {
            buffer.writeString(id);
        }
        ClientPlayNetworking.send(HelloWorldMod.EXPORT_LITEMATIC_PACKET, buffer);

        String displayPath = path.isEmpty() ? name + ".litematic" : path + "/" + name + ".litematic";
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.literal(
                    com.example.helloworld.I18n.tr("export.litematic.exporting_server", displayPath)), false);
        }
        close();
    }

    private void openFolderChooser() {
        Path defaultDirectory = LITEMATIC_DIR.toAbsolutePath().normalize();
        if (!Files.isDirectory(defaultDirectory)) {
            defaultDirectory = Path.of(System.getProperty("user.home"));
        }
        String selected = TinyFileDialogs.tinyfd_selectFolderDialog(
                com.example.helloworld.I18n.tr("export.litematic.choose_folder"), defaultDirectory.toString());
        if (selected != null) {
            pathField.setText(selected);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
