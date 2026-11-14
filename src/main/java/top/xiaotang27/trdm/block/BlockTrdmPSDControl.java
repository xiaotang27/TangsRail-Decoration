package top.xiaotang27.trdm.block;

import mtr.Items;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.client.TrdmControlClient;
import top.xiaotang27.trdm.control.PsdDoorControl;
import top.xiaotang27.trdm.control.PsdManualHold;

import java.util.List;

/**
 * 屏蔽门集控装置：一台用于手动开关某个站台全部屏蔽门的控制设备。
 * <ul>
 */
public class BlockTrdmPSDControl extends BaseEntityBlock {

    /** 选中的站台 id（0 = 还没选）。 */
    public static final String KEY_PLATFORM_ID = "platform_id";
    /** 是否处于「手动开门并保持」状态。 */
    public static final String KEY_MANUAL_OPEN = "manual_open";
    /** 是否处于「屏蔽门隔离」状态（站台门只受本装置控制，MTR 完全影响不到）。 */
    public static final String KEY_ISOLATED = "isolated";

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE_NORTH = Shapes.box(2.0 / 16, 0, 9.0 / 16,
            14.0 / 16, 9.0 / 16, 1.0);
    private static final VoxelShape SHAPE_SOUTH = Shapes.box(2.0 / 16, 0, 0,
            14.0 / 16, 9.0 / 16, 7.0 / 16);
    private static final VoxelShape SHAPE_EAST = Shapes.box(0, 0, 2.0 / 16,
            7.0 / 16, 9.0 / 16, 14.0 / 16);
    private static final VoxelShape SHAPE_WEST = Shapes.box(9.0 / 16, 0, 2.0 / 16,
            1.0, 9.0 / 16, 14.0 / 16);

    public BlockTrdmPSDControl() {
        super(Properties.of().strength(2F).noOcclusion());
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
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos,
                                        CollisionContext context) {
        return getShape(state, world, pos, context);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityTrdmPSDControl(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        final boolean holdingBrush = isHoldingBrush(player);

        if (world.isClientSide) {
            // 刷子右键 → 客户端直接开选择界面（站台列表客户端本来就有，不用问服务端）
            if (holdingBrush && world.getBlockEntity(pos) instanceof TileEntityTrdmPSDControl control) {
                TrdmControlClient.openPlatformSelect(pos, control.getPlatformId(), control.isIsolated(),
                        TrdmControlClient.SelectMode.CONTROL_DEVICE);
            }
            return InteractionResult.SUCCESS;
        }

        if (holdingBrush) {
            // 选站台是客户端 + 一条 C2S 包的事，服务端这里什么都不用做
            return InteractionResult.SUCCESS;
        }
        if (!(world instanceof ServerLevel serverLevel) || !(world.getBlockEntity(pos) instanceof TileEntityTrdmPSDControl control)) {
            return InteractionResult.PASS;
        }

        final long platformId = control.getPlatformId();
        if (platformId == 0) {
            player.displayClientMessage(Component.translatable("gui.trdm.psd_control.no_platform"), true);
            return InteractionResult.CONSUME;
        }

        final String label = PsdDoorControl.platformLabel(serverLevel, platformId);
        final String platformName = label == null ? String.valueOf(platformId) : label;

        final TileEntityTrdmPSDControl.ManualResult result = control.toggleManualOpen(serverLevel);
        if (result.platformMissing()) {
            player.displayClientMessage(Component.translatable("gui.trdm.psd_control.missing_platform"), true);
        } else if (result.doors() == 0) {
            player.displayClientMessage(Component.translatable("gui.trdm.psd_control.no_doors", platformName), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    result.open() ? "gui.trdm.psd_control.held_open" : "gui.trdm.psd_control.released",
                    platformName, result.doors()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 拆掉装置时把手动开的门交还给列车自动逻辑，免得门永远开着。
        // 这里走 PsdManualHold 而不是方块实体：onRemove 时 BE 可能已经先被移除了。
        if (!state.is(newState.getBlock()) && world instanceof ServerLevel serverLevel) {
            final boolean wasManuallyOpen = world.getBlockEntity(pos) instanceof TileEntityTrdmPSDControl control
                    && control.isManualOpen();
            PsdManualHold.release(serverLevel, pos, wasManuallyOpen);
        }
        super.onRemove(state, world, pos, newState, movedByPiston);
    }

    /** 拿刷子右键才是「配置」，空手右键是「开关门」。刷子 = MTR 的 mtr:brush。 */
    public static boolean isHoldingBrush(Player player) {
        final Item brush = Items.BRUSH.get();
        return player.getMainHandItem().is(brush) || player.getOffhandItem().is(brush);
    }

    /**
     * 集控装置的方块实体：存「选中的站台 id」+「手动保持开启」+「屏蔽门隔离」，
     * 全都会 sync 给客户端（界面里能显示当前状态）。
     * 门列表不放这里，放在 {@link PsdManualHold} 里 —— 那里不依赖本区块是否在 tick。
     */
    public static class TileEntityTrdmPSDControl extends BlockEntity {

        private long platformId;
        private boolean manualOpen;
        private boolean isolated;

        public TileEntityTrdmPSDControl(BlockPos pos, BlockState state) {
            super(Trdm.PSD_CONTROL_BE, pos, state);
        }

        public long getPlatformId() {
            return platformId;
        }

        public boolean isManualOpen() {
            return manualOpen;
        }

        public boolean isIsolated() {
            return isolated;
        }

        /**
         * 换站台。先把上一个站台的门交还自动逻辑（手动开的门会关回去），
         * 然后清掉手动/隔离状态 —— 新站台要从干净状态开始配。
         */
        public void setPlatformId(ServerLevel world, long platformId) {
            if (this.platformId == platformId) {
                return;
            }
            PsdManualHold.release(world, worldPosition, manualOpen);
            this.platformId = platformId;
            this.manualOpen = false;
            this.isolated = false;
            setChanged();
            sync();
        }

        /**
         * 空手右键：没在手动开门 → 开门并保持；已经在保持 → 关掉并交还自动逻辑。
         */
        public ManualResult toggleManualOpen(ServerLevel world) {
            if (manualOpen) {
                manualOpen = false;
                setChanged();
                sync();
                // 隔离还开着的话就继续压着「关」，否则整条交还 MTR
                final int doors = isolated ? applyHold(world, false) : PsdManualHold.release(world, worldPosition, true);
                return new ManualResult(false, doors, false);
            }

            if (platformId == 0) {
                return new ManualResult(false, 0, true);
            }
            final List<BlockPos> doors = PsdDoorControl.findDoors(world, platformId);
            if (doors == null) {
                return new ManualResult(false, 0, true);
            }
            if (doors.isEmpty()) {
                return new ManualResult(false, 0, false);
            }
            manualOpen = true;
            setChanged();
            sync();
            return new ManualResult(true, PsdManualHold.hold(world, worldPosition, platformId, doors, true), false);
        }

        /**
         * 屏蔽门隔离开关。隔离开着时这个站台的门只受本装置控制：
         * 手动开着就一直开，手动关着（或没手动开）就一直关，MTR 的列车逻辑完全改不动。
         */
        public boolean toggleIsolated(ServerLevel world) {
            isolated = !isolated;
            setChanged();
            sync();
            applyHold(world, false);
            return isolated;
        }

        /**
         * 按当前状态重新压一次门：
         * 手动开 / 隔离 任一开着就进保持表（目标值 = 手动开 ? 开 : 关），否则退出保持表。
         */
        private int applyHold(ServerLevel world, boolean closeDoorsWhenReleasing) {
            if (!manualOpen && !isolated) {
                return PsdManualHold.release(world, worldPosition, closeDoorsWhenReleasing);
            }
            final List<BlockPos> doors = PsdDoorControl.findDoors(world, platformId);
            if (doors == null || doors.isEmpty()) {
                return PsdManualHold.release(world, worldPosition, closeDoorsWhenReleasing);
            }
            return PsdManualHold.hold(world, worldPosition, platformId, doors, manualOpen);
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            tag.putLong(KEY_PLATFORM_ID, platformId);
            tag.putBoolean(KEY_MANUAL_OPEN, manualOpen);
            tag.putBoolean(KEY_ISOLATED, isolated);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            platformId = tag.getLong(KEY_PLATFORM_ID);
            manualOpen = tag.getBoolean(KEY_MANUAL_OPEN);
            isolated = tag.getBoolean(KEY_ISOLATED);
            // 区块重新加载后把保持状态重新登记上（门列表下一个全局 tick 自己扫）
            if ((manualOpen || isolated) && level instanceof ServerLevel serverLevel && platformId != 0) {
                PsdManualHold.register(serverLevel, worldPosition, platformId, manualOpen);
            }
        }

        @Override
        public CompoundTag getUpdateTag() {
            return saveWithoutMetadata();
        }

        @Override
        public Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }

        private void sync() {
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().blockChanged(worldPosition);
            }
        }

        /**
         * @param open            操作后是不是「手动保持开启」
         * @param doors           这次操作动了几扇门
         * @param platformMissing 站台已经不存在了
         */
        public record ManualResult(boolean open, int doors, boolean platformMissing) {
        }
    }
}
