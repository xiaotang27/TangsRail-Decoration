package top.xiaotang27.trdm.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jcodec.api.awt.AWTFrameGrab;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import top.xiaotang27.trdm.Trdm;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * PIS 广告的 <b>mp4 播放器</b>（纯 Java 解码，jar 里自带 JCodec，不依赖系统装 ffmpeg）。
 * <p>
 * <b>不用视频的时候一律不启动</b>：
 * <ul>
 *   <li>只有「当前正要播的那条广告」是 {@code .mp4 / .m4v / .mov} 时，才会读文件、建解码器、起线程；</li>
 *   <li>广告轮播切走 → {@link #keepOnly(ResourceLocation)} 立刻关掉解码器；</li>
 *   <li>超过 {@link #IDLE_MS} 没人要帧 → 工作线程自己退出；</li>
 *   <li>解码全程在后台线程，渲染线程只做一次「取当前帧」的引用；</li>
 *   <li>只保留「当前帧」一张图（不解码整片、不缓存整片），内存几 MB 以内。</li>
 * </ul>
 * 播放时钟由解码线程自己走（按容器里的帧率，读不到就按 {@link #FALLBACK_FPS}），
 * <b>不依赖容器里能不能读到总帧数</b> —— 读不到也能正常循环播。
 * 解不出来（非 H.264 / 文件坏了）就返回 null，调用方退回默认广告图，不会崩客户端。
 */
public final class TrdmPisVideo {

    /** 认这些扩展名是视频。 */
    private static final String[] EXTENSIONS = {".mp4", ".m4v", ".mov"};
    /** 多久没人要帧就停线程、放掉解码器。 */
    private static final long IDLE_MS = 4000L;
    /** 解出来的帧超过这个宽度就缩小（广告区在贴图上只有 720 像素宽，再大纯浪费）。 */
    private static final int MAX_WIDTH = 512;
    /** 视频文件体积上限：整个文件要读进内存给解码器当可寻址通道，太大直接拒掉。 */
    private static final long MAX_VIDEO_BYTES = 64L * 1024 * 1024;
    /** 容器里读不到帧率时的兜底。 */
    private static final float FALLBACK_FPS = 25F;

    private static final Map<String, Clip> CLIPS = new HashMap<>();
    /** 视频时长缓存（毫秒）：广告 json 没写 time 时用它当默认显示时长。 */
    private static final Map<String, Long> DURATIONS = new HashMap<>();

    private TrdmPisVideo() {
    }

    /**
     * 视频时长（毫秒）；不是视频 / 读不出来返回 -1。
     * 用广告 json 里没写 {@code time} 的片子拿它当默认显示时长。
     */
    public static long durationMs(ResourceLocation location) {
        if (!isVideo(location)) {
            return -1L;
        }
        final String key = location.toString();
        synchronized (DURATIONS) {
            final Long cached = DURATIONS.get(key);
            if (cached != null) {
                return cached;
            }
        }
        long result = -1L;
        try {
            final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isPresent()) {
                try (InputStream stream = resource.get().open()) {
                    final byte[] data = stream.readAllBytes();
                    final AWTFrameGrab grab = AWTFrameGrab.createAWTFrameGrab(
                            ByteBufferSeekableByteChannel.readFromByteBuffer(ByteBuffer.wrap(data)));
                    final DemuxerTrackMeta meta = grab.getVideoTrack().getMeta();
                    final int frames = meta.getTotalFrames();
                    final double seconds = meta.getTotalDuration();
                    if (seconds > 0) {
                        result = Math.round(seconds * 1000.0D);
                    } else if (frames > 0) {
                        result = Math.round(frames * 1000.0D / FALLBACK_FPS);
                    }
                }
            }
        } catch (Throwable e) {
            Trdm.LOGGER.warn("[pis] 读不出视频时长 {} : {}", location.getPath(), e.toString());
        }
        synchronized (DURATIONS) {
            DURATIONS.put(key, result);
        }
        return result;
    }

    /** 这个资源是不是视频。 */
    public static boolean isVideo(ResourceLocation location) {
        if (location == null || !top.xiaotang27.trdm.TrdmConfig.pisVideoEnabled()) {
            return false;
        }
        final String path = location.getPath().toLowerCase(java.util.Locale.ROOT);
        for (final String extension : EXTENSIONS) {
            if (path.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前这一帧（第一帧还没解出来就返回 null，调用方会退回默认广告图）。
     *
     * @param nowMs 只用来记「还有人在看」，播放进度由解码线程自己走
     */
    public static BufferedImage frame(ResourceLocation location, long nowMs) {
        final Clip clip = clip(location);
        if (clip == null) {
            return null;
        }
        clip.touch();
        return clip.current;
    }

    /**
     * 版本号：每出一帧 +1（不是视频/解码器都没建起来返回 -1）。
     * 渲染器拿它拼烘焙缓存的 key —— 换帧就重贴广告区，帧号内部怎么算不用关心。
     */
    public static int frameIndex(ResourceLocation location, long nowMs) {
        final Clip clip = clip(location);
        if (clip == null) {
            return -1;
        }
        clip.touch();
        return clip.frameSeq;
    }

    /**
     * 只留当前这条广告的解码器：轮播切走的那条立刻停（没有视频时是一次空 map 判断，零开销）。
     */
    public static void keepOnly(ResourceLocation location) {
        final String keep = isVideo(location) ? location.toString() : null;
        synchronized (CLIPS) {
            if (CLIPS.isEmpty()) {
                return;
            }
            CLIPS.entrySet().removeIf(entry -> {
                if (entry.getKey().equals(keep)) {
                    return false;
                }
                entry.getValue().close();
                return true;
            });
        }
    }

    /** 资源包重载 / 退出世界时全部关掉。 */
    public static void clear() {
        synchronized (CLIPS) {
            for (final Clip clip : CLIPS.values()) {
                clip.close();
            }
            CLIPS.clear();
        }
    }

    /** 有几个解码器还活着（调试用）。 */
    public static int activeClips() {
        synchronized (CLIPS) {
            return CLIPS.size();
        }
    }

    // ===== 内部实现 =====

    private static Clip clip(ResourceLocation location) {
        if (!isVideo(location)) {
            return null;
        }
        final String key = location.toString();
        synchronized (CLIPS) {
            final Clip existing = CLIPS.get(key);
            if (existing != null && !existing.closed) {
                return existing;
            }
            final Clip created = Clip.create(location);
            if (created != null) {
                CLIPS.put(key, created);
            }
            return created;
        }
    }

    /** 一条视频的解码状态；只有 {@link #clip(ResourceLocation)} 会创建它。 */
    private static final class Clip {

        private final byte[] data;
        private final Object lock = new Object();
        /** 当前帧；解码线程写、渲染线程读，所以是 volatile。 */
        volatile BufferedImage current;
        /** 每出一帧 +1，当缓存 key 用。 */
        volatile int frameSeq;
        volatile long lastRequest = System.currentTimeMillis();
        volatile boolean closed;
        /** 第一帧出来之前打了日志没有（避免刷屏）。 */
        private boolean warned = false;

        private Clip(byte[] data) {
            this.data = data;
        }

        static Clip create(ResourceLocation location) {
            try {
                final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
                if (resource.isEmpty()) {
                    Trdm.LOGGER.warn("[pis] 找不到视频 {}", location);
                    return null;
                }
                final byte[] data;
                try (InputStream stream = resource.get().open()) {
                    data = stream.readAllBytes();
                }
                if (data.length == 0) {
                    Trdm.LOGGER.warn("[pis] 视频 {} 是空文件", location.getPath());
                    return null;
                }
                if (data.length > MAX_VIDEO_BYTES) {
                    Trdm.LOGGER.warn("[pis] 视频 {} 太大（{} MB），跳过不播（上限 {} MB）",
                            location.getPath(), data.length / (1024 * 1024), MAX_VIDEO_BYTES / (1024 * 1024));
                    return null;
                }
                final Clip clip = new Clip(data);
                final Thread worker = new Thread(clip::run, "trdm-pis-video");
                worker.setDaemon(true);
                worker.setPriority(Thread.MIN_PRIORITY + 1);
                worker.start();
                return clip;
            } catch (Exception e) {
                Trdm.LOGGER.warn("[pis] 读不了视频 {} : {}", location, e.toString());
                return null;
            }
        }

        void touch() {
            lastRequest = System.currentTimeMillis();
        }

        void close() {
            closed = true;
            current = null;
            frameSeq = 0;
        }

        /**
         * 解码线程：自己按帧率走时钟。
         * <p>
         * 每一帧在「本轮开始 + 帧号 / fps」这个时刻发布，所以播放速度和视频原速一致；
         * 到片尾（{@code getNativeFrame()} 返回 null）就 seek 回开头重新计时，
         * <b>不需要容器里的总帧数</b> —— 很多 mp4 那里读出来是 0。
         */
        private void run() {
            try {
                final AWTFrameGrab grab = AWTFrameGrab.createAWTFrameGrab(
                        ByteBufferSeekableByteChannel.readFromByteBuffer(ByteBuffer.wrap(data)));
                float fps = FALLBACK_FPS;
                try {
                    final DemuxerTrackMeta meta = grab.getVideoTrack().getMeta();
                    final int frames = meta.getTotalFrames();
                    final double seconds = meta.getTotalDuration();
                    if (frames > 0 && seconds > 0) {
                        final float measured = (float) (frames / seconds);
                        if (measured >= 1F && measured <= 120F) {
                            fps = measured;
                        }
                    }
                } catch (Exception ignored) {
                    // 读不到元数据就用兜底帧率
                }
                Trdm.LOGGER.info("[pis] 开始播视频（{} 帧/秒，{} KB）", fps, data.length / 1024);

                long passStart = System.currentTimeMillis();
                int decoded = 0;
                int emptyReads = 0;
                org.jcodec.api.PictureWithMetadata pending = null;
                double pendingTimestamp = 0.0D;
                while (!closed) {
                    final long now = System.currentTimeMillis();
                    if (now - lastRequest > IDLE_MS) {
                        break;
                    }
                    // 先取一帧（含它自己的时间戳），再按「这一帧该在什么时候出现」等
                    if (pending == null) {
                        final org.jcodec.api.PictureWithMetadata next = grab.getNativeFrameWithMetadata();
                        if (next == null || next.getPicture() == null) {
                            if (++emptyReads > 3) {
                                // 连着读到空帧，说明这个文件一帧都解不出来，别在这儿空转
                                Trdm.LOGGER.warn("[pis] 视频解不出任何帧（{} KB），停止播放，改用默认广告图",
                                        data.length / 1024);
                                break;
                            }
                            // 片尾：回开头，重新计时，广告是循环播的
                            decoded = 0;
                            passStart = System.currentTimeMillis();
                            grab.seekToFrameSloppy(0);
                            continue;
                        }
                        emptyReads = 0;
                        pending = next;
                        // 用容器里这一帧自己的时间戳排程：变帧率（手机录屏/VFR）的片子
                        // 按「帧号 / 平均帧率」推会一快一慢，看着就像抽搐
                        double timestamp = next.getTimestamp();
                        if (Double.isNaN(timestamp) || timestamp < 0 || timestamp > 6000) {
                            timestamp = decoded / (double) fps;
                        }
                        pendingTimestamp = timestamp;
                    }
                    final long dueAt = passStart + Math.round(pendingTimestamp * 1000.0D);
                    final long behind = now - dueAt;
                    if (behind < -1L) {
                        // 细粒度等待：一次睡 50ms 会被系统时钟粒度放大，帧间距就抖了
                        Thread.sleep(Math.min(5L, Math.max(1L, (-behind) / 2L)));
                        continue;
                    }
                    if (behind > 500L) {
                        // 卡了一下（GC / 加载区块）：把整条时间轴往后挪，避免连发一串帧
                        passStart += behind;
                    }
                    current = scale(AWTUtil.toBufferedImage(pending.getPicture()));
                    frameSeq++;
                    pending = null;
                    decoded++;
                }
            } catch (Throwable e) {
                Trdm.LOGGER.warn("[pis] 视频解码失败: {}", e.toString());
            } finally {
                closed = true;
                synchronized (CLIPS) {
                    CLIPS.entrySet().removeIf(entry -> entry.getValue() == this);
                }
            }
        }

        private BufferedImage scale(BufferedImage source) {
            if (source.getWidth() <= MAX_WIDTH) {
                return source;
            }
            final int width = MAX_WIDTH;
            final int height = Math.max(1, Math.round(source.getHeight() * (width / (float) source.getWidth())));
            final BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            final Graphics2D graphics = target.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(source, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }
            return target;
        }
    }
}
