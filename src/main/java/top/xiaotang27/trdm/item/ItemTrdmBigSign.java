package top.xiaotang27.trdm.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * 大站名牌的物品。站名后缀（STATION / ZHAN）和无障碍电梯牌不再是不同物品，
 * 而是在牌子自己的设置界面里用刷子右键切换，所以悬停提示只说这件事。
 */
public class ItemTrdmBigSign extends BlockItem {

    public ItemTrdmBigSign(Block block) {
        super(block, new Item.Properties());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.trdm.big_station_sign").withStyle(ChatFormatting.GRAY));
    }
}
