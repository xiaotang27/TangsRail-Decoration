package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.fare.FareRule;
import top.xiaotang27.trdm.ticket.PassRule;

import java.util.List;

/** 通票适用票务规则范围：全部规则或指定规则集合。 */
public final class PassScopeScreen extends Screen {

    private static final int MAX_RULES = 8;

    private final Screen parent;
    private final PassRule rule;
    private final List<FareRule> fareRules;

    public PassScopeScreen(Screen parent, PassRule rule) {
        super(Component.translatable("gui.trdm.pass.scope_title"));
        this.parent = parent;
        this.rule = rule;
        this.fareRules = FareRuntime.get().config().rules;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(allLabel(), button -> {
                    rule.allRules = !rule.allRules;
                    if (!rule.allRules && rule.ruleIds.isEmpty() && !fareRules.isEmpty()) {
                        rule.ruleIds.add(fareRules.get(0).id);
                    }
                    rebuild();
                })
                .bounds(20, 44, 220, 20).build());

        final int shown = Math.min(fareRules.size(), MAX_RULES);
        for (int index = 0; index < shown; index++) {
            final FareRule fare = fareRules.get(index);
            final boolean selected = rule.allRules || rule.ruleIds.contains(fare.id);
            final Button button = Button.builder(Component.literal(
                            (selected ? "[×] " : "[ ] ") + fare.name), ignored -> {
                        if (rule.allRules) {
                            return;
                        }
                        if (!rule.ruleIds.remove(fare.id)) {
                            rule.ruleIds.add(fare.id);
                        }
                        rebuild();
                    })
                    .bounds(20, 84 + index * 22, 300, 20).build();
            button.active = !rule.allRules;
            addRenderableWidget(button);
        }
        if (fareRules.size() > MAX_RULES) {
            addRenderableWidget(Button.builder(Component.literal("…"), button -> {
            }).bounds(20, 84 + MAX_RULES * 22, 300, 16).build()).active = false;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.back"),
                        button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private Component allLabel() {
        return Component.translatable("gui.trdm.pass.scope_all", rule.allRules
                ? Component.translatable("gui.trdm.pass.all_rules")
                : Component.translatable("gui.trdm.pass.specified_rules"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.pass.scope_title"),
                20, 18, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.pass.scope_hint"),
                20, 68, 0xA0A0A0, false);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        FareRuntime.get().save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void rebuild() {
        this.clearWidgets();
        this.init(this.minecraft, this.width, this.height);
    }
}
