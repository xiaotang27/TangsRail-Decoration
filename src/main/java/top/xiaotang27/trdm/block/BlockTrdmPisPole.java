package top.xiaotang27.trdm.block;

import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

/**
 * 显示屏柱：单独一格的立杆（模型 {@code rv_pids_pole_extended.json}，0~24 像素、1.5 格高），
 * 用来把上面的 PIS 屏幕架高。和 JCM 的 {@code rv_pids_pole} 一样，纯模型方块、没有方块实体。
 */
public class BlockTrdmPisPole extends Block {

    public BlockTrdmPisPole() {
        super(Properties.of().strength(1F).noOcclusion());
    }

    /** 碰撞箱：中间那根 1/16 见方的杆子。 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return IBlock.getVoxelShapeByDirection(7, 0, 8, 9, 16, 10, net.minecraft.core.Direction.NORTH);
    }

    @Override
    public Item asItem() {
        return Trdm.PIS_POLE_ITEM;
    }
}
