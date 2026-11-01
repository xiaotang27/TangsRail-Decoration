package top.xiaotang27.trdm.block;

import mtr.block.BlockPSDAPGGlassEndBase;
import mtr.block.BlockPSDAPGGlassEndBase.EnumPSDAPGGlassEndSide;
import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

/**
 * TRD 屏蔽门端头玻璃：直角造型（转角条 + 垂直端板），碰撞箱与模型完全一致。
 */
public class BlockTrdmPSDGlassEnd extends BlockPSDAPGGlassEndBase {

    /** 普通玻璃面（与门排同一条线：x 2–4）。 */
    private static final double[] PANE = {2, 0, 0, 4, 16, 16};
    /** 拐向左端的垂直挡板（z 14–16）。 */
    private static final double[] RETURN_LEFT = {4, 0, 14, 16, 16, 16};
    /** 拐向右端的垂直挡板（z 0–2）。 */
    private static final double[] RETURN_RIGHT = {4, 0, 0, 16, 16, 2};

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
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        final Direction facing = IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING);
        return Shapes.or(cornerBox(PANE, facing), cornerBox(turnsRight(state) ? RETURN_RIGHT : RETURN_LEFT, facing));
    }

    /** 有门的一侧不拐弯，拐向另一侧（与 blockstate 的选型规则一致）。 */
    public static boolean turnsRight(BlockState state) {
        return IBlock.getStatePropertySafe(state, TOUCHING_LEFT) == EnumPSDAPGGlassEndSide.DOOR
                || IBlock.getStatePropertySafe(state, TOUCHING_RIGHT) == EnumPSDAPGGlassEndSide.AIR;
    }

    @Override
    public Item asItem() {
        return Trdm.PSD_GLASS_END_ITEM_1;
    }

    /**
     * 模型坐标（面向西）下的一个方块，按 FACING 绕方块中心旋转后返回碰撞盒。
     * 与 blockstate 的 y 旋转一致：west 0°、north 90°、east 180°、south 270°。
     */
    public static VoxelShape cornerBox(double[] box, Direction facing) {
        final double[][] corners = {
                {box[0], box[2]}, {box[3], box[2]}, {box[3], box[5]}, {box[0], box[5]}
        };
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (final double[] corner : corners) {
            final double x;
            final double z;
            switch (facing) {
                case NORTH -> {
                    x = 16 - corner[1];
                    z = corner[0];
                }
                case EAST -> {
                    x = 16 - corner[0];
                    z = 16 - corner[1];
                }
                case SOUTH -> {
                    x = corner[1];
                    z = 16 - corner[0];
                }
                default -> {
                    x = corner[0];
                    z = corner[1];
                }
            }
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        return Block.box(minX, box[1], minZ, maxX, box[4], maxZ);
    }
}
