package top.xiaotang27.trdm.client;

import mtr.block.IBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import top.xiaotang27.trdm.block.BlockTrdmPSDEndGate;

/**
 * 端门的「按住右键向下拖动 = 调开度」客户端状态机。
 * <p>
 * 约定（已确认的需求）：
 * <ul>
 *     <li><b>短按</b>（没拖动就松手）= 和以前一样开 / 关（0 档 ↔ 满档）；</li>
 *     <li><b>按住后向下拖</b> = 开度跟着鼠标走，向下 = 开大，向上 = 关小；</li>
 *     <li><b>松手</b> = 停在当前角度，落到最近的 1/15 档（状态的 16 档）；</li>
 *     <li><b>不需要潜行</b>；潜行 + 刷子仍然是「屏蔽门外观设置」。</li>
 * </ul>
 * 状态机在客户端 tick 里跑；<b>连续角度</b>则是每次渲染时按当前鼠标位置现算的，
 * 所以拖动看着是平滑的，而方块状态只有 16 档（靠方块状态同步给其他人，不需要额外网络通道）。
 */
public final class TrdmEndGateDrag {

    /** 满开角（度）。 */
    public static final float MAX_ANGLE = 90F;
    /** 鼠标移动多少像素 = 转满 90°。 */
    private static final double PIXELS_PER_FULL_ANGLE = 200D;
    /** 鼠标移动超过这么多像素才算「拖动」（否则当短按）。 */
    private static final double DRAG_THRESHOLD_PX = 4D;
    /** 一格端门最多几格宽（和渲染端的 MAX_RUN 一致）。 */
    private static final int MAX_RUN = 64;

    /** 按下的那一格；null = 当前没有按住。 */
    private static BlockPos pressPos;
    /** 按下时的开度（度）与鼠标 Y。 */
    private static float pressAngle;
    private static double pressMouseY;
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
        pressAngle = BlockTrdmPSDEndGate.openStep(state) / (float) BlockTrdmPSDEndGate.OPEN_STEPS * MAX_ANGLE;
        dragging = false;
        lastSentStep = BlockTrdmPSDEndGate.openStep(state);
        pressMouseY = Minecraft.getInstance().mouseHandler.ypos();
    }

    /** 当前是否正在拖动某一段端门（渲染端据此画指示条）。 */
    public static boolean dragging() {
        return dragging && pressPos != null;
    }

    /**
     * 渲染端取这一段的显示角度（度）：正在拖动这一段时返回连续角度，否则返回状态量化角。
     */
    public static float angleDegrees(BlockState state, BlockPos renderedPos) {
        if (!dragging || pressPos == null) {
            return BlockTrdmPSDEndGate.openStep(state) / (float) BlockTrdmPSDEndGate.OPEN_STEPS * MAX_ANGLE;
        }
        return sameRun(renderedPos, pressPos) ? currentAngle() : BlockTrdmPSDEndGate.openStep(state)
                / (float) BlockTrdmPSDEndGate.OPEN_STEPS * MAX_ANGLE;
    }

    /** 指示条是否该画在这一段上（只有正在拖的那一段）。 */
    public static boolean indicatorAt(BlockPos renderedPos) {
        return dragging && pressPos != null && sameRun(renderedPos, pressPos);
    }

    /** 每客户端 tick 调一次：判断是否进入拖动、拖动中给服务端发档位、松手时发最终档位。 */
    public static void tick(Minecraft client) {
        if (pressPos == null) {
            return;
        }
        final boolean holdingRight = client.mouseHandler.isRightPressed();
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

        if (!dragging && Math.abs(client.mouseHandler.ypos() - pressMouseY) > DRAG_THRESHOLD_PX) {
            dragging = true;
        }

        if (holdingRight) {
            if (dragging) {
                final int step = quantise(currentAngle());
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
            TrdmControlClient.sendEndGateOpen(pressPos, quantise(currentAngle()));
        } else {
            // 短按：开 / 关
            final int current = BlockTrdmPSDEndGate.openStep(state);
            TrdmControlClient.sendEndGateOpen(pressPos,
                    current > 0 ? 0 : BlockTrdmPSDEndGate.OPEN_STEPS);
        }
        reset();
    }

    /** 按当前鼠标位置算出的连续角度（度，0~90）。向下拖 = 开大。 */
    private static float currentAngle() {
        final double deltaPx = Minecraft.getInstance().mouseHandler.ypos() - pressMouseY;
        final float deltaAngle = (float) (deltaPx / PIXELS_PER_FULL_ANGLE * MAX_ANGLE);
        return Math.max(0F, Math.min(MAX_ANGLE, pressAngle + deltaAngle));
    }

    /** 连续角度 → 状态档位（四舍五入）。 */
    private static int quantise(float angle) {
        final int step = Math.round(angle / MAX_ANGLE * BlockTrdmPSDEndGate.OPEN_STEPS);
        return Math.max(0, Math.min(BlockTrdmPSDEndGate.OPEN_STEPS, step));
    }

    private static void reset() {
        pressPos = null;
        dragging = false;
    }

    /**
     * 两格是不是同一段端门（同方块、同朝向、沿门排连着）。
     * 合并后的整段是一扇门，拖动其中一格时整段都要按连续角度显示。
     */
    private static boolean sameRun(BlockPos a, BlockPos b) {
        final Minecraft client = Minecraft.getInstance();
        final Level level = client.level;
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
