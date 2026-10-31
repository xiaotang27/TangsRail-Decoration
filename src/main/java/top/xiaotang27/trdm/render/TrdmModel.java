package top.xiaotang27.trdm.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.data.IGui;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import top.xiaotang27.trdm.Trdm;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 极简的「原版方块模型」（{@code .json} 里的 elements / faces）加载与绘制。
 * <p>
 * 用来把 DoJ 的 PIDS 模型（{@code pids_rv.json} / {@code rv_pids_pole_extended.json}）
 * 直接搬进自绘渲染器：模型里的 UV 是 0~16 的方块纹理坐标，这里换算成 0~1。
 * 只支持 {@code from}/{@code to}/六面 {@code faces}（不含 rotation），够这套模型用。
 */
public final class TrdmModel {

    /** PIDS 本体（屏幕外壳 + 两侧挡板 + 短立杆）。 */
    public static final ResourceLocation PIDS = new ResourceLocation(Trdm.MOD_ID, "models/block/pids_rv.json");
    /** 加长立杆（0~24 像素，1.5 格高）。 */
    public static final ResourceLocation POLE_EXTENDED =
            new ResourceLocation(Trdm.MOD_ID, "models/block/rv_pids_pole_extended.json");

    /** 一个面：UV（0~16 方块纹理坐标）+ 贴图。 */
    public record Face(ResourceLocation texture, float u1, float v1, float u2, float v2) {
    }

    /** 一个立方体：坐标是 0~16 的方块像素（y 可以超出 0..16）。 */
    public record Element(String name, float x1, float y1, float z1, float x2, float y2, float z2,
                          Map<Direction, Face> faces) {
    }

    private static final Map<ResourceLocation, List<Element>> CACHE = new HashMap<>();

    private TrdmModel() {
    }

    public static synchronized List<Element> load(ResourceLocation location) {
        final List<Element> cached = CACHE.get(location);
        if (cached != null) {
            return cached;
        }
        final List<Element> elements = read(location);
        CACHE.put(location, elements);
        return elements;
    }

    /** 资源包换了之后清缓存。 */
    public static synchronized void invalidate() {
        CACHE.clear();
    }

    private static List<Element> read(ResourceLocation location) {
        try {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return List.of();
            }
            final Optional<Resource> resource = minecraft.getResourceManager().getResource(location);
            if (resource.isEmpty()) {
                Trdm.LOGGER.warn("[model] missing {}", location);
                return List.of();
            }
            try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                final JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                final Map<String, ResourceLocation> textures = new HashMap<>();
                if (root.has("textures")) {
                    for (final Map.Entry<String, JsonElement> entry : root.getAsJsonObject("textures").entrySet()) {
                        final String value = entry.getValue().getAsString();
                        if (!value.startsWith("#")) {
                            textures.put(entry.getKey(), texture(value));
                        }
                    }
                }
                final List<Element> elements = new ArrayList<>();
                if (!root.has("elements")) {
                    return elements;
                }
                for (final JsonElement elementJson : root.getAsJsonArray("elements")) {
                    final JsonObject element = elementJson.getAsJsonObject();
                    final float[] from = readVec(element.getAsJsonArray("from"));
                    final float[] to = readVec(element.getAsJsonArray("to"));
                    final String name = element.has("name") ? element.get("name").getAsString() : "";
                    final Map<Direction, Face> faces = new HashMap<>();
                    if (element.has("faces")) {
                        for (final Map.Entry<String, JsonElement> faceEntry : element.getAsJsonObject("faces").entrySet()) {
                            final Direction direction = byName(faceEntry.getKey());
                            if (direction == null || !faceEntry.getValue().isJsonObject()) {
                                continue;
                            }
                            final JsonObject face = faceEntry.getValue().getAsJsonObject();
                            final String textureRef = face.has("texture") ? face.get("texture").getAsString() : "";
                            final ResourceLocation texture = textures.get(
                                    textureRef.startsWith("#") ? textureRef.substring(1) : textureRef);
                            if (texture == null) {
                                continue;
                            }
                            final float[] uv = face.has("uv") ? readVec(face.getAsJsonArray("uv"))
                                    : new float[]{0F, 0F, 16F, 16F};
                            faces.put(direction, new Face(texture, uv[0], uv[1], uv[2], uv[3]));
                        }
                    }
                    elements.add(new Element(name, from[0], from[1], from[2], to[0], to[1], to[2], faces));
                }
                return elements;
            }
        } catch (Exception e) {
            Trdm.LOGGER.warn("[model] failed to read {}: {}", location, e.toString());
            return List.of();
        }
    }

    private static float[] readVec(JsonElement element) {
        final float[] values = new float[Math.min(4, element.getAsJsonArray().size())];
        for (int index = 0; index < values.length; index++) {
            values[index] = element.getAsJsonArray().get(index).getAsFloat();
        }
        return values;
    }

    /**
     * 模型里写的是 {@code mtr:block/metal} 这种「短名」，贴图实际路径是
     * {@code mtr:textures/block/metal.png}；已经是完整路径的原样返回。
     */
    private static ResourceLocation texture(String value) {
        final ResourceLocation raw = new ResourceLocation(value);
        final String path = raw.getPath();
        if (path.startsWith("textures/") || path.endsWith(".png")) {
            return raw;
        }
        return new ResourceLocation(raw.getNamespace(), "textures/" + path + ".png");
    }

    private static Direction byName(String name) {
        for (final Direction direction : Direction.values()) {
            if (direction.getSerializedName().equals(name)) {
                return direction;
            }
        }
        return null;
    }

    /**
     * 画一整个模型。
     *
     * @param origin  方块原点（世界坐标，方块最小角）
     * @param yawOffset 朝向：模型本身按「屏幕朝 ±X」做的，这里按方块 facing 旋转
     * @param skipName 跳过该名字的元素（避免两套模型里重复的立杆打架）
     * @param scale    整体缩放（1 = 原大小）
     */
    public static void render(PoseStack matrices, List<Element> elements, Vec3 origin, Direction facing,
                              String skipName, float scale, int light, Map<ResourceLocation, List<Face>> out) {
        for (final Element element : elements) {
            if (skipName != null && skipName.equals(element.name())) {
                continue;
            }
            for (final Map.Entry<Direction, Face> entry : element.faces().entrySet()) {
                out.computeIfAbsent(entry.getValue().texture(), ignored -> new ArrayList<>())
                        .add(entry.getValue());
            }
        }
    }

    /** 单个面的四个顶点（世界坐标，已按 facing 旋转、按 1/16 缩放到格）。 */
    public static Vec3[] corners(Element element, Direction face, Vec3 origin, Direction facing, float scale) {
        final float x1 = element.x1() * scale;
        final float y1 = element.y1() * scale;
        final float z1 = element.z1() * scale;
        final float x2 = element.x2() * scale;
        final float y2 = element.y2() * scale;
        final float z2 = element.z2() * scale;

        // 原版模型的顶点顺序（从外面看：左下、右下、右上、左上）
        final float[][] order = switch (face) {
            case NORTH -> new float[][]{{x2, y2, z1}, {x1, y2, z1}, {x1, y1, z1}, {x2, y1, z1}};
            case SOUTH -> new float[][]{{x1, y2, z2}, {x2, y2, z2}, {x2, y1, z2}, {x1, y1, z2}};
            case WEST -> new float[][]{{x1, y2, z1}, {x1, y2, z2}, {x1, y1, z2}, {x1, y1, z1}};
            case EAST -> new float[][]{{x2, y2, z2}, {x2, y2, z1}, {x2, y1, z1}, {x2, y1, z2}};
            case UP -> new float[][]{{x1, y2, z1}, {x1, y2, z2}, {x2, y2, z2}, {x2, y2, z1}};
            case DOWN -> new float[][]{{x1, y1, z2}, {x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}};
        };

        final Vec3[] result = new Vec3[4];
        for (int index = 0; index < 4; index++) {
            result[index] = rotate(order[index], facing).add(origin);
        }
        return result;
    }

    /**
     * 把「模型坐标」按方块 facing 旋转到世界方向。
     * <p>
     * 和原版 blockstate 的 {@code "y": 0/90/180/270} 严格对应（north 不转、east 转 90°、
     * south 转 180°、west 转 270°），这样 JCM 那套 blockstate 怎么转，这里的屏幕就怎么转。
     */
    private static Vec3 rotate(float[] point, Direction facing) {
        // 屏幕在模型里朝 ±X，为了让屏幕朝着方块 facing（也就是放下去时朝着玩家），
        // 整体再转 90°：facing=north → y 90（和 blockstates/pis.json 里的 y 值一一对应）
        final float degrees = switch (facing) {
            case NORTH -> 90F;
            case EAST -> 180F;
            case SOUTH -> 270F;
            case WEST -> 0F;
            default -> 90F;
        };
        final float radians = (float) Math.toRadians(degrees);
        final float cos = (float) Math.cos(radians);
        final float sin = (float) Math.sin(radians);
        final float dx = point[0] - 8F;
        final float dz = point[2] - 8F;
        final float rx = dx * cos - dz * sin;
        final float rz = dx * sin + dz * cos;
        return new Vec3((rx + 8F) / 16F, point[1] / 16F, (rz + 8F) / 16F);
    }

    /** 画一个带 UV 的面（UV 用 0~16 的方块纹理坐标）。 */
    public static void emit(Matrix4f pose, VertexConsumer vertexConsumer, Vec3[] corners, Face face,
                            Vec3 outward, int light) {
        final float[] us = {face.u2() / 16F, face.u1() / 16F, face.u1() / 16F, face.u2() / 16F};
        final float[] vs = {face.v1() / 16F, face.v1() / 16F, face.v2() / 16F, face.v2() / 16F};
        final Vec3 ab = corners[1].subtract(corners[0]);
        final Vec3 ac = corners[2].subtract(corners[0]);
        final Vec3 cross = new Vec3(ab.y * ac.z - ab.z * ac.y, ab.z * ac.x - ab.x * ac.z, ab.x * ac.y - ab.y * ac.x);
        final boolean reversed = cross.dot(outward) < 0;
        for (int index = 0; index < 4; index++) {
            final int actual = reversed ? 3 - index : index;
            final Vec3 corner = corners[actual];
            vertexConsumer.vertex(pose, (float) corner.x, (float) corner.y, (float) corner.z)
                    .color(IGui.ARGB_WHITE)
                    .uv(us[actual], vs[actual])
                    .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal((float) outward.x, (float) outward.y, (float) outward.z)
                    .endVertex();
        }
    }
}
