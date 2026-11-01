package top.xiaotang27.trdm.block;

import mtr.block.BlockPSDAPGGlassEndBase;
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
 * TRD 屏蔽门端头玻璃（移植自 MTR 的 BlockPSDGlassEnd）。
 * TOUCHING_LEFT / TOUCHING_RIGHT 由 MTR 的 BlockPSDAPGGlassEndBase.updateShape 自动推断
 */
public class BlockTrdmPSDGlassEnd extends BlockPSDAPGGlassEndBase {

    public BlockTrdmPSDGlassEnd() {
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
        return Trdm.PSD_GLASS_END_ITEM_1;
    }
}
