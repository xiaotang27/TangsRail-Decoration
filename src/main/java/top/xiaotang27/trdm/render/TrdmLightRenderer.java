package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.block.BlockTrdmWallLight;
import top.xiaotang27.trdm.block.PsdLightHolder;
import top.xiaotang27.trdm.block.PsdLightKind;

import java.util.List;
import java.util.Map;

/**
 * 三种灯的模型绘制。灯是方块实体渲染器（BER）画出来的：
 * <ul>
 *     <li>门楣 / 延伸板上的灯从 {@link #renderAttachment} 进来，模型坐标和门楣模型同一套，
 *         按门楣的 {@code facing} 旋转；</li>
 *     <li>独立放置的墙上的荧光灯管从 {@link #renderWallLight} 进来，模型坐标再用
 *         {@link BlockTrdmWallLight.EnumMount} 给出的「底座朝哪面」矩阵摆到吊顶 / 地面 / 墙面。</li>
 * </ul>
 * 模型 0 号贴图（灯管）按自发光画（{@code 0xF000F0}）；模型 3 的 color 贴图是灯的底座，
 * 按普通受光乘上方块实体里存的颜色。金属底座走环境光。
 */
public final class TrdmLightRenderer {

    private static final ResourceLocation MODEL_TUBE = Trdm.id("models/block/psd_toplight_1.json");
    private static final ResourceLocation MODEL_PLAIN = Trdm.id("models/block/psd_toplight_2.json");
    private static final ResourceLocation MODEL_COLOR = Trdm.id("models/block/psd_toplight_3.json");

    private static final ResourceLocation TEXTURE_TUBE = Trdm.id("textures/block/lighrt_psdtop_1.png");
    private static final ResourceLocation TEXTURE_COLOR = Trdm.id("textures/block/psdtop_3_color.png");

    private static final int FULLBRIGHT = 0xF000F0;
    private static final int WHITE = 0xFFFFFFFF;

    /** 模型 1 的灯管中心在门楣坐标里的 x（= (6.144 + 7.0) / 2 / 16 格）；独立放置时把它挪到格子中心。 */
    private static final double TUBE_CENTRE_X = (6.144D + 7.0D) / 2.0D;
    private static final double WALL_LIGHT_CENTRE = 8.0D;
    /** 模型 x = 灯管中心 → 方块中心：模型 +x 就是世界 +x 时用正，反过来用负。 */
    private static final double CENTRE_POS = (WALL_LIGHT_CENTRE - TUBE_CENTRE_X) / 16.0D;
    private static final double CENTRE_NEG = (WALL_LIGHT_CENTRE + TUBE_CENTRE_X) / 16.0D;

    private TrdmLightRenderer() {
    }

    /**
     * 门楣 / 延伸板上装着的灯（模型 1 的贴装形态、模型 2 / 3）。
     * <p>
     * 模型 2 / 3 的文件坐标是以门楣 x = 0 那面（轨道侧）为贴装面写的；实际要装在
     * <b>站台内侧</b>，所以先沿 x = 6 镜像（{@code x' = 6/16 - x}）再按 facing 旋转。
     * 模型 1 的坐标本来就在 x = 6 那面（站台侧），不镜像。
     */
    public static void renderAttachment(BlockState state, PsdLightHolder holder, PoseStack matrices,
                                        MultiBufferSource buffers, int light) {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        final Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        final Matrix4f facingTransform = facingMatrix(facing);
        if (holder.hasTubeLight()) {
            renderModel(matrices, MODEL_TUBE, facingTransform, -1, buffers, light);
        }
        final PsdLightKind top = holder.topLight();
        if (top == PsdLightKind.PLAIN) {
            renderModel(matrices, MODEL_PLAIN, facingTransform.mul(platformRotation()), -1, buffers, light);
        } else if (top == PsdLightKind.COLOR) {
            renderModel(matrices, MODEL_COLOR, facingTransform.mul(platformRotation()),
                    holder.topLightColor(), buffers, light);
        }
    }

    /** 门楣模型面向（facing）对应的旋转，和方块状态里 {@code y} 的取值一致。 */
    private static Matrix4f facingMatrix(Direction facing) {
        final float degrees = switch (facing) {
            case NORTH -> 90F;
            case EAST -> 180F;
            case SOUTH -> 270F;
            default -> 0F;
        };
        return new Matrix4f()
                .translate(0.5F, 0.0F, 0.5F)
                .rotateY((float) Math.toRadians(-degrees))
                .translate(-0.5F, 0.0F, -0.5F);
    }

    /**
     * 模型 2 / 3 的文件坐标贴在门楣 x = 0 那面；实际要贴 x = 6 那面（站台内侧）。
     * 用绕竖轴 180° 的旋转而不是镜像：x' = 6/16 - x、z' = 1 - z，
     * 这样法线不会被翻面，底座受光才对。
     */
    private static Matrix4f platformRotation() {
        return new Matrix4f()
                .translate(3.0F / 16.0F, 0.0F, 0.5F)
                .rotateY((float) Math.PI)
                .translate(-3.0F / 16.0F, 0.0F, -0.5F);
    }

    /** 独立放置的「墙上的荧光灯管」：按方块状态的底座朝向摆模型 1。 */
    public static void renderWallLight(BlockState state, PoseStack matrices, MultiBufferSource buffers, int light) {
        final BlockTrdmWallLight.EnumMount mount = state.getValue(BlockTrdmWallLight.MOUNT);
        final Direction facing = state.getValue(BlockTrdmWallLight.FACING);
        final Matrix4f transform = mountTransform(mount, facing);
        renderModel(matrices, MODEL_TUBE, transform, -1, buffers, light);
    }

    /**
     * 画一个模型。
     *
     * @param transform 模型原始坐标（0~16 像素 = 0~1 格）到方块局部坐标的变换
     * @param tintRgb   模型 3 color 面的染色（0xRRGGBB），-1 表示不染
     */
    private static void renderModel(PoseStack matrices, ResourceLocation modelLocation, Matrix4f transform,
                                    int tintRgb, MultiBufferSource buffers, int light) {
        final List<TrdmModel.Element> elements = TrdmModel.load(modelLocation);
        if (elements.isEmpty()) {
            return;
        }
        final int tint = tintRgb < 0 ? -1 : tintRgb & 0xFFFFFF;
        final Matrix4f pose = matrices.last().pose();
        for (final TrdmModel.Element element : elements) {
            for (final Map.Entry<Direction, TrdmModel.Face> entry : element.faces().entrySet()) {
                final TrdmModel.Face face = entry.getValue();
                final Vec3[] raw = TrdmModel.corners(element, entry.getKey(), Vec3.ZERO, Direction.WEST, 1F);
                final Vec3[] corners = new Vec3[4];
                for (int index = 0; index < 4; index++) {
                    corners[index] = transformPoint(transform, raw[index]);
                }
                final Vec3 outward = faceNormal(corners);
                final boolean colourFace = face.texture().equals(TEXTURE_COLOR);
                final boolean tubeFace = face.texture().equals(TEXTURE_TUBE);
                final int argb = colourFace && tint >= 0 ? 0xFF000000 | tint : WHITE;
                // 只有灯管本身自发光；color 贴图是灯的「底座」，按普通受光上色
                final int packedLight = tubeFace ? FULLBRIGHT : light;
                final VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(face.texture()));
                TrdmModel.emit(pose, consumer, corners, face, outward, packedLight, argb);
            }
        }
    }

    /**
     * 模型 1 的原始坐标里，底座法线是 +y、灯管沿 z、灯管中心在 x≈6.57（门楣朝站台那侧的 6 像素面）。
     * 独立放置时按「底座朝哪面」重新给出一组正交基：
     * <ul>
     *     <li>{@code u}：模型 +x 的像；</li>
     *     <li>{@code v}：模型 +y 的像（底座法线）；</li>
     *     <li>{@code w}：模型 +z 的像（灯管长度方向）。</li>
     * </ul>
     * 底座贴面（v 指向被贴的那面），灯管在面内水平；贴墙时还要把灯管中心抬到 y = 0.5 格左右，
     * 贴天花板 / 地面时把灯管中心挪到格子正中。
     */
    private static Matrix4f mountTransform(BlockTrdmWallLight.EnumMount mount, Direction facing) {
        final boolean runAlongX = facing.getAxis() == Direction.Axis.X;
        final Vec3 u;
        final Vec3 v;
        final Vec3 w;
        final Vec3 t;
        switch (mount) {
            case UP -> {
                v = new Vec3(0, 1, 0);
                if (runAlongX) {
                    // 灯管沿 x：模型 +z → 东，+x → 北，灯管中心沿 z 居中
                    w = new Vec3(1, 0, 0);
                    u = new Vec3(0, 0, -1);
                    t = new Vec3(0, 0, CENTRE_NEG);
                } else {
                    // 灯管沿 z：+z → 南，+x → 东，灯管中心沿 x 居中
                    w = new Vec3(0, 0, 1);
                    u = new Vec3(1, 0, 0);
                    t = new Vec3(CENTRE_POS, 0, 0);
                }
            }
            case DOWN -> {
                v = new Vec3(0, -1, 0);
                if (runAlongX) {
                    w = new Vec3(1, 0, 0);
                    u = new Vec3(0, 0, 1);
                    t = new Vec3(0, 1, CENTRE_POS);
                } else {
                    w = new Vec3(0, 0, 1);
                    u = new Vec3(-1, 0, 0);
                    t = new Vec3(CENTRE_NEG, 1, 0);
                }
            }
            case NORTH -> {
                // 底座朝北（z=0），灯管沿 x；x 的像朝下，把模型 x 中心抬到 0.5 格
                v = new Vec3(0, 0, -1);
                w = new Vec3(1, 0, 0);
                u = new Vec3(0, -1, 0);
                t = new Vec3(0, CENTRE_NEG, 1);
            }
            case SOUTH -> {
                v = new Vec3(0, 0, 1);
                w = new Vec3(1, 0, 0);
                u = new Vec3(0, 1, 0);
                t = new Vec3(0, CENTRE_POS, 0);
            }
            case WEST -> {
                // 底座朝西（x=0），灯管沿 z
                v = new Vec3(-1, 0, 0);
                w = new Vec3(0, 0, 1);
                u = new Vec3(0, 1, 0);
                t = new Vec3(1, CENTRE_POS, 0);
            }
            case EAST -> {
                v = new Vec3(1, 0, 0);
                w = new Vec3(0, 0, 1);
                u = new Vec3(0, -1, 0);
                t = new Vec3(0, CENTRE_NEG, 0);
            }
            default -> {
                v = new Vec3(0, 1, 0);
                w = new Vec3(0, 0, 1);
                u = new Vec3(1, 0, 0);
                t = new Vec3(CENTRE_POS, 0, 0);
            }
        }

        final Matrix4f matrix = new Matrix4f();
        matrix.m00((float) u.x).m01((float) u.y).m02((float) u.z).m03(0F);
        matrix.m10((float) v.x).m11((float) v.y).m12((float) v.z).m13(0F);
        matrix.m20((float) w.x).m21((float) w.y).m22((float) w.z).m23(0F);
        matrix.m30((float) t.x).m31((float) t.y).m32((float) t.z).m33(1F);
        return matrix;
    }

    private static Vec3 transformPoint(Matrix4f matrix, Vec3 point) {
        final Vector3f vector = new Vector3f((float) point.x, (float) point.y, (float) point.z);
        matrix.transformPosition(vector);
        return new Vec3(vector.x, vector.y, vector.z);
    }

    private static Vec3 faceNormal(Vec3[] corners) {
        final Vec3 ab = corners[1].subtract(corners[0]);
        final Vec3 ac = corners[2].subtract(corners[0]);
        final Vec3 normal = ab.cross(ac);
        return normal.lengthSqr() < 1.0E-12D ? new Vec3(0, 1, 0) : normal.normalize();
    }
}
