package top.xiaotang27.trdm.client;

import mtr.block.IBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import top.xiaotang27.trdm.block.BlockTrdmPSDEndGate;

/**
 * 端门的「按住右键向下拖动 = 调开度」客户端状态机。
 * <p>
 * 约定（已确认的需求）：
 * <ul>
 *     <li><b>短按</b>（没拖动就松手）= 和以前一样开 / 关（0 档 ↔ 满档）；</li>
 *     <li><b>按住后向下拖</b> = 开度跟着鼠标走，向下 = 开大，向上 = 关小；</li>
 *     <li><b>松手</b> = 停在当前角度，落到最近的 1/15 档（状态的 16 档）；</li>
 *     <li><b>拖动期间玩家视角不动</b>：鼠标位移由 {@code MouseHandlerTurnMixin} 从
 *         {@code MouseHandler#turnPlayer} 里截下来喂给 {@link #onMouseDelta}，不再拿来转视角；</li>
 *     <li><b>不需要潜行</b>；潜行 + 刷子仍然是「屏蔽门外观设置」。</li>
 * </ul>
 * 状态机在客户端 tick 里跑；<b>连续角度</b>靠累积进来的鼠标位移算，所以拖动是平滑的，
 * 而方块状态只有 16 档（靠方块状态同步给其他人，不需要额外网络通道）。
 */
public final class TrdmEndGateDrag {

    /** 满开角（度）。 */
    public static final float MAX_ANGLE = 90F;
    /** 鼠标移动多少像素 = 转满 90°。 */
    private static final double PIXELS_PER_FULL_ANGLE = 200D;
    /** 累积位移超过这么多像素才算「拖动」（否则当短按）。 */
    private static final double DRAG_THRESHOLD_PX = 4D;
    /** 一格端门最多几格宽（和渲染端的 MAX_RUN 一致）。 */
    private static final int MAX_RUN = 64;

    /** 按下的那一格；null = 当前没有按住。 */
    private static BlockPos pressPos;
    /** 按下时的开度（度）。 */
    private static float pressAngle;
    /** 连续角度（度，0~90）：每次鼠标位移都更新，渲染端直接用它。 */
    private static float angle;
    /** 按下后累积的位移（用来判断是否进入拖动）。 */
    private static double accumulatedPx;
    /** 是否已经进入拖动（超过阈值）。 */
    private static boolean dragging;
    /** 拖动中最后一次发给服务端的档位。 */
    private static int lastSentStep;

    private TrdmEndGateDrag() {
    }

    /** 右键按下时（客户端 use 回调里）开始一次候选；已经在按着同一格就忽略原版的重复触发。 */
    public static void begin(BlockPos pos, BlockState state) {
        if (pressPos != null && pressPos.equals(pos)) {
            return;
        }
        pressPos = pos.immutable();
        final int step = BlockTrdmPSDEndGate.openStep(state);
        pressAngle = step / (float) BlockTrdmPSDEndGate.OPEN_STEPS * MAX_ANGLE;
        angle = pressAngle;
        accumulatedPx = 0D;
        dragging = false;
        lastSentStep = step;
    }

    /** 右键按住期间为 true（视角要被锁住的时间段）。 */
    public static boolean isPressing() {
        return pressPos != null;
    }

    /** 正在拖动某一段端门（渲染端据此画指示条）。 */
    public static boolean dragging() {
        return dragging && pressPos != null;
    }

    /**
     * 当前开度进度（0~1）给 HUD 指示条用；没在拖动时返回 -1（HUD 据此不画）。
     */
    public static float progress() {
        if (!dragging || pressPos == null) {
            return -1F;
        }
        return Math.max(0F, Math.min(1F, angle / MAX_ANGLE));
    }

    /**
     * 鼠标位移（来自 {@code MouseHandler}，像素；y 向下为正）。
     * 向下拖 = dy > 0 = 开大。
     */
    public static void onMouseDelta(double dx, double dy) {
        if (pressPos == null) {
            return;
        }
        accumulatedPx += Math.abs(dy);
        if (!dragging && accumulatedPx > DRAG_THRESHOLD_PX) {
            dragging = true;
        }
        if (!dragging) {
            return;
        }
        final float deltaAngle = (float) (dy / PIXELS_PER_FULL_ANGLE * MAX_ANGLE);
        angle = Math.max(0F, Math.min(MAX_ANGLE, angle + deltaAngle));
    }

    /**
     * 渲染端取这一段的显示角度（度）：正在拖动这一段时返回连续角度，否则返回状态量化角。
     */
    public static float angleDegrees(BlockState state, BlockPos renderedPos) {
        final float quantised = BlockTrdmPSDEndGate.openStep(state)
                / (float) BlockTrdmPSDEndGate.OPEN_STEPS * MAX_ANGLE;
        if (!dragging || pressPos == null) {
            return quantised;
        }
        return sameRun(renderedPos, pressPos) ? angle : quantised;
    }

    /** 指示条是否该画在这一段上（只有正在拖的那一段）。 */
    public static boolean indicatorAt(BlockPos renderedPos) {
        return dragging && pressPos != null && sameRun(renderedPos, pressPos);
    }

    /** 每客户端 tick 调一次：拖动中按档位变化同步给服务端，松手时落地。 */
    public static void tick(Minecraft client) {
        if (pressPos == null) {
            return;
        }
        final Level level = client.level;
        if (level == null || client.player == null) {
            reset();
            return;
        }
        // 按住期间方块被拆了 / 换掉了就放弃
        final BlockState state = level.getBlockState(pressPos);
        if (!(state.getBlock() instanceof BlockTrdmPSDEndGate)) {
            reset();
            return;
        }

        if (client.mouseHandler.isRightPressed()) {
            if (dragging) {
                final int step = quantise(angle);
                if (step != lastSentStep) {
                    lastSentStep = step;
                    TrdmControlClient.sendEndGateOpen(pressPos, step);
                }
            }
            return;
        }

        // 松手
        if (dragging) {
            // 停在当前角度：落到最近一档
            TrdmControlClient.sendEndGateOpen(pressPos, quantise(angle));
        } else {
            // 短按：开 / 关
            final int current = BlockTrdmPSDEndGate.openStep(state);
            TrdmControlClient.sendEndGateOpen(pressPos,
                    current > 0 ? 0 : BlockTrdmPSDEndGate.OPEN_STEPS);
        }
        reset();
    }

    /** 连续角度 → 状态档位（四舍五入）。 */
    private static int quantise(float degrees) {
        final int step = Math.round(degrees / MAX_ANGLE * BlockTrdmPSDEndGate.OPEN_STEPS);
        return Math.max(0, Math.min(BlockTrdmPSDEndGate.OPEN_STEPS, step));
    }

    private static void reset() {
        pressPos = null;
        dragging = false;
        accumulatedPx = 0D;
    }

    /**
     * 两格是不是同一段端门（同方块、同朝向、沿门排连着）。
     * 合并后的整段是一扇门，拖动其中一格时整段都要按连续角度显示。
     */
    private static boolean sameRun(BlockPos a, BlockPos b) {
        final Level level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        final BlockState state = level.getBlockState(b);
        if (!(state.getBlock() instanceof BlockTrdmPSDEndGate)) {
            return false;
        }
        final Direction facing = IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING);
        if (!(level.getBlockState(a).getBlock() instanceof BlockTrdmPSDEndGate)
                || IBlock.getStatePropertySafe(level.getBlockState(a), HorizontalDirectionalBlock.FACING) != facing) {
            return false;
        }
        // 沿门排两侧走一遍，看 a 是否落在同一段里
        for (final Direction direction : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockPos current = b;
            for (int i = 1; i <= MAX_RUN; i++) {
                current = current.relative(direction);
                final BlockState next = level.getBlockState(current);
                if (!(next.getBlock() instanceof BlockTrdmPSDEndGate)
                        || IBlock.getStatePropertySafe(next, HorizontalDirectionalBlock.FACING) != facing) {
                    break;
                }
                if (current.equals(a)) {
                    return true;
                }
            }
        }
        return a.equals(b);
    }
}
