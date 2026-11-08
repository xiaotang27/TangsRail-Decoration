package top.xiaotang27.trdm.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.xiaotang27.trdm.ticket.TicketTooltips;

import java.util.List;

/** 出站票：无票补票时由客服终端发放，只允许在指定车站出站一次。 */
public class ItemExitTicket extends Item {

    public ItemExitTicket() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        TicketTooltips.exitTicket(stack, tooltip);
    }
}
