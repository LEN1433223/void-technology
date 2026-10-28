package com.voidtechnology;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 给模组自己的 GUI 贴图打开线性过滤（仅客户端）。
 *
 * <p>为什么必须这么做：贴图是 3× 超采样渲染的（528px 画到 176 GUI 像素）。默认 GUI 贴图是
 * 最近邻采样，缩小时会直接丢采样点（细线断裂、出现锯齿/摩尔纹），比 1× 手绘更难看。
 * 开启 blur 过滤后才是真正的抗锯齿缩放。</p>
 *
 * <p>由 {@code ClientTickEvent.Post} 在前若干 tick 内重试一次（贴图由资源管理器懒加载，
 * 首次 getTexture 才会真正读文件），成功后置位不再执行。</p>
 */
@EventBusSubscriber(modid = VoidTechnology.MODID, value = Dist.CLIENT)
public final class VoidGuiTextures {

    private static boolean applied;

    private VoidGuiTextures() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (applied) return;
        var mc = Minecraft.getInstance();
        if (mc.getTextureManager() == null) return;

        var texture = mc.getTextureManager().getTexture(VoidControllerUi.sheet());
        if (texture instanceof AbstractTexture abstractTexture) {
            abstractTexture.setFilter(true, false);   // blur=true 线性过滤，mipmap=false
            applied = true;
            VoidTechnology.LOGGER.info("[gui] linear filtering enabled for void_controller_sheet");
        }
    }
}
