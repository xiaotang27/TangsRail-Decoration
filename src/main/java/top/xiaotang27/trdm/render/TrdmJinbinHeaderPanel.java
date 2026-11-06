package top.xiaotang27.trdm.render;

import top.xiaotang27.trdm.control.PsdStyles;
import top.xiaotang27.trdm.render.TrdmHeaderTextures.Badge;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 津滨轻轨风格站名吊板的 AWT 排版。
 * <p>
 * 参考图是 Frame 88–93：白底、底部一条细蓝线，站名居中，左下角是「箭头 + 线路牌」，
 * 右侧一排「开往 / 下一站 / 上一站」。两种样式的差别：
 * <ul>
 *     <li>{@link PsdStyles#HEADER_JINBIN}：左下线路牌是「大数字 + 号线 / Line N」，
 *     换乘站的方形线路标志在左上角（Frame 89）；英文站名保持 MTR 原样；</li>
 *     <li>{@link PsdStyles#HEADER_JINBIN_RETROFIT}：左下线路牌是「9号线 / Line 9」两行，
 *     换乘站左右各一个方形线路标志（Frame 93）；不显示「上一站」；英文站名同样保持原样。</li>
 * </ul>
 * 颜色：线路牌、箭头、底部细线都跟随线路色；顶部方形标志沿用现有
 * {@link TrdmHeaderTextures#drawBadge} 的绘制方式（也是线路色）。
 */
public final class TrdmJinbinHeaderPanel {

    private static final Pattern ROUTE_NUMBER_PATTERN = Pattern.compile("^\\s*(\\d+)\\s*(.*)$");

    private static final int TEXT_COLOR = 0xFF000000;

    // ===== 参考图（2718×675）上的比例 =====

    /** 站名中文墨高 / 板高。 */
    private static final float NAME_CN_HEIGHT = 149F / 675F;
    /** 站名英文墨高 / 板高（bmt）。 */
    private static final float NAME_EN_HEIGHT_BMT = 60F / 675F;
    /** 站名英文墨高 / 板高（改造）。 */
    private static final float NAME_EN_HEIGHT_TRT = 48F / 675F;
    /** 站名中文基线 / 板高。 */
    private static final float NAME_CN_BASELINE = 307F / 675F;
    /** 站名英文基线 / 板高（bmt / 改造）。 */
    private static final float NAME_EN_BASELINE_BMT = 381F / 675F;
    private static final float NAME_EN_BASELINE_TRT = 375F / 675F;

    /** 底部信息行的中文墨高 / 板高。 */
    private static final float INFO_CN_HEIGHT = 59F / 675F;
    /** bmt 底部英文墨高 / 板高。 */
    private static final float INFO_EN_HEIGHT_BMT = 30F / 675F;
    /** 改造版底部英文墨高 / 板高。 */
    private static final float INFO_EN_HEIGHT_TRT = 22F / 675F;

    /** 左下线路牌宽、高（相对板高）。 */
    private static final float BADGE_WIDTH = 176F / 675F;
    private static final float BADGE_HEIGHT = 88F / 675F;
    /** bmt / 改造线路牌顶边（相对板高）。 */
    private static final float BADGE_TOP_BMT = 493F / 675F;
    private static final float BADGE_TOP_TRT = 502F / 675F;
    /** 箭头宽、高（相对板高，素材 177×48）。 */
    private static final float ARROW_WIDTH = 177F / 675F;
    private static final float ARROW_HEIGHT_BMT = 45F / 675F;
    private static final float ARROW_HEIGHT_TRT = 43F / 675F;
    /** 箭头底边到线路牌顶边的留白 / 板高。 */
    private static final float ARROW_GAP = 19F / 675F;

    /** bmt 左边距 / 板宽；改造版线路牌起点 / 板宽。 */
    private static final float MARGIN_X = 66F / 2718F;
    private static final float BADGE_X_TRT = 468F / 2718F;
    /** 左下线路牌到「开往」的间距 / 板高。 */
    private static final float BADGE_TO_DEST = 32F / 675F;
    /** 目的地与下一站之间的竖线。 */
    private static final float SEPARATOR_WIDTH = 6F / 675F;
    private static final float SEPARATOR_GAP = 27F / 675F;
    /** 下一站块起点的参考位置（相对板宽，bmt / 改造）。 */
    private static final float NEXT_X_BMT = 689F / 2718F;
    private static final float NEXT_X_TRT = 1683F / 2718F;
    /** 上一站块起点的参考位置（相对板宽，bmt）。 */
    private static final float PREV_X_BMT = 1695F / 2718F;

    /** 换乘站方形线路标志边长 / 板高。 */
    private static final float TOP_BADGE_SIZE = 222F / 675F;
    /** bmt 左上标志位置（相对板宽 / 板高）。 */
    private static final float TOP_BADGE_X_BMT = 406F / 2718F;
    private static final float TOP_BADGE_Y = 154F / 675F;
    /** 改造版左右标志的左右边距（相对板宽）。 */
    private static final float TOP_BADGE_MARGIN_TRT = 476F / 2718F;

    /** 底部细线高度 / 板高。 */
    private static final float STRIPE_HEIGHT = 19F / 675F;

    private TrdmJinbinHeaderPanel() {
    }

    /**
     * 画一块津滨轻轨风格站名板（含站名、线路牌、开往 / 下一站 / 上一站）。
     *
     * @param style             {@link PsdStyles#HEADER_JINBIN} 或 {@link PsdStyles#HEADER_JINBIN_RETROFIT}
     * @param stationName       MTR 的「中文|English」
     * @param routeBadge        本站台当前线路的标志（左下线路牌 + 顶部方形标志都用它）
     * @param interchange       本站是不是换乘站；只有换乘站才画顶部方形线路标志
     * @param destination       终点站（MTR 的「中文|English」）
     * @param nextStation       下一站（MTR 的「中文|English」；终点站时可空）
     * @param previousStation   上一站（可为空；改造版不画）
     * @param terminal          本站是不是线路终点站
     * @param arrowLeft         箭头是否朝左（版面镜像）
     */
    public static BufferedImage render(int width, int height, int style, String stationName, Badge routeBadge,
                                       boolean interchange, String destination, String nextStation,
                                       String previousStation, boolean terminal, boolean arrowLeft) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            draw(graphics, width, height, style, stationName, routeBadge, interchange, destination, nextStation,
                    previousStation, terminal, arrowLeft);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    public static void draw(Graphics2D graphics, int width, int height, int style, String stationName,
                            Badge routeBadge, boolean interchange, String destination, String nextStation,
                            String previousStation, boolean terminal, boolean arrowLeft) {
        final boolean retrofit = style == PsdStyles.HEADER_JINBIN_RETROFIT;
        final int routeColor = routeBadge == null ? 0x0032D6 : routeBadge.color();
        final String[] name = splitName(stationName);
        final String[] dest = splitName(destination);
        final String[] next = splitName(nextStation);
        final String[] prev = splitName(previousStation);

        // ===== 白底 + 底部细线 =====
        graphics.setColor(new Color(0xFFFFFFFF, true));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(new Color(routeColor | 0xFF000000, true));
        graphics.fillRect(0, Math.round(height * (1F - STRIPE_HEIGHT)), width,
                Math.max(1, Math.round(height * STRIPE_HEIGHT)));

        // ===== 站名 =====
        final float nameCnHeight = height * NAME_CN_HEIGHT;
        final float nameEnHeight = height * (retrofit ? NAME_EN_HEIGHT_TRT : NAME_EN_HEIGHT_BMT);
        final Font nameCnFont = fitFont(graphics, name[0], true, nameCnHeight, width * 0.82F);
        final Font nameEnFont = fitFont(graphics, name[1], false, nameEnHeight, width * 0.82F);
        drawCenteredAtBaseline(graphics, name[0], nameCnFont, width / 2F,
                height * NAME_CN_BASELINE, true);
        drawCenteredAtBaseline(graphics, name[1], nameEnFont, width / 2F,
                height * (retrofit ? NAME_EN_BASELINE_TRT : NAME_EN_BASELINE_BMT), false);

        // ===== 换乘站的顶部方形线路标志 =====
        if (interchange && routeBadge != null) {
            final int topBadgeSize = Math.max(8, Math.round(height * TOP_BADGE_SIZE));
            final int topBadgeY = Math.round(height * TOP_BADGE_Y);
            if (retrofit) {
                final int left = Math.round(width * TOP_BADGE_MARGIN_TRT);
                final int right = width - left - topBadgeSize;
                TrdmHeaderTextures.drawBadge(graphics, left, topBadgeY, topBadgeSize, topBadgeSize, routeBadge, 0F);
                TrdmHeaderTextures.drawBadge(graphics, right, topBadgeY, topBadgeSize, topBadgeSize, routeBadge, 0F);
            } else {
                final int x = arrowLeft
                        ? Math.round(width * TOP_BADGE_X_BMT)
                        : width - Math.round(width * TOP_BADGE_X_BMT) - topBadgeSize;
                TrdmHeaderTextures.drawBadge(graphics, x, topBadgeY, topBadgeSize, topBadgeSize, routeBadge, 0F);
            }
        }

        // ===== 底部信息行 =====
        final float infoCnHeight = height * INFO_CN_HEIGHT;
        final float infoEnHeight = height * (retrofit ? INFO_EN_HEIGHT_TRT : INFO_EN_HEIGHT_BMT);
        final float destCnBaseline = height * (retrofit ? 535F / 675F : 527F / 675F);
        final float nextCnBaseline = height * 527F / 675F;
        final float prevCnBaseline = height * 533F / 675F;
        final float destEnBaseline = height * (retrofit ? 573F / 675F : 571F / 675F);
        final float nextEnBaseline = height * (retrofit ? 563F / 675F : 571F / 675F);
        final float prevEnBaseline = height * 575F / 675F;

        // 目的地：开往 X / To X；终点站时第二块改成「终点站 / Terminal Station」
        final String destLabel = "开往";
        final String destCn = dest[0].isEmpty() ? dest[1] : dest[0];
        final String destEn = dest[0].isEmpty() || dest[1].isEmpty() ? "" : "To " + dest[1];
        final String nextLabel = terminal ? "终点站" : "下一站";
        final String nextCn = terminal ? "" : (next[0].isEmpty() ? next[1] : next[0]);
        final String nextEn = terminal ? "Terminal Station"
                : (next[0].isEmpty() || next[1].isEmpty() ? "" : "Next Station  " + next[1]);
        final String prevLabel = "上一站";
        final String prevCn = prev[0].isEmpty() ? prev[1] : prev[0];
        final String prevEn = prev[0].isEmpty() || prev[1].isEmpty() ? "" : "Previous Station  " + prev[1];

        // 先按参考图比例算字号，再按可用宽度整体缩一次
        Font destCnFont = fitFont(graphics, destLabel + destCn, true, infoCnHeight, width);
        Font destEnFont = fitFont(graphics, destEn, false, infoEnHeight, width);
        Font nextCnFont = fitFont(graphics, nextLabel + nextCn, true, infoCnHeight, width);
        Font nextEnFont = fitFont(graphics, nextEn, false, infoEnHeight, width);
        Font prevCnFont = fitFont(graphics, prevLabel + prevCn, true, infoCnHeight, width);
        Font prevEnFont = fitFont(graphics, prevEn, false, infoEnHeight, width);

        final float badgeW = height * BADGE_WIDTH;
        final float badgeH = height * BADGE_HEIGHT;
        final float arrowW = height * ARROW_WIDTH;
        final float arrowH = height * (retrofit ? ARROW_HEIGHT_TRT : ARROW_HEIGHT_BMT);
        final float badgeTop = height * (retrofit ? BADGE_TOP_TRT : BADGE_TOP_BMT);
        final float marginX = width * MARGIN_X;
        final float badgeX = width * (retrofit ? BADGE_X_TRT : MARGIN_X);
        final float destX = badgeX + badgeW + height * BADGE_TO_DEST;
        final boolean drawPrevious = !retrofit && !prevCn.isEmpty();
        final boolean drawSeparator = !retrofit;

        // 先算一遍，再按「下一站不能压到目的地、整行不能出右边」缩字号
        float shrink = 1F;
        for (int iteration = 0; iteration < 6; iteration++) {
            final float destWidth = blockWidth(graphics, destLabel, destCn, destCnFont, destEn, destEnFont);
            final float nextWidth = blockWidth(graphics, nextLabel, nextCn, nextCnFont, nextEn, nextEnFont);
            final float prevWidth = blockWidth(graphics, prevLabel, prevCn, prevCnFont, prevEn, prevEnFont);
            final float sepX = destX + destWidth + height * SEPARATOR_GAP;
            final float nextX = retrofit
                    ? Math.max(width * NEXT_X_TRT, destX + destWidth + height * 0.05F)
                    : Math.max(sepX + height * SEPARATOR_WIDTH + height * SEPARATOR_GAP, width * NEXT_X_BMT);
            float prevX = Math.max(nextX + nextWidth + height * 0.08F, width * PREV_X_BMT);
            if (drawPrevious && prevX + prevWidth > width - marginX) {
                prevX = Math.max(nextX + nextWidth + height * 0.05F, width - marginX - prevWidth);
            }
            final float contentRight = drawPrevious ? Math.max(nextX + nextWidth, prevX + prevWidth)
                    : nextX + nextWidth;
            final float overlapLimit = nextX - destX - height * 0.02F;
            float factor = 1F;
            if (destWidth > overlapLimit && destWidth > 0F) {
                factor = Math.min(factor, overlapLimit / destWidth);
            }
            if (contentRight > width - marginX) {
                factor = Math.min(factor, (width - marginX - destX) / Math.max(1F, contentRight - destX));
            }
            if (factor >= 0.995F || shrink <= 0.3F) {
                break;
            }
            shrink *= Math.max(0.5F, factor * 0.98F);
            destCnFont = scaleFont(destCnFont, shrink);
            destEnFont = scaleFont(destEnFont, shrink);
            nextCnFont = scaleFont(nextCnFont, shrink);
            nextEnFont = scaleFont(nextEnFont, shrink);
            prevCnFont = scaleFont(prevCnFont, shrink);
            prevEnFont = scaleFont(prevEnFont, shrink);
        }

        // 分块：左侧「箭头 + 线路牌」，目的地、竖线（仅 bmt）、下一站，最后是上一站（仅 bmt）
        final float destWidth = blockWidth(graphics, destLabel, destCn, destCnFont, destEn, destEnFont);
        final float sepX = destX + destWidth + height * SEPARATOR_GAP;
        final float nextX = retrofit
                ? Math.max(width * NEXT_X_TRT, destX + destWidth + height * 0.05F)
                : Math.max(sepX + height * SEPARATOR_WIDTH + height * SEPARATOR_GAP, width * NEXT_X_BMT);
        final float nextWidth = blockWidth(graphics, nextLabel, nextCn, nextCnFont, nextEn, nextEnFont);
        float prevX = Math.max(nextX + nextWidth + height * 0.08F, width * PREV_X_BMT);
        final float prevWidth = blockWidth(graphics, prevLabel, prevCn, prevCnFont, prevEn, prevEnFont);
        if (drawPrevious && prevX + prevWidth > width - marginX) {
            prevX = Math.max(nextX + nextWidth + height * 0.05F, width - marginX - prevWidth);
        }

        // 版面镜像：arrowLeft=false 时整块左右翻，但文字本身不翻
        final float groupX = arrowLeft ? badgeX : width - badgeX - badgeW;
        final float destBoxX = arrowLeft ? destX : width - destX - destWidth;
        final float sepBoxX = arrowLeft ? sepX : width - sepX - height * SEPARATOR_WIDTH;
        final float nextBoxX = arrowLeft ? nextX : width - nextX - nextWidth;
        final float prevBoxX = arrowLeft ? prevX : width - prevX - prevWidth;

        // 左上线路牌 + 箭头
        drawArrowAndBadge(graphics, style, routeBadge, routeColor, groupX, badgeTop, badgeW, badgeH,
                arrowW, arrowH, height * ARROW_GAP, arrowLeft);

        // 开往 / 下一站 / 上一站
        drawInfoBlock(graphics, destBoxX, destWidth, destLabel, destCn, destCnFont, destEn, destEnFont,
                destCnBaseline, destEnBaseline, arrowLeft);
        if (!terminal && !nextCn.isEmpty()) {
            drawInfoBlock(graphics, nextBoxX, nextWidth, nextLabel, nextCn, nextCnFont, nextEn, nextEnFont,
                    nextCnBaseline, nextEnBaseline, arrowLeft);
        } else if (terminal) {
            drawInfoBlock(graphics, nextBoxX, nextWidth, nextLabel, "", nextCnFont, nextEn, nextEnFont,
                    nextCnBaseline, nextEnBaseline, arrowLeft);
        }
        if (drawPrevious) {
            drawInfoBlock(graphics, prevBoxX, prevWidth, prevLabel, prevCn, prevCnFont, prevEn, prevEnFont,
                    prevCnBaseline, prevEnBaseline, arrowLeft);
        }

        // 竖线：只画在「开往」和「下一站」之间（bmt）
        if (drawSeparator) {
            final int sepTop = Math.round(destCnBaseline - height * INFO_CN_HEIGHT);
            final int sepBottom = Math.round(height * 0.845F);
            graphics.setColor(new Color(TEXT_COLOR, true));
            graphics.fillRect(Math.round(sepBoxX), sepTop, Math.max(1, Math.round(height * SEPARATOR_WIDTH)),
                    Math.max(1, sepBottom - sepTop));
        }
    }

    // ===== 左侧箭头 + 线路牌 =====

    private static void drawArrowAndBadge(Graphics2D graphics, int style, Badge badge, int routeColor,
                                          float x, float top, float width, float height,
                                          float arrowWidth, float arrowHeight, float arrowGap, boolean arrowLeft) {
        if (badge != null) {
            drawBadgePanel(graphics, style, badge, routeColor, x, top, width, height);
        }
        final TrdmSignAssets.Sprite arrow = TrdmSignAssets.sprite(TrdmSignAssets.BMT_ARROW, !arrowLeft);
        if (arrow == null) {
            return;
        }
        final float arrowX = arrowLeft ? x + width - arrowWidth : x;
        TrdmSignAssets.drawSpriteInk(graphics, arrow, arrowX, top - arrowHeight - arrowGap, arrowWidth, arrowHeight);
    }

    private static void drawBadgePanel(Graphics2D graphics, int style, Badge badge, int routeColor,
                                       float x, float top, float width, float height) {
        final int fill = routeColor | 0xFF000000;
        graphics.setColor(new Color(fill, true));
        graphics.fillRect(Math.round(x), Math.round(top), Math.round(width), Math.round(height));

        final boolean light = isLightColor(routeColor);
        final int text = light ? 0xFF000000 : 0xFFFFFFFF;
        final String[] route = splitRouteNumber(badge.chineseName());
        final String number = route[0];
        final String suffix = route[1];
        final String english = badge.englishName() == null ? "" : badge.englishName();

        if (style == PsdStyles.HEADER_JINBIN && !number.isEmpty()) {
            // bmt：大数字在左，右侧「号线 / Line N」上下两行
            final float numberHeight = height * 0.72F;
            final Font numberFont = fitFont(graphics, number, false, numberHeight, width * 0.45F);
            final Font suffixFont = fitFont(graphics, suffix, true, height * 0.34F, width * 0.52F);
            final Font englishFont = fitFont(graphics, english, false, height * 0.27F, width * 0.52F);
            final FontMetrics numberMetrics = graphics.getFontMetrics(numberFont);
            final float numberBaseline = top + (height + numberMetrics.getAscent() - numberMetrics.getDescent()) / 2F;
            graphics.setColor(new Color(text, true));
            graphics.setFont(numberFont);
            graphics.drawString(number, x + width * 0.07F, numberBaseline);

            final float columnX = x + width * 0.07F + textWidth(graphics, number, numberFont) + width * 0.05F;
            final FontMetrics suffixMetrics = graphics.getFontMetrics(suffixFont);
            final FontMetrics englishMetrics = graphics.getFontMetrics(englishFont);
            final float gap = height * 0.03F;
            float cursor = top + (height - suffixMetrics.getAscent() - gap - englishMetrics.getAscent()) / 2F;
            graphics.setFont(suffixFont);
            graphics.drawString(suffix, columnX, cursor + suffixMetrics.getAscent());
            cursor += suffixMetrics.getAscent() + gap;
            graphics.setFont(englishFont);
            graphics.drawString(english, columnX, cursor + englishMetrics.getAscent());
            return;
        }

        // 改造版 / 文字线路：整个线路名一行，英文一行
        final String chineseName = badge.chineseName() == null ? "" : badge.chineseName();
        final Font chineseFont = fitFont(graphics, chineseName, true, height * 0.46F, width * 0.82F);
        final Font englishFont = fitFont(graphics, english, false, height * 0.27F, width * 0.82F);
        final FontMetrics chineseMetrics = graphics.getFontMetrics(chineseFont);
        final FontMetrics englishMetrics = graphics.getFontMetrics(englishFont);
        final float gap = height * 0.04F;
        float cursor = top + (height - chineseMetrics.getAscent() - gap - englishMetrics.getAscent()) / 2F;
        graphics.setColor(new Color(text, true));
        graphics.setFont(chineseFont);
        graphics.drawString(chineseName, x + (width - textWidth(graphics, chineseName, chineseFont)) / 2F,
                cursor + chineseMetrics.getAscent());
        cursor += chineseMetrics.getAscent() + gap;
        graphics.setFont(englishFont);
        graphics.drawString(english, x + (width - textWidth(graphics, english, englishFont)) / 2F,
                cursor + englishMetrics.getAscent());
    }

    // ===== 信息块 =====

    private static void drawInfoBlock(Graphics2D graphics, float x, float width, String label, String chinese,
                                      Font cnFont, String english, Font enFont,
                                      float cnBaseline, float enBaseline, boolean arrowLeft) {
        final float gap = cnFont.getSize2D() * 0.42F;
        final float labelWidth = textWidth(graphics, label, cnFont);
        final float chineseWidth = textWidth(graphics, chinese, cnFont);
        final float enWidth = textWidth(graphics, english, enFont);

        graphics.setColor(new Color(TEXT_COLOR, true));
        graphics.setFont(cnFont);
        if (arrowLeft) {
            float cursor = x;
            drawBold(graphics, label, cursor, cnBaseline, cnFont);
            cursor += labelWidth + gap;
            if (!chinese.isEmpty()) {
                drawBold(graphics, chinese, cursor, cnBaseline, cnFont);
            }
        } else {
            float cursor = x + width;
            if (!chinese.isEmpty()) {
                cursor -= chineseWidth;
                drawBold(graphics, chinese, cursor, cnBaseline, cnFont);
                cursor -= gap;
            }
            cursor -= labelWidth;
            drawBold(graphics, label, cursor, cnBaseline, cnFont);
        }
        if (!english.isEmpty()) {
            graphics.setFont(enFont);
            final float enX = arrowLeft ? x : x + width - enWidth;
            graphics.drawString(english, enX, enBaseline);
        }
    }

    private static float blockWidth(Graphics2D graphics, String label, String chinese, Font cnFont,
                                    String english, Font enFont) {
        final float gap = cnFont.getSize2D() * 0.42F;
        final float labelWidth = textWidth(graphics, label, cnFont);
        final float chineseWidth = textWidth(graphics, chinese, cnFont);
        final float cnWidth = chinese.isEmpty() ? labelWidth : labelWidth + gap + chineseWidth;
        final float enWidth = enFont == null ? 0F : textWidth(graphics, english, enFont);
        return Math.max(cnWidth, enWidth);
    }

    // ===== 字体 / 文字 =====

    private static Font fitFont(Graphics2D graphics, String text, boolean cjk, float targetHeight, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return cjk ? TrdmHeaderTextures.font(targetHeight) : TrdmHeaderTextures.latinFont(targetHeight);
        }
        // getStringBounds 量到的是字体的行高，不是墨迹高度；这里按经验把目标放大，
        // 让最终画出来的字和参考图一样高（中文约 0.88 倍字号，英文约 0.95 倍字号）。
        final float fittedTarget = targetHeight * (cjk ? 1.28F : 1.18F);
        float size = Math.max(4F, fittedTarget);
        for (int iteration = 0; iteration < 5; iteration++) {
            final Font font = cjk ? TrdmHeaderTextures.font(size) : TrdmHeaderTextures.latinFont(size);
            final Rectangle2D bounds = font.getStringBounds(text, graphics.getFontRenderContext());
            final float measuredHeight = Math.max(1F, (float) bounds.getHeight());
            final float measuredWidth = Math.max(1F, (float) bounds.getWidth());
            float factor = fittedTarget / measuredHeight;
            if (measuredWidth * factor > maxWidth) {
                factor = Math.min(factor, maxWidth / measuredWidth);
            }
            size *= Math.max(0.4F, Math.min(2.5F, factor));
        }
        return cjk ? TrdmHeaderTextures.font(size) : TrdmHeaderTextures.latinFont(size);
    }

    private static Font scaleFont(Font font, float scale) {
        return font.deriveFont(Math.max(1F, font.getSize2D() * scale));
    }

    private static void drawCenteredAtBaseline(Graphics2D graphics, String text, Font font, float centerX,
                                               float baseline, boolean bold) {
        if (text == null || text.isEmpty()) {
            return;
        }
        final float width = textWidth(graphics, text, font);
        final float x = centerX - width / 2F;
        graphics.setColor(new Color(TEXT_COLOR, true));
        if (bold) {
            drawBold(graphics, text, x, baseline, font);
        } else {
            graphics.setFont(font);
            graphics.drawString(text, x, baseline);
        }
    }

    private static void drawBold(Graphics2D graphics, String text, float x, float baseline, Font font) {
        if (text == null || text.isEmpty()) {
            return;
        }
        graphics.setFont(font);
        graphics.drawString(text, x, baseline);
        // SimHei 没有粗体字重，横向重描一次模拟参考图的粗黑体
        graphics.drawString(text, x + Math.max(0.8F, font.getSize2D() * 0.025F), baseline);
    }

    private static float textWidth(Graphics2D graphics, String text, Font font) {
        if (text == null || text.isEmpty()) {
            return 0F;
        }
        graphics.setFont(font);
        return (float) font.getStringBounds(text, graphics.getFontRenderContext()).getWidth();
    }

    // ===== 数据 =====

    /** 拆「中文|English」；英文保持原样大小写（改造版按需求保留 MTR 原英文）。 */
    static String[] splitName(String name) {
        final String value = name == null ? "" : name.trim();
        final String[] parts = value.split("\\|");
        if (parts.length == 1) {
            return isCjk(parts[0]) ? new String[]{parts[0].trim(), ""} : new String[]{"", parts[0].trim()};
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

    private static boolean isCjk(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= 0x2E80) {
                return true;
            }
        }
        return false;
    }

    private static String[] splitRouteNumber(String routeName) {
        final String name = routeName == null ? "" : routeName.trim();
        final Matcher matcher = ROUTE_NUMBER_PATTERN.matcher(name);
        if (matcher.matches()) {
            return new String[]{matcher.group(1), matcher.group(2).trim()};
        }
        return new String[]{"", name};
    }

    private static boolean isLightColor(int color) {
        final int red = (color >> 16) & 0xFF;
        final int green = (color >> 8) & 0xFF;
        final int blue = color & 0xFF;
        return 0.299F * red + 0.587F * green + 0.114F * blue > 165F;
    }
}
