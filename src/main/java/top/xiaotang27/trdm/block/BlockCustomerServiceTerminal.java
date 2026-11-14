package top.xiaotang27.trdm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.client.TicketClient;

import java.util.Locale;

/** 客服终端：带两种可选模型，刷子右键打开模型选择界面。 */
public class BlockCustomerServiceTerminal extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Model> MODEL = EnumProperty.create("model", Model.class);

    private static final VoxelShape SHAPE_ONE = Shapes.box(4.0 / 16, 0, 6.0 / 16,
            12.0 / 16, 9.0 / 16, 10.0 / 16);
    private static final VoxelShape SHAPE_TWO = Shapes.box(3.0 / 16, 0, 0,
            13.0 / 16, 15.0 / 16, 1.0);

    public enum Model implements StringRepresentable {
        ONE,
        TWO;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public BlockCustomerServiceTerminal() {
        super(Properties.of().strength(1F).noOcclusion());
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(MODEL, Model.ONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(MODEL, Model.ONE);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return state.getValue(MODEL) == Model.TWO ? SHAPE_TWO : SHAPE_ONE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, world, pos, context);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (world.isClientSide) {
            if (BlockTrdmPSDControl.isHoldingBrush(player)) {
                TicketClient.openTerminalModel(pos, state.getValue(MODEL));
            } else {
                TicketClient.openTerminal(pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** 刷子选择模型后由服务端写回方块状态。 */
    public static void setModel(ServerLevel world, BlockPos pos, Model model) {
        final BlockState state = world.getBlockState(pos);
        if (state.is(Trdm.CUSTOMER_SERVICE_TERMINAL) && state.getValue(MODEL) != model) {
            world.setBlock(pos, state.setValue(MODEL, model), Block.UPDATE_ALL);
        }
    }

    @Override
    public Item asItem() {
        return Trdm.CUSTOMER_SERVICE_TERMINAL_ITEM;
    }
}
