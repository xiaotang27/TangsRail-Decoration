package top.xiaotang27.trdm.control;

import mtr.block.BlockPSDAPGDoorBase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 屏蔽门「接管」驱动，两种模式共用一个机制：
 * <ul>
 *   <li><b>手动保持开</b>（{@code open = true}）：集控装置手动开门后保持开启；</li>
 *   <li><b>隔离</b>（{@code open = false}）：站台锁死，门只能由本装置控制。</li>
 * </ul>
 * 两件事保证 MTR 影响不到这些门：
 * <ol>
 *   <li><b>掐掉列车的写入</b>：{@code TrainServerDoorMixin} 把 MTR
 *       {@code TrainServer.openDoors(...)} 里针对这些门的调用整个跳过
 *       （那是 MTR 唯一写门开度的地方，进站开门和排定自动关门都在里面）。
 *       因此门上的 {@code UNLOCKED} 不动 —— MTR 的「锁定贴纸」只在刷子锁门时出现；</li>
 *   <li><b>自己每 tick 压值</b>：表里存的是<b>门的位置</b>，所以装置所在区块卸载了也照样能驱动。</li>
 * </ol>
 * 开/关都按 MTR 的列车开门速度逐 tick 爬（{@link Train#DOOR_MOVE_TIME}），不是瞬移，
 * 所以手动开关门和列车自动开关门看起来一样快。
 */
public final class PsdManualHold {

    /** 每隔这么多 tick 重新找一次门（补上新放的、去掉被拆的），顺便检查装置还在不在。 */
    private static final int RESCAN_INTERVAL = 600;

    /**
     * 一个 tick 门开度推进多少（0~1）。
     * <p>
     * 照 MTR 列车开门的算法反推的：列车每 tick 让 {@code doorValue} 走
     * {@code 1 / DOOR_MOVE_TIME}（= 1/64），写进门的是
     * {@code clamp(doorValue * DOOR_MOVE_TIME, 0, MAX_OPEN_VALUE)}，
     * 也就是每 tick 正好 <b>1 个开度单位</b>、{@code MAX_OPEN_VALUE}（32）tick 走完全行程。
     * 所以这里用 {@code 1 / MAX_OPEN_VALUE}，手动开关门和列车自动开关门速度一致。
     */
    private static final float STEP_PER_TICK = 1F / BlockPSDAPGDoorBase.MAX_OPEN_VALUE;

    /** 谁在保持：装置位置 → 保持信息（只在服务端内存里，不进存档）。 */
    private static final Map<GlobalPos, Entry> HELD = new HashMap<>();
    /** 被接管的所有门位置（维度 + 坐标），给 mixin 做 O(1) 判断。 */
    private static final Set<GlobalPos> HELD_DOORS = new HashSet<>();

    private PsdManualHold() {
    }

    private static final class Entry {

        private final long platformId;
        private final List<BlockPos> doors = new ArrayList<>();
        /** true = 保持开启；false = 保持关闭（隔离）。 */
        private boolean open;
        /** 门当前开度 0~1（每 tick 向 {@link #open} 靠一步）。 */
        private float progress = -1F;
        /** 已经交还控制、正在等门关完就撤表（取消手动开门时用）。 */
        private boolean releaseWhenClosed;
        private int rescanTimer;

        private Entry(long platformId) {
            this.platformId = platformId;
        }
    }

    /** 在主入口里调一次：注册全局 tick。 */
    public static void registerServerTick() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (HELD.isEmpty()) {
                return;
            }
            final Iterator<Map.Entry<GlobalPos, Entry>> iterator = HELD.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<GlobalPos, Entry> mapEntry = iterator.next();
                final GlobalPos devicePos = mapEntry.getKey();
                final Entry entry = mapEntry.getValue();
                final ServerLevel world = server.getLevel(devicePos.dimension());
                if (world == null) {
                    iterator.remove();
                    refreshDoorIndex();
                    continue;
                }

                if (--entry.rescanTimer <= 0) {
                    entry.rescanTimer = RESCAN_INTERVAL;
                    // 装置被删了（非正常破坏）就别再压门了
                    if (world.hasChunkAt(devicePos.pos()) && world.getBlockEntity(devicePos.pos()) == null) {
                        PsdDoorControl.apply(world, entry.doors, 0);
                        iterator.remove();
                        refreshDoorIndex();
                        continue;
                    }
                    final List<BlockPos> found = PsdDoorControl.findDoors(world, entry.platformId);
                    entry.doors.clear();
                    if (found != null) {
                        entry.doors.addAll(found);
                    }
                    entry.progress = sampleProgress(world, entry.doors);
                    refreshDoorIndex();
                }

                if (entry.doors.isEmpty()) {
                    continue;
                }
                if (entry.progress < 0F) {
                    entry.progress = sampleProgress(world, entry.doors);
                }

                // 按 MTR 列车的行程速度爬，不瞬移
                entry.progress = Mth.clamp(entry.progress + (entry.open ? STEP_PER_TICK : -STEP_PER_TICK), 0F, 1F);
                PsdDoorControl.apply(world, entry.doors, Math.round(entry.progress * BlockPSDAPGDoorBase.MAX_OPEN_VALUE));

                // 交还控制后等门关完再撤表，这样关门动画也是完整的一趟
                if (entry.releaseWhenClosed && !entry.open && entry.progress <= 0F) {
                    iterator.remove();
                    refreshDoorIndex();
                }
            }
        });
    }

    /**
     * 开始 / 更新接管：记下门位置，目标状态由 {@code open} 决定，门会从当前开度按列车速度爬过去。
     *
     * @param open true = 保持开启，false = 保持关闭（隔离）
     * @return 这次接管了几扇门
     */
    public static int hold(ServerLevel world, BlockPos devicePos, long platformId, List<BlockPos> doors, boolean open) {
        final Entry entry = HELD.computeIfAbsent(GlobalPos.of(world.dimension(), devicePos), key -> new Entry(platformId));
        entry.open = open;
        entry.releaseWhenClosed = false;
        entry.doors.clear();
        entry.doors.addAll(doors);
        entry.progress = sampleProgress(world, doors);
        refreshDoorIndex();
        return PsdDoorControl.apply(world, entry.doors, Math.round(entry.progress * BlockPSDAPGDoorBase.MAX_OPEN_VALUE));
    }

    /**
     * 只登记接管状态，门列表留到下一个 tick 自己扫（区块重新加载后用）。
     */
    public static void register(ServerLevel world, BlockPos devicePos, long platformId, boolean open) {
        final Entry entry = HELD.computeIfAbsent(GlobalPos.of(world.dimension(), devicePos), key -> new Entry(platformId));
        entry.open = open;
        entry.progress = -1F;
    }

    /**
     * 结束接管（交还 MTR 的列车逻辑）。
     *
     * @param closeDoors true = 先把门关回去（按列车速度关，关完才撤表）；
     *                   false = 立刻撤表，门保持现状等 MTR 处理（取消隔离时用）
     * @return 这次有多少扇门进入关闭流程
     */
    public static int release(ServerLevel world, BlockPos devicePos, boolean closeDoors) {
        final GlobalPos key = GlobalPos.of(world.dimension(), devicePos);
        final Entry entry = HELD.get(key);
        if (entry == null) {
            return 0;
        }

        if (closeDoors && entry.progress > 0.001F) {
            // 停在这里继续压「关」，关到 0 再撤表 —— 关门也是完整一趟动画
            entry.open = false;
            entry.releaseWhenClosed = true;
            return entry.doors.size();
        }

        HELD.remove(key);
        refreshDoorIndex();
        return closeDoors ? PsdDoorControl.apply(world, entry.doors, 0) : 0;
    }

    /**
     * 这个位置的门是不是正被某个集控装置接管（mixin 用）。
     * MTR 扫描到的门位置可能落在门的任意一半上，所以上下各看一格。
     */
    public static boolean isHeldDoor(Level world, BlockPos pos) {
        if (HELD_DOORS.isEmpty()) {
            return false;
        }
        final ResourceKey<Level> dimension = world.dimension();
        return HELD_DOORS.contains(GlobalPos.of(dimension, pos))
                || HELD_DOORS.contains(GlobalPos.of(dimension, pos.above()))
                || HELD_DOORS.contains(GlobalPos.of(dimension, pos.below()));
    }

    /** 这个装置是否正在接管（调试用）。 */
    public static boolean isHolding(Level world, BlockPos devicePos) {
        return HELD.containsKey(GlobalPos.of(world.dimension(), devicePos));
    }

    private static void refreshDoorIndex() {
        HELD_DOORS.clear();
        for (final Map.Entry<GlobalPos, Entry> mapEntry : HELD.entrySet()) {
            final ResourceKey<Level> dimension = mapEntry.getKey().dimension();
            for (final BlockPos pos : mapEntry.getValue().doors) {
                HELD_DOORS.add(GlobalPos.of(dimension, pos));
            }
        }
    }

    /** 门现在的实际开度（0~1），用来决定爬升的起点，不会瞬移。 */
    private static float sampleProgress(ServerLevel world, List<BlockPos> doors) {
        for (final BlockPos pos : doors) {
            if (world.getBlockEntity(pos) instanceof BlockPSDAPGDoorBase.TileEntityPSDAPGDoorBase door) {
                return Mth.clamp(door.getOpenValue() / (float) BlockPSDAPGDoorBase.MAX_OPEN_VALUE, 0F, 1F);
            }
        }
        return 0F;
    }
}
