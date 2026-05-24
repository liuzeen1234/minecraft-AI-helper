package com.example.helloworld;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

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
    private ButtonWidget referenceButton; // 引用按钮

    // 引用的 txt 文件（跨屏幕保持选择状态）
    private static final Set<String> referencedFiles = new HashSet<>();

    private int scrollOffset = 0;
    private boolean isWaiting = false;
    private long thinkingStartTime = 0; // 开始思考的时间戳

    // 截图延迟发送状态（static 以便在屏幕关闭后仍可被 tick 事件访问）
    private static String pendingSendText = null;
    private static Set<String> pendingSendFiles = null;
    private static int screenshotDelayTicks = 0;
    private static AiChatScreen pendingInstance = null;

    // "终止思考" 按钮的点击区域
    private int cancelTextX = 0;
    private int cancelTextY = 0;
    private int cancelTextWidth = 0;

    // 布局常量
    private int chatAreaTop;
    private int chatAreaBottom;
    private int chatAreaLeft;
    private int chatAreaRight;
    private static final int LINE_HEIGHT = 12;
    private static final int PADDING = 8;

    public AiChatScreen(Screen parent) {
        super(Text.literal(I18n.get("AI 聊天", "AI Chat")));
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

        // 输入框 - 留出引用按钮 + 发送按钮 + 清空按钮空间
        int inputY = this.height - margin - inputAreaHeight + 5;
        int buttonsWidth = 76; // 引用(28) + 发送(20) + 清空(20) + 间距(8)
        int inputWidth = this.width - margin * 2 - buttonsWidth;
        inputField = new TextFieldWidget(this.textRenderer, margin, inputY, inputWidth, 20, Text.literal(I18n.get("输入消息...", "Message...")));
        inputField.setPlaceholder(Text.literal(I18n.get("§7输入消息，按 Enter 发送...", "§7Type a message, press Enter to send...")));
        inputField.setMaxLength(1024);
        inputField.setEditable(true);
        this.addDrawableChild(inputField);

        int btnX = margin + inputWidth + 4;

        // 引用按钮
        referenceButton = ButtonWidget.builder(Text.literal(I18n.get("引用", "Ref")), button -> openFileSelection())
                .dimensions(btnX, inputY, 28, 20)
                .build();
        this.addDrawableChild(referenceButton);
        btnX += 32;

        // 发送按钮
        sendButton = ButtonWidget.builder(Text.literal("↑"), button -> sendMessage())
                .dimensions(btnX, inputY, 20, 20)
                .build();
        this.addDrawableChild(sendButton);
        btnX += 24;

        // 清空按钮
        clearButton = ButtonWidget.builder(Text.literal("\uD83D\uDDD1"), button -> clearHistory())
                .dimensions(btnX, inputY, 20, 20)
                .build();
        this.addDrawableChild(clearButton);

        // 设置焦点（必须在所有 widget 添加完之后）
        this.setFocused(inputField);
        inputField.setFocused(true);

        // 重新计算换行
        rebuildWrappedLines();
        scrollToBottom();
    }

    private void openFileSelection() {
        this.client.setScreen(new TxtFileSelectionScreen(this, referencedFiles, selectedFiles -> {
            referencedFiles.clear();
            referencedFiles.addAll(selectedFiles);
        }));
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || isWaiting) return;

        // 构建显示消息（给用户看的）
        String displayText = text;
        if (!referencedFiles.isEmpty()) {
            displayText = text + "\n§7[引用了 " + referencedFiles.size() + " 个文件]";
        }

        // 添加用户消息到历史（显示用）
        messageHistory.add(new ChatMessage("user", displayText));
        inputField.setText("");

        // 显示等待状态
        isWaiting = true;
        thinkingStartTime = System.currentTimeMillis();
        sendButton.active = false;
        messageHistory.add(new ChatMessage("system", "正在思考..."));

        rebuildWrappedLines();
        scrollToBottom();

        // 检查是否启用截图
        boolean screenshotEnabled = HelloWorldMod.getConfig().isScreenshotEnabled();
        if (screenshotEnabled) {
            // 延迟截图：先隐藏界面，等2 tick后截图再发送
            pendingSendText = text;
            pendingSendFiles = new HashSet<>(referencedFiles);
            screenshotDelayTicks = 2;
            pendingInstance = this;
            // 暂时隐藏界面以获取干净的游戏画面
            this.client.setScreen(null);
        } else {
            // 不截图，直接发送
            doSendWithImage(text, "");
        }
    }

    /**
     * 执行截图并发送消息（由客户端 tick 事件延迟调用）
     */
    static void doScreenshotAndSend() {
        MinecraftClient client = MinecraftClient.getInstance();
        String screenshotPath = "";

        try {
            // 从 framebuffer 截图并保存到文件
            var framebuffer = client.getFramebuffer();
            int width = framebuffer.textureWidth;
            int height = framebuffer.textureHeight;

            IntBuffer pixelBuffer = BufferUtils.createIntBuffer(width * height);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, framebuffer.getColorAttachment());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixelBuffer);

            int[] pixels = new int[width * height];
            pixelBuffer.get(pixels);

            // OpenGL 纹理上下翻转
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = pixels[(height - 1 - y) * width + x];
                    int r = (pixel >> 16) & 0xFF;
                    int g = (pixel >> 8) & 0xFF;
                    int b = pixel & 0xFF;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }

            // 缩放到 512px 宽
            int maxWidth = 512;
            if (width > maxWidth) {
                int newHeight = (int) ((double) maxWidth / width * height);
                java.awt.Image scaled = image.getScaledInstance(maxWidth, newHeight, java.awt.Image.SCALE_SMOOTH);
                BufferedImage scaledImage = new BufferedImage(maxWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                scaledImage.getGraphics().drawImage(scaled, 0, 0, null);
                image = scaledImage;
            }

            // 保存到文件（与 /ai 命令一致的路径）
            File screenshotDir = ModPaths.getScreenshotsDir().toFile();
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }
            File outputFile = new File(screenshotDir, "ai_chat_temp.png");
            ImageIO.write(image, "png", outputFile);
            screenshotPath = outputFile.getAbsolutePath();
        } catch (Exception e) {
            // 截图失败不影响发送
        }

        // 重新打开聊天界面
        AiChatScreen instance = pendingInstance;
        client.setScreen(instance);

        // 发送消息（传文件路径而非 base64）
        String text = pendingSendText;
        Set<String> files = pendingSendFiles;
        pendingSendText = null;
        pendingSendFiles = null;
        pendingInstance = null;

        if (instance != null && text != null) {
            instance.doSendWithImageStatic(text, screenshotPath, files);
        }
    }

    /**
     * 发送消息到服务端（带可选的截图文件路径）
     */
    private void doSendWithImage(String text, String screenshotPath) {
        doSendWithImageStatic(text, screenshotPath, referencedFiles);
    }

    private void doSendWithImageStatic(String text, String screenshotPath, Set<String> files) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(text);
        buf.writeInt(files != null ? files.size() : 0);
        if (files != null) {
            for (String fileName : files) {
                buf.writeString(fileName);
            }
        }
        buf.writeString(screenshotPath != null ? screenshotPath : "");
        ClientPlayNetworking.send(HelloWorldMod.CHAT_SCREEN_MSG_WITH_IMG_PACKET, buf);

        // 发送后焦点回到输入框
        if (inputField != null) {
            inputField.setFocused(true);
            this.setFocused(inputField);
        }
    }

    /**
     * 由客户端 tick 事件调用，处理延迟截图
     */
    public static void tickScreenshot() {
        if (pendingSendText != null && screenshotDelayTicks > 0) {
            screenshotDelayTicks--;
            if (screenshotDelayTicks == 0) {
                doScreenshotAndSend();
            }
        }
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

        // 如果有流式累积内容，移除它（最终完整回复会替代）
        if (!messageHistory.isEmpty()) {
            ChatMessage last = messageHistory.get(messageHistory.size() - 1);
            if (last.role.equals("assistant") && last == streamingMessage) {
                messageHistory.remove(messageHistory.size() - 1);
            }
        }
        streamingMessage = null;

        // 如果是服务端发来的终止消息，且本地已经有终止提示了，跳过
        if (response.equals("§7[思考已终止]")) {
            if (!messageHistory.isEmpty()) {
                ChatMessage last = messageHistory.get(messageHistory.size() - 1);
                if (last.role.equals("system") && last.content.equals("§7[思考已终止]")) {
                    return; // 已经有了，不重复添加
                }
            }
            messageHistory.add(new ChatMessage("system", "§7[思考已终止]"));
            return;
        }

        // 添加 AI 回复
        messageHistory.add(new ChatMessage("assistant", response));
    }

    // 流式消息引用（用于追加增量内容）
    private static ChatMessage streamingMessage = null;

    /**
     * 接收流式增量内容（由 ClientMod 调用）
     */
    public void appendStreamDelta(String delta) {
        // 移除 "正在思考..." 消息（首次收到流式内容时）
        if (streamingMessage == null) {
            if (!messageHistory.isEmpty()) {
                ChatMessage last = messageHistory.get(messageHistory.size() - 1);
                if (last.role.equals("system") && last.content.equals("正在思考...")) {
                    messageHistory.remove(messageHistory.size() - 1);
                }
            }
            // 创建流式消息占位
            streamingMessage = new ChatMessage("assistant", delta);
            messageHistory.add(streamingMessage);
        } else {
            // 追加到现有流式消息 - 需要替换（ChatMessage.content 是 final 的）
            int idx = messageHistory.indexOf(streamingMessage);
            if (idx >= 0) {
                String newContent = streamingMessage.content + delta;
                streamingMessage = new ChatMessage("assistant", newContent);
                messageHistory.set(idx, streamingMessage);
            }
        }
        rebuildWrappedLines();
        scrollToBottom();
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

    /**
     * 终止思考 - 发送取消请求到服务端
     */
    private void cancelThinking() {
        if (!isWaiting) return;

        // 发送取消数据包到服务端
        PacketByteBuf buf = PacketByteBufs.create();
        ClientPlayNetworking.send(HelloWorldMod.CHAT_CANCEL_PACKET, buf);

        // 立即更新本地状态
        isWaiting = false;
        if (sendButton != null) sendButton.active = true;

        // 移除 "正在思考..." 消息，替换为终止提示
        if (!messageHistory.isEmpty()) {
            ChatMessage last = messageHistory.get(messageHistory.size() - 1);
            if (last.role.equals("system") && last.content.equals("正在思考...")) {
                messageHistory.remove(messageHistory.size() - 1);
            }
        }
        messageHistory.add(new ChatMessage("system", "§7[思考已终止]"));

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

            // 对"正在思考..."消息追加计时
            String content = msg.content;
            if (msg.role.equals("system") && content.equals("正在思考...") && isWaiting && thinkingStartTime > 0) {
                long elapsedSeconds = (System.currentTimeMillis() - thinkingStartTime) / 1000;
                content = "正在思考... §8[" + elapsedSeconds + "s]";
            }

            // 按行分割内容
            String[] lines = content.split("\n");
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
            long elapsedSeconds = (System.currentTimeMillis() - thinkingStartTime) / 1000;
            String indicator = "§7AI 正在思考" + ".".repeat((int) dots) + " §8[" + elapsedSeconds + "s]";
            context.drawTextWithShadow(this.textRenderer, Text.literal(indicator),
                    chatAreaLeft + PADDING, chatAreaBottom + 2, 0xFFAAAAAA);

            // 红色可点击 "终止思考" 文本
            String cancelText = "终止思考";
            cancelTextWidth = this.textRenderer.getWidth(cancelText);
            cancelTextX = chatAreaLeft + PADDING + this.textRenderer.getWidth(
                    ("AI 正在思考" + ".".repeat((int) dots) + " [" + elapsedSeconds + "s]  ").replace("§7", "").replace("§8", ""));
            cancelTextY = chatAreaBottom + 2;

            // 检测鼠标悬停
            boolean hovered = mouseX >= cancelTextX && mouseX <= cancelTextX + cancelTextWidth
                    && mouseY >= cancelTextY && mouseY <= cancelTextY + LINE_HEIGHT;
            int cancelColor = hovered ? 0xFFFF6666 : 0xFFFF4444; // 悬停时稍亮
            String cancelDisplay = (hovered ? "§n" : "") + "终止思考";
            context.drawTextWithShadow(this.textRenderer, Text.literal("§c" + cancelDisplay),
                    cancelTextX, cancelTextY, cancelColor);
        }

        // 引用文件指示器
        if (!referencedFiles.isEmpty()) {
            String refText = "§6\uD83D\uDCCE 已引用 " + referencedFiles.size() + " 个文件";
            int refTextWidth = this.textRenderer.getWidth(refText.replaceAll("§[0-9a-fk-or]", ""));
            int refX = isWaiting ? cancelTextX + cancelTextWidth + 10 : chatAreaLeft + PADDING;
            context.drawTextWithShadow(this.textRenderer, Text.literal(refText),
                    refX, chatAreaBottom + 2, 0xFFFFAA00);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 检查是否点击了 "终止思考" 文本
        if (isWaiting && button == 0) {
            if (mouseX >= cancelTextX && mouseX <= cancelTextX + cancelTextWidth
                    && mouseY >= cancelTextY && mouseY <= cancelTextY + LINE_HEIGHT) {
                cancelThinking();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        if (inputField != null && !inputField.isFocused() && this.getFocused() != sendButton && this.getFocused() != clearButton && this.getFocused() != referenceButton) {
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
