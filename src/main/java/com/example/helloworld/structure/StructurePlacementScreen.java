package com.example.helloworld.structure;

import com.example.helloworld.HelloWorldMod;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * 结构放置界面。
 *
 * <p>用户在结构浏览器中确认要放置的结构文件后进入本界面，界面显示：
 * <ul>
 *   <li>要放置的结构名</li>
 *   <li>结构大小（X×Y×Z）</li>
 *   <li>放置原点 XYZ 坐标（可编辑，默认值为玩家当前所在位置）</li>
 * </ul>
 *
 * <p>点击"放置"后，把相对路径与原点 XYZ 写入对应的 PLACE 网络包发送给服务端。
 */
public class StructurePlacementScreen extends Screen {

    private final Screen parent;
    /** 发送给服务端的网络包（PLACE_NBT / PLACE_LITEMATIC / PLACE_TXT）。 */
    private final Identifier placePacket;
    /** 相对于对应子目录（nbts/ txts/ litematic/）的路径。 */
    private final String relativePath;
    /** 展示用的结构名。 */
    private final String structureName;
    /** 结构尺寸，任一维为负表示未知。 */
    private final int sizeX, sizeY, sizeZ;

    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget zField;

    // 弹窗尺寸
    private static final int POPUP_WIDTH = 280;
    private static final int POPUP_HEIGHT = 176;

    public StructurePlacementScreen(Screen parent, Identifier placePacket, String relativePath,
                                    String structureName, int sizeX, int sizeY, int sizeZ) {
        super(Text.literal(com.example.helloworld.I18n.tr("placement.title")));
        this.parent = parent;
        this.placePacket = placePacket;
        this.relativePath = relativePath;
        this.structureName = structureName;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int popLeft = cx - POPUP_WIDTH / 2;
        int popTop = cy - POPUP_HEIGHT / 2;

        int fieldLeft = popLeft + 20;
        int contentW = POPUP_WIDTH - 40;

        // 默认原点 = 玩家当前所在位置
        BlockPos origin = (this.client != null && this.client.player != null)
                ? this.client.player.getBlockPos()
                : BlockPos.ORIGIN;

        // 三个坐标输入框横向排列
        int coordY = popTop + 84;
        int labelW = 14;
        int gap = 8;
        int fieldW = (contentW - labelW * 3 - gap * 2) / 3;

        int x0 = fieldLeft + labelW;
        xField = new TextFieldWidget(this.textRenderer, x0, coordY, fieldW, 18, Text.literal("X"));
        xField.setText(Integer.toString(origin.getX()));
        xField.setMaxLength(12);
        this.addDrawableChild(xField);

        int y0 = x0 + fieldW + gap + labelW;
        yField = new TextFieldWidget(this.textRenderer, y0, coordY, fieldW, 18, Text.literal("Y"));
        yField.setText(Integer.toString(origin.getY()));
        yField.setMaxLength(12);
        this.addDrawableChild(yField);

        int z0 = y0 + fieldW + gap + labelW;
        zField = new TextFieldWidget(this.textRenderer, z0, coordY, fieldW, 18, Text.literal("Z"));
        zField.setText(Integer.toString(origin.getZ()));
        zField.setMaxLength(12);
        this.addDrawableChild(zField);

        // "回到玩家位置" 按钮
        int resetY = coordY + 26;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.tr("placement.button.reset")),
                button -> resetToPlayer())
                .dimensions(fieldLeft, resetY, contentW, 18).build());

        // 放置按钮
        int placeY = resetY + 24;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.tr("placement.button.place")),
                button -> doPlace())
                .dimensions(fieldLeft, placeY, contentW, 20).build());

        // 返回按钮
        int backY = placeY + 24;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(com.example.helloworld.I18n.tr("button.back")),
                button -> close())
                .dimensions(cx - 40, backY, 80, 20).build());
    }

    private void resetToPlayer() {
        if (this.client != null && this.client.player != null) {
            BlockPos p = this.client.player.getBlockPos();
            xField.setText(Integer.toString(p.getX()));
            yField.setText(Integer.toString(p.getY()));
            zField.setText(Integer.toString(p.getZ()));
        }
    }

    /** 解析坐标输入，非法时回退为玩家当前对应坐标。 */
    private int parseCoord(TextFieldWidget field, int fallback) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void doPlace() {
        BlockPos playerPos = (this.client != null && this.client.player != null)
                ? this.client.player.getBlockPos()
                : BlockPos.ORIGIN;
        int x = parseCoord(xField, playerPos.getX());
        int y = parseCoord(yField, playerPos.getY());
        int z = parseCoord(zField, playerPos.getZ());

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(relativePath);
        buf.writeInt(x);
        buf.writeInt(y);
        buf.writeInt(z);
        ClientPlayNetworking.send(placePacket, buf);

        this.client.setScreen(null);
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

        int textLeft = popLeft + 20;

        // 结构名
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(com.example.helloworld.I18n.tr("placement.name") + " §f" + structureName),
                textLeft, popTop + 30, 0xFFAAAAAA);

        // 结构大小
        String sizeText = (sizeX >= 0 && sizeY >= 0 && sizeZ >= 0)
                ? sizeX + " × " + sizeY + " × " + sizeZ
                : com.example.helloworld.I18n.tr("placement.size.unknown");
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(com.example.helloworld.I18n.tr("placement.size") + " §f" + sizeText),
                textLeft, popTop + 44, 0xFFAAAAAA);

        // 原点标签
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(com.example.helloworld.I18n.tr("placement.origin")),
                textLeft, popTop + 66, 0xFFFFFF);

        // 坐标轴标签（X/Y/Z 紧贴各输入框左侧）
        int coordY = popTop + 84 + 5;
        context.drawTextWithShadow(this.textRenderer, Text.literal("§cX"), xField.getX() - 12, coordY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§aY"), yField.getX() - 12, coordY, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, Text.literal("§9Z"), zField.getX() - 12, coordY, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
