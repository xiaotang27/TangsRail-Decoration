package top.xiaotang27.trdm.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 客服终端界面：补票/补记录、交通卡充值/记录查询、售票售卡。目前是演示数据。 */
public class CustomerServiceTerminalScreen extends TrdmScreenBase {

    private static final int VIEW_HOME = 0;
    private static final int VIEW_REPAIR = 1;
    private static final int VIEW_INFO = 2;
    private static final int VIEW_CARD = 3;
    private static final int VIEW_RECHARGE = 4;
    private static final int VIEW_RECORDS = 5;

    private static final float BACK_W = 84F;
    private static final float BACK_H = 26F;

    private final String stationName;
    private final BlockPos pos;

    private int view = VIEW_HOME;
    private String amountInput = "";
    private String dialog;
    private String infoTitle = "";
    private final List<String> infoLines = new ArrayList<>();

    public CustomerServiceTerminalScreen(String stationName, BlockPos pos) {
        super(null, Component.translatable("block.trdm.customer_service_terminal"));
        this.stationName = stationName == null ? "" : stationName;
        this.pos = pos;
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
            default -> renderHome(guiGraphics, mx, my);
        }
        if (dialog != null) {
            TicketUi.dialog(this, guiGraphics, Component.translatable("gui.trdm.ticket.demo_success"), mx, my);
        }
    }

    private void renderHeader(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.translatable("gui.trdm.terminal.station", stationName),
                TicketUi.PANEL_X + 12F, TicketUi.PANEL_Y + 10F, FONT_ROW, COLOR_TEXT);
        if (view != VIEW_HOME) {
            final float x = TicketUi.PANEL_X + TicketUi.PANEL_W - BACK_W - 10F;
            buttonText(guiGraphics, x, TicketUi.PANEL_Y + 4F, BACK_W, BACK_H,
                    Component.translatable("gui.trdm.ticket.back"), false,
                    hit(mx, my, x, TicketUi.PANEL_Y + 4F, BACK_W, BACK_H), FONT_ROW);
        }
    }

    private void renderHome(GuiGraphics guiGraphics, float mx, float my) {
        centred(guiGraphics, Component.translatable("gui.trdm.terminal.choose"),
                DESIGN_W / 2F, 84F, FONT_SECTION, COLOR_TEXT);
        TicketUi.card(this, guiGraphics, 120F, 150F, 200F, 180F, 0xFF1E6FD9,
                Component.translatable("gui.trdm.terminal.repair"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 360F, 150F, 200F, 180F, 0xFF0A7A1E,
                Component.translatable("gui.trdm.terminal.card"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 600F, 150F, 200F, 180F, 0xFF9A5A12,
                Component.translatable("gui.trdm.terminal.sales"),
                Component.translatable("gui.trdm.terminal.sales.sub"), mx, my);
    }

    private void renderRepair(GuiGraphics guiGraphics, float mx, float my) {
        centred(guiGraphics, Component.translatable("gui.trdm.terminal.choose_repair"),
                DESIGN_W / 2F, 84F, FONT_SECTION, COLOR_TEXT);
        TicketUi.card(this, guiGraphics, 140F, 160F, 190F, 160F, 0xFF1E6FD9,
                Component.translatable("gui.trdm.terminal.fare"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 365F, 160F, 190F, 160F, 0xFF0A7A1E,
                Component.translatable("gui.trdm.terminal.record"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 590F, 160F, 190F, 160F, 0xFF9A5A12,
                Component.translatable("gui.trdm.terminal.no_ticket"),
                Component.translatable("gui.trdm.terminal.no_ticket.sub"), mx, my);
    }

    private void renderInfo(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.literal(infoTitle), TicketUi.PANEL_X + 20F, 78F,
                FONT_SECTION, COLOR_TEXT);
        for (int index = 0; index < infoLines.size(); index++) {
            text(guiGraphics, Component.literal(infoLines.get(index)), TicketUi.PANEL_X + 20F,
                    120F + index * 22F, FONT_ROW, COLOR_TEXT);
        }
        buttonText(guiGraphics, TicketUi.PANEL_X + 20F, 400F, 150F, 28F,
                Component.translatable("gui.trdm.terminal.pay"), false,
                hit(mx, my, TicketUi.PANEL_X + 20F, 400F, 150F, 28F), FONT_ROW);
        buttonText(guiGraphics, TicketUi.PANEL_X + 184F, 400F, 100F, 28F,
                Component.translatable("gui.trdm.ticket.back"), false,
                hit(mx, my, TicketUi.PANEL_X + 184F, 400F, 100F, 28F), FONT_ROW);
    }

    private void renderCardMenu(GuiGraphics guiGraphics, float mx, float my) {
        centred(guiGraphics, Component.translatable("gui.trdm.terminal.choose_card"),
                DESIGN_W / 2F, 84F, FONT_SECTION, COLOR_TEXT);
        TicketUi.card(this, guiGraphics, 220F, 170F, 200F, 160F, 0xFF1E6FD9,
                Component.translatable("gui.trdm.terminal.recharge"), Component.empty(), mx, my);
        TicketUi.card(this, guiGraphics, 480F, 170F, 200F, 160F, 0xFF9A5A12,
                Component.translatable("gui.trdm.terminal.records"), Component.empty(), mx, my);
    }

    private void renderRecharge(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.translatable("gui.trdm.terminal.recharge_title"),
                TicketUi.PANEL_X + 20F, 78F, FONT_SECTION, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.total", parseAmount()),
                TicketUi.PANEL_X + 20F, 120F, FONT_ROW, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.pay_emerald", parseAmount()),
                TicketUi.PANEL_X + 20F, 144F, FONT_ROW, COLOR_TEXT);
        buttonText(guiGraphics, TicketUi.PANEL_X + 20F, 400F, 150F, 28F,
                Component.translatable("gui.trdm.terminal.confirm"), false,
                hit(mx, my, TicketUi.PANEL_X + 20F, 400F, 150F, 28F), FONT_ROW);

        final float keypadX = 552F;
        text(guiGraphics, Component.translatable("gui.trdm.terminal.enter_amount"),
                keypadX, 254F, FONT_ROW, COLOR_TEXT);
        rect(guiGraphics, keypadX, 274F, 190F, 24F, COLOR_FIELD_BORDER);
        rect(guiGraphics, keypadX + 1F, 275F, 188F, 22F, 0xFF161616);
        text(guiGraphics, Component.literal(amountInput.isEmpty() ? "¥" : "¥" + amountInput),
                keypadX + 8F, 280F, FONT_ROW, COLOR_TEXT);
        TicketUi.keypad(this, guiGraphics, keypadX, 306F, mx, my);
    }

    private void renderRecords(GuiGraphics guiGraphics, float mx, float my) {
        text(guiGraphics, Component.translatable("gui.trdm.terminal.records_title"),
                TicketUi.PANEL_X + 20F, 78F, FONT_SECTION, COLOR_TEXT);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.col_time"),
                TicketUi.PANEL_X + 24F, 120F, FONT_SUB, COLOR_SUB);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.col_reason"),
                TicketUi.PANEL_X + 250F, 120F, FONT_SUB, COLOR_SUB);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.col_change"),
                TicketUi.PANEL_X + 400F, 120F, FONT_SUB, COLOR_SUB);
        text(guiGraphics, Component.translatable("gui.trdm.terminal.col_balance"),
                TicketUi.PANEL_X + 540F, 120F, FONT_SUB, COLOR_SUB);

        final String[][] rows = {
                {"2026-11-01 10:00:00", "gui.trdm.terminal.reason.fare", "- ¥10", "¥100"},
                {"2026-07-21 00:00:00", "gui.trdm.terminal.reason.topup", "+ ¥10", "¥110"},
                {"2026-01-01 00:00:00", "gui.trdm.terminal.reason.initial", "+ ¥100", "¥100"}
        };
        for (int index = 0; index < rows.length; index++) {
            final float y = 146F + index * 24F;
            text(guiGraphics, Component.literal(rows[index][0]), TicketUi.PANEL_X + 24F, y,
                    FONT_SUB, COLOR_TEXT);
            text(guiGraphics, Component.translatable(rows[index][1]), TicketUi.PANEL_X + 250F, y,
                    FONT_SUB, COLOR_TEXT);
            text(guiGraphics, Component.literal(rows[index][2]), TicketUi.PANEL_X + 400F, y,
                    FONT_SUB, COLOR_TEXT);
            text(guiGraphics, Component.literal(rows[index][3]), TicketUi.PANEL_X + 540F, y,
                    FONT_SUB, COLOR_TEXT);
        }
        hint(guiGraphics, "gui.trdm.terminal.records.demo", TicketUi.PANEL_X + 24F, 240F);
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
            if (hit(mx, my, x, TicketUi.PANEL_Y + 4F, BACK_W, BACK_H)) {
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
            default -> clickHome(mx, my);
        };
    }

    private boolean clickHome(float mx, float my) {
        if (hit(mx, my, 120F, 150F, 200F, 180F)) {
            view = VIEW_REPAIR;
            return true;
        }
        if (hit(mx, my, 360F, 150F, 200F, 180F)) {
            view = VIEW_CARD;
            return true;
        }
        if (hit(mx, my, 600F, 150F, 200F, 180F)) {
            Minecraft.getInstance().setScreen(new TicketVendingScreen(this, stationName, pos));
            return true;
        }
        return false;
    }

    private boolean clickRepair(float mx, float my) {
        if (hit(mx, my, 140F, 160F, 190F, 160F)) {
            showFareInfo();
            return true;
        }
        if (hit(mx, my, 365F, 160F, 190F, 160F)) {
            showRecordInfo();
            return true;
        }
        if (hit(mx, my, 590F, 160F, 190F, 160F)) {
            showNoTicketInfo();
            return true;
        }
        return false;
    }

    private void showFareInfo() {
        infoTitle = Component.translatable("gui.trdm.terminal.fare").getString();
        infoLines.clear();
        infoLines.add(Component.translatable("gui.trdm.terminal.sold_at", "刘园|LIYUAN").getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.ticket_amount", 2).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.fare_due", 3).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.extra", 1).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.total_line", 1).getString());
        view = VIEW_INFO;
    }

    private void showRecordInfo() {
        infoTitle = Component.translatable("gui.trdm.terminal.record").getString();
        infoLines.clear();
        infoLines.add(Component.translatable("gui.trdm.terminal.entry", "刘园|LIYUAN").getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.fare_due", 3).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.total_line", 3).getString());
        view = VIEW_INFO;
    }

    private void showNoTicketInfo() {
        infoTitle = Component.translatable("gui.trdm.terminal.no_ticket").getString();
        infoLines.clear();
        infoLines.add(Component.translatable("gui.trdm.terminal.no_ticket_amount", 10).getString());
        infoLines.add(Component.translatable("gui.trdm.terminal.total_line", 10).getString());
        view = VIEW_INFO;
    }

    private boolean clickInfo(float mx, float my) {
        if (hit(mx, my, TicketUi.PANEL_X + 20F, 400F, 150F, 28F)) {
            dialog = "ok";
            return true;
        }
        if (hit(mx, my, TicketUi.PANEL_X + 184F, 400F, 100F, 28F)) {
            view = VIEW_REPAIR;
            return true;
        }
        return false;
    }

    private boolean clickCardMenu(float mx, float my) {
        if (hit(mx, my, 220F, 170F, 200F, 160F)) {
            view = VIEW_RECHARGE;
            amountInput = "";
            return true;
        }
        if (hit(mx, my, 480F, 170F, 200F, 160F)) {
            view = VIEW_RECORDS;
            return true;
        }
        return false;
    }

    private boolean clickRecharge(float mx, float my) {
        final String key = TicketUi.keypadHit(552F, 306F, mx, my);
        if (key != null) {
            if ("<".equals(key)) {
                if (!amountInput.isEmpty()) {
                    amountInput = amountInput.substring(0, amountInput.length() - 1);
                }
            } else if (!"OK".equals(key) && amountInput.length() < 4) {
                amountInput = amountInput.equals("0") ? key : amountInput + key;
            }
            return true;
        }
        if (hit(mx, my, TicketUi.PANEL_X + 20F, 400F, 150F, 28F)) {
            dialog = "ok";
            return true;
        }
        return false;
    }

    private int parseAmount() {
        if (amountInput.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(9999, Integer.parseInt(amountInput)));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void back() {
        view = switch (view) {
            case VIEW_INFO -> VIEW_REPAIR;
            case VIEW_RECHARGE, VIEW_RECORDS -> VIEW_CARD;
            default -> VIEW_HOME;
        };
    }

    private boolean inPanel(float mx, float my) {
        return mx >= TicketUi.PANEL_X && mx < TicketUi.PANEL_X + TicketUi.PANEL_W
                && my >= TicketUi.PANEL_Y && my < TicketUi.PANEL_Y + TicketUi.PANEL_H;
    }
}
