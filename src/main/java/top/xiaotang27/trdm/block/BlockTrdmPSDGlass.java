package top.xiaotang27.trdm.block;

import mtr.block.BlockPSDAPGGlassBase;
import mtr.mappings.BlockEntityMapper;
import mtr.mappings.EntityBlockMapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import top.xiaotang27.trdm.Trdm;

/**
 * TRD 屏蔽门玻璃。横向拼接完全由 MTR 基类负责。
 * <p>
 * 蹲下右键打开外观界面（含「端门」开关）；非蹲下右键且已标记为端门时，只切换本格的开门状态。
 * 端门标记与开门状态存在方块实体里（不动 blockstate，避免影响 MTR 的多变体模型）。
 */
public class BlockTrdmPSDGlass extends BlockPSDAPGGlassBase implements EntityBlockMapper {

    public BlockTrdmPSDGlass() {
        super();
    }

    /** 该格是否是端门。 */
    public static boolean isEndGate(BlockGetter world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof TileEntityTrdmPSDGlass glass && glass.isEndGate();
    }

    /** 该格端门是否开着。 */
    public static boolean isOpen(BlockGetter world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof TileEntityTrdmPSDGlass glass && glass.isOpen();
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hitResult) {
        final InteractionResult appearance = BlockTrdmPSDTop.openAppearanceOnShiftBrush(world, pos, player);
        if (appearance != null) {
            return appearance;
        }
        if (!world.isClientSide && player.isShiftKeyDown()
                && world.getBlockEntity(pos) instanceof TileEntityTrdmPSDGlass glass0) {
            // 空手蹲右键：切换端门标记
            glass0.setEndGate(!glass0.isEndGate());
            world.sendBlockUpdated(pos, state, state, 3);
            return InteractionResult.SUCCESS;
        }
        if (!world.isClientSide && world.getBlockEntity(pos) instanceof TileEntityTrdmPSDGlass glass && glass.isEndGate()) {
            glass.setOpen(!glass.isOpen());
            world.sendBlockUpdated(pos, state, state, 3);
            return InteractionResult.SUCCESS;
        }
        return super.use(state, world, pos, player, hand, hitResult);
    }

    @Override
    public BlockEntityMapper createBlockEntity(BlockPos pos, BlockState state) {
        return new TileEntityTrdmPSDGlass(pos, state);
    }

    @Override
    public Item asItem() {
        return Trdm.PSD_GLASS_ITEM_1;
    }

    public BlockEntityType<? extends BlockEntityMapper> blockEntityType() {
        return Trdm.PSD_GLASS_BE;
    }

    /** 端门标记：false = 普通玻璃；true = 端门（加粗边框、无装饰条、门楣带灯）。 */
    public static class TileEntityTrdmPSDGlass extends BlockEntityMapper {

        private boolean endGate;
        private boolean open;

        public TileEntityTrdmPSDGlass(BlockPos pos, BlockState state) {
            super(Trdm.PSD_GLASS_BE, pos, state);
        }

        public boolean isEndGate() {
            return endGate;
        }

        public void setEndGate(boolean endGate) {
            this.endGate = endGate;
            setChanged();
        }

        public boolean isOpen() {
            return open;
        }

        public void setOpen(boolean open) {
            this.open = open;
            setChanged();
        }
    }
}
