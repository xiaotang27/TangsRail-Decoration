package top.xiaotang27.trdm.block;

import mtr.block.IBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * 站名牌：白底挂牌，显示本站站名（中文 + 拼音）+ 线路色带 + 下一站，排版照 MTR 的车站站名牌。
 * <ul>
 */
public class BlockTrdmStationSign extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 选中的站台 id（0 = 跟随标牌附近的站台）。 */
    public static final String KEY_PLATFORM_ID = "platform_id";

    public BlockTrdmStationSign() {
        super(Properties.of().strength(1F).noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** FACING = 牌面朝向（字朝哪边）。放置时朝向玩家。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** 碰撞箱就是贴墙那块底板：1 格宽 × 3/4 格高 × 1/16 厚（底板和牌面都由渲染器画）。 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return IBlock.getVoxelShapeByDirection(0, 2, 15, 16, 14, 16, IBlock.getStatePropertySafe(state, FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityTrdmStationSign(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (world.isClientSide) {
            if (BlockTrdmPSDControl.isHoldingBrush(player)
                    && world.getBlockEntity(pos) instanceof TileEntityTrdmStationSign sign) {
                TrdmControlClient.openPlatformSelect(pos, sign.getPlatformId(), false,
                        TrdmControlClient.SelectMode.STATION_SIGN);
            }
            return InteractionResult.SUCCESS;
        }
        return BlockTrdmPSDControl.isHoldingBrush(player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** 站名牌的方块实体：只存一个「手动指定的站台 id」，0 = 跟随附近站台。 */
    public static class TileEntityTrdmStationSign extends BlockEntity {

        private long platformId;

        public TileEntityTrdmStationSign(BlockPos pos, BlockState state) {
            super(Trdm.STATION_SIGN_BE, pos, state);
        }

        public long getPlatformId() {
            return platformId;
        }

        public void setPlatformId(long platformId) {
            if (this.platformId != platformId) {
                this.platformId = platformId;
                setChanged();
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.getChunkSource().blockChanged(worldPosition);
                }
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putLong(KEY_PLATFORM_ID, platformId);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            platformId = tag.getLong(KEY_PLATFORM_ID);
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

    /** 牌面朝向。 */
    public static Direction facing(BlockState state) {
        return IBlock.getStatePropertySafe(state, FACING);
    }
}
