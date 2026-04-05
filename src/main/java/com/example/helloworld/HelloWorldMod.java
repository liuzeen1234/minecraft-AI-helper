package com.example.helloworld;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloWorldMod implements ModInitializer {

    public static final String MOD_ID = "helloworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Hello World Mod 已加载!");

        // 当玩家加入服务器时，在聊天框发送 "Hello World"
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            player.sendMessage(Text.literal("Hello World!"), false);
        });

        // 注册 /echo 命令：将命令后的所有文字回显到聊天框
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("lze")
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                    .executes(this::executeEcho)
                )
            );
        });
    }

    private int executeEcho(CommandContext<ServerCommandSource> context) {
        String message = StringArgumentType.getString(context, "message");
        context.getSource().sendFeedback(() -> Text.literal(message), false);
        return 1;
    }
}
