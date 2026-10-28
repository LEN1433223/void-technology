package com.voidtechnology;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.List;

/**
 * 虚空石碑的交互面板（176×88）：标题 + 两个按钮槽。
 *
 * <ul>
 *   <li><b>预览结构</b>：客户端切换包围盒线框；</li>
 *   <li><b>成型结构</b>：服务端把结构放置到石碑上方。</li>
 * </ul>
 *
 * <p>底图由 {@code docs/stele-gui.html} 经无头 Chrome 渲染（3× 超采样）。</p>
 */
final class VoidSteleUi {

    private static final float PANEL_W = 176f;
    private static final float PANEL_H = 100f;

    private static final float BTN_X = 15f;
    private static final float BTN_W = 146f;
    private static final float BTN_H = 20f;
    private static final float BTN1_Y = 27f;
    private static final float BTN2_Y = 53f;

    private static final float TITLE_X = 26f, TITLE_Y = 4f;
    private static final int TEXT_TITLE = 0xFFE6B9BB;
    private static final int TEXT_DIM = 0xFFA08688;
    /** 按钮配色（与底图的烬色/金色一致）。 */
    private static final int BTN_BASE = 0xCC2A0B0E;
    private static final int BTN_HOVER = 0xEE4A1216;
    private static final int BTN_PRESSED = 0xFF0A0507;

    private VoidSteleUi() {
    }

    /** 自检用：无玩家、无方块实体时渲染面板本身。 */
    static ModularUI createPreview() {
        return build(null, BlockPos.ZERO, "void_technology:void_palace");
    }

    static ModularUI create(BlockUIMenuType.BlockUIHolder holder) {
        var pos = holder.pos;
        var level = holder.player.level();
        var stele = level.getBlockEntity(pos) instanceof VoidSteleBlockEntity be ? be : null;
        return build(stele == null ? null : holder.player, pos,
                stele == null ? "void_technology:void_palace" : stele.getStructureId().toString());
    }

    private static ModularUI build(net.minecraft.world.entity.player.Player player, BlockPos pos, String structureId) {

        var stele = player != null && player.level().getBlockEntity(pos) instanceof VoidSteleBlockEntity be ? be : null;
        var root = new UIElement()
                .layout(layout -> layout.width(PANEL_W).height(PANEL_H));
        // 同上：贴图只在客户端使用
        if (player != null && player.level().isClientSide) {
            root.style(style -> style.background(SpriteTexture.of(panel())));
        }

        root.addChild(absolute(TITLE_X, TITLE_Y, 130f, 11f)
                .addChild(text(Component.translatable("block.void_technology.void_stele"), TEXT_TITLE)));

        // ---- 预览结构（纯客户端切换）
        var previewBtn = button(Component.translatable("gui.void_technology.stele.preview"));
        previewBtn.setOnClick(event -> {
            if (stele != null) {
                VoidStelePreview.toggle(pos, stele.sizeX(), stele.sizeY(), stele.sizeZ());
            }
        });
        root.addChild(absolute(BTN_X, BTN1_Y, BTN_W, BTN_H).addChild(previewBtn));

        // ---- 成型结构（服务端执行）
        var buildBtn = button(Component.translatable("gui.void_technology.stele.build"));
        buildBtn.setOnServerClick(event -> {
            if (player != null && player.level().getBlockEntity(pos) instanceof VoidSteleBlockEntity be) {
                be.buildStructure(player);
            }
        });
        root.addChild(absolute(BTN_X, BTN2_Y, BTN_W, BTN_H).addChild(buildBtn));

        // 底部提示：结构 id
        root.addChild(absolute(16f, 79f, 144f, 9f)
                .addChild(text(Component.literal(structureId), TEXT_DIM)));

        UiScale.fit(root, PANEL_W, PANEL_H);
        return new ModularUI(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC))),
                player);
    }

    private static Button button(Component label) {
        var btn = new Button().setText(label);
        btn.layout(layout -> layout.width(BTN_W).height(BTN_H));
        btn.style(style -> style.background(new ColorRectTexture(BTN_BASE)));
        // Button 的样式对象通过 getButtonStyle() 暴露（没有 buttonStyle(Consumer) 重载）
        btn.getButtonStyle()
                .baseTexture(new ColorRectTexture(BTN_BASE))
                .hoverTexture(new ColorRectTexture(BTN_HOVER))
                .pressedTexture(new ColorRectTexture(BTN_PRESSED));
        return btn;
    }

    private static ResourceLocation panel() {
        return ResourceLocation.fromNamespaceAndPath(VoidTechnology.MODID,
                "textures/gui/void_stele_panel.png");
    }

    private static UIElement absolute(float x, float y, float w, float h) {
        return new UIElement().layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(x).top(y).width(w).height(h));
    }

    private static Label text(Component value, int color) {
        Label label = new Label().setValue(value);
        label.layout(layout -> layout.widthPercent(100f).height(9f));
        label.textStyle(ts -> ts.textAlignHorizontal(Horizontal.LEFT).textColor(color));
        return label;
    }
}
