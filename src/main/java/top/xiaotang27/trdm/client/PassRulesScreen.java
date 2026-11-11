package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.fare.script.PassScriptCompiler;
import top.xiaotang27.trdm.fare.script.PassSettings;
import top.xiaotang27.trdm.ticket.PassRule;
import top.xiaotang27.trdm.ticket.PassRules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通票模板管理：名称、积木设置入口和适用票务规则范围。
 * 售价/有效期/折扣/每日次数由通票积木决定；范围不属于积木。
 */
public final class PassRulesScreen extends Screen {

    private static final int ROW_H = 44;
    private static final int VISIBLE = 8;

    private final Screen parent;
    private final List<PassRule> rules;
    private String error = "";
    private int listTop;

    public PassRulesScreen(Screen parent) {
        super(Component.translatable("gui.trdm.pass.title"));
        this.parent = parent;
        this.rules = FareRuntime.get().config().passRules;
    }

    @Override
    protected void init() {
        this.listTop = 62;
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.pass.add_rule"), button -> {
                    if (rules.size() >= 8) {
                        error = Component.translatable("gui.trdm.pass.limit").getString();
                        rebuild();
                        return;
                    }
                    rules.add(new PassRule(PassRules.uniqueId(rules, "pass_"),
                            Component.translatable("gui.trdm.pass.new_rule").getString(), 10, 1));
                    error = "";
                    rebuild();
                })
                .bounds(this.width - 130, 14, 110, 20).build());

        final int nameWidth = Math.max(80, Math.min(150, this.width - 210));
        final int blockX = 20 + nameWidth + 6;
        final int scopeX = blockX + 76;
        final int deleteX = scopeX + 76;
        final int shown = Math.min(rules.size(), VISIBLE);
        for (int index = 0; index < shown; index++) {
            final int row = index;
            final int y = listTop + index * ROW_H;
            final PassRule rule = rules.get(index);
            rule.ensureScript();

            final EditBox name = new EditBox(this.font, 20, y, nameWidth, 18,
                    Component.translatable("gui.trdm.pass.name"));
            name.setValue(rule.name == null ? "" : rule.name);
            name.setResponder(text -> rules.get(row).name = text);
            addRenderableWidget(name);

            addRenderableWidget(Button.builder(Component.translatable("gui.trdm.pass.edit_blocks"),
                            button -> openBlocks(rule))
                    .bounds(blockX, y, 70, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.trdm.pass.edit_scope"),
                            button -> openScope(rule))
                    .bounds(scopeX, y, 70, 18).build());
            addRenderableWidget(Button.builder(Component.literal("×"), button -> {
                        rules.remove(row);
                        error = "";
                        rebuild();
                    })
                    .bounds(deleteX, y, 20, 18).build());
        }
        if (rules.size() > VISIBLE) {
            addRenderableWidget(Button.builder(Component.literal("…"), button -> {
            }).bounds(20, listTop + VISIBLE * ROW_H, Math.min(300, this.width - 40), 16).build()).active = false;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.editor.save"),
                        button -> save())
                .bounds(this.width / 2 - 106, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.back"),
                        button -> onClose())
                .bounds(this.width / 2 + 6, this.height - 28, 100, 20).build());
    }

    private void openBlocks(PassRule rule) {
        if (minecraft != null) {
            minecraft.setScreen(new FareScriptScreen(this, rule));
        }
    }

    private void openScope(PassRule rule) {
        if (minecraft != null) {
            minecraft.setScreen(new PassScopeScreen(this, rule));
        }
    }

    private void save() {
        if (rules.isEmpty()) {
            error = Component.translatable("gui.trdm.pass.empty").getString();
            rebuild();
            return;
        }
        final Set<String> ids = new HashSet<>();
        for (final PassRule rule : rules) {
            rule.ensureScript();
            if (rule.name == null || rule.name.isBlank()) {
                error = Component.translatable("gui.trdm.pass.name_required").getString();
                rebuild();
                return;
            }
            if (!ids.add(rule.id)) {
                error = Component.translatable("gui.trdm.pass.duplicate").getString();
                rebuild();
                return;
            }
            try {
                rule.ensureScript();
                final PassSettings settings = PassScriptCompiler.evaluate(rule.script);
                if (settings.priceEmeralds() <= 0) {
                    error = Component.translatable("gui.trdm.pass.price_invalid").getString();
                    rebuild();
                    return;
                }
                if (settings.priceEmeralds() > 9999) {
                    error = Component.translatable("gui.trdm.pass.price_invalid").getString();
                    rebuild();
                    return;
                }
                rule.applySettings(settings);
            } catch (RuntimeException exception) {
                error = Component.translatable("gui.trdm.pass.script_invalid",
                        rule.name, String.valueOf(exception.getMessage())).getString();
                rebuild();
                return;
            }
        }
        FareRuntime.get().save();
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.pass.title"), 20, 18, 0xFFFFFF, false);
        final int nameWidth = Math.max(80, Math.min(150, this.width - 210));
        final int blockX = 20 + nameWidth + 6;
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.pass.name"), 20, listTop - 12, 0xA0A0A0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.pass.summary"), blockX, listTop - 12, 0xA0A0A0, false);
        final int shown = Math.min(rules.size(), VISIBLE);
        for (int index = 0; index < shown; index++) {
            final PassRule rule = rules.get(index);
            final int y = listTop + index * ROW_H;
            String summary;
            try {
                final PassSettings settings = rule.settings();
                summary = Component.translatable("gui.trdm.pass.summary_value",
                        settings.priceEmeralds(), settings.days(), settings.discountPercent(),
                        settings.dailyLimit() <= 0
                                ? Component.translatable("gui.trdm.pass.unlimited").getString()
                                : Integer.toString(settings.dailyLimit())).getString();
            } catch (RuntimeException exception) {
                summary = Component.translatable("gui.trdm.pass.script_invalid",
                        rule.name, String.valueOf(exception.getMessage())).getString();
            }
            guiGraphics.drawString(this.font, summary, 20, y + 24, 0xB0B0B0, false);
            guiGraphics.drawString(this.font, Component.translatable("gui.trdm.pass.scope_value",
                    rule.allRules ? Component.translatable("gui.trdm.pass.all_rules").getString()
                            : Integer.toString(rule.ruleIds.size())).getString(),
                    blockX, y + 24, 0xB0B0B0, false);
        }
        if (!error.isEmpty()) {
            guiGraphics.drawString(this.font, error, 20, this.height - 46, 0xFF5555, false);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
