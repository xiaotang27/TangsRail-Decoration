package top.xiaotang27.trdm.client;

import mtr.client.ClientCache;
import mtr.client.ClientData;
import mtr.data.Platform;
import mtr.data.Station;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.control.StationScope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 站台选择界面：屏蔽门集控装置和站名牌共用，只有底部多一行隔离开关的区别。
 * <p>
 */
public class PsdControlScreen extends TrdmScreenBase {

    private static final float SEARCH_Y = 128F;
    private static final float SEARCH_H = 22F;
    private static final float LIST_TOP = 160F;
    /** 没有隔离开关时列表的底。 */
    private static final float LIST_BOTTOM_PLAIN = 496F;
    /** 有隔离开关时列表让出底部一行。 */
    private static final float LIST_BOTTOM_ISOLATE = 460F;

    private static final float ISOLATE_Y = 466F;
    private static final float ISOLATE_SWITCH_W = TOGGLE_W;
    private static final float ISOLATE_SWITCH_H = TOGGLE_H;
    private static final float SWITCH_X = CONTENT_RIGHT - TOGGLE_W;

    private static final float ROW_HEADER_H = 22F;
    private static final float ROUTE_H = 28F;
    private static final float ROW_PAD = 4F;
    private static final float BADGE_W = 18F;
    private static final float BADGE_H = 22F;

    private static final float BUTTON_W = 84F;
    private static final float BUTTON_H = 24F;
    private static final float BUTTON_Y = TITLE_Y;
    private static final float BUTTON_CLEAR_X = CONTENT_RIGHT - BUTTON_W * 2F - 8F;
    private static final float BUTTON_CLOSE_X = CONTENT_RIGHT - BUTTON_W;

    private static final int COLOR_TERMINUS = 0xFFA8A8A8;
    private static final int COLOR_BADGE_TEXT_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_BADGE_TEXT_DARK = 0xFF101010;

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private static final Object FIELD_SEARCH = "psd:search";

    private final BlockPos controlPos;
    private final TrdmControlClient.SelectMode mode;
    private final List<Row> rows = new ArrayList<>();

    private long selectedPlatformId;
    private boolean isolated;
    private Station deviceStation;

    private List<Row> visibleRows = List.of();
    private String query = "";
    private String lastQuery = "";
    private float scroll;

    /**
     * @param targetPos          打开界面的那个方块（集控装置 / 站名牌）的位置，选完写回它
     * @param selectedPlatformId 它当前选的站台（0 = 没选，用来高亮）
     */
    public PsdControlScreen(BlockPos targetPos, long selectedPlatformId, boolean isolated,
                            TrdmControlClient.SelectMode mode) {
        super(null, Component.translatable(mode == TrdmControlClient.SelectMode.STATION_SIGN
                ? "block.trdm.station_sign" : "block.trdm.psd_control"));
        this.controlPos = targetPos;
        this.selectedPlatformId = selectedPlatformId;
        this.isolated = isolated;
        this.mode = mode;
    }

    private boolean isControlDevice() {
        return mode == TrdmControlClient.SelectMode.CONTROL_DEVICE;
    }

    @Override
    protected void onInit() {
        rows.clear();
        deviceStation = TrdmControlClient.resolveStation(controlPos);
        final List<Platform> platforms = new ArrayList<>(ClientData.PLATFORMS);
        platforms.sort(Comparator.comparing((Platform platform) -> platform.name, String.CASE_INSENSITIVE_ORDER));
        for (final Platform platform : platforms) {
            final Station platformStation = StationScope.stationOf(ClientData.DATA_CACHE.platformIdToStation,
                    platform.id);
            if (!StationScope.sameStation(deviceStation, platformStation)) {
                continue;
            }
            rows.add(new Row(platform.id, String.valueOf(platform.name), buildRouteLines(platform.id)));
        }
        scroll = 0F;
        lastQuery = null;
        syncRows(query);
    }

    /**
     * 一个站台的线路列表（MTR 的 PlatformRouteDetails：线路色、线路名、站序）。
     * 终点站取这条线路的末站；本站就是末站时显示「终点站」。
     */
    private static List<RouteLine> buildRouteLines(long platformId) {
        final List<ClientCache.PlatformRouteDetails> details = new ArrayList<>(
                ClientData.DATA_CACHE.requestPlatformIdToRoutes(platformId));
        details.sort(Comparator.comparing((ClientCache.PlatformRouteDetails route) -> route.routeName,
                String.CASE_INSENSITIVE_ORDER));

        final List<RouteLine> lines = new ArrayList<>();
        for (final ClientCache.PlatformRouteDetails route : details) {
            final List<ClientCache.PlatformRouteDetails.StationDetails> stations = route.stationDetails;
            final String terminus;
            if (stations.isEmpty()) {
                terminus = "";
            } else if (route.currentStationIndex >= stations.size() - 1) {
                terminus = Component.translatable("gui.trdm.psd_control.terminal").getString();
            } else {
                terminus = Component.translatable("gui.trdm.psd_control.towards",
                        stations.get(stations.size() - 1).stationName).getString();
            }
            lines.add(new RouteLine(routeNumber(route.routeName), route.routeName, terminus, route.routeColor));
        }
        return lines;
    }

    /** 线路色块里那个数字：线路名里第一段连续数字（「1号线|Line 1」→「1」），没有就取头两个字符。 */
    private static String routeNumber(String routeName) {
        final String chinesePart = routeName == null ? "" : routeName.split("\\|")[0].trim();
        final Matcher matcher = DIGITS.matcher(chinesePart);
        if (matcher.find()) {
            return matcher.group();
        }
        return chinesePart.isEmpty() ? "#" : chinesePart.substring(0, Math.min(2, chinesePart.length()));
    }

    /** 搜索框里的实时文字优先，没在输入就用已提交的关键字。 */
    private String effectiveQuery() {
        return fieldActive(FIELD_SEARCH) ? editingValue() : query;
    }

    private void syncRows(String newQuery) {
        final String safe = newQuery == null ? "" : newQuery;
        if (safe.equals(lastQuery)) {
            return;
        }
        lastQuery = safe;
        final String needle = safe.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            visibleRows = List.copyOf(rows);
        } else {
            final List<Row> filtered = new ArrayList<>();
            for (final Row row : rows) {
                if (row.matches(needle)) {
                    filtered.add(row);
                }
            }
            visibleRows = filtered;
        }
        clampScroll();
    }

    private static float rowHeight(Row row) {
        return ROW_HEADER_H + Math.max(1, row.routes().size()) * ROUTE_H + ROW_PAD;
    }

    private float totalHeight() {
        float total = 0F;
        for (final Row row : visibleRows) {
            total += rowHeight(row);
        }
        return total;
    }

    private float listBottom() {
        return isControlDevice() ? LIST_BOTTOM_ISOLATE : LIST_BOTTOM_PLAIN;
    }

    private void clampScroll() {
        scroll = Math.max(0F, Math.min(scroll, totalHeight() - (listBottom() - LIST_TOP)));
    }

    // ===== 绘制 =====

    @Override
    protected void renderPage(GuiGraphics guiGraphics, float mx, float my) {
        title(guiGraphics, title);
        hint(guiGraphics, deviceStation == null
                ? Component.translatable("gui.trdm.psd_control.no_station")
                : Component.translatable("gui.trdm.psd_control.station", deviceStation.name), CONTENT_X, SUB_Y);

        buttonText(guiGraphics, BUTTON_CLEAR_X, BUTTON_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.trdm.psd_control.clear"), false,
                hit(mx, my, BUTTON_CLEAR_X, BUTTON_Y, BUTTON_W, BUTTON_H), FONT_ROW);
        buttonText(guiGraphics, BUTTON_CLOSE_X, BUTTON_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.trdm.psd_control.done"), false,
                hit(mx, my, BUTTON_CLOSE_X, BUTTON_Y, BUTTON_W, BUTTON_H), FONT_ROW);

        section(guiGraphics, "gui.trdm.psd_control.pick", SECTION_Y);
        field(guiGraphics, FIELD_SEARCH, CONTENT_X, SEARCH_Y, CONTENT_W, SEARCH_H, FONT_ROW, query);
        if (query.isEmpty() && !fieldActive(FIELD_SEARCH)) {
            text(guiGraphics, Component.translatable("gui.trdm.psd_control.search"), CONTENT_X + 8F,
                    SEARCH_Y + (SEARCH_H - FONT_ROW) / 2F - 1F, FONT_ROW, 0xFF808080);
        }

        syncRows(effectiveQuery());
        final float bottom = listBottom();
        if (visibleRows.isEmpty()) {
            hint(guiGraphics, deviceStation == null ? "gui.trdm.psd_control.no_station" : "gui.trdm.psd_control.empty",
                    CONTENT_X, LIST_TOP + 6F);
        } else {
            scissorOn(guiGraphics, CONTENT_X, LIST_TOP, CONTENT_W, bottom - LIST_TOP);
            float top = LIST_TOP - scroll;
            for (final Row row : visibleRows) {
                final float height = rowHeight(row);
                if (top + height >= LIST_TOP && top <= bottom) {
                    renderRow(guiGraphics, row, top, height, mx, my);
                }
                top += height;
            }
            scissorOff(guiGraphics);
            scrollbar(guiGraphics, CONTENT_RIGHT - 4F, LIST_TOP, bottom - LIST_TOP, totalHeight(),
                    bottom - LIST_TOP, scroll);
        }

        if (isControlDevice()) {
            text(guiGraphics, Component.translatable("gui.trdm.psd_control.isolate_label"), CONTENT_X,
                    ISOLATE_Y + 2F, FONT_BODY, COLOR_TEXT);
            hint(guiGraphics, "gui.trdm.psd_control.isolate_hint", CONTENT_X, ISOLATE_Y + 22F);
            final Component state = Component.translatable(isolated ? "gui.trdm.psd_control.on"
                    : "gui.trdm.psd_control.off");
            textRight(guiGraphics, state, SWITCH_X - 10F, ISOLATE_Y + 4F, FONT_ROW, COLOR_TEXT);
            toggle(guiGraphics, SWITCH_X, ISOLATE_Y, isolated);
        }
    }

    private void renderRow(GuiGraphics guiGraphics, Row row, float top, float height, float mx, float my) {
        final boolean hovered = mx >= CONTENT_X && mx < CONTENT_RIGHT && my >= top && my < top + height;
        final boolean selected = row.platformId() == selectedPlatformId;
        row(guiGraphics, CONTENT_X, top, CONTENT_W, height - 2F, selected, hovered);
        rect(guiGraphics, CONTENT_X, top, CONTENT_W, ROW_HEADER_H, 0x24000000);

        text(guiGraphics, Component.translatable("gui.trdm.psd_control.platform", row.platformNumber()),
                CONTENT_X + 9F, top + 4F, FONT_ROW, selected ? COLOR_TEXT : COLOR_SUB);

        float lineTop = top + ROW_HEADER_H + 1F;
        if (row.routes().isEmpty()) {
            hint(guiGraphics, "gui.trdm.psd_control.no_route", CONTENT_X + 9F + BADGE_W + 8F, lineTop + 5F);
            return;
        }
        for (final RouteLine line : row.routes()) {
            final float badgeLeft = CONTENT_X + 9F;
            rect(guiGraphics, badgeLeft, lineTop, BADGE_W, BADGE_H, 0xFF000000 | line.color());
            textCentred(guiGraphics, Component.literal(line.badgeText()), badgeLeft, BADGE_W, lineTop + 6F,
                    FONT_SUB, contrastColor(line.color()));

            final float textLeft = badgeLeft + BADGE_W + 8F;
            text(guiGraphics, Component.literal(line.routeName()), textLeft, lineTop + 1F, FONT_ROW, COLOR_TEXT);
            if (!line.terminus().isEmpty()) {
                text(guiGraphics, Component.literal(line.terminus()), textLeft, lineTop + 16F, FONT_SUB,
                        COLOR_TERMINUS);
            }
            lineTop += ROUTE_H;
        }
    }

    /** 色块上的字：线路色偏亮就用黑字，偏暗就用白字。 */
    private static int contrastColor(int rgb) {
        final int red = (rgb >> 16) & 0xFF;
        final int green = (rgb >> 8) & 0xFF;
        final int blue = rgb & 0xFF;
        return 0.299 * red + 0.587 * green + 0.114 * blue > 155 ? COLOR_BADGE_TEXT_DARK : COLOR_BADGE_TEXT_LIGHT;
    }

    // ===== 点击 =====

    @Override
    protected boolean clickPage(float mx, float my) {
        if (hit(mx, my, CONTENT_X, SEARCH_Y, CONTENT_W, SEARCH_H)) {
            beginField(FIELD_SEARCH, CONTENT_X, SEARCH_Y, CONTENT_W, SEARCH_H, query, 64, FONT_ROW, value -> {
                query = value;
                syncRows(value);
            });
            return true;
        }
        if (hit(mx, my, BUTTON_CLEAR_X, BUTTON_Y, BUTTON_W, BUTTON_H)) {
            selectedPlatformId = 0;
            TrdmControlClient.sendSelection(controlPos, 0);
            return true;
        }
        if (hit(mx, my, BUTTON_CLOSE_X, BUTTON_Y, BUTTON_W, BUTTON_H)) {
            onClose();
            return true;
        }
        if (isControlDevice() && hit(mx, my, SWITCH_X, ISOLATE_Y, ISOLATE_SWITCH_W, ISOLATE_SWITCH_H)) {
            if (selectedPlatformId == 0) {
                return true;
            }
            isolated = !isolated;
            TrdmControlClient.sendToggleIsolate(controlPos);
            return true;
        }

        if (my < LIST_TOP || my >= listBottom()) {
            return false;
        }
        syncRows(effectiveQuery());
        float offset = my - LIST_TOP + scroll;
        for (final Row row : visibleRows) {
            final float height = rowHeight(row);
            if (offset < height) {
                selectedPlatformId = row.platformId();
                TrdmControlClient.sendSelection(controlPos, selectedPlatformId);
                return true;
            }
            offset -= height;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        final float my = designY(mouseY);
        if (my >= LIST_TOP && my < listBottom()) {
            scroll -= (float) delta * ROUTE_H;
            clampScroll();
            return true;
        }
        return false;
    }

    /** 列表一行：一个站台 + 它停靠的线路。 */
    private record Row(long platformId, String platformNumber, List<RouteLine> routes) {

        private boolean matches(String needle) {
            if (platformNumber.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
            for (final RouteLine line : routes) {
                if (line.routeName().toLowerCase(Locale.ROOT).contains(needle)
                        || line.terminus().toLowerCase(Locale.ROOT).contains(needle)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 一条线路：色块里的线路号、线路全名、终点站那一行、线路色。 */
    private record RouteLine(String badgeText, String routeName, String terminus, int color) {
    }
}
