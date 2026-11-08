package top.xiaotang27.trdm.fare;

import java.util.List;

/**
 * 计费快照里的一条线路：按站台顺序、相邻同站去重后的车站序列。
 * <p>
 * {@code circular} 为真时，首尾站之间会补一条边（环线）。
 */
public record FareRoute(long id, String name, List<Long> stationIds, boolean circular) {

    public FareRoute {
        name = name == null ? "" : name;
        stationIds = List.copyOf(stationIds);
    }

    public String displayName() {
        return name.isBlank() ? Long.toString(id) : name;
    }
}
