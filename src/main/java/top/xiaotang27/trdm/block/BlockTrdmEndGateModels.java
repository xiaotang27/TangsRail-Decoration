package top.xiaotang27.trdm.block;

import mtr.block.IBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * <b>纯技术方块，永远不会出现在世界里</b>：它只有一个用途 —— 让方块状态表把
 * 端门门扇的 4 个模型（left / mid / right / single）烘进模型管理器，
 * 这样渲染器可以用 {@code renderSingleBlock(状态)} 直接画出整扇 2 格高的门扇。
 * <p>
 * 朝向也交给这里：{@code facing} 在方块状态表里按 MTR 玻璃同样的 {@code y} 旋转表出图
 * （west 0° / north 90° / east 180° / south 270°），渲染器就不用自己算旋转了 ——
 * 手写旋转很容易和 MTR 的模型坐标系搞反。
 */
public class BlockTrdmEndGateModels extends Block {

    /** 门扇在门排里的位置，对应 4 个模型。 */
    public static final EnumProperty<IBlock.EnumSide> SIDE =
            EnumProperty.create("side", IBlock.EnumSide.class);

    public BlockTrdmEndGateModels() {
        super(Properties.of().noCollission().noOcclusion().strength(-1.0F, 3600000.0F));
        registerDefaultState(defaultBlockState()
                .setValue(SIDE, IBlock.EnumSide.SINGLE)
                .setValue(HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.WEST));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDE, HorizontalDirectionalBlock.FACING);
    }
}
