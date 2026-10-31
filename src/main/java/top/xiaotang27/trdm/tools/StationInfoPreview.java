package top.xiaotang27.trdm.tools;

import top.xiaotang27.trdm.render.TrdmHeaderTextures;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一次性小工具：离线烘焙「开往 / 下一站」站点信息吊板，用来核对长玻璃段下有没有被横向拉伸。
 * <p>
 * 用法：{@code ./gradlew previewStationInfo}，输出 {@code build/preview/info_<格数>_<L|R>.png}，
 * 并把每块的贴图尺寸和宽高比打在控制台上（宽高比应当等于吊板宽高比）。
 */
public final class StationInfoPreview {

    /** 和渲染端一致：SIDE_PADDING 两边各让 0.125/16，高度是 1 − TOP_PADDING − BOTTOM_PADDING。 */
    private static final float SIDE_PADDING = 0.125F / 16F;
    private static final float BAND_HEIGHT = 7F / 16F;

    private StationInfoPreview() {
    }

    public static void main(String[] args) throws Exception {
        final Path outDir = Path.of("build/preview");
        Files.createDirectories(outDir);

        for (int blocks = 2; blocks <= 8; blocks++) {
            final float aspect = (blocks - SIDE_PADDING * 2F) / BAND_HEIGHT;
            for (final boolean arrowLeft : new boolean[]{true, false}) {
                final BufferedImage image = TrdmHeaderTextures.stationInfoImage(
                        "曹庄|CaoZhuang", "天津站|Tianjinzhan", arrowLeft, blocks, aspect);
                final Path target = outDir.resolve(String.format("info_%d_%s.png", blocks, arrowLeft ? "L" : "R"));
                ImageIO.write(image, "PNG", target.toFile());
                System.out.printf("%d 格  aspect %.3f  ->  %dx%d  (贴图宽高比 %.3f)%n",
                        blocks, aspect, image.getWidth(), image.getHeight(), image.getWidth() / (float) image.getHeight());
            }
        }
    }
}
