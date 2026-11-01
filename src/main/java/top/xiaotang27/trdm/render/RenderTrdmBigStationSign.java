package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.block.IBlock;
import mtr.data.IGui;
import mtr.render.RenderRouteBase;
import mtr.render.RenderTrains;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.block.BlockTrdmBigStationSign;
import top.xiaotang27.trdm.block.SignRuns;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 墙挂大站名牌渲染器：深灰底板 + 压在正面的贴图，牌高 3/4 格、贴墙挂。
 * <p>
 */
public class RenderTrdmBigStationSign<T extends BlockTrdmBigStationSign.TileEntityTrdmBigStationSign>
        implements BlockEntityRenderer<T>, IBlock, IGui {

    private static final float BOARD_HALF_DEPTH = BlockTrdmBigStationSign.BOARD_THICKNESS / 2F;
    /** 底板中线到方块中心的距离（沿法线，朝外为正）：底板占 [−0.5, −7/16]。 */
    private static final float BOARD_OFFSET = -(0.5F - BOARD_HALF_DEPTH);
    /** 牌面在底板正面外多少格：留够余量，不然和底板共面会 z-fighting 闪。 */
    private static final float PLATE_OFFSET = -(0.5F - BlockTrdmBigStationSign.BOARD_THICKNESS - 0.01F);
    /** 贴图最大宽度，超过就把纹理像素密度降下来（不在渲染端裁图）。 */
    private static final float MAX_PLATE_WIDTH = 8F;

    /** 底板贴图（黑色混凝土）：牌面底色已经改成纯黑，侧面/背面跟着一致。 */
    private static final ResourceLocation BOARD_TEXTURE = new ResourceLocation("minecraft", "textures/block/black_concrete.png");

    /** 每个牌子上一次画出来的贴图：新贴图烘焙期间先用它顶着，不然会闪空白。 */
    private static final Map<BlockPos, ResourceLocation> LAST_TEXTURES = new HashMap<>();

    /** 调试用：打开 {@code -Dtrdm.debugSign=true} 才打印牌子状态。 */
    private static final boolean DEBUG = Boolean.getBoolean("trdm.debugSign");
    /** 调试用：上次打印过的「牌子状态」，避免每帧刷日志。 */
    private static String LAST_DEBUG_KEY = "";

    public RenderTrdmBigStationSign() {
    }

    @Override
    public void render(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final Level world = entity.getLevel();
        if (world == null) {
            return;
        }
        final BlockPos pos = entity.getBlockPos();
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmBigStationSign)) {
            return;
        }
        final Direction facing = IBlock.getStatePropertySafe(state, BlockTrdmBigStationSign.FACING);

        // 合并段：整段只由最左那格画一次
        final List<BlockPos> run = SignRuns.collect(world, pos, state.getBlock(), BlockTrdmBigStationSign.FACING, facing);
        if (SignRuns.indexOf(run, pos) != 0) {
            return;
        }
        final int rightCount = run.size() - 1;
        final float centreOffset = rightCount / 2F;
        final float plateWidth = Math.min(MAX_PLATE_WIDTH, rightCount + 1F);

        final TrdmBigSignContent.Content content = TrdmBigSignContent.resolve(world, run);
        if (content == null) {
            return;
        }

        final float aspect = plateWidth / BlockTrdmBigStationSign.PLATE_HEIGHT;
        if (DEBUG) {
            final String debugKey = pos + "|" + content.stationName() + "|" + content.exitName() + "|"
                    + content.badges().size() + "|" + content.zhanStyle() + "|" + content.elevator();
            if (!debugKey.equals(LAST_DEBUG_KEY)) {
                LAST_DEBUG_KEY = debugKey;
                Trdm.LOGGER.info("[big sign render] pos={} station='{}' routes={} exit='{}' zhan={} elevator={} aspect={}",
                        pos, content.stationName(), content.badges().size(), content.exitName(),
                        content.zhanStyle(), content.elevator(), aspect);
            }
        }
        ResourceLocation texture = TrdmHeaderTextures.getBigStationSign(content.stationName(), content.badges(),
                content.exitName(), content.zhanStyle(), content.elevator(), aspect);
        if (texture == null) {
            // 新贴图还在烘焙：先继续画上一张，避免换出口/换线路时牌子闪一下空白
            texture = LAST_TEXTURES.get(pos);
        }
        if (texture == null) {
            return;
        }
        if (LAST_TEXTURES.size() > 512) {
            LAST_TEXTURES.clear();
        }
        LAST_TEXTURES.put(pos, texture);

        final Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        final Vec3 right = new Vec3(0, 1, 0).cross(normal);
        final Vec3 up = new Vec3(0, 1, 0);
        final Vec3 base = Vec3.atCenterOf(pos).add(right.scale(centreOffset));
        final Vec3 plateCentre = base.add(normal.scale(PLATE_OFFSET));
        final Vec3 boardCentre = base.add(normal.scale(BOARD_OFFSET));
        final int shading = RenderRouteBase.getShadingColor(facing, IGui.ARGB_WHITE);

        final float halfWidth = plateWidth / 2F;
        final float halfHeight = BlockTrdmBigStationSign.PLATE_HEIGHT / 2F;

        RenderTrains.scheduleRender(BOARD_TEXTURE, false, RenderType::entityCutout, (matricesNew, vertexConsumer) -> {
            final Matrix4f pose = matricesNew.last().pose();
            emitBox(pose, vertexConsumer, boardCentre, right, normal, up, halfWidth, BOARD_HALF_DEPTH, halfHeight,
                    shading, light);
        });

        RenderTrains.scheduleRender(texture, false, RenderType::entityCutout, (matricesNew, vertexConsumer) -> {
            final Matrix4f pose = matricesNew.last().pose();
            emitFace(pose, vertexConsumer, plateCentre, right, up, halfWidth, halfHeight, normal, shading, light);
        });
    }

    /**
     * 一个朝外的矩形面。
     * @param centre  面中心
     */
    static void emitFace(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 centre, Vec3 uAxis, Vec3 vAxis,
                         float halfU, float halfV, Vec3 outward, int argb, int light) {
        final Vec3 topLeft = centre.add(uAxis.scale(-halfU)).add(vAxis.scale(halfV));
        final Vec3 topRight = centre.add(uAxis.scale(halfU)).add(vAxis.scale(halfV));
        final Vec3 bottomRight = centre.add(uAxis.scale(halfU)).add(vAxis.scale(-halfV));
        final Vec3 bottomLeft = centre.add(uAxis.scale(-halfU)).add(vAxis.scale(-halfV));
        final Vec3[] corners = {topLeft, topRight, bottomRight, bottomLeft};
        final float[] u = {0F, 1F, 1F, 0F};
        final float[] v = {0F, 0F, 1F, 1F};

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
    static void emitBox(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 centre, Vec3 right, Vec3 normal, Vec3 up,
                        float halfWidth, float halfDepth, float halfHeight, int argb, int light) {
        emitFace(pose, vertexConsumer, centre.add(normal.scale(halfDepth)), right, up, halfWidth, halfHeight, normal, argb, light);
        emitFace(pose, vertexConsumer, centre.add(normal.scale(-halfDepth)), right, up, halfWidth, halfHeight, normal.scale(-1), argb, light);
        emitFace(pose, vertexConsumer, centre.add(up.scale(halfHeight)), right, normal, halfWidth, halfDepth, up, argb, light);
        emitFace(pose, vertexConsumer, centre.add(up.scale(-halfHeight)), right, normal, halfWidth, halfDepth, up.scale(-1), argb, light);
        emitFace(pose, vertexConsumer, centre.add(right.scale(halfWidth)), normal, up, halfDepth, halfHeight, right, argb, light);
        emitFace(pose, vertexConsumer, centre.add(right.scale(-halfWidth)), normal, up, halfDepth, halfHeight, right.scale(-1), argb, light);
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return true;
    }
}
