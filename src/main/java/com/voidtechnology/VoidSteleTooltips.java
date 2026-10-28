package com.voidtechnology;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import java.util.List;

/**
 * 虚空石碑的工具提示接线（客户端）。
 *
 * <ul>
 *   <li>{@link RenderTooltipEvent.GatherComponents}：鼠标悬停石碑物品时，往提示里塞一个
 *       {@link TypewriterTooltip}（打字机组件）；</li>
 *   <li>{@link RegisterClientTooltipComponentFactoriesEvent}：把该组件映射到客户端渲染器
 *       {@link TypewriterClientTooltip}。</li>
 * </ul>
 *
 * <p>动画起点在这里按"当前悬停的物品"重置，所以换物品会重新打字。</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = VoidTechnology.MODID, value = Dist.CLIENT)
public final class VoidSteleTooltips {

    /** 逐字动画的起点（毫秒）。 */
    private static long animationStart = System.currentTimeMillis();
    /** 上一次渲染工具提示的时间，用于判断"是否是一次新的悬停"。 */
    private static long lastTooltipRenderMillis;

    /** 两次渲染间隔超过这个值，就认为玩家离开过又移回来了 → 重新打字。 */
    private static final long NEW_HOVER_GAP_MS = 250L;

    private VoidSteleTooltips() {
    }

    static long animationStartMillis() {
        return animationStart;
    }

    /** 由主类在客户端初始化时注册（RegisterClientTooltipComponentEvent 走 mod 总线）。 */
    public static void register(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(TypewriterTooltip.class, TypewriterClientTooltip::new);
    }

    /**
     * 工具提示每帧渲染前触发：用"距上次渲染的间隔"判断是否是新的一次悬停。
     * 这样每次把鼠标移回石碑都会从头打字，而不是只在换物品时重播。
     */
    @SubscribeEvent
    public static void onTooltipPre(RenderTooltipEvent.Pre event) {
        long now = System.currentTimeMillis();
        if (now - lastTooltipRenderMillis > NEW_HOVER_GAP_MS) {
            animationStart = now;
        }
        lastTooltipRenderMillis = now;
    }

    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        if (event.getTooltipElements().isEmpty()) {
            return;
        }
        var stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof VoidSteleBlock)) {
            return;
        }

        event.getTooltipElements().add(com.mojang.datafixers.util.Either.right(new TypewriterTooltip(lines())));
    }

    /** 提示文本（两行，来自语言文件，缺省时回退到中文原文）。 */
    private static List<String> lines() {
        return List.of(
                Component.translatable("tooltip.void_technology.void_stele.line1").getString(),
                Component.translatable("tooltip.void_technology.void_stele.line2").getString());
    }

}
