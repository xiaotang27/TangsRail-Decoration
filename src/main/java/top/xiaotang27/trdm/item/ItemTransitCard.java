package top.xiaotang27.trdm.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.xiaotang27.trdm.ticket.TicketTooltips;

import java.util.List;

/** 交通卡：余额、押金、状态、交易记录。 */
public class ItemTransitCard extends Item {

    public ItemTransitCard() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        TicketTooltips.card(stack, tooltip);
    }
}
