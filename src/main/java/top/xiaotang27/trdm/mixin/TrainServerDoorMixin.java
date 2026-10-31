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
 * 为什么不直接改门上的 {@code UNLOCKED}：那会带出 MTR 的「锁定贴纸」外观，
 * 而需求是<b>只有刷子手动锁的门才显示贴纸</b>，集控装置隔离的门要保持原样。
 * 所以这里从列车这一侧动手：{@code TrainServer.openDoors(...)} 是 MTR 唯一会写门开度的地方
 * （进站开门 + 排定离开时的自动关门），对它下手等于把整条自动链路掐掉。
 * <p>
 * 只对「正被集控装置保持 / 隔离」的那些门生效，其它站台的门照常由列车控制。
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
