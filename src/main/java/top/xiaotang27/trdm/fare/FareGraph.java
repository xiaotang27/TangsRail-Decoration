package top.xiaotang27.trdm.fare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 站点图。
 * <p>
 * 节点是 MTR 车站（不是站台）：同一车站的多个站台合并成一个节点。
 * 边来自每条线路相邻两个站台映射到的车站；多条线路经过同一对车站时保留多条边，
 * 这样 B 分段计价才能按 {@code routeId} 切段。
 * <p>
 * 区间数 = 边数；里程 = 车站中心 XZ 直线距离（1 格 = 1 米）。
 */
public final class FareGraph {

    /** 一条有向边。{@code meters} 未知时为 0。 */
    public record Edge(long from, long to, long routeId, String routeName, long meters) {
    }

    /** 一条路径。{@code connected} 为假表示不可达（{@code edges} 为空）。 */
    public record Path(List<Edge> edges, boolean connected) {

        public Path {
            edges = List.copyOf(edges);
        }

        public long intervals() {
            return edges.size();
        }

        public long meters() {
            long total = 0L;
            for (final Edge edge : edges) {
                total += edge.meters();
            }
            return total;
        }
    }

    public static final Path UNREACHABLE = new Path(List.of(), false);

    private final Map<Long, FareStation> stations;
    private final Map<Long, List<Edge>> adjacency;
    private final long signature;
    private final boolean empty;

    private FareGraph(Map<Long, FareStation> stations, Map<Long, List<Edge>> adjacency,
                      long signature, boolean empty) {
        this.stations = stations;
        this.adjacency = adjacency;
        this.signature = signature;
        this.empty = empty;
    }

    public static FareGraph empty() {
        return new FareGraph(Map.of(), Map.of(), 0L, true);
    }

    public boolean isEmpty() {
        return empty;
    }

    /** 车站/线路/坐标的哈希；用于判断 MTR 数据变了没有，变了才重建图。 */
    public long signature() {
        return signature;
    }

    public FareStation station(long id) {
        return stations.get(id);
    }

    public static FareGraph build(FareNetwork network) {
        final Map<Long, FareStation> stations = new HashMap<>();
        final List<FareStation> stationList = new ArrayList<>(network.stations());
        stationList.sort(Comparator.comparingLong(FareStation::id));

        long signature = 1125899906842597L;
        for (final FareStation station : stationList) {
            stations.put(station.id(), station);
            signature = signature * 31L + station.id();
            signature = signature * 31L + station.zone();
            signature = signature * 31L + Double.hashCode(station.x());
            signature = signature * 31L + Double.hashCode(station.z());
        }

        final List<FareRoute> routes = new ArrayList<>(network.routes());
        routes.sort(Comparator.comparingLong(FareRoute::id));

        final Map<Long, List<Edge>> adjacency = new HashMap<>();
        for (final FareRoute route : routes) {
            final List<Long> ids = route.stationIds();
            signature = signature * 31L + route.id();
            for (final long stationId : ids) {
                signature = signature * 31L + stationId;
            }
            for (int index = 0; index + 1 < ids.size(); index++) {
                link(adjacency, stations, ids.get(index), ids.get(index + 1), route);
            }
            if (route.circular() && ids.size() >= 2) {
                final long first = ids.get(0);
                final long last = ids.get(ids.size() - 1);
                if (first != last) {
                    link(adjacency, stations, last, first, route);
                }
            }
        }

        final Map<Long, List<Edge>> frozen = new HashMap<>();
        for (final Map.Entry<Long, List<Edge>> entry : adjacency.entrySet()) {
            final List<Edge> edges = entry.getValue();
            edges.sort(Comparator.comparingLong(Edge::to)
                    .thenComparingLong(Edge::routeId)
                    .thenComparingLong(Edge::meters));
            frozen.put(entry.getKey(), List.copyOf(edges));
        }
        return new FareGraph(Map.copyOf(stations), Map.copyOf(frozen), signature, false);
    }

    private static void link(Map<Long, List<Edge>> adjacency, Map<Long, FareStation> stations,
                             long from, long to, FareRoute route) {
        if (from == to) {
            return;
        }
        final long meters = distance(stations, from, to);
        adjacency.computeIfAbsent(from, key -> new ArrayList<>())
                .add(new Edge(from, to, route.id(), route.displayName(), meters));
        adjacency.computeIfAbsent(to, key -> new ArrayList<>())
                .add(new Edge(to, from, route.id(), route.displayName(), meters));
    }

    private static long distance(Map<Long, FareStation> stations, long from, long to) {
        final FareStation a = stations.get(from);
        final FareStation b = stations.get(to);
        if (a == null || b == null) {
            return 0L;
        }
        return Math.max(0L, Math.round(Math.hypot(a.x() - b.x(), a.z() - b.z())));
    }

    /**
     * 最短路径。{@code metric} 决定主权重（区间数或里程），另一个作为平局时的次级权重。
     * 不可达返回 {@link #UNREACHABLE}。
     */
    public Path shortest(long from, long to, FareConfig.Metric metric) {
        if (from == to) {
            return new Path(List.of(), true);
        }
        if (!stations.containsKey(from) || !stations.containsKey(to)) {
            return UNREACHABLE;
        }
        final boolean byDistance = metric == FareConfig.Metric.DISTANCE;
        final Map<Long, long[]> best = new HashMap<>();
        final Map<Long, Edge> previous = new HashMap<>();
        final PriorityQueue<Step> queue = new PriorityQueue<>();
        best.put(from, new long[]{0L, 0L});
        queue.add(new Step(from, 0L, 0L));
        while (!queue.isEmpty()) {
            final Step step = queue.poll();
            final long[] known = best.get(step.station());
            if (known == null || known[0] != step.primary() || known[1] != step.secondary()) {
                continue;
            }
            if (step.station() == to) {
                return reconstruct(previous, from, to);
            }
            for (final Edge edge : adjacency.getOrDefault(step.station(), List.of())) {
                final long primary = step.primary() + (byDistance ? Math.max(0L, edge.meters()) : 1L);
                final long secondary = step.secondary() + (byDistance ? 1L : Math.max(0L, edge.meters()));
                final long[] current = best.get(edge.to());
                if (current == null || primary < current[0]
                        || (primary == current[0] && secondary < current[1])) {
                    best.put(edge.to(), new long[]{primary, secondary});
                    previous.put(edge.to(), edge);
                    queue.add(new Step(edge.to(), primary, secondary));
                }
            }
        }
        return UNREACHABLE;
    }

    private static Path reconstruct(Map<Long, Edge> previous, long from, long to) {
        final List<Edge> edges = new ArrayList<>();
        long cursor = to;
        while (cursor != from) {
            final Edge edge = previous.get(cursor);
            if (edge == null) {
                return UNREACHABLE;
            }
            edges.add(edge);
            cursor = edge.from();
        }
        Collections.reverse(edges);
        return new Path(edges, true);
    }

    private record Step(long station, long primary, long secondary) implements Comparable<Step> {

        @Override
        public int compareTo(Step other) {
            int result = Long.compare(primary, other.primary);
            if (result == 0) {
                result = Long.compare(secondary, other.secondary);
            }
            if (result == 0) {
                result = Long.compare(station, other.station);
            }
            return result;
        }
    }
}
