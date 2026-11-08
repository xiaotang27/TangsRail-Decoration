package top.xiaotang27.trdm.mixin;

import mtr.data.TicketSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.xiaotang27.trdm.ticket.TicketGateHandler;

/** 接管 MTR 闸机/检票机的通过判定：TRDM 票务启用时改用 TRDM 车票与交通卡。 */
@Mixin(TicketSystem.class)
public abstract class TicketSystemMixin {

    @Inject(method = "passThrough", at = @At("HEAD"), cancellable = true, require = 0)
    private static void trdm$passThrough(Level world, BlockPos pos, Player player, boolean isEntrance,
                                         boolean isExit, SoundEvent entranceSound, SoundEvent concessionarySound,
                                         SoundEvent exitSound, SoundEvent exitConcessionarySound,
                                         SoundEvent failSound, boolean printMessage,
                                         CallbackInfoReturnable<TicketSystem.EnumTicketBarrierOpen> cir) {
        if (!(world instanceof ServerLevel level)) {
            return;
        }
        final TicketSystem.EnumTicketBarrierOpen result =
                TicketGateHandler.handle(level, pos, player, isEntrance, isExit);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
