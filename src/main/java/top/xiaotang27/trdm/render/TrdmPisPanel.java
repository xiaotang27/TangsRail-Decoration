package top.xiaotang27.trdm.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * PIS 屏幕的版面（把 JCM 的 {@code pids_tj_custom.js} 一对一搬过来）。
 * <p>
 * 坐标系沿用 JCM 脚本的「画布单位」：整块屏 <b>136 × 72</b>（16:9），
 * 脚本里 {@code Text.create().scale(s)} 的字高 = {@code 16 × s} 画布单位，
 * {@code pos(x, y)} 是文字中心（配合 {@code centerAlign()}）。烘到贴图时整体乘一个倍数即可。
 * <pre>
 * ┌──────┬──────────────────────────────┐
 * │欢迎您 │                              │
 * │本次列车│        广告图 97×56          │
 * │ 15 分钟│        (37,3)                │
 * ├──────┤                              │
 * │下次列车│                              │
 * │ 22 分钟│                              │
 * ├──────┼──────────────────────────────┤
 * │ 20:31│  跑马灯 41..134, y=60         │
 * │ 日期  │                              │
 * ├──────┴──────────────────────────────┤
 * │        车厢示意图（头/中/尾 + 车号）    │
 * └─────────────────────────────────────┘
 * </pre>
 * 这条版面只画「一帧」，滚动 / 倒计时 / 换广告都由调用方把不同的 {@link View} 喂进来。
 */
public final class TrdmPisPanel {

    /** 画布宽（设计稿 1920×1080 按 15 px = 1 单位换算，正好 16:9）。 */
    public static final int CANVAS_WIDTH = 128;
    /** 画布高。 */
    public static final int CANVAS_HEIGHT = 72;

    /** 字号 = 9 × scale（画布单位），一个汉字的宽度约等于字号。 */
    private static final float TEXT_UNIT = 9F;

    // ===== 版面常量 =====
    /** 文字尺寸按字形墨水高度（画布单位）给定，绘制时换算成字号。 */
    /**
     * 左栏所有文字（到站、开往、时钟、日期）共用的竖中线：
     * = 背景板里「天津地铁欢迎您」的**「铁」字中心**（量出来约 17.0 画布单位，
     * 见 {@code gradlew measurePisBackground}）。
     */
    private static final float LEFT_X = 17.0F;
    private static final float ETA_Y = 18F;
    private static final float DEST_Y = 24F;
    private static final float ROW_STEP = 20F;
    /** 「已到站 / 即将进站」：4 个字要放进左栏，比数字小一档。 */
    private static final float INK_ETA = 4.0F;
    /** 「N 分钟」的数字：设计稿 90 px。 */
    private static final float INK_MINUTES = 6.0F;
    /** 数字和「分钟」整体相对 LEFT_X 往左让一点（数字占左、单位在右）。 */
    private static final float MINUTES_X = 15.5F;
    /** 「分钟」小字：设计稿 23 px。 */
    private static final float INK_UNIT_TEXT = 1.53F;
    private static final float UNIT_X = 20.3F;
    private static final float UNIT_DY = 1.8F;
    /** 「开往:XXX」：设计稿 36 px。 */
    private static final float INK_DEST = 2.4F;
    private static final float CLOCK_Y = 54F;
    private static final float DATE_Y = 60.5F;
    /** 时钟：设计稿 66 px，整体再放大一档（左栏宽度放得下「23:58」）。 */
    private static final float INK_CLOCK = 6.0F;
    /**
     * 「天津地铁欢迎您」在背景板里的宽度（画布单位，量自 tjpids.png：54..455 设计像素）。
     * 日期那一行要撑到同样宽 —— 参考图里两行是左右对齐的，靠**放大字号**撑，不动字距。
     */
    private static final float INK_WELCOME_WIDTH = 26.7F;

    private static final float AD_X = 36F;
    private static final float AD_Y = 2F;
    private static final float AD_W = 90F;
    private static final float AD_H = 54F;

    /** 跑马灯文字的竖直中心（比裁剪条中心略高一点，视觉上更居中）。 */
    private static final float TICKER_Y = 60.5F;
    private static final float TICKER_START_X = 37F;
    private static final float TICKER_END_X = 126F;
    /** 跑马灯文字整体滚动，不再逐字跳：速度单位是「画布单位/秒」（调慢后的值）。 */
    public static final float TICKER_SPEED = 16F;
    /** 文字滚完一轮后，下一轮之间留的空档（画布单位）。 */
    private static final float TICKER_LOOP_GAP = 14F;
    /** 裁剪区：正好盖住背景那条浅蓝跑马灯条。 */
    private static final float TICKER_CLIP_TOP = 58F;
    private static final float TICKER_CLIP_BOTTOM = 64F;
    /** 跑马灯文字：**白字**压在浅蓝条上，字形高度基本顶满这条带子（上下留一点空隙）。 */
    private static final int TICKER_COLOR = 0xFFFFFF;
    private static final float INK_TICKER = 4.2F;

    private static final float CAR_W = 14F;
    private static final float CAR_GAP = 1.2F;
    private static final float CAR_H = 6F;
    private static final float CAR_BOTTOM = 2F;
    /** 车号：设计稿 23 px。 */
    private static final float INK_CAR_NUMBER = 1.53F;    /** 中间车的车号要往左让一点（脚本里减 2.9）。 */
    private static final float CAR_MIDDLE_NUMBER_DX = 2.9F;

    private static final int ETA_COLOR = 0xFFFF00;
    private static final int TEXT_COLOR = 0xFFFFFF;

    /**
     * 一趟车。
     *
     * @param etaSeconds  还有多少秒到站（≤0 = 已到站，&lt;60 = 即将进站）
     * @param destination 终点站（MTR 的「中文|English」，这里只取中文）
     * @param carCount    编组节数
     */
    public record Arrival(int etaSeconds, String destination, int carCount) {
    }

    /**
     * 一帧画面。
     *
     * @param tickerOffset 跑马灯已经滚了多少画布单位（可以带小数：{@code TICKER_SPEED × 秒数}），
     *                     整条文字连续平移，不再逐字跳
     */
    public record View(Arrival first, Arrival second, String clock, String date,
                       String ticker, float tickerOffset) {
    }

    private TrdmPisPanel() {
    }

    /**
     * 画一帧。
     *
     * @param width      贴图宽（像素）
     * @param height     贴图高（像素）
     * @param ad         广告图（null = 不画）
     * @param carHead    头车素材
     * @param carMiddle  中间车素材
     * @param carTail    尾车素材
     */
    public static BufferedImage render(int width, int height, View view, BufferedImage ad,
                                       BufferedImage carHead, BufferedImage carMiddle, BufferedImage carTail) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        renderInto(image, view, ad, carHead, carMiddle, carTail);
        return image;
    }

    /**
     * 画进一张**已经存在**的图里（先清空），供渲染器复用底图用 ——
     * 每帧 new 一张 768×432 的 BufferedImage 会把 GC 压垮（每块屏每秒 10 张）。
     */
    public static void renderInto(BufferedImage target, View view, BufferedImage ad,
                                  BufferedImage carHead, BufferedImage carMiddle, BufferedImage carTail) {
        drawInto(target, view, ad, carHead, carMiddle, carTail, true);
    }

    /**
     * 只画「静态部分」：背景 / 到站 / 时钟 / 跑马灯 / 车厢以外的话题 ——
     * 不含广告、<b>也不含跑马灯</b>。渲染器把跑马灯单独按 30Hz 往合成图上画，滚动才丝滑。
     */
    public static void renderBaseInto(BufferedImage target, View view,
                                      BufferedImage carHead, BufferedImage carMiddle, BufferedImage carTail) {
        drawInto(target, view, null, carHead, carMiddle, carTail, false);
    }

    /** 把跑马灯单独画到一张图（合成图上 30Hz 调用，滚动由这里决定的精度决定）。 */
    public static void drawTickerInto(Graphics2D graphics, int width, String ticker, float offset) {
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawTicker(graphics, width / (float) CANVAS_WIDTH, ticker, offset);
    }

    private static void drawInto(BufferedImage target, View view, BufferedImage ad,
                                 BufferedImage carHead, BufferedImage carMiddle, BufferedImage carTail,
                                 boolean withTicker) {
        final int width = target.getWidth();
        final int height = target.getHeight();
        final Graphics2D graphics = target.createGraphics();
        try {
            graphics.setComposite(java.awt.AlphaComposite.Clear);
            graphics.fillRect(0, 0, width, height);
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            final float scale = width / (float) CANVAS_WIDTH;

            // 背景板（JCM 那张 tjpids.png，整幅铺满）
            final BufferedImage background = TrdmPisAssets.image(TrdmPisAssets.BACKGROUND);
            if (background != null) {
                graphics.drawImage(background, 0, 0, width, height, null);
            }

            // 左列：本次 / 下次列车
            drawArrival(graphics, scale, view.first(), ETA_Y, DEST_Y);
            drawArrival(graphics, scale, view.second(), ETA_Y + ROW_STEP, DEST_Y + ROW_STEP);

            // 时钟 / 日期（日期靠放大字号撑到和「天津地铁欢迎您」同宽）
            drawCentred(graphics, scale, view.clock(), LEFT_X, CLOCK_Y, INK_CLOCK, TEXT_COLOR, true);
            drawFitted(graphics, scale, view.date(), LEFT_X, DATE_Y, INK_WELCOME_WIDTH, TEXT_COLOR);

            // 广告
            drawAd(graphics, scale, ad);

            // 跑马灯（渲染器自己按 30Hz 单独画，这样滚动才丝滑；预览工具里照常画）
            if (withTicker) {
                drawTicker(graphics, scale, view.ticker(), view.tickerOffset());
            }

            // 车厢示意图
            drawCars(graphics, scale, view.first(), carHead, carMiddle, carTail);
        } finally {
            graphics.dispose();
        }
    }

    /**
     * 广告区在某个贴图宽度下的像素矩形：{@code {x, y, 宽, 高}}。
     * 渲染器要单独往版面底图上贴动图帧，所以这个矩形必须和 {@link #render} 里画广告的位置完全一致。
     */
    public static int[] adRect(int width) {
        final float scale = width / (float) CANVAS_WIDTH;
        return new int[]{
                Math.round(AD_X * scale),
                Math.round(AD_Y * scale),
                Math.round(AD_W * scale),
                Math.round(AD_H * scale),
        };
    }

    /** 把广告图铺进广告区（贴图尺寸 = 版面尺寸）。 */
    public static void drawAd(Graphics2D graphics, int width, BufferedImage ad) {
        if (ad == null) {
            return;
        }
        final int[] rect = adRect(width);
        graphics.drawImage(ad, rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3],
                0, 0, ad.getWidth(), ad.getHeight(), null);
    }

    private static void drawAd(Graphics2D graphics, float scale, BufferedImage ad) {
        if (ad == null) {
            return;
        }
        graphics.drawImage(ad, Math.round(AD_X * scale), Math.round(AD_Y * scale),
                Math.round((AD_X + AD_W) * scale), Math.round((AD_Y + AD_H) * scale),
                0, 0, ad.getWidth(), ad.getHeight(), null);
    }

    private static void drawArrival(Graphics2D graphics, float scale, Arrival arrival, float etaY, float destY) {
        if (arrival == null) {
            return;
        }
        final int eta = arrival.etaSeconds();
        if (eta <= 0) {
            drawCentred(graphics, scale, "已到站", LEFT_X, etaY, INK_ETA, ETA_COLOR, true);
        } else if (eta < 60) {
            drawCentred(graphics, scale, "即将进站", LEFT_X, etaY, INK_ETA, ETA_COLOR, true);
        } else {
            drawCentred(graphics, scale, String.valueOf(eta / 60), MINUTES_X, etaY, INK_MINUTES, ETA_COLOR, false);
            drawCentred(graphics, scale, "分钟", UNIT_X, etaY + UNIT_DY, INK_UNIT_TEXT, TEXT_COLOR, true);
        }
        drawCentred(graphics, scale, "开往:" + cjkOnly(arrival.destination()), LEFT_X, destY, INK_DEST,
                TEXT_COLOR, true);
    }

    /**
     * 跑马灯：<b>整条文字当成一个整体连续平移</b>（不再是逐字跳）。
     * <p>
     * 做法：把整行文字一次性画出来，用「跑马灯条」那个矩形裁剪，然后按连续的像素偏移左右平移；
     * 一轮滚完再接着画下一份，接缝处留 {@link #TICKER_LOOP_GAP} 的空档，所以看上去是一直流动的。
     *
     * @param offset 已经滚过去多少画布单位（小数也有效，可以是 {@code TICKER_SPEED × 秒数}）
     */
    private static void drawTicker(Graphics2D graphics, float scale, String ticker, float offset) {
        if (ticker == null || ticker.isEmpty()) {
            return;
        }
        final float inkPx = INK_TICKER * scale;
        final Font font = TrdmHeaderTextures.pisFont(inkPx / TrdmHeaderTextures.pisInkRatio()).deriveFont(Font.BOLD);
        graphics.setFont(font);
        final FontMetrics metrics = graphics.getFontMetrics(font);
        final float textWidth = metrics.stringWidth(ticker);
        if (textWidth <= 0) {
            return;
        }
        final float loopWidth = textWidth + TICKER_LOOP_GAP * scale;

        // 只在这条带子里画
        graphics.setClip(Math.round(TICKER_START_X * scale), Math.round(TICKER_CLIP_TOP * scale),
                Math.round((TICKER_END_X - TICKER_START_X) * scale),
                Math.round((TICKER_CLIP_BOTTOM - TICKER_CLIP_TOP) * scale));

        final float baseline = TICKER_Y * scale + inkPx * 0.42F;
        // 起点在条子右端，按偏移往左推；不足一轮时再补画一份接在后面
        final float startX = TICKER_END_X * scale - offset * scale;
        graphics.setColor(new Color(TICKER_COLOR | 0xFF000000, true));
        graphics.drawString(ticker, startX, baseline);
        graphics.drawString(ticker, startX + loopWidth, baseline);

        graphics.setClip(null);
    }

    private static void drawCars(Graphics2D graphics, float scale, Arrival arrival,
                                BufferedImage carHead, BufferedImage carMiddle, BufferedImage carTail) {
        final int count = Math.max(1, arrival == null ? 0 : arrival.carCount());
        final float total = count * CAR_W + (count - 1) * CAR_GAP;
        final float startX = (CANVAS_WIDTH - total) / 2F;
        final float carY = CANVAS_HEIGHT - CAR_H - CAR_BOTTOM;
        for (int index = 0; index < count; index++) {
            final float carX = startX + index * (CAR_W + CAR_GAP);
            final BufferedImage sprite = count == 1 ? carHead
                    : index == 0 ? carHead
                    : index == count - 1 ? carTail
                    : carMiddle;
            if (sprite != null) {
                graphics.drawImage(sprite, Math.round(carX * scale), Math.round(carY * scale),
                        Math.round((carX + CAR_W) * scale), Math.round((carY + CAR_H) * scale),
                        0, 0, sprite.getWidth(), sprite.getHeight(), null);
            }
            float textX = carX + CAR_W / 2F;
            if (index > 0 && index < count - 1) {
                textX -= CAR_MIDDLE_NUMBER_DX;
            }
            drawCentred(graphics, scale, String.valueOf(index + 1), textX, carY + CAR_H / 2F - 1.2F,
                    INK_CAR_NUMBER, TEXT_COLOR, false);
        }
    }

    /**
     * 画一行「撑到指定宽度」的字：<b>靠放大/缩小字号来撑，字距保持字体自己的</b>（不拉字间距）。
     * 日期那一行要和背景板里的「天津地铁欢迎您」左右对齐，用的就是这个。
     */
    private static void drawFitted(Graphics2D graphics, float scale, String text, float x, float y,
                                   float targetWidthUnits, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final float probePx = 200F;
        final Font probe = TrdmHeaderTextures.pisFont(probePx).deriveFont(Font.BOLD);
        final float naturalWidth = graphics.getFontMetrics(probe).stringWidth(text);
        if (naturalWidth <= 0F) {
            return;
        }
        // 目标宽度 ÷ 自然宽度 = 字号倍率
        final float fontSize = probePx * (targetWidthUnits * scale) / naturalWidth;
        final Font font = TrdmHeaderTextures.pisFont(fontSize).deriveFont(Font.BOLD);
        graphics.setFont(font);
        final FontMetrics metrics = graphics.getFontMetrics(font);
        final float inkPx = fontSize * TrdmHeaderTextures.pisInkRatio();
        final float baseline = y * scale + inkPx * 0.42F;
        graphics.setColor(new Color(color | 0xFF000000, true));
        graphics.drawString(text, x * scale - metrics.stringWidth(text) / 2F, baseline);
    }

    /** 以 (x, y) 为中心画一行字；{@code inkUnits} 是设计稿里的**字形墨水高度**（画布单位）。 */
    private static void drawCentred(Graphics2D graphics, float scale, String text, float x, float y,
                                    float inkUnits, int color, boolean cjk) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final float inkPx = inkUnits * scale;
        final float fontSize = inkPx / TrdmHeaderTextures.pisInkRatio();
        // PIS 整个版面都用思源黑体（中文、数字、英文同一套字形）
        final Font font = TrdmHeaderTextures.pisFont(fontSize).deriveFont(Font.BOLD);
        graphics.setFont(font);
        final FontMetrics metrics = graphics.getFontMetrics(font);
        graphics.setColor(new Color(color | 0xFF000000, true));
        final float px = x * scale;
        // 让字形墨水（不是行高）垂直居中
        final float baseline = y * scale + inkPx * 0.42F;
        graphics.drawString(text, px - metrics.stringWidth(text) / 2F, baseline);
    }

    /** 「中文|English」只取中文那段（脚本里的 {@code TextUtil.getCjkParts}）。 */
    private static String cjkOnly(String destination) {
        if (destination == null) {
            return "";
        }
        final String[] parts = destination.split("\\|");
        return parts[0].trim();
    }

    // ===== 给方块渲染器用：拆成「静态版面」+「跑马灯贴图条」两层 =====

    /** 跑马灯条在画布里的矩形（左、上、右、下）。 */
    public static float[] tickerRect() {
        return new float[]{TICKER_START_X, TICKER_CLIP_TOP, TICKER_END_X, TICKER_CLIP_BOTTOM};
    }

    /**
     * 只画静态版面（不含跑马灯文字）—— 方块上这层每秒重烘一次就够了。
     */
    public static BufferedImage renderPlate(int width, int height, View view, BufferedImage ad,
                                           BufferedImage carHead, BufferedImage carMiddle, BufferedImage carTail) {
        final View noTicker = new View(view.first(), view.second(), view.clock(), view.date(), "", 0F);
        return render(width, height, noTicker, ad, carHead, carMiddle, carTail);
    }

    /**
     * 跑马灯文字条：一整轮文字（文字 + 空档）烘成一条**透明底**的贴图，
     * 渲染端拿它做 UV 平移，就能连续滚动而不用每帧重烘整块屏。
     *
     * @param stripScale 每画布单位多少像素（和版面贴图同一个比例）
     */
    public static TickerStrip renderTickerStrip(String ticker, int stripScale) {
        final String text = ticker == null ? "" : ticker;
        final float inkPx = INK_TICKER * stripScale;
        final float fontSize = inkPx / TrdmHeaderTextures.pisInkRatio();
        final BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D probeGraphics = probe.createGraphics();
        probeGraphics.setFont(TrdmHeaderTextures.pisFont(fontSize));
        final FontMetrics metrics = probeGraphics.getFontMetrics();
        final int textWidth = metrics.stringWidth(text);
        final float loopUnits = (textWidth / (float) stripScale) + TICKER_LOOP_GAP;
        probeGraphics.dispose();

        final int width = Math.max(1, Math.round(loopUnits * stripScale));
        final int height = Math.max(1, Math.round((TICKER_CLIP_BOTTOM - TICKER_CLIP_TOP) * stripScale));
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            final Font font = TrdmHeaderTextures.pisFont(fontSize).deriveFont(Font.BOLD);
            graphics.setFont(font);
            graphics.setColor(new Color(TICKER_COLOR | 0xFF000000, true));
            // 和版面里同一套竖直居中：让字形墨水基本顶满这条带子
            graphics.drawString(text, 0F, height / 2F + inkPx * 0.42F);
        } finally {
            graphics.dispose();
        }
        return new TickerStrip(image, loopUnits);
    }

    /**
     * @param image     一整轮文字的贴图（透明底）
     * @param loopUnits 这一轮在画布单位里有多宽（= 贴图宽 / 每单位像素数）
     */
    public record TickerStrip(BufferedImage image, float loopUnits) {
    }
}
