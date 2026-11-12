package top.xiaotang27.trdm.server;

import mtr.data.RailwayData;
import mtr.data.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import top.xiaotang27.trdm.fare.CardRuleStats;
import top.xiaotang27.trdm.fare.FareConfig;
import top.xiaotang27.trdm.fare.FareEngine;
import top.xiaotang27.trdm.fare.FareGraph;
import top.xiaotang27.trdm.fare.FareQuote;
import top.xiaotang27.trdm.fare.QuoteEnvironment;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务端计费运行时：用 {@link ServerFareConfig} 的存档配置和 MTR 服务端数据建站点图并报价。
 */
public final class ServerFareRuntime {

    private record CachedGraph(long signature, FareGraph graph) {
    }

    private static final Map<ResourceKey<Level>, CachedGraph> GRAPHS = new HashMap<>();

    private ServerFareRuntime() {
    }

    public static FareConfig config() {
        return ServerFareConfig.config();
    }

    public static synchronized FareGraph graph(net.minecraft.server.level.ServerLevel level) {
        final RailwayData data = RailwayData.getInstance(level);
        if (data == null) {
            return FareGraph.empty();
        }
        final ServerFareNetwork network = ServerFareNetwork.snapshot(data);
        final CachedGraph cached = GRAPHS.get(level.dimension());
        if (cached == null || cached.signature() != network.signature()) {
            final CachedGraph rebuilt = new CachedGraph(network.signature(), FareGraph.build(network));
            GRAPHS.put(level.dimension(), rebuilt);
            return rebuilt.graph();
        }
        return cached.graph();
    }

    public static FareQuote quote(net.minecraft.server.level.ServerLevel level, long from, long to) {
        return FareEngine.quote(config(), graph(level), from, to, dimensionId(level));
    }

    public static FareQuote quote(net.minecraft.server.level.ServerLevel level, long from, long to,
                                  Map<String, CardRuleStats> cardStats) {
        return FareEngine.quote(config(), graph(level), from, to, dimensionId(level), cardStats);
    }

    public static FareQuote quote(net.minecraft.server.level.ServerLevel level, long from, long to,
                                  Map<String, CardRuleStats> cardStats,
                                  QuoteEnvironment environment) {
        return FareEngine.quote(config(), graph(level), from, to, dimensionId(level),
                cardStats, environment);
    }

    public static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }

    /** 站台 / 栏杆所在位置的车站；不在车站内返回 {@code null}。 */
    public static Station stationAt(Level level, BlockPos pos) {
        final RailwayData data = RailwayData.getInstance(level);
        if (data == null) {
            return null;
        }
        return RailwayData.getStation(data.stations, data.dataCache, pos);
    }

    public static boolean hasStation(Level level, long stationId) {
        return stationById(level, stationId) != null;
    }

    public static Station stationById(Level level, long stationId) {
        final RailwayData data = RailwayData.getInstance(level);
        if (data == null || stationId == 0L) {
            return null;
        }
        for (final Station station : data.stations) {
            if (station.id == stationId) {
                return station;
            }
        }
        return null;
    }

    public static synchronized void reset() {
        GRAPHS.clear();
    }
}
