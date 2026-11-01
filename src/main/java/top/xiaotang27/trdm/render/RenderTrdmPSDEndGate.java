package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.block.IBlock;
import mtr.mappings.BlockEntityRendererMapper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import top.xiaotang27.trdm.block.BlockTrdmPSDEndGate;
import top.xiaotang27.trdm.block.BlockTrdmPSDEndGate.TileEntityTrdmPSDEndGate;

/**
 * 端门渲染：按整段长度算门/玻璃比例（门 floor(0.6W) 在最外侧），门绕最外侧竖边旋转。
 * 几何沿用端头玻璃：玻璃/门 2px 厚、门楣 y12–16。
 */
public class RenderTrdmPSDEndGate<T extends TileEntityTrdmPSDEndGate>
        extends BlockEntityRendererMapper<T> {

    private static final ResourceLocation TEX_GLASS = new ResourceLocation("mtr", "textures/block/psd_glass_bottom_1.png");
    private static final ResourceLocation TEX_TOP = new ResourceLocation("mtr", "textures/block/psd_top.png");
    private static final ResourceLocation TEX_LAMP = new ResourceLocation("mtr", "textures/block/light_on.png");

    public RenderTrdmPSDEndGate(net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher dispatcher) {
        super(dispatcher);
    }

    private static final float PANEL_Z = 2F / 16F;
    private static final float GLASS_TOP = 12F / 16F;
    private static final float HEADER_TOP = 1F;

    @Override
    public void render(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light,
                       int overlay) {
        final Level world = entity.getLevel();
        if (world == null) {
            return;
        }
        final BlockState state = entity.getBlockState();
        final Direction facing = IBlock.getStatePropertySafe(state, net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        final Tuple<net.minecraft.core.BlockPos, Integer> run =
                BlockTrdmPSDEndGate.run(world, entity.getBlockPos(), facing);
        final int length = run.getB();
        if (length <= 0) {
            return;
        }
        final int doorPx = length < 2 ? 0 : BlockTrdmPSDEndGate.doorWidth(length);
        final boolean open = IBlock.getStatePropertySafe(state, BlockTrdmPSDEndGate.OPEN);

        final int offset = Math.abs((entity.getBlockPos().getX() - run.getA().getX())
                + (entity.getBlockPos().getZ() - run.getA().getZ()));
        final float totalBlocks = length;
        final float doorBlocks = doorPx / 16F;
        final float startLocal = offset - totalBlocks / 2F;

        matrices.pushPose();
        matrices.translate(0.5, 0, 0.5);
        matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));

        final VertexConsumer glass = vertexConsumers.getBuffer(RenderType.entityCutout(TEX_GLASS));
        final PoseStack.Pose pose = matrices.last();
        final Matrix4f matrix = pose.pose();
        final Matrix3f normal = pose.normal();

        // 门楣（整格贯通）
        quad(matrix, normal, vertexConsumers.getBuffer(RenderType.entityCutout(TEX_TOP)), light,
                -0.5F, GLASS_TOP, 0F, 0.5F, HEADER_TOP, 0F, Direction.UP);

        // 玻璃段
        final float glassStart = Math.max(startLocal, doorBlocks);
        final float glassEnd = startLocal + 1F;
        if (glassEnd > glassStart) {
            panel(matrix, normal, glass, light, glassStart, glassEnd, 0F, GLASS_TOP, PANEL_Z);
        }

        // 门段：绕最外侧竖边旋转
        final float doorStart = startLocal;
        final float doorEnd = Math.min(startLocal + 1F, doorBlocks);
        if (doorEnd > doorStart && doorBlocks > 0F) {
            matrices.pushPose();
            matrices.translate(-totalBlocks / 2F, 0, 0);
            if (open) {
                matrices.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90F));
            }
            matrices.translate(totalBlocks / 2F, 0, 0);
            final PoseStack.Pose doorPose = matrices.last();
            panel(doorPose.pose(), doorPose.normal(), glass, light, doorStart, doorEnd, 0F, GLASS_TOP, PANEL_Z);
            matrices.popPose();
        }

        matrices.popPose();
    }

    private static void panel(Matrix4f matrix, Matrix3f normal, VertexConsumer vc, int light,
                              float x0, float x1, float y0, float y1, float z) {
        quad(matrix, normal, vc, light, x0, y0, z, x1, y1, z, Direction.UP);
    }

    private static void quad(Matrix4f matrix, Matrix3f normal, VertexConsumer vc, int light,
                             float x0, float y0, float z0, float x1, float y1, float z1, Direction direction) {
        float vx = direction.getStepX();
        float vy = direction.getStepY();
        float vz = direction.getStepZ();
        vc.vertex(matrix, x0, y0, z0).color(255, 255, 255, 255).uv(0F, 1F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, vx, vy, vz).endVertex();
        vc.vertex(matrix, x1, y0, z0).color(255, 255, 255, 255).uv(1F, 1F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, vx, vy, vz).endVertex();
        vc.vertex(matrix, x1, y1, z1).color(255, 255, 255, 255).uv(1F, 0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, vx, vy, vz).endVertex();
        vc.vertex(matrix, x0, y1, z1).color(255, 255, 255, 255).uv(0F, 0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, vx, vy, vz).endVertex();
    }
}
