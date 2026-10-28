package com.voidtechnology;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 虚空样板供应器的逻辑。
 *
 * <p>这里只做两件事，其余全部交给 AE2 本体的 {@link PatternProviderLogic}：</p>
 * <ol>
 *   <li><b>可用性门控</b>：没有相邻的、在线的虚空控制器时，不上报任何样板
 *       （合成终端里根本选不到它，也不会消耗材料）；</li>
 *   <li><b>相邻控制器探测</b>：供控制器反向确认"自己连着在线供应器"。</li>
 * </ol>
 *
 * <p><b>为什么不再自己实现推送：</b>AE2 的推送路径会先找相邻的 {@code ICraftingMachine}，
 * 成功后才调用 private 的 {@code onPushPatternSuccess} 记账（合成 CPU 靠它确认这一步已执行）。
 * 所以虚空控制器实现该接口后，材料销毁与产物回吐都在控制器侧完成，AE2 的账自然正确——
 * 之前自己覆写 {@code pushPattern} 会导致 CPU 卡在"正在合成"、ETA 变成天文数字。</p>
 */
public class VoidPatternProviderLogic extends PatternProviderLogic {

    private final IManagedGridNode node;
    private final PatternProviderLogicHost host;
    private final int patternSlots;

    public VoidPatternProviderLogic(IManagedGridNode node, PatternProviderLogicHost host, int patternSlots) {
        super(node, host, patternSlots);
        this.node = node;
        this.host = host;
        this.patternSlots = patternSlots;
    }

    public int getPatternSlots() {
        return patternSlots;
    }

    /** 网络是否真正可用（ONLINE = 已连接且拿到频道）；排错见 {@link VoidPatternProviderBlockEntity.NetworkState}。 */
    public boolean isGridActive() {
        if (host instanceof VoidPatternProviderBlockEntity be) {
            return be.isGridActive();
        }
        return node != null && node.isActive();
    }

    /** 六面任一相邻的虚空控制器；没有则 null。 */
    @Nullable
    VoidControllerBlockEntity findController() {
        return host instanceof VoidPatternProviderBlockEntity be ? be.findController() : null;
    }

    /** 没有控制器 = 不可用：不上报任何样板，合成终端不会选中它。 */
    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        var controller = findController();
        if (controller == null || !controller.isLinked()) {
            return List.of();
        }
        return super.getAvailablePatterns();
    }
}
