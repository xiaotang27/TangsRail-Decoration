package top.xiaotang27.trdm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

/**
 * 盲道台阶：半格高（8px）的盲道砖，顶面用线/点贴图，侧面和底面用 {@code gray_paving}。
 * <p>
 * 连接逻辑与贴地盲道 {@link BlockTrdmTactilePaving} 完全一致：四个水平方向各一个布尔量，
 * 只根据「紧贴着的邻格是不是同一种盲道台阶」重算；两正对邻格 → 线（南北向不转、东西向 y=90），
 * 端点 / 转角 / 丁字 / 十字 / 单独一格 → 点。
 * <p>
 * 台阶是完整的半格方块（有碰撞，可以直接踩上去），和贴地盲道各自成段、互不连接。
 */
public class BlockTrdmTactilePavingStep extends Block {

    /** 这一格的正北 / 正东 / 正南 / 正西是否紧贴着另一块盲道台阶。 */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    /** 半格高（8/16）的碰撞箱 / 选中框。 */
    private static final VoxelShape SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 1.0D);

    public BlockTrdmTactilePavingStep() {
        super(Properties.of()
                .mapColor(MapColor.STONE)
                .strength(0.5F)
                .sound(SoundType.STONE));
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

    /** 普通半格方块，不要求下方支撑；放置时按四周台阶算连接。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    /** 任意邻格变动都重算四向连接，和贴地盲道一致。 */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return withConnections(state, level, pos);
    }

    /** 把四个方向的「邻格是不是盲道台阶」写进状态。 */
    private static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState result = state;
        for (final Direction direction : Direction.Plane.HORIZONTAL) {
            result = result.setValue(connectionProperty(direction),
                    level.getBlockState(pos.relative(direction)).getBlock() instanceof BlockTrdmTactilePavingStep);
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

    @Override
    public Item asItem() {
        return Trdm.TACTILE_PAVING_STEP_ITEM;
    }
}
