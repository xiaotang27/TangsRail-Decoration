package top.xiaotang27.trdm.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 一次性小工具：沿水平/垂直方向扫一条线，把颜色按类归并成「区间」打印出来，
 * 用来精确量参考图里线宽、圆点直径、站距、色块位置这些尺寸（不靠眼睛估）。
 */
public final class PixelScan {

    private PixelScan() {
    }

    public static void main(String[] args) throws Exception {
        final List<String> lines = Files.readAllLines(Path.of(args[0]));
        String inputPath = null;
        for (final String raw : lines) {
            final String line = raw.trim();
            if (line.startsWith("input ")) {
                inputPath = line.substring(6).trim();
                break;
            }
        }
        if (inputPath == null) {
            System.out.println("spec file needs a line: input <path>");
            return;
        }
        final BufferedImage source = ImageIO.read(new File(inputPath));
        if (source == null) {
            System.out.println("Cannot read image: " + inputPath);
            return;
        }
        System.out.println("Input " + source.getWidth() + "x" + source.getHeight());

        for (final String raw : lines) {
            final String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("input ")) {
                continue;
            }
            final String[] parts = line.split("\\s+");
            if (parts.length < 4) {
                System.out.println("skip: " + line);
                continue;
            }
            final boolean horizontal = parts[0].startsWith("h");
            final int fixed = Integer.parseInt(parts[1]);
            final int from = Integer.parseInt(parts[2]);
            final int to = Integer.parseInt(parts[3]);
            final StringBuilder builder = new StringBuilder(line).append(" -> ");
            String previous = null;
            int runStart = from;
            for (int i = from; i <= to; i++) {
                final int x = horizontal ? i : fixed;
                final int y = horizontal ? fixed : i;
                final String current = classify(source.getRGB(
                        Math.max(0, Math.min(source.getWidth() - 1, x)),
                        Math.max(0, Math.min(source.getHeight() - 1, y))));
                if (previous == null) {
                    previous = current;
                    runStart = i;
                } else if (!previous.equals(current)) {
                    if (i - runStart >= 3) {
                        builder.append('[').append(runStart).append("..").append(i - 1).append(' ').append(previous).append(']');
                    }
                    previous = current;
                    runStart = i;
                }
            }
            if (to - runStart >= 3) {
                builder.append('[').append(runStart).append("..").append(to).append(' ').append(previous).append(']');
            }
            System.out.println(builder);
        }
    }

    private static String classify(int argb) {
        final int r = (argb >> 16) & 0xFF;
        final int g = (argb >> 8) & 0xFF;
        final int b = argb & 0xFF;
        if (r > 235 && g > 235 && b > 235) {
            return "white";
        }
        if (r < 70 && g < 70 && b < 70) {
            return "black";
        }
        if (b > 140 && b - r > 60 && b - g > 30) {
            return "blue";
        }
        if (r > 180 && g < 90 && b < 90) {
            return "red";
        }
        if (r > 190 && g > 150 && b < 110) {
            return "yellow";
        }
        if (r > 90 && b > 120 && r - g > 40 && b - g > 40) {
            return "purple";
        }
        if (Math.abs(r - g) < 22 && Math.abs(g - b) < 22) {
            return g < 200 ? "grey" : "lightgrey";
        }
        return "color";
    }
}
