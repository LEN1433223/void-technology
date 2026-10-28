package com.voidtechnology;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/**
 * 让整块界面按 GUI 空间等比缩放，避免在小窗口或高 GUI 缩放下被裁切。
 *
 * <p>只做"缩小"（k ≤ 1）：GUI 空间足够时保持原尺寸，不足时整体等比缩到能放下为止。
 * LDLib2 的 {@code Transform2D} 会把命中测试做逆变换，所以缩放后点击坐标仍然正确。</p>
 */
final class UiScale {

    /** 界面四周留白，避免贴边。 */
    private static final float MARGIN = 8f;

    private UiScale() {
    }

    /** 客户端调用；服务端（构建菜单时）直接跳过。 */
    static void fit(UIElement root, float designWidth, float designHeight) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            UiScaleClient.apply(root, designWidth, designHeight, MARGIN);
        }
    }
}
