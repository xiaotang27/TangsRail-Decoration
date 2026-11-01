package top.xiaotang27.trdm.block;

import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

/**
 * 悬挂式站外站名牌：内容和墙挂的大站名牌完全一样（标志 + 本站所有线路 + 站名 + 出口字母，
 * 刷子右键开同一个设置界面），区别是<b>吊在天花板上</b>——
 */
public class BlockTrdmHungBigSign extends BlockTrdmBigStationSign {

    /** 牌子下沿到方块底面的距离（格）：牌子贴着方块底。 */
    public static final float PLATE_BOTTOM = 0F;
    /** 吊杆长度（格）：牌子上沿到方块顶面。 */
    public static final float ROD_LENGTH = 4F / 16F;

    public BlockTrdmHungBigSign() {
        super();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 碰撞箱：牌子那一层板（2/16 厚，和渲染的厚度一致），从上到下盖住牌子高度。 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return IBlock.getVoxelShapeByDirection(0, (int) (PLATE_BOTTOM * 16F), 7, 16,
                (int) ((PLATE_BOTTOM + PLATE_HEIGHT) * 16F), 9, IBlock.getStatePropertySafe(state, FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityTrdmHungBigSign(pos, state);
    }

    @Override
    public Item asItem() {
        return Trdm.HUNG_SIGN_ITEM;
    }

    /** 和墙挂版共用同一套数据（出口 / 站名后缀 / 无障碍电梯牌），只是方块实体类型不同。 */
    public static class TileEntityTrdmHungBigSign extends TileEntityTrdmBigStationSign {

        public TileEntityTrdmHungBigSign(BlockPos pos, BlockState state) {
            super(Trdm.HUNG_SIGN_BE, pos, state);
        }
    }
}
