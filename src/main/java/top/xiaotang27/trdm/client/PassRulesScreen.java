package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.ticket.PassRule;
import top.xiaotang27.trdm.ticket.PassRules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通票模板管理：标题行添加，行内删除，名称/售价/天数直接编辑。
 * 保存后写回服务端权威配置并广播。
 */
public final class PassRulesScreen extends Screen {

    private final Screen parent;
    private final List<PassRule> rules = new ArrayList<>();
    private String error = "";
    private int listTop;

    public PassRulesScreen(Screen parent) {
        super(Component.translatable("gui.trdm.pass.title"));
        this.parent = parent;
        for (final PassRule rule : FareRuntime.get().config().passRules) {
            rules.add(rule.copy());
        }
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

        final int shown = Math.min(rules.size(), 8);
        for (int index = 0; index < shown; index++) {
            final int row = index;
            final int y = listTop + index * 28;
            final PassRule rule = rules.get(index);
            final EditBox name = new EditBox(this.font, 20, y, 160, 20,
                    Component.translatable("gui.trdm.pass.name"));
            name.setValue(rule.name == null ? "" : rule.name);
            name.setResponder(text -> rules.get(row).name = text);
            addRenderableWidget(name);

            final EditBox price = new EditBox(this.font, 186, y, 56, 20,
                    Component.translatable("gui.trdm.pass.price"));
            price.setValue(Integer.toString(rule.priceEmeralds));
            price.setResponder(text -> {
                try {
                    rules.get(row).priceEmeralds = Integer.parseInt(text.trim());
                } catch (NumberFormatException ignored) {
                    // 让玩家把数字打完
                }
            });
            addRenderableWidget(price);

            final EditBox days = new EditBox(this.font, 248, y, 44, 20,
                    Component.translatable("gui.trdm.pass.days"));
            days.setValue(Integer.toString(rule.days));
            days.setResponder(text -> {
                try {
                    rules.get(row).days = Integer.parseInt(text.trim());
                } catch (NumberFormatException ignored) {
                    // 让玩家把数字打完
                }
            });
            addRenderableWidget(days);

            addRenderableWidget(Button.builder(Component.literal("×"), button -> {
                        rules.remove(row);
                        error = "";
                        rebuild();
                    })
                    .bounds(300, y, 20, 20).build());
        }
        if (rules.size() > 8) {
            addRenderableWidget(Button.builder(Component.literal("…"), button -> {
            }).bounds(20, listTop + 8 * 28, 300, 16).build()).active = false;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.editor.save"),
                        button -> save())
                .bounds(this.width / 2 - 106, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.trdm.ticket.back"),
                        button -> onClose())
                .bounds(this.width / 2 + 6, this.height - 28, 100, 20).build());
    }

    private void save() {
        if (rules.isEmpty()) {
            error = Component.translatable("gui.trdm.pass.empty").getString();
            rebuild();
            return;
        }
        final Set<String> ids = new HashSet<>();
        for (final PassRule rule : rules) {
            if (rule.name == null || rule.name.isBlank()) {
                error = Component.translatable("gui.trdm.pass.name_required").getString();
                rebuild();
                return;
            }
            if (rule.priceEmeralds <= 0) {
                error = Component.translatable("gui.trdm.pass.price_invalid").getString();
                rebuild();
                return;
            }
            if (rule.days < 1 || rule.days > 30) {
                error = Component.translatable("gui.trdm.pass.days_invalid").getString();
                rebuild();
                return;
            }
            if (!ids.add(rule.id)) {
                error = Component.translatable("gui.trdm.pass.duplicate").getString();
                rebuild();
                return;
            }
        }
        FareRuntime.get().config().passRules.clear();
        for (final PassRule rule : rules) {
            FareRuntime.get().config().passRules.add(rule.copy());
        }
        FareRuntime.get().save();
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.pass.title"), 20, 18, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.pass.name"), 20, listTop - 12, 0xA0A0A0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.pass.price"), 186, listTop - 12, 0xA0A0A0, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.trdm.pass.days"), 248, listTop - 12, 0xA0A0A0, false);
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
