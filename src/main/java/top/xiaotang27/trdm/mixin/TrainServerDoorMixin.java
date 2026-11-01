package top.xiaotang27.trdm.mixin;

import mtr.data.TrainServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.xiaotang27.trdm.control.PsdManualHold;

/**
 * 让 MTR 的列车完全不去动「被集控装置接管」的屏蔽门。
 * <p>
 */
@Mixin(TrainServer.class)
public abstract class TrainServerDoorMixin {

    @Inject(
            method = "openDoors(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;I)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void trdm$skipDoorsHeldByControlDevice(Level world, Block block, BlockPos checkPos, int dwellTicks,
                                                   CallbackInfoReturnable<Boolean> callbackInfo) {
        if (PsdManualHold.isHeldDoor(world, checkPos)) {
            callbackInfo.setReturnValue(false);
        }
    }
}
