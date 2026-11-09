package top.xiaotang27.trdm.client;

import mtr.client.ClientData;
import mtr.data.Route;
import mtr.data.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.fare.FareMoney;
import top.xiaotang27.trdm.ticket.PassValidity;
import top.xiaotang27.trdm.ticket.TicketData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 客服终端界面：补票/补记录、交通卡充值/记录查询、售票售卡。浅色主题；补票按计费引擎报价。 */
public class CustomerServiceTerminalScreen extends TrdmScreenBase implements TicketResultReceiver {

    private static final int VIEW_HOME = 0;
    private static final int VIEW_REPAIR = 1;
    private static final int VIEW_INFO = 2;
    private static final int VIEW_CARD = 3;
    private static final int VIEW_RECHARGE = 4;
    private static final int VIEW_RECORDS = 5;
    private static final int VIEW_RECORD_TYPE = 6;
    private static final int VIEW_STATION = 7;

    private static final float BACK_W = 84F;
    private static final float BACK_H = 24F;

    private static final ResourceLocation ICON_TICKETS =
            new ResourceLocation("mtr", "textures/block/sign/tickets.png");
    private static final ResourceLocation ICON_GATE =
            new ResourceLocation("mtr", "textures/item/ticket_processor.png");
    private static final ResourceLocation ICON_CARD =
            new ResourceLocation("trdm", "textures/item/transit_card.png");
    private static final ResourceLocation ICON_MACHINE =
            new ResourceLocation("mtr", "textures/item/ticket_machine.png");
    private static final ResourceLocation ICON_ENTRY =
            new ResourceLocation("mtr", "textures/item/ticket_processor_entrance.png");
    private static final ResourceLocation ICON_EXIT =
            new ResourceLocation("mtr", "textures/item/ticket_processor_exit.png");

    private static final Object FIELD_STATION = new Object();

    /** 补票/补记录时，选择的车站代表“入闸站”；出闸站临时改为所选站。 */
    private static final int RECORD_ENTRY = 0;
    private static final int RECORD_EXIT = 1;

    private static final int ACTION_NONE = 0;
    private static final int ACTION_PAY_FARE = 1;
    private static final int ACTION_RECORD_ENTRY = 2;
    private static final int ACTION_RECORD_EXIT = 3;
    private static final int ACTION_EXIT_TICKET = 4;

    private final String stationName;
    private final long stationId;
    private final BlockPos pos;

    private int view = VIEW_HOME;
    private String amountInput = "";
    private String dialog;
    private Component dialogMessage = Component.empty();
    private boolean purchasePending;
    private String infoTitle = "";
    private final List<String> infoLines = new ArrayList<>();
    private long infoAmountCents;
    private boolean infoNeedsPayment;
    private boolean infoFromRecord;
    private int infoAction = ACTION_NONE;
    private long infoStationId;

    private final List<LineGroup> stationGroups = new ArrayList<>();
    private String stationQuery = "";
    private float stationScroll;
    private int recordMode = RECORD_ENTRY;
    private String recordStation = "";
    private long recordStationId;

    public CustomerServiceTerminalScreen(String stationName, long stationId, BlockPos pos) {
        super(null, Component.translatable("block.trdm.customer_service_terminal"));
        this.stationName = stationName == null ? "" : stationName;
        this.stationId = stationId;
        this.pos = pos;
        themeFieldBg = 0xFFFFFFFF;
        themeFieldBorder = TicketUi.BORDER;
        themeFieldText = TicketUi.TEXT_DARK;
        themeFieldFocus = TicketUi.GREEN;
        loadStationGroups();
    }

    private void loadStationGroups() {
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
                stationGroups.add(new LineGroup(route.name == null ? "?" : route.name,
                        rowsOf(stations, fares)));
            }
        }
    }

    private List<StationRow> rowsOf(Map<Long, String> stations, FareRuntime fares) {
        final List<StationRow> rows = new ArrayList<>();
        for (final Map.Entry<Long, String> entry : stations.entrySet()) {
            final long fare = fares.quote(entry.getKey(), stationId).totalCents();
            rows.add(new StationRow(entry.getKey(), entry.getValue(), fare));
        }
        return rows;
    }

    @Override
    protected void renderPage(GuiGraphics guiGraphics, float mx, float my) {
        TicketUi.panel(this, guiGraphics, false);
        renderHeader(guiGraphics, mx, my);
        switch (view) {
            case VIEW_REPAIR -> renderRepair(guiGraphics, mx, my);
            case VIEW_INFO -> renderInfo(guiGraphics, mx, my);
            case VIEW_CARD -> renderCardMenu(guiGraphics, mx, my);
            case VIEW_RECHARGE -> renderRecharge(guiGraphics, mx, my);
            case VIEW_RECORDS -> renderRecords(guiGraphics, mx, my);
            case VIEW_RECORD_TYPE -> renderRecordType(guiGraphics, mx, my);
            case VIEW_STATION -> renderStationPicker(guiGraphics, mx, my);
            default -> renderHome(guiGraphics, mx, my);
        }
        if (dialog != null) {
            TicketUi.dialog(this, guiGraphics, dialogMessage, mx, my);
        }
    }

    private void renderHeader(GuiGraphics guiGraphics, float mx, float my) {
        TicketUi.logo(this, guiGraphics, TicketUi.PANEL_X + 10F, TicketUi.PANEL_Y + 5F, 24F);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.station", stationName),
                TicketUi.PANEL_X + 74F, TicketUi.PANEL_Y + 10F, FONT_ROW, TicketUi.TEXT_LIGHT);
        if (view != VIEW_HOME) {
            final float x = TicketUi.PANEL_X + TicketUi.PANEL_W - BACK_W - 10F;
            TicketUi.button(this, guiGraphics, x, TicketUi.PANEL_Y + 5F, BACK_W, BACK_H,
                    Component.translatable("gui.trdm.ticket.back"),
                    hit(mx, my, x, TicketUi.PANEL_Y + 5F, BACK_W, BACK_H), FONT_ROW);
        }
    }

    private void renderHome(GuiGraphics guiGraphics, float mx, float my) {
        centred(guiGraphics, Component.translatable("gui.trdm.terminal.choose"),
                DESIGN_W / 2F, 84F, FONT_SECTION, TicketUi.TEXT_DARK);
        TicketUi.card(this, guiGraphics, 140F, 150F, 200F, 180F, ICON_TICKETS, 0xFF1E6FD9,
                Component.translatable("gui.trdm.terminal.repair"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 380F, 150F, 200F, 180F, ICON_CARD, 0xFF0A7A1E,
                Component.translatable("gui.trdm.terminal.card"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 620F, 150F, 200F, 180F, ICON_MACHINE, 0xFF9A5A12,
                Component.translatable("gui.trdm.terminal.sales"),
                Component.translatable("gui.trdm.terminal.sales.sub"), mx, my);
        renderHomeNotices(guiGraphics, mx, my);
    }

    /** 主页业务按钮下方的待处理提示：未结算车程、低余额。 */
    private void renderHomeNotices(GuiGraphics guiGraphics, float mx, float my) {
        final ItemStack held = heldItem();
        final boolean pending = hasPendingJourney(held);
        final boolean low = hasLowBalance(held);
        if (!pending && !low) {
            return;
        }
        final float x = 140F;
        final float y = 340F;
        final float w = 680F;
        final int rows = (pending ? 1 : 0) + (low ? 1 : 0);
        final float h = 30F + rows * 36F;
        rect(guiGraphics, x, y, w, h, TicketUi.BORDER);
        rect(guiGraphics, x + 1F, y + 1F, w - 2F, h - 2F, 0xFFF7F7F7);
        rect(guiGraphics, x + 1F, y + 1F, w - 2F, 22F, TicketUi.ROW_HEADER);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.notice_title"),
                x + 10F, y + 8F, FONT_ROW, TicketUi.TEXT_DARK);

        float rowY = y + 28F;
        if (pending) {
            rect(guiGraphics, x + 1F, rowY - 2F, 3F, 32F, 0xFFC77A00);
            text(guiGraphics, Component.translatable("gui.trdm.terminal.notice_pending"),
                    x + 10F, rowY, FONT_ROW, TicketUi.TEXT_DARK);
            final String entryName = TicketData.entryName(held);
            if (!entryName.isEmpty()) {
                text(guiGraphics, Component.translatable("gui.trdm.terminal.notice_entry", entryName),
                        x + 10F, rowY + 14F, FONT_SUB, TicketUi.TEXT_MUTED);
            }
            final float bx = x + w - 108F;
            TicketUi.button(this, guiGraphics, bx, rowY - 3F, 96F, 24F,
                    Component.translatable("gui.trdm.terminal.notice_handle"),
                    hit(mx, my, bx, rowY - 3F, 96F, 24F), FONT_ROW);
            rowY += 36F;
        }
        if (low) {
            rect(guiGraphics, x + 1F, rowY - 2F, 3F, 32F, 0xFFC0392B);
            text(guiGraphics, Component.translatable("gui.trdm.terminal.notice_low_balance"),
                    x + 10F, rowY, FONT_ROW, TicketUi.TEXT_DARK);
            text(guiGraphics, Component.translatable("gui.trdm.terminal.notice_balance",
                            FareMoney.text(TicketData.balance(held))),
                    x + 10F, rowY + 14F, FONT_SUB, TicketUi.TEXT_MUTED);
            final float bx = x + w - 108F;
            TicketUi.button(this, guiGraphics, bx, rowY - 3F, 96F, 24F,
                    Component.translatable("gui.trdm.terminal.notice_recharge"),
                    hit(mx, my, bx, rowY - 3F, 96F, 24F), FONT_ROW);
        }
    }

    private static boolean hasPendingJourney(ItemStack held) {
        if (!TicketData.hasData(held)) {
            return false;
        }
        if (!held.is(Trdm.SINGLE_TICKET_ITEM) && !held.is(Trdm.TRANSIT_CARD_ITEM)
                && !held.is(Trdm.VARIABLE_TICKET_ITEM)) {
            return false;
        }
        return TicketData.state(held) == TicketData.State.ENTERED && TicketData.hasEntry(held);
    }

    private static boolean hasLowBalance(ItemStack held) {
        return held.is(Trdm.TRANSIT_CARD_ITEM) && TicketData.balance(held) < 1000L;
    }

    private void renderRepair(GuiGraphics guiGraphics, float mx, float my) {
        centred(guiGraphics, Component.translatable("gui.trdm.terminal.choose_repair"),
                DESIGN_W / 2F, 84F, FONT_SECTION, TicketUi.TEXT_DARK);
        TicketUi.card(this, guiGraphics, 160F, 160F, 190F, 160F, ICON_TICKETS, 0xFF1E6FD9,
                Component.translatable("gui.trdm.terminal.fare"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 385F, 160F, 190F, 160F, ICON_GATE, 0xFF0A7A1E,
                Component.translatable("gui.trdm.terminal.record"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 610F, 160F, 190F, 160F, ICON_TICKETS, 0xFF9A5A12,
                Component.translatable("gui.trdm.terminal.no_ticket"),
                Component.translatable("gui.trdm.terminal.no_ticket.sub"), mx, my);
    }

    private void renderInfo(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.literal(infoTitle), TicketUi.PANEL_X + 20F, 78F,
                FONT_SECTION, TicketUi.TEXT_DARK);
        for (int index = 0; index < infoLines.size(); index++) {
            text(guiGraphics, Component.literal(infoLines.get(index)), TicketUi.PANEL_X + 20F,
                    120F + index * 24F, FONT_ROW, TicketUi.TEXT_DARK);
        }
        final float payY = 120F + infoLines.size() * 24F + 6F;
        if (infoNeedsPayment) {
            final Component pay = Component.translatable("gui.trdm.terminal.pay_emerald",
                    FareMoney.emeralds(infoAmountCents));
            text(guiGraphics, pay, TicketUi.PANEL_X + 20F, payY, FONT_ROW, TicketUi.TEXT_DARK);
            TicketUi.emerald(this, guiGraphics,
                    TicketUi.PANEL_X + 20F + textWidth(pay, FONT_ROW) + 6F, payY - 1F, 14F);
        }
        TicketUi.button(this, guiGraphics, TicketUi.PANEL_X + 20F, 400F, 150F, 28F,
                Component.translatable(infoNeedsPayment
                        ? "gui.trdm.terminal.pay" : "gui.trdm.terminal.confirm"),
                hit(mx, my, TicketUi.PANEL_X + 20F, 400F, 150F, 28F), FONT_ROW);
        TicketUi.button(this, guiGraphics, TicketUi.PANEL_X + 184F, 400F, 100F, 28F,
                Component.translatable("gui.trdm.ticket.back"),
                hit(mx, my, TicketUi.PANEL_X + 184F, 400F, 100F, 28F), FONT_ROW);
    }

    private void renderRecordType(GuiGraphics guiGraphics, float mx, float my) {
        centred(guiGraphics, Component.translatable("gui.trdm.terminal.choose_record"),
                DESIGN_W / 2F, 84F, FONT_SECTION, TicketUi.TEXT_DARK);
        TicketUi.card(this, guiGraphics, 250F, 170F, 200F, 160F, ICON_ENTRY, 0xFF1E6FD9,
                Component.translatable("gui.trdm.terminal.entry_card"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 510F, 170F, 200F, 160F, ICON_EXIT, 0xFF9A5A12,
                Component.translatable("gui.trdm.terminal.exit_card"), Component.empty(), mx, my);
    }

    private void renderStationPicker(GuiGraphics guiGraphics, float mx, float my) {
        final String titleKey = recordMode == RECORD_EXIT
                ? "gui.trdm.terminal.choose_exit_station"
                : "gui.trdm.terminal.choose_entry_station";
        text(guiGraphics, Component.translatable(titleKey),
                TicketUi.PANEL_X + 20F, 70F, FONT_SECTION, TicketUi.TEXT_DARK);
        field(guiGraphics, FIELD_STATION, TicketUi.PANEL_X + 20F, 98F,
                TicketUi.PANEL_W - 40F, 22F, FONT_ROW, stationQuery);
        if (stationQuery.isEmpty() && !fieldActive(FIELD_STATION)) {
            text(guiGraphics, Component.translatable("gui.trdm.terminal.search_station"),
                    TicketUi.PANEL_X + 28F, 104F, FONT_ROW, TicketUi.TEXT_MUTED);
        }
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.all_stations"),
                TicketUi.PANEL_X + 20F, 130F, FONT_ROW, TicketUi.TEXT_DARK);

        if (stationGroups.isEmpty()) {
            text(guiGraphics, Component.translatable("gui.trdm.ticket.no_line_data"),
                    TicketUi.PANEL_X + 20F, 154F + 4F, FONT_ROW, TicketUi.TEXT_MUTED);
            return;
        }

        final List<LineGroup> visible = visibleStationGroups();
        final float total = stationTotalHeight(visible);
        final float listTop = 154F;
        final float listBottom = 470F;
        if (total <= 0F) {
            text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.empty"),
                    TicketUi.PANEL_X + 20F, listTop + 4F, FONT_SUB, TicketUi.TEXT_MUTED);
            return;
        }
        scissorOn(guiGraphics, TicketUi.PANEL_X + 16F, listTop,
                TicketUi.PANEL_W - 32F, listBottom - listTop);
        float top = listTop - stationScroll;
        for (final LineGroup group : visible) {
            if (top + 22F >= listTop && top <= listBottom) {
                rect(guiGraphics, TicketUi.PANEL_X + 16F, top, TicketUi.PANEL_W - 32F, 21F,
                        TicketUi.ROW_HEADER);
                text(guiGraphics, Component.literal(group.name()), TicketUi.PANEL_X + 22F, top + 4F,
                        FONT_SUB, TicketUi.TEXT_DARK);
            }
            top += 22F;
            for (final StationRow row : group.stations()) {
                if (top + 24F >= listTop && top <= listBottom) {
                    final boolean hovered = mx >= TicketUi.PANEL_X + 16F
                            && mx < TicketUi.PANEL_X + TicketUi.PANEL_W - 16F
                            && my >= top && my < top + 24F;
                    rect(guiGraphics, TicketUi.PANEL_X + 16F, top, TicketUi.PANEL_W - 32F, 23F,
                            hovered ? TicketUi.ROW_HOVER : TicketUi.ROW);
                    text(guiGraphics, Component.literal(row.name()), TicketUi.PANEL_X + 22F, top + 6F,
                            FONT_ROW, TicketUi.TEXT_DARK);
                    if (showsStationFares()) {
                        textRight(guiGraphics, Component.literal(FareMoney.yuan(row.fareCents())),
                                TicketUi.PANEL_X + TicketUi.PANEL_W - 22F, top + 6F,
                                FONT_ROW, TicketUi.TEXT_DARK);
                    } else {
                        textRight(guiGraphics, Component.literal("—"),
                                TicketUi.PANEL_X + TicketUi.PANEL_W - 22F, top + 6F,
                                FONT_ROW, TicketUi.TEXT_MUTED);
                    }
                }
                top += 24F;
            }
        }
        scissorOff(guiGraphics);
        scrollbar(guiGraphics, TicketUi.PANEL_X + TicketUi.PANEL_W - 24F, listTop,
                listBottom - listTop, total, listBottom - listTop, stationScroll);
    }

    private List<LineGroup> visibleStationGroups() {
        final String raw = fieldActive(FIELD_STATION) ? editingValue() : stationQuery;
        final String needle = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return stationGroups;
        }
        final List<LineGroup> filtered = new ArrayList<>();
        for (final LineGroup group : stationGroups) {
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

    private float stationTotalHeight(List<LineGroup> visible) {
        float total = 0F;
        for (final LineGroup group : visible) {
            total += 22F + group.stations().size() * 24F;
        }
        return total;
    }

    private void renderCardMenu(GuiGraphics guiGraphics, float mx, float my) {
        centred(guiGraphics, Component.translatable("gui.trdm.terminal.choose_card"),
                DESIGN_W / 2F, 84F, FONT_SECTION, TicketUi.TEXT_DARK);
        TicketUi.card(this, guiGraphics, 250F, 170F, 200F, 160F, ICON_CARD, 0xFF1E6FD9,
                Component.translatable("gui.trdm.terminal.recharge"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 510F, 170F, 200F, 160F, ICON_CARD, 0xFF9A5A12,
                Component.translatable("gui.trdm.terminal.records"), Component.empty(), mx, my);
    }

    private void renderRecharge(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.translatable("gui.trdm.terminal.recharge_title"),
                TicketUi.PANEL_X + 20F, 78F, FONT_SECTION, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.total", parseAmount()),
                TicketUi.PANEL_X + 20F, 120F, FONT_ROW, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.pay_emerald", parseAmount()),
                TicketUi.PANEL_X + 20F, 144F, FONT_ROW, TicketUi.TEXT_DARK);
        final Component rechargePay = Component.translatable("gui.trdm.terminal.pay_emerald", parseAmount());
        TicketUi.emerald(this, guiGraphics,
                TicketUi.PANEL_X + 20F + textWidth(rechargePay, FONT_ROW) + 6F, 143F, 14F);
        final ItemStack held = heldItem();
        if (held.is(Trdm.TRANSIT_CARD_ITEM)) {
            final long balance = TicketData.hasData(held) ? TicketData.balance(held) : 0L;
            text(guiGraphics, Component.translatable("gui.trdm.terminal.card_balance",
                            FareMoney.yuan(balance)),
                    TicketUi.PANEL_X + 20F, 172F, FONT_ROW, TicketUi.TEXT_DARK);
        }
        TicketUi.button(this, guiGraphics, TicketUi.PANEL_X + 20F, 400F, 150F, 28F,
                Component.translatable("gui.trdm.terminal.confirm"),
                hit(mx, my, TicketUi.PANEL_X + 20F, 400F, 150F, 28F), FONT_ROW);

        final float keypadX = 552F;
        text(guiGraphics, Component.translatable("gui.trdm.terminal.enter_amount"),
                keypadX, 282F, FONT_ROW, TicketUi.TEXT_DARK);
        rect(guiGraphics, keypadX, 302F, 190F, 24F, TicketUi.BORDER);
        rect(guiGraphics, keypadX + 1F, 303F, 188F, 22F, 0xFFFFFFFF);
        text(guiGraphics, Component.literal(amountInput.isEmpty() ? "¥" : "¥" + amountInput),
                keypadX + 8F, 308F, FONT_ROW, TicketUi.TEXT_DARK);
        TicketUi.keypad(this, guiGraphics, keypadX, 334F, mx, my);
    }

    private void renderRecords(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.translatable("gui.trdm.terminal.records_title"),
                TicketUi.PANEL_X + 20F, 78F, FONT_SECTION, TicketUi.TEXT_DARK);
        rect(guiGraphics, TicketUi.PANEL_X + 12F, 104F, TicketUi.PANEL_W - 24F, 344F, TicketUi.BORDER);
        rect(guiGraphics, TicketUi.PANEL_X + 13F, 105F, TicketUi.PANEL_W - 26F, 342F, 0xFFFFFFFF);
        rect(guiGraphics, TicketUi.PANEL_X + 13F, 105F, TicketUi.PANEL_W - 26F, 26F, TicketUi.ROW_HEADER);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.col_time"),
                TicketUi.PANEL_X + 24F, 112F, FONT_SUB, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.col_reason"),
                TicketUi.PANEL_X + 250F, 112F, FONT_SUB, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.col_change"),
                TicketUi.PANEL_X + 400F, 112F, FONT_SUB, TicketUi.TEXT_DARK);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.col_balance"),
                TicketUi.PANEL_X + 540F, 112F, FONT_SUB, TicketUi.TEXT_DARK);

        final List<String[]> rows = recordRows();
        for (int index = 0; index < rows.size(); index++) {
            final String[] row = rows.get(index);
            final float y = 140F + index * 26F;
            if (index > 0) {
                rect(guiGraphics, TicketUi.PANEL_X + 14F, y - 5F, TicketUi.PANEL_W - 28F, 1F, 0xFFDDDDDD);
            }
            text(guiGraphics, Component.literal(row[0]), TicketUi.PANEL_X + 24F, y,
                    FONT_SUB, TicketUi.TEXT_DARK);
            text(guiGraphics, Component.literal(row[1]), TicketUi.PANEL_X + 250F, y,
                    FONT_SUB, TicketUi.TEXT_DARK);
            text(guiGraphics, Component.literal(row[2]), TicketUi.PANEL_X + 400F, y,
                    FONT_SUB, TicketUi.TEXT_DARK);
            text(guiGraphics, Component.literal(row[3]), TicketUi.PANEL_X + 540F, y,
                    FONT_SUB, TicketUi.TEXT_DARK);
        }
        if (rows.isEmpty()) {
            text(guiGraphics, Component.translatable("gui.trdm.terminal.records.empty"),
                    TicketUi.PANEL_X + 24F, 146F, FONT_SUB, TicketUi.TEXT_MUTED);
        }
        text(guiGraphics, Component.translatable("gui.trdm.terminal.records.hint"),
                TicketUi.PANEL_X + 24F, 424F, FONT_SUB, TicketUi.TEXT_MUTED);
    }

    /** 从主手交通卡里读最近 10 条交易记录。 */
    private List<String[]> recordRows() {
        final List<String[]> rows = new ArrayList<>();
        final ItemStack held = heldItem();
        if (!held.is(Trdm.TRANSIT_CARD_ITEM) || !TicketData.hasData(held)) {
            return rows;
        }
        final int count = Math.min(10, TicketData.txCount(held));
        for (int index = 0; index < count; index++) {
            final TicketData.Tx tx = TicketData.txAt(held, index);
            if (tx == null) {
                continue;
            }
            final String reason = Component.translatable("topup".equals(tx.type())
                    ? "gui.trdm.terminal.reason.topup" : "gui.trdm.terminal.reason.fare").getString();
            final String change = (tx.amountCents() < 0 ? "- ¥" : "+ ¥")
                    + FareMoney.text(Math.abs(tx.amountCents()));
            rows.add(new String[]{formatTime(tx.time()), reason, change,
                    FareMoney.yuan(tx.balanceAfter())});
        }
        return rows;
    }

    private static String formatTime(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private static ItemStack heldItem() {
        final net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
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
                if (view == VIEW_RECHARGE) {
                    amountInput = "";
                }
            }
            return true;
        }

        if (view != VIEW_HOME) {
            final float x = TicketUi.PANEL_X + TicketUi.PANEL_W - BACK_W - 10F;
            if (hit(mx, my, x, TicketUi.PANEL_Y + 5F, BACK_W, BACK_H)) {
                back();
                return true;
            }
        }

        return switch (view) {
            case VIEW_REPAIR -> clickRepair(mx, my);
            case VIEW_INFO -> clickInfo(mx, my);
            case VIEW_CARD -> clickCardMenu(mx, my);
            case VIEW_RECHARGE -> clickRecharge(mx, my);
            case VIEW_RECORDS -> false;
            case VIEW_RECORD_TYPE -> clickRecordType(mx, my);
            case VIEW_STATION -> clickStation(mx, my);
            default -> clickHome(mx, my);
        };
    }

    private boolean clickHome(float mx, float my) {
        final ItemStack held = heldItem();
        final boolean pending = hasPendingJourney(held);
        final boolean low = hasLowBalance(held);
        float rowY = 368F;
        if (pending) {
            final float bx = 140F + 680F - 108F;
            if (hit(mx, my, bx, rowY - 3F, 96F, 24F)) {
                openRecordType();
                return true;
            }
            rowY += 36F;
        }
        if (low) {
            final float bx = 140F + 680F - 108F;
            if (hit(mx, my, bx, rowY - 3F, 96F, 24F)) {
                view = VIEW_RECHARGE;
                amountInput = "";
                return true;
            }
        }
        if (hit(mx, my, 140F, 150F, 200F, 180F)) {
            view = VIEW_REPAIR;
            return true;
        }
        if (hit(mx, my, 380F, 150F, 200F, 180F)) {
            view = VIEW_CARD;
            return true;
        }
        if (hit(mx, my, 620F, 150F, 200F, 180F)) {
            Minecraft.getInstance().setScreen(new TicketVendingScreen(this, stationName, stationId, pos));
            return true;
        }
        return false;
    }

    private boolean clickRepair(float mx, float my) {
        if (hit(mx, my, 160F, 160F, 190F, 160F)) {
            openFarePreview();
            return true;
        }
        if (hit(mx, my, 385F, 160F, 190F, 160F)) {
            openRecordType();
            return true;
        }
        if (hit(mx, my, 610F, 160F, 190F, 160F)) {
            openNoTicketPreview();
            return true;
        }
        return false;
    }

    private void showMessage(Component message) {
        dialogMessage = message;
        dialog = "ok";
    }

    @Override
    public void showTicketResult(boolean success, Component message) {
        purchasePending = false;
        showMessage(message);
        if (success) {
            view = VIEW_HOME;
        }
    }

    // ===== 补票 / 补记录确认界面 =====

    private void openFarePreview() {
        final ItemStack held = heldItem();
        if (held.is(Trdm.VARIABLE_TICKET_ITEM)) {
            if (!TicketData.isPass(held)) {
                showMessage(Component.translatable("gui.trdm.ticket.error.invalid_ticket"));
            } else {
                showMessage(Component.translatable("gui.trdm.ticket.fare.nothing_due"));
            }
            return;
        }
        if (!held.is(Trdm.SINGLE_TICKET_ITEM) && !held.is(Trdm.TRANSIT_CARD_ITEM)) {
            showMessage(Component.translatable("gui.trdm.ticket.error.no_item"));
            return;
        }
        if (!TicketData.hasData(held) || TicketData.state(held) != TicketData.State.ENTERED
                || !TicketData.hasEntry(held)) {
            showMessage(Component.translatable("gui.trdm.ticket.error.no_entry"));
            return;
        }
        final boolean card = held.is(Trdm.TRANSIT_CARD_ITEM);
        final boolean continuous = card && TicketData.journeyContinuing(held);
        final String entryDimension = continuous
                ? TicketData.journeyEntryDimension(held) : TicketData.entryDimension(held);
        if (!entryDimension.equals(FareRuntime.dimensionId())) {
            showMessage(Component.translatable("gui.trdm.ticket.error.cross_dimension"));
            return;
        }
        final long origin = continuous
                ? TicketData.journeyEntryStation(held) : TicketData.entryStation(held);
        final long paid = continuous ? TicketData.journeyPaid(held) : 0L;
        final long fare = FareRuntime.get().quote(origin, stationId).totalCents();
        final long value = card ? TicketData.balance(held) : TicketData.faceValue(held);
        final long due = Math.max(0L, fare - paid - value);
        infoAction = ACTION_PAY_FARE;
        infoStationId = origin;
        infoTitle = Component.translatable("gui.trdm.terminal.fare").getString();
        infoLines.clear();
        final String soldName = TicketData.soldName(held);
        if (!soldName.isEmpty()) {
            infoLines.add(Component.translatable("gui.trdm.terminal.sold_at", soldName).getString());
        }
        infoLines.add(Component.translatable("gui.trdm.terminal.entry",
                TicketData.entryName(held)).getString());
        infoLines.add(card
                ? Component.translatable("gui.trdm.terminal.card_balance", FareMoney.yuan(value)).getString()
                : Component.translatable("gui.trdm.terminal.ticket_amount", FareMoney.text(value)).getString());
        if (paid > 0L) {
            infoLines.add(Component.translatable("gui.trdm.terminal.paid_line",
                    FareMoney.text(paid)).getString());
        }
        infoLines.add(Component.translatable("gui.trdm.terminal.fare_due",
                FareMoney.text(fare)).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.extra",
                FareMoney.text(due)).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.total_line",
                FareMoney.text(due)).getString());
        infoAmountCents = due;
        infoNeedsPayment = due > 0L;
        infoFromRecord = false;
        view = VIEW_INFO;
    }

    private void openRecordType() {
        final ItemStack held = heldItem();
        if (!held.is(Trdm.SINGLE_TICKET_ITEM) && !held.is(Trdm.TRANSIT_CARD_ITEM)
                && !held.is(Trdm.VARIABLE_TICKET_ITEM)) {
            showMessage(Component.translatable("gui.trdm.ticket.error.no_item"));
            return;
        }
        if (held.is(Trdm.VARIABLE_TICKET_ITEM) && !TicketData.isPass(held)) {
            showMessage(Component.translatable("gui.trdm.ticket.error.invalid_ticket"));
            return;
        }
        view = VIEW_RECORD_TYPE;
    }

    private boolean clickRecordType(float mx, float my) {
        if (hit(mx, my, 250F, 170F, 200F, 160F)) {
            final ItemStack held = heldItem();
            if (held.is(Trdm.SINGLE_TICKET_ITEM)) {
                if (!TicketData.hasData(held) || !TicketData.TYPE_SINGLE.equals(TicketData.type(held))) {
                    showMessage(Component.translatable("gui.trdm.ticket.error.invalid_ticket"));
                    return true;
                }
            } else if (held.is(Trdm.TRANSIT_CARD_ITEM)) {
                // 交通卡可以直接补入站记录
            } else if (held.is(Trdm.VARIABLE_TICKET_ITEM)) {
                if (!TicketData.isPass(held)) {
                    showMessage(Component.translatable("gui.trdm.ticket.error.invalid_ticket"));
                    return true;
                }
                if (TicketData.passActive(held)
                        && (PassValidity.expired(TicketData.passUntil(held), System.currentTimeMillis())
                        || !passOwnedByPlayer(held))) {
                    showMessage(Component.translatable("gui.trdm.ticket.error.invalid_ticket"));
                    return true;
                }
            } else {
                showMessage(Component.translatable("gui.trdm.ticket.error.no_item"));
                return true;
            }
            if (TicketData.state(held) == TicketData.State.ENTERED) {
                showMessage(Component.translatable("gui.trdm.ticket.error.already_entry"));
                return true;
            }
            openStationPicker(RECORD_ENTRY);
            return true;
        }
        if (hit(mx, my, 510F, 170F, 200F, 160F)) {
            final ItemStack held = heldItem();
            if (held.is(Trdm.SINGLE_TICKET_ITEM) || held.is(Trdm.TRANSIT_CARD_ITEM)) {
                if (!TicketData.hasData(held) || TicketData.state(held) != TicketData.State.ENTERED
                        || !TicketData.hasEntry(held)) {
                    showMessage(Component.translatable("gui.trdm.ticket.error.no_entry"));
                    return true;
                }
                final boolean continuous = held.is(Trdm.TRANSIT_CARD_ITEM) && TicketData.journeyContinuing(held);
                final String entryDimension = continuous
                        ? TicketData.journeyEntryDimension(held) : TicketData.entryDimension(held);
                if (!entryDimension.equals(FareRuntime.dimensionId())) {
                    showMessage(Component.translatable("gui.trdm.ticket.error.cross_dimension"));
                    return true;
                }
            } else if (held.is(Trdm.VARIABLE_TICKET_ITEM)) {
                if (!TicketData.isPass(held)) {
                    showMessage(Component.translatable("gui.trdm.ticket.error.invalid_ticket"));
                    return true;
                }
                if (!TicketData.passActive(held)) {
                    showMessage(Component.translatable("gui.trdm.ticket.error.no_entry"));
                    return true;
                }
                if (PassValidity.expired(TicketData.passUntil(held), System.currentTimeMillis())
                        || !passOwnedByPlayer(held)) {
                    showMessage(Component.translatable("gui.trdm.ticket.error.invalid_ticket"));
                    return true;
                }
                if (TicketData.state(held) != TicketData.State.ENTERED || !TicketData.hasEntry(held)) {
                    showMessage(Component.translatable("gui.trdm.ticket.error.no_entry"));
                    return true;
                }
                if (!TicketData.entryDimension(held).equals(FareRuntime.dimensionId())) {
                    showMessage(Component.translatable("gui.trdm.ticket.error.cross_dimension"));
                    return true;
                }
            } else {
                showMessage(Component.translatable("gui.trdm.ticket.error.no_item"));
                return true;
            }
            openStationPicker(RECORD_EXIT);
            return true;
        }
        return false;
    }

    private void openStationPicker(int mode) {
        recordMode = mode;
        view = VIEW_STATION;
        stationQuery = "";
        stationScroll = 0F;
        final ItemStack held = heldItem();
        final boolean fares = mode == RECORD_EXIT && !isPassItem(held);
        repriceStationGroups(fares ? recordOrigin(held) : 0L, fares);
    }

    private boolean showsStationFares() {
        return recordMode == RECORD_EXIT && !isPassItem(heldItem());
    }

    private static boolean isPassItem(ItemStack stack) {
        return stack.is(Trdm.VARIABLE_TICKET_ITEM) && TicketData.isPass(stack);
    }

    private static boolean passOwnedByPlayer(ItemStack stack) {
        final String owner = TicketData.passOwner(stack);
        final net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        return owner.isEmpty() || (player != null && owner.equals(player.getUUID().toString()));
    }

    /** 连续计费时从原始入闸站算票价，否则从本次入闸站算。 */
    private static long recordOrigin(ItemStack held) {
        if (held.is(Trdm.TRANSIT_CARD_ITEM) && TicketData.journeyContinuing(held)) {
            return TicketData.journeyEntryStation(held);
        }
        return TicketData.entryStation(held);
    }

    /** 按“入闸站 → 该站”的票价重算列表；补入站记录不需要票价。 */
    private void repriceStationGroups(long fromStationId, boolean withFares) {
        final FareRuntime fares = FareRuntime.get();
        final List<LineGroup> repriced = new ArrayList<>();
        for (final LineGroup group : stationGroups) {
            final List<StationRow> rows = new ArrayList<>();
            for (final StationRow row : group.stations()) {
                final long fare = withFares && fromStationId > 0L
                        ? fares.quote(fromStationId, row.id()).totalCents() : 0L;
                rows.add(new StationRow(row.id(), row.name(), fare));
            }
            repriced.add(new LineGroup(group.name(), rows));
        }
        stationGroups.clear();
        stationGroups.addAll(repriced);
    }

    private boolean clickStation(float mx, float my) {
        if (hit(mx, my, TicketUi.PANEL_X + 20F, 98F, TicketUi.PANEL_W - 40F, 22F)) {
            beginField(FIELD_STATION, TicketUi.PANEL_X + 20F, 98F, TicketUi.PANEL_W - 40F, 22F,
                    stationQuery, 64, FONT_ROW, value -> {
                        stationQuery = value == null ? "" : value;
                        clampStationScroll();
                    });
            return true;
        }
        final float listTop = 154F;
        final float listBottom = 470F;
        if (my < listTop || my >= listBottom) {
            return false;
        }
        float top = listTop - stationScroll;
        for (final LineGroup group : visibleStationGroups()) {
            top += 22F;
            for (final StationRow row : group.stations()) {
                if (my >= top && my < top + 24F) {
                    recordStationId = row.id();
                    recordStation = row.name();
                    if (recordMode == RECORD_ENTRY) {
                        showEntryRecordPreview(row);
                    } else {
                        showExitRecordPreview(row);
                    }
                    return true;
                }
                top += 24F;
            }
        }
        return false;
    }

    private void showEntryRecordPreview(StationRow row) {
        infoAction = ACTION_RECORD_ENTRY;
        infoStationId = row.id();
        infoTitle = Component.translatable("gui.trdm.terminal.entry_record").getString();
        infoLines.clear();
        infoLines.add(Component.translatable("gui.trdm.terminal.entry", row.name()).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.total_line",
                FareMoney.text(0L)).getString());
        infoAmountCents = 0L;
        infoNeedsPayment = false;
        infoFromRecord = true;
        view = VIEW_INFO;
    }

    private void showExitRecordPreview(StationRow row) {
        final ItemStack held = heldItem();
        if (isPassItem(held)) {
            infoAction = ACTION_RECORD_EXIT;
            infoStationId = row.id();
            infoTitle = Component.translatable("gui.trdm.terminal.exit_record").getString();
            infoLines.clear();
            infoLines.add(Component.translatable("gui.trdm.terminal.entry",
                    TicketData.entryName(held)).getString());
            infoLines.add(Component.translatable("gui.trdm.terminal.exit_station", row.name()).getString());
            infoLines.add(Component.translatable("gui.trdm.terminal.total_line",
                    FareMoney.text(0L)).getString());
            infoAmountCents = 0L;
            infoNeedsPayment = false;
            infoFromRecord = true;
            view = VIEW_INFO;
            return;
        }
        if (!held.is(Trdm.SINGLE_TICKET_ITEM) && !held.is(Trdm.TRANSIT_CARD_ITEM)) {
            showMessage(Component.translatable("gui.trdm.ticket.error.no_item"));
            return;
        }
        if (!TicketData.hasData(held) || !TicketData.hasEntry(held)) {
            showMessage(Component.translatable("gui.trdm.ticket.error.no_entry"));
            return;
        }
        final boolean card = held.is(Trdm.TRANSIT_CARD_ITEM);
        final boolean continuous = card && TicketData.journeyContinuing(held);
        final long paid = continuous ? TicketData.journeyPaid(held) : 0L;
        final long fare = row.fareCents();
        final long value = card ? TicketData.balance(held) : TicketData.faceValue(held);
        final long due = Math.max(0L, fare - paid - value);
        infoAction = ACTION_RECORD_EXIT;
        infoStationId = row.id();
        infoTitle = Component.translatable("gui.trdm.terminal.exit_record").getString();
        infoLines.clear();
        infoLines.add(Component.translatable("gui.trdm.terminal.entry",
                TicketData.entryName(held)).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.exit_station", row.name()).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.fare_line",
                FareMoney.text(fare)).getString());
        infoLines.add(card
                ? Component.translatable("gui.trdm.terminal.card_balance", FareMoney.yuan(value)).getString()
                : Component.translatable("gui.trdm.terminal.ticket_amount", FareMoney.text(value)).getString());
        if (paid > 0L) {
            infoLines.add(Component.translatable("gui.trdm.terminal.paid_line",
                    FareMoney.text(paid)).getString());
        }
        infoLines.add(Component.translatable("gui.trdm.terminal.extra",
                FareMoney.text(due)).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.total_line",
                FareMoney.text(due)).getString());
        infoAmountCents = due;
        infoNeedsPayment = due > 0L;
        infoFromRecord = true;
        view = VIEW_INFO;
    }

    private void openNoTicketPreview() {
        final long cap = FareRuntime.get().config().maxFareCapCents();
        if (cap <= 0L) {
            showMessage(Component.translatable("gui.trdm.terminal.no_fare_rule"));
            return;
        }
        infoAction = ACTION_EXIT_TICKET;
        infoStationId = stationId;
        infoTitle = Component.translatable("gui.trdm.terminal.no_ticket").getString();
        infoLines.clear();
        infoLines.add(Component.translatable("gui.trdm.terminal.no_ticket_amount",
                FareMoney.text(cap)).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.total_line",
                FareMoney.text(cap)).getString());
        infoAmountCents = cap;
        infoNeedsPayment = true;
        infoFromRecord = false;
        view = VIEW_INFO;
    }

    private void clampStationScroll() {
        final float total = stationTotalHeight(visibleStationGroups());
        stationScroll = Math.max(0F, Math.min(stationScroll, Math.max(0F, total - (470F - 154F))));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        final float my = designY(mouseY);
        if (view == VIEW_STATION && my >= 154F && my < 470F) {
            stationScroll -= (float) delta * 24F;
            clampStationScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private boolean clickInfo(float mx, float my) {
        if (hit(mx, my, TicketUi.PANEL_X + 20F, 400F, 150F, 28F)) {
            if (purchasePending) {
                return true;
            }
            purchasePending = true;
            switch (infoAction) {
                case ACTION_PAY_FARE -> TicketClientNetwork.payFare(pos);
                case ACTION_RECORD_ENTRY -> TicketClientNetwork.recordEntry(pos, infoStationId);
                case ACTION_RECORD_EXIT -> TicketClientNetwork.recordExit(pos, infoStationId);
                case ACTION_EXIT_TICKET -> TicketClientNetwork.buyExitTicket(pos);
                default -> purchasePending = false;
            }
            return true;
        }
        if (hit(mx, my, TicketUi.PANEL_X + 184F, 400F, 100F, 28F)) {
            view = infoFromRecord ? VIEW_STATION : VIEW_REPAIR;
            return true;
        }
        return false;
    }

    private boolean clickCardMenu(float mx, float my) {
        if (hit(mx, my, 250F, 170F, 200F, 160F)) {
            view = VIEW_RECHARGE;
            amountInput = "";
            return true;
        }
        if (hit(mx, my, 510F, 170F, 200F, 160F)) {
            view = VIEW_RECORDS;
            return true;
        }
        return false;
    }

    private boolean clickRecharge(float mx, float my) {
        final String key = TicketUi.keypadHit(552F, 334F, mx, my);
        if (key != null) {
            if ("<".equals(key)) {
                if (!amountInput.isEmpty()) {
                    amountInput = amountInput.substring(0, amountInput.length() - 1);
                }
            } else if (!"OK".equals(key) && amountInput.length() < 3) {
                amountInput = amountInput.equals("0") ? key : amountInput + key;
            }
            return true;
        }
        if (hit(mx, my, TicketUi.PANEL_X + 20F, 400F, 150F, 28F)) {
            final int amount = parseAmount();
            if (amount <= 0) {
                showMessage(Component.translatable("gui.trdm.terminal.enter_amount_hint"));
            } else if (!purchasePending) {
                purchasePending = true;
                TicketClientNetwork.topUp(pos, amount);
            }
            return true;
        }
        return false;
    }

    private int parseAmount() {
        if (amountInput.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(999, Integer.parseInt(amountInput)));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void back() {
        view = switch (view) {
            case VIEW_INFO -> infoFromRecord ? VIEW_STATION : VIEW_REPAIR;
            case VIEW_STATION -> VIEW_RECORD_TYPE;
            case VIEW_RECORD_TYPE -> VIEW_REPAIR;
            case VIEW_RECHARGE, VIEW_RECORDS -> VIEW_CARD;
            default -> VIEW_HOME;
        };
    }

    private boolean inPanel(float mx, float my) {
        return mx >= TicketUi.PANEL_X && mx < TicketUi.PANEL_X + TicketUi.PANEL_W
                && my >= TicketUi.PANEL_Y && my < TicketUi.PANEL_Y + TicketUi.PANEL_H;
    }

    private record LineGroup(String name, List<StationRow> stations) {
    }

    private record StationRow(long id, String name, long fareCents) {
    }
}
