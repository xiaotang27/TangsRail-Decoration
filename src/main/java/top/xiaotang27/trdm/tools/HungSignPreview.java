package top.xiaotang27.trdm.tools;

import top.xiaotang27.trdm.render.TrdmBigStationSignPanel;
import top.xiaotang27.trdm.render.TrdmHeaderTextures;
import top.xiaotang27.trdm.render.TrdmSignAssets;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 一次性小工具：不开游戏，按方块网格画出「悬挂式站外站名牌」的模型示意
 * （3 格 / 6 格正视图 + 侧视图），用来核对尺寸
 * （牌子 12/16 高、1/16 厚、吊杆 4/16、<b>吊杆只在两端</b>）和内容排版。
 * <p>
 * 用法：{@code ./gradlew previewHungSign}，输出到 {@code build/preview/hung_sign_model.png}。
 */
public final class HungSignPreview {

    /** 每格画多少像素。 */
    private static final int PX = 72;

    private static final Color CEILING = new Color(0x2B2B2B);
    private static final Color ROD = new Color(0x6E6E6E);
    private static final Color GRID = new Color(0x44000000, true);
    private static final Color LABEL = new Color(0xFF25303A);
    private static final Color DIM = new Color(0xFF5A6A76);
    private static final Color BG = new Color(0xC8D2D8);
    private static final int MARGIN = 34;
    private static final int GAP = 40;

    private HungSignPreview() {
    }

    public static void main(String[] args) throws Exception {
        final Path outDir = Path.of("build/preview");
        Files.createDirectories(outDir);
        // 大站名牌那张图要用地铁标志素材，离线从这里读
        TrdmSignAssets.primeFromDirectory(Path.of("src/main/resources/assets/trdm/textures/block/sign"));

        final int height = MARGIN * 2 + PX + 82;
        final int width = MARGIN * 2 + 3 * PX + GAP + 9 * PX + GAP + PX * 2;
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(BG);
        g.fillRect(0, 0, width, height);

        final int top = MARGIN;
        final int blockBottom = top + PX;

        int left = MARGIN;
        left = drawFront(g, left, top, blockBottom, 3, "3 格一段");
        left = drawFront(g, left + GAP, top, blockBottom, 9, "9 格一段（牌宽 8 格封顶）");
        drawSide(g, left + GAP, top, blockBottom);

        g.dispose();
        final Path target = outDir.resolve("hung_sign_model.png");
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + width + "x" + height);
    }

    /** 正视图：天花板 + 两端吊杆 + 牌面。返回这一段右边缘的 x。 */
    private static int drawFront(Graphics2D g, int left, int top, int blockBottom, int blocks, String caption)
            throws Exception {
        final int runWidth = blocks * PX;
        final int plateBlocks = Math.min(8, blocks);
        final int plateWidth = plateBlocks * PX;
        final int plateLeft = left + (runWidth - plateWidth) / 2;
        final int runCentre = left + runWidth / 2;
        final int rodLength = (int) (4F / 16F * PX);
        final int plateTop = blockBottom - (int) (12F / 16F * PX);

        g.setColor(CEILING);
        g.fillRect(left, top - 12, runWidth, 12);

        // 吊杆：只在牌面两端各一根（牌宽 8 格封顶，段再长也往里收）；一格时中间一根
        final int rodWidth = Math.max(3, PX / 16);
        final int rodOffset = Math.max(0, plateWidth / 2 - PX / 2);
        g.setColor(ROD);
        if (rodOffset == 0) {
            g.fillRect(runCentre - rodWidth / 2, plateTop - rodLength, rodWidth, rodLength);
        } else {
            g.fillRect(runCentre - rodOffset - rodWidth / 2, plateTop - rodLength, rodWidth, rodLength);
            g.fillRect(runCentre + rodOffset - rodWidth / 2, plateTop - rodLength, rodWidth, rodLength);
        }

        // 牌面
        final float aspect = plateBlocks / (12F / 16F);
        final TrdmHeaderTextures.TextureSize size = TrdmHeaderTextures.bigSignTextureSize(aspect);
        final BufferedImage plate = TrdmBigStationSignPanel.render(size.width(), size.height(),
                "红旗南路站", "HONGQINANLU ZHAN STATION", badges(), "A",
                TrdmSignAssets.sprite(TrdmSignAssets.STATION_LOGO), null);
        g.drawImage(plate, plateLeft, plateTop, plateLeft + plateWidth, blockBottom,
                0, 0, plate.getWidth(), plate.getHeight(), null);

        // 方块网格
        g.setColor(GRID);
        g.setStroke(new BasicStroke(1F));
        for (int block = 0; block <= blocks; block++) {
            final int x = left + block * PX;
            g.drawLine(x, top - 12, x, blockBottom);
        }
        g.drawLine(left, blockBottom, left + runWidth, blockBottom);

        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        g.setColor(DIM);
        g.drawString("牌高 12/16 · 吊杆 4/16", left, plateTop + 13);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        g.setColor(LABEL);
        g.drawString(caption, left, blockBottom + 32);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        g.setColor(DIM);
        g.drawString("吊杆跟着牌面两端，不会出界", left, blockBottom + 50);
        return left + runWidth;
    }

    private static void drawSide(Graphics2D g, int left, int top, int blockBottom) {
        final int rodLength = (int) (4F / 16F * PX);
        final int plateTop = blockBottom - (int) (12F / 16F * PX);
        g.setColor(CEILING);
        g.fillRect(left, top - 12, PX, 12);
        final int rodWidth = Math.max(3, PX / 16);
        g.setColor(ROD);
        g.fillRect(left + PX / 2 - rodWidth / 2, plateTop - rodLength, rodWidth, rodLength);
        g.setColor(new Color(0x3A3A3A));
        final int thickness = Math.max(3, PX * 2 / 16);
        g.fillRect(left + PX / 2 - thickness / 2, plateTop, thickness, blockBottom - plateTop);
        g.setColor(GRID);
        g.drawLine(left, top - 12, left, blockBottom);
        g.drawLine(left + PX, top - 12, left + PX, blockBottom);

        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        g.setColor(DIM);
        g.drawString("2/16 厚", left + PX + 4, plateTop + 13);
        g.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        g.setColor(LABEL);
        g.drawString("侧视：居中悬空", left, blockBottom + 32);
    }

    private static List<TrdmHeaderTextures.Badge> badges() {
        return List.of(
                new TrdmHeaderTextures.Badge(0xD4001C, "1号线", "Line 1"),
                new TrdmHeaderTextures.Badge(0x0075C2, "2号线", "Line 2"),
                new TrdmHeaderTextures.Badge(0x2E9E4F, "8号线", "Line 8"));
    }
}
