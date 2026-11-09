package top.xiaotang27.trdm.client;

import net.minecraft.client.Minecraft;
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
 * 最小可用的积木规则编辑器（块列表，不是自由画布）。
 * <p>
 * 左侧是语句链，右侧是选中块的参数和值槽；值类型用按钮循环切换，
 * 阶梯表直接编辑行。保存前走 {@link ScriptCompiler} 校验。
 */
public final class FareScriptScreen extends Screen {

    private static final String[] STATEMENT_TYPES = {
            "set_fare", "add_fare", "multiply_fare", "cap_fare", "floor_fare", "round_fare"};
    private static final String[] VALUE_TYPES = {
            "number", "context_intervals", "context_meters", "context_stations",
            "context_zone_diff", "table_intervals", "table_meters"};

    private final Screen parent;
    private final FareRule rule;
    private final FareScript script;
    private int selected;
    private String error = "";
    private int leftX;
    private int leftW;
    private int rightX;
    private int rightW;
    private int rowsTop;

    public FareScriptScreen(Screen parent, FareRule rule) {
        super(Component.translatable("gui.trdm.script.title"));
        this.parent = parent;
        this.rule = rule;
        this.script = rule.script == null ? Scripts.empty() : rule.script.copy();
        Scripts.ensureHat(this.script);
        this.selected = chain().isEmpty() ? -1 : 0;
    }

    @Override
    protected void init() {
        this.leftX = 20;
        this.leftW = Math.min(320, Math.max(220, this.width / 2 - 30));
        this.rightX = this.leftX + this.leftW + 16;
        this.rightW = Math.max(160, this.width - this.rightX - 20);
        this.rowsTop = 58;

        final List<ScriptNode> chain = chain();
        final int shown = Math.min(chain.size(), 8);
        for (int index = 0; index < shown; index++) {
            final int row = index;
            final ScriptNode node = chain.get(index);
            final String prefix = index == selected ? "▶ " : "   ";
            addRenderableWidget(Button.builder(
                            Component.literal(prefix + (index + 1) + ". " + blockLabel(node)),
                            button -> {
                                selected = row;
                                error = "";
                                rebuild();
                            })
                    .bounds(leftX, rowsTop + index * 24, leftW, 20).build());
        }
        if (chain.size() > 8) {
            addRenderableWidget(Button.builder(Component.literal("…"), button -> {
            }).bounds(leftX, rowsTop + 8 * 24, leftW, 16).build()).active = false;
        }

        final int controlsY = rowsTop + Math.min(chain.size(), 8) * 24 + 6;
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.add_statement"),
                        button -> addStatement())
                .bounds(leftX, controlsY, leftW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.up"),
                        button -> move(-1))
                .bounds(leftX, controlsY + 24, leftW / 3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.down"),
                        button -> move(1))
                .bounds(leftX + leftW / 3, controlsY + 24, leftW / 3, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.delete"),
                        button -> deleteStatement())
                .bounds(leftX + leftW * 2 / 3, controlsY + 24, leftW / 3, 20).build());

        if (selected >= 0 && selected < chain.size()) {
            final ScriptNode node = chain.get(selected);
            final int typeY = rowsTop + 24;
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.trdm.script.block_type", blockLabel(node)),
                            button -> cycleStatementType(node))
                    .bounds(rightX, typeY, rightW, 20).build());
            if (valueStatement(node.type)) {
                final int valueY = typeY + 26;
                final ScriptNode value = script.node(node.inputs.get("value"));
                addRenderableWidget(Button.builder(
                                Component.translatable("gui.trdm.script.value_type",
                                        value == null ? Component.translatable("gui.trdm.script.none")
                                                : Component.literal(valueLabel(value))),
                                button -> cycleValueType(node, value))
                        .bounds(rightX, valueY, rightW, 20).build());
                if (value != null) {
                    addValueWidgets(value, valueY + 26);
                }
            } else if ("round_fare".equals(node.type)) {
                addRenderableWidget(Button.builder(
                                Component.literal(node.fields.getOrDefault("mode", "HALF")),
                                button -> {
                                    node.fields.put("mode", nextMode(node.fields.getOrDefault("mode", "HALF")));
                                    rebuild();
                                })
                        .bounds(rightX, typeY + 26, 80, 20).build());
                final EditBox unit = new EditBox(this.font, rightX + 88, typeY + 26, 80, 20,
                        Component.translatable("gui.trdm.script.unit"));
                unit.setValue(node.fields.getOrDefault("unit", "0.1"));
                unit.setResponder(text -> node.fields.put("unit", text));
                addRenderableWidget(unit);
            }
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.editor.save"),
                        button -> save())
                .bounds(this.width / 2 - 106, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.back"),
                        button -> onClose())
                .bounds(this.width / 2 + 6, this.height - 28, 100, 20).build());
    }

    private void addValueWidgets(ScriptNode value, int y) {
        if ("number".equals(value.type)) {
            final EditBox box = new EditBox(this.font, rightX, y, 100, 20,
                    Component.translatable("gui.trdm.script.unit"));
            box.setValue(value.fields.getOrDefault("value", "0"));
            box.setResponder(text -> value.fields.put("value", text));
            addRenderableWidget(box);
            return;
        }
        if ("table_intervals".equals(value.type) || "table_meters".equals(value.type)) {
            final int shown = Math.min(value.tiers.size(), 6);
            for (int index = 0; index < shown; index++) {
                final int row = index;
                final ScriptTier tier = value.tiers.get(index);
                final int rowY = y + index * 24;
                final EditBox upTo = new EditBox(this.font, rightX, rowY, 80, 20,
                        Component.translatable("gui.trdm.script.tier_up_to"));
                upTo.setValue(tier.upTo() == null ? "" : trimNumber(tier.upTo()));
                upTo.setResponder(text -> setTierUpTo(value, row, text));
                addRenderableWidget(upTo);
                final EditBox fare = new EditBox(this.font, rightX + 86, rowY, 80, 20,
                        Component.translatable("gui.trdm.script.tier_fare"));
                fare.setValue(trimNumber(tier.fareYuan()));
                fare.setResponder(text -> setTierFare(value, row, text));
                addRenderableWidget(fare);
                addRenderableWidget(Button.builder(Component.literal("×"), button -> {
                            value.tiers.remove(row);
                            error = "";
                            rebuild();
                        })
                        .bounds(rightX + 172, rowY, 20, 20).build());
            }
            addRenderableWidget(Button.builder(Component.translatable("gui.trdm.script.add_row"),
                            button -> addTier(value))
                    .bounds(rightX, y + shown * 24, 120, 20).build());
        }
    }

    private void setTierUpTo(ScriptNode value, int row, String text) {
        try {
            final ScriptTier current = value.tiers.get(row);
            final Double upTo = text == null || text.isBlank() ? null : Double.parseDouble(text.trim());
            value.tiers.set(row, new ScriptTier(upTo, current.fareYuan()));
        } catch (NumberFormatException ignored) {
            // 让玩家把数字打完
        }
    }

    private void setTierFare(ScriptNode value, int row, String text) {
        try {
            final ScriptTier current = value.tiers.get(row);
            value.tiers.set(row, new ScriptTier(current.upTo(), Double.parseDouble(text.trim())));
        } catch (NumberFormatException ignored) {
            // 让玩家把数字打完
        }
    }

    private void addTier(ScriptNode value) {
        if (value.tiers.size() >= 6) {
            error = Component.translatable("gui.trdm.script.limit_tiers").getString();
            rebuild();
            return;
        }
        double next = 10.0;
        if (!value.tiers.isEmpty()) {
            final ScriptTier last = value.tiers.get(value.tiers.size() - 1);
            if (last.upTo() != null) {
                next = last.upTo() + 10.0;
            }
        }
        int insert = value.tiers.size();
        if (insert > 0 && value.tiers.get(insert - 1).upTo() == null) {
            insert--;
        }
        value.tiers.add(insert, new ScriptTier(next, 0.0));
        error = "";
        rebuild();
    }

    private void addStatement() {
        final List<ScriptNode> chain = chain();
        if (chain.size() >= 8) {
            error = Component.translatable("gui.trdm.script.limit_statements").getString();
            rebuild();
            return;
        }
        final ScriptNode node = new ScriptNode(Scripts.uniqueId(script, "s"), "set_fare");
        final ScriptNode value = new ScriptNode(Scripts.uniqueId(script, "v"), "number");
        value.fields.put("value", "2.0");
        node.inputs.put("value", value.id);
        script.nodes.add(node);
        script.nodes.add(value);
        if (chain.isEmpty()) {
            script.node(script.hat).next = node.id;
        } else {
            chain.get(chain.size() - 1).next = node.id;
        }
        selected = chain.size();
        error = "";
        rebuild();
    }

    private void deleteStatement() {
        final List<ScriptNode> chain = chain();
        if (selected < 0 || selected >= chain.size()) {
            return;
        }
        final ScriptNode node = chain.get(selected);
        if (selected == 0) {
            script.node(script.hat).next = node.next;
        } else {
            chain.get(selected - 1).next = node.next;
        }
        script.nodes.remove(node);
        selected = Math.min(selected, chain.size() - 2);
        if (selected < 0 && !chain.isEmpty()) {
            selected = 0;
        }
        error = "";
        rebuild();
    }

    private void move(int delta) {
        final List<ScriptNode> chain = chain();
        final int target = selected + delta;
        if (selected < 0 || selected >= chain.size() || target < 0 || target >= chain.size()) {
            return;
        }
        final List<String> ids = new ArrayList<>();
        for (final ScriptNode node : chain) {
            ids.add(node.id);
        }
        final String moved = ids.remove(selected);
        ids.add(target, moved);
        script.node(script.hat).next = ids.get(0);
        for (int index = 0; index < ids.size(); index++) {
            script.node(ids.get(index)).next = index + 1 < ids.size() ? ids.get(index + 1) : null;
        }
        selected = target;
        error = "";
        rebuild();
    }

    private void cycleStatementType(ScriptNode node) {
        int index = 0;
        for (int i = 0; i < STATEMENT_TYPES.length; i++) {
            if (STATEMENT_TYPES[i].equals(node.type)) {
                index = i;
                break;
            }
        }
        node.type = STATEMENT_TYPES[(index + 1) % STATEMENT_TYPES.length];
        if (valueStatement(node.type) && script.node(node.inputs.get("value")) == null) {
            final ScriptNode value = new ScriptNode(Scripts.uniqueId(script, "v"), "number");
            value.fields.put("value", "2.0");
            script.nodes.add(value);
            node.inputs.put("value", value.id);
        }
        error = "";
        rebuild();
    }

    private void cycleValueType(ScriptNode statement, ScriptNode current) {
        int index = -1;
        for (int i = 0; i < VALUE_TYPES.length; i++) {
            if (current != null && VALUE_TYPES[i].equals(current.type)) {
                index = i;
                break;
            }
        }
        final String type = VALUE_TYPES[(index + 1) % VALUE_TYPES.length];
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
        statement.inputs.put("value", value.id);
        error = "";
        rebuild();
    }

    private void save() {
        if (hasUnsupported()) {
            error = Component.translatable("gui.trdm.script.unsupported").getString();
            rebuild();
            return;
        }
        final ScriptCompiler.Result result = ScriptCompiler.compile(script);
        if (result.compiled() == null) {
            error = String.join("；", result.errors());
            rebuild();
            return;
        }
        // 服务端同步可能已经替换了配置里的规则对象，保存时按 ID 找当前对象。
        final FareRule current = FareRuntime.get().config().ruleById(rule.id);
        final FareRule target = current != null ? current : rule;
        target.script = script.copy();
        FareRuntime.get().save();
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.script.title"), 20, 16, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.script.statements"),
                leftX, rowsTop - 14, 0xA0A0A0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.script.parameters"),
                rightX, rowsTop - 14, 0xA0A0A0, false);
        if (!error.isEmpty()) {
            guiGraphics.drawString(this.font, error, 20, this.height - 44, 0xFF5555, false);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

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

    private boolean hasUnsupported() {
        for (final ScriptNode node : chain()) {
            if ("if".equals(node.type) || (node.otherwise != null && !node.otherwise.isBlank())) {
                return true;
            }
        }
        return false;
    }

    private static boolean valueStatement(String type) {
        return "set_fare".equals(type) || "add_fare".equals(type) || "multiply_fare".equals(type)
                || "cap_fare".equals(type) || "floor_fare".equals(type);
    }

    private String blockLabel(ScriptNode node) {
        return Component.translatable("gui.trdm.script.block." + node.type).getString();
    }

    private String valueLabel(ScriptNode node) {
        if ("table_intervals".equals(node.type) || "table_meters".equals(node.type)) {
            return Component.translatable("gui.trdm.script.block." + node.type).getString()
                    + " (" + node.tiers.size() + ")";
        }
        return Component.translatable("gui.trdm.script.block." + node.type).getString();
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

    private void rebuild() {
        this.clearWidgets();
        this.init(this.minecraft, this.width, this.height);
    }
}
