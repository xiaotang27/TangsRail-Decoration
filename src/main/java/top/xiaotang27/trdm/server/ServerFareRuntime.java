package top.xiaotang27.trdm.server;

import mtr.data.RailwayData;
import mtr.data.Station;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import top.xiaotang27.trdm.fare.FareConfig;
import top.xiaotang27.trdm.fare.FareDefaults;
import top.xiaotang27.trdm.fare.FareEngine;
import top.xiaotang27.trdm.fare.FareGraph;
import top.xiaotang27.trdm.fare.FareJson;
import top.xiaotang27.trdm.fare.FareQuote;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 服务端计费运行时：读取 {@code config/trdm-fares.json}，用 MTR 服务端数据建站点图并报价。
 * <p>
 * 单人游戏里客户端和服务端在同一个 JVM、共用同一个 config 目录，所以设置页保存后
 * 服务端下一次报价就能读到；专用服务器上以服务器自己的配置文件为准。
 */
public final class ServerFareRuntime {

    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("trdm-fares.json");

    private record CachedGraph(long signature, FareGraph graph) {
    }

    private static FareConfig config;
    private static long lastModified = Long.MIN_VALUE;
    private static final Map<ResourceKey<Level>, CachedGraph> GRAPHS = new HashMap<>();

    private ServerFareRuntime() {
    }

    /** 配置带 mtime 缓存：设置页保存后会自动重读。 */
    public static synchronized FareConfig config() {
        long modified = -1L;
        try {
            if (Files.isRegularFile(CONFIG_PATH)) {
                modified = Files.getLastModifiedTime(CONFIG_PATH).toMillis();
            }
        } catch (Exception ignored) {
            // 读不到 mtime 就沿用缓存
        }
        if (config == null || modified != lastModified) {
            config = FareJson.loadOrDefault(CONFIG_PATH);
            lastModified = modified;
        }
        return config == null ? FareDefaults.config() : config;
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
        final RailwayData data = RailwayData.getInstance(level);
        if (data == null || stationId == 0L) {
            return false;
        }
        for (final Station station : data.stations) {
            if (station.id == stationId) {
                return true;
            }
        }
        return false;
    }

    public static void reset() {
        GRAPHS.clear();
        config = null;
        lastModified = Long.MIN_VALUE;
    }
}
