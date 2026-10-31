package top.xiaotang27.trdm;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 通用设置：存在客户端 {@code config/trdm.json}，和 PIS 全局跑马灯共用同一个文件。
 * <p>
 * 读的时候懒加载一次，写的时候「读整份 → 改一个键 → 写回」，不动其它键。
 */
public final class TrdmConfig {

    private static final String FILE_NAME = "trdm.json";
    /** 用 TRDM 自带的思源黑体替换系统字体。 */
    private static final String KEY_USE_TRDM_FONT = "use_trdm_font";
    /** 允许 PIS 播放 mp4 视频广告。 */
    private static final String KEY_PIS_VIDEO = "pis_video_enabled";

    private static Boolean useTrdmFont;
    private static Boolean pisVideoEnabled;

    private TrdmConfig() {
    }

    /** 是否用 TRDM 自带的字体（默认开）。 */
    public static synchronized boolean useTrdmFont() {
        if (useTrdmFont == null) {
            final JsonObject root = read();
            useTrdmFont = !root.has(KEY_USE_TRDM_FONT) || root.get(KEY_USE_TRDM_FONT).getAsBoolean();
        }
        return useTrdmFont;
    }

    public static synchronized void setUseTrdmFont(boolean value) {
        useTrdmFont = value;
        write(KEY_USE_TRDM_FONT, value);
    }

    /** 是否允许 PIS 视频广告（默认开）。 */
    public static synchronized boolean pisVideoEnabled() {
        if (pisVideoEnabled == null) {
            final JsonObject root = read();
            pisVideoEnabled = !root.has(KEY_PIS_VIDEO) || root.get(KEY_PIS_VIDEO).getAsBoolean();
        }
        return pisVideoEnabled;
    }

    public static synchronized void setPisVideoEnabled(boolean value) {
        pisVideoEnabled = value;
        write(KEY_PIS_VIDEO, value);
    }

    /** 重新从磁盘读（改完配置后调一次）。 */
    public static synchronized void invalidate() {
        useTrdmFont = null;
        pisVideoEnabled = null;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static JsonObject read() {
        try {
            final Path path = file();
            if (!Files.isRegularFile(path)) {
                return new JsonObject();
            }
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            Trdm.LOGGER.warn("[config] 读不了配置: {}", e.toString());
            return new JsonObject();
        }
    }

    private static void write(String key, boolean value) {
        try {
            final Path path = file();
            final JsonObject root = read();
            root.addProperty(key, value);
            Files.createDirectories(path.getParent());
            Files.writeString(path, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Trdm.LOGGER.warn("[config] 写不了配置: {}", e.toString());
        }
    }
}
