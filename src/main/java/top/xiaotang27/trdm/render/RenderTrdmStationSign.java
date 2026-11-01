package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.block.IBlock;
import mtr.client.ClientCache;
import mtr.client.ClientData;
import mtr.data.IGui;
import mtr.data.RailwayData;
import mtr.data.Station;
import mtr.render.RenderRouteBase;
import mtr.render.RenderTrains;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import top.xiaotang27.trdm.block.BlockTrdmStationSign;
import top.xiaotang27.trdm.block.SignRuns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 站名牌渲染器：一块白底板 + 压在它正面的贴图，画在「合并段水平中点」上。
 * <p>
 */
public class RenderTrdmStationSign<T extends BlockTrdmStationSign.TileEntityTrdmStationSign>
        implements BlockEntityRenderer<T>, IBlock, IGui {

    /** 牌面尺寸（格）：1 格宽 × 3/4 格高。 */
    private static final float PLATE_WIDTH = 1F;
    private static final float PLATE_HEIGHT = 12F / 16F;

    /** 底板厚度（格）＝ 1/16，贴在方块靠墙那一侧。 */
    private static final float BOARD_HALF_DEPTH = 1F / 32F;
    /** 底板中线到方块中心的距离（沿法线，朝外为正）：底板占 [−0.5, −7/16]。 */
    private static final float BOARD_OFFSET = -(0.5F - 1F / 32F);
    /**
     * 牌面到方块中心的距离：底板正面在 −7/16，牌面再往外 0.002 格压在上面，
     * 所以牌子是「贴着墙挂的一块板，正面印着字」。
     */
    private static final float PLATE_OFFSET = -(0.5F - 1F / 16F - 0.01F);

    /** 底板用原版白色混凝土贴图（纯色块，看起来就是一块白板）。 */
    private static final ResourceLocation BOARD_TEXTURE = new ResourceLocation("minecraft", "textures/block/white_concrete.png");

    /** 每个牌子上一次画出来的贴图：新贴图烘焙期间先用它顶着，不然会闪空白。 */
    private static final java.util.Map<BlockPos, ResourceLocation> LAST_TEXTURES = new java.util.HashMap<>();

    public RenderTrdmStationSign() {
    }

    @Override
    public void render(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final Level world = entity.getLevel();
        if (world == null) {
            return;
        }
        final BlockPos pos = entity.getBlockPos();
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmStationSign)) {
            return;
        }
        final Direction facing = IBlock.getStatePropertySafe(state, BlockTrdmStationSign.FACING);

        // 合并段：整段只由「-across 方向最左」那格画一次
        final List<BlockPos> run = SignRuns.collect(world, pos, state.getBlock(), BlockTrdmStationSign.FACING, facing);
        if (SignRuns.indexOf(run, pos) != 0) {
            return;
        }
        final int rightCount = run.size() - 1;
        final float centreOffset = rightCount / 2F;

        // 站台是整段牌共用的：段里哪一格存了站台都要认（新放的一格是空的，
        // 不能因为它正好是段首就让整段牌掉回「最近站台」）
        long storedPlatformId = 0;
        for (final BlockPos member : run) {
            if (world.getBlockEntity(member) instanceof BlockTrdmStationSign.TileEntityTrdmStationSign sign
                    && sign.getPlatformId() != 0) {
                storedPlatformId = sign.getPlatformId();
                break;
            }
        }
        final long platformId = storedPlatformId != 0
                ? storedPlatformId
                : RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos);
        if (platformId == 0) {
            return;
        }
        final List<ClientCache.PlatformRouteDetails> routes = new ArrayList<>(
                ClientData.DATA_CACHE.requestPlatformIdToRoutes(platformId));
        if (routes.isEmpty()) {
            return;
        }
        routes.sort(Comparator.comparing((ClientCache.PlatformRouteDetails route) -> route.routeName,
                String.CASE_INSENSITIVE_ORDER));
        final ClientCache.PlatformRouteDetails route = routes.get(0);

        final Station station = ClientData.DATA_CACHE.platformIdToStation.get(platformId);
        final String stationName = station == null ? "" : station.name;

        final List<ClientCache.PlatformRouteDetails.StationDetails> stations = route.stationDetails;
        final int lastIndex = stations.size() - 1;
        final int currentIndex = Mth.clamp(route.currentStationIndex, 0, Math.max(lastIndex, 0));
        final boolean terminal = lastIndex < 0 || currentIndex >= lastIndex;
        final String nextStation = terminal ? null : stations.get(currentIndex + 1).stationName;

        ResourceLocation texture = TrdmHeaderTextures.getStationSign(stationName, nextStation, route.routeColor, terminal);
        if (texture == null) {
            // 新贴图还在烘焙：先继续画上一张，别闪空白
            texture = LAST_TEXTURES.get(pos);
        }
        if (texture == null) {
            return;
        }
        if (LAST_TEXTURES.size() > 512) {
            LAST_TEXTURES.clear();
        }
        LAST_TEXTURES.put(pos, texture);

        // 世界坐标：沿法线让到靠墙那侧，沿牌面横向挪到合并段中点
        final Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        final Vec3 right = new Vec3(0, 1, 0).cross(normal);
        final Vec3 up = new Vec3(0, 1, 0);
        final Vec3 base = Vec3.atCenterOf(pos).add(right.scale(centreOffset));
        final Vec3 plateCentre = base.add(normal.scale(PLATE_OFFSET));
        final Vec3 boardCentre = base.add(normal.scale(BOARD_OFFSET));
        final int shading = RenderRouteBase.getShadingColor(facing, IGui.ARGB_WHITE);

        // 底板：一块 1 格宽 × 3/4 格高 × 1/16 厚的白板
        RenderTrains.scheduleRender(BOARD_TEXTURE, false, RenderType::entityCutout, (matricesNew, vertexConsumer) -> {
            final Matrix4f pose = matricesNew.last().pose();
            emitBox(pose, vertexConsumer, boardCentre, right, normal, up,
                    PLATE_WIDTH / 2F, BOARD_HALF_DEPTH, PLATE_HEIGHT / 2F, shading, light);
        });

        // 牌面：压在底板正面的贴图（本站 / 拼音 / 线路色带 / 下一站）
        RenderTrains.scheduleRender(texture, false, RenderType::entityCutout, (matricesNew, vertexConsumer) -> {
            final Matrix4f pose = matricesNew.last().pose();
            emitFace(pose, vertexConsumer, plateCentre, right, up,
                    PLATE_WIDTH / 2F, PLATE_HEIGHT / 2F, normal, shading, light);
        });
    }

    /**
     * 一个朝外的矩形面。
     * @param centre   面中心
     */
    private static void emitFace(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 centre, Vec3 uAxis, Vec3 vAxis,
                                 float halfU, float halfV, Vec3 outward, int argb, int light) {
        final Vec3 topLeft = centre.add(uAxis.scale(-halfU)).add(vAxis.scale(halfV));
        final Vec3 topRight = centre.add(uAxis.scale(halfU)).add(vAxis.scale(halfV));
        final Vec3 bottomRight = centre.add(uAxis.scale(halfU)).add(vAxis.scale(-halfV));
        final Vec3 bottomLeft = centre.add(uAxis.scale(-halfU)).add(vAxis.scale(-halfV));
        final Vec3[] corners = {topLeft, topRight, bottomRight, bottomLeft};
        final float[] u = {0F, 1F, 1F, 0F};
        final float[] v = {0F, 0F, 1F, 1F};

        // (B−A)×(C−A) 与朝外法线同向才是正面，不同向就把顺序倒过来
        final Vec3 ab = topRight.subtract(topLeft);
        final Vec3 ac = bottomRight.subtract(topLeft);
        final Vec3 cross = new Vec3(
                ab.y * ac.z - ab.z * ac.y,
                ab.z * ac.x - ab.x * ac.z,
                ab.x * ac.y - ab.y * ac.x);
        final boolean reversed = cross.dot(outward) < 0;

        for (int index = 0; index < 4; index++) {
            final int actual = reversed ? 3 - index : index;
            final Vec3 corner = corners[actual];
            vertexConsumer.vertex(pose, (float) corner.x, (float) corner.y, (float) corner.z)
                    .color(argb)
                    .uv(u[actual], v[actual])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal((float) outward.x, (float) outward.y, (float) outward.z)
                    .endVertex();
        }
    }

    /** 一个轴对齐的盒子（6 个面全部朝外画）。 */
    private static void emitBox(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 centre, Vec3 right, Vec3 normal, Vec3 up,
                                float halfWidth, float halfDepth, float halfHeight, int argb, int light) {
        // 正面 / 背面
        emitFace(pose, vertexConsumer, centre.add(normal.scale(halfDepth)), right, up, halfWidth, halfHeight, normal, argb, light);
        emitFace(pose, vertexConsumer, centre.add(normal.scale(-halfDepth)), right, up, halfWidth, halfHeight, normal.scale(-1), argb, light);
        // 上 / 下
        emitFace(pose, vertexConsumer, centre.add(up.scale(halfHeight)), right, normal, halfWidth, halfDepth, up, argb, light);
        emitFace(pose, vertexConsumer, centre.add(up.scale(-halfHeight)), right, normal, halfWidth, halfDepth, up.scale(-1), argb, light);
        // 左 / 右
        emitFace(pose, vertexConsumer, centre.add(right.scale(halfWidth)), normal, up, halfDepth, halfHeight, right, argb, light);
        emitFace(pose, vertexConsumer, centre.add(right.scale(-halfWidth)), normal, up, halfDepth, halfHeight, right.scale(-1), argb, light);
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return true;
    }
}
