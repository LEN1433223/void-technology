package com.voidtechnology;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 把「预览结构」登记的包围盒画成世界线框。
 *
 * <p>用 {@link RenderLevelStageEvent} 在透明方块之后绘制，颜色跟虚空主题一致（烬色）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VoidStelePreviewRenderer {

    private VoidStelePreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (VoidStelePreview.isEmpty()) {
            return;
        }
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null) {
            return;
        }

        var buffers = mc.renderBuffers().bufferSource();
        var consumer = buffers.getBuffer(RenderType.LINES);
        var pose = event.getPoseStack();
        var cam = event.getCamera().getPosition();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (var b : VoidStelePreview.bounds()) {
            AABB box = new AABB(b.x0(), b.y0(), b.z0(), b.x1(), b.y1(), b.z1());
            // 外框：烬色
            LevelRenderer.renderLineBox(pose, consumer, box, 1.0f, 0.42f, 0.26f, 1.0f);
            // 底面再画一层淡金，方便判断贴地位置
            AABB floor = new AABB(b.x0(), b.y0() + 0.02, b.z0(), b.x1(), b.y0() + 0.02, b.z1());
            LevelRenderer.renderLineBox(pose, consumer, floor, 0.85f, 0.70f, 0.36f, 1.0f);
        }
        pose.popPose();
        buffers.endBatch(RenderType.LINES);
    }

    /** 退出世界时清空预览，避免线框残留在别的存档里。 */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        VoidStelePreview.clear();
    }
}
