package com.example.helloworld;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 全屏 AI 聊天界面，类似 /ai 指令但提供完整的聊天体验。
 * 支持多轮对话、滚动历史、实时显示 AI 回复。
 */
public class AiChatScreen extends Screen {

    private final Screen parent;

    /** 聊天消息 */
    private static class ChatMessage {
        final String role;   // "user" 或 "assistant" 或 "system"
        final String content;
        final long timestamp;

        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // 消息历史（跨屏幕保持）
    private static final List<ChatMessage> messageHistory = new ArrayList<>();

    // 渲染用的已换行消息
    private final List<WrappedLine> wrappedLines = new ArrayList<>();

    private static class WrappedLine {
        final String text;
        final int color;

        WrappedLine(String text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private TextFieldWidget inputField;
    private ButtonWidget sendButton;
    private ButtonWidget clearButton;

    private int scrollOffset = 0;
    private boolean isWaiting = false;

    // 布局常量
    private int chatAreaTop;
    private int chatAreaBottom;
    private int chatAreaLeft;
    private int chatAreaRight;
    private static final int LINE_HEIGHT = 12;
    private static final int PADDING = 8;

    public AiChatScreen(Screen parent) {
        super(Text.literal("AI 聊天"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int margin = 10;

        // 标题区域高度
        int titleHeight = 25;

        // 输入区域高度
        int inputAreaHeight = 30;

        // 聊天区域
        chatAreaLeft = margin;
        chatAreaRight = this.width - margin;
        chatAreaTop = margin + titleHeight;
        chatAreaBottom = this.height - margin - inputAreaHeight - 5;

        // 输入框
        int inputY = this.height - margin - inputAreaHeight + 5;
        int inputWidth = this.width - margin * 2 - 52; // 留出图标按钮空间
        inputField = new TextFieldWidget(this.textRenderer, margin, inputY, inputWidth, 20, Text.literal("输入消息..."));
        inputField.setPlaceholder(Text.literal("§7输入消息，按 Enter 发送..."));
        inputField.setMaxLength(1024);
        inputField.setEditable(true);
        this.addDrawableChild(inputField);

        // 发送按钮
        sendButton = ButtonWidget.builder(Text.literal("↑"), button -> sendMessage())
                .dimensions(margin + inputWidth + 4, inputY, 20, 20)
                .build();
        this.addDrawableChild(sendButton);

        // 清空按钮
        clearButton = ButtonWidget.builder(Text.literal("\uD83D\uDDD1"), button -> clearHistory())
                .dimensions(margin + inputWidth + 28, inputY, 20, 20)
                .build();
        this.addDrawableChild(clearButton);

        // 设置焦点（必须在所有 widget 添加完之后）
        this.setFocused(inputField);
        inputField.setFocused(true);

        // 重新计算换行
        rebuildWrappedLines();
        scrollToBottom();
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || isWaiting) return;

        // 添加用户消息
        messageHistory.add(new ChatMessage("user", text));
        inputField.setText("");

        // 显示等待状态
        isWaiting = true;
        sendButton.active = false;
        messageHistory.add(new ChatMessage("system", "正在思考..."));

        // 发送到服务端
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(text);
        ClientPlayNetworking.send(HelloWorldMod.CHAT_SCREEN_MESSAGE_PACKET, buf);

        rebuildWrappedLines();
        scrollToBottom();

        // 发送后焦点回到输入框
        inputField.setFocused(true);
        this.setFocused(inputField);
    }

    /**
     * 接收 AI 回复（由 ClientMod 调用）
     */
    public static void receiveResponse(String response) {
        // 移除 "正在思考..." 消息
        if (!messageHistory.isEmpty()) {
            ChatMessage last = messageHistory.get(messageHistory.size() - 1);
            if (last.role.equals("system") && last.content.equals("正在思考...")) {
                messageHistory.remove(messageHistory.size() - 1);
            }
        }

        // 添加 AI 回复
        messageHistory.add(new ChatMessage("assistant", response));
    }

    /**
     * 标记等待结束（由 ClientMod 调用）
     */
    public void setWaitingDone() {
        isWaiting = false;
        if (sendButton != null) sendButton.active = true;
        rebuildWrappedLines();
        scrollToBottom();
    }

    private void clearHistory() {
        messageHistory.clear();
        wrappedLines.clear();
        scrollOffset = 0;

        // 同时通知服务端清空对话历史
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString("__CLEAR_HISTORY__");
        ClientPlayNetworking.send(HelloWorldMod.CHAT_SCREEN_MESSAGE_PACKET, buf);

        // 焦点回到输入框
        inputField.setFocused(true);
        this.setFocused(inputField);
    }

    private void rebuildWrappedLines() {
        wrappedLines.clear();
        int maxWidth = chatAreaRight - chatAreaLeft - PADDING * 2 - 10;

        for (ChatMessage msg : messageHistory) {
            // 角色标签
            String prefix;
            int color;
            switch (msg.role) {
                case "user" -> { prefix = "§b[你] "; color = 0xFF55FFFF; }
                case "assistant" -> { prefix = "§a[AI] "; color = 0xFF55FF55; }
                default -> { prefix = "§7"; color = 0xFFAAAAAA; }
            }

            // 按行分割内容
            String[] lines = msg.content.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = (i == 0 ? prefix : "     ") + lines[i];
                // 自动换行
                List<String> wrapped = wrapText(line, maxWidth);
                for (String w : wrapped) {
                    wrappedLines.add(new WrappedLine(w, color));
                }
            }
            // 消息间空行
            wrappedLines.add(new WrappedLine("", 0));
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        if (text.isEmpty()) {
            result.add("");
            return result;
        }

        // 去掉颜色代码计算宽度
        String stripped = text.replaceAll("§[0-9a-fk-or]", "");
        int textWidth = this.textRenderer.getWidth(stripped);

        if (textWidth <= maxWidth) {
            result.add(text);
            return result;
        }

        // 需要换行 - 简单按字符切割
        StringBuilder current = new StringBuilder();
        String colorPrefix = "";
        int currentWidth = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                colorPrefix = "" + c + text.charAt(i + 1);
                current.append(c).append(text.charAt(i + 1));
                i++;
                continue;
            }

            int charWidth = this.textRenderer.getWidth(String.valueOf(c));
            if (currentWidth + charWidth > maxWidth && current.length() > 0) {
                result.add(current.toString());
                current = new StringBuilder(colorPrefix);
                currentWidth = 0;
            }
            current.append(c);
            currentWidth += charWidth;
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

    private void scrollToBottom() {
        int visibleLines = (chatAreaBottom - chatAreaTop) / LINE_HEIGHT;
        int totalLines = wrappedLines.size();
        scrollOffset = Math.max(0, totalLines - visibleLines);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 背景
        this.renderBackground(context, mouseX, mouseY, delta);

        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, "§e✦ AI 聊天 ✦", this.width / 2, 12, 0xFFFFFF55);

        // 聊天区域背景
        context.fill(chatAreaLeft, chatAreaTop, chatAreaRight, chatAreaBottom, 0xCC000000);
        // 边框
        context.fill(chatAreaLeft, chatAreaTop, chatAreaRight, chatAreaTop + 1, 0xFF333333);
        context.fill(chatAreaLeft, chatAreaBottom - 1, chatAreaRight, chatAreaBottom, 0xFF333333);
        context.fill(chatAreaLeft, chatAreaTop, chatAreaLeft + 1, chatAreaBottom, 0xFF333333);
        context.fill(chatAreaRight - 1, chatAreaTop, chatAreaRight, chatAreaBottom, 0xFF333333);

        // 渲染消息
        int visibleLines = (chatAreaBottom - chatAreaTop - PADDING * 2) / LINE_HEIGHT;
        int startLine = scrollOffset;
        int endLine = Math.min(startLine + visibleLines, wrappedLines.size());

        // 启用裁剪
        context.enableScissor(chatAreaLeft + 1, chatAreaTop + 1, chatAreaRight - 1, chatAreaBottom - 1);

        for (int i = startLine; i < endLine; i++) {
            WrappedLine line = wrappedLines.get(i);
            int y = chatAreaTop + PADDING + (i - startLine) * LINE_HEIGHT;
            if (!line.text.isEmpty()) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(line.text), chatAreaLeft + PADDING, y, 0xFFFFFFFF);
            }
        }

        context.disableScissor();

        // 滚动条
        if (wrappedLines.size() > visibleLines) {
            int scrollBarHeight = chatAreaBottom - chatAreaTop - 4;
            int thumbHeight = Math.max(20, scrollBarHeight * visibleLines / wrappedLines.size());
            int thumbY = chatAreaTop + 2 + (scrollBarHeight - thumbHeight) * scrollOffset / Math.max(1, wrappedLines.size() - visibleLines);
            int scrollBarX = chatAreaRight - 5;

            context.fill(scrollBarX, chatAreaTop + 2, scrollBarX + 3, chatAreaBottom - 2, 0x44FFFFFF);
            context.fill(scrollBarX, thumbY, scrollBarX + 3, thumbY + thumbHeight, 0xAAFFFFFF);
        }

        // 等待指示器
        if (isWaiting) {
            long dots = (System.currentTimeMillis() / 500) % 4;
            String indicator = "§7AI 正在思考" + ".".repeat((int) dots);
            context.drawTextWithShadow(this.textRenderer, Text.literal(indicator),
                    chatAreaLeft + PADDING, chatAreaBottom + 2, 0xFFAAAAAA);
        }

        // 提示文字
        String hint = "§8ESC 返回 | Enter 发送 | 滚轮翻页";
        context.drawTextWithShadow(this.textRenderer, Text.literal(hint),
                this.width - this.textRenderer.getWidth(hint.replaceAll("§[0-9a-fk-or]", "")) - 12,
                12, 0xFF888888);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (inputField.isFocused() && !inputField.getText().trim().isEmpty()) {
                sendMessage();
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        // Page Up / Page Down
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scroll(-10);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll(10);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll((int) (-verticalAmount * 3));
        return true;
    }

    private void scroll(int amount) {
        int visibleLines = (chatAreaBottom - chatAreaTop - PADDING * 2) / LINE_HEIGHT;
        int maxScroll = Math.max(0, wrappedLines.size() - visibleLines);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + amount));
    }

    @Override
    public void tick() {
        super.tick();
        // 确保输入框始终保持焦点（除非正在等待）
        if (inputField != null && !inputField.isFocused() && this.getFocused() != sendButton && this.getFocused() != clearButton) {
            this.setFocused(inputField);
            inputField.setFocused(true);
        }
        // 定期刷新显示（处理新消息到达）
        int oldSize = wrappedLines.size();
        rebuildWrappedLines();
        if (wrappedLines.size() != oldSize) {
            // 有新消息，自动滚动到底部
            scrollToBottom();
            if (!isWaiting) {
                sendButton.active = true;
            }
        }
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false; // 不暂停游戏
    }
}
