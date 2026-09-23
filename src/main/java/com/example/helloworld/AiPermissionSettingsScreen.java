package com.example.helloworld;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * AI 权限设置页面：控制 AI 在游戏中能做多少事、能做到什么程度的相关配置。
 * 目前包含最大工具调用轮数（多轮 agentic loop 的上限）。
 */
public class AiPermissionSettingsScreen extends Screen {

    private final Screen parent;
    private final ModConfig config;

    private TextFieldWidget maxToolRoundsField;
    // 输入框相对布局用的坐标缓存（供 render 绘制标签）
    private int maxRoundsLabelX;
    private int maxRoundsLabelY;
    private String maxRoundsStatus = null;
    private int maxRoundsStatusColor = 0xAAAAAA;

    public AiPermissionSettingsScreen(Screen parent) {
        super(Text.literal(I18n.tr("settings.permission.title")));
        this.parent = parent;
        this.config = HelloWorldMod.getConfig();
    }

    @Override
    protected void init() {
        int startX = this.width / 2 - 100;
        int btnH = 20;
        int gap = 4;
        int startY = this.height / 2 - 50;

        // 允许 AI 使用原版命令开关。关闭后 AI 只能使用 mod 自带的具体功能（放置方块、
        // 给物品、生成实体等），不会再建议任何 /命令（包括预填聊天框待确认的方式）。
        this.addDrawableChild(ButtonWidget.builder(
                getVanillaCommandsButtonText(),
                button -> {
                    config.setVanillaCommandsEnabled(!config.isVanillaCommandsEnabled());
                    button.setMessage(getVanillaCommandsButtonText());
                })
                .dimensions(startX, startY, 200, btnH)
                .build()
        );

        // 执行前需玩家确认开关。开启后，AI 使用 mod 自定义功能（放置方块、建造蓝图、
        // 给物品、查询等）前会先在聊天框发一条 [是]/[否] 确认消息，玩家点击"是"才会真正执行。
        int confirmY = startY + (btnH + gap);
        this.addDrawableChild(ButtonWidget.builder(
                getConfirmBeforeExecuteButtonText(),
                button -> {
                    config.setConfirmBeforeExecuteEnabled(!config.isConfirmBeforeExecuteEnabled());
                    button.setMessage(getConfirmBeforeExecuteButtonText());
                })
                .dimensions(startX, confirmY, 200, btnH)
                .build()
        );

        // 最大工具调用轮数输入框（0=禁用多轮）。标签绘制在输入框上方，输入框宽度略小以容纳保存按钮。
        int roundsY = confirmY + (btnH + gap) + 10; // 上方留一点空间给标签
        maxRoundsLabelX = startX;
        maxRoundsLabelY = roundsY - 10;
        maxToolRoundsField = new TextFieldWidget(this.textRenderer, startX, roundsY, 150, btnH,
                Text.literal("max_tool_rounds"));
        maxToolRoundsField.setMaxLength(6);
        maxToolRoundsField.setText(String.valueOf(config.getMaxToolRounds()));
        this.addDrawableChild(maxToolRoundsField);

        // 保存按钮（在输入框右侧）
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("settings.api.save")),
                button -> saveMaxToolRounds())
                .dimensions(startX + 155, roundsY, 45, btnH)
                .build()
        );

        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(I18n.tr("button.back")),
                button -> {
                    saveMaxToolRounds();
                    this.client.setScreen(this.parent);
                })
                .dimensions(startX, roundsY + (btnH + gap) * 2, 200, btnH)
                .build()
        );
    }

    private Text getVanillaCommandsButtonText() {
        String state = config.isVanillaCommandsEnabled() ? I18n.tr("settings.chat.on") : I18n.tr("settings.chat.off");
        return Text.literal(I18n.tr("settings.permission.vanilla_commands", state));
    }

    private Text getConfirmBeforeExecuteButtonText() {
        String state = config.isConfirmBeforeExecuteEnabled() ? I18n.tr("settings.chat.on") : I18n.tr("settings.chat.off");
        return Text.literal(I18n.tr("settings.permission.confirm_before_execute", state));
    }

    /** 解析输入框内容并保存 max_tool_rounds；非法输入给出提示且不覆盖旧值。 */
    private void saveMaxToolRounds() {
        if (maxToolRoundsField == null) return;
        String raw = maxToolRoundsField.getText().trim();
        try {
            int value = Integer.parseInt(raw);
            if (value < 0) throw new NumberFormatException("negative");
            config.setMaxToolRounds(value);
            maxRoundsStatus = I18n.tr("settings.chat.max_tool_rounds.saved", value);
            maxRoundsStatusColor = 0x55FF55;
            // 归一化显示（clamp 后回填）
            maxToolRoundsField.setText(String.valueOf(config.getMaxToolRounds()));
        } catch (NumberFormatException e) {
            maxRoundsStatus = I18n.tr("settings.chat.max_tool_rounds.invalid");
            maxRoundsStatusColor = 0xFF5555;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        // 输入框上方的说明标签
        if (maxToolRoundsField != null) {
            context.drawTextWithShadow(this.textRenderer,
                    I18n.tr("settings.chat.max_tool_rounds"), maxRoundsLabelX, maxRoundsLabelY, 0xAAAAAA);
            if (maxRoundsStatus != null) {
                String clean = maxRoundsStatus.replaceAll("§[0-9a-fk-or]", "");
                context.drawTextWithShadow(this.textRenderer, clean,
                        maxRoundsLabelX, maxRoundsField_bottomY(), maxRoundsStatusColor);
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private int maxRoundsField_bottomY() {
        return maxToolRoundsField.getY() + maxToolRoundsField.getHeight() + 2;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
