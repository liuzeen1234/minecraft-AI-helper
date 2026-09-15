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
    private static final Path LITEMATIC_DIR = com.example.helloworld.ModPaths.getLitematicDir();

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

        boolean nbtsExist = Files.isDirectory(NBTS_DIR);
        boolean litematicExist = Files.isDirectory(LITEMATIC_DIR);
        if (!nbtsExist && !litematicExist) {
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.dir.notfound")), false);
            return 0;
        }

        // 分别递归扫描 nbts/（.nbt）与 litematic/（.litematic）
        List<Path> nbtFiles;
        List<Path> litematicFiles;
        try {
            nbtFiles = scanFiles(NBTS_DIR, ".nbt");
            litematicFiles = scanFiles(LITEMATIC_DIR, ".litematic");
        } catch (IOException e) {
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.scan.failed", e.getMessage())), false);
            return 0;
        }

        int total = nbtFiles.size() + litematicFiles.size();
        if (total == 0) {
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.list.empty")), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.list.found", total)), false);
        // 用 nbts/ 或 litematic/ 前缀显示，方便区分来源
        for (Path p : nbtFiles) {
            String relativePath = "nbts/" + NBTS_DIR.relativize(p).toString().replace('\\', '/');
            long size = p.toFile().length();
            source.sendFeedback(() -> Text.literal("§a  - §f" + relativePath + " §7(" + size + " bytes)"), false);
        }
        for (Path p : litematicFiles) {
            String relativePath = "litematic/" + LITEMATIC_DIR.relativize(p).toString().replace('\\', '/');
            long size = p.toFile().length();
            source.sendFeedback(() -> Text.literal("§d  - §f" + relativePath + " §7(" + size + " bytes)"), false);
        }
        return 1;
    }

    /** 递归扫描指定目录下所有匹配扩展名的文件；目录不存在时返回空列表。 */
    private static List<Path> scanFiles(Path dir, String ext) throws IOException {
        if (!Files.isDirectory(dir)) {
            return java.util.Collections.emptyList();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().toLowerCase().endsWith(ext))
                    .toList();
        }
    }

    /**
     * 解析用户输入的文件名，支持以下格式：
     *   - roof                          → 先找 nbts/roof.nbt，再递归搜索子文件夹
     *   - woodland_mansion/roof         → nbts/woodland_mansion/roof.nbt
     *   - woodland_mansion roof         → 空格转为 /，等同上面
     *   - woodland_mansion/roof.nbt     → 直接使用
     */
    public static File resolveNbtFile(String input) {
        return resolveInDir(NBTS_DIR, input, ".nbt");
    }

    /**
     * 在 litematic/ 目录下解析用户输入的 .litematic 文件名，规则同 {@link #resolveNbtFile}。
     */
    public static File resolveLitematicFile(String input) {
        return resolveInDir(LITEMATIC_DIR, input, ".litematic");
    }

    /**
     * 统一入口：先在 nbts/ 找 .nbt，找不到再在 litematic/ 找 .litematic。
     * 供 /ainbt place、info 等命令使用。
     */
    public static File resolveStructureFile(String input) {
        File nbt = resolveNbtFile(input);
        if (nbt != null && nbt.exists()) return nbt;
        return resolveLitematicFile(input);
    }

    /**
     * 在指定根目录下解析文件名，支持：
     *   - name              → root/name{ext}，找不到再递归按文件名搜索
     *   - sub/name          → root/sub/name{ext}
     *   - sub name          → 空格转为 /
     *   - sub/name{ext}     → 直接使用
     *
     * @param root 搜索根目录（NBTS_DIR 或 LITEMATIC_DIR）
     * @param ext  目标扩展名（含点，如 ".nbt" / ".litematic"）
     */
    private static File resolveInDir(Path root, String input, String ext) {
        if (!Files.isDirectory(root)) {
            return null;
        }
        // 空格转为路径分隔符，支持 "sub name" 写法
        String normalized = input.trim().replace(' ', '/');
        boolean hasExt = normalized.toLowerCase().endsWith(ext);
        String withExt = hasExt ? normalized : normalized + ext;

        // 1. 先尝试精确路径
        File file = root.resolve(withExt).toFile();
        if (file.exists()) return file;

        // 2. 回退：递归搜索文件名匹配的文件
        String baseName = withExt.contains("/")
                ? withExt.substring(withExt.lastIndexOf('/') + 1)
                : withExt;
        String baseLower = baseName.toLowerCase();
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().equals(baseLower))
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

        File file = resolveStructureFile(filename);
        if (file == null || !file.exists()) {
            String fn = filename;
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.file.notfound", fn)), false);
            return 0;
        }

        try {
            NbtStructureParser.StructureData data = NbtStructureParser.parseAny(file);
            String summary = NbtStructureParser.getSummary(data);
            for (String line : summary.split("\n")) {
                String l = line;
                source.sendFeedback(() -> Text.literal(l), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.parse.failed", e.getMessage())), false);
        }
        return 1;
    }

    private static int showAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        List<NbtStructureParser.StructureData> all = new java.util.ArrayList<>();
        all.addAll(NbtStructureParser.parseAll(NBTS_DIR));
        all.addAll(NbtStructureParser.parseAll(LITEMATIC_DIR));

        if (all.isEmpty()) {
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.all.empty")), false);
            return 0;
        }

        source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.all.header")), false);
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
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.player.only")), false);
            return 0;
        }

        String filename = StringArgumentType.getString(ctx, "filename");

        File file = resolveStructureFile(filename);
        if (file == null || !file.exists()) {
            String fn = filename;
            source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.file.notfound", fn)), false);
            return 0;
        }

        String fn = file.getName();
        BlockPos origin = player.getBlockPos();
        source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.place.placing", fn)), false);

        CompletableFuture.runAsync(() -> {
            try {
                NbtStructureParser.StructureData data = NbtStructureParser.parseAny(file);
                player.getServer().execute(() -> {
                    int count = NbtStructurePlacer.place(data, player.getServerWorld(), origin);
                    source.sendFeedback(() -> Text.literal(
                            com.example.helloworld.I18n.tr("nbtcmd.place.done",
                                fn, count, origin.getX(), origin.getY(), origin.getZ())
                    ), false);
                });
            } catch (Exception e) {
                player.getServer().execute(() -> {
                    source.sendFeedback(() -> Text.literal(com.example.helloworld.I18n.tr("nbtcmd.place.failed", e.getMessage())), false);
                });
            }
        });

        return 1;
    }
}
