package top.xiaotang27.trdm.block;

import mtr.Items;
import mtr.block.BlockPSDAPGBase;
import mtr.block.BlockPSDAPGDoorBase;
import mtr.block.BlockPSDAPGGlassBase;
import mtr.block.BlockPSDAPGGlassEndBase;
import mtr.block.BlockPSDTop;
import mtr.block.IBlock;
import mtr.mappings.BlockDirectionalMapper;
import mtr.mappings.BlockEntityMapper;
import mtr.mappings.EntityBlockMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

/**
 * TRD 屏蔽门门楣（第三格），逻辑主体照抄 MTR 的 BlockPSDTop，差异有三条（都是本 mod 的需求）：
 * <ol>
 */
public class BlockTrdmPSDTop extends BlockDirectionalMapper implements EntityBlockMapper, IBlock {

    private static final float PERSISTENT_OFFSET = 7.5F;
    public static final float PERSISTENT_OFFSET_SMALL = PERSISTENT_OFFSET / 16;

    /**
     * 沿一排门楣横向遍历的上限，纯粹防死循环。
     */
    private static final int MAX_RUN_LENGTH = 256;

    /** 往上找门楣最多找几格（门扇 / 玻璃都是 2 格高，门楣在第三格）。 */
    private static final int HEADER_SEARCH_HEIGHT = 4;

    /** 吊版样式（方块状态，跟箭头方向一样靠 setBlockAndUpdate 存盘和同步）。 */
    public static final IntegerProperty HEADER_STYLE = IntegerProperty.create("header_style", 0, 15);
    /** 玻璃装饰线样式（方块状态）。 */
    public static final IntegerProperty BAND_STYLE = IntegerProperty.create("band_style", 0, 15);

    public static final BooleanProperty AIR_LEFT = BooleanProperty.create("air_left");
    public static final BooleanProperty AIR_RIGHT = BooleanProperty.create("air_right");
    public static final IntegerProperty ARROW_DIRECTION = IntegerProperty.create("arrow_direction", 0, 3);
    public static final EnumProperty<EnumPersistent> PERSISTENT = EnumProperty.create("persistent", EnumPersistent.class);

    public BlockTrdmPSDTop() {
        super(Properties.of().requiresCorrectToolForDrops().strength(2).noOcclusion());
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        // 蹲下 + 刷子：打开屏蔽门外观设置（吊版 / 玻璃装饰线样式）。
        // 放在最前面，门楣也算「屏蔽门」的一部分 —— 点哪一格都能开。
        final InteractionResult appearance = openAppearanceOnShiftBrush(world, pos, player);
        if (appearance != null) {
            return appearance;
        }
        return IBlock.checkHoldingItem(world, player, item -> {
            if (item == Items.BRUSH.get()) {
                // 刷子：翻转整排门楣的箭头方向。站点信息 / 滑动门 / 线路图吊板都能刷，
                // 刷哪一格都是整面墙一起换向（站点信息版面和线路图都读 ARROW_DIRECTION）
                cycleArrowDirection(world, pos, IBlock.getStatePropertySafe(state, FACING));
            } else {
                final boolean shouldBePersistent = IBlock.getStatePropertySafe(state, PERSISTENT) == EnumPersistent.NONE;
                setPersistentState(world, pos, shouldBePersistent);
                final Direction facing = IBlock.getStatePropertySafe(state, FACING);
                propagate(world, pos, facing.getClockWise(), offsetPos -> setPersistentState(world, offsetPos, shouldBePersistent), 1);
                propagate(world, pos, facing.getCounterClockWise(), offsetPos -> setPersistentState(world, offsetPos, shouldBePersistent), 1);
            }
        }, null, Items.BRUSH.get(), net.minecraft.world.item.Items.SHEARS);
    }

    /**
     * 放下一格门楣就把整段门楣连起来（以前是「蹲下 + 刷子」干的活，现在自动做，
     * 蹲下 + 刷子腾出来开外观设置）。
     */
    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, world, pos, oldState, movedByPiston);
        if (!world.isClientSide) {
            connectHeaders(world, pos, state);
        }
    }

    /**
     * 「蹲下 + 刷子右键」屏蔽门 / 玻璃 / 端头玻璃：打开这一段的「屏蔽门外观设置」
     * （吊版样式 + 玻璃装饰线样式）。
     */
    public static InteractionResult openAppearanceOnShiftBrush(Level world, BlockPos pos, Player player) {
        if (!player.isShiftKeyDown() || !BlockTrdmPSDControl.isHoldingBrush(player)) {
            return null;
        }
        if (world.isClientSide) {
            top.xiaotang27.trdm.client.TrdmControlClient.openPsdAppearance(pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 找到这次点击对应的门楣：点的是门楣本身就用它，点的是门扇 / 玻璃 / 端头玻璃就往上看
     * （门扇和玻璃都是 2 格高，门楣在第三格）。找不到返回 null。
     */
    public static BlockPos findHeaderAbove(BlockGetter world, BlockPos pos) {
        if (world.getBlockState(pos).getBlock() instanceof BlockTrdmPSDTop) {
            return pos;
        }
        for (int offset = 1; offset <= HEADER_SEARCH_HEIGHT; offset++) {
            final BlockPos candidate = pos.above(offset);
            if (world.getBlockState(candidate).getBlock() instanceof BlockTrdmPSDTop) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 翻转整排门楣的箭头方向（{@link #ARROW_DIRECTION} 每次 +1，奇偶翻转 = 左右互换）。
     * 站点信息吊板的版面镜像和线路图的 flip 都读这个值，所以刷一次整排（含线路图）一起跟着换向，
     * 文字内容本身不变。
     */
    private static void cycleArrowDirection(Level world, BlockPos pos, Direction facing) {
        final BlockState state = world.getBlockState(pos);
        final int arrowDirection = (IBlock.getStatePropertySafe(state, ARROW_DIRECTION) + 1) % 4;
        setArrowDirection(world, pos, arrowDirection);
        setArrowDirectionAlong(world, pos, facing, facing.getClockWise(), arrowDirection);
        setArrowDirectionAlong(world, pos, facing, facing.getCounterClockWise(), arrowDirection);
    }

    private static void setArrowDirectionAlong(Level world, BlockPos pos, Direction facing, Direction direction, int arrowDirection) {
        BlockPos current = pos.relative(direction);
        for (int i = 1; i <= MAX_RUN_LENGTH; i++) {
            final BlockState state = world.getBlockState(current);
            if (!(state.getBlock() instanceof BlockTrdmPSDTop) || IBlock.getStatePropertySafe(state, FACING) != facing) {
                return;
            }
            setArrowDirection(world, current, arrowDirection);
            current = current.relative(direction);
        }
    }

    private static void setArrowDirection(Level world, BlockPos pos, int arrowDirection) {
        final BlockState state = world.getBlockState(pos);
        if (IBlock.getStatePropertySafe(state, ARROW_DIRECTION) != arrowDirection) {
            world.setBlockAndUpdate(pos, state.setValue(ARROW_DIRECTION, arrowDirection));
        }
    }

    /**
     * 判断这一格门楣是否应该画「站点信息吊板」。
     * <p>
     */
    public static boolean isStationInfoPanel(BlockGetter world, BlockPos pos, Direction facing) {
        if (!isPlainGlassHeader(world, pos)) {
            return false;
        }

        BlockPos leftMost = pos;
        for (int i = 0; i < MAX_RUN_LENGTH && isHeader(world, leftMost.relative(facing.getCounterClockWise())); i++) {
            leftMost = leftMost.relative(facing.getCounterClockWise());
        }

        int glassGroupIndex = -1;
        boolean inGlass = false;
        BlockPos current = leftMost;
        for (int i = 0; i < MAX_RUN_LENGTH; i++) {
            final boolean glass = isPlainGlassHeader(world, current);
            if (glass && !inGlass) {
                glassGroupIndex++;
            }
            inGlass = glass;
            if (current.equals(pos)) {
                break;
            }
            final BlockPos next = current.relative(facing.getClockWise());
            if (!isHeader(world, next)) {
                break;
            }
            current = next;
        }

        return glassGroupIndex >= 0 && glassGroupIndex % 2 == 0;
    }

    private static boolean isPlainGlassHeader(BlockGetter world, BlockPos pos) {
        if (!isHeader(world, pos)) {
            return false;
        }
        final Block below = world.getBlockState(pos.below()).getBlock();
        return below instanceof BlockPSDAPGGlassBase && !(below instanceof BlockPSDAPGGlassEndBase);
    }

    /**
     * 把这一格和左右连续的同类门楣按「左右还有没有门楣」重算侧别，从而合并成一整排。
     */
    private void connectHeaders(Level world, BlockPos pos, BlockState state) {
        connectHeader(world, pos);
        final Direction facing = IBlock.getStatePropertySafe(state, FACING);
        propagate(world, pos, facing.getClockWise(), offsetPos -> connectHeader(world, offsetPos), 1);
        propagate(world, pos, facing.getCounterClockWise(), offsetPos -> connectHeader(world, offsetPos), 1);
    }

    private static void connectHeader(Level world, BlockPos pos) {
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmPSDTop)) {
            return;
        }
        final Direction facing = IBlock.getStatePropertySafe(state, FACING);
        final EnumSide side = TrdmPSDSide.compute(isHeader(world, pos.relative(facing.getCounterClockWise())), isHeader(world, pos.relative(facing.getClockWise())));
        if (state.getValue(SIDE_EXTENDED) != side) {
            world.setBlockAndUpdate(pos, state.setValue(SIDE_EXTENDED, side));
        }
    }

    private static boolean isHeader(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockTrdmPSDTop;
    }

    private void setPersistentState(Level world, BlockPos pos, boolean shouldBePersistent) {
        final Block blockBelow = world.getBlockState(pos.below()).getBlock();
        if (blockBelow instanceof BlockPSDAPGBase) {
            if (shouldBePersistent) {
                final EnumPersistent persistent = blockBelow instanceof BlockPSDAPGDoorBase ? EnumPersistent.ARROW
                        : blockBelow instanceof BlockPSDAPGGlassEndBase ? EnumPersistent.BLANK : EnumPersistent.ROUTE;
                world.setBlockAndUpdate(pos, world.getBlockState(pos).setValue(PERSISTENT, persistent));
            } else {
                world.setBlockAndUpdate(pos, world.getBlockState(pos).setValue(PERSISTENT, EnumPersistent.NONE));
            }
        }
    }

    @Override
    public Item asItem() {
        return Trdm.PSD_GLASS_ITEM_1;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter blockGetter, BlockPos blockPos, BlockState blockState) {
        return new ItemStack(asItem());
    }

    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        final Block blockDown = world.getBlockState(pos.below()).getBlock();
        if (blockDown instanceof BlockPSDAPGBase) {
            blockDown.playerWillDestroy(world, pos.below(), world.getBlockState(pos.below()), player);
            world.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
        }
        super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState newState, LevelAccessor world, BlockPos pos, BlockPos posFrom) {
        if (direction == Direction.DOWN && IBlock.getStatePropertySafe(state, PERSISTENT) == EnumPersistent.NONE && !(newState.getBlock() instanceof BlockPSDAPGBase)) {
            return Blocks.AIR.defaultBlockState();
        } else {
            return getActualState(world, pos);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter blockGetter, BlockPos pos, CollisionContext collisionContext) {
        final VoxelShape baseShape = IBlock.getVoxelShapeByDirection(0, IBlock.getStatePropertySafe(state, PERSISTENT) == EnumPersistent.NONE ? 0 : PERSISTENT_OFFSET, 0, 16, 16, 6, IBlock.getStatePropertySafe(state, FACING));
        final boolean airLeft = IBlock.getStatePropertySafe(state, AIR_LEFT);
        final boolean airRight = IBlock.getStatePropertySafe(state, AIR_RIGHT);
        return airLeft || airRight ? BlockTrdmPSDGlassEnd.getEndOutlineShape(baseShape, state, 16, 6, airLeft, airRight) : baseShape;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIDE_EXTENDED, AIR_LEFT, AIR_RIGHT, ARROW_DIRECTION, PERSISTENT, HEADER_STYLE, BAND_STYLE);
    }

    /** 这一格门楣当前选的吊版样式。 */
    public static int headerStyle(BlockState state) {
        return IBlock.getStatePropertySafe(state, HEADER_STYLE);
    }

    /** 这一格门楣当前选的玻璃装饰线样式。 */
    public static int bandStyle(BlockState state) {
        return IBlock.getStatePropertySafe(state, BAND_STYLE);
    }

    /**
     * 改一格门楣的样式。样式是<b>方块状态</b>（不是方块实体 NBT）：MTR 的 BE 基类把
     * {@code saveAdditional} 写成了 final，加不了自己的数据；而方块状态本来就会存盘，
     * 改完自动同步给客户端，渲染端下一帧就能读到。
     */
    public static void applyStyles(Level world, BlockPos pos, int headerStyle, int bandStyle) {
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmPSDTop)) {
            return;
        }
        if (headerStyle(state) == headerStyle && bandStyle(state) == bandStyle) {
            return;
        }
        world.setBlockAndUpdate(pos, state.setValue(HEADER_STYLE, headerStyle).setValue(BAND_STYLE, bandStyle));
    }

    @Override
    public BlockEntityMapper createBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityTrdmPSDTop(pos, state);
    }

    /**
     * 门楣状态推导，和 MTR 原版一致：
     * <ul>
     */
    public static BlockState getActualState(BlockGetter world, BlockPos pos) {
        Direction facing = null;
        EnumSide side = null;
        boolean airLeft = false;
        boolean airRight = false;

        final BlockState stateBelow = world.getBlockState(pos.below());
        final Block blockBelow = stateBelow.getBlock();
        final BlockState oldState = world.getBlockState(pos);
        if (blockBelow instanceof BlockPSDAPGBase) {
            facing = IBlock.getStatePropertySafe(stateBelow, FACING);
            final boolean leftConnected = isHeader(world, pos.relative(facing.getCounterClockWise()));
            final boolean rightConnected = isHeader(world, pos.relative(facing.getClockWise()));
            side = TrdmPSDSide.compute(leftConnected, rightConnected);
            final boolean persistent = IBlock.getStatePropertySafe(oldState, PERSISTENT) != EnumPersistent.NONE;
            if (blockBelow instanceof BlockPSDAPGGlassEndBase && !persistent) {
                // 端头玻璃：空着的那一侧弯成端头
                airLeft = !leftConnected;
                airRight = !rightConnected;
            }
        }

        BlockState newState = (oldState.getBlock() instanceof BlockTrdmPSDTop ? oldState : Trdm.PSD_TOP.defaultBlockState())
                .setValue(AIR_LEFT, airLeft)
                .setValue(AIR_RIGHT, airRight);
        if (facing != null) {
            newState = newState.setValue(FACING, facing);
        }
        if (side != null) {
            newState = newState.setValue(SIDE_EXTENDED, side);
        }
        return newState;
    }

    public static class TileEntityTrdmPSDTop extends BlockPSDTop.TileEntityRouteBase {

        public TileEntityTrdmPSDTop(BlockPos pos, BlockState state) {
            super(Trdm.PSD_TOP_BE, pos, state);
        }
    }

    public enum EnumPersistent implements net.minecraft.util.StringRepresentable {

        NONE("none"), ARROW("arrow"), ROUTE("route"), BLANK("blank");

        private final String name;

        EnumPersistent(String nameIn) {
            name = nameIn;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
