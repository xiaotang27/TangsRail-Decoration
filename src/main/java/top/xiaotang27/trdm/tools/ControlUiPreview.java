package top.xiaotang27.trdm.tools;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 一次性小工具：不开游戏，把集控装置的站台选择界面按同样的坐标/配色画成 PNG，
 * 用来核对排版（线路色块 + 线路名 + 开往终点站、站台编号、隔离开关）。
 */
public final class ControlUiPreview {

    // ===== 和 PsdControlScreen 里一致的布局常量 =====
    private static final int WIDTH = 360;
    private static final int HEIGHT = 268;
    private static final int LIST_WIDTH = 320;
    private static final int LIST_TOP = 52;
    private static final int LIST_BOTTOM_MARGIN = 62;
    private static final int ROW_HEADER_HEIGHT = 12;
    private static final int ROUTE_HEIGHT = 22;
    private static final int BADGE_WIDTH = 13;
    private static final int PILL_WIDTH = 26;
    private static final int PILL_HEIGHT = 12;

    private ControlUiPreview() {
    }

    public static void main(String[] args) throws Exception {
        final Path outDir = Path.of("build/preview");
        Files.createDirectories(outDir);

        final BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        final Font text = font(11);
        final Font small = font(10);

        // 背景（MC 的 renderBackground 是压暗的渐变，这里用纯深色近似）
        graphics.setColor(new Color(0x101010));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);

        final int centerX = WIDTH / 2;
        final int listLeft = centerX - LIST_WIDTH / 2;
        final int listRight = listLeft + LIST_WIDTH;
        final int listBottom = HEIGHT - LIST_BOTTOM_MARGIN;

        // 标题 + 车站
        drawCentered(graphics, text, "选择要控制的站台", centerX, 8, 0xFFFFFF);
        drawCentered(graphics, small, "车站：人民广场", centerX, 20, 0xAAAAAA);

        // 搜索框
        graphics.setColor(new Color(0x000000));
        graphics.fillRect(listLeft, 30, LIST_WIDTH, 18);
        graphics.setColor(new Color(0xFF808080));
        graphics.setStroke(new BasicStroke(1F));
        graphics.drawRect(listLeft, 30, LIST_WIDTH, 18);
        graphics.setColor(new Color(0x808080));
        graphics.setFont(small);
        graphics.drawString("搜索站名 / 站台名…", listLeft + 4, 42);

        // 列表底
        graphics.setColor(new Color(0, 0, 0, 112));
        graphics.fillRect(listLeft - 1, LIST_TOP - 1, LIST_WIDTH + 2, listBottom - LIST_TOP + 2);

        int top = LIST_TOP;
        top = drawRow(graphics, small, text, "站台 1", true,
                new Route[]{
                        new Route("1", "1号线|Line 1", "开往 人民广场", 0x3B6FBF)
                }, top, listLeft, listRight);

        top = drawRow(graphics, small, text, "站台 2", false,
                new Route[]{
                        new Route("2", "2号线|Line 2", "开往 火车站", 0x2E9E4F),
                        new Route("5", "5号线|Line 5", "终点站", 0xC0392B)
                }, top, listLeft, listRight);

        drawRow(graphics, small, text, "站台 3", false,
                new Route[]{
                        new Route("8", "塘铁8号线|Tangs Rail Line 8", "开往 机场北", 0xE0B015)
                }, top, listLeft, listRight);

        // 隔离开关行（标题 + 状态字 + 药丸形开关）+ 两个按钮
        drawToggleRow(graphics, small, listLeft, listRight, HEIGHT - 54, true);

        final int buttonWidth = (LIST_WIDTH - 4) / 2;
        drawButton(graphics, small, "清除选择", listLeft, HEIGHT - 30, buttonWidth);
        drawButton(graphics, small, "取消", listLeft + buttonWidth + 4, HEIGHT - 30, buttonWidth);

        graphics.dispose();
        final Path target = outDir.resolve("psd_control_ui.png");
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + WIDTH + "x" + HEIGHT);
    }

    private static int drawRow(Graphics2D graphics, Font small, Font text, String platformLabel, boolean selected,
                               Route[] routes, int top, int listLeft, int listRight) {
        final int rowHeight = ROW_HEADER_HEIGHT + Math.max(1, routes.length) * ROUTE_HEIGHT + 2;

        graphics.setColor(selected ? new Color(0xC0, 0xFF, 0xC0, 0x60) : new Color(0xFF, 0xFF, 0xFF, 0x20));
        graphics.fillRect(listLeft, top, LIST_WIDTH, rowHeight - 1);

        graphics.setFont(small);
        graphics.setColor(selected ? new Color(0xFFFFFF80) : new Color(0xAAAAAA));
        graphics.drawString(platformLabel, listLeft + 6, top + 11);

        int lineTop = top + ROW_HEADER_HEIGHT;
        final int ascent = graphics.getFontMetrics().getAscent();
        for (final Route route : routes) {
            final int badgeLeft = listLeft + 6;
            graphics.setColor(new Color(route.color()));
            graphics.fillRect(badgeLeft, lineTop + 1, BADGE_WIDTH, ROUTE_HEIGHT - 2);

            graphics.setFont(small);
            graphics.setColor(new Color(contrastColor(route.color())));
            final int numberWidth = graphics.getFontMetrics().stringWidth(route.badge());
            graphics.drawString(route.badge(), badgeLeft + (BADGE_WIDTH - numberWidth) / 2 + 1,
                    lineTop + 1 + (ROUTE_HEIGHT - 2) / 2 + ascent / 2);

            final int textLeft = badgeLeft + BADGE_WIDTH + 4;
            graphics.setColor(selected ? new Color(0xFFFFFF80) : new Color(0xE8E8E8));
            graphics.drawString(route.name(), textLeft, lineTop + 2 + ascent);
            graphics.setColor(new Color(0x9E9E9E));
            graphics.drawString(route.terminus(), textLeft, lineTop + 12 + ascent);
            lineTop += ROUTE_HEIGHT;
        }
        return top + rowHeight;
    }

    private static void drawToggleRow(Graphics2D graphics, Font font, int listLeft, int listRight, int top,
                                      boolean value) {
        graphics.setFont(font);
        graphics.setColor(new Color(0xE8E8E8));
        graphics.drawString("屏蔽门隔离", listLeft + 4, top + 12);

        final String stateText = value ? "开" : "关";
        final int pillLeft = listRight - PILL_WIDTH - 4;
        final int pillTop = top + (16 - PILL_HEIGHT) / 2;

        graphics.setColor(value ? new Color(0x7BD88F) : new Color(0xAAAAAA));
        final int stateWidth = graphics.getFontMetrics().stringWidth(stateText);
        graphics.drawString(stateText, pillLeft - 6 - stateWidth, top + 12);

        graphics.setColor(new Color(0x8A8A8A));
        graphics.fillRoundRect(pillLeft - 1, pillTop - 1, PILL_WIDTH + 2, PILL_HEIGHT + 2, PILL_HEIGHT + 2,
                PILL_HEIGHT + 2);
        graphics.setColor(value ? new Color(0x2E8B57) : new Color(0x4A4A4A));
        graphics.fillRoundRect(pillLeft, pillTop, PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT, PILL_HEIGHT);

        final int knob = PILL_HEIGHT - 4;
        final int knobX = value ? pillLeft + PILL_WIDTH - knob - 2 : pillLeft + 2;
        graphics.setColor(value ? Color.WHITE : new Color(0xE0E0E0));
        graphics.fillOval(knobX, pillTop + 2, knob, knob);
    }

    private static void drawButton(Graphics2D graphics, Font font, String label, int x, int y, int width) {
        graphics.setColor(new Color(0x2B2B2B));
        graphics.fillRect(x, y, width, 20);
        graphics.setColor(new Color(0xFF9A9A9A));
        graphics.drawRect(x, y, width, 20);
        graphics.setFont(font);
        graphics.setColor(Color.WHITE);
        final int textWidth = graphics.getFontMetrics().stringWidth(label);
        graphics.drawString(label, x + (width - textWidth) / 2, y + 14);
    }

    private static void drawCentered(Graphics2D graphics, Font font, String text, int centerX, int y, int rgb) {
        graphics.setFont(font);
        graphics.setColor(new Color(rgb));
        final int textWidth = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, centerX - textWidth / 2, y + font.getSize());
    }

    private static int contrastColor(int rgb) {
        final int red = (rgb >> 16) & 0xFF;
        final int green = (rgb >> 8) & 0xFF;
        final int blue = rgb & 0xFF;
        return 0.299 * red + 0.587 * green + 0.114 * blue > 155 ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static Font font(int size) {
        final String[] preferred = {"Microsoft YaHei", "Noto Sans CJK SC", "SimHei", Font.DIALOG};
        final String[] available = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (final String name : preferred) {
            if (Arrays.asList(available).contains(name)) {
                return new Font(name, Font.PLAIN, size);
            }
        }
        return new Font(Font.DIALOG, Font.PLAIN, size);
    }

    private record Route(String badge, String name, String terminus, int color) {
    }
}
