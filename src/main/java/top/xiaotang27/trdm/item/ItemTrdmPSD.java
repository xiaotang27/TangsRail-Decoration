package top.xiaotang27.trdm.item;

import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import top.xiaotang27.trdm.Trdm;

/**
 * 屏蔽门放置物品，与 MTR 的 ItemPSDAPGBase.useOn 一致：
 * 门扇 2 格宽、玻璃/端头玻璃 1 格宽，都是 2 格高，再在最上面放一格门楣。
 * 列宽沿玩家朝向的顺时针方向铺开；玻璃的横向拼接交给 mtr:brush（MTR 的 connectGlass）。
 */
public class ItemTrdmPSD extends Item {

    public enum Kind {
        DOOR, GLASS, GLASS_END
    }

    private final Kind kind;

    public ItemTrdmPSD(Kind kind) {
        super(new Item.Properties());
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        final Level world = context.getLevel();
        final Direction facing = context.getHorizontalDirection();
        final BlockPos origin = context.getClickedPos().relative(context.getClickedFace());
        final int width = kind == Kind.DOOR ? 2 : 1;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < 3; y++) {
                if (!world.getBlockState(origin.relative(facing.getClockWise(), x).above(y)).canBeReplaced()) {
                    return InteractionResult.FAIL;
                }
            }
        }

        if (!world.isClientSide) {
            for (int x = 0; x < width; x++) {
                final BlockPos pos = origin.relative(facing.getClockWise(), x);
                for (int y = 0; y < 2; y++) {
                    world.setBlockAndUpdate(pos.above(y), getPlacedState(x, y, facing));
                }
                world.setBlockAndUpdate(pos.above(2), Trdm.PSD_TOP.defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, facing)
                        .setValue(IBlock.SIDE_EXTENDED, kind == Kind.DOOR
                                ? (x == 0 ? IBlock.EnumSide.LEFT : IBlock.EnumSide.RIGHT)
                                : IBlock.EnumSide.SINGLE));
            }

            context.getItemInHand().shrink(1);
        }

        return InteractionResult.SUCCESS;
    }

    private BlockState getPlacedState(int x, int y, Direction facing) {
        final BlockState state;
        switch (kind) {
            case DOOR:
                state = Trdm.PSD_DOOR_1.defaultBlockState()
                        .setValue(IBlock.SIDE, x == 0 ? IBlock.EnumSide.LEFT : IBlock.EnumSide.RIGHT);
                break;
            case GLASS:
                state = Trdm.PSD_GLASS_1.defaultBlockState()
                        .setValue(IBlock.SIDE_EXTENDED, IBlock.EnumSide.SINGLE);
                break;
            default:
                state = Trdm.PSD_GLASS_END_1.defaultBlockState()
                        .setValue(IBlock.SIDE_EXTENDED, IBlock.EnumSide.SINGLE);
                break;
        }
        return state.setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(IBlock.HALF, y == 1 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }
}
