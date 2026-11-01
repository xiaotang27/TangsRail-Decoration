package top.xiaotang27.trdm.client;

import mtr.data.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.block.BlockTrdmPis;
import top.xiaotang27.trdm.render.TrdmPisAds;
import top.xiaotang27.trdm.render.TrdmPisGlobal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PIS 屏幕设置界面（刷子右键屏幕打开）。分三段：
 * <ol>
 */
public class PisSettingsScreen extends TrdmScreenBase {

    // ===== 跑马信息区 =====
    private static final float LABEL_W = 56F;
    private static final float FIELD_X = CONTENT_X + LABEL_W;
    private static final float FIELD_W = CONTENT_RIGHT - FIELD_X;
    private static final float FIELD_H = 22F;
    private static final float FIELD_LOCAL_Y = 128F;
    private static final float HINT_LOCAL_Y = 156F;
    private static final float FIELD_GLOBAL_Y = 172F;
    private static final float HINT_GLOBAL_Y = 200F;

    // ===== 广告列表 =====
    private static final float ADS_SECTION_Y = 228F;
    private static final float LIST_TOP = 254F;
    private static final float LIST_BOTTOM = 496F;
    private static final float AD_ROW_H = 26F;
    private static final float TIME_W = 96F;
    private static final float TIME_H = 20F;
    private static final float BTN_W = 30F;
    private static final float BTN_H = 20F;
    private static final float BTN_Y_OFF = 3F;
    private static final float BTN_DOWN_X = CONTENT_RIGHT - 12F - BTN_W;
    private static final float BTN_UP_X = BTN_DOWN_X - BTN_W - 4F;
    private static final float BTN_PLUS_X = BTN_UP_X - BTN_W - 4F;
    private static final float BTN_MINUS_X = BTN_PLUS_X - BTN_W - 4F;
    private static final float TIME_X = BTN_MINUS_X - 8F - TIME_W;

    // ===== 按钮 =====
    private static final float BUTTON_H = 24F;
    private static final float DONE_W = 84F;
    private static final float DONE_X = CONTENT_RIGHT - DONE_W;
    private static final float RESCAN_W = 96F;
    private static final float RESCAN_X = DONE_X - 8F - RESCAN_W;

    private static final Object FIELD_LOCAL = "pis:ticker";
    private static final Object FIELD_GLOBAL = "pis:ticker_global";

    private final BlockPos targetPos;

    private String ticker = BlockTrdmPis.DEFAULT_TICKER;
    private boolean adsEnabled = true;
    private final List<String> order = new ArrayList<>();
    private final Map<String, Boolean> enabled = new HashMap<>();
    private final Map<String, Integer> times = new HashMap<>();

    private Station station;
    private float scroll;

    public PisSettingsScreen(BlockPos targetPos) {
        super(null, Component.translatable("block.trdm.pis"));
        this.targetPos = targetPos;
    }

    @Override
    protected void onInit() {
        station = TrdmControlClient.resolveStation(targetPos);
        readFromBlock();
    }

    /** 从方块实体读当前设置；广告顺序没存过就按 json 的顺序。 */
    private void readFromBlock() {
        order.clear();
        enabled.clear();
        times.clear();
        if (Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getBlockEntity(targetPos)
                instanceof BlockTrdmPis.TileEntityTrdmPis pis) {
            ticker = pis.getTicker();
            adsEnabled = pis.isAdsEnabled();
            order.addAll(pis.getAdOrder());
            enabled.putAll(pis.getAdEnabled());
            times.putAll(pis.getAdTimes());
        }
        syncCatalogue();
    }

    /** 把 json 里新出现的广告补进列表尾巴，删掉已经不存在的。 */
    private void syncCatalogue() {
        final List<TrdmPisAds.Ad> catalogue = TrdmPisAds.list();
        final List<String> known = new ArrayList<>();
        for (final TrdmPisAds.Ad ad : catalogue) {
            known.add(ad.id());
        }
        order.removeIf(id -> !known.contains(id));
        for (final String id : known) {
            if (!order.contains(id)) {
                order.add(id);
            }
        }
        for (final String id : known) {
            enabled.putIfAbsent(id, Boolean.TRUE);
        }
        for (final TrdmPisAds.Ad ad : catalogue) {
            if (!times.containsKey(ad.id())) {
                times.put(ad.id(), ad.timeMs());
            }
        }
        clampScroll();
    }

    // ===== 绘制 =====

    @Override
    protected void renderPage(GuiGraphics guiGraphics, float mx, float my) {
        title(guiGraphics, Component.translatable("block.trdm.pis"));
        hint(guiGraphics, station == null
                ? Component.translatable("gui.trdm.psd_control.no_station")
                : Component.translatable("gui.trdm.pis.station", station.name), CONTENT_X, SUB_Y);
        hint(guiGraphics, Component.translatable("gui.trdm.pis.pack_count", TrdmPisAds.packCount()),
                CONTENT_X, SUB_Y + SUB_LINE);

        buttonText(guiGraphics, RESCAN_X, TITLE_Y, RESCAN_W, BUTTON_H,
                Component.translatable("gui.trdm.pis.rescan"), false,
                hit(mx, my, RESCAN_X, TITLE_Y, RESCAN_W, BUTTON_H), FONT_ROW);
        buttonText(guiGraphics, DONE_X, TITLE_Y, DONE_W, BUTTON_H,
                Component.translatable("gui.trdm.pis.done"), false,
                hit(mx, my, DONE_X, TITLE_Y, DONE_W, BUTTON_H), FONT_ROW);

        // 屏幕跑马信息显示
        section(guiGraphics, "gui.trdm.pis.ticker_section", SECTION_Y);
        text(guiGraphics, Component.translatable("gui.trdm.pis.ticker_local"), CONTENT_X, FIELD_LOCAL_Y + 5F,
                FONT_ROW, COLOR_SUB);
        field(guiGraphics, FIELD_LOCAL, FIELD_X, FIELD_LOCAL_Y, FIELD_W, FIELD_H, FONT_ROW, ticker);
        hint(guiGraphics, "gui.trdm.pis.ticker_local_hint", CONTENT_X, HINT_LOCAL_Y);

        text(guiGraphics, Component.translatable("gui.trdm.pis.ticker_global"), CONTENT_X, FIELD_GLOBAL_Y + 5F,
                FONT_ROW, COLOR_SUB);
        field(guiGraphics, FIELD_GLOBAL, FIELD_X, FIELD_GLOBAL_Y, FIELD_W, FIELD_H, FONT_ROW,
                TrdmPisGlobal.ticker());
        hint(guiGraphics, "gui.trdm.pis.global_ticker_hint", CONTENT_X, HINT_GLOBAL_Y);

        // 广告显示设置
        section(guiGraphics, "gui.trdm.pis.ads_section", ADS_SECTION_Y);
        if (order.isEmpty()) {
            hint(guiGraphics, "gui.trdm.pis.no_ads", CONTENT_X, LIST_TOP + 6F);
        } else {
            clampScroll();
            scissorOn(guiGraphics, CONTENT_X, LIST_TOP, CONTENT_W, LIST_BOTTOM - LIST_TOP);
            for (int index = 0; index < order.size(); index++) {
                renderAdRow(guiGraphics, index, mx, my);
            }
            scissorOff(guiGraphics);
            scrollbar(guiGraphics, CONTENT_RIGHT - 4F, LIST_TOP, LIST_BOTTOM - LIST_TOP,
                    order.size() * AD_ROW_H, LIST_BOTTOM - LIST_TOP, scroll);
        }
    }

    private void renderAdRow(GuiGraphics guiGraphics, int index, float mx, float my) {
        final float top = rowTop(index);
        if (top + AD_ROW_H < LIST_TOP || top > LIST_BOTTOM) {
            return;
        }
        final String id = order.get(index);
        final TrdmPisAds.Ad ad = TrdmPisAds.byId(id);
        final boolean video = ad != null && ad.video();
        final boolean on = !Boolean.FALSE.equals(enabled.get(id));
        final boolean hovered = mx >= CONTENT_X && mx < CONTENT_RIGHT && my >= top && my < top + AD_ROW_H;

        row(guiGraphics, CONTENT_X, top, CONTENT_W, AD_ROW_H - 1F, false, hovered);
        checkbox(guiGraphics, CONTENT_X + 7F, top + 6F, on);

        final Component name = Component.literal(id);
        final float textLeft = CONTENT_X + 28F;
        // 名字 + 视频提示都夹在时长框左边，太长就裁掉，不许压到时长框
        scissorOn(guiGraphics, textLeft, top + 2F, TIME_X - 8F - textLeft, AD_ROW_H - 4F);
        text(guiGraphics, name, textLeft, top + 7F, FONT_ROW, on ? COLOR_TEXT : COLOR_SUB);
        if (video) {
            text(guiGraphics, Component.translatable("gui.trdm.pis.video_warn"),
                    textLeft + textWidth(name, FONT_ROW) + 8F, top + 8F, FONT_SUB, COLOR_WARN);
        }
        scissorOff(guiGraphics);

        field(guiGraphics, timeFieldId(id), TIME_X, top + BTN_Y_OFF, TIME_W, TIME_H, FONT_ROW, timeLabel(id));
        glyphButton(guiGraphics, BTN_MINUS_X, top + BTN_Y_OFF, "−");
        glyphButton(guiGraphics, BTN_PLUS_X, top + BTN_Y_OFF, "＋");
        glyphButton(guiGraphics, BTN_UP_X, top + BTN_Y_OFF, "▲");
        glyphButton(guiGraphics, BTN_DOWN_X, top + BTN_Y_OFF, "▼");
    }

    private void glyphButton(GuiGraphics guiGraphics, float x, float y, String glyph) {
        button(guiGraphics, x, y, BTN_W, BTN_H, false, false);
        textCentred(guiGraphics, Component.literal(glyph), x, BTN_W, y + (BTN_H - FONT_ROW) / 2F - 1F, FONT_ROW,
                COLOR_TEXT);
    }

    private float rowTop(int index) {
        return LIST_TOP - scroll + index * AD_ROW_H;
    }

    // ===== 点击 =====

    @Override
    protected boolean clickPage(float mx, float my) {
        if (hit(mx, my, RESCAN_X, TITLE_Y, RESCAN_W, BUTTON_H)) {
            TrdmPisAds.invalidate();
            syncCatalogue();
            return true;
        }
        if (hit(mx, my, DONE_X, TITLE_Y, DONE_W, BUTTON_H)) {
            onClose();
            return true;
        }
        if (hit(mx, my, FIELD_X, FIELD_LOCAL_Y, FIELD_W, FIELD_H)) {
            beginField(FIELD_LOCAL, FIELD_X, FIELD_LOCAL_Y, FIELD_W, FIELD_H, ticker, 512, FONT_ROW, value -> {
                ticker = value;
                commit();
            });
            return true;
        }
        if (hit(mx, my, FIELD_X, FIELD_GLOBAL_Y, FIELD_W, FIELD_H)) {
            beginField(FIELD_GLOBAL, FIELD_X, FIELD_GLOBAL_Y, FIELD_W, FIELD_H, TrdmPisGlobal.ticker(), 512,
                    FONT_ROW, TrdmPisGlobal::setTicker);
            return true;
        }

        final int index = rowAt(my);
        if (index < 0) {
            return false;
        }
        final String id = order.get(index);
        final float top = rowTop(index);
        final float buttonY = top + BTN_Y_OFF;
        if (hit(mx, my, TIME_X, buttonY, TIME_W, TIME_H)) {
            beginField(timeFieldId(id), TIME_X, buttonY, TIME_W, TIME_H, secondsLabel(id), 8, FONT_ROW,
                    value -> applyTimeText(id, value));
            return true;
        }
        if (hit(mx, my, BTN_MINUS_X, buttonY, BTN_W, BTN_H)) {
            adjustTime(id, -1);
        } else if (hit(mx, my, BTN_PLUS_X, buttonY, BTN_W, BTN_H)) {
            adjustTime(id, 1);
        } else if (hit(mx, my, BTN_UP_X, buttonY, BTN_W, BTN_H)) {
            move(id, -1);
        } else if (hit(mx, my, BTN_DOWN_X, buttonY, BTN_W, BTN_H)) {
            move(id, 1);
        } else if (my >= top && my < top + AD_ROW_H) {
            enabled.put(id, Boolean.FALSE.equals(enabled.get(id)));
        } else {
            return false;
        }
        commit();
        return true;
    }

    private int rowAt(float my) {
        if (my < LIST_TOP || my >= LIST_BOTTOM) {
            return -1;
        }
        final int index = (int) ((my - LIST_TOP + scroll) / AD_ROW_H);
        return index >= 0 && index < order.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        final float my = designY(mouseY);
        if (my >= LIST_TOP && my < LIST_BOTTOM) {
            scroll -= (float) delta * AD_ROW_H;
            clampScroll();
            return true;
        }
        return false;
    }

    private void clampScroll() {
        final float visible = LIST_BOTTOM - LIST_TOP;
        scroll = Math.max(0F, Math.min(scroll, order.size() * AD_ROW_H - visible));
    }

    // ===== 时长 / 顺序 =====

    private static Object timeFieldId(String id) {
        return "pis:time:" + id;
    }

    /** 这条广告的「1 倍」时长：json 里的 time（视频没写就是视频长度）。 */
    private int baseTimeMs(String id) {
        final TrdmPisAds.Ad ad = TrdmPisAds.byId(id);
        return ad == null ? 5000 : Math.max(100, ad.timeMs());
    }

    /** 这条广告当前生效的时长（毫秒）。 */
    private int timeMs(String id) {
        final int stored = times.getOrDefault(id, 0);
        return stored > 0 ? stored : baseTimeMs(id);
    }

    private String timeLabel(String id) {
        return String.format(Locale.ROOT, "%.1fs", timeMs(id) / 1000F);
    }

    /** 输入框里显示不带单位，方便键入选。 */
    private String secondsLabel(String id) {
        return String.format(Locale.ROOT, "%.1f", timeMs(id) / 1000F);
    }

    /** 倍增 / 倍减，最少 1 倍（1 倍 = 这条广告的基准时长）。 */
    private void adjustTime(String id, int delta) {
        final int base = baseTimeMs(id);
        final int current = Math.max(base, timeMs(id));
        final int next = delta > 0 ? current * 2 : Math.max(base, current / 2);
        times.put(id, Math.min(next, 600_000));
    }

    /** 键入的秒数换算成毫秒存下来（低于 1 倍就按 1 倍算）。 */
    private void applyTimeText(String id, String text) {
        try {
            final double seconds = Double.parseDouble(text.trim().replace("s", "").replace("S", ""));
            final int ms = (int) Math.round(seconds * 1000.0D);
            if (ms > 0) {
                times.put(id, Math.max(baseTimeMs(id), Math.min(ms, 600_000)));
            }
        } catch (Exception ignored) {
            // 输错就保持原值
        }
        commit();
    }

    private void move(String id, int delta) {
        final int index = order.indexOf(id);
        final int target = index + delta;
        if (index < 0 || target < 0 || target >= order.size()) {
            return;
        }
        order.remove(index);
        order.add(target, id);
    }

    private void commit() {
        TrdmControlClient.sendPisSettings(targetPos, ticker, adsEnabled, order, enabled, times);
    }
}
