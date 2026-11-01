package top.xiaotang27.trdm.block;

import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.client.TrdmControlClient;

/**
 * 大站名牌：横向大牌，深灰底板 + 地铁标志 + 线路编号块 + 本站站名 + 右边出口字母，
 * 牌高 3/4 格、贴墙挂。
 */
public class BlockTrdmBigStationSign extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 选中的出口名（MTR 车站出口，如「A」「B1」）；空串 = 不显示出口字母。 */
    public static final String KEY_EXIT_NAME = "exit_name";
    /** 站名后缀用 ZHAN（true）还是 STATION（false）。 */
    public static final String KEY_ZHAN_STYLE = "zhan_style";
    /** 是否画成「站名 + 无障碍电梯」牌。 */
    public static final String KEY_ELEVATOR = "elevator";
    /** 这一格是否被设置过（老存档里只存了出口的牌子没有这个标记）。 */
    public static final String KEY_CONFIGURED = "configured";
    /** 牌高（格）：3/4。 */
    public static final float PLATE_HEIGHT = 12F / 16F;
    /** 底板厚度（格）。 */
    public static final float BOARD_THICKNESS = 1F / 16F;

    public BlockTrdmBigStationSign() {
        super(Properties.of().strength(1F).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 碰撞箱：和牌面同一位置同一朝向（贴墙那一侧的 1/16 板，牌高 3/4）。 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return IBlock.getVoxelShapeByDirection(0, 2, 15, 16, 14, 16, IBlock.getStatePropertySafe(state, FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityTrdmBigStationSign(pos, state);
    }

    @Override
    public Item asItem() {
        return Trdm.BIG_SIGN_ITEM_1;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (world.isClientSide) {
            if (BlockTrdmPSDControl.isHoldingBrush(player)
                    && world.getBlockEntity(pos) instanceof TileEntityTrdmBigStationSign sign) {
                // 刷子右键 → 站名后缀 / 无障碍电梯牌 / 出口，三样都在同一个菜单里
                TrdmControlClient.openBigSignMenu(pos, sign.getExitName(), sign.isZhanStyle(), sign.isElevator());
            }
            return InteractionResult.SUCCESS;
        }
        return BlockTrdmPSDControl.isHoldingBrush(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /**
     * 方块实体：整段牌共用的三项 —— 出口名、站名后缀、无障碍电梯牌。
     */
    public static class TileEntityTrdmBigStationSign extends BlockEntity {

        private String exitName = "";
        private boolean zhanStyle;
        private boolean elevator;
        private boolean configured;

        public TileEntityTrdmBigStationSign(BlockPos pos, BlockState state) {
            this(Trdm.BIG_SIGN_1_BE, pos, state);
        }

        /** 悬挂款共用同一套数据（出口 / 后缀 / 无障碍电梯），只是方块实体类型不同。 */
        protected TileEntityTrdmBigStationSign(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        public String getExitName() {
            return exitName;
        }

        public boolean isZhanStyle() {
            return zhanStyle;
        }

        public boolean isElevator() {
            return elevator;
        }

        /** 这一格有没有被设置过：渲染端据此挑「整段牌的数据源」，刚放的空格子不会覆盖整段。 */
        public boolean isConfigured() {
            return configured;
        }

        public void setExitName(String exitName) {
            final String safe = exitName == null ? "" : exitName;
            if (!safe.equals(this.exitName)) {
                this.exitName = safe;
                this.configured = true;
                markSynced();
            }
        }

        public void setOptions(boolean zhanStyle, boolean elevator) {
            if (zhanStyle == this.zhanStyle && elevator == this.elevator && configured) {
                return;
            }
            this.zhanStyle = zhanStyle;
            this.elevator = elevator;
            this.configured = true;
            markSynced();
        }

        private void markSynced() {
            setChanged();
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().blockChanged(worldPosition);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putString(KEY_EXIT_NAME, exitName);
            tag.putBoolean(KEY_ZHAN_STYLE, zhanStyle);
            tag.putBoolean(KEY_ELEVATOR, elevator);
            tag.putBoolean(KEY_CONFIGURED, configured);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            exitName = tag.getString(KEY_EXIT_NAME);
            zhanStyle = tag.getBoolean(KEY_ZHAN_STYLE);
            elevator = tag.getBoolean(KEY_ELEVATOR);
            // 老存档没有这个标记：有出口也算设置过
            configured = tag.getBoolean(KEY_CONFIGURED) || !exitName.isEmpty();
        }

        @Override
        public CompoundTag getUpdateTag() {
            return saveWithoutMetadata();
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
