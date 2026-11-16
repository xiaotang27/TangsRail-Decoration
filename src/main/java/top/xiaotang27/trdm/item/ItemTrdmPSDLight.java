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

        final PsdLightKind lightKind = kind == Kind.COLOR ? PsdLightKind.COLOR : PsdLightKind.PLAIN;
        // 换灯时保留已经调过的颜色：装可染色灯 → 沿用当前颜色；装普通灯 → 颜色留着但不显示
        final int color = holder.topLight() == PsdLightKind.COLOR
                ? holder.topLightColor() : PsdLightHolder.DEFAULT_COLOR;
        final boolean changed = holder.topLight() != lightKind
                || (lightKind == PsdLightKind.COLOR && holder.topLightColor() != color);
        holder.setTopLight(lightKind, color);

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
    }
}
