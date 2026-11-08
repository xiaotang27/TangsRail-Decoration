package top.xiaotang27.trdm.block;

import mtr.block.BlockPSDAPGBase;
import mtr.block.BlockPSDAPGDoorBase;
import mtr.block.BlockPSDTop;
import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

/**
 * 高站台门延伸板：放在高站台门门楣的同一高度，把门楣继续往两边/拐角延伸。
 * <p>
 * 造型完全沿用现成的三种门楣模型（不新增美术资源）：
 * <ul>
 *     <li>{@link EnumShape#STRAIGHT}：直线段，用普通高站台门「无倒角」门楣
 *         （{@code psd_top_left} + {@code psd_top_right} 左右两半拼成整格）；</li>
 * </ul>
 * 直线段的竖向接缝跟下方门 / 合并玻璃 / 门楣自己走：一扇门、一组用刷子合并的玻璃内部无缝，
 * 只在一组玻璃的两端、玻璃和滑动门之间用带边线的半模型（和普通门楣的 MTR 原版规则一致）。
 * <ul>
 *     <li>{@link EnumShape#CORNER_LEFT} / {@link EnumShape#CORNER_RIGHT}：转角，用直角转弯的 L 形门楣
 *         （{@code psd_top_end_square_left/right}）；</li>
 *     <li>{@link EnumShape#END_WALL}：转过弯后的那一段，用端门门楣（{@code psd_end_gate_top}，
 *         4px 厚、和端门齐平），并沿着这一段继续往两边传播。</li>
 * </ul>
 * 放置规则照 MC 原版楼梯：{@code facing} 取玩家朝向（门楣正面朝外），形状由四邻自动推：
 * <ul>
 *     <li>本格内侧（{@code facing} 的反方向）接了一块朝向另一条边的延伸板，并且左右至少有一块同朝向的直线段
 *         → 转角；L 形往左还是往右由内侧那块板的朝向决定；</li>
 *     <li>左右紧贴着一块「内侧带同朝向直线段」的转角板，或者紧贴着一块已经判成 {@link EnumShape#END_WALL}
 *         的同朝向延伸板 → {@link EnumShape#END_WALL}（前者是过弯第一格，后者把端门门楣继续传下去）；</li>
 *     <li>其余 → 直线段。</li>
 * </ul>
 * 这样一段延伸板在拐角处自动变成 L 形，拐弯后的整段自动变成端门门楣，和楼梯的 straight / corner 规则一个思路。
 */
public class BlockTrdmPSDExtension extends Block {

    /** 门楣正面朝向（朝外的一侧）。 */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 这一段用哪套门楣模型，见类注释。 */
    public static final EnumProperty<EnumShape> SHAPE = EnumProperty.create("shape", EnumShape.class);

    /**
     * 直线段的竖向接缝侧别，跟下方门 / 合并玻璃 / 门楣分组走（MTR 原版门楣同一套规则）：
     * {@code left}/{@code right} 只在一侧压边，{@code middle} 两侧无缝，{@code single} 两侧压边。
     */
    public static final EnumProperty<IBlock.EnumSide> SIDE = IBlock.SIDE_EXTENDED;

    /** 普通门楣 / 转角门楣按 6px 厚做选中框；端门门楣 4px。 */
    private static final int STRAIGHT_THICKNESS = 6;
    private static final int END_WALL_THICKNESS = 4;

    public BlockTrdmPSDExtension() {
        super(Properties.of().requiresCorrectToolForDrops().strength(2).noOcclusion());
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(SHAPE, EnumShape.STRAIGHT)
                .setValue(SIDE, IBlock.EnumSide.MIDDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHAPE, SIDE);
    }

    /** 和楼梯一样：朝向取玩家水平朝向，形状按放置时四周的延伸板自动推；接缝侧别看下方门/玻璃分组。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        state = withSide(state, context.getLevel(), context.getClickedPos());
        return withShape(state, context.getLevel(), context.getClickedPos());
    }

    /** 任意邻格变化都重推形状；接缝侧别跟着下方门/玻璃/门楣的分组一起更新。 */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return withShape(withSide(state, level, pos), level, pos);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        final int thickness = state.getValue(SHAPE) == EnumShape.END_WALL ? END_WALL_THICKNESS : STRAIGHT_THICKNESS;
        return IBlock.getVoxelShapeByDirection(0, 0, 0, 16, 16, thickness, state.getValue(FACING));
    }

    @Override
    public Item asItem() {
        return Trdm.PSD_EXTENSION_ITEM;
    }

    private static BlockState withShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(SHAPE, computeShape(level, pos, state.getValue(FACING)));
    }

    private static BlockState withSide(BlockState state, BlockGetter level, BlockPos pos) {
        return state.setValue(SIDE, computeSide(level, pos));
    }

    /**
     * 接缝侧别只看下方那格：
     * 门 → 门的 SIDE（一扇门 2 格，左右各一条外缘）；
     * 玻璃 / 端头玻璃 / 端门 → 玻璃的 SIDE_EXTENDED（一组用刷子合并的玻璃，左右/中间）；
     * 普通门楣 / 延伸板 → 它们自己的 side（延伸板叠在门楣上时可继承同一组接缝）；
     * 其它（空气、站台地面）→ middle，整格无缝。
     */
    private static IBlock.EnumSide computeSide(BlockGetter level, BlockPos pos) {
        final BlockState below = level.getBlockState(pos.below());
        final Block block = below.getBlock();
        if (block instanceof BlockPSDAPGDoorBase) {
            return IBlock.getStatePropertySafe(below, BlockPSDAPGDoorBase.SIDE);
        }
        if (block instanceof BlockPSDAPGBase) {
            return IBlock.getStatePropertySafe(below, IBlock.SIDE_EXTENDED);
        }
        if (block instanceof BlockTrdmPSDTop) {
            return IBlock.getStatePropertySafe(below, IBlock.SIDE_EXTENDED);
        }
        if (block instanceof BlockPSDTop) {
            return IBlock.getStatePropertySafe(below, IBlock.SIDE_EXTENDED);
        }
        if (block instanceof BlockTrdmPSDExtension) {
            return IBlock.getStatePropertySafe(below, SIDE);
        }
        return IBlock.EnumSide.MIDDLE;
    }

    /**
     * 只根据四邻的「朝向 + 位置」推形状，不依赖邻格自己的 shape 是否已经刷新
     * （放置顺序反过来时也能在同一 tick 内收敛）。
     */
    private static EnumShape computeShape(BlockGetter level, BlockPos pos, Direction facing) {
        final Direction inward = facing.getOpposite();
        final BlockState inwardState = level.getBlockState(pos.relative(inward));
        if (isPerpendicularExtension(inwardState, facing)) {
            // 内侧那块板朝向另一条边：如果左右还有同朝向的直线段，这一格就是转角。
            // L 形回折边往哪边，跟内侧那块板的朝向一致：
            // facing=west 时，内侧朝南 → psd_top_end_square_left（回折边在南）；朝北 → right。
            final boolean hasRun = isSameFacing(level, pos.relative(facing.getClockWise()), facing)
                    || isSameFacing(level, pos.relative(facing.getCounterClockWise()), facing);
            if (hasRun) {
                return inwardState.getValue(FACING) == facing.getCounterClockWise()
                        ? EnumShape.CORNER_LEFT
                        : EnumShape.CORNER_RIGHT;
            }
        }

        for (final Direction run : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
            final BlockPos neighborPos = pos.relative(run);
            final BlockState neighbor = level.getBlockState(neighborPos);
            if (!isExtension(neighbor)) {
                continue;
            }
            // 过弯第一格：旁边是转角板（朝向和自己垂直，且转角板另一侧有同朝向的直线段）。
            if (neighbor.getValue(FACING).getAxis() != facing.getAxis()
                    && hasSameFacingRun(level, neighborPos, neighbor.getValue(FACING))) {
                return EnumShape.END_WALL;
            }
            // 过弯后的后续格：同朝向的邻格已经是端门门楣，顺着传下去。
            if (neighbor.getValue(FACING) == facing && neighbor.getValue(SHAPE) == EnumShape.END_WALL) {
                return EnumShape.END_WALL;
            }
        }
        return EnumShape.STRAIGHT;
    }

    private static boolean isExtension(BlockState state) {
        return state.getBlock() instanceof BlockTrdmPSDExtension;
    }

    private static boolean isPerpendicularExtension(BlockState state, Direction facing) {
        return isExtension(state) && state.getValue(FACING).getAxis() != facing.getAxis();
    }

    private static boolean isSameFacing(BlockGetter level, BlockPos pos, Direction facing) {
        final BlockState state = level.getBlockState(pos);
        return isExtension(state) && state.getValue(FACING) == facing;
    }

    /** 这一格左右（沿门楣方向）有没有和自己同朝向的延伸板。 */
    private static boolean hasSameFacingRun(BlockGetter level, BlockPos pos, Direction facing) {
        return isSameFacing(level, pos.relative(facing.getClockWise()), facing)
                || isSameFacing(level, pos.relative(facing.getCounterClockWise()), facing);
    }

    public enum EnumShape implements StringRepresentable {
        STRAIGHT("straight"),
        CORNER_LEFT("corner_left"),
        CORNER_RIGHT("corner_right"),
        END_WALL("end_wall");

        private final String name;

        EnumShape(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
