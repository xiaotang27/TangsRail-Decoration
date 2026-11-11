package top.xiaotang27.trdm.client;

import mtr.client.ClientData;
import mtr.data.Route;
import mtr.data.Station;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.fare.FareMoney;
import top.xiaotang27.trdm.ticket.PassRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 售票机界面：单程票 / 通用票 / 交通卡三个页签，浅色主题；单程票价格来自计费引擎。 */
public class TicketVendingScreen extends TrdmScreenBase implements TicketResultReceiver {

    private static final int TAB_SINGLE = 0;
    private static final int TAB_PASS = 1;
    private static final int TAB_CARD = 2;

    private static final float TAB_W = 132F;
    private static final float TAB_H = 26F;
    private static final float TAB_Y = 20F;

    private static final float LIST_X = 104F;
    private static final float LIST_W = 430F;
    private static final float LIST_TOP = 116F;
    private static final float LIST_BOTTOM = 478F;
    private static final float GROUP_H = 22F;
    private static final float ROW_H = 24F;

    private static final float RIGHT_X = 552F;
    private static final float RIGHT_W = 300F;

    private static final Object FIELD_SEARCH = new Object();

    private final String stationName;
    private final long stationId;
    private final BlockPos pos;
    private final List<LineGroup> groups = new ArrayList<>();

    private int tab = TAB_SINGLE;
    private String query = "";
    private float listScroll;
    private int selectedLine = -1;
    private long selectedStationId = -1L;
    private String selectedStationName = "";

    private String priceInput = "";
    private boolean transactionOpen;
    private String transactionTitle = "";
    private long transactionStationId = -1L;
    private boolean transactionByPrice;
    private String transactionPassId = "";
    private long unitPriceCents = 200L;
    private int quantity = 1;
    private String dialog;
    private Component dialogMessage = Component.empty();
    private boolean purchasePending;

    public TicketVendingScreen(String stationName, long stationId, BlockPos pos) {
        this(null, stationName, stationId, pos);
    }

    public TicketVendingScreen(Screen parent, String stationName, long stationId, BlockPos pos) {
        super(parent, Component.translatable("block.trdm.ticket_vending_machine"));
        this.stationName = stationName == null ? "" : stationName;
        this.stationId = stationId;
        this.pos = pos;
        themeFieldBg = 0xFFFFFFFF;
        themeFieldBorder = TicketUi.BORDER;
        themeFieldText = TicketUi.TEXT_DARK;
        themeFieldFocus = TicketUi.GREEN;
        loadStations();
    }

    private void loadStations() {
        final FareRuntime fares = FareRuntime.get();
        fares.sync();
        final List<Route> routes = new ArrayList<>(ClientData.ROUTES);
        routes.sort(Comparator.comparing((Route route) -> route.name == null ? "" : route.name,
                String.CASE_INSENSITIVE_ORDER));
        for (final Route route : routes) {
            final Map<Long, String> stations = new LinkedHashMap<>();
            for (final Route.RoutePlatform platform : route.platformIds) {
                final Station station = ClientData.DATA_CACHE.platformIdToStation.get(platform.platformId);
                if (station != null && station.name != null && !station.name.isBlank()) {
                    stations.putIfAbsent(station.id, station.name);
                }
            }
            if (!stations.isEmpty()) {
                groups.add(new LineGroup(route.name == null ? "?" : route.name,
                        rowsOf(stations, fares)));
            }
        }
    }

    private List<StationRow> rowsOf(Map<Long, String> stations, FareRuntime fares) {
        final List<StationRow> rows = new ArrayList<>();
        for (final Map.Entry<Long, String> entry : stations.entrySet()) {
            final long fare = fares.quote(stationId, entry.getKey()).totalCents();
            rows.add(new StationRow(entry.getKey(), entry.getValue(), fare));
        }
        return rows;
    }

    @Override
    protected void renderPage(GuiGraphics guiGraphics, float mx, float my) {
        TicketUi.panel(this, guiGraphics);
        renderTabs(guiGraphics, mx, my);
        if (tab == TAB_SINGLE) {
            if (transactionOpen) {
                renderTransaction(guiGraphics, mx, my);
            } else {
                renderSingle(guiGraphics, mx, my);
            }
            renderRightPanel(guiGraphics, mx, my);
        } else if (tab == TAB_PASS) {
            if (transactionOpen) {
                renderTransaction(guiGraphics, mx, my);
            } else {
                renderPasses(guiGraphics, mx, my);
            }
            renderStationInfo(guiGraphics);
        } else {
            renderCard(guiGraphics, mx, my);
            renderKeypadPanel(guiGraphics, mx, my, "gui.trdm.ticket_machine.topup");
        }
        if (dialog != null) {
            TicketUi.dialog(this, guiGraphics, dialogMessage, mx, my);
        }
    }

    private void renderTabs(GuiGraphics guiGraphics, float mx, float my) {
        final String[] keys = {
                "gui.trdm.ticket_machine.single",
                "gui.trdm.ticket_machine.pass",
                "gui.trdm.ticket_machine.card"
        };
        for (int index = 0; index < keys.length; index++) {
            final float x = 100F + index * 138F;
            TicketUi.tab(this, guiGraphics, x, TAB_Y, TAB_W, TAB_H,
                    Component.translatable(keys[index]), tab == index,
                    hit(mx, my, x, TAB_Y, TAB_W, TAB_H));
        }
    }

    // ===== 单程票 =====

    private void renderSingle(GuiGraphics guiGraphics, float mx, float my) {
        rect(guiGraphics, LIST_X - 6F, 58F, LIST_W + 12F, LIST_BOTTOM - 58F, TicketUi.BORDER);
        rect(guiGraphics, LIST_X - 5F, 59F, LIST_W + 10F, LIST_BOTTOM - 60F, TicketUi.BG_LIGHT);

        field(guiGraphics, FIELD_SEARCH, LIST_X, 64F, LIST_W, 22F, FONT_ROW, query);
        if (query.isEmpty() && !fieldActive(FIELD_SEARCH)) {
            text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.search"),
                    LIST_X + 8F, 70F, FONT_ROW, TicketUi.TEXT_MUTED);
        }
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.all_stations"),
                LIST_X, 96F, FONT_ROW, TicketUi.TEXT_DARK);

        if (groups.isEmpty()) {
            text(guiGraphics, Component.translatable("gui.trdm.ticket.no_line_data"),
                    LIST_X, LIST_TOP + 4F, FONT_ROW, TicketUi.TEXT_MUTED);
            return;
        }

        final List<LineGroup> visible = visibleGroups();
        final float total = totalHeight(visible);
        if (total <= 0F) {
            hintDark(guiGraphics, "gui.trdm.ticket_machine.empty", LIST_X, LIST_TOP + 4F);
            return;
        }
        scissorOn(guiGraphics, LIST_X - 4F, LIST_TOP, LIST_W + 8F, LIST_BOTTOM - LIST_TOP);
        float top = LIST_TOP - listScroll;
        for (final LineGroup group : visible) {
            if (top + GROUP_H >= LIST_TOP && top <= LIST_BOTTOM) {
                rect(guiGraphics, LIST_X - 4F, top, LIST_W + 8F, GROUP_H - 1F, TicketUi.ROW_HEADER);
                text(guiGraphics, Component.literal(group.name()), LIST_X + 4F, top + 5F,
                        FONT_SUB, TicketUi.TEXT_DARK);
            }
            top += GROUP_H;
            for (final StationRow row : group.stations()) {
                if (top + ROW_H >= LIST_TOP && top <= LIST_BOTTOM) {
                    final boolean selected = row.id() == selectedStationId;
                    final boolean hovered = mx >= LIST_X && mx < LIST_X + LIST_W
                            && my >= top && my < top + ROW_H;
                    rect(guiGraphics, LIST_X - 4F, top, LIST_W + 8F, ROW_H - 1F,
                            selected ? TicketUi.GREEN : (hovered ? TicketUi.ROW_HOVER : TicketUi.ROW));
                    text(guiGraphics, Component.literal(row.name()), LIST_X + 4F, top + 7F,
                            FONT_ROW, selected ? TicketUi.TEXT_LIGHT : TicketUi.TEXT_DARK);
                    textRight(guiGraphics, Component.literal(FareMoney.yuan(row.fareCents())),
                            LIST_X + LIST_W - 4F, top + 7F, FONT_ROW,
                            selected ? TicketUi.TEXT_LIGHT : TicketUi.TEXT_DARK);
                }
                top += ROW_H;
            }
        }
        scissorOff(guiGraphics);
        scrollbar(guiGraphics, LIST_X + LIST_W + 2F, LIST_TOP, LIST_BOTTOM - LIST_TOP,
                total, LIST_BOTTOM - LIST_TOP, listScroll);
    }

    private List<LineGroup> visibleGroups() {
        final String raw = fieldActive(FIELD_SEARCH) ? editingValue() : query;
        final String needle = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        final List<LineGroup> base = selectedLine >= 0 && selectedLine < groups.size()
                ? List.of(groups.get(selectedLine)) : groups;
        if (needle.isEmpty()) {
            return base;
        }
        final List<LineGroup> filtered = new ArrayList<>();
        for (final LineGroup group : base) {
            final List<StationRow> rows = new ArrayList<>();
            for (final StationRow row : group.stations()) {
                if (row.name().toLowerCase(Locale.ROOT).contains(needle)) {
                    rows.add(row);
                }
            }
            if (!rows.isEmpty()) {
                filtered.add(new LineGroup(group.name(), rows));
            }
        }
        return filtered;
    }

    private float totalHeight(List<LineGroup> visible) {
        float total = 0F;
        for (final LineGroup group : visible) {
            total += GROUP_H + group.stations().size() * ROW_H;
        }
        return total;
    }

    private void renderRightPanel(GuiGraphics guiGraphics, float mx, float my) {
        rect(guiGraphics, RIGHT_X - 8F, 58F, RIGHT_W + 16F, 66F, 0xFF8A8A8A);
        text(guiGraphics, Component.literal(ellipsize(stationName, RIGHT_W, FONT_SECTION)),
                RIGHT_X, 66F, FONT_SECTION, TicketUi.TEXT_LIGHT);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.in_service"),
                RIGHT_X, 88F, FONT_SUB, 0xFFE0E0E0);

        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.choose_line"),
                RIGHT_X, 134F, FONT_ROW, TicketUi.TEXT_DARK);
        final int lineCount = Math.min(4, groups.size());
        for (int index = 0; index < lineCount; index++) {
            final float y = 154F + index * 28F;
            TicketUi.option(this, guiGraphics, RIGHT_X, y, 220F, 24F,
                    Component.literal(ellipsize(groups.get(index).name(), 210F, FONT_SUB)),
                    selectedLine == index, hit(mx, my, RIGHT_X, y, 220F, 24F), FONT_SUB);
        }

        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.choose_price"),
                RIGHT_X, 282F, FONT_ROW, TicketUi.TEXT_DARK);
        rect(guiGraphics, RIGHT_X, 302F, 190F, 24F, TicketUi.BORDER);
        rect(guiGraphics, RIGHT_X + 1F, 303F, 188F, 22F, 0xFFFFFFFF);
        text(guiGraphics, Component.literal(priceInput.isEmpty() ? "¥" : "¥" + priceInput),
                RIGHT_X + 8F, 308F, FONT_ROW, TicketUi.TEXT_DARK);
        TicketUi.keypad(this, guiGraphics, RIGHT_X, 334F, mx, my);
    }

    // ===== 通用票 =====

    private void renderPasses(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.all_passes"),
                LIST_X, 64F, FONT_ROW, TicketUi.TEXT_DARK);
        final java.util.List<PassRule> passes = FareRuntime.get().config().passRules;
        for (int index = 0; index < passes.size(); index++) {
            final PassRule pass = passes.get(index);
            final float top = 92F + index * 34F;
            rect(guiGraphics, LIST_X - 4F, top, LIST_W + 8F, 32F,
                    hit(mx, my, LIST_X, top, LIST_W, 32F) ? TicketUi.ROW_HOVER : TicketUi.ROW);
            text(guiGraphics, Component.literal(pass.name), LIST_X + 4F, top + 10F,
                    FONT_ROW, TicketUi.TEXT_DARK);
            textRight(guiGraphics, Component.literal(FareMoney.yuan(pass.settings().priceEmeralds() * 100L)),
                    LIST_X + LIST_W - 4F, top + 10F, FONT_ROW, TicketUi.TEXT_DARK);
        }
        if (passes.isEmpty()) {
            text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.no_passes"),
                    LIST_X + 4F, 96F, FONT_ROW, TicketUi.TEXT_MUTED);
        }
        hintDark(guiGraphics, "gui.trdm.ticket_machine.pass_hint", LIST_X, 244F);
    }

    // ===== 交通卡 =====

    private void renderCard(GuiGraphics guiGraphics, float mx, float my) {
        final int topUp = parsePrice();
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.card_info"),
                LIST_X, 64F, FONT_SECTION, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.card_fee"),
                LIST_X, 108F, FONT_ROW, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.card_topup", topUp),
                LIST_X, 132F, FONT_ROW, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.card_total", 18 + topUp),
                LIST_X, 164F, FONT_ROW, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.pay_emerald", 18 + topUp),
                LIST_X, 188F, FONT_ROW, TicketUi.TEXT_DARK);
        TicketUi.emerald(this, guiGraphics,
                LIST_X + textWidth(Component.translatable("gui.trdm.ticket_machine.pay_emerald", 18 + topUp),
                        FONT_ROW) + 6F, 187F, 14F);
        TicketUi.button(this, guiGraphics, LIST_X, 400F, 150F, 28F,
                Component.translatable("gui.trdm.ticket_machine.pay"),
                hit(mx, my, LIST_X, 400F, 150F, 28F), FONT_ROW);
    }

    private void renderKeypadPanel(GuiGraphics guiGraphics, float mx, float my, String labelKey) {
        text(guiGraphics, Component.translatable(labelKey), RIGHT_X, 282F, FONT_ROW, TicketUi.TEXT_DARK);
        rect(guiGraphics, RIGHT_X, 302F, 190F, 24F, TicketUi.BORDER);
        rect(guiGraphics, RIGHT_X + 1F, 303F, 188F, 22F, 0xFFFFFFFF);
        text(guiGraphics, Component.literal(priceInput.isEmpty() ? "¥" : "¥" + priceInput),
                RIGHT_X + 8F, 308F, FONT_ROW, TicketUi.TEXT_DARK);
        TicketUi.keypad(this, guiGraphics, RIGHT_X, 334F, mx, my);
    }

    private void renderStationInfo(GuiGraphics guiGraphics) {
        rect(guiGraphics, RIGHT_X - 8F, 58F, RIGHT_W + 16F, 66F, 0xFF8A8A8A);
        text(guiGraphics, Component.literal(ellipsize(stationName, RIGHT_W, FONT_SECTION)),
                RIGHT_X, 66F, FONT_SECTION, TicketUi.TEXT_LIGHT);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.in_service"),
                RIGHT_X, 88F, FONT_SUB, 0xFFE0E0E0);
    }

    // ===== 交易信息 =====

    private void renderTransaction(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.transaction"),
                LIST_X, 64F, FONT_SECTION, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.literal(transactionTitle), LIST_X, 108F, FONT_ROW, TicketUi.TEXT_DARK);
        final long totalCents = unitPriceCents * quantity;
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.fare_amount",
                FareMoney.text(unitPriceCents)), LIST_X, 132F, FONT_ROW, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.total",
                FareMoney.text(totalCents)), LIST_X, 160F, FONT_ROW, TicketUi.TEXT_DARK);
        final Component pay = Component.translatable("gui.trdm.ticket_machine.pay_emerald",
                FareMoney.emeralds(unitPriceCents) * quantity);
        text(guiGraphics, pay, LIST_X, 184F, FONT_ROW, TicketUi.TEXT_DARK);
        TicketUi.emerald(this, guiGraphics, LIST_X + textWidth(pay, FONT_ROW) + 6F, 183F, 14F);

        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.quantity"),
                LIST_X, 226F, FONT_ROW, TicketUi.TEXT_DARK);
        TicketUi.button(this, guiGraphics, LIST_X + 70F, 218F, 26F, 24F,
                Component.literal("-"), hit(mx, my, LIST_X + 70F, 218F, 26F, 24F), FONT_ROW);
        textCentred(guiGraphics, Component.literal(String.valueOf(quantity)), LIST_X + 100F, 28F,
                222F, FONT_ROW, TicketUi.TEXT_DARK);
        TicketUi.button(this, guiGraphics, LIST_X + 132F, 218F, 26F, 24F,
                Component.literal("+"), hit(mx, my, LIST_X + 132F, 218F, 26F, 24F), FONT_ROW);

        TicketUi.button(this, guiGraphics, LIST_X, 400F, 150F, 28F,
                Component.translatable("gui.trdm.ticket_machine.pay"),
                hit(mx, my, LIST_X, 400F, 150F, 28F), FONT_ROW);
        TicketUi.button(this, guiGraphics, LIST_X + 164F, 400F, 100F, 28F,
                Component.translatable("gui.trdm.ticket_machine.back"),
                hit(mx, my, LIST_X + 164F, 400F, 100F, 28F), FONT_ROW);
    }

    // ===== 点击 =====

    @Override
    protected boolean clickPage(float mx, float my) {
        if (!inPanel(mx, my)) {
            return false;
        }
        if (dialog != null) {
            if (TicketUi.dialogConfirmHit(mx, my)) {
                dialog = null;
                transactionOpen = false;
            }
            return true;
        }

        for (int index = 0; index < 3; index++) {
            final float x = 100F + index * 138F;
            if (hit(mx, my, x, TAB_Y, TAB_W, TAB_H)) {
                tab = index;
                transactionOpen = false;
                priceInput = "";
                return true;
            }
        }

        if (tab == TAB_SINGLE) {
            return clickSingle(mx, my);
        }
        if (tab == TAB_PASS) {
            return clickPass(mx, my);
        }
        return clickCard(mx, my);
    }

    private boolean clickSingle(float mx, float my) {
        if (transactionOpen) {
            if (hit(mx, my, LIST_X + 70F, 218F, 26F, 24F)) {
                quantity = Math.max(1, quantity - 1);
                return true;
            }
            if (hit(mx, my, LIST_X + 132F, 218F, 26F, 24F)) {
                quantity = Math.min(64, quantity + 1);
                return true;
            }
            if (hit(mx, my, LIST_X, 400F, 150F, 28F)) {
                if (!purchasePending) {
                    purchasePending = true;
                    TicketClientNetwork.buySingle(pos, transactionByPrice ? 0L : transactionStationId,
                            unitPriceCents, quantity, transactionByPrice);
                }
                return true;
            }
            if (hit(mx, my, LIST_X + 164F, 400F, 100F, 28F)) {
                transactionOpen = false;
                return true;
            }
            return false;
        }

        if (hit(mx, my, LIST_X, 64F, LIST_W, 22F)) {
            beginField(FIELD_SEARCH, LIST_X, 64F, LIST_W, 22F, query, 64, FONT_ROW, value -> {
                query = value == null ? "" : value;
                clampScroll();
            });
            return true;
        }

        if (my >= LIST_TOP && my < LIST_BOTTOM && mx >= LIST_X && mx < LIST_X + LIST_W) {
            float top = LIST_TOP - listScroll;
            for (final LineGroup group : visibleGroups()) {
                top += GROUP_H;
                for (final StationRow row : group.stations()) {
                    if (my >= top && my < top + ROW_H) {
                        selectedStationId = row.id();
                        selectedStationName = row.name();
                        priceInput = FareMoney.input(row.fareCents());
                        transactionTitle = Component.translatable("gui.trdm.ticket_machine.single_title",
                                row.name()).getString();
                        transactionStationId = row.id();
                        transactionByPrice = false;
                        unitPriceCents = row.fareCents();
                        quantity = 1;
                        transactionOpen = true;
                        return true;
                    }
                    top += ROW_H;
                }
            }
        }

        final int lineCount = Math.min(4, groups.size());
        for (int index = 0; index < lineCount; index++) {
            final float y = 154F + index * 28F;
            if (hit(mx, my, RIGHT_X, y, 220F, 24F)) {
                selectedLine = selectedLine == index ? -1 : index;
                selectedStationId = -1L;
                selectedStationName = "";
                priceInput = "";
                listScroll = 0F;
                return true;
            }
        }

        final String key = TicketUi.keypadHit(RIGHT_X, 334F, mx, my);
        if (key != null) {
            handleKey(key);
            return true;
        }
        return false;
    }

    private boolean clickPass(float mx, float my) {
        if (transactionOpen) {
            if (hit(mx, my, LIST_X + 70F, 218F, 26F, 24F)) {
                quantity = Math.max(1, quantity - 1);
                return true;
            }
            if (hit(mx, my, LIST_X + 132F, 218F, 26F, 24F)) {
                quantity = Math.min(64, quantity + 1);
                return true;
            }
            if (hit(mx, my, LIST_X, 400F, 150F, 28F)) {
                if (!purchasePending && !transactionPassId.isEmpty()) {
                    purchasePending = true;
                    TicketClientNetwork.buyPass(pos, transactionPassId, quantity);
                }
                return true;
            }
            if (hit(mx, my, LIST_X + 164F, 400F, 100F, 28F)) {
                transactionOpen = false;
                return true;
            }
            return false;
        }
        final java.util.List<PassRule> passes = FareRuntime.get().config().passRules;
        for (int index = 0; index < passes.size(); index++) {
            if (hit(mx, my, LIST_X, 92F + index * 34F, LIST_W, 32F)) {
                final PassRule pass = passes.get(index);
                transactionPassId = pass.id;
                transactionTitle = pass.name;
                unitPriceCents = pass.settings().priceEmeralds() * 100L;
                quantity = 1;
                transactionOpen = true;
                return true;
            }
        }
        return false;
    }

    private boolean clickCard(float mx, float my) {
        final String key = TicketUi.keypadHit(RIGHT_X, 334F, mx, my);
        if (key != null) {
            handleKey(key);
            return true;
        }
        if (hit(mx, my, LIST_X, 400F, 150F, 28F)) {
            if (!purchasePending) {
                purchasePending = true;
                TicketClientNetwork.buyCard(pos, parsePrice());
            }
            return true;
        }
        return false;
    }

    private void handleKey(String key) {
        switch (key) {
            case "<" -> {
                if (!priceInput.isEmpty()) {
                    priceInput = priceInput.substring(0, priceInput.length() - 1);
                }
            }
            case "OK" -> {
                if (tab == TAB_SINGLE && !priceInput.isEmpty() && parsePrice() > 0) {
                    transactionTitle = Component.translatable("gui.trdm.ticket_machine.by_price").getString();
                    selectedStationId = -1L;
                    selectedStationName = "";
                    transactionStationId = -1L;
                    transactionByPrice = true;
                    unitPriceCents = parsePrice() * 100L;
                    quantity = 1;
                    transactionOpen = true;
                }
            }
            default -> {
                if (priceInput.length() < 3) {
                    priceInput = priceInput.equals("0") ? key : priceInput + key;
                }
            }
        }
    }

    private int parsePrice() {
        if (priceInput.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(999, Integer.parseInt(priceInput)));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    @Override
    public void showTicketResult(boolean success, Component message) {
        purchasePending = false;
        dialogMessage = message;
        dialog = "ok";
        if (success) {
            transactionOpen = false;
        }
    }

    private void clampScroll() {
        final float total = totalHeight(visibleGroups());
        listScroll = Math.max(0F, Math.min(listScroll, Math.max(0F, total - (LIST_BOTTOM - LIST_TOP))));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        final float my = designY(mouseY);
        if (tab == TAB_SINGLE && !transactionOpen && my >= LIST_TOP && my < LIST_BOTTOM) {
            listScroll -= (float) delta * ROW_H;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean inPanel(float mx, float my) {
        return mx >= TicketUi.PANEL_X && mx < TicketUi.PANEL_X + TicketUi.PANEL_W
                && my >= TicketUi.PANEL_Y && my < TicketUi.PANEL_Y + TicketUi.PANEL_H;
    }

    private void hintDark(GuiGraphics guiGraphics, String key, float x, float y) {
        text(guiGraphics, Component.translatable(key), x, y, FONT_SUB, TicketUi.TEXT_MUTED);
    }

    private String ellipsize(String text, float maxWidth, float fontPx) {
        final String value = text == null ? "" : text;
        if (textWidth(Component.literal(value), fontPx) <= maxWidth) {
            return value;
        }
        final String ellipsis = "…";
        final float tail = textWidth(Component.literal(ellipsis), fontPx);
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final String next = builder.toString() + value.charAt(index);
            if (textWidth(Component.literal(next), fontPx) + tail > maxWidth) {
                break;
            }
            builder.append(value.charAt(index));
        }
        return builder + ellipsis;
    }

    private record LineGroup(String name, List<StationRow> stations) {
    }

    private record StationRow(long id, String name, long fareCents) {
    }
}
