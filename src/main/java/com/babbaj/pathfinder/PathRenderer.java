package com.babbaj.pathfinder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL15.*;


public class PathRenderer {

    private final List<BlockPos> path;

    private final int bufferId;
    private final int numVertices;

    public PathRenderer(List<BlockPos> path) {
        this.path = path;

        final int floatSize = 4;
        final int vertexSize = floatSize * 3;
        ByteBuffer buffer = GLAllocation.createDirectByteBuffer(path.size() * vertexSize);
        for (BlockPos pos : path) {
            buffer.putFloat(pos.getX());
            buffer.putFloat(pos.getY());
            buffer.putFloat(pos.getZ());
        }
        ((Buffer)buffer).rewind(); // stupid no method error
        this.bufferId = uploadBuffer(buffer);
        this.numVertices = path.size();
    }


    private static int uploadBuffer(ByteBuffer buffer) {
        IntBuffer ints = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();

        glGenBuffers(ints);
        final int id = ints.get(0);
        OpenGlHelper.glBindBuffer(GL_ARRAY_BUFFER, id);
        OpenGlHelper.glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
        OpenGlHelper.glBindBuffer(GL_ARRAY_BUFFER, 0);

        return id;
    }

    public static Vec3d getInterpolatedAmount(Entity entity, double ticks) {
        return new Vec3d(
            (entity.posX - entity.lastTickPosX) * ticks,
            (entity.posY - entity.lastTickPosY) * ticks,
            (entity.posZ - entity.lastTickPosZ) * ticks);
    }

    private static Vec3d interpolatedPos(Entity entity, float partialTicks) {
        return new Vec3d(entity.lastTickPosX, entity.lastTickPosY, entity.lastTickPosZ)
            .add(getInterpolatedAmount(entity, partialTicks));
    }

    private static boolean isInNether() {
        return Minecraft.getMinecraft().player.dimension == -1;
    }


    private static Vec3d getTranslation(float partialTicks) {
        Entity renderEntity = Minecraft.getMinecraft().getRenderViewEntity();
        return interpolatedPos(renderEntity, partialTicks);
    }

    static void preRender() {
        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.disableDepth();
    }

    static void postRender() {
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    private static void drawLine(int bufferId, int numVertices, float partialTicks) {

        GlStateManager.color(0, 0, 1.f);
        Vec3d translation = getTranslation(partialTicks);
        GlStateManager.translate(-translation.x, -translation.y, -translation.z); // TODO: this probably doesnt have to be done in 1.13+

        OpenGlHelper.glBindBuffer(GL_ARRAY_BUFFER, bufferId);
        GlStateManager.glEnableClientState(GL11.GL_VERTEX_ARRAY);

        GlStateManager.glVertexPointer(3, GL11.GL_FLOAT, 0, 0);
        GlStateManager.glDrawArrays(GL11.GL_LINE_STRIP, 0, numVertices);

        // post draw
        OpenGlHelper.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GlStateManager.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GlStateManager.resetColor(); // probably not needed
    }

    @SubscribeEvent
    public void onRender(RenderWorldLastEvent event) {
        if (!isInNether()) return;

        preRender();
        GlStateManager.glLineWidth(1.f);
        drawLine(this.bufferId, this.numVertices, event.getPartialTicks());
        postRender();
    }

    // Must be called before throwing away this renderer
    public void deleteBuffer() {
        OpenGlHelper.glDeleteBuffers(this.bufferId);
    }
}
