package top.xiaotang27.trdm.render;

import top.xiaotang27.trdm.render.TrdmHeaderTextures.Badge;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 线路图吊板的绘制。
 * <p>
 * <b>固定比例</b>：站距、字号、图标全部按吊板高度取固定比例（{@link #PITCH} 等），
 * 吊板再宽也只是两边多留空、整组水平居中，绝不横向拉伸；吊板太窄时由调用方借旁边滑动门的吊板来加宽。
 * <p>
 * 版面（从左到右、从上到下）：
 * <pre>
 *      ╱站名中文        ← 站名按 31° 斜着排，中文在上、英文在下
 *    ╱ XIEMING
 *  ⊙──────●──────⊚──────◀──────⊚──────○──────⊙      线路（后方灰色 / 前方线路色）+ 方向箭头
 *                 │            │
 *               红色尖旗      黄|紫
 *              换乘1号线    换乘2号线
 *              Transfer…    Transfer…
 *                                    图例：◀当前站 ○站点 ⊚换乘站 ⊙始终站 ▉前方到站 ▉驶过车站 [3号线]
 * </pre>
 * 图例固定钉在玻璃吊板那一侧（不跟着线路图内容漂移），借用了滑动门吊板时也不会跑到门上去。
 */
public final class TrdmRouteMap {

    /** 一条线路（换乘用）：颜色 + 中英文名。 */
    public record Line(int color, String chineseName, String englishName) {
    }

    /** 一个站：中英文名 + 该站可换乘的线路（空表＝普通站）。 */
    public record Station(String chinese, String english, List<Line> interchange) {
    }

    /**
     * 整块吊板的数据。
     *
     * @param currentIndex 本站在线路里的序号
     * @param arrowLeft    行车方向是否朝左（朝左时整张线路图镜像，箭头贴左端）
     */
    public record Model(List<Station> stations, int currentIndex, int routeColor,
                        String routeChinese, String routeEnglish, boolean arrowLeft) {
    }

    // ===== 版面比例（相对吊板高度 H）=====

    private static final float LINE_Y = 0.4852F;
    private static final float LINE_THICKNESS = 0.0562F;
    private static final float MARKER = 0.0827F;
    private static final float ARROW_WIDTH = 0.148F;
    private static final float ARROW_HEIGHT = 0.099F;
    /** 箭头不透明部分靠里的一边，距端头站圆心的距离（× 站点直径）。 */
    private static final float ARROW_GAP = 0.858F;
    /** 站名起点在圆心上方多少（× 站点直径）。 */
    private static final float NAME_ANCHOR = 0.62F;
    private static final float NAME_CN = 0.0562F;
    private static final float NAME_EN = 0.0378F;
    private static final double NAME_ANGLE = Math.toRadians(31.0);
    private static final float NAME_LINE_GAP = 0.0148F;
    private static final float NAME_TOP_MARGIN = 0.02F;
    /** 站名斜着伸出去那一侧要留的横向空间。 */
    private static final float NAME_SIDE_RESERVE = 0.30F;
    /** 另一侧只留字头。 */
    private static final float NAME_BLEED = 0.055F;
    /** 固定站距 —— 吊板再宽也不变，这是「不拉伸」的关键。 */
    private static final float PITCH = 0.285F;
    /** 站距允许放大到的上限：吊板比线路图需要的更宽时，最多摊到这么宽就不再撑（约 3 站/格）。 */
    private static final float MAX_PITCH = 0.5F;
    private static final float FLAG_WIDTH = 0.0429F;
    private static final float FLAG_LENGTH = 0.1256F;
    /** 尖旗尾部伸进换乘圈里的长度（× 站点直径），这样旗子像是从圈后面挂出来的。 */
    private static final float FLAG_INTO_MARKER = 0.18F;
    private static final float TRANSFER_CN = 0.0378F;
    private static final float TRANSFER_EN = 0.030F;
    private static final float TRANSFER_GAP = 0.012F;
    private static final float TRANSFER_LINE_GAP = 0.004F;
    private static final float TRANSFER_GROUP_GAP = 0.018F;
    private static final float LEGEND_CN = 0.049F;
    private static final float LEGEND_ICON = 0.0715F;
    private static final float LEGEND_SWATCH_WIDTH = 0.143F;
    private static final float LEGEND_SWATCH_HEIGHT = 0.0511F;
    private static final float LEGEND_ICON_GAP = 0.012F;
    private static final float LEGEND_ITEM_GAP = 0.032F;
    private static final float LEGEND_CENTER_FROM_BOTTOM = 0.133F;
    /** 图例自己至少要这么宽（再窄就只能缩图例了）。 */
    private static final float LEGEND_MIN_WIDTH = 2.05F;
    private static final float SIDE_MARGIN = 0.012F;
    private static final int TEXT_COLOR = 0xFF000000;
    private static final int TRAVELLED_COLOR = 0xFFA0A0A0;

    private TrdmRouteMap() {
    }

    /**
     * 按给定站距算这块线路图需要多宽（宽高比）。
     */
    public static float aspectAt(int stationCount, boolean arrowLeft, float pitch) {
        final boolean mirrored = !arrowLeft;
        final float overhang = MARKER * ARROW_GAP + ARROW_WIDTH;
        final float leftExtent = (arrowLeft ? overhang : 0F) + (mirrored ? NAME_SIDE_RESERVE : NAME_BLEED);
        final float rightExtent = (arrowLeft ? 0F : overhang) + (mirrored ? NAME_BLEED : NAME_SIDE_RESERVE);
        final float content = leftExtent + rightExtent + pitch * Math.max(0, stationCount - 1);
        return Math.max(content, LEGEND_MIN_WIDTH);
    }

    /** 信息完整所需的最小宽度（站距压到 {@link #PITCH} 时）。比这窄就得缩字号了。 */
    public static float minimumAspect(int stationCount, boolean arrowLeft) {
        return aspectAt(stationCount, arrowLeft, PITCH);
    }

    /** 站距放到上限时的宽度；吊板比这还宽，线路图就居中、两边留空。 */
    public static float maximumAspect(int stationCount, boolean arrowLeft) {
        return aspectAt(stationCount, arrowLeft, MAX_PITCH);
    }

    /**
     * 画一整块线路图吊板。
     *
     * @param runLeftFraction  这块贴图左边有多少是「借用」来的滑动门吊板（0~1）
     * @param runRightFraction 右边同理；图例钉在玻璃吊板那一侧，不钉在借用来的位置
     */
    public static BufferedImage render(int width, int height, Model model,
                                       float runLeftFraction, float runRightFraction) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            draw(graphics, width, height, model, runLeftFraction, runRightFraction);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public static void draw(Graphics2D graphics, int width, int height, Model model,
                            float runLeftFraction, float runRightFraction) {
        final List<Station> stations = model.stations();
        final int count = stations.size();
        if (count < 1) {
            return;
        }

        final float h = height;
        final boolean arrowLeft = model.arrowLeft();
        final boolean mirrored = !arrowLeft;

        // 白底只铺「这块玻璃吊板」的范围：借用进来的滑动门那段留透明，免得把门的吊板盖成一片白
        final float runLeftEdge = width * clamp01(runLeftFraction);
        final float runRightEdge = width - width * clamp01(runRightFraction);
        graphics.setColor(new Color(0xFFFFFFFF, true));
        graphics.fillRect(Math.round(runLeftEdge), 0, Math.max(1, Math.round(runRightEdge - runLeftEdge)), height);

        final float lineY = h * LINE_Y;
        final int lineThickness = Math.max(2, Math.round(h * LINE_THICKNESS));
        final int lineTop = Math.round(lineY - lineThickness / 2F);
        final int lineBottom = lineTop + lineThickness;
        final float marker = h * MARKER;
        final float arrowInkWidth = h * ARROW_WIDTH;
        final float arrowInkHeight = h * ARROW_HEIGHT;
        final float arrowInset = marker * ARROW_GAP;
        final float overhang = arrowInset + arrowInkWidth;
        final int currentIndex = Math.max(0, Math.min(count - 1, model.currentIndex()));

        // ===== 站距：固定比例，吊板更宽时最多摊到 MAX_PITCH，整组水平居中 =====
        final float leftExtent = (arrowLeft ? overhang : 0F) + h * (mirrored ? NAME_SIDE_RESERVE : NAME_BLEED);
        final float rightExtent = (arrowLeft ? 0F : overhang) + h * (mirrored ? NAME_BLEED : NAME_SIDE_RESERVE);
        final float available = Math.max(1F, width - leftExtent - rightExtent);
        final float fitPitch = count > 1 ? available / (count - 1) : 0F;
        final float pitch = count > 1 ? Math.min(fitPitch, h * MAX_PITCH) : 0F;
        // 比固定站距还挤的时候（吊板太窄又借不到门）字号跟着缩，别让字压在一起
        final float squeeze = fitPitch < h * PITCH && h * PITCH > 0 ? fitPitch / (h * PITCH) : 1F;
        final float groupWidth = pitch * (count - 1);
        final float offset = Math.max(0F, (width - (leftExtent + groupWidth + rightExtent)) / 2F);
        final float groupLeft = offset + leftExtent;

        final float[] xs = new float[count];
        for (int i = 0; i < count; i++) {
            // 前方永远是「箭头那一侧」，即序号大的那边
            xs[i] = arrowLeft ? groupLeft + groupWidth - pitch * i : groupLeft + pitch * i;
        }

        final float frontX = xs[count - 1];
        final float arrowBaseX = arrowLeft ? frontX - arrowInset : frontX + arrowInset;

        // ===== 线路 =====
        graphics.setColor(new Color(TRAVELLED_COLOR, true));
        fillSpan(graphics, xs[currentIndex], xs[0], lineTop, lineThickness);
        graphics.setColor(new Color(model.routeColor() | 0xFF000000, true));
        fillSpan(graphics, xs[currentIndex], arrowBaseX, lineTop, lineThickness);

        // ===== 方向箭头（底边与线路下沿对齐）=====
        final TrdmSignAssets.Sprite arrow = TrdmSignAssets.tintedSprite(
                TrdmSignAssets.ROUTE_LINE_ARROW, model.routeColor(), !arrowLeft);
        if (arrow != null) {
            final float inkLeft = arrowLeft ? arrowBaseX - arrowInkWidth : arrowBaseX;
            TrdmSignAssets.drawSpriteInk(graphics, arrow, inkLeft, lineBottom - arrowInkHeight, arrowInkWidth, arrowInkHeight);
        }

        // ===== 站名（斜排，中文在上、英文在下；整张图镜像时跟着往另一侧斜）=====
        final float nameScale = fitNameScale(graphics, h, stations, lineY, marker) * Math.min(1F, squeeze);
        drawStationNames(graphics, h, stations, xs, lineY, marker, nameScale, mirrored);

        // ===== 换乘尖旗 + 文字（旗子尾巴伸进圆圈里，所以先画旗、后画站点图标）=====
        for (int i = 0; i < count; i++) {
            final List<Line> interchange = stations.get(i).interchange();
            if (interchange.isEmpty()) {
                continue;
            }
            drawInterchange(graphics, h, xs[i], lineY + marker / 2F, marker * FLAG_INTO_MARKER, pitch, interchange);
        }

        // ===== 站点图标（换乘圈按线路色重上色）=====
        final List<BufferedImage> usedTransferIcons = new ArrayList<>();
        final Set<String> seenIcons = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            final Station station = stations.get(i);
            final BufferedImage markerImage = markerImage(station, i, count, currentIndex, arrowLeft, model.routeColor());
            TrdmSignAssets.drawSprite(graphics, markerImage, xs[i], lineY, marker);

            if (!station.interchange().isEmpty() && markerImage != null) {
                final String key = (station.interchange().size() > 1 ? "3" : "2")
                        + Arrays.toString(arcColors(model.routeColor(), station.interchange()));
                if (seenIcons.add(key)) {
                    usedTransferIcons.add(markerImage);
                }
            }
        }

        // ===== 图例（钉在玻璃吊板右下角）=====
        drawLegend(graphics, h, model, usedTransferIcons, runLeftEdge, runRightEdge);
    }

    private static float clamp01(float value) {
        return Math.max(0F, Math.min(1F, value));
    }

    private static void fillSpan(Graphics2D graphics, float a, float b, int top, int height) {
        final int x1 = Math.round(Math.min(a, b));
        final int x2 = Math.round(Math.max(a, b));
        if (x2 > x1) {
            graphics.fillRect(x1, top, x2 - x1, height);
        }
    }

    // ===== 站名 =====

    /**
     * 定站名字号：既要塞进「线路到吊板上沿」，又要塞进横向的名字留白，取两者更小的那个。
     */
    private static float fitNameScale(Graphics2D graphics, float h, List<Station> stations, float lineY, float marker) {
        final float roomVertical = lineY - marker * NAME_ANCHOR - h * NAME_TOP_MARGIN;
        final float roomHorizontal = h * NAME_SIDE_RESERVE;
        float scale = 1F;
        for (int iteration = 0; iteration < 5; iteration++) {
            final float cnSize = h * NAME_CN * scale;
            final float enSize = h * NAME_EN * scale;
            final Font cnFont = TrdmHeaderTextures.font(cnSize);
            final Font enFont = TrdmHeaderTextures.latinFont(enSize);
            final float blockHeight = graphics.getFontMetrics(enFont).getAscent() + h * NAME_LINE_GAP * scale + cnSize;
            float longest = 0;
            for (final Station station : stations) {
                longest = Math.max(longest, stringWidth(graphics, station.chinese(), cnFont));
                longest = Math.max(longest, stringWidth(graphics, station.english(), enFont));
            }
            final float along = longest * (float) Math.cos(NAME_ANGLE) + blockHeight * (float) Math.sin(NAME_ANGLE);
            final float perpendicular = longest * (float) Math.sin(NAME_ANGLE) + blockHeight * (float) Math.cos(NAME_ANGLE);
            float factor = 1F;
            if (perpendicular > roomVertical && perpendicular > 0) {
                factor = Math.min(factor, roomVertical / perpendicular);
            }
            if (along > roomHorizontal && along > 0) {
                factor = Math.min(factor, roomHorizontal / along);
            }
            if (factor >= 0.995F) {
                break;
            }
            scale *= Math.max(0.25F, factor * 0.98F);
        }
        return scale;
    }

    private static void drawStationNames(Graphics2D graphics, float h, List<Station> stations, float[] xs,
                                        float lineY, float marker, float scale, boolean mirrored) {
        final float anchorOffset = marker * NAME_ANCHOR;
        final float cnSize = h * NAME_CN * scale;
        final float enSize = h * NAME_EN * scale;
        final Font cnFont = TrdmHeaderTextures.font(cnSize);
        final Font enFont = TrdmHeaderTextures.latinFont(enSize);
        final FontMetrics enMetrics = graphics.getFontMetrics(enFont);
        final float cnBaselineOffset = enMetrics.getAscent() + h * NAME_LINE_GAP * scale;

        graphics.setColor(new Color(TEXT_COLOR, true));
        for (int i = 0; i < stations.size(); i++) {
            final Station station = stations.get(i);
            final float anchorX = xs[i];
            final float anchorY = lineY - anchorOffset;
            final java.awt.geom.AffineTransform old = graphics.getTransform();
            if (mirrored) {
                // 镜像：站名贴着圆心往左上斜，文字仍然正着读（右对齐到圆心）
                graphics.rotate(NAME_ANGLE, anchorX, anchorY);
                final float chineseWidth = stringWidth(graphics, station.chinese(), cnFont);
                final float englishWidth = stringWidth(graphics, station.english(), enFont);
                if (!station.chinese().isEmpty()) {
                    drawBold(graphics, station.chinese(), anchorX - chineseWidth, anchorY - cnBaselineOffset, cnFont);
                }
                if (!station.english().isEmpty()) {
                    drawBold(graphics, station.english(), anchorX - englishWidth, anchorY, enFont);
                }
            } else {
                // 参考图的排法：站名从圆心往右上斜
                graphics.rotate(-NAME_ANGLE, anchorX, anchorY);
                if (!station.chinese().isEmpty()) {
                    drawBold(graphics, station.chinese(), anchorX, anchorY - cnBaselineOffset, cnFont);
                }
                if (!station.english().isEmpty()) {
                    drawBold(graphics, station.english(), anchorX, anchorY, enFont);
                }
            }
            graphics.setTransform(old);
        }
    }

    /**
     * 参考图里的站名比 SimHei 常规体要粗，AWT 对没有粗体字重的字体不会合成加粗，
     * 这里用一次轻微横向重描模拟。
     */
    private static void drawBold(Graphics2D graphics, String text, float x, float baseline, Font font) {
        graphics.setFont(font);
        graphics.drawString(text, x, baseline);
        graphics.drawString(text, x + Math.max(0.6F, font.getSize2D() * 0.022F), baseline);
    }

    // ===== 站点图标 =====

    /**
     * 站点图标的优先级：换乘站 &gt; 当前站 &gt; 始终站 &gt; 普通站。
     */
    private static BufferedImage markerImage(Station station, int index, int count, int currentIndex,
                                             boolean arrowLeft, int routeColor) {
        if (!station.interchange().isEmpty()) {
            final String spriteName = station.interchange().size() > 1
                    ? TrdmSignAssets.TRANSFER_3 : TrdmSignAssets.TRANSFER_2;
            // 圆弧按「本站线路 + 各换乘线路」的线路色重新上色
            final BufferedImage recolored = TrdmSignAssets.recolored(spriteName, arcColors(routeColor, station.interchange()));
            if (recolored != null) {
                return recolored;
            }
            return imageOf(spriteName, false);
        }
        if (index == currentIndex) {
            // 素材的箭头朝左：行车方向朝右时镜像
            return imageOf(TrdmSignAssets.CURRENT_STATION, !arrowLeft);
        }
        if (index == 0 || index == count - 1) {
            return imageOf(TrdmSignAssets.HEAD_TAIL, false);
        }
        return imageOf(TrdmSignAssets.STATION_CIRCLE, false);
    }

    /**
     * 换乘圈的每段圆弧对应哪个颜色：第一段给本站线路色，其余按换乘线路顺序给。
     */
    private static int[] arcColors(int routeColor, List<Line> interchange) {
        final int[] colors = new int[interchange.size() + 1];
        colors[0] = routeColor;
        for (int i = 0; i < interchange.size(); i++) {
            colors[i + 1] = interchange.get(i).color();
        }
        return colors;
    }

    // ===== 换乘 =====

    private static void drawInterchange(Graphics2D graphics, float h, float centerX, float markerBottomY,
                                       float overlap, float pitch, List<Line> interchange) {
        final float flagWidth = h * FLAG_WIDTH;
        // 尾巴往圆圈里伸进去一点（旗先画、圈后画，尾巴就被圆圈盖住了）
        final float flagTop = markerBottomY - overlap;
        final float flagBottom = markerBottomY + h * FLAG_LENGTH;
        final float band = flagWidth / interchange.size();

        // 一个旗：几条换乘线就竖着平均劈几段（共用同一个旗尖，不是几条各自一个三角）
        final float flagLeft = centerX - flagWidth / 2F;
        final java.awt.Shape oldClip = graphics.getClip();
        for (int i = 0; i < interchange.size(); i++) {
            final BufferedImage flag = TrdmSignAssets.tinted(TrdmSignAssets.TRANSFER_LABEL, interchange.get(i).color());
            if (flag == null) {
                continue;
            }
            final int bandX0 = Math.round(flagLeft + band * i);
            // 最后一段不多让一格，中间段多让 1 像素免得相邻色块之间留缝
            final int bandX1 = Math.round(flagLeft + band * (i + 1)) + (i == interchange.size() - 1 ? 0 : 1);
            graphics.setClip(oldClip);
            graphics.clipRect(bandX0, Math.round(flagTop), Math.max(1, bandX1 - bandX0),
                    Math.max(1, Math.round(flagBottom - flagTop) + 1));
            graphics.drawImage(flag,
                    Math.round(flagLeft), Math.round(flagTop),
                    Math.round(flagLeft + flagWidth), Math.round(flagBottom),
                    0, 0, flag.getWidth(), flag.getHeight(), null);
        }
        graphics.setClip(oldClip);

        // 文字：「换乘X号线」+「Transfer to Line X」，多条线路时上下堆叠
        final List<String> chineseLines = new ArrayList<>();
        final List<String> englishLines = new ArrayList<>();
        float cnSize = h * TRANSFER_CN;
        float enSize = h * TRANSFER_EN;
        for (final Line line : interchange) {
            chineseLines.add(transferChinese(line));
            englishLines.add(transferEnglish(line));
        }
        float widest = 0;
        for (int i = 0; i < chineseLines.size(); i++) {
            widest = Math.max(widest, stringWidth(graphics, chineseLines.get(i), TrdmHeaderTextures.font(cnSize)));
            widest = Math.max(widest, stringWidth(graphics, englishLines.get(i), TrdmHeaderTextures.latinFont(enSize)));
        }
        if (pitch > 0 && widest > pitch * 0.94F) {
            final float shrink = pitch * 0.94F / widest;
            cnSize *= shrink;
            enSize *= shrink;
        }

        final Font cnFont = TrdmHeaderTextures.font(cnSize);
        final Font enFont = TrdmHeaderTextures.latinFont(enSize);
        final FontMetrics cnMetrics = graphics.getFontMetrics(cnFont);
        final FontMetrics enMetrics = graphics.getFontMetrics(enFont);

        graphics.setColor(new Color(TEXT_COLOR, true));
        float y = flagBottom + h * TRANSFER_GAP;
        for (int i = 0; i < chineseLines.size(); i++) {
            final String chinese = chineseLines.get(i);
            final String english = englishLines.get(i);
            final float chineseBaseline = y + cnMetrics.getAscent();
            graphics.setFont(cnFont);
            graphics.drawString(chinese, centerX - stringWidth(graphics, chinese, cnFont) / 2F, chineseBaseline);
            y = chineseBaseline + h * TRANSFER_LINE_GAP;
            if (!english.isEmpty()) {
                final float englishBaseline = y + enMetrics.getAscent();
                graphics.setFont(enFont);
                graphics.drawString(english, centerX - stringWidth(graphics, english, enFont) / 2F, englishBaseline);
                y = englishBaseline;
            }
            y += h * TRANSFER_GROUP_GAP;
        }
    }

    private static String transferChinese(Line line) {
        final String name = line.chineseName() == null ? "" : line.chineseName().trim();
        if (name.isEmpty()) {
            return "换乘" + lineNumberOf(line) + "号线";
        }
        return name.startsWith("换乘") ? name : "换乘" + name;
    }

    private static String transferEnglish(Line line) {
        final String name = line.englishName() == null ? "" : line.englishName().trim();
        if (name.isEmpty()) {
            return "Transfer to Line " + lineNumberOf(line);
        }
        return name.startsWith("Transfer") ? name : "Transfer to " + name;
    }

    private static String lineNumberOf(Line line) {
        return digits(line.chineseName()) + digits(line.englishName());
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

    private static void drawLegend(Graphics2D graphics, float h, Model model, List<BufferedImage> transferIcons,
                                   float runLeft, float runRight) {
        float scale = 1F;
        final float available = Math.max(1F, runRight - runLeft - h * 0.05F);
        for (int iteration = 0; iteration < 4; iteration++) {
            final float total = legendRow(graphics, 0, 0, h, scale, model, transferIcons, false);
            if (total <= available || total <= 0) {
                break;
            }
            scale *= Math.max(0.3F, available / total * 0.98F);
        }

        final float centerY = h - h * LEGEND_CENTER_FROM_BOTTOM;
        final float total = legendRow(graphics, 0, centerY, h, scale, model, transferIcons, false);
        // 图例固定在吊板右下角（不跟着线路图镜像）
        final float startX = Math.max(runLeft, runRight - h * 0.02F - total);
        legendRow(graphics, startX, centerY, h, scale, model, transferIcons, true);
    }

    private static float legendRow(Graphics2D graphics, float startX, float centerY, float h, float scale,
                                   Model model, List<BufferedImage> transferIcons, boolean draw) {
        final float icon = h * LEGEND_ICON * scale;
        final float em = h * LEGEND_CN * scale;
        final float swatchWidth = h * LEGEND_SWATCH_WIDTH * scale;
        final float swatchHeight = h * LEGEND_SWATCH_HEIGHT * scale;
        final float iconGap = h * LEGEND_ICON_GAP * scale;
        final float itemGap = h * LEGEND_ITEM_GAP * scale;
        final Font labelFont = TrdmHeaderTextures.font(em);
        final FontMetrics metrics = graphics.getFontMetrics(labelFont);
        final float labelBaseline = centerY + metrics.getAscent() * 0.36F;

        float x = startX;
        // 「图例：」
        final String prefix = "图例：";
        if (draw) {
            graphics.setColor(new Color(TEXT_COLOR, true));
            graphics.setFont(labelFont);
            graphics.drawString(prefix, startX, labelBaseline);
        }
        x += stringWidth(graphics, prefix, labelFont) + iconGap;

        // 当前站 / 站点 / 换乘站（用线路图里真正用到的圆圈）/ 始终站
        x = drawSpriteItem(graphics, x, centerY, icon, iconGap, itemGap, labelFont, metrics, labelBaseline,
                imageOf(TrdmSignAssets.CURRENT_STATION, !model.arrowLeft()), "当前站", draw);
        x = drawSpriteItem(graphics, x, centerY, icon, iconGap, itemGap, labelFont, metrics, labelBaseline,
                imageOf(TrdmSignAssets.STATION_CIRCLE, false), "站点", draw);

        final List<BufferedImage> icons = transferIcons.isEmpty()
                ? List.of(imageOf(TrdmSignAssets.TRANSFER_2, false))
                : transferIcons;
        if (draw) {
            for (int i = 0; i < icons.size(); i++) {
                TrdmSignAssets.drawSprite(graphics, icons.get(i), x + icon * (i + 0.5F), centerY, icon);
            }
        }
        x += icon * icons.size();
        x = drawLabel(graphics, x + iconGap, iconGap, itemGap, labelFont, metrics, labelBaseline, "换乘站", draw);

        x = drawSpriteItem(graphics, x, centerY, icon, iconGap, itemGap, labelFont, metrics, labelBaseline,
                imageOf(TrdmSignAssets.HEAD_TAIL, false), "始终站", draw);

        // 前方到站（线路色）/ 驶过车站（灰）
        x = drawSwatchItem(graphics, x, centerY, swatchWidth, swatchHeight, iconGap, itemGap, labelFont, metrics,
                labelBaseline, model.routeColor(), "前方到站", draw);
        x = drawSwatchItem(graphics, x, centerY, swatchWidth, swatchHeight, iconGap, itemGap, labelFont, metrics,
                labelBaseline, TRAVELLED_COLOR, "驶过车站", draw);

        // 线路标志
        final float chip = icon * 0.8F;
        final Badge routeBadge = new Badge(model.routeColor(), model.routeChinese(), model.routeEnglish());
        final float chipWidth = TrdmHeaderTextures.badgeWidth(graphics, chip, routeBadge);
        if (draw) {
            TrdmHeaderTextures.drawBadge(graphics, Math.round(x), Math.round(centerY - chip / 2F),
                    Math.round(chipWidth), Math.round(chip), routeBadge, 0F);
        }
        x += chipWidth;
        return x - startX;
    }

    private static float drawSpriteItem(Graphics2D graphics, float x, float centerY, float icon, float iconGap,
                                        float itemGap, Font labelFont, FontMetrics metrics, float labelBaseline,
                                        BufferedImage image, String label, boolean draw) {
        if (draw) {
            TrdmSignAssets.drawSprite(graphics, image, x + icon / 2F, centerY, icon);
        }
        return drawLabel(graphics, x + icon + iconGap, iconGap, itemGap, labelFont, metrics, labelBaseline, label, draw);
    }

    private static float drawLabel(Graphics2D graphics, float x, float iconGap, float itemGap, Font labelFont,
                                   FontMetrics metrics, float labelBaseline, String label, boolean draw) {
        if (draw) {
            graphics.setColor(new Color(TEXT_COLOR, true));
            graphics.setFont(labelFont);
            graphics.drawString(label, x, labelBaseline);
        }
        return x + stringWidth(graphics, label, labelFont) + itemGap;
    }

    private static float drawSwatchItem(Graphics2D graphics, float x, float centerY, float swatchWidth,
                                        float swatchHeight, float iconGap, float itemGap, Font labelFont,
                                        FontMetrics metrics, float labelBaseline, int color, String label, boolean draw) {
        if (draw) {
            final BufferedImage swatch = TrdmSignAssets.tinted(TrdmSignAssets.COLOR_LINE, color);
            if (swatch != null) {
                graphics.drawImage(swatch,
                        Math.round(x), Math.round(centerY - swatchHeight / 2F),
                        Math.round(x + swatchWidth), Math.round(centerY + swatchHeight / 2F),
                        0, 0, swatch.getWidth(), swatch.getHeight(), null);
            }
        }
        return drawLabel(graphics, x + swatchWidth + iconGap, iconGap, itemGap, labelFont, metrics, labelBaseline, label, draw);
    }

    private static BufferedImage imageOf(String spriteName, boolean mirror) {
        final TrdmSignAssets.Sprite sprite = TrdmSignAssets.sprite(spriteName, mirror);
        return sprite == null ? null : sprite.image();
    }

    private static float stringWidth(Graphics2D graphics, String text, Font font) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        graphics.setFont(font);
        return graphics.getFontMetrics(font).stringWidth(text);
    }
}
