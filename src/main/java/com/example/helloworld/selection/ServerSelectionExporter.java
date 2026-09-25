package com.example.helloworld.selection;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 服务端选区导出器：在服务端扫描选区方块并导出为 NBT 文件。
 * 使用原版 StructureTemplate 进行 NBT 导出，与结构方块保存格式完全一致。
 * 因为在服务端执行，可以完整读取 BlockEntity 数据（箱子内容、告示牌文字、熔炉物品等）。
 */
public class ServerSelectionExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger("ServerSelectionExporter");

    /**
     * 使用原版 StructureTemplate 在服务端导出选区为 NBT 文件（含完整 BlockEntity + 实体数据）。
     */
    public static void exportNbt(ServerWorld world, BlockPos pos1, BlockPos pos2, String name) throws IOException {
        exportNbt(world, pos1, pos2, name, "", true);
    }

    /**
     * 使用原版 StructureTemplate 在服务端导出选区为 NBT 文件，可指定子目录。
     * 调用 Minecraft 原版结构方块的同一套保存逻辑，无大小限制，
     * 完整保留方块状态、BlockEntity 数据和实体信息。
     */
    public static void exportNbt(ServerWorld world, BlockPos pos1, BlockPos pos2, String name, String subPath) throws IOException {
        exportNbt(world, pos1, pos2, name, subPath, true);
    }

    /**
     * 使用原版 StructureTemplate 在服务端导出选区为 NBT 文件，可指定子目录和是否包含实体。
     *
     * @param includeEntities 是否保存实体（盔甲架、物品展示框、矿车等）
     */
    public static void exportNbt(ServerWorld world, BlockPos pos1, BlockPos pos2, String name, String subPath, boolean includeEntities) throws IOException {
        exportNbt(world, pos1, pos2, name, subPath, includeEntities, Collections.emptySet());
    }

    /**
     * 使用原版 StructureTemplate 在服务端导出选区为 NBT 文件，可指定子目录、是否包含实体，
     * 并按方块种类过滤掉被忽略的方块。
     *
     * @param ignoredBlocks 被忽略（不记录）的方块种类 id 集合（不含 minecraft: 前缀），
     *                      这些种类的方块会从导出结构中剔除（视为空位）。
     */
    public static void exportNbt(ServerWorld world, BlockPos pos1, BlockPos pos2, String name, String subPath,
                                 boolean includeEntities, Set<String> ignoredBlocks) throws IOException {
        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        // 使用原版 StructureTemplate 保存选区
        StructureTemplate template = new StructureTemplate();
        Vec3i size = new Vec3i(sizeX, sizeY, sizeZ);
        // saveFromWorld: 从世界中保存方块和实体到模板
        // 参数: world, origin, size, includeEntities, ignoredBlock(结构空位方块)
        template.saveFromWorld(world, min, size, includeEntities, Blocks.STRUCTURE_VOID);

        // 序列化为 NBT（与结构方块保存格式一致）
        NbtCompound nbt = template.writeNbt(new NbtCompound());

        // 按方块种类过滤掉被忽略的方块
        int removed = filterStructureNbt(nbt, ignoredBlocks);

        // 写入文件
        Path dir = com.example.helloworld.ModPaths.getNbtsDir();
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }

        // 如果指定了子目录，追加到路径
        if (subPath != null && !subPath.isEmpty()) {
            dir = dir.resolve(subPath);
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
        }

        String fileName = com.example.helloworld.ModPaths.sanitizeFileName(name, "structure") + ".nbt";
        File file = dir.resolve(fileName).toFile();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            NbtIo.writeCompressed(nbt, fos);
        }

        LOGGER.info("服务端导出 NBT 完成 (StructureTemplate): {} ({}x{}x{}, 忽略方块种类: {}, 剔除方块: {})",
                fileName, sizeX, sizeY, sizeZ, ignoredBlocks.size(), removed);
    }

    /**
     * Exports the selected world area as a Litematica .litematic file.
     * The output uses one region at the schematic origin and preserves block entities
     * and, when requested, entities captured by the vanilla StructureTemplate.
     */
    public static void exportLitematic(ServerWorld world, BlockPos pos1, BlockPos pos2, String name,
                                       String subPath, boolean includeEntities) throws IOException {
        exportLitematic(world, pos1, pos2, name, subPath, includeEntities, Collections.emptySet());
    }

    /**
     * 导出选区为 Litematica .litematic 文件，并按方块种类过滤掉被忽略的方块。
     *
     * @param ignoredBlocks 被忽略（不记录）的方块种类 id 集合（不含 minecraft: 前缀）。
     */
    public static void exportLitematic(ServerWorld world, BlockPos pos1, BlockPos pos2, String name,
                                       String subPath, boolean includeEntities, Set<String> ignoredBlocks) throws IOException {
        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        StructureTemplate template = new StructureTemplate();
        template.saveFromWorld(world, min, new Vec3i(sizeX, sizeY, sizeZ), includeEntities, Blocks.STRUCTURE_VOID);

        String sanitizedName = com.example.helloworld.ModPaths.sanitizeFileName(name, "structure");
        NbtCompound vanillaStructure = template.writeNbt(new NbtCompound());
        // 按方块种类过滤掉被忽略的方块（在解析为 Litematica 格式前处理）
        filterStructureNbt(vanillaStructure, ignoredBlocks);
        com.example.helloworld.nbt.NbtStructureParser.StructureData structure =
                com.example.helloworld.nbt.NbtStructureParser.parseNbt(vanillaStructure, sanitizedName + ".litematic");
        NbtCompound litematic = LitematicNbtWriter.create(structure, sanitizedName, "AI Builder");

        Path dir = com.example.helloworld.ModPaths.getLitematicDir();
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }
        if (subPath != null && !subPath.isEmpty()) {
            dir = dir.resolve(subPath);
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
        }

        String fileName = sanitizedName + ".litematic";
        try (FileOutputStream output = new FileOutputStream(dir.resolve(fileName).toFile())) {
            NbtIo.writeCompressed(litematic, output);
        }

        LOGGER.info("服务端导出 Litematica 完成: {} ({}x{}x{}, 含实体: {})",
                fileName, sizeX, sizeY, sizeZ, includeEntities);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyValueString(BlockState state, Property<T> prop) {
        return prop.name(state.get(prop));
    }

    /**
     * 在原版 StructureTemplate 序列化出的 NBT 上，按方块种类剔除被忽略的方块。
     *
     * <p>原版结构 NBT 格式：{@code palette} 是方块状态列表（每项含 {@code Name}，
     * 如 {@code minecraft:cobblestone}）；{@code blocks} 是方块列表（每项含 {@code state}
     * 指向 palette 下标）。本方法找出被忽略种类对应的 palette 下标，删除所有引用这些下标的
     * {@code blocks} 条目（等同于把这些位置留空）。palette 保留不变（无用条目无害），
     * 无需重建下标。
     *
     * @param nbt           StructureTemplate.writeNbt 得到的结构 NBT（原地修改）
     * @param ignoredBlocks 被忽略的方块种类 id 集合（不含 minecraft: 前缀）
     * @return 实际被剔除的方块数量
     */
    static int filterStructureNbt(NbtCompound nbt, Set<String> ignoredBlocks) {
        if (ignoredBlocks == null || ignoredBlocks.isEmpty() || nbt == null) {
            return 0;
        }
        // 收集被忽略种类对应的 palette 下标
        Set<Integer> ignoredStates = new HashSet<>();
        NbtList palette = nbt.getList("palette", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < palette.size(); i++) {
            NbtCompound entry = palette.getCompound(i);
            String fullName = entry.getString("Name"); // 形如 minecraft:cobblestone
            String path = stripNamespace(fullName);
            if (ignoredBlocks.contains(path)) {
                ignoredStates.add(i);
            }
        }
        if (ignoredStates.isEmpty()) {
            return 0;
        }
        // 删除引用被忽略下标的 blocks 条目
        NbtList blocks = nbt.getList("blocks", NbtElement.COMPOUND_TYPE);
        int removed = 0;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            NbtCompound block = blocks.getCompound(i);
            if (ignoredStates.contains(block.getInt("state"))) {
                blocks.remove(i);
                removed++;
            }
        }
        return removed;
    }

    /** 去掉方块 id 的命名空间前缀（minecraft:cobblestone -> cobblestone）。 */
    private static String stripNamespace(String fullName) {
        if (fullName == null) return "";
        int idx = fullName.indexOf(':');
        return idx >= 0 ? fullName.substring(idx + 1) : fullName;
    }



    // =========================================================================
    // TXT 导出（含容器内容物）
    // =========================================================================

    /**
     * 在服务端扫描选区并导出为 TXT 文件（MCBLUEPRINT v2 格式，含容器内容物）。
     */
    public static void exportTxt(ServerWorld world, BlockPos pos1, BlockPos pos2, String name, String subPath) throws IOException {
        exportTxt(world, pos1, pos2, name, subPath, Collections.emptySet());
    }

    /**
     * 在服务端扫描选区并导出为 TXT 文件，按方块种类过滤掉被忽略的方块。
     *
     * @param ignoredBlocks 被忽略（不记录）的方块种类 id 集合（不含 minecraft: 前缀）。
     */
    public static void exportTxt(ServerWorld world, BlockPos pos1, BlockPos pos2, String name, String subPath,
                                 Set<String> ignoredBlocks) throws IOException {
        Set<String> ignored = ignoredBlocks == null ? Collections.emptySet() : ignoredBlocks;
        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        StringBuilder sb = new StringBuilder();

        // 文件头
        sb.append("# MCBLUEPRINT v2\n");
        sb.append("# name: ").append(name).append("\n");
        sb.append("# size: ").append(sizeX).append("x").append(sizeY).append("x").append(sizeZ).append("\n");
        sb.append("# origin: 0,0,0\n");
        sb.append("# 坐标原点在结构西北角最低层，x向东，y向上，z向南\n");
        sb.append("# 格式：x,y,z  block_id  [key=value ...]\n");
        sb.append("\n");
        sb.append("## BLOCKS\n");
        sb.append("\n");

        int blockCount = 0;
        int containerCount = 0;

        // 按 y 层分组输出
        for (int y = min.getY(); y <= max.getY(); y++) {
            int relY = y - min.getY();
            boolean layerHeaderWritten = false;

            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();
                    // 跳过被忽略的方块种类（air/cave_air/void_air 也通过 ignored 集合控制，
                    // 默认在导出界面被预置为忽略，故默认不写入）
                    if (ignored.contains(blockId)) continue;

                    // 写层头（延迟到有非空气方块时才写）
                    if (!layerHeaderWritten) {
                        sb.append("# --- 第 ").append(relY + 1).append(" 层 (y=").append(relY).append(") ---\n");
                        layerHeaderWritten = true;
                    }

                    int relX = x - min.getX();
                    int relZ = z - min.getZ();

                    sb.append(relX).append(",").append(relY).append(",").append(relZ);
                    sb.append("   ").append(blockId);

                    // 输出所有属性
                    for (Property<?> prop : state.getProperties()) {
                        sb.append("   ").append(prop.getName()).append("=").append(getPropertyValueString(state, prop));
                    }
                    sb.append("\n");
                    blockCount++;

                    // 检查容器内容物
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    if (blockEntity instanceof Inventory inv && inv.size() > 0) {
                        List<String> itemLines = new ArrayList<>();
                        for (int slot = 0; slot < inv.size(); slot++) {
                            ItemStack stack = inv.getStack(slot);
                            if (stack.isEmpty()) continue;
                            String itemId = Registries.ITEM.getId(stack.getItem()).getPath();
                            StringBuilder itemLine = new StringBuilder();
                            itemLine.append("    slot=").append(slot);
                            itemLine.append("  ").append(itemId);
                            itemLine.append("  count=").append(stack.getCount());
                            if (stack.hasNbt()) {
                                itemLine.append("  nbt=").append(stack.getNbt().toString());
                            }
                            itemLines.add(itemLine.toString());
                        }
                        if (!itemLines.isEmpty()) {
                            sb.append("  items:\n");
                            for (String itemLine : itemLines) {
                                sb.append(itemLine).append("\n");
                            }
                            containerCount++;
                        }
                    }

                    // 检查告示牌文字
                    if (blockEntity instanceof SignBlockEntity signEntity) {
                        List<String> frontLines = getSignTextLines(signEntity.getFrontText());
                        List<String> backLines = getSignTextLines(signEntity.getBackText());
                        boolean hasText = false;
                        for (String line : frontLines) if (!line.isEmpty()) { hasText = true; break; }
                        if (!hasText) for (String line : backLines) if (!line.isEmpty()) { hasText = true; break; }

                        if (hasText) {
                            sb.append("  sign_text:\n");
                            sb.append("    front:\n");
                            for (String line : frontLines) {
                                sb.append("      ").append(line).append("\n");
                            }
                            sb.append("    back:\n");
                            for (String line : backLines) {
                                sb.append("      ").append(line).append("\n");
                            }
                        }
                    }
                }
            }

            if (layerHeaderWritten) {
                sb.append("\n");
            }
        }

        // 写入文件
        Path dir = com.example.helloworld.ModPaths.getTxtsDir();
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
        }

        // 如果指定了子目录，追加到路径
        if (subPath != null && !subPath.isEmpty()) {
            dir = dir.resolve(subPath);
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
            }
        }

        String fileName = com.example.helloworld.ModPaths.sanitizeFileName(name, "structure") + ".txt";
        Path filePath = dir.resolve(fileName);
        Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);

        LOGGER.info("服务端导出 TXT 完成: {} ({}x{}x{}, {} 个方块, {} 个容器)",
                fileName, sizeX, sizeY, sizeZ, blockCount, containerCount);
    }

    /**
     * 扫描选区并返回 MCBLUEPRINT v2 格式的文本（不写文件），用于把地形信息回喂给 AI。
     *
     * 与 {@link #exportTxt} 使用同一套逐方块扫描逻辑（方块 id + block state 属性 +
     * 容器内容物 + 告示牌文字），但直接返回字符串而非落盘。空气方块（air/cave_air/void_air）
     * 会被跳过以节省 token。
     *
     * 区域体积没有硬性上限（由 system prompt 以软性建议约束 AI 的查询范围），
     * 范围过大时会返回较长文本，请留意上下文占用与性能。
     *
     * @param world 服务端世界
     * @param pos1  选区一角
     * @param pos2  选区对角
     * @return MCBLUEPRINT v2 文本
     */
    public static String scanToText(ServerWorld world, BlockPos pos1, BlockPos pos2) {
        BlockPos min = new BlockPos(
                Math.min(pos1.getX(), pos2.getX()),
                Math.min(pos1.getY(), pos2.getY()),
                Math.min(pos1.getZ(), pos2.getZ()));
        BlockPos max = new BlockPos(
                Math.max(pos1.getX(), pos2.getX()),
                Math.max(pos1.getY(), pos2.getY()),
                Math.max(pos1.getZ(), pos2.getZ()));

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        // 默认忽略三种空气，避免大量空位刷屏、浪费 token
        Set<String> ignored = new HashSet<>(Arrays.asList("air", "cave_air", "void_air"));

        StringBuilder sb = new StringBuilder();
        sb.append("# MCBLUEPRINT v2\n");
        sb.append("# name: region_query\n");
        sb.append("# size: ").append(sizeX).append("x").append(sizeY).append("x").append(sizeZ).append("\n");
        sb.append("# origin: absolute ").append(min.getX()).append(" ").append(min.getY()).append(" ").append(min.getZ()).append("\n");
        sb.append("# 说明：以下为该区域现有地形，坐标为世界绝对坐标 x,y,z（已省略空气方块）\n");
        sb.append("# 格式：x,y,z  block_id  [key=value ...]\n");
        sb.append("\n");
        sb.append("## BLOCKS\n");
        sb.append("\n");

        int blockCount = 0;

        for (int y = min.getY(); y <= max.getY(); y++) {
            boolean layerHeaderWritten = false;
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();
                    if (ignored.contains(blockId)) continue;

                    if (!layerHeaderWritten) {
                        sb.append("# --- y=").append(y).append(" ---\n");
                        layerHeaderWritten = true;
                    }

                    // 使用世界绝对坐标，方便 AI 后续用绝对坐标建造/操作
                    sb.append(x).append(",").append(y).append(",").append(z);
                    sb.append("   ").append(blockId);

                    for (Property<?> prop : state.getProperties()) {
                        sb.append("   ").append(prop.getName()).append("=").append(getPropertyValueString(state, prop));
                    }
                    sb.append("\n");
                    blockCount++;

                    // 容器内容物
                    BlockEntity blockEntity = world.getBlockEntity(pos);
                    if (blockEntity instanceof Inventory inv && inv.size() > 0) {
                        List<String> itemLines = new ArrayList<>();
                        for (int slot = 0; slot < inv.size(); slot++) {
                            ItemStack stack = inv.getStack(slot);
                            if (stack.isEmpty()) continue;
                            String itemId = Registries.ITEM.getId(stack.getItem()).getPath();
                            StringBuilder itemLine = new StringBuilder();
                            itemLine.append("    slot=").append(slot);
                            itemLine.append("  ").append(itemId);
                            itemLine.append("  count=").append(stack.getCount());
                            if (stack.hasNbt()) {
                                itemLine.append("  nbt=").append(stack.getNbt().toString());
                            }
                            itemLines.add(itemLine.toString());
                        }
                        if (!itemLines.isEmpty()) {
                            sb.append("  items:\n");
                            for (String itemLine : itemLines) {
                                sb.append(itemLine).append("\n");
                            }
                        }
                    }

                    // 告示牌文字
                    if (blockEntity instanceof SignBlockEntity signEntity) {
                        List<String> frontLines = getSignTextLines(signEntity.getFrontText());
                        List<String> backLines = getSignTextLines(signEntity.getBackText());
                        boolean hasText = false;
                        for (String line : frontLines) if (!line.isEmpty()) { hasText = true; break; }
                        if (!hasText) for (String line : backLines) if (!line.isEmpty()) { hasText = true; break; }

                        if (hasText) {
                            sb.append("  sign_text:\n");
                            sb.append("    front:\n");
                            for (String line : frontLines) {
                                sb.append("      ").append(line).append("\n");
                            }
                            sb.append("    back:\n");
                            for (String line : backLines) {
                                sb.append("      ").append(line).append("\n");
                            }
                        }
                    }
                }
            }
        }

        if (blockCount == 0) {
            sb.append("# （该区域全为空气，没有实心方块）\n");
        }

        LOGGER.info("服务端地形查询: {}x{}x{} (体积 {}), 非空气方块 {} 个",
                sizeX, sizeY, sizeZ, (long) sizeX * sizeY * sizeZ, blockCount);

        return sb.toString();
    }

    /**
     * 从 SignText 中提取 4 行纯文本内容。
     */
    private static List<String> getSignTextLines(SignText signText) {
        List<String> lines = new ArrayList<>(4);
        for (int i = 0; i < 4; i++) {
            Text message = signText.getMessage(i, false);
            String content = message.getString();
            lines.add(content != null ? content : "");
        }
        return lines;
    }
}
