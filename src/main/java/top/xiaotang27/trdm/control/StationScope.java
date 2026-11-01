package top.xiaotang27.trdm.control;

import mtr.data.Station;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Map;

/**
 * 车站范围判定（客户端 / 服务端通用，纯函数，不碰 Level）。
 * <p>
 */
public final class StationScope {

    private StationScope() {
    }

    /** 这个坐标落在哪个车站的范围里（不在任何车站内返回 null）。 */
    public static Station stationAt(Collection<Station> stations, BlockPos pos) {
        for (final Station station : stations) {
            if (station.inArea(pos.getX(), pos.getZ())) {
                return station;
            }
        }
        return null;
    }

    /** 这个站台属于哪个车站（没有归属返回 null）。 */
    public static Station stationOf(Map<Long, Station> platformIdToStation, long platformId) {
        return platformIdToStation.get(platformId);
    }

    /** 两个车站是不是同一个（null 一律不算同一个）。 */
    public static boolean sameStation(Station left, Station right) {
        return left != null && right != null && left.id == right.id;
    }
}
