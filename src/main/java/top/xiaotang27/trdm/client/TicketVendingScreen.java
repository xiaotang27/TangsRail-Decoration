package top.xiaotang27.trdm.client;

import mtr.client.ClientData;
import mtr.data.Route;
import mtr.data.Station;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 售票机界面：单程票 / 通用票 / 交通卡三个页签，目前是演示数据。 */
public class TicketVendingScreen extends TrdmScreenBase {

    private static final int TAB_SINGLE = 0;
    private static final int TAB_PASS = 1;
    private static final int TAB_CARD = 2;

    private static final float TAB_W = 132F;
    private static final float TAB_H = 26F;
    private static final float TAB_Y = 20F;

    private static final float LIST_X = 104F;
    private static final float LIST_W = 430F;
    private static final float LIST_TOP = 126F;
    private static final float LIST_BOTTOM = 478F;
    private static final float ROW_H = 24F;

    private static final float RIGHT_X = 552F;
    private static final float RIGHT_W = 300F;

    private static final Object FIELD_SEARCH = new Object();

    private static final String[] SAMPLE_STATIONS = {
            "刘园|LIUYUAN", "天津站|TIANJIN", "双桥河|SHUANGQIAOHE", "车站|CHEZHAN", "名字|MINGZI"
    };

    private static final PassRow[] PASSES = {
            new PassRow("一日票", 10),
            new PassRow("三日票", 25),
            new PassRow("青春18券", 50),
            new PassRow("未命名车票", 2)
    };

    private final String stationName;
    private final BlockPos pos;
    private final List<StationRow> stations = new ArrayList<>();

    private int tab = TAB_SINGLE;
    private String query = "";
    private String lastQuery = "";
    private float listScroll;
    private int selectedStation = -1;
    private int selectedLine = -1;

    private String priceInput = "";
    private boolean transactionOpen;
    private String transactionTitle = "";
    private int unitPrice = 2;
    private int quantity = 1;
    private String dialog;

    public TicketVendingScreen(String stationName, BlockPos pos) {
        this(null, stationName, pos);
    }

    public TicketVendingScreen(Screen parent, String stationName, BlockPos pos) {
        super(parent, Component.translatable("block.trdm.ticket_vending_machine"));
        this.stationName = stationName == null ? "" : stationName;
        this.pos = pos;
        loadStations();
    }

    private void loadStations() {
        final List<Station> all = new ArrayList<>(ClientData.STATIONS);
        all.sort(Comparator.comparing((Station station) -> station.name == null ? "" : station.name,
                String.CASE_INSENSITIVE_ORDER));
        for (int index = 0; index < all.size(); index++) {
            final String name = all.get(index).name;
            stations.add(new StationRow(name == null ? "?" : name, 2 + index % 3));
        }
        if (stations.isEmpty()) {
            for (int index = 0; index < SAMPLE_STATIONS.length; index++) {
                stations.add(new StationRow(SAMPLE_STATIONS[index], 2 + index % 3));
            }
        }
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
            TicketUi.dialog(this, guiGraphics, Component.translatable("gui.trdm.ticket.demo_success"), mx, my);
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
            buttonText(guiGraphics, x, TAB_Y, TAB_W, TAB_H, Component.translatable(keys[index]),
                    tab == index, hit(mx, my, x, TAB_Y, TAB_W, TAB_H), FONT_ROW);
        }
    }

    // ===== 单程票 =====

    private void renderSingle(GuiGraphics guiGraphics, float mx, float my) {
        field(guiGraphics, FIELD_SEARCH, LIST_X, 64F, LIST_W, 22F, FONT_ROW, query);
        if (query.isEmpty() && !fieldActive(FIELD_SEARCH)) {
            text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.search"),
                    LIST_X + 8F, 70F, FONT_ROW, 0xFF808080);
        }
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.all_stations"),
                LIST_X, 98F, FONT_ROW, COLOR_TEXT);

        final List<StationRow> visible = visibleStations();
        if (visible.isEmpty()) {
            hint(guiGraphics, "gui.trdm.ticket_machine.empty", LIST_X, LIST_TOP + 4F);
            return;
        }
        scissorOn(guiGraphics, LIST_X, LIST_TOP, LIST_W, LIST_BOTTOM - LIST_TOP);
        float top = LIST_TOP - listScroll;
        for (int index = 0; index < visible.size(); index++) {
            final StationRow row = visible.get(index);
            if (top + ROW_H >= LIST_TOP && top <= LIST_BOTTOM) {
                final boolean hovered = mx >= LIST_X && mx < LIST_X + LIST_W && my >= top && my < top + ROW_H;
                row(guiGraphics, LIST_X, top, LIST_W, ROW_H - 1F, index == selectedStation, hovered);
                text(guiGraphics, Component.literal(row.name()), LIST_X + 8F, top + 7F, FONT_ROW, COLOR_TEXT);
                textRight(guiGraphics, Component.literal("¥" + row.price()), LIST_X + LIST_W - 10F,
                        top + 7F, FONT_ROW, COLOR_TEXT);
            }
            top += ROW_H;
        }
        scissorOff(guiGraphics);
        scrollbar(guiGraphics, LIST_X + LIST_W - 4F, LIST_TOP, LIST_BOTTOM - LIST_TOP,
                visible.size() * ROW_H, LIST_BOTTOM - LIST_TOP, listScroll);
    }

    private List<StationRow> visibleStations() {
        final String raw = fieldActive(FIELD_SEARCH) ? editingValue() : query;
        final String needle = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return stations;
        }
        final List<StationRow> filtered = new ArrayList<>();
        for (final StationRow row : stations) {
            if (row.name().toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(row);
            }
        }
        return filtered;
    }

    private void renderRightPanel(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.literal(stationName), RIGHT_X, 64F, FONT_SECTION, COLOR_TEXT);
        hint(guiGraphics, "gui.trdm.ticket_machine.in_service", RIGHT_X, 86F);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.choose_line"),
                RIGHT_X, 112F, FONT_ROW, COLOR_TEXT);

        final List<String> lines = lineNames();
        for (int index = 0; index < Math.min(4, lines.size()); index++) {
            final float y = 132F + index * 26F;
            buttonText(guiGraphics, RIGHT_X, y, 190F, 22F, Component.literal(lines.get(index)),
                    selectedLine == index, hit(mx, my, RIGHT_X, y, 190F, 22F), FONT_SUB);
        }

        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.choose_price"),
                RIGHT_X, 254F, FONT_ROW, COLOR_TEXT);
        rect(guiGraphics, RIGHT_X, 274F, 190F, 24F, COLOR_FIELD_BORDER);
        rect(guiGraphics, RIGHT_X + 1F, 275F, 188F, 22F, 0xFF161616);
        text(guiGraphics, Component.literal(priceInput.isEmpty() ? "¥" : "¥" + priceInput),
                RIGHT_X + 8F, 280F, FONT_ROW, COLOR_TEXT);
        TicketUi.keypad(this, guiGraphics, RIGHT_X, 306F, mx, my);
    }

    private List<String> lineNames() {
        final List<String> names = new ArrayList<>();
        final List<Route> routes = new ArrayList<>(ClientData.ROUTES);
        routes.sort(Comparator.comparing((Route route) -> route.name == null ? "" : route.name,
                String.CASE_INSENSITIVE_ORDER));
        for (final Route route : routes) {
            if (route.name != null && !route.name.isBlank()) {
                names.add(route.name);
            }
        }
        if (names.isEmpty()) {
            names.add("1号线|Line 1");
            names.add("2号线|Line 2");
            names.add("3号线|Line 3");
        }
        return names;
    }

    // ===== 通用票 =====

    private void renderPasses(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.all_passes"),
                LIST_X, 64F, FONT_ROW, COLOR_TEXT);
        for (int index = 0; index < PASSES.length; index++) {
            final PassRow pass = PASSES[index];
            final float top = 90F + index * 32F;
            row(guiGraphics, LIST_X, top, LIST_W, 30F, false, hit(mx, my, LIST_X, top, LIST_W, 30F));
            text(guiGraphics, Component.literal(pass.name()), LIST_X + 8F, top + 10F, FONT_ROW, COLOR_TEXT);
            textRight(guiGraphics, Component.literal("¥" + pass.price()), LIST_X + LIST_W - 10F,
                    top + 10F, FONT_ROW, COLOR_TEXT);
        }
        hint(guiGraphics, "gui.trdm.ticket_machine.pass_hint", LIST_X, 236F);
    }

    // ===== 交通卡 =====

    private void renderCard(GuiGraphics guiGraphics, float mx, float my) {
        final int topUp = parsePrice();
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.card_info"),
                LIST_X, 64F, FONT_SECTION, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.card_fee"),
                LIST_X, 104F, FONT_ROW, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.card_topup", topUp),
                LIST_X, 128F, FONT_ROW, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.card_total", 18 + topUp),
                LIST_X, 160F, FONT_ROW, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.pay_emerald", 18 + topUp),
                LIST_X, 184F, FONT_ROW, COLOR_TEXT);
        buttonText(guiGraphics, LIST_X, 400F, 150F, 28F,
                Component.translatable("gui.trdm.ticket_machine.pay"), false,
                hit(mx, my, LIST_X, 400F, 150F, 28F), FONT_ROW);
    }

    private void renderKeypadPanel(GuiGraphics guiGraphics, float mx, float my, String labelKey) {
        text(guiGraphics, Component.translatable(labelKey), RIGHT_X, 254F, FONT_ROW, COLOR_TEXT);
        rect(guiGraphics, RIGHT_X, 274F, 190F, 24F, COLOR_FIELD_BORDER);
        rect(guiGraphics, RIGHT_X + 1F, 275F, 188F, 22F, 0xFF161616);
        text(guiGraphics, Component.literal(priceInput.isEmpty() ? "¥" : "¥" + priceInput),
                RIGHT_X + 8F, 280F, FONT_ROW, COLOR_TEXT);
        TicketUi.keypad(this, guiGraphics, RIGHT_X, 306F, mx, my);
    }

    private void renderStationInfo(GuiGraphics guiGraphics) {
        text(guiGraphics, Component.literal(stationName), RIGHT_X, 64F, FONT_SECTION, COLOR_TEXT);
        hint(guiGraphics, "gui.trdm.ticket_machine.in_service", RIGHT_X, 86F);
    }

    // ===== 交易信息 =====

    private void renderTransaction(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.transaction"),
                LIST_X, 64F, FONT_SECTION, COLOR_TEXT);
        text(guiGraphics, Component.literal(transactionTitle), LIST_X, 104F, FONT_ROW, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.fare_amount", unitPrice),
                LIST_X, 126F, FONT_ROW, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.total", unitPrice * quantity),
                LIST_X, 154F, FONT_ROW, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.pay_emerald", unitPrice * quantity),
                LIST_X, 176F, FONT_ROW, COLOR_TEXT);

        text(guiGraphics, Component.translatable("gui.trdm.ticket_machine.quantity"),
                LIST_X, 222F, FONT_ROW, COLOR_TEXT);
        buttonText(guiGraphics, LIST_X + 70F, 214F, 26F, 24F, Component.literal("-"), false,
                hit(mx, my, LIST_X + 70F, 214F, 26F, 24F), FONT_ROW);
        textCentred(guiGraphics, Component.literal(String.valueOf(quantity)), LIST_X + 100F, 28F,
                220F, FONT_ROW, COLOR_TEXT);
        buttonText(guiGraphics, LIST_X + 132F, 214F, 26F, 24F, Component.literal("+"), false,
                hit(mx, my, LIST_X + 132F, 214F, 26F, 24F), FONT_ROW);

        buttonText(guiGraphics, LIST_X, 400F, 150F, 28F,
                Component.translatable("gui.trdm.ticket_machine.pay"), false,
                hit(mx, my, LIST_X, 400F, 150F, 28F), FONT_ROW);
        buttonText(guiGraphics, LIST_X + 164F, 400F, 100F, 28F,
                Component.translatable("gui.trdm.ticket_machine.back"), false,
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
            if (hit(mx, my, LIST_X + 70F, 214F, 26F, 24F)) {
                quantity = Math.max(1, quantity - 1);
                return true;
            }
            if (hit(mx, my, LIST_X + 132F, 214F, 26F, 24F)) {
                quantity = Math.min(64, quantity + 1);
                return true;
            }
            if (hit(mx, my, LIST_X, 400F, 150F, 28F)) {
                dialog = "ok";
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
                lastQuery = query;
                clampScroll();
            });
            return true;
        }

        final List<StationRow> visible = visibleStations();
        if (my >= LIST_TOP && my < LIST_BOTTOM && mx >= LIST_X && mx < LIST_X + LIST_W) {
            final int index = (int) ((my - LIST_TOP + listScroll) / ROW_H);
            if (index >= 0 && index < visible.size()) {
                selectedStation = index;
                priceInput = String.valueOf(visible.get(index).price());
                return true;
            }
        }

        for (int index = 0; index < Math.min(4, lineNames().size()); index++) {
            if (hit(mx, my, RIGHT_X, 132F + index * 26F, 190F, 22F)) {
                selectedLine = index;
                return true;
            }
        }

        final String key = TicketUi.keypadHit(RIGHT_X, 306F, mx, my);
        if (key != null) {
            handleKey(key);
            return true;
        }
        return false;
    }

    private boolean clickPass(float mx, float my) {
        if (transactionOpen) {
            if (hit(mx, my, LIST_X + 70F, 214F, 26F, 24F)) {
                quantity = Math.max(1, quantity - 1);
                return true;
            }
            if (hit(mx, my, LIST_X + 132F, 214F, 26F, 24F)) {
                quantity = Math.min(64, quantity + 1);
                return true;
            }
            if (hit(mx, my, LIST_X, 400F, 150F, 28F)) {
                dialog = "ok";
                return true;
            }
            if (hit(mx, my, LIST_X + 164F, 400F, 100F, 28F)) {
                transactionOpen = false;
                return true;
            }
            return false;
        }
        for (int index = 0; index < PASSES.length; index++) {
            if (hit(mx, my, LIST_X, 90F + index * 32F, LIST_W, 30F)) {
                transactionTitle = PASSES[index].name();
                unitPrice = PASSES[index].price();
                quantity = 1;
                transactionOpen = true;
                return true;
            }
        }
        return false;
    }

    private boolean clickCard(float mx, float my) {
        final String key = TicketUi.keypadHit(RIGHT_X, 306F, mx, my);
        if (key != null) {
            handleKey(key);
            return true;
        }
        if (hit(mx, my, LIST_X, 400F, 150F, 28F)) {
            dialog = "ok";
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
                if (tab == TAB_SINGLE && !priceInput.isEmpty()) {
                    transactionTitle = Component.translatable("gui.trdm.ticket_machine.by_price").getString();
                    unitPrice = parsePrice();
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

    private void clampScroll() {
        final float total = visibleStations().size() * ROW_H;
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

    private record StationRow(String name, int price) {
    }

    private record PassRow(String name, int price) {
    }
}
