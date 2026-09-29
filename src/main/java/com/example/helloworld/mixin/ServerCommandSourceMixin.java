package com.example.helloworld.mixin;

import com.example.helloworld.PendingCommandSuggestion;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * 在命令执行期间收集 {@code ServerCommandSource} 的反馈/错误文本，供
 * "AI 建议命令执行结果自动续跑" 使用。
 *
 * <p>仅当当前线程存在一个由 {@link com.example.helloworld.mixin.ServerPlayNetworkHandlerMixin}
 * 打开的捕获窗口（即玩家正在执行一条登记过的 AI 建议命令）时，才会真正记录；其余情况下
 * {@link PendingCommandSuggestion#captureFeedback} 内部直接返回，不产生任何副作用，
 * 不影响原版命令反馈的正常显示。
 */
@Mixin(ServerCommandSource.class)
public abstract class ServerCommandSourceMixin {

    /**
     * {@code sendFeedback(Supplier<Text>, boolean)}：命令成功时的反馈（如 "Set the time to 1000"）。
     * 1.20.4 中反馈文本用 Supplier 延迟构造，这里 get() 取出文本转成纯字符串收集。
     */
    @Inject(method = "sendFeedback", at = @At("HEAD"))
    private void aihelper$captureFeedback(Supplier<Text> feedbackSupplier, boolean broadcastToOps, CallbackInfo ci) {
        try {
            Text text = feedbackSupplier != null ? feedbackSupplier.get() : null;
            if (text != null) {
                PendingCommandSuggestion.captureFeedback(sourcePlayer(), text.getString());
            }
        } catch (Exception ignored) {
            // 收集反馈是纯旁路逻辑，任何异常都不能影响命令本身的反馈发送
        }
    }

    /**
     * {@code sendError(Text)}：命令失败时的错误反馈（如未知命令、语法错误、权限不足）。
     * 同样收集起来，让 AI 知道它建议的命令执行失败及原因，便于修正后再建议。
     */
    @Inject(method = "sendError", at = @At("HEAD"))
    private void aihelper$captureError(Text message, CallbackInfo ci) {
        try {
            if (message != null) {
                PendingCommandSuggestion.captureFeedback(sourcePlayer(), message.getString());
            }
        } catch (Exception ignored) {
            // 同上，旁路收集不得干扰原版行为
        }
    }

    /** 取当前命令源对应的玩家（命令方块/控制台等非玩家源返回 null）。 */
    private ServerPlayerEntity sourcePlayer() {
        return ((ServerCommandSource) (Object) this).getPlayer();
    }
}
