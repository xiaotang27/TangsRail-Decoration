package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.MTRClient;
import mtr.block.BlockPSDAPGDoorBase;
import mtr.block.IBlock;
import mtr.data.IGui;
import mtr.mappings.BlockEntityRendererMapper;
import mtr.mappings.ModelDataWrapper;
import mtr.mappings.ModelMapper;
import mtr.mappings.UtilitiesClient;
import mtr.render.MoreRenderLayers;
import mtr.render.RenderTrains;
import mtr.render.StoredMatrixTransformations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.block.BlockTrdmPSDDoor;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 屏蔽门门扇渲染器，移植自 MTR 的 RenderPSDAPGDoor（只保留样式 1/2 的全高门分支）。
 * 贴图暂时仍指向 mtr:block 下的原版资源；要改外观时把这里的路径换成 trdm:block 并补同名 PNG 即可。
 */
public class RenderTrdmPSDDoor<T extends BlockTrdmPSDDoor.TileEntityTrdmPSDDoor> extends BlockEntityRendererMapper<T> implements IGui, IBlock {

    private static final ModelSingleCube MODEL_PSD = new ModelSingleCube(36, 18, 0, 0, 0, 16, 16, 2);
    private static final ModelSingleCube MODEL_PSD_END_LEFT_1 = new ModelSingleCube(20, 18, 0, 0, 0, 8, 16, 2);
    private static final ModelSingleCube MODEL_PSD_END_RIGHT_1 = new ModelSingleCube(20, 18, 8, 0, 0, 8, 16, 2);
    private static final ModelSingleCube MODEL_PSD_END_LEFT_2 = new ModelSingleCube(20, 18, 8, 0, 2, 8, 16, 2);
    private static final ModelSingleCube MODEL_PSD_END_RIGHT_2 = new ModelSingleCube(20, 18, 0, 0, 2, 8, 16, 2);
    /** 原版的灯模型（1×1×1 的方块，Box UV 每个面各占 1 个像素）。 */
    private static final ModelSingleCube MODEL_PSD_LIGHT_LEFT = new ModelSingleCube(16, 16, 0, -1, 5, 1, 1, 1);
    private static final ModelSingleCube MODEL_PSD_LIGHT_RIGHT = new ModelSingleCube(16, 16, 15, -1, 5, 1, 1, 1);
    /**
     * 灯沿<b>模型 z 轴</b>偏移多少（格）。原版灯在模型 z 5~6、x 0~1，位置仍是原版那套，
     * 只是在 z 方向让开吊板；左右灯用同一个偏移。
     */
    private static final float LIGHT_Z_OFFSET = 0.5F / 16F;

    /**
     * 灯在色带里垂直居中要往上挪多少（格）。吊板色带从 14.5/16 铺到 16/16（厚 1.5/16），灯高 1/16，
     * 所以正中的位置是往上让 (1.5 − 1) / 2 = 0.25/16。
     */
    private static final float LIGHT_Y_OFFSET = ((16F - 14.5F) - 1F) / 2F / 16F;
    private static final ModelSingleCube MODEL_PSD_DOOR_LOCKED = new ModelSingleCube(6, 6, 5, 6, 1, 6, 6, 0);

    private final int style;

    public RenderTrdmPSDDoor(BlockEntityRenderDispatcher dispatcher, int style) {
        super(dispatcher);
        this.style = style;
    }

    @Override
    public void render(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final Level world = entity.getLevel();
        if (world == null) {
            return;
        }

        final BlockPos pos = entity.getBlockPos();
        final Direction facing = IBlock.getStatePropertySafe(world, pos, BlockPSDAPGDoorBase.FACING);
        final boolean side = IBlock.getStatePropertySafe(world, pos, BlockPSDAPGDoorBase.SIDE) == EnumSide.RIGHT;
        final boolean half = IBlock.getStatePropertySafe(world, pos, BlockPSDAPGDoorBase.HALF) == DoubleBlockHalf.UPPER;
        final boolean end = IBlock.getStatePropertySafe(world, pos, BlockPSDAPGDoorBase.END);
        final boolean unlocked = IBlock.getStatePropertySafe(world, pos, BlockPSDAPGDoorBase.UNLOCKED);
        final float open = Math.min(entity.getOpen(MTRClient.getLastFrameDuration()), 1);

        final StoredMatrixTransformations storedMatrixTransformations = new StoredMatrixTransformations();
        storedMatrixTransformations.add(matricesNew -> {
            matricesNew.translate(0.5 + pos.getX(), pos.getY(), 0.5 + pos.getZ());
            UtilitiesClient.rotateYDegrees(matricesNew, -facing.toYRot());
            UtilitiesClient.rotateXDegrees(matricesNew, 180);
        });
        final StoredMatrixTransformations storedMatrixTransformationsLight = storedMatrixTransformations.copy();
        // 指示灯用「方块局部坐标」的变换（BE 渲染器拿到的 matrices 已经把方块放到原点，不能再加 pos）
        final StoredMatrixTransformations localMatrixTransformations = new StoredMatrixTransformations();
        localMatrixTransformations.add(matricesNew -> {
            matricesNew.translate(0.5, 0, 0.5);
            UtilitiesClient.rotateYDegrees(matricesNew, -facing.toYRot());
            UtilitiesClient.rotateXDegrees(matricesNew, 180);
        });

        if (half) {
            // 灯跟着门「有没有在动」走：行程中闪，停稳后定格（开着常亮、关着熄灭）
            final int openValue = entity.getOpenValue();
            final float target = openValue / (float) BlockPSDAPGDoorBase.MAX_OPEN_VALUE;
            // 开度停在中间值 = 门正在走；再加上「插值还没追上目标」这一条，
            // 兜住直接跳值（例如撤销手动开门时一帧设成 0）的情况
            final boolean inTransit = openValue > 0 && openValue < BlockPSDAPGDoorBase.MAX_OPEN_VALUE;
            final boolean moving = inTransit || Math.abs(open - target) > 0.05F;
            final boolean doorLightOn = TrdmDoorLight.isOn(entity, openValue, moving, world.getGameTime());
            final ResourceLocation lightTexture = new ResourceLocation(String.format("mtr:textures/block/light_%s.png", doorLightOn ? "on" : "off"));
            final RenderType lightRenderType = doorLightOn
                    ? MoreRenderLayers.getLight(lightTexture, false)
                    : MoreRenderLayers.getExterior(lightTexture);
            localMatrixTransformations.transform(matrices);
            // 原版位置再沿模型 +z 让开吊板；这个坐标系 y 是反的，所以「上移」是 -y
            matrices.translate(0, -LIGHT_Y_OFFSET, LIGHT_Z_OFFSET);
            (side ? MODEL_PSD_LIGHT_RIGHT : MODEL_PSD_LIGHT_LEFT)
                    .renderToBuffer(matrices, vertexConsumers.getBuffer(lightRenderType), light, overlay, 1, 1, 1, 1);
            matrices.popPose();
        }

        if (end) {
            RenderTrains.scheduleRender(new ResourceLocation(String.format("mtr:textures/block/psd_door_end_%s_%s_2_%s.png", half ? "top" : "bottom", side ? "right" : "left", style == 0 ? "1" : "2")), false, RenderTrains.QueuedRenderLayer.EXTERIOR, (matricesNew, vertexConsumer) -> {
                storedMatrixTransformationsLight.transform(matricesNew);
                matricesNew.translate(open / 2 * (side ? -1 : 1), 0, 0);
                (side ? MODEL_PSD_END_RIGHT_2 : MODEL_PSD_END_LEFT_2).renderToBuffer(matricesNew, vertexConsumer, light, overlay, 1, 1, 1, 1);
                matricesNew.popPose();
            });
        }

        storedMatrixTransformations.add(matricesNew -> matricesNew.translate(open * (side ? -1 : 1), 0, 0));

        if (end) {
            RenderTrains.scheduleRender(new ResourceLocation(String.format("mtr:textures/block/psd_door_end_%s_%s_1_%s.png", half ? "top" : "bottom", side ? "right" : "left", style == 0 ? "1" : "2")), false, RenderTrains.QueuedRenderLayer.EXTERIOR, (matricesNew, vertexConsumer) -> {
                storedMatrixTransformations.transform(matricesNew);
                (side ? MODEL_PSD_END_RIGHT_1 : MODEL_PSD_END_LEFT_1).renderToBuffer(matricesNew, vertexConsumer, light, overlay, 1, 1, 1, 1);
                matricesNew.popPose();
            });
        } else {
            RenderTrains.scheduleRender(new ResourceLocation(String.format("mtr:textures/block/psd_door_%s_%s_%s.png", half ? "top" : "bottom", side ? "right" : "left", style == 0 ? "1" : "2")), false, RenderTrains.QueuedRenderLayer.EXTERIOR, (matricesNew, vertexConsumer) -> {
                storedMatrixTransformations.transform(matricesNew);
                MODEL_PSD.renderToBuffer(matricesNew, vertexConsumer, light, overlay, 1, 1, 1, 1);
                matricesNew.popPose();
            });
        }

        if (half && !unlocked) {
            RenderTrains.scheduleRender(new ResourceLocation("mtr:textures/block/sign/door_not_in_use.png"), false, RenderTrains.QueuedRenderLayer.EXTERIOR, (matricesNew, vertexConsumer) -> {
                storedMatrixTransformations.transform(matricesNew);
                if (end) {
                    matricesNew.translate(side ? 0.25 : -0.25, 0, 0);
                }
                MODEL_PSD_DOOR_LOCKED.renderToBuffer(matricesNew, vertexConsumer, light, overlay, 1, 1, 1, 1);
                matricesNew.popPose();
            });
        }
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return true;
    }

    private static class ModelSingleCube extends EntityModel<Entity> {

        private final ModelMapper cube;

        private ModelSingleCube(int textureWidth, int textureHeight, int x, int y, int z, int length, int height, int depth) {
            final ModelDataWrapper modelDataWrapper = new ModelDataWrapper(this, textureWidth, textureHeight);
            cube = new ModelMapper(modelDataWrapper);
            cube.texOffs(0, 0).addBox(x - 8, y - 16, z - 8, length, height, depth, 0, false);
            modelDataWrapper.setModelPart(textureWidth, textureHeight);
            cube.setModelPart();
        }

        @Override
        public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            cube.render(matrices, vertices, 0, 0, 0, packedLight, packedOverlay);
        }

        @Override
        public void setupAnim(Entity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        }
    }
}
