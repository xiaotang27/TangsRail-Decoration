package top.xiaotang27.trdm.block;

import mtr.block.IBlock;
import mtr.mappings.BlockDirectionalMapper;
import mtr.mappings.BlockEntityMapper;
import mtr.mappings.EntityBlockMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

/**
 * 屏蔽门端门：一整段（≥2 格）= 门楣 + 外侧可旋开的门 + 内侧固定玻璃；单格只有玻璃。
 * 门与玻璃比例 3:2，门宽取 floor(0.6W)。铰链在门的最外端，向站台侧旋转。
 */
public class BlockTrdmPSDEndGate extends BlockDirectionalMapper implements EntityBlockMapper {

    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    private static final int MAX_RUN = 64;

    public BlockTrdmPSDEndGate() {
        super(Properties.of().strength(1.5F).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, OPEN);
    }

    /** 这一格所属整段：返回 (段首, 段长)。 */
    public static Tuple<BlockPos, Integer> run(BlockGetter world, BlockPos pos, Direction facing) {
        final Block block = world.getBlockState(pos).getBlock();
        final Direction across = facing.getCounterClockWise();
        BlockPos start = pos;
        for (int i = 0; i < MAX_RUN && isMember(world, start.relative(across.getOpposite()), block, facing); i++) {
            start = start.relative(across.getOpposite());
        }
        int length = 0;
        BlockPos current = start;
        while (length < MAX_RUN && isMember(world, current, block, facing)) {
            length++;
            current = current.relative(across);
        }
        return new Tuple<>(start, length);
    }

    private static boolean isMember(BlockGetter world, BlockPos pos, Block block, Direction facing) {
        final BlockState state = world.getBlockState(pos);
        return state.getBlock() == block
                && IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING) == facing;
    }

    /** 段内这一格是门（true）还是玻璃（false）。 */
    public static boolean isDoorPart(BlockGetter world, BlockPos pos, Direction facing) {
        final Tuple<BlockPos, Integer> run = run(world, pos, facing);
        final int length = run.getB();
        if (length < 2) {
            return false;
        }
        final int doorWidth = doorWidth(length);
        final int offset = Math.abs(
                (pos.getX() - run.getA().getX()) + (pos.getZ() - run.getA().getZ()));
        return offset * 16 < doorWidth;
    }

    /** 门宽（px）：floor(0.6 * 16N)。 */
    public static int doorWidth(int runLength) {
        return (int) Math.floor(0.6D * 16D * runLength);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        final Direction facing = IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING);
        if (!isDoorPart(world, pos, facing)) {
            return gateBox(facing);
        }
        if (IBlock.getStatePropertySafe(state, OPEN)) {
            return Shapes.empty();
        }
        return gateBox(facing);
    }

    /** 单格的实心盒：2px 板，靠方块外缘。 */
    private static VoxelShape gateBox(Direction facing) {
        return switch (facing) {
            case NORTH -> Block.box(0, 0, 14, 16, 16, 16);
            case EAST -> Block.box(0, 0, 0, 2, 16, 16);
            case SOUTH -> Block.box(0, 0, 0, 16, 16, 2);
            default -> Block.box(14, 0, 0, 16, 16, 16);
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hitResult) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        final Direction facing = IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING);
        if (!isDoorPart(world, pos, facing)) {
            return InteractionResult.PASS;
        }
        final Tuple<BlockPos, Integer> run = run(world, pos, facing);
        final boolean open = !IBlock.getStatePropertySafe(state, OPEN);
        final Direction across = facing.getCounterClockWise();
        BlockPos current = run.getA();
        for (int i = 0; i < run.getB(); i++) {
            final BlockState target = world.getBlockState(current);
            if (target.getBlock() == this) {
                world.setBlockAndUpdate(current, target.setValue(OPEN, open));
            }
            current = current.relative(across);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntityMapper createBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityTrdmPSDEndGate(pos, state);
    }

    @Override
    public Item asItem() {
        return Trdm.PSD_END_GATE_ITEM;
    }

    public BlockEntityType<? extends BlockEntityMapper> blockEntityType() {
        return Trdm.PSD_END_GATE_BE;
    }

    public static class TileEntityTrdmPSDEndGate extends BlockEntityMapper {

        public TileEntityTrdmPSDEndGate(BlockPos pos, BlockState state) {
            super(Trdm.PSD_END_GATE_BE, pos, state);
        }
    }
}
