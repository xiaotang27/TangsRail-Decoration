package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.control.PsdStyles;

/**
 * 站台门外观设置（蹲下 + 刷子右键屏蔽门 / 玻璃 / 端头玻璃打开），分两段：
 * <ul>
 */
public class PsdAppearanceScreen extends TrdmScreenBase {

    private static final float ROW_H = 24F;
    private static final float ROWS_TOP_OFFSET = 26F;
    private static final float SECTION_GAP = 46F;

    private static final float BUTTON_W = 84F;
    private static final float BUTTON_H = 24F;
    private static final float BUTTON_Y = TITLE_Y;
    private static final float BUTTON_X = CONTENT_RIGHT - BUTTON_W;

    private final BlockPos headerPos;
    private int headerStyle;
    private int bandStyle;
    private boolean endGate;

    public PsdAppearanceScreen(BlockPos headerPos, int headerStyle, int bandStyle, boolean endGate) {
        super(null, Component.translatable("block.trdm.psd_door_1"));
        this.headerPos = headerPos;
        this.headerStyle = headerStyle;
        this.bandStyle = bandStyle;
        this.endGate = endGate;
    }

    private float headerRowsTop() {
        return SECTION_Y + ROWS_TOP_OFFSET;
    }

    private float bandSectionY() {
        return headerRowsTop() + PsdStyles.HEADER_STYLES.length * ROW_H + SECTION_GAP;
    }

    private float bandRowsTop() {
        return bandSectionY() + ROWS_TOP_OFFSET;
    }

    private float endGateSectionY() {
        return bandRowsTop() + PsdStyles.BAND_STYLES.length * ROW_H + SECTION_GAP;
    }

    @Override
    protected void renderPage(GuiGraphics guiGraphics, float mx, float my) {
        title(guiGraphics, title);
        hint(guiGraphics, "gui.trdm.psd_style.hint", CONTENT_X, SUB_Y);

        buttonText(guiGraphics, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.trdm.psd_style.done"), false,
                hit(mx, my, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H), FONT_ROW);

        section(guiGraphics, "gui.trdm.psd_style.header_label", SECTION_Y);
        renderOptions(guiGraphics, headerRowsTop(), PsdStyles.HEADER_STYLES, headerStyle, true, mx, my);

        section(guiGraphics, "gui.trdm.psd_style.band_label", bandSectionY());
        renderOptions(guiGraphics, bandRowsTop(), PsdStyles.BAND_STYLES, bandStyle, false, mx, my);

        // 端门开关（沿用预设 toggle 样式）
        final float rowTop = endGateSectionY() + ROWS_TOP_OFFSET;
        text(guiGraphics, Component.translatable("gui.trdm.psd_style.end_gate"),
                CONTENT_X + 2F, rowTop + 2F, FONT_ROW, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.psd_style.end_gate_hint"),
                CONTENT_X + 2F, rowTop + 18F, FONT_SUB, COLOR_TEXT);
        toggle(guiGraphics, CONTENT_RIGHT - TOGGLE_W - 2F, rowTop, endGate);
    }

    private void renderOptions(GuiGraphics guiGraphics, float rowsTop, int[] options, int selected, boolean isHeader,
                               float mx, float my) {
        for (int index = 0; index < options.length; index++) {
            final int style = options[index];
            final float top = rowsTop + index * ROW_H;
            final boolean hovered = mx >= CONTENT_X && mx < CONTENT_RIGHT && my >= top && my < top + ROW_H;
            final boolean active = style == selected;
            row(guiGraphics, CONTENT_X, top, CONTENT_W, ROW_H - 2F, active, hovered);

            final String nameKey = isHeader ? PsdStyles.headerNameKey(style) : PsdStyles.bandNameKey(style);
            text(guiGraphics, Component.translatable(nameKey), CONTENT_X + 9F, top + 6F, FONT_ROW, COLOR_TEXT);
            if (active) {
                final Component tag = Component.translatable("gui.trdm.psd_style.in_use");
                textRight(guiGraphics, tag, CONTENT_RIGHT - 10F, top + 8F, FONT_SUB, COLOR_TEXT);
            }
        }
    }

    @Override
    protected boolean clickPage(float mx, float my) {
        if (hit(mx, my, BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H)) {
            onClose();
            return true;
        }
        if (select(my, headerRowsTop(), PsdStyles.HEADER_STYLES, true)) {
            return true;
        }
        if (select(my, bandRowsTop(), PsdStyles.BAND_STYLES, false)) {
            return true;
        }
        final float rowTop = endGateSectionY() + ROWS_TOP_OFFSET;
        if (my >= rowTop && my < rowTop + ROW_H) {
            endGate = !endGate;
            top.xiaotang27.trdm.client.TrdmControlClient.sendEndGate(headerPos.below(), endGate);
            return true;
        }
        return false;
    }

    private boolean select(float my, float rowsTop, int[] options, boolean isHeader) {
        final int index = (int) ((my - rowsTop) / ROW_H);
        if (my < rowsTop || index < 0 || index >= options.length) {
            return false;
        }
        final int style = options[index];
        if (isHeader) {
            if (style == headerStyle) {
                return true;
            }
            headerStyle = style;
        } else {
            if (style == bandStyle) {
                return true;
            }
            bandStyle = style;
        }
        TrdmControlClient.sendPsdAppearance(headerPos, headerStyle, bandStyle);
        return true;
    }
}
