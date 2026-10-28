package com.voidtechnology;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

/**
 * 「打字机」工具提示的数据载体（两端通用）。
 *
 * <p>真正的逐字显示与配色在客户端的 {@code TypewriterClientTooltip} 里。</p>
 */
public record TypewriterTooltip(List<String> lines) implements TooltipComponent {
}
