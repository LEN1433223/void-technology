package com.voidtechnology;

import appeng.api.AECapabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * AE2 能力注册。这里补的都是"AE2 只给自己注册、不会替附加模组做"的能力。
 *
 * <ol>
 *   <li><b>{@code IN_WORLD_GRID_NODE_HOST}</b>（供应器）：AE2 的线缆通过这个能力查找相邻方块实体
 *       是否暴露 {@code IGridNode}（见 {@code IInWorldGridNodeHost} 的注释：
 *       "Must be provided via the ... capability"）。AE2 在 {@code InitCapabilityProviders} 里
 *       只遍历自己的 {@code AEBlockEntities}，我们注册的方块实体类型不在其中——
 *       这就是"线缆连不上、必须挖掉重放"的根因。</li>
 *   <li><b>{@code CRAFTING_MACHINE}</b>（控制器）：让 AE2 的样板供应器把控制器当作"相邻合成机器"，
 *       从而走它自己的推送路径（含 private 的 {@code onPushPatternSuccess} 记账）。</li>
 * </ol>
 *
 * <p>本类只在 AE2 存在时被加载（调用方先判断 {@link Ae2Compat#isLoaded()}）。</p>
 */
final class Ae2MachineCapabilities {

    private Ae2MachineCapabilities() {
    }

    static void register(RegisterCapabilitiesEvent event) {
        // 供应器：暴露网格节点宿主，相邻线缆才认得出它
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                VoidTechnology.VOID_PATTERN_PROVIDER_BE.get(),
                (blockEntity, side) -> blockEntity);
        // 与 AE2 自身注册保持一致：不允许穿透代理
        event.setNonProxyable(AECapabilities.IN_WORLD_GRID_NODE_HOST);

        // 控制器：暴露成合成机器
        event.registerBlockEntity(
                AECapabilities.CRAFTING_MACHINE,
                VoidTechnology.VOID_CONTROLLER_BE.get(),
                (blockEntity, side) -> blockEntity);

        VoidTechnology.LOGGER.info(
                "[voidlink] registered capabilities: IN_WORLD_GRID_NODE_HOST (pattern provider), "
                        + "CRAFTING_MACHINE (controller)");
    }
}
