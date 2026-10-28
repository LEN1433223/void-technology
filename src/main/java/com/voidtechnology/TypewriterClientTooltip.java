package com.voidtechnology;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 「打字机」工具提示（客户端渲染）。
 *
 * <p>效果：文字逐字出现，已出现的字按「暗红 → 烬红」渐变着色，末尾带一个闪烁的方块光标。</p>
 *
 * <p>动画起点由 {@link VoidSteleTooltips} 在悬停目标变化时重置，所以同一物品上会
 * 从头再打一遍；不同物品之间不会串。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class TypewriterClientTooltip implements ClientTooltipComponent {

    /** 每个字出现的间隔（毫秒）。120ms × 25 字 ≈ 3 秒打完。 */
    private static final long CHAR_INTERVAL_MS = 120L;
    /** 起始颜色：暗红。注意不能压到纯黑——工具提示底色本身就接近黑，纯黑字会完全看不见。 */
    private static final int COLOR_FROM = 0x8A2A22;
    /** 结束颜色：亮烬红。 */
    private static final int COLOR_TO = 0xFF7A66;
    /** 光标颜色：偏金的暖红，明显但不刺眼。 */
    private static final int CURSOR_COLOR = 0xFFB37A;

    private final List<String> lines;

    public TypewriterClientTooltip(TypewriterTooltip data) {
        this.lines = data.lines();
    }

    private int totalChars() {
        int n = 0;
        for (var line : lines) {
            n += line.length();
        }
        return n;
    }

    /** 已经"打"出来的字符数。 */
    private int revealedChars() {
        long elapsed = (System.currentTimeMillis() - VoidSteleTooltips.animationStartMillis()) / CHAR_INTERVAL_MS;
        return (int) Mth.clamp(elapsed, 0, totalChars());
    }

    @Override
    public int getHeight() {
        return lines.size() * 10 + 2;
    }

    @Override
    public int getWidth(Font font) {
        int w = 0;
        for (var line : lines) {
            w = Math.max(w, font.width(line));
        }
        return w + 4;
    }

    @Override
    public void renderText(Font font, int mouseX, int mouseY, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        int budget = revealedChars();
        boolean typing = budget < totalChars();

        for (int row = 0; row < lines.size(); row++) {
            String line = lines.get(row);
            if (budget <= 0) {
                break;
            }
            int visible = Math.min(line.length(), budget);
            budget -= line.length();

            int y = mouseY + row * 10;
            List<FormattedCharSequence> pieces = new ArrayList<>();
            for (int i = 0; i < visible; i++) {
                char c = line.charAt(i);
                int color = colorFor(i, visible);
                pieces.add(FormattedCharSequence.forward(String.valueOf(c),
                        Style.EMPTY.withColor(color).withBold(false)));
            }
            // 正在打字的那一行末尾加光标
            if (typing && row == lines.size() - 1) {
                pieces.add(FormattedCharSequence.forward("\u2588", Style.EMPTY.withColor(CURSOR_COLOR)));
            }

            PoseStack pose = new PoseStack();
            pose.mulPose(matrix);
            int drawX = mouseX;
            for (var seq : pieces) {
                font.drawInBatch(seq, drawX, y, 0xFFFFFFFF, true, pose.last().pose(), buffer,
                        Font.DisplayMode.NORMAL, 0, 0xF000F0);
                drawX += font.width(seq);
            }
        }
    }

    /** 逐字渐变：越靠后越亮。 */
    private static int colorFor(int index, int visible) {
        float t = visible <= 1 ? 1f : (float) index / (visible - 1);
        // 加一点缓动，前段更暗，后段迅速变亮
        t = t * t;
        int r = (int) Mth.lerp(t, (COLOR_FROM >> 16) & 0xFF, (COLOR_TO >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (COLOR_FROM >> 8) & 0xFF, (COLOR_TO >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, COLOR_FROM & 0xFF, COLOR_TO & 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    /** 供外部判断是否需要重绘（打字期间每帧变化）。 */
    public boolean isAnimating() {
        return revealedChars() < totalChars();
    }

    /** 忽略此处的 FormattedText 引用，保持 API 面干净。 */
    private static void unused(FormattedText text) {
    }
}
