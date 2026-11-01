package top.xiaotang27.trdm.render;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 大站名牌的版面（照参考图那种横向大牌）：
 * <pre>
 */
public final class TrdmBigStationSignPanel {

    /** 底色：纯黑（墙挂 / 悬挂两款大站名牌共用这张版面）。 */
    private static final int BACKGROUND_COLOR = 0xFF000000;

    // ===== 版面比例（单位：牌子高度 H）=====
    private static final float PADDING = 0.13F;
    private static final float LOGO_SIZE = 0.72F;
    private static final float BADGE_SIZE = 0.66F;
    private static final float GAP = 0.10F;
    private static final float SECTION_GAP = 0.22F;
    private static final float LETTER_SIZE = 0.62F;
    private static final float NAME_CJK_SIZE = 0.38F;
    private static final float NAME_EN_SIZE = 0.175F;
    /** 中文字距最多加到每字这么多倍字号。 */
    private static final float MAX_TRACKING = 0.45F;

    private TrdmBigStationSignPanel() {
    }

    /**
     * @param width     贴图宽（像素）
     * @param height    贴图高（像素）
     */
    public static BufferedImage render(int width, int height, String cjk, String latin,
                                        java.util.List<TrdmHeaderTextures.Badge> badges, String letter,
                                        TrdmSignAssets.Sprite logo, TrdmSignAssets.Sprite elevator) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        final float h = height;
        final float w = width;

        graphics.setColor(new Color(BACKGROUND_COLOR, true));
        graphics.fillRect(0, 0, width, height);

        final String safeCjk = cjk == null ? "" : cjk;
        final String safeLatin = latin == null ? "" : latin;
        final String safeLetter = letter == null ? "" : letter;

        // ===== 先按标称字号量一遍，算总宽 =====
        float cjkSize = NAME_CJK_SIZE * h;
        float enSize = NAME_EN_SIZE * h;
        final Font cjkFont = TrdmHeaderTextures.font(cjkSize);
        final Font enFont = TrdmHeaderTextures.latinFont(enSize);
        final float cjkBaseWidth = textWidth(graphics, safeCjk, cjkFont);
        final float enWidth = textWidth(graphics, safeLatin, enFont);
        final int cjkCount = Math.max(0, safeCjk.length());
        float tracking = 0F;
        if (enWidth > cjkBaseWidth && cjkCount > 1) {
            // 英文比中文长 → 给中文加字距去凑，最多 MAX_TRACKING 倍字号
            tracking = Math.min((enWidth - cjkBaseWidth) / (cjkCount - 1), MAX_TRACKING * cjkSize);
        }
        final float cjkWidth = cjkBaseWidth + tracking * Math.max(0, cjkCount - 1);
        final float nameWidth = Math.max(cjkWidth, enWidth);

        final float logoNominal = LOGO_SIZE * h;
        final float badgeHeight = BADGE_SIZE * h;
        final int badgeCount = badges == null ? 0 : badges.size();
        // 每块编号的宽度可能不同：纯文字线路（如「津静线」）是长条
        final float[] badgeWidths = new float[badgeCount];
        float badgeBlock = 0F;
        for (int index = 0; index < badgeCount; index++) {
            badgeWidths[index] = TrdmHeaderTextures.badgeWidth(graphics, badgeHeight, badges.get(index));
            badgeBlock += badgeWidths[index] + (index < badgeCount - 1 ? GAP * h : 0F);
        }
        // 无障碍电梯图标和编号块同样大，排在公交线路标签后面
        final float elevatorSize = elevator == null ? 0F : badgeHeight;
        final float letterSize = LETTER_SIZE * h;
        final float letterWidth = textWidth(graphics, safeLetter, TrdmHeaderTextures.latinFont(letterSize));
        final float gap = GAP * h;
        final float sectionGap = SECTION_GAP * h;
        final float padding = PADDING * h;

        final float total = padding * 2F + logoNominal
                + (badgeCount == 0 ? 0F : gap + badgeBlock)
                + (elevator == null ? 0F : gap + elevatorSize)
                + sectionGap + nameWidth + sectionGap + letterWidth;
        // 空间不够：所有元素（含间距、字距）等比缩小
        final float scale = total > w && total > 0 ? w / total : 1F;

        final float logoSize = logoNominal * scale;
        final float badgeDrawHeight = badgeHeight * scale;
        final float elevatorDrawSize = elevatorSize * scale;
        final float letterDrawSize = letterSize * scale;
        final float nameCjkSize = cjkSize * scale;
        final float nameEnSize = enSize * scale;
        final float nameTracking = tracking * scale;
        final float drawGap = gap * scale;
        final float drawSectionGap = sectionGap * scale;
        final float drawPadding = padding * scale;
        final float drawNameWidth = nameWidth * scale;

        // ===== 左侧：地铁标志 + 本站所有线路的编号块 =====
        float cursor = drawPadding;
        if (logo != null) {
            TrdmSignAssets.drawSpriteInk(graphics, logo, cursor, h / 2F - logoSize / 2F, logoSize, logoSize);
        }
        cursor += logoSize;

        if (badgeCount > 0) {
            cursor += drawGap;
            final float drawBadgeGap = GAP * h * scale;
            final float badgeOutline = Math.max(2F, badgeDrawHeight * 0.06F);
            for (int index = 0; index < badgeCount; index++) {
                final float drawWidth = badgeWidths[index] * scale;
                TrdmHeaderTextures.drawBadge(graphics, Math.round(cursor),
                        Math.round(h / 2F - badgeDrawHeight / 2F), Math.round(drawWidth),
                        Math.round(badgeDrawHeight), badges.get(index), badgeOutline);
                cursor += drawWidth + (index < badgeCount - 1 ? drawBadgeGap : 0F);
            }
        }

        // 线路标签后面：无障碍电梯图标
        if (elevator != null) {
            cursor += drawGap;
            TrdmSignAssets.drawSpriteInk(graphics, elevator, cursor, h / 2F - elevatorDrawSize / 2F,
                    elevatorDrawSize, elevatorDrawSize);
            cursor += elevatorDrawSize;
        }

        // ===== 中间：站名（在「编号块右边 ~ 站台字母左边」这段里水平居中）=====
        final float letterRight = w - drawPadding;
        final float middleLeft = cursor + drawSectionGap;
        final float middleRight = letterRight - textWidth(graphics, safeLetter,
                TrdmHeaderTextures.latinFont(letterDrawSize)) - drawSectionGap;
        final float nameCenterX = middleRight > middleLeft ? (middleLeft + middleRight) / 2F : (middleLeft + middleRight) / 2F;

        final Font drawCjkFont = TrdmHeaderTextures.font(nameCjkSize);
        final Font drawEnFont = TrdmHeaderTextures.latinFont(nameEnSize);
        final FontMetrics cjkMetrics = graphics.getFontMetrics(drawCjkFont);
        final FontMetrics enMetrics = graphics.getFontMetrics(drawEnFont);
        final float cjkInk = cjkMetrics.getAscent() + cjkMetrics.getDescent();
        final float enInk = enMetrics.getAscent() + enMetrics.getDescent();
        final float lineGap = h * 0.04F;
        final float blockHeight = cjkInk + (safeLatin.isEmpty() ? 0F : lineGap + enInk);
        final float cjkAscent = h / 2F - blockHeight / 2F + cjkMetrics.getAscent();

        graphics.setColor(new Color(0xFFFFFFFF, true));
        if (!safeCjk.isEmpty()) {
            drawTracked(graphics, safeCjk, drawCjkFont, nameTracking, nameCenterX, cjkAscent);
        }
        if (!safeLatin.isEmpty()) {
            graphics.setFont(drawEnFont);
            final float baseline = cjkAscent + cjkMetrics.getDescent() + lineGap + enMetrics.getAscent();
            graphics.drawString(safeLatin, nameCenterX - textWidth(graphics, safeLatin, drawEnFont) / 2F, baseline);
        }

        // ===== 右侧：站台字母 =====
        if (!safeLetter.isEmpty()) {
            final Font letterFont = TrdmHeaderTextures.latinFont(letterDrawSize);
            final FontMetrics letterMetrics = graphics.getFontMetrics(letterFont);
            graphics.setFont(letterFont);
            graphics.setColor(new Color(0xFFFFFFFF, true));
            graphics.drawString(safeLetter, letterRight - textWidth(graphics, safeLetter, letterFont),
                    h / 2F + (letterMetrics.getAscent() - letterMetrics.getDescent()) / 2F);
        }

        graphics.dispose();
        return image;
    }

    /** 带字距的居中绘制（中文字距拉宽用）。 */
    private static void drawTracked(Graphics2D graphics, String text, Font font, float extraTracking,
                                    float centerX, float baseline) {
        graphics.setFont(font);
        final FontMetrics metrics = graphics.getFontMetrics(font);
        float total = extraTracking * Math.max(0, text.length() - 1);
        for (int i = 0; i < text.length(); i++) {
            total += metrics.charWidth(text.charAt(i));
        }
        float x = centerX - total / 2F;
        for (int i = 0; i < text.length(); i++) {
            final char character = text.charAt(i);
            graphics.drawString(String.valueOf(character), x, baseline);
            x += metrics.charWidth(character) + extraTracking;
        }
    }

    private static float textWidth(Graphics2D graphics, String text, Font font) {
        if (text == null || text.isEmpty()) {
            return 0F;
        }
        graphics.setFont(font);
        return graphics.getFontMetrics(font).stringWidth(text);
    }
}
