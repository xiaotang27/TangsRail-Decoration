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

    public static void exitTicket(ItemStack stack, List<Component> tooltip) {
        if (!TicketData.isExitTicket(stack)) {
            tooltip.add(Component.translatable("item.trdm.ticket.tooltip.invalid"));
            return;
        }
        tooltip.add(Component.translatable("item.trdm.exit_ticket.tooltip.station",
                TicketData.exitName(stack)));
        tooltip.add(Component.translatable("item.trdm.exit_ticket.tooltip.expires",
                formatTime(TicketData.exitExpires(stack))));
        tooltip.add(Component.translatable("item.trdm.exit_ticket.tooltip.usage"));
    }

    public static void pass(ItemStack stack, List<Component> tooltip) {
        if (!TicketData.isPass(stack)) {
            tooltip.add(Component.translatable("item.trdm.pass.tooltip.invalid"));
            return;
        }
        if (!TicketData.passActive(stack)) {
            tooltip.add(Component.translatable("item.trdm.pass.tooltip.inactive"));
        } else {
            if (PassValidity.expired(TicketData.passUntil(stack), System.currentTimeMillis())) {
                tooltip.add(Component.translatable("item.trdm.pass.tooltip.expired"));
            } else {
                tooltip.add(Component.translatable("item.trdm.pass.tooltip.valid_until",
                        PassValidity.format(TicketData.passUntil(stack))));
            }
            final String owner = TicketData.passOwnerName(stack);
            if (!owner.isEmpty()) {
                tooltip.add(Component.translatable("item.trdm.pass.tooltip.owner", owner));
            }
            tooltip.add(stateLine(stack));
        }
        tooltip.add(Component.translatable("item.trdm.pass.tooltip.usage"));
    }

    private static String formatTime(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
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
