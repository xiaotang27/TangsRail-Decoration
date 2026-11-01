package top.xiaotang27.trdm.block;

import mtr.block.BlockPSDAPGGlassBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import top.xiaotang27.trdm.Trdm;

/**
 * TRD 屏蔽门玻璃，与 MTR 的 BlockPSDGlass 完全一致。
 * 横向拼接（SIDE_EXTENDED 的 left/right/middle/single）完全由 MTR 的
 */
public class BlockTrdmPSDGlass extends BlockPSDAPGGlassBase {

    public BlockTrdmPSDGlass() {
        super();
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hitResult) {
        final InteractionResult appearance = BlockTrdmPSDTop.openAppearanceOnShiftBrush(world, pos, player);
        if (appearance != null) {
            return appearance;
        }
        return super.use(state, world, pos, player, hand, hitResult);
    }

    @Override
    public Item asItem() {
        return Trdm.PSD_GLASS_ITEM_1;
    }
}
