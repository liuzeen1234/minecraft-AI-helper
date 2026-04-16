package com.example.helloworld.nbt;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 注册 /lzenbt 命令，用于在游戏内解析和查看 NBT 结构文件。
 *
 * 用法：
 *   /lzenbt list          - 列出 nbts/ 目录下所有 .nbt 文件
 *   /lzenbt info <文件名>  - 查看指定 NBT 文件的详细信息
 *   /lzenbt all           - 查看所有 NBT 文件的摘要
 *   /lzenbt place <文件名> - 在玩家脚下位置放置 NBT 结构
 */
public class NbtCommands {

    private static final Path NBTS_DIR = Path.of("../nbts");

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("lzenbt")
            // /lzenbt list
            .then(CommandManager.literal("list")
                .executes(NbtCommands::listFiles)
            )
            // /lzenbt info <filename>
            .then(CommandManager.literal("info")
                .then(CommandManager.argument("filename", StringArgumentType.greedyString())
                    .executes(NbtCommands::showInfo)
                )
            )
            // /lzenbt all
            .then(CommandManager.literal("all")
                .executes(NbtCommands::showAll)
            )
            // /lzenbt place <filename>
            .then(CommandManager.literal("place")
                .then(CommandManager.argument("filename", StringArgumentType.greedyString())
                    .executes(NbtCommands::placeStructure)
                )
            )
        );
    }

    private static int listFiles(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        File dir = NBTS_DIR.toFile();

        if (!dir.exists() || !dir.isDirectory()) {
            source.sendFeedback(() -> Text.literal("§cnbts/ 目录不存在"), false);
            return 0;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".nbt"));
        if (files == null || files.length == 0) {
            source.sendFeedback(() -> Text.literal("§enbts/ 目录下没有 .nbt 文件"), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§e找到 " + files.length + " 个 NBT 文件:"), false);
        for (File f : files) {
            String name = f.getName();
            long size = f.length();
            source.sendFeedback(() -> Text.literal("§a  - §f" + name + " §7(" + size + " bytes)"), false);
        }
        return 1;
    }

    private static int showInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String filename = StringArgumentType.getString(ctx, "filename");

        if (!filename.endsWith(".nbt")) {
            filename = filename + ".nbt";
        }

        File file = NBTS_DIR.resolve(filename).toFile();
        if (!file.exists()) {
            String fn = filename;
            source.sendFeedback(() -> Text.literal("§c文件不存在: nbts/" + fn), false);
            return 0;
        }

        try {
            NbtStructureParser.StructureData data = NbtStructureParser.parse(file);
            String summary = NbtStructureParser.getSummary(data);
            for (String line : summary.split("\n")) {
                String l = line;
                source.sendFeedback(() -> Text.literal(l), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c解析失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        List<NbtStructureParser.StructureData> all = NbtStructureParser.parseAll(NBTS_DIR);

        if (all.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§enbts/ 目录下没有可解析的 .nbt 文件"), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal("§e=== NBT 结构文件摘要 ==="), false);
        for (NbtStructureParser.StructureData data : all) {
            String summary = NbtStructureParser.getSummary(data);
            for (String line : summary.split("\n")) {
                String l = line;
                source.sendFeedback(() -> Text.literal(l), false);
            }
            source.sendFeedback(() -> Text.literal("§7---"), false);
        }
        return 1;
    }

    private static int placeStructure(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            source.sendFeedback(() -> Text.literal("§c只有玩家可以执行此命令"), false);
            return 0;
        }

        String filename = StringArgumentType.getString(ctx, "filename");
        if (!filename.endsWith(".nbt")) {
            filename = filename + ".nbt";
        }

        File file = NBTS_DIR.resolve(filename).toFile();
        if (!file.exists()) {
            String fn = filename;
            source.sendFeedback(() -> Text.literal("§c文件不存在: nbts/" + fn), false);
            return 0;
        }

        String fn = filename;
        BlockPos origin = player.getBlockPos();
        source.sendFeedback(() -> Text.literal("§e[NBT] 正在放置结构: " + fn + " ..."), false);

        CompletableFuture.runAsync(() -> {
            try {
                NbtStructureParser.StructureData data = NbtStructureParser.parse(file);
                player.getServer().execute(() -> {
                    int count = NbtStructurePlacer.place(data, player.getServerWorld(), origin);
                    source.sendFeedback(() -> Text.literal(
                            "§a[NBT] " + fn + " 放置完成! 共 " + count + " 个方块 (原点: "
                                    + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")"
                    ), false);
                });
            } catch (Exception e) {
                player.getServer().execute(() -> {
                    source.sendFeedback(() -> Text.literal("§c[NBT] 放置失败: " + e.getMessage()), false);
                });
            }
        });

        return 1;
    }
}
