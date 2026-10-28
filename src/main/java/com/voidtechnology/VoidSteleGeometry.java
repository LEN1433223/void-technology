package com.voidtechnology;

import net.minecraft.core.BlockPos;

/**
 * 虚空石碑与结构的相对位置计算（服务端成型与客户端预览**共用**，保证线框和落点一致）。
 *
 * <h2>规则</h2>
 * 石碑站在「结构正前方」、「左右居中」、「与结构最低层同高」。
 *
 * <pre>
 *   石碑            结构（向 +X 展开）
 *    ▲  3 格   ┌────────────────────┐
 *    │─────────│       阶梯金字塔     │
 *              └────────────────────┘
 *                        ↑ Z 方向居中
 * </pre>
 *
 * <h2>常量来源</h2>
 * 当前结构 {@code void_palace} 从世界存档直接提取（144×48×145，141,768 方块，含 3 个方块实体）：
 * <ul>
 *   <li>世界范围 x -80..63、y -60..-13、z -72..72</li>
 *   <li>石碑在世界坐标 (-95, -60, 0)：位于建筑<b>西侧</b>（-X 面外）、Z 方向居中、
 *       Y 与建筑最底层同高</li>
 * </ul>
 * 因此正面轴是 <b>X</b>：结构向石碑的 +X 展开；Z 按尺寸居中；Y 对齐石碑。
 * 尺寸从结构模板读取，换结构不需要改代码。
 */
final class VoidSteleGeometry {

    /** 石碑与结构正面之间留的空格数。 */
    static final int FRONT_GAP = 3;

    private VoidSteleGeometry() {
    }

    /**
     * 结构应该放置的原点（结构最小角所在位置）。
     *
     * @param stele 石碑方块位置
     * @param sizeX 结构 X 尺寸（保留参数，便于将来换朝向）
     * @param sizeZ 结构 Z 尺寸（用于 Z 方向居中）
     */
    static BlockPos structureOrigin(BlockPos stele, int sizeX, int sizeZ) {
        return new BlockPos(
                stele.getX() + FRONT_GAP,        // 正面在 +X：石碑站在建筑西侧外 3 格
                stele.getY(),                    // 与建筑最底层同高
                stele.getZ() - sizeZ / 2);       // Z 方向居中
    }
}
