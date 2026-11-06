package top.xiaotang27.trdm.render;

import top.xiaotang27.trdm.control.PsdStyles;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 津滨轻轨风格线路图的 AWT 排版。
 * <p>
 * 参考图 Frame 91（bmt）和 Frame 94（改造），所有比例都按参考图 675 高的画布量：
 * <ul>
 *     <li>线路是一条粗横线，站名竖排在线上方：汉字逐字直立堆叠、英文旋转 90° 紧贴汉字右侧，
 *     整个「汉字 + 英文」块以站点圆心为中心；</li>
 *     <li>线头一侧是参考图里的斜切箭头：箭头底边与线路下沿齐平，左沿和线路端点重合，
 *     所以视觉上是线路被斜着切了一刀，而不是另外贴一个箭头；</li>
 *     <li>线路图的左右方向和天津地铁样式一致：{@code arrowLeft} 时序号 0 在最右、
 *     序号大的往左排，箭头在最左端，即「前方 = 箭头那一侧 = 序号大的那边」；</li>
 *     <li>bmt：换乘站用虚线引出小线路牌，图例中英双语；</li>
 *     <li>改造：换乘站用竖色旗 + 「换乘X号线」文字，图例中文、字号比 bmt 大一档，
 *     复用现有站名牌素材。</li>
 * </ul>
 * 站距、图标、字号都按参考图比例算；吊板太窄时字号跟着缩，不横向拉伸。
 */
public final class TrdmJinbinRouteMap {

    private static final int TEXT_COLOR = 0xFF000000;
    private static final int CURRENT_COLOR = 0xFFFF0000;
    private static final int PASSED_COLOR = 0xFF8B8B8B;
    private static final int LEADER_COLOR = 0xFF9A9A9A;

    // ===== 参考图（675 高）比例 =====

    /** 线路中心线（Frame 91 / Frame 94）。 */
    private static final float LINE_Y_BMT = 376F / 675F;
    private static final float LINE_Y_TRT = 328F / 675F;
    /** 线路粗细（参考图 363–389 共 27 px）。 */
    private static final float LINE_THICKNESS = 27F / 675F;
    /** 站点图标直径（参考图 49 px）。 */
    private static final float MARKER = 49F / 675F;

    /** 线路图箭头素材（bmt_linehead，84×61）的墨迹尺寸。 */
    private static final float ARROW_W = 84F / 675F;
    private static final float ARROW_H = 61F / 675F;
    /** 箭头墨迹左沿到端头站圆心的距离（参考图 1202 → 1356）。 */
    private static final float ARROW_LEAD = 154F / 675F;
    /** 线路本体从箭头左沿再往里收多少，让斜切边完整露出来（参考图 20 px）。 */
    private static final float ARROW_LINE_INSET = 20F / 675F;

    private static final float MIN_PITCH = 0.19F;
    /** 参考图站距 164/675；吊板再宽也不把站距拉得比参考图更大。 */
    private static final float MAX_PITCH = 164F / 675F;
    /** 整组站点占吊板宽度的比例（两边留白）。 */
    private static final float GROUP_WIDTH = 0.86F;

    /** 竖排站名：汉字字号 / 板高（参考图汉字墨高约 37 px、字距约 38 px）。 */
    private static final float NAME_CN = 42F / 675F;
    /** 英文旋转后的字高 / 板高（参考图墨迹厚约 17 px）。 */
    private static final float NAME_EN = 18F / 675F;
    /** 汉字行距 / 字号。 */
    private static final float NAME_CN_LINE = 0.92F;
    /** 汉字列与英文列之间的缝隙（参考图 4 px）。 */
    private static final float NAME_COLUMN_GAP = 4F / 675F;
    /** 站名基线到线路上沿（bmt / 改造）。 */
    private static final float NAME_GAP_BMT = 29F / 675F;
    private static final float NAME_GAP_TRT = 21F / 675F;
    /** 站名块离吊板上沿至少留这么多。 */
    private static final float NAME_TOP_MARGIN = 0.02F;

    /** bmt 换乘小线路牌（参考图 54×37，两块紧挨着）。 */
    private static final float BMT_BADGE_W = 54F / 675F;
    private static final float BMT_BADGE_H = 37F / 675F;
    private static final float BMT_BADGE_GAP = 2F / 675F;
    private static final float BMT_BADGE_TOP = 482F / 675F;

    /** 改造版换乘色旗：每条线路一条，30 宽（参考图两条线共 60 宽），从线中心往下挂 127。 */
    private static final float TRT_FLAG_UNIT_W = 30F / 675F;
    private static final float TRT_FLAG_H = 127F / 675F;
    private static final float TRT_TEXT_GAP = 18F / 675F;
    private static final float TRT_TEXT_CN = 32F / 675F;
    private static final float TRT_TEXT_EN = 19F / 675F;

    /** bmt 图例整条素材（Group 49）：宽 774 px，高度按 37/675 板高画。 */
    private static final float BMT_LEGEND_IMAGE_W = 774F;
    private static final float BMT_LEGEND_CENTER = 0.925F;

    /** 改造版图例（只有中文，字号大一档）。 */
    private static final float TRT_LEGEND_ICON = 0.075F;
    private static final float TRT_LEGEND_CN = 0.072F;
    private static final float TRT_LEGEND_SWATCH_W = 0.182F;
    private static final float TRT_LEGEND_SWATCH_H = 0.070F;
    private static final float TRT_LEGEND_CENTER = 0.906F;

    private static final float LEGEND_ICON_GAP = 0.010F;
    private static final float LEGEND_ITEM_GAP = 0.028F;

    private TrdmJinbinRouteMap() {
    }

    /**
     * 信息完整所需的最小宽高比：站距压到 {@link #MIN_PITCH} 时。
     * 渲染端拿它决定要不要向旁边的滑动门吊板借位。
     */
    public static float minimumAspect(int stationCount, boolean arrowLeft, int style) {
        final float content = MIN_PITCH * Math.max(0, stationCount - 1);
        return Math.max(2.4F, content + ARROW_LEAD + 0.32F);
    }

    public static BufferedImage render(int width, int height, int style, TrdmRouteMap.Model model,
                                       float runLeftFraction, float runRightFraction) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            draw(graphics, width, height, style, model, runLeftFraction, runRightFraction);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public static void draw(Graphics2D graphics, int width, int height, int style, TrdmRouteMap.Model model,
                            float runLeftFraction, float runRightFraction) {
        final List<TrdmRouteMap.Station> stations = model.stations();
        final int count = stations.size();
        if (count < 1) {
            return;
        }
        final boolean retrofit = style == PsdStyles.HEADER_JINBIN_RETROFIT;
        final boolean arrowLeft = model.arrowLeft();
        final int routeColor = model.routeColor() | 0xFF000000;
        final int current = Math.max(0, Math.min(count - 1, model.currentIndex()));

        // 只铺「这块玻璃吊板」的白色底，借来的滑动门那一截留透明
        final float runLeft = width * clamp01(runLeftFraction);
        final float runRight = width - width * clamp01(runRightFraction);
        graphics.setColor(new Color(0xFFFFFFFF, true));
        graphics.fillRect(Math.round(runLeft), 0, Math.max(1, Math.round(runRight - runLeft)), height);
        graphics.setColor(new Color(routeColor, true));
        graphics.fillRect(Math.round(runLeft), Math.round(height * 0.972F),
                Math.max(1, Math.round(runRight - runLeft)), Math.max(1, Math.round(height * 0.028F)));

        final float lineY = height * (retrofit ? LINE_Y_TRT : LINE_Y_BMT);
        final float thickness = height * LINE_THICKNESS;
        final float marker = height * MARKER;
        final float lineTop = lineY - thickness / 2F;
        final float lineBottom = lineY + thickness / 2F;

        // ===== 站距：固定比例，吊板更宽时最多摊到 MAX_PITCH，整组水平居中 =====
        final float available = Math.max(1F, width * GROUP_WIDTH);
        final float fitPitch = count > 1 ? available / (count - 1) : 0F;
        final float pitch = count > 1 ? Math.min(fitPitch, height * MAX_PITCH) : 0F;
        final float squeeze = fitPitch > 0F && fitPitch < height * MIN_PITCH
                ? Math.max(0.35F, fitPitch / (height * MIN_PITCH)) : 1F;
        final float groupWidth = pitch * (count - 1);
        // 整组（含箭头那一侧的伸出、末端半个站点图标）水平居中，和参考图一样
        final float leading = height * ARROW_LEAD;
        // 端头站那一侧的内容边界就是圆形图标的右/左沿
        final float trailing = marker * 0.5F;
        final float originX = (width - (leading + groupWidth + trailing)) / 2F;
        final float groupLeft = arrowLeft ? originX + leading : originX + trailing;
        final float[] xs = new float[count];
        for (int i = 0; i < count; i++) {
            // 和天津地铁样式一致：前方 = 箭头那一侧 = 序号大的那边。
            // arrowLeft 时序号 0 在最右，序号往左递增；arrowRight 反过来。
            xs[i] = arrowLeft ? groupLeft + groupWidth - pitch * i : groupLeft + pitch * i;
        }

        // ===== 线路 =====
        final float firstX = xs[0];
        final float lastX = xs[count - 1];
        final float leftMost = Math.min(firstX, lastX);
        final float rightMost = Math.max(firstX, lastX);
        // 没有箭头的那一端是始发/终点站：线路收到站点圆心就停，端头被圆形图标整个盖住
        final float lineLeft = arrowLeft ? leftMost - height * ARROW_LEAD : leftMost;
        final float lineRight = arrowLeft ? rightMost : rightMost + height * ARROW_LEAD;
        // 箭头那一侧的线头往里收一点，让斜切边露出线路轮廓（参考图的「斜切」就是这样来的）
        final float drawLeft = arrowLeft ? lineLeft + height * ARROW_LINE_INSET : lineLeft;
        final float drawRight = arrowLeft ? lineRight : lineRight - height * ARROW_LINE_INSET;
        graphics.setColor(new Color(routeColor, true));
        graphics.fillRect(Math.round(drawLeft), Math.round(lineTop),
                Math.max(1, Math.round(drawRight - drawLeft)), Math.max(1, Math.round(thickness)));

        // ===== 换乘信息（先画，站点图标后画会盖住旗子 / 引线顶端）=====
        if (retrofit) {
            drawTrtInterchange(graphics, height, stations, xs, lineY, marker, pitch, squeeze);
        } else {
            drawBmtInterchange(graphics, height, stations, xs, lineY, marker, squeeze);
        }

        // ===== 箭头（底边和线路下沿齐平，左沿就是线路端点）=====
        drawArrowWing(graphics, height, routeColor, lineLeft, lineRight, lineBottom, arrowLeft);

        // ===== 站名 =====
        final float nameBottom = lineTop - height * (retrofit ? NAME_GAP_TRT : NAME_GAP_BMT);
        final float nameScale = fitNameScale(graphics, height, nameBottom, pitch, stations, squeeze);
        for (int i = 0; i < count; i++) {
            final int color = nameColor(stations.get(i), i, current, arrowLeft, retrofit);
            drawVerticalName(graphics, height, xs[i], nameBottom, nameScale, stations.get(i), color);
        }

        // ===== 站点图标 =====
        for (int i = 0; i < count; i++) {
            final BufferedImage icon = markerImage(stations.get(i), i, count, current, arrowLeft, routeColor, retrofit);
            TrdmSignAssets.drawSprite(graphics, icon, xs[i], lineY, marker);
        }

        // ===== 图例 =====
        if (retrofit) {
            drawTrtLegend(graphics, width, height, stations, current, routeColor, runLeft, runRight, arrowLeft);
        } else {
            drawBmtLegend(graphics, width, height, runLeft, runRight);
        }
    }

    private static float clamp01(float value) {
        return Math.max(0F, Math.min(1F, value));
    }

    // ===== 线路箭头 / 站点图标 =====

    private static void drawArrowWing(Graphics2D graphics, float height, int routeColor, float lineLeft,
                                      float lineRight, float lineBottom, boolean arrowLeft) {
        final TrdmSignAssets.Sprite wing = TrdmSignAssets.tintedSprite(
                TrdmSignAssets.BMT_LINEHEAD, routeColor, !arrowLeft);
        if (wing == null) {
            return;
        }
        final float width = height * ARROW_W;
        final float wingHeight = height * ARROW_H;
        final float x = arrowLeft ? lineLeft : lineRight - width;
        TrdmSignAssets.drawSpriteInk(graphics, wing, x, lineBottom - wingHeight, width, wingHeight);
    }

    private static BufferedImage markerImage(TrdmRouteMap.Station station, int index, int count, int current,
                                             boolean arrowLeft, int routeColor, boolean retrofit) {
        final boolean terminal = index == 0 || index == count - 1;
        final boolean transfer = !station.interchange().isEmpty();
        final boolean passed = passed(index, current, arrowLeft);
        if (retrofit) {
            if (terminal) {
                return imageOf(TrdmSignAssets.HEAD_TAIL, false);
            }
            if (transfer) {
                final String name = station.interchange().size() > 1
                        ? TrdmSignAssets.TRANSFER_3 : TrdmSignAssets.TRANSFER_2;
                final BufferedImage recolored = TrdmSignAssets.recolored(name, arcColors(routeColor, station.interchange()));
                return recolored != null ? recolored : imageOf(name, false);
            }
            if (index == current) {
                return TrdmSignAssets.recoloredBody(TrdmSignAssets.TRT_BMT_THIS_STATION, routeColor, !arrowLeft);
            }
            if (passed) {
                return imageOf(TrdmSignAssets.BMT_PASS_STATION, false);
            }
            return TrdmSignAssets.recoloredBody(TrdmSignAssets.TRT_BMT_NEXT_STATION, routeColor);
        }

        if (terminal) {
            return imageOf(TrdmSignAssets.BMT_HEAD_STATION, false);
        }
        if (transfer) {
            return imageOf(TrdmSignAssets.BMT_TRANSFER_STATION, false);
        }
        if (index == current) {
            return imageOf(TrdmSignAssets.BMT_THIS_STATION, false);
        }
        if (passed) {
            return imageOf(TrdmSignAssets.BMT_PASS_STATION, false);
        }
        return imageOf(TrdmSignAssets.BMT_NEXT_STATION, false);
    }

    /** 行进方向上的「已驶过」：arrowLeft 时序号往左递增，序号小于本站的在身后。 */
    private static boolean passed(int index, int current, boolean arrowLeft) {
        return arrowLeft ? index < current : index > current;
    }

    private static int nameColor(TrdmRouteMap.Station station, int index, int current, boolean arrowLeft,
                                 boolean retrofit) {
        if (retrofit) {
            return TEXT_COLOR;
        }
        if (index == current) {
            return CURRENT_COLOR;
        }
        return passed(index, current, arrowLeft) ? PASSED_COLOR : TEXT_COLOR;
    }

    private static int[] arcColors(int routeColor, List<TrdmRouteMap.Line> interchange) {
        final int[] colors = new int[interchange.size() + 1];
        colors[0] = routeColor;
        for (int i = 0; i < interchange.size(); i++) {
            colors[i + 1] = interchange.get(i).color();
        }
        return colors;
    }

    private static BufferedImage imageOf(String name, boolean mirror) {
        final TrdmSignAssets.Sprite sprite = TrdmSignAssets.sprite(name, mirror);
        return sprite == null ? null : sprite.image();
    }

    // ===== 竖排站名 =====

    private static float fitNameScale(Graphics2D graphics, float height, float nameBottom, float pitch,
                                      List<TrdmRouteMap.Station> stations, float squeeze) {
        float scale = squeeze;
        // 名字底边固定在线路上方，往上最多占到吊板上沿留一点白
        final float availableHeight = Math.max(1F, nameBottom - height * NAME_TOP_MARGIN);
        final float cnSize = height * NAME_CN;
        final float enSize = height * NAME_EN;
        for (int iteration = 0; iteration < 4; iteration++) {
            float worst = 0F;
            for (final TrdmRouteMap.Station station : stations) {
                final int chars = station.chinese() == null ? 0 : station.chinese().length();
                final float cnBlock = chars * cnSize * scale * NAME_CN_LINE;
                final float enBlock = textWidth(graphics, station.english(), TrdmHeaderTextures.latinFont(enSize * scale));
                worst = Math.max(worst, Math.max(cnBlock, enBlock));
            }
            if (worst <= availableHeight || worst <= 0F) {
                break;
            }
            scale *= Math.max(0.55F, availableHeight / worst * 0.98F);
        }
        if (pitch > 0F) {
            final float columnWidth = cnSize * scale * 0.96F + height * NAME_COLUMN_GAP + enSize * scale * 0.95F;
            if (columnWidth > pitch * 0.86F) {
                scale *= Math.max(0.45F, pitch * 0.86F / columnWidth);
            }
        }
        return scale;
    }

    private static void drawVerticalName(Graphics2D graphics, float height, float x, float bottom, float scale,
                                         TrdmRouteMap.Station station, int color) {
        final float cnSize = height * NAME_CN * scale;
        final float enSize = height * NAME_EN * scale;
        final Font cnFont = TrdmHeaderTextures.font(cnSize);
        final Font enFont = TrdmHeaderTextures.latinFont(enSize);

        final String chinese = station.chinese() == null ? "" : station.chinese();
        final String english = station.english() == null ? "" : station.english();
        final int chars = chinese.length();

        // 「汉字列 + 英文列」整体以站点圆心为中心：参考图里汉字略偏左、英文紧贴右侧
        final float cnWidth = cnSize * 0.96F;
        final float enThickness = enSize * 0.95F;
        final float gap = height * NAME_COLUMN_GAP;
        final float total = cnWidth + gap + enThickness;
        final float blockLeft = x - total / 2F;
        final float cnCenter = blockLeft + cnWidth / 2F;

        graphics.setColor(new Color(color, true));
        final float lineHeight = cnSize * NAME_CN_LINE;
        for (int i = 0; i < chars; i++) {
            final String ch = chinese.substring(i, i + 1);
            final float baseline = bottom - (chars - 1 - i) * lineHeight;
            final float charWidth = textWidth(graphics, ch, cnFont);
            graphics.setFont(cnFont);
            graphics.drawString(ch, cnCenter - charWidth / 2F, baseline);
            graphics.drawString(ch, cnCenter - charWidth / 2F + cnSize * 0.03F, baseline);
        }

        if (!english.isEmpty()) {
            final FontMetrics enMetrics = graphics.getFontMetrics(enFont);
            final float englishWidth = textWidth(graphics, english, enFont);
            final AffineTransform old = graphics.getTransform();
            // 旋转 90°：英文从上方往下读；底端和最后一个汉字对齐，整列紧贴汉字右边
            graphics.translate(blockLeft + cnWidth + gap + enMetrics.getDescent(),
                    bottom - englishWidth + cnSize * 0.08F);
            graphics.rotate(Math.PI / 2);
            graphics.setFont(enFont);
            graphics.setColor(new Color(color, true));
            graphics.drawString(english, 0F, 0F);
            graphics.setTransform(old);
        }
    }

    // ===== bmt 换乘：虚线 + 小线路牌 =====

    private static void drawBmtInterchange(Graphics2D graphics, float height, List<TrdmRouteMap.Station> stations,
                                           float[] xs, float lineY, float marker, float squeeze) {
        final float badgeW = height * BMT_BADGE_W;
        final float badgeH = height * BMT_BADGE_H;
        final float gap = height * BMT_BADGE_GAP;
        final float badgeTop = height * BMT_BADGE_TOP;
        final float markerBottom = lineY + marker / 2F;
        final BasicStroke leader = new BasicStroke(Math.max(1F, height * 0.0022F), BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10F, new float[]{height * 0.006F, height * 0.006F}, 0F);

        for (int i = 0; i < stations.size(); i++) {
            final List<TrdmRouteMap.Line> interchange = stations.get(i).interchange();
            if (interchange.isEmpty()) {
                continue;
            }
            graphics.setColor(new Color(LEADER_COLOR, true));
            graphics.setStroke(leader);
            graphics.drawLine(Math.round(xs[i]), Math.round(markerBottom), Math.round(xs[i]), Math.round(badgeTop));

            final float totalWidth = interchange.size() * badgeW + (interchange.size() - 1) * gap;
            float left = xs[i] - totalWidth / 2F;
            for (final TrdmRouteMap.Line line : interchange) {
                drawSmallLineBadge(graphics, left, badgeTop, badgeW, badgeH, line, squeeze);
                left += badgeW + gap;
            }
        }
        graphics.setStroke(new BasicStroke(1F));
    }

    private static void drawSmallLineBadge(Graphics2D graphics, float x, float y, float width,
                                           float heightPx, TrdmRouteMap.Line line, float squeeze) {
        final int color = line.color() | 0xFF000000;
        graphics.setColor(new Color(color, true));
        graphics.fillRect(Math.round(x), Math.round(y), Math.round(width), Math.round(heightPx));
        final boolean light = isLightColor(color);
        graphics.setColor(new Color(light ? 0xFF000000 : 0xFFFFFFFF, true));
        final float cnSize = heightPx * 0.48F * squeeze;
        final float enSize = heightPx * 0.30F * squeeze;
        final Font cnFont = TrdmHeaderTextures.font(cnSize);
        final Font enFont = TrdmHeaderTextures.latinFont(enSize);
        final FontMetrics cnMetrics = graphics.getFontMetrics(cnFont);
        final FontMetrics enMetrics = graphics.getFontMetrics(enFont);
        final String chinese = line.chineseName() == null || line.chineseName().isEmpty()
                ? "线路" : line.chineseName();
        final String english = line.englishName() == null || line.englishName().isEmpty()
                ? "" : line.englishName();
        final float block = cnMetrics.getAscent() + (english.isEmpty() ? 0F : enMetrics.getAscent() + 1F);
        float cursor = y + (heightPx - block) / 2F;
        graphics.setFont(cnFont);
        graphics.drawString(chinese, x + (width - textWidth(graphics, chinese, cnFont)) / 2F,
                cursor + cnMetrics.getAscent());
        if (!english.isEmpty()) {
            cursor += cnMetrics.getAscent() + 1F;
            graphics.setFont(enFont);
            graphics.drawString(english, x + (width - textWidth(graphics, english, enFont)) / 2F,
                    cursor + enMetrics.getAscent());
        }
    }

    // ===== 改造版换乘：竖色旗 + 换乘文字 =====

    private static void drawTrtInterchange(Graphics2D graphics, float height, List<TrdmRouteMap.Station> stations,
                                           float[] xs, float lineY, float marker, float pitch, float squeeze) {
        final float flagH = height * TRT_FLAG_H;
        final float flagTop = lineY;
        final float textGap = height * TRT_TEXT_GAP;
        final float textTop = flagTop + flagH + textGap;

        for (int i = 0; i < stations.size(); i++) {
            final List<TrdmRouteMap.Line> interchange = stations.get(i).interchange();
            if (interchange.isEmpty()) {
                continue;
            }
            // 换乘旗宽度一律按单线换乘算（多线只在同一宽度里分色条），不管换乘几条线都一样宽
            final float flagW = height * TRT_FLAG_UNIT_W;
            final float left = xs[i] - flagW / 2F;
            final float band = flagW / interchange.size();
            final Shape oldClip = graphics.getClip();
            for (int j = 0; j < interchange.size(); j++) {
                final BufferedImage flag = TrdmSignAssets.tinted(TrdmSignAssets.TRANSFER_LABEL,
                        interchange.get(j).color());
                if (flag == null) {
                    continue;
                }
                final int x0 = Math.round(left + band * j);
                final int x1 = Math.round(left + band * (j + 1)) + (j == interchange.size() - 1 ? 0 : 1);
                graphics.setClip(oldClip);
                graphics.clipRect(x0, Math.round(flagTop), Math.max(1, x1 - x0),
                        Math.max(1, Math.round(flagH) + 1));
                graphics.drawImage(flag, Math.round(left), Math.round(flagTop),
                        Math.round(left + flagW), Math.round(flagTop + flagH),
                        0, 0, flag.getWidth(), flag.getHeight(), null);
            }
            graphics.setClip(oldClip);

            float cnSize = height * TRT_TEXT_CN * squeeze;
            float enSize = height * TRT_TEXT_EN * squeeze;
            float widest = 0F;
            for (final TrdmRouteMap.Line line : interchange) {
                widest = Math.max(widest, textWidth(graphics, transferChinese(line),
                        TrdmHeaderTextures.font(cnSize)));
                widest = Math.max(widest, textWidth(graphics, transferEnglish(line),
                        TrdmHeaderTextures.latinFont(enSize)));
            }
            if (pitch > 0F && widest > pitch * 0.96F) {
                final float shrink = pitch * 0.96F / widest;
                cnSize *= shrink;
                enSize *= shrink;
            }
            final Font cnFont = TrdmHeaderTextures.font(cnSize);
            final Font enFont = TrdmHeaderTextures.latinFont(enSize);
            final FontMetrics cnMetrics = graphics.getFontMetrics(cnFont);
            final FontMetrics enMetrics = graphics.getFontMetrics(enFont);
            graphics.setColor(new Color(TEXT_COLOR, true));
            float y = textTop;
            for (final TrdmRouteMap.Line line : interchange) {
                final String chinese = transferChinese(line);
                final String english = transferEnglish(line);
                graphics.setFont(cnFont);
                graphics.drawString(chinese, xs[i] - textWidth(graphics, chinese, cnFont) / 2F,
                        y + cnMetrics.getAscent());
                graphics.drawString(chinese, xs[i] - textWidth(graphics, chinese, cnFont) / 2F + cnSize * 0.03F,
                        y + cnMetrics.getAscent());
                y += cnMetrics.getAscent() + height * 0.004F;
                if (!english.isEmpty()) {
                    graphics.setFont(enFont);
                    graphics.drawString(english, xs[i] - textWidth(graphics, english, enFont) / 2F,
                            y + enMetrics.getAscent());
                    y += enMetrics.getAscent();
                }
                y += height * 0.018F;
            }
        }
    }

    private static String transferChinese(TrdmRouteMap.Line line) {
        final String name = line.chineseName() == null ? "" : line.chineseName().trim();
        if (name.isEmpty()) {
            return "换乘" + digits(line.englishName()) + "号线";
        }
        return name.startsWith("换乘") ? name : "换乘" + name;
    }

    private static String transferEnglish(TrdmRouteMap.Line line) {
        final String name = line.englishName() == null ? "" : line.englishName().trim();
        if (name.isEmpty()) {
            return "Transfer to Line " + digits(line.chineseName());
        }
        return name.startsWith("Transfer") ? name : "Transfer to " + name;
    }

    private static String digits(String text) {
        if (text == null) {
            return "";
        }
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    // ===== 图例 =====

    private static void drawBmtLegend(Graphics2D graphics, int width, int height, float runLeft, float runRight) {
        // 整条图例直接用 Group 49 素材（774×37，透明底），和参考图一字不差：
        // 高度 37/675 板高、右对齐、垂直中心 0.925 板高
        final TrdmSignAssets.Sprite legend = TrdmSignAssets.sprite(TrdmSignAssets.BMT_LEGEND);
        if (legend == null) {
            return;
        }
        final float scale = height / 675F;
        float legendWidth = BMT_LEGEND_IMAGE_W * scale;
        final float available = Math.max(1F, runRight - runLeft - width * 0.02F);
        if (legendWidth > available) {
            legendWidth = available;
        }
        if (legendWidth <= 0F) {
            return;
        }
        final float centerX = runRight - width * 0.01F - legendWidth / 2F;
        final float centerY = height * BMT_LEGEND_CENTER;
        TrdmSignAssets.drawSprite(graphics, legend.image(), centerX, centerY, legendWidth);
    }

    private static void drawTrtLegend(Graphics2D graphics, int width, int height,
                                      List<TrdmRouteMap.Station> stations, int current, int routeColor,
                                      float runLeft, float runRight, boolean arrowLeft) {
        final float available = Math.max(1F, runRight - runLeft - width * 0.02F);
        float scale = 1F;
        float total = 0F;
        for (int iteration = 0; iteration < 4; iteration++) {
            total = trtLegendWidth(graphics, height, scale);
            if (total <= available || total <= 0F) {
                break;
            }
            scale *= Math.max(0.35F, available / total * 0.98F);
        }
        final float icon = height * TRT_LEGEND_ICON * scale;
        final float swatchW = height * TRT_LEGEND_SWATCH_W * scale;
        final float swatchH = height * TRT_LEGEND_SWATCH_H * scale;
        final float cnSize = height * TRT_LEGEND_CN * scale;
        final float iconGap = height * LEGEND_ICON_GAP * scale;
        final float itemGap = height * LEGEND_ITEM_GAP * scale;
        final Font cnFont = TrdmHeaderTextures.font(cnSize);
        final FontMetrics metrics = graphics.getFontMetrics(cnFont);
        final float centerY = height * TRT_LEGEND_CENTER;
        final float baseline = centerY + metrics.getAscent() * 0.45F;
        final float startX = Math.max(runLeft, runRight - width * 0.015F - total);

        float x = startX;
        graphics.setColor(new Color(TEXT_COLOR, true));
        graphics.setFont(cnFont);
        graphics.drawString("图例：", x, baseline);
        x += textWidth(graphics, "图例：", cnFont) + iconGap;

        // 当前站
        TrdmSignAssets.drawSprite(graphics, TrdmSignAssets.recoloredBody(
                TrdmSignAssets.TRT_BMT_THIS_STATION, routeColor, !arrowLeft), x + icon / 2F, centerY, icon);
        x += icon + iconGap;
        x = trtLegendLabel(graphics, x, baseline, "当前站", cnFont, cnSize) + itemGap;

        // 换乘站：用线路图里真实出现过的换乘圈
        final List<BufferedImage> transferIcons = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (final TrdmRouteMap.Station station : stations) {
            if (station.interchange().isEmpty()) {
                continue;
            }
            final String name = station.interchange().size() > 1
                    ? TrdmSignAssets.TRANSFER_3 : TrdmSignAssets.TRANSFER_2;
            final BufferedImage image = TrdmSignAssets.recolored(name, arcColors(routeColor, station.interchange()));
            final String key = name + "|" + Arrays.toString(arcColors(routeColor, station.interchange()));
            if (image != null && seen.add(key)) {
                transferIcons.add(image);
            }
        }
        if (transferIcons.isEmpty()) {
            transferIcons.add(TrdmSignAssets.recolored(TrdmSignAssets.TRANSFER_2,
                    new int[]{routeColor, 0xE4007F}));
        }
        for (int i = 0; i < transferIcons.size(); i++) {
            TrdmSignAssets.drawSprite(graphics, transferIcons.get(i), x + icon * (i + 0.5F), centerY, icon);
        }
        x += icon * transferIcons.size() + iconGap;
        x = trtLegendLabel(graphics, x, baseline, "换乘站", cnFont, cnSize) + itemGap;

        // 始终站
        TrdmSignAssets.drawSprite(graphics, imageOf(TrdmSignAssets.HEAD_TAIL, false), x + icon / 2F, centerY, icon);
        x += icon + iconGap;
        x = trtLegendLabel(graphics, x, baseline, "始终站", cnFont, cnSize) + itemGap;

        // 前方到站 / 驶过车站
        x = drawSwatch(graphics, x, centerY, swatchW, swatchH, routeColor, iconGap);
        x = trtLegendLabel(graphics, x, baseline, "前方到站", cnFont, cnSize) + itemGap;
        x = drawSwatch(graphics, x, centerY, swatchW, swatchH, 0xFF8B8B8B, iconGap);
        trtLegendLabel(graphics, x, baseline, "驶过车站", cnFont, cnSize);
    }

    private static float trtLegendWidth(Graphics2D graphics, float height, float scale) {
        final float icon = height * TRT_LEGEND_ICON * scale;
        final float swatchW = height * TRT_LEGEND_SWATCH_W * scale;
        final float cnSize = height * TRT_LEGEND_CN * scale;
        final float iconGap = height * LEGEND_ICON_GAP * scale;
        final float itemGap = height * LEGEND_ITEM_GAP * scale;
        final Font cnFont = TrdmHeaderTextures.font(cnSize);
        final String[] labels = {"当前站", "换乘站", "始终站", "前方到站", "驶过车站"};
        float total = textWidth(graphics, "图例：", cnFont) + iconGap;
        for (int i = 0; i < labels.length; i++) {
            total += textWidth(graphics, labels[i], cnFont) + iconGap + itemGap;
            total += i == 3 || i == 4 ? swatchW : icon * (i == 1 ? 2F : 1F);
        }
        return total;
    }

    private static float trtLegendLabel(Graphics2D graphics, float x, float baseline, String label, Font font,
                                        float size) {
        graphics.setColor(new Color(TEXT_COLOR, true));
        graphics.setFont(font);
        graphics.drawString(label, x, baseline);
        graphics.drawString(label, x + size * 0.03F, baseline);
        return x + textWidth(graphics, label, font);
    }

    private static float drawSwatch(Graphics2D graphics, float x, float centerY, float width, float height,
                                    int color, float gap) {
        final BufferedImage sprite = TrdmSignAssets.tinted(TrdmSignAssets.COLOR_LINE, color);
        if (sprite != null) {
            graphics.drawImage(sprite, Math.round(x), Math.round(centerY - height / 2F),
                    Math.round(x + width), Math.round(centerY + height / 2F),
                    0, 0, sprite.getWidth(), sprite.getHeight(), null);
        }
        return x + width + gap;
    }

    // ===== 字体 =====

    private static float textWidth(Graphics2D graphics, String text, Font font) {
        if (text == null || text.isEmpty()) {
            return 0F;
        }
        graphics.setFont(font);
        return (float) font.getStringBounds(text, graphics.getFontRenderContext()).getWidth();
    }

    private static boolean isLightColor(int color) {
        final int red = (color >> 16) & 0xFF;
        final int green = (color >> 8) & 0xFF;
        final int blue = color & 0xFF;
        return 0.299F * red + 0.587F * green + 0.114F * blue > 165F;
    }
}
