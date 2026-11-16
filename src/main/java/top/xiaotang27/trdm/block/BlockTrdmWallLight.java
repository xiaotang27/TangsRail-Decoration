package top.xiaotang27.trdm.block;

import mtr.mappings.BlockEntityMapper;
import mtr.mappings.EntityBlockMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

/**
 * 墙上的荧光灯管（模型 1）。它单独放置时是一个真的方块：
 * <ul>
 *     <li>贴在方块的下表面（吊顶）→ 灯管沿玩家朝向水平布置，模型在格子里居中；</li>
 *     <li>贴在方块的侧面（墙）→ 底座贴墙，灯管水平伸出；</li>
 *     <li>贴在方块的上表面（地面）→ 底座贴地（也允许，主要方便放在低处）。</li>
 * </ul>
 * 模型本身只在方块实体渲染器里画（{@link top.xiaotang27.trdm.render.RenderTrdmWallLight}），
 * 方块状态只存「底座朝哪面」和「灯管沿哪条水平轴」，方块模型的 elements 为空，
 * 但保留 particle 贴图给破坏粒子用。
 * <p>
 * 如果玩家把这件物品点在 TRD 门楣 / 延伸板上，{@link top.xiaotang27.trdm.item.ItemTrdmWallLight}
 * 会改为「贴装到门楣上」，不再放这一格独立方块。
 */
public class BlockTrdmWallLight extends Block implements EntityBlockMapper {

    /** 底座贴在哪一面：UP = 吊顶、DOWN = 地面、其余 = 那面墙。 */
    public static final EnumProperty<EnumMount> MOUNT = EnumProperty.create("mount", EnumMount.class);
    /** 灯管的水平走向（只对吊顶 / 地面有意义；贴墙时由 MOUNT 决定）。 */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** 灯管本体只有约 0.8 像素粗；选中框 / 碰撞箱按模型实际占的那一条薄片给。 */
    private static final double TUBE_EDGE = 0.85D / 16.0D;
    private static final double TUBE_Y0 = 7.5D / 16.0D;
    private static final double TUBE_Y1 = 8.5D / 16.0D;
    private static final double CEILING_Y0 = 15.18D / 16.0D;

    public BlockTrdmWallLight() {
        super(Properties.of().strength(0.5F).noOcclusion().lightLevel(state -> 15));
        registerDefaultState(defaultBlockState()
                .setValue(MOUNT, EnumMount.UP)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOUNT, FACING);
    }

    /**
     * 放置时把「底座贴在点击面的反方向」：点方块的下表面 → 新方块在下面、底座朝上（吊顶），
     * 点侧面 → 底座朝向被点的那一格（墙），点上表面 → 底座朝下（地面）。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        final Direction mount = context.getClickedFace().getOpposite();
        return defaultBlockState()
                .setValue(MOUNT, EnumMount.fromDirection(mount))
                .setValue(FACING, context.getHorizontalDirection());
    }

    /**
     * 选中框 / 碰撞箱和模型的朝向一致：
     * 吊顶 / 地面时灯管沿 {@link #FACING} 的水平轴铺满整格；贴墙时灯管沿墙面铺满，
     * 厚度只占底座到灯管外沿那一条。之前吊顶的框固定按 z 向给，横着放时就会和模型错 90°。
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        final boolean alongX = state.getValue(FACING).getAxis() == Direction.Axis.X;
        return switch (state.getValue(MOUNT)) {
            case UP -> alongX
                    ? Shapes.box(0.0D, CEILING_Y0, 7.5D / 16D, 1.0D, 1.0D, 8.5D / 16D)
                    : Shapes.box(7.5D / 16D, CEILING_Y0, 0.0D, 8.5D / 16D, 1.0D, 1.0D);
            case DOWN -> alongX
                    ? Shapes.box(0.0D, 0.0D, 7.5D / 16D, 1.0D, TUBE_EDGE, 8.5D / 16D)
                    : Shapes.box(7.5D / 16D, 0.0D, 0.0D, 8.5D / 16D, TUBE_EDGE, 1.0D);
            case NORTH -> Shapes.box(0.0D, TUBE_Y0, 0.0D, 1.0D, TUBE_Y1, TUBE_EDGE);
            case SOUTH -> Shapes.box(0.0D, TUBE_Y0, 1.0D - TUBE_EDGE, 1.0D, TUBE_Y1, 1.0D);
            case WEST -> Shapes.box(0.0D, TUBE_Y0, 0.0D, TUBE_EDGE, TUBE_Y1, 1.0D);
            case EAST -> Shapes.box(1.0D - TUBE_EDGE, TUBE_Y0, 0.0D, 1.0D, TUBE_Y1, 1.0D);
        };
    }

    /**
     * 碰撞箱和选中框一致：都很薄，贴着放置面，不挡路；但方向和模型一致，
     * F3+B 看到的框 / 射线选中的位置不会和灯管错开。
     */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    public Item asItem() {
        return Trdm.WALL_LIGHT_ITEM;
    }

    @Override
    public BlockEntityMapper createBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityTrdmWallLight(pos, state);
    }

    /** 独立灯管没有额外数据；方块实体只是为了挂 BER。 */
    public static class TileEntityTrdmWallLight extends BlockEntityMapper {

        public TileEntityTrdmWallLight(BlockPos pos, BlockState state) {
            super(Trdm.WALL_LIGHT_BE, pos, state);
        }
    }

    public enum EnumMount implements StringRepresentable {
        UP("up"),
        DOWN("down"),
        NORTH("north"),
        SOUTH("south"),
        EAST("east"),
        WEST("west");

        private final String name;

        EnumMount(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public static EnumMount fromDirection(Direction direction) {
            if (direction == null) {
                return UP;
            }
            return switch (direction) {
                case DOWN -> DOWN;
                case NORTH -> NORTH;
                case SOUTH -> SOUTH;
                case EAST -> EAST;
                case WEST -> WEST;
                default -> UP;
            };
        }
    }
}
