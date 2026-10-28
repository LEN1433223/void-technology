package com.voidtechnology;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.appliedenergistics.yoga.YogaPositionType;

import java.util.List;
import java.util.function.Supplier;

/**
 * 虚空控制器的 LDLib2 界面 —— 严格按 {@code docs/gui-mockup.html} 的 .ctrl 原型布局。
 *
 * <p>贴图由 {@code docs/gui-art.html} 经无头 Chrome 以 3× 超采样渲染成 4×4/16 帧图集
 * （{@code void_controller_sheet.png}，2112×2112），游戏内由 {@link VoidGuiTextures}
 * 打开线性过滤缩回 176 GUI 像素，因此细线、圆角、辉光都是平滑的。</p>
 *
 * <p><b>关键</b>：{@code AnimationTexture#setCellSize(int)} 是「图集网格列数」（不是像素尺寸），
 * 所以 4×4 图集必须传 4；格子像素 528 是元素尺寸 176 的 3 倍，正好做超采样。</p>
 *
 * <p>坐标全在 176×176 空间内，与 {@code tools/render_gui_html.py} / {@code docs/gui-art.html} 对应。</p>
 */
final class VoidControllerUi {

    // ---- 布局常量（与 docs/gui-art.html 常量一致）
    private static final float PANEL = 176f;
    private static final int SHEET_COLUMNS = 5;      // 图集网格列数（5×5，用 24 格）
    private static final int SHEET_LAST_FRAME = 23;  // 24 帧
    private static final float SECONDS_PER_LOOP = 6.0f;  // 24 帧摊在 6 秒里：比原来慢 80%，靠相邻帧淡入保持顺滑

    private static final float TITLE_X = 8f, TITLE_Y = 6f;
    private static final float ROW_X = 8f, ROW_W = 160f, ROW_H = 14f, ROW_GAP = 4f;
    private static final float ROW_BOTTOM = 9f;      // 原型 .rows{bottom:9px}
    private static final float KEY_X = 22f;          // 行内 5px padding + 4px LED + 5px gap
    private static final float VALUE_X = 82f;
    private static final float TEXT_DY = 3f;
    private static final float TEXT_H = 11f;

    private static final int TEXT_TITLE = 0xFFE6B9BB;
    private static final int TEXT_KEY = 0xFFA08688;
    private static final int TEXT_VALUE = 0xFFECDCDC;

    private static final int TINT_ONLINE = 0xFFFFFFFF;
    private static final int TINT_OFFLINE = 0xFF9EA6B0;

    private VoidControllerUi() {
    }

    static ModularUI create(BlockUIMenuType.BlockUIHolder holder) {
        var be = holder.player.level().getBlockEntity(holder.pos);
        var controller = be instanceof VoidControllerBlockEntity c ? c : null;
        boolean online = controller != null && controller.isOnline();

        if (controller == null) {
            return build(holder.player, online,
                    () -> Component.translatable("gui.void_technology.void_controller.unavailable"),
                    () -> Component.translatable("gui.void_technology.void_controller.task.none"),
                    () -> Component.translatable("gui.void_technology.void_controller.online.off"));
        }
        return build(holder.player, online, controller::structureText, controller::taskText, controller::onlineText);
    }

    /** 界面构建入口（与方块解耦，开发期自检截图可用假数据渲染同一套界面）。 */
    static ModularUI build(Player player, boolean online,
                           Supplier<Component> structure, Supplier<Component> task, Supplier<Component> net) {
        var root = new UIElement()
                .layout(layout -> layout.width(PANEL).height(PANEL));
        // 动画背景只在客户端创建：专用服务器也会构建同一套 UI 以注册容器槽，
        // 那里既不需要绘制，也不应加载客户端渲染类。
        boolean clientSide = player != null && player.level().isClientSide;
        if (clientSide) {
            root.style(style -> style.background(new TimedSheetTexture(sheet(), SHEET_COLUMNS,
                    SHEET_LAST_FRAME + 1, SECONDS_PER_LOOP, online ? 0xFFFFFF : 0x9EA6B0)));
        }

        // ---- 标题（原型 .ctrl-head .t：8px、字距 1px、#e6b9bb）
        root.addChild(absolute(TITLE_X, TITLE_Y, 150f, TEXT_H)
                .addChild(text(Component.translatable("gui.void_technology.void_controller.title"), TEXT_TITLE)));

        // ---- 三行信息槽（原型 .rows：bottom 9px、gap 4px、行高 14px）
        float rowsTop = PANEL - ROW_BOTTOM - (3 * ROW_H + 2 * ROW_GAP);
        addRow(root, 0, rowsTop, "gui.void_technology.void_controller.structure", structure);
        addRow(root, 1, rowsTop + (ROW_H + ROW_GAP), "gui.void_technology.void_controller.task", task);
        addRow(root, 2, rowsTop + 2 * (ROW_H + ROW_GAP), "gui.void_technology.void_controller.online", net);

        UiScale.fit(root, PANEL, PANEL);

        return new ModularUI(
                UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC))),
                player);
    }

    // ------------------------------------------------------------------ 小工具

    static ResourceLocation sheet() {
        return ResourceLocation.fromNamespaceAndPath(VoidTechnology.MODID,
                "textures/gui/void_controller_sheet.png");
    }

    private static UIElement absolute(float x, float y, float w, float h) {
        return new UIElement().layout(layout -> layout
                .positionType(YogaPositionType.ABSOLUTE)
                .left(x).top(y).width(w).height(h));
    }

    private static Label text(Component value, int color) {
        Label label = new Label().setValue(value);
        // 绝对定位容器里的 Label 不会自己撑开，必须显式给尺寸，否则文字不绘制
        label.layout(layout -> layout.widthPercent(100f).height(TEXT_H));
        label.style(style -> style.color(color));
        return label;
    }

    /** 一行：LED 与底板由贴图绘制，这里只放「键名 + 值」。 */
    private static void addRow(UIElement root, int index, float rowTop, String keyKey,
                               Supplier<Component> valueSupplier) {
        float y = rowTop + TEXT_DY;
        float keyW = VALUE_X - KEY_X - 6f;

        root.addChild(absolute(KEY_X, y, keyW, TEXT_H)
                .addChild(text(Component.translatable(keyKey), TEXT_KEY)));

        Label value = new Label().bindDataSource(SupplierDataSource.of(valueSupplier));
        value.layout(layout -> layout.widthPercent(100f).height(TEXT_H));
        value.style(style -> style.color(TEXT_VALUE));
        root.addChild(absolute(VALUE_X, y, ROW_X + ROW_W - VALUE_X - 6f, TEXT_H)
                .addChild(value));
    }
}
