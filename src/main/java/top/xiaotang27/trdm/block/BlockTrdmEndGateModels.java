package top.xiaotang27.trdm.block;

import mtr.block.IBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * <b>纯技术方块，永远不会出现在世界里</b>：它只有一个用途 —— 让方块状态表把
 * 端门门扇的 4 个模型（left / mid / right / single）烘进模型管理器，
 * 这样渲染器可以用 {@code renderSingleBlock(状态)} 直接画出整扇 2 格高的门扇。
 * <p>
 * 为什么不把模型挂在端门方块自己身上：端门的 blockstate 必须全是空模型
 * （开合两个方向都要由渲染器做动画，静态玻璃会抢镜），所以要另找一个地方摆放这些模型。
 */
public class BlockTrdmEndGateModels extends Block {

    /** 门扇在门排里的位置，对应 4 个模型。 */
    public static final EnumProperty<IBlock.EnumSide> SIDE =
            EnumProperty.create("side", IBlock.EnumSide.class);

    public BlockTrdmEndGateModels() {
        super(Properties.of().noCollission().noOcclusion().strength(-1.0F, 3600000.0F));
        registerDefaultState(defaultBlockState().setValue(SIDE, IBlock.EnumSide.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDE);
    }
}
