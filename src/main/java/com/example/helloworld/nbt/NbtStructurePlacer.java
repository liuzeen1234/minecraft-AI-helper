package com.example.helloworld.nbt;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

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
                    NbtCompound nbt = block.blockEntityNbt.copy();
                    // 更新坐标到实际位置
                    nbt.putInt("x", targetPos.getX());
                    nbt.putInt("y", targetPos.getY());
                    nbt.putInt("z", targetPos.getZ());
                    be.readNbt(nbt);
                    be.markDirty();
                }
            }

            placed++;
        }

        LOGGER.info("放置完成: {} - 共 {} 个方块 (原点: {})", data.fileName, placed, origin.toShortString());
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
}
