package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.block.BlockPSDAPGDoorBase;
import mtr.block.BlockPSDAPGGlassBase;
import mtr.block.BlockPSDAPGGlassEndBase;
import mtr.block.IBlock;
import mtr.client.ClientCache;
import mtr.client.ClientData;
import mtr.client.IDrawing;
import mtr.data.IGui;
import mtr.data.Platform;
import mtr.data.Station;
import mtr.mappings.BlockEntityRendererMapper;
import mtr.mappings.UtilitiesClient;
import mtr.render.RenderRouteBase;
import mtr.render.RenderTrains;
import mtr.render.StoredMatrixTransformations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import mtr.render.MoreRenderLayers;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.block.BlockTrdmPSDGlass;
import top.xiaotang27.trdm.block.BlockTrdmPSDDoor;
import top.xiaotang27.trdm.block.BlockTrdmPSDTop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门楣渲染器（重写版，不再继承 MTR 的 RenderRouteBase，只沿用它的几何与坐标约定）。
 * <p>
 */
public class RenderTrdmPSDTop extends BlockEntityRendererMapper<BlockTrdmPSDTop.TileEntityTrdmPSDTop> implements IGui, IBlock {

    private static final float Z_OFFSET = (2 - SMALL_OFFSET_16) / 16F;
    private static final float TOP_PADDING = 7.5F / 16F;
    private static final float BOTTOM_PADDING = 1.5F / 16F;
    private static final float SIDE_PADDING = 0.125F / 16F;
    private static final float COLOR_STRIP_START = 14.5F / 16F;
    private static final float COLOR_STRIP_END = 15F / 16F;
    /**
     * 色带向下延伸到的位置（模型坐标 y 越大越靠下，见渲染时的 rotateZ(180)）：
     * 16/16 = 吊板方块的下沿，所以色带从原来的 0.5/16 厚变成一直铺到底部（1.5/16）。
     */
    private static final float COLOR_STRIP_BOTTOM = 16F / 16F;

    // ===== 玻璃上的装饰带 =====

    /** 装饰带高度（格）。 */
    private static final float BAND_HEIGHT = TrdmDecalBand.HEIGHT_BLOCKS;
    /**
     * 装饰带的竖直中心：门楣占吊板坐标系的 0~1，上面那格玻璃 1~2、下面那格玻璃 2~3，
     * 所以两格玻璃的竖直中缝正好在 2，也就是「玻璃门的 y 轴中心」。
     */
    private static final float BAND_CENTRE_Y = 2F;
    /**
     * 装饰带整体再往上抬多少（格）：从两格玻璃的中缝往上让一点，视觉上更贴门楣下沿。
     */
    private static final float BAND_Y_LIFT = 2F / 16F;
    /**
     * 玻璃板两个面到方块中心、沿门楣 facing 方向量的距离：
     * 6/16 那面朝站台（facing 那一侧），4/16 那面朝轨道。
     */
    private static final float GLASS_SURFACE_PLATFORM = 6F / 16F;
    private static final float GLASS_SURFACE_TRACK = 4F / 16F;
    /** 往各自那一侧再让一丝，避免和玻璃面共面打架。 */
    private static final float BAND_Z_NUDGE = 0.004F;
    /**
     * 装饰带两端各往里收多少（格）：玻璃段两端是框，收到框内侧才像贴在玻璃面上，
     * 而不是顶着框一直铺到边。
     */
    private static final float BAND_END_INSET = 1F / 16F;

    private static final boolean TRANSPARENT_WHITE = true;

    private static final float END_FRONT_OFFSET = 1 / (Mth.SQRT_OF_TWO * 16);
    private static final float BOTTOM_DIAGONAL_OFFSET = ((float) Math.sqrt(3) - 1) / 32;
    private static final float ROOT_TWO_SCALED = Mth.SQRT_OF_TWO / 16;
    private static final float BOTTOM_END_DIAGONAL_OFFSET = END_FRONT_OFFSET - BOTTOM_DIAGONAL_OFFSET / Mth.SQRT_OF_TWO;

    public RenderTrdmPSDTop(BlockEntityRenderDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(BlockTrdmPSDTop.TileEntityTrdmPSDTop entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final Level world = entity.getLevel();
        if (world == null) {
            return;
        }

        final BlockPos pos = entity.getBlockPos();
        final BlockState state = world.getBlockState(pos);
        final Direction facing = IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING);

        final StoredMatrixTransformations storedMatrixTransformations = new StoredMatrixTransformations();
        storedMatrixTransformations.add(matricesNew -> {
            matricesNew.translate(0.5 + pos.getX(), pos.getY(), 0.5 + pos.getZ());
            UtilitiesClient.rotateYDegrees(matricesNew, -facing.toYRot());
        });

        // 端头交给 trdm:block/psd_top_end_square_* 模型，不再用 MTR 的斜角封板画法

        if (RenderTrains.shouldNotRender(pos, RenderTrains.maxTrainRenderDistance, null)) {
            return;
        }

        final long platformId = entity.getPlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE);
        if (platformId == 0) {
            return;
        }

        final Block blockBelow = world.getBlockState(pos.below()).getBlock();
        final boolean doorBelow = blockBelow instanceof BlockPSDAPGDoorBase;
        final boolean glassBelow = blockBelow instanceof BlockPSDAPGGlassBase && !(blockBelow instanceof BlockPSDAPGGlassEndBase);

        storedMatrixTransformations.add(matricesNew -> {
            matricesNew.translate(0, 1, 0);
            UtilitiesClient.rotateZDegrees(matricesNew, 180);
            matricesNew.translate(-0.5, -getAdditionalOffset(state), Z_OFFSET);
        });

        final BlockTrdmPSDTop.EnumPersistent persistent = IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.PERSISTENT);

        final GroupKind groupKind = glassBelow ? GroupKind.GLASS : GroupKind.ANY;
        final int leftBlocks;
        final int rightBlocks;
        if (doorBelow) {
            // 门只有两格宽：站名必须居中在「一扇门」上，而不是挨着的整排门上
            leftBlocks = getDoorRunBlocks(world, pos, facing, true);
            rightBlocks = getDoorRunBlocks(world, pos, facing, false);
        } else {
            leftBlocks = getRunBlocks(world, pos, facing, true, groupKind);
            rightBlocks = getRunBlocks(world, pos, facing, false, groupKind);
        }

        final boolean stationNameMode;
        final boolean routeMapMode;
        if (persistent == BlockTrdmPSDTop.EnumPersistent.ARROW) {
            stationNameMode = true;
            routeMapMode = false;
        } else if (persistent == BlockTrdmPSDTop.EnumPersistent.ROUTE) {
            stationNameMode = false;
            routeMapMode = true;
        } else if (persistent == BlockTrdmPSDTop.EnumPersistent.BLANK) {
            stationNameMode = false;
            routeMapMode = false;
        } else {
            stationNameMode = doorBelow;
            routeMapMode = glassBelow;
        }

        final int color = RenderRouteBase.getShadingColor(facing, ARGB_WHITE);
        final Direction quadFacing = facing.getOpposite();

        if (stationNameMode) {
            renderStationName(storedMatrixTransformations, world, pos, platformId, state, leftBlocks, rightBlocks, quadFacing, color, light);
        }
        // 线路图始终由「这一段最左那一格」整张画，不看 SIDE_EXTENDED（否则段中间会有格子不画、把图切掉）
        if (routeMapMode) {
            renderRouteMap(storedMatrixTransformations, world, pos, platformId, state, facing, leftBlocks, rightBlocks, quadFacing, color, light);
        }

        // 端门格不绘制装饰条（灯由门楣模型/渲染负责）
        if (!BlockTrdmPSDGlass.isEndGate(world, pos.below())) {
            renderColorStrip(storedMatrixTransformations, platformId, state, quadFacing, color, light);
        }

        // 玻璃上的装饰带：按「合并后的整块玻璃」分段 —— 一段 = 刷子连起来的一组玻璃，
        // 由这一组的左端那一格画完整一条；整段都没合并过的玻璃视同合并的一整块。
        // 端头玻璃不画。
        if (glassBelow) {
            final int groupRightBlocks = getBandRightBlocks(world, pos, facing);
            if (groupRightBlocks >= 0) {
                renderDecalBand(pos, platformId, state, quadFacing, color, light, groupRightBlocks);
            }
        }
    }

    // ===== 玻璃上的装饰带 =====

    /**
     * 装饰带：线路色底 + 两端人字纹 + 正中一张「文字 + logo」图，画在玻璃两格的中缝高度上。
     * <p>
     */
    private static void renderDecalBand(BlockPos pos, long platformId, BlockState state, Direction quadFacing,
                                        int color, int light, int rightBlocks) {
        final ClientCache.PlatformRouteDetails routeDetails = getPrimaryRoute(platformId);
        if (routeDetails == null) {
            return;
        }

        final float runWidth = rightBlocks + 1;
        // 两端各收一点：收完的宽度才是装饰带真正的长度，贴图宽高比也按这个算
        final float startX = BAND_END_INSET;
        final float endX = runWidth - BAND_END_INSET;
        final float bandWidth = Math.max(BAND_HEIGHT, endX - startX);
        final boolean arrowLeft = (IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.ARROW_DIRECTION) & 0b01) == 0;
        // 装饰线样式（这一格门楣选的）：0 = 有底色带 + slogan，1 = 只有人字纹、人字染线路色
        final int bandStyle = BlockTrdmPSDTop.bandStyle(state);
        // 两面共用同一张原图（中央图文不镜像）：人字指向行进方向，slogan 保持原样
        final ResourceLocation texture = TrdmHeaderTextures.getDecalBand(bandStyle, routeDetails.routeColor,
                bandWidth / BAND_HEIGHT, arrowLeft, false);
        if (texture == null) {
            return;
        }

        final Direction blockFacing = quadFacing.getOpposite();
        // 朝站台的方向（站台在 facing 那一侧）与沿玻璃段的跑向
        final Vec3 toPlatform = Vec3.atLowerCornerOf(blockFacing.getNormal());
        final Vec3 runDir = Vec3.atLowerCornerOf(blockFacing.getClockWise().getNormal());
        final Vec3 centre = Vec3.atCenterOf(pos);

        // 玻璃两面在世界中的位置：从方块中心沿「朝站台」方向量，6/16 是站台那面、4/16 是轨道那面
        final Vec3 platformPlane = toPlatform.scale(GLASS_SURFACE_PLATFORM + BAND_Z_NUDGE);
        final Vec3 trackPlane = toPlatform.scale(GLASS_SURFACE_TRACK - BAND_Z_NUDGE);

        // 竖直位置：门楣方块上沿往下一格是上玻璃、再下一格是下玻璃，中缝在门楣顶面往下 2 格处；
        // 再往上抬 BAND_Y_LIFT 一点
        final double yCentre = pos.getY() + 1 - BAND_CENTRE_Y + BAND_Y_LIFT;
        final float yTop = (float) (yCentre + BAND_HEIGHT / 2D);
        final float yBottom = (float) (yCentre - BAND_HEIGHT / 2D);
        // 沿跑向：吊板坐标 x∈[0,1] 覆盖本格，x = startX / endX 就是装饰带两头
        final Vec3 runStart = runDir.scale(startX - 0.5);
        final Vec3 runEnd = runDir.scale(endX - 0.5);

        final int trackColor = RenderRouteBase.getShadingColor(quadFacing, ARGB_WHITE);

        // 一张双面印刷的不干胶：两面同时画，各朝外，贴图是同一张原图（不镜像）
        RenderTrains.scheduleRender(texture, false, RenderType::entityCutout, (matrices, vertexConsumer) -> {
            final Matrix4f pose = matrices.last().pose();
            // 站台面：印在朝站台那面，法线朝站台
            emitBandQuad(pose, vertexConsumer, centre, runStart, runEnd, platformPlane, yTop, yBottom,
                    toPlatform, color, light);
            // 轨道面：印在朝轨道那面，法线朝轨道
            emitBandQuad(pose, vertexConsumer, centre, runStart, runEnd, trackPlane, yTop, yBottom,
                    toPlatform.scale(-1), trackColor, light);
        });
    }

    /**
     * 自己写四个顶点画装饰带的一个面。绕序按右手定则现场判定，保证这一面朝着 {@code outward}。
     */
    private static void emitBandQuad(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 centre, Vec3 runStart, Vec3 runEnd,
                                     Vec3 planeOffset, float yTop, float yBottom,
                                     Vec3 outward, int argb, int light) {
        // 四角（世界坐标，相对方块原点已在 centre 里算好）
        final double startX = centre.x + runStart.x + planeOffset.x;
        final double endX = centre.x + runEnd.x + planeOffset.x;
        final double startZ = centre.z + runStart.z + planeOffset.z;
        final double endZ = centre.z + runEnd.z + planeOffset.z;

        final double[][] corners = {
                {startX, yTop, startZ, 0, 0},
                {endX, yTop, endZ, 1, 0},
                {endX, yBottom, endZ, 1, 1},
                {startX, yBottom, startZ, 0, 1}
        };

        // 右手定则：(B−A)×(C−A) 要和朝外法线同向，否则把顺序倒过来
        final double[] ab = {corners[1][0] - corners[0][0], corners[1][1] - corners[0][1], corners[1][2] - corners[0][2]};
        final double[] ac = {corners[2][0] - corners[0][0], corners[2][1] - corners[0][1], corners[2][2] - corners[0][2]};
        final double nx = ab[1] * ac[2] - ab[2] * ac[1];
        final double ny = ab[2] * ac[0] - ab[0] * ac[2];
        final double nz = ab[0] * ac[1] - ab[1] * ac[0];
        final boolean reversed = nx * outward.x + ny * outward.y + nz * outward.z < 0;

        final int order = reversed ? -1 : 1;
        final int first = reversed ? 3 : 0;
        for (int i = 0; i < 4; i++) {
            final double[] corner = corners[Math.floorMod(first + order * i, 4)];
            vertexConsumer.vertex(pose,
                            (float) corner[0], (float) corner[1], (float) corner[2])
                    .color(argb)
                    .uv((float) corner[3], (float) corner[4])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal((float) outward.x, (float) outward.y, (float) outward.z)
                    .endVertex();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(BlockTrdmPSDTop.TileEntityTrdmPSDTop blockEntity) {
        return true;
    }

    // ===== 站名吊板 =====

    private void renderStationName(StoredMatrixTransformations storedMatrixTransformations, Level world, BlockPos pos, long platformId, BlockState state, int leftBlocks, int rightBlocks, Direction facing, int color, int light) {
        final String stationName = getStationNameForRendering(platformId);
        if (stationName == null) {
            // 站台没开站名显示模式（PSD display mode 不是 1/2）时退回方向箭头，和 MTR 一致
            renderDirectionArrow(storedMatrixTransformations, platformId, state, leftBlocks, rightBlocks, facing, color, light);
            return;
        }

        final float width = leftBlocks + rightBlocks + 1 - SIDE_PADDING * 2;
        final float height = 1 - TOP_PADDING - BOTTOM_PADDING;
        final List<TrdmHeaderTextures.Badge> badges = getBadges(platformId);
        final ResourceLocation texture = TrdmHeaderTextures.get(platformId, stationName, badges, width / height);
        if (texture == null) {
            return;
        }
        scheduleRunQuad(storedMatrixTransformations, texture, leftBlocks, rightBlocks, facing, color, light);
    }

    /**
     * 换乘站 → 本站台所停靠线路的标志列表；非换乘站 → 空表（只画站名）。
     */
    private static List<TrdmHeaderTextures.Badge> getBadges(long platformId) {
        final Station station = ClientData.DATA_CACHE.platformIdToStation.get(platformId);
        if (station == null) {
            return List.of();
        }

        final Map<Integer, ClientCache.ColorNameTuple> stationRoutes = ClientData.DATA_CACHE.stationIdToRoutes.get(station.id);
        final List<ClientCache.PlatformRouteDetails> platformRoutes = ClientData.DATA_CACHE.requestPlatformIdToRoutes(platformId);
        final boolean interchange = (stationRoutes != null && stationRoutes.size() > 1) || platformRoutes.size() > 1;
        if (!interchange) {
            return List.of();
        }

        final List<ClientCache.PlatformRouteDetails> sorted = new ArrayList<>(platformRoutes);
        sorted.sort(Comparator.comparing(details -> details.routeName, String.CASE_INSENSITIVE_ORDER));

        final List<TrdmHeaderTextures.Badge> badges = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (final ClientCache.PlatformRouteDetails details : sorted) {
            final String[] nameParts = splitRouteName(details.routeName);
            final String key = details.routeColor + "|" + nameParts[0] + "|" + nameParts[1];
            if (seen.add(key)) {
                badges.add(new TrdmHeaderTextures.Badge(details.routeColor, nameParts[0], nameParts[1]));
            }
        }
        return badges;
    }

    private static String[] splitRouteName(String routeName) {
        final String name = routeName == null ? "" : routeName.trim();
        final String[] parts = name.split("\\|");
        if (parts.length == 1) {
            return new String[]{name, ""};
        }
        final StringBuilder english = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            final String part = parts[i].trim();
            if (!part.isEmpty()) {
                if (english.length() > 0) {
                    english.append(' ');
                }
                english.append(part);
            }
        }
        // 线路标志的英文保持原样大小写（MTR 里填的是「Line 2」就画「Line 2」），不强行转大写
        return new String[]{parts[0].trim(), english.toString()};
    }

    /**
     * 门上恒定显示本站站名（不再看 MTR 的 PSD Display Mode：模式 0「方向箭头」也会显示站名，
     * 因为本 mod 的门楣按需求就是站名吊板）。只有站台没分配到车站时才退回方向箭头。
     */
    private static String getStationNameForRendering(long platformId) {
        final Station station = ClientData.DATA_CACHE.platformIdToStation.get(platformId);
        return station == null ? null : station.name;
    }

    // ===== 玻璃上的线路图 / 站点信息（照抄 MTR RenderRouteBase 的选型）=====

    /** 一组滑动门 = 2 格宽。 */
    private static final int DOOR_GROUP_BLOCKS = 2;

    /** 线路图比玻璃吊板宽时会盖到借来的滑动门吊板上，往前挪一丝避免共面打架。 */
    private static final float SPAN_Z_NUDGE = 0.002F;

    private void renderRouteMap(StoredMatrixTransformations storedMatrixTransformations, Level world, BlockPos pos, long platformId, BlockState state, Direction blockFacing, int leftBlocks, int rightBlocks, Direction facing, int color, int light) {
        // 玻璃门楣按分组奇偶交替：站点信息吊板 - 滑动门 - 线路图 - 滑动门 - 站点信息 …
        if (BlockTrdmPSDTop.isStationInfoPanel(world, pos, blockFacing)) {
            renderStationInfo(storedMatrixTransformations, platformId, state, leftBlocks, rightBlocks, facing, color, light);
            return;
        }
        // 这一段最左的那一格负责画整张线路图（每格都画会重复叠画）
        if (leftBlocks != 0) {
            return;
        }

        final ClientCache.PlatformRouteDetails routeDetails = getPrimaryRoute(platformId);
        if (routeDetails == null) {
            return;
        }
        final int arrowDirection = IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.ARROW_DIRECTION);
        final boolean arrowLeft = (arrowDirection & 0b01) == 0;
        final int stationCount = Math.max(1, routeDetails.stationDetails.size());

        final float height = 1 - TOP_PADDING - BOTTOM_PADDING;
        final float minimumWidth = TrdmRouteMap.minimumAspect(stationCount, arrowLeft) * height;
        final float runWidth = leftBlocks + rightBlocks + 1;
        final float runLeftX = -leftBlocks;
        final float runRightX = 1 + rightBlocks;

        // 站距压到最紧也放不进这块玻璃 → 借「右侧」一组滑动门的吊板（图例仍然钉在玻璃吊板右下角）
        float areaLeftX = runLeftX;
        float areaRightX = runRightX;
        if (minimumWidth > runWidth) {
            final int doorRight = getAdjacentGroupWidth(world, pos, facing, false, GroupKind.DOOR);
            areaRightX += Math.min(DOOR_GROUP_BLOCKS, Math.max(0, doorRight));
        }
        final float areaWidth = areaRightX - areaLeftX;
        // 无论吊板朝哪边、多宽，线路图都用同一个比例画：
        // 面积不够就把四边形画出可用范围（盖到旁边去），绝不缩图、绝不裁剪 —— 信息永远完整且一模一样
        final float minimumAspect = TrdmRouteMap.minimumAspect(stationCount, arrowLeft);
        final float requestedAspect = Math.max(areaWidth / height, minimumAspect);
        final TrdmHeaderTextures.TextureSize size = TrdmHeaderTextures.routeMapTextureSize(requestedAspect);
        final float usedWidth = size.aspect() * height;
        final float centreX = (areaLeftX + areaRightX) / 2F;
        final float quadLeftX = centreX - usedWidth / 2F;

        // 图例钉在玻璃吊板（不是借来的门）的右下角
        final float runLeftFraction = clamp01((runLeftX - quadLeftX) / usedWidth);
        final float runRightFraction = clamp01((quadLeftX + usedWidth - runRightX) / usedWidth);

        // 素材在渲染线程先读一次，后台烘焙线程只读缓存
        TrdmSignAssets.primeAll();
        final ResourceLocation texture = TrdmHeaderTextures.getRouteMapPanel(
                platformId, requestedAspect, arrowLeft, runLeftFraction, runRightFraction, routeDataSignature(routeDetails));
        if (texture != null) {
            scheduleSpanQuad(storedMatrixTransformations, texture, quadLeftX, quadLeftX + usedWidth, facing, color, light);
        }
    }

    /**
     * 整张线路图一个四边形：比一格宽（会盖到旁边滑动门的吊板上）也照画。
     * 稍微往前挪一点点（{@link #SPAN_Z_NUDGE}），免得和借来的那块吊板自己的贴图共面打架。
     */
    private static void scheduleSpanQuad(StoredMatrixTransformations storedMatrixTransformations, ResourceLocation texture,
                                         float x0, float x1, Direction facing, int color, int light) {
        RenderTrains.scheduleRender(texture, false, RenderTrains.QueuedRenderLayer.EXTERIOR, (matrices, vertexConsumer) -> {
            storedMatrixTransformations.transform(matrices);
            IDrawing.drawTexture(matrices, vertexConsumer,
                    x0, TOP_PADDING, SPAN_Z_NUDGE,
                    x1, 1 - BOTTOM_PADDING, SPAN_Z_NUDGE,
                    0, 0, 1, 1,
                    facing, color, light);
            matrices.popPose();
        });
    }

    private static float clamp01(float value) {
        return Math.max(0F, Math.min(1F, value));
    }

    /**
     * 站点信息吊板：箭头 +「开往 终点站」+「下一站 下一站名」。
     * <p>
     */
    private void renderStationInfo(StoredMatrixTransformations storedMatrixTransformations, long platformId, BlockState state, int leftBlocks, int rightBlocks, Direction facing, int color, int light) {
        final int arrowDirection = IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.ARROW_DIRECTION);
        final boolean arrowLeft = (arrowDirection & 0b01) == 0;

        final ClientCache.PlatformRouteDetails routeDetails = getPrimaryRoute(platformId);
        if (routeDetails == null) {
            return;
        }
        final List<ClientCache.PlatformRouteDetails.StationDetails> stations = routeDetails.stationDetails;
        if (stations.size() < 2) {
            return;
        }

        final int lastIndex = stations.size() - 1;
        final int currentIndex = Mth.clamp(routeDetails.currentStationIndex, 0, lastIndex);

        final float width = leftBlocks + rightBlocks + 1 - SIDE_PADDING * 2;
        final float height = 1 - TOP_PADDING - BOTTOM_PADDING;
        final int blocks = leftBlocks + rightBlocks + 1;

        // 本平台就是这条线的终点站（线路末站）：整块吊板改成居中的「终点站 / Terminal Station」。
        // 起点站（首站）不算，那边照常显示「开往 / 下一站」。
        if (currentIndex == lastIndex) {
            final ResourceLocation terminal = TrdmHeaderTextures.getTerminal(platformId, width / height);
            if (terminal != null) {
                scheduleRunQuad(storedMatrixTransformations, terminal, leftBlocks, rightBlocks, facing, color, light);
            }
            return;
        }

        final int nextIndex = Mth.clamp(currentIndex + 1, 0, lastIndex);
        final String destination = stations.get(lastIndex).stationName;
        final String nextStation = stations.get(nextIndex).stationName;
        final ResourceLocation texture = TrdmHeaderTextures.getStationInfo(platformId, destination, nextStation, arrowLeft, blocks, width / height);
        if (texture != null) {
            scheduleRunQuad(storedMatrixTransformations, texture, leftBlocks, rightBlocks, facing, color, light);
        }
    }

    /**
     * 线路数据指纹：站点增删、换乘线路变化、当前站、线路名/色一变指纹就变，
     * 线路图贴图缓存因此自动重做（不用重启、不用重新放置）。
     */
    private static String routeDataSignature(ClientCache.PlatformRouteDetails routeDetails) {
        final StringBuilder builder = new StringBuilder();
        builder.append(routeDetails.currentStationIndex).append('|')
                .append(routeDetails.routeColor).append('|')
                .append(routeDetails.routeName);
        for (final ClientCache.PlatformRouteDetails.StationDetails details : routeDetails.stationDetails) {
            builder.append(';').append(details.stationName);
            for (final ClientCache.ColorNameTuple tuple : details.interchangeRoutes) {
                builder.append(',').append(tuple.color).append('/').append(tuple.name);
            }
        }
        return Integer.toHexString(builder.toString().hashCode()) + "-" + routeDetails.stationDetails.size();
    }

    private static ClientCache.PlatformRouteDetails getPrimaryRoute(long platformId) {
        final List<ClientCache.PlatformRouteDetails> routes = ClientData.DATA_CACHE.requestPlatformIdToRoutes(platformId);
        if (routes.isEmpty()) {
            return null;
        }
        final List<ClientCache.PlatformRouteDetails> sorted = new ArrayList<>(routes);
        sorted.sort(Comparator.comparing(details -> details.routeName, String.CASE_INSENSITIVE_ORDER));
        return sorted.get(0);
    }

    private void renderDirectionArrow(StoredMatrixTransformations storedMatrixTransformations, long platformId, BlockState state, int leftBlocks, int rightBlocks, Direction facing, int color, int light) {
        final float width = leftBlocks + rightBlocks + 1 - SIDE_PADDING * 2;
        final float height = 1 - TOP_PADDING - BOTTOM_PADDING;
        scheduleRunQuad(storedMatrixTransformations, getDirectionArrowResource(platformId, state, width / height), leftBlocks, rightBlocks, facing, color, light);
    }

    private static ResourceLocation getDirectionArrowResource(long platformId, BlockState state, float aspectRatio) {
        final int arrowDirection = IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.ARROW_DIRECTION);
        return ClientData.DATA_CACHE.getDirectionArrow(platformId, (arrowDirection & 0b01) > 0, (arrowDirection & 0b10) > 0, HorizontalAlignment.CENTER, true, 0.25F, aspectRatio, ARGB_WHITE, ARGB_BLACK, TRANSPARENT_WHITE ? ARGB_WHITE : 0).resourceLocation;
    }

    private static boolean isDisplayMode2(long platformId) {
        final Platform platform = ClientData.DATA_CACHE.platformIdMap.get(platformId);
        return platform != null && platform.getPsdDisplayMode() == 2;
    }

    /**
     * MTR 的绘制方式：整段一张贴图，每格裁自己那一条 UV。
     */
    private static void scheduleRunQuad(StoredMatrixTransformations storedMatrixTransformations, ResourceLocation texture, int leftBlocks, int rightBlocks, Direction facing, int color, int light) {
        final float width = leftBlocks + rightBlocks + 1 - SIDE_PADDING * 2;
        RenderTrains.scheduleRender(texture, false, RenderTrains.QueuedRenderLayer.EXTERIOR, (matrices, vertexConsumer) -> {
            storedMatrixTransformations.transform(matrices);
            IDrawing.drawTexture(matrices, vertexConsumer,
                    leftBlocks == 0 ? SIDE_PADDING : 0, TOP_PADDING, 0,
                    1 - (rightBlocks == 0 ? SIDE_PADDING : 0), 1 - BOTTOM_PADDING, 0,
                    (leftBlocks - (leftBlocks == 0 ? 0 : SIDE_PADDING)) / width, 0,
                    (width - rightBlocks + (rightBlocks == 0 ? 0 : SIDE_PADDING)) / width, 1,
                    facing, color, light);
            matrices.popPose();
        });
    }

    // ===== 分段 =====

    private enum GroupKind {
        /** 普通玻璃（不含端头玻璃）：线路图 / 站名 / 装饰带都用它，装饰带因此不会画到端头玻璃上。 */
        GLASS,
        DOOR,
        ANY
    }

    /**
     * 一扇屏蔽门只有两格宽（左右半），所以要找到「同一扇门」的另一半：
     * 相邻那一格也是门楣、下方也是门、且门的 SIDE 与自己相反。
     */
    private static int getDoorRunBlocks(Level world, BlockPos pos, Direction facing, boolean searchLeft) {
        final EnumSide ownSide = getDoorSide(world, pos);
        if (ownSide == null) {
            return 0;
        }
        final BlockPos neighbour = pos.relative(searchLeft ? facing.getCounterClockWise() : facing.getClockWise());
        if (!(world.getBlockState(neighbour).getBlock() instanceof BlockTrdmPSDTop)) {
            return 0;
        }
        final EnumSide neighbourSide = getDoorSide(world, neighbour);
        return neighbourSide != null && neighbourSide != ownSide ? 1 : 0;
    }

    private static EnumSide getDoorSide(Level world, BlockPos pos) {
        final BlockState below = world.getBlockState(pos.below());
        if (below.getBlock() instanceof BlockPSDAPGDoorBase) {
            return IBlock.getStatePropertySafe(below, BlockPSDAPGDoorBase.SIDE);
        }
        return null;
    }

    /**
     * 从当前格往一侧数，共几格属于同一组（同一组的贴图是连着的）。
     */
    private static int getRunBlocks(Level world, BlockPos pos, Direction facing, boolean searchLeft, GroupKind groupKind) {
        int count = 0;
        BlockPos current = pos;
        while (isInGroup(world, current, groupKind)) {
            count++;
            current = current.relative(searchLeft ? facing.getCounterClockWise() : facing.getClockWise());
        }
        return Math.max(0, count - 1);
    }

    private static boolean isInGroup(Level world, BlockPos pos, GroupKind groupKind) {
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmPSDTop)) {
            return false;
        }
        if (groupKind == GroupKind.ANY) {
            return true;
        }
        final Block below = world.getBlockState(pos.below()).getBlock();
        if (groupKind == GroupKind.DOOR) {
            return below instanceof BlockPSDAPGDoorBase;
        }
        return below instanceof BlockPSDAPGGlassBase && !(below instanceof BlockPSDAPGGlassEndBase);
    }

    /**
     * 紧挨着的一组吊板有几格（0＝这一侧没有）。线路图放不下时用它来「借一组滑动门的吊板」。
     */
    private static int getAdjacentGroupWidth(Level world, BlockPos pos, Direction facing, boolean searchLeft, GroupKind groupKind) {
        final BlockPos neighbour = pos.relative(searchLeft ? facing.getCounterClockWise() : facing.getClockWise());
        if (!isInGroup(world, neighbour, groupKind)) {
            return 0;
        }
        return getRunBlocks(world, neighbour, facing, searchLeft, groupKind) + 1;
    }

    // ===== 玻璃的合并分组（装饰带分段用）=====

    /**
     * 装饰带的分段入口。
     * <p>
     */
    private static int getBandRightBlocks(Level world, BlockPos pos, Direction facing) {
        final EnumSide ownSide = getMergedGlassSide(world, pos, facing);
        if (ownSide == null) {
            return -1;
        }
        // 一条都没合并过的玻璃段：整段视同一整块合并玻璃
        if (ownSide == EnumSide.SINGLE && !hasMergedGlassRun(world, pos, facing)) {
            if (countGlassRun(world, pos, facing, true) != 0) {
                return -1;
            }
            return countGlassRun(world, pos, facing, false);
        }
        return getMergedGlassRightBlocks(world, pos, facing);
    }

    /**
     * 这一整段连续玻璃（往两侧一直数到滑动门 / 端头玻璃 / 断口为止）里有没有任何一块被合并过。
     */
    private static boolean hasMergedGlassRun(Level world, BlockPos pos, Direction facing) {
        for (final boolean searchLeft : new boolean[]{true, false}) {
            BlockPos current = pos;
            while (true) {
                final BlockPos next = current.relative(searchLeft ? facing.getCounterClockWise() : facing.getClockWise());
                final EnumSide side = getMergedGlassSide(world, next, facing);
                if (side == null) {
                    break;
                }
                if (side != EnumSide.SINGLE) {
                    return true;
                }
                current = next;
            }
        }
        return false;
    }

    /**
     * 从本格往一侧数，同一朝向的连续普通玻璃还有几格（不看合并状态）。
     */
    private static int countGlassRun(Level world, BlockPos pos, Direction facing, boolean searchLeft) {
        int count = 0;
        BlockPos current = pos;
        while (true) {
            final BlockPos next = current.relative(searchLeft ? facing.getCounterClockWise() : facing.getClockWise());
            if (getMergedGlassSide(world, next, facing) == null) {
                return count;
            }
            count++;
            current = next;
        }
    }

    /**
     * 装饰带的「一段」= 用刷子连起来的一块玻璃，也就是 MTR {@code connectGlass} 写在玻璃方块上的
     * {@code SIDE_EXTENDED} 所描述的那一组。语义（照 MTR 的 connectGlass）：
     */
    private static int getMergedGlassRightBlocks(Level world, BlockPos pos, Direction facing) {
        EnumSide side = getMergedGlassSide(world, pos, facing);
        // 左边连着别人（RIGHT / MIDDLE）就不是起点，交给那一组最左那格画
        if (side != EnumSide.LEFT && side != EnumSide.SINGLE) {
            return -1;
        }

        int count = 0;
        BlockPos current = pos;
        while (side == EnumSide.LEFT || side == EnumSide.MIDDLE) {
            final BlockPos next = current.relative(facing.getClockWise());
            final EnumSide nextSide = getMergedGlassSide(world, next, facing);
            // 右边那格也得声明「我左边连着」，否则中间有立柱，不能连成同一段
            if (nextSide != EnumSide.RIGHT && nextSide != EnumSide.MIDDLE) {
                break;
            }
            count++;
            current = next;
            side = nextSide;
        }
        return count;
    }

    /**
     * 这一格下方玻璃的横向拼接状态。只有「有门楣 + 同一朝向 + 普通玻璃（不含端头玻璃）」才算数，
     * 其它情况返回 null —— 端头玻璃因此永远不会被算进装饰带的分段里。
     */
    private static EnumSide getMergedGlassSide(Level world, BlockPos pos, Direction facing) {
        final BlockState top = world.getBlockState(pos);
        if (!(top.getBlock() instanceof BlockTrdmPSDTop)
                || IBlock.getStatePropertySafe(top, HorizontalDirectionalBlock.FACING) != facing) {
            return null;
        }
        final BlockState below = world.getBlockState(pos.below());
        final Block block = below.getBlock();
        if (!(block instanceof BlockPSDAPGGlassBase) || block instanceof BlockPSDAPGGlassEndBase
                || IBlock.getStatePropertySafe(below, HorizontalDirectionalBlock.FACING) != facing) {
            return null;
        }
        return IBlock.getStatePropertySafe(below, SIDE_EXTENDED);
    }

    // ===== 站台色带 =====

    private static float getAdditionalOffset(BlockState state) {
        return IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.PERSISTENT) == BlockTrdmPSDTop.EnumPersistent.NONE ? 0 : BlockTrdmPSDTop.PERSISTENT_OFFSET_SMALL;
    }

    private static void renderColorStrip(StoredMatrixTransformations storedMatrixTransformations, long platformId, BlockState state, Direction facing, int color, int light) {
        final boolean isNotPersistent = IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.PERSISTENT) == BlockTrdmPSDTop.EnumPersistent.NONE;
        final boolean airLeft = isNotPersistent && IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.AIR_LEFT);
        final boolean airRight = isNotPersistent && IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.AIR_RIGHT);
        RenderTrains.scheduleRender(ClientData.DATA_CACHE.getColorStrip(platformId).resourceLocation, false, RenderTrains.QueuedRenderLayer.EXTERIOR, (matrices, vertexConsumer) -> {
            storedMatrixTransformations.transform(matrices);
            // 直角端头：色带整幅铺满正面
            IDrawing.drawTexture(matrices, vertexConsumer, 0, COLOR_STRIP_START, 0, 1, COLOR_STRIP_BOTTOM, 0, facing, color, light);
            // 转角门楣两个外表面也铺色带
            matrices.popPose();
        });
    }

    // ===== 端头弯角（AIR_LEFT/AIR_RIGHT 恒 false，正常不会执行；保留 MTR 原几何）=====

    private static void renderEndCaps(StoredMatrixTransformations storedMatrixTransformations, BlockState state, Direction facing, int light) {
        final boolean airLeft = IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.AIR_LEFT);
        final boolean airRight = IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.AIR_RIGHT);
        final boolean persistent = IBlock.getStatePropertySafe(state, BlockTrdmPSDTop.PERSISTENT) != BlockTrdmPSDTop.EnumPersistent.NONE;
        if (!airLeft && !airRight || persistent) {
            return;
        }
        RenderTrains.scheduleRender(new ResourceLocation("mtr:textures/block/psd_top.png"), false, RenderTrains.QueuedRenderLayer.EXTERIOR, (matrices, vertexConsumer) -> {
            storedMatrixTransformations.transform(matrices);
            if (airLeft) {
                // back
                IDrawing.drawTexture(matrices, vertexConsumer, -0.125F, 0, 0.5F, 0.5F, 0, -0.125F, 0.5F, 1, -0.125F, -0.125F, 1, 0.5F, 0, 0, 1, 1, facing, -1, light);
                // front
                IDrawing.drawTexture(matrices, vertexConsumer, 0.5F - END_FRONT_OFFSET, 0.0625F, -0.5F - END_FRONT_OFFSET, -0.25F - END_FRONT_OFFSET, 0.0625F, 0.25F - END_FRONT_OFFSET, -0.25F - END_FRONT_OFFSET, 1, 0.25F - END_FRONT_OFFSET, 0.5F - END_FRONT_OFFSET, 1, -0.5F - END_FRONT_OFFSET, 0, 0, 1, 0.9375F, facing.getOpposite(), -1, light);
                // top curve
                IDrawing.drawTexture(matrices, vertexConsumer, 0.5F - BOTTOM_END_DIAGONAL_OFFSET, BOTTOM_DIAGONAL_OFFSET, -0.5F - BOTTOM_END_DIAGONAL_OFFSET, -0.25F - BOTTOM_END_DIAGONAL_OFFSET, BOTTOM_DIAGONAL_OFFSET, 0.25F - BOTTOM_END_DIAGONAL_OFFSET, -0.25F - END_FRONT_OFFSET, 0.0625F, 0.25F - END_FRONT_OFFSET, 0.5F - END_FRONT_OFFSET, 0.0625F, -0.5F - END_FRONT_OFFSET, 0, 0.9375F, 1, 0.96875F, facing.getOpposite(), -1, light);
                // bottom curve
                IDrawing.drawTexture(matrices, vertexConsumer, 0.5F, 0, -0.5F, -0.25F, 0, 0.25F, -0.25F - BOTTOM_END_DIAGONAL_OFFSET, BOTTOM_DIAGONAL_OFFSET, 0.25F - BOTTOM_END_DIAGONAL_OFFSET, 0.5F - BOTTOM_END_DIAGONAL_OFFSET, BOTTOM_DIAGONAL_OFFSET, -0.5F - BOTTOM_END_DIAGONAL_OFFSET, 0, 0.96875F, 1, 1, facing.getOpposite(), -1, light);
                // bottom
                IDrawing.drawTexture(matrices, vertexConsumer, 0.5F, SMALL_OFFSET, -0.125F, -0.125F, SMALL_OFFSET, 0.5F, -0.125F, SMALL_OFFSET, 0.125F, 0.5F, SMALL_OFFSET, -0.5F, 0.125F, 0.125F, 0.1875F, 0.1875F, facing, -1, light);
                // top
                IDrawing.drawTexture(matrices, vertexConsumer, 0.5F, 1 - SMALL_OFFSET, -0.5F, -0.125F, 1 - SMALL_OFFSET, 0.125F, -0.125F, 1 - SMALL_OFFSET, 0.5F, 0.5F, 1 - SMALL_OFFSET, -0.125F, 0.125F, 0.125F, 0.1875F, 0.1875F, Direction.UP, -1, light);
                // top front
                IDrawing.drawTexture(matrices, vertexConsumer, 0.5F - END_FRONT_OFFSET, 1 - SMALL_OFFSET, -0.5F - END_FRONT_OFFSET, -0.125F - ROOT_TWO_SCALED, 1 - SMALL_OFFSET, 0.125F, -0.125F, 1 - SMALL_OFFSET, 0.125F, 0.5F, 1 - SMALL_OFFSET, -0.5F, 0.125F, 0.125F, 0.1875F, 0.1875F, Direction.UP, -1, light);
                // left side diagonal
                IDrawing.drawTexture(matrices, vertexConsumer, 0.5F, 0.0625F, -0.5F, 0.5F - END_FRONT_OFFSET, 0.0625F, -0.5F - END_FRONT_OFFSET, 0.5F - END_FRONT_OFFSET, 1, -0.5F - END_FRONT_OFFSET, 0.5F, 1, -0.5F, 0.9375F, 0, 1, 0.9375F, facing, -1, light);
                // left side diagonal square
                IDrawing.drawTexture(matrices, vertexConsumer, 0.5F, 0, -0.5F, 0.5F - BOTTOM_END_DIAGONAL_OFFSET, BOTTOM_DIAGONAL_OFFSET, -0.5F - BOTTOM_END_DIAGONAL_OFFSET, 0.5F - END_FRONT_OFFSET, 0.0625F, -0.5F - END_FRONT_OFFSET, 0.5F, 0.0625F, -0.5F, 0.9375F, 0.9375F, 1, 1, facing, -1, light);
            }
            if (airRight) {
                // back
                IDrawing.drawTexture(matrices, vertexConsumer, -0.5F, 0, -0.125F, 0.125F, 0, 0.5F, 0.125F, 1, 0.5F, -0.5F, 1, -0.125F, 0, 0, 1, 1, facing, -1, light);
                // front
                IDrawing.drawTexture(matrices, vertexConsumer, 0.25F + END_FRONT_OFFSET, 0.0625F, 0.25F - END_FRONT_OFFSET, -0.5F + END_FRONT_OFFSET, 0.0625F, -0.5F - END_FRONT_OFFSET, -0.5F + END_FRONT_OFFSET, 1, -0.5F - END_FRONT_OFFSET, 0.25F + END_FRONT_OFFSET, 1, 0.25F - END_FRONT_OFFSET, 0, 0, 1, 0.9375F, facing.getOpposite(), -1, light);
                // top curve
                IDrawing.drawTexture(matrices, vertexConsumer, 0.25F + BOTTOM_END_DIAGONAL_OFFSET, BOTTOM_DIAGONAL_OFFSET, 0.25F - BOTTOM_END_DIAGONAL_OFFSET, -0.5F + BOTTOM_END_DIAGONAL_OFFSET, BOTTOM_DIAGONAL_OFFSET, -0.5F - BOTTOM_END_DIAGONAL_OFFSET, -0.5F + END_FRONT_OFFSET, 0.0625F, -0.5F - END_FRONT_OFFSET, 0.25F + END_FRONT_OFFSET, 0.0625F, 0.25F - END_FRONT_OFFSET, 0, 0.9375F, 1, 0.96875F, facing.getOpposite(), -1, light);
                // bottom curve
                IDrawing.drawTexture(matrices, vertexConsumer, 0.25F, 0, 0.25F, -0.5F, 0, -0.5F, -0.5F + BOTTOM_END_DIAGONAL_OFFSET, BOTTOM_DIAGONAL_OFFSET, -0.5F - BOTTOM_END_DIAGONAL_OFFSET, 0.25F + BOTTOM_END_DIAGONAL_OFFSET, BOTTOM_DIAGONAL_OFFSET, 0.25F - BOTTOM_END_DIAGONAL_OFFSET, 0, 0.96875F, 1, 1, facing.getOpposite(), -1, light);
                // bottom
                IDrawing.drawTexture(matrices, vertexConsumer, 0.125F, SMALL_OFFSET, 0.5F, -0.5F, SMALL_OFFSET, -0.125F, -0.5F, SMALL_OFFSET, -0.5F, 0.125F, SMALL_OFFSET, 0.125F, 0.125F, 0.125F, 0.1875F, 0.1875F, facing, -1, light);
                // top
                IDrawing.drawTexture(matrices, vertexConsumer, 0.125F, 1 - SMALL_OFFSET, 0.125F, -0.5F, 1 - SMALL_OFFSET, -0.5F, -0.5F, 1 - SMALL_OFFSET, -0.125F, 0.125F, 1 - SMALL_OFFSET, 0.5F, 0.125F, 0.125F, 0.1875F, 0.1875F, Direction.UP, -1, light);
                // top front
                IDrawing.drawTexture(matrices, vertexConsumer, 0.125F + ROOT_TWO_SCALED, 1 - SMALL_OFFSET, 0.125F, -0.5F + END_FRONT_OFFSET, 1 - SMALL_OFFSET, -0.5F - END_FRONT_OFFSET, -0.5F, 1 - SMALL_OFFSET, -0.5F, 0.125F, 1 - SMALL_OFFSET, 0.125F, 0.125F, 0.125F, 0.1875F, 0.1875F, Direction.UP, -1, light);
                // left side diagonal
                IDrawing.drawTexture(matrices, vertexConsumer, -0.5F + END_FRONT_OFFSET, 0.0625F, -0.5F - END_FRONT_OFFSET, -0.5F, 0.0625F, -0.5F, -0.5F, 1, -0.5F, -0.5F + END_FRONT_OFFSET, 1, -0.5F - END_FRONT_OFFSET, 0, 0, 0.0625F, 0.9375F, facing, -1, light);
                // left side diagonal square
                IDrawing.drawTexture(matrices, vertexConsumer, -0.5F + BOTTOM_END_DIAGONAL_OFFSET, BOTTOM_DIAGONAL_OFFSET, -0.5F - BOTTOM_END_DIAGONAL_OFFSET, -0.5F, 0, -0.5F, -0.5F, 0.0625F, -0.5F, -0.5F + END_FRONT_OFFSET, 0.0625F, -0.5F - END_FRONT_OFFSET, 0, 0.9375F, 0.0625F, 1, facing, -1, light);
            }
            matrices.popPose();
        });
    }
}
