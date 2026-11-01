package top.xiaotang27.trdm.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * 一次性小工具：量 PIS 背景图（tjpids.png，1920×1080 设计稿）里
 * 「天津地铁欢迎您」那行白字所占的水平范围，算出<b>「铁」字的中心 x</b>，
 */
public final class PisMeasure {

    private PisMeasure() {
    }

    public static void main(String[] args) throws Exception {
        final Path file = Path.of("src/main/resources/assets/trdm/textures/block/pis/tjpids.png");
        final BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            System.out.println("missing " + file.toAbsolutePath());
            return;
        }
        final int width = image.getWidth();
        final int height = image.getHeight();
        final double unit = height / 72D;   // 1080 / 72 = 15 设计像素每画布单位
        System.out.println("background " + width + "x" + height + "  unit=" + unit + " px");

        // 逐行打印，先看清楚每一带白字在哪（调试用）；注意这里是**设计像素**，不乘 unit
        for (int y = 10; y < 240 && y < height; y += 10) {
            int rowMin = width;
            int rowMax = -1;
            for (int x = 0; x < Math.min(width, 700); x++) {
                final int argb = image.getRGB(x, y);
                final int r = (argb >> 16) & 0xFF;
                final int g = (argb >> 8) & 0xFF;
                final int b = argb & 0xFF;
                final int a = (argb >>> 24) & 0xFF;
                if (a > 8 && r > 185 && g > 185 && b > 185) {
                    rowMin = Math.min(rowMin, x);
                    rowMax = Math.max(rowMax, x);
                }
            }
            if (rowMax >= rowMin) {
                System.out.printf("  y=%4d  white x=%4d..%4d  (%.2f..%.2f units, centre %.2f)%n",
                        y, rowMin, rowMax, rowMin / unit, rowMax / unit, (rowMin + rowMax) / 2D / unit);
            }
        }

        // 欢迎语那一行：设计稿 y 大约 20..170（放宽一点，别把字的边缘漏掉）
        int minX = width;
        int maxX = -1;
        for (int y = 20; y < 170 && y < height; y++) {
            for (int x = 0; x < Math.min(width, 700); x++) {
                final int argb = image.getRGB(x, y);
                final int r = (argb >> 16) & 0xFF;
                final int g = (argb >> 8) & 0xFF;
                final int b = argb & 0xFF;
                if (r > 185 && g > 185 && b > 185) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
        }
        if (maxX < minX) {
            System.out.println("welcome text not found");
            return;
        }
        final double centrePx = (minX + maxX) / 2D;
        System.out.printf("welcome text x=%d..%d  centre=%.1f px = %.2f canvas units%n",
                minX, maxX, centrePx, centrePx / unit);
    }
}
