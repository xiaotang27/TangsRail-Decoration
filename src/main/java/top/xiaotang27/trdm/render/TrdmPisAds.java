package top.xiaotang27.trdm.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import top.xiaotang27.trdm.Trdm;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PIS 广告资源：<b>由资源包驱动</b>。
 * <pre>
 */
public final class TrdmPisAds {

    /** 广告配置 json 的位置（资源包根：assets/trdm/pis_ads.json）。 */
    public static final ResourceLocation CONFIG = new ResourceLocation(Trdm.MOD_ID, "pis_ads.json");
    /** 广告图目录（相对 assets/trdm）。 */
    public static final String TEXTURE_DIRECTORY = "textures/block/pis/ads/";

    /**
     * 一条广告。
     * @param id        json 里的键
     */
    public record Ad(String id, String name, int timeMs, ResourceLocation texture) {

        /** 这条广告是不是 mp4 视频（界面上要单独提示「对性能影响较大」）。 */
        public boolean video() {
            return TrdmPisVideo.isVideo(texture);
        }
    }

    private static List<Ad> cached;
    private static boolean loaded;

    private TrdmPisAds() {
    }

    /** 当前生效的广告列表（json 顺序）；读不到就是空列表。 */
    public static synchronized List<Ad> list() {
        if (loaded) {
            return cached == null ? List.of() : cached;
        }
        loaded = true;
        cached = read();
        return cached;
    }

    /** 资源包变了 / 按了「重新扫描」：清缓存，下次重新读。 */
    public static synchronized void invalidate() {
        loaded = false;
        cached = null;
    }

    /** 广告配置里一共有几条（界面显示用）。 */
    public static int count() {
        return list().size();
    }

    /** 有多少个资源包提供了 pis_ads.json（内置模组里没有这个文件，所以这就是广告资源包个数）。 */
    public static int packCount() {
        try {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return 0;
            }
            return minecraft.getResourceManager().getResourceStack(CONFIG).size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 按 id 找一条广告（找不到返回 null）。 */
    public static Ad byId(String id) {
        if (id == null) {
            return null;
        }
        for (final Ad ad : list()) {
            if (ad.id().equals(id)) {
                return ad;
            }
        }
        return null;
    }

    private static List<Ad> read() {
        try {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return List.of();
            }
            final Optional<Resource> resource = minecraft.getResourceManager().getResource(CONFIG);
            if (resource.isEmpty()) {
                return List.of();
            }
            try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                final JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has("pis_ads") || !root.get("pis_ads").isJsonObject()) {
                    return List.of();
                }
                final JsonObject ads = root.getAsJsonObject("pis_ads");
                final List<Ad> result = new ArrayList<>();
                for (final Map.Entry<String, JsonElement> entry : ads.entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    final JsonObject value = entry.getValue().getAsJsonObject();
                    final String rawName = value.has("name") ? value.get("name").getAsString() : "";
                    if (rawName.isEmpty()) {
                        continue;
                    }
                    // 没写扩展名按 png 处理；写 .gif / .mp4 就是动图 / 视频
                    final String name = rawName.indexOf('.') >= 0 ? rawName : rawName + ".png";
                    final ResourceLocation texture = new ResourceLocation(Trdm.MOD_ID, TEXTURE_DIRECTORY + name);
                    final int time;
                    if (value.has("time")) {
                        time = Math.max(100, value.get("time").getAsInt());
                    } else {
                        // 没写 time：视频默认按视频本身的长度播，其他默认 5 秒
                        final long duration = TrdmPisVideo.durationMs(texture);
                        time = duration > 0 ? (int) Math.min(duration, 600_000L) : 5000;
                    }
                    result.add(new Ad(entry.getKey(), name, time, texture));
                }
                if (!result.isEmpty()) {
                    final StringBuilder summary = new StringBuilder();
                    for (final Ad ad : result) {
                        if (summary.length() > 0) {
                            summary.append(", ");
                        }
                        summary.append(ad.id()).append('(').append(ad.name()).append(", ")
                                .append(ad.timeMs()).append("ms)");
                    }
                    Trdm.LOGGER.info("[pis ads] 读到 {} 条: {}", result.size(), summary);
                }
                return result;
            }
        } catch (Exception e) {
            Trdm.LOGGER.warn("[pis ads] 读不了 {} : {}", CONFIG, e.toString());
            return List.of();
        }
    }

    /**
     * 按「启用 + 顺序 + 每张自己的 time」算当前该放哪张。
     * @param ids     顺序（可能是屏幕里覆盖过的顺序）
     */
    public static Ad current(List<String> ids, Map<String, Boolean> enabled, Map<String, Integer> times) {
        final Map<String, Ad> byId = new LinkedHashMap<>();
        for (final Ad ad : list()) {
            byId.put(ad.id(), ad);
        }
        final List<Ad> playable = new ArrayList<>();
        final List<Integer> durations = new ArrayList<>();
        for (final String id : ids) {
            final Ad ad = byId.get(id);
            if (ad == null || Boolean.FALSE.equals(enabled.get(id))) {
                continue;
            }
            // 0 / 负数 = 没设过（方块 NBT 里未编辑的条目就是 0），要用广告自己的时长，
            // 不然一次设置界面点下来每条都变成 100ms，看着就像「只有一张图在闪」
            final Integer time = times.get(id);
            playable.add(ad);
            durations.add(time == null || time <= 0 ? ad.timeMs() : Math.max(100, time));
        }
        if (playable.isEmpty()) {
            return null;
        }
        long total = 0;
        for (final int duration : durations) {
            total += duration;
        }
        long now = System.currentTimeMillis() % Math.max(1, total);
        for (int index = 0; index < playable.size(); index++) {
            now -= durations.get(index);
            if (now < 0) {
                return playable.get(index);
            }
        }
        return playable.get(playable.size() - 1);
    }
}
