package top.xiaotang27.trdm.block;

import mtr.block.BlockPSDAPGDoorBase;
import mtr.mappings.BlockEntityMapper;
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
 * TRD 全高屏蔽门（门扇），与 MTR 的 BlockPSDDoor 完全一致（独立 BE）。
 * 默认状态保持 MTR 原样：UNLOCKED = false，需要先用 mtr:brush 点一下解锁，列车才会开门。
 */
public class BlockTrdmPSDDoor extends BlockPSDAPGDoorBase {

    public BlockTrdmPSDDoor() {
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
    public BlockEntityMapper createBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityTrdmPSDDoor(pos, state);
    }

    @Override
    public Item asItem() {
        return Trdm.PSD_DOOR_ITEM_1;
    }

    public static class TileEntityTrdmPSDDoor extends TileEntityPSDAPGDoorBase {

        public TileEntityTrdmPSDDoor(BlockPos pos, BlockState state) {
            super(Trdm.PSD_DOOR_1_BE, pos, state);
        }
    }
}
