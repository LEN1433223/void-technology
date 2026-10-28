package com.voidtechnology;

import appeng.block.crafting.PatternProviderBlock;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 虚空样板供应器方块。
 *
 * <p>继承 AE2 的 {@link PatternProviderBlock}：push_direction 等方块状态、放置与旋转逻辑沿用 AE2；
 * 方块实体换成 {@link VoidPatternProviderBlockEntity}（54 个样板槽）。
 * 界面不再复用 AE2 的 Screen，而是用 LDLib2 自绘（见 {@link VoidProviderUi}），
 * 底图由 {@code docs/pattern-provider-gui.html} 渲染。</p>
 */
public class VoidPatternProviderBlock extends PatternProviderBlock implements BlockUIMenuType.BlockUI {

    public VoidPatternProviderBlock() {
        super();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VoidPatternProviderBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            // 打开界面前刷新网络状态，让界面上的在线/离线标识是实时值；
            // 不在线时额外用动作栏把「到底缺什么」直接告诉玩家。
            if (level.getBlockEntity(pos) instanceof VoidPatternProviderBlockEntity provider) {
                provider.refreshConnectableSides();
                provider.refreshNetworkState();
                var netState = provider.getNetworkState();
                if (netState != VoidPatternProviderBlockEntity.NetworkState.ONLINE) {
                    serverPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable(switch (netState) {
                                case NO_GRID -> "msg.void_technology.provider.no_grid";
                                case NO_CHANNEL -> "msg.void_technology.provider.no_channel";
                                default -> "msg.void_technology.provider.not_ready";
                            }), true);
                }
            }
            // 用 LDLib2 打开自绘界面（54 个样板槽，符文盘布局）
            BlockUIMenuType.openUI(serverPlayer, pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return VoidProviderUi.create(holder);
    }
}
