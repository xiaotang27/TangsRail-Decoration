package top.xiaotang27.trdm.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import top.xiaotang27.trdm.Trdm;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 吊板用的图标素材（{@code assets/trdm/textures/block/sign/}）。
 * <p>
 * 全部按<b>原图</b>缓存（不像 {@link TrdmArrowAsset} 那样只取剪影蒙版）：这些素材自带颜色
 * （黑色圆环、多色转向箭头、白色圆面），直接叠在白色吊板上就行。需要按线路色着色的
 * （尖旗、线路色块、方向箭头）走 {@link #tinted(String, int)}：只保留 alpha，其余填成目标色。
 * <p>
 * 每张图还会算一次「墨水外框」（非透明像素的包围盒），用来把素材的不透明部分精确对齐到目标位置
 * ——比如方向箭头的底边要对齐线路下沿，这样即使素材四周留白也不会偏。
 * <p>
 * 素材缺失时返回 null，调用方自行跳过。第一次读要在渲染线程做（{@link #sprite(String)}），
 * 之后后台烘焙线程只读缓存。
 */
public final class TrdmSignAssets {

    public static final String STATION_CIRCLE = "stncircle";
    public static final String CURRENT_STATION = "thisstn";
    public static final String TRANSFER_2 = "transfer_circle_2";
    public static final String TRANSFER_3 = "transfer_circle_3";
    public static final String HEAD_TAIL = "headtail_stn";
    public static final String TRANSFER_LABEL = "transfer_label";
    public static final String COLOR_LINE = "colorline";
    public static final String ROUTE_LINE_ARROW = "routeline_arrow";
    /** 大站名牌左边那个地铁标志。 */
    public static final String STATION_LOGO = "station";
    /** 无障碍电梯牌左侧线路标签后面那个图标（用户自己放进来的图，缺了就不画）。 */
    public static final String ACCESSIBLE_ELEVATOR = "accessible_elevator";

    private static final Map<String, Sprite> SPRITES = new HashMap<>();
    private static final Map<String, BufferedImage> TINTED = new HashMap<>();
    private static final Map<String, BufferedImage> RECOLORED = new HashMap<>();
    private static final Map<String, List<Integer>> PALETTES = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    private TrdmSignAssets() {
    }

    /**
     * 素材 + 它的不透明像素包围盒。
     */
    public record Sprite(BufferedImage image, int inkX, int inkY, int inkW, int inkH) {
    }

    /**
     * 取素材（带缓存）。{@code mirror} 为 true 时返回左右翻转后的版本，墨水框也跟着翻。
     */
    public static synchronized Sprite sprite(String name, boolean mirror) {
        final String key = mirror ? name + "|m" : name;
        final Sprite cached = SPRITES.get(key);
        if (cached != null || MISSING.contains(key)) {
            return cached;
        }
        final Sprite base = loadSprite(name);
        if (base == null) {
            MISSING.add(key);
            return null;
        }
        final Sprite result = mirror ? mirror(base) : base;
        SPRITES.put(key, result);
        return result;
    }

    public static synchronized Sprite sprite(String name) {
        return sprite(name, false);
    }

    private static Sprite loadSprite(String name) {
        final Sprite cached = SPRITES.get(name);
        if (cached != null || MISSING.contains(name)) {
            return cached;
        }
        final BufferedImage image = loadMinecraft(name);
        if (image == null) {
            MISSING.add(name);
            return null;
        }
        final Sprite sprite = toSprite(image);
        SPRITES.put(name, sprite);
        return sprite;
    }

    /**
     * 只要 alpha、其余填成 {@code color} 的着色版本（尖旗 / 色块 / 线路箭头用）。
     */
    public static synchronized BufferedImage tinted(String name, int color) {
        final Sprite sprite = tintedSprite(name, color, false);
        return sprite == null ? null : sprite.image();
    }

    /**
     * 着色版本，且保留（可能镜像过的）墨水框 —— 画线路箭头这类「按墨水框对齐」的素材要用它。
     */
    public static synchronized Sprite tintedSprite(String name, int color, boolean mirror) {
        final Sprite sprite = sprite(name, mirror);
        if (sprite == null) {
            return null;
        }
        final int rgb = color & 0xFFFFFF;
        final String key = name + (mirror ? "|m" : "") + "@" + Integer.toHexString(rgb);
        final BufferedImage cached = TINTED.get(key);
        if (cached != null) {
            return new Sprite(cached, sprite.inkX(), sprite.inkY(), sprite.inkW(), sprite.inkH());
        }
        final BufferedImage source = sprite.image();
        final BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                result.setRGB(x, y, (source.getRGB(x, y) & 0xFF000000) | rgb);
            }
        }
        TINTED.put(key, result);
        return new Sprite(result, sprite.inkX(), sprite.inkY(), sprite.inkW(), sprite.inkH());
    }

    /**
     * 一次性把吊板要用的素材全读进缓存（在渲染线程调用；之后后台烘焙线程只读缓存）。
     */
    public static synchronized void primeAll() {
        for (final String name : new String[]{STATION_CIRCLE, CURRENT_STATION, TRANSFER_2, TRANSFER_3, HEAD_TAIL,
                TRANSFER_LABEL, COLOR_LINE, ROUTE_LINE_ARROW, STATION_LOGO, ACCESSIBLE_ELEVATOR}) {
            loadSprite(name);
        }
    }

    /**
     * 素材里各段圆弧原来的颜色，按<b>从正上方顺时针</b>排好 —— 换乘圈按线路色重上色时按这个顺序对应。
     * 只认「饱和」的像素（圆弧），白底、黑描边都不算。
     */
    public static synchronized List<Integer> arcPalette(String name) {
        final List<Integer> cached = PALETTES.get(name);
        if (cached != null) {
            return cached;
        }
        final Sprite sprite = sprite(name);
        if (sprite == null) {
            return List.of();
        }
        final List<Integer> palette = detectArcs(sprite.image());
        PALETTES.put(name, palette);
        return palette;
    }

    /**
     * 把素材上的各段圆弧改成给定颜色（颜色个数少于圆弧数时循环使用），白底和黑描边原样保留。
     * 素材读不到或找不到圆弧时返回 null。
     */
    public static synchronized BufferedImage recolored(String name, int[] colors) {
        if (colors == null || colors.length == 0) {
            return null;
        }
        final String key = name + "|" + Arrays.toString(colors);
        final BufferedImage cached = RECOLORED.get(key);
        if (cached != null) {
            return cached;
        }
        final Sprite sprite = sprite(name);
        if (sprite == null) {
            return null;
        }
        final List<Integer> palette = arcPalette(name);
        if (palette.isEmpty()) {
            return null;
        }
        final BufferedImage source = sprite.image();
        final BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                final int argb = source.getRGB(x, y);
                final int alpha = (argb >>> 24) & 0xFF;
                final int rgb = argb & 0xFFFFFF;
                if (alpha < 8 || !isSaturated(rgb)) {
                    result.setRGB(x, y, argb);
                    continue;
                }
                final int index = nearestColor(palette, rgb);
                result.setRGB(x, y, (alpha << 24) | (colors[Math.max(0, index) % colors.length] & 0xFFFFFF));
            }
        }
        RECOLORED.put(key, result);
        return result;
    }

    private static List<Integer> detectArcs(BufferedImage image) {
        // 每桶累计：x 和、y 和、像素数、r 和、g 和、b 和
        final Map<Integer, long[]> buckets = new HashMap<>();
        final int width = image.getWidth();
        final int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int argb = image.getRGB(x, y);
                if (((argb >>> 24) & 0xFF) < 200) {
                    continue;
                }
                final int rgb = argb & 0xFFFFFF;
                if (!isSaturated(rgb)) {
                    continue;
                }
                // 按 3 bit/通道聚类：平涂的弧会被合成一桶，抗锯齿的过渡色各自成小桶
                final int key = ((rgb >> 21) & 0x7) << 6 | ((rgb >> 13) & 0x7) << 3 | ((rgb >> 5) & 0x7);
                final long[] bucket = buckets.computeIfAbsent(key, ignored -> new long[6]);
                bucket[0] += x;
                bucket[1] += y;
                bucket[2]++;
                bucket[3] += (rgb >> 16) & 0xFF;
                bucket[4] += (rgb >> 8) & 0xFF;
                bucket[5] += rgb & 0xFF;
            }
        }
        if (buckets.isEmpty()) {
            return List.of();
        }

        final List<long[]> sorted = new ArrayList<>(buckets.values());
        sorted.sort((a, b) -> Long.compare(b[2], a[2]));

        final List<Integer> colors = new ArrayList<>();
        final List<Double> angles = new ArrayList<>();
        final int minimum = Math.max(40, width * height / 200);
        final double centerX = (width - 1) / 2.0;
        final double centerY = (height - 1) / 2.0;
        for (final long[] bucket : sorted) {
            if (colors.size() >= 4) {
                break;
            }
            final long count = bucket[2];
            if (count < minimum) {
                continue;
            }
            final int rgb = ((int) (bucket[3] / count) << 16) | ((int) (bucket[4] / count) << 8) | (int) (bucket[5] / count);
            boolean duplicate = false;
            for (final int existing : colors) {
                // distance 返回的是欧氏距离的平方，这里 60² 以内算同一段圆弧（素材里的弧带渐变，会分成两桶）
                if (distance(existing, rgb) < 3600) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                continue;
            }
            colors.add(rgb);
            // 从正上方顺时针的角度：0° 在 12 点，90° 在 3 点
            angles.add(Math.toDegrees(Math.atan2(bucket[0] / (double) count - centerX,
                    centerY - bucket[1] / (double) count)));
        }

        final Integer[] ordered = colors.toArray(new Integer[0]);
        final Double[] orderAngles = angles.toArray(new Double[0]);
        final Integer[] index = new Integer[ordered.length];
        for (int i = 0; i < index.length; i++) {
            index[i] = i;
        }
        // 按重心角度排序：左(−90°) → 上(0°) → 右(+90°) → 下(±180°)。
        // 素材实测下来第一段正好是「参考图里放当前线路色」的那一段（两段弧是上方、三段弧是左侧）。
        Arrays.sort(index, (a, b) -> Double.compare(orderAngles[a], orderAngles[b]));

        final List<Integer> result = new ArrayList<>(ordered.length);
        for (final int i : index) {
            result.add(ordered[i]);
        }
        return result;
    }

    private static boolean isSaturated(int rgb) {
        final int red = (rgb >> 16) & 0xFF;
        final int green = (rgb >> 8) & 0xFF;
        final int blue = rgb & 0xFF;
        final int max = Math.max(red, Math.max(green, blue));
        final int min = Math.min(red, Math.min(green, blue));
        return max > 45 && (max - min) > max * 0.35;
    }

    private static int nearestColor(List<Integer> palette, int rgb) {
        int best = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < palette.size(); i++) {
            final int distance = distance(palette.get(i), rgb);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private static int distance(int a, int b) {
        final int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        final int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        final int db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }

    /**
     * 离线预览用：直接从磁盘目录装载素材（游戏里没有 Minecraft 实例的场合）。
     */
    public static synchronized void primeFromDirectory(Path directory) {
        final String[] names = {
                STATION_CIRCLE, CURRENT_STATION, TRANSFER_2, TRANSFER_3, HEAD_TAIL,
                TRANSFER_LABEL, COLOR_LINE, ROUTE_LINE_ARROW, STATION_LOGO, ACCESSIBLE_ELEVATOR
        };
        for (final String name : names) {
            try {
                final Path file = directory.resolve(name + ".png");
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                final BufferedImage image = ImageIO.read(file.toFile());
                if (image != null) {
                    SPRITES.put(name, toSprite(image));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static Sprite toSprite(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getRGB(x, y) >>> 24) & 0xFF) > 8) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            // 全透明：当成满幅
            return new Sprite(image, 0, 0, image.getWidth(), image.getHeight());
        }
        return new Sprite(image, minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    public static Sprite mirror(Sprite sprite) {
        final BufferedImage source = sprite.image();
        final int width = source.getWidth();
        final int height = source.getHeight();
        final BufferedImage flipped = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                flipped.setRGB(width - 1 - x, y, source.getRGB(x, y));
            }
        }
        return new Sprite(flipped, width - (sprite.inkX() + sprite.inkW()), sprite.inkY(), sprite.inkW(), sprite.inkH());
    }

    /**
     * 把整张素材画进以 {@code (centerX, centerY)} 为中心、边长 {@code size} 的方框里（素材按自身宽高比塞进去）。
     */
    public static void drawSprite(Graphics2D graphics, BufferedImage image, float centerX, float centerY, float size) {
        if (image == null || size <= 0) {
            return;
        }
        final int width = Math.max(1, Math.round(size));
        final int height = Math.max(1, Math.round(size * image.getHeight() / Math.max(1F, image.getWidth())));
        graphics.drawImage(image,
                Math.round(centerX - width / 2F), Math.round(centerY - height / 2F),
                Math.round(centerX - width / 2F) + width, Math.round(centerY - height / 2F) + height,
                0, 0, image.getWidth(), image.getHeight(), null);
    }

    /**
     * 把素材<b>不透明部分</b>对齐到 {@code (x, y, width, height)} 这个框（素材四周的留白不算）。
     */
    public static void drawSpriteInk(Graphics2D graphics, Sprite sprite, float x, float y, float width, float height) {
        if (sprite == null || width <= 0 || height <= 0) {
            return;
        }
        final BufferedImage image = sprite.image();
        final float scaleX = width / Math.max(1, sprite.inkW());
        final float scaleY = height / Math.max(1, sprite.inkH());
        final int destX = Math.round(x - sprite.inkX() * scaleX);
        final int destY = Math.round(y - sprite.inkY() * scaleY);
        final int destW = Math.max(1, Math.round(image.getWidth() * scaleX));
        final int destH = Math.max(1, Math.round(image.getHeight() * scaleY));
        graphics.drawImage(image, destX, destY, destX + destW, destY + destH,
                0, 0, image.getWidth(), image.getHeight(), null);
    }

    /**
     * 取素材并在画不了时安全返回 null（只在渲染线程第一次调用会真的读盘）。
     */
    private static BufferedImage loadMinecraft(String name) {
        try {
            final ResourceLocation location = new ResourceLocation(Trdm.MOD_ID, "textures/block/sign/" + name + ".png");
            final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isPresent()) {
                try (final InputStream stream = resource.get().open()) {
                    return ImageIO.read(stream);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
