package top.xiaotang27.trdm.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.fare.FareConfig;
import top.xiaotang27.trdm.fare.FareDefaults;
import top.xiaotang27.trdm.fare.FareJson;
import top.xiaotang27.trdm.ticket.TrdmClock;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 服务端权威的票务配置。
 * <p>
 * 存在存档里：{@code <world>/data/trdm_fares.json}。第一次加载时如果存档里没有，
 * 会尝试把旧的 {@code config/trdm-fares.json} 迁移进来，再落盘。
 */
public final class ServerFareConfig {

    private static FareConfig config;
    private static Path path;

    private ServerFareConfig() {
    }

    public static synchronized void load(MinecraftServer server) {
        path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("trdm_fares.json");
        FareConfig loaded = null;
        try {
            loaded = FareJson.tryLoad(path);
        } catch (Exception exception) {
            Trdm.LOGGER.error("读取存档票务配置失败: {}", path, exception);
        }
        if (loaded == null && !Files.exists(path)) {
            final Path legacy = FabricLoader.getInstance().getConfigDir().resolve("trdm-fares.json");
            if (Files.exists(legacy)) {
                try {
                    loaded = FareJson.tryLoad(legacy);
                    if (loaded != null) {
                        Trdm.LOGGER.info("已从旧配置迁移票务规则: {}", legacy);
                    }
                } catch (Exception exception) {
                    Trdm.LOGGER.error("迁移旧票务配置失败: {}", legacy, exception);
                }
            }
        }
        if (loaded == null) {
            loaded = FareDefaults.config();
        }
        config = loaded;
        TrdmClock.setBasis(config.timeBasis);
        save();
    }

    public static synchronized FareConfig config() {
        if (config == null) {
            config = FareDefaults.config();
            TrdmClock.setBasis(config.timeBasis);
        }
        return config;
    }

    /** 用客户端提交的配置覆盖并落盘；调用方负责权限校验。 */
    public static synchronized void replace(FareConfig incoming) {
        config().copyFrom(incoming);
        config().revision++;
        TrdmClock.setBasis(config().timeBasis);
        save();
    }

    public static synchronized void save() {
        if (path == null) {
            return;
        }
        try {
            FareJson.save(path, config());
        } catch (Exception exception) {
            Trdm.LOGGER.error("保存存档票务配置失败: {}", path, exception);
        }
    }

    public static synchronized void reset() {
        config = null;
        path = null;
    }

    public static synchronized Path path() {
        return path;
    }
}
