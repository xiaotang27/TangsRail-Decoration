package top.xiaotang27.trdm.tools;

import top.xiaotang27.trdm.render.TrdmRouteMap;
import top.xiaotang27.trdm.render.TrdmSignAssets;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 离线预览线路图吊板：用样例数据（天津地铁 3 号线）按指定宽高比画一张 PNG 到 {@code build/preview/}，
 * 方便不开游戏也能核对版面比例。
 * <p>
 * 用法：{@code ./gradlew previewRouteMap -Paspect=4.57 -ParrowLeft=true}
 * （不传参数就按参考图的 4.57 宽高比、箭头朝左）。游戏运行时用不到这个类。
 */
public final class RouteMapPreview {

    private static final int ROUTE_COLOR = 0x0075C2;
    private static final int LINE1 = 0xC00000;
    private static final int LINE2 = 0xFFC000;
    private static final int LINE9 = 0x7B2E8F;

    private RouteMapPreview() {
    }

    public static void main(String[] args) throws Exception {
        TrdmSignAssets.primeFromDirectory(Path.of("src/main/resources/assets/trdm/textures/block/sign"));
        System.out.println("transfer_circle_2 圆弧（顺时针）：" + hexPalette(TrdmSignAssets.TRANSFER_2));
        System.out.println("transfer_circle_3 圆弧（顺时针）：" + hexPalette(TrdmSignAssets.TRANSFER_3));

        final boolean arrowLeft = args.length < 2 || Boolean.parseBoolean(args[1]);

        // 站序照「行车方向朝左」排：序号大的在前方（左），所以从右往左填
        final List<TrdmRouteMap.Station> stations = new ArrayList<>();
        stations.add(station("南站", "NANZHAN", false));
        stations.add(station("王顶堤", "WANGDINGDI", false));
        stations.add(station("红旗南路", "HONGQINANLU", true));
        stations.add(station("周邓纪念馆", "ZHOUDENGJINIANGUAN", false));
        stations.add(station("天津站", "TIANJINZHAN", true));
        stations.add(station("津湾广场", "JINWANGGUANGCHANG", false));
        stations.add(station("营口道", "YINGKOUDAO", true));
        stations.add(station("天士力", "TIANSHILI", false));
        stations.add(station("小淀", "XIAODIAN", false));

        final float naturalAspect = TrdmRouteMap.maximumAspect(stations.size(), arrowLeft);
        final float aspect;
        if (args.length > 0 && !args[0].isBlank() && !args[0].equals("natural")) {
            aspect = Float.parseFloat(args[0]);
        } else {
            aspect = naturalAspect;
        }
        System.out.println("固定比例下的宽高比：" + String.format(Locale.ROOT, "%.3f", naturalAspect)
                + "（" + Math.round(naturalAspect * 0.4375F * 16F) / 16F + " 格宽）");

        final TrdmRouteMap.Model model = new TrdmRouteMap.Model(stations, 3, ROUTE_COLOR, "3号线", "Line 3", arrowLeft);

        final int height = 512;
        final int width = Math.max(64, Math.round(height * aspect));
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = image.createGraphics();
        // 预览给白底，游戏里吊板本身不带底色（跟其他吊板一致）
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        TrdmRouteMap.draw(graphics, width, height, model, 0F, 0F);
        graphics.dispose();

        final Path target = Path.of("build/preview/route_map_" + Math.round(aspect) + "_" + (arrowLeft ? "L" : "R") + ".png");
        target.toFile().getParentFile().mkdirs();
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + width + "x" + height);
    }

    private static String hexPalette(String sprite) {
        final StringBuilder builder = new StringBuilder();
        for (final int color : TrdmSignAssets.arcPalette(sprite)) {
            builder.append(String.format("#%06X ", color));
        }
        return builder.toString().trim();
    }

    private static TrdmRouteMap.Station station(String chinese, String english, boolean interchange) {
        final List<TrdmRouteMap.Line> lines = new ArrayList<>();
        if (interchange) {
            switch (chinese) {
                case "天津站" -> {
                    lines.add(new TrdmRouteMap.Line(LINE2, "2号线", "Line 2"));
                    lines.add(new TrdmRouteMap.Line(LINE9, "9号线", "Line 9"));
                }
                default -> lines.add(new TrdmRouteMap.Line(LINE1, "1号线", "Line 1"));
            }
        }
        return new TrdmRouteMap.Station(chinese, english, lines);
    }
}
