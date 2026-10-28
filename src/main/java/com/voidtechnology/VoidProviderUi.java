package com.voidtechnology;

import appeng.api.inventories.InternalInventory;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.List;

/**
 * 虚空样板供应器界面（LDLib2 自绘，208×304）。
 *
 * <p>底图 {@code textures/gui/pattern_provider_panel.png} 由 {@code docs/pattern-provider-gui.html}
 * 经无头 Chrome 渲染（3× 超采样），槽位坐标与 {@code docs/pattern_slots.json} 完全一致：</p>
 *
 * <ul>
 *   <li>三环 12 / 18 / 24 = <b>54 个样板槽</b>，槽心在 r=40/60/80 的环上，起始角 -90°，间隔 360/n</li>
 *   <li>背包 9×3 @ (24,218)、快捷栏 @ (24,278)，槽心 = 左上角 + 8</li>
 *   <li>样板槽绑定 AE2 样板库存（{@code getLogic().getPatternInv()}），只收编码样板</li>
 * </ul>
 *
 * <p><b>注意</b>：LDLib2 的 {@code ItemSlot} 默认会画一块浅灰圆角底，会盖住底图上的暗槽，
 * 所以每个槽都把背景覆盖成全透明。</p>
 */
final class VoidProviderUi {

    // ---- 与 HTML / pattern_slots.json 必须一致
    private static final float PANEL_W = 208f;
    private static final float PANEL_H = 304f;
    private static final float CENTER_X = 104f, CENTER_Y = 112f;
    private static final float[] RING_RADII = {40f, 60f, 80f};
    private static final int[] RING_COUNTS = {12, 18, 24};
    private static final float SLOT = 16f;

    private static final float INV_X = 24f, INV_Y = 218f, HOTBAR_Y = 278f;
    private static final float PITCH = 18f;

    private static final float TITLE_X = 26f, TITLE_Y = 4f;
    private static final float LABEL_X = 26f, LABEL_Y = 207f;
    private static final int TEXT_TITLE = 0xFFE6B9BB;
    private static final int TEXT_DIM = 0xFFA08688;

    private VoidProviderUi() {
    }

    static ModularUI create(BlockUIMenuType.BlockUIHolder holder) {
        var level = holder.player.level();
        var be = level.getBlockEntity(holder.pos);
        var provider = be instanceof VoidPatternProviderBlockEntity p ? p : null;
        InternalInventory patterns = provider == null ? null : provider.getLogic().getPatternInv();
        boolean linked = provider != null && provider.hasLinkedController();
        return build(holder.player, patterns, linked,
                provider == null ? VoidPatternProviderBlockEntity.NetworkState.NOT_READY
                        : provider.getNetworkState());
    }

    /** 自检用：没有玩家 / 没有方块实体时只画底图与 54 个样板槽，用于截图核对布局。 */
    static ModularUI createPreview(boolean linked, VoidPatternProviderBlockEntity.NetworkState state) {
        return build(null, null, linked, state);
    }

    private static ModularUI build(Player player, InternalInventory patternInventory, boolean linked,
                                   VoidPatternProviderBlockEntity.NetworkState previewState) {
        var root = new UIElement()
                .layout(layout -> layout.width(PANEL_W).height(PANEL_H));
        // 背景贴图只在客户端设置：专用服务器也会构建同一套 UI 用来注册容器槽，
        // 那里不需要（也不应该）触碰客户端贴图资源。
        if (player != null && player.level().isClientSide) {
            root.style(style -> style.background(SpriteTexture.of(panel())));
        }

        // ---- 标题（底图标题带 y 3..14，文字从 4 起）
        root.addChild(absolute(TITLE_X, TITLE_Y, 150f, 11f)
                .addChild(text(Component.translatable("block.void_technology.void_pattern_provider"), TEXT_TITLE)));

        // ---- 物品栏标签（底图分隔线 y=206）
        root.addChild(absolute(LABEL_X, LABEL_Y, 60f, 10f)
                .addChild(text(Component.translatable("gui.void_technology.inventory"), TEXT_DIM)));

        // ---- 在线/离线标识（标题带右下角：一个 3px 状态点 + 文字）
        if (patternInventory != null || linked) {
            var state = previewState;
            int dotColor = switch (state) {
                case ONLINE -> 0xFF4CD07D;
                case NO_CHANNEL -> 0xFFE0B341;
                case NO_GRID -> 0xFFE05A4A;
                default -> 0xFF8A8F98;
            };
            String key = switch (state) {
                case ONLINE -> "gui.void_technology.provider.net.online";
                case NO_CHANNEL -> "gui.void_technology.provider.net.no_channel";
                case NO_GRID -> "gui.void_technology.provider.net.no_grid";
                default -> "gui.void_technology.provider.net.not_ready";
            };
            // 整行右锚定：右边距 10px，与标题金线的左侧缩进（10px）对称；
            // 文字右对齐、状态点贴最右，所以中文/英文长度不同也不会跑偏。
            final float ROW_W = 70f;
            final float RIGHT_INSET = 10f;
            float rowLeft = PANEL_W - RIGHT_INSET - ROW_W;

            Label stateLabel = new Label().setValue(Component.translatable(key));
            stateLabel.textStyle(ts -> ts
                    .textAlignHorizontal(Horizontal.RIGHT)
                    .textColor(dotColor));
            stateLabel.layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                    .left(0f).top(1f).width(ROW_W - 7f).height(10f));

            var stateDot = new UIElement()
                    .layout(layout -> layout.positionType(YogaPositionType.ABSOLUTE)
                            .left(ROW_W - 4f).top(3f).width(4f).height(4f))
                    .style(style -> style.background(new ColorRectTexture(dotColor)));

            root.addChild(absolute(rowLeft, 3f, ROW_W, 10f)
                    .addChild(stateLabel)
                    .addChild(stateDot));
        }

        // ---- 未连接控制器：整体压暗并给出可操作提示（模型 C：没有控制器就完全不可用）
        if (!linked) {
            root.addChild(absolute(0f, 0f, PANEL_W, PANEL_H)
                    .style(style -> style.background(new ColorRectTexture(0xAA0A0406))));
            root.addChild(absolute(0f, PANEL_H / 2f - 6f, PANEL_W, 12f)
                    .addChild(text(Component.translatable("gui.void_technology.void_pattern_provider.no_controller"),
                            0xFFFF6A4A)));
        }

        // ---- 54 个样板槽
        if (patternInventory != null) {
            int index = 0;
            for (int ring = 0; ring < RING_RADII.length; ring++) {
                float radius = RING_RADII[ring];
                int count = RING_COUNTS[ring];
                for (int i = 0; i < count && index < patternInventory.size(); i++, index++) {
                    double angle = Math.toRadians(-90 + i * 360.0 / count);
                    float cx = (float) (CENTER_X + Math.cos(angle) * radius);
                    float cy = (float) (CENTER_Y + Math.sin(angle) * radius);
                    root.addChild(slotAt(cx, cy, new PatternInventorySlot(patternInventory, index)));
                }
            }
        }

        // ---- 玩家背包（9×3 + 快捷栏 9）
        if (player != null) {
            var inventory = player.getInventory();
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    float cx = INV_X + col * PITCH + SLOT / 2f;
                    float cy = INV_Y + row * PITCH + SLOT / 2f;
                    root.addChild(slotAt(cx, cy, new Slot(inventory, 9 + row * 9 + col, 0, 0)));
                }
            }
            for (int col = 0; col < 9; col++) {
                float cx = INV_X + col * PITCH + SLOT / 2f;
                float cy = HOTBAR_Y + SLOT / 2f;
                root.addChild(slotAt(cx, cy, new Slot(inventory, col, 0, 0)));
            }
        }

        UiScale.fit(root, PANEL_W, PANEL_H);

        return new ModularUI(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC))),
                player);
    }

    /** 槽元素：以槽心定位，尺寸 16×16，背景透明（槽的观感由底图提供）。 */
    private static UIElement slotAt(float centerX, float centerY, Slot slot) {
        var element = absolute(centerX - SLOT / 2f, centerY - SLOT / 2f, SLOT, SLOT);
        // 注意：UIElement#layout / #style 返回 UIElement，会挡住 ItemSlot 自己的 #slotStyle，
        // 所以这里分步设置：先 slotStyle（悬停/常驻高亮），再 layout 与 background。
        ItemSlot itemSlot = new ItemSlot(slot);
        // 悬停高亮：默认是半透明白色填充块，改成 1px 描边（烬色），只描方块边缘
        itemSlot.slotStyle(style -> style
                .hoverOverlay(new ColorBorderTexture(1, 0xFFFF6A4A))
                .slotOverlay(new ColorRectTexture(0x00000000)));
        itemSlot.layout(layout -> layout.width(SLOT).height(SLOT));
        // 槽底由底图提供，把 ItemSlot 自带的浅灰底盖成透明
        itemSlot.style(style -> style.background(new ColorRectTexture(0x00000000)));
        element.addChild(itemSlot);
        return element;
    }

    private static ResourceLocation panel() {
        return ResourceLocation.fromNamespaceAndPath(VoidTechnology.MODID,
                "textures/gui/pattern_provider_panel.png");
    }

    private static UIElement absolute(float x, float y, float w, float h) {
        return new UIElement().layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(x).top(y).width(w).height(h));
    }

    private static Label text(Component value, int color) {
        Label label = new Label().setValue(value);
        label.layout(layout -> layout.widthPercent(100f).height(11f));
        label.style(style -> style.color(color));
        return label;
    }
}
