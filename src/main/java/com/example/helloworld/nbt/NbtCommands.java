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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 注册 /ainbt 命令，用于在游戏内解析和查看 NBT 结构文件。
 *
 * 用法：
 *   /ainbt list          - 列出 nbts/ 目录下所有 .nbt 文件
 *   /ainbt info <文件名>  - 查看指定 NBT 文件的详细信息
 *   /ainbt all           - 查看所有 NBT 文件的摘要
 *   /ainbt place <文件名> - 在玩家脚下位置放置 NBT 结构
 */
public class NbtCommands {

    private static final Path NBTS_DIR = com.example.helloworld.ModPaths.getNbtsDir();

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("ainbt")
            // /ainbt list
            .then(CommandManager.literal("list")
                .executes(NbtCommands::listFiles)
            )
            // /ainbt info <filename>
            .then(CommandManager.literal("info")
                .then(CommandManager.argument("filename", StringArgumentType.greedyString())
                    .executes(NbtCommands::showInfo)
                )
            )
            // /ainbt all
            .then(CommandManager.literal("all")
                .executes(NbtCommands::showAll)
            )
            // /ainbt place <filename>
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
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§cnbts/ 目录不存在", "§cnbts/ directory not found")), false);
            return 0;
        }

        // 递归扫描所有子文件夹中的 .nbt 文件
        List<Path> nbtFiles;
        try (Stream<Path> walk = Files.walk(NBTS_DIR)) {
            nbtFiles = walk
                    .filter(p -> p.toString().endsWith(".nbt"))
                    .filter(p -> Files.isRegularFile(p))
                    .toList();
        } catch (IOException e) {
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§c扫描目录失败: ", "§cFailed to scan directory: ") + e.getMessage()), false);
            return 0;
        }

        if (nbtFiles.isEmpty()) {
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§enbts/ 目录下没有 .nbt 文件", "§eNo .nbt files in nbts/ directory")), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§e找到 " + nbtFiles.size() + " 个 NBT 文件:", "§eFound " + nbtFiles.size() + " NBT file(s):")), false);
        for (Path p : nbtFiles) {
            // 显示相对于 nbts/ 的路径，方便用户复制使用
            String relativePath = NBTS_DIR.relativize(p).toString().replace('\\', '/');
            long size = p.toFile().length();
            source.sendFeedback(() -> Text.literal("§a  - §f" + relativePath + " §7(" + size + " bytes)"), false);
        }
        return 1;
    }

    /**
     * 解析用户输入的文件名，支持以下格式：
     *   - roof                          → 先找 nbts/roof.nbt，再递归搜索子文件夹
     *   - woodland_mansion/roof         → nbts/woodland_mansion/roof.nbt
     *   - woodland_mansion roof         → 空格转为 /，等同上面
     *   - woodland_mansion/roof.nbt     → 直接使用
     */
    public static File resolveNbtFile(String input) {
        // 空格转为路径分隔符，支持 "woodland_mansion roof" 写法
        String normalized = input.trim().replace(' ', '/');
        if (!normalized.endsWith(".nbt")) {
            normalized = normalized + ".nbt";
        }

        // 1. 先尝试精确路径
        File file = NBTS_DIR.resolve(normalized).toFile();
        if (file.exists()) return file;

        // 2. 回退：递归搜索文件名匹配的文件
        String targetName = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;
        try (Stream<Path> walk = Files.walk(NBTS_DIR)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(targetName))
                    .findFirst()
                    .map(Path::toFile)
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static int showInfo(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String filename = StringArgumentType.getString(ctx, "filename");

        File file = resolveNbtFile(filename);
        if (file == null || !file.exists()) {
            String fn = filename;
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§c文件不存在: " + fn + " (提示: 子文件夹用空格分隔，如 woodland_mansion roof)", "§cFile not found: " + fn + " (tip: use spaces for subfolders, e.g. woodland_mansion roof)")), false);
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
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§c解析失败: ", "§cParse failed: ") + e.getMessage()), false);
        }
        return 1;
    }

    private static int showAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        List<NbtStructureParser.StructureData> all = NbtStructureParser.parseAll(NBTS_DIR);

        if (all.isEmpty()) {
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§enbts/ 目录下没有可解析的 .nbt 文件", "§eNo parseable .nbt files in nbts/ directory")), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§e=== NBT 结构文件摘要 ===", "§e=== NBT Structure Summary ===")), false);
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
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§c只有玩家可以执行此命令", "§cOnly players can execute this command")), false);
            return 0;
        }

        String filename = StringArgumentType.getString(ctx, "filename");

        File file = resolveNbtFile(filename);
        if (file == null || !file.exists()) {
            String fn = filename;
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§c文件不存在: " + fn + " (提示: 子文件夹用空格分隔，如 woodland_mansion roof)", "§cFile not found: " + fn + " (tip: use spaces for subfolders, e.g. woodland_mansion roof)")), false);
            return 0;
        }

        String fn = file.getName();
        BlockPos origin = player.getBlockPos();
        source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§e[NBT] 正在放置结构: " + fn + " ...", "§e[NBT] Placing structure: " + fn + " ...")), false);

        CompletableFuture.runAsync(() -> {
            try {
                NbtStructureParser.StructureData data = NbtStructureParser.parse(file);
                player.getServer().execute(() -> {
                    int count = NbtStructurePlacer.place(data, player.getServerWorld(), origin);
                    source.sendFeedback(() -> Text.literal(
                            com.example.helloworld.I18n.get(
                                "§a[NBT] " + fn + " 放置完成! 共 " + count + " 个方块 (原点: " + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")",
                                "§a[NBT] " + fn + " placed! " + count + " blocks (origin: " + origin.getX() + ", " + origin.getY() + ", " + origin.getZ() + ")")
                    ), false);
                });
            } catch (Exception e) {
                player.getServer().execute(() -> {
                    source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.get("§c[NBT] 放置失败: ", "§c[NBT] Place failed: ") + e.getMessage()), false);
                });
            }
        });

        return 1;
    }
}
