package top.xiaotang27.trdm.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import top.xiaotang27.trdm.Trdm;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * PIS 屏幕用的位图素材。
 * <p>
 */
public final class TrdmPisAssets {

    /** 背景板。 */
    public static final String BACKGROUND = "tjpids";
    /** 车厢：头车。 */
    public static final String CAR_HEAD = "tc";
    /** 车厢：中间车。 */
    public static final String CAR_MIDDLE = "zj";
    /** 车厢：尾车。 */
    public static final String CAR_TAIL = "0tc";
    /** 默认广告（资源包没给广告时用）。 */
    public static final String AD_DEFAULT = "adsdef";

    /** 内置素材所在目录（相对 assets/trdm）。 */
    public static final String DIRECTORY = "textures/block/pis/";

    private static final Map<String, BufferedImage> IMAGES = new HashMap<>();
    private static final Map<String, BufferedImage> ADS = new HashMap<>();
    private static final Map<String, Gif> GIFS = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();

    /**
     * 动图最多解这么多帧。
     */
    private static final int MAX_GIF_FRAMES = 120;
    /**
     * 一张动图所有帧占用内存的上限（字节）。广告区在版面上只有 720×432，
     * 超过这个预算就把每帧缩小 —— 资源包塞个 1080p 的大 gif 也不会把客户端撑爆。
     */
    private static final long GIF_BUDGET_BYTES = 32L * 1024 * 1024;

    private TrdmPisAssets() {
    }

    /**
     * 一张动图：所有帧 + 每帧停留毫秒。
     * @param frames  帧
     */
    public record Gif(List<BufferedImage> frames, int[] delays, int totalMs) {

        /** 按时间取第几帧（循环播放）。 */
        public int frameAt(long nowMs) {
            if (frames.isEmpty() || totalMs <= 0) {
                return 0;
            }
            int time = (int) Math.floorMod(nowMs, (long) totalMs);
            for (int index = 0; index < delays.length; index++) {
                time -= delays[index];
                if (time < 0) {
                    return index;
                }
            }
            return frames.size() - 1;
        }

        /** 按时间取当前帧。 */
        public BufferedImage imageAt(long nowMs) {
            return frames.get(frameAt(nowMs));
        }
    }

    /** 内置素材（背景 / 车厢 / 默认广告）。 */
    public static synchronized BufferedImage image(String name) {
        final BufferedImage cached = IMAGES.get(name);
        if (cached != null || MISSING.contains(name)) {
            return cached;
        }
        final BufferedImage loaded = readMinecraft(new ResourceLocation(Trdm.MOD_ID, DIRECTORY + name + ".png"));
        if (loaded == null) {
            MISSING.add(name);
            return null;
        }
        IMAGES.put(name, loaded);
        return loaded;
    }

    /** 广告图（可以是任何资源包里的图）。 */
    public static synchronized BufferedImage ad(ResourceLocation location) {
        if (location == null) {
            return null;
        }
        final String key = location.toString();
        if (ADS.containsKey(key)) {
            return ADS.get(key);
        }
        if (MISSING.contains(key)) {
            return null;
        }
        final BufferedImage loaded = readMinecraft(location);
        if (loaded == null) {
            MISSING.add(key);
            return null;
        }
        ADS.put(key, loaded);
        return loaded;
    }

    /** 这个资源是不是动图（按扩展名判断）。 */
    public static boolean isGif(ResourceLocation location) {
        return location != null && location.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".gif");
    }

    /**
     * 广告图当前这一帧：静态图直接返回整张，<b>gif 返回按时间算出来的那一帧</b>，
     * <b>mp4 走 {@link TrdmPisVideo}</b>（第一帧还没解出来时返回 null，调用方退回默认广告图）。
     */
    public static synchronized BufferedImage ad(ResourceLocation location, long nowMs) {
        if (TrdmPisVideo.isVideo(location)) {
            return TrdmPisVideo.frame(location, nowMs);
        }
        if (!isGif(location)) {
            return ad(location);
        }
        final Gif gif = gif(location);
        return gif == null ? null : gif.imageAt(nowMs);
    }

    /**
     * 动图 / 视频当前是第几帧（静态图 / 缺失返回 -1）。
     * 渲染器拿它拼进烘焙缓存的 key，帧一变就重贴一次广告区。
     */
    public static synchronized int adFrame(ResourceLocation location, long nowMs) {
        if (TrdmPisVideo.isVideo(location)) {
            return TrdmPisVideo.frameIndex(location, nowMs);
        }
        if (!isGif(location)) {
            return -1;
        }
        final Gif gif = gif(location);
        return gif == null ? -1 : gif.frameAt(nowMs);
    }

    /** 动图（拆好帧的那个）；不是动图或读不到返回 null。 */
    public static synchronized Gif gif(ResourceLocation location) {
        if (!isGif(location)) {
            return null;
        }
        final String key = location.toString();
        final Gif cached = GIFS.get(key);
        if (cached != null || MISSING.contains(key)) {
            return cached;
        }
        final Gif loaded = readGif(location);
        if (loaded == null) {
            MISSING.add(key);
            return null;
        }
        GIFS.put(key, loaded);
        return loaded;
    }

    /** 资源包重载后清空缓存（图片可能被换掉了）。 */
    public static synchronized void clear() {
        IMAGES.clear();
        ADS.clear();
        GIFS.clear();
        MISSING.clear();
        TrdmPisVideo.clear();
    }

    private static BufferedImage readMinecraft(ResourceLocation location) {
        try {
            final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty()) {
                return null;
            }
            try (final InputStream stream = resource.get().open()) {
                return clampImage(ImageIO.read(stream), location);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /** 静态广告最大宽度：广告区在版面上只有 720 像素宽，比这更大的图纯浪费内存。 */
    private static final int MAX_AD_WIDTH = 1280;

    /** 过大的静态图先缩小再进缓存（资源包塞张 4K 图也不会一下吃 30MB）。 */
    private static BufferedImage clampImage(BufferedImage image, ResourceLocation location) {
        if (image == null || image.getWidth() <= MAX_AD_WIDTH) {
            return image;
        }
        final int width = MAX_AD_WIDTH;
        final int height = Math.max(1, Math.round(image.getHeight() * (width / (float) image.getWidth())));
        Trdm.LOGGER.info("[pis ads] {} 太大（{}x{}），缩到 {}x{}",
                location.getPath(), image.getWidth(), image.getHeight(), width, height);
        return scaleTo(image, width, height);
    }

    /** 缩放到指定尺寸（已经是这个尺寸就原样返回）。 */
    private static BufferedImage scaleTo(BufferedImage source, int width, int height) {
        if (source == null) {
            return null;
        }
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }
        final BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /**
     * 把一个 gif 拆成帧：用 JDK 自带的 gif 解码器逐帧读，帧间隔从
     * {@code GraphicControlExtension.delayTime}（百分之一秒）里取。
     */
    private static Gif readGif(ResourceLocation location) {
        try {
            final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isEmpty()) {
                return null;
            }
            try (final InputStream stream = resource.get().open();
                 final javax.imageio.stream.ImageInputStream input = ImageIO.createImageInputStream(stream)) {
                if (input == null) {
                    return null;
                }
                final java.util.Iterator<javax.imageio.ImageReader> readers =
                        ImageIO.getImageReadersByFormatName("gif");
                if (!readers.hasNext()) {
                    return null;
                }
                final javax.imageio.ImageReader reader = readers.next();
                try {
                    reader.setInput(input, false, false);
                    final int count = Math.min(MAX_GIF_FRAMES, Math.max(1, reader.getNumImages(true)));
                    // 先按「帧数 × 尺寸」算该缩到多大，保证整段动图的内存不超预算
                    final int sourceWidth = Math.max(1, reader.getWidth(0));
                    final int sourceHeight = Math.max(1, reader.getHeight(0));
                    final long fullBytes = (long) count * sourceWidth * sourceHeight * 4L;
                    final double downscale = fullBytes > GIF_BUDGET_BYTES
                            ? Math.sqrt(GIF_BUDGET_BYTES / (double) fullBytes)
                            : 1.0D;
                    final int targetWidth = Math.max(1, (int) Math.round(sourceWidth * downscale));
                    final int targetHeight = Math.max(1, (int) Math.round(sourceHeight * downscale));
                    if (downscale < 1.0D) {
                        Trdm.LOGGER.info("[pis ads] {} 太大（{} 帧 {}x{}），缩到 {}x{}",
                                location.getPath(), count, sourceWidth, sourceHeight, targetWidth, targetHeight);
                    }
                    final List<BufferedImage> frames = new java.util.ArrayList<>(count);
                    final int[] delays = new int[count];
                    int total = 0;
                    for (int index = 0; index < count; index++) {
                        final BufferedImage frame = reader.read(index);
                        if (frame == null) {
                            break;
                        }
                        frames.add(scaleTo(frame, targetWidth, targetHeight));
                        final int delay = Math.max(20, readGifDelay(reader, index));
                        delays[index] = delay;
                        total += delay;
                    }
                    if (frames.isEmpty()) {
                        return null;
                    }
                    return new Gif(List.copyOf(frames),
                            java.util.Arrays.copyOf(delays, frames.size()), Math.max(1, total));
                } finally {
                    reader.dispose();
                }
            }
        } catch (Exception e) {
            Trdm.LOGGER.warn("[pis ads] gif 读不了 {} : {}", location, e.toString());
            return null;
        }
    }

    /** 一帧停多久（毫秒）；gif 里没写就按 100ms。 */
    private static int readGifDelay(javax.imageio.ImageReader reader, int index) {
        try {
            final org.w3c.dom.Node root = reader.getImageMetadata(index)
                    .getAsTree("javax_imageio_gif_image_1.0");
            final org.w3c.dom.NodeList children = root.getChildNodes();
            for (int node = 0; node < children.getLength(); node++) {
                if (!"GraphicControlExtension".equals(children.item(node).getNodeName())) {
                    continue;
                }
                final org.w3c.dom.Node delay = children.item(node).getAttributes().getNamedItem("delayTime");
                if (delay != null) {
                    return Math.max(20, Integer.parseInt(delay.getNodeValue().trim()) * 10);
                }
            }
        } catch (Exception ignored) {
            // 没有元数据就按默认
        }
        return 100;
    }

    /**
     * 离线预览用：直接从磁盘目录读内置素材。
     */
    public static synchronized void primeFromDirectory(Path directory) {
        for (final String name : new String[]{BACKGROUND, CAR_HEAD, CAR_MIDDLE, CAR_TAIL, AD_DEFAULT}) {
            try {
                final Path file = directory.resolve(name + ".png");
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                final BufferedImage image = ImageIO.read(file.toFile());
                if (image != null) {
                    IMAGES.put(name, image);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
