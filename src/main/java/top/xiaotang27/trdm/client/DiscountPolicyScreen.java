package top.xiaotang27.trdm.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.fare.DiscountPolicy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 通用设置里的交通卡优惠政策定义：沿用票务规则详情页的暗色布局。 */
public final class DiscountPolicyScreen extends TrdmScreenBase {

    private static final String[] PAGES = {
            "gui.trdm.config.general", "gui.trdm.config.ticket", "gui.trdm.config.about"};
    private static final float BACK_W = 84F;
    private static final float BACK_H = 24F;
    private static final float ROW_TOP = 104F;
    private static final float ROW_H = 30F;

    private final List<DiscountPolicy> policies = new ArrayList<>();
    private final List<Object> nameFields = new ArrayList<>();
    private final List<Object> idFields = new ArrayList<>();
    private final Screen parent;
    private String error = "";

    public DiscountPolicyScreen(Screen parent) {
        super(parent, Component.translatable("gui.trdm.policies.title"));
        this.parent = parent;
        for (final DiscountPolicy policy : FareRuntime.get().config().discountPolicies) {
            policies.add(policy.copy());
            nameFields.add(new Object());
            idFields.add(new Object());
        }
    }

    @Override
    protected String[] pages() {
        return PAGES;
    }

    @Override
    protected int currentPage() {
        return 1;
    }

    private void drawSidebarIcons(GuiGraphics guiGraphics) {
        for (int index = 0; index < PAGES.length; index++) {
            final float centreX = SIDEBAR_W / 2F;
            final float centreY = 8F + index * (SIDEBAR_BTN_H + 2F) + SIDEBAR_BTN_H / 2F;
            final int color = index == 1 ? 0xFFFFFFFF : 0xFF606060;
            if (index == 0) {
                rect(guiGraphics, centreX - 5F, centreY - 5F, 10F, 10F, color);
                rect(guiGraphics, centreX - 2F, centreY - 8F, 4F, 3F, color);
                rect(guiGraphics, centreX - 2F, centreY + 5F, 4F, 3F, color);
                rect(guiGraphics, centreX - 8F, centreY - 2F, 3F, 4F, color);
                rect(guiGraphics, centreX + 5F, centreY - 2F, 3F, 4F, color);
                rect(guiGraphics, centreX - 2F, centreY - 2F, 4F, 4F, 0xFF000000);
            } else if (index == 1) {
                rect(guiGraphics, centreX - 8F, centreY - 5F, 16F, 10F, color);
                rect(guiGraphics, centreX - 3F, centreY - 2F, 6F, 4F, 0xFF000000);
                rect(guiGraphics, centreX - 1F, centreY - 2F, 2F, 4F, color);
            } else {
                rect(guiGraphics, centreX - 1F, centreY - 7F, 2F, 3F, color);
                rect(guiGraphics, centreX - 1F, centreY - 3F, 2F, 10F, color);
            }
        }
    }

    @Override
    protected void renderPage(GuiGraphics guiGraphics, float mx, float my) {
        drawSidebarIcons(guiGraphics);
        title(guiGraphics, this.title);
        final float backX = CONTENT_RIGHT - BACK_W;
        buttonText(guiGraphics, backX, TITLE_Y, BACK_W, BACK_H,
                Component.translatable("gui.trdm.ticket.back"), false,
                hit(mx, my, backX, TITLE_Y, BACK_W, BACK_H), FONT_ROW);

        text(guiGraphics, Component.translatable("gui.trdm.policies.name"),
                CONTENT_X, 78F, FONT_ROW, COLOR_SUB);
        text(guiGraphics, Component.translatable("gui.trdm.policies.id"),
                CONTENT_X + 280F, 78F, FONT_ROW, COLOR_SUB);

        for (int index = 0; index < policies.size() && index < 9; index++) {
            final float y = ROW_TOP + index * ROW_H;
            rect(guiGraphics, CONTENT_X - 8F, y - 3F, CONTENT_W + 16F, ROW_H - 2F, COLOR_ROW);
            final DiscountPolicy policy = policies.get(index);
            field(guiGraphics, nameFields.get(index), CONTENT_X, y, 260F, 22F, FONT_ROW, policy.name());
            field(guiGraphics, idFields.get(index), CONTENT_X + 280F, y, 220F, 22F, FONT_ROW, policy.id());
            final float removeX = CONTENT_RIGHT - 80F;
            buttonText(guiGraphics, removeX, y, 80F, 22F,
                    Component.translatable("gui.trdm.policies.delete"), false,
                    hit(mx, my, removeX, y, 80F, 22F), FONT_SUB);
        }

        final float footerY = 476F;
        buttonText(guiGraphics, CONTENT_X, footerY, 120F, 24F,
                Component.translatable("gui.trdm.policies.add"), false,
                hit(mx, my, CONTENT_X, footerY, 120F, 24F), FONT_ROW);
        buttonText(guiGraphics, CONTENT_RIGHT - 190F, footerY, 90F, 24F,
                Component.translatable("gui.trdm.ticket.editor.save"), false,
                hit(mx, my, CONTENT_RIGHT - 190F, footerY, 90F, 24F), FONT_ROW);
        buttonText(guiGraphics, CONTENT_RIGHT - 90F, footerY, 90F, 24F,
                Component.translatable("gui.trdm.ticket.back"), false,
                hit(mx, my, CONTENT_RIGHT - 90F, footerY, 90F, 24F), FONT_ROW);
        text(guiGraphics, Component.translatable("gui.trdm.policies.hint"),
                CONTENT_X, 430F, FONT_SUB, COLOR_SUB);
        if (!error.isEmpty()) {
            text(guiGraphics, Component.literal(error), CONTENT_X, 446F, FONT_SUB, 0xFFFF8080);
        }
    }

    @Override
    protected boolean clickPage(float mx, float my) {
        if (mx >= 0 && mx < SIDEBAR_W) {
            for (int index = 0; index < PAGES.length; index++) {
                final float top = 8F + index * (SIDEBAR_BTN_H + 2F);
                if (my >= top && my < top + SIDEBAR_BTN_H) {
                    if (parent instanceof TrdmConfigScreen config) {
                        config.showPage(index);
                        net.minecraft.client.Minecraft.getInstance().setScreen(config);
                    } else {
                        onClose();
                    }
                    return true;
                }
            }
        }
        final float backX = CONTENT_RIGHT - BACK_W;
        if (hit(mx, my, backX, TITLE_Y, BACK_W, BACK_H)) {
            onClose();
            return true;
        }
        for (int index = 0; index < policies.size() && index < 9; index++) {
            final float y = ROW_TOP + index * ROW_H;
            final int row = index;
            if (hit(mx, my, CONTENT_X, y, 260F, 22F)) {
                beginField(nameFields.get(index), CONTENT_X, y, 260F, 22F,
                        policies.get(index).name(), 40, FONT_ROW,
                        value -> policies.set(row, new DiscountPolicy(
                                policies.get(row).id(), value == null ? "" : value.trim())));
                return true;
            }
            if (hit(mx, my, CONTENT_X + 280F, y, 220F, 22F)) {
                beginField(idFields.get(index), CONTENT_X + 280F, y, 220F, 22F,
                        policies.get(index).id(), 40, FONT_ROW,
                        value -> policies.set(row, new DiscountPolicy(
                                value == null ? "" : value.trim(), policies.get(row).name())));
                return true;
            }
            final float removeX = CONTENT_RIGHT - 80F;
            if (hit(mx, my, removeX, y, 80F, 22F)) {
                policies.remove(index);
                nameFields.remove(index);
                idFields.remove(index);
                error = "";
                return true;
            }
        }
        final float footerY = 476F;
        if (hit(mx, my, CONTENT_X, footerY, 120F, 24F)) {
            if (policies.size() >= 9) {
                error = Component.translatable("gui.trdm.policies.limit").getString();
                return true;
            }
            policies.add(new DiscountPolicy("policy_" + (policies.size() + 1),
                    Component.translatable("gui.trdm.policies.new").getString()));
            nameFields.add(new Object());
            idFields.add(new Object());
            return true;
        }
        if (hit(mx, my, CONTENT_RIGHT - 190F, footerY, 90F, 24F)) {
            save();
            return true;
        }
        if (hit(mx, my, CONTENT_RIGHT - 90F, footerY, 90F, 24F)) {
            onClose();
            return true;
        }
        return false;
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
}
