package top.xiaotang27.trdm.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.fare.FareConfig;
import top.xiaotang27.trdm.fare.FareDefaults;
import top.xiaotang27.trdm.fare.FareEngine;
import top.xiaotang27.trdm.fare.FareGraph;
import top.xiaotang27.trdm.fare.FareJson;
import top.xiaotang27.trdm.fare.FareQuote;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 客户端计费运行时：配置加载/保存、站点图缓存、统一报价入口。
 * <p>
 * 当前阶段配置落在 {@code config/trdm-fares.json}，便于先把手感和算价跑通；
 * 服务端权威版本会复用同一份 {@link FareConfig}/{@link FareJson}，只把加载来源换成世界存档。
 */
public final class FareRuntime {

    private static final String FILE_NAME = "trdm-fares.json";

    private static FareRuntime instance;

    private final Path path;
    private final FareConfig config;
    private FareGraph graph = FareGraph.empty();
    private long networkSignature = Long.MIN_VALUE;

    private FareRuntime(Path path, FareConfig config) {
        this.path = path;
        this.config = config;
    }

    public static synchronized FareRuntime get() {
        if (instance == null) {
            final Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
            FareConfig config = null;
            try {
                config = FareJson.tryLoad(path);
            } catch (Exception exception) {
                Trdm.LOGGER.error("读取票务计费配置失败，已回退默认规则: {}", path, exception);
            }
            final boolean existed = config != null;
            if (config == null) {
                config = FareDefaults.config();
            }
            instance = new FareRuntime(path, config);
            if (!existed) {
                instance.save();
            }
        }
        return instance;
    }

    public synchronized FareConfig config() {
        return config;
    }

    /** MTR 站点/线路数据变化后重建站点图；没变化时直接复用。 */
    public synchronized void sync() {
        final ClientFareNetwork snapshot = ClientFareNetwork.snapshot();
        if (!graph.isEmpty() && snapshot.signature() == networkSignature) {
            return;
        }
        graph = FareGraph.build(snapshot);
        networkSignature = snapshot.signature();
    }

    public synchronized FareGraph graph() {
        sync();
        return graph;
    }

    public FareQuote quote(long fromStationId, long toStationId) {
        sync();
        return FareEngine.quote(config, graph, fromStationId, toStationId, dimensionId());
    }

    /** 保存配置；失败只记日志，不打断界面。 */
    public synchronized void save() {
        config.touch();
        try {
            FareJson.save(path, config);
        } catch (IOException exception) {
            Trdm.LOGGER.error("保存票务计费配置失败: {}", path, exception);
        }
    }

    /** 当前维度的 ID；不在世界时按主世界处理（设置页无世界时只读展示）。 */
    public static String dimensionId() {
        final Level level = Minecraft.getInstance().level;
        if (level == null) {
            return FareConfig.DEFAULT_DIMENSION;
        }
        final ResourceLocation location = level.dimension().location();
        return location == null ? FareConfig.DEFAULT_DIMENSION : location.toString();
    }
}
