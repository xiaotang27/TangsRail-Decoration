package top.xiaotang27.trdm.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 站名牌的版面（参照 MTR 车站站名牌的排法）：
 * <pre>
 */
public final class TrdmStationSignPanel {

    // ===== 版面比例（都以牌子高度 H 为单位）=====
    /** 本站中文的字号。 */
    private static final float STATION_CJK_SIZE = 0.26F;
    /** 本站拼音的标称字号（再夹到中文那行的 30%~42% 宽高）。 */
    private static final float STATION_LATIN_SIZE = 0.10F;
    /** 本站中文+拼音这一块的竖直中心。 */
    private static final float STATION_BLOCK_CENTER = 0.27F;
    /** 上下两行之间的间距。 */
    private static final float STATION_LINE_GAP = 0.05F;

    /** 线路色带的上边缘与厚度。 */
    private static final float BAND_TOP = 0.555F;
    private static final float BAND_HEIGHT = 0.09F;

    /** 底部一行：中文 / 英文的字号 + 整块的竖直中心。 */
    /** 左边「下一站 / 终点站」标签的字号。 */
    private static final float BOTTOM_LABEL_CJK = 0.112F;
    private static final float BOTTOM_LABEL_LATIN = 0.058F;
    /** 右边下一站站名的字号，比标签大一号。 */
    private static final float BOTTOM_NAME_CJK = 0.150F;
    private static final float BOTTOM_NAME_LATIN = 0.078F;
    private static final float BOTTOM_BLOCK_CENTRE = 0.855F;

    /** 左右留白、底部两块的宽度上限。 */
    private static final float MARGIN_RATIO = 0.05F;
    private static final float HALF_WIDTH_RATIO = 0.44F;
    /** 底下两块（「下一站」块 / 下一站名块）的中心位置：往中间靠，不贴左右边。 */
    private static final float BOTTOM_LEFT_CENTRE = 0.28F;
    private static final float BOTTOM_RIGHT_CENTRE = 0.72F;
    /** 底下每块最多占板宽的比例（两块不会撞在一起）。 */
    private static final float BOTTOM_BLOCK_WIDTH = 0.34F;

    private TrdmStationSignPanel() {
    }

    /**
     * @param stationName 本站（MTR 的「中文|English」字符串）
     * @param nextStation 下一站（同上；null = 终点站）
     */
    public static BufferedImage render(int width, int height, String stationName, String nextStation, int routeColor,
                                       boolean terminal, String nextLabel, String nextLabelEn) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        final float h = height;
        final float w = width;
        final float marginX = MARGIN_RATIO * w;
        final float halfWidth = HALF_WIDTH_RATIO * w;
        final int black = 0xFF000000;

        // 白底
        graphics.setColor(new Color(0xFFFFFFFF, true));
        graphics.fillRect(0, 0, width, height);

        // 本站：中文 + 英文，<b>英文按字号缩放到和中文一样宽</b>，两行都居中
        final String[] stationParts = TrdmHeaderTextures.splitName(stationName);
        final String stationCjk = stationParts[0];
        final String stationLatin = stationParts[1];
        float cjkSize = STATION_CJK_SIZE * h;
        // 长站名先缩到板宽的 90% 以内
        final float maxCjkWidth = w * 0.90F;
        final float rawCjkWidth = textWidth(graphics, stationCjk, TrdmHeaderTextures.font(cjkSize));
        if (rawCjkWidth > maxCjkWidth && rawCjkWidth > 0F) {
            cjkSize *= maxCjkWidth / rawCjkWidth;
        }
        final Font stationCjkFont = TrdmHeaderTextures.font(cjkSize);
        final float stationCjkWidth = textWidth(graphics, stationCjk, stationCjkFont);
        graphics.setColor(new Color(black, true));
        Font stationLatinFont = null;
        if (!stationLatin.isEmpty()) {
            // 英文字号由「要撑到多宽」反算，宽度严格等于中文那一行
            stationLatinFont = matchWidth(graphics, TrdmHeaderTextures.latinFont(STATION_LATIN_SIZE * h),
                    stationLatin, stationCjkWidth);
        }
        drawStacked(graphics, stationCjk, stationLatin, stationCjkFont, stationLatinFont,
                w / 2F, STATION_BLOCK_CENTER * h, STATION_LINE_GAP * h);

        // 线路色带：整幅宽
        graphics.setColor(new Color(routeColor | 0xFF000000, true));
        graphics.fillRect(0, Math.round(BAND_TOP * h), width, Math.max(2, Math.round(BAND_HEIGHT * h)));

        // 底部一行：「下一站」块 + 下一站名块，两块各自居中、并往中间靠
        graphics.setColor(new Color(black, true));
        // 终点站：整块直接摆在板子的 X 轴中心
        drawCentredBlock(graphics, nextLabel, nextLabelEn, terminal ? w / 2F : BOTTOM_LEFT_CENTRE * w, w, h,
                BOTTOM_LABEL_CJK, BOTTOM_LABEL_LATIN);
        if (!terminal) {
            final String[] nextParts = TrdmHeaderTextures.splitName(nextStation);
            final String nextRight = nextParts[0].isEmpty() ? nextParts[1] : nextParts[0];
            final String nextRightEn = nextParts[0].isEmpty() ? "" : nextParts[1];
            drawCentredBlock(graphics, nextRight, nextRightEn, BOTTOM_RIGHT_CENTRE * w, w, h,
                    BOTTOM_NAME_CJK, BOTTOM_NAME_LATIN);
        }

        graphics.dispose();
        return image;
    }

    /** 底下一块：「下一站」这种标签（或站名）+ 它下面的英文，整块以 centerX 居中。 */
    private static void drawCentredBlock(Graphics2D graphics, String cjk, String latin, float centerX,
                                         float w, float h, float cjkSize, float latinSize) {
        if (cjk == null || cjk.isEmpty()) {
            return;
        }
        final float maxWidth = BOTTOM_BLOCK_WIDTH * w;
        final Font cjkFont = fitToWidth(graphics, cjk, TrdmHeaderTextures.font(cjkSize * h), maxWidth);
        final float cjkWidth = textWidth(graphics, cjk, cjkFont);
        Font latinFont = null;
        if (latin != null && !latin.isEmpty()) {
            // 英文宽度 = 中文宽度
            latinFont = matchWidth(graphics, TrdmHeaderTextures.latinFont(latinSize * h), latin, cjkWidth);
        }
        drawStacked(graphics, cjk, latin, cjkFont, latinFont, centerX, BOTTOM_BLOCK_CENTRE * h, 0.012F * h);
    }

    /**
     * 中文一行、英文一行叠在一起，<b>整块以 (centerX, centerY) 居中</b>。
     * 行高按字体度量算，英文被放大到与中文同宽时也不会两行相撞。
     */
    private static void drawStacked(Graphics2D graphics, String cjk, String latin, Font cjkFont, Font latinFont,
                                    float centerX, float centerY, float gap) {
        if (cjk == null || cjk.isEmpty()) {
            return;
        }
        graphics.setFont(cjkFont);
        final FontMetrics cjkMetrics = graphics.getFontMetrics(cjkFont);
        final float cjkHeight = cjkMetrics.getAscent() + cjkMetrics.getDescent();
        final boolean hasLatin = latin != null && !latin.isEmpty() && latinFont != null;
        final FontMetrics latinMetrics = hasLatin ? graphics.getFontMetrics(latinFont) : null;
        final float latinHeight = latinMetrics == null ? 0F : latinMetrics.getAscent() + latinMetrics.getDescent();
        final float total = cjkHeight + (hasLatin ? gap + latinHeight : 0F);
        final float top = centerY - total / 2F;

        graphics.setFont(cjkFont);
        graphics.drawString(cjk, centerX - cjkMetrics.stringWidth(cjk) / 2F, top + cjkMetrics.getAscent());
        if (hasLatin) {
            graphics.setFont(latinFont);
            graphics.drawString(latin, centerX - latinMetrics.stringWidth(latin) / 2F,
                    top + cjkHeight + gap + latinMetrics.getAscent());
        }
    }

    /** 把字号调到「这行字正好宽 targetWidth」（targetWidth <= 0 就用原字号）。 */
    private static Font matchWidth(Graphics2D graphics, Font base, String text, float targetWidth) {
        if (text == null || text.isEmpty() || targetWidth <= 0F) {
            return base;
        }
        final float width = textWidth(graphics, text, base);
        if (width <= 0F) {
            return base;
        }
        return base.deriveFont(base.getSize2D() * (targetWidth / width));
    }

    /** 以 centerX 居中画一行（按墨水视觉中心对齐 centerY）。 */
    private static void drawCentredInk(Graphics2D graphics, String text, Font font, float centerX, float centerY) {
        if (text == null || text.isEmpty()) {
            return;
        }
        graphics.setFont(font);
        final FontMetrics metrics = graphics.getFontMetrics(font);
        graphics.drawString(text, centerX - metrics.stringWidth(text) / 2F, baseline(metrics, centerY));
    }

    /** 字号太大就缩到 maxWidth 以内，站名长短差别很大（「北站」到「人民广场」）。 */
    private static Font fitToWidth(Graphics2D graphics, String text, Font base, float maxWidth) {
        if (text == null || text.isEmpty()) {
            return base;
        }
        final float width = textWidth(graphics, text, base);
        if (width <= maxWidth || width <= 0) {
            return base;
        }
        return base.deriveFont(base.getSize2D() * (maxWidth / width));
    }

    private static float textWidth(Graphics2D graphics, String text, Font font) {
        if (text == null || text.isEmpty()) {
            return 0F;
        }
        graphics.setFont(font);
        return graphics.getFontMetrics(font).stringWidth(text);
    }

    private static void drawLeftInk(Graphics2D graphics, String text, Font font, float leftX, float centerY) {
        if (text == null || text.isEmpty()) {
            return;
        }
        graphics.setFont(font);
        graphics.drawString(text, leftX, baseline(graphics.getFontMetrics(font), centerY));
    }

    private static void drawRightInk(Graphics2D graphics, String text, Font font, float rightX, float centerY) {
        if (text == null || text.isEmpty()) {
            return;
        }
        graphics.setFont(font);
        final FontMetrics metrics = graphics.getFontMetrics(font);
        graphics.drawString(text, rightX - metrics.stringWidth(text), baseline(metrics, centerY));
    }

    /** 让文字「视觉中心」落在 centerY：基线 = 中心 + (上伸 − 下伸) / 2。 */
    private static float baseline(FontMetrics metrics, float centerY) {
        return centerY + (metrics.getAscent() - metrics.getDescent()) / 2F;
    }
}
