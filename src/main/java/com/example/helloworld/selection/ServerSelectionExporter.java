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

        String fileName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".nbt";
        File file = dir.resolve(fileName).toFile();

        try (FileOutputStream fos = new FileOutputStream(file)) {
            NbtIo.writeCompressed(nbt, fos);
        }

        LOGGER.info("服务端导出 NBT 完成 (StructureTemplate): {} ({}x{}x{})",
                fileName, sizeX, sizeY, sizeZ);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String getPropertyValueString(BlockState state, Property<T> prop) {
        return prop.name(state.get(prop));
    }



    // =========================================================================
    // TXT 导出（含容器内容物）
    // =========================================================================

    /**
     * 在服务端扫描选区并导出为 TXT 文件（MCBLUEPRINT v2 格式，含容器内容物）。
     */
    public static void exportTxt(ServerWorld world, BlockPos pos1, BlockPos pos2, String name, String subPath) throws IOException {
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

                    if (state.isAir()) continue;

                    // 写层头（延迟到有非空气方块时才写）
                    if (!layerHeaderWritten) {
                        sb.append("# --- 第 ").append(relY + 1).append(" 层 (y=").append(relY).append(") ---\n");
                        layerHeaderWritten = true;
                    }

                    int relX = x - min.getX();
                    int relZ = z - min.getZ();
                    String blockId = Registries.BLOCK.getId(state.getBlock()).getPath();

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

        String fileName = name.replaceAll("[^a-zA-Z0-9_\\-]", "_") + ".txt";
        Path filePath = dir.resolve(fileName);
        Files.writeString(filePath, sb.toString(), StandardCharsets.UTF_8);

        LOGGER.info("服务端导出 TXT 完成: {} ({}x{}x{}, {} 个方块, {} 个容器)",
                fileName, sizeX, sizeY, sizeZ, blockCount, containerCount);
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
