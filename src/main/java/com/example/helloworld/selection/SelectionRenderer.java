package com.example.helloworld.selection;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * 在世界中渲染选区高亮框。
 */
public class SelectionRenderer {

    public static void register() {
        WorldRenderEvents.LAST.register(SelectionRenderer::onWorldRender);
    }

    private static void onWorldRender(WorldRenderContext context) {
        SelectionManager mgr = SelectionManager.getInstance();
        if (!mgr.isComplete()) return;

        BlockPos min = mgr.getMin();
        BlockPos max = mgr.getMax();

        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();

        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        // 选区边界（方块外边缘，所以 max 各 +1）
        float x1 = min.getX();
        float y1 = min.getY();
        float z1 = min.getZ();
        float x2 = max.getX() + 1f;
        float y2 = max.getY() + 1f;
        float z2 = max.getZ() + 1f;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();

        // 画半透明面
        drawFaces(tessellator, matrix, x1, y1, z1, x2, y2, z2);

        // 画边框线
        drawEdges(tessellator, matrix, x1, y1, z1, x2, y2, z2);

        matrices.pop();
    }

    private static void drawFaces(Tessellator tessellator, Matrix4f matrix,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder buf = tessellator.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float r = 0.2f, g = 0.6f, b = 1.0f, a = 0.25f;

        // Bottom (y1)
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y1, z1).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y1, z2).color(r, g, b, a).next();
        buf.vertex(matrix, x1, y1, z2).color(r, g, b, a).next();

        // Top (y2)
        buf.vertex(matrix, x1, y2, z1).color(r, g, b, a).next();
        buf.vertex(matrix, x1, y2, z2).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y2, z1).color(r, g, b, a).next();

        // North (z1)
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
        buf.vertex(matrix, x1, y2, z1).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y2, z1).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y1, z1).color(r, g, b, a).next();

        // South (z2)
        buf.vertex(matrix, x1, y1, z2).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y1, z2).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
        buf.vertex(matrix, x1, y2, z2).color(r, g, b, a).next();

        // West (x1)
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
        buf.vertex(matrix, x1, y1, z2).color(r, g, b, a).next();
        buf.vertex(matrix, x1, y2, z2).color(r, g, b, a).next();
        buf.vertex(matrix, x1, y2, z1).color(r, g, b, a).next();

        // East (x2)
        buf.vertex(matrix, x2, y1, z1).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y2, z1).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y1, z2).color(r, g, b, a).next();

        tessellator.draw();

        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static void drawEdges(Tessellator tessellator, Matrix4f matrix,
                                   float x1, float y1, float z1,
                                   float x2, float y2, float z2) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.lineWidth(2.0f);
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferBuilder buf = tessellator.getBuffer();
        buf.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float r = 0.2f, g = 0.8f, b = 1.0f, a = 1.0f;

        // Bottom edges
        line(buf, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buf, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buf, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buf, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        // Top edges
        line(buf, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buf, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buf, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buf, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        // Vertical edges
        line(buf, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buf, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buf, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buf, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);

        tessellator.draw();

        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static void line(BufferBuilder buf, Matrix4f matrix,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float r, float g, float b, float a) {
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a).next();
        buf.vertex(matrix, x2, y2, z2).color(r, g, b, a).next();
    }
}
