package com.example.helloworld.nbt;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 将解析后的 NBT 结构数据放置到世界中。
 * 以指定的 origin 为原点，按照结构中的相对坐标放置方块。
 */
public class NbtStructurePlacer {

    private static final Logger LOGGER = LoggerFactory.getLogger("NbtStructurePlacer");

    /**
     * 放置结构到世界中。
     *
     * @param data   解析后的结构数据
     * @param world  目标世界
     * @param origin 放置原点（玩家脚下位置）
     * @return 放置的非空气方块数量
     */
    public static int place(NbtStructureParser.StructureData data, ServerWorld world, BlockPos origin) {
        int placed = 0;

        for (NbtStructureParser.BlockEntry block : data.blocks) {
            NbtStructureParser.PaletteEntry palette = data.palette.get(block.paletteIndex);

            // 跳过空气和结构空位
            if (palette.blockName.equals("minecraft:air")
                    || palette.blockName.equals("minecraft:structure_void")) {
                continue;
            }

            BlockPos targetPos = origin.add(block.x, block.y, block.z);
            BlockState state = resolveBlockState(palette);

            if (state == null) {
                LOGGER.warn("无法解析方块: {}", palette.blockName);
                continue;
            }

            // 放置方块（使用 flag 3 = 通知客户端 + 触发方块更新）
            world.setBlockState(targetPos, state, 3);

            // 如果有方块实体 NBT 数据（如箱子内容、告示牌文字等），写入
            if (block.blockEntityNbt != null) {
                BlockEntity be = world.getBlockEntity(targetPos);
                if (be != null) {
                    try {
                        NbtCompound nbt = block.blockEntityNbt.copy();
                        // 更新坐标到实际位置
                        nbt.putInt("x", targetPos.getX());
                        nbt.putInt("y", targetPos.getY());
                        nbt.putInt("z", targetPos.getZ());
                        // 转换旧版告示牌 NBT 格式为 1.20+ 格式
                        if (palette.blockName.contains("sign")) {
                            upgradeSignNbt(nbt);
                        }
                        be.readNbt(nbt);
                        be.markDirty();
                    } catch (Exception e) {
                        LOGGER.debug("方块实体 NBT 写入跳过 ({}): {}", palette.blockName, e.getMessage());
                    }
                }
            }

            placed++;
        }

        // 放置实体（画、物品展示框、盔甲架等）—— 在方块放完之后执行，确保附着面已存在
        int entityCount = 0;
        for (NbtStructureParser.EntityEntry ee : data.entities) {
            if (ee.entityNbt == null) continue;

            try {
                NbtCompound nbt = ee.entityNbt.copy();

                // 计算绝对坐标
                double absX = origin.getX() + ee.posX;
                double absY = origin.getY() + ee.posY;
                double absZ = origin.getZ() + ee.posZ;

                // 设置 Pos
                NbtList posTag = new NbtList();
                posTag.add(NbtDouble.of(absX));
                posTag.add(NbtDouble.of(absY));
                posTag.add(NbtDouble.of(absZ));
                nbt.put("Pos", posTag);

                // 更新悬挂实体的附着坐标（画、物品展示框）
                if (nbt.contains("TileX")) {
                    nbt.putInt("TileX", origin.getX() + ee.blockPosX);
                    nbt.putInt("TileY", origin.getY() + ee.blockPosY);
                    nbt.putInt("TileZ", origin.getZ() + ee.blockPosZ);
                }

                // 分配新 UUID
                nbt.putUuid("UUID", UUID.randomUUID());

                // 通过 EntityType 从 NBT 创建实体
                Optional<Entity> opt = EntityType.getEntityFromNbt(nbt, world);
                if (opt.isPresent()) {
                    Entity entity = opt.get();
                    world.spawnEntity(entity);
                    entityCount++;
                } else {
                    String id = nbt.contains("id") ? nbt.getString("id") : "unknown";
                    LOGGER.debug("无法创建实体: {}", id);
                }
            } catch (Exception e) {
                LOGGER.debug("实体放置跳过: {}", e.getMessage());
            }
        }

        LOGGER.info("放置完成: {} - 共 {} 个方块, {} 个实体 (原点: {})",
                data.fileName, placed, entityCount, origin.toShortString());
        return placed;
    }

    /**
     * 根据调色板条目解析出 BlockState（带属性）
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState resolveBlockState(NbtStructureParser.PaletteEntry palette) {
        Identifier id = new Identifier(palette.blockName);
        Block block = Registries.BLOCK.get(id);

        if (block == Blocks.AIR && !palette.blockName.equals("minecraft:air")) {
            return null;
        }

        BlockState state = block.getDefaultState();

        // 应用所有属性
        for (Map.Entry<String, String> prop : palette.properties.entrySet()) {
            Property<?> property = findProperty(state, prop.getKey());
            if (property != null) {
                Optional<?> value = property.parse(prop.getValue());
                if (value.isPresent()) {
                    state = state.with((Property) property, (Comparable) value.get());
                } else {
                    LOGGER.warn("无法解析属性: {}={} (方块: {})", prop.getKey(), prop.getValue(), palette.blockName);
                }
            }
        }

        return state;
    }

    private static Property<?> findProperty(BlockState state, String name) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals(name)) {
                return prop;
            }
        }
        return null;
    }

    /**
     * 修复告示牌 NBT 数据，确保 front_text/back_text 的 messages 列表格式正确。
     * 1.20+ 要求 messages 是一个包含 4 个 JSON 字符串的列表。
     * 同时兼容旧版 Text1~Text4 格式的转换。
     */
    private static void upgradeSignNbt(NbtCompound nbt) {
        // 新格式：修复 front_text 和 back_text 中的 messages
        if (nbt.contains("front_text", NbtElement.COMPOUND_TYPE)) {
            fixSignTextNbt(nbt.getCompound("front_text"));
            if (nbt.contains("back_text", NbtElement.COMPOUND_TYPE)) {
                fixSignTextNbt(nbt.getCompound("back_text"));
            } else {
                nbt.put("back_text", buildEmptySignTextNbt());
            }
            return;
        }

        // 旧格式：提取 Text1~Text4 并转换
        String[] texts = new String[4];
        for (int i = 0; i < 4; i++) {
            String key = "Text" + (i + 1);
            if (nbt.contains(key, NbtElement.STRING_TYPE)) {
                texts[i] = nbt.getString(key);
                nbt.remove(key);
            } else {
                texts[i] = "{\"text\":\"\"}";
            }
        }

        NbtCompound frontText = new NbtCompound();
        NbtList messages = new NbtList();
        for (String text : texts) {
            messages.add(NbtString.of(text));
        }
        frontText.put("messages", messages);
        frontText.putString("color", "black");
        frontText.putBoolean("has_glowing_text", false);
        nbt.put("front_text", frontText);
        nbt.put("back_text", buildEmptySignTextNbt());

        nbt.remove("GlowingText");
        nbt.remove("Color");
    }

    /**
     * 修复单个 front_text/back_text 的 messages 列表，
     * 确保是恰好 4 个 NbtString 元素。
     */
    private static void fixSignTextNbt(NbtCompound textNbt) {
        boolean needsFix = true;
        if (textNbt.contains("messages", NbtElement.LIST_TYPE)) {
            NbtList original = textNbt.getList("messages", NbtElement.STRING_TYPE);
            if (original.size() == 4) {
                needsFix = false;
            }
        }
        if (needsFix) {
            NbtList fixed = new NbtList();
            for (int i = 0; i < 4; i++) {
                fixed.add(NbtString.of("{\"text\":\"\"}"));
            }
            textNbt.put("messages", fixed);
        }
    }

    private static NbtCompound buildEmptySignTextNbt() {
        NbtCompound textNbt = new NbtCompound();
        NbtList messages = new NbtList();
        for (int i = 0; i < 4; i++) {
            messages.add(NbtString.of("{\"text\":\"\"}"));
        }
        textNbt.put("messages", messages);
        textNbt.putString("color", "black");
        textNbt.putBoolean("has_glowing_text", false);
        return textNbt;
    }
}
