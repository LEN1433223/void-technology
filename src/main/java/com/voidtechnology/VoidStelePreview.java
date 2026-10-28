package com.voidtechnology;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端侧的"预览包围盒"登记表。
 *
 * <p>只影响本地渲染，不需要同步；石碑面板上的「预览结构」按钮调用 {@link #toggle}。</p>
 *
 * <p><b>注意</b>：线框必须用 {@link VoidSteleGeometry#structureOrigin} 计算，才能和
 * 「成型结构」的实际落点完全一致（之前直接用石碑位置导致预览位置不对）。</p>
 */
@ApiStatus.Internal
public final class VoidStelePreview {

    /** 预览存活时间：到点自动消失。 */
    public static final long PREVIEW_DURATION_MS = 10_000L;

    private record Entry(BlockPos stele, int sx, int sy, int sz, long expireAtMillis) {
    }

    private static final Map<BlockPos, Entry> ACTIVE = new ConcurrentHashMap<>();

    private VoidStelePreview() {
    }

    /** 切换某个石碑的预览；尺寸无效（<=0）时不登记。 */
    public static void toggle(BlockPos stele, int sx, int sy, int sz) {
        if (ACTIVE.remove(stele) != null) {
            return;
        }
        if (sx > 0 && sy > 0 && sz > 0) {
            ACTIVE.put(stele.immutable(), new Entry(stele.immutable(), sx, sy, sz,
                    System.currentTimeMillis() + PREVIEW_DURATION_MS));
        }
    }

    public static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    public static void clear() {
        ACTIVE.clear();
    }

    /** 供渲染器使用：世界坐标包围盒（与成型落点一致）。 */
    public static List<Bounds> bounds() {
        // 先清掉过期的预览
        long now = System.currentTimeMillis();
        ACTIVE.entrySet().removeIf(e -> e.getValue().expireAtMillis() <= now);

        List<Bounds> out = new ArrayList<>(ACTIVE.size());
        for (Entry e : ACTIVE.values()) {
            var origin = VoidSteleGeometry.structureOrigin(e.stele(), e.sx(), e.sz());
            double x = origin.getX();
            double y = origin.getY();
            double z = origin.getZ();
            out.add(new Bounds(x, y, z, x + e.sx(), y + e.sy(), z + e.sz(), e.sx(), e.sy(), e.sz()));
        }
        return out;
    }

    public record Bounds(double x0, double y0, double z0, double x1, double y1, double z1,
                         int sx, int sy, int sz) {
    }
}
