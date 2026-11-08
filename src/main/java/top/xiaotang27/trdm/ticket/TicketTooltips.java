package top.xiaotang27.trdm.ticket;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import top.xiaotang27.trdm.fare.FareMoney;

import java.util.List;

/** 车票 / 交通卡的悬浮提示。 */
public final class TicketTooltips {

    private TicketTooltips() {
    }

    public static void single(ItemStack stack, List<Component> tooltip) {
        if (!TicketData.hasData(stack)) {
            tooltip.add(Component.translatable("item.trdm.ticket.tooltip.invalid"));
            return;
        }
        tooltip.add(Component.translatable("item.trdm.ticket.tooltip.face",
                FareMoney.yuan(TicketData.faceValue(stack))));
        tooltip.add(stateLine(stack));
        if (TicketData.hasEntry(stack)) {
            tooltip.add(Component.translatable("item.trdm.ticket.tooltip.entry",
                    TicketData.entryName(stack)));
        }
        if (!TicketData.soldName(stack).isEmpty()) {
            tooltip.add(Component.translatable("item.trdm.ticket.tooltip.sold",
                    TicketData.soldName(stack)));
        }
    }

    public static void card(ItemStack stack, List<Component> tooltip) {
        if (!TicketData.hasData(stack)) {
            tooltip.add(Component.translatable("item.trdm.ticket.tooltip.invalid_card"));
            return;
        }
        tooltip.add(Component.translatable("item.trdm.ticket.tooltip.balance",
                FareMoney.yuan(TicketData.balance(stack))));
        tooltip.add(stateLine(stack));
        if (TicketData.hasEntry(stack)) {
            tooltip.add(Component.translatable("item.trdm.ticket.tooltip.entry",
                    TicketData.entryName(stack)));
        }
        final int txCount = TicketData.txCount(stack);
        if (txCount > 0) {
            tooltip.add(Component.translatable("item.trdm.ticket.tooltip.tx", txCount));
        }
    }

    private static Component stateLine(ItemStack stack) {
        final String key = switch (TicketData.state(stack)) {
            case EMPTY -> "item.trdm.ticket.state.empty";
            case ENTERED -> "item.trdm.ticket.state.entered";
            case EXITED -> "item.trdm.ticket.state.exited";
        };
        return Component.translatable("item.trdm.ticket.tooltip.state",
                Component.translatable(key));
    }
}
