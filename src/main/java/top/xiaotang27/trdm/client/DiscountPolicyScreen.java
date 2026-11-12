package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.fare.DiscountPolicy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 通用设置里的交通卡优惠政策定义：只有 ID 和名称。 */
public final class DiscountPolicyScreen extends Screen {

    private static final int PANEL_W = 700;
    private static final int PANEL_TOP = 24;
    private static final int ROW_H = 28;

    private final Screen parent;
    private final List<DiscountPolicy> policies = new ArrayList<>();
    private String error = "";

    public DiscountPolicyScreen(Screen parent) {
        super(Component.translatable("gui.trdm.policies.title"));
        this.parent = parent;
        for (final DiscountPolicy policy : FareRuntime.get().config().discountPolicies) {
            policies.add(policy.copy());
        }
    }

    private int panelX() {
        return Math.max(12, (this.width - PANEL_W) / 2);
    }

    private int panelH() {
        return Math.max(260, this.height - PANEL_TOP - 20);
    }

    @Override
    protected void init() {
        final int x = panelX();
        final int nameX = x + 20;
        final int idX = x + 290;
        final int removeX = x + PANEL_W - 88;
        for (int index = 0; index < policies.size() && index < 9; index++) {
            final int row = index;
            final DiscountPolicy policy = policies.get(index);
            final int y = PANEL_TOP + 70 + index * ROW_H;
            final EditBox name = new EditBox(this.font, nameX, y, 250, 20,
                    Component.translatable("gui.trdm.policies.name"));
            name.setMaxLength(40);
            name.setValue(policy.name());
            name.setTextColor(0xFFE8E8E8);
            name.setResponder(value -> policies.set(row, new DiscountPolicy(
                    policies.get(row).id(), value == null ? "" : value.trim())));
            addRenderableWidget(name);
            final EditBox id = new EditBox(this.font, idX, y, 220, 20,
                    Component.translatable("gui.trdm.policies.id"));
            id.setMaxLength(40);
            id.setValue(policy.id());
            id.setTextColor(0xFFE8E8E8);
            id.setResponder(value -> policies.set(row, new DiscountPolicy(
                    value == null ? "" : value.trim(), policies.get(row).name())));
            addRenderableWidget(id);
            addRenderableWidget(new TrdmEditorButton(removeX, y, 68, 20,
                    Component.translatable("gui.trdm.policies.delete"), button -> {
                        policies.remove(row);
                        error = "";
                        rebuild();
                    }));
        }
        final int footerY = PANEL_TOP + panelH() - 36;
        addRenderableWidget(new TrdmEditorButton(x + 20, footerY, 110, 20,
                Component.translatable("gui.trdm.policies.add"), button -> {
                    if (policies.size() >= 9) {
                        error = Component.translatable("gui.trdm.policies.limit").getString();
                        return;
                    }
                    policies.add(new DiscountPolicy("policy_" + (policies.size() + 1),
                            Component.translatable("gui.trdm.policies.new").getString()));
                    rebuild();
                }));
        addRenderableWidget(new TrdmEditorButton(x + PANEL_W - 210, footerY, 90, 20,
                Component.translatable("gui.trdm.ticket.editor.save"), button -> save()));
        addRenderableWidget(new TrdmEditorButton(x + PANEL_W - 110, footerY, 90, 20,
                Component.translatable("gui.trdm.ticket.back"), button -> onClose()));
    }

    private void rebuild() {
        this.clearWidgets();
        this.init(this.minecraft, this.width, this.height);
    }

    private void save() {
        final Set<String> ids = new HashSet<>();
        for (final DiscountPolicy policy : policies) {
            if (policy.id().isBlank()) {
                error = Component.translatable("gui.trdm.policies.id_required").getString();
                return;
            }
            if (!ids.add(policy.id())) {
                error = Component.translatable("gui.trdm.policies.duplicate").getString();
                return;
            }
            if (policy.name().isBlank()) {
                error = Component.translatable("gui.trdm.policies.name_required").getString();
                return;
            }
        }
        final var config = FareRuntime.get().config();
        config.discountPolicies.clear();
        for (final DiscountPolicy policy : policies) {
            config.discountPolicies.add(policy.copy());
        }
        FareRuntime.get().save();
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        final int x = panelX();
        final int h = panelH();
        graphics.fill(x - 1, PANEL_TOP - 1, x + PANEL_W + 1, PANEL_TOP + h + 1, 0xFF9A9A9A);
        graphics.fill(x, PANEL_TOP, PANEL_W, h, 0xF0161616);
        graphics.fill(x, PANEL_TOP, PANEL_W, 34, 0xFF5B4B8A);
        graphics.drawString(this.font, this.title, x + 20, PANEL_TOP + 12, 0xFFFFFF, false);
        graphics.drawString(this.font, Component.translatable("gui.trdm.policies.name"),
                x + 20, PANEL_TOP + 54, 0xFFA0A0A0, false);
        graphics.drawString(this.font, Component.translatable("gui.trdm.policies.id"),
                x + 290, PANEL_TOP + 54, 0xFFA0A0A0, false);
        for (int index = 0; index < policies.size() && index < 9; index++) {
            final int y = PANEL_TOP + 70 + index * ROW_H;
            graphics.fill(x + 12, y - 3, x + PANEL_W - 12, y + 23, 0x33FFFFFF);
            graphics.fill(x + 12, y - 3, x + 15, y + 23, 0xFF0A7A1E);
        }
        graphics.drawString(this.font, Component.translatable("gui.trdm.policies.hint"),
                x + 20, PANEL_TOP + h - 58, 0xFFA0A0A0, false);
        if (!error.isEmpty()) {
            graphics.drawString(this.font, error, x + 20, PANEL_TOP + h - 46, 0xFFFF8080, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
