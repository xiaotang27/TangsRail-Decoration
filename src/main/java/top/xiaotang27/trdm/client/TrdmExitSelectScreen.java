package top.xiaotang27.trdm.client;

import mtr.data.Station;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.control.ExitNames;

import java.util.ArrayList;
import java.util.List;

/**
 * 大站名牌的设置界面（刷子右键大站名牌打开）。分两段：
 * <ul>
 *   <li><b>牌子设置</b>：站名后缀（STATION / ZHAN）、无障碍电梯牌，各一个开关；</li>
 *   <li><b>选择本站出口</b>：本站的出口列表（A / A1 / B1 …，带数字后缀的还会给出「A（全部）」合并选项）。</li>
 * </ul>
 * 数据全在客户端缓存 + 方块实体里，改哪样才发哪条 C2S 包，并且先本地改、当帧生效。
 */
public class TrdmExitSelectScreen extends TrdmScreenBase {

    private static final float TOGGLE_1_Y = 126F;
    private static final float TOGGLE_2_Y = 180F;
    private static final float SWITCH_X = CONTENT_RIGHT - TOGGLE_W;

    private static final float EXIT_SECTION_Y = 230F;
    private static final float LIST_TOP = 256F;
    private static final float LIST_BOTTOM = 496F;
    private static final float ROW_H = 24F;
    private static final float DEST_X = CONTENT_X + 170F;

    private static final float BUTTON_W = 84F;
    private static final float BUTTON_H = 24F;
    private static final float BUTTON_Y = TITLE_Y;
    private static final float BUTTON_CLEAR_X = CONTENT_RIGHT - BUTTON_W * 2F - 8F;
    private static final float BUTTON_DONE_X = CONTENT_RIGHT - BUTTON_W;

    private final BlockPos targetPos;
    private final List<Row> rows = new ArrayList<>();
    private String selectedExit;
    private boolean zhanStyle;
    private boolean elevator;
    private Station station;
    private float scroll;

    public TrdmExitSelectScreen(BlockPos targetPos, String selectedExit, boolean zhanStyle, boolean elevator) {
        super(null, Component.translatable("block.trdm.big_station_sign_1"));
        this.targetPos = targetPos;
        this.selectedExit = selectedExit == null ? "" : selectedExit;
        this.zhanStyle = zhanStyle;
        this.elevator = elevator;
    }

    @Override
    protected void onInit() {
        rows.clear();
        station = TrdmControlClient.resolveStation(targetPos);
        if (station != null) {
            // 出口常常是 A1 / A2 / B1 这种带数字后缀的：除了真实出口，还要给出
            // 「A」「B」这种整个出入口的选项（牌子上只有一个字母的位置）
            for (final String option : ExitNames.options(station)) {
                final List<String> destinations = station.exits.get(option);
                if (destinations != null) {
                    rows.add(new Row(option, String.join(" · ", destinations), false));
                } else {
                    rows.add(new Row(option, String.join(" / ", ExitNames.membersOf(station, option)), true));
                }
            }
        }
        scroll = 0F;
    }

    @Override
    protected void renderPage(GuiGraphics guiGraphics, float mx, float my) {
        title(guiGraphics, title);
        hint(guiGraphics, station == null
                ? Component.translatable("gui.trdm.psd_control.no_station")
                : Component.translatable("gui.trdm.big_sign.exit_station", station.name), CONTENT_X, SUB_Y);

        buttonText(guiGraphics, BUTTON_CLEAR_X, BUTTON_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.trdm.psd_control.clear"), false,
                hit(mx, my, BUTTON_CLEAR_X, BUTTON_Y, BUTTON_W, BUTTON_H), FONT_ROW);
        buttonText(guiGraphics, BUTTON_DONE_X, BUTTON_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.trdm.psd_control.done"), false,
                hit(mx, my, BUTTON_DONE_X, BUTTON_Y, BUTTON_W, BUTTON_H), FONT_ROW);

        section(guiGraphics, "gui.trdm.big_sign.options_section", SECTION_Y);
        toggleRow(guiGraphics, TOGGLE_1_Y, Component.translatable("gui.trdm.big_sign.zhan_label"),
                Component.translatable("gui.trdm.big_sign.zhan_hint"),
                Component.translatable(zhanStyle ? "gui.trdm.big_sign.style_zhan"
                        : "gui.trdm.big_sign.style_station"), zhanStyle);
        toggleRow(guiGraphics, TOGGLE_2_Y, Component.translatable("gui.trdm.big_sign.elevator_label"),
                Component.translatable("gui.trdm.big_sign.elevator_hint"),
                Component.translatable(elevator ? "gui.trdm.psd_control.on" : "gui.trdm.psd_control.off"),
                elevator);

        section(guiGraphics, "gui.trdm.big_sign.exit_title", EXIT_SECTION_Y);
        if (rows.isEmpty()) {
            hint(guiGraphics, "gui.trdm.big_sign.exit_empty", CONTENT_X, LIST_TOP + 6F);
        } else {
            clampScroll();
            scissorOn(guiGraphics, CONTENT_X, LIST_TOP, CONTENT_W, LIST_BOTTOM - LIST_TOP);
            for (int index = 0; index < rows.size(); index++) {
                final float top = LIST_TOP - scroll + index * ROW_H;
                if (top + ROW_H < LIST_TOP || top > LIST_BOTTOM) {
                    continue;
                }
                final Row entry = rows.get(index);
                final boolean selected = entry.name().equals(selectedExit);
                final boolean hovered = mx >= CONTENT_X && mx < CONTENT_RIGHT && my >= top && my < top + ROW_H;
                row(guiGraphics, CONTENT_X, top, CONTENT_W, ROW_H - 2F, selected, hovered);
                text(guiGraphics, Component.translatable(
                                entry.baseOnly() ? "gui.trdm.big_sign.exit_base_row" : "gui.trdm.big_sign.exit_row",
                                entry.name()),
                        CONTENT_X + 9F, top + 6F, FONT_ROW, selected ? COLOR_TEXT : COLOR_SUB);
                if (!entry.destinations().isEmpty()) {
                    hint(guiGraphics, Component.literal(entry.destinations()), DEST_X, top + 8F);
                }
            }
            scissorOff(guiGraphics);
            scrollbar(guiGraphics, CONTENT_RIGHT - 4F, LIST_TOP, LIST_BOTTOM - LIST_TOP, rows.size() * ROW_H,
                    LIST_BOTTOM - LIST_TOP, scroll);
        }
    }

    private void toggleRow(GuiGraphics guiGraphics, float top, Component label, Component sub, Component state,
                           boolean on) {
        text(guiGraphics, label, CONTENT_X, top, FONT_BODY, COLOR_TEXT);
        hint(guiGraphics, sub, CONTENT_X, top + 20F);
        textRight(guiGraphics, state, SWITCH_X - 10F, top + 3F, FONT_ROW, COLOR_TEXT);
        toggle(guiGraphics, SWITCH_X, top - 2F, on);
    }

    private void clampScroll() {
        scroll = Math.max(0F, Math.min(scroll, rows.size() * ROW_H - (LIST_BOTTOM - LIST_TOP)));
    }

    @Override
    protected boolean clickPage(float mx, float my) {
        if (hit(mx, my, SWITCH_X, TOGGLE_1_Y - 2F, TOGGLE_W, TOGGLE_H)) {
            zhanStyle = !zhanStyle;
            TrdmControlClient.sendBigSignOptions(targetPos, zhanStyle, elevator);
            return true;
        }
        if (hit(mx, my, SWITCH_X, TOGGLE_2_Y - 2F, TOGGLE_W, TOGGLE_H)) {
            elevator = !elevator;
            TrdmControlClient.sendBigSignOptions(targetPos, zhanStyle, elevator);
            return true;
        }
        if (hit(mx, my, BUTTON_CLEAR_X, BUTTON_Y, BUTTON_W, BUTTON_H)) {
            selectedExit = "";
            TrdmControlClient.sendExitSelection(targetPos, "");
            return true;
        }
        if (hit(mx, my, BUTTON_DONE_X, BUTTON_Y, BUTTON_W, BUTTON_H)) {
            onClose();
            return true;
        }
        if (my >= LIST_TOP && my < LIST_BOTTOM) {
            final int index = (int) ((my - LIST_TOP + scroll) / ROW_H);
            if (index >= 0 && index < rows.size()) {
                selectedExit = rows.get(index).name();
                TrdmControlClient.sendExitSelection(targetPos, selectedExit);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        final float my = designY(mouseY);
        if (my >= LIST_TOP && my < LIST_BOTTOM) {
            scroll -= (float) delta * ROW_H;
            clampScroll();
            return true;
        }
        return false;
    }

    private record Row(String name, String destinations, boolean baseOnly) {
    }
}
