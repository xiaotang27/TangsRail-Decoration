package top.xiaotang27.trdm.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.xiaotang27.trdm.client.TrdmEndGateDrag;

/**
 * 端门「按住右键拖动调开度」时，**不转视角**，把鼠标位移交给拖动状态机。
 * <p>
 * 原版 {@code MouseHandler#turnPlayer} 会把累积的鼠标位移拿去转玩家视角，然后把它清零。
 * 这里在它开头就把位移取走、清零并取消调用 —— 于是拖动期间视角纹丝不动，
 * 位移变成门的开度（向下拖 = 开大）。
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerTurnMixin {

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void trdm$dragInsteadOfTurn(CallbackInfo info) {
        if (!TrdmEndGateDrag.isPressing()) {
            return;
        }
        final double dx = this.accumulatedDX;
        final double dy = this.accumulatedDY;
        this.accumulatedDX = 0.0D;
        this.accumulatedDY = 0.0D;
        TrdmEndGateDrag.onMouseDelta(dx, dy);
        info.cancel();
    }
}
