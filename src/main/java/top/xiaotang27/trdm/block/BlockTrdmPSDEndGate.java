package top.xiaotang27.trdm.block;

import mtr.block.IBlock;
import mtr.mappings.BlockDirectionalMapper;
import mtr.mappings.BlockEntityMapper;
import mtr.mappings.EntityBlockMapper;
import net.minecraft.core.BlockPos;
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
 * 屏蔽门端门：一格玻璃面板 + 上方门楣（门楣是独立的 psd_top 方块）。
 * 右键只切换这一格的开门状态；关着挡人、开着放行。门楣不动。
 */
public class BlockTrdmPSDEndGate extends BlockDirectionalMapper implements EntityBlockMapper {

    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public BlockTrdmPSDEndGate() {
        super(Properties.of().strength(1.5F).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HorizontalDirectionalBlock.FACING, OPEN);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        if (IBlock.getStatePropertySafe(state, OPEN)) {
            return Shapes.empty();
        }
        final net.minecraft.core.Direction facing = IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING);
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
        world.setBlockAndUpdate(pos, state.setValue(OPEN, !IBlock.getStatePropertySafe(state, OPEN)));
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
