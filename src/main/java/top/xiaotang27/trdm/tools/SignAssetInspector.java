package top.xiaotang27.trdm.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * 一次性小工具：把 {@code assets/trdm/textures/block/sign/} 下的素材全读一遍，
 * 打印尺寸、透明度统计和一张粗粒度的「墨水遮罩」示意图 —— 用来确认这些 PNG 到底是
 * 「透明背景 + 图形」还是「不透明白底 + 图形」，渲染端要按这个决定按 alpha 还是按亮度取蒙版。
 * <p>
 * 只在本地跑（{@code ./gradlew inspectSignAssets}），游戏运行时用不到。
 */
public final class SignAssetInspector {

    private SignAssetInspector() {
    }

    public static void main(String[] args) throws Exception {
        final Path root = Path.of(args.length > 0 ? args[0] : "src/main/resources/assets/trdm/textures/block/sign");
        final File[] files = root.toFile().listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null) {
            System.out.println("No such directory: " + root.toAbsolutePath());
            return;
        }
        final List<File> sorted = Arrays.asList(files);
        sorted.sort((a, b) -> a.getName().compareTo(b.getName()));
        for (final File file : sorted) {
            inspect(file);
        }
    }

    private static void inspect(File file) throws Exception {
        final BufferedImage image = ImageIO.read(file);
        if (image == null) {
            System.out.println("== " + file.getName() + " (unreadable)");
            return;
        }
        final int width = image.getWidth();
        final int height = image.getHeight();
        int transparent = 0;
        int opaque = 0;
        int white = 0;
        int black = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int argb = image.getRGB(x, y);
                final int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 8) {
                    transparent++;
                } else if (alpha > 247) {
                    opaque++;
                }
                if (alpha > 8) {
                    final int r = (argb >> 16) & 0xFF;
                    final int g = (argb >> 8) & 0xFF;
                    final int b = argb & 0xFF;
                    if (r > 240 && g > 240 && b > 240) {
                        white++;
                    } else if (r < 40 && g < 40 && b < 40) {
                        black++;
                    }
                }
            }
        }
        final int total = width * height;
        System.out.println("== " + file.getName() + "  " + width + "x" + height + "  type=" + image.getType()
                + "  alpha=" + (image.getColorModel().hasAlpha())
                + "  transparent=" + pct(transparent, total)
                + "  opaque=" + pct(opaque, total)
                + "  white=" + pct(white, total)
                + "  black=" + pct(black, total)
                + "  bytes=" + Files.size(file.toPath()));
        // 32 列 ASCII：'#' = 不透明暗色，'+' = 不透明非白，'.' = 不透明白，' ' = 透明
        final int cols = 32;
        final int rows = Math.max(1, Math.round((float) cols * height / width / 2F));
        for (int row = 0; row < rows; row++) {
            final StringBuilder builder = new StringBuilder();
            for (int col = 0; col < cols; col++) {
                final int x = Math.min(width - 1, (int) ((col + 0.5F) * width / cols));
                final int y = Math.min(height - 1, (int) ((row + 0.5F) * height / rows));
                final int argb = image.getRGB(x, y);
                final int alpha = (argb >>> 24) & 0xFF;
                if (alpha < 8) {
                    builder.append(' ');
                    continue;
                }
                final int r = (argb >> 16) & 0xFF;
                final int g = (argb >> 8) & 0xFF;
                final int b = argb & 0xFF;
                if (r > 240 && g > 240 && b > 240) {
                    builder.append('.');
                } else if (r < 60 && g < 60 && b < 60) {
                    builder.append('#');
                } else {
                    builder.append('+');
                }
            }
            System.out.println("   |" + builder + "|");
        }
    }

    private static String pct(int part, int total) {
        return String.format("%.1f%%", 100.0 * part / Math.max(1, total));
    }
}
