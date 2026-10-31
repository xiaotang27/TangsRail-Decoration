package top.xiaotang27.trdm.render;

import mtr.client.ClientData;
import mtr.data.RailwayData;
import mtr.data.Route;
import mtr.data.ScheduleEntry;
import net.minecraft.core.BlockPos;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PIS 屏幕的数据层：把 MTR3 的到站信息换成 {@link TrdmPisPanel} 要的一帧数据。
 * <p>
 * 数据源就是 MTR3 自己那块 PIDS 用的东西：
 * <ul>
 *   <li>到站列表：{@code ClientData.SCHEDULES_FOR_PLATFORM}（站台 id → 到站条目，按 {@code arrivalMillis} 排序）</li>
 *   <li>终点站：{@code ClientData.DATA_CACHE.routeIdMap.get(routeId)} +
 *       {@code ClientData.DATA_CACHE.getFormattedRouteDestination(route, currentStationIndex, ...)}</li>
 *   <li>编组节数：{@link ScheduleEntry#trainCars}</li>
 * </ul>
 * 倒计时直接由 {@code arrivalMillis - System.currentTimeMillis()} 算，和 MTR4 版
 * （JCM 的 {@code arrivalTime()}）语义完全一样。
 */
public final class TrdmPisData {

    private static final String[] WEEKDAYS = {"星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};

    private TrdmPisData() {
    }

    /** 方块所在站台的 id（没手动选就用最近站台）。 */
    public static long resolvePlatformId(BlockPos pos, long stored) {
        if (stored != 0) {
            return stored;
        }
        return RailwayData.getClosePlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE, pos);
    }

    /** 一帧的完整数据（跑马灯偏移由渲染端给）。 */
    public static TrdmPisPanel.View view(long platformId, String ticker, float tickerOffset) {
        final List<ScheduleEntry> arrivals = arrivals(platformId);
        final TrdmPisPanel.Arrival first = arrivals.size() > 0 ? toArrival(arrivals.get(0)) : null;
        final TrdmPisPanel.Arrival second = arrivals.size() > 1 ? toArrival(arrivals.get(1)) : null;

        final Date now = new Date();
        final String clock = new SimpleDateFormat("HH:mm", Locale.ROOT).format(now);
        // 日期按参考图的写法留空格：2026 / 03 / 25 星期三
        final String date = new SimpleDateFormat("yyyy / MM / dd", Locale.ROOT).format(now) + " "
                + WEEKDAYS[now.getDay() % 7];
        return new TrdmPisPanel.View(first, second, clock, date, ticker, tickerOffset);
    }

    /**
     * 这个站台的到站列表（按到达时间升序）。
     * <p>
     * {@code SCHEDULES_FOR_PLATFORM} 里每个站台对应一个集合，元素是 {@link ScheduleEntry}；
     * 这里不依赖它的具体泛型（List / Set 都行），逐个挑出 {@code ScheduleEntry} 再排序。
     */
    @SuppressWarnings("unchecked")
    public static List<ScheduleEntry> arrivals(long platformId) {
        if (platformId == 0) {
            return List.of();
        }
        final Map<Long, ?> schedules = (Map<Long, ?>) (Map<?, ?>) ClientData.SCHEDULES_FOR_PLATFORM;
        if (schedules == null) {
            return List.of();
        }
        final Object value = schedules.get(platformId);
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        final List<ScheduleEntry> entries = new ArrayList<>();
        for (final Object element : collection) {
            if (element instanceof ScheduleEntry entry) {
                entries.add(entry);
            }
        }
        entries.sort(Comparator.naturalOrder());
        return entries;
    }

    /** 一趟车 → 版面要的数据。 */
    private static TrdmPisPanel.Arrival toArrival(ScheduleEntry entry) {
        final long remainingMillis = entry.arrivalMillis - System.currentTimeMillis();
        // 向上取整：还剩 30 秒也显示 1 分钟，避免「0 分钟」闪一下
        final int seconds = (int) Math.max(0, Math.ceil(remainingMillis / 1000D));
        return new TrdmPisPanel.Arrival(seconds, destination(entry), entry.trainCars);
    }

    /** 终点站（MTR 的「中文|English」字符串）。 */
    public static String destination(ScheduleEntry entry) {
        final Route route = ClientData.DATA_CACHE.routeIdMap.get(entry.routeId);
        if (route == null) {
            return "";
        }
        try {
            final String destination = ClientData.DATA_CACHE.getFormattedRouteDestination(
                    route, entry.currentStationIndex, "");
            return destination == null ? "" : destination;
        } catch (Exception e) {
            // 站序越界之类的情况不让整块屏崩掉，退回线路名
            return route.name == null ? "" : route.name;
        }
    }
}
