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
import top.xiaotang27.trdm.fare.script.ScriptTree;
import top.xiaotang27.trdm.fare.script.ScriptTree.Branch;
import top.xiaotang27.trdm.fare.script.Scripts;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 积木规则的可视化编辑器：
 * <ul>
 *   <li>左侧分类积木栏，中间是自由摆放、可缩放/平移的画布；</li>
 *   <li>语句块按执行顺序串成链，if 是带“那么/否则”两个分支的 C 形块；</li>
 *   <li>值块拖到语句的值槽、if 的条件槽或条件积木的槽位里；</li>
 *   <li>参数用跟随积木的浮窗编辑，撤销/重做覆盖结构修改。</li>
 * </ul>
 */
public final class FareScriptScreen extends Screen {

    private static final String[] STATEMENT_TYPES = {
            "set_fare", "add_fare", "multiply_fare", "cap_fare", "floor_fare", "round_fare"};
    private static final String[] NUMERIC_VALUE_TYPES = {
            "number", "context_intervals", "context_meters", "context_stations",
            "context_zone_diff", "table_intervals", "table_meters"};
    private static final String[] OPS = {"<", "<=", ">", ">=", "==", "!="};

    private static final int BLOCK_H = 58;
    private static final int BLOCK_GAP = 8;
    private static final int INDENT = 18;
    private static final int IF_HEADER = 40;
    private static final int IF_DIVIDER = 24;
    private static final int IF_SLOT_MIN = 26;
    private static final int IF_FOOTER = 10;
    private static final int MAX_STATEMENTS = 24;
    private static final int MAX_BRANCH_DEPTH = 4;

    private static final int PALETTE_X = 8;
    private static final int PALETTE_W = 154;
    private static final int PALETTE_TOP = 52;
    private static final int PALETTE_ROW = 25;
    private static final int PALETTE_HEADER = 18;
    private static final int CANVAS_LEFT = 174;
    private static final int CANVAS_TOP = 48;
    private static final int TIER_VISIBLE = 3;

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
        list.add(new PaletteEntry("gui.trdm.script.category.control", null, false, true, 0xFF8C5A20));
        list.add(new PaletteEntry("gui.trdm.script.block.if", "if", true, false, colorFor("if")));
        list.add(new PaletteEntry("gui.trdm.script.category.conditions", null, false, true, 0xFF2F6E45));
        for (final String type : List.of("compare", "and", "or", "not")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
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

    private enum DropKind {
        PORT_BEFORE, PORT_AFTER, BRANCH_APPEND
    }

    private record BranchKey(String ownerId, Branch branch) {
    }

    private record ValueOwner(String ownerId, String slot) {
    }

    private record ValueTarget(String ownerId, String slot, boolean booleanSlot) {
    }

    private record DropTarget(DropKind kind, String nodeId, BranchKey branch) {
    }

    private record ChipArea(String ownerId, String slot, boolean booleanSlot,
                            int x, int y, int width, int height) {
    }

    private static final class View {
        private final ScriptNode node;
        private final int depth;
        private final BranchKey branch;
        private int left;
        private int top;
        private int width;
        private int height;
        private boolean isIf;
        private int headerH;
        private int thenTop;
        private int thenBottom;
        private int elseHeaderTop;
        private int elseTop;
        private int elseBottom;
        private int condLeft;
        private int condTop;
        private int condWidth;
        private int condHeight;
        private String nextSiblingId;

        private View(ScriptNode node, int depth, BranchKey branch) {
            this.node = node;
            this.depth = depth;
            this.branch = branch;
        }
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
    private int gapScreen;
    private int indentScreen;
    private int slotMinScreen;
    private int ifHeaderScreen;
    private int ifDividerScreen;
    private int ifFooterScreen;
    private int slotPad;
    private float zoom = 1.0F;
    private float panX;
    private float panY;
    private boolean panning;
    private double panLastX;
    private double panLastY;

    private final List<View> views = new ArrayList<>();
    private final Map<String, View> viewById = new HashMap<>();
    private final List<ChipArea> popupChips = new ArrayList<>();

    private boolean popupVisible;
    private int popupX;
    private int popupY;
    private int popupW;
    private int popupH;

    private final Deque<FareScript> undoStack = new ArrayDeque<>();
    private final Deque<FareScript> redoStack = new ArrayDeque<>();
    private boolean restoring;
    private boolean dragUndoPushed;

    private String selectedStatement;
    private String selectedValue;
    private String dragStatementId;
    private String dragValueId;
    private String dragStatementType;
    private String dragValueType;
    private double dragX;
    private double dragY;
    private double dragGrabScreenX;
    private double dragGrabScreenY;
    private int dragBaseLeftScreen;
    private int dragBaseTopScreen;
    private DropTarget statementDrop;
    private boolean overTrash;
    private String tierScrollNode;
    private int tierScroll;
    private float paletteScroll;

    public FareScriptScreen(Screen parent, FareRule rule) {
        super(Component.translatable("gui.trdm.script.title"));
        this.parent = parent;
        this.rule = rule;
        this.script = rule.script == null ? Scripts.empty() : rule.script.copy();
        Scripts.ensureHat(this.script);
        final String first = topLevelFirst();
        if (first != null) {
            this.selectedStatement = first;
        }
        this.popupVisible = this.selectedStatement != null;
    }

    @Override
    protected void init() {
        this.canvasLeft = CANVAS_LEFT;
        this.canvasTop = CANVAS_TOP;
        this.canvasRight = this.width - 16;
        this.canvasBottom = this.height - 44;
        layout();
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
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.undo"),
                        button -> undo())
                .bounds(this.width - 184, 6, 84, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.redo"),
                        button -> redo())
                .bounds(this.width - 94, 6, 84, 20).build());

        if (popupVisible) {
            addPopupWidgets();
        }
    }

    // ===== 画布布局 =====

    private String topLevelFirst() {
        return ScriptTree.first(script, ScriptTree.Slot.top());
    }

    private static ScriptTree.Slot slot(BranchKey key) {
        return new ScriptTree.Slot(key.ownerId(), key.branch());
    }

    private void layout() {
        views.clear();
        viewById.clear();
        blockW = Math.max(190, Math.min(Math.round(330 * zoom), canvasRight - canvasLeft - 20));
        blockH = Math.max(36, Math.round(BLOCK_H * zoom));
        gapScreen = Math.max(6, Math.round(BLOCK_GAP * zoom));
        indentScreen = Math.max(14, Math.round(INDENT * zoom));
        slotMinScreen = Math.max(18, Math.round(IF_SLOT_MIN * zoom));
        ifHeaderScreen = Math.max(32, Math.round(IF_HEADER * zoom));
        ifDividerScreen = Math.max(18, Math.round(IF_DIVIDER * zoom));
        ifFooterScreen = Math.max(6, Math.round(IF_FOOTER * zoom));
        slotPad = Math.max(3, Math.round(4 * zoom));

        final ScriptNode hat = script.node(script.hat);
        if (hat == null || hat.next == null || hat.next.isBlank()) {
            return;
        }
        final int baseLeft = canvasLeft + Math.round(panX) + Math.round(16 * zoom);
        final int baseTop = canvasTop + Math.round(panY) + Math.round(10 * zoom);
        layoutChain(hat.next, baseLeft, baseTop, 0, new BranchKey("", Branch.TOP), new HashSet<>());
    }

    /** 递归布局一条语句链，返回这条链内容的最下沿。 */
    private int layoutChain(String firstId, int baseLeft, int baseTop, int depth,
                            BranchKey branch, Set<String> laidOut) {
        int cursor = baseTop;
        int maxBottom = baseTop;
        String id = firstId;
        int guard = 0;
        while (id != null && !id.isBlank() && guard++ < FareScript.MAX_NODES) {
            final ScriptNode node = script.node(id);
            if (node == null || !laidOut.add(node.id)) {
                break;
            }
            final View view = new View(node, depth, branch);
            view.left = baseLeft + Math.round(node.x * zoom);
            view.top = cursor + Math.round(node.y * zoom);
            view.width = Math.max(120, blockW - depth * indentScreen);
            view.isIf = "if".equals(node.type);
            view.nextSiblingId = view.isIf ? node.after : node.next;

            // 先加入列表：父块在子块之前，反向命中的是子块。
            views.add(view);
            viewById.put(node.id, view);

            if (view.isIf) {
                view.headerH = ifHeaderScreen;
                view.condLeft = view.left + 10 + font.width(blockLabel("if")) + 8;
                view.condTop = view.top + Math.max(5, Math.round(6 * zoom));
                view.condHeight = Math.max(16, view.headerH - 2 * Math.max(5, Math.round(6 * zoom)));
                final int thenLabelWidth = view.width >= 200
                        ? font.width(Component.translatable("gui.trdm.script.then").getString()) + 6 : 0;
                view.condWidth = Math.max(40,
                        view.width - (view.condLeft - view.left) - 10 - thenLabelWidth);

                final int bodyLeft = view.left + indentScreen;
                final int thenBase = view.top + view.headerH + slotPad;
                final int thenBottom = layoutChain(node.next, bodyLeft, thenBase, depth + 1,
                        new BranchKey(node.id, Branch.THEN), laidOut);
                final int thenEnd = Math.max(thenBase + slotMinScreen, thenBottom + slotPad);
                view.thenTop = view.top + view.headerH;
                view.thenBottom = thenEnd;

                view.elseHeaderTop = thenEnd;
                final int elseBase = view.elseHeaderTop + ifDividerScreen + slotPad;
                final int elseBottom = layoutChain(node.otherwise, bodyLeft, elseBase, depth + 1,
                        new BranchKey(node.id, Branch.ELSE), laidOut);
                final int elseEnd = Math.max(elseBase + slotMinScreen, elseBottom + slotPad);
                view.elseTop = view.elseHeaderTop + ifDividerScreen;
                view.elseBottom = elseEnd;
                view.height = elseEnd + ifFooterScreen - view.top;
            } else {
                view.height = blockH;
            }

            maxBottom = Math.max(maxBottom, view.top + view.height);
            cursor = view.top + view.height + gapScreen;
            id = view.nextSiblingId;
        }
        return maxBottom;
    }

    private boolean inCanvas(double mx, double my) {
        return mx >= canvasLeft && mx < canvasRight && my >= canvasTop && my < canvasBottom;
    }

    private boolean insidePopup(double mx, double my) {
        return popupVisible && mx >= popupX && mx < popupX + popupW
                && my >= popupY && my < popupY + popupH;
    }

    private View viewAt(double mx, double my) {
        for (int index = views.size() - 1; index >= 0; index--) {
            final View view = views.get(index);
            if (mx >= view.left && mx < view.left + view.width
                    && my >= view.top && my < view.top + view.height) {
                return view;
            }
        }
        return null;
    }

    private boolean inValueChip(View view, double mx, double my) {
        if (!valueStatement(view.node.type)) {
            return false;
        }
        final int chipTop = view.top + Math.round(30 * zoom);
        final int chipH = Math.max(12, Math.round(20 * zoom));
        return mx >= view.left + 10 && mx <= view.left + view.width - 10
                && my >= chipTop && my <= chipTop + chipH;
    }

    private boolean inConditionChip(View view, double mx, double my) {
        return view.isIf && mx >= view.condLeft && mx <= view.condLeft + view.condWidth
                && my >= view.condTop && my <= view.condTop + view.condHeight;
    }

    private Branch branchInIfBody(View view, double mx, double my) {
        if (!view.isIf || mx < view.left + indentScreen || mx > view.left + view.width) {
            return null;
        }
        if (my >= view.thenTop && my < view.thenBottom) {
            return Branch.THEN;
        }
        if (my >= view.elseTop && my < view.elseBottom) {
            return Branch.ELSE;
        }
        return null;
    }

    /** 找出鼠标下最深的 if 分支（允许落在分支里的子块上，用于新建语句）。 */
    private DropTarget ifBodyTarget(double mx, double my, boolean allowOverChildren) {
        View best = null;
        Branch bestBranch = null;
        for (final View view : views) {
            if (!view.isIf) {
                continue;
            }
            final Branch branch = branchInIfBody(view, mx, my);
            if (branch == null) {
                continue;
            }
            if (!allowOverChildren && viewAt(mx, my) != view) {
                continue;
            }
            if (best == null || view.depth > best.depth) {
                best = view;
                bestBranch = branch;
            }
        }
        return best == null ? null : new DropTarget(DropKind.BRANCH_APPEND, best.node.id,
                new BranchKey(best.node.id, bestBranch));
    }

    private DropTarget nearestPort(double mx, double my, String exclude) {
        double best = 30;
        View bestView = null;
        boolean bestBefore = false;
        for (final View view : views) {
            if (exclude != null && (view.node.id.equals(exclude) || isDescendant(view.node.id, exclude))) {
                continue;
            }
            final double cx = view.left + view.width / 2.0;
            final double dTop = Math.hypot(mx - cx, my - view.top);
            if (dTop < best) {
                best = dTop;
                bestView = view;
                bestBefore = true;
            }
            final double dBottom = Math.hypot(mx - cx, my - (view.top + view.height));
            if (dBottom < best) {
                best = dBottom;
                bestView = view;
                bestBefore = false;
            }
        }
        if (bestView == null) {
            return null;
        }
        return new DropTarget(bestBefore ? DropKind.PORT_BEFORE : DropKind.PORT_AFTER,
                bestView.node.id, bestView.branch);
    }

    private DropTarget computeStatementDrop(double mx, double my, String exclude, boolean palette) {
        overTrash = inTrash(mx, my);
        if (overTrash) {
            return null;
        }
        final DropTarget port = nearestPort(mx, my, exclude);
        if (port != null) {
            return port;
        }
        return ifBodyTarget(mx, my, palette);
    }

    private int trashX() {
        return canvasRight - 96;
    }

    private int trashY() {
        return canvasBottom - 42;
    }

    private boolean inTrash(double mx, double my) {
        return mx >= trashX() && mx <= trashX() + 88 && my >= trashY() && my <= trashY() + 32;
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        fill(guiGraphics, 0, 0, this.width, 40, 0xFF5B4B8A);
        drawString(guiGraphics, Component.translatable("gui.trdm.script.title").getString(), 16, 15, 0xFFFFFF);
        drawPalette(guiGraphics, mouseX, mouseY);
        final ValueTarget valueTarget = (dragValueType != null || dragValueId != null)
                ? valueTargetAt(mouseX, mouseY) : null;
        drawCanvas(guiGraphics, mouseX, mouseY, valueTarget);
        if (popupVisible) {
            drawPopup(guiGraphics);
            drawPopupLabels(guiGraphics, valueTarget);
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

    private void drawCanvas(GuiGraphics guiGraphics, int mouseX, int mouseY, ValueTarget valueTarget) {
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

        // 先画所有 C 形块的“外壳”，再画连接线和块内容。
        for (final View view : views) {
            if (view.isIf && visible(view)) {
                final boolean hovered = mouseX >= view.left && mouseX <= view.left + view.width
                        && mouseY >= view.top && mouseY <= view.top + view.height;
                drawIfBackground(guiGraphics, view, view.node.id.equals(selectedStatement), hovered);
            }
        }
        for (final View view : views) {
            if (!visible(view)) {
                continue;
            }
            final View next = view.nextSiblingId == null ? null : viewById.get(view.nextSiblingId);
            if (next != null && visible(next)) {
                drawConnector(guiGraphics, view.left + view.width / 2, view.top + view.height,
                        next.left + next.width / 2, next.top);
            }
        }
        for (final View view : views) {
            if (!visible(view)) {
                continue;
            }
            final boolean hovered = mouseX >= view.left && mouseX <= view.left + view.width
                    && mouseY >= view.top && mouseY <= view.top + view.height;
            if (view.isIf) {
                drawIfDetails(guiGraphics, view, valueTarget);
            } else {
                drawStatement(guiGraphics, view, valueTarget, view.node.id.equals(selectedStatement),
                        hovered);
            }
        }
        if (views.isEmpty()) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.canvas_empty").getString(),
                    canvasLeft + 20, canvasTop + 20, TEXT_MUTED);
        }

        // 拖放反馈画在块内容之上。
        if (statementDrop != null) {
            if (statementDrop.kind() == DropKind.BRANCH_APPEND) {
                drawBranchHighlight(guiGraphics, statementDrop.branch());
            } else {
                drawPortRing(guiGraphics, statementDrop);
            }
        }
        if (dragStatementId != null || dragStatementType != null) {
            final int tx = trashX();
            final int ty = trashY();
            fill(guiGraphics, tx, ty, 88, 32, overTrash ? 0xFFC0392B : 0xFFE8E8E8);
            fill(guiGraphics, tx, ty, 88, 2, 0xFF9A9A9A);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.trash").getString(),
                    tx + 12, ty + 11, overTrash ? 0xFFFFFFFF : TEXT_MUTED);
        }
        guiGraphics.disableScissor();

        drawString(guiGraphics, "缩放 " + Math.round(zoom * 100) + "%",
                canvasRight - 90, canvasTop + 6, TEXT_MUTED);
    }

    private boolean visible(View view) {
        return view.top + view.height >= canvasTop && view.top <= canvasBottom
                && view.left + view.width >= canvasLeft && view.left <= canvasRight;
    }

    private void drawConnector(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2) {
        fill(guiGraphics, x1 - 1, Math.min(y1, y2), 2, Math.abs(y2 - y1), 0xFF8A8A8A);
        fill(guiGraphics, Math.min(x1, x2), y2 - 1, Math.abs(x2 - x1), 2, 0xFF8A8A8A);
        fill(guiGraphics, x2 - 4, y2 - 2, 8, 4, 0xFF8A8A8A);
    }

    private void drawIfBackground(GuiGraphics guiGraphics, View view, boolean selected, boolean hovered) {
        final int x = view.left;
        final int y = view.top;
        final int w = view.width;
        final int h = view.height;
        final int color = hovered ? brighten(colorFor("if")) : colorFor("if");
        fill(guiGraphics, x + 3, y + 4, w, h, 0x28000000);
        fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, 0xFF2A2A2A);
        fill(guiGraphics, x, y, w, h, color);
        fill(guiGraphics, x, y, w, 3, 0x45FFFFFF);

        // 顶部凹槽/凸起。
        fill(guiGraphics, x + 11, y - 5, 22, 6, 0xFF2A2A2A);
        fill(guiGraphics, x + 12, y - 4, 20, 4, color);

        // 挖空“那么”和“否则”分支，留下的左竖条和横条就是 C 形。
        final int bodyLeft = x + indentScreen;
        final int bodyWidth = w - indentScreen;
        fill(guiGraphics, bodyLeft, view.thenTop, bodyWidth, view.thenBottom - view.thenTop, CANVAS_BG);
        fill(guiGraphics, bodyLeft, view.thenTop, bodyWidth, 1, 0xFF2A2A2A);
        fill(guiGraphics, bodyLeft, view.elseTop, bodyWidth, view.elseBottom - view.elseTop, CANVAS_BG);
        fill(guiGraphics, bodyLeft, view.elseTop, bodyWidth, 1, 0xFF2A2A2A);
        fill(guiGraphics, x, view.elseHeaderTop, w, ifDividerScreen, color);
        fill(guiGraphics, bodyLeft, view.elseBottom - 1, bodyWidth, 1, 0xFF2A2A2A);

        // 底部凸条和凹槽。
        final int footTop = view.elseBottom;
        fill(guiGraphics, x, footTop, w, ifFooterScreen, color);
        fill(guiGraphics, x + 12, y + h - 5, 20, 5, CANVAS_BG);
        fill(guiGraphics, x + 12, y + h - 5, 20, 1, 0xFF2A2A2A);
        fill(guiGraphics, x, y + h - 3, w, 3, 0x30000000);

        drawString(guiGraphics, Component.translatable("gui.trdm.script.block.if").getString(),
                x + 10, y + 8, 0xFFFFFF);
        drawString(guiGraphics, Component.translatable("gui.trdm.script.else").getString(),
                x + 10, view.elseHeaderTop + Math.max(3, (ifDividerScreen - 8) / 2), 0xFFFFFF);

        if (selected) {
            fill(guiGraphics, x - 2, y - 2, w + 4, 2, 0xFFFFD45A);
            fill(guiGraphics, x - 2, y + h, w + 4, 2, 0xFFFFD45A);
            fill(guiGraphics, x - 2, y - 2, 2, h + 4, 0xFFFFD45A);
            fill(guiGraphics, x + w, y - 2, 2, h + 4, 0xFFFFD45A);
        }
    }

    private void drawIfDetails(GuiGraphics guiGraphics, View view, ValueTarget valueTarget) {
        final ScriptNode node = view.node;
        drawConditionChip(guiGraphics, view, valueTarget);
        if (ScriptTree.first(script, new ScriptTree.Slot(node.id, Branch.THEN)) == null) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.drop_statement").getString(),
                    view.left + indentScreen + 8, view.thenTop + 7, TEXT_MUTED);
        }
        if (ScriptTree.first(script, new ScriptTree.Slot(node.id, Branch.ELSE)) == null) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.drop_statement").getString(),
                    view.left + indentScreen + 8, view.elseTop + 7, TEXT_MUTED);
        }
    }

    private void drawConditionChip(GuiGraphics guiGraphics, View view, ValueTarget valueTarget) {
        final ScriptNode condition = script.node(view.node.inputs.get("condition"));
        final int x = view.condLeft;
        final int y = view.condTop;
        final int w = view.condWidth;
        final int h = view.condHeight;
        final boolean target = valueTarget != null && valueTarget.booleanSlot()
                && view.node.id.equals(valueTarget.ownerId()) && "condition".equals(valueTarget.slot());
        fill(guiGraphics, x, y, w, h, target ? 0xFFFFD45A : 0xFF202020);
        if (condition != null) {
            fill(guiGraphics, x + 2, y + 1, 3, h - 2, colorFor(condition.type));
            final String label = fit(conditionSummary(condition.id), w - 10);
            drawString(guiGraphics, label, x + 8, y + Math.max(2, (h - 8) / 2),
                    target ? 0xFF202020 : 0xE8E8E8);
        } else {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.condition_none").getString(),
                    x + 8, y + Math.max(2, (h - 8) / 2), target ? 0xFF202020 : 0xFFB0B0B0);
        }
        if (view.width >= 200) {
            final String then = Component.translatable("gui.trdm.script.then").getString();
            drawString(guiGraphics, then, view.left + view.width - 10 - font.width(then),
                    view.top + 8, 0xFFFFFF);
        }
    }

    private void drawStatement(GuiGraphics guiGraphics, View view, ValueTarget valueTarget,
                               boolean selected, boolean hovered) {
        final ScriptNode node = view.node;
        final int x = view.left;
        final int top = view.top;
        final int w = view.width;
        drawBlockShape(guiGraphics, x, top, w, view.height, colorFor(node.type), selected, hovered);
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
                value == null ? Component.translatable("gui.trdm.script.none")
                        : Component.literal(valueSummary(value.id))).getString();
        final int chipTop = top + Math.round(30 * zoom);
        final int chipH = Math.max(12, Math.round(20 * zoom));
        final boolean target = valueTarget != null && !valueTarget.booleanSlot()
                && node.id.equals(valueTarget.ownerId()) && "value".equals(valueTarget.slot());
        fill(guiGraphics, x + 10, chipTop, w - 20, chipH, target ? 0xFFFFD45A : 0xFF202020);
        fill(guiGraphics, x + 12, chipTop + 1, Math.max(3, Math.round(4 * zoom)), chipH - 2,
                value == null ? 0xFF555555 : colorFor(value.type));
        drawString(guiGraphics, fit(chip, w - 34), x + 20,
                chipTop + Math.max(2, Math.round(4 * zoom)), target ? 0xFF202020 : 0xE8E8E8);
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

    private void drawBranchHighlight(GuiGraphics guiGraphics, BranchKey key) {
        final View view = viewById.get(key.ownerId());
        if (view == null || !view.isIf) {
            return;
        }
        final int top = key.branch() == Branch.THEN ? view.thenTop : view.elseTop;
        final int bottom = key.branch() == Branch.THEN ? view.thenBottom : view.elseBottom;
        fill(guiGraphics, view.left + indentScreen, top, view.width - indentScreen, bottom - top,
                0x35FFD45A);
        drawString(guiGraphics, Component.translatable("gui.trdm.script.drop_here").getString(),
                view.left + indentScreen + 8, top + 7, 0xFF8A5A00);
    }

    private void drawPortRing(GuiGraphics guiGraphics, DropTarget target) {
        final View view = viewById.get(target.nodeId());
        if (view == null) {
            return;
        }
        final int px = view.left + view.width / 2;
        final int py = target.kind() == DropKind.PORT_BEFORE
                ? view.top : view.top + view.height;
        fill(guiGraphics, px - 7, py - 7, 14, 14, 0xFFFF8A00);
        fill(guiGraphics, px - 4, py - 4, 8, 8, CANVAS_BG);
    }

    private void drawPopup(GuiGraphics guiGraphics) {
        fill(guiGraphics, popupX + 4, popupY + 4, popupW, popupH, 0x40000000);
        fill(guiGraphics, popupX - 1, popupY - 1, popupW + 2, popupH + 2, PANEL_BORDER);
        fill(guiGraphics, popupX, popupY, popupW, popupH, 0xFFFFFDF8);
        final ScriptNode value = popupValue();
        final ScriptNode statement = selectedStatementNode();
        final String title;
        if (value != null) {
            title = Component.translatable("gui.trdm.script.selected_value",
                    blockLabel(value.type)).getString();
        } else if (statement != null) {
            title = Component.translatable("gui.trdm.script.selected_statement",
                    blockLabel(statement.type)).getString();
        } else {
            title = Component.translatable("gui.trdm.script.title").getString();
        }
        drawString(guiGraphics, title, popupX + 10, popupY + 8, TEXT_DARK);
        if (value != null && isTable(value.type) && value.tiers.size() > TIER_VISIBLE) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.tier_scroll_hint").getString(),
                    popupX + popupW - 88, popupY + 8, TEXT_MUTED);
        }
    }

    private void drawPopupLabels(GuiGraphics guiGraphics, ValueTarget valueTarget) {
        final ScriptNode value = popupValue();
        if (value == null) {
            if (selectedStatementNode() != null && "if".equals(selectedStatementNode().type)) {
                final ScriptNode condition = script.node(selectedStatementNode().inputs.get("condition"));
                drawString(guiGraphics, fit(condition == null
                                ? Component.translatable("gui.trdm.script.condition_none").getString()
                                : Component.translatable("gui.trdm.script.condition_summary",
                                conditionSummary(condition.id)).getString(), popupW - 20),
                        popupX + 10, popupY + 28, TEXT_MUTED);
            }
            return;
        }
        if ("number".equals(value.type)) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.value_yuan").getString(),
                    popupX + 10, popupY + 28, TEXT_MUTED);
        }
        if (isTable(value.type)) {
            final boolean meters = "table_meters".equals(value.type);
            drawString(guiGraphics, Component.translatable(meters
                            ? "gui.trdm.script.tier_meter_up_to" : "gui.trdm.script.tier_interval_up_to").getString(),
                    popupX + 10, popupY + 28, TEXT_MUTED);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.tier_fare_yuan").getString(),
                    popupX + 80, popupY + 28, TEXT_MUTED);
        }
        if ("compare".equals(value.type)) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.operator").getString(),
                    popupX + 10, popupY + 33, TEXT_MUTED);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.value_left").getString(),
                    popupX + 10, popupY + 56, TEXT_MUTED);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.value_right").getString(),
                    popupX + 10, popupY + 80, TEXT_MUTED);
        } else if ("and".equals(value.type) || "or".equals(value.type)) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.boolean_hint").getString(),
                    popupX + 10, popupY + 28, TEXT_MUTED);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.condition_a").getString(),
                    popupX + 10, popupY + 56, TEXT_MUTED);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.condition_b").getString(),
                    popupX + 10, popupY + 80, TEXT_MUTED);
        } else if ("not".equals(value.type)) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.boolean_hint").getString(),
                    popupX + 10, popupY + 28, TEXT_MUTED);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.condition_a").getString(),
                    popupX + 10, popupY + 56, TEXT_MUTED);
        }
        for (final ChipArea chip : popupChips) {
            final ScriptNode owner = script.node(chip.ownerId());
            final ScriptNode child = owner == null ? null : script.node(owner.inputs.get(chip.slot()));
            final boolean target = valueTarget != null && chip.ownerId().equals(valueTarget.ownerId())
                    && chip.slot().equals(valueTarget.slot());
            drawValueChip(guiGraphics, chip, child, target);
        }
    }

    private void drawValueChip(GuiGraphics guiGraphics, ChipArea chip, ScriptNode child, boolean target) {
        fill(guiGraphics, chip.x(), chip.y(), chip.width(), chip.height(),
                target ? 0xFFFFD45A : 0xFF202020);
        if (child != null) {
            fill(guiGraphics, chip.x() + 2, chip.y() + 1, 3, chip.height() - 2, colorFor(child.type));
        }
        final String label = child == null
                ? Component.translatable("gui.trdm.script.none").getString()
                : valueSummary(child.id);
        drawString(guiGraphics, fit(label, chip.width() - 12), chip.x() + 8,
                chip.y() + Math.max(2, (chip.height() - 8) / 2), target ? 0xFF202020 : 0xE8E8E8);
    }

    private void drawTierScrollbar(GuiGraphics guiGraphics) {
        final ScriptNode table = popupValue();
        if (table == null || !isTable(table.type) || table.tiers.size() <= TIER_VISIBLE
                || table != effectiveTable()) {
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
        final ChipArea chip = chipAt(mouseX, mouseY);
        if (chip != null) {
            selectChip(chip);
            return true;
        }
        if (insidePopup(mouseX, mouseY)) {
            return true;
        }
        final PaletteEntry paletteEntry = paletteEntryAt(mouseX, mouseY);
        if (paletteEntry != null && !paletteEntry.header()) {
            if (paletteEntry.statement()) {
                dragStatementType = paletteEntry.type();
                popupVisible = false;
            } else {
                // 保留参数浮窗，值积木可以直接拖到条件槽位上。
                dragValueType = paletteEntry.type();
            }
            dragX = mouseX;
            dragY = mouseY;
            rebuild();
            return true;
        }
        final View view = viewAt(mouseX, mouseY);
        if (view != null) {
            if (inConditionChip(view, mouseX, mouseY)) {
                selectedStatement = view.node.id;
                selectedValue = view.node.inputs.get("condition");
                popupVisible = true;
                error = "";
                rebuild();
                return true;
            }
            if (inValueChip(view, mouseX, mouseY)) {
                final String valueId = view.node.inputs.get("value");
                selectedStatement = view.node.id;
                selectedValue = valueId;
                if (valueId != null) {
                    dragValueId = valueId;
                }
                popupVisible = false;
                dragX = mouseX;
                dragY = mouseY;
                rebuild();
                return true;
            }
            selectedStatement = view.node.id;
            selectedValue = null;
            dragStatementId = view.node.id;
            dragUndoPushed = false;
            dragGrabScreenX = mouseX - view.left;
            dragGrabScreenY = mouseY - view.top;
            dragBaseLeftScreen = view.left - Math.round(view.node.x * zoom);
            dragBaseTopScreen = view.top - Math.round(view.node.y * zoom);
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
            final DropTarget target = computeStatementDrop(mouseX, mouseY, null, true);
            if (!overTrash) {
                if (target != null) {
                    insertNewStatement(dragStatementType, target);
                } else if (inCanvas(mouseX, mouseY)) {
                    insertNewStatementAt(dragStatementType, mouseX, mouseY);
                }
            }
            clearDrag();
            popupVisible = selectedStatement != null;
            rebuild();
            return true;
        }
        if (dragValueType != null) {
            dropNewValue(dragValueType, mouseX, mouseY);
            clearDrag();
            popupVisible = selectedStatement != null;
            rebuild();
            return true;
        }
        if (dragStatementId != null) {
            final String id = dragStatementId;
            final boolean moved = dragUndoPushed;
            final DropTarget target = moved ? computeStatementDrop(mouseX, mouseY, id, false) : null;
            if (moved && overTrash) {
                deleteStatement(id, false);
                selectedStatement = null;
                selectedValue = null;
            } else if (moved && target != null) {
                moveStatement(id, target);
            } else if (moved && inCanvas(mouseX, mouseY)
                    && viewById.containsKey(id)
                    && viewById.get(id).branch.branch() != Branch.TOP) {
                // 从 C 形块里拖到画布空白处：变成顶层语句。
                moveOutToTop(id, mouseX, mouseY);
            }
            clearDrag();
            popupVisible = selectedStatement != null;
            rebuild();
            return true;
        }
        if (dragValueId != null) {
            final String id = dragValueId;
            final ValueTarget target = valueTargetAt(mouseX, mouseY);
            if (target != null && typeFits(target.booleanSlot(), script.node(id))) {
                final ValueOwner owner = ownerOfValue(id);
                if (owner == null || !owner.ownerId().equals(target.ownerId())
                        || !owner.slot().equals(target.slot())) {
                    pushUndo();
                    removeValueReferences(id);
                    script.node(target.ownerId()).inputs.put(target.slot(), id);
                    selectedValue = id;
                    selectedStatement = containingStatement(id);
                    pruneUnreachable();
                }
            }
            clearDrag();
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

    private void clearDrag() {
        dragStatementType = null;
        dragValueType = null;
        dragStatementId = null;
        dragValueId = null;
        dragUndoPushed = false;
        statementDrop = null;
        overTrash = false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning) {
            panX += (float) (mouseX - panLastX);
            panY += (float) (mouseY - panLastY);
            panLastX = mouseX;
            panLastY = mouseY;
            layout();
            return true;
        }
        if (dragStatementId != null) {
            if (!dragUndoPushed) {
                pushUndo();
                dragUndoPushed = true;
            }
            final ScriptNode node = script.node(dragStatementId);
            if (node != null) {
                final double newLeft = mouseX - dragGrabScreenX;
                final double newTop = mouseY - dragGrabScreenY;
                node.x = (int) Math.round(((newLeft - dragBaseLeftScreen) / zoom) / 5) * 5;
                node.y = (int) Math.round(((newTop - dragBaseTopScreen) / zoom) / 5) * 5;
                layout();
            }
            statementDrop = computeStatementDrop(mouseX, mouseY, dragStatementId, false);
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        if (dragStatementType != null) {
            statementDrop = computeStatementDrop(mouseX, mouseY, null, true);
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        if (dragValueType != null || dragValueId != null) {
            statementDrop = null;
            overTrash = false;
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        final ScriptNode table = popupValue();
        if (table != null && isTable(table.type) && table.tiers.size() > TIER_VISIBLE
                && popupVisible && mouseX >= popupX && mouseX < popupX + popupW
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
            layout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ===== 编辑操作 =====

    private void insertNewStatement(String type, DropTarget target) {
        if (!canAddStatement(target.branch())) {
            return;
        }
        pushUndo();
        final ScriptNode node = newStatementNode(type);
        switch (target.kind()) {
            case PORT_BEFORE -> insertIntoBranch(target.branch(), node.id, target.nodeId());
            case PORT_AFTER -> insertAfter(target.nodeId(), node.id);
            case BRANCH_APPEND -> appendToBranch(target.branch(), node.id);
        }
        selectedStatement = node.id;
        selectedValue = node.inputs.get("condition") != null ? node.inputs.get("condition")
                : node.inputs.get("value");
        popupVisible = true;
        error = "";
    }

    private void insertNewStatementAt(String type, double mouseX, double mouseY) {
        final BranchKey top = new BranchKey("", Branch.TOP);
        if (!canAddStatement(top)) {
            return;
        }
        pushUndo();
        final ScriptNode node = newStatementNode(type);
        final int baseLeft = canvasLeft + Math.round(panX) + Math.round(16 * zoom);
        final int baseTop = topLevelAppendTop();
        node.x = (int) Math.round((((mouseX - baseLeft) / zoom)) / 5) * 5;
        node.y = (int) Math.round((((mouseY - baseTop) / zoom)) / 5) * 5;
        appendToBranch(top, node.id);
        selectedStatement = node.id;
        selectedValue = node.inputs.get("condition") != null ? node.inputs.get("condition")
                : node.inputs.get("value");
        popupVisible = true;
        error = "";
    }

    private ScriptNode newStatementNode(String type) {
        final ScriptNode node = new ScriptNode(Scripts.uniqueId(script, "s"), type);
        if ("if".equals(type)) {
            final ScriptNode condition = newBooleanValue("compare");
            node.inputs.put("condition", condition.id);
        } else if (valueStatement(type)) {
            node.inputs.put("value", newNumberValue("number").id);
        } else if ("round_fare".equals(type)) {
            node.fields.put("mode", "HALF");
            node.fields.put("unit", "0.1");
        }
        script.nodes.add(node);
        return node;
    }

    private int topLevelAppendTop() {
        final int defaultTop = canvasTop + Math.round(panY) + Math.round(10 * zoom);
        String id = topLevelFirst();
        View last = null;
        int guard = 0;
        while (id != null && guard++ < FareScript.MAX_NODES) {
            final View view = viewById.get(id);
            if (view == null) {
                break;
            }
            last = view;
            id = view.nextSiblingId;
        }
        return last == null ? defaultTop : last.top + last.height + gapScreen;
    }

    private void moveStatement(String id, DropTarget target) {
        final ScriptNode node = script.node(id);
        if (node == null || target == null) {
            return;
        }
        if (target.nodeId() != null && (target.nodeId().equals(id)
                || isDescendant(target.nodeId(), id))) {
            return;
        }
        if (target.branch() != null && target.branch().ownerId() != null
                && !target.branch().ownerId().isEmpty()
                && (target.branch().ownerId().equals(id) || isDescendant(target.branch().ownerId(), id))) {
            return;
        }
        if (target.kind() == DropKind.BRANCH_APPEND
                && branchDepth(target.branch()) >= MAX_BRANCH_DEPTH) {
            error = Component.translatable("gui.trdm.script.limit_depth").getString();
            return;
        }
        if (!dragUndoPushed) {
            pushUndo();
        }
        detach(id);
        node.x = 0;
        node.y = 0;
        switch (target.kind()) {
            case PORT_BEFORE -> insertIntoBranch(target.branch(), id, target.nodeId());
            case PORT_AFTER -> insertAfter(target.nodeId(), id);
            case BRANCH_APPEND -> appendToBranch(target.branch(), id);
        }
        selectedStatement = id;
        selectedValue = null;
        popupVisible = true;
        error = "";
    }

    /** 把嵌套语句拖到 C 形块外，按落点放回顶层。 */
    private void moveOutToTop(String id, double mouseX, double mouseY) {
        final ScriptNode node = script.node(id);
        if (node == null) {
            return;
        }
        if (!dragUndoPushed) {
            pushUndo();
        }
        detach(id);
        layout();
        final int baseLeft = canvasLeft + Math.round(panX) + Math.round(16 * zoom);
        final int baseTop = topLevelAppendTop();
        node.x = (int) Math.round((((mouseX - baseLeft) / zoom)) / 5) * 5;
        node.y = (int) Math.round((((mouseY - baseTop) / zoom)) / 5) * 5;
        appendToBranch(new BranchKey("", Branch.TOP), id);
        selectedStatement = id;
        selectedValue = null;
        popupVisible = true;
        error = "";
    }

    private boolean canAddStatement(BranchKey key) {
        if (statementCount() >= MAX_STATEMENTS) {
            error = Component.translatable("gui.trdm.script.limit_statements").getString();
            return false;
        }
        if (branchDepth(key) >= MAX_BRANCH_DEPTH) {
            error = Component.translatable("gui.trdm.script.limit_depth").getString();
            return false;
        }
        return true;
    }

    private int branchDepth(BranchKey key) {
        if (key == null || key.branch() == Branch.TOP) {
            return 0;
        }
        final View owner = viewById.get(key.ownerId());
        return owner == null ? 0 : branchDepth(owner.branch) + 1;
    }

    private int statementCount() {
        return ScriptTree.countStatements(script);
    }

    private ScriptNode newNumberValue(String type) {
        final ScriptNode value = new ScriptNode(Scripts.uniqueId(script, "v"), type);
        switch (type) {
            case "number" -> value.fields.put("value", "3.0");
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

    private ScriptNode newBooleanValue(String type) {
        final ScriptNode value = new ScriptNode(Scripts.uniqueId(script, "v"), type);
        switch (type) {
            case "compare" -> {
                value.fields.put("op", ">=");
                value.inputs.put("a", newNumberValue("context_intervals").id);
                value.inputs.put("b", newNumberValue("number").id);
            }
            case "and", "or" -> {
                value.inputs.put("a", newBooleanValue("compare").id);
                value.inputs.put("b", newBooleanValue("compare").id);
            }
            case "not" -> value.inputs.put("a", newBooleanValue("compare").id);
            default -> {
            }
        }
        script.nodes.add(value);
        return value;
    }

    private void dropNewValue(String type, double mouseX, double mouseY) {
        final ValueTarget target = valueTargetAt(mouseX, mouseY);
        if (target == null || !typeFits(target.booleanSlot(), type)) {
            return;
        }
        pushUndo();
        final ScriptNode value = isBoolean(type) ? newBooleanValue(type) : newNumberValue(type);
        script.node(target.ownerId()).inputs.put(target.slot(), value.id);
        selectedValue = value.id;
        selectedStatement = containingStatement(value.id);
        error = "";
        pruneUnreachable();
    }

    private void selectChip(ChipArea chip) {
        final ScriptNode owner = script.node(chip.ownerId());
        if (owner == null) {
            return;
        }
        String childId = owner.inputs.get(chip.slot());
        if (childId == null || script.node(childId) == null) {
            pushUndo();
            final String type = chip.booleanSlot() ? "compare" : "number";
            final ScriptNode value = isBoolean(type) ? newBooleanValue(type) : newNumberValue(type);
            owner.inputs.put(chip.slot(), value.id);
            childId = value.id;
        }
        selectedValue = childId;
        selectedStatement = containingStatement(childId);
        popupVisible = true;
        error = "";
        rebuild();
    }

    private ValueTarget valueTargetAt(double mouseX, double mouseY) {
        final ChipArea chip = chipAt(mouseX, mouseY);
        if (chip != null) {
            return new ValueTarget(chip.ownerId(), chip.slot(), chip.booleanSlot());
        }
        if (insidePopup(mouseX, mouseY)) {
            return null;
        }
        final View view = viewAt(mouseX, mouseY);
        if (view == null) {
            return null;
        }
        if (view.isIf && mouseY <= view.top + view.headerH) {
            return new ValueTarget(view.node.id, "condition", true);
        }
        if (inValueChip(view, mouseX, mouseY)) {
            return new ValueTarget(view.node.id, "value", false);
        }
        return null;
    }

    private ChipArea chipAt(double mouseX, double mouseY) {
        for (final ChipArea chip : popupChips) {
            if (mouseX >= chip.x() && mouseX < chip.x() + chip.width()
                    && mouseY >= chip.y() && mouseY < chip.y() + chip.height()) {
                return chip;
            }
        }
        return null;
    }

    private static boolean typeFits(boolean booleanSlot, ScriptNode value) {
        if (value == null) {
            return false;
        }
        return booleanSlot ? isBoolean(value.type) : isNumeric(value.type);
    }

    private static boolean typeFits(boolean booleanSlot, String type) {
        if (type == null) {
            return false;
        }
        return booleanSlot ? isBoolean(type) : isNumeric(type);
    }

    private void cycleValueType() {
        pushUndo();
        final ScriptNode selected = selectedValueNode();
        String ownerId = null;
        String slot = null;
        ScriptNode old = selected;
        if (selected != null) {
            final ValueOwner owner = ownerOfValue(selected.id);
            if (owner != null) {
                ownerId = owner.ownerId();
                slot = owner.slot();
            }
        } else {
            final ScriptNode statement = selectedStatementNode();
            if (statement != null && valueStatement(statement.type)) {
                ownerId = statement.id;
                slot = "value";
                old = script.node(statement.inputs.get("value"));
            }
        }
        if (ownerId == null || slot == null) {
            return;
        }
        if (old != null && isBoolean(old.type)) {
            return;
        }
        final ScriptNode replacement;
        if (old == null || !isNumeric(old.type)) {
            replacement = newNumberValue("number");
        } else {
            int index = 0;
            for (int i = 0; i < NUMERIC_VALUE_TYPES.length; i++) {
                if (NUMERIC_VALUE_TYPES[i].equals(old.type)) {
                    index = i;
                    break;
                }
            }
            final String type = NUMERIC_VALUE_TYPES[(index + 1) % NUMERIC_VALUE_TYPES.length];
            replacement = newNumberValue(type);
        }
        script.node(ownerId).inputs.put(slot, replacement.id);
        selectedValue = replacement.id;
        selectedStatement = containingStatement(replacement.id);
        error = "";
        pruneUnreachable();
        rebuild();
    }

    private void cycleStatementType() {
        final ScriptNode node = selectedStatementNode();
        if (node == null || "if".equals(node.type)) {
            return;
        }
        pushUndo();
        int index = 0;
        for (int i = 0; i < STATEMENT_TYPES.length; i++) {
            if (STATEMENT_TYPES[i].equals(node.type)) {
                index = i;
                break;
            }
        }
        final String oldType = node.type;
        node.type = STATEMENT_TYPES[(index + 1) % STATEMENT_TYPES.length];
        if (valueStatement(oldType) && !valueStatement(node.type)) {
            node.inputs.remove("value");
        }
        if (valueStatement(node.type) && script.node(node.inputs.get("value")) == null) {
            node.inputs.put("value", newNumberValue("number").id);
        }
        selectedValue = null;
        error = "";
        pruneUnreachable();
        rebuild();
    }

    private void deleteSelected() {
        if (selectedValue != null) {
            if (!canDeleteValue(selectedValue)) {
                return;
            }
            deleteValue(selectedValue);
            return;
        }
        if (selectedStatement != null) {
            deleteStatement(selectedStatement, true);
            rebuild();
        }
    }

    private void deleteValue(String id) {
        pushUndo();
        removeValueReferences(id);
        selectedValue = null;
        error = "";
        pruneUnreachable();
        rebuild();
    }

    private void deleteStatement(String id, boolean recordUndo) {
        if (recordUndo) {
            pushUndo();
        }
        detach(id);
        final Set<String> remove = new HashSet<>();
        collectStatementTree(id, remove);
        script.nodes.removeIf(node -> remove.contains(node.id));
        selectedStatement = null;
        selectedValue = null;
        popupVisible = false;
        error = "";
        pruneUnreachable();
    }

    private void collectStatementTree(String id, Set<String> remove) {
        ScriptTree.collectSubtree(script, id, remove);
    }

    private void removeValueReferences(String valueId) {
        ScriptTree.removeValueReferences(script, valueId);
    }

    private ValueOwner ownerOfValue(String valueId) {
        final ScriptTree.ValueOwner owner = ScriptTree.ownerOfValue(script, valueId);
        return owner == null ? null : new ValueOwner(owner.ownerId(), owner.slot());
    }

    private String containingStatement(String valueId) {
        final String found = ScriptTree.containingStatement(script, valueId);
        return found == null ? selectedStatement : found;
    }

    private void detach(String id) {
        ScriptTree.detach(script, id);
    }

    private void insertAfter(String targetId, String id) {
        final View target = viewById.get(targetId);
        if (target == null) {
            return;
        }
        final String next = ScriptTree.next(script, target.node.id);
        ScriptTree.insertBefore(script, slot(target.branch), id, next);
    }

    private void insertIntoBranch(BranchKey key, String id, String beforeId) {
        ScriptTree.insertBefore(script, slot(key), id, beforeId);
    }

    private void appendToBranch(BranchKey key, String id) {
        ScriptTree.append(script, slot(key), id);
    }

    private boolean isDescendant(String candidate, String ancestor) {
        return ScriptTree.isDescendant(script, candidate, ancestor);
    }

    private void pruneUnreachable() {
        ScriptTree.prune(script);
    }

    // ===== 参数浮窗 =====

    private void computePopup() {
        popupChips.clear();
        if (!popupVisible || selectedStatement == null || selectedStatementNode() == null) {
            popupVisible = false;
            popupW = 0;
            popupH = 0;
            return;
        }
        final ScriptNode value = popupValue();
        final ScriptNode selected = selectedValueNode();
        popupW = Math.min(320, Math.max(220, this.width - 24));
        if (selected != null && "compare".equals(selected.type)) {
            popupH = 146;
        } else if (selected != null && ("and".equals(selected.type) || "or".equals(selected.type))) {
            popupH = 146;
        } else if (selected != null && "not".equals(selected.type)) {
            popupH = 128;
        } else if (value != null && isTable(value.type)) {
            popupH = 168;
        } else {
            popupH = 108;
        }

        final View anchor = viewById.get(selectedStatement);
        if (anchor == null) {
            popupVisible = false;
            return;
        }
        int px = anchor.left + anchor.width + 10;
        int py = anchor.top - 6;
        if (px + popupW > this.width - 8) {
            final int leftOption = anchor.left - popupW - 10;
            if (leftOption >= 8) {
                px = leftOption;
            } else {
                px = clamp(anchor.left + 10, 8, Math.max(8, this.width - popupW - 8));
                py = anchor.top + anchor.height + 6;
            }
        }
        popupX = clamp(px, 8, Math.max(8, this.width - popupW - 8));
        popupY = clamp(py, 44, Math.max(44, this.height - popupH - 40));

        if (selected == null) {
            return;
        }
        if ("compare".equals(selected.type)) {
            addChip(selected.id, "a", false, popupY + 52);
            addChip(selected.id, "b", false, popupY + 76);
        } else if ("and".equals(selected.type) || "or".equals(selected.type)) {
            addChip(selected.id, "a", true, popupY + 52);
            addChip(selected.id, "b", true, popupY + 76);
        } else if ("not".equals(selected.type)) {
            addChip(selected.id, "a", true, popupY + 52);
        }
    }

    private void addChip(String ownerId, String slot, boolean booleanSlot, int y) {
        popupChips.add(new ChipArea(ownerId, slot, booleanSlot, popupX + 66, y,
                Math.max(40, popupW - 78), 20));
    }

    private void addPopupWidgets() {
        final ScriptNode statement = selectedStatementNode();
        final ScriptNode selected = selectedValueNode();
        final ScriptNode value = popupValue();
        if (statement == null) {
            return;
        }
        final int x = popupX + 10;
        final int y = popupY + 8;
        final int buttonY = popupY + popupH - 26;

        if ("if".equals(statement.type) && selected == null) {
            final boolean hasCondition = script.node(statement.inputs.get("condition")) != null;
            addRenderableWidget(Button.builder(Component.translatable(hasCondition
                                    ? "gui.trdm.script.edit_condition" : "gui.trdm.script.add_condition"),
                            button -> {
                                ScriptNode condition = script.node(statement.inputs.get("condition"));
                                if (condition == null) {
                                    pushUndo();
                                    condition = newBooleanValue("compare");
                                    statement.inputs.put("condition", condition.id);
                                    error = "";
                                }
                                selectedValue = condition.id;
                                popupVisible = true;
                                rebuild();
                            })
                    .bounds(x, popupY + 46, 110, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.delete"),
                            button -> deleteSelected())
                    .bounds(x + 118, popupY + 46, 64, 18).build());
            return;
        }

        if (value != null && "number".equals(value.type)) {
            final EditBox box = editBox(x + 60, y + 22, 90, 18,
                    Component.translatable("gui.trdm.script.unit"));
            box.setValue(value.fields.getOrDefault("value", "0"));
            box.setResponder(text -> value.fields.put("value", text));
            addRenderableWidget(box);
        }
        if (value != null && isTable(value.type)) {
            syncTierScroll(value);
            addTierWidgets(value, x, y);
        }
        if ("round_fare".equals(statement.type) && value == null) {
            addRenderableWidget(Button.builder(
                            Component.literal(statement.fields.getOrDefault("mode", "HALF")),
                            button -> {
                                pushUndo();
                                statement.fields.put("mode",
                                        nextMode(statement.fields.getOrDefault("mode", "HALF")));
                                rebuild();
                            })
                    .bounds(x + 60, y + 22, 70, 18).build());
            final EditBox unit = editBox(x + 136, y + 22, 70, 18,
                    Component.translatable("gui.trdm.script.unit"));
            unit.setValue(statement.fields.getOrDefault("unit", "0.1"));
            unit.setResponder(text -> statement.fields.put("unit", text));
            addRenderableWidget(unit);
        }
        if (selected != null && "compare".equals(selected.type)) {
            addRenderableWidget(Button.builder(
                            Component.literal(selected.fields.getOrDefault("op", ">=")),
                            button -> {
                                pushUndo();
                                selected.fields.put("op",
                                        nextOp(selected.fields.getOrDefault("op", ">=")));
                                rebuild();
                            })
                    .bounds(x + 60, y + 22, 70, 18).build());
        }

        int bx = x;
        if (selected != null) {
            final ValueOwner owner = ownerOfValue(selected.id);
            if (owner != null) {
                addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.back"),
                                button -> {
                                    final ScriptNode ownerNode = script.node(owner.ownerId());
                                    selectedValue = ownerNode != null && isBoolean(ownerNode.type)
                                            ? ownerNode.id : null;
                                    selectedStatement = containingStatement(owner.ownerId());
                                    popupVisible = true;
                                    rebuild();
                                })
                        .bounds(bx, buttonY, 76, 18).build());
                bx += 82;
            }
        }
        if (selected != null && isNumeric(selected.type)) {
            addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.change_value"),
                            button -> cycleValueType())
                    .bounds(bx, buttonY, 84, 18).build());
            bx += 90;
        } else if (selected == null && valueStatement(statement.type)) {
            addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.change_value"),
                            button -> cycleValueType())
                    .bounds(bx, buttonY, 84, 18).build());
            bx += 90;
        }
        if (selected == null && !"if".equals(statement.type)) {
            addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.change_type"),
                            button -> cycleStatementType())
                    .bounds(bx, buttonY, 84, 18).build());
            bx += 90;
        }
        if (selected == null || canDeleteValue(selected.id)) {
            addRenderableWidget(Button.builder(Component.translatable(selected != null
                                    && isTopCondition(selected.id)
                                    ? "gui.trdm.script.delete_condition" : "gui.trdm.script.delete"),
                            button -> deleteSelected())
                    .bounds(bx, buttonY, 76, 18).build());
        }
    }

    private boolean canDeleteValue(String id) {
        if (isTopCondition(id)) {
            return true;
        }
        final ValueOwner owner = ownerOfValue(id);
        if (owner == null) {
            return false;
        }
        return "value".equals(owner.slot());
    }

    private boolean isTopCondition(String id) {
        for (final ScriptNode node : script.nodes) {
            if ("if".equals(node.type) && id.equals(node.inputs.get("condition"))) {
                return true;
            }
        }
        return false;
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
                        pushUndo();
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
                            pushUndo();
                            table.tiers.add(insert, new ScriptTier(next, 0.0));
                            tierScroll = clamp(Math.max(tierScroll, insert - TIER_VISIBLE + 1), 0,
                                    Math.max(0, table.tiers.size() - TIER_VISIBLE));
                            error = "";
                            rebuild();
                        })
                .bounds(x + 164, y + 42, 80, 18).build());
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

    private ScriptNode selectedStatementNode() {
        return script.node(selectedStatement);
    }

    private ScriptNode selectedValueNode() {
        return script.node(selectedValue);
    }

    /** 浮窗实际编辑的值：优先选中的值，否则是选中语句自身的值槽。 */
    private ScriptNode popupValue() {
        final ScriptNode selected = selectedValueNode();
        if (selected != null) {
            return selected;
        }
        final ScriptNode statement = selectedStatementNode();
        if (statement != null && valueStatement(statement.type)) {
            return script.node(statement.inputs.get("value"));
        }
        return null;
    }

    private ScriptNode effectiveTable() {
        final ScriptNode value = popupValue();
        return value != null && isTable(value.type) ? value : null;
    }

    private void save() {
        final ScriptCompiler.Result result = ScriptCompiler.compile(script);
        if (result.compiled() == null) {
            error = String.join("；", result.errors());
            rebuild();
            return;
        }
        final FareRule current = FareRuntime.get().config().ruleById(rule.id);
        final FareRule target = current != null ? current : rule;
        target.script = script.copy();
        FareRuntime.get().save();
        minecraft.setScreen(parent);
    }

    private void pushUndo() {
        if (restoring) {
            return;
        }
        undoStack.push(script.copy());
        while (undoStack.size() > 50) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        redoStack.push(script.copy());
        applySnapshot(undoStack.pop());
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            return;
        }
        undoStack.push(script.copy());
        applySnapshot(redoStack.pop());
    }

    private void applySnapshot(FareScript snapshot) {
        restoring = true;
        script.nodes.clear();
        for (final ScriptNode node : snapshot.nodes) {
            script.nodes.add(node.copy());
        }
        script.hat = snapshot.hat;
        script.schemaVersion = snapshot.schemaVersion;
        selectedStatement = null;
        selectedValue = null;
        popupVisible = false;
        error = "";
        restoring = false;
        rebuild();
    }

    // ===== 工具 =====

    private static boolean valueStatement(String type) {
        return "set_fare".equals(type) || "add_fare".equals(type) || "multiply_fare".equals(type)
                || "cap_fare".equals(type) || "floor_fare".equals(type);
    }

    private static boolean isBoolean(String type) {
        return "compare".equals(type) || "and".equals(type) || "or".equals(type) || "not".equals(type);
    }

    private static boolean isNumeric(String type) {
        return NUMERIC_VALUE_TYPES.length > 0 && List.of(NUMERIC_VALUE_TYPES).contains(type);
    }

    private static boolean isTable(String type) {
        return "table_intervals".equals(type) || "table_meters".equals(type);
    }

    private static int colorFor(String type) {
        return switch (type) {
            case "if" -> 0xFFB06A1F;
            case "compare", "and", "or", "not" -> 0xFF3E8E5A;
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

    private String valueSummary(String id) {
        return valueSummary(id, 0);
    }

    private String valueSummary(String id, int depth) {
        if (depth > 6) {
            return "…";
        }
        final ScriptNode node = script.node(id);
        if (node == null) {
            return "?";
        }
        return switch (node.type) {
            case "number" -> node.fields.getOrDefault("value", "0");
            case "table_intervals", "table_meters" -> blockLabel(node.type) + "(" + node.tiers.size() + ")";
            case "compare" -> valueSummary(node.inputs.get("a"), depth + 1) + " "
                    + node.fields.getOrDefault("op", ">=") + " "
                    + valueSummary(node.inputs.get("b"), depth + 1);
            case "and" -> valueSummary(node.inputs.get("a"), depth + 1) + " 且 "
                    + valueSummary(node.inputs.get("b"), depth + 1);
            case "or" -> valueSummary(node.inputs.get("a"), depth + 1) + " 或 "
                    + valueSummary(node.inputs.get("b"), depth + 1);
            case "not" -> "非 " + valueSummary(node.inputs.get("a"), depth + 1);
            default -> blockLabel(node.type);
        };
    }

    private String conditionSummary(String id) {
        return valueSummary(id);
    }

    private String fit(String text, int width) {
        if (text == null) {
            return "";
        }
        if (font.width(text) <= width) {
            return text;
        }
        final String cut = font.plainSubstrByWidth(text, Math.max(0, width - font.width("…")));
        return cut + "…";
    }

    private static String nextOp(String op) {
        for (int index = 0; index < OPS.length; index++) {
            if (OPS[index].equals(op)) {
                return OPS[(index + 1) % OPS.length];
            }
        }
        return OPS[0];
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Z) {
                undo();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                && popupVisible && this.getFocused() == null) {
            popupVisible = false;
            rebuild();
            return true;
        }
        if ((keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE)
                && this.getFocused() == null
                && (selectedStatement != null || selectedValue != null)) {
            deleteSelected();
            rebuild();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
