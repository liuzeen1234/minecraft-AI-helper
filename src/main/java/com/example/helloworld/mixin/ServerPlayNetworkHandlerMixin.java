package com.example.helloworld.mixin;

import com.example.helloworld.PendingCommandSuggestion;
import net.minecraft.network.message.LastSeenMessageList;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截玩家在原版聊天框执行命令的实际执行方法 {@code ServerPlayNetworkHandler.handleCommandExecution}，
 * 为 "AI 建议命令执行结果自动续跑" 打开/关闭反馈捕获窗口。
 *
 * <p>为什么注入 {@code handleCommandExecution} 而非 {@code onCommandExecution}：
 * {@code onCommandExecution} 运行在 Netty IO 网络线程，它只是校验后通过
 * {@code server.submit(...)} 把真正的命令解析与执行调度到服务端主线程里的
 * {@code handleCommandExecution}。命令的 {@code sendFeedback}/{@code sendError} 反馈发生在主线程，
 * 若在网络线程用 ThreadLocal 开捕获窗口，既跨不到主线程、时序上也早于命令真正执行（会收集到 0 行反馈）。
 * 因此必须注入到主线程上同步包住"解析 + 执行 + 反馈"全过程的 {@code handleCommandExecution}。
 *
 * <p>只做窗口管理，不改变命令本身的解析与执行——所有实际判断（这条命令是否是登记过的
 * AI 建议、要不要续跑）都在 {@link PendingCommandSuggestion} 里完成，避免对无关命令产生开销。
 *
 * <p>为什么不用 Fabric 的 {@code ServerMessageEvents}：该事件系列拿不到命令执行产生的
 * 反馈文本（{@code sendFeedback} 的内容），而我们要把反馈回喂给 AI，因此必须自己包一层捕获窗口。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow @Final public ServerPlayerEntity player;

    /**
     * 命令执行开始处（服务端主线程）：打开捕获窗口（仅当这条命令是登记过的 AI 建议时才真正打开）。
     */
    @Inject(method = "handleCommandExecution", at = @At("HEAD"))
    private void aihelper$beginCommandCapture(CommandExecutionC2SPacket packet,
                                              LastSeenMessageList lastSeenMessages, CallbackInfo ci) {
        PendingCommandSuggestion.beginCapture(this.player, packet.command());
    }

    /**
     * 命令执行返回处（服务端主线程，与打开窗口同线程）：关闭捕获窗口，若命中登记的建议则触发续跑。
     * 用 RETURN 保证正常返回路径一定关闭窗口并清理 ThreadLocal；handleCommandExecution 内部自行
     * 捕获消息链异常并提前 return，这些提前返回同样会命中 RETURN 注入，窗口不会泄漏。
     */
    @Inject(method = "handleCommandExecution", at = @At("RETURN"))
    private void aihelper$endCommandCapture(CommandExecutionC2SPacket packet,
                                            LastSeenMessageList lastSeenMessages, CallbackInfo ci) {
        PendingCommandSuggestion.endCaptureAndResolve();
    }
}
