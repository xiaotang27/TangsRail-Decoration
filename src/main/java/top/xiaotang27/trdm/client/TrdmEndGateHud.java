package top.xiaotang27.trdm.client;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 端门拖动时的**屏幕 UI 开度指示条**：屏幕右侧居中的一根竖直条，浅灰槽 + 青色填充，
 * 填充高度 = 当前开度。只在拖动期间画，松手立刻消失；不显示百分比数字。
 * <p>
 * 尺寸都是像素，想挪位置/改大小改这里几个常量即可。
 */
public final class TrdmEndGateHud {

    /** 槽尺寸（像素）。 */
    private static final int BAR_WIDTH = 26;
    private static final int BAR_HEIGHT = 192;
    /** 填充条宽度与左右内边距。 */
    private static final int FILL_WIDTH = 14;
    /** 槽离屏幕右边缘多远。 */
    private static final int RIGHT_MARGIN = 90;

    /** 外框（半透明深灰）、槽（浅灰白）、填充（青色）。 */
    private static final int COLOR_OUTLINE = 0xB0202429;
    private static final int COLOR_TRACK = 0xE8F2F4F8;
    private static final int COLOR_FILL = 0xFF48B0F7;

    private TrdmEndGateHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((GuiGraphics graphics, float tickDelta) -> render(graphics));
    }

    private static void render(GuiGraphics graphics) {
        final float progress = TrdmEndGateDrag.progress();
        if (progress < 0F) {
            return;
        }
        final int x = graphics.guiWidth() - RIGHT_MARGIN;
        final int y = (graphics.guiHeight() - BAR_HEIGHT) / 2;

        // 外框 + 槽
        graphics.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, COLOR_OUTLINE);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, COLOR_TRACK);

        // 填充：从槽底往上，高度 = 开度 × 槽高（上下各留 2 像素内边距）
        final int inner = BAR_HEIGHT - 4;
        final int fillHeight = Math.round(inner * Math.max(0F, Math.min(1F, progress)));
        if (fillHeight <= 0) {
            return;
        }
        final int fillX = x + (BAR_WIDTH - FILL_WIDTH) / 2;
        final int bottom = y + BAR_HEIGHT - 2;
        graphics.fill(fillX, bottom - fillHeight, fillX + FILL_WIDTH, bottom, COLOR_FILL);
    }
}
