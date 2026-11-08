package top.xiaotang27.trdm.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.xiaotang27.trdm.ticket.TicketTooltips;

import java.util.List;

/** 单程票：票面额、状态、入闸站都写在 NBT 里。 */
public class ItemSingleTicket extends Item {

    public ItemSingleTicket() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        TicketTooltips.single(stack, tooltip);
    }
}
