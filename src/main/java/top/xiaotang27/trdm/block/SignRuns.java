package top.xiaotang27.trdm.block;

import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 合并牌的「段」划分：连续、同一朝向、<b>同一种方块</b>的一串牌子算一段。
 * <p>
 */
public final class SignRuns {

    /** 一段最多找多少格（防止数据异常时死循环）。 */
    private static final int MAX_LENGTH = 256;

    private SignRuns() {
    }

    /** 这一格是否属于同一段：同一种方块 + 同一朝向。 */
    public static boolean isMember(BlockGetter world, BlockPos pos, Block block, DirectionProperty property,
                                   Direction facing) {
        final BlockState state = world.getBlockState(pos);
        return state.getBlock() == block && IBlock.getStatePropertySafe(state, property) == facing;
    }

    /**
     * 含 {@code pos} 自身的一整段，顺序从最左（facing 的逆时针侧）到最右。
     */
    public static List<BlockPos> collect(BlockGetter world, BlockPos pos, Block block, DirectionProperty property,
                                         Direction facing) {
        final Direction across = facing.getCounterClockWise();
        final List<BlockPos> left = new ArrayList<>();
        BlockPos current = pos;
        while (left.size() < MAX_LENGTH) {
            current = current.relative(across.getOpposite());
            if (!isMember(world, current, block, property, facing)) {
                break;
            }
            left.add(0, current.immutable());
        }

        final List<BlockPos> result = new ArrayList<>(left);
        result.add(pos.immutable());
        current = pos;
        while (result.size() < MAX_LENGTH) {
            current = current.relative(across);
            if (!isMember(world, current, block, property, facing)) {
                break;
            }
            result.add(current.immutable());
        }
        return result;
    }

    /** 这一格在段里排第几个（0 = 段首，也就是唯一负责绘制的那一格）。 */
    public static int indexOf(List<BlockPos> run, BlockPos pos) {
        for (int index = 0; index < run.size(); index++) {
            final BlockPos member = run.get(index);
            if (member.getX() == pos.getX() && member.getY() == pos.getY() && member.getZ() == pos.getZ()) {
                return index;
            }
        }
        return -1;
    }
}
