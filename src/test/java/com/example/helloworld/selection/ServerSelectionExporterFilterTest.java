package com.example.helloworld.selection;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link ServerSelectionExporter#filterStructureNbt} 按方块种类过滤的行为。
 * 该方法作用于原版 StructureTemplate 序列化出的结构 NBT（palette + blocks）。
 */
class ServerSelectionExporterFilterTest {

    /** 构造一个最小的原版结构 NBT：给定 palette 名称列表 + 每个方块引用的 palette 下标。 */
    private static NbtCompound buildStructure(String[] paletteNames, int[] blockStates) {
        NbtCompound root = new NbtCompound();

        NbtList palette = new NbtList();
        for (String name : paletteNames) {
            NbtCompound entry = new NbtCompound();
            entry.putString("Name", name);
            palette.add(entry);
        }
        root.put("palette", palette);

        NbtList blocks = new NbtList();
        for (int i = 0; i < blockStates.length; i++) {
            NbtCompound block = new NbtCompound();
            block.putInt("state", blockStates[i]);
            NbtList pos = new NbtList();
            pos.add(NbtInt.of(i)); // 位置无关紧要，只用来区分
            pos.add(NbtInt.of(0));
            pos.add(NbtInt.of(0));
            block.put("pos", pos);
            blocks.add(block);
        }
        root.put("blocks", blocks);
        return root;
    }

    private static NbtList blocks(NbtCompound structure) {
        return structure.getList("blocks", NbtElement.COMPOUND_TYPE);
    }

    @Test
    void removesBlocksOfIgnoredType() {
        // palette: 0=stone, 1=cobblestone, 2=dirt
        // blocks:  stone, cobblestone, cobblestone, dirt, stone
        NbtCompound structure = buildStructure(
                new String[]{"minecraft:stone", "minecraft:cobblestone", "minecraft:dirt"},
                new int[]{0, 1, 1, 2, 0});

        int removed = ServerSelectionExporter.filterStructureNbt(structure, Set.of("cobblestone"));

        assertEquals(2, removed, "应剔除 2 个 cobblestone 方块");
        NbtList remaining = blocks(structure);
        assertEquals(3, remaining.size());
        // 剩下的方块不应引用 cobblestone 的 palette 下标(1)
        for (int i = 0; i < remaining.size(); i++) {
            assertFalse(remaining.getCompound(i).getInt("state") == 1,
                    "剩余方块不应引用被忽略种类的 palette 下标");
        }
    }

    @Test
    void removesMultipleIgnoredTypes() {
        NbtCompound structure = buildStructure(
                new String[]{"minecraft:stone", "minecraft:cobblestone", "minecraft:dirt"},
                new int[]{0, 1, 2, 0, 2, 1});

        Set<String> ignored = new HashSet<>();
        ignored.add("cobblestone");
        ignored.add("dirt");
        int removed = ServerSelectionExporter.filterStructureNbt(structure, ignored);

        assertEquals(4, removed);
        NbtList remaining = blocks(structure);
        assertEquals(2, remaining.size());
        for (int i = 0; i < remaining.size(); i++) {
            assertEquals(0, remaining.getCompound(i).getInt("state"), "只剩 stone");
        }
    }

    @Test
    void emptyIgnoreSetIsNoOp() {
        NbtCompound structure = buildStructure(
                new String[]{"minecraft:stone", "minecraft:cobblestone"},
                new int[]{0, 1, 0, 1});

        int removed = ServerSelectionExporter.filterStructureNbt(structure, Collections.emptySet());

        assertEquals(0, removed);
        assertEquals(4, blocks(structure).size());
    }

    @Test
    void nullIgnoreSetIsNoOp() {
        NbtCompound structure = buildStructure(
                new String[]{"minecraft:stone"},
                new int[]{0, 0});

        int removed = ServerSelectionExporter.filterStructureNbt(structure, null);

        assertEquals(0, removed);
        assertEquals(2, blocks(structure).size());
    }

    @Test
    void ignoredTypeNotPresentRemovesNothing() {
        NbtCompound structure = buildStructure(
                new String[]{"minecraft:stone", "minecraft:cobblestone"},
                new int[]{0, 1, 0, 1});

        int removed = ServerSelectionExporter.filterStructureNbt(structure, Set.of("diamond_block"));

        assertEquals(0, removed);
        assertEquals(4, blocks(structure).size());
    }

    @Test
    void matchesByPathIgnoringNamespace() {
        // palette 名称带 minecraft: 前缀，忽略集合是不带前缀的 path
        NbtCompound structure = buildStructure(
                new String[]{"minecraft:oak_planks", "minecraft:chest"},
                new int[]{0, 1, 0});

        int removed = ServerSelectionExporter.filterStructureNbt(structure, Set.of("chest"));

        assertEquals(1, removed);
        NbtList remaining = blocks(structure);
        assertEquals(2, remaining.size());
        for (int i = 0; i < remaining.size(); i++) {
            assertTrue(remaining.getCompound(i).getInt("state") == 0, "只剩 oak_planks");
        }
    }

    // =========================================================================
    // 空气方块过滤：air/cave_air/void_air 作为普通方块种类，通过 ignoredBlocks 控制
    // =========================================================================

    /** 导出界面默认把这三种空气预置为忽略，对应这里的默认忽略集合。 */
    private static Set<String> defaultAirIgnoreSet() {
        Set<String> ignored = new HashSet<>();
        ignored.add("air");
        ignored.add("cave_air");
        ignored.add("void_air");
        return ignored;
    }

    @Test
    void removesPlainAirWhenIgnored() {
        // 原版 saveFromWorld 会把 air 记入结构；默认忽略集合应把它剔除
        NbtCompound structure = buildStructure(
                new String[]{"minecraft:air", "minecraft:stone"},
                new int[]{0, 1, 0, 1, 0});

        int removed = ServerSelectionExporter.filterStructureNbt(structure, defaultAirIgnoreSet());

        assertEquals(3, removed, "应剔除 3 个 air 方块");
        NbtList remaining = blocks(structure);
        assertEquals(2, remaining.size());
        for (int i = 0; i < remaining.size(); i++) {
            assertEquals(1, remaining.getCompound(i).getInt("state"), "只剩 stone");
        }
    }

    @Test
    void removesAllAirVariantsWhenIgnored() {
        // 三种空气各自作为独立 palette 条目，默认全部被剔除
        NbtCompound structure = buildStructure(
                new String[]{"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:stone"},
                new int[]{0, 1, 2, 3, 0, 1, 2});

        int removed = ServerSelectionExporter.filterStructureNbt(structure, defaultAirIgnoreSet());

        assertEquals(6, removed, "应剔除全部 air/cave_air/void_air 方块");
        NbtList remaining = blocks(structure);
        assertEquals(1, remaining.size());
        assertEquals(3, remaining.getCompound(0).getInt("state"), "只剩 stone");
    }

    @Test
    void keepsAirWhenNotIgnored() {
        // 用户在导出界面点 +/- 取消忽略 air 后，air 应被保留
        NbtCompound structure = buildStructure(
                new String[]{"minecraft:air", "minecraft:stone"},
                new int[]{0, 1, 0});

        // ignoredBlocks 为空表示不忽略任何种类（包括 air）
        int removed = ServerSelectionExporter.filterStructureNbt(structure, Collections.emptySet());

        assertEquals(0, removed);
        assertEquals(3, blocks(structure).size(), "air 应被完整保留");
    }

    @Test
    void keepsAirButRemovesOtherAirVariant() {
        // 混合场景：保存 air，但仍忽略 cave_air —— 各空气种类独立控制
        NbtCompound structure = buildStructure(
                new String[]{"minecraft:air", "minecraft:cave_air", "minecraft:stone"},
                new int[]{0, 1, 2, 0, 1});

        int removed = ServerSelectionExporter.filterStructureNbt(structure, Set.of("cave_air"));

        assertEquals(2, removed, "只剔除 cave_air");
        NbtList remaining = blocks(structure);
        assertEquals(3, remaining.size());
        for (int i = 0; i < remaining.size(); i++) {
            assertFalse(remaining.getCompound(i).getInt("state") == 1, "不应残留 cave_air");
        }
    }
}
