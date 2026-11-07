package top.xiaotang27.trdm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

/**
 * 盲道砖：贴在地面上的一层薄贴片（无碰撞，可以踩着走）。
 * <p>
 * 四个水平方向各有一个 blockstate 布尔量（{@link #NORTH} / {@link #EAST} / {@link #SOUTH} / {@link #WEST}），
 * 放置和邻格变化时只根据「紧贴着的邻格是不是盲道砖」重算这四个值；具体画线还是画点交给
 * {@code blockstates/tactile_paving.json}：
 * <ul>
 *     <li>只有一个邻格（一段连续盲道的端点）→ 点；</li>
 *     <li>两个正对邻格（直线中段）→ 线，南北向不转、东西向 y=90；</li>
 *     <li>两个相邻邻格（转角）、三个邻格（丁字路口）、四个邻格（十字路口）→ 点，
 *         也就是「交点用点」；</li>
 *     <li>没有邻格 → 点。</li>
 * </ul>
 * 这样一段连续的盲道永远是「两端点、中间线」，分叉/交叉处自动落成点。
 * <p>
 * 贴片本身只有一张贴地的平面（见 {@code models/block/tactile_paving_dot.json} 与
 * {@code tactile_paving_line.json}，上下两面都画），要放在完整方块的顶面上；下面被拆掉时贴片跟着消失。
 */
public class BlockTrdmTactilePaving extends Block {

    /** 这一格的正北 / 正东 / 正南 / 正西是否紧贴着另一块盲道砖。 */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    /** 选中框：1/16 高，够用鼠标点到就行；碰撞箱由 {@code noCollission()} 交给空形状。 */
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.0625D, 1.0D);

    public BlockTrdmTactilePaving() {
        super(Properties.of()
                .mapColor(MapColor.STONE)
                .strength(0.5F)
                .sound(SoundType.STONE)
                .noCollission()
                .noOcclusion());
        registerDefaultState(defaultBlockState()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    /**
     * 只允许贴着方块顶面放：盲道是地面贴片，不挂在墙或天花板上。
     * 放置位置由 {@link BlockPlaceContext#getClickedPos()} 给出（点顶面时就是上面那格）。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) {
            return null;
        }
        final BlockPos pos = context.getClickedPos();
        final BlockState state = defaultBlockState();
        if (!state.canSurvive(context.getLevel(), pos)) {
            return null;
        }
        return withConnections(state, context.getLevel(), pos);
    }

    /** 下面必须是完整的上表面（完整方块 / 上半台阶），否则不放。 */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        final BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /**
     * 任意邻格变动都重算四向连接；脚下没了就整块掉掉。
     * 连接标记只看「邻格是不是盲道砖」，所以直线 / 点 / 转角的重画由 blockstate 直接选模型，不需要额外逻辑。
     */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return withConnections(state, level, pos);
    }

    /** 把四个方向的「邻格是不是盲道砖」写进状态。 */
    private static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState result = state;
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            result = result.setValue(connectionProperty(direction),
                    level.getBlockState(pos.relative(direction)).getBlock() instanceof BlockTrdmTactilePaving);
        }
        return result;
    }

    private static BooleanProperty connectionProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Not a horizontal direction: " + direction);
        };
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** 没有碰撞，但也别让生物把这一格当成障碍绕开（和地毯一样可以踩上去）。 */
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return true;
    }

    @Override
    public Item asItem() {
        return Trdm.TACTILE_PAVING_ITEM;
    }
}
