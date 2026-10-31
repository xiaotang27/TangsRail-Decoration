package top.xiaotang27.trdm.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import top.xiaotang27.trdm.Trdm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PIS 的<b>全局跑马灯</b>：所有 PIS 都先滚这段，再滚各屏自己的文字。
 * <p>
 * 存在客户端配置文件 {@code config/trdm.json}：
 * <pre>
 * { "pis_global_ticker": "天津地铁提醒您：请先下后上，注意站台与列车之间的空隙" }
 * </pre>
 * 也可以直接编辑这个文件（改完重进世界 / 重开界面就生效）。
 * <p>
 * 跑马灯只在客户端渲染，所以放客户端配置里 —— 不需要网络包、也不占方块 NBT。
 */
public final class TrdmPisGlobal {

    /** 配置文件名（配置目录下）。 */
    private static final String FILE_NAME = "trdm.json";
    /** json 里的键。 */
    private static final String KEY = "pis_global_ticker";
    /** 全局文字和屏幕自己的文字之间的间隔（空格数）。 */
    private static final String SEPARATOR = "        ";

    private static String globalTicker = "";
    private static boolean loaded;

    private TrdmPisGlobal() {
    }

    /** 全局跑马灯文字（没设过就是空串）。 */
    public static synchronized String ticker() {
        load();
        return globalTicker;
    }

    /** 改全局跑马灯文字并立刻写盘。 */
    public static synchronized void setTicker(String text) {
        load();
        globalTicker = text == null ? "" : text;
        save();
    }

    /**
     * 一块屏幕最终要滚的文字：<b>先全局，再自己那块</b>，中间留一段空档。
     *
     * @param own 屏幕自己的跑马灯文字（可以是空）
     */
    public static synchronized String combined(String own) {
        final String global = ticker();
        final String personal = own == null ? "" : own;
        if (global.isEmpty()) {
            return personal;
        }
        if (personal.isEmpty()) {
            return global;
        }
        return global + SEPARATOR + personal;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            final Path file = file();
            if (!Files.isRegularFile(file)) {
                return;
            }
            final String content = Files.readString(file, StandardCharsets.UTF_8);
            final JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            if (root.has(KEY)) {
                globalTicker = root.get(KEY).getAsString();
            }
        } catch (Exception e) {
            Trdm.LOGGER.warn("[pis] 读不了全局跑马灯配置: {}", e.toString());
        }
    }

    private static void save() {
        try {
            final Path file = file();
            final JsonObject root = new JsonObject();
            if (Files.isRegularFile(file)) {
                try {
                    // 把已有的整份读进来，别把别的键冲掉
                    final JsonObject existing = JsonParser.parseString(
                            Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                    for (final String key : existing.keySet()) {
                        root.add(key, existing.get(key));
                    }
                } catch (Exception ignored) {
                    // 文件坏了就只写这一项
                }
            }
            root.addProperty(KEY, globalTicker);
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Trdm.LOGGER.warn("[pis] 写不了全局跑马灯配置: {}", e.toString());
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
