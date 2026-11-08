package top.xiaotang27.trdm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import top.xiaotang27.trdm.Trdm;

/**
 * 售票机：占位方块，右键由客户端打开售票界面（模型/贴图后续替换）。
 */
public class BlockTicketVendingMachine extends Block {

    public BlockTicketVendingMachine() {
        super(Properties.of().strength(1F));
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hitResult) {
        if (world.isClientSide) {
            top.xiaotang27.trdm.client.TicketClient.openVending(pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public Item asItem() {
        return Trdm.TICKET_VENDING_MACHINE_ITEM;
    }
}
