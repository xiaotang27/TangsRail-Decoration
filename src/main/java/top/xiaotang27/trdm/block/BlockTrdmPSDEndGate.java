package top.xiaotang27.trdm.block;

import mtr.block.BlockPSDAPGGlassBase;
import mtr.block.IBlock;
import mtr.mappings.BlockEntityMapper;
import mtr.mappings.EntityBlockMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

/**
 * 屏蔽门端门：与普通 PSD 玻璃同一套方块（{@link BlockPSDAPGGlassBase}，2 格高，
 * 状态 half=lower/upper、side=left/right/middle/single、facing），再额外加一个 {@link #OPEN}。
 * <p>
 * 上方的门楣是独立的 {@link BlockTrdmPSDTop}（不动），端门自己只有下面这 2 格可动。
 * <ul>
 *     <li>合并：拿 MTR 的刷子右键，直接走 {@code BlockPSDAPGGlassBase#use} 的 connectGlass，
 *         与普通玻璃同一套，把左右邻格焊成一整段；</li>
 *     <li>开关：非刷子右键翻 {@link #OPEN}，这一格 + 上下半格 + 左右连着的同向格一起翻，
 *         开门时整段玻璃一起旋开；</li>
 *     <li>碰撞：开着时碰撞箱为空（让出通道），关着时沿用 PSD 玻璃的 4px 板；
 *         选中框两态都保留玻璃那块板，否则开门后点不到、关不回来。</li>
 * </ul>
 * 门扇怎么画：
 * <ul>
 *     <li>关着：blockstate 直接指向 MTR 的玻璃模型，外观与普通 PSD 玻璃没有差别；</li>
 *     <li>开着：blockstate 指向空模型（{@code minecraft:block/air}），整段门扇交给
 *         {@code RenderTrdmPSDEndGate} 绕「最外侧竖轴」整体摆 90° 画出来 ——
 *         一整扇 N 格宽的门转开之后已经跨到别的格子，静态方块模型画不了。</li>
 * </ul>
 */
public class BlockTrdmPSDEndGate extends BlockPSDAPGGlassBase implements EntityBlockMapper {

    /** 开度状态的最大档位：0 = 全关，{@code OPEN_STEPS} = 全开（每档 6°）。 */
    public static final int OPEN_STEPS = 15;

    /**
     * 开门开度，量化成 {@link #OPEN_STEPS} 档（0 = 全关，15 = 全开），整段共享同一个值。
     * <p>
     * 为什么是状态而不是方块实体里的浮点：状态是现成的同步手段（别人也看得见），不用加同步通道。
     * 拖动时客户端另外在本地按连续角度显示（见 {@code TrdmEndGateDrag}），松手落到最近一档，
     * 所以手感是平滑的、状态仍是离散的。
     */
    public static final IntegerProperty OPEN = IntegerProperty.create("open", 0, OPEN_STEPS);

    /** 沿门排横向传播的上限，纯粹防死循环。 */
    private static final int MAX_RUN_LENGTH = 256;

    public BlockTrdmPSDEndGate() {
        super();
        registerDefaultState(defaultBlockState().setValue(OPEN, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // FACING / HALF / SIDE_EXTENDED 是 MTR 的 BlockPSDAPGGlassBase 加的，这里只追加 open
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final BlockState state = super.getStateForPlacement(context);
        // 单独立一格时按 SINGLE 画（两侧都有灯条）；连成一排再拿刷子合并
        return state == null ? null : state.setValue(IBlock.SIDE_EXTENDED, IBlock.EnumSide.SINGLE);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hitResult) {
        // 蹲下 + 刷子：打开这一段的「屏蔽门外观设置」（门楣吊版 / 玻璃装饰线），
        // 与普通玻璃 / 门扇 / 门楣同一套入口。
        final InteractionResult appearance = BlockTrdmPSDTop.openAppearanceOnShiftBrush(world, pos, player);
        if (appearance != null) {
            return appearance;
        }
        if (BlockTrdmPSDControl.isHoldingBrush(player)) {
            // 刷子：交给 MTR 的 connectGlass 焊接左右邻格（与普通玻璃同一套合并逻辑）
            return super.use(state, world, pos, player, hand, hitResult);
        }
        // 开合 / 开度由客户端在「松开右键」时发 C2S 包决定（短按 = 全开/全关，按住拖动 = 给开度）：
        // 服务端这里不做任何事 —— 原版按住右键每 4 tick 会重复触发 use，服务端一改就会来回跳。
        return InteractionResult.SUCCESS;
    }

    /**
     * 选中框 / 碰撞箱要分开：
     * <ul>
     *     <li>{@link #getShape} 沿用 PSD 玻璃那块板 —— 原版取物（{@code ClipContext.Block.OUTLINE}）只认它，
     *         所以**不能**做成空形状，否则开门后点不到方块、关不回来；</li>
     *     <li>{@link #getCollisionShape}：<b>只要开了（任意开度，包括半开）就让出通道</b>，
     *         只有全关（0 档）才按玻璃板挡人 —— 是按需求定的：半开也得能过人。</li>
     * </ul>
     * 门扇旋出去的那部分落在别的格子里，方块自己的碰撞箱管不到那里，所以这一格永远是「要么挡、要么空」。
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return isOpen(state) ? Shapes.empty() : super.getShape(state, world, pos, context);
    }

    /** 这一格当前的开度档位（0 = 全关，{@link #OPEN_STEPS} = 全开）。 */
    public static int openStep(BlockState state) {
        return IBlock.getStatePropertySafe(state, OPEN);
    }

    /** 这一格是不是开着（任意档位 > 0）。 */
    public static boolean isOpen(BlockState state) {
        return openStep(state) > 0;
    }

    /**
     * 点任意一格改开度：这一格 + 它的上下半格 + 左右连着的整段一起改。
     * 合并后整段共享同一个 {@link #OPEN}（开门时整段玻璃一起动）。
     */
    public static void setOpenAround(Level world, BlockPos pos, int step) {
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmPSDEndGate)) {
            return;
        }
        final int target = Mth.clamp(step, 0, OPEN_STEPS);
        final Direction facing = IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING);
        final DoubleBlockHalf half = IBlock.getStatePropertySafe(state, IBlock.HALF);
        final BlockPos otherHalf = half == DoubleBlockHalf.UPPER ? pos.below() : pos.above();
        applyOpen(world, pos, target);
        applyOpen(world, otherHalf, target);
        // 上下半格各自沿门排传播（两个半格不是同一格，得分开走）
        for (final BlockPos from : new BlockPos[]{pos, otherHalf}) {
            propagateOpen(world, from, facing, facing.getClockWise(), target);
            propagateOpen(world, from, facing, facing.getCounterClockWise(), target);
        }
    }

    /** 只改一格；不是端门 / 已经是目标值就跳过。 */
    private static void applyOpen(Level world, BlockPos pos, int step) {
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmPSDEndGate) || openStep(state) == step) {
            return;
        }
        world.setBlockAndUpdate(pos, state.setValue(OPEN, step));
    }

    /** 从 from 沿 direction 一路改，碰到不是同方向端门的格子就停。 */
    private static void propagateOpen(Level world, BlockPos from, Direction facing, Direction direction, int step) {
        BlockPos current = from.relative(direction);
        for (int i = 0; i < MAX_RUN_LENGTH; i++) {
            final BlockState state = world.getBlockState(current);
            if (!(state.getBlock() instanceof BlockTrdmPSDEndGate)
                    || IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING) != facing) {
                return;
            }
            applyOpen(world, current, step);
            current = current.relative(direction);
        }
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
