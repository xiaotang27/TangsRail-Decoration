package top.xiaotang27.trdm.client;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import top.xiaotang27.trdm.block.BlockTrdmPSDEndGate;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 端门开合角的**客户端缓动**：让「右键单击开门」也有旋转动画，而不是瞬间跳到 90°。
 * <p>
 * 做法：渲染时不给它状态里的量化角，而是让一个缓存角度以固定角速度追过去
 * （状态一变，门就在 ~0.4 秒里转到目标角度）。
 * <ul>
 *     <li><b>开着</b>：从当前角缓动到目标角（单击开门 = 0° → 90°，一段一段的档位变化也会被抹平）；</li>
 *     <li><b>关着</b>：直接归零、不做关闭动画 —— 关门时静态玻璃模型会立刻回到这格，
 *         再叠一个正在转的门扇会重影，所以关闭仍然是瞬时的；</li>
 *     <li><b>拖动中</b>：完全跟手（{@link TrdmEndGateDrag} 的连续角度），松手后从这里继续缓动。</li>
 * </ul>
 * 缓存按「渲染时用的那一格」作为键（渲染器只从铰链那一格画），条目会定期清理。
 */
public final class TrdmEndGateAnim {

    /** 缓动角速度（度/秒）。90° 大约 0.41 秒转完。 */
    private static final float DEGREES_PER_SECOND = 220F;
    /** 单帧最多推进的时长（毫秒）：避免刚打开的第一帧因为时间差太大直接跳到位。 */
    private static final long MAX_FRAME_MS = 100L;
    /** 多久没被问过就清掉这条缓存（毫秒）。 */
    private static final long STALE_MS = 10_000L;
    /** 缓存超过这个条数就顺手清理一次。 */
    private static final int PRUNE_THRESHOLD = 128;

    private static final Map<BlockPos, Entry> CACHE = new HashMap<>();

    private TrdmEndGateAnim() {
    }

    private static final class Entry {
        private float angle;
        private long atMs;

        private Entry(float angle, long atMs) {
            this.angle = angle;
            this.atMs = atMs;
        }
    }

    /** 这一格该按多少度来画（含缓动 / 拖动跟手）。 */
    public static float angleDegrees(BlockState state, BlockPos pos) {
        final int step = BlockTrdmPSDEndGate.openStep(state);
        final float target = step / (float) BlockTrdmPSDEndGate.OPEN_STEPS * TrdmEndGateDrag.MAX_ANGLE;
        final long now = Util.getMillis();
        final Entry entry = CACHE.computeIfAbsent(pos.immutable(), key -> new Entry(0F, now));

        // 关着：不做关闭动画（静态玻璃已经回来了），直接归零
        if (step <= 0) {
            entry.angle = 0F;
            entry.atMs = now;
            return 0F;
        }
        // 拖动中：跟手，并把进度写回缓存，松手后从这里继续缓动
        if (TrdmEndGateDrag.indicatorAt(pos)) {
            final float drag = TrdmEndGateDrag.angleDegrees(state, pos);
            entry.angle = drag;
            entry.atMs = now;
            return drag;
        }

        final long dt = Math.min(MAX_FRAME_MS, Math.max(0L, now - entry.atMs));
        entry.atMs = now;
        final float maxDelta = DEGREES_PER_SECOND * dt / 1000F;
        final float delta = target - entry.angle;
        if (Math.abs(delta) <= maxDelta) {
            entry.angle = target;
        } else {
            entry.angle += Math.copySign(maxDelta, delta);
        }
        prune(now);
        return entry.angle;
    }

    /** 清理太久没被问过的条目（端门被拆掉之后不会一直留在内存里）。 */
    private static void prune(long now) {
        if (CACHE.size() < PRUNE_THRESHOLD) {
            return;
        }
        final Iterator<Map.Entry<BlockPos, Entry>> iterator = CACHE.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().atMs > STALE_MS) {
                iterator.remove();
            }
        }
    }
}
