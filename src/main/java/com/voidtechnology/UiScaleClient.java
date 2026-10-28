package com.voidtechnology;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 客户端侧的等比缩放实现（GameClient 类不在这里被服务端解析）。 */
@OnlyIn(Dist.CLIENT)
final class UiScaleClient {

    private UiScaleClient() {
    }

    static void apply(UIElement root, float designWidth, float designHeight, float margin) {
        var window = Minecraft.getInstance().getWindow();
        float availW = window.getGuiScaledWidth() - margin * 2f;
        float availH = window.getGuiScaledHeight() - margin * 2f;
        float scale = Math.min(1f, Math.min(availW / designWidth, availH / designHeight));
        if (scale < 0.999f) {
            // 围绕元素中心等比缩小；LDLib2 的命中测试会自动做逆变换
            root.transform(t -> t.scale(scale));
        }
    }
}
