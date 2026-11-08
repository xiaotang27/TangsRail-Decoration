package top.xiaotang27.trdm.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** 通票（可变车票）：通票系统尚未接入，先只显示提示。 */
public class ItemVariableTicket extends Item {

    public ItemVariableTicket() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.trdm.ticket.tooltip.todo"));
    }
}
