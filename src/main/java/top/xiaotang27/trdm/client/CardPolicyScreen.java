package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import top.xiaotang27.trdm.fare.DiscountPolicy;
import top.xiaotang27.trdm.ticket.TicketData;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 客服终端：给主手交通卡勾选优惠政策。 */
public final class CardPolicyScreen extends Screen {

    private final Screen parent;
    private final BlockPos pos;
    private final Set<String> selected = new LinkedHashSet<>();

    public CardPolicyScreen(Screen parent, BlockPos pos, ItemStack card) {
        super(Component.translatable("gui.trdm.policies.card_title"));
        this.parent = parent;
        this.pos = pos;
        selected.addAll(TicketData.cardPolicies(card));
    }

    @Override
    protected void init() {
        final List<DiscountPolicy> policies = FareRuntime.get().config().discountPolicies;
        final int shown = Math.min(policies.size(), 10);
        for (int index = 0; index < shown; index++) {
            final DiscountPolicy policy = policies.get(index);
            final boolean on = selected.contains(policy.id());
            final Button button = new TrdmEditorButton(40, 54 + index * 24,
                    Math.max(180, this.width - 80), 20,
                    Component.literal((on ? "[×] " : "[ ] ") + policy.name()), b -> {
                        if (!selected.remove(policy.id())) {
                            selected.add(policy.id());
                        }
                        rebuild();
                    });
            addRenderableWidget(button);
        }
        addRenderableWidget(new TrdmEditorButton(40, this.height - 52, 100, 20,
                Component.translatable("gui.trdm.ticket.editor.save"), b -> save()));
        addRenderableWidget(new TrdmEditorButton(this.width - 140, this.height - 52, 100, 20,
                Component.translatable("gui.trdm.ticket.back"), b -> onClose()));
    }

    private void rebuild() {
        this.clearWidgets();
        this.init(this.minecraft, this.width, this.height);
    }

    private void save() {
        TicketClientNetwork.setCardPolicies(pos, selected);
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawString(this.font, Component.translatable("gui.trdm.policies.card_hint"),
                40, 36, 0xA0A0A0, false);
        if (FareRuntime.get().config().discountPolicies.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.trdm.policies.hint"),
                    40, 60, 0xFFFFC85A, false);
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
