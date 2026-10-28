package com.voidtechnology;

import com.lowdragmc.lowdraglib2.client.shader.LDLibRenderTypes;
import com.lowdragmc.lowdraglib2.gui.texture.AnimationTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 时间驱动的图集动画（悬浮/旋转类动效用它）。
 *
 * <p>为什么不用 {@code AnimationTexture}：它每个**游戏 tick**才推进一帧（上限 20 帧/秒），
 * 要做得慢就必须拉大每帧 tick 数，结果就是 4–6 fps 的卡顿感。这里改成：</p>
 * <ul>
 *   <li>帧位置由真实时间决定，因此速度可以任意慢（本项目 6 秒一轮）；</li>
 *   <li>相邻两帧**叠加淡入**：先画当前帧（不透明），再把下一帧按小数部分透明度盖上去。
 *       由于各帧只有光效不同，叠加表现为连续的拖影，视觉上顺滑，不会把面板画成半透明。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
final class TimedSheetTexture extends AnimationTexture {

    private final long startNanos = System.nanoTime();
    private final int frameCount;
    private final float secondsPerLoop;
    private final int tintRgb;

    TimedSheetTexture(ResourceLocation image, int columns, int frameCount, float secondsPerLoop, int tintRgb) {
        this.frameCount = frameCount;
        this.secondsPerLoop = Math.max(0.05f, secondsPerLoop);
        this.tintRgb = tintRgb & 0x00FFFFFF;
        this.imageLocation = image;
        setCellSize(columns);
        setAnimation(0, frameCount - 1);
    }

    /** 屏蔽基类的 tick 推进，完全交给 {@link #drawInternal} 的时间计算。 */
    @Override
    public void updateTick() {
        // no-op
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY,
                                float x, float y, float width, float height, float partialTicks) {
        double seconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        double position = (seconds / secondsPerLoop) * frameCount;
        int base = (int) Math.floor(position) % frameCount;
        int next = (base + 1) % frameCount;
        float frac = (float) (position - Math.floor(position));

        // 当前帧不透明打底，下一帧按小数部分淡入 → 连续拖影
        drawFrame(graphics, base, 1f, x, y, width, height);
        if (frac > 0.01f) {
            drawFrame(graphics, next, frac, x, y, width, height);
        }
    }

    private void drawFrame(GuiGraphics graphics, int frame, float alpha,
                           float x, float y, float width, float height) {
        float cell = 1f / cellSize;
        int col = frame % cellSize;
        int row = frame / cellSize;
        float u = col * cell;
        float v = row * cell;

        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        int color = (a << 24) | tintRgb;

        var buffer = graphics.bufferSource().getBuffer(LDLibRenderTypes.guiTexture(imageLocation));
        RenderSystem.disableDepthTest();
        var matrix = graphics.pose().last().pose();
        buffer.addVertex(matrix, x, y + height, 0).setUv(u, v + cell).setColor(color);
        buffer.addVertex(matrix, x + width, y + height, 0).setUv(u + cell, v + cell).setColor(color);
        buffer.addVertex(matrix, x + width, y, 0).setUv(u + cell, v).setColor(color);
        buffer.addVertex(matrix, x, y, 0).setUv(u, v).setColor(color);
    }
}
