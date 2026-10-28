package com.voidtechnology;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 虚空样板供应器的方块实体。
 *
 * <p>继承 AE2 的 {@link PatternProviderBlockEntity}：样板槽、推送调度、与 ME 网络/合成 CPU 的联动
 * 全部复用 AE2 本体；容量放大到 54，逻辑换成 {@link VoidPatternProviderLogic}。</p>
 *
 * <p>网络状态分四档，用于界面上的在线/离线标识与排错：</p>
 * <ol>
 *   <li>{@link NetworkState#NOT_READY} —— 方块实体的网格节点还没创建（区块刚加载/未 ready）</li>
 *   <li>{@link NetworkState#NO_GRID} —— 节点在，但没连到任何 ME 网络（附近没线缆/没供电网络）</li>
 *   <li>{@link NetworkState#NO_CHANNEL} —— 连上了网络，但拿不到频道或网络没电</li>
 *   <li>{@link NetworkState#ONLINE} —— 已连接并获得频道</li>
 * </ol>
 */
public class VoidPatternProviderBlockEntity extends PatternProviderBlockEntity {

    /** 样板槽数量：12 + 18 + 24 = 54（符文盘三环布局），原版为 9。 */
    public static final int PATTERN_SLOTS = 54;

    private static final String TAG_NET_STATE = "NetworkState";

    /** 网络连接状态（供 UI 与联动判定使用）。 */
    public enum NetworkState {
        NOT_READY,
        NO_GRID,
        NO_CHANNEL,
        ONLINE
    }

    /** 相邻控制器缓存，每 20 tick 刷新一次，避免每次都扫六个面。 */
    private VoidControllerBlockEntity cachedController;
    private long cachedAt = Long.MIN_VALUE;

    /** 最近一次计算的网络状态，随方块实体数据同步给客户端。 */
    private NetworkState networkState = NetworkState.NOT_READY;


    public VoidPatternProviderBlockEntity(BlockPos pos, BlockState state) {
        super(VoidTechnology.VOID_PATTERN_PROVIDER_BE.get(), pos, state);
    }

    /**
     * AE2 的 {@link PatternProviderLogic} 支持自定义库存容量（默认 9），这里换成 54 并换成虚空逻辑。
     *
     * <p>注意：该方法是父类字段初始化期被调用的，所以容量必须是编译期常量。</p>
     */
    @Override
    protected PatternProviderLogic createLogic() {
        return new VoidPatternProviderLogic(this.getMainNode(), this, PATTERN_SLOTS);
    }

    /**
     * 六面全部允许接入 ME 网络。
     *
     * <p>AE2 原版实现会在 {@code push_direction} 指向某一面时，把那一面从可连接面里排除；
     * 而该集合只在方块实体构造时设置一次（{@code onGridConnectableSidesChanged()}），
     * 之后用扳手改方向不会刷新 → 表现为"线缆连不上，挖掉重放才好"。</p>
     *
     * <p>虚空供应器总是把材料送进相邻控制器，没有"专属输出面"，所以这里直接放开六面。</p>
     */
    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    /**
     * 重新计算并应用"可连接面"，等价于重建方块实体时发生的那一步。
     * 玩家右键、网络状态变化时调用，用来修掉线缆的陈旧连接状态。
     */
    public void refreshConnectableSides() {
        try {
            onGridConnectableSidesChanged();
        } catch (Throwable t) {
            VoidTechnology.LOGGER.warn("[voidlink] failed to refresh connectable sides", t);
        }
    }

    /**
     * 轻推一下邻居，让相邻线缆重新扫描连接（效果等同于"挖掉重放"，但不动方块）。
     */
    public void nudgeNeighbors() {
        if (level == null || level.isClientSide) return;
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        sync();
    }
    // ------------------------------------------------------------------ 网络状态

    public NetworkState getNetworkState() {
        return networkState;
    }

    public boolean isOnline() {
        return networkState == NetworkState.ONLINE;
    }

    /**
     * 服务端调用：重新计算网络状态、必要时写盘并同步给客户端。
     * 打开界面、网络事件、以及推送成功时都会调用。
     */
    public void refreshNetworkState() {
        if (level == null || level.isClientSide) return;

        NetworkState next;
        boolean ready;
        boolean active;
        boolean hasGrid;
        try {
            var node = getMainNode();
            ready = node != null && node.isReady();
            hasGrid = ready && node.getGrid() != null;
            active = ready && node.isActive();
            if (!ready) {
                next = NetworkState.NOT_READY;
            } else if (!hasGrid) {
                next = NetworkState.NO_GRID;
            } else if (!active) {
                next = NetworkState.NO_CHANNEL;
            } else {
                next = NetworkState.ONLINE;
            }
        } catch (Throwable t) {
            ready = false;
            hasGrid = false;
            active = false;
            next = NetworkState.NOT_READY;
        }


        if (next != networkState) {
            VoidTechnology.LOGGER.info(
                    "[voidlink] provider at {} state {} -> {} (ready={}, grid={}, active={})",
                    worldPosition, networkState, next, ready, hasGrid, active);
            networkState = next;
            setChanged();
            sync();
        }
    }

    /** 六面任一相邻的虚空控制器（缓存 20 tick）。 */
    VoidControllerBlockEntity findController() {
        if (level == null) return null;
        long now = level.getGameTime();
        if (cachedController != null && !cachedController.isRemoved() && cachedAt != Long.MIN_VALUE
                && now - cachedAt < 20L) {
            return cachedController;
        }
        cachedAt = now;
        cachedController = null;
        for (Direction dir : Direction.values()) {
            var be = level.getBlockEntity(worldPosition.relative(dir));
            if (be instanceof VoidControllerBlockEntity controller && !controller.isRemoved()) {
                cachedController = controller;
                break;
            }
        }
        return cachedController;
    }

    public boolean hasLinkedController() {
        return findController() != null;
    }

    /** 网格是否激活（逻辑判定用；AE2 缺席时安全返回 false）。 */
    public boolean isGridActive() {
        if (!Ae2Compat.isLoaded()) return false;
        refreshNetworkState();
        return networkState == NetworkState.ONLINE;
    }

    // ------------------------------------------------------------------ 产物回吐

    /**
     * 把本次合成的产物插回 ME 网络；网络满时余量进 AE2 的返回库存（会周期性重试）。
     * 由相邻的虚空控制器在 {@code ICraftingMachine#pushPattern} 里调用。
     */
    /**
     * 排队待注入的产物。
     *
     * <p><b>为什么不能直接插入网络：</b>AE2 的合成 CPU 是在 {@code pushPattern} 返回之后
     * 才把期望产物登记进 {@code job.waitingFor}；在此之前插入会被 CPU 拒收（返回 0），
     * 物品落进普通存储而 CPU 永远等不到产物。</p>
     *
     * <p><b>为什么不用 AE2 的 returnInv：</b>它是 {@code GenericStackInv} 且调用了
     * {@code useRegisteredCapacities()}，每个槽的容量受物品种类限制（物品=64），
     * 一次排队超过一组就会被丢弃——这正是"请求 1000 只出 64"的原因。</p>
     *
     * <p>所以这里用自建队列（按 key 合并、数量为 long），在下一个服务端 tick 由
     * {@link VoidLinkTicker} 注入网络，那时 {@code waitingFor} 已登记，CPU 会正常结算。</p>
     */
    private final List<GenericStack> pendingOutputs = new ArrayList<>();

    private static final java.util.Set<VoidPatternProviderBlockEntity> PENDING_PROVIDERS =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** 控制器推送完成后调用：把产物登记到延迟队列。 */
    public void queueOutputs(List<GenericStack> outputs) {
        if (outputs == null || outputs.isEmpty()) return;
        synchronized (pendingOutputs) {
            for (GenericStack out : outputs) {
                if (out == null || out.amount() <= 0) continue;
                boolean merged = false;
                for (int i = 0; i < pendingOutputs.size(); i++) {
                    var cur = pendingOutputs.get(i);
                    if (cur.what().equals(out.what())) {
                        pendingOutputs.set(i, new GenericStack(cur.what(), cur.amount() + out.amount()));
                        merged = true;
                        break;
                    }
                }
                if (!merged) {
                    pendingOutputs.add(new GenericStack(out.what(), out.amount()));
                }
            }
            PENDING_PROVIDERS.add(this);
            VoidTechnology.LOGGER.info("[voidlink] queued {} pending output kind(s), total {}",
                    pendingOutputs.size(),
                    pendingOutputs.stream().mapToLong(GenericStack::amount).sum());
        }
    }

    /** 服务端每个 tick 调用：把队列里的产物真正插进 ME 网络（此时 CPU 已登记等待）。 */
    public void flushPendingOutputs() {
        if (level == null || level.isClientSide) return;
        List<GenericStack> snapshot;
        synchronized (pendingOutputs) {
            if (pendingOutputs.isEmpty()) {
                PENDING_PROVIDERS.remove(this);
                return;
            }
            snapshot = new ArrayList<>(pendingOutputs);
            pendingOutputs.clear();
        }

        var node = getMainNode();
        var grid = node != null && node.isReady() ? node.getGrid() : null;
        if (grid == null) {
            // 网络暂时不可用：放回去，下个 tick 再试
            synchronized (pendingOutputs) {
                for (GenericStack s : snapshot) pendingOutputs.add(s);
                PENDING_PROVIDERS.add(this);
            }
            return;
        }

        var storage = grid.getStorageService().getInventory();
        boolean anyLeft = false;
        for (GenericStack stack : snapshot) {
            long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, IActionSource.empty());
            long remaining = stack.amount() - inserted;
            VoidTechnology.LOGGER.info("[voidlink] flush {} x{} (inserted {}, remaining {})",
                    stack.what().getId(), stack.amount(), inserted, remaining);
            if (remaining > 0) {
                synchronized (pendingOutputs) {
                    pendingOutputs.add(new GenericStack(stack.what(), remaining));
                }
                anyLeft = true;
            }
        }
        if (anyLeft) {
            PENDING_PROVIDERS.add(this);
        } else {
            PENDING_PROVIDERS.remove(this);
        }
    }

    /** 供 tick 处理器遍历。 */
    public static java.util.Collection<VoidPatternProviderBlockEntity> pendingProviders() {
        return PENDING_PROVIDERS;
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        PENDING_PROVIDERS.remove(this);
    }
    // ------------------------------------------------------------------ 存档与同步

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByte(TAG_NET_STATE, (byte) networkState.ordinal());
    }

    // AE2 的 tag 钩子是 loadTag（loadAdditional 是 final）
    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        byte raw = tag.getByte(TAG_NET_STATE);
        var values = NetworkState.values();
        networkState = values[Math.max(0, Math.min(values.length - 1, raw))];
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}
