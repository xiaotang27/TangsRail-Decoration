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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import org.jetbrains.annotations.Nullable;
import top.xiaotang27.trdm.Trdm;

/**
 * PIS 乘客信息屏：外壳用模型的 {@code pids_rv}（立杆 + 两块黑挡板），
 * 屏幕是贴在挡板外表面上的动态贴图（正面 + 背面各一块，见
 * {@link top.xiaotang27.trdm.render.RenderTrdmPis}）。
 * <p>
 * 数据：到站列表走 {@code ClientData.SCHEDULES_FOR_PLATFORM}，见
 * {@link top.xiaotang27.trdm.render.TrdmPisData}。
 */
public class BlockTrdmPis extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 手动选的站台 id（0 = 自动取最近站台）。 */
    public static final String KEY_PLATFORM_ID = "platform_id";
    /** 每块屏幕自己的跑马灯文字。 */
    public static final String KEY_TICKER = "ticker";
    /** 是否显示广告区。 */
    public static final String KEY_ADS_ENABLED = "ads_enabled";
    /** 广告覆盖设置（顺序 + 启用 + 时长）。 */
    public static final String KEY_ADS = "ads";

    /** 屏幕宽：模型的一半 12 像素，复制一份镜像拼起来 = 24 像素 = 1.5 格。 */
    public static final float PANEL_WIDTH = 24F / 16F;
    /** 屏幕高：模型挡板 {@code y = -3..11}，14 像素 = 14/16 格（24:14 ≈ 16:9）。 */
    public static final float PANEL_HEIGHT = 14F / 16F;
    /** 面板平面离方块中心的距离（模型挡板外表面 {@code x = 6} / {@code x = 10}）＝ 2/16 格。 */
    public static final float PANEL_PLANE = 2F / 16F;
    /** 面板中心离方块底面的高度（模型 {@code y = 4}）。 */
    public static final float PANEL_CENTRE_Y = 4F / 16F;
    /** 默认跑马灯文字。 */
    public static final String DEFAULT_TICKER = "欢迎使用TangsRail Decoration 塘铁装饰模组。      (C)TRTIMC 2026";

    public BlockTrdmPis() {
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

    /**
     * 外壳也由方块实体渲染器画（模型只做了一半，渲染器要把它复制一份拼成完整屏幕），
     * 所以原版模型渲染器这里不再出图。
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * 碰撞箱：整块屏占<b>两格</b>，所以每格把沿横轴（挡板宽度方向）的整格都占满，
     * 厚度取挡板本身（模型 x 6..10），高到挡板顶 y = 11。两格拼起来就是一条两格宽的碰撞箱。
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return boxFor(IBlock.getStatePropertySafe(state, FACING), 6, 0, 0, 10, 11, 16);
    }

    private boolean isPis(BlockGetter world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() == this;
    }

    /** 屏幕（观众面向屏幕时）的右手方向，也就是两格拼接的方向。 */
    public static Direction rightOf(Direction facing) {
        return switch (facing) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    /** 放下一格就顺手把拼成两格宽的另一格放上去（屏幕要占两格）。 */
    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        if (world.isClientSide) {
            return;
        }
        final BlockPos partner = pos.relative(rightOf(IBlock.getStatePropertySafe(state, FACING)));
        if (world.getBlockState(partner).canBeReplaced()) {
            world.setBlock(partner, state, Block.UPDATE_ALL);
        }
    }

    /** 拆一格就把配对的另一格一起拆掉（只掉一份）。 */
    @Override
    public void playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        if (!world.isClientSide) {
            final Direction right = rightOf(IBlock.getStatePropertySafe(state, FACING));
            for (final Direction direction : new Direction[]{right, right.getOpposite()}) {
                final BlockPos partner = pos.relative(direction);
                if (isPis(world, partner)) {
                    world.removeBlock(partner, false);
                }
            }
        }
        super.playerWillDestroy(world, pos, state, player);
    }

    /** 模型坐标（像素）里的盒子，按 facing 的模型旋转角度转成世界坐标的碰撞箱。 */
    private static VoxelShape boxFor(Direction facing, double x1, double y1, double z1,
                                     double x2, double y2, double z2) {
        final double radians = Math.toRadians(modelRotation(facing));
        final double cos = Math.round(Math.cos(radians));
        final double sin = Math.round(Math.sin(radians));
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (final double x : new double[]{x1, x2}) {
            for (final double z : new double[]{z1, z2}) {
                final double dx = x - 8D;
                final double dz = z - 8D;
                final double rotatedX = dx * cos - dz * sin + 8D;
                final double rotatedZ = dx * sin + dz * cos + 8D;
                minX = Math.min(minX, rotatedX);
                maxX = Math.max(maxX, rotatedX);
                minZ = Math.min(minZ, rotatedZ);
                maxZ = Math.max(maxZ, rotatedZ);
            }
        }
        return Block.box(minX, y1, minZ, maxX, y2, maxZ);
    }

    /**
     * 方块朝向 → 模型在 blockstates 里的 {@code y} 角度。
     * <p>
     * 模型（{@code pids_rv.json}）的屏幕面板朝模型 ±X，这里要把它转到方块 facing：
     * north = 270、east = 0、south = 90、west = 180（和 {@code blockstates/pis.json} 一一对应）。
     */
    public static int modelRotation(Direction facing) {
        return switch (facing) {
            case NORTH -> 270;
            case SOUTH -> 90;
            case WEST -> 180;
            default -> 0;
        };
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityTrdmPis(pos, state);
    }

    @Override
    public Item asItem() {
        return Trdm.PIS_ITEM;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 刷子右键 → PIS 设置界面（资源包设置 / 跑马文字 / 广告图设置）
        if (BlockTrdmPSDControl.isHoldingBrush(player)) {
            // 整块屏由两格里的左边那格负责渲染，设置也要写到那一格去，不然改了没反应
            final BlockPos target = renderedPos(world, pos, IBlock.getStatePropertySafe(state, FACING));
            if (world.isClientSide && world.getBlockEntity(target) instanceof TileEntityTrdmPis) {
                top.xiaotang27.trdm.client.TrdmControlClient.openPisSettings(target);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** 两格拼成的屏由哪一格负责画：有左邻居就交给左邻居，否则自己画。 */
    public static BlockPos renderedPos(BlockGetter world, BlockPos pos, Direction facing) {
        final Direction right = rightOf(facing);
        final BlockPos left = pos.relative(right.getOpposite());
        if (world.getBlockState(left).getBlock() instanceof BlockTrdmPis) {
            return left;
        }
        return pos;
    }

    /** 方块实体：站台、跑马灯文字、广告开关（后续还有广告覆盖项）。 */
    public static class TileEntityTrdmPis extends BlockEntity {

        private long platformId;
        private String ticker = DEFAULT_TICKER;
        private boolean adsEnabled = true;
        /** 广告播放顺序（空 = 按 pis_ads.json 的顺序）。 */
        private final java.util.List<String> adOrder = new java.util.ArrayList<>();
        private final java.util.Map<String, Boolean> adEnabled = new java.util.HashMap<>();
        private final java.util.Map<String, Integer> adTimes = new java.util.HashMap<>();

        public TileEntityTrdmPis(BlockPos pos, BlockState state) {
            this(Trdm.PIS_BE, pos, state);
        }

        protected TileEntityTrdmPis(BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        public long getPlatformId() {
            return platformId;
        }

        public String getTicker() {
            return ticker == null || ticker.isEmpty() ? DEFAULT_TICKER : ticker;
        }

        public boolean isAdsEnabled() {
            return adsEnabled;
        }

        public java.util.List<String> getAdOrder() {
            return adOrder;
        }

        public java.util.Map<String, Boolean> getAdEnabled() {
            return adEnabled;
        }

        public java.util.Map<String, Integer> getAdTimes() {
            return adTimes;
        }

        public void setPlatformId(long platformId) {
            if (this.platformId != platformId) {
                this.platformId = platformId;
                markSynced();
            }
        }

        public void setTicker(String ticker) {
            final String safe = ticker == null ? "" : ticker;
            if (!safe.equals(this.ticker)) {
                this.ticker = safe;
                markSynced();
            }
        }

        public void setAdsEnabled(boolean adsEnabled) {
            if (this.adsEnabled != adsEnabled) {
                this.adsEnabled = adsEnabled;
                markSynced();
            }
        }

        /** 整份广告覆盖设置（顺序 + 启用 + 每张的时长）。 */
        public void setAds(java.util.List<String> order, java.util.Map<String, Boolean> enabled,
                           java.util.Map<String, Integer> times) {
            adOrder.clear();
            adOrder.addAll(order);
            adEnabled.clear();
            adEnabled.putAll(enabled);
            adTimes.clear();
            adTimes.putAll(times);
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
            tag.putLong(KEY_PLATFORM_ID, platformId);
            tag.putString(KEY_TICKER, ticker);
            tag.putBoolean(KEY_ADS_ENABLED, adsEnabled);

            final net.minecraft.nbt.ListTag ads = new net.minecraft.nbt.ListTag();
            for (final String id : adOrder) {
                final CompoundTag entry = new CompoundTag();
                entry.putString("id", id);
                entry.putBoolean("enabled", !Boolean.FALSE.equals(adEnabled.get(id)));
                entry.putInt("time", adTimes.getOrDefault(id, 0));
                ads.add(entry);
            }
            tag.put(KEY_ADS, ads);
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            platformId = tag.getLong(KEY_PLATFORM_ID);
            ticker = tag.contains(KEY_TICKER) ? tag.getString(KEY_TICKER) : DEFAULT_TICKER;
            adsEnabled = !tag.contains(KEY_ADS_ENABLED) || tag.getBoolean(KEY_ADS_ENABLED);
            adOrder.clear();
            adEnabled.clear();
            adTimes.clear();
            final net.minecraft.nbt.ListTag ads = tag.getList(KEY_ADS, 10);
            for (int index = 0; index < ads.size(); index++) {
                final CompoundTag entry = ads.getCompound(index);
                final String id = entry.getString("id");
                if (id.isEmpty()) {
                    continue;
                }
                adOrder.add(id);
                adEnabled.put(id, entry.getBoolean("enabled"));
                adTimes.put(id, entry.getInt("time"));
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
    }
}
