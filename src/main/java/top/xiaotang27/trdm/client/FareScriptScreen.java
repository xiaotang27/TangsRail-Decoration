package top.xiaotang27.trdm.client;

import mtr.client.ClientData;
import mtr.data.Route;
import mtr.data.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import top.xiaotang27.trdm.fare.FareConfig;
import top.xiaotang27.trdm.fare.FareGraph;
import top.xiaotang27.trdm.fare.FareMoney;
import top.xiaotang27.trdm.fare.FareRule;
import top.xiaotang27.trdm.fare.FareStation;
import top.xiaotang27.trdm.fare.script.FareScript;
import top.xiaotang27.trdm.fare.script.PassSettings;
import top.xiaotang27.trdm.fare.script.PassScriptCompiler;
import top.xiaotang27.trdm.fare.script.PassScripts;
import top.xiaotang27.trdm.fare.script.ScriptCompiler;
import top.xiaotang27.trdm.fare.script.ScriptContext;
import top.xiaotang27.trdm.fare.script.ScriptException;
import top.xiaotang27.trdm.fare.script.ScriptNode;
import top.xiaotang27.trdm.fare.script.ScriptTier;
import top.xiaotang27.trdm.fare.script.ScriptTree;
import top.xiaotang27.trdm.fare.script.ScriptTree.Branch;
import top.xiaotang27.trdm.fare.script.Scripts;
import top.xiaotang27.trdm.ticket.PassRule;
import top.xiaotang27.trdm.ticket.PassRules;
import top.xiaotang27.trdm.ticket.TrdmClock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
            "set_fare", "add_fare", "multiply_fare", "discount_fare",
            "cap_fare", "floor_fare", "round_fare", "free_ride", "surcharge"};
    private static final String[] PASS_STATEMENT_TYPES = {
            "pass_price", "pass_days", "pass_discount", "pass_daily_limit"};
    private static final String[] NUMERIC_VALUE_TYPES = {
            "number", "context_intervals", "context_meters", "context_stations",
            "context_zone_diff", "card_rule_rides", "card_rule_amount", "card_rule_distance",
            "time_hour", "time_minute", "time_weekday",
            "card_day_rides", "card_day_amount", "card_month_rides", "card_month_amount",
            "table_intervals", "table_meters"};
    private static final String[] ARITHMETIC_VALUE_TYPES = {
            "add", "subtract", "multiply", "divide", "min", "max"};
    private static final String[] OPS = {"<", "<=", ">", ">=", "==", "!="};

    private static final int BLOCK_H = 58;
    private static final int INDENT = 18;
    private static final int IF_HEADER = 40;
    private static final int IF_DIVIDER = 24;
    private static final int IF_SLOT_MIN = 26;
    private static final int IF_FOOTER = 10;
    private static final int MAX_STATEMENTS = 24;
    private static final int MAX_BRANCH_DEPTH = 4;

    private static final int PALETTE_X = 8;
    private static final int PALETTE_W = 154;
    private static final int PALETTE_TOP = 76;
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

    private record TrialLine(long routeId, String name, List<Long> stationIds) {
    }

    private static final List<PaletteEntry> PALETTE = buildPalette();
    private static final List<PaletteEntry> PASS_PALETTE = buildPassPalette();

    private static List<PaletteEntry> buildPalette() {
        final List<PaletteEntry> list = new ArrayList<>();
        list.add(new PaletteEntry("gui.trdm.script.category.control", null, false, true, 0xFF8C5A20));
        list.add(new PaletteEntry("gui.trdm.script.block.if", "if", true, false, colorFor("if")));
        list.add(new PaletteEntry("gui.trdm.script.block.if_else", "if_else", true, false,
                colorFor("if")));
        list.add(new PaletteEntry("gui.trdm.script.category.conditions", null, false, true, 0xFF2F6E45));
        for (final String type : List.of("compare", "and", "or", "not", "line_is",
                "is_weekend", "card_has_policy")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.fare", null, false, true, 0xFF2F7D4F));
        for (final String type : List.of("set_fare", "add_fare", "multiply_fare",
                "discount_fare", "cap_fare", "floor_fare", "free_ride", "surcharge")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, true, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.block.use_rule", "use_rule", true, false,
                colorFor("use_rule")));
        list.add(new PaletteEntry("gui.trdm.script.category.round", null, false, true, 0xFF2D6E82));
        list.add(new PaletteEntry("gui.trdm.script.block.round_fare", "round_fare", true, false,
                colorFor("round_fare")));
        list.add(new PaletteEntry("gui.trdm.script.category.values", null, false, true, 0xFF8A5A2B));
        for (final String type : List.of("number", "context_intervals", "context_meters",
                "context_stations", "context_zone_diff")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.loyalty", null, false, true, 0xFF9A6428));
        for (final String type : List.of("card_rule_rides", "card_rule_amount",
                "card_rule_distance")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.card_period", null, false, true, 0xFF7A5A9A));
        for (final String type : List.of("card_day_rides", "card_day_amount",
                "card_month_rides", "card_month_amount")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.time", null, false, true, 0xFF2D6E82));
        for (final String type : List.of("time_hour", "time_minute", "time_weekday")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.math", null, false, true, 0xFF8A5A2B));
        for (final String type : ARITHMETIC_VALUE_TYPES) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.tables", null, false, true, 0xFF6B4A8A));
        for (final String type : List.of("table_intervals", "table_meters")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.comment", null, false, true, 0xFFB38B2E));
        list.add(new PaletteEntry("gui.trdm.script.block.comment", "comment", false, false,
                colorFor("comment")));
        return list;
    }

    private static List<PaletteEntry> buildPassPalette() {
        final List<PaletteEntry> list = new ArrayList<>();
        list.add(new PaletteEntry("gui.trdm.script.category.pass", null, false, true, 0xFF2F7D4F));
        for (final String type : List.of("pass_price", "pass_days", "pass_discount", "pass_daily_limit")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, true, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.control", null, false, true, 0xFF8C5A20));
        list.add(new PaletteEntry("gui.trdm.script.block.if", "if", true, false, colorFor("if")));
        list.add(new PaletteEntry("gui.trdm.script.block.if_else", "if_else", true, false, colorFor("if")));
        list.add(new PaletteEntry("gui.trdm.script.category.conditions", null, false, true, 0xFF2F6E45));
        for (final String type : List.of("compare", "and", "or", "not")) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.values", null, false, true, 0xFF8A5A2B));
        list.add(new PaletteEntry("gui.trdm.script.block.number", "number", false, false, colorFor("number")));
        list.add(new PaletteEntry("gui.trdm.script.category.math", null, false, true, 0xFF8A5A2B));
        for (final String type : ARITHMETIC_VALUE_TYPES) {
            list.add(new PaletteEntry("gui.trdm.script.block." + type, type, false, false, colorFor(type)));
        }
        list.add(new PaletteEntry("gui.trdm.script.category.comment", null, false, true, 0xFFB38B2E));
        list.add(new PaletteEntry("gui.trdm.script.block.comment", "comment", false, false,
                colorFor("comment")));
        return list;
    }

    private List<PaletteEntry> palette() {
        return passRule == null ? PALETTE : PASS_PALETTE;
    }

    private List<PaletteEntry> visiblePalette() {
        final List<PaletteEntry> all = palette();
        final List<PaletteEntry> result = new ArrayList<>();
        final String query = paletteQuery == null ? "" : paletteQuery.trim().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            for (final PaletteEntry entry : all) {
                if (entry.header()) {
                    continue;
                }
                final String label = Component.translatable(entry.labelKey()).getString()
                        .toLowerCase(Locale.ROOT);
                if (label.contains(query) || entry.type().toLowerCase(Locale.ROOT).contains(query)) {
                    result.add(entry);
                }
            }
            return result;
        }
        boolean skip = false;
        for (final PaletteEntry entry : all) {
            if (entry.header()) {
                skip = collapsedCategories.contains(entry.labelKey());
                result.add(entry);
            } else if (!skip) {
                result.add(entry);
            }
        }
        return result;
    }

    private int paletteY(int index) {
        int y = PALETTE_TOP;
        final List<PaletteEntry> entries = visiblePalette();
        for (int i = 0; i < index && i < entries.size(); i++) {
            y += entries.get(i).header() ? PALETTE_HEADER : PALETTE_ROW;
        }
        return y;
    }

    private int paletteTotalHeight() {
        return paletteY(visiblePalette().size()) - PALETTE_TOP;
    }

    private int paletteViewHeight() {
        return Math.max(0, this.height - PALETTE_TOP - 12);
    }

    private float paletteMaxScroll() {
        return Math.max(0, paletteTotalHeight() - paletteViewHeight());
    }

    private PaletteEntry paletteEntryAt(double mx, double my) {
        if (mx < PALETTE_X || mx >= PALETTE_X + PALETTE_W
                || my < PALETTE_TOP || my >= PALETTE_TOP + paletteViewHeight()) {
            return null;
        }
        for (int index = 0; index < visiblePalette().size(); index++) {
            final PaletteEntry entry = visiblePalette().get(index);
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

    private record LineOption(long id, String name) {
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
        private boolean hasElse;
        private boolean firstInChain;
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
    private final PassRule passRule;
    private final FareScript script;
    private String error = "";

    private int canvasLeft;
    private int canvasTop;
    private int canvasRight;
    private int canvasBottom;
    private int blockW;
    private int blockH;
    private int indentScreen;
    private int slotMinScreen;
    private int ifHeaderScreen;
    private int ifDividerScreen;
    private int ifFooterScreen;
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
    private boolean operatorDropOpen;
    private int operatorFieldX;
    private int operatorFieldY;
    private int operatorFieldW;
    private int operatorFieldH;
    private int operatorListX;
    private int operatorListY;
    private int operatorListW;
    private int operatorListH;

    private boolean ruleDropOpen;
    private String ruleDropNodeId;
    private int ruleListX;
    private int ruleListY;
    private int ruleListW;
    private int ruleListH;
    private int ruleDropScroll;
    private boolean lineDropOpen;
    private int lineFieldX;
    private int lineFieldY;
    private int lineFieldW;
    private int lineFieldH;
    private int lineListX;
    private int lineListY;
    private int lineListW;
    private int lineListH;
    private int lineDropScroll;
    private final List<String> missingRules = new ArrayList<>();
    private boolean warningsDismissed;
    private int noticeX;
    private int noticeY;
    private int noticeW;
    private int noticeH;

    private final Deque<FareScript> undoStack = new ArrayDeque<>();
    private final Deque<FareScript> redoStack = new ArrayDeque<>();
    private boolean restoring;
    private boolean dragMoved;
    private boolean dirty;
    private boolean confirmCloseOpen;
    private boolean backspaceHeld;
    private EditBox backspaceBox;
    private int backspaceDelay;

    private String selectedStatement;
    private String selectedValue;
    private List<ScriptNode> clipboard = List.of();
    private String clipboardRootId;
    private boolean clipboardStatement;
    private String dragStatementId;
    private String dragValueId;
    private String dragStatementType;
    private String dragValueType;
    private String dragCommentType;
    private String dragCommentId;
    private double commentDragOffsetX;
    private double commentDragOffsetY;
    private String lastCommentClickId = "";
    private long lastCommentClickAt;
    private boolean commentUndoPushed;
    private ScriptNode editingComment;
    private String commentEditBuffer = "";
    private int commentCaret;
    private int commentAnchor;

    private boolean trialPanelOpen;
    private boolean trialCollapsed;
    private int trialLineIndex;
    private int trialStartIndex;
    private int trialPolicyIndex;
    private boolean trialLineDropOpen;
    private boolean trialStartDropOpen;
    private boolean trialPolicyDropOpen;
    private float trialScroll;
    private float trialLineDropScroll;
    private float trialStartDropScroll;
    private float trialPolicyDropScroll;
    private boolean trialDirty = true;
    private String trialError = "";
    private final List<TrialLine> trialLines = new ArrayList<>();
    private final List<Long> trialFares = new ArrayList<>();
    private boolean traceOpen;
    private int traceStationIndex = -1;
    private top.xiaotang27.trdm.fare.script.ExecutionTrace trace;
    private float traceScroll;
    private String traceError = "";
    private double dragX;
    private double dragY;
    private DropTarget statementDrop;
    private boolean dropOnSelf;
    private boolean overTrash;
    private String tierScrollNode;
    private int tierScroll;
    private float paletteScroll;
    private String paletteQuery = "";
    private final Set<String> collapsedCategories = new HashSet<>();
    private EditBox paletteSearch;

    public FareScriptScreen(Screen parent, FareRule rule) {
        this(parent, rule, null);
    }

    public FareScriptScreen(Screen parent, PassRule passRule) {
        this(parent, null, passRule);
    }

    private FareScriptScreen(Screen parent, FareRule rule, PassRule passRule) {
        super(passRule == null
                ? Component.translatable("gui.trdm.script.title")
                : Component.translatable("gui.trdm.script.pass_title"));
        this.parent = parent;
        this.rule = rule;
        this.passRule = passRule;
        if (passRule != null) {
            passRule.ensureScript();
            this.script = passRule.script.copy();
            PassScripts.ensureHat(this.script);
        } else {
            this.script = rule.script == null ? Scripts.empty() : rule.script.copy();
            Scripts.ensureHat(this.script);
        }
        refreshMissingRules();
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
        this.canvasBottom = this.height - 12;
        layout();
        this.paletteScroll = Math.max(0, Math.min(paletteMaxScroll(), paletteScroll));
        computePopup();

        final int saveX = this.width - 300;
        final int backX = this.width - 232;
        addRenderableWidget(new TrdmEditorButton(saveX, 6, 64, 20,
                Component.translatable("gui.trdm.ticket.editor.save"), button -> save()));
        addRenderableWidget(new TrdmEditorButton(backX, 6, 64, 20,
                Component.translatable("gui.trdm.ticket.back"), button -> onClose()));
        paletteSearch = new EditBox(this.font, PALETTE_X, 38, PALETTE_W, 16,
                Component.translatable("gui.trdm.script.search"));
        paletteSearch.setValue(paletteQuery);
        paletteSearch.setResponder(value -> {
            paletteQuery = value == null ? "" : value;
            paletteScroll = 0F;
        });
        addRenderableWidget(paletteSearch);
        addRenderableWidget(new TrdmEditorButton(this.width - 164, 6, 64, 20,
                Component.translatable("gui.trdm.script.undo"), button -> undo()));
        addRenderableWidget(new TrdmEditorButton(this.width - 96, 6, 64, 20,
                Component.translatable("gui.trdm.script.redo"), button -> redo()));
        final TrdmEditorButton copy = new TrdmEditorButton(78, 6, 48, 20,
                Component.translatable("gui.trdm.script.copy"), button -> copySelection());
        copy.active = selectedStatement != null || selectedValue != null;
        addRenderableWidget(copy);
        final TrdmEditorButton paste = new TrdmEditorButton(130, 6, 48, 20,
                Component.translatable("gui.trdm.script.paste"), button -> pasteClipboard());
        paste.active = !clipboard.isEmpty();
        addRenderableWidget(paste);
        final TrdmEditorButton disable = new TrdmEditorButton(182, 6, 56, 20,
                Component.translatable(selectedStatement != null && script.node(selectedStatement) != null
                        && script.node(selectedStatement).disabled
                        ? "gui.trdm.script.enable" : "gui.trdm.script.disable"),
                button -> toggleDisabled());
        disable.active = selectedStatement != null && script.node(selectedStatement) != null;
        addRenderableWidget(disable);
        final TrdmEditorButton trial = new TrdmEditorButton(242, 6, 56, 20,
                Component.translatable("gui.trdm.script.trial.button"), button -> openTrial());
        trial.active = rule != null;
        addRenderableWidget(trial);

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
        indentScreen = Math.max(14, Math.round(INDENT * zoom));
        slotMinScreen = Math.max(18, Math.round(IF_SLOT_MIN * zoom));
        ifHeaderScreen = Math.max(32, Math.round(IF_HEADER * zoom));
        ifDividerScreen = Math.max(18, Math.round(IF_DIVIDER * zoom));
        ifFooterScreen = Math.max(6, Math.round(IF_FOOTER * zoom));

        final ScriptNode hat = script.node(script.hat);
        if (hat == null || hat.next == null || hat.next.isBlank()) {
            return;
        }
        final int baseLeft = canvasLeft + Math.round(panX) + Math.round(20 * zoom);
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
            view.left = baseLeft;
            view.top = cursor;
            view.firstInChain = id.equals(firstId);
            view.width = Math.max(120, blockW - depth * indentScreen);
            view.isIf = "if".equals(node.type);
            view.nextSiblingId = view.isIf ? node.after : node.next;

            // 先加入列表：父块在子块之前，反向命中的是子块。
            views.add(view);
            viewById.put(node.id, view);

            if (view.isIf) {
                view.headerH = ifHeaderScreen;
                view.hasElse = hasElseBranch(node);
                view.condLeft = view.left + 10 + font.width(blockLabel("if")) + 8;
                view.condTop = view.top + Math.max(5, Math.round(6 * zoom));
                view.condHeight = Math.max(16, view.headerH - 2 * Math.max(5, Math.round(6 * zoom)));
                final int thenLabelWidth = view.width >= 200
                        ? font.width(Component.translatable("gui.trdm.script.then").getString()) + 6 : 0;
                view.condWidth = Math.max(40,
                        view.width - (view.condLeft - view.left) - 10 - thenLabelWidth);

                final int bodyLeft = view.left + indentScreen;
                final int thenBase = view.top + view.headerH;
                final int thenBottom = layoutChain(node.next, bodyLeft, thenBase, depth + 1,
                        new BranchKey(node.id, Branch.THEN), laidOut);
                final int thenEnd = Math.max(thenBase + slotMinScreen, thenBottom);
                view.thenTop = thenBase;
                view.thenBottom = thenEnd;

                if (view.hasElse) {
                    view.elseHeaderTop = thenEnd;
                    final int elseBase = view.elseHeaderTop + ifDividerScreen;
                    final int elseBottom = layoutChain(node.otherwise, bodyLeft, elseBase, depth + 1,
                            new BranchKey(node.id, Branch.ELSE), laidOut);
                    final int elseEnd = Math.max(elseBase + slotMinScreen, elseBottom);
                    view.elseTop = elseBase;
                    view.elseBottom = elseEnd;
                    view.height = elseEnd + ifFooterScreen - view.top;
                } else {
                    view.elseHeaderTop = thenEnd;
                    view.elseTop = thenEnd;
                    view.elseBottom = thenEnd;
                    view.height = thenEnd + ifFooterScreen - view.top;
                }
            } else {
                view.height = blockH;
            }

            maxBottom = Math.max(maxBottom, view.top + view.height);
            cursor = view.top + view.height;
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
        if (view.hasElse && my >= view.elseTop && my < view.elseBottom) {
            return Branch.ELSE;
        }
        return null;
    }

    private DropTarget computeStatementDrop(double mx, double my, String exclude) {
        overTrash = inTrash(mx, my);
        dropOnSelf = false;
        if (overTrash) {
            return null;
        }
        final View under = viewAt(mx, my);
        if (under != null) {
            if (exclude != null && (under.node.id.equals(exclude)
                    || isDescendant(under.node.id, exclude))) {
                dropOnSelf = true;
                return null;
            }
            final Branch body = under.isIf ? branchInIfBody(under, mx, my) : null;
            if (body != null) {
                return new DropTarget(DropKind.BRANCH_APPEND, under.node.id,
                        new BranchKey(under.node.id, body));
            }
            final boolean before = my < under.top + under.height / 2.0;
            return new DropTarget(before ? DropKind.PORT_BEFORE : DropKind.PORT_AFTER,
                    under.node.id, under.branch);
        }
        if (inCanvas(mx, my)) {
            final View first = viewById.get(topLevelFirst());
            if (first != null && my < first.top) {
                return new DropTarget(DropKind.PORT_BEFORE, first.node.id, first.branch);
            }
            final View last = lastTopLevelView();
            if (last != null && my > last.top + last.height) {
                return new DropTarget(DropKind.PORT_AFTER, last.node.id, last.branch);
            }
        }
        return null;
    }

    private View lastTopLevelView() {
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
        return last;
    }

    private int trashX() {
        return canvasLeft + 12;
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
        fill(guiGraphics, 0, 0, this.width, 30, 0xFF5B4B8A);
        drawString(guiGraphics, Component.translatable("gui.trdm.script.title").getString(), 16, 11, 0xFFFFFF);
        drawPalette(guiGraphics, mouseX, mouseY);
        final ValueTarget valueTarget = (dragValueType != null || dragValueId != null)
                ? valueTargetAt(mouseX, mouseY) : null;
        drawCanvas(guiGraphics, mouseX, mouseY, valueTarget);
        drawMinimap(guiGraphics);
        drawTrialPanel(guiGraphics, mouseX, mouseY);
        if (popupVisible) {
            drawPopup(guiGraphics);
            drawPopupLabels(guiGraphics, valueTarget);
            drawTierScrollbar(guiGraphics);
        }
        if (dragStatementType != null || dragValueType != null || dragStatementId != null
                || dragValueId != null || dragCommentType != null) {
            drawGhost(guiGraphics);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        drawRuleDropdown(guiGraphics, mouseX, mouseY);
        drawLineDropdown(guiGraphics, mouseX, mouseY);
        drawOperatorDropdown(guiGraphics, mouseX, mouseY);
        drawTraceWindow(guiGraphics);
        drawNotice(guiGraphics);
        if (confirmCloseOpen) {
            drawConfirmClose(guiGraphics);
        }
    }

    private void openTrace(int stationIndex) {
        final List<Long> stations = trialStations();
        if (rule == null || stationIndex < 0 || stationIndex >= stations.size()) {
            return;
        }
        traceStationIndex = stationIndex;
        traceOpen = true;
        traceScroll = 0F;
        traceError = "";
        trace = null;
        final FareConfig config = FareRuntime.get().config();
        final FareGraph graph = FareRuntime.get().graph();
        final TrialLine line = trialLines.get(trialLineIndex);
        final long start = trialStartStation();
        final long target = stations.get(stationIndex);
        if (start == target) {
            trace = new top.xiaotang27.trdm.fare.script.ExecutionTrace(List.of(), 0.0);
            return;
        }
        final FareGraph.Path path = graph.shortest(start, target, config.metric);
        if (path == null || !path.connected()) {
            traceError = Component.translatable("gui.trdm.script.trial.unreachable").getString();
            return;
        }
        final ScriptCompiler.Result compiled = ScriptCompiler.compile(script);
        if (!compiled.errors().isEmpty()) {
            traceError = String.join("；", compiled.errors());
            return;
        }
        final FareStation from = graph.station(start);
        final FareStation to = graph.station(target);
        final Set<String> visiting = new HashSet<>();
        visiting.add(rule.id);
        try {
            final top.xiaotang27.trdm.fare.script.ExecutionTrace raw = compiled.compiled().runTraced(
                    new ScriptContext(path.intervals(), path.meters(),
                            from == null ? 0 : from.zone(), to == null ? 0 : to.zone(),
                            line.routeId(), line.name(), 0L, 0L, 0L,
                            TrdmClock.hour(), TrdmClock.minute(), TrdmClock.weekday(),
                            0L, 0L, 0L, 0L, trialPolicyIds()),
                    config.ruleResolver(visiting));
            trace = raw;
            if (rule.maxFareCents > 0L && raw.finalFare() * 100.0 > rule.maxFareCents) {
                final java.util.List<top.xiaotang27.trdm.fare.script.TraceStep> steps =
                        new ArrayList<>(raw.steps());
                steps.add(new top.xiaotang27.trdm.fare.script.TraceStep(steps.size(),
                        "cap", "rule_cap", "规则封顶 " + FareMoney.yuan(rule.maxFareCents),
                        raw.finalFare(), rule.maxFareCents / 100.0));
                trace = new top.xiaotang27.trdm.fare.script.ExecutionTrace(steps,
                        rule.maxFareCents / 100.0);
            }
        } catch (RuntimeException exception) {
            traceError = String.valueOf(exception.getMessage());
        }
    }

    private void drawTrialPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!trialPanelOpen || rule == null) {
            return;
        }
        ensureTrialLines();
        if (trialDirty) {
            rebuildTrialData();
        }
        final int x = trialPanelX();
        final int y = trialPanelY();
        final int w = trialPanelWidth();
        final int h = trialPanelHeight();
        fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, 0xFF8A8A8A);
        fill(guiGraphics, x, y, w, h, 0xB0161616);
        fill(guiGraphics, x, y, w, 22, 0xD02A2A2A);
        drawString(guiGraphics, Component.translatable("gui.trdm.script.trial.title").getString(),
                x + 8, y + 7, 0xFFFFFF);
        drawString(guiGraphics, trialCollapsed ? "▸" : "▾", x + w - 18, y + 7, 0xFFFFFF);
        if (trialCollapsed) {
            return;
        }
        final int controlX = x + 8;
        final int controlW = w - 16;
        final int lineY = y + 30;
        final int startY = y + 54;
        final int policyY = y + 78;
        drawTrialSelector(guiGraphics, controlX, lineY, controlW, 18,
                Component.translatable("gui.trdm.script.trial.line").getString() + " · "
                        + (trialLines.isEmpty()
                        ? Component.translatable("gui.trdm.script.trial.no_line").getString()
                        : trialLines.get(trialLineIndex).name()), trialLineDropOpen);
        drawTrialSelector(guiGraphics, controlX, startY, controlW, 18,
                Component.translatable("gui.trdm.script.trial.from").getString() + " · "
                        + (trialLines.isEmpty() ? "-" : trialStationName(trialStartStation())),
                trialStartDropOpen);
        drawTrialSelector(guiGraphics, controlX, policyY, controlW, 18,
                Component.translatable("gui.trdm.script.trial.policy").getString() + " · "
                        + trialPolicyName(), trialPolicyDropOpen);

        final int listX = x + 8;
        final int listY = y + 104;
        final int listW = w - 16;
        final int listH = Math.max(40, h - 112);
        guiGraphics.enableScissor(listX, listY, listX + listW, listY + listH);
        final List<Long> stations = trialStations();
        final float maxScroll = Math.max(0F, stations.size() * 22F - listH);
        trialScroll = Math.max(0F, Math.min(maxScroll, trialScroll));
        for (int index = 0; index < stations.size(); index++) {
            final int rowY = Math.round(listY + index * 22F - trialScroll);
            if (rowY + 22 < listY || rowY > listY + listH) {
                continue;
            }
            final long stationId = stations.get(index);
            final boolean start = index == trialStartIndex;
            final boolean hovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + 22;
            if (hovered) {
                fill(guiGraphics, listX, rowY, listW, 22, 0x33FFFFFF);
            }
            fill(guiGraphics, listX + 9, rowY + 4, 2, 15, 0xFF7A7A7A);
            fill(guiGraphics, listX + 5, rowY + 7, 10, 10, start ? 0xFF39C46A : 0xFFB8B8B8);
            fill(guiGraphics, listX + 7, rowY + 9, 6, 6, start ? 0xFF1C7A3A : 0xFF5A5A5A);
            drawString(guiGraphics, fit(trialStationName(stationId), listW - 88),
                    listX + 24, rowY + 6, start ? 0xFFA8FFC0 : 0xFFFFFFFF);
            final long fare = index < trialFares.size() ? trialFares.get(index) : -1L;
            final String price = fare < 0L ? "--" : FareMoney.yuan(fare);
            drawString(guiGraphics, price, listX + listW - this.font.width(price) - 4,
                    rowY + 6, fare < 0L ? 0xFFAAAAAA : 0xFF55E080);
        }
        guiGraphics.disableScissor();
        if (maxScroll > 0F) {
            final int barX = listX + listW - 3;
            fill(guiGraphics, barX, listY, 3, listH, 0x66FFFFFF);
            final int thumbH = Math.max(16, Math.round(listH * listH / (stations.size() * 22F)));
            final int thumbY = listY + Math.round((listH - thumbH) * trialScroll / maxScroll);
            fill(guiGraphics, barX, thumbY, 3, thumbH, 0xFF55E080);
        }
        if (!trialError.isEmpty()) {
            drawString(guiGraphics, fit(trialError, w - 16), x + 8, y + h - 12, 0xFFFF9090);
        }
        drawTrialDropdown(guiGraphics, trialLineDropOpen, lineY + 19, controlW,
                trialLineNames(), trialLineIndex, trialLineDropScroll);
        drawTrialDropdown(guiGraphics, trialStartDropOpen, startY + 19, controlW,
                trialStationNames(), trialStartIndex, trialStartDropScroll);
        drawTrialDropdown(guiGraphics, trialPolicyDropOpen, policyY + 19, controlW,
                trialPolicyNames(), trialPolicyIndex, trialPolicyDropScroll);
    }

    private void drawTrialSelector(GuiGraphics guiGraphics, int x, int y, int w, int h,
                                   String text, boolean open) {
        fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, 0xFF8A8A8A);
        fill(guiGraphics, x, y, w, h, open ? 0xCC2A5A38 : 0xCC1E1E1E);
        drawString(guiGraphics, fit(text, w - 24), x + 6, y + 5, 0xFFFFFF);
        drawString(guiGraphics, open ? "▴" : "▾", x + w - 14, y + 5, 0xC8FFD0);
    }

    private void drawTrialDropdown(GuiGraphics guiGraphics, boolean open, int y, int w,
                                   List<String> options, int selected, float scroll) {
        if (!open || options.isEmpty()) {
            return;
        }
        final int x = trialPanelX() + 8;
        final int visible = Math.min(8, options.size());
        final int h = visible * 16 + 4;
        final int popupY = Math.min(y, canvasBottom - h - 4);
        fill(guiGraphics, x - 1, popupY - 1, w + 2, h + 2, 0xFF9A9A9A);
        fill(guiGraphics, x, popupY, w, h, 0xF0202020);
        final float max = Math.max(0F, options.size() * 16F - (h - 4));
        final float clamped = Math.max(0F, Math.min(max, scroll));
        guiGraphics.enableScissor(x, popupY + 2, x + w, popupY + h - 2);
        for (int index = 0; index < options.size(); index++) {
            final int rowY = Math.round(popupY + 2 + index * 16F - clamped);
            if (rowY + 16 < popupY || rowY > popupY + h) {
                continue;
            }
            if (index == selected) {
                fill(guiGraphics, x + 1, rowY, w - 2, 16, 0xFF2A5A38);
            }
            drawString(guiGraphics, fit(options.get(index), w - 12), x + 6, rowY + 4, 0xFFFFFF);
        }
        guiGraphics.disableScissor();
    }

    private int trialPanelX() {
        return canvasRight - trialPanelWidth() - 10;
    }

    private int trialPanelY() {
        return canvasTop + 92;
    }

    private int trialPanelWidth() {
        return 240;
    }

    private int trialPanelHeight() {
        return trialCollapsed ? 22 : Math.min(350, canvasBottom - trialPanelY() - 8);
    }

    private void ensureTrialLines() {
        if (!trialLines.isEmpty()) {
            return;
        }
        for (final Route route : ClientData.ROUTES) {
            final List<Long> stationIds = new ArrayList<>();
            for (final Route.RoutePlatform platform : route.platformIds) {
                final Station station = ClientData.DATA_CACHE.platformIdToStation.get(platform.platformId);
                if (station == null) {
                    continue;
                }
                if (stationIds.isEmpty() || stationIds.get(stationIds.size() - 1) != station.id) {
                    stationIds.add(station.id);
                }
            }
            if (stationIds.size() >= 2) {
                trialLines.add(new TrialLine(route.id, route.name == null || route.name.isBlank()
                        ? Component.translatable("gui.trdm.script.trial.unnamed_line").getString()
                        : route.name, List.copyOf(stationIds)));
            }
        }
        trialLineIndex = 0;
        trialStartIndex = 0;
        trialDirty = true;
    }

    private List<Long> trialStations() {
        return trialLines.isEmpty() ? List.of() : trialLines.get(trialLineIndex).stationIds();
    }

    private long trialStartStation() {
        final List<Long> stations = trialStations();
        if (stations.isEmpty()) {
            return -1L;
        }
        return stations.get(Math.max(0, Math.min(trialStartIndex, stations.size() - 1)));
    }

    private String trialStationName(long id) {
        final FareStation station = FareRuntime.get().graph().station(id);
        return station == null ? Long.toString(id) : station.name();
    }

    private List<String> trialLineNames() {
        final List<String> result = new ArrayList<>();
        for (final TrialLine line : trialLines) {
            result.add(line.name());
        }
        return result;
    }

    private List<String> trialStationNames() {
        final List<String> result = new ArrayList<>();
        for (final long id : trialStations()) {
            result.add(trialStationName(id));
        }
        return result;
    }

    private List<String> trialPolicyNames() {
        final List<String> result = new ArrayList<>();
        result.add(Component.translatable("gui.trdm.script.trial.no_policy").getString());
        for (final top.xiaotang27.trdm.fare.DiscountPolicy policy
                : FareRuntime.get().config().discountPolicies) {
            result.add(policy.name());
        }
        return result;
    }

    private String trialPolicyName() {
        if (trialPolicyIndex <= 0) {
            return Component.translatable("gui.trdm.script.trial.no_policy").getString();
        }
        final List<top.xiaotang27.trdm.fare.DiscountPolicy> policies =
                FareRuntime.get().config().discountPolicies;
        final int index = trialPolicyIndex - 1;
        return index >= 0 && index < policies.size()
                ? policies.get(index).name()
                : Component.translatable("gui.trdm.script.trial.no_policy").getString();
    }

    private java.util.Set<String> trialPolicyIds() {
        if (trialPolicyIndex <= 0) {
            return java.util.Set.of();
        }
        final List<top.xiaotang27.trdm.fare.DiscountPolicy> policies =
                FareRuntime.get().config().discountPolicies;
        final int index = trialPolicyIndex - 1;
        return index >= 0 && index < policies.size()
                ? java.util.Set.of(policies.get(index).id()) : java.util.Set.of();
    }

    private void rebuildTrialData() {
        trialDirty = false;
        trialFares.clear();
        trialError = "";
        if (rule == null || trialLines.isEmpty()) {
            return;
        }
        final List<Long> stations = trialStations();
        for (int index = 0; index < stations.size(); index++) {
            trialFares.add(-1L);
        }
        final FareConfig config = FareRuntime.get().config();
        final FareGraph graph = FareRuntime.get().graph();
        final ScriptCompiler.Result compiled = ScriptCompiler.compile(script);
        if (!compiled.errors().isEmpty()) {
            trialError = String.join("；", compiled.errors());
            return;
        }
        final TrialLine line = trialLines.get(trialLineIndex);
        final long start = trialStartStation();
        final Set<String> visiting = new HashSet<>();
        visiting.add(rule.id);
        for (int index = 0; index < stations.size(); index++) {
            final long stationId = stations.get(index);
            if (stationId == start) {
                trialFares.set(index, 0L);
                continue;
            }
            final FareGraph.Path path = graph.shortest(start, stationId, config.metric);
            if (path == null || !path.connected()) {
                continue;
            }
            final FareStation from = graph.station(start);
            final FareStation to = graph.station(stationId);
            try {
                final double fare = compiled.compiled().run(new ScriptContext(
                        path.intervals(), path.meters(),
                        from == null ? 0 : from.zone(), to == null ? 0 : to.zone(),
                        line.routeId(), line.name(), 0L, 0L, 0L,
                        TrdmClock.hour(), TrdmClock.minute(), TrdmClock.weekday(),
                        0L, 0L, 0L, 0L, trialPolicyIds()), config.ruleResolver(visiting));
                long cents = Math.max(0L, Math.round(fare * 100.0));
                if (rule.maxFareCents > 0L) {
                    cents = Math.min(cents, rule.maxFareCents);
                }
                trialFares.set(index, cents);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private boolean handleTrialClick(double mx, double my) {
        if (!trialPanelOpen || rule == null) {
            return false;
        }
        final int x = trialPanelX();
        final int y = trialPanelY();
        final int w = trialPanelWidth();
        final int h = trialPanelHeight();
        if (mx >= x && mx < x + w && my >= y && my < y + 22) {
            trialCollapsed = !trialCollapsed;
            return true;
        }
        if (trialCollapsed) {
            return false;
        }
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            trialLineDropOpen = false;
            trialStartDropOpen = false;
            trialPolicyDropOpen = false;
            return false;
        }
        if (trialLineDropOpen) {
            if (handleTrialDropdownClick(mx, my, y + 49, w, trialLineNames().size(),
                    trialLineDropScroll, index -> {
                        trialLineIndex = index;
                        trialStartIndex = 0;
                        trialDirty = true;
                        trialLineDropOpen = false;
                    })) {
                return true;
            }
        }
        if (trialStartDropOpen) {
            if (handleTrialDropdownClick(mx, my, y + 73, w, trialStationNames().size(),
                    trialStartDropScroll, index -> {
                        trialStartIndex = index;
                        trialDirty = true;
                        trialStartDropOpen = false;
                    })) {
                return true;
            }
        }
        if (trialPolicyDropOpen) {
            if (handleTrialDropdownClick(mx, my, y + 97, w, trialPolicyNames().size(),
                    trialPolicyDropScroll, index -> {
                        trialPolicyIndex = index;
                        trialDirty = true;
                        trialPolicyDropOpen = false;
                    })) {
                return true;
            }
        }
        if (mx >= x + 8 && mx < x + w - 8 && my >= y + 30 && my < y + 48) {
            trialLineDropOpen = !trialLineDropOpen;
            trialStartDropOpen = false;
            trialPolicyDropOpen = false;
            return true;
        }
        if (mx >= x + 8 && mx < x + w - 8 && my >= y + 54 && my < y + 72) {
            trialStartDropOpen = !trialStartDropOpen;
            trialLineDropOpen = false;
            trialPolicyDropOpen = false;
            return true;
        }
        if (mx >= x + 8 && mx < x + w - 8 && my >= y + 78 && my < y + 96) {
            trialPolicyDropOpen = !trialPolicyDropOpen;
            trialLineDropOpen = false;
            trialStartDropOpen = false;
            return true;
        }
        final int listY = y + 104;
        final int listH = Math.max(40, h - 112);
        if (mx >= x + 8 && mx < x + w - 8 && my >= listY && my < listY + listH) {
            final int index = (int) Math.floor((my - listY + trialScroll) / 22F);
            if (index >= 0 && index < trialStations().size()) {
                openTrace(index);
                return true;
            }
        }
        return true;
    }

    private boolean handleTrialDropdownClick(double mx, double my, int y, int w, int count,
                                             float scroll, java.util.function.IntConsumer select) {
        final int x = trialPanelX() + 8;
        final int visible = Math.min(8, count);
        final int h = visible * 16 + 4;
        final int popupY = Math.min(y, canvasBottom - h - 4);
        if (mx < x || mx >= x + w || my < popupY || my >= popupY + h || count == 0) {
            return false;
        }
        final int index = (int) Math.floor((my - popupY - 2 + scroll) / 16F);
        if (index >= 0 && index < count) {
            select.accept(index);
            return true;
        }
        return true;
    }

    private boolean handleTrialScroll(double mouseX, double mouseY, double delta) {
        if (!trialPanelOpen || trialCollapsed || rule == null) {
            return false;
        }
        final int x = trialPanelX();
        final int y = trialPanelY();
        final int w = trialPanelWidth();
        final int h = trialPanelHeight();
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            return false;
        }
        if (trialLineDropOpen) {
            trialLineDropScroll = Math.max(0F, trialLineDropScroll - (float) delta * 16F);
            return true;
        }
        if (trialStartDropOpen) {
            trialStartDropScroll = Math.max(0F, trialStartDropScroll - (float) delta * 16F);
            return true;
        }
        if (trialPolicyDropOpen) {
            trialPolicyDropScroll = Math.max(0F, trialPolicyDropScroll - (float) delta * 16F);
            return true;
        }
        final int listY = y + 104;
        final int listH = Math.max(40, h - 112);
        if (mouseY >= listY) {
            final float max = Math.max(0F, trialStations().size() * 22F - listH);
            trialScroll = Math.max(0F, Math.min(max, trialScroll - (float) delta * 22F));
        }
        return true;
    }

    private int traceX() {
        return canvasLeft + (canvasRight - canvasLeft - 380) / 2;
    }

    private int traceY() {
        return canvasTop + (canvasBottom - canvasTop - 286) / 2;
    }

    private void drawTraceWindow(GuiGraphics guiGraphics) {
        if (!traceOpen) {
            return;
        }
        final int x = traceX();
        final int y = traceY();
        final int w = 380;
        final int h = 286;
        fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, 0xFF9A9A9A);
        fill(guiGraphics, x, y, w, h, 0xF0161616);
        fill(guiGraphics, x, y, w, 22, 0xD02A2A2A);
        final String start = trialLines.isEmpty() ? "-" : trialStationName(trialStartStation());
        final String end = traceStationIndex >= 0 && traceStationIndex < trialStations().size()
                ? trialStationName(trialStations().get(traceStationIndex)) : "-";
        drawString(guiGraphics, Component.translatable("gui.trdm.script.trace.title").getString()
                + "  " + start + " → " + end, x + 8, y + 7, 0xFFFFFF);
        drawString(guiGraphics, "×", x + w - 16, y + 6, 0xFFFFFF);
        if (!traceError.isEmpty()) {
            drawString(guiGraphics, fit(traceError, w - 16), x + 8, y + 34, 0xFFFF9090);
            return;
        }
        if (trace == null) {
            return;
        }
        final int listY = y + 30;
        final int listW = w - 16;
        final int listH = h - 62;
        guiGraphics.enableScissor(x + 8, listY, x + 8 + listW, listY + listH);
        int rowY = listY + 4 - Math.round(traceScroll);
        for (final top.xiaotang27.trdm.fare.script.TraceStep step : trace.steps()) {
            if (rowY + 24 >= listY && rowY <= listY + listH) {
                final String index = Integer.toString(step.index() + 1);
                drawString(guiGraphics, index, x + 12, rowY + 7, 0xFF9A9A9A);
                drawString(guiGraphics, fit(step.detail(), listW - 112), x + 32, rowY + 7,
                        colorForStep(step.type()));
                final String fare = String.format(java.util.Locale.ROOT, "%.2f → %.2f",
                        step.fareBefore(), step.fareAfter());
                drawString(guiGraphics, fare, x + 8 + listW - this.font.width(fare) - 4,
                        rowY + 7, 0xFF8DE0A0);
                fill(guiGraphics, x + 10, rowY + 23, listW - 4, 1, 0x33FFFFFF);
            }
            rowY += 24;
        }
        guiGraphics.disableScissor();
        final float total = trace.steps().size() * 24F;
        final float max = Math.max(0F, total - listH);
        traceScroll = Math.max(0F, Math.min(max, traceScroll));
        if (max > 0F) {
            final int barX = x + w - 10;
            fill(guiGraphics, barX, listY, 3, listH, 0x66FFFFFF);
            final int thumbH = Math.max(14, Math.round(listH * listH / total));
            final int thumbY = listY + Math.round((listH - thumbH) * traceScroll / max);
            fill(guiGraphics, barX, thumbY, 3, thumbH, 0xFF8DE0A0);
        }
        final String finalText = Component.translatable("gui.trdm.script.trace.final",
                String.format(java.util.Locale.ROOT, "%.2f", trace.finalFare())).getString();
        drawString(guiGraphics, finalText, x + 12, y + h - 20, 0xFF55E080);
    }

    private static int colorForStep(String type) {
        return switch (type) {
            case "set_fare", "add_fare", "multiply_fare", "discount_fare",
                 "free_ride", "surcharge", "cap_fare", "floor_fare", "round_fare",
                 "use_rule", "rule_cap" -> 0xFFE8E8E8;
            case "if" -> 0xFFFFC85A;
            default -> 0xFFB8B8B8;
        };
    }

    private boolean handleTraceClick(double mx, double my) {
        if (!traceOpen) {
            return false;
        }
        final int x = traceX();
        final int y = traceY();
        final int w = 380;
        final int h = 286;
        if (mx >= x + w - 22 && mx < x + w - 4 && my >= y + 4 && my < y + 20) {
            traceOpen = false;
            return true;
        }
        if (mx < x || mx >= x + w || my < y || my >= y + h) {
            traceOpen = false;
            return true;
        }
        return true;
    }

    private boolean handleTraceScroll(double mx, double my, double delta) {
        if (!traceOpen) {
            return false;
        }
        final int x = traceX();
        final int y = traceY();
        if (mx < x || mx >= x + 380 || my < y + 30 || my >= y + 286 - 32) {
            return false;
        }
        final int listH = 286 - 62;
        final float total = trace == null ? 0F : trace.steps().size() * 24F;
        final float max = Math.max(0F, total - listH);
        traceScroll = Math.max(0F, Math.min(max, traceScroll - (float) delta * 24F));
        return true;
    }

    private void drawMinimap(GuiGraphics guiGraphics) {
        if (views.isEmpty()) {
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (final View view : views) {
            minX = Math.min(minX, view.left);
            minY = Math.min(minY, view.top);
            maxX = Math.max(maxX, view.left + view.width);
            maxY = Math.max(maxY, view.top + view.height);
        }
        final int mapW = 104;
        final int mapH = 74;
        final int mapX = canvasRight - mapW - 10;
        final int mapY = canvasTop + 8;
        final int contentW = Math.max(1, maxX - minX);
        final int contentH = Math.max(1, maxY - minY);
        final float scale = Math.min((mapW - 8F) / contentW, (mapH - 8F) / contentH);
        fill(guiGraphics, mapX - 1, mapY - 1, mapW + 2, mapH + 2, 0xCC303030);
        fill(guiGraphics, mapX, mapY, mapW, mapH, 0xE6FFFFFF);
        guiGraphics.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
        for (final View view : views) {
            final int x = mapX + 4 + Math.round((view.left - minX) * scale);
            final int y = mapY + 4 + Math.round((view.top - minY) * scale);
            final int w = Math.max(2, Math.round(view.width * scale));
            final int h = Math.max(2, Math.round(view.height * scale));
            fill(guiGraphics, x, y, w, h,
                    view.node.disabled ? 0xFF999999 : colorFor(view.node.type));
        }
        guiGraphics.disableScissor();
    }

    private void drawPalette(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        final int top = 34;
        final int panelH = this.height - top - 8;
        fill(guiGraphics, PALETTE_X, top, PALETTE_W, panelH, PANEL_BORDER);
        fill(guiGraphics, PALETTE_X + 1, top + 1, PALETTE_W - 2, panelH - 2, PANEL_BG);

        final int viewH = paletteViewHeight();
        guiGraphics.enableScissor(PALETTE_X + 1, PALETTE_TOP, PALETTE_X + PALETTE_W - 1,
                PALETTE_TOP + viewH);
        for (int index = 0; index < visiblePalette().size(); index++) {
            final PaletteEntry entry = visiblePalette().get(index);
            final int y = Math.round(paletteY(index) - paletteScroll);
            final int entryH = entry.header() ? PALETTE_HEADER - 2 : 21;
            if (y + entryH < PALETTE_TOP || y > PALETTE_TOP + viewH) {
                continue;
            }
            if (entry.header()) {
                fill(guiGraphics, PALETTE_X + 1, y, PALETTE_W - 2, PALETTE_HEADER - 2, entry.color());
                drawString(guiGraphics, (collapsedCategories.contains(entry.labelKey()) ? "▶ " : "▼ ")
                                + Component.translatable(entry.labelKey()).getString(),
                        PALETTE_X + 8, y + 4, 0xFFFFFF);
            } else {
                final boolean hover = mouseX >= PALETTE_X + 6 && mouseX < PALETTE_X + PALETTE_W - 6
                        && mouseY >= y && mouseY < y + 21;
                drawPaletteBlock(guiGraphics, PALETTE_X + 6, y, PALETTE_W - 12, 21,
                        entry.color(), Component.translatable(entry.labelKey()).getString(), hover,
                        entry.statement());
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
    }

    private void drawPaletteBlock(GuiGraphics guiGraphics, int x, int y, int w, int h, int color,
                                  String label, boolean hover, boolean statement) {
        final int fillColor = hover ? brighten(color) : color;
        if (statement) {
            // 可上下拼接的语句：带顶部凸起和底部凹槽的小积木。
            fill(guiGraphics, x + 6, y - 4, 14, 4, 0xFF2A2A2A);
            fill(guiGraphics, x + 7, y - 3, 12, 3, fillColor);
            fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, 0xFF2A2A2A);
            fill(guiGraphics, x, y, w, h, fillColor);
            fill(guiGraphics, x, y, w, 2, 0x45FFFFFF);
            fill(guiGraphics, x + 6, y + h - 4, 14, 4, PANEL_BG);
            fill(guiGraphics, x + 6, y + h - 4, 14, 1, 0xFF2A2A2A);
            drawString(guiGraphics, label, x + 7, y + 6, 0xFFFFFF);
            // 右侧双横线提示“可以接在下面”。
            fill(guiGraphics, x + w - 14, y + 8, 7, 1, 0xC8FFFFFF);
            fill(guiGraphics, x + w - 14, y + 11, 7, 1, 0xC8FFFFFF);
        } else {
            // 只能填进槽位的值：圆角胶囊形，没有上下接口。
            fill(guiGraphics, x + 2, y - 1, w - 4, h + 2, 0xFF2A2A2A);
            fill(guiGraphics, x - 1, y + 3, w + 2, h - 6, 0xFF2A2A2A);
            fill(guiGraphics, x + 2, y, w - 4, h, fillColor);
            fill(guiGraphics, x, y + 3, w, h - 6, fillColor);
            fill(guiGraphics, x + 3, y + 1, w - 6, 2, 0x45FFFFFF);
            drawString(guiGraphics, label, x + 8, y + 6, 0xFFFFFF);
            // 左侧圆点提示“值/填充块”。
            fill(guiGraphics, x + 3, y + h / 2 - 1, 2, 3, 0xD0FFFFFF);
        }
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

        // 先画所有 C 形块的“外壳”，再按顺序画块内容；块与块直接咬合，没有连接线。
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
            final boolean hovered = mouseX >= view.left && mouseX <= view.left + view.width
                    && mouseY >= view.top && mouseY <= view.top + view.height;
            if (view.isIf) {
                drawIfDetails(guiGraphics, view, valueTarget);
            } else {
                drawStatement(guiGraphics, view, valueTarget, view.node.id.equals(selectedStatement),
                        hovered);
            }
        }
        // 空画布不显示文字提示。
        drawComments(guiGraphics);

        // 拖放反馈画在块内容之上。
        if (statementDrop != null) {
            if (statementDrop.kind() == DropKind.BRANCH_APPEND) {
                drawBranchHighlight(guiGraphics, statementDrop.branch());
            } else {
                drawDropGap(guiGraphics, statementDrop);
            }
        }
        if (dragStatementId != null || dragStatementType != null) {
            final int tx = trashX();
            final int ty = trashY();
            fill(guiGraphics, tx, ty, 88, 32, overTrash ? 0xFFC0392B : 0xFFE8E8E8);
            fill(guiGraphics, tx, ty, 88, 2, 0xFF9A9A9A);
            final int icon = overTrash ? 0xFFFFFFFF : 0xFF5A5A5A;
            fill(guiGraphics, tx + 33, ty + 7, 22, 3, icon);
            fill(guiGraphics, tx + 39, ty + 5, 10, 2, icon);
            fill(guiGraphics, tx + 36, ty + 10, 16, 14, icon);
            fill(guiGraphics, tx + 39, ty + 12, 2, 9, overTrash ? 0xFFC0392B : 0xFFE8E8E8);
            fill(guiGraphics, tx + 44, ty + 12, 2, 9, overTrash ? 0xFFC0392B : 0xFFE8E8E8);
            fill(guiGraphics, tx + 49, ty + 12, 2, 9, overTrash ? 0xFFC0392B : 0xFFE8E8E8);
        }
        guiGraphics.disableScissor();
    }

    private int commentScreenX(ScriptNode node) {
        return canvasLeft + Math.round(panX) + Math.round(node.x * zoom);
    }

    private int commentScreenY(ScriptNode node) {
        return canvasTop + Math.round(panY) + Math.round(node.y * zoom);
    }

    private int commentWidth() {
        return Math.max(100, Math.round(160 * zoom));
    }

    private java.util.List<String> commentTextLines(ScriptNode node) {
        final String text = node == editingComment
                ? commentEditBuffer : node.fields.getOrDefault("text", "");
        if (text.isEmpty() && node != editingComment) {
            return List.of(Component.translatable("gui.trdm.script.comment.empty").getString());
        }
        return List.of(text.split("\\n", -1));
    }

    private int commentHeight(ScriptNode node) {
        return Math.max(26, commentTextLines(node).size() * 10 + 12);
    }

    private ScriptNode commentAt(double mx, double my) {
        for (int index = script.nodes.size() - 1; index >= 0; index--) {
            final ScriptNode node = script.nodes.get(index);
            if (!"comment".equals(node.type)) {
                continue;
            }
            final int x = commentScreenX(node);
            final int y = commentScreenY(node);
            if (mx >= x && mx < x + commentWidth() && my >= y && my < y + commentHeight(node)) {
                return node;
            }
        }
        return null;
    }

    private void drawComments(GuiGraphics guiGraphics) {
        for (final ScriptNode node : script.nodes) {
            if (!"comment".equals(node.type)) {
                continue;
            }
            final int x = commentScreenX(node);
            final int y = commentScreenY(node);
            final int w = commentWidth();
            final int h = commentHeight(node);
            if (x + w < canvasLeft || x > canvasRight || y + h < canvasTop || y > canvasBottom) {
                continue;
            }
            final boolean active = node.id.equals(dragCommentId) || node == editingComment;
            fill(guiGraphics, x + 2, y + 2, w, h, 0x44000000);
            fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, active ? 0xFF9A7420 : 0xFFD6B84A);
            fill(guiGraphics, x, y, w, h, 0xFFFFF3B0);
            fill(guiGraphics, x, y, w, 3, 0xFFE0C05A);
            guiGraphics.enableScissor(x, y + 3, x + w, y + h);
            final List<String> lines = commentTextLines(node);
            final int textX = x + 6;
            final int textY = y + 8;
            if (node == editingComment) {
                drawCommentSelection(guiGraphics, x + 6, textY, lines);
            }
            for (int index = 0; index < lines.size(); index++) {
                guiGraphics.drawString(this.font, lines.get(index), textX, textY + index * 10,
                        0xFF4A3A12, false);
            }
            if (node == editingComment) {
                final int caretLine = commentCaretLine();
                final int caretColumn = commentCaret - commentLineStart(caretLine);
                final int caretX = textX + this.font.width(commentLine(caretLine)
                        .substring(0, Math.min(caretColumn, commentLine(caretLine).length())));
                final int caretY = textY + caretLine * 10;
                fill(guiGraphics, caretX, caretY - 1, 1, 10, 0xFF2A1E00);
            }
            guiGraphics.disableScissor();
        }
    }

    private void drawCommentSelection(GuiGraphics guiGraphics, int x, int y, List<String> lines) {
        final int start = Math.min(commentAnchor, commentCaret);
        final int end = Math.max(commentAnchor, commentCaret);
        if (start == end) {
            return;
        }
        int offset = 0;
        for (int index = 0; index < lines.size(); index++) {
            final String line = lines.get(index);
            final int lineStart = offset;
            final int lineEnd = offset + line.length();
            final int from = Math.max(start, lineStart) - lineStart;
            final int to = Math.min(end, lineEnd) - lineStart;
            if (from < to) {
                final int left = x + this.font.width(line.substring(0, from));
                final int right = x + this.font.width(line.substring(0, to));
                fill(guiGraphics, left, y + index * 10 - 1, Math.max(1, right - left), 10, 0x553B5BDB);
            }
            offset = lineEnd + 1;
        }
    }

    private List<String> commentEditLines() {
        if (commentEditBuffer.isEmpty()) {
            return List.of("");
        }
        return List.of(commentEditBuffer.split("\\n", -1));
    }

    private String commentLine(int line) {
        final List<String> lines = commentEditLines();
        return line < 0 || line >= lines.size() ? "" : lines.get(line);
    }

    private int commentCaretLine() {
        int line = 0;
        for (int index = 0; index < Math.min(commentCaret, commentEditBuffer.length()); index++) {
            if (commentEditBuffer.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private int commentLineStart(int line) {
        int start = 0;
        for (int index = 0; index < line; index++) {
            final int newline = commentEditBuffer.indexOf('\n', start);
            if (newline < 0) {
                return commentEditBuffer.length();
            }
            start = newline + 1;
        }
        return start;
    }

    private void setCommentCaretAt(double mouseX, double mouseY, boolean extend) {
        if (editingComment == null) {
            return;
        }
        final List<String> lines = commentEditLines();
        final int textX = commentScreenX(editingComment) + 6;
        final int textY = commentScreenY(editingComment) + 8;
        int line = Mth.clamp((int) ((mouseY - textY) / 10.0), 0, lines.size() - 1);
        final String text = lines.get(line);
        int column = 0;
        while (column < text.length()) {
            final int width = this.font.width(text.substring(0, column + 1));
            if (textX + width > mouseX) {
                break;
            }
            column++;
        }
        commentCaret = commentLineStart(line) + column;
        if (!extend) {
            commentAnchor = commentCaret;
        }
    }

    private void createComment(double mouseX, double mouseY) {
        pushUndo();
        final ScriptNode node = new ScriptNode(Scripts.uniqueId(script, "c"), "comment");
        node.fields.put("text", "");
        node.x = Math.round((float) ((mouseX - canvasLeft - panX) / zoom));
        node.y = Math.round((float) ((mouseY - canvasTop - panY) / zoom));
        script.nodes.add(node);
        openCommentEditor(node);
    }

    private void openCommentEditor(ScriptNode node) {
        editingComment = node;
        commentEditBuffer = node.fields.getOrDefault("text", "");
        commentCaret = commentEditBuffer.length();
        commentAnchor = commentCaret;
        rebuild();
    }

    private void commitCommentEdit(boolean save) {
        if (editingComment == null) {
            return;
        }
        if (save) {
            editingComment.fields.put("text", commentEditBuffer.stripTrailing());
        }
        editingComment = null;
        commentEditBuffer = "";
        commentCaret = 0;
        commentAnchor = 0;
        rebuild();
    }

    private boolean inCommentEditor(double mx, double my) {
        if (editingComment == null) {
            return false;
        }
        final int x = commentScreenX(editingComment);
        final int y = commentScreenY(editingComment);
        return mx >= x && mx < x + commentWidth()
                && my >= y && my < y + commentHeight(editingComment);
    }

    private boolean visible(View view) {
        return view.top + view.height >= canvasTop && view.top <= canvasBottom
                && view.left + view.width >= canvasLeft && view.left <= canvasRight;
    }

    private boolean attachedAbove(View view) {
        return !view.firstInChain || view.branch.branch() != Branch.TOP;
    }

    private boolean attachedBelow(View view) {
        return view.branch.branch() != Branch.TOP
                || (view.nextSiblingId != null && viewById.containsKey(view.nextSiblingId));
    }

    private boolean hasNextBlock(View view) {
        return view.nextSiblingId != null && viewById.containsKey(view.nextSiblingId);
    }

    private boolean drawBottomGroove(View view) {
        return hasNextBlock(view) || view.branch.branch() == Branch.TOP;
    }

    private void drawIfBackground(GuiGraphics guiGraphics, View view, boolean selected, boolean hovered) {
        final int x = view.left;
        final int y = view.top;
        final int w = view.width;
        final int h = view.height;
        final int color = hovered ? brighten(colorFor("if")) : colorFor("if");
        final boolean above = attachedAbove(view);
        final boolean below = attachedBelow(view);
        fill(guiGraphics, x + 3, y + 4, w, h, 0x28000000);
        fill(guiGraphics, x - 1, y - 1, 1, h + 2, 0xFF2A2A2A);
        fill(guiGraphics, x + w, y - 1, 1, h + 2, 0xFF2A2A2A);
        fill(guiGraphics, x - 1, y - 1, w + 2, 1, 0xFF2A2A2A);
        fill(guiGraphics, x - 1, y + h, w + 2, 1, 0xFF2A2A2A);
        fill(guiGraphics, x, y, w, h, color);
        if (!above) {
            fill(guiGraphics, x, y, w, 3, 0x45FFFFFF);
        }

        // 顶部凹槽/凸起。
        fill(guiGraphics, x + 11, y - 5, 22, 6, 0xFF2A2A2A);
        fill(guiGraphics, x + 12, y - 4, 20, 4, color);

        // 挖空“那么”（和“否则”）分支，留下的左竖条和横条就是 C 形。
        final int bodyLeft = x + indentScreen;
        final int bodyWidth = w - indentScreen;
        fill(guiGraphics, bodyLeft, view.thenTop, bodyWidth, view.thenBottom - view.thenTop, CANVAS_BG);
        fill(guiGraphics, bodyLeft, view.thenTop, bodyWidth, 1, 0xFF2A2A2A);
        if (view.hasElse) {
            fill(guiGraphics, bodyLeft, view.elseTop, bodyWidth, view.elseBottom - view.elseTop, CANVAS_BG);
            fill(guiGraphics, bodyLeft, view.elseTop, bodyWidth, 1, 0xFF2A2A2A);
            fill(guiGraphics, x, view.elseHeaderTop, w, ifDividerScreen, color);
            fill(guiGraphics, bodyLeft, view.elseBottom - 1, bodyWidth, 1, 0xFF2A2A2A);
        }

        // 底部凸条和凹槽。
        final int footTop = view.hasElse ? view.elseBottom : view.thenBottom;
        fill(guiGraphics, x, footTop, w, ifFooterScreen, color);
        if (drawBottomGroove(view)) {
            fill(guiGraphics, x + 12, y + h - 5, 20, 5, CANVAS_BG);
            fill(guiGraphics, x + 12, y + h - 5, 20, 1, 0xFF2A2A2A);
        }
        if (!below) {
            fill(guiGraphics, x, y + h - 3, w, 3, 0x30000000);
        }

        drawString(guiGraphics, Component.translatable("gui.trdm.script.block.if").getString(),
                x + 10, y + 8, 0xFFFFFF);
        if (view.hasElse) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.else").getString(),
                    x + 10, view.elseHeaderTop + Math.max(3, (ifDividerScreen - 8) / 2), 0xFFFFFF);
        }

        if (selected) {
            fill(guiGraphics, x - 2, y - 2, w + 4, 2, 0xFFFFD45A);
            fill(guiGraphics, x - 2, y + h, w + 4, 2, 0xFFFFD45A);
            fill(guiGraphics, x - 2, y - 2, 2, h + 4, 0xFFFFD45A);
            fill(guiGraphics, x + w, y - 2, 2, h + 4, 0xFFFFD45A);
        }
    }

    private void drawIfDetails(GuiGraphics guiGraphics, View view, ValueTarget valueTarget) {
        drawConditionChip(guiGraphics, view, valueTarget);
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
        drawBlockShape(guiGraphics, x, top, w, view.height,
                node.disabled ? 0xFF777777 : colorFor(node.type), selected, hovered,
                attachedAbove(view), attachedBelow(view), drawBottomGroove(view));
        drawString(guiGraphics, blockLabel(node.type), x + 10, top + 8, 0xFFFFFF);
        if (node.disabled) {
            final String disabled = Component.translatable("gui.trdm.script.disabled_tag").getString();
            drawString(guiGraphics, disabled, x + Math.max(10, w - font.width(disabled) - 8),
                    top + 8, 0xFFE0E0E0);
        }
        if ("use_rule".equals(node.type)) {
            drawRuleField(guiGraphics, view);
            return;
        }
        if (!valueStatement(node.type)) {
            if ("round_fare".equals(node.type)) {
                drawString(guiGraphics, "取整  " + node.fields.getOrDefault("mode", "HALF")
                                + " / " + node.fields.getOrDefault("unit", "0.1"),
                        x + 10, top + Math.round(32 * zoom), 0xE8E8E8);
            }
            return;
        }
        final ScriptNode value = script.node(node.inputs.get("value"));
        final String summary = value == null ? "?" : valueSummary(value.id);
        final String chip;
        if ("discount_fare".equals(node.type)) {
            chip = Component.translatable("gui.trdm.script.discount_chip", summary).getString();
        } else if ("pass_price".equals(node.type)) {
            chip = Component.translatable("gui.trdm.script.pass_price_chip", summary).getString();
        } else if ("pass_days".equals(node.type)) {
            chip = Component.translatable("gui.trdm.script.pass_days_chip", summary).getString();
        } else if ("pass_discount".equals(node.type)) {
            chip = Component.translatable("gui.trdm.script.pass_discount_chip", summary).getString();
        } else if ("pass_daily_limit".equals(node.type)) {
            chip = Component.translatable("gui.trdm.script.pass_daily_chip", summary).getString();
        } else {
            chip = Component.translatable("gui.trdm.script.value_chip",
                    value == null ? Component.translatable("gui.trdm.script.none")
                            : Component.literal(summary)).getString();
        }
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

    private int[] ruleFieldRect(View view) {
        final int h = Math.max(12, Math.round(20 * zoom));
        return new int[]{view.left + 10, view.top + Math.round(30 * zoom),
                Math.max(40, view.width - 20), h};
    }

    private boolean isLineSelected() {
        final ScriptNode selected = selectedValueNode();
        return popupVisible && selected != null && "line_is".equals(selected.type);
    }

    private boolean inLineField(double mx, double my) {
        return isLineSelected() && mx >= lineFieldX && mx < lineFieldX + lineFieldW
                && my >= lineFieldY && my < lineFieldY + lineFieldH;
    }

    private void drawLineField(GuiGraphics guiGraphics, ScriptNode node) {
        final int x = lineFieldX;
        final int y = lineFieldY;
        final int w = lineFieldW;
        final int h = lineFieldH;
        if (w <= 0 || h <= 0) {
            return;
        }
        fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, PANEL_BORDER);
        fill(guiGraphics, x, y, w, h, 0xFF202020);
        final String selected = node.fields.get("line_name");
        final String label = selected == null || selected.isBlank()
                ? Component.translatable("gui.trdm.script.choose_line").getString() : selected;
        drawString(guiGraphics, fit(label, w - 24), x + 7,
                y + Math.max(2, (h - 8) / 2),
                selected == null || selected.isBlank() ? 0xFFB0B0B0 : 0xE8E8E8);
        final int tx = x + w - 13;
        final int ty = y + h / 2 - 2;
        for (int row = 0; row < 4; row++) {
            fill(guiGraphics, tx + row, ty + row, 8 - row * 2, 1, 0xFFB0B0B0);
        }
    }

    private boolean handleLineClick(double mx, double my) {
        if (lineDropOpen) {
            final LineOption chosen = lineOptionAt(mx, my);
            if (chosen != null) {
                final ScriptNode selected = selectedValueNode();
                if (selected != null && "line_is".equals(selected.type)) {
                    pushUndo();
                    selected.fields.put("line", Long.toString(chosen.id()));
                    selected.fields.put("line_name", chosen.name());
                    error = "";
                }
                lineDropOpen = false;
                rebuild();
                return true;
            }
            final boolean insideList = mx >= lineListX && mx < lineListX + lineListW
                    && my >= lineListY && my < lineListY + lineListH;
            lineDropOpen = false;
            return insideList || inLineField(mx, my);
        }
        if (!inLineField(mx, my)) {
            return false;
        }
        if (lineOptions().isEmpty()) {
            error = Component.translatable("gui.trdm.script.no_lines").getString();
            return true;
        }
        lineDropOpen = true;
        lineDropScroll = 0;
        operatorDropOpen = false;
        ruleDropOpen = false;
        return true;
    }

    private LineOption lineOptionAt(double mx, double my) {
        if (!lineDropOpen || lineListW <= 0 || lineListH <= 0) {
            return null;
        }
        if (mx < lineListX || mx >= lineListX + lineListW
                || my < lineListY || my >= lineListY + lineListH) {
            return null;
        }
        final int row = (int) ((my - lineListY - 2) / 18);
        final int visible = Math.min(8, lineOptions().size());
        if (row < 0 || row >= visible) {
            return null;
        }
        final List<LineOption> options = lineOptions();
        final int index = row + lineDropScroll;
        return index >= 0 && index < options.size() ? options.get(index) : null;
    }

    private void drawLineDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!lineDropOpen) {
            return;
        }
        final List<LineOption> options = lineOptions();
        if (options.isEmpty()) {
            lineDropOpen = false;
            return;
        }
        final int visible = Math.min(8, options.size());
        lineListX = lineFieldX;
        lineListW = Math.max(240, lineFieldW);
        lineListH = visible * 18 + 4;
        lineListY = lineFieldY + lineFieldH + 2;
        if (lineListY + lineListH > this.height - 8) {
            lineListY = Math.max(44, lineFieldY - lineListH - 2);
        }
        lineDropScroll = clamp(lineDropScroll, 0, Math.max(0, options.size() - visible));
        fill(guiGraphics, lineListX - 1, lineListY - 1, lineListW + 2, lineListH + 2, PANEL_BORDER);
        fill(guiGraphics, lineListX, lineListY, lineListW, lineListH, 0xF0161616);
        final ScriptNode selected = selectedValueNode();
        for (int row = 0; row < visible; row++) {
            final int index = row + lineDropScroll;
            if (index >= options.size()) {
                break;
            }
            final LineOption option = options.get(index);
            final int top = lineListY + 2 + row * 18;
            final boolean hovered = mouseX >= lineListX && mouseX < lineListX + lineListW
                    && mouseY >= top && mouseY < top + 18;
            final boolean current = selected != null
                    && Long.toString(option.id()).equals(selected.fields.get("line"));
            if (current) {
                fill(guiGraphics, lineListX + 1, top, lineListW - 2, 18, 0x3345B35A);
            } else if (hovered) {
                fill(guiGraphics, lineListX + 1, top, lineListW - 2, 18, 0x33FFFFFF);
            }
            drawString(guiGraphics, fit(option.name(), lineListW - 16), lineListX + 8, top + 5,
                    hovered ? 0xFFFFFFFF : 0xFFD0D0D0);
        }
        if (options.size() > visible) {
            final int trackX = lineListX + lineListW - 5;
            final int trackH = lineListH - 6;
            fill(guiGraphics, trackX, lineListY + 3, 3, trackH, 0xFF3A3A3A);
            final int thumbH = Math.max(10, Math.round(trackH * (float) visible / options.size()));
            final int maxScroll = options.size() - visible;
            final int thumbY = lineListY + 3 + Math.round((trackH - thumbH)
                    * (float) lineDropScroll / maxScroll);
            fill(guiGraphics, trackX, thumbY, 3, thumbH, 0xFF8A8A8A);
        }
    }

    private void drawRuleField(GuiGraphics guiGraphics, View view) {        final int[] rect = ruleFieldRect(view);
        final int x = rect[0];
        final int y = rect[1];
        final int w = rect[2];
        final int h = rect[3];
        fill(guiGraphics, x, y, w, h, 0xFF202020);
        fill(guiGraphics, x + 2, y + 1, 3, h - 2, colorFor("use_rule"));
        final ScriptNode node = view.node;
        final FareRule selected = selectedRule(node);
        final String text;
        final int color;
        if (selected != null) {
            text = selected.name;
            color = selected.enabled ? 0xE8E8E8 : 0xFFB0B0B0;
        } else if (node.fields.get("rule") != null && !node.fields.get("rule").isBlank()) {
            final String storedName = node.fields.get("rule_name");
            text = storedName == null || storedName.isBlank()
                    ? node.fields.get("rule") : storedName;
            color = 0xFFFF9090;
        } else {
            text = Component.translatable("gui.trdm.script.choose_rule").getString();
            color = 0xFFB0B0B0;
        }
        drawString(guiGraphics, fit(text, w - 24), x + 9, y + Math.max(2, (h - 8) / 2), color);
        final int tx = x + w - 13;
        final int ty = y + h / 2 - 2;
        for (int row = 0; row < 4; row++) {
            fill(guiGraphics, tx + row, ty + row, 8 - row * 2, 1, 0xFFB0B0B0);
        }
    }

    private FareRule selectedRule(ScriptNode node) {
        final String id = node == null ? null : node.fields.get("rule");
        if (id == null || id.isBlank()) {
            return null;
        }
        for (final FareRule rule : FareRuntime.get().config().rules) {
            if (id.equals(rule.id)) {
                return rule;
            }
        }
        return null;
    }

    private List<FareRule> availableRules() {
        final List<FareRule> all = FareRuntime.get().config().rules;
        final List<FareRule> result = new ArrayList<>();
        for (final FareRule candidate : all) {
            if (rule == null || rule.id == null || !rule.id.equals(candidate.id)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private List<LineOption> lineOptions() {
        final List<LineOption> result = new ArrayList<>();
        final Set<Long> seen = new HashSet<>();
        for (final Route route : ClientData.ROUTES) {
            if (route.name != null && !route.name.isBlank() && seen.add(route.id)) {
                result.add(new LineOption(route.id, route.name));
            }
        }
        return result;
    }

    private View ruleFieldAt(double mx, double my) {
        for (final View view : views) {
            if (!"use_rule".equals(view.node.type)) {
                continue;
            }
            final int[] rect = ruleFieldRect(view);
            if (mx >= rect[0] && mx < rect[0] + rect[2]
                    && my >= rect[1] && my < rect[1] + rect[3]) {
                return view;
            }
        }
        return null;
    }

    private boolean handleRuleClick(double mx, double my) {
        if (ruleDropOpen) {
            final FareRule chosen = ruleOptionAt(mx, my);
            if (chosen != null) {
                final ScriptNode node = script.node(ruleDropNodeId);
                if (node != null) {
                    pushUndo();
                    node.fields.put("rule", chosen.id);
                    node.fields.put("rule_name", chosen.name);
                    warningsDismissed = false;
                    refreshMissingRules();
                    error = "";
                }
                ruleDropOpen = false;
                rebuild();
                return true;
            }
            final boolean insideList = mx >= ruleListX && mx < ruleListX + ruleListW
                    && my >= ruleListY && my < ruleListY + ruleListH;
            ruleDropOpen = false;
            if (insideList || ruleFieldAt(mx, my) != null) {
                return true;
            }
            return false;
        }
        if (insidePopup(mx, my)) {
            return false;
        }
        final View field = ruleFieldAt(mx, my);
        if (field == null) {
            return false;
        }
        if (availableRules().isEmpty()) {
            error = Component.translatable("gui.trdm.script.no_rules").getString();
            return true;
        }
        ruleDropOpen = true;
        ruleDropNodeId = field.node.id;
        ruleDropScroll = 0;
        operatorDropOpen = false;
        lineDropOpen = false;
        return true;
    }

    private FareRule ruleOptionAt(double mx, double my) {
        if (!ruleDropOpen || ruleListW <= 0 || ruleListH <= 0) {
            return null;
        }
        if (mx < ruleListX || mx >= ruleListX + ruleListW
                || my < ruleListY || my >= ruleListY + ruleListH) {
            return null;
        }
        final int row = (int) ((my - ruleListY - 2) / 18);
        if (row < 0 || row >= Math.min(8, availableRules().size())) {
            return null;
        }
        final int index = row + ruleDropScroll;
        final List<FareRule> rules = availableRules();
        return index >= 0 && index < rules.size() ? rules.get(index) : null;
    }

    private void drawRuleDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!ruleDropOpen) {
            return;
        }
        final ScriptNode node = script.node(ruleDropNodeId);
        final View view = ruleDropNodeId == null ? null : viewById.get(ruleDropNodeId);
        if (node == null || view == null) {
            ruleDropOpen = false;
            return;
        }
        final List<FareRule> rules = availableRules();
        if (rules.isEmpty()) {
            ruleDropOpen = false;
            return;
        }
        final int[] field = ruleFieldRect(view);
        final int visible = Math.min(8, rules.size());
        ruleListX = field[0];
        ruleListW = Math.max(240, field[2]);
        ruleListH = visible * 18 + 4;
        ruleListY = field[1] + field[3] + 2;
        if (ruleListY + ruleListH > this.height - 8) {
            ruleListY = Math.max(44, field[1] - ruleListH - 2);
        }
        ruleDropScroll = Math.max(0, Math.min(ruleDropScroll,
                Math.max(0, rules.size() - visible)));
        fill(guiGraphics, ruleListX - 1, ruleListY - 1, ruleListW + 2, ruleListH + 2, PANEL_BORDER);
        fill(guiGraphics, ruleListX, ruleListY, ruleListW, ruleListH, 0xF0161616);
        for (int row = 0; row < visible; row++) {
            final int index = row + ruleDropScroll;
            if (index >= rules.size()) {
                break;
            }
            final FareRule rule = rules.get(index);
            final int top = ruleListY + 2 + row * 18;
            final boolean hovered = mouseX >= ruleListX && mouseX < ruleListX + ruleListW
                    && mouseY >= top && mouseY < top + 18;
            final boolean selected = rule.id.equals(node.fields.get("rule"));
            if (selected) {
                fill(guiGraphics, ruleListX + 1, top, ruleListW - 2, 18, 0x3345B35A);
            } else if (hovered) {
                fill(guiGraphics, ruleListX + 1, top, ruleListW - 2, 18, 0x33FFFFFF);
            }
            final String label = rule.enabled ? rule.name
                    : rule.name + Component.translatable("gui.trdm.ticket.disabled_suffix").getString();
            drawString(guiGraphics, fit(label, ruleListW - 16), ruleListX + 8, top + 5,
                    hovered ? 0xFFFFFFFF : 0xFFD0D0D0);
        }
        if (rules.size() > visible) {
            final int trackX = ruleListX + ruleListW - 5;
            final int trackH = ruleListH - 6;
            fill(guiGraphics, trackX, ruleListY + 3, 3, trackH, 0xFF3A3A3A);
            final int thumbH = Math.max(10, Math.round(trackH * (float) visible / rules.size()));
            final int maxScroll = rules.size() - visible;
            final int thumbY = ruleListY + 3 + Math.round((trackH - thumbH)
                    * (float) ruleDropScroll / maxScroll);
            fill(guiGraphics, trackX, thumbY, 3, thumbH, 0xFF8A8A8A);
        }
    }

    /** 重新扫描“使用规则”积木引用的规则；缺失引用只警告并跳过，不阻断保存。 */
    private void refreshMissingRules() {
        final List<String> previous = new ArrayList<>(missingRules);
        missingRules.clear();
        final Set<String> seen = new HashSet<>();
        for (final ScriptNode node : script.nodes) {
            if (!"use_rule".equals(node.type)) {
                continue;
            }
            final String id = node.fields.get("rule");
            if (id == null || id.isBlank()) {
                continue;
            }
            boolean found = false;
            for (final FareRule rule : FareRuntime.get().config().rules) {
                if (id.equals(rule.id)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                final String storedName = node.fields.get("rule_name");
                final String name = storedName == null || storedName.isBlank() ? id : storedName;
                if (name != null && !name.isBlank() && seen.add(name)) {
                    missingRules.add(name);
                }
            }
        }
        if (!missingRules.equals(previous)) {
            warningsDismissed = false;
        }
    }

    private boolean noticeVisible() {
        return !error.isEmpty() || (!warningsDismissed && !missingRules.isEmpty());
    }

    private void drawNotice(GuiGraphics guiGraphics) {
        if (!noticeVisible()) {
            return;
        }
        final boolean missing = !warningsDismissed && !missingRules.isEmpty();
        final String title = missing
                ? Component.translatable("gui.trdm.script.missing_rules_title").getString()
                : Component.translatable("gui.trdm.script.notice_title").getString();
        final List<String> lines = new ArrayList<>();
        if (missing) {
            for (final String name : missingRules) {
                lines.add("· " + name);
            }
            if (!error.isEmpty()) {
                lines.add("· " + error);
            }
        } else {
            lines.add(error);
        }
        final int width = 300;
        final int height = 24 + lines.size() * 12 + 8;
        noticeX = Math.max(canvasLeft + 4, canvasRight - width - 10);
        noticeY = Math.max(canvasTop + 4, canvasBottom - height - 12);
        noticeW = width;
        noticeH = height;
        fill(guiGraphics, noticeX + 3, noticeY + 4, width, height, 0x50000000);
        fill(guiGraphics, noticeX - 1, noticeY - 1, width + 2, height + 2, 0xFF6A1A1A);
        fill(guiGraphics, noticeX, noticeY, width, height, 0xF2A02A2A);
        fill(guiGraphics, noticeX, noticeY, 4, height, 0xFFE05A5A);
        drawString(guiGraphics, fit(title, width - 28), noticeX + 10, noticeY + 7, 0xFFFFE0E0);
        for (int index = 0; index < lines.size(); index++) {
            drawString(guiGraphics, fit(lines.get(index), width - 22),
                    noticeX + 12, noticeY + 22 + index * 12, 0xFFFFFFFF);
        }
        drawString(guiGraphics, "×", noticeX + width - 17, noticeY + 5, 0xFFFFFFFF);
    }

    private boolean handleNoticeClick(double mx, double my) {
        if (!noticeVisible()) {
            return false;
        }
        final boolean inside = mx >= noticeX && mx < noticeX + noticeW
                && my >= noticeY && my < noticeY + noticeH;
        if (!inside) {
            return false;
        }
        if (mx >= noticeX + noticeW - 20 && my < noticeY + 20) {
            error = "";
            warningsDismissed = true;
        }
        return true;
    }

    private void drawBlockShape(GuiGraphics guiGraphics, int x, int y, int w, int h, int color,
                                boolean selected, boolean hovered, boolean attachedAbove,
                                boolean attachedBelow, boolean drawGroove) {
        fill(guiGraphics, x + 3, y + 4, w, h, 0x28000000);
        fill(guiGraphics, x + 11, y - 5, 22, 6, 0xFF2A2A2A);
        fill(guiGraphics, x + 12, y - 4, 20, 4, color);
        fill(guiGraphics, x - 1, y - 1, 1, h + 2, 0xFF2A2A2A);
        fill(guiGraphics, x + w, y - 1, 1, h + 2, 0xFF2A2A2A);
        fill(guiGraphics, x - 1, y - 1, w + 2, 1, 0xFF2A2A2A);
        fill(guiGraphics, x - 1, y + h, w + 2, 1, 0xFF2A2A2A);
        fill(guiGraphics, x, y, w, h, hovered ? brighten(color) : color);
        if (!attachedAbove) {
            fill(guiGraphics, x, y, w, 3, 0x45FFFFFF);
        }
        if (!attachedBelow) {
            fill(guiGraphics, x, y + h - 3, w, 3, 0x30000000);
        }
        if (drawGroove) {
            fill(guiGraphics, x + 12, y + h - 5, 20, 5, CANVAS_BG);
            fill(guiGraphics, x + 12, y + h - 5, 20, 1, 0xFF2A2A2A);
        }
        if (selected) {
            fill(guiGraphics, x - 2, y - 2, w + 4, 2, 0xFFFFD45A);
            fill(guiGraphics, x - 2, y + h, w + 4, 2, 0xFFFFD45A);
            fill(guiGraphics, x - 2, y - 2, 2, h + 4, 0xFFFFD45A);
            fill(guiGraphics, x + w, y - 2, 2, h + 4, 0xFFFFD45A);
        }
    }

    private void drawBranchHighlight(GuiGraphics guiGraphics, BranchKey key) {
        final View view = viewById.get(key.ownerId());
        if (view == null || !view.isIf || (key.branch() == Branch.ELSE && !view.hasElse)) {
            return;
        }
        final int top = key.branch() == Branch.THEN ? view.thenTop : view.elseTop;
        final int bottom = key.branch() == Branch.THEN ? view.thenBottom : view.elseBottom;
        fill(guiGraphics, view.left + indentScreen, top, view.width - indentScreen, bottom - top,
                0x35FFD45A);
    }

    private void drawDropGap(GuiGraphics guiGraphics, DropTarget target) {
        final View view = viewById.get(target.nodeId());
        if (view == null) {
            return;
        }
        final int y = target.kind() == DropKind.PORT_BEFORE
                ? view.top : view.top + view.height;
        // 顺序堆叠的插入提示：在两块之间画一条亮色接缝。
        fill(guiGraphics, view.left - 4, y - 2, view.width + 8, 4, 0xFFFF9F1C);
        fill(guiGraphics, view.left - 4, y - 4, 4, 8, 0xFFFF9F1C);
        fill(guiGraphics, view.left + view.width, y - 4, 4, 8, 0xFFFF9F1C);
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
            drawString(guiGraphics, Component.translatable(numberFieldLabel(value)).getString(),
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
            drawOperatorField(guiGraphics, value);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.value_left").getString(),
                    popupX + 10, popupY + 56, TEXT_MUTED);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.value_right").getString(),
                    popupX + 10, popupY + 80, TEXT_MUTED);
        } else if ("and".equals(value.type) || "or".equals(value.type)) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.condition_a").getString(),
                    popupX + 10, popupY + 56, TEXT_MUTED);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.condition_b").getString(),
                    popupX + 10, popupY + 80, TEXT_MUTED);
        } else if ("not".equals(value.type)) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.condition_a").getString(),
                    popupX + 10, popupY + 56, TEXT_MUTED);
        } else if ("line_is".equals(value.type)) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.line_name").getString(),
                    popupX + 10, popupY + 33, TEXT_MUTED);
            drawLineField(guiGraphics, value);
        } else if (isArithmetic(value.type)) {
            drawString(guiGraphics, Component.translatable("gui.trdm.script.value_left").getString(),
                    popupX + 10, popupY + 56, TEXT_MUTED);
            drawString(guiGraphics, Component.translatable("gui.trdm.script.value_right").getString(),
                    popupX + 10, popupY + 80, TEXT_MUTED);
        }
        for (final ChipArea chip : popupChips) {
            final ScriptNode owner = script.node(chip.ownerId());
            final ScriptNode child = owner == null ? null : script.node(owner.inputs.get(chip.slot()));
            final boolean target = valueTarget != null && chip.ownerId().equals(valueTarget.ownerId())
                    && chip.slot().equals(valueTarget.slot());
            drawValueChip(guiGraphics, chip, child, target);
        }
    }

    private void drawOperatorField(GuiGraphics guiGraphics, ScriptNode compare) {
        final int x = operatorFieldX;
        final int y = operatorFieldY;
        final int w = operatorFieldW;
        final int h = operatorFieldH;
        if (w <= 0 || h <= 0) {
            return;
        }
        fill(guiGraphics, x - 1, y - 1, w + 2, h + 2, PANEL_BORDER);
        fill(guiGraphics, x, y, w, h, 0xFF202020);
        drawString(guiGraphics, compare.fields.getOrDefault("op", ">="), x + 7,
                y + Math.max(2, (h - 8) / 2), 0xE8E8E8);
        final int tx = x + w - 13;
        final int ty = y + h / 2 - 2;
        for (int row = 0; row < 4; row++) {
            fill(guiGraphics, tx + row, ty + row, 8 - row * 2, 1, 0xFFB0B0B0);
        }
    }

    private boolean isCompareSelected() {
        final ScriptNode selected = selectedValueNode();
        return popupVisible && selected != null && "compare".equals(selected.type);
    }

    private boolean inOperatorField(double mx, double my) {
        return isCompareSelected() && mx >= operatorFieldX && mx < operatorFieldX + operatorFieldW
                && my >= operatorFieldY && my < operatorFieldY + operatorFieldH;
    }

    private boolean handleOperatorClick(double mx, double my) {
        if (operatorDropOpen) {
            final String chosen = operatorOptionAt(mx, my);
            if (chosen != null) {
                final ScriptNode compare = selectedValueNode();
                if (compare != null) {
                    pushUndo();
                    compare.fields.put("op", chosen);
                }
                operatorDropOpen = false;
                rebuild();
                return true;
            }
            operatorDropOpen = false;
            return insidePopup(mx, my) || inOperatorField(mx, my);
        }
        if (inOperatorField(mx, my)) {
            operatorDropOpen = true;
            ruleDropOpen = false;
            lineDropOpen = false;
            return true;
        }
        return false;
    }

    private String operatorOptionAt(double mx, double my) {
        if (!operatorDropOpen || operatorListW <= 0 || operatorListH <= 0) {
            return null;
        }
        if (mx < operatorListX || mx >= operatorListX + operatorListW
                || my < operatorListY || my >= operatorListY + operatorListH) {
            return null;
        }
        for (int index = 0; index < OPS.length; index++) {
            final int top = operatorListY + 2 + index * 18;
            if (my >= top && my < top + 18) {
                return OPS[index];
            }
        }
        return null;
    }

    private void drawOperatorDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!operatorDropOpen || !isCompareSelected()) {
            return;
        }
        final ScriptNode compare = selectedValueNode();
        if (compare == null) {
            return;
        }
        operatorListX = operatorFieldX;
        operatorListW = Math.max(operatorFieldW, 84);
        operatorListY = operatorFieldY + operatorFieldH + 2;
        operatorListH = OPS.length * 18 + 4;
        if (operatorListY + operatorListH > this.height - 8) {
            operatorListY = Math.max(44, operatorFieldY - operatorListH - 2);
        }
        fill(guiGraphics, operatorListX - 1, operatorListY - 1, operatorListW + 2,
                operatorListH + 2, PANEL_BORDER);
        fill(guiGraphics, operatorListX, operatorListY, operatorListW, operatorListH, 0xF0161616);
        for (int index = 0; index < OPS.length; index++) {
            final int top = operatorListY + 2 + index * 18;
            final boolean hovered = mouseX >= operatorListX && mouseX < operatorListX + operatorListW
                    && mouseY >= top && mouseY < top + 18;
            if (OPS[index].equals(compare.fields.getOrDefault("op", ">="))) {
                fill(guiGraphics, operatorListX + 1, top, operatorListW - 2, 18, 0x3345B35A);
            } else if (hovered) {
                fill(guiGraphics, operatorListX + 1, top, operatorListW - 2, 18, 0x33FFFFFF);
            }
            drawString(guiGraphics, OPS[index], operatorListX + 8, top + 5,
                    hovered ? 0xFFFFFFFF : 0xFFD0D0D0);
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
        } else if (dragCommentType != null) {
            label = Component.translatable("gui.trdm.script.block.comment").getString();
            color = colorFor("comment");
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
        if (editingComment != null) {
            if (inCommentEditor(mouseX, mouseY)) {
                setCommentCaretAt(mouseX, mouseY, Screen.hasShiftDown());
                return true;
            }
            commitCommentEdit(true);
        }
        if (handleTraceClick(mouseX, mouseY)) {
            return true;
        }
        if (confirmCloseOpen) {
            return handleConfirmClick(mouseX, mouseY);
        }
        if (handleNoticeClick(mouseX, mouseY)) {
            return true;
        }
        if (handleRuleClick(mouseX, mouseY)) {
            return true;
        }
        if (handleLineClick(mouseX, mouseY)) {
            return true;
        }
        if (handleOperatorClick(mouseX, mouseY)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (handleTrialClick(mouseX, mouseY)) {
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
        if (paletteEntry != null && paletteEntry.header()) {
            if (!collapsedCategories.remove(paletteEntry.labelKey())) {
                collapsedCategories.add(paletteEntry.labelKey());
            }
            paletteScroll = Math.max(0, Math.min(paletteMaxScroll(), paletteScroll));
            rebuild();
            return true;
        }
        if (paletteEntry != null && !paletteEntry.header()) {
            if ("comment".equals(paletteEntry.type())) {
                dragCommentType = "comment";
                popupVisible = false;
            } else if (paletteEntry.statement()) {
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
        final ScriptNode comment = commentAt(mouseX, mouseY);
        if (comment != null && button == 0) {
            final long now = System.currentTimeMillis();
            final boolean doubleClick = comment.id.equals(lastCommentClickId)
                    && now - lastCommentClickAt <= 350L;
            lastCommentClickId = comment.id;
            lastCommentClickAt = now;
            if (doubleClick) {
                pushUndo();
                openCommentEditor(comment);
                dragCommentId = null;
                return true;
            }
            dragCommentId = comment.id;
            dragMoved = false;
            commentUndoPushed = false;
            commentDragOffsetX = mouseX - commentScreenX(comment);
            commentDragOffsetY = mouseY - commentScreenY(comment);
            popupVisible = false;
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
            dragMoved = false;
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
        if (dragCommentType != null) {
            if (inCanvas(mouseX, mouseY)) {
                createComment(mouseX, mouseY);
            }
            clearDrag();
            rebuild();
            return true;
        }
        if (dragCommentId != null) {
            final String id = dragCommentId;
            if (dragMoved && !dropOnSelf && overTrash) {
                final ScriptNode node = script.node(id);
                if (node != null) {
                    if (!commentUndoPushed) {
                        pushUndo();
                    }
                    script.nodes.remove(node);
                }
            }
            clearDrag();
            rebuild();
            return true;
        }
        if (dragStatementType != null) {
            final DropTarget target = computeStatementDrop(mouseX, mouseY, null);
            if (!overTrash) {
                if (target != null) {
                    insertNewStatement(dragStatementType, target);
                } else if (inCanvas(mouseX, mouseY)) {
                    insertNewStatementTop(dragStatementType);
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
            final boolean moved = dragMoved;
            final DropTarget target = moved ? computeStatementDrop(mouseX, mouseY, id) : null;
            if (moved && !dropOnSelf && overTrash) {
                deleteStatement(id, true);
                selectedStatement = null;
                selectedValue = null;
            } else if (moved && !dropOnSelf && target != null) {
                moveStatement(id, target);
            } else if (moved && !dropOnSelf && inCanvas(mouseX, mouseY)
                    && viewById.containsKey(id)
                    && viewById.get(id).branch.branch() != Branch.TOP) {
                // 从 C 形块里拖到画布空白处：移到顶层末尾。
                moveToTopLevelEnd(id);
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
        dragCommentType = null;
        dragCommentId = null;
        commentUndoPushed = false;
        dragMoved = false;
        statementDrop = null;
        dropOnSelf = false;
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
        if (dragCommentId != null) {
            if (!commentUndoPushed) {
                pushUndo();
                commentUndoPushed = true;
            }
            dragMoved = true;
            final ScriptNode node = script.node(dragCommentId);
            if (node != null) {
                node.x = Math.round((float) ((mouseX - canvasLeft - panX - commentDragOffsetX) / zoom));
                node.y = Math.round((float) ((mouseY - canvasTop - panY - commentDragOffsetY) / zoom));
            }
            overTrash = mouseX >= trashX() && mouseX < trashX() + 88
                    && mouseY >= trashY() && mouseY < trashY() + 32;
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        if (dragStatementId != null) {
            dragMoved = true;
            statementDrop = computeStatementDrop(mouseX, mouseY, dragStatementId);
            this.dragX = mouseX;
            this.dragY = mouseY;
            return true;
        }
        if (dragStatementType != null) {
            statementDrop = computeStatementDrop(mouseX, mouseY, null);
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
        if (handleTraceScroll(mouseX, mouseY, delta)) {
            return true;
        }
        if (handleTrialScroll(mouseX, mouseY, delta)) {
            return true;
        }
        if (lineDropOpen && mouseX >= lineListX && mouseX < lineListX + lineListW
                && mouseY >= lineListY && mouseY < lineListY + lineListH) {
            final int visible = Math.min(8, lineOptions().size());
            final int max = Math.max(0, lineOptions().size() - visible);
            final int direction = delta > 0 ? -1 : 1;
            lineDropScroll = clamp(lineDropScroll + direction, 0, max);
            return true;
        }
        if (ruleDropOpen && mouseX >= ruleListX && mouseX < ruleListX + ruleListW
                && mouseY >= ruleListY && mouseY < ruleListY + ruleListH) {
            final int visible = Math.min(8, availableRules().size());
            final int max = Math.max(0, availableRules().size() - visible);
            final int direction = delta > 0 ? -1 : 1;
            ruleDropScroll = clamp(ruleDropScroll + direction, 0, max);
            return true;
        }
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

    private void insertNewStatementTop(String type) {
        final BranchKey top = new BranchKey("", Branch.TOP);
        if (!canAddStatement(top)) {
            return;
        }
        pushUndo();
        final ScriptNode node = newStatementNode(type);
        appendToBranch(top, node.id);
        selectedStatement = node.id;
        selectedValue = node.inputs.get("condition") != null ? node.inputs.get("condition")
                : node.inputs.get("value");
        popupVisible = true;
        error = "";
    }

    private ScriptNode newStatementNode(String type) {
        final boolean ifElse = "if_else".equals(type);
        final String nodeType = ifElse ? "if" : type;
        final ScriptNode node = new ScriptNode(Scripts.uniqueId(script, "s"), nodeType);
        if ("if".equals(nodeType)) {
            final ScriptNode condition = newBooleanValue("compare");
            node.inputs.put("condition", condition.id);
            if (ifElse) {
                node.fields.put("else", "1");
            }
        } else if (valueStatement(nodeType)) {
            final ScriptNode number = newNumberValue("number");
            switch (nodeType) {
                case "discount_fare" -> number.fields.put("value", "10");
                case "surcharge" -> number.fields.put("value", "1");
                case "pass_price" -> number.fields.put("value", "10");
                case "pass_days" -> number.fields.put("value", "1");
                case "pass_discount" -> number.fields.put("value", "100");
                case "pass_daily_limit" -> number.fields.put("value", "0");
                default -> {
                }
            }
            node.inputs.put("value", number.id);
        } else if ("round_fare".equals(nodeType)) {
            node.fields.put("mode", "HALF");
            node.fields.put("unit", "0.1");
        } else if ("use_rule".equals(nodeType)) {
            node.fields.put("rule", "");
            node.fields.put("rule_name", "");
        }
        script.nodes.add(node);
        return node;
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
        pushUndo();
        detach(id);
        // 位置字段只用于旧存档兼容；界面改为顺序堆叠后不再使用。
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

    /** 把嵌套语句拖到 C 形块外，追加到顶层末尾。 */
    private void moveToTopLevelEnd(String id) {
        if (script.node(id) == null) {
            return;
        }
        pushUndo();
        detach(id);
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
        // 先登记再创建子节点，避免子节点和父节点拿到同一个 ID。
        final ScriptNode value = new ScriptNode(Scripts.uniqueId(script, "v"), type);
        script.nodes.add(value);
        switch (type) {
            case "number" -> value.fields.put("value", "3.0");
            case "table_intervals" -> {
                value.tiers.add(new ScriptTier(2.0, 2.0));
                value.tiers.add(new ScriptTier(9.0, 3.0));
                value.tiers.add(new ScriptTier(null, 4.0));
            }
            case "table_meters" -> {
                value.tiers.add(new ScriptTier(1.0, 2.0));
                value.tiers.add(new ScriptTier(3.0, 3.0));
                value.tiers.add(new ScriptTier(null, 4.0));
            }
            case "add", "subtract", "multiply", "divide", "min", "max" -> {
                final ScriptNode a = newNumberValue("number");
                final ScriptNode b = newNumberValue("number");
                switch (type) {
                    case "subtract" -> {
                        a.fields.put("value", "3.0");
                        b.fields.put("value", "1.0");
                    }
                    case "divide" -> {
                        a.fields.put("value", "6.0");
                        b.fields.put("value", "2.0");
                    }
                    case "min", "max" -> {
                        a.fields.put("value", "2.0");
                        b.fields.put("value", "3.0");
                    }
                    default -> {
                        a.fields.put("value", "2.0");
                        b.fields.put("value", "2.0");
                    }
                }
                value.inputs.put("a", a.id);
                value.inputs.put("b", b.id);
            }
            default -> {
            }
        }
        return value;
    }

    private ScriptNode newBooleanValue(String type) {
        final ScriptNode value = new ScriptNode(Scripts.uniqueId(script, "v"), type);
        script.nodes.add(value);
        switch (type) {
            case "compare" -> {
                value.fields.put("op", ">=");
                value.inputs.put("a", newNumberValue(passRule == null
                        ? "context_intervals" : "number").id);
                value.inputs.put("b", newNumberValue("number").id);
            }
            case "and", "or" -> {
                value.inputs.put("a", newBooleanValue("compare").id);
                value.inputs.put("b", newBooleanValue("compare").id);
            }
            case "not" -> value.inputs.put("a", newBooleanValue("compare").id);
            case "card_has_policy" -> {
                final List<top.xiaotang27.trdm.fare.DiscountPolicy> policies =
                        FareRuntime.get().config().discountPolicies;
                value.fields.put("policy", policies.isEmpty() ? "" : policies.get(0).id());
                value.fields.put("policy_name", policies.isEmpty() ? "" : policies.get(0).name());
            }
            case "line_is" -> {
                final List<LineOption> options = lineOptions();
                if (!options.isEmpty()) {
                    value.fields.put("line", Long.toString(options.get(0).id()));
                    value.fields.put("line_name", options.get(0).name());
                }
            }
            default -> {
            }
        }
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

    private void cyclePolicy(ScriptNode node) {
        if (node == null) {
            return;
        }
        final List<top.xiaotang27.trdm.fare.DiscountPolicy> policies =
                FareRuntime.get().config().discountPolicies;
        if (policies.isEmpty()) {
            return;
        }
        final String current = node.fields.getOrDefault("policy", "");
        int index = 0;
        for (int i = 0; i < policies.size(); i++) {
            if (policies.get(i).id().equals(current)) {
                index = (i + 1) % policies.size();
                break;
            }
        }
        node.fields.put("policy", policies.get(index).id());
        node.fields.put("policy_name", policies.get(index).name());
        rebuild();
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
        if (old != null && !isLiteralNumeric(old.type)) {
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
        if (node == null || "if".equals(node.type) || "use_rule".equals(node.type)) {
            return;
        }
        pushUndo();
        final String[] types = passRule == null ? STATEMENT_TYPES : PASS_STATEMENT_TYPES;
        int index = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i].equals(node.type)) {
                index = i;
                break;
            }
        }
        final String oldType = node.type;
        node.type = types[(index + 1) % types.length];
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
        operatorDropOpen = false;
        lineDropOpen = false;
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
        } else if (selected != null && isArithmetic(selected.type)) {
            popupH = 146;
        } else if (value != null && isArithmetic(value.type)) {
            popupH = 146;
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
        if (selected != null && "compare".equals(selected.type)) {
            operatorFieldX = popupX + 60;
            operatorFieldY = popupY + 22;
            operatorFieldW = 70;
            operatorFieldH = 18;
        }
        if (selected != null && "line_is".equals(selected.type)) {
            lineFieldX = popupX + 60;
            lineFieldY = popupY + 22;
            lineFieldW = popupW - 70;
            lineFieldH = 18;
        }

        if (selected == null && (value == null || !isArithmetic(value.type))) {
            return;
        }
        final ScriptNode chipOwner = selected != null ? selected : value;
        if ("compare".equals(chipOwner.type)) {
            addChip(chipOwner.id, "a", false, popupY + 52);
            addChip(chipOwner.id, "b", false, popupY + 76);
        } else if ("and".equals(chipOwner.type) || "or".equals(chipOwner.type)) {
            addChip(chipOwner.id, "a", true, popupY + 52);
            addChip(chipOwner.id, "b", true, popupY + 76);
        } else if ("not".equals(chipOwner.type)) {
            addChip(chipOwner.id, "a", true, popupY + 52);
        } else if (isArithmetic(chipOwner.type)) {
            addChip(chipOwner.id, "a", false, popupY + 52);
            addChip(chipOwner.id, "b", false, popupY + 76);
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
            button(x, popupY + 46, 110, 18,
                    Component.translatable(hasCondition
                            ? "gui.trdm.script.edit_condition" : "gui.trdm.script.add_condition"),
                    () -> {
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
                    });
            button(x + 118, popupY + 46, 64, 18,
                    Component.translatable("gui.trdm.script.delete"), this::deleteSelected);
            final boolean hasElse = hasElseBranch(statement);
            if (hasElse) {
                if (ScriptTree.first(script, new ScriptTree.Slot(statement.id, Branch.ELSE)) == null) {
                    button(x + 190, popupY + 46, 96, 18,
                            Component.translatable("gui.trdm.script.remove_else"),
                            () -> {
                                pushUndo();
                                statement.fields.remove("else");
                                statement.otherwise = null;
                                error = "";
                                rebuild();
                            });
                }
            } else {
                button(x + 190, popupY + 46, 96, 18,
                        Component.translatable("gui.trdm.script.add_else"),
                        () -> {
                            pushUndo();
                            statement.fields.put("else", "1");
                            error = "";
                            rebuild();
                        });
            }
            return;
        }

        if (value != null && "number".equals(value.type)) {
            final EditBox box = editBox(x + 60, y + 22, 90, 18,
                    Component.translatable(numberFieldLabel(value)));
            box.setValue(value.fields.getOrDefault("value", "0"));
            box.setResponder(text -> {
                value.fields.put("value", text);
                dirty = true;
            });
            addRenderableWidget(box);
        }
        if (value != null && isTable(value.type)) {
            syncTierScroll(value);
            addTierWidgets(value, x, y);
        }
        if ("round_fare".equals(statement.type) && value == null) {
            button(x + 60, y + 22, 70, 18,
                    Component.literal(statement.fields.getOrDefault("mode", "HALF")),
                    () -> {
                        pushUndo();
                        statement.fields.put("mode",
                                nextMode(statement.fields.getOrDefault("mode", "HALF")));
                        rebuild();
                    });
            final EditBox unit = editBox(x + 136, y + 22, 70, 18,
                    Component.translatable("gui.trdm.script.unit"));
            unit.setValue(statement.fields.getOrDefault("unit", "0.1"));
            unit.setResponder(text -> {
                statement.fields.put("unit", text);
                dirty = true;
            });
            addRenderableWidget(unit);
        }
        if (selected != null && "compare".equals(selected.type)) {
            // 运算符用和线路选择一样的下拉框绘制，不在这里放按钮。
        }
        if (selected != null && "card_has_policy".equals(selected.type)) {
            final String current = selected.fields.getOrDefault("policy_name", "");
            button(x, y + 22, 180, 18, Component.literal(current.isBlank()
                    ? Component.translatable("gui.trdm.script.choose_policy").getString()
                    : current), () -> cyclePolicy(selected));
        }

        int bx = x;
        if (selected != null) {
            final ValueOwner owner = ownerOfValue(selected.id);
            if (owner != null) {
                button(bx, buttonY, 76, 18,
                        Component.translatable("gui.trdm.script.back"),
                        () -> {
                            final ScriptNode ownerNode = script.node(owner.ownerId());
                            selectedValue = isValueNode(ownerNode) ? ownerNode.id : null;
                            selectedStatement = containingStatement(owner.ownerId());
                            popupVisible = true;
                            rebuild();
                        });
                bx += 82;
            }
        }
        if (selected != null && isLiteralNumeric(selected.type)) {
            button(bx, buttonY, 84, 18,
                    Component.translatable("gui.trdm.script.change_value"), this::cycleValueType);
            bx += 90;
        } else if (selected == null && valueStatement(statement.type)
                && (value == null || isLiteralNumeric(value.type))) {
            button(bx, buttonY, 84, 18,
                    Component.translatable("gui.trdm.script.change_value"), this::cycleValueType);
            bx += 90;
        }
        if (selected == null && !"if".equals(statement.type)) {
            button(bx, buttonY, 84, 18,
                    Component.translatable("gui.trdm.script.change_type"), this::cycleStatementType);
            bx += 90;
        }
        if (selected == null || canDeleteValue(selected.id)) {
            button(bx, buttonY, 76, 18,
                    Component.translatable(selected != null && isTopCondition(selected.id)
                            ? "gui.trdm.script.delete_condition" : "gui.trdm.script.delete"),
                    this::deleteSelected);
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
                    dirty = true;
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
                    dirty = true;
                } catch (NumberFormatException ignored) {
                    // 继续输入
                }
            });
            addRenderableWidget(fare);
            button(x + 140, rowY, 18, 18, Component.literal("×"), () -> {
                pushUndo();
                table.tiers.remove(row);
                tierScroll = clamp(tierScroll, 0,
                        Math.max(0, table.tiers.size() - TIER_VISIBLE));
                error = "";
                rebuild();
            });
        }
        button(x + 164, y + 42, 80, 18, Component.translatable("gui.trdm.script.add_row"), () -> {
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
        });
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

    // ===== 复制 / 粘贴 =====

    private void toggleDisabled() {
        final ScriptNode node = script.node(selectedStatement);
        if (node == null || "on_fare".equals(node.type)) {
            return;
        }
        pushUndo();
        node.disabled = !node.disabled;
        rebuild();
    }

    private void openTrial() {
        if (rule == null) {
            return;
        }
        trialPanelOpen = !trialPanelOpen;
        trialCollapsed = false;
        if (trialPanelOpen) {
            trialDirty = true;
        }
        rebuild();
    }

    private void copySelection() {        final String root = selectedValue != null ? selectedValue : selectedStatement;
        if (root == null) {
            return;
        }
        final boolean statement = selectedValue == null;
        final List<ScriptNode> copied = ScriptTree.copyNodes(script, root, statement);
        if (copied.isEmpty()) {
            return;
        }
        clipboard = copied;
        clipboardRootId = root;
        clipboardStatement = statement;
        error = "";
        rebuild();
    }

    private void pasteClipboard() {
        if (clipboard.isEmpty() || clipboardRootId == null) {
            return;
        }
        if (clipboardStatement) {
            pasteStatement();
        } else {
            pasteValue();
        }
    }

    private void pasteStatement() {
        pushUndo();
        final String root = ScriptTree.duplicate(script, clipboard, clipboardRootId);
        if (root == null) {
            error = Component.translatable("gui.trdm.script.paste_failed").getString();
            rebuild();
            return;
        }
        final String anchor = selectedValue != null ? containingStatement(selectedValue)
                : selectedStatement;
        final View anchorView = anchor == null ? null : viewById.get(anchor);
        if (anchorView != null) {
            final String next = ScriptTree.next(script, anchor);
            ScriptTree.insertBefore(script, slot(anchorView.branch), root, next);
        } else {
            ScriptTree.append(script, ScriptTree.Slot.top(), root);
        }
        selectedStatement = root;
        selectedValue = null;
        popupVisible = true;
        error = "";
        pruneUnreachable();
        rebuild();
    }

    private void pasteValue() {
        final String templateType = clipboardRootType();
        final ValueTarget target = currentValueTarget();
        if (target == null) {
            error = Component.translatable("gui.trdm.script.paste_no_target").getString();
            rebuild();
            return;
        }
        if (templateType == null || !typeFits(target.booleanSlot(), templateType)) {
            error = Component.translatable("gui.trdm.script.paste_type_mismatch").getString();
            rebuild();
            return;
        }
        pushUndo();
        final String root = ScriptTree.duplicate(script, clipboard, clipboardRootId);
        if (root == null || script.node(target.ownerId()) == null) {
            if (root != null) {
                pruneUnreachable();
            }
            error = Component.translatable("gui.trdm.script.paste_failed").getString();
            rebuild();
            return;
        }
        script.node(target.ownerId()).inputs.put(target.slot(), root);
        selectedValue = root;
        selectedStatement = containingStatement(root);
        popupVisible = true;
        error = "";
        pruneUnreachable();
        rebuild();
    }

    private String clipboardRootType() {
        for (final ScriptNode node : clipboard) {
            if (node.id.equals(clipboardRootId)) {
                return node.type;
            }
        }
        return null;
    }

    /** 粘贴值时当前可替换的槽位。 */
    private ValueTarget currentValueTarget() {
        if (selectedValue != null) {
            final ValueOwner owner = ownerOfValue(selectedValue);
            if (owner != null) {
                return new ValueTarget(owner.ownerId(), owner.slot(),
                        slotIsBoolean(owner.ownerId(), owner.slot()));
            }
        }
        final ScriptNode statement = selectedStatementNode();
        if (statement == null) {
            return null;
        }
        if ("if".equals(statement.type)) {
            return new ValueTarget(statement.id, "condition", true);
        }
        if (valueStatement(statement.type)) {
            return new ValueTarget(statement.id, "value", false);
        }
        return null;
    }

    private boolean slotIsBoolean(String ownerId, String slot) {
        final ScriptNode owner = script.node(ownerId);
        if (owner == null) {
            return false;
        }
        if ("if".equals(owner.type)) {
            return "condition".equals(slot);
        }
        return "and".equals(owner.type) || "or".equals(owner.type) || "not".equals(owner.type);
    }

    /** 数字块的字段标题：金额、系数还是普通数值。 */
    private String numberFieldLabel(ScriptNode number) {
        final ValueOwner owner = ownerOfValue(number.id);
        final ScriptNode ownerNode = owner == null ? null : script.node(owner.ownerId());
        if (ownerNode != null && "value".equals(owner.slot())) {
            if ("multiply_fare".equals(ownerNode.type)) {
                return "gui.trdm.script.value_factor";
            }
            if ("discount_fare".equals(ownerNode.type)) {
                return "gui.trdm.script.value_percent";
            }
            if ("pass_price".equals(ownerNode.type)) {
                return "gui.trdm.script.value_emeralds";
            }
            if ("pass_days".equals(ownerNode.type)) {
                return "gui.trdm.script.value_days";
            }
            if ("pass_discount".equals(ownerNode.type)) {
                return "gui.trdm.script.value_percent";
            }
            if ("pass_daily_limit".equals(ownerNode.type)) {
                return "gui.trdm.script.value_daily_limit";
            }
            if ("set_fare".equals(ownerNode.type) || "add_fare".equals(ownerNode.type)
                    || "cap_fare".equals(ownerNode.type) || "floor_fare".equals(ownerNode.type)) {
                return "gui.trdm.script.value_amount";
            }
        }
        return "gui.trdm.script.value_number";
    }

    private void save() {
        if (passRule != null) {
            final PassSettings settings;
            try {
                settings = PassScriptCompiler.evaluate(script);
            } catch (ScriptException exception) {
                error = exception.getMessage();
                rebuild();
                return;
            }
            final PassRule current = PassRules.byId(FareRuntime.get().config().passRules, passRule.id);
            final PassRule target = current != null ? current : passRule;
            target.script = script.copy();
            target.applySettings(settings);
            FareRuntime.get().save();
            dirty = false;
            minecraft.setScreen(parent);
            return;
        }
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
        dirty = false;
        minecraft.setScreen(parent);
    }

    private void pushUndo() {
        if (restoring) {
            return;
        }
        dirty = true;
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
        dirty = true;
        restoring = false;
        ruleDropOpen = false;
        refreshMissingRules();
        rebuild();
    }

    // ===== 工具 =====

    private static boolean valueStatement(String type) {
        return "set_fare".equals(type) || "add_fare".equals(type) || "multiply_fare".equals(type)
                || "discount_fare".equals(type) || "cap_fare".equals(type) || "floor_fare".equals(type)
                || "surcharge".equals(type)
                || "pass_price".equals(type) || "pass_days".equals(type)
                || "pass_discount".equals(type) || "pass_daily_limit".equals(type);
    }

    private static boolean statementType(String type) {
        return "on_fare".equals(type) || "if".equals(type) || "round_fare".equals(type)
                || "use_rule".equals(type) || valueStatement(type);
    }

    private static boolean hasElseBranch(ScriptNode node) {
        return node != null && "if".equals(node.type)
                && ("1".equals(node.fields.get("else"))
                || (node.otherwise != null && !node.otherwise.isBlank()));
    }

    private static boolean isValueNode(ScriptNode node) {
        return node != null && !statementType(node.type);
    }

    private static boolean isBoolean(String type) {
        return "compare".equals(type) || "and".equals(type) || "or".equals(type)
                || "not".equals(type) || "line_is".equals(type)
                || "is_weekend".equals(type) || "card_has_policy".equals(type);
    }

    private static boolean isLiteralNumeric(String type) {
        return List.of(NUMERIC_VALUE_TYPES).contains(type);
    }

    private static boolean isArithmetic(String type) {
        return List.of(ARITHMETIC_VALUE_TYPES).contains(type);
    }

    private static boolean isNumeric(String type) {
        return isLiteralNumeric(type) || isArithmetic(type);
    }

    private static boolean isTable(String type) {
        return "table_intervals".equals(type) || "table_meters".equals(type);
    }

    private static int colorFor(String type) {
        return switch (type) {
            case "if", "if_else" -> 0xFFB06A1F;
            case "compare", "and", "or", "not", "line_is", "is_weekend", "card_has_policy" ->
                    0xFF3E8E5A;
            case "set_fare", "add_fare", "multiply_fare", "discount_fare",
                 "cap_fare", "floor_fare", "free_ride", "surcharge" -> 0xFF2F7D4F;
            case "pass_price", "pass_days", "pass_discount", "pass_daily_limit" -> 0xFF2F7D4F;
            case "use_rule" -> 0xFF2F6E7D;
            case "round_fare" -> 0xFF2D6E82;
            case "number", "context_intervals", "context_meters", "context_stations",
                 "context_zone_diff", "card_rule_rides", "card_rule_amount",
                 "card_rule_distance", "time_hour", "time_minute", "time_weekday",
                 "card_day_rides", "card_day_amount", "card_month_rides", "card_month_amount",
                 "add", "subtract", "multiply", "divide", "min", "max" ->
                    0xFF8A5A2B;
            case "table_intervals", "table_meters" -> 0xFF6B4A8A;
            case "comment" -> 0xFFB38B2E;
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
            case "line_is" -> "当前线路 = " + node.fields.getOrDefault("line_name",
                    node.fields.getOrDefault("line", "?"));
            case "is_weekend" -> "周末";
            case "card_has_policy" -> "政策：" + node.fields.getOrDefault("policy_name",
                    node.fields.getOrDefault("policy", "?"));
            case "add" -> arithmeticSummary(node, "+", depth);
            case "subtract" -> arithmeticSummary(node, "-", depth);
            case "multiply" -> arithmeticSummary(node, "×", depth);
            case "divide" -> arithmeticSummary(node, "÷", depth);
            case "min" -> "min(" + valueSummary(node.inputs.get("a"), depth + 1) + ", "
                    + valueSummary(node.inputs.get("b"), depth + 1) + ")";
            case "max" -> "max(" + valueSummary(node.inputs.get("a"), depth + 1) + ", "
                    + valueSummary(node.inputs.get("b"), depth + 1) + ")";
            default -> blockLabel(node.type);
        };
    }

    private String arithmeticSummary(ScriptNode node, String op, int depth) {
        return "(" + valueSummary(node.inputs.get("a"), depth + 1) + " " + op + " "
                + valueSummary(node.inputs.get("b"), depth + 1) + ")";
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

    private TrdmEditorButton button(int x, int y, int w, int h, Component label, Runnable action) {
        final TrdmEditorButton widget = new TrdmEditorButton(x, y, w, h, label,
                button -> action.run());
        addRenderableWidget(widget);
        return widget;
    }

    private void drawString(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.drawString(this.font, text, x, y, color, false);
    }

    private void handleCommentKey(int keyCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            commitCommentEdit(false);
            return;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            replaceCommentSelection("\n");
            return;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            if (commentSelectionStart() != commentSelectionEnd()) {
                replaceCommentSelection("");
            } else if (commentCaret > 0) {
                commentEditBuffer = commentEditBuffer.substring(0, commentCaret - 1)
                        + commentEditBuffer.substring(commentCaret);
                commentCaret--;
                commentAnchor = commentCaret;
            }
            return;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE) {
            if (commentSelectionStart() != commentSelectionEnd()) {
                replaceCommentSelection("");
            } else if (commentCaret < commentEditBuffer.length()) {
                commentEditBuffer = commentEditBuffer.substring(0, commentCaret)
                        + commentEditBuffer.substring(commentCaret + 1);
                commentAnchor = commentCaret;
            }
            return;
        }
        final boolean extend = Screen.hasShiftDown();
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT) {
            setCommentCaret(Math.max(0, commentCaret - 1), extend);
            return;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT) {
            setCommentCaret(Math.min(commentEditBuffer.length(), commentCaret + 1), extend);
            return;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            moveCommentLine(-1, extend);
            return;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            moveCommentLine(1, extend);
            return;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) {
            final int line = commentCaretLine();
            setCommentCaret(commentLineStart(line), extend);
            return;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_END) {
            final int line = commentCaretLine();
            final int start = commentLineStart(line);
            final int newline = commentEditBuffer.indexOf('\n', start);
            final int end = newline < 0 ? commentEditBuffer.length() : newline;
            setCommentCaret(end, extend);
            return;
        }
        if (Screen.hasControlDown()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_A) {
                commentAnchor = 0;
                commentCaret = commentEditBuffer.length();
                return;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C) {
                Minecraft.getInstance().keyboardHandler.setClipboard(commentSelectionText());
                return;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_X) {
                Minecraft.getInstance().keyboardHandler.setClipboard(commentSelectionText());
                replaceCommentSelection("");
                return;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
                replaceCommentSelection(Minecraft.getInstance().keyboardHandler.getClipboard());
            }
        }
    }

    private int commentCaretLineOf(int line) {
        return Math.max(0, Math.min(line, commentEditLines().size() - 1));
    }

    private void moveCommentLine(int delta, boolean extend) {
        final int line = commentCaretLine();
        final int column = commentCaret - commentLineStart(line);
        final int target = Mth.clamp(line + delta, 0, commentEditLines().size() - 1);
        final int start = commentLineStart(target);
        final int end = target == commentEditLines().size() - 1
                ? commentEditBuffer.length()
                : Math.max(start, commentEditBuffer.indexOf('\n', start));
        setCommentCaret(Math.min(end, start + column), extend);
    }

    private void setCommentCaret(int position, boolean extend) {
        commentCaret = Mth.clamp(position, 0, commentEditBuffer.length());
        if (!extend) {
            commentAnchor = commentCaret;
        }
    }

    private int commentSelectionStart() {
        return Math.min(commentAnchor, commentCaret);
    }

    private int commentSelectionEnd() {
        return Math.max(commentAnchor, commentCaret);
    }

    private String commentSelectionText() {
        return commentEditBuffer.substring(commentSelectionStart(), commentSelectionEnd());
    }

    private void replaceCommentSelection(String replacement) {
        final int start = commentSelectionStart();
        final int end = commentSelectionEnd();
        commentEditBuffer = commentEditBuffer.substring(0, start) + replacement
                + commentEditBuffer.substring(end);
        commentCaret = start + replacement.length();
        commentAnchor = commentCaret;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editingComment != null) {
            if (!Screen.hasControlDown() && !Character.isISOControl(codePoint)) {
                replaceCommentSelection(String.valueOf(codePoint));
            }
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingComment != null) {
            handleCommentKey(keyCode, modifiers);
            return true;
        }
        if (traceOpen && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            traceOpen = false;
            return true;
        }
        if (confirmCloseOpen) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                confirmCloseOpen = false;
                rebuild();
            }
            return true;
        }
        if (backspaceHeld && keyCode != org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            backspaceHeld = false;
            backspaceBox = null;
        }
        if (ruleDropOpen && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            ruleDropOpen = false;
            return true;
        }
        if (lineDropOpen && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            lineDropOpen = false;
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE
                && this.getFocused() instanceof EditBox box) {
            if (!backspaceHeld || backspaceBox != box) {
                backspaceBox = box;
                backspaceHeld = true;
                backspaceDelay = 5;
                deleteBackspace(box);
            }
            return true;
        }
        if (operatorDropOpen && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            operatorDropOpen = false;
            return true;
        }
        if (Screen.hasControlDown()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C && !editingText()) {
                copySelection();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V && !editingText()) {
                pasteClipboard();
                return true;
            }
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
                && popupVisible && !editingText()) {
            popupVisible = false;
            rebuild();
            return true;
        }
        if ((keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE)
                && !editingText()
                && (selectedStatement != null || selectedValue != null)) {
            deleteSelected();
            rebuild();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (!backspaceHeld) {
            return;
        }
        if (!(this.getFocused() instanceof EditBox box) || box != backspaceBox) {
            backspaceHeld = false;
            backspaceBox = null;
            return;
        }
        if (minecraft == null || minecraft.getWindow() == null
                || !com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                minecraft.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE)) {
            backspaceHeld = false;
            backspaceBox = null;
            return;
        }
        if (backspaceDelay > 0) {
            backspaceDelay--;
            return;
        }
        deleteBackspace(box);
        backspaceDelay = 2;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            backspaceHeld = false;
            backspaceBox = null;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /** 长按退格：手动删除一个字符，随后由 {@link #tick()} 连续触发。 */
    private void deleteBackspace(EditBox box) {
        final String value = box.getValue();
        final int cursor = box.getCursorPosition();
        if (cursor <= 0 || value.isEmpty()) {
            return;
        }
        final int end = Math.min(cursor, value.length());
        box.setValue(value.substring(0, end - 1) + value.substring(end));
        box.setCursorPosition(Math.max(0, end - 1));
        dirty = true;
    }

    private boolean editingText() {
        return this.getFocused() instanceof EditBox;
    }

    @Override
    public void onClose() {
        if (confirmCloseOpen) {
            confirmCloseOpen = false;
            rebuild();
            return;
        }
        if (dirty) {
            confirmCloseOpen = true;
            operatorDropOpen = false;
            rebuild();
            return;
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private int confirmPanelX() {
        return (this.width - 420) / 2;
    }

    private int confirmPanelY() {
        return (this.height - 174) / 2;
    }

    private int confirmButtonY() {
        return confirmPanelY() + 124;
    }

    private int confirmSaveX() {
        return confirmPanelX() + 50;
    }

    private int confirmDiscardX() {
        return confirmSaveX() + 110;
    }

    private int confirmCancelX() {
        return confirmSaveX() + 220;
    }

    private boolean handleConfirmClick(double mx, double my) {
        final int y = confirmButtonY();
        if (mx >= confirmSaveX() && mx < confirmSaveX() + 100 && my >= y && my < y + 24) {
            confirmCloseOpen = false;
            save();
            return true;
        }
        if (mx >= confirmDiscardX() && mx < confirmDiscardX() + 100 && my >= y && my < y + 24) {
            dirty = false;
            confirmCloseOpen = false;
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
            return true;
        }
        if (mx >= confirmCancelX() && mx < confirmCancelX() + 100 && my >= y && my < y + 24) {
            confirmCloseOpen = false;
            rebuild();
            return true;
        }
        return true;
    }

    private void drawConfirmClose(GuiGraphics guiGraphics) {
        final int x = confirmPanelX();
        final int y = confirmPanelY();
        fill(guiGraphics, 0, 0, this.width, this.height, 0xA0000000);
        fill(guiGraphics, x - 1, y - 1, 422, 176, PANEL_BORDER);
        fill(guiGraphics, x, y, 420, 174, 0xFFFFFDF8);
        final String title = Component.translatable("gui.trdm.script.unsaved_title").getString();
        final String text = Component.translatable("gui.trdm.script.unsaved_text").getString();
        drawString(guiGraphics, title, x + (420 - font.width(title)) / 2, y + 24, TEXT_DARK);
        drawString(guiGraphics, text, x + (420 - font.width(text)) / 2, y + 52, TEXT_MUTED);
        final int by = confirmButtonY();
        drawConfirmButton(guiGraphics, confirmSaveX(), by,
                Component.translatable("gui.trdm.ticket.editor.save").getString(), false);
        drawConfirmButton(guiGraphics, confirmDiscardX(), by,
                Component.translatable("gui.trdm.script.discard").getString(), false);
        drawConfirmButton(guiGraphics, confirmCancelX(), by,
                Component.translatable("gui.trdm.script.cancel").getString(), false);
    }

    private void drawConfirmButton(GuiGraphics guiGraphics, int x, int y, String label, boolean hovered) {
        fill(guiGraphics, x, y, 100, 24, hovered ? 0xFFB0B0B0 : 0xFF8A8A8A);
        fill(guiGraphics, x, y, 100, 2, 0x40FFFFFF);
        drawString(guiGraphics, label, x + (100 - font.width(label)) / 2, y + 8, 0xFFFFFFFF);
    }

    private void rebuild() {
        trialDirty = true;
        refreshMissingRules();
        this.clearWidgets();
        this.init(this.minecraft, this.width, this.height);
    }
}
