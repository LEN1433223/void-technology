package com.voidtechnology;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 把虚空供应器排队的产物在**下一个服务端 tick** 注入 ME 网络。
 *
 * <p>为什么必须延后一个 tick：AE2 的合成 CPU 在 {@code pushPattern} 返回之后才把期望产物
 * 登记到 {@code job.waitingFor}，提前插入会被 CPU 拒收（返回 0），物品落进普通存储，
 * 合成任务便永远等不到产物。</p>
 *
 * <p>为什么不用 AE2 的 returnInv：它按物品种类的单组容量（物品 64）限制每个槽，
 * 大批量产物会被丢弃。</p>
 */
public final class VoidLinkTicker {

    private VoidLinkTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (var provider : VoidPatternProviderBlockEntity.pendingProviders()) {
            try {
                provider.flushPendingOutputs();
            } catch (Throwable t) {
                VoidTechnology.LOGGER.error("[voidlink] failed to flush pending outputs", t);
            }
        }
    }
}
