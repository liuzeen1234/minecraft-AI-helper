package com.example.helloworld.selection;

import net.minecraft.util.math.BlockPos;

/**
 * 管理选区的两个坐标点。
 */
public class SelectionManager {

    private static final SelectionManager INSTANCE = new SelectionManager();

    private BlockPos pos1;
    private BlockPos pos2;

    // 草稿：保留用户在输入框中输入的文本（即使未确认）
    private String draftX1 = "", draftY1 = "", draftZ1 = "";
    private String draftX2 = "", draftY2 = "", draftZ2 = "";

    public static SelectionManager getInstance() {
        return INSTANCE;
    }

    public BlockPos getPos1() { return pos1; }
    public BlockPos getPos2() { return pos2; }

    public void setPos1(BlockPos pos) { this.pos1 = pos; }
    public void setPos2(BlockPos pos) { this.pos2 = pos; }

    public String getDraftX1() { return draftX1; }
    public String getDraftY1() { return draftY1; }
    public String getDraftZ1() { return draftZ1; }
    public String getDraftX2() { return draftX2; }
    public String getDraftY2() { return draftY2; }
    public String getDraftZ2() { return draftZ2; }

    public void saveDraft(String x1, String y1, String z1, String x2, String y2, String z2) {
        this.draftX1 = x1; this.draftY1 = y1; this.draftZ1 = z1;
        this.draftX2 = x2; this.draftY2 = y2; this.draftZ2 = z2;
    }

    public boolean hasDraft() {
        return !draftX1.isEmpty() || !draftY1.isEmpty() || !draftZ1.isEmpty()
            || !draftX2.isEmpty() || !draftY2.isEmpty() || !draftZ2.isEmpty();
    }

    public void clearDraft() {
        draftX1 = ""; draftY1 = ""; draftZ1 = "";
        draftX2 = ""; draftY2 = ""; draftZ2 = "";
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null;
    }

    public void clear() {
        pos1 = null;
        pos2 = null;
    }

    public BlockPos getMin() {
        if (!isComplete()) return null;
        return new BlockPos(
            Math.min(pos1.getX(), pos2.getX()),
            Math.min(pos1.getY(), pos2.getY()),
            Math.min(pos1.getZ(), pos2.getZ())
        );
    }

    public BlockPos getMax() {
        if (!isComplete()) return null;
        return new BlockPos(
            Math.max(pos1.getX(), pos2.getX()),
            Math.max(pos1.getY(), pos2.getY()),
            Math.max(pos1.getZ(), pos2.getZ())
        );
    }
}
