package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import mtr.mappings.BlockEntityRendererMapper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import top.xiaotang27.trdm.block.BlockTrdmPSDExtension;

/**
 * 延伸板上的顶部灯。延伸板本体还是静态方块模型，这里只负责把挂在方块实体里的灯画出来
 * （模型往轨道那侧探出 10 像素，方块自己的模型放不下，也不该占额外的碰撞箱）。
 */
public class RenderTrdmPSDExtension<T extends BlockTrdmPSDExtension.TileEntityTrdmPSDExtension>
        extends BlockEntityRendererMapper<T> {

    public RenderTrdmPSDExtension(BlockEntityRenderDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers,
                       int light, int overlay) {
        final Level world = entity.getLevel();
        if (world == null) {
            return;
        }
        final BlockPos pos = entity.getBlockPos();
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmPSDExtension)) {
            return;
        }
        TrdmLightRenderer.renderAttachment(state, entity, matrices, vertexConsumers, light);
    }
}
