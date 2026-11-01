package top.xiaotang27.trdm.tools;

import org.jcodec.api.awt.AWTSequenceEncoder;
import top.xiaotang27.trdm.render.TrdmHeaderTextures;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 生成 PIS 广告示例资源包：两张 png + 一个 mp4，外面套好 {@code pis_ads.json} 和 {@code pack.mcmeta}。
 * <p>
 */
public final class SamplePackGenerator {

    /** 广告区成品尺寸（5:3，和 PIS 版面里的广告框一致）。 */
    private static final int AD_WIDTH = 720;
    private static final int AD_HEIGHT = 432;
    /** 视频尺寸（同样是 5:3，小一点解码快）。 */
    private static final int VIDEO_WIDTH = 320;
    private static final int VIDEO_HEIGHT = 192;
    /** 视频帧率 / 时长。 */
    private static final int VIDEO_FPS = 15;
    private static final int VIDEO_SECONDS = 4;

    private static final Path FONT = Path.of("src/main/resources/assets/trdm/font/sourcehansanshwsc-vf.ttf");

    private SamplePackGenerator() {
    }

    public static void main(String[] args) throws Exception {
        final Path root = Path.of(args.length > 0 ? args[0] : "build/sample-pack/trdm-pis-sample");
        TrdmHeaderTextures.primeBundledFont(FONT);
        System.out.println("font = " + TrdmHeaderTextures.pisFont(24F).getFontName());

        final Path ads = root.resolve("assets/trdm/textures/block/pis/ads");
        Files.createDirectories(ads);

        writeText(root.resolve("pack.mcmeta"), mcmeta());
        writeText(root.resolve("assets/trdm/pis_ads.json"), adsJson());
        writeText(root.resolve("README.txt"), readme());
        writeImage(ads.resolve("ad_tianjin.png"), tianjin());
        writeImage(ads.resolve("ad_notice.png"), notice());
        writeVideo(ads.resolve("ad_video.mp4"));

        System.out.println("pack -> " + root.toAbsolutePath());
        Files.walk(root).filter(Files::isRegularFile).sorted().forEach(path ->
                System.out.println("  " + root.relativize(path) + "  " + size(path) + " bytes"));
    }

    // ===== 资源包元数据 =====

    /** 1.20.1 的 pack_format 是 15。 */
    private static String mcmeta() {
        return """
                {
                  "pack": {
                    "pack_format": 15,
                    "description": "TRDM PIS 示例广告（2 张 png + 1 个 mp4）"
                  }
                }
                """;
    }

    private static String adsJson() {
        return """
                {
                  "pis_ads": {
                    "tianjin": { "name": "ad_tianjin.png", "time": 6000 },
                    "notice": { "name": "ad_notice.png", "time": 6000 },
                    "video": { "name": "ad_video.mp4", "time": 8000 }
                  }
                }
                """;
    }

    private static String readme() {
        return """
                TRDM PIS 示例广告资源包
                ==============================

                安装
                -----
                1. 把整个 trdm-pis-sample 文件夹（或同名 .zip）丢进 .minecraft/resourcepacks/
                2. 游戏里 选项 -> 资源包，把「TRDM PIS 示例广告」放到启用列表
                3. 刷子右键任意 PIS 屏幕 -> 广告图设置 里就能看到 3 条广告

                里面有什么
                -----------
                assets/trdm/pis_ads.json                     广告清单（播放顺序 = json 顺序）
                assets/trdm/textures/block/pis/ads/ad_tianjin.png   720x432 静态图
                assets/trdm/textures/block/pis/ads/ad_notice.png    720x432 静态图
                assets/trdm/textures/block/pis/ads/ad_video.mp4     320x192 / 15fps / 4s 视频

                自己做广告
                -----------
                * 图片：放进 ads/ 目录，png / gif 都行；建议 720x432（5:3），别的比例会被拉伸。
                * gif：直接写 .gif，按它自己的帧间隔循环，最高 30fps。
                * 视频：直接写 .mp4（H.264 / MP4），建议 <= 640x360、<= 30fps；
                  视频是实时解码的，界面上会标「⚠ 视频」，对性能影响比静态图大。
                * 每条广告的 time 是它播多久（毫秒），到点换下一条。
                * 改完在游戏里刷子右键 -> 「重新扫描」即可生效，不用重开。

                注意
                -----
                pis_ads.json 的键（tianjin / notice / video）就是屏幕设置界面里显示的名字，
                可以随便改；name 只写文件名，图片必须和 json 里的文件名一致。
                """;
    }

    // ===== 两张 png =====

    /** 一张蓝底「天津地铁」风格广告。 */
    private static BufferedImage tianjin() {
        final BufferedImage image = new BufferedImage(AD_WIDTH, AD_HEIGHT, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setPaint(new GradientPaint(0, 0, new Color(0x0B4FB3), AD_WIDTH, AD_HEIGHT, new Color(0x1F8CE0)));
        graphics.fillRect(0, 0, AD_WIDTH, AD_HEIGHT);

        // 右下角浅色装饰圆
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.16F));
        graphics.setColor(Color.WHITE);
        graphics.fillOval(AD_WIDTH - 210, AD_HEIGHT - 250, 420, 420);
        graphics.setComposite(AlphaComposite.SrcOver);

        graphics.setColor(Color.WHITE);
        drawText(graphics, "天津地铁", 104F, 56, 150, Font.BOLD);
        drawText(graphics, "TIANJIN METRO", 30F, 60, 200, Font.PLAIN);
        drawText(graphics, "欢迎乘坐  ·  请先下后上", 34F, 60, 292, Font.PLAIN);

        // 底部细条
        graphics.setColor(new Color(0xFFFFFF));
        graphics.fillRect(56, AD_HEIGHT - 78, AD_WIDTH - 112, 3);
        graphics.setColor(new Color(0xDCE9FF));
        drawText(graphics, "示例广告 · 制作：TRTIMC 2026", 24F, 60, AD_HEIGHT - 48, Font.PLAIN);

        graphics.dispose();
        return image;
    }

    /** 一张浅底「乘车提示」风格广告。 */
    private static BufferedImage notice() {
        final BufferedImage image = new BufferedImage(AD_WIDTH, AD_HEIGHT, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(0xF4F6FA));
        graphics.fillRect(0, 0, AD_WIDTH, AD_HEIGHT);

        // 左侧色带
        graphics.setColor(new Color(0xE4002B));
        graphics.fillRect(0, 0, 18, AD_HEIGHT);
        graphics.setColor(new Color(0xFFC845));
        graphics.fillRect(18, 0, 8, AD_HEIGHT);

        graphics.setColor(new Color(0x1B1F27));
        drawText(graphics, "乘车提示", 76F, 84, 130, Font.BOLD);
        graphics.setColor(new Color(0x40474F));
        drawText(graphics, "请先下后上，注意站台间隙", 40F, 86, 200, Font.PLAIN);
        drawText(graphics, "严禁携带危险品进站乘车", 40F, 86, 254, Font.PLAIN);

        // 右下角小圆标（里面两个字要真的居中在圆里）
        final int circleSize = 104;
        final int circleX = AD_WIDTH - circleSize - 40;
        final int circleY = AD_HEIGHT - circleSize - 34;
        graphics.setColor(new Color(0xE4002B));
        graphics.fillOval(circleX, circleY, circleSize, circleSize);
        graphics.setColor(Color.WHITE);
        drawTextCentred(graphics, "安全", 34F, circleX + circleSize / 2F, circleY + circleSize / 2F, Font.BOLD);

        graphics.setColor(new Color(0x8A9099));
        drawText(graphics, "示例广告 · ad_notice.png", 22F, 88, AD_HEIGHT - 46, Font.PLAIN);

        graphics.dispose();
        return image;
    }

    // ===== 一个 mp4 =====

    /**
     * 4 秒 / 15fps 的 H.264 mp4：底部一条来回跑的色带 + 滚动字幕，一眼能看出是在播视频。
     * 用 JCodec 自己的编码器写，解出来就是 mod 里那套解码链路。
     */
    private static void writeVideo(Path file) throws Exception {
        // JCodec 的 writableFileChannel 是 CREATE+WRITE，不会截断已有文件（反复生成会一直往后追加，
        // 之前就把示例 mp4 追加成了 190 多 MB 的坏文件），所以先删掉再写。
        Files.createDirectories(file.getParent());
        Files.deleteIfExists(file);
        final int frames = VIDEO_FPS * VIDEO_SECONDS;
        final AWTSequenceEncoder encoder = AWTSequenceEncoder.createSequenceEncoder(file.toFile(), VIDEO_FPS);
        try {
            for (int index = 0; index < frames; index++) {
                final BufferedImage image = new BufferedImage(VIDEO_WIDTH, VIDEO_HEIGHT, BufferedImage.TYPE_INT_RGB);
                final Graphics2D graphics = image.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setPaint(new GradientPaint(0, 0, new Color(0x10243F), VIDEO_WIDTH, VIDEO_HEIGHT,
                        new Color(0x1F6FA8)));
                graphics.fillRect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT);

                // 顶部进度条
                final float progress = (index + 1) / (float) frames;
                graphics.setColor(new Color(0xFFFFFF));
                graphics.fillRect(0, 0, Math.round(VIDEO_WIDTH * progress), 4);

                graphics.setColor(Color.WHITE);
                drawText(graphics, "视频广告示例", 42F, 24, 82, Font.BOLD);
                drawText(graphics, "ad_video.mp4 · 15fps · 320x192", 16F, 26, 112, Font.PLAIN);

                // 底部来回跑的色带
                final double phase = Math.sin(index / (double) frames * Math.PI * 2.0);
                final int bandWidth = 96;
                final int bandX = (int) Math.round((VIDEO_WIDTH - bandWidth) / 2.0 + phase * (VIDEO_WIDTH - bandWidth) / 2.0);
                graphics.setColor(new Color(0xFFD24A));
                graphics.fillRect(bandX, VIDEO_HEIGHT - 58, bandWidth, 12);
                graphics.setColor(new Color(0xFF5A3C));
                graphics.fillRect(VIDEO_WIDTH - bandX - bandWidth, VIDEO_HEIGHT - 30, bandWidth, 6);

                // 秒数
                graphics.setColor(new Color(0xBFE0FF));
                drawText(graphics, String.format("%.1fs", (index + 1) / (float) VIDEO_FPS), 20F,
                        VIDEO_WIDTH - 62, 28, Font.PLAIN);

                graphics.dispose();
                encoder.encodeImage(image);
            }
        } finally {
            encoder.finish();
        }
        System.out.println("video frames = " + frames);
    }

    // ===== 小工具 =====

    private static void drawText(Graphics2D graphics, String text, float size, float x, float baseline, int style) {
        graphics.setFont(TrdmHeaderTextures.pisFont(size).deriveFont(style));
        graphics.drawString(text, x, baseline);
    }

    private static void drawTextCentred(Graphics2D graphics, String text, float size, float centerX, float centerY,
                                        int style) {
        final Font font = TrdmHeaderTextures.pisFont(size).deriveFont(style);
        graphics.setFont(font);
        final FontMetrics metrics = graphics.getFontMetrics(font);
        final float width = metrics.stringWidth(text);
        // 按「字形墨水高度」居中，不按行高（否则中文字会明显偏上）
        final float ink = (float) (size * TrdmHeaderTextures.pisInkRatio());
        graphics.drawString(text, centerX - width / 2F, centerY + ink * 0.42F);
    }

    private static void writeText(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static void writeImage(Path file, BufferedImage image) throws Exception {
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            return -1;
        }
    }

    static {
        // 让 BasicStroke 的 import 不被优化掉（示例里不用描边，但留着方便二次编辑）
        new BasicStroke(1F);
    }
}
