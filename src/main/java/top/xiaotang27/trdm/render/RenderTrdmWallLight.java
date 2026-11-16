package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import mtr.mappings.BlockEntityRendererMapper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import top.xiaotang27.trdm.block.BlockTrdmWallLight;

/**
 * 独立放置的「墙上的荧光灯管」（模型 1）。
 * 方块状态只记底座朝向，具体摆位由 {@link TrdmLightRenderer#renderWallLight} 算。
 */
public class RenderTrdmWallLight extends BlockEntityRendererMapper<BlockTrdmWallLight.TileEntityTrdmWallLight> {

    public RenderTrdmWallLight(BlockEntityRenderDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(BlockTrdmWallLight.TileEntityTrdmWallLight entity, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay) {
        final Level world = entity.getLevel();
        if (world == null) {
            return;
        }
        final BlockPos pos = entity.getBlockPos();
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmWallLight)) {
            return;
        }
        TrdmLightRenderer.renderWallLight(state, matrices, vertexConsumers, light);
    }
}
