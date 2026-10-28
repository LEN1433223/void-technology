package com.voidtechnology;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 虚空控制器方块实体。
 *
 * <p>按模型 C：控制器不解析样板、不关心目标产物，只负责 <b>销毁</b> 与 <b>状态展示</b>。</p>
 *
 * <ul>
 *     <li>{@link #isLinked()} —— 是否隔着相邻面连上一个在线的虚空样板供应器（控制器自身没有 AE 节点）</li>
 *     <li>{@link #getTotalVoided()} —— 累计销毁数量（仅统计，不参与逻辑）</li>
 *     <li>{@link #taskText()} —— 固定文案（不识别具体物品）</li>
 * </ul>
 */
public class VoidControllerBlockEntity extends BlockEntity implements ICraftingMachine {

    private static final String TAG_FORMED = "StructureFormed";
    private static final String TAG_LINKED = "Linked";
    private static final String TAG_TOTAL = "TotalVoided";

    /** 是否隔着相邻面连上了一个在线的虚空样板供应器（控制器本身不接 AE 网络）。 */
    /** 是否连着网格激活的虚空样板供应器。 */
    private boolean linked;
    /** 累计销毁数量（展示用）。 */
    private long totalVoided;

    public VoidControllerBlockEntity(BlockPos pos, BlockState state) {
        super(VoidTechnology.VOID_CONTROLLER_BE.get(), pos, state);
    }

    /** 兼容旧调用点：控制器的"成型"就是"已连上在线供应器"。 */
    public boolean isStructureFormed() {
        return linked;
    }

    public boolean isLinked() {
        return linked;
    }

    /** 在线 = 结构成型 && AE2 在场 && 已连上激活的供应器。 */
    public boolean isOnline() {
        return linked && Ae2Compat.isLoaded();
    }


    public long getTotalVoided() {
        return totalVoided;
    }

    // ------------------------------------------------------------------ 销毁入口

    /**
     * 销毁一批契约内物品。由 {@link VoidPatternProviderLogic#pushPattern} 调用。
     * 只记账与丢弃，不落任何实际物品。
     */
    public void absorb(AEKey key, long amount) {
        if (key == null || amount <= 0) return;
        // 只做统计：界面不再识别具体物品，任务行固定显示常量文本
        totalVoided += amount;
        setChanged();
    }

    /** 供应器每完成一次推送回调，累计统计。 */
    public void onVoided(long amount) {
        if (amount <= 0) return;
        totalVoided += amount;
        setChanged();
    }


    // ------------------------------------------------------------------ ICraftingMachine

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        return new PatternContainerGroup(
                AEItemKey.of(VoidTechnology.VOID_CONTROLLER_ITEM.get()),
                Component.translatable("block.void_technology.void_controller"),
                List.of());
    }

    /** 只有"连着在线供应器"时才接受推送，否则让 AE2 继续找别的目标。 */
    @Override
    public boolean acceptsPlans() {
        var provider = findOnlineProvider();
        if (provider == null && !loggedAcceptFalse) {
            loggedAcceptFalse = true;
            VoidTechnology.LOGGER.info("[voidlink] controller at {} rejects plans: no online provider adjacent", worldPosition);
        } else if (provider != null && loggedAcceptFalse) {
            loggedAcceptFalse = false;
            VoidTechnology.LOGGER.info("[voidlink] controller at {} accepts plans (provider online)", worldPosition);
        }
        return provider != null;
    }

    private boolean loggedAcceptFalse;

    /**
     * AE2 的样板供应器把材料推给我们：这里直接销毁，并让隔壁在线供应器把产物插回网络。
     * 返回 true 后 AE2 自己会完成合成记账（这正是 CPU 能认出完成的关键）。
     */
    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputs, Direction ejectionDirection) {
        var provider = findOnlineProvider();
        if (provider == null || patternDetails == null) {
            VoidTechnology.LOGGER.warn("[voidlink] controller pushPattern rejected (provider={}, details={})",
                    provider != null, patternDetails != null);
            return false;
        }
        VoidTechnology.LOGGER.info("[voidlink] controller pushPattern: inputs={}, outputs={}",
                inputs == null ? 0 : inputs.length,
                patternDetails.getOutputs().size());

        long voided = 0;
        for (KeyCounter counter : inputs) {
            if (counter == null) continue;
            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (key == null || amount <= 0) continue;
                absorb(key, amount);
                voided += amount;
            }
        }

        provider.queueOutputs(patternDetails.getOutputs());
        onVoided(voided);
        provider.refreshNetworkState();
        sync();
        return true;
    }

    /** 六面中第一个"网格在线"的虚空样板供应器。 */
    private VoidPatternProviderBlockEntity findOnlineProvider() {
        if (level == null) return null;
        for (Direction dir : Direction.values()) {
            var be = level.getBlockEntity(worldPosition.relative(dir));
            if (be instanceof VoidPatternProviderBlockEntity provider
                    && !provider.isRemoved()
                    && provider.isGridActive()) {
                return provider;
            }
        }
        return null;
    }
    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, VoidControllerBlockEntity be) {
        // 每秒刷新一次结构与连接状态，避免每 tick 扫邻居
        if (level.getGameTime() % 20L != 0L) return;

        boolean nowLinked = checkLinked(level, pos);
        boolean changed = false;

        if (nowLinked != be.linked) {
            // 方块外观随连接状态切换（FORMED 属性保留，语义改为"已连接"）
            BlockState newState = state.setValue(VoidControllerBlock.FORMED, nowLinked);
            level.setBlock(pos, newState, 3);
            be.linked = nowLinked;
            changed = true;
        }
        if (changed) {
            be.setChanged();
            be.sync();
        }
    }

    /** 六面之一存在网格激活的虚空样板供应器即算连上。 */
    private static boolean checkLinked(Level level, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            var be = level.getBlockEntity(pos.relative(dir));
            if (be instanceof VoidPatternProviderBlockEntity provider
                    && !provider.isRemoved()
                    && provider.isGridActive()) {
                return true;
            }
        }
        return false;
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ 存档与同步

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(TAG_FORMED, linked);
        tag.putBoolean(TAG_LINKED, linked);
        tag.putLong(TAG_TOTAL, totalVoided);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // TAG_FORMED 保留读取以兼容旧存档，语义等同 linked
        linked = tag.getBoolean(TAG_LINKED) || tag.getBoolean(TAG_FORMED);
        totalVoided = tag.getLong(TAG_TOTAL);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** 给 GUI 用的三行文本（字段已同步，两端都能算）。 */
    public Component structureText() {
        boolean formed = isLinked();
        return Component.translatable(formed
                        ? "gui.void_technology.void_controller.structure.formed"
                        : "gui.void_technology.void_controller.structure.unformed")
                .withStyle(formed ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    /** 任务行：固定显示常量文本（不识别具体物品）。 */
    public Component taskText() {
        return Component.translatable("gui.void_technology.void_controller.task.voiding")
                .withStyle(ChatFormatting.GREEN);
    }

    public Component onlineText() {
        boolean online = isOnline();
        if (!online && !isLinked()) {
            // 结构成型但没连供应器：给出可操作的提示
            return Component.translatable("gui.void_technology.void_controller.online.unlinked")
                    .withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable(online
                        ? "gui.void_technology.void_controller.online.on"
                        : "gui.void_technology.void_controller.online.off")
                .withStyle(online ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
