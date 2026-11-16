package top.xiaotang27.trdm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * 可染色顶部灯的「颜色传染」。
 * <p>
 * 规则：
 * <ul>
 *     <li>新装的可染色灯默认继承<b>相邻连通的门楣 / 延伸板</b>上最近一盏可染色灯的颜色；</li>
 *     <li>玩家在「设置颜色」里改过的那盏灯标成手动，不再跟随；</li>
 *     <li>手动改色会把新颜色传染给连通的、没手动设过的灯；遇到手动灯就停下，
 *         避免越过别人单独定制的颜色。</li>
 * </ul>
 * 连通的判定只看门楣 / 延伸板方块本身（六方向相邻），所以拐角、直段、端墙段都能连起来。
 */
public final class PsdLightColors {

    /** 单次搜索最多走多少格，防止超长门排一次扫太多。 */
    private static final int MAX_VISITED = 256;

    private PsdLightColors() {
    }

    /** 找这一格附近（连通的）最近一盏可染色灯的颜色；没有就回默认白色。 */
    public static int findNearbyColor(Level level, BlockPos origin) {
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        enqueueNeighbours(origin, queue);
        while (!queue.isEmpty() && seen.size() < MAX_VISITED) {
            final BlockPos pos = queue.poll();
            if (!seen.add(pos)) {
                continue;
            }
            if (!isLightBoard(level, pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof PsdLightHolder holder
                    && holder.topLight() == PsdLightKind.COLOR) {
                return holder.topLightColor();
            }
            enqueueNeighbours(pos, queue);
        }
        return PsdLightHolder.DEFAULT_COLOR;
    }

    /**
     * 把手动设置的颜色传染给连通的、非手动的可染色灯。手动灯是边界：不改它，也不穿过它。
     * 只在服务端调用（客户端等方块实体同步回来）。
     */
    public static void propagate(Level level, BlockPos origin, int rgb) {
        if (level.isClientSide) {
            return;
        }
        final int color = PsdLightHolder.sanitizeColor(rgb);
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        enqueueNeighbours(origin, queue);
        while (!queue.isEmpty() && seen.size() < MAX_VISITED) {
            final BlockPos pos = queue.poll();
            if (!seen.add(pos)) {
                continue;
            }
            if (!isLightBoard(level, pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof PsdLightHolder holder) {
                if (holder.topLight() == PsdLightKind.COLOR) {
                    if (holder.isTopLightColorManual()) {
                        // 手动灯是边界：不改它，也不再往它后面传
                        continue;
                    }
                    holder.inheritTopLightColor(color);
                }
            }
            enqueueNeighbours(pos, queue);
        }
    }

    private static boolean isLightBoard(Level level, BlockPos pos) {
        final net.minecraft.world.level.block.Block block = level.getBlockState(pos).getBlock();
        return block instanceof BlockTrdmPSDTop || block instanceof BlockTrdmPSDExtension;
    }

    private static void enqueueNeighbours(BlockPos pos, ArrayDeque<BlockPos> queue) {
        for (final Direction direction : Direction.values()) {
            queue.add(pos.relative(direction));
        }
    }
}
