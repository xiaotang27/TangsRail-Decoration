package top.xiaotang27.trdm.tools;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 一次性小工具：把一张参考图按「名字 x y w h 倍数」的清单裁成若干放大裁剪图，用来核对排版比例。
 * <p>
 * 用法（{@code ./gradlew cropImage}）：清单文件 {@code build/preview/crops.txt}，
 * 第一行 {@code input <图片路径>} 指定输入，其后每行 {@code 名字 x y 宽 高 放大倍数}，{@code #} 开头为注释。
 * 只在本地跑，游戏运行时用不到。
 */
public final class ImageCrop {

    private ImageCrop() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: ImageCrop <specFile> <outDir>");
            return;
        }
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

        final Path outDir = Path.of(args[1]);
        Files.createDirectories(outDir);

        for (final String raw : lines) {
            final String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("input ")) {
                continue;
            }
            final String[] parts = line.split("\\s+");
            if (parts.length < 6) {
                System.out.println("skip: " + line);
                continue;
            }
            final String name = parts[0];
            final int x = Integer.parseInt(parts[1]);
            final int y = Integer.parseInt(parts[2]);
            final int w = Math.min(Integer.parseInt(parts[3]), source.getWidth() - x);
            final int h = Math.min(Integer.parseInt(parts[4]), source.getHeight() - y);
            final int scale = Integer.parseInt(parts[5]);
            if (w <= 0 || h <= 0) {
                System.out.println("empty crop: " + line);
                continue;
            }
            final BufferedImage crop = source.getSubimage(x, y, w, h);
            final BufferedImage scaled = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
            final java.awt.Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(crop, 0, 0, w * scale, h * scale, null);
            graphics.dispose();
            final Path target = outDir.resolve(name + ".png");
            ImageIO.write(scaled, "PNG", target.toFile());
            System.out.println("wrote " + target.toAbsolutePath() + "  " + w + "x" + h + " x" + scale);
        }
    }
}
