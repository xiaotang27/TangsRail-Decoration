package top.xiaotang27.trdm.tools;

import top.xiaotang27.trdm.render.TrdmHeaderTextures;
import top.xiaotang27.trdm.render.TrdmPisAssets;
import top.xiaotang27.trdm.render.TrdmPisPanel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一次性小工具：不开游戏，用固定假数据把 PIS 屏幕整块烘出来（16:9，136×72 画布放大 8 倍），
 * 用来和 JCM 版（MTR4）逐项对比版面：到站倒计时、开往站、时钟日期、广告位、跑马灯、车厢图。
 * <p>
 * 用法：{@code ./gradlew previewPis}，输出到 {@code build/preview/pis_*.png}。
 */
public final class PisPreview {

    /** 画布放大倍数（136×72 → 1088×576）。 */
    private static final int SCALE = 8;

    private PisPreview() {
    }

    public static void main(String[] args) throws Exception {
        final Path assets = Path.of("src/main/resources/assets/trdm/textures/block/pis");
        // 预览也用打进 jar 的思源黑体，和游戏里一致
        TrdmHeaderTextures.primeBundledFont(Path.of("src/main/resources/assets/trdm/font/sourcehansanshwsc-vf.ttf"));
        TrdmPisAssets.primeFromDirectory(assets);
        System.out.println("background: " + (TrdmPisAssets.image(TrdmPisAssets.BACKGROUND) == null ? "missing" : "loaded"));
        System.out.println("cars: " + (TrdmPisAssets.image(TrdmPisAssets.CAR_HEAD) == null ? "missing" : "loaded"));
        System.out.println("ad: " + (TrdmPisAssets.image(TrdmPisAssets.AD_DEFAULT) == null ? "missing" : "loaded"));

        final Path outDir = Path.of("build/preview");
        Files.createDirectories(outDir);

        // 量一下字体的实际墨水比例（换字体后字号观感会变，靠这个把版面比例钉死）
        final java.awt.image.BufferedImage probe = new java.awt.image.BufferedImage(1, 1,
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D probeGraphics = probe.createGraphics();
        final java.awt.Font probeFont = TrdmHeaderTextures.pisFont(100F).deriveFont(java.awt.Font.BOLD);
        final java.awt.FontMetrics probeMetrics = probeGraphics.getFontMetrics(probeFont);
        final java.awt.geom.Rectangle2D bounds = probeMetrics.getStringBounds("0分钟", probeGraphics);
        System.out.printf("pis font=%s pisInkRatio=%.3f | 站名牌 font=%s inkRatio=%.3f%n",
                TrdmHeaderTextures.pisFont(100F).getFontName(), TrdmHeaderTextures.pisInkRatio(),
                TrdmHeaderTextures.font(100F).getFontName(), TrdmHeaderTextures.inkRatio());
        probeGraphics.dispose();

        final int width = TrdmPisPanel.CANVAS_WIDTH * SCALE;
        final int height = TrdmPisPanel.CANVAS_HEIGHT * SCALE;
        final BufferedImage ad = TrdmPisAssets.image(TrdmPisAssets.AD_DEFAULT);
        final BufferedImage head = TrdmPisAssets.image(TrdmPisAssets.CAR_HEAD);
        final BufferedImage middle = TrdmPisAssets.image(TrdmPisAssets.CAR_MIDDLE);
        final BufferedImage tail = TrdmPisAssets.image(TrdmPisAssets.CAR_TAIL);

        // 1) 正常：两趟车都是 N 分钟（跑马灯停在小数偏移上，验证是连续平移不是跳字）
        write(outDir, "pis_far", width, height, ad, head, middle, tail, new TrdmPisPanel.View(
                new TrdmPisPanel.Arrival(15 * 60, "梅江会展中心|MEIJIANG HUIZHANZHONGXIN", 6),
                new TrdmPisPanel.Arrival(22 * 60, "梅江会展中心|MEIJIANG HUIZHANZHONGXIN", 6),
                "20:31", "2026/02/14 星期六",
                "欢迎乘坐天津地铁 6 号线，请先下后上，注意脚下安全。      (C)TRTIMC 2026", 42.7F));

        // 2) 本车即将进站、下次 3 分钟
        write(outDir, "pis_arriving", width, height, ad, head, middle, tail, new TrdmPisPanel.View(
                new TrdmPisPanel.Arrival(30, "南何庄|NANHEZHUANG", 4),
                new TrdmPisPanel.Arrival(3 * 60, "南何庄|NANHEZHUANG", 4),
                "07:05", "2026/02/15 星期日",
                "本次列车即将进站，请站在黄线以内候车。      (C)TRTIMC 2026", 120F));

        // 3) 已到站
        write(outDir, "pis_at_platform", width, height, ad, head, middle, tail, new TrdmPisPanel.View(
                new TrdmPisPanel.Arrival(0, "西站|XI ZHAN", 8),
                new TrdmPisPanel.Arrival(9 * 60, "西站|XI ZHAN", 8),
                "23:58", "2026/02/14 星期六",
                "末班车运营时间请留意车站公告。      (C)TRTIMC 2026", 200F));
    }

    private static void write(Path outDir, String name, int width, int height, BufferedImage ad,
                              BufferedImage head, BufferedImage middle, BufferedImage tail,
                              TrdmPisPanel.View view) throws Exception {
        final BufferedImage image = TrdmPisPanel.render(width, height, view, ad, head, middle, tail);
        final Path target = outDir.resolve(name + ".png");
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + width + "x" + height);
    }
}
