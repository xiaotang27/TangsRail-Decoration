package top.xiaotang27.trdm.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mtr.client.ClientData;
import mtr.data.Route;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.fare.FareConfig;
import top.xiaotang27.trdm.fare.FareJson;
import top.xiaotang27.trdm.fare.FareMoney;
import top.xiaotang27.trdm.fare.FareRule;

import java.awt.Desktop;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 通用设置里的“票务系统”页：票务规则列表 + 通用车票模板列表、规则/模板详情、非管理员遮罩。
 * <p>
 * 规则和线路绑定来自 {@link FareRuntime} 的 {@link FareConfig}（{@code config/trdm-fares.json}）；
 * 车票模板仍是界面骨架，等通票系统接入后替换。
 */
final class TicketConfigPage {

    // ===== 布局 =====
    private static final float ENABLE_LABEL_Y = 64F;
    private static final float ENABLE_SUB_Y = 82F;
    private static final float ENABLE_TOGGLE_Y = 60F;

    private static final float RULES_SECTION_Y = 112F;
    private static final float RULES_TOP = 136F;
    private static final float RULES_PANEL_H = 136F;
    private static final float TEMPLATES_SECTION_Y = 300F;
    private static final float TEMPLATES_TOP = 324F;
    private static final float ROW_H = 34F;

    private static final float DROP_W = 250F;
    private static final float DROP_H = 22F;
    private static final float DROP_ROW_H = 18F;
    private static final int DROP_VISIBLE = 6;

    private static final float BACK_W = 84F;
    private static final float BACK_H = 24F;

    private static final float DETAIL_SCOPE_LABEL_Y = 72F;
    private static final float DETAIL_SCOPE_Y = 66F;
    private static final float DETAIL_NAME_SECTION_Y = 118F;
    private static final float DETAIL_NAME_FIELD_Y = 142F;
    private static final float DETAIL_CONTENT_SECTION_Y = 192F;
    private static final float DETAIL_CONTENT_TOP = 222F;
    private static final float DETAIL_CONTENT_LINE = 24F;
    private static final float DETAIL_CONTENT_VALUE_X = 170F;
    private static final float DETAIL_EDIT_BUTTON_Y = 466F;

    // ===== 规则编辑器 =====
    private static final float EDITOR_TYPE_SECTION_Y = 64F;
    private static final float EDITOR_TYPE_BUTTON_Y = 88F;
    private static final float EDITOR_TYPE_BUTTON_W = 190F;
    private static final float EDITOR_TYPE_BUTTON_H = 26F;
    private static final float EDITOR_TYPE_GAP = 200F;

    private static final float EDITOR_CONTENT_SECTION_Y = 136F;
    private static final float EDITOR_FLAT_FIELD_Y = 164F;
    private static final float EDITOR_TABLE_HEADER_Y = 160F;
    private static final float EDITOR_TABLE_TOP = 176F;
    private static final float EDITOR_TABLE_ROW_H = 24F;
    private static final float EDITOR_TABLE_FIELD_H = 20F;
    private static final float EDITOR_LIMIT_X = 72F;
    private static final float EDITOR_LIMIT_W = 110F;
    private static final float EDITOR_UNIT_X = 190F;
    private static final float EDITOR_FARE_LABEL_X = 262F;
    private static final float EDITOR_FARE_X = 276F;
    private static final float EDITOR_FARE_W = 110F;
    private static final float EDITOR_DELETE_X = 396F;
    private static final float EDITOR_DELETE_W = 56F;
    private static final float EDITOR_ADD_W = 120F;
    private static final float EDITOR_ADD_H = 24F;
    private static final int EDITOR_MAX_TIERS = 8;

    private static final float EDITOR_CAP_FIELD_W = 160F;
    private static final float EDITOR_CAP_FIELD_H = 24F;
    private static final float EDITOR_BUTTON_Y = 476F;
    private static final float EDITOR_SAVE_W = 120F;
    private static final float EDITOR_CANCEL_X = 128F;
    private static final float EDITOR_CANCEL_W = 100F;
    private static final float EDITOR_BUTTON_H = 28F;
    private static final float EDITOR_MESSAGE_X = 300F;

    private static final Object FIELD_NAME = new Object();

    // ===== 视图 / 下拉状态 =====
    private static final int VIEW_MAIN = 0;
    private static final int VIEW_RULE = 1;
    private static final int VIEW_TEMPLATE = 2;
    private static final int VIEW_RULE_EDIT = 3;

    private static final int DROP_NONE = 0;
    private static final int DROP_RULE = 1;
    private static final int DROP_SYSTEM = 2;
    private static final int DROP_TEMPLATE = 3;
    private static final int DROP_DETAIL_RULE = 4;
    private static final int DROP_DETAIL_TEMPLATE = 5;

    private static final int SELECT_NONE = -1;
    private static final int SELECT_SYSTEM = 0;
    private static final int SELECT_RULE = 1;
    private static final int SELECT_TEMPLATE = 2;

    private final TrdmScreenBase host;
    private final FareConfig config;
    private final List<FareRule> rules;

    private final List<String> lines = new ArrayList<>();
    /** 线路名 → MTR 线路 ID；绑定按“维度|线路 ID”落盘。 */
    private final java.util.Map<String, Long> lineIds = new java.util.HashMap<>();
    private final List<TemplateRow> templates = new ArrayList<>();
    /** 线路名 → 规则下标；不在表里 = 系统行（MTR 原版公式）。 */
    private final java.util.Map<String, Integer> lineOwner = new java.util.HashMap<>();

    // 规则编辑器状态：draft 是编辑中的副本，保存时写回 rules。
    private final Object flatFareField = new Object();
    private final Object maxFareField = new Object();
    private final List<Object> tierLimitFields = new ArrayList<>();
    private final List<Object> tierFareFields = new ArrayList<>();
    private FareRule draft;
    private String editorMessage = "";

    private int view = VIEW_MAIN;
    private int detailRuleIndex = -1;
    private int detailTemplateIndex = -1;

    private int selectedKind = SELECT_NONE;
    private int selectedIndex = -1;

    private int dropKind = DROP_NONE;
    private int dropIndex = -1;
    private float dropScroll;

    private int lastClickKind = SELECT_NONE;
    private int lastClickIndex = -1;
    private long lastClickAt;
    private float ruleScroll;

    private boolean exportDialogOpen;
    private String exportedFileName = "";
    private Path exportedFile;
    private boolean confirmDeleteOpen;

    TicketConfigPage(TrdmScreenBase host) {
        this.host = host;
        this.config = FareRuntime.get().config();
        this.rules = config.rules;
        seed();
    }

    // ===== 初始化 =====

    private void seed() {
        final List<String> real = new ArrayList<>();
        for (final Route route : ClientData.ROUTES) {
            if (route.name != null && !route.name.isBlank()) {
                real.add(route.name);
                lineIds.putIfAbsent(route.name, route.id);
            }
        }
        real.sort(String.CASE_INSENSITIVE_ORDER);
        lines.addAll(real);
        rebuildOwners();

        templates.add(new TemplateRow("一日票"));
        templates.add(new TemplateRow("三日票"));
        templates.add(new TemplateRow("青春18券"));
        templates.add(new TemplateRow("未命名车票"));

        templates.get(1).rules.add(0);
        templates.get(1).rules.add(1);
        templates.get(2).rules.add(1);

        selectedKind = SELECT_SYSTEM;
        selectedIndex = -1;
    }

    /** 从配置里的线路绑定反推“线路名 → 规则下标”，供下拉勾选状态和摘要使用。 */
    private void rebuildOwners() {
        lineOwner.clear();
        final String dimension = FareRuntime.dimensionId();
        for (final String line : lines) {
            final Long routeId = lineIds.get(line);
            if (routeId == null) {
                continue;
            }
            final FareRule rule = config.ruleById(
                    config.routeRules.get(FareConfig.bindingKey(dimension, routeId)));
            final int index = ruleIndex(rule == null ? null : rule.id);
            if (index >= 0) {
                lineOwner.put(line, index);
            }
        }
    }

    private int ruleIndex(String ruleId) {
        if (ruleId == null) {
            return -1;
        }
        for (int index = 0; index < rules.size(); index++) {
            if (rules.get(index).id.equals(ruleId)) {
                return index;
            }
        }
        return -1;
    }

    private void setBinding(String line, FareRule rule) {
        final Long routeId = lineIds.get(line);
        if (routeId == null) {
            return;
        }
        config.routeRules.put(FareConfig.bindingKey(FareRuntime.dimensionId(), routeId), rule.id);
        FareRuntime.get().save();
        rebuildOwners();
    }

    private void clearBinding(String line) {
        final Long routeId = lineIds.get(line);
        if (routeId == null) {
            return;
        }
        config.routeRules.remove(FareConfig.bindingKey(FareRuntime.dimensionId(), routeId));
        FareRuntime.get().save();
        rebuildOwners();
    }

    void onLeave() {
        if (host.isEditing()) {
            host.endField(false);
        }
        view = VIEW_MAIN;
        draft = null;
        editorMessage = "";
        dropKind = DROP_NONE;
        dropIndex = -1;
        dropScroll = 0F;
        selectedKind = SELECT_NONE;
        selectedIndex = -1;
        lastClickKind = SELECT_NONE;
        lastClickIndex = -1;
    }

    // ===== 绘制 =====

    void render(GuiGraphics guiGraphics, float mx, float my) {
        if (!inWorld()) {
            host.title(guiGraphics, Component.translatable("gui.trdm.ticket.title"));
            host.centred(guiGraphics, Component.translatable("gui.trdm.ticket.need_world"),
                    TrdmScreenBase.DESIGN_W / 2F, 250F, TrdmScreenBase.FONT_BODY, TrdmScreenBase.COLOR_SUB);
            return;
        }
        if (!canEdit()) {
            if (view == VIEW_MAIN) {
                renderMain(guiGraphics, mx, my);
            } else {
                renderDetail(guiGraphics, mx, my);
            }
            renderOverlay(guiGraphics);
            return;
        }
        if (view == VIEW_MAIN) {
            renderMain(guiGraphics, mx, my);
        } else {
            renderDetail(guiGraphics, mx, my);
        }
        if (dropKind != DROP_NONE) {
            renderDropdown(guiGraphics, mx, my);
        }
        if (confirmDeleteOpen) {
            renderDeleteConfirm(guiGraphics, mx, my);
        } else if (exportDialogOpen) {
            renderExportDialog(guiGraphics, mx, my);
        }
    }

    private void renderConfirmPanel(GuiGraphics guiGraphics) {
        host.rect(guiGraphics, 0F, 0F, TrdmScreenBase.DESIGN_W, TrdmScreenBase.DESIGN_H, 0x90000000);
        host.rect(guiGraphics, 219F, 219F, 522F, 152F, 0xFF9A9A9A);
        host.rect(guiGraphics, 220F, 220F, 520F, 150F, 0xF0202020);
    }

    private void renderDeleteConfirm(GuiGraphics guiGraphics, float mx, float my) {
        renderConfirmPanel(guiGraphics);
        final String name = detailRuleIndex >= 0 && detailRuleIndex < rules.size()
                ? rules.get(detailRuleIndex).name : "";
        host.centred(guiGraphics, Component.translatable("gui.trdm.ticket.delete_confirm", name),
                TrdmScreenBase.DESIGN_W / 2F, 262F, TrdmScreenBase.FONT_BODY, TrdmScreenBase.COLOR_TEXT);
        host.centred(guiGraphics, Component.translatable("gui.trdm.ticket.delete_confirm_hint"),
                TrdmScreenBase.DESIGN_W / 2F, 290F, TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
        final float deleteX = 350F;
        final float cancelX = 490F;
        host.buttonText(guiGraphics, deleteX, 320F, 120F, 28F,
                Component.translatable("gui.trdm.ticket.delete_rule"), false,
                host.hit(mx, my, deleteX, 320F, 120F, 28F), TrdmScreenBase.FONT_ROW);
        host.buttonText(guiGraphics, cancelX, 320F, 120F, 28F,
                Component.translatable("gui.trdm.script.cancel"), false,
                host.hit(mx, my, cancelX, 320F, 120F, 28F), TrdmScreenBase.FONT_ROW);
    }

    private void renderExportDialog(GuiGraphics guiGraphics, float mx, float my) {
        renderConfirmPanel(guiGraphics);
        host.centred(guiGraphics, Component.translatable("gui.trdm.ticket.export_saved", exportedFileName),
                TrdmScreenBase.DESIGN_W / 2F, 262F, TrdmScreenBase.FONT_BODY, TrdmScreenBase.COLOR_TEXT);
        host.centred(guiGraphics, Component.translatable("gui.trdm.ticket.export_folder_hint"),
                TrdmScreenBase.DESIGN_W / 2F, 290F, TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
        final float okX = 420F;
        host.buttonText(guiGraphics, okX, 320F, 120F, 28F,
                Component.translatable("gui.trdm.ticket.ok"), false,
                host.hit(mx, my, okX, 320F, 120F, 28F), TrdmScreenBase.FONT_ROW);
    }

    private void renderMain(GuiGraphics guiGraphics, float mx, float my) {
        host.title(guiGraphics, Component.translatable("gui.trdm.ticket.title"));
        host.text(guiGraphics, Component.translatable("gui.trdm.ticket.enable"),
                TrdmScreenBase.CONTENT_X, ENABLE_LABEL_Y, TrdmScreenBase.FONT_BODY, TrdmScreenBase.COLOR_TEXT);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.enable.sub"),
                TrdmScreenBase.CONTENT_X, ENABLE_SUB_Y);
        final float toggleX = TrdmScreenBase.CONTENT_RIGHT - TrdmScreenBase.TOGGLE_W;
        final Component state = Component.translatable(config.enabled
                ? "gui.trdm.config.on" : "gui.trdm.config.off");
        host.textRight(guiGraphics, state, toggleX - 10F, ENABLE_LABEL_Y + 4F,
                TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_TEXT);
        host.toggle(guiGraphics, toggleX, ENABLE_TOGGLE_Y, config.enabled);

        host.section(guiGraphics, "gui.trdm.ticket.rules", RULES_SECTION_Y);
        if (lines.isEmpty()) {
            host.textRight(guiGraphics, Component.translatable("gui.trdm.ticket.no_line_data"),
                    TrdmScreenBase.CONTENT_RIGHT - 230F, RULES_SECTION_Y + 5F,
                    TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_WARN);
        } else {
            host.textRight(guiGraphics, Component.translatable("gui.trdm.ticket.double_click"),
                    TrdmScreenBase.CONTENT_RIGHT - 230F, RULES_SECTION_Y + 5F,
                    TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
        }
        host.buttonText(guiGraphics, TrdmScreenBase.CONTENT_RIGHT - 178F, RULES_SECTION_Y - 3F,
                60F, 18F, Component.translatable("gui.trdm.ticket.import_short"), false,
                host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 178F, RULES_SECTION_Y - 3F, 60F, 18F),
                TrdmScreenBase.FONT_SUB);
        host.buttonText(guiGraphics, TrdmScreenBase.CONTENT_RIGHT - 112F, RULES_SECTION_Y - 3F,
                50F, 18F, Component.translatable("gui.trdm.ticket.add_rule_short"), false,
                host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 112F, RULES_SECTION_Y - 3F, 50F, 18F),
                TrdmScreenBase.FONT_SUB);
        host.buttonText(guiGraphics, TrdmScreenBase.CONTENT_RIGHT - 58F, RULES_SECTION_Y - 3F,
                50F, 18F, Component.translatable("gui.trdm.ticket.add_script_short"), false,
                host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 58F, RULES_SECTION_Y - 3F, 50F, 18F),
                TrdmScreenBase.FONT_SUB);

        renderRulesPanel(guiGraphics, mx, my);

        host.section(guiGraphics, "gui.trdm.ticket.templates", TEMPLATES_SECTION_Y);
        host.textRight(guiGraphics, Component.translatable("gui.trdm.ticket.double_click"),
                TrdmScreenBase.CONTENT_RIGHT - 84F, TEMPLATES_SECTION_Y + 5F,
                TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
        host.buttonText(guiGraphics, TrdmScreenBase.CONTENT_RIGHT - 80F, TEMPLATES_SECTION_Y - 3F,
                80F, 18F, Component.translatable("gui.trdm.ticket.pass_settings"), false,
                host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 80F, TEMPLATES_SECTION_Y - 3F, 80F, 18F),
                TrdmScreenBase.FONT_SUB);
        for (int index = 0; index < templates.size(); index++) {
            renderTemplateRow(guiGraphics, index, mx, my);
        }
    }

    private void renderRulesPanel(GuiGraphics guiGraphics, float mx, float my) {
        final float x = TrdmScreenBase.CONTENT_X;
        final float y = RULES_TOP;
        final float w = TrdmScreenBase.CONTENT_W;
        host.rect(guiGraphics, x - 2F, y - 2F, w + 4F, RULES_PANEL_H + 4F, 0xFF5A5A5A);
        host.rect(guiGraphics, x, y, w, RULES_PANEL_H, 0x50000000);
        host.scissorOn(guiGraphics, x, y, w, RULES_PANEL_H);
        renderRuleRow(guiGraphics, -1, 0, mx, my);
        for (int index = 0; index < rules.size(); index++) {
            renderRuleRow(guiGraphics, index, index + 1, mx, my);
        }
        host.scissorOff(guiGraphics);
        final float total = (rules.size() + 1) * ROW_H;
        host.scrollbar(guiGraphics, TrdmScreenBase.CONTENT_RIGHT - 6F, y, RULES_PANEL_H,
                total, RULES_PANEL_H, ruleScroll);
    }

    private void renderRuleRow(GuiGraphics guiGraphics, int ruleIndex, int visualIndex, float mx, float my) {
        final float top = RULES_TOP + visualIndex * ROW_H - ruleScroll;
        if (top + ROW_H < RULES_TOP || top > RULES_TOP + RULES_PANEL_H) {
            return;
        }
        final boolean hovered = hoveredRuleRow(top, mx, my);
        final boolean selected = selectedKind == (ruleIndex < 0 ? SELECT_SYSTEM : SELECT_RULE)
                && selectedIndex == ruleIndex;
        host.row(guiGraphics, TrdmScreenBase.CONTENT_X, top, TrdmScreenBase.CONTENT_W, ROW_H - 2F,
                selected, hovered);

        final String name = ruleIndex < 0
                ? Component.translatable("gui.trdm.ticket.mtr_rule").getString()
                : rules.get(ruleIndex).name;
        final float nameWidth = dropX() - TrdmScreenBase.CONTENT_X - 18F;
        host.text(guiGraphics, Component.literal(ellipsize(name, nameWidth, TrdmScreenBase.FONT_ROW)),
                TrdmScreenBase.CONTENT_X + 9F, top + 10F,
                TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_TEXT);

        host.textRight(guiGraphics, Component.translatable("gui.trdm.ticket.line_scope"),
                dropX() - 8F, top + 11F, TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
        renderDropBox(guiGraphics, dropX(), top + 6F, lineSummary(ruleIndex),
                dropKind == (ruleIndex < 0 ? DROP_SYSTEM : DROP_RULE) && dropIndex == ruleIndex, mx, my);
    }

    private void renderTemplateRow(GuiGraphics guiGraphics, int index, float mx, float my) {
        final float top = TEMPLATES_TOP + index * ROW_H;
        final boolean hovered = hoveredRow(top, mx, my);
        final boolean selected = selectedKind == SELECT_TEMPLATE && selectedIndex == index;
        host.row(guiGraphics, TrdmScreenBase.CONTENT_X, top, TrdmScreenBase.CONTENT_W, ROW_H - 2F,
                selected, hovered);

        final float nameWidth = dropX() - TrdmScreenBase.CONTENT_X - 18F;
        host.text(guiGraphics, Component.literal(ellipsize(templates.get(index).name, nameWidth,
                        TrdmScreenBase.FONT_ROW)), TrdmScreenBase.CONTENT_X + 9F, top + 10F,
                TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_TEXT);

        host.textRight(guiGraphics, Component.translatable("gui.trdm.ticket.rule_scope"),
                dropX() - 8F, top + 11F, TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
        renderDropBox(guiGraphics, dropX(), top + 6F, ruleSummary(templates.get(index)),
                dropKind == DROP_TEMPLATE && dropIndex == index, mx, my);
    }

    private void renderDropBox(GuiGraphics guiGraphics, float x, float y, String summary, boolean open,
                               float mx, float my) {
        host.rect(guiGraphics, x, y, DROP_W, DROP_H, TrdmScreenBase.COLOR_FIELD_BORDER);
        host.rect(guiGraphics, x + 1F, y + 1F, DROP_W - 2F, DROP_H - 2F, 0xFF1A1A1A);
        if (open) {
            host.rect(guiGraphics, x + 1F, y + 1F, DROP_W - 2F, DROP_H - 2F, 0x330A7A1E);
        }
        host.text(guiGraphics, Component.literal(ellipsize(summary, DROP_W - 32F, TrdmScreenBase.FONT_SUB)),
                x + 7F, y + 6F, TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_TEXT);
        final float triX = x + DROP_W - 14F;
        final float triY = y + 9F;
        final boolean hovered = host.hit(mx, my, x, y, DROP_W, DROP_H);
        for (int row = 0; row < 4; row++) {
            host.rect(guiGraphics, triX + row, triY + row, 7F - row * 2F, 1F,
                    hovered ? 0xFFFFFFFF : 0xFFB0B0B0);
        }
    }

    private void renderDropdown(GuiGraphics guiGraphics, float mx, float my) {
        final List<String> options = dropOptions();
        if (options.isEmpty()) {
            return;
        }
        final float[] rect = popupRect(options.size());
        host.rect(guiGraphics, rect[0] - 1F, rect[1] - 1F, rect[2] + 2F, rect[3] + 2F, 0xFF9A9A9A);
        host.rect(guiGraphics, rect[0], rect[1], rect[2], rect[3], 0xF0161616);
        host.scissorOn(guiGraphics, rect[0] + 1F, rect[1] + 1F, rect[2] - 2F, rect[3] - 2F);
        for (int index = 0; index < options.size(); index++) {
            final float top = rect[1] + 3F + index * DROP_ROW_H - dropScroll;
            if (top + DROP_ROW_H < rect[1] || top > rect[1] + rect[3]) {
                continue;
            }
            final boolean hovered = mx >= rect[0] && mx < rect[0] + rect[2]
                    && my >= top && my < top + DROP_ROW_H;
            if (hovered) {
                host.rect(guiGraphics, rect[0] + 1F, top, rect[2] - 2F, DROP_ROW_H, 0x33FFFFFF);
            }
            host.checkbox(guiGraphics, rect[0] + 8F, top + 3F, isChecked(index));
            host.text(guiGraphics, Component.literal(ellipsize(options.get(index), rect[2] - 40F,
                            TrdmScreenBase.FONT_SUB)), rect[0] + 28F, top + 4F,
                    TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_TEXT);
        }
        host.scissorOff(guiGraphics);
        host.scrollbar(guiGraphics, rect[0] + rect[2] - 5F, rect[1] + 2F, rect[3] - 4F,
                options.size() * DROP_ROW_H + 6F, rect[3] - 4F, dropScroll);
    }

    private void renderDetail(GuiGraphics guiGraphics, float mx, float my) {
        if (view == VIEW_RULE_EDIT) {
            renderRuleEditor(guiGraphics, mx, my);
            return;
        }
        final String title = view == VIEW_RULE ? ruleName(detailRuleIndex)
                : templates.get(detailTemplateIndex).name;
        host.title(guiGraphics, Component.literal(title));
        host.buttonText(guiGraphics, TrdmScreenBase.CONTENT_RIGHT - BACK_W, TrdmScreenBase.TITLE_Y,
                BACK_W, BACK_H, Component.translatable("gui.trdm.ticket.back"), false,
                host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - BACK_W, TrdmScreenBase.TITLE_Y, BACK_W, BACK_H),
                TrdmScreenBase.FONT_ROW);

        final String scope = view == VIEW_RULE
                ? Component.translatable("gui.trdm.ticket.line_scope").getString()
                : Component.translatable("gui.trdm.ticket.rule_scope").getString();
        host.text(guiGraphics, Component.literal(scope), TrdmScreenBase.CONTENT_X,
                DETAIL_SCOPE_LABEL_Y, TrdmScreenBase.FONT_BODY, TrdmScreenBase.COLOR_TEXT);
        final String summary = view == VIEW_RULE
                ? lineSummary(detailRuleIndex) : ruleSummary(templates.get(detailTemplateIndex));
        renderDropBox(guiGraphics, dropX(), DETAIL_SCOPE_Y, summary,
                (dropKind == DROP_DETAIL_RULE || dropKind == DROP_DETAIL_TEMPLATE), mx, my);

        if (view == VIEW_RULE) {
            renderRuleDetail(guiGraphics, mx, my);
        } else {
            renderTemplateDetail(guiGraphics, mx, my);
        }
    }

    private void renderRuleDetail(GuiGraphics guiGraphics, float mx, float my) {
        if (detailRuleIndex < 0) {
            host.text(guiGraphics, Component.translatable("gui.trdm.ticket.mtr_locked"),
                    TrdmScreenBase.CONTENT_X, DETAIL_NAME_SECTION_Y,
                    TrdmScreenBase.FONT_BODY, TrdmScreenBase.COLOR_WARN);
            host.section(guiGraphics, "gui.trdm.ticket.rule_content", DETAIL_CONTENT_SECTION_Y);
            host.text(guiGraphics, Component.translatable("gui.trdm.ticket.rule_vanilla_line"),
                    TrdmScreenBase.CONTENT_X, DETAIL_CONTENT_TOP,
                    TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_TEXT);
            return;
        }
        final FareRule rule = rules.get(detailRuleIndex);
        host.section(guiGraphics, "gui.trdm.ticket.rule_name", DETAIL_NAME_SECTION_Y);
        host.field(guiGraphics, FIELD_NAME, TrdmScreenBase.CONTENT_X, DETAIL_NAME_FIELD_Y,
                TrdmScreenBase.CONTENT_W * 0.62F, 24F, TrdmScreenBase.FONT_ROW, rule.name);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.name_placeholder"),
                TrdmScreenBase.CONTENT_X, DETAIL_NAME_FIELD_Y + 30F);

        host.section(guiGraphics, "gui.trdm.ticket.rule_content", DETAIL_CONTENT_SECTION_Y);
        renderRuleSummary(guiGraphics, rule);
        renderEditButton(guiGraphics, mx, my);
        final float exportX = TrdmScreenBase.CONTENT_RIGHT - 380F;
        host.buttonText(guiGraphics, exportX, DETAIL_EDIT_BUTTON_Y, 120F, 24F,
                Component.translatable("gui.trdm.ticket.export_rule"), false,
                host.hit(mx, my, exportX, DETAIL_EDIT_BUTTON_Y, 120F, 24F), TrdmScreenBase.FONT_ROW);
        final float deleteX = TrdmScreenBase.CONTENT_RIGHT - 250F;
        host.buttonText(guiGraphics, deleteX, DETAIL_EDIT_BUTTON_Y, 120F, 24F,
                Component.translatable("gui.trdm.ticket.delete_rule"), false,
                host.hit(mx, my, deleteX, DETAIL_EDIT_BUTTON_Y, 120F, 24F), TrdmScreenBase.FONT_ROW);
    }

    /** 规则详情：类型、票价档位、最高票价。 */
    private void renderRuleSummary(GuiGraphics guiGraphics, FareRule rule) {
        float y = DETAIL_CONTENT_TOP;
        summaryRow(guiGraphics, Component.translatable("gui.trdm.ticket.rule_type"), kindLabel(rule.kind), y);
        y += DETAIL_CONTENT_LINE;
        if (rule.kind == FareRule.Kind.FLAT) {
            summaryRow(guiGraphics, Component.translatable("gui.trdm.ticket.rule_fare"),
                    FareMoney.yuan(rule.flatFareCents), y);
            y += DETAIL_CONTENT_LINE;
        } else if (rule.kind == FareRule.Kind.SCRIPT) {
            summaryRow(guiGraphics, Component.translatable("gui.trdm.ticket.rule_blocks"),
                    String.valueOf(rule.script == null ? 0 : rule.script.nodes.size()), y);
            y += DETAIL_CONTENT_LINE;
        } else {
            int shown = 0;
            for (final FareRule.Tier tier : rule.tiers) {
                if (shown >= 6) {
                    summaryRow(guiGraphics, Component.literal("…"), "", y);
                    y += DETAIL_CONTENT_LINE;
                    break;
                }
                final Component label = tier.upTo() == null
                        ? Component.translatable("gui.trdm.ticket.rule_other")
                        : Component.literal("≤ " + tier.upTo() + " " + unitLabel(rule.kind));
                summaryRow(guiGraphics, label, FareMoney.yuan(tier.fareCents()), y);
                y += DETAIL_CONTENT_LINE;
                shown++;
            }
        }
        summaryRow(guiGraphics, Component.translatable("gui.trdm.ticket.rule_cap"),
                rule.maxFareCents > 0L ? FareMoney.yuan(rule.maxFareCents)
                        : Component.translatable("gui.trdm.ticket.rule_uncapped").getString(), y);
    }

    private void summaryRow(GuiGraphics guiGraphics, Component label, String value, float y) {
        host.text(guiGraphics, label, TrdmScreenBase.CONTENT_X, y,
                TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_SUB);
        if (value != null && !value.isEmpty()) {
            host.text(guiGraphics, Component.literal(value),
                    TrdmScreenBase.CONTENT_X + DETAIL_CONTENT_VALUE_X, y,
                    TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_TEXT);
        }
    }

    private String kindLabel(FareRule.Kind kind) {
        final String key = switch (kind) {
            case MTR_VANILLA -> "gui.trdm.ticket.kind.vanilla";
            case FLAT -> "gui.trdm.ticket.kind.flat";
            case INTERVAL_TABLE -> "gui.trdm.ticket.kind.interval";
            case DISTANCE_TABLE -> "gui.trdm.ticket.kind.distance";
            case SCRIPT -> "gui.trdm.ticket.kind.script";
        };
        return Component.translatable(key).getString();
    }

    private String unitLabel(FareRule.Kind kind) {
        return Component.translatable(kind == FareRule.Kind.DISTANCE_TABLE
                ? "gui.trdm.ticket.unit.meter" : "gui.trdm.ticket.unit.interval").getString();
    }

    private void renderTemplateDetail(GuiGraphics guiGraphics, float mx, float my) {
        final TemplateRow template = templates.get(detailTemplateIndex);
        host.section(guiGraphics, "gui.trdm.ticket.rule_name", DETAIL_NAME_SECTION_Y);
        host.field(guiGraphics, FIELD_NAME, TrdmScreenBase.CONTENT_X, DETAIL_NAME_FIELD_Y,
                TrdmScreenBase.CONTENT_W * 0.62F, 24F, TrdmScreenBase.FONT_ROW, template.name);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.name_placeholder"),
                TrdmScreenBase.CONTENT_X, DETAIL_NAME_FIELD_Y + 30F);

        host.section(guiGraphics, "gui.trdm.ticket.edit_template", DETAIL_CONTENT_SECTION_Y);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.edit_hint"),
                TrdmScreenBase.CONTENT_X, DETAIL_CONTENT_SECTION_Y + 20F);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.edit_hint2"),
                TrdmScreenBase.CONTENT_X, DETAIL_CONTENT_SECTION_Y + 34F);
        renderEditButton(guiGraphics, mx, my);
    }

    private void renderEditButton(GuiGraphics guiGraphics, float mx, float my) {
        final float x = TrdmScreenBase.CONTENT_RIGHT - 120F;
        host.buttonText(guiGraphics, x, DETAIL_EDIT_BUTTON_Y, 120F, 24F,
                Component.translatable("gui.trdm.ticket.edit_button"), false,
                host.hit(mx, my, x, DETAIL_EDIT_BUTTON_Y, 120F, 24F), TrdmScreenBase.FONT_ROW);
    }

    // ===== 规则编辑器 =====

    private void renderRuleEditor(GuiGraphics guiGraphics, float mx, float my) {
        host.title(guiGraphics, Component.translatable("gui.trdm.ticket.editor.title"));
        host.buttonText(guiGraphics, TrdmScreenBase.CONTENT_RIGHT - BACK_W, TrdmScreenBase.TITLE_Y,
                BACK_W, BACK_H, Component.translatable("gui.trdm.ticket.back"), false,
                host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - BACK_W, TrdmScreenBase.TITLE_Y, BACK_W, BACK_H),
                TrdmScreenBase.FONT_ROW);
        if (draft == null) {
            return;
        }

        host.section(guiGraphics, "gui.trdm.ticket.rule_type", EDITOR_TYPE_SECTION_Y);
        renderKindButton(guiGraphics, mx, my, 0, FareRule.Kind.FLAT, "gui.trdm.ticket.kind.flat");
        renderKindButton(guiGraphics, mx, my, 1, FareRule.Kind.INTERVAL_TABLE, "gui.trdm.ticket.kind.interval");
        renderKindButton(guiGraphics, mx, my, 2, FareRule.Kind.DISTANCE_TABLE, "gui.trdm.ticket.kind.distance");

        if (draft.kind == FareRule.Kind.FLAT) {
            host.section(guiGraphics, "gui.trdm.ticket.rule_fare", EDITOR_CONTENT_SECTION_Y);
            host.field(guiGraphics, flatFareField, TrdmScreenBase.CONTENT_X, EDITOR_FLAT_FIELD_Y, 200F, 24F,
                    TrdmScreenBase.FONT_ROW, FareMoney.text(draft.flatFareCents));
            host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.editor.yuan"),
                    TrdmScreenBase.CONTENT_X + 210F, EDITOR_FLAT_FIELD_Y + 7F);
            host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.editor.flat_hint"),
                    TrdmScreenBase.CONTENT_X, EDITOR_FLAT_FIELD_Y + 34F);
        } else {
            renderTierEditor(guiGraphics, mx, my);
        }

        final float capY = capSectionY();
        host.section(guiGraphics, "gui.trdm.ticket.rule_cap", capY);
        host.field(guiGraphics, maxFareField, TrdmScreenBase.CONTENT_X, capY + 26F,
                EDITOR_CAP_FIELD_W, EDITOR_CAP_FIELD_H, TrdmScreenBase.FONT_ROW,
                FareMoney.text(draft.maxFareCents));
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.editor.max_hint"),
                TrdmScreenBase.CONTENT_X + 172F, capY + 33F);

        renderEditorButtons(guiGraphics, mx, my);
        final String note = !editorMessage.isEmpty() ? editorMessage : capWarning();
        if (!note.isEmpty()) {
            host.text(guiGraphics, Component.literal(note),
                    TrdmScreenBase.CONTENT_X + EDITOR_MESSAGE_X, EDITOR_BUTTON_Y + 9F,
                    TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_WARN);
        }
    }

    /** 最高票价低于档位票价时提醒：超出的部分会被该规则封顶。 */
    private String capWarning() {
        if (draft == null || draft.maxFareCents <= 0L) {
            return "";
        }
        long highest = draft.kind == FareRule.Kind.FLAT ? draft.flatFareCents : 0L;
        if (draft.kind != FareRule.Kind.FLAT) {
            for (final FareRule.Tier tier : draft.tiers) {
                highest = Math.max(highest, tier.fareCents());
            }
        }
        return draft.maxFareCents < highest
                ? Component.translatable("gui.trdm.ticket.editor.cap_warning").getString() : "";
    }

    private void renderKindButton(GuiGraphics guiGraphics, float mx, float my, int slot,
                                  FareRule.Kind kind, String key) {
        final float x = TrdmScreenBase.CONTENT_X + slot * EDITOR_TYPE_GAP;
        host.buttonText(guiGraphics, x, EDITOR_TYPE_BUTTON_Y, EDITOR_TYPE_BUTTON_W, EDITOR_TYPE_BUTTON_H,
                Component.translatable(key), draft.kind == kind,
                host.hit(mx, my, x, EDITOR_TYPE_BUTTON_Y, EDITOR_TYPE_BUTTON_W, EDITOR_TYPE_BUTTON_H),
                TrdmScreenBase.FONT_ROW);
    }

    private void renderTierEditor(GuiGraphics guiGraphics, float mx, float my) {
        host.section(guiGraphics, "gui.trdm.ticket.editor.tiers", EDITOR_CONTENT_SECTION_Y);
        host.text(guiGraphics, Component.translatable("gui.trdm.ticket.editor.limit"),
                EDITOR_LIMIT_X, EDITOR_TABLE_HEADER_Y, TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
        host.text(guiGraphics, Component.translatable("gui.trdm.ticket.editor.fare"),
                EDITOR_FARE_X, EDITOR_TABLE_HEADER_Y, TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);

        for (int index = 0; index < draft.tiers.size(); index++) {
            final FareRule.Tier tier = draft.tiers.get(index);
            final float rowY = EDITOR_TABLE_TOP + index * EDITOR_TABLE_ROW_H;
            final boolean open = tier.upTo() == null;
            if (open) {
                host.text(guiGraphics, Component.translatable("gui.trdm.ticket.rule_other"),
                        EDITOR_LIMIT_X, rowY + 5F, TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_TEXT);
            } else {
                host.text(guiGraphics, Component.literal("≤"), TrdmScreenBase.CONTENT_X, rowY + 5F,
                        TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_TEXT);
                host.field(guiGraphics, tierLimitFields.get(index), EDITOR_LIMIT_X, rowY + 1F,
                        EDITOR_LIMIT_W, EDITOR_TABLE_FIELD_H, TrdmScreenBase.FONT_ROW,
                        Long.toString(tier.upTo()));
                host.text(guiGraphics, Component.literal(unitLabel(draft.kind)),
                        EDITOR_UNIT_X, rowY + 5F, TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
            }
            host.text(guiGraphics, Component.literal("¥"), EDITOR_FARE_LABEL_X, rowY + 5F,
                    TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_TEXT);
            host.field(guiGraphics, tierFareFields.get(index), EDITOR_FARE_X, rowY + 1F,
                    EDITOR_FARE_W, EDITOR_TABLE_FIELD_H, TrdmScreenBase.FONT_ROW,
                    FareMoney.text(tier.fareCents()));
            if (!open) {
                host.buttonText(guiGraphics, EDITOR_DELETE_X, rowY + 1F, EDITOR_DELETE_W, EDITOR_TABLE_FIELD_H,
                        Component.translatable("gui.trdm.ticket.editor.delete"), false,
                        host.hit(mx, my, EDITOR_DELETE_X, rowY + 1F, EDITOR_DELETE_W, EDITOR_TABLE_FIELD_H),
                        TrdmScreenBase.FONT_SUB);
            }
        }

        final float addY = addTierButtonY();
        host.buttonText(guiGraphics, TrdmScreenBase.CONTENT_X, addY, EDITOR_ADD_W, EDITOR_ADD_H,
                Component.translatable("gui.trdm.ticket.editor.add_tier"), false,
                host.hit(mx, my, TrdmScreenBase.CONTENT_X, addY, EDITOR_ADD_W, EDITOR_ADD_H),
                TrdmScreenBase.FONT_SUB);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.editor.hint"),
                TrdmScreenBase.CONTENT_X + 136F, addY + 8F);
    }

    private float addTierButtonY() {
        return EDITOR_TABLE_TOP + draft.tiers.size() * EDITOR_TABLE_ROW_H + 6F;
    }

    private float capSectionY() {
        if (draft != null && draft.kind == FareRule.Kind.FLAT) {
            return EDITOR_FLAT_FIELD_Y + 48F;
        }
        return addTierButtonY() + 40F;
    }

    private void renderEditorButtons(GuiGraphics guiGraphics, float mx, float my) {
        host.buttonText(guiGraphics, TrdmScreenBase.CONTENT_X, EDITOR_BUTTON_Y, EDITOR_SAVE_W, EDITOR_BUTTON_H,
                Component.translatable("gui.trdm.ticket.editor.save"), false,
                host.hit(mx, my, TrdmScreenBase.CONTENT_X, EDITOR_BUTTON_Y, EDITOR_SAVE_W, EDITOR_BUTTON_H),
                TrdmScreenBase.FONT_ROW);
        host.buttonText(guiGraphics, TrdmScreenBase.CONTENT_X + EDITOR_CANCEL_X, EDITOR_BUTTON_Y,
                EDITOR_CANCEL_W, EDITOR_BUTTON_H,
                Component.translatable("gui.trdm.ticket.editor.cancel"), false,
                host.hit(mx, my, TrdmScreenBase.CONTENT_X + EDITOR_CANCEL_X, EDITOR_BUTTON_Y,
                        EDITOR_CANCEL_W, EDITOR_BUTTON_H), TrdmScreenBase.FONT_ROW);
    }

    private boolean clickRuleEditor(float mx, float my) {
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - BACK_W, TrdmScreenBase.TITLE_Y, BACK_W, BACK_H)) {
            cancelEditor();
            return true;
        }
        if (draft == null) {
            return false;
        }
        final FareRule.Kind[] kinds = {
                FareRule.Kind.FLAT, FareRule.Kind.INTERVAL_TABLE, FareRule.Kind.DISTANCE_TABLE
        };
        for (int slot = 0; slot < kinds.length; slot++) {
            final float x = TrdmScreenBase.CONTENT_X + slot * EDITOR_TYPE_GAP;
            if (host.hit(mx, my, x, EDITOR_TYPE_BUTTON_Y, EDITOR_TYPE_BUTTON_W, EDITOR_TYPE_BUTTON_H)) {
                switchKind(kinds[slot]);
                return true;
            }
        }
        if (draft.kind == FareRule.Kind.FLAT) {
            if (host.hit(mx, my, TrdmScreenBase.CONTENT_X, EDITOR_FLAT_FIELD_Y, 200F, 24F)) {
                host.beginField(flatFareField, TrdmScreenBase.CONTENT_X, EDITOR_FLAT_FIELD_Y, 200F, 24F,
                        FareMoney.text(draft.flatFareCents), 7, TrdmScreenBase.FONT_ROW, this::commitFlatFare);
                return true;
            }
        } else {
            for (int index = 0; index < draft.tiers.size(); index++) {
                final int slot = index;
                final FareRule.Tier tier = draft.tiers.get(index);
                final float rowY = EDITOR_TABLE_TOP + index * EDITOR_TABLE_ROW_H;
                final boolean open = tier.upTo() == null;
                if (!open && host.hit(mx, my, EDITOR_LIMIT_X, rowY + 1F, EDITOR_LIMIT_W, EDITOR_TABLE_FIELD_H)) {
                    host.beginField(tierLimitFields.get(index), EDITOR_LIMIT_X, rowY + 1F,
                            EDITOR_LIMIT_W, EDITOR_TABLE_FIELD_H, Long.toString(tier.upTo()), 7,
                            TrdmScreenBase.FONT_ROW, value -> commitTierLimit(slot, value));
                    return true;
                }
                if (host.hit(mx, my, EDITOR_FARE_X, rowY + 1F, EDITOR_FARE_W, EDITOR_TABLE_FIELD_H)) {
                    host.beginField(tierFareFields.get(index), EDITOR_FARE_X, rowY + 1F,
                            EDITOR_FARE_W, EDITOR_TABLE_FIELD_H, FareMoney.text(tier.fareCents()), 7,
                            TrdmScreenBase.FONT_ROW, value -> commitTierFare(slot, value));
                    return true;
                }
                if (!open && host.hit(mx, my, EDITOR_DELETE_X, rowY + 1F, EDITOR_DELETE_W, EDITOR_TABLE_FIELD_H)) {
                    deleteTier(index);
                    return true;
                }
            }
            if (host.hit(mx, my, TrdmScreenBase.CONTENT_X, addTierButtonY(), EDITOR_ADD_W, EDITOR_ADD_H)) {
                addTier();
                return true;
            }
        }

        final float capY = capSectionY();
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_X, capY + 26F, EDITOR_CAP_FIELD_W, EDITOR_CAP_FIELD_H)) {
            host.beginField(maxFareField, TrdmScreenBase.CONTENT_X, capY + 26F, EDITOR_CAP_FIELD_W,
                    EDITOR_CAP_FIELD_H, FareMoney.text(draft.maxFareCents), 7,
                    TrdmScreenBase.FONT_ROW, this::commitMaxFare);
            return true;
        }
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_X, EDITOR_BUTTON_Y, EDITOR_SAVE_W, EDITOR_BUTTON_H)) {
            applyDraft();
            return true;
        }
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_X + EDITOR_CANCEL_X, EDITOR_BUTTON_Y,
                EDITOR_CANCEL_W, EDITOR_BUTTON_H)) {
            cancelEditor();
            return true;
        }
        return false;
    }

    private void openEditor() {
        if (detailRuleIndex < 0 || detailRuleIndex >= rules.size()) {
            return;
        }
        if (rules.get(detailRuleIndex).kind == FareRule.Kind.SCRIPT) {
            Minecraft.getInstance().setScreen(new FareScriptScreen(host, rules.get(detailRuleIndex)));
            return;
        }
        final FareRule rule = rules.get(detailRuleIndex);
        if (rule.kind != FareRule.Kind.MTR_VANILLA) {
            if (rule.migrateToScript()) {
                FareRuntime.get().save();
            }
            if (rule.kind == FareRule.Kind.SCRIPT) {
                Minecraft.getInstance().setScreen(new FareScriptScreen(host, rule));
                return;
            }
        }
        draft = rules.get(detailRuleIndex).copy();
        if (draft.kind == FareRule.Kind.MTR_VANILLA) {
            draft.kind = FareRule.Kind.FLAT;
            if (draft.flatFareCents <= 0L) {
                draft.flatFareCents = 200L;
            }
        }
        if (draft.tiers.isEmpty() || draft.tiers.get(draft.tiers.size() - 1).upTo() != null) {
            final long fare = draft.tiers.isEmpty() ? 0L
                    : draft.tiers.get(draft.tiers.size() - 1).fareCents();
            draft.tiers.add(new FareRule.Tier(null, fare));
        }
        resetEditorFields();
        editorMessage = "";
        view = VIEW_RULE_EDIT;
    }

    private void addFareRule() {
        final FareRule rule = new FareRule(uniqueRuleId(),
                Component.translatable("gui.trdm.ticket.new_rule_default").getString(), FareRule.Kind.SCRIPT);
        rule.script = top.xiaotang27.trdm.fare.script.Scripts.flat(2.0);
        rule.maxFareCents = 400L;
        config.rules.add(rule);
        FareRuntime.get().save();
        detailRuleIndex = config.rules.size() - 1;
        view = VIEW_RULE;
        ruleScroll = Math.max(0F, (rules.size() + 1) * ROW_H - RULES_PANEL_H);
    }

    /** 把选中的规则导出成单独的文件；文件名使用规则名。 */
    private void exportSelectedRule() {
        if (detailRuleIndex < 0 || detailRuleIndex >= rules.size()) {
            return;
        }
        final FareRule rule = rules.get(detailRuleIndex);
        final FareConfig export = new FareConfig();
        export.enabled = config.enabled;
        export.mode = config.mode;
        export.metric = config.metric;
        export.transferDiscountCents = config.transferDiscountCents;
        export.mergeSameRule = config.mergeSameRule;
        export.rules.add(rule.copy());

        final String fileName = exportFileName(rule.name);
        final Path dir = FabricLoader.getInstance().getConfigDir().resolve("trdm_exports");
        try {
            Files.createDirectories(dir);
            final Path file = dir.resolve(fileName);
            Files.writeString(file, FareJson.write(export), StandardCharsets.UTF_8);
            exportedFile = file;
            exportedFileName = fileName;
            exportDialogOpen = true;
            confirmDeleteOpen = false;
        } catch (Exception exception) {
            showMessage(Component.translatable("gui.trdm.ticket.export_failed",
                    String.valueOf(exception.getMessage())));
        }
    }

    private static String exportFileName(String ruleName) {
        String safe = ruleName == null ? "" : ruleName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isBlank()) {
            safe = "rule";
        }
        if (safe.length() > 48) {
            safe = safe.substring(0, 48);
        }
        return "trdmfares_" + safe + ".json";
    }

    private void requestImport() {
        showMessage(Component.translatable("gui.trdm.ticket.import_hint"));
    }

    /** 处理拖入窗口的 JSON 文件。 */
    void importFiles(List<Path> paths) {
        if (paths == null || paths.isEmpty() || !canEdit()) {
            return;
        }
        confirmDeleteOpen = false;
        exportDialogOpen = false;
        int imported = 0;
        boolean invalid = false;
        for (final Path path : paths) {
            if (path == null || !Files.isRegularFile(path)) {
                continue;
            }
            final String text;
            try {
                text = Files.readString(path, StandardCharsets.UTF_8);
            } catch (Exception exception) {
                invalid = true;
                continue;
            }
            if (!looksLikeTrdmRules(text)) {
                invalid = true;
                continue;
            }
            final FareConfig parsed;
            try {
                parsed = FareJson.parse(text);
            } catch (Exception exception) {
                invalid = true;
                continue;
            }
            if (parsed.rules.isEmpty()) {
                invalid = true;
                continue;
            }
            for (final FareRule source : parsed.rules) {
                final FareRule copy = source.copy();
                if (copy.id == null || copy.id.isBlank() || findRule(copy.id) != null) {
                    copy.id = uniqueRuleId();
                }
                copy.name = uniqueRuleName(copy.name);
                config.rules.add(copy);
                imported++;
            }
        }
        if (imported > 0) {
            FareRuntime.get().save();
            detailRuleIndex = config.rules.size() - 1;
            selectedKind = SELECT_RULE;
            selectedIndex = detailRuleIndex;
            view = VIEW_RULE;
            ruleScroll = Math.max(0F, (rules.size() + 1) * ROW_H - RULES_PANEL_H);
            rebuildOwners();
            showMessage(Component.translatable("gui.trdm.ticket.import_done", imported));
        } else if (invalid) {
            showMessage(Component.translatable("gui.trdm.ticket.import_invalid"));
        }
    }

    private boolean looksLikeTrdmRules(String text) {
        try {
            final JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                return false;
            }
            final JsonObject object = root.getAsJsonObject();
            if (!object.has("rules") || !object.get("rules").isJsonArray()) {
                return false;
            }
            final JsonArray array = object.getAsJsonArray("rules");
            for (final JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                final JsonObject rule = element.getAsJsonObject();
                final boolean hasName = rule.has("name") && rule.get("name").isJsonPrimitive()
                        && !rule.get("name").getAsString().isBlank();
                if (!hasName) {
                    continue;
                }
                if (rule.has("script") && rule.get("script").isJsonObject()) {
                    return true;
                }
                if (rule.has("kind") && rule.get("kind").isJsonPrimitive()) {
                    final String kind = rule.get("kind").getAsString();
                    if ("MTR_VANILLA".equals(kind) || "FLAT".equals(kind)
                            || "INTERVAL_TABLE".equals(kind) || "DISTANCE_TABLE".equals(kind)
                            || "SCRIPT".equals(kind)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception exception) {
            return false;
        }
    }

    private String uniqueRuleName(String name) {
        String base = name == null || name.isBlank()
                ? Component.translatable("gui.trdm.ticket.import_default_name").getString()
                : name.trim();
        String candidate = base;
        int index = 2;
        while (findRuleByName(candidate) != null) {
            candidate = base + " (" + index + ")";
            index++;
        }
        return candidate;
    }

    private FareRule findRuleByName(String name) {
        for (final FareRule rule : config.rules) {
            if (rule.name.equals(name)) {
                return rule;
            }
        }
        return null;
    }

    private void showMessage(Component message) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }

    private void requestDeleteRule() {
        confirmDeleteOpen = true;
        exportDialogOpen = false;
    }

    private void openExportedFolder() {
        if (exportedFile == null) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(exportedFile.getParent().toFile());
            }
        } catch (Exception ignored) {
            // 打不开文件夹时文件仍然已保存。
        }
    }

    private void addScriptRule() {
        final FareRule rule = new FareRule(uniqueRuleId(),
                Component.translatable("gui.trdm.ticket.new_rule_default").getString(), FareRule.Kind.SCRIPT);
        rule.script = top.xiaotang27.trdm.fare.script.Scripts.flat(2.0);
        config.rules.add(rule);
        FareRuntime.get().save();
        detailRuleIndex = config.rules.size() - 1;
        view = VIEW_RULE;
        ruleScroll = Math.max(0F, (rules.size() + 1) * ROW_H - RULES_PANEL_H);
    }

    private String uniqueRuleId() {
        int index = 1;
        while (findRule("rule_" + index) != null) {
            index++;
        }
        return "rule_" + index;
    }

    private FareRule findRule(String id) {
        for (final FareRule rule : config.rules) {
            if (rule.id.equals(id)) {
                return rule;
            }
        }
        return null;
    }

    private void deleteRule() {
        if (detailRuleIndex < 0 || detailRuleIndex >= config.rules.size()) {
            return;
        }
        final FareRule rule = config.rules.get(detailRuleIndex);
        config.rules.remove(detailRuleIndex);
        config.routeRules.values().removeIf(rule.id::equals);
        FareRuntime.get().save();
        detailRuleIndex = -1;
        selectedKind = SELECT_SYSTEM;
        selectedIndex = -1;
        view = VIEW_MAIN;
        rebuildOwners();
        ruleScroll = Math.max(0F, (rules.size() + 1) * ROW_H - RULES_PANEL_H);
    }

    private void cancelEditor() {
        if (host.isEditing()) {
            host.endField(false);
        }
        draft = null;
        editorMessage = "";
        view = VIEW_RULE;
    }

    /** 切换规则类型时把票价“带过去”，避免切到阶梯后出现 0 元票价。 */
    private void switchKind(FareRule.Kind kind) {
        if (draft == null || draft.kind == kind) {
            return;
        }
        final FareRule.Kind previous = draft.kind;
        draft.kind = kind;
        if (kind == FareRule.Kind.FLAT) {
            final FareRule.Tier open = lastOpenTier();
            if (open != null && open.fareCents() > 0L) {
                draft.flatFareCents = open.fareCents();
            }
        } else if (previous == FareRule.Kind.FLAT || previous == FareRule.Kind.MTR_VANILLA) {
            ensureTableTiers(kind);
        }
        editorMessage = "";
    }

    private void ensureTableTiers(FareRule.Kind kind) {
        final boolean empty = draft.tiers.isEmpty();
        final boolean singleZeroOpen = draft.tiers.size() == 1
                && draft.tiers.get(0).upTo() == null && draft.tiers.get(0).fareCents() <= 0L;
        if (!empty && !singleZeroOpen) {
            return;
        }
        final long base = draft.flatFareCents > 0L ? draft.flatFareCents : 200L;
        draft.tiers.clear();
        if (kind == FareRule.Kind.DISTANCE_TABLE) {
            draft.tiers.add(new FareRule.Tier(1000L, base));
            draft.tiers.add(new FareRule.Tier(null, base + 100L));
        } else {
            draft.tiers.add(new FareRule.Tier(2L, base));
            draft.tiers.add(new FareRule.Tier(null, base + 100L));
        }
        if (draft.maxFareCents > 0L && draft.maxFareCents < base + 100L) {
            draft.maxFareCents = base + 100L;
        }
        resetEditorFields();
    }

    private FareRule.Tier lastOpenTier() {
        for (int index = draft.tiers.size() - 1; index >= 0; index--) {
            if (draft.tiers.get(index).upTo() == null) {
                return draft.tiers.get(index);
            }
        }
        return null;
    }

    private void applyDraft() {
        if (draft == null || detailRuleIndex < 0 || detailRuleIndex >= rules.size()) {
            cancelEditor();
            return;
        }
        final FareRule target = rules.get(detailRuleIndex);
        target.kind = draft.kind;
        target.flatFareCents = Math.max(0L, draft.flatFareCents);
        target.maxFareCents = Math.max(0L, draft.maxFareCents);
        target.tiers.clear();
        if (target.kind != FareRule.Kind.FLAT) {
            target.tiers.addAll(normalizedTiers());
        }
        FareRuntime.get().save();
        rebuildOwners();
        draft = null;
        editorMessage = "";
        view = VIEW_RULE;
    }

    private List<FareRule.Tier> normalizedTiers() {
        final List<FareRule.Tier> bounded = new ArrayList<>();
        FareRule.Tier open = null;
        for (final FareRule.Tier tier : draft.tiers) {
            if (tier.upTo() == null) {
                open = tier;
            } else {
                bounded.add(tier);
            }
        }
        bounded.sort(java.util.Comparator.comparingLong(FareRule.Tier::upTo));
        final List<FareRule.Tier> result = new ArrayList<>(bounded);
        if (open != null) {
            result.add(open);
        } else if (!bounded.isEmpty()) {
            result.add(new FareRule.Tier(null, bounded.get(bounded.size() - 1).fareCents()));
        }
        return result;
    }

    private void resetEditorFields() {
        tierLimitFields.clear();
        tierFareFields.clear();
        if (draft == null) {
            return;
        }
        for (int index = 0; index < draft.tiers.size(); index++) {
            tierLimitFields.add(new Object());
            tierFareFields.add(new Object());
        }
    }

    private void addTier() {
        if (draft == null || draft.kind == FareRule.Kind.FLAT) {
            return;
        }
        if (draft.tiers.size() >= EDITOR_MAX_TIERS) {
            editorMessage = Component.translatable("gui.trdm.ticket.editor.max_tiers",
                    EDITOR_MAX_TIERS).getString();
            return;
        }
        final long step = draft.kind == FareRule.Kind.DISTANCE_TABLE ? 1000L : 1L;
        long upTo = step;
        long fare = 0L;
        if (draft.tiers.size() >= 2) {
            final FareRule.Tier previous = draft.tiers.get(draft.tiers.size() - 2);
            if (previous.upTo() != null) {
                upTo = previous.upTo() + step;
                fare = previous.fareCents();
            }
        }
        draft.tiers.add(Math.max(0, draft.tiers.size() - 1), new FareRule.Tier(upTo, fare));
        resetEditorFields();
        editorMessage = "";
    }

    private void deleteTier(int index) {
        if (draft == null || index < 0 || index >= draft.tiers.size() - 1) {
            return;
        }
        draft.tiers.remove(index);
        if (draft.tiers.isEmpty()) {
            draft.tiers.add(new FareRule.Tier(null, 0L));
        } else if (draft.tiers.get(draft.tiers.size() - 1).upTo() != null) {
            draft.tiers.add(new FareRule.Tier(null,
                    draft.tiers.get(draft.tiers.size() - 1).fareCents()));
        }
        resetEditorFields();
        editorMessage = "";
    }

    private void commitFlatFare(String value) {
        final Long cents = parseMoneyCents(value);
        if (cents == null) {
            editorMessage = invalidMessage();
            return;
        }
        draft.flatFareCents = cents;
        editorMessage = "";
    }

    private void commitMaxFare(String value) {
        final Long cents = parseMoneyCents(value);
        if (cents == null) {
            editorMessage = invalidMessage();
            return;
        }
        draft.maxFareCents = cents;
        editorMessage = "";
    }

    private void commitTierLimit(int index, String value) {
        final Long limit = parseCount(value);
        if (limit == null) {
            editorMessage = invalidMessage();
            return;
        }
        if (draft == null || index < 0 || index >= draft.tiers.size()) {
            return;
        }
        final FareRule.Tier tier = draft.tiers.get(index);
        draft.tiers.set(index, new FareRule.Tier(limit, tier.fareCents()));
        editorMessage = "";
    }

    private void commitTierFare(int index, String value) {
        final Long cents = parseMoneyCents(value);
        if (cents == null) {
            editorMessage = invalidMessage();
            return;
        }
        if (draft == null || index < 0 || index >= draft.tiers.size()) {
            return;
        }
        final FareRule.Tier tier = draft.tiers.get(index);
        draft.tiers.set(index, new FareRule.Tier(tier.upTo(), cents));
        editorMessage = "";
    }

    private String invalidMessage() {
        return Component.translatable("gui.trdm.ticket.editor.invalid").getString();
    }

    /** 解析“元”文本为分；最多两位小数。非法返回 {@code null}。 */
    private static Long parseMoneyCents(String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            final java.math.BigDecimal value = new java.math.BigDecimal(trimmed);
            if (value.signum() < 0 || value.scale() > 2) {
                return null;
            }
            return value.movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    /** 解析档位上限，必须是正整数。 */
    private static Long parseCount(String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            final long value = Long.parseLong(trimmed);
            return value > 0L ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }


    private void renderOverlay(GuiGraphics guiGraphics) {
        host.rect(guiGraphics, TrdmScreenBase.SIDEBAR_W, 0F,
                TrdmScreenBase.DESIGN_W - TrdmScreenBase.SIDEBAR_W, TrdmScreenBase.DESIGN_H, 0xB4000000);
        host.centred(guiGraphics, Component.translatable("gui.trdm.ticket.no_permission1"),
                TrdmScreenBase.DESIGN_W / 2F, 228F, 20F, TrdmScreenBase.COLOR_TEXT);
        host.centred(guiGraphics, Component.translatable("gui.trdm.ticket.no_permission2"),
                TrdmScreenBase.DESIGN_W / 2F, 258F, TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_SUB);
        host.centred(guiGraphics, Component.translatable("gui.trdm.ticket.no_permission3"),
                TrdmScreenBase.DESIGN_W / 2F, 278F, TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
    }

    // ===== 点击 / 滚轮 / 键盘 =====

    boolean click(float mx, float my) {
        if (!inWorld()) {
            return false;
        }
        if (confirmDeleteOpen) {
            if (host.hit(mx, my, 350F, 320F, 120F, 28F)) {
                confirmDeleteOpen = false;
                deleteRule();
            } else if (host.hit(mx, my, 490F, 320F, 120F, 28F)) {
                confirmDeleteOpen = false;
            }
            return true;
        }
        if (exportDialogOpen) {
            if (host.hit(mx, my, 420F, 320F, 120F, 28F)) {
                exportDialogOpen = false;
                openExportedFolder();
            }
            return true;
        }
        if (!canEdit()) {
            return mx >= TrdmScreenBase.SIDEBAR_W;
        }
        if (dropKind != DROP_NONE) {
            if (clickDropdown(mx, my)) {
                return true;
            }
            dropKind = DROP_NONE;
        }
        return view == VIEW_MAIN ? clickMain(mx, my) : clickDetail(mx, my);
    }

    private boolean clickMain(float mx, float my) {
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 178F, RULES_SECTION_Y - 3F, 60F, 18F)) {
            requestImport();
            return true;
        }
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 112F, RULES_SECTION_Y - 3F, 50F, 18F)) {
            addFareRule();
            return true;
        }
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 58F, RULES_SECTION_Y - 3F, 50F, 18F)) {
            addScriptRule();
            return true;
        }
        final float toggleX = TrdmScreenBase.CONTENT_RIGHT - TrdmScreenBase.TOGGLE_W;
        if (host.hit(mx, my, toggleX, ENABLE_TOGGLE_Y, TrdmScreenBase.TOGGLE_W, TrdmScreenBase.TOGGLE_H)) {
            config.enabled = !config.enabled;
            FareRuntime.get().save();
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(config.enabled
                        ? "gui.trdm.ticket.toggle.on" : "gui.trdm.ticket.toggle.off"), false);
            }
            return true;
        }

        if (host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 80F, TEMPLATES_SECTION_Y - 3F, 80F, 18F)) {
            Minecraft.getInstance().setScreen(new PassRulesScreen(host));
            return true;
        }
        if (clickRuleRow(-1, 0, mx, my)) {
            return true;
        }
        for (int index = 0; index < rules.size(); index++) {
            if (clickRuleRow(index, index + 1, mx, my)) {
                return true;
            }
        }
        for (int index = 0; index < templates.size(); index++) {
            if (clickTemplateRow(index, mx, my)) {
                return true;
            }
        }
        return false;
    }

    private boolean clickRuleRow(int ruleIndex, int visualIndex, float mx, float my) {
        final float top = RULES_TOP + visualIndex * ROW_H - ruleScroll;
        if (top + ROW_H < RULES_TOP || top > RULES_TOP + RULES_PANEL_H) {
            return false;
        }
        if (host.hit(mx, my, dropX(), top + 6F, DROP_W, DROP_H)) {
            openDrop(ruleIndex < 0 ? DROP_SYSTEM : DROP_RULE, ruleIndex);
            return true;
        }
        if (hoveredRuleRow(top, mx, my)) {
            rowClick(ruleIndex < 0 ? SELECT_SYSTEM : SELECT_RULE, ruleIndex);
            return true;
        }
        return false;
    }

    private boolean clickTemplateRow(int index, float mx, float my) {
        final float top = TEMPLATES_TOP + index * ROW_H;
        if (host.hit(mx, my, dropX(), top + 6F, DROP_W, DROP_H)) {
            openDrop(DROP_TEMPLATE, index);
            return true;
        }
        if (hoveredRow(top, mx, my)) {
            rowClick(SELECT_TEMPLATE, index);
            return true;
        }
        return false;
    }

    private boolean clickDetail(float mx, float my) {
        if (view == VIEW_RULE_EDIT) {
            return clickRuleEditor(mx, my);
        }
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - BACK_W, TrdmScreenBase.TITLE_Y, BACK_W, BACK_H)) {
            backToMain();
            return true;
        }
        if (host.hit(mx, my, dropX(), DETAIL_SCOPE_Y, DROP_W, DROP_H)) {
            openDrop(view == VIEW_RULE ? DROP_DETAIL_RULE : DROP_DETAIL_TEMPLATE,
                    view == VIEW_RULE ? detailRuleIndex : detailTemplateIndex);
            return true;
        }
        if (view == VIEW_RULE && detailRuleIndex < 0) {
            return false;
        }
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_X, DETAIL_NAME_FIELD_Y,
                TrdmScreenBase.CONTENT_W * 0.62F, 24F)) {
            final String current = view == VIEW_RULE
                    ? rules.get(detailRuleIndex).name : templates.get(detailTemplateIndex).name;
            host.beginField(FIELD_NAME, TrdmScreenBase.CONTENT_X, DETAIL_NAME_FIELD_Y,
                    TrdmScreenBase.CONTENT_W * 0.62F, 24F, current, 60, TrdmScreenBase.FONT_ROW, value -> {
                        if (value != null && !value.isBlank()) {
                            final String trimmed = value.trim();
                            if (view == VIEW_RULE) {
                                rules.get(detailRuleIndex).name = trimmed;
                                FareRuntime.get().save();
                            } else {
                                templates.get(detailTemplateIndex).name = trimmed;
                            }
                        }
                    });
            return true;
        }
        if (view == VIEW_RULE && detailRuleIndex >= 0
                && host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 380F,
                DETAIL_EDIT_BUTTON_Y, 120F, 24F)) {
            exportSelectedRule();
            return true;
        }
        if (view == VIEW_RULE && detailRuleIndex >= 0 && host.hit(mx, my,
                TrdmScreenBase.CONTENT_RIGHT - 250F, DETAIL_EDIT_BUTTON_Y, 120F, 24F)) {
            requestDeleteRule();
            return true;
        }
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 120F, DETAIL_EDIT_BUTTON_Y, 120F, 24F)) {
            if (view == VIEW_RULE && detailRuleIndex >= 0) {
                openEditor();
            } else {
                final Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(
                            Component.translatable("gui.trdm.ticket.editor_todo"), true);
                }
            }
            return true;
        }
        return false;
    }

    boolean scroll(double delta, float mx, float my) {
        if (dropKind != DROP_NONE) {
            final float[] rect = popupRect(dropOptions().size());
            if (host.hit(mx, my, rect[0], rect[1], rect[2], rect[3])) {
                final int count = dropOptions().size();
                final float max = Math.max(0F, count * DROP_ROW_H + 6F - rect[3]);
                dropScroll = Math.max(0F, Math.min(max, dropScroll - (float) delta * DROP_ROW_H));
                return true;
            }
        }
        if (view == VIEW_MAIN && mx >= TrdmScreenBase.CONTENT_X && mx < TrdmScreenBase.CONTENT_RIGHT
                && my >= RULES_TOP && my < RULES_TOP + RULES_PANEL_H) {
            final float total = (rules.size() + 1) * ROW_H;
            final float max = Math.max(0F, total - RULES_PANEL_H);
            ruleScroll = Math.max(0F, Math.min(max, ruleScroll - (float) delta * 20F));
            return true;
        }
        return false;
    }

    boolean keyPressed(int keyCode) {
        if (confirmDeleteOpen) {
            if (keyCode == 256) {
                confirmDeleteOpen = false;
            }
            return true;
        }
        if (exportDialogOpen) {
            if (keyCode == 256) {
                exportDialogOpen = false;
            }
            return true;
        }
        if (keyCode == 256 && !host.isEditing()) {
            if (view == VIEW_RULE_EDIT) {
                cancelEditor();
                return true;
            }
            if (view != VIEW_MAIN) {
                backToMain();
                return true;
            }
        }
        return false;
    }

    private void rowClick(int kind, int index) {
        final long now = System.currentTimeMillis();
        if (lastClickKind == kind && lastClickIndex == index && now - lastClickAt <= 350L) {
            lastClickKind = SELECT_NONE;
            lastClickIndex = -1;
            if (kind == SELECT_TEMPLATE) {
                detailTemplateIndex = index;
                view = VIEW_TEMPLATE;
            } else {
                detailRuleIndex = index;
                view = VIEW_RULE;
            }
            dropKind = DROP_NONE;
            return;
        }
        lastClickKind = kind;
        lastClickIndex = index;
        lastClickAt = now;
        selectedKind = kind;
        selectedIndex = index;
    }

    private void backToMain() {
        if (host.isEditing()) {
            host.endField(true);
        }
        view = VIEW_MAIN;
        draft = null;
        editorMessage = "";
        dropKind = DROP_NONE;
        dropScroll = 0F;
        selectedKind = SELECT_NONE;
        selectedIndex = -1;
    }

    // ===== 下拉 =====

    private void openDrop(int kind, int index) {
        dropKind = kind;
        dropIndex = index;
        dropScroll = 0F;
    }

    private float dropX() {
        return TrdmScreenBase.CONTENT_RIGHT - DROP_W - 10F;
    }

    private float dropAnchorY() {
        return switch (dropKind) {
            case DROP_RULE -> RULES_TOP + (dropIndex + 1) * ROW_H - ruleScroll + 6F;
            case DROP_SYSTEM -> RULES_TOP - ruleScroll + 6F;
            case DROP_TEMPLATE -> TEMPLATES_TOP + dropIndex * ROW_H + 6F;
            default -> DETAIL_SCOPE_Y;
        };
    }

    private float[] popupRect(int count) {
        final float h = Math.min(count, DROP_VISIBLE) * DROP_ROW_H + 6F;
        float y = dropAnchorY() + DROP_H + 3F;
        if (y + h > TrdmScreenBase.DESIGN_H - 16F) {
            y = dropAnchorY() - h - 3F;
        }
        return new float[]{dropX(), y, DROP_W, h};
    }

    private List<String> dropOptions() {
        return switch (dropKind) {
            case DROP_RULE, DROP_SYSTEM, DROP_DETAIL_RULE -> lines;
            case DROP_TEMPLATE, DROP_DETAIL_TEMPLATE -> ruleOptionNames();
            default -> List.of();
        };
    }

    private boolean isChecked(int option) {
        return switch (dropKind) {
            case DROP_SYSTEM -> !lineOwner.containsKey(lines.get(option));
            case DROP_RULE, DROP_DETAIL_RULE -> lineOwner.getOrDefault(lines.get(option), -1)
                    == ruleIndexOfDrop();
            case DROP_TEMPLATE, DROP_DETAIL_TEMPLATE ->
                    templateForDrop().rules.contains(ruleOptions().get(option));
            default -> false;
        };
    }

    private boolean clickDropdown(float mx, float my) {
        final List<String> options = dropOptions();
        if (options.isEmpty()) {
            return false;
        }
        final float[] rect = popupRect(options.size());
        if (!host.hit(mx, my, rect[0], rect[1], rect[2], rect[3])) {
            return false;
        }
        final int option = (int) Math.floor((my - rect[1] - 3F + dropScroll) / DROP_ROW_H);
        if (option < 0 || option >= options.size()) {
            return true;
        }
        switch (dropKind) {
            case DROP_SYSTEM -> clearBinding(lines.get(option));
            case DROP_RULE, DROP_DETAIL_RULE -> {
                final String line = lines.get(option);
                final int ruleIndex = ruleIndexOfDrop();
                if (lineOwner.getOrDefault(line, -1) == ruleIndex) {
                    clearBinding(line);
                } else if (ruleIndex >= 0 && ruleIndex < rules.size()) {
                    setBinding(line, rules.get(ruleIndex));
                }
            }
            case DROP_TEMPLATE, DROP_DETAIL_TEMPLATE -> {
                final int ruleIndex = ruleOptions().get(option);
                final TemplateRow template = templateForDrop();
                if (!template.rules.remove(ruleIndex)) {
                    template.rules.add(ruleIndex);
                }
            }
            default -> {
            }
        }
        return true;
    }

    private int ruleIndexOfDrop() {
        return dropKind == DROP_DETAIL_RULE ? detailRuleIndex : dropIndex;
    }

    private TemplateRow templateForDrop() {
        return templates.get(dropKind == DROP_DETAIL_TEMPLATE ? detailTemplateIndex : dropIndex);
    }

    private List<Integer> ruleOptions() {
        final List<Integer> result = new ArrayList<>();
        result.add(-1);
        for (int index = 0; index < rules.size(); index++) {
            result.add(index);
        }
        return result;
    }

    private List<String> ruleOptionNames() {
        final List<String> result = new ArrayList<>();
        for (final int index : ruleOptions()) {
            result.add(ruleName(index));
        }
        return result;
    }

    // ===== 数据摘要 =====

    private List<String> linesFor(int ruleIndex) {
        final List<String> result = new ArrayList<>();
        for (final String line : lines) {
            if (lineOwner.getOrDefault(line, -1) == ruleIndex) {
                result.add(line);
            }
        }
        return result;
    }

    private String lineSummary(int ruleIndex) {
        final List<String> assigned = linesFor(ruleIndex);
        if (assigned.isEmpty()) {
            return Component.translatable("gui.trdm.ticket.none_lines").getString();
        }
        if (assigned.size() == 1) {
            return assigned.get(0);
        }
        return Component.translatable("gui.trdm.ticket.count_lines", assigned.get(0),
                assigned.size() - 1).getString();
    }

    private String ruleSummary(TemplateRow template) {
        final List<String> names = new ArrayList<>();
        for (final int index : new LinkedHashSet<>(template.rules)) {
            names.add(ruleName(index));
        }
        if (names.isEmpty()) {
            return Component.translatable("gui.trdm.ticket.none_rules").getString();
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return Component.translatable("gui.trdm.ticket.count_rules", names.get(0),
                names.size() - 1).getString();
    }

    private String ruleName(int index) {
        if (index < 0) {
            return Component.translatable("gui.trdm.ticket.mtr_rule").getString();
        }
        if (index >= rules.size()) {
            return "";
        }
        return rules.get(index).name;
    }

    // ===== 工具 =====

    private boolean hoveredRow(float top, float mx, float my) {
        return mx >= TrdmScreenBase.CONTENT_X && mx < TrdmScreenBase.CONTENT_RIGHT
                && my >= top && my < top + ROW_H - 2F;
    }

    private boolean hoveredRuleRow(float top, float mx, float my) {
        return my >= RULES_TOP && my < RULES_TOP + RULES_PANEL_H && hoveredRow(top, mx, my);
    }

    private boolean inWorld() {
        return Minecraft.getInstance().player != null;
    }

    /** 权限以服务端下发的快照为准（未同步时退回客户端近似判断）。 */
    private boolean canEdit() {
        return FareRuntime.get().canEdit();
    }

    private String ellipsize(String text, float maxWidth, float fontPx) {
        final String value = text == null ? "" : text;
        if (host.textWidth(Component.literal(value), fontPx) <= maxWidth) {
            return value;
        }
        final String ellipsis = "…";
        final float tail = host.textWidth(Component.literal(ellipsis), fontPx);
        final StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            final String next = builder.toString() + value.charAt(index);
            if (host.textWidth(Component.literal(next), fontPx) + tail > maxWidth) {
                break;
            }
            builder.append(value.charAt(index));
        }
        return builder + ellipsis;
    }

    private static final class TemplateRow {
        private String name;
        private final Set<Integer> rules = new HashSet<>();

        private TemplateRow(String name) {
            this.name = name;
        }
    }
}
