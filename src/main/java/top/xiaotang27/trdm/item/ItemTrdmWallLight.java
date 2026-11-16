package top.xiaotang27.trdm.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import top.xiaotang27.trdm.block.BlockTrdmPSDExtension;
import top.xiaotang27.trdm.block.BlockTrdmPSDTop;
import top.xiaotang27.trdm.block.PsdLightHolder;
import top.xiaotang27.trdm.block.PsdLightKind;

import java.util.List;

/**
 * 墙上的荧光灯管（模型 1）的方块物品。
 * <p>
 * 单独放置时走原版方块放置；点在 TRD 门楣 / 延伸板上时改为「贴装」：
 * 把灯记到那一格的方块实体里，不再放下独立方块 —— 这样门楣上那盏灯
 * 和门楣共用同一个格子与碰撞箱。
 * <p>
 * 贴装位置由渲染器按门楣的朝向画：模型 1 的坐标本来就在门楣朝站台那一侧（x = 6.14~7），
 * 底座贴着吊顶（y = 16），和门楣的 6 像素板面贴合。
 */
public class ItemTrdmWallLight extends BlockItem {

    public ItemTrdmWallLight(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final Block block = level.getBlockState(pos).getBlock();
        if ((block instanceof BlockTrdmPSDTop || block instanceof BlockTrdmPSDExtension)
                && level.getBlockEntity(pos) instanceof PsdLightHolder holder) {
            // 反过来：装墙管时把原来的顶部灯拆下来掉回世界（两种灯现在都在站台侧，不能叠着）
            final PsdLightKind oldTop = holder.topLight();
            if (oldTop != PsdLightKind.NONE) {
                holder.setTopLight(PsdLightKind.NONE, holder.topLightColor());
                if (!level.isClientSide) {
                    Block.popResource(level, pos, new ItemStack(oldTop == PsdLightKind.COLOR
                            ? top.xiaotang27.trdm.Trdm.PSD_TOP_LIGHT_COLOR_ITEM
                            : top.xiaotang27.trdm.Trdm.PSD_TOP_LIGHT_ITEM));
                }
            }
            final boolean changed = !holder.hasTubeLight();
            holder.setTubeLight(true);
            if (!level.isClientSide && changed) {
                final Player player = context.getPlayer();
                if (player == null || !player.getAbilities().instabuild) {
                    context.getItemInHand().shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.trdm.wall_light.mount")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.trdm.wall_light.header")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.trdm.psd_light.remove")
                .withStyle(ChatFormatting.GRAY));
    }
}
