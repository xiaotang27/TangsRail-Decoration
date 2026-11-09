package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.fare.FareRule;
import top.xiaotang27.trdm.fare.script.FareScript;
import top.xiaotang27.trdm.fare.script.ScriptCompiler;
import top.xiaotang27.trdm.fare.script.ScriptNode;
import top.xiaotang27.trdm.fare.script.ScriptTier;
import top.xiaotang27.trdm.fare.script.Scripts;

import java.util.ArrayList;
import java.util.List;

/**
 * 积木规则的可视化编辑器（v1 画布）：
 * 左侧是积木栏，中间是语句链画布，底部是选中块参数。
 * 语句块可以上下拖动排序；值块可以拖到语句块的值槽里。
 */
public final class FareScriptScreen extends Screen {

    private static final String[] STATEMENT_TYPES = {
            "set_fare", "add_fare", "multiply_fare", "cap_fare", "floor_fare", "round_fare"};
    private static final String[] VALUE_TYPES = {
            "number", "context_intervals", "context_meters", "context_stations",
            "context_zone_diff", "table_intervals", "table_meters"};

    private static final int BLOCK_H = 58;
    private static final int BLOCK_GAP = 8;
    private static final int PALETTE_X = 8;
    private static final int PALETTE_W = 154;
    private static final int PALETTE_TOP = 52;
    private static final int PALETTE_ROW = 25;
    private static final int PALETTE_HEADER = 18;
    private static final int CANVAS_LEFT = 174;
    private static final int CANVAS_TOP = 48;
    private static final int POPUP_W = 280;

    private static final int CANVAS_BG = 0xFFF7F7F7;
    private static final int PANEL_BG = 0xFFF2F2F2;
    private static final int PANEL_BORDER = 0xFF9A9A9A;
    private static final int TEXT_DARK = 0xFF202020;
    private static final int TEXT_MUTED = 0xFF6A6A6A;

    private record PaletteEntry(String labelKey, String type, boolean statement, boolean header, int color) {
    }

    private static final List<PaletteEntry> PALETTE = buildPalette();

    private static List<PaletteEntry> buildPalette() {
        final List<PaletteEntry> list = new ArrayList<>();
        list.add(new PaletteEntry("gui.trdm.script.category.fare", null, false, true, 0xFF2F7D4F));
        for (final String type : List.of("set_fare", "add_fare", "multiply_fare", "cap_fare", "floor_fare")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, true, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.round", null, false, true, 0xFF2D6E82));
        list.add(new PaletteEntry("gui.trdm.script.block.round_fare", "round_fare", true, false,
                colorFor("round_fare")));
        list.add(new PaletteEntry("gui.trdm.script.category.values", null, false, true, 0xFF8A5A2B));
        for (final String type : List.of("number", "context_intervals", "context_meters",
                "context_stations", "context_zone_diff")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.tables", null, false, true, 0xFF6B4A8A));
        for (final String type : List.of("table_intervals", "table_meters")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        return list;
    }

    private static int paletteY(int index) {
        int y = PALETTE_TOP;
        for (int i = 0; i < index && i < PALETTE.size(); i++) {
            y += PALETTE.get(i).header() ? PALETTE_HEADER : PALETTE_ROW;
        }
        return y;
    }

    private static int paletteTotalHeight() {
        return paletteY(PALETTE.size()) - PALETTE_TOP;
    }

    private int paletteViewHeight() {
        return Math.max(0, this.height - PALETTE_TOP - 28);
    }

    private float paletteMaxScroll() {
        return Math.max(0, paletteTotalHeight() - paletteViewHeight());
    }

    private PaletteEntry paletteEntryAt(double mx, double my) {
        if (mx < PALETTE_X || mx >= PALETTE_X + PALETTE_W
                || my < PALETTE_TOP || my >= PALETTE_TOP + paletteViewHeight()) {
            return null;
        }
        for (int index = 0; index < PALETTE.size(); index++) {
            final PaletteEntry entry = PALETTE.get(index);
            final float y = paletteY(index) - paletteScroll;
            final int h = entry.header() ? PALETTE_HEADER : 21;
            if (my >= y && my < y + h) {
                return entry;
            }
        }
        return null;
    }

    private final Screen parent;
    private final FareRule rule;
    private final FareScript script;
    private String error = "";

    private int canvasLeft;
    private int canvasTop;
    private int canvasRight;
    private int canvasBottom;
    private int blockW;
    private int blockH;
    private float zoom = 1.0F;
    private float panX;
    private float panY;
    private boolean panning;
    private double panLastX;
    private double panLastY;
    private boolean popupVisible;
    private int popupX;
    private int popupY;
    private int popupW;
    private int popupH;

    private String selectedStatement;
    private String selectedValue;
    private String dragStatementId;
    private String dragValueId;
    private String dragValueSource;
    private String dragStatementType;
    private String dragValueType;
    private double dragX;
    private double dragY;
    private double dragWorldOffsetX;
    private double dragWorldOffsetY;
    private String tierScrollNode;
    private int tierScroll;
    private float paletteScroll;

    private static final int TIER_VISIBLE = 3;

    public FareScriptScreen(Screen parent, FareRule rule) {        super(Component.translatable("gui.trdm.script.title"));
        this.parent = parent;
        this.rule = rule;
        this.script = rule.script == null ? Scripts.empty() : rule.script.copy();
        Scripts.ensureHat(this.script);
        if (!chain().isEmpty()) {
            this.selectedStatement = chain().get(0).id;
        }
        this.popupVisible = this.selectedStatement != null;
    }

    @Override
    protected void init() {
        this.canvasLeft = CANVAS_LEFT;
        this.canvasTop = CANVAS_TOP;
        this.canvasRight = this.width - 16;
        this.canvasBottom = this.height - 44;
        layoutBlocks();
        this.paletteScroll = Math.max(0, Math.min(paletteMaxScroll(), paletteScroll));
        computePopup();

        final boolean narrow = this.width < 600;
        final int saveX = narrow ? this.width - 220 : this.width / 2 - 106;
        final int backX = narrow ? this.width - 110 : this.width / 2 + 6;
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.editor.save"),
                        button -> save())
                .bounds(saveX, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.back"),
                        button -> onClose())
                .bounds(backX, this.height - 28, 100, 20).build());

        if (popupVisible) {
            addPopupWidgets();
        }
    }

    private void computePopup() {
        if (!popupVisible || selectedStatement == null) {
            popupVisible = false;
            popupW = 0;
            popupH = 0;
            return;
        }
        final ScriptNode statement = selectedStatementNode();
        if (statement == null) {
            popupVisible = false;
            return;
        }
        final ScriptNode value = effectiveValue();
        final boolean table = value != null
                && ("table_intervals".equals(value.type) || "table_meters".equals(value.type));
        popupW = POPUP_W;
        popupH = table ? 142 : 96;
        final int index = chain().indexOf(statement);
        final int left = index >= 0 ? blockLeft(index) : canvasLeft + 16;
        final int top = index >= 0 ? blockTop(index) : canvasTop + 10;
        int px = left + blockW + 10;
        int py = top - 6;
        if (px + popupW > this.width - 8) {
            final int leftOption = left - popupW - 10;
            if (leftOption >= 8) {
                px = leftOption;
            } else {
                px = Math.max(8, Math.min(left, this.width - popupW - 8));
                py = top + blockH + 6;
            }
        }
        popupX = px;
        popupY = clamp(py, 44, Math.max(44, this.height - popupH - 40));
    }

    // ===== 画布布局 =====

    private List<ScriptNode> chain() {
        final List<ScriptNode> chain = new ArrayList<>();
        final ScriptNode hat = script.node(script.hat);
        if (hat == null) {
            return chain;
        }
        String cursor = hat.next;
        while (cursor != null && chain.size() < 64) {
            final ScriptNode node = script.node(cursor);
            if (node == null) {
                break;
            }
            chain.add(node);
            cursor = node.next;
        }
        return chain;
    }

    private ScriptNode selectedStatementNode() {
        return script.node(selectedStatement);
    }

    private ScriptNode selectedValueNode() {
        return script.node(selectedValue);
    }

    /** 当前参数面板实际编辑的值块：优先选中的值，否则是选中语句的值槽。 */
    private ScriptNode effectiveValue() {
        final ScriptNode value = selectedValueNode();
        if (value != null) {
            return value;
        }
        final ScriptNode statement = selectedStatementNode();
        if (statement != null && valueStatement(statement.type)) {
            return script.node(statement.inputs.get("value"));
        }
        return null;
    }

    private ScriptNode effectiveTable() {
        final ScriptNode value = effectiveValue();
        if (value == null) {
            return null;
        }
        return "table_intervals".equals(value.type) || "table_meters".equals(value.type) ? value : null;
    }

    private void syncTierScroll(ScriptNode table) {
        if (table == null) {
            tierScrollNode = null;
            tierScroll = 0;
            return;
        }
        if (!table.id.equals(tierScrollNode)) {
            tierScrollNode = table.id;
            tierScroll = 0;
        }
        tierScroll = clamp(tierScroll, 0, Math.max(0, table.tiers.size() - TIER_VISIBLE));
    }

    private void layoutBlocks() {
        blockW = Math.max(180, Math.min(Math.round(330 * zoom), canvasRight - canvasLeft - 20));
        blockH = Math.max(36, Math.round(BLOCK_H * zoom));
    }

    private int blockLeft(int index) {
        final List<ScriptNode> chain = chain();
        final ScriptNode node = index >= 0 && index < chain.size() ? chain.get(index) : null;
        final float worldX = 16F + (node == null ? 0F : node.x);
        return Math.round(canvasLeft + panX + worldX * zoom);
    }

    private int blockTop(int index) {
        final List<ScriptNode> chain = chain();
        final ScriptNode node = index >= 0 && index < chain.size() ? chain.get(index) : null;
        final float worldY = 10F + index * (BLOCK_H + BLOCK_GAP)
                + (node == null ? 0F : node.y);
        return Math.round(canvasTop + panY + worldY * zoom);
    }

    private boolean inCanvas(double mx, double my) {
        return mx >= canvasLeft && mx < canvasRight && my >= canvasTop && my < canvasBottom;
    }

    private boolean insidePopup(double mx, double my) {
        return popupVisible && mx >= popupX && mx < popupX + popupW
                && my >= popupY && my < popupY + popupH;
    }

    private int statementAt(double mx, double my) {
        final List<ScriptNode> chain = chain();
        for (int index = 0; index < chain.size(); index++) {
            final int top = blockTop(index);
            final int left = blockLeft(index);
            if (mx >= left && mx <= left + blockW && my >= top && my <= top + blockH) {
                return index;
            }
        }
        return -1;
    }

    private boolean inValueChip(int index, double mx, double my) {
        final int top = blockTop(index);
        final int left = blockLeft(index);
        final int chipTop = top + Math.round(30 * zoom);
        final int chipH = Math.round(20 * zoom);
        return mx >= left + 10 && mx <= left + blockW - 10
                && my >= chipTop && my <= chipTop + chipH;
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        fill(guiGraphics, 0, 0, this.width, 40, 0xFF5B4B8A);
        drawString(guiGraphics, Component.translatable("gui.trdm.script.title").getString(), 16, 15, 0xFFFFFF);
        drawPalette(guiGraphics, mouseX, mouseY);
        drawCanvas(guiGraphics, mouseX, mouseY);
        if (popupVisible) {
            drawPopup(guiGraphics);
            drawPopupLabels(guiGraphics);
            drawTierScrollbar(guiGraphics);
        }
        if (!error.isEmpty()) {
            drawString(guiGraphics, error, 200, 15, 0xFFFFD0D0);
        }
        if (dragStatementType != null || dragValueType != null || dragStatementId != null
                || dragValueId != null) {
            drawGhost(guiGraphics);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void drawPalette(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        final int top = 46;
        final int panelH = this.height - top - 8;
        fill(guiGraphics, PALETTE_X, top, PALETTE_W, panelH, PANEL_BORDER);
        fill(guiGraphics, PALETTE_X + 1, top + 1, PALETTE_W - 2, panelH - 2, PANEL_BG);

        final int viewH = paletteViewHeight();
        guiGraphics.enableScissor(PALETTE_X + 1, PALETTE_TOP, PALETTE_X + PALETTE_W - 1,
                PALETTE_TOP + viewH);
        for (int index = 0; index < PALETTE.size(); index++) {
            final PaletteEntry entry = PALETTE.get(index);
            final int y = Math.round(paletteY(index) - paletteScroll);
            final int entryH = entry.header() ? PALETTE_HEADER - 2 : 21;
            if (y + entryH < PALETTE_TOP || y > PALETTE_TOP + viewH) {
                continue;
            }
            if (entry.header()) {
                fill(guiGraphics, PALETTE_X + 1, y, PALETTE_W - 2, PALETTE_HEADER - 2, entry.color());
                drawString(guiGraphics, Component.translatable(entry.labelKey()).getString(),
                        PALETTE_X + 8, y + 4, 0xFFFFFF);
            } else {
                final boolean hover = mouseX >= PALETTE_X + 6 && mouseX < PALETTE_X + PALETTE_W - 6
                        && mouseY >= y && mouseY < y + 21;
                drawPaletteBlock(guiGraphics, PALETTE_X + 6, y, PALETTE_W - 12, 21,
                        entry.color(), Component.translatable(entry.labelKey()).getString(), hover);
            }
        }
        guiGraphics.disableScissor();

        if (paletteMaxScroll() > 0) {
            final int x = PALETTE_X + PALETTE_W - 7;
            fill(guiGraphics, x, PALETTE_TOP, 4, viewH, 0xFFD0D0D0);
            final float thumbH = Math.max(18F, viewH * (float) viewH / paletteTotalHeight());
            final float thumbY = PALETTE_TOP + (viewH - thumbH) * paletteScroll / paletteMaxScroll();
            fill(guiGraphics, x, Math.round(thumbY), 4, Math.round(thumbH), 0xFF8A8A8A);
        }

        drawString(guiGraphics, Component.translatable("gui.trdm.script.palette_hint").getString(),
                PALETTE_X + 8, this.height - 20, TEXT_MUTED);
    }

    private void drawPaletteBlock(GuiGraphics guiGraphics, int x, int y, int w, int h, int color,
                                  String label, boolean hover) {
        fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, 0xFF2A2A2A);
        fill(guiGraphics, x, y, w, h, hover ? brighten(color) : color);
        fill(guiGraphics, x, y, w, 2, 0x45FFFFFF);
        drawString(guiGraphics, label, x + 6, y + 6, 0xFFFFFF);
    }

    private void drawCanvas(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        fill(guiGraphics, canvasLeft - 1, canvasTop - 1, canvasRight - canvasLeft + 2,
                canvasBottom - canvasTop + 2, PANEL_BORDER);
        fill(guiGraphics, canvasLeft, canvasTop, canvasRight - canvasLeft,
                canvasBottom - canvasTop, CANVAS_BG);

        guiGraphics.enableScissor(canvasLeft, canvasTop, canvasRight, canvasBottom);
        final int spacing = Math.max(8, Math.round(20 * zoom));
        final int offsetX = ((Math.round(panX) % spacing) + spacing) % spacing;
        final int offsetY = ((Math.round(panY) % spacing) + spacing) % spacing;
        for (int gx = canvasLeft + offsetX; gx < canvasRight; gx += spacing) {
            for (int gy = canvasTop + offsetY; gy < canvasBottom; gy += spacing) {
                fill(guiGraphics, gx, gy, 1, 1, 0xFFE0E0E0);
            }
        }
        final List<ScriptNode> chain = chain();
        final int valueTarget = dragValueType != null || dragValueId != null
                ? statementAt(mouseX, mouseY) : -1;
        for (int index = 0; index < chain.size(); index++) {
            final ScriptNode node = chain.get(index);
            final int top = blockTop(index);
            final int left = blockLeft(index);
            if (top + blockH < canvasTop || top > canvasBottom || left + blockW < canvasLeft
                    || left > canvasRight) {
                continue;
            }
            final boolean hovered = mouseX >= left && mouseX <= left + blockW
                    && mouseY >= top && mouseY <= top + blockH;
            drawStatement(guiGraphics, node, index, top, node.id.equals(selectedStatement),
                    hovered, index == valueTarget);
            if (index + 1 < chain.size()) {
                drawConnector(guiGraphics, left + blockW / 2, top + blockH,
                        blockLeft(index + 1) + blockW / 2, blockTop(index + 1));
            }
        }
        if (chain.isEmpty()) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.canvas_empty").getString(),
                    canvasLeft + 20, canvasTop + 20, TEXT_MUTED);
        }
        guiGraphics.disableScissor();

        drawString(guiGraphics, "缩放 " + Math.round(zoom * 100) + "%",
                canvasRight - 90, canvasTop + 6, TEXT_MUTED);
    }

    private void drawConnector(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2) {
        fill(guiGraphics, x1 - 1, Math.min(y1, y2), 2, Math.abs(y2 - y1), 0xFF8A8A8A);
        fill(guiGraphics, Math.min(x1, x2), y2 - 1, Math.abs(x2 - x1), 2, 0xFF8A8A8A);
        fill(guiGraphics, x2 - 4, y2 - 2, 8, 4, 0xFF8A8A8A);
    }

    private void drawStatement(GuiGraphics guiGraphics, ScriptNode node, int index, int top,
                               boolean selected, boolean hovered, boolean valueTarget) {
        final int x = blockLeft(index);
        final int color = colorFor(node.type);
        drawBlockShape(guiGraphics, x, top, blockW, blockH, color, selected, hovered);
        drawString(guiGraphics, blockLabel(node.type), x + 10, top + 8, 0xFFFFFF);
        if (!valueStatement(node.type)) {
            if ("round_fare".equals(node.type)) {
                drawString(guiGraphics, "取整  " + node.fields.getOrDefault("mode", "HALF")
                        + " / " + node.fields.getOrDefault("unit", "0.1"),
                        x + 10, top + Math.round(32 * zoom), 0xE8E8E8);
            }
            return;
        }
        final ScriptNode value = script.node(node.inputs.get("value"));
        final String chip = Component.translatable("gui.trdm.script.value_chip",
                value == null ? Component.translatable("gui.trdm.script.none") : Component.literal(valueLabel(value)))
                .getString();
        final int chipTop = top + Math.round(30 * zoom);
        final int chipH = Math.max(12, Math.round(20 * zoom));
        fill(guiGraphics, x + 10, chipTop, blockW - 20, chipH,
                valueTarget ? 0xFFFFD45A : 0xFF202020);
        fill(guiGraphics, x + 12, chipTop + 1, Math.max(3, Math.round(4 * zoom)), chipH - 2,
                colorFor(value == null ? "none" : value.type));
        drawString(guiGraphics, chip, x + 20, chipTop + Math.max(2, Math.round(4 * zoom)),
                valueTarget ? 0xFF202020 : 0xE8E8E8);
    }

    /** 画带顶部凸起、底部凹槽、阴影的积木块。 */
    private void drawBlockShape(GuiGraphics guiGraphics, int x, int y, int w, int h, int color,
                                boolean selected, boolean hovered) {
        fill(guiGraphics, x + 3, y + 4, w, h, 0x28000000);
        fill(guiGraphics, x + 11, y - 5, 22, 6, 0xFF2A2A2A);
        fill(guiGraphics, x + 12, y - 4, 20, 4, color);
        fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, 0xFF2A2A2A);
        fill(guiGraphics, x, y, w, h, hovered ? brighten(color) : color);
        fill(guiGraphics, x, y, w, 3, 0x45FFFFFF);
        fill(guiGraphics, x, y + h - 3, w, 3, 0x30000000);
        fill(guiGraphics, x + 12, y + h - 5, 20, 5, CANVAS_BG);
        fill(guiGraphics, x + 12, y + h - 5, 20, 1, 0xFF2A2A2A);
        if (selected) {
            fill(guiGraphics, x - 2, y - 2, w + 4, 2, 0xFFFFD45A);
            fill(guiGraphics, x - 2, y + h, w + 4, 2, 0xFFFFD45A);
            fill(guiGraphics, x - 2, y - 2, 2, h + 4, 0xFFFFD45A);
            fill(guiGraphics, x + w, y - 2, 2, h + 4, 0xFFFFD45A);
        }
    }

    private void drawPopup(GuiGraphics guiGraphics) {
        fill(guiGraphics, popupX + 4, popupY + 4, popupW, popupH, 0x40000000);
        fill(guiGraphics, popupX - 1, popupY - 1, popupW + 2, popupH + 2, PANEL_BORDER);
        fill(guiGraphics, popupX, popupY, popupW, popupH, 0xFFFFFDF8);
        final ScriptNode statement = selectedStatementNode();
        final ScriptNode value = effectiveValue();
        final String title;
        if (selectedValue != null && value != null) {
            title = Component.translatable("gui.trdm.script.selected_value",
                    blockLabel(value.type)).getString();
        } else if (statement != null) {
            title = value == null
                    ? Component.translatable("gui.trdm.script.selected_statement",
                    blockLabel(statement.type)).getString()
                    : blockLabel(statement.type) + " · " + blockLabel(value.type);
        } else {
            title = Component.translatable("gui.trdm.script.title").getString();
        }
        drawString(guiGraphics, title, popupX + 10, popupY + 8, TEXT_DARK);
        if (statement != null && value != null
                && ("table_intervals".equals(value.type) || "table_meters".equals(value.type))
                && value.tiers.size() > TIER_VISIBLE) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.tier_scroll_hint").getString(),
                    popupX + popupW - 88, popupY + 8, TEXT_MUTED);
        }
    }

    private void drawPopupLabels(GuiGraphics guiGraphics) {
        final ScriptNode value = effectiveValue();
        if (value == null) {
            return;
        }
        if ("number".equals(value.type)) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.value_yuan").getString(),
                    popupX + 10, popupY + 28, TEXT_MUTED);
        }
        if ("table_intervals".equals(value.type) || "table_meters".equals(value.type)) {
            final boolean meters = "table_meters".equals(value.type);
            drawString(guiGraphics, Component.translatable(meters
                            ? "gui.trdm.script.tier_meter_up_to" : "gui.trdm.script.tier_interval_up_to").getString(),
                    popupX + 10, popupY + 28, TEXT_MUTED);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.tier_fare_yuan").getString(),
                    popupX + 80, popupY + 28, TEXT_MUTED);
        }
    }

    private void drawTierScrollbar(GuiGraphics guiGraphics) {
        final ScriptNode table = effectiveTable();
        if (table == null || table.tiers.size() <= TIER_VISIBLE) {
            return;
        }
        final int x = popupX + popupW - 8;
        final int top = popupY + 50;
        final int height = TIER_VISIBLE * 20;
        fill(guiGraphics, x, top, 4, height, 0xFFD0D0D0);
        final int max = table.tiers.size() - TIER_VISIBLE;
        final int thumb = Math.max(16, height * TIER_VISIBLE / table.tiers.size());
        final int thumbY = top + (height - thumb) * tierScroll / Math.max(1, max);
        fill(guiGraphics, x, thumbY, 4, thumb, 0xFF8A8A8A);
    }

    private void drawGhost(GuiGraphics guiGraphics) {
        final String label;
        final int color;
        if (dragStatementType != null) {
            label = blockLabel(dragStatementType);
            color = colorFor(dragStatementType);
        } else if (dragValueType != null) {
            label = blockLabel(dragValueType);
            color = colorFor(dragValueType);
        } else if (dragStatementId != null) {
            final ScriptNode node = script.node(dragStatementId);
            label = node == null ? "?" : blockLabel(node.type);
            color = node == null ? 0xFF555555 : colorFor(node.type);
        } else {
            final ScriptNode node = script.node(dragValueId);
            label = node == null ? "?" : blockLabel(node.type);
            color = node == null ? 0xFF555555 : colorFor(node.type);
        }
        final int w = 160;
        final int h = 20;
        final int x = Math.round((float) dragX + 8);
        final int y = Math.round((float) dragY + 8);
        fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, 0xFF101010);
        fill(guiGraphics, x, y, w, h, (color & 0x00FFFFFF) | 0xCC000000);
        drawString(guiGraphics, label, x + 6, y + 6, 0xFFFFFF);
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if ((button == 1 || button == 2) && inCanvas(mouseX, mouseY)) {
            panning = true;
            panLastX = mouseX;
            panLastY = mouseY;
            popupVisible = false;
            rebuild();
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (insidePopup(mouseX, mouseY)) {
            return true;
        }
        final PaletteEntry paletteEntry = paletteEntryAt(mouseX, mouseY);
        if (paletteEntry != null && !paletteEntry.header()) {
            if (paletteEntry.statement()) {
                dragStatementType = paletteEntry.type();
            } else {
                dragValueType = paletteEntry.type();
            }
            popupVisible = false;
            dragX = mouseX;
            dragY = mouseY;
            rebuild();
            return true;
        }
        final int index = statementAt(mouseX, mouseY);
        if (index >= 0) {
            final ScriptNode node = chain().get(index);
            if (valueStatement(node.type) && inValueChip(index, mouseX, mouseY)) {
                final String valueId = node.inputs.get("value");
                selectedStatement = node.id;
                selectedValue = valueId;
                if (valueId != null) {
                    dragValueId = valueId;
                    dragValueSource = node.id;
                }
                popupVisible = false;
                dragX = mouseX;
                dragY = mouseY;
                rebuild();
                return true;
            }
            selectedStatement = node.id;
            selectedValue = null;
            dragStatementId = node.id;
            final double worldX = (mouseX - canvasLeft - panX) / zoom;
            final double worldY = (mouseY - canvasTop - panY) / zoom;
            dragWorldOffsetX = worldX - (16 + node.x);
            dragWorldOffsetY = worldY - (10 + index * (BLOCK_H + BLOCK_GAP) + node.y);
            popupVisible = false;
            dragX = mouseX;
            dragY = mouseY;
            rebuild();
            return true;
        }
        selectedStatement = null;
        selectedValue = null;
        popupVisible = false;
        rebuild();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (panning) {
            panning = false;
            return true;
        }
        if (button != 0) {
            return super.mouseReleased(mouseX, mouseY, button);
        }
        if (dragStatementType != null) {
            if (inCanvas(mouseX, mouseY)) {
                insertStatementAt(dragStatementType, mouseX, mouseY);
            }
            dragStatementType = null;
            popupVisible = selectedStatement != null;
            rebuild();
            return true;
        }
        if (dragValueType != null) {
            popupVisible = true;
            final int target = statementAt(mouseX, mouseY);
            if (target >= 0) {
                attachNewValue(chain().get(target), dragValueType);
            }
            dragValueType = null;
            popupVisible = selectedStatement != null;
            rebuild();
            return true;
        }
        if (dragStatementId != null) {
            dragStatementId = null;
            popupVisible = selectedStatement != null;
            rebuild();
            return true;
        }
        if (dragValueId != null) {
            final int target = statementAt(mouseX, mouseY);
            if (target >= 0) {
                final ScriptNode statement = chain().get(target);
                if (valueStatement(statement.type)) {
                    if (!dragValueId.equals(statement.inputs.get("value"))) {
                        final ScriptNode source = script.node(dragValueSource);
                        if (source != null && dragValueId.equals(source.inputs.get("value"))) {
                            source.inputs.remove("value");
                        }
                    }
                    statement.inputs.put("value", dragValueId);
                    selectedStatement = statement.id;
                    selectedValue = dragValueId;
                    error = "";
                }
            }
            dragValueId = null;
            dragValueSource = null;
            popupVisible = selectedStatement != null;
            rebuild();
            return true;
        }
        if (selectedStatement != null && !popupVisible) {
            popupVisible = true;
            rebuild();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning) {
            panX += (float) (mouseX - panLastX);
            panY += (float) (mouseY - panLastY);
            panLastX = mouseX;
            panLastY = mouseY;
            return true;
        }
        if (dragStatementId != null) {
            final ScriptNode node = script.node(dragStatementId);
            final int index = chain().indexOf(node);
            if (node != null && index >= 0) {
                final double worldX = (mouseX - canvasLeft - panX) / zoom;
                final double worldY = (mouseY - canvasTop - panY) / zoom;
                node.x = (int) Math.round((worldX - dragWorldOffsetX - 16) / 5) * 5;
                node.y = (int) Math.round((worldY - dragWorldOffsetY
                        - (10 + index * (BLOCK_H + BLOCK_GAP))) / 5) * 5;
            }
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        if (dragStatementType != null || dragValueType != null || dragValueId != null) {
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        final ScriptNode table = effectiveTable();
        if (table != null && table.tiers.size() > TIER_VISIBLE && popupVisible
                && mouseX >= popupX && mouseX < popupX + popupW
                && mouseY >= popupY && mouseY < popupY + popupH) {
            final int max = table.tiers.size() - TIER_VISIBLE;
            final int direction = delta > 0 ? -1 : 1;
            tierScroll = clamp(tierScroll + direction, 0, max);
            rebuild();
            return true;
        }
        if (insidePopup(mouseX, mouseY)) {
            return true;
        }
        if (mouseX >= PALETTE_X && mouseX < PALETTE_X + PALETTE_W
                && mouseY >= PALETTE_TOP && mouseY < PALETTE_TOP + paletteViewHeight()
                && paletteMaxScroll() > 0) {
            paletteScroll = Math.max(0, Math.min(paletteMaxScroll(),
                    paletteScroll - (float) delta * 20F));
            return true;
        }
        if (popupVisible) {
            popupVisible = false;
            rebuild();
        }
        if (inCanvas(mouseX, mouseY)) {
            final double oldZoom = zoom;
            final double factor = delta > 0 ? 1.1 : 1.0 / 1.1;
            zoom = (float) Math.max(0.75, Math.min(1.5, zoom * factor));
            final double worldX = (mouseX - canvasLeft - panX) / oldZoom;
            final double worldY = (mouseY - canvasTop - panY) / oldZoom;
            panX = (float) (mouseX - canvasLeft - worldX * zoom);
            panY = (float) (mouseY - canvasTop - worldY * zoom);
            layoutBlocks();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ===== 编辑操作 =====

    private void insertStatementAt(String type, double mouseX, double mouseY) {
        final List<ScriptNode> chain = chain();
        if (chain.size() >= 8) {
            error = Component.translatable("gui.trdm.script.limit_statements").getString();
            return;
        }
        final int index = chain.size();
        final ScriptNode node = new ScriptNode(Scripts.uniqueId(script, "s"), type);
        node.x = (int) Math.round(((mouseX - canvasLeft - panX) / zoom - 16) / 5) * 5;
        node.y = (int) Math.round(((mouseY - canvasTop - panY) / zoom
                - (10 + index * (BLOCK_H + BLOCK_GAP))) / 5) * 5;
        if (valueStatement(type)) {
            node.inputs.put("value", newValue(type).id);
        }
        if ("round_fare".equals(type)) {
            node.fields.put("mode", "HALF");
            node.fields.put("unit", "0.1");
        }
        script.nodes.add(node);
        final ScriptNode hat = script.node(script.hat);
        if (hat != null) {
            if (chain.isEmpty()) {
                hat.next = node.id;
            } else {
                chain.get(chain.size() - 1).next = node.id;
            }
        }
        selectedStatement = node.id;
        selectedValue = node.inputs.get("value");
        popupVisible = true;
        error = "";
        rebuild();
    }

    private void relink(List<String> ids) {
        final ScriptNode hat = script.node(script.hat);
        if (hat == null) {
            return;
        }
        hat.next = ids.isEmpty() ? null : ids.get(0);
        for (int index = 0; index < ids.size(); index++) {
            final ScriptNode node = script.node(ids.get(index));
            if (node != null) {
                node.next = index + 1 < ids.size() ? ids.get(index + 1) : null;
            }
        }
    }

    private ScriptNode newValue(String type) {
        final ScriptNode value = new ScriptNode(Scripts.uniqueId(script, "v"), type);
        switch (type) {
            case "number" -> value.fields.put("value", "2.0");
            case "table_intervals" -> {
                value.tiers.add(new ScriptTier(2.0, 2.0));
                value.tiers.add(new ScriptTier(9.0, 3.0));
                value.tiers.add(new ScriptTier(null, 4.0));
            }
            case "table_meters" -> {
                value.tiers.add(new ScriptTier(1000.0, 2.0));
                value.tiers.add(new ScriptTier(3000.0, 3.0));
                value.tiers.add(new ScriptTier(null, 4.0));
            }
            default -> {
            }
        }
        script.nodes.add(value);
        return value;
    }

    private void attachNewValue(ScriptNode statement, String type) {
        if (statement == null || !valueStatement(statement.type)) {
            return;
        }
        final ScriptNode value = newValue(type);
        statement.inputs.put("value", value.id);
        selectedStatement = statement.id;
        selectedValue = value.id;
        popupVisible = true;
        error = "";
        rebuild();
    }

    private void cycleValueType() {
        final ScriptNode statement = selectedStatementNode();
        if (statement == null) {
            return;
        }
        final ScriptNode value = effectiveValue();
        if (value == null) {
            attachNewValue(statement, "number");
            return;
        }
        int index = 0;
        for (int i = 0; i < VALUE_TYPES.length; i++) {
            if (VALUE_TYPES[i].equals(value.type)) {
                index = i;
                break;
            }
        }
        final String type = VALUE_TYPES[(index + 1) % VALUE_TYPES.length];
        final ScriptNode replacement = newValue(type);
        statement.inputs.put("value", replacement.id);
        script.nodes.remove(value);
        selectedValue = replacement.id;
        error = "";
        rebuild();
    }

    private void cycleStatementType() {
        final ScriptNode node = selectedStatementNode();
        if (node == null) {
            return;
        }
        int index = 0;
        for (int i = 0; i < STATEMENT_TYPES.length; i++) {
            if (STATEMENT_TYPES[i].equals(node.type)) {
                index = i;
                break;
            }
        }
        node.type = STATEMENT_TYPES[(index + 1) % STATEMENT_TYPES.length];
        if (valueStatement(node.type) && script.node(node.inputs.get("value")) == null) {
            node.inputs.put("value", newValue("number").id);
        }
        error = "";
        rebuild();
    }

    private void deleteSelected() {
        if (selectedValue != null) {
            final ScriptNode value = selectedValueNode();
            if (value != null) {
                for (final ScriptNode node : script.nodes) {
                    if (value.id.equals(node.inputs.get("value"))) {
                        node.inputs.remove("value");
                    }
                }
                script.nodes.remove(value);
            }
            selectedValue = null;
            error = "";
            rebuild();
            return;
        }
        final ScriptNode statement = selectedStatementNode();
        if (statement != null) {
            final List<String> ids = new ArrayList<>();
            for (final ScriptNode node : chain()) {
                if (!node.id.equals(statement.id)) {
                    ids.add(node.id);
                }
            }
            relink(ids);
            script.nodes.remove(statement);
            selectedStatement = ids.isEmpty() ? null : ids.get(Math.max(0, ids.size() - 1));
            selectedValue = null;
            error = "";
            rebuild();
        }
    }

    private void save() {
        if (hasUnsupported()) {
            error = Component.translatable("gui.trdm.script.unsupported").getString();
            return;
        }
        final ScriptCompiler.Result result = ScriptCompiler.compile(script);
        if (result.compiled() == null) {
            error = String.join("；", result.errors());
            return;
        }
        final FareRule current = FareRuntime.get().config().ruleById(rule.id);
        final FareRule target = current != null ? current : rule;
        target.script = script.copy();
        FareRuntime.get().save();
        minecraft.setScreen(parent);
    }

    // ===== 参数浮窗 =====

    private void addPopupWidgets() {
        final ScriptNode statement = selectedStatementNode();
        final ScriptNode value = effectiveValue();
        final ScriptNode target = value != null ? value : statement;
        if (target == null) {
            return;
        }
        final int x = popupX + 10;
        final int y = popupY + 8;
        final boolean table = value != null
                && ("table_intervals".equals(value.type) || "table_meters".equals(value.type));

        if (value != null && "number".equals(value.type)) {
            final EditBox box = editBox(x + 60, y + 22, 90, 18,
                    Component.translatable("gui.trdm.script.unit"));
            box.setValue(value.fields.getOrDefault("value", "0"));
            box.setResponder(text -> value.fields.put("value", text));
            addRenderableWidget(box);
        }
        if (table) {
            syncTierScroll(value);
            addTierWidgets(value, x, y);
        }
        if ("round_fare".equals(target.type) && value == null) {
            addRenderableWidget(Button.builder(
                            Component.literal(target.fields.getOrDefault("mode", "HALF")),
                            button -> {
                                target.fields.put("mode", nextMode(target.fields.getOrDefault("mode", "HALF")));
                                rebuild();
                            })
                    .bounds(x + 60, y + 22, 70, 18).build());
            final EditBox unit = editBox(x + 136, y + 22, 70, 18,
                    Component.translatable("gui.trdm.script.unit"));
            unit.setValue(target.fields.getOrDefault("unit", "0.1"));
            unit.setResponder(text -> target.fields.put("unit", text));
            addRenderableWidget(unit);
        }
        final int buttonY = table ? y + 110 : y + 56;
        if (value != null || (statement != null && valueStatement(statement.type))) {
            addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.change_value"),
                            button -> cycleValueType())
                    .bounds(x, buttonY, 84, 18).build());
        }
        if (statement != null) {
            addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.change_type"),
                            button -> cycleStatementType())
                    .bounds(x + 90, buttonY, 84, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.delete"),
                        button -> deleteSelected())
                .bounds(x + 180, buttonY, 64, 18).build());
    }

    private void addTierWidgets(ScriptNode table, int x, int y) {
        final int maxScroll = Math.max(0, table.tiers.size() - TIER_VISIBLE);
        tierScroll = clamp(tierScroll, 0, maxScroll);
        final int rows = Math.min(TIER_VISIBLE, table.tiers.size() - tierScroll);
        for (int display = 0; display < rows; display++) {
            final int index = tierScroll + display;
            final int row = index;
            final ScriptTier tier = table.tiers.get(index);
            final int rowY = y + 42 + display * 20;
            final EditBox upTo = editBox(x, rowY, 64, 18,
                    Component.translatable("gui.trdm.script.tier_up_to"));
            upTo.setValue(tier.upTo() == null ? "" : trimNumber(tier.upTo()));
            upTo.setResponder(text -> {
                try {
                    final ScriptTier current = table.tiers.get(row);
                    table.tiers.set(row, new ScriptTier(text.isBlank() ? null
                            : Double.parseDouble(text.trim()), current.fareYuan()));
                } catch (NumberFormatException ignored) {
                    // 继续输入
                }
            });
            addRenderableWidget(upTo);
            final EditBox fare = editBox(x + 70, rowY, 64, 18,
                    Component.translatable("gui.trdm.script.tier_fare"));
            fare.setValue(trimNumber(tier.fareYuan()));
            fare.setResponder(text -> {
                try {
                    final ScriptTier current = table.tiers.get(row);
                    table.tiers.set(row, new ScriptTier(current.upTo(), Double.parseDouble(text.trim())));
                } catch (NumberFormatException ignored) {
                    // 继续输入
                }
            });
            addRenderableWidget(fare);
            addRenderableWidget(Button.builder(Component.literal("×"), button -> {
                        table.tiers.remove(row);
                        tierScroll = clamp(tierScroll, 0,
                                Math.max(0, table.tiers.size() - TIER_VISIBLE));
                        error = "";
                        rebuild();
                    }).bounds(x + 140, rowY, 18, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.add_row"),
                        button -> {
                            if (table.tiers.size() >= 6) {
                                error = Component.translatable("gui.trdm.script.limit_tiers").getString();
                                rebuild();
                                return;
                            }
                            double next = 10.0;
                            if (!table.tiers.isEmpty()) {
                                final ScriptTier last = table.tiers.get(table.tiers.size() - 1);
                                if (last.upTo() != null) {
                                    next = last.upTo() + 10.0;
                                }
                            }
                            int insert = table.tiers.size();
                            if (insert > 0 && table.tiers.get(insert - 1).upTo() == null) {
                                insert--;
                            }
                            table.tiers.add(insert, new ScriptTier(next, 0.0));
                            tierScroll = clamp(Math.max(tierScroll, insert - TIER_VISIBLE + 1), 0,
                                    Math.max(0, table.tiers.size() - TIER_VISIBLE));
                            error = "";
                            rebuild();
                        })
                .bounds(x + 164, y + 42, 80, 18).build());
    }

    private boolean hasUnsupported() {
        for (final ScriptNode node : chain()) {
            if ("if".equals(node.type) || (node.otherwise != null && !node.otherwise.isBlank())) {
                return true;
            }
        }
        return false;
    }

    // ===== 命中与工具 =====

    private static boolean valueStatement(String type) {
        return "set_fare".equals(type) || "add_fare".equals(type) || "multiply_fare".equals(type)
                || "cap_fare".equals(type) || "floor_fare".equals(type);
    }

    private static int colorFor(String type) {
        return switch (type) {
            case "set_fare", "add_fare", "multiply_fare", "cap_fare", "floor_fare" -> 0xFF2F7D4F;
            case "round_fare" -> 0xFF2D6E82;
            case "number", "context_intervals", "context_meters", "context_stations",
                 "context_zone_diff" -> 0xFF8A5A2B;
            case "table_intervals", "table_meters" -> 0xFF6B4A8A;
            default -> 0xFF555555;
        };
    }

    private static int brighten(int color) {
        final int r = Math.min(255, ((color >> 16) & 0xFF) + 24);
        final int g = Math.min(255, ((color >> 8) & 0xFF) + 24);
        final int b = Math.min(255, (color & 0xFF) + 24);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private String blockLabel(String type) {
        return Component.translatable("gui.trdm.script.block." + type).getString();
    }

    private String valueLabel(ScriptNode node) {
        final String label = blockLabel(node.type);
        if ("number".equals(node.type)) {
            return label + " " + node.fields.getOrDefault("value", "0");
        }
        if ("table_intervals".equals(node.type) || "table_meters".equals(node.type)) {
            return label + " (" + node.tiers.size() + ")";
        }
        return label;
    }

    private static String nextMode(String mode) {
        return switch (mode) {
            case "UP" -> "DOWN";
            case "DOWN" -> "HALF";
            default -> "UP";
        };
    }

    private static String trimNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void fill(GuiGraphics guiGraphics, int x, int y, int w, int h, int color) {
        guiGraphics.fill(x, y, x + w, y + h, color);
    }

    private EditBox editBox(int x, int y, int w, int h, Component narration) {
        final EditBox box = new EditBox(this.font, x, y, w, h, narration);
        box.setTextColor(0xFFE8E8E8);
        return box;
    }

    private void drawString(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.drawString(this.font, text, x, y, color, false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void rebuild() {
        this.clearWidgets();
        this.init(this.minecraft, this.width, this.height);
    }
}
