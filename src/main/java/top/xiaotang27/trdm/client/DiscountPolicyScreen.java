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

    @Override
    protected void init() {
        final int nameX = 30;
        final int idX = 250;
        for (int index = 0; index < policies.size(); index++) {
            final int row = index;
            final DiscountPolicy policy = policies.get(index);
            final int y = 58 + index * 26;
            final EditBox name = new EditBox(this.font, nameX, y, 210, 18,
                    Component.translatable("gui.trdm.policies.name"));
            name.setMaxLength(40);
            name.setValue(policy.name());
            name.setResponder(value -> policies.set(row, new DiscountPolicy(
                    policies.get(row).id(), value == null ? "" : value.trim())));
            addRenderableWidget(name);
            final EditBox id = new EditBox(this.font, idX, y, 180, 18,
                    Component.translatable("gui.trdm.policies.id"));
            id.setMaxLength(40);
            id.setValue(policy.id());
            addRenderableWidget(id);
            id.setResponder(value -> policies.set(row, new DiscountPolicy(
                    value == null ? "" : value.trim(), policies.get(row).name())));
            addRenderableWidget(new TrdmEditorButton(450, y, 66, 18,
                    Component.translatable("gui.trdm.policies.delete"), button -> {
                        policies.remove(row);
                        error = "";
                        rebuild();
                    }));
        }
        addRenderableWidget(new TrdmEditorButton(30, this.height - 56, 110, 20,
                Component.translatable("gui.trdm.policies.add"), button -> {
                    if (policies.size() >= 10) {
                        error = Component.translatable("gui.trdm.policies.limit").getString();
                        return;
                    }
                    policies.add(new DiscountPolicy("policy_" + (policies.size() + 1),
                            Component.translatable("gui.trdm.policies.new").getString()));
                    rebuild();
                }));
        addRenderableWidget(new TrdmEditorButton(this.width - 220, this.height - 56, 90, 20,
                Component.translatable("gui.trdm.ticket.editor.save"), button -> save()));
        addRenderableWidget(new TrdmEditorButton(this.width - 120, this.height - 56, 90, 20,
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
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.trdm.policies.name"),
                30, 46, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.translatable("gui.trdm.policies.id"),
                250, 46, 0xA0A0A0, false);
        graphics.drawString(this.font, Component.translatable("gui.trdm.policies.hint"),
                30, this.height - 78, 0x9A9A9A, false);
        if (!error.isEmpty()) {
            graphics.drawString(this.font, error, 30, this.height - 68, 0xFFFF8080, false);
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
