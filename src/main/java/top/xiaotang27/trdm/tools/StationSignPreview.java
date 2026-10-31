package top.xiaotang27.trdm.tools;

import top.xiaotang27.trdm.render.TrdmStationSignPanel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一次性小工具：不开游戏，直接烘焙几张站名牌预览图，用来核对排版
 * （本站大字 + 拼音、线路色带、左下「下一站」、右下下一站名）。
 * <p>
 * 用法：{@code ./gradlew previewStationSign}，输出 {@code build/preview/station_sign*.png}。
 */
public final class StationSignPreview {

    /** 和 RenderTrdmStationSign 里一致的牌面像素尺寸（1 格 × 3/4 格 = 4:3）。 */
    private static final int WIDTH = 512;
    private static final int HEIGHT = 384;

    private StationSignPreview() {
    }

    public static void main(String[] args) throws Exception {
        final Path outDir = Path.of("build/preview");
        Files.createDirectories(outDir);

        write(outDir, "station_sign", TrdmStationSignPanel.render(WIDTH, HEIGHT,
                "新开河|XINKAIHE", "北站|BEIZHAN", 0x9C2A8C, false, "下一站", "Next Station"));
        write(outDir, "station_sign_terminal", TrdmStationSignPanel.render(WIDTH, HEIGHT,
                "塘铁机场|TANGTIE AIRPORT", null, 0x0075C2, true, "终点站", "Terminal Station"));
        write(outDir, "station_sign_bilingual", TrdmStationSignPanel.render(WIDTH, HEIGHT,
                "人民广场|PEOPLE'S SQUARE", "南京东路|EAST NANJING ROAD", 0xD4001C, false, "下一站", "Next Station"));
    }

    private static void write(Path outDir, String name, BufferedImage image) throws Exception {
        final Path target = outDir.resolve(name + ".png");
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + image.getWidth() + "x" + image.getHeight());
    }
}
