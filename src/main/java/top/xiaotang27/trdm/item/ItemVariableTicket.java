package top.xiaotang27.trdm.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.xiaotang27.trdm.ticket.PassCatalog;
import top.xiaotang27.trdm.ticket.TicketData;
import top.xiaotang27.trdm.ticket.TicketTooltips;

import java.util.List;

/** 通票（可变车票）：一个物品，用 NBT 里的定义 ID 区分一日票/三日票/青春18。 */
public class ItemVariableTicket extends Item {

    public ItemVariableTicket() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        final PassCatalog.Definition definition = PassCatalog.byId(TicketData.passDefinition(stack));
        return definition == null ? super.getName(stack) : Component.translatable(definition.nameKey());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        TicketTooltips.pass(stack, tooltip);
    }
}
