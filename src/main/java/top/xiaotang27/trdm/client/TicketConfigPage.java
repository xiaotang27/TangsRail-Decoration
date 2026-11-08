package top.xiaotang27.trdm.client;

import mtr.client.ClientData;
import mtr.data.Route;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 通用设置里的“票务系统”页：票务规则列表 + 通用车票模板列表、规则/模板详情、非管理员遮罩。
 * <p>
 * 目前是纯客户端界面骨架：规则/线路分配只是内存里的演示数据，等票务服务端快照接入后再替换。
 */
final class TicketConfigPage {

    // ===== 布局 =====
    private static final float ENABLE_LABEL_Y = 64F;
    private static final float ENABLE_SUB_Y = 82F;
    private static final float ENABLE_TOGGLE_Y = 60F;

    private static final float RULES_SECTION_Y = 112F;
    private static final float RULES_TOP = 136F;
    private static final float TEMPLATES_SECTION_Y = 286F;
    private static final float TEMPLATES_TOP = 310F;
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
    private static final float DETAIL_EDIT_SECTION_Y = 192F;
    private static final float DETAIL_EDIT_BUTTON_Y = 198F;

    private static final Object FIELD_NAME = new Object();

    // ===== 视图 / 下拉状态 =====
    private static final int VIEW_MAIN = 0;
    private static final int VIEW_RULE = 1;
    private static final int VIEW_TEMPLATE = 2;

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

    private final List<String> lines = new ArrayList<>();
    private final List<RuleRow> rules = new ArrayList<>();
    private final List<TemplateRow> templates = new ArrayList<>();
    /** 线路名 → 自定义规则下标；不存在的线路 = 系统行（MTR 原版公式）。 */
    private final java.util.Map<String, Integer> lineOwner = new java.util.HashMap<>();

    private boolean enabled = true;

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

    TicketConfigPage(TrdmScreenBase host) {
        this.host = host;
        seed();
    }

    // ===== 初始化 =====

    private void seed() {
        final List<String> real = new ArrayList<>();
        for (final Route route : ClientData.ROUTES) {
            if (route.name != null && !route.name.isBlank()) {
                real.add(route.name);
            }
        }
        real.sort(String.CASE_INSENSITIVE_ORDER);
        lines.addAll(real);

        rules.add(new RuleRow("按里程分段计费"));
        rules.add(new RuleRow("按区间分段计费"));
        rules.add(new RuleRow("自定义规则1"));

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

    void onLeave() {
        if (host.isEditing()) {
            host.endField(false);
        }
        view = VIEW_MAIN;
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
    }

    private void renderMain(GuiGraphics guiGraphics, float mx, float my) {
        host.title(guiGraphics, Component.translatable("gui.trdm.ticket.title"));
        host.text(guiGraphics, Component.translatable("gui.trdm.ticket.enable"),
                TrdmScreenBase.CONTENT_X, ENABLE_LABEL_Y, TrdmScreenBase.FONT_BODY, TrdmScreenBase.COLOR_TEXT);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.enable.sub"),
                TrdmScreenBase.CONTENT_X, ENABLE_SUB_Y);
        final float toggleX = TrdmScreenBase.CONTENT_RIGHT - TrdmScreenBase.TOGGLE_W;
        final Component state = Component.translatable(enabled
                ? "gui.trdm.config.on" : "gui.trdm.config.off");
        host.textRight(guiGraphics, state, toggleX - 10F, ENABLE_LABEL_Y + 4F,
                TrdmScreenBase.FONT_ROW, TrdmScreenBase.COLOR_TEXT);
        host.toggle(guiGraphics, toggleX, ENABLE_TOGGLE_Y, enabled);

        host.section(guiGraphics, "gui.trdm.ticket.rules", RULES_SECTION_Y);
        if (lines.isEmpty()) {
            host.textRight(guiGraphics, Component.translatable("gui.trdm.ticket.no_line_data"),
                    TrdmScreenBase.CONTENT_RIGHT, RULES_SECTION_Y + 5F,
                    TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_WARN);
        } else {
            host.textRight(guiGraphics, Component.translatable("gui.trdm.ticket.double_click"),
                    TrdmScreenBase.CONTENT_RIGHT, RULES_SECTION_Y + 5F,
                    TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
        }

        renderRuleRow(guiGraphics, -1, 0, mx, my);
        for (int index = 0; index < rules.size(); index++) {
            renderRuleRow(guiGraphics, index, index + 1, mx, my);
        }

        host.section(guiGraphics, "gui.trdm.ticket.templates", TEMPLATES_SECTION_Y);
        host.textRight(guiGraphics, Component.translatable("gui.trdm.ticket.double_click"),
                TrdmScreenBase.CONTENT_RIGHT, TEMPLATES_SECTION_Y + 5F,
                TrdmScreenBase.FONT_SUB, TrdmScreenBase.COLOR_SUB);
        for (int index = 0; index < templates.size(); index++) {
            renderTemplateRow(guiGraphics, index, mx, my);
        }
    }

    private void renderRuleRow(GuiGraphics guiGraphics, int ruleIndex, int visualIndex, float mx, float my) {
        final float top = RULES_TOP + visualIndex * ROW_H;
        final boolean hovered = hoveredRow(top, mx, my);
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
            return;
        }
        final RuleRow rule = rules.get(detailRuleIndex);
        host.section(guiGraphics, "gui.trdm.ticket.rule_name", DETAIL_NAME_SECTION_Y);
        host.field(guiGraphics, FIELD_NAME, TrdmScreenBase.CONTENT_X, DETAIL_NAME_FIELD_Y,
                TrdmScreenBase.CONTENT_W * 0.62F, 24F, TrdmScreenBase.FONT_ROW, rule.name);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.name_placeholder"),
                TrdmScreenBase.CONTENT_X, DETAIL_NAME_FIELD_Y + 30F);

        host.section(guiGraphics, "gui.trdm.ticket.edit_rule", DETAIL_EDIT_SECTION_Y);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.edit_hint"),
                TrdmScreenBase.CONTENT_X, DETAIL_EDIT_SECTION_Y + 20F);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.edit_hint2"),
                TrdmScreenBase.CONTENT_X, DETAIL_EDIT_SECTION_Y + 34F);
        renderEditButton(guiGraphics, mx, my);
    }

    private void renderTemplateDetail(GuiGraphics guiGraphics, float mx, float my) {
        final TemplateRow template = templates.get(detailTemplateIndex);
        host.section(guiGraphics, "gui.trdm.ticket.rule_name", DETAIL_NAME_SECTION_Y);
        host.field(guiGraphics, FIELD_NAME, TrdmScreenBase.CONTENT_X, DETAIL_NAME_FIELD_Y,
                TrdmScreenBase.CONTENT_W * 0.62F, 24F, TrdmScreenBase.FONT_ROW, template.name);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.name_placeholder"),
                TrdmScreenBase.CONTENT_X, DETAIL_NAME_FIELD_Y + 30F);

        host.section(guiGraphics, "gui.trdm.ticket.edit_template", DETAIL_EDIT_SECTION_Y);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.edit_hint"),
                TrdmScreenBase.CONTENT_X, DETAIL_EDIT_SECTION_Y + 20F);
        host.hint(guiGraphics, Component.translatable("gui.trdm.ticket.edit_hint2"),
                TrdmScreenBase.CONTENT_X, DETAIL_EDIT_SECTION_Y + 34F);
        renderEditButton(guiGraphics, mx, my);
    }

    private void renderEditButton(GuiGraphics guiGraphics, float mx, float my) {
        final float x = TrdmScreenBase.CONTENT_RIGHT - 120F;
        host.buttonText(guiGraphics, x, DETAIL_EDIT_BUTTON_Y, 120F, 24F,
                Component.translatable("gui.trdm.ticket.edit_button"), false,
                host.hit(mx, my, x, DETAIL_EDIT_BUTTON_Y, 120F, 24F), TrdmScreenBase.FONT_ROW);
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
        final float toggleX = TrdmScreenBase.CONTENT_RIGHT - TrdmScreenBase.TOGGLE_W;
        if (host.hit(mx, my, toggleX, ENABLE_TOGGLE_Y, TrdmScreenBase.TOGGLE_W, TrdmScreenBase.TOGGLE_H)) {
            enabled = !enabled;
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
        final float top = RULES_TOP + visualIndex * ROW_H;
        if (host.hit(mx, my, dropX(), top + 6F, DROP_W, DROP_H)) {
            openDrop(ruleIndex < 0 ? DROP_SYSTEM : DROP_RULE, ruleIndex);
            return true;
        }
        if (hoveredRow(top, mx, my)) {
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
                            } else {
                                templates.get(detailTemplateIndex).name = trimmed;
                            }
                        }
                    });
            return true;
        }
        if (host.hit(mx, my, TrdmScreenBase.CONTENT_RIGHT - 120F, DETAIL_EDIT_BUTTON_Y, 120F, 24F)) {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(
                        Component.translatable("gui.trdm.ticket.editor_todo"), true);
            }
            return true;
        }
        return false;
    }

    boolean scroll(double delta, float mx, float my) {
        if (dropKind == DROP_NONE) {
            return false;
        }
        final float[] rect = popupRect(dropOptions().size());
        if (!host.hit(mx, my, rect[0], rect[1], rect[2], rect[3])) {
            return false;
        }
        final int count = dropOptions().size();
        final float max = Math.max(0F, count * DROP_ROW_H + 6F - rect[3]);
        dropScroll = Math.max(0F, Math.min(max, dropScroll - (float) delta * DROP_ROW_H));
        return true;
    }

    boolean keyPressed(int keyCode) {
        if (keyCode == 256 && view != VIEW_MAIN && !host.isEditing()) {
            backToMain();
            return true;
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
        return TrdmScreenBase.CONTENT_RIGHT - DROP_W;
    }

    private float dropAnchorY() {
        return switch (dropKind) {
            case DROP_RULE -> RULES_TOP + (dropIndex + 1) * ROW_H + 6F;
            case DROP_SYSTEM -> RULES_TOP + 6F;
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
            case DROP_SYSTEM -> lineOwner.remove(lines.get(option));
            case DROP_RULE, DROP_DETAIL_RULE -> {
                final String line = lines.get(option);
                final int ruleIndex = ruleIndexOfDrop();
                if (lineOwner.getOrDefault(line, -1) == ruleIndex) {
                    lineOwner.remove(line);
                } else {
                    lineOwner.put(line, ruleIndex);
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
        return rules.get(index).name;
    }

    // ===== 工具 =====

    private boolean hoveredRow(float top, float mx, float my) {
        return mx >= TrdmScreenBase.CONTENT_X && mx < TrdmScreenBase.CONTENT_RIGHT
                && my >= top && my < top + ROW_H - 2F;
    }

    private boolean inWorld() {
        return Minecraft.getInstance().player != null;
    }

    /** 服务端快照接入前先用客户端可见信息做 UX 判断；真正的权限以服务端为准。 */
    private boolean canEdit() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() != null) {
            return true;
        }
        return minecraft.player != null && minecraft.player.hasPermissions(2);
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

    private static final class RuleRow {
        private String name;

        private RuleRow(String name) {
            this.name = name;
        }
    }

    private static final class TemplateRow {
        private String name;
        private final Set<Integer> rules = new HashSet<>();

        private TemplateRow(String name) {
            this.name = name;
        }
    }
}
