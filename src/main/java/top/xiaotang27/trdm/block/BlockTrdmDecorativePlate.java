package top.xiaotang27.trdm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 装饰薄板：1/16 格厚（1px）的带贴面片，用来在已有方块的表面「贴一层花纹」——
 * 大理石铺地、穿孔板做吊顶，都不用整块占掉一格。
 * <p>
 * 和工程里的贴地盲道 {@link BlockTrdmTactilePaving} 一样是「贴片」语义：
 * 有依托才存在，依托（哪一侧贴着的方块）被拆掉就整片消失。
 * <ul>
 *     <li>{@link #TOP} = {@code false}：铺在地面上（占 y 0–1/16）；</li>
 *     <li>{@link #TOP} = {@code true}：贴在天花板下面（占 y 15/16–1），
 *         放置时点方块的底面即可。</li>
 * </ul>
 * 面片没有碰撞箱（可以踩过去 / 抬头穿过），选中框按当前朝向给对应那一层。
 */
public class BlockTrdmDecorativePlate extends Block {

    /** 薄板贴在格子的顶部（吊顶）还是底部（铺地）。 */
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    /** 铺地：格底 1/16 那一层。 */
    private static final VoxelShape SHAPE_BOTTOM = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.0625D, 1.0D);
    /** 吊顶：格顶 1/16 那一层。 */
    private static final VoxelShape SHAPE_TOP = Shapes.box(0.0D, 0.9375D, 0.0D, 1.0D, 1.0D, 1.0D);

    private BlockTrdmDecorativePlate(MapColor mapColor, SoundType sound) {
        super(Properties.of()
                .mapColor(mapColor)
                .strength(0.5F)
                .sound(sound)
                .noCollission()
                .noOcclusion());
        registerDefaultState(defaultBlockState().setValue(TOP, false));
    }

    /** 大理石地板的薄板。 */
    public static BlockTrdmDecorativePlate stone() {
        return new BlockTrdmDecorativePlate(MapColor.STONE, SoundType.STONE);
    }

    /** 穿孔吊顶板的薄板。 */
    public static BlockTrdmDecorativePlate metal() {
        return new BlockTrdmDecorativePlate(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP);
    }

    /**
     * 点在方块底面 → 贴上去（吊顶）；点顶面 / 侧面 → 铺在地面。
     * 没依托（比如悬空点方块侧面）就不放。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final BlockState state = defaultBlockState().setValue(TOP, context.getClickedFace() == Direction.DOWN);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** 依托：铺地要求下方方块上表面结实，吊顶要求上方方块下表面结实。 */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        final Direction support = state.getValue(TOP) ? Direction.UP : Direction.DOWN;
        final BlockPos anchorPos = pos.relative(support);
        return level.getBlockState(anchorPos).isFaceSturdy(level, anchorPos, support.getOpposite());
    }

    /** 依托没了就整片消失（和贴地盲道一致）。 */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    /** 选中框跟着朝向走：铺地是格底那层，吊顶是格顶那层。 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TOP) ? SHAPE_TOP : SHAPE_BOTTOM;
    }
}
