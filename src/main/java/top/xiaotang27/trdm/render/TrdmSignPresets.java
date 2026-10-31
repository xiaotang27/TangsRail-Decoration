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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 内置的 MTR 指示牌预设，格式和 {@code mtr_custom_resources.json} 的 {@code custom_signs} 一致：
 * <pre>
 * assets/trdm/sign_presets.json
 * { "custom_signs": { "id": { "texture_id": "trdm:textures/block/sign/x.png",
 *                             "custom_text": "中文|English", "flip_custom_text": false, "small": true } } }
 * </pre>
 * 贴图在 {@code assets/trdm/textures/block/sign/}，由本 mod 自带，不依赖外部资源包。
 */
public final class TrdmSignPresets {

    /** 预设文件位置。 */
    public static final ResourceLocation FILE = new ResourceLocation(Trdm.MOD_ID, "sign_presets.json");
    /** json 里的键名（和 MTR 一致）。 */
    private static final String KEY_SIGNS = "custom_signs";

    /**
     * 一条预设。
     *
     * @param id       mtr 里的标志 id
     * @param texture  贴图
     * @param text     「中文|English」，可为空
     * @param flip     是否左右翻转（同一张贴图的正反两份）
     * @param small    是否小号标志
     */
    public record Entry(String id, ResourceLocation texture, String text, boolean flip, boolean small) {
    }

    private static Map<String, Entry> cached;

    private TrdmSignPresets() {
    }

    /** 全部预设（按 json 顺序）；读不到就是空表。 */
    public static synchronized Map<String, Entry> entries() {
        if (cached != null) {
            return cached;
        }
        cached = read();
        if (!cached.isEmpty()) {
            Trdm.LOGGER.info("[sign presets] {} 个内置指示牌: {}", cached.size(), cached.keySet());
        }
        return cached;
    }

    /** 资源包切换后重新读。 */
    public static synchronized void invalidate() {
        cached = null;
    }

    private static Map<String, Entry> read() {
        final Map<String, Entry> result = new LinkedHashMap<>();
        try {
            final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(FILE);
            if (resource.isEmpty()) {
                return result;
            }
            try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                final JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (!root.has(KEY_SIGNS) || !root.get(KEY_SIGNS).isJsonObject()) {
                    return result;
                }
                for (final Map.Entry<String, JsonElement> entry : root.getAsJsonObject(KEY_SIGNS).entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    final JsonObject value = entry.getValue().getAsJsonObject();
                    if (!value.has("texture_id")) {
                        continue;
                    }
                    result.put(entry.getKey(), new Entry(
                            entry.getKey(),
                            new ResourceLocation(value.get("texture_id").getAsString()),
                            value.has("custom_text") ? value.get("custom_text").getAsString() : "",
                            value.has("flip_custom_text") && value.get("flip_custom_text").getAsBoolean(),
                            !value.has("small") || value.get("small").getAsBoolean()));
                }
            }
        } catch (Exception e) {
            Trdm.LOGGER.warn("[sign presets] 读不了 {} : {}", FILE, e.toString());
        }
        return result;
    }
}
