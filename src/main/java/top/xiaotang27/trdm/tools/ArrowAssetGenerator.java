package top.xiaotang27.trdm.tools;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * 生成吊板用的图标素材（{@code assets/trdm/textures/block/sign/}）：
 * <ul>
 */
public final class ArrowAssetGenerator {

    private ArrowAssetGenerator() {
    }

    public static void main(String[] args) throws Exception {
        final Path root = Path.of(args.length > 0 ? args[0] : "src/main/resources/assets/trdm/textures/block/sign");
        write(root.resolve("arrow.png"), 352, 213, ArrowAssetGenerator::drawArrow);
        write(root.resolve("subway.png"), 615, 621, ArrowAssetGenerator::drawSubway);
    }

    private static void write(Path target, int width, int height, ShapeDrawer drawer) throws Exception {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        drawer.draw(graphics, width, height);
        graphics.dispose();

        if (target.getParent() != null) {
            target.getParent().toFile().mkdirs();
        }
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("Generated icon asset: " + target.toAbsolutePath());
    }

    /**
     * 箭头朝右：满高大三角头占全长 53%，杆厚占高度的 40%。
     */
    private static void drawArrow(Graphics2D graphics, int width, int height) {
        final int headWidth = Math.round(width * 0.53F);
        final int halfShaft = Math.round(height * 0.20F);
        final int centerY = height / 2;
        graphics.fillPolygon(
                new int[]{width, width - headWidth, width - headWidth, 0, 0, width - headWidth, width - headWidth},
                new int[]{centerY, 0, centerY - halfShaft, centerY - halfShaft, centerY + halfShaft, centerY + halfShaft, height},
                7);
    }

    /**
     * 地铁标识：拱形隧道 + 正面列车（车顶灯、车窗、两个车灯都是挖空）。
     */
    private static void drawSubway(Graphics2D graphics, int width, int height) {
        final Area shape = new Area();

        // 拱门：外圈圆角矩形减去内圈，内圈一直挖到画面底部 → 两条腿 + 顶部圆弧
        final Area arch = new Area(new RoundRectangle2D.Float(
                width * 0.05F, height * 0.055F, width * 0.90F, height * 0.84F, width * 0.90F, height * 0.84F));
        arch.subtract(new Area(new RoundRectangle2D.Float(
                width * 0.145F, height * 0.155F, width * 0.71F, height * 0.92F, width * 0.71F, height * 0.74F)));
        shape.add(arch);

        // 车厢
        shape.add(new Area(new RoundRectangle2D.Float(
                width * 0.315F, height * 0.295F, width * 0.37F, height * 0.50F, width * 0.07F, height * 0.07F)));

        // 车顶灯（挖空）
        shape.subtract(new Area(new RoundRectangle2D.Float(
                width * 0.415F, height * 0.305F, width * 0.17F, height * 0.055F, width * 0.055F, height * 0.055F)));
        // 车窗（挖空）
        shape.subtract(new Area(new RoundRectangle2D.Float(
                width * 0.35F, height * 0.375F, width * 0.30F, height * 0.145F, width * 0.02F, height * 0.02F)));
        // 两个车灯（挖空）
        final double lampRadius = width * 0.05F;
        shape.subtract(new Area(new Ellipse2D.Double(width * 0.395F - lampRadius, height * 0.635F - lampRadius, lampRadius * 2, lampRadius * 2)));
        shape.subtract(new Area(new Ellipse2D.Double(width * 0.605F - lampRadius, height * 0.635F - lampRadius, lampRadius * 2, lampRadius * 2)));

        // 车厢底下的两段轨道
        shape.add(new Area(new RoundRectangle2D.Float(
                width * 0.345F, height * 0.795F, width * 0.31F, height * 0.03F, width * 0.02F, height * 0.02F)));

        graphics.fill(shape);
    }

    interface ShapeDrawer {
        void draw(Graphics2D graphics, int width, int height);
    }
}
