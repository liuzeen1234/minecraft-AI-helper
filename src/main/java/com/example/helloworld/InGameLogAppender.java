package com.example.helloworld;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 自定义 Log4j2 Appender，将控制台中的报错信息转发到游戏聊天框。
 * <p>
 * 由于 Log 事件可能在任意线程触发，这里先放入线程安全队列，
 * 再由客户端 tick 事件在主线程中发送到聊天框。
 */
public class InGameLogAppender extends AbstractAppender {

    /** 是否启用（玩家可通过 /ailog 切换） */
    private static volatile boolean enabled = true;

    /** 最低显示级别：默认只显示 WARN 及以上 */
    private static volatile Level minLevel = Level.WARN;

    /** 待发送到聊天框的消息队列 */
    private static final Queue<LogEntry> pendingMessages = new ConcurrentLinkedQueue<>();

    /** 队列最大容量，防止刷屏 */
    private static final int MAX_QUEUE_SIZE = 50;

    /** 单条消息最大字符数 */
    private static final int MAX_MESSAGE_LENGTH = 300;

    public InGameLogAppender() {
        super("InGameLogAppender", null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
    }

    @Override
    public void append(LogEvent event) {
        if (!enabled) return;
        if (!event.getLevel().isMoreSpecificThan(minLevel)) return;

        // 跳过自身产生的日志，避免无限循环
        String loggerName = event.getLoggerName();
        if (loggerName != null && loggerName.contains("InGameLogAppender")) return;

        String message = event.getMessage().getFormattedMessage();
        if (message == null || message.isBlank()) return;

        // 截断过长消息
        if (message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH) + "...";
        }

        // 控制队列大小
        if (pendingMessages.size() >= MAX_QUEUE_SIZE) {
            pendingMessages.poll(); // 丢弃最旧的
        }

        pendingMessages.offer(new LogEntry(event.getLevel(), loggerName, message));
    }

    // ========== 静态方法供外部调用 ==========

    /**
     * 在客户端 tick 中调用，将队列中的日志消息发送到聊天框。
     */
    public static void flushToChat() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;

        LogEntry entry;
        int count = 0;
        while ((entry = pendingMessages.poll()) != null && count < 10) {
            Text chatMessage = formatLogMessage(entry);
            client.player.sendMessage(chatMessage, false);
            count++;
        }
    }

    /**
     * 将日志条目格式化为带颜色的聊天消息。
     */
    private static Text formatLogMessage(LogEntry entry) {
        // 级别标签
        MutableText levelTag;
        if (entry.level == Level.ERROR || entry.level == Level.FATAL) {
            levelTag = Text.literal("[ERROR] ").formatted(Formatting.RED, Formatting.BOLD);
        } else if (entry.level == Level.WARN) {
            levelTag = Text.literal("[WARN] ").formatted(Formatting.YELLOW);
        } else {
            levelTag = Text.literal("[" + entry.level.name() + "] ").formatted(Formatting.GRAY);
        }

        // 来源（简化 logger 名称，只取最后一段）
        String shortLogger = entry.loggerName;
        if (shortLogger != null && shortLogger.contains(".")) {
            shortLogger = shortLogger.substring(shortLogger.lastIndexOf('.') + 1);
        }
        MutableText sourceTag = Text.literal("[" + shortLogger + "] ").formatted(Formatting.DARK_GRAY);

        // 消息内容
        Formatting msgColor = (entry.level == Level.ERROR || entry.level == Level.FATAL)
                ? Formatting.RED : Formatting.GOLD;
        MutableText msgText = Text.literal(entry.message).formatted(msgColor);

        return Text.literal("§8[" + I18n.get("日志", "Log") + "] ").append(levelTag).append(sourceTag).append(msgText);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            pendingMessages.clear();
        }
    }

    public static void toggleEnabled() {
        setEnabled(!enabled);
    }

    public static Level getMinLevel() {
        return minLevel;
    }

    public static void setMinLevel(Level level) {
        minLevel = level;
    }

    /**
     * 注册此 Appender 到 Log4j2 的 Root Logger。
     */
    public static void install() {
        InGameLogAppender appender = new InGameLogAppender();
        appender.start();

        org.apache.logging.log4j.core.Logger rootLogger =
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
        rootLogger.addAppender(appender);
    }

    // ========== 内部数据类 ==========

    private static class LogEntry {
        final Level level;
        final String loggerName;
        final String message;

        LogEntry(Level level, String loggerName, String message) {
            this.level = level;
            this.loggerName = loggerName;
            this.message = message;
        }
    }
}
