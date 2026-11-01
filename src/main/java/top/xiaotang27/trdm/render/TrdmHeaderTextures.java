package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.platform.NativeImage;
import mtr.client.ClientCache;
import mtr.client.ClientData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import top.xiaotang27.trdm.Trdm;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 门楣吊板的动态贴图缓存 + AWT 排版。
 * <p>
 */
public final class TrdmHeaderTextures {

    /**
     * 贴图高度（像素）。贴图宽度按门楣段的宽高比换算，所以横向越长分辨率越高。
     * 512 是「贴进 7/16 格高的吊板后，近距离看也不糊」的档位（原来 256 会明显看出像素块）。
     */
    private static final int TEXTURE_HEIGHT = 512;

    /**
     * 线路图吊板的贴图高度要比别的吊板高：这张图元素细（换乘文字只有 3.8% 高度，
     * 256 高的图上才 10 像素），翻倍画才不至于糊。
     */
    private static final int ROUTE_MAP_TEXTURE_HEIGHT = 1024;

    /** 线路图贴图的像素总宽上限（超过就压高度，见 {@link #routeMapTextureSize(float)}）。 */
    private static final int ROUTE_MAP_MAX_TEXTURE_WIDTH = 4096;

    /** 普通吊板（站名 / 站点信息 / 终点站）贴图的像素宽上限。 */
    private static final int PANEL_MAX_TEXTURE_WIDTH = 4096;

    /**
     * 装饰带的贴图高度（像素）。带子本身只有 4/16 格高，160 像素 = 640 像素/格，
     * 近距离看人字纹和中央图文都不糊，同时一条 12 格长的带子也只有 3 千多像素宽。
     */
    private static final int BAND_TEXTURE_HEIGHT = 160;
    private static final int BAND_MAX_TEXTURE_WIDTH = 4096;

    /**
     * 站名牌贴图的像素尺寸。牌面是 1 格宽 × 3/4 格高（4:3），
     * 384 高 ≈ 512 像素/格，近距离看字不糊。
     */
    private static final int SIGN_TEXTURE_HEIGHT = 384;
    private static final int SIGN_TEXTURE_WIDTH = 512;

    /**
     * 贴图缓存条目上限。太小会「抽搐」：一帧里同时在用的贴图（多块吊板 + 线路图 + 装饰带 + 站名牌）
     * 一旦超过上限，每帧都在淘汰-重烘焙之间来回，画面就会闪。
     */
    private static final int MAX_CACHE_SIZE = 48;
    private static final int TEXT_COLOR = 0xFF000000;

    /**
     * 站名牌 / 门楣 / 吊板这些一律用系统普通黑体（SimHei），装不上再按顺序退。
     * <p>
     * <b>只有 PIS 屏用打进 jar 的思源黑体</b>（见 {@link #pisFont(float)}），其余牌子都用这套。
     */
    private static final String[] FONT_CANDIDATES = {
            "SimHei", "黑体", "Microsoft YaHei",
            "Source Han Sans SC", "思源黑体", "Source Han Sans CN", "Source Han Sans",
            "Noto Sans CJK SC",
            "PingFang SC", "Heiti SC", "WenQuanYi Zen Hei", "Arial Unicode MS"
    };

    /**
     * 英文用 Arial，装不上再退到同类无衬线字体。
     */
    private static final String[] LATIN_FONT_CANDIDATES = {
            "Arial", "Liberation Sans", "Helvetica", "DejaVu Sans", "Nimbus Sans", "Segoe UI"
    };

    private static final Pattern ROUTE_NUMBER_PATTERN = Pattern.compile("^\\s*(\\d+)\\s*(.*)$");

    private static final Map<String, ResourceLocation> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static final Set<String> PENDING = new HashSet<>();
    /** 烘焙抛过异常的 key：同一种内容不反复重试（否则每帧都烘一次，卡成幻灯片）。 */
    private static final Set<String> FAILED = new HashSet<>();
    private static final Queue<Runnable> TASKS = new ConcurrentLinkedQueue<>();
    /** 被淘汰、但还没真正释放的贴图（避免释放掉当帧还在用的那张，那会闪白/闪黑）。 */
    private static final java.util.Deque<ResourceLocation> PENDING_RELEASE = new java.util.ArrayDeque<>();
    /** 淘汰多少张之后才真正释放最老的那张。 */
    private static final int RELEASE_GRACE = 32;

    private static Font baseFont;
    private static Font latinBaseFont;
    private static float inkRatio = -1F;
    private static float pisInkRatio = -1F;
    private static int counter;

    private TrdmHeaderTextures() {
    }

    /**
     * 取（必要时异步生成）一块门楣贴图。返回 null 表示还在烘焙，这一帧先不画。
     */
    public static ResourceLocation get(long platformId, String stationName, List<Badge> badges, float aspect) {
        final String key = "name|" + platformId + "|" + stationName + "|" + badgeKey(badges) + "|" + formatAspect(aspect);
        final List<Badge> badgeSnapshot = new ArrayList<>(badges);
        return resolve(key, () -> generate(stationName, badgeSnapshot, aspect));
    }

    /**
     * 站点信息吊板：箭头 +「开往 X」+「下一站 Y」。
     * @param destination 开往的终点站（MTR 的「中文|English」原样字符串）
     */
    public static ResourceLocation getStationInfo(long platformId, String destination, String nextStation, boolean arrowLeft, int blocks, float aspect) {
        final String key = "info|" + platformId + "|" + destination + "|" + nextStation + "|" + arrowLeft + "|" + blocks + "|" + formatAspect(aspect);
        return resolve(key, () -> generateStationInfo(destination, nextStation, arrowLeft, blocks, aspect));
    }

    /**
     * 终点站吊板：整块居中「终点站 / Terminal Station」，字体沿用滑动门站名吊板那一套。
     * 内容与平台无关（不含站台编号、不含方向），所以终点站整排吊板长得完全一样。
     */
    public static ResourceLocation getTerminal(long platformId, float aspect) {
        final String key = "terminal|" + platformId + "|" + formatAspect(aspect);
        return resolve(key, () -> generateTerminal(aspect));
    }

    /**
     * 线路图吊板：一条粗横线（后方灰、前方线路色）+ 方向箭头 + 站点图标 + 斜排站名
     * + 换乘尖旗与文字 + 右下角图例。版面实现在 {@link TrdmRouteMap}。
     */
    public static ResourceLocation getRouteMapPanel(long platformId, float aspect, boolean arrowLeft,
                                                   float runLeftFraction, float runRightFraction, String signature) {
        final String key = "rmap|" + platformId + "|" + arrowLeft + "|" + formatAspect(aspect)
                + "|" + formatAspect(runLeftFraction) + "|" + formatAspect(runRightFraction) + "|" + signature;
        return resolve(key, () -> generateRouteMapPanel(platformId, aspect, arrowLeft, runLeftFraction, runRightFraction));
    }

    /**
     * 线路图贴图的像素尺寸。宽高比很大时（很长的玻璃段）固定总像素宽、把高度压下来，
     * 这样贴图比例始终等于画的四边形比例（不会横向拉伸），代价只是分辨率低一点。
     */
    /**
     * 站名牌贴图（1 格宽 × 3/4 格高 = 4:3）。
     * @param stationName 本站（MTR 的「中文|English」字符串）
     */
    public static ResourceLocation getStationSign(String stationName, String nextStation, int routeColor, boolean terminal) {
        final String key = "sign|" + stationName + "|" + nextStation + "|"
                + Integer.toHexString(routeColor & 0xFFFFFF) + "|" + terminal;
        // 文案在渲染线程取好再交给烘焙线程（语言表不是线程安全的）
        final String nextLabel = terminal
                ? Component.translatable("gui.trdm.station_sign.terminal").getString()
                : Component.translatable("gui.trdm.station_sign.next").getString();
        final String nextLabelEn = terminal
                ? Component.translatable("gui.trdm.station_sign.terminal_en").getString()
                : Component.translatable("gui.trdm.station_sign.next_en").getString();
        // 同步烘焙：站台一改，牌面当帧就换
        return resolveNow(key, () -> toNativeImage(TrdmStationSignPanel.render(
                SIGN_TEXTURE_WIDTH, SIGN_TEXTURE_HEIGHT, stationName, nextStation, routeColor,
                terminal, nextLabel, nextLabelEn)));
    }

    /**
     * 大站名牌贴图（牌高 3/4 格，宽度随合并段长度变，所以宽高比要传进来）。
     * @param stationName  本站（MTR 的「中文|English」）
     */
    public static ResourceLocation getBigStationSign(String stationName, List<Badge> badges, String exitName,
                                                     boolean zhanSuffix, boolean elevator, float aspect) {
        final List<Badge> badgeSnapshot = new ArrayList<>(badges);
        final String key = "bigsign|" + stationName + "|" + badgeKey(badgeSnapshot) + "|"
                + exitName + "|" + zhanSuffix + "|" + elevator + "|" + formatAspect(aspect);
        // 素材和文案都在渲染线程准备好，烘焙线程只读
        TrdmSignAssets.primeAll();
        final TrdmSignAssets.Sprite logo = TrdmSignAssets.sprite(TrdmSignAssets.STATION_LOGO);
        // 无障碍电梯牌的图标：用户在 resources/assets/trdm/textures/block/sign/ 里放了图就有，
        // 没放就自然为 null（版面里这一段直接不占位）
        final TrdmSignAssets.Sprite elevatorIcon = elevator
                ? TrdmSignAssets.sprite(TrdmSignAssets.ACCESSIBLE_ELEVATOR) : null;
        final String elevatorCjk = Component.translatable("gui.trdm.big_sign.elevator_cjk").getString();
        final String elevatorEn = Component.translatable("gui.trdm.big_sign.elevator_en").getString();
        final String[] nameParts = splitName(stationName);
        final String cjk = nameParts[0];
        // ZHAN 牌子即使名字里已经带「站」也要再写一遍（西站 → 西站站）；STATION 牌子只在缺「站」时补
        final String chineseBase = cjk.isEmpty()
                ? cjk
                : zhanSuffix || !cjk.endsWith("站") ? cjk + "站" : cjk;
        // 无障碍电梯牌：站名（含「站」）后面再接「无障碍电梯」
        final String chineseName = elevator ? chineseBase + elevatorCjk : chineseBase;
        final String suffix = zhanSuffix
                ? Component.translatable("gui.trdm.big_sign.suffix_zhan").getString()
                : Component.translatable("gui.trdm.big_sign.suffix_station").getString();
        final String base = nameParts[1].isEmpty() ? cjk : nameParts[1];
        final String upperBase = base.toUpperCase(Locale.ROOT);
        // 和中文同一套规则：ZHAN 一律再拼一次（XI ZHAN → XI ZHAN ZHAN），STATION 只在没有时才拼
        final String latinBase = zhanSuffix || !upperBase.endsWith(suffix)
                ? upperBase + " " + suffix
                : upperBase;
        final String latin = elevator ? latinBase + " " + elevatorEn : latinBase;
        // 同步烘焙：牌子上的内容（出口、站名）改完必须当帧就变，不能先画上一张
        return resolveNow(key, () -> {
            final TextureSize size = bigSignTextureSize(aspect);
            return toNativeImage(TrdmBigStationSignPanel.render(size.width(), size.height(),
                    chineseName, latin, badgeSnapshot, exitName == null ? "" : exitName, logo, elevatorIcon));
        });
    }

    /**
     * 大站名牌贴图的像素尺寸：高度固定 384（≈512 像素/格），宽度按牌宽换算，
     * 太宽就反过来压高度，保证贴图比例和牌子比例一致。
     */
    public static TextureSize bigSignTextureSize(float aspect) {
        final float safe = Math.min(Math.max(aspect, 1F), 16F);
        float widthPx = 384F * safe;
        float heightPx = 384F;
        if (widthPx > 4096F) {
            widthPx = 4096F;
            heightPx = widthPx / safe;
        }
        return new TextureSize(Math.max(48, Math.round(widthPx)), Math.max(48, Math.round(heightPx)));
    }

    public record TextureSize(int width, int height) {
        public float aspect() {
            return width / (float) height;
        }
    }

    public static TextureSize routeMapTextureSize(float aspect) {
        final float safe = Math.min(Math.max(aspect, 0.5F), 48F);
        float widthPx = ROUTE_MAP_TEXTURE_HEIGHT * safe;
        float heightPx = ROUTE_MAP_TEXTURE_HEIGHT;
        if (widthPx > ROUTE_MAP_MAX_TEXTURE_WIDTH) {
            widthPx = ROUTE_MAP_MAX_TEXTURE_WIDTH;
            heightPx = widthPx / safe;
        }
        return new TextureSize(Math.max(48, Math.round(widthPx)), Math.max(48, Math.round(heightPx)));
    }

    /**
     * 普通吊板（站名 / 站点信息 / 终点站）的贴图尺寸。
     * <p>
     */
    public static TextureSize panelTextureSize(float aspect) {
        final float safe = Math.min(Math.max(aspect, 0.5F), 96F);
        float widthPx = TEXTURE_HEIGHT * safe;
        float heightPx = TEXTURE_HEIGHT;
        if (widthPx > PANEL_MAX_TEXTURE_WIDTH) {
            widthPx = PANEL_MAX_TEXTURE_WIDTH;
            heightPx = widthPx / safe;
        }
        return new TextureSize(Math.max(24, Math.round(widthPx)), Math.max(24, Math.round(heightPx)));
    }

    /**
     * 装饰带贴图（玻璃上那条「人字纹 + 中央图文」）：底是线路色，两端人字纹，正中一张图。
     * @param style      装饰线样式（见 {@link top.xiaotang27.trdm.control.PsdStyles}）：
     */
    public static ResourceLocation getDecalBand(int style, int routeColor, float aspect, boolean arrowLeft,
                                                boolean mirrorCentre) {
        final boolean chevronOnly = style == top.xiaotang27.trdm.control.PsdStyles.BAND_CHEVRON_ONLY;
        final String key = "band|" + style + "|" + Integer.toHexString(routeColor & 0xFFFFFF) + "|"
                + formatAspect(aspect) + "|" + arrowLeft + "|" + mirrorCentre;
        // 中央图必须在渲染线程先读一次，烘焙线程只读快照（只有人字的样式用不到它）
        if (!chevronOnly) {
            TrdmDecalBand.primeCentre();
        }
        final TrdmSignAssets.Sprite centre = chevronOnly ? null : TrdmDecalBand.centreSprite(mirrorCentre);
        // 同步烘焙：线路色一换，整条带子当帧就该跟着换色
        return resolveNow(key, () -> {
            final TextureSize size = bandTextureSize(aspect);
            return toNativeImage(TrdmDecalBand.render(size.width(), size.height(), routeColor, arrowLeft, centre,
                    chevronOnly));
        });
    }

    /**
     * 装饰带贴图像素尺寸：高度固定，宽度按宽高比换算，太宽就反过来压高度（不会横向拉伸）。
     */
    public static TextureSize bandTextureSize(float aspect) {
        final float safe = Math.min(Math.max(aspect, 0.5F), 96F);
        float widthPx = BAND_TEXTURE_HEIGHT * safe;
        float heightPx = BAND_TEXTURE_HEIGHT;
        if (widthPx > BAND_MAX_TEXTURE_WIDTH) {
            widthPx = BAND_MAX_TEXTURE_WIDTH;
            heightPx = widthPx / safe;
        }
        return new TextureSize(Math.max(16, Math.round(widthPx)), Math.max(16, Math.round(heightPx)));
    }

    private static NativeImage generateRouteMapPanel(long platformId, float aspect, boolean arrowLeft,
                                                     float runLeftFraction, float runRightFraction) {
        final TrdmRouteMap.Model model = buildRouteMapModel(platformId, arrowLeft);
        if (model == null) {
            return null;
        }
        final TextureSize size = routeMapTextureSize(aspect);
        return toNativeImage(TrdmRouteMap.render(size.width(), size.height(), model, runLeftFraction, runRightFraction));
    }

    /**
     * 把 MTR 的线路数据翻成线路图要用的结构（站名、本站序号、换乘线路、线路色/名）。
     */
    private static TrdmRouteMap.Model buildRouteMapModel(long platformId, boolean arrowLeft) {
        final List<ClientCache.PlatformRouteDetails> routes = ClientData.DATA_CACHE.requestPlatformIdToRoutes(platformId);
        if (routes.isEmpty()) {
            return null;
        }
        final List<ClientCache.PlatformRouteDetails> sorted = new ArrayList<>(routes);
        sorted.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.routeName, b.routeName));
        final ClientCache.PlatformRouteDetails route = sorted.get(0);
        final List<ClientCache.PlatformRouteDetails.StationDetails> stations = route.stationDetails;
        if (stations.isEmpty()) {
            return null;
        }

        // 素材在渲染线程读进缓存（后台烘焙线程只读缓存）
        TrdmSignAssets.primeAll();

        final String[] routeName = splitRouteNameRaw(route.routeName);
        final List<TrdmRouteMap.Station> entries = new ArrayList<>();
        for (final ClientCache.PlatformRouteDetails.StationDetails details : stations) {
            final String[] parts = splitName(details.stationName);
            final List<TrdmRouteMap.Line> interchange = new ArrayList<>();
            for (final ClientCache.ColorNameTuple tuple : details.interchangeRoutes) {
                final String[] lineName = splitRouteNameRaw(tuple.name);
                // 数据里偶尔会把本站台的线路也算进换乘列表，剔掉自己
                if (lineName[0].equals(routeName[0]) && lineName[1].equals(routeName[1])) {
                    continue;
                }
                interchange.add(new TrdmRouteMap.Line(tuple.color, lineName[0], lineName[1]));
            }
            interchange.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.chineseName(), b.chineseName()));
            entries.add(new TrdmRouteMap.Station(parts[0].isEmpty() ? parts[1] : parts[0],
                    parts[0].isEmpty() ? "" : parts[1], interchange));
        }

        return new TrdmRouteMap.Model(entries, route.currentStationIndex, route.routeColor,
                routeName[0], routeName[1], arrowLeft);
    }

    /**
     * 线路名拆成「中文 / 英文」，英文保持原样大小写（MTR 里填的是「Line 2」就画「Line 2」）。
     */
    private static String[] splitRouteNameRaw(String routeName) {
        final String name = routeName == null ? "" : routeName.trim();
        final String[] parts = name.split("\\|");
        if (parts.length == 1) {
            return new String[]{name, ""};
        }
        final StringBuilder english = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            final String part = parts[i].trim();
            if (!part.isEmpty()) {
                if (english.length() > 0) {
                    english.append(' ');
                }
                english.append(part);
            }
        }
        return new String[]{parts[0].trim(), english.toString()};
    }

    /**
     * 吊板统一给一块白底（整张贴图铺满），叠在吊板模型上就成了一块白色吊板。
     */
    private static void fillBackground(Graphics2D graphics, int width, int height) {
        graphics.setColor(new Color(0xFFFFFFFF, true));
        graphics.fillRect(0, 0, width, height);
    }

    private static NativeImage toNativeImage(BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        final NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                nativeImage.setPixelRGBA(x, y, toNativePixel(pixels[y * width + x]));
            }
        }
        return nativeImage;
    }

    private static String formatAspect(float aspect) {
        return String.format(Locale.ROOT, "%.4f", aspect);
    }

    private static ResourceLocation resolve(String key, java.util.function.Supplier<NativeImage> supplier) {
        flush();

        final ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        if (PENDING.add(key)) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    return supplier.get();
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }).thenAccept(image -> TASKS.add(() -> register(key, image)));
        }

        return null;
    }

    /**
     * 当场烘焙并在这一帧注册（阻塞渲染线程）。
     * <p>
     */
    private static ResourceLocation resolveNow(String key, java.util.function.Supplier<NativeImage> supplier) {
        flush();
        if (FAILED.size() > 64) {
            FAILED.clear();
        }

        final ResourceLocation cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        if (FAILED.contains(key)) {
            return null;
        }

        final NativeImage image;
        try {
            image = supplier.get();
        } catch (Exception e) {
            e.printStackTrace();
            FAILED.add(key);
            return null;
        }
        if (image == null) {
            FAILED.add(key);
            return null;
        }
        register(key, image);
        return CACHE.get(key);
    }

    private static String badgeKey(List<Badge> badges) {
        final StringBuilder builder = new StringBuilder();
        for (final Badge badge : badges) {
            builder.append(badge.color()).append(':').append(badge.chineseName()).append(':').append(badge.englishName()).append(';');
        }
        return builder.toString();
    }

    /**
     * 在渲染线程把后台生成的 NativeImage 注册成动态贴图。
     */
    private static void flush() {
        Runnable task;
        while ((task = TASKS.poll()) != null) {
            task.run();
        }
    }

    private static void register(String key, NativeImage image) {
        PENDING.remove(key);
        if (image == null) {
            return;
        }

        final DynamicTexture texture = new DynamicTexture(image);
        // 线性过滤：吊板贴的是矢量文字，双线性比 NEAREST 少很多锯齿
        texture.setFilter(true, false);
        final ResourceLocation location = new ResourceLocation(Trdm.MOD_ID, "dynamic/header_" + (counter++));
        Minecraft.getInstance().getTextureManager().register(location, texture);
        CACHE.put(key, location);

        while (CACHE.size() > MAX_CACHE_SIZE) {
            final Iterator<Map.Entry<String, ResourceLocation>> iterator = CACHE.entrySet().iterator();
            final ResourceLocation eldest = iterator.next().getValue();
            iterator.remove();
            // 不立刻 release：那一帧可能还有渲染器在用这张贴图（用着已被释放的贴图会闪白/闪黑）。
            // 先挂进「待释放」队列，等又被淘汰掉一批之后再真正释放。
            PENDING_RELEASE.addLast(eldest);
            while (PENDING_RELEASE.size() > RELEASE_GRACE) {
                Minecraft.getInstance().getTextureManager().release(PENDING_RELEASE.pollFirst());
            }
        }
    }

    /**
     * 离线预览用：直接画一张门楣「站名吊板」（不注册贴图、不碰渲染线程）。
     */
    public static BufferedImage previewStationNamePanel(String stationName, List<Badge> badges, float aspect) {
        final TextureSize size = panelTextureSize(aspect);
        final int width = size.width();
        final int height = size.height();
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            fillBackground(graphics, width, height);
            layout(graphics, width, height, stationName, badges);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static NativeImage generate(String stationName, List<Badge> badges, float aspect) {        // 等比尺寸：只截宽度会把贴图横向拉伸
        final TextureSize size = panelTextureSize(aspect);
        final int width = size.width();
        final int height = size.height();
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            fillBackground(graphics, width, height);
            layout(graphics, width, height, stationName, badges);
        } finally {
            graphics.dispose();
        }

        final int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        final NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                nativeImage.setPixelRGBA(x, y, toNativePixel(pixels[y * width + x]));
            }
        }
        return nativeImage;
    }

    /**
     * 终点站吊板：整块吊板居中写「终点站 / TERMINAL STATION」，字体沿用滑动门站名吊板那一套
     * （中文黑体、英文 Arial 全大写，英文按字号缩放去贴合中文宽度）。
     */
    private static NativeImage generateTerminal(float aspect) {
        final TextureSize size = panelTextureSize(aspect);
        final int width = size.width();
        final int height = size.height();
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            fillBackground(graphics, width, height);
            drawStationName(graphics, width / 2, height / 2, "终点站", "Terminal Station",
                    height * 0.44F, height * 0.22F, Math.max(1, Math.round(height * 0.085F)));
        } finally {
            graphics.dispose();
        }

        final int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        final NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                nativeImage.setPixelRGBA(x, y, toNativePixel(pixels[y * width + x]));
            }
        }
        return nativeImage;
    }

    /**
     * 站点信息吊板：{@code [←] 开往 X / TO X  |  下一站 / Next Station  |  Y / Y EN}，
     * 箭头朝右时整块版面水平镜像。
     */
    private static NativeImage generateStationInfo(String destination, String nextStation, boolean arrowLeft, int blocks, float aspect) {
        return toNativeImage(stationInfoImage(destination, nextStation, arrowLeft, blocks, aspect));
    }

    /**
     * 站点信息吊板的图像本体（离线预览也走这里，保证预览和游戏里一模一样）。
     * <p>
     */
    public static BufferedImage stationInfoImage(String destination, String nextStation, boolean arrowLeft, int blocks, float aspect) {
        final TextureSize size = panelTextureSize(aspect);
        final int width = size.width();
        final int height = size.height();
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            fillBackground(graphics, width, height);
            layoutStationInfo(graphics, width, height, destination, nextStation, arrowLeft, blocks);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /**
     * 站点信息吊板排版（照参考图）：
     * <pre>
     */
    private static void layoutStationInfo(Graphics2D graphics, int width, int height, String destination, String nextStation, boolean arrowLeft, int blocks) {
        final String[] destinationParts = splitName(destination);
        final String[] nextParts = splitName(nextStation);

        final String destinationChinese = destinationParts[0].isEmpty() ? destinationParts[1] : "开往" + destinationParts[0];
        final String destinationEnglish = destinationParts[0].isEmpty() || destinationParts[1].isEmpty() ? "" : "TO " + destinationParts[1];
        final String nextLabelChinese = "下一站";
        final String nextLabelEnglish = "Next Station";
        final String nextChinese = nextParts[0].isEmpty() ? nextParts[1] : nextParts[0];
        final String nextEnglish = nextParts[0].isEmpty() ? "" : nextParts[1];

        float destinationChineseSize = height * 0.37F;
        float destinationEnglishSize = height * 0.16F;
        float nextLabelChineseSize = height * 0.12F;
        float nextLabelEnglishSize = height * 0.095F;
        float nextChineseSize = height * 0.12F;
        float nextEnglishSize = height * 0.095F;
        float arrowWidth = height * 0.53F;
        float arrowHeight = height * 0.32F;
        final float margin = width * 0.05F;
        final float arrowGap = height * 0.12F;
        final float blockGap = height * 0.18F;
        final float labelGap = height * 0.10F;
        final float destinationLineGap = height * 0.10F;
        final float nextLineGap = height * 0.015F;
        final boolean wide = blocks >= 3;

        // 箭头素材要在渲染线程读一次（资源管理器不是线程安全的），后台烘焙线程只拿缓存好的蒙版
        TrdmArrowAsset.getArrow();

        // 整组放不下就等比缩小（尺寸一缩，整组也跟着收窄）
        for (int iteration = 0; iteration < 4; iteration++) {
            final float destinationCell = Math.max(
                    textWidth(graphics, destinationChinese, font(destinationChineseSize)),
                    destinationEnglish.isEmpty() ? 0 : textWidth(graphics, destinationEnglish, latinFont(destinationEnglishSize)));
            final float nextCell = nextCellWidth(graphics, nextLabelChinese, nextLabelEnglish, nextChinese, nextEnglish,
                    nextLabelChineseSize, nextLabelEnglishSize, nextChineseSize, nextEnglishSize, labelGap);
            final float needed = arrowWidth + arrowGap + destinationCell + blockGap + nextCell;
            final float available = wide ? width * 2F / 3F : width - margin * 2F;
            if (needed <= available || needed <= 0) {
                break;
            }
            final float shrink = Math.max(0.3F, available / needed * 0.97F);
            destinationChineseSize *= shrink;
            destinationEnglishSize *= shrink;
            nextLabelChineseSize *= shrink;
            nextLabelEnglishSize *= shrink;
            nextChineseSize *= shrink;
            nextEnglishSize *= shrink;
            arrowWidth *= shrink;
            arrowHeight *= shrink;
        }

        final Font destinationChineseFont = font(destinationChineseSize);
        final Font destinationEnglishFont = latinFont(destinationEnglishSize);
        final Font nextLabelChineseFont = font(nextLabelChineseSize);
        final Font nextLabelEnglishFont = latinFont(nextLabelEnglishSize);
        final Font nextChineseFont = font(nextChineseSize);
        final Font nextEnglishFont = latinFont(nextEnglishSize);

        final float destinationBigWidth = textWidth(graphics, destinationChinese, destinationChineseFont);
        final float destinationEnglishWidth = textWidth(graphics, destinationEnglish, destinationEnglishFont);
        final float nextLabelChineseWidth = textWidth(graphics, nextLabelChinese, nextLabelChineseFont);
        final float nextLabelEnglishWidth = textWidth(graphics, nextLabelEnglish, nextLabelEnglishFont);
        final float nextChineseWidth = textWidth(graphics, nextChinese, nextChineseFont);
        final float nextEnglishWidth = textWidth(graphics, nextEnglish, nextEnglishFont);

        // 下一站这一列分成两个子列：「下一站 / Next Station」和「站名 / 英文站名」，按列上下对应
        final float nextLabelColumn = Math.max(nextLabelChineseWidth, nextLabelEnglishWidth);
        final float nextNameColumn = Math.max(nextChineseWidth, nextEnglishWidth);
        final float destinationCell = Math.max(destinationBigWidth, destinationEnglishWidth);
        final float nextCell = nextLabelColumn + labelGap + nextNameColumn;
        final float groupWidth = arrowWidth + arrowGap + destinationCell + blockGap + nextCell;

        final FontMetrics destinationChineseMetrics = graphics.getFontMetrics(destinationChineseFont);
        final FontMetrics destinationEnglishMetrics = graphics.getFontMetrics(destinationEnglishFont);
        final FontMetrics nextChineseMetrics = graphics.getFontMetrics(nextChineseFont);

        final int totalHeight = destinationChineseMetrics.getAscent() + Math.round(destinationLineGap) + destinationEnglishMetrics.getAscent();
        final int top = (height - totalHeight) / 2;
        final int destinationChineseBaseline = top + destinationChineseMetrics.getAscent();
        final int englishBaseline = destinationChineseBaseline + Math.round(destinationLineGap) + destinationEnglishMetrics.getAscent();
        // 下一站这一列行距更小，但英文基线和左边的英文对齐（底部齐平）
        final int nextChineseBaseline = englishBaseline - Math.round(nextLineGap) - nextChineseMetrics.getAscent();

        final float startX;
        final float endX;
        if (wide) {
            startX = width / 6F;
            endX = width * 5F / 6F;
        } else {
            startX = Math.max(margin, (width - groupWidth) / 2F);
            endX = startX + groupWidth;
        }

        final float arrowX;
        final float destinationX;
        final float nextX;
        if (arrowLeft) {
            arrowX = startX;
            destinationX = startX + arrowWidth + arrowGap;
            nextX = endX - nextCell;
        } else {
            arrowX = endX - arrowWidth;
            destinationX = endX - arrowWidth - arrowGap - destinationCell;
            nextX = startX;
        }

        graphics.setColor(new Color(TEXT_COLOR, true));

        drawMaybeBold(graphics, destinationChinese, align(destinationX, destinationCell, destinationBigWidth, arrowLeft), destinationChineseBaseline, destinationChineseFont, true);
        if (!destinationEnglish.isEmpty()) {
            drawMaybeBold(graphics, destinationEnglish, align(destinationX, destinationCell, destinationEnglishWidth, arrowLeft), englishBaseline, destinationEnglishFont, true);
        }

        graphics.setFont(nextLabelChineseFont);
        graphics.drawString(nextLabelChinese, align(nextX, nextLabelColumn, nextLabelChineseWidth, arrowLeft), nextChineseBaseline);
        graphics.setFont(nextChineseFont);
        graphics.drawString(nextChinese, align(nextX + nextLabelColumn + labelGap, nextNameColumn, nextChineseWidth, arrowLeft), nextChineseBaseline);

        graphics.setFont(nextLabelEnglishFont);
        graphics.drawString(nextLabelEnglish, align(nextX, nextLabelColumn, nextLabelEnglishWidth, arrowLeft), englishBaseline);
        if (!nextEnglish.isEmpty()) {
            graphics.setFont(nextEnglishFont);
            graphics.drawString(nextEnglish, align(nextX + nextLabelColumn + labelGap, nextNameColumn, nextEnglishWidth, arrowLeft), englishBaseline);
        }

        drawArrow(graphics, arrowX, (height - arrowHeight) / 2F, arrowWidth, arrowHeight, arrowLeft);
    }

    private static float nextCellWidth(Graphics2D graphics, String labelChinese, String labelEnglish, String nextChinese, String nextEnglish,
                                       float labelChineseSize, float labelEnglishSize, float chineseSize, float englishSize, float labelGap) {
        final float labelColumn = Math.max(
                textWidth(graphics, labelChinese, font(labelChineseSize)),
                textWidth(graphics, labelEnglish, latinFont(labelEnglishSize)));
        final float nameColumn = Math.max(
                textWidth(graphics, nextChinese, font(chineseSize)),
                nextEnglish.isEmpty() ? 0 : textWidth(graphics, nextEnglish, latinFont(englishSize)));
        return labelColumn + labelGap + nameColumn;
    }

    /**
     * 单元格内对齐：朝左时左对齐，镜像时右对齐。
     */
    private static float align(float cellX, float cellWidth, float itemWidth, boolean arrowLeft) {
        return arrowLeft ? cellX : cellX + cellWidth - itemWidth;
    }

    /**
     * AWT 对没有粗体字重的字体（比如 SimHei）不会合成加粗，这里用 1~2 像素的横向重描模拟加粗。
     */
    private static void drawMaybeBold(Graphics2D graphics, String text, float x, float baseline, Font font, boolean bold) {
        graphics.setFont(font);
        graphics.drawString(text, x, baseline);
        if (bold) {
            graphics.drawString(text, x + Math.max(0.8F, font.getSize2D() * 0.025F), baseline);
        }
    }

    /**
     * 实心箭头：优先贴 {@code trdm:textures/block/sign/arrow.png} 素材（素材朝右，朝左时左右翻转），
     * 素材缺失时退回按同一比例矢量绘制的多边形。
     */
    private static void drawArrow(Graphics2D graphics, float x, float y, float width, float height, boolean left) {
        final BufferedImage asset = TrdmArrowAsset.getArrow();
        final int x1 = Math.round(x);
        final int y1 = Math.round(y);
        final int x2 = Math.round(x + width);
        final int y2 = Math.round(y + height);

        if (asset != null) {
            if (left) {
                // 素材指向右，朝左时把目标矩形左右对调即为水平翻转
                graphics.drawImage(asset, x2, y1, x1, y2, 0, 0, asset.getWidth(), asset.getHeight(), null);
            } else {
                graphics.drawImage(asset, x1, y1, x2, y2, 0, 0, asset.getWidth(), asset.getHeight(), null);
            }
            return;
        }

        final int tipX = x1;
        final int endX = x2;
        final int centerY = Math.round(y + height / 2F);
        final int halfHeight = Math.max(1, Math.round(height / 2F));
        final int headWidth = Math.max(2, Math.round(width * 0.53F));
        final int halfShaft = Math.max(1, Math.round(height * 0.20F));

        if (left) {
            graphics.fillPolygon(
                    new int[]{tipX, tipX + headWidth, tipX + headWidth, endX, endX, tipX + headWidth, tipX + headWidth},
                    new int[]{centerY, centerY - halfHeight, centerY - halfShaft, centerY - halfShaft, centerY + halfShaft, centerY + halfShaft, centerY + halfHeight},
                    7);
        } else {
            graphics.fillPolygon(
                    new int[]{endX, endX - headWidth, endX - headWidth, tipX, tipX, endX - headWidth, endX - headWidth},
                    new int[]{centerY, centerY - halfHeight, centerY - halfShaft, centerY - halfShaft, centerY + halfShaft, centerY + halfShaft, centerY + halfHeight},
                    7);
        }
    }

    /**
     * BufferedImage 是 ARGB，NativeImage.setPixelRGBA 吃的是 R/B 互换后的值（见 MTR RouteMapGenerator.invertColor）。
     */
    private static int toNativePixel(int argb) {
        return (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
    }

    private static void layout(Graphics2D graphics, int width, int height, String stationName, List<Badge> badges) {
        final String[] nameParts = splitName(stationName);
        final String chinese = nameParts[0];
        final String english = nameParts[1];

        final int count = badges.size();
        // 换乘站的标志一定要左右两侧都有：单条线路时同一个标志左右各画一次（照参考图），
        // 两条以上时按半数分到左右两边
        final int leftCount;
        final int rightCount;
        if (count == 1) {
            leftCount = 1;
            rightCount = 1;
        } else {
            leftCount = (count + 1) / 2;
            rightCount = count - leftCount;
        }

        float badgeSize = height * 0.62F;
        float badgeGap = height * 0.08F;
        float margin = height * 0.05F;
        float labelGap = height * 0.10F;

        // 两侧标志最多各占可用宽度的 30%，超了就整体缩小标志
        final float sideBudget = (width - margin * 2) * 0.30F;
        final int rightFrom = count == 1 ? 0 : leftCount;
        float widestGroup = Math.max(groupWidth(graphics, badges, 0, leftCount, badgeSize, badgeGap),
                groupWidth(graphics, badges, rightFrom, rightCount, badgeSize, badgeGap));
        if (widestGroup > sideBudget && widestGroup > 0) {
            final float shrink = sideBudget / widestGroup;
            badgeSize *= shrink;
            badgeGap *= shrink;
        }

        final float usedByBadges = Math.max(groupWidth(graphics, badges, 0, leftCount, badgeSize, badgeGap),
                groupWidth(graphics, badges, rightFrom, rightCount, badgeSize, badgeGap));
        final float available = width - margin * 2 - 2 * usedByBadges - 2 * labelGap;

        // 中文大、英文小一号：英文按字号缩放去凑中文那一行的宽度（吊板原来的版式）
        float chineseSize = height * 0.44F;
        float englishSize = height * 0.22F;
        final float natural = Math.max(
                textWidth(graphics, chinese, font(chineseSize)),
                english.isEmpty() ? 0 : textWidth(graphics, english, latinFont(englishSize)));
        if (natural > available && natural > 0) {
            final float shrink = Math.max(0.25F, available / natural);
            chineseSize *= shrink;
            englishSize *= shrink;
        }

        final int centerX = width / 2;
        final int centerY = height / 2;

        if (leftCount > 0) {
            float x = margin;
            for (int i = 0; i < leftCount; i++) {
                final Badge badge = badges.get(i % count);
                final float badgeWidth = badgeWidth(graphics, badgeSize, badge);
                drawBadge(graphics, Math.round(x), Math.round((height - badgeSize) / 2F),
                        Math.round(badgeWidth), Math.round(badgeSize), badge, 0F);
                x += badgeWidth + badgeGap;
            }
        }
        if (rightCount > 0) {
            float x = width - margin - groupWidth(graphics, badges, rightFrom, rightCount, badgeSize, badgeGap);
            for (int i = 0; i < rightCount; i++) {
                final Badge badge = badges.get(count == 1 ? 0 : leftCount + i);
                final float badgeWidth = badgeWidth(graphics, badgeSize, badge);
                drawBadge(graphics, Math.round(x), Math.round((height - badgeSize) / 2F),
                        Math.round(badgeWidth), Math.round(badgeSize), badge, 0F);
                x += badgeWidth + badgeGap;
            }
        }

        drawStationName(graphics, centerX, centerY, chinese, english, chineseSize, englishSize, Math.max(1, Math.round(height * 0.085F)));
    }

    /** 一组线路标识占多宽（纯文字线路按文字宽度算，逐块累加）。 */
    private static float groupWidth(Graphics2D graphics, java.util.List<Badge> badges, int from, int blocks,
                                    float height, float gap) {
        if (blocks <= 0 || badges.isEmpty()) {
            return 0F;
        }
        float total = 0F;
        for (int i = 0; i < blocks; i++) {
            total += badgeWidth(graphics, height, badges.get((from + i) % badges.size()));
        }
        return total + (blocks - 1) * gap;
    }

    /**
     * 中文（黑体）在上、英文（Arial，已全大写）在下，两行都水平居中，
     * 英文<b>按字号缩放</b>去凑中文那一行的宽度（不改字距），所以两行左右两端自然对齐。
     */
    static void drawStationName(Graphics2D graphics, int centerX, int centerY, String chinese, String english, float chineseSize, float englishSize, int lineGap) {
        drawStationName(graphics, centerX, centerY, chinese, english, chineseSize, englishSize, lineGap, 0.25F, 0.85F);
    }

    /**
     * 同上，但英文的缩放范围可以指定（站名牌要更小一点的英文，吊板要铺满中文那行宽度）。
     */
    static void drawStationName(Graphics2D graphics, int centerX, int centerY, String chinese, String english,
                                float chineseSize, float englishSize, int lineGap,
                                float englishMinScale, float englishMaxScale) {
        if (chinese.isEmpty() && english.isEmpty()) {
            return;
        }

        graphics.setColor(new Color(TEXT_COLOR, true));

        if (english.isEmpty()) {
            drawCentered(graphics, chinese, font(chineseSize), centerX, centerY);
            return;
        }
        if (chinese.isEmpty()) {
            drawCentered(graphics, english, latinFont(englishSize), centerX, centerY);
            return;
        }

        final Font chineseFont = font(chineseSize);
        final FontMetrics chineseMetrics = graphics.getFontMetrics(chineseFont);
        final float chineseWidth = textWidth(graphics, chinese, chineseFont);

        englishSize = fitFontSize(graphics, english, englishSize, chineseWidth, englishMinScale, englishMaxScale, chineseSize);
        final Font englishFont = latinFont(englishSize);
        final FontMetrics englishMetrics = graphics.getFontMetrics(englishFont);

        final int totalHeight = chineseMetrics.getAscent() + lineGap + englishMetrics.getAscent();
        final int top = centerY - totalHeight / 2;
        final int chineseBaseline = top + chineseMetrics.getAscent();

        drawCenteredAtBaseline(graphics, chinese, chineseFont, centerX, chineseBaseline);
        drawCenteredAtBaseline(graphics, english, englishFont, centerX, chineseBaseline + lineGap + englishMetrics.getAscent());
    }

    /**
     * 求「让这段文字刚好等于目标宽度」的字号，再夹到 [minScale, maxScale] × referenceSize 之间，
     * 免得超长英文被缩成蚂蚁、超短英文被撑成巨字。
     */
    private static float fitFontSize(Graphics2D graphics, String text, float nominalSize, float targetWidth, float minScale, float maxScale, float referenceSize) {
        final float measured = textWidth(graphics, text, latinFont(nominalSize));
        if (measured <= 0 || targetWidth <= 0) {
            return nominalSize;
        }
        final float fitted = nominalSize * targetWidth / measured;
        return Math.max(referenceSize * minScale, Math.min(referenceSize * maxScale, fitted));
    }

    private static void drawCentered(Graphics2D graphics, String text, Font font, int centerX, int centerY) {
        final FontMetrics metrics = graphics.getFontMetrics(font);
        graphics.setFont(font);
        graphics.drawString(text, centerX - textWidth(graphics, text, font) / 2F, centerY + (metrics.getAscent() - metrics.getDescent()) / 2F);
    }

    private static void drawCenteredAtBaseline(Graphics2D graphics, String text, Font font, int centerX, float baseline) {
        graphics.setFont(font);
        graphics.drawString(text, centerX - textWidth(graphics, text, font) / 2F, baseline);
    }

    static void drawBadge(Graphics2D graphics, int x, int y, int size, Badge badge) {
        drawBadge(graphics, x, y, size, size, badge, 0F);
    }

    /**
     * 线路编号标识。
     *
     * @param outlineWidth 白色描边粗细（像素，0 = 不描边）；大站名牌要粗描边，吊板上不描
     */
    static void drawBadge(Graphics2D graphics, int x, int y, int size, Badge badge, float outlineWidth) {
        drawBadge(graphics, x, y, size, size, badge, outlineWidth);
    }

    /** 纯文字线路名：中文相对牌高的字号。 */
    private static final float TEXT_NAME_SIZE = 0.38F;
    /** 纯文字线路名：英文相对牌高的字号。 */
    private static final float TEXT_SUB_SIZE = 0.24F;
    /** 纯文字线路名：文字到边框的留白（相对牌高，左右各一份）。 */
    private static final float TEXT_PADDING = 0.17F;

    /**
     * 这个线路标识要画多宽（给定高度）。
     * <p>
     */
    static float badgeWidth(Graphics2D graphics, float height, Badge badge) {
        if (!isTextOnly(badge)) {
            return height;
        }
        final String chinese = badge.chineseName() == null ? "" : badge.chineseName();
        final String english = badge.englishName() == null ? "" : badge.englishName();
        float textWidth = 0F;
        if (graphics != null) {
            textWidth = textWidth(graphics, chinese, font(height * TEXT_NAME_SIZE));
            if (!english.isEmpty()) {
                textWidth = Math.max(textWidth, textWidth(graphics, english, latinFont(height * TEXT_SUB_SIZE)));
            }
        } else {
            // 没有画布时按「一个中文字约 0.6 倍牌高」估，只用来占位
            textWidth = height * 0.6F * Math.max(1, chinese.length());
        }
        return Math.max(height, textWidth + height * TEXT_PADDING * 2F);
    }

    /** 线路名里有没有数字（没有就是纯文字线路，用参考图「津静线」那种画法）。 */
    static boolean isTextOnly(Badge badge) {
        return badge != null && splitRouteNumber(badge.chineseName())[0].isEmpty();
    }

    /**
     * 线路编号标识（可指定宽度）。
     * <p>两种画法：
     */
    static void drawBadge(Graphics2D graphics, int x, int y, int width, int height, Badge badge, float outlineWidth) {
        if (isTextOnly(badge)) {
            drawTextBadge(graphics, x, y, width, height, badge);
            return;
        }
        final int size = Math.min(width, height);
        final Color fill = new Color(badge.color() | 0xFF000000, true);
        final int arc = Math.max(2, Math.round(size * 0.18F));
        graphics.setColor(fill);
        graphics.fillRoundRect(x, y, size, size, arc, arc);

        // 粗白描边（画在色块内侧，所以贴着圆角走）
        if (outlineWidth > 0F) {
            final float inset = outlineWidth / 2F;
            graphics.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(new Color(0xFFFFFFFF, true));
            graphics.drawRoundRect(Math.round(x + inset), Math.round(y + inset),
                    Math.round(size - outlineWidth), Math.round(size - outlineWidth), arc, arc);
        }

        final int textColor = isLightColor(badge.color()) ? 0xFF000000 : 0xFFFFFFFF;
        graphics.setColor(new Color(textColor, true));

        final String[] numberParts = splitRouteNumber(badge.chineseName());
        final String number = numberParts[0];
        final String suffix = numberParts[1];
        final String english = badge.englishName();

        // 文字比例照参考图：大数字占左半，右侧「号线」在上、「Line N」在下，两行都不小
        final float padding = size * 0.06F;
        final float gap = size * 0.04F;

        float numberSize = size * 0.72F;
        float suffixSize = size * 0.25F;
        float englishSize = size * 0.21F;
        // 数字一律用 Arial（线路号是大字，黑体的数字和参考图不像）
        Font numberFont = latinFont(numberSize);
        Font suffixFont = font(suffixSize);
        Font englishFont = latinFont(englishSize);
        float numberWidth = textWidth(graphics, number, numberFont);
        float suffixWidth = suffix.isEmpty() ? 0 : textWidth(graphics, suffix, suffixFont);
        float englishWidth = english.isEmpty() ? 0 : textWidth(graphics, english, englishFont);
        float columnWidth = Math.max(suffixWidth, englishWidth);

        final float available = size - padding * 2 - gap;
        final float natural = numberWidth + columnWidth;
        if (natural > available && natural > 0) {
            final float shrink = available / natural;
            numberSize *= shrink;
            suffixSize *= shrink;
            englishSize *= shrink;
            numberFont = latinFont(numberSize);
            suffixFont = font(suffixSize);
            englishFont = latinFont(englishSize);
            numberWidth = textWidth(graphics, number, numberFont);
            suffixWidth = suffix.isEmpty() ? 0 : textWidth(graphics, suffix, suffixFont);
            englishWidth = english.isEmpty() ? 0 : textWidth(graphics, english, englishFont);
            columnWidth = Math.max(suffixWidth, englishWidth);
        }

        // 「Line N」用字号缩放到与「号线」同宽（不改字距）
        if (!suffix.isEmpty() && !english.isEmpty()) {
            englishSize = fitFontSize(graphics, english, englishSize, suffixWidth, 0.5F, 1.5F, suffixSize);
            englishFont = latinFont(englishSize);
            englishWidth = textWidth(graphics, english, englishFont);
        }
        final float columnBlockWidth = Math.max(suffixWidth, englishWidth);

        final FontMetrics numberMetrics = graphics.getFontMetrics(numberFont);
        graphics.setFont(numberFont);
        graphics.drawString(number, x + padding, y + (size + numberMetrics.getAscent() - numberMetrics.getDescent()) / 2F);

        if (columnBlockWidth <= 0) {
            return;
        }

        final FontMetrics suffixMetrics = graphics.getFontMetrics(suffixFont);
        final FontMetrics englishMetrics = graphics.getFontMetrics(englishFont);
        final int columnGap = Math.max(1, Math.round(size * 0.02F));
        float remaining = 0;
        if (!suffix.isEmpty()) {
            remaining += suffixMetrics.getAscent();
        }
        if (!english.isEmpty()) {
            remaining += englishMetrics.getAscent();
        }
        if (!suffix.isEmpty() && !english.isEmpty()) {
            remaining += columnGap;
        }

        float cursorY = y + (size - remaining) / 2F;
        final float columnX = x + padding + numberWidth + gap;

        if (!suffix.isEmpty()) {
            graphics.setFont(suffixFont);
            graphics.drawString(suffix, columnX, cursorY + suffixMetrics.getAscent());
            cursorY += suffixMetrics.getAscent() + columnGap;
        }
        if (!english.isEmpty()) {
            graphics.setFont(englishFont);
            graphics.drawString(english, columnX, cursorY + englishMetrics.getAscent());
        }
    }

    /**
     * 纯文字线路名的标识，照参考图里「津静线 / JINJING Line」的画法：
     * 浅粉底（线路色兑白）、白色圆角边框，<b>线路名本身用黑色或白色</b>（按底色亮度取对比色，
     */
    private static void drawTextBadge(Graphics2D graphics, int x, int y, int width, int height, Badge badge) {
        final String chinese = badge.chineseName() == null ? "" : badge.chineseName();
        final String english = badge.englishName() == null ? "" : badge.englishName();
        final int arc = Math.max(2, Math.round(height * 0.18F));

        // 底：线路色兑白，看起来是「浅线路色」
        final int fill = mixWithWhite(badge.color(), 0.62F);
        graphics.setColor(new Color(fill, true));
        graphics.fillRoundRect(x, y, width, height, arc, arc);

        // 白色圆角边框（和数字线路的粗白描边同一套观感）
        final float border = Math.max(2F, height * 0.05F);
        graphics.setStroke(new BasicStroke(border, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(0xFFFFFFFF, true));
        graphics.drawRoundRect(Math.round(x + border / 2F), Math.round(y + border / 2F),
                Math.round(width - border), Math.round(height - border), arc, arc);

        final float pad = height * TEXT_PADDING;
        final float available = width - pad * 2F;

        float nameSize = height * TEXT_NAME_SIZE;
        Font nameFont = font(nameSize);
        float nameWidth = textWidth(graphics, chinese, nameFont);
        if (nameWidth > available && nameWidth > 0) {
            nameSize *= available / nameWidth;
            nameFont = font(nameSize);
            nameWidth = textWidth(graphics, chinese, nameFont);
        }

        float subSize = english.isEmpty() ? 0F : height * TEXT_SUB_SIZE;
        Font subFont = latinFont(subSize);
        float subWidth = english.isEmpty() ? 0F : textWidth(graphics, english, subFont);
        if (subWidth > available && subWidth > 0) {
            subSize *= available / subWidth;
            subFont = latinFont(subSize);
            subWidth = textWidth(graphics, english, subFont);
        }

        final FontMetrics nameMetrics = graphics.getFontMetrics(nameFont);
        final FontMetrics subMetrics = english.isEmpty() ? null : graphics.getFontMetrics(subFont);
        final float lineGap = english.isEmpty() ? 0F : height * 0.02F;
        final float blockHeight = nameMetrics.getAscent() + (subMetrics == null ? 0F : lineGap + subMetrics.getAscent());
        final float top = y + (height - blockHeight) / 2F;

        // 线路名用黑或白：底色偏亮写黑、偏暗写白（和带数字的线路同一套对比色规则）
        final int textColor = isLightColor(fill) ? 0xFF000000 : 0xFFFFFFFF;
        graphics.setColor(new Color(textColor, true));
        graphics.setFont(nameFont);
        graphics.drawString(chinese, x + (width - nameWidth) / 2F, top + nameMetrics.getAscent());
        if (subMetrics != null) {
            graphics.setFont(subFont);
            graphics.drawString(english, x + (width - subWidth) / 2F,
                    top + nameMetrics.getAscent() + lineGap + subMetrics.getAscent());
        }
    }

    /** 颜色往白里兑：{@code amount} = 0 就是原色，1 就是纯白（返回不透明 ARGB）。 */
    private static int mixWithWhite(int color, float amount) {
        final int red = Math.round(((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * amount);
        final int green = Math.round(((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * amount);
        final int blue = Math.round((color & 0xFF) + (255 - (color & 0xFF)) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /** 颜色压暗：{@code amount} = 0 就是原色，1 就是全黑（返回不透明 ARGB）。 */
    private static int darken(int color, float amount) {
        final int red = Math.round(((color >> 16) & 0xFF) * (1F - amount));
        final int green = Math.round(((color >> 8) & 0xFF) * (1F - amount));
        final int blue = Math.round((color & 0xFF) * (1F - amount));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /**
     * 站名拆成中文 / 英文两段：MTR 的约定是「中文|English」。
     */
    static String[] splitName(String stationName) {
        final String name = stationName == null ? "" : stationName;
        final String[] parts = name.split("\\|");
        if (parts.length == 1) {
            final String single = parts[0].trim();
            return isCjk(single) ? new String[]{single, ""} : new String[]{"", single.toUpperCase(Locale.ROOT)};
        }
        final StringBuilder english = new StringBuilder();
        for (int i = 1; i < parts.length; i++) {
            final String part = parts[i].trim();
            if (!part.isEmpty()) {
                if (english.length() > 0) {
                    english.append(' ');
                }
                english.append(part);
            }
        }
        return new String[]{parts[0].trim(), english.toString().toUpperCase(Locale.ROOT)};
    }

    /**
     * 线路名拆成「数字 + 后缀」：「1号线」→「1」「号线」。
     */
    private static String[] splitRouteNumber(String routeName) {
        final String name = routeName == null ? "" : routeName.trim();
        final Matcher matcher = ROUTE_NUMBER_PATTERN.matcher(name);
        if (matcher.matches()) {
            return new String[]{matcher.group(1), matcher.group(2).trim()};
        }
        return new String[]{"", name};
    }

    static boolean isCjk(String text) {
        for (int i = 0; i < text.length(); i++) {
            final Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS || block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA || block == Character.UnicodeBlock.HANGUL_SYLLABLES
                    || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLightColor(int color) {
        final int red = (color >> 16) & 0xFF;
        final int green = (color >> 8) & 0xFF;
        final int blue = color & 0xFF;
        return 0.299F * red + 0.587F * green + 0.114F * blue > 140;
    }

    /**
     * 当前中文字体的「字形墨水高度 / 字号」比例（量一次，缓存）。
     * <p>
     */
    public static synchronized float inkRatio() {
        if (inkRatio > 0F) {
            return inkRatio;
        }
        inkRatio = measureInkRatio(font(100F));
        return inkRatio;
    }

    public static Font font(float size) {
        return baseFont().deriveFont(Font.PLAIN, Math.max(1F, size));
    }

    /**
     * <b>PIS 屏专用</b>：jar 里打进的那份思源黑体（读不到才退回系统黑体）。
     * <p>
     */
    public static Font pisFont(float size) {
        final Font bundled = bundledFont();
        final Font chosen = bundled != null && bundled.canDisplayUpTo("号线站") < 0 ? bundled : baseFont();
        return chosen.deriveFont(Font.PLAIN, Math.max(1F, size));
    }

    /** PIS 版面的「墨水高度 / 字号」比例（思源黑体自己的比例，和站名牌用的那套分开量）。 */
    public static synchronized float pisInkRatio() {
        if (pisInkRatio > 0F) {
            return pisInkRatio;
        }
        pisInkRatio = measureInkRatio(pisFont(100F));
        return pisInkRatio;
    }

    private static float measureInkRatio(Font font) {
        try {
            final int probeSize = 100;
            final BufferedImage image = new BufferedImage(probeSize * 3, probeSize * 3, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setFont(font);
            graphics.setColor(Color.BLACK);
            graphics.drawString("0号线", 10, probeSize * 2);
            graphics.dispose();
            int minY = image.getHeight();
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (((image.getRGB(x, y) >>> 24) & 0xFF) > 8) {
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                        break;
                    }
                }
            }
            return maxY >= minY ? (maxY - minY + 1) / (float) probeSize : 0.9F;
        } catch (Exception e) {
            return 0.9F;
        }
    }

    /**
     * 英文用的 Arial。Arial 里没有汉字的字形，所以中英混排必须分开取字体。
     */
    static Font latinFont(float size) {
        return latinBaseFont().deriveFont(Font.PLAIN, Math.max(1F, size));
    }

    private static synchronized Font latinBaseFont() {
        if (latinBaseFont != null) {
            return latinBaseFont;
        }
        if (top.xiaotang27.trdm.TrdmConfig.useTrdmFont()) {
            final Font trdm = trdmLatinFont();
            if (trdm != null) {
                latinBaseFont = trdm;
                return latinBaseFont;
            }
        }
        final Set<String> available = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT)));
        for (final String candidate : LATIN_FONT_CANDIDATES) {
            if (available.contains(candidate)) {
                latinBaseFont = new Font(candidate, Font.PLAIN, 32);
                return latinBaseFont;
            }
        }
        latinBaseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 32);
        return latinBaseFont;
    }

    private static synchronized Font baseFont() {
        if (baseFont != null) {
            return baseFont;
        }
        // 「使用TRDM字体」开：用随 mod 打包的 TRDM 字体；关：系统黑体 + Arial
        if (top.xiaotang27.trdm.TrdmConfig.useTrdmFont()) {
            final Font trdm = trdmCjkFont();
            if (trdm != null) {
                baseFont = trdm;
                return baseFont;
            }
        }
        // 普通黑体（SimHei）优先；思源黑体只给 PIS 用，见 pisFont(float)
        final Set<String> available = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT)));
        for (final String candidate : FONT_CANDIDATES) {
            if (available.contains(candidate)) {
                final Font font = new Font(candidate, Font.PLAIN, 32);
                if (font.canDisplayUpTo("号线站") < 0) {
                    baseFont = font;
                    return baseFont;
                }
            }
        }
        for (final String candidate : available) {
            final Font font = new Font(candidate, Font.PLAIN, 32);
            if (font.canDisplayUpTo("号线站") < 0) {
                baseFont = font;
                return baseFont;
            }
        }
        baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 32);
        return baseFont;
    }

    // ===== 打进 jar 的思源黑体 =====

    /** TRDM 字体：随 mod 打包的 MTR 字体（中文 Noto Serif CJK / 英文 Noto Sans）。 */
    private static final ResourceLocation TRDM_CJK_FONT =
            new ResourceLocation("mtr", "font/noto-serif-cjk-tc-semibold.ttf");
    private static final ResourceLocation TRDM_LATIN_FONT =
            new ResourceLocation("mtr", "font/noto-sans-semibold.ttf");
    private static Font trdmCjk;
    private static Font trdmLatin;
    private static boolean trdmFontChecked;

    /** 中文用的 TRDM 字体；读不到返回 null（调用方退回系统字体）。 */
    public static synchronized Font trdmCjkFont() {
        if (!trdmFontChecked) {
            trdmFontChecked = true;
            trdmLatin = readFont(TRDM_LATIN_FONT);
            trdmCjk = readFont(TRDM_CJK_FONT);
        }
        return trdmCjk;
    }

    /** 英文用的 TRDM 字体；读不到返回 null。 */
    public static synchronized Font trdmLatinFont() {
        if (!trdmFontChecked) {
            trdmCjkFont();
        }
        return trdmLatin;
    }

    private static Font readFont(ResourceLocation location) {
        try {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return null;
            }
            final var resource = minecraft.getResourceManager().getResource(location);
            if (resource.isEmpty()) {
                Trdm.LOGGER.warn("[font] 找不到 {}", location);
                return null;
            }
            try (final java.io.InputStream stream = resource.get().open()) {
                return Font.createFont(Font.TRUETYPE_FONT, stream);
            }
        } catch (Exception e) {
            Trdm.LOGGER.warn("[font] 读不了 {} : {}", location, e.toString());
            return null;
        }
    }
    /** jar 里的字体路径（assets/trdm/font/...，资源路径只能小写）。 */
    public static final ResourceLocation BUNDLED_FONT =
            new ResourceLocation(Trdm.MOD_ID, "font/sourcehansanshwsc-vf.ttf");

    private static Font bundledBaseFont;
    private static boolean bundledFontChecked;

    /**
     * 取打进 jar 的思源黑体；读不到就返回 null（自动退回系统字体）。
     * 第一次调用要在渲染线程（或者用 {@link #primeBundledFont(Path)} 由离线工具预先加载）。
     */
    public static synchronized Font bundledFont() {
        if (bundledFontChecked) {
            return bundledBaseFont;
        }
        bundledFontChecked = true;
        try {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null) {
                return null;
            }
            final var resource = minecraft.getResourceManager().getResource(BUNDLED_FONT);
            if (resource.isEmpty()) {
                return null;
            }
            try (final java.io.InputStream stream = resource.get().open()) {
                bundledBaseFont = Font.createFont(Font.TRUETYPE_FONT, stream);
            }
        } catch (Exception e) {
            e.printStackTrace();
            bundledBaseFont = null;
        }
        return bundledBaseFont;
    }

    /** 离线预览用：直接从磁盘的 ttf 读字体。 */
    public static synchronized void primeBundledFont(Path file) {
        if (bundledBaseFont != null) {
            return;
        }
        try {
            if (file != null && Files.isRegularFile(file)) {
                bundledBaseFont = Font.createFont(Font.TRUETYPE_FONT, file.toFile());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        bundledFontChecked = true;
    }

    /** 换字体后用：丢字体与贴图缓存，下一次绘制重新选字体并重烘。 */
    public static synchronized void resetFonts() {
        baseFont = null;
        latinBaseFont = null;
        inkRatio = -1F;
        pisInkRatio = -1F;
        trdmFontChecked = false;
        trdmCjk = null;
        trdmLatin = null;
        CACHE.clear();
        PENDING.clear();
        FAILED.clear();
    }
    private static float textWidth(Graphics2D graphics, String text, Font font) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        graphics.setFont(font);
        return graphics.getFontMetrics(font).stringWidth(text);
    }

    /**
     * 一块线路标志：线路色 + 中文名 + 英文名。
     */
    public record Badge(int color, String chineseName, String englishName) {
    }
}
