package top.xiaotang27.trdm.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import top.xiaotang27.trdm.block.BlockTrdmPSDExtension;
import top.xiaotang27.trdm.block.BlockTrdmPSDTop;
import top.xiaotang27.trdm.block.PsdLightColors;
import top.xiaotang27.trdm.block.PsdLightHolder;
import top.xiaotang27.trdm.block.PsdLightKind;

import java.util.List;

/**
 * 站台门延伸板顶部灯 / 可染色的顶部灯（模型 2 / 3）。
 * 只能点在 TRD 门楣或延伸板上：直接把灯装到那格方块实体里，不占新方块，
 * 也就天然和门楣 / 延伸板共用占地面积和碰撞箱。
 */
public class ItemTrdmPSDLight extends Item {

    public enum Kind {
        /** 模型 2：金属色，不可染色。 */
        PLAIN,
        /** 模型 3：color 贴图是灯的底座，颜色由刷子右键界面设置（灯管保持白色）。 */
        COLOR
    }

    private final Kind kind;

    public ItemTrdmPSDLight(Kind kind) {
        super(new Item.Properties());
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        final Level level = context.getLevel();
        final BlockPos pos = context.getClickedPos();
        final Block block = level.getBlockState(pos).getBlock();
        if (!(block instanceof BlockTrdmPSDTop) && !(block instanceof BlockTrdmPSDExtension)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof PsdLightHolder holder)) {
            return InteractionResult.PASS;
        }

        // 门楣上两种灯会占同一侧，装顶部灯时把原来那盏墙管拆下来掉回世界
        if (holder.hasTubeLight()) {
            holder.setTubeLight(false);
            if (!level.isClientSide) {
                Block.popResource(level, pos, new ItemStack(top.xiaotang27.trdm.Trdm.WALL_LIGHT_ITEM));
            }
        }

        final PsdLightKind lightKind = kind == Kind.COLOR ? PsdLightKind.COLOR : PsdLightKind.PLAIN;
        final int color;
        final boolean manual;
        if (lightKind == PsdLightKind.COLOR) {
            if (holder.topLight() == PsdLightKind.COLOR && holder.isTopLightColorManual()) {
                // 已经手动定过色：重新装同一件物品不覆盖它
                color = holder.topLightColor();
                manual = true;
            } else {
                final int nearby = PsdLightColors.findNearbyColor(level, pos);
                // 附近没有别的灯时保留这格原来的颜色（它本来就是继承来的）
                color = nearby == PsdLightHolder.DEFAULT_COLOR && holder.topLight() == PsdLightKind.COLOR
                        ? holder.topLightColor() : nearby;
                manual = false;
            }
        } else {
            color = holder.topLightColor();
            manual = holder.isTopLightColorManual();
        }
        final boolean changed = holder.topLight() != lightKind
                || (lightKind == PsdLightKind.COLOR
                && (holder.topLightColor() != color || holder.isTopLightColorManual() != manual));
        holder.setTopLight(lightKind, color, manual);

        if (!level.isClientSide && changed) {
            final Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.trdm.psd_top_light.only_header")
                .withStyle(ChatFormatting.GRAY));
        if (kind == Kind.COLOR) {
            tooltip.add(Component.translatable("tooltip.trdm.psd_top_light.color_brush")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.trdm.psd_light.remove")
                .withStyle(ChatFormatting.GRAY));
    }
}
