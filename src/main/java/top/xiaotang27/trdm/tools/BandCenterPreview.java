package top.xiaotang27.trdm.tools;

import top.xiaotang27.trdm.render.TrdmDecalBand;
import top.xiaotang27.trdm.render.TrdmHeaderTextures;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一次性小工具：不开游戏，直接离线烘焙几张装饰带预览图，用来对版面（人字间距、中央图大小）。
 * <p>
 * 用法：{@code ./gradlew previewBand}，输出到 {@code build/preview/band_<比例>_<L|R>.png}
 * （有 slogan 的样式）和 {@code band_only_<比例>_<L|R>.png}（只有人字纹的样式，垫了灰底才看得见）。
 * 中央图从 {@code src/main/resources/assets/trdm/textures/block/band/band_center.png} 读。
 */
public final class BandCenterPreview {

    /** 只有人字纹的样式是透明底，预览时垫一层玻璃灰，方便看效果。 */
    private static final int GLASS_BACKDROP = 0x8A9AA6;

    private BandCenterPreview() {
    }

    public static void main(String[] args) throws Exception {
        final Path centrePath = Path.of("src/main/resources/assets/trdm/textures/block/band/band_center.png");
        TrdmDecalBand.primeCentreFromFile(centrePath);
        System.out.println("centre image: " + (TrdmDecalBand.centreSprite() == null ? "missing" : "loaded from " + centrePath.toAbsolutePath()));

        final Path outDir = Path.of("build/preview");
        Files.createDirectories(outDir);

        // 装饰带按「合并后的一组玻璃」分段，所以这里的比例就是「组里几格玻璃」：
        // 宽 = 格数 - 两端各 1/16，高 = 1/6 格 → 宽高比 = (格数 - 0.125) / (1/6) = 6 × 格数 - 0.75
        // 1 格 5.25 / 2 格 11.25 / 3 格 17.25 / 6 格 35.25
        final float[] aspects = {5.25F, 11.25F, 17.25F, 35.25F};
        final int[] colors = {0x8E2A9C, 0x0075C2, 0xD4001C};
        for (final float aspect : aspects) {
            for (final boolean arrowLeft : new boolean[]{true, false}) {
                final TrdmHeaderTextures.TextureSize size = TrdmHeaderTextures.bandTextureSize(aspect);
                final int color = colors[arrowLeft ? 0 : 1];
                final String suffix = String.format("band_%s_%s.png",
                        String.format("%.2f", aspect).replace('.', '_'), arrowLeft ? "L" : "R");

                final BufferedImage withSlogan = TrdmDecalBand.preview(size.width(), size.height(), color, arrowLeft, false);
                write(outDir, suffix, withSlogan);
                final BufferedImage chevronsOnly = TrdmDecalBand.preview(size.width(), size.height(), color, arrowLeft, true);
                write(outDir, "only_" + suffix, onGlass(chevronsOnly));
            }
        }
    }

    private static BufferedImage onGlass(BufferedImage band) {
        final BufferedImage out = new BufferedImage(band.getWidth(), band.getHeight(), BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = out.createGraphics();
        graphics.setColor(new Color(GLASS_BACKDROP));
        graphics.fillRect(0, 0, out.getWidth(), out.getHeight());
        graphics.drawImage(band, 0, 0, null);
        graphics.dispose();
        return out;
    }

    private static void write(Path outDir, String name, BufferedImage image) throws Exception {
        final Path target = outDir.resolve(name);
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + image.getWidth() + "x" + image.getHeight());
    }
}
