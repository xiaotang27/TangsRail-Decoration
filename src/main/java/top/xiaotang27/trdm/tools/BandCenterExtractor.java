package top.xiaotang27.trdm.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一次性小工具：从参考图里抠出装饰带中央那张「文字 + logo」图，去掉洋红底色存成透明底 PNG。
 * <p>
 */
public final class BandCenterExtractor {

    /** 与底色的距离 ≥ 这个值就当作完全不透明。 */
    private static final double ALPHA_EDGE = 70D;
    private static final int INK_THRESHOLD = 40;

    private BandCenterExtractor() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: BandCenterExtractor <input> <output> [x0,y0,x1,y1]");
            return;
        }
        final BufferedImage source = ImageIO.read(new File(args[0]));
        if (source == null) {
            System.out.println("Cannot read: " + args[0]);
            return;
        }
        int x0 = 0;
        int y0 = 0;
        int x1 = source.getWidth();
        int y1 = source.getHeight();
        if (args.length > 2 && !args[2].isBlank()) {
            final String[] parts = args[2].split(",");
            x0 = Math.max(0, Integer.parseInt(parts[0].trim()));
            y0 = Math.max(0, Integer.parseInt(parts[1].trim()));
            x1 = Math.min(source.getWidth(), Integer.parseInt(parts[2].trim()));
            y1 = Math.min(source.getHeight(), Integer.parseInt(parts[3].trim()));
        }

        final int background = source.getRGB(x0 + 1, y0 + 1) & 0xFFFFFF;
        System.out.printf("source %dx%d region [%d,%d - %d,%d] background #%06X%n",
                source.getWidth(), source.getHeight(), x0, y0, x1, y1, background);

        final int width = x1 - x0;
        final int height = y1 - y0;
        final int[] pixels = new int[width * height];
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int rgb = source.getRGB(x0 + x, y0 + y) & 0xFFFFFF;
                final double distance = Math.sqrt(distance(rgb, background));
                final double alpha = Math.min(1D, distance / ALPHA_EDGE);
                if (alpha <= 0.04D) {
                    pixels[y * width + x] = 0;
                    continue;
                }
                final int r = unblend((rgb >> 16) & 0xFF, (background >> 16) & 0xFF, alpha);
                final int g = unblend((rgb >> 8) & 0xFF, (background >> 8) & 0xFF, alpha);
                final int b = unblend(rgb & 0xFF, background & 0xFF, alpha);
                pixels[y * width + x] = ((int) Math.round(alpha * 255) << 24) | (r << 16) | (g << 8) | b;
                if (distance >= INK_THRESHOLD) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX) {
            System.out.println("nothing to extract");
            return;
        }

        final int pad = 2;
        final int cropX = Math.max(0, minX - pad);
        final int cropY = Math.max(0, minY - pad);
        final int cropW = Math.min(width - cropX, maxX - minX + 1 + pad * 2);
        final int cropH = Math.min(height - cropY, maxY - minY + 1 + pad * 2);
        final BufferedImage result = new BufferedImage(cropW, cropH, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < cropH; y++) {
            for (int x = 0; x < cropW; x++) {
                result.setRGB(x, y, pixels[(cropY + y) * width + cropX + x]);
            }
        }

        final Path target = Path.of(args[1]);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        ImageIO.write(result, "PNG", target.toFile());
        System.out.printf("wrote %s  %dx%d  aspect %.3f%n", target.toAbsolutePath(), cropW, cropH, cropW / (float) cropH);
    }

    private static int unblend(int value, int background, double alpha) {
        final double result = (value - background * (1D - alpha)) / alpha;
        return (int) Math.max(0, Math.min(255, Math.round(result)));
    }

    private static int distance(int a, int b) {
        final int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        final int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        final int db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db;
    }
}
