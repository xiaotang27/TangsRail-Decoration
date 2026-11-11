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
import top.xiaotang27.trdm.ticket.TrdmClock;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 客户端计费运行时：服务端配置快照、站点图缓存、统一报价入口。
 * <p>
 * 进入世界后由服务端下发权威配置（{@link #applySnapshot}）；设置页保存时发回服务端落盘并广播。
 * 本地 {@code config/trdm-fares.json} 只在未收到快照时作为回退（以及服务端首次迁移的来源）。
 */
public final class FareRuntime {

    private static final String FILE_NAME = "trdm-fares.json";

    private static FareRuntime instance;

    private final Path path;
    private final FareConfig config;
    private FareGraph graph = FareGraph.empty();
    private long networkSignature = Long.MIN_VALUE;
    private boolean synced;
    private boolean serverCanEdit;

    private FareRuntime(Path path, FareConfig config) {
        this.path = path;
        this.config = config;
        TrdmClock.setBasis(config.timeBasis);
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

    /** 服务端下发的权威配置：原地覆盖，界面上缓存的 rules 列表引用仍然有效。 */
    public synchronized void applySnapshot(String json, boolean canEdit) {
        try {
            final FareConfig parsed = FareJson.parse(json);
            config.copyFrom(parsed);
            TrdmClock.setBasis(config.timeBasis);
            serverCanEdit = canEdit;
            synced = true;
        } catch (Exception exception) {
            Trdm.LOGGER.error("应用服务端票务配置失败", exception);
        }
    }

    /** 当前是否已经收到服务端快照。 */
    public synchronized boolean synced() {
        return synced;
    }

    /** 是否允许编辑票务设置；已同步时以服务端权限为准。 */
    public boolean canEdit() {
        if (synced) {
            return serverCanEdit;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() != null) {
            return true;
        }
        return minecraft.player != null && minecraft.player.hasPermissions(2);
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

    /** 保存配置：已联网时交给服务端；否则写本地文件（回退）。 */
    public synchronized void save() {
        if (synced && Minecraft.getInstance().getConnection() != null) {
            TicketClientNetwork.sendConfigUpdate(FareJson.write(config));
            return;
        }
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
