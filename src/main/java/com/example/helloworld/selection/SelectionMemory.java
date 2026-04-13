package com.example.helloworld.selection;

import net.minecraft.util.math.BlockPos;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 选区记忆：保存/加载历史选区到文件，最多保留 10 条。
 * 格式每行: name|x1,y1,z1|x2,y2,z2
 */
public class SelectionMemory {

    private static final int MAX_ENTRIES = 10;
    private static final Path FILE_PATH = Paths.get("config", "helloworld_selections.txt");

    public record Entry(String name, BlockPos pos1, BlockPos pos2) {}

    public static List<Entry> load() {
        List<Entry> list = new ArrayList<>();
        if (!Files.exists(FILE_PATH)) return list;
        try {
            for (String line : Files.readAllLines(FILE_PATH, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\|");
                if (parts.length != 3) continue;
                String name = parts[0];
                BlockPos p1 = parsePos(parts[1]);
                BlockPos p2 = parsePos(parts[2]);
                if (p1 != null && p2 != null) {
                    list.add(new Entry(name, p1, p2));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return list;
    }

    public static void save(List<Entry> entries) {
        try {
            Files.createDirectories(FILE_PATH.getParent());
            List<String> lines = new ArrayList<>();
            int start = Math.max(0, entries.size() - MAX_ENTRIES);
            for (int i = start; i < entries.size(); i++) {
                Entry e = entries.get(i);
                lines.add(e.name + "|" + posStr(e.pos1) + "|" + posStr(e.pos2));
            }
            Files.write(FILE_PATH, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addEntry(String name, BlockPos pos1, BlockPos pos2) {
        List<Entry> entries = load();
        entries.add(new Entry(name, pos1, pos2));
        save(entries);
    }

    public static void removeEntry(int index) {
        List<Entry> entries = load();
        if (index >= 0 && index < entries.size()) {
            entries.remove(index);
            save(entries);
        }
    }

    private static BlockPos parsePos(String s) {
        try {
            String[] c = s.split(",");
            return new BlockPos(Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private static String posStr(BlockPos p) {
        return p.getX() + "," + p.getY() + "," + p.getZ();
    }
}
