package com.example.helloworld;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 汇合单轮 AI 工具调用中多个异步挂起结果，确保它们只触发一次后续 AI 请求。
 *
 * <p>一轮回复可以同时包含普通游戏操作（等待 [是]/[否]）和 {@code execute_command}
 * （等待玩家在聊天框执行）。两类结果的完成时间无确定顺序；若每个回调各自续跑，
 * 会从同一轮派生出两条独立对话。该类先收集所有结果，待调用方声明的挂起项全部结束后，
 * 将合并反馈一次性交给续跑回调。
 */
final class PendingToolRoundContinuation {
    private final Consumer<String> onReady;
    private final List<String> feedbackParts = new ArrayList<>();

    /** -1 表示 process() 尚未返回，当前只缓存可能提前到达的结果。 */
    private int expectedSignals = -1;
    private int receivedSignals;
    private boolean resumed;

    PendingToolRoundContinuation(Consumer<String> onReady) {
        this.onReady = onReady;
    }

    /** 记录一个挂起工具的最终反馈；可安全地在 {@link #seal(int)} 前调用。 */
    void record(String feedback) {
        String combined = null;
        synchronized (this) {
            if (resumed) return;
            receivedSignals++;
            if (feedback != null && !feedback.isBlank()) {
                feedbackParts.add(feedback.trim());
            }
            combined = takeReadyFeedbackLocked();
        }
        if (combined != null) onReady.accept(combined);
    }

    /**
     * 在解析本轮 ACTION 后声明应等待的挂起结果数量。
     * 结果可能已在此之前到达；因此封口时也会检查是否已经可以续跑。
     */
    void seal(int expectedSignals) {
        if (expectedSignals < 0) {
            throw new IllegalArgumentException("expectedSignals must not be negative");
        }
        String combined = null;
        synchronized (this) {
            if (this.expectedSignals != -1) {
                throw new IllegalStateException("continuation is already sealed");
            }
            this.expectedSignals = expectedSignals;
            combined = takeReadyFeedbackLocked();
        }
        if (combined != null) onReady.accept(combined);
    }

    private String takeReadyFeedbackLocked() {
        if (resumed || expectedSignals <= 0 || receivedSignals < expectedSignals) return null;
        resumed = true;
        return String.join("\n\n", feedbackParts);
    }
}
