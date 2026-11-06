package top.xiaotang27.trdm.tools;

import top.xiaotang27.trdm.control.PsdStyles;
import top.xiaotang27.trdm.render.TrdmHeaderTextures;
import top.xiaotang27.trdm.render.TrdmJinbinHeaderPanel;
import top.xiaotang27.trdm.render.TrdmJinbinRouteMap;
import top.xiaotang27.trdm.render.TrdmRouteMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次性小工具：不开游戏，直接烘焙几张「门楣站名吊板」预览图，用来对
 * 站名中/英字号比例（英文只比中文略小）、换乘站线路标志（含纯文字线路的长条底牌）和留白。
 */
public final class HeaderPanelPreview {

    private HeaderPanelPreview() {
    }

    public static void main(String[] args) throws Exception {
        final Path outDir = Path.of("build/preview");
        Files.createDirectories(outDir);

        final List<TrdmHeaderTextures.Badge> mixed = new ArrayList<>(List.of(
                new TrdmHeaderTextures.Badge(0xE4007F, "5号线", "Line 5"),
                new TrdmHeaderTextures.Badge(0x7B2CBF, "6号线", "Line 6"),
                new TrdmHeaderTextures.Badge(0xE0457B, "津静线", "JINJING Line")));

        write(outDir, "header_panel_4_plain", "南何庄|NANHEZHUANG", List.of(), 4F);
        write(outDir, "header_panel_4_routes", "南何庄|NANHEZHUANG", mixed, 4F);
        write(outDir, "header_panel_8_routes", "红旗南路|HONGQINANLU", mixed, 8F);
        write(outDir, "header_panel_2_routes", "西站|XI ZHAN", List.of(
                new TrdmHeaderTextures.Badge(0xE0457B, "津静线", "JINJING Line")), 2F);

        // 津滨轻轨风格：按参考图的 2718×675 直接画，方便和 Frame 88–93 对照
        final Path jinbinDir = outDir.resolve("jinbin");
        Files.createDirectories(jinbinDir);
        // 离线没有 Minecraft 实例，素材从源码目录直接读
        top.xiaotang27.trdm.render.TrdmSignAssets.primeFromDirectory(
                Path.of("src/main/resources/assets/trdm/textures/block/sign"));
        final List<TrdmHeaderTextures.Badge> line9 = List.of(
                new TrdmHeaderTextures.Badge(0x015AAA, "9号线", "Line 9"));
        writeJinbin(jinbinDir, "new-bmt-normal", PsdStyles.HEADER_JINBIN, line9.get(0), false, false, false, true);
        writeJinbin(jinbinDir, "new-bmt-short", PsdStyles.HEADER_JINBIN, line9.get(0), true, true, false, true);
        writeJinbin(jinbinDir, "new-bmt-terminal", PsdStyles.HEADER_JINBIN, line9.get(0), false, false, true, true);
        writeJinbin(jinbinDir, "new-trt-normal", PsdStyles.HEADER_JINBIN_RETROFIT, line9.get(0), false, false, false, true);
        writeJinbin(jinbinDir, "new-trt-short", PsdStyles.HEADER_JINBIN_RETROFIT, line9.get(0), true, true, false, true);
        writeJinbin(jinbinDir, "new-bmt-right", PsdStyles.HEADER_JINBIN, line9.get(0), true, false, false, false);
        writeJinbin(jinbinDir, "new-trt-right", PsdStyles.HEADER_JINBIN_RETROFIT, line9.get(0), true, false, false, false);
        writeJinbinSized(jinbinDir, "new-bmt-ingame", PsdStyles.HEADER_JINBIN, line9.get(0), true, false, 2322, 512);
        writeJinbinSized(jinbinDir, "new-trt-ingame", PsdStyles.HEADER_JINBIN_RETROFIT, line9.get(0), true, true, 2322, 512);
        // 津滨样式的贴图一直铺到门楣模型下沿（不再给 MTR 色带留 1.5/16）：
        // 两格宽 = 2 − 2×SIDE_PADDING = 1.984 格 / (1 − 7.5/16) 格高 → 宽高比 ≈ 3.735
        writeJinbinSized(jinbinDir, "new-bmt-full", PsdStyles.HEADER_JINBIN, line9.get(0), false, false, 1912, 512);
        writeJinbinSized(jinbinDir, "new-trt-full", PsdStyles.HEADER_JINBIN_RETROFIT, line9.get(0), false, true, 1912, 512);

        final TrdmRouteMap.Model routeMap = sampleRouteMap();
        writeRouteMap(jinbinDir, "new-bmt-routemap", PsdStyles.HEADER_JINBIN, routeMap);
        writeRouteMap(jinbinDir, "new-trt-routemap", PsdStyles.HEADER_JINBIN_RETROFIT, routeMap);
        writeRouteMapSized(jinbinDir, "new-bmt-routemap-ingame", PsdStyles.HEADER_JINBIN, routeMap, 2925, 744);
        writeRouteMapSized(jinbinDir, "new-trt-routemap-ingame", PsdStyles.HEADER_JINBIN_RETROFIT, routeMap, 2925, 744);
        writeRouteMapSized(jinbinDir, "new-bmt-routemap-full", PsdStyles.HEADER_JINBIN, routeMap, 3854, 1024);
        writeRouteMapSized(jinbinDir, "new-trt-routemap-full", PsdStyles.HEADER_JINBIN_RETROFIT, routeMap, 3854, 1024);
        final TrdmRouteMap.Model routeMapRight = new TrdmRouteMap.Model(routeMap.stations(),
                routeMap.currentIndex(), routeMap.routeColor(), routeMap.routeChinese(), routeMap.routeEnglish(), false);
        writeRouteMapSized(jinbinDir, "new-bmt-routemap-right", PsdStyles.HEADER_JINBIN, routeMapRight, 2925, 744);
        writeRouteMapSized(jinbinDir, "new-trt-routemap-right", PsdStyles.HEADER_JINBIN_RETROFIT, routeMapRight, 2925, 744);

        // 真实线路（天津地铁 6 号线，15 站、长站名）：参考图宽幅 + 两格玻璃吊板两种比例
        final TrdmRouteMap.Model line6 = sampleLine6();
        writeRouteMapSized(jinbinDir, "jinbin-line6-wide", PsdStyles.HEADER_JINBIN, line6, 3728, 675);
        writeRouteMapSized(jinbinDir, "jinbin-line6-2block", PsdStyles.HEADER_JINBIN, line6, 3854, 1024);
        writeRouteMapSized(jinbinDir, "jinbin-line6-wide-trt", PsdStyles.HEADER_JINBIN_RETROFIT, line6, 3728, 675);
        writeRouteMapSized(jinbinDir, "jinbin-line6-2block-trt", PsdStyles.HEADER_JINBIN_RETROFIT, line6, 3854, 1024);
    }

    /**
     * 天津地铁 6 号线（游戏存档里的顺序：沥水道是 0 号，往南孙庄方向序号增大），
     * 本站梅江会展中心。用来检查 15 站 + 长站名在两格宽玻璃吊板上的排版。
     */
    private static TrdmRouteMap.Model sampleLine6() {
        final List<TrdmRouteMap.Line> line5 = List.of(new TrdmRouteMap.Line(0xF26522, "5号线", "Line 5"));
        final List<TrdmRouteMap.Line> line6 = List.of(new TrdmRouteMap.Line(0x7B2CBF, "6号线", "Line 6"));
        final List<TrdmRouteMap.Station> stations = List.of(
                new TrdmRouteMap.Station("沥水道", "LISHUIDAO", List.of()),
                new TrdmRouteMap.Station("梅江会展中心", "MEIJIANGHUIZHANZHONGXIN", List.of()),
                new TrdmRouteMap.Station("解放南路", "JIEFANGNANLU", List.of()),
                new TrdmRouteMap.Station("黑牛城道", "HEINIUCHENGDAO", List.of()),
                new TrdmRouteMap.Station("文化中心", "WENHUAZHONGXIN", List.of()),
                new TrdmRouteMap.Station("迎风道", "YINGFENGDAO", List.of()),
                new TrdmRouteMap.Station("红旗南路", "HONGQINANLU", List.of()),
                new TrdmRouteMap.Station("天拖", "TIANTUO", List.of()),
                new TrdmRouteMap.Station("西站", "XIZHAN", List.of()),
                new TrdmRouteMap.Station("北运河", "BEIYUNHE", List.of()),
                new TrdmRouteMap.Station("天泰路", "TAITAILU", List.of()),
                new TrdmRouteMap.Station("金钟河大街", "JINZHONGHEDAJIE", List.of(line6.get(0))),
                new TrdmRouteMap.Station("金钟街", "JINZHONGJIE", List.of()),
                new TrdmRouteMap.Station("南何庄", "NANHEZHUANG", line5),
                new TrdmRouteMap.Station("南孙庄", "NANSUNZHUANG", List.of()));
        return new TrdmRouteMap.Model(stations, 1, 0x7B2CBF, "6号线", "Line 6", true);
    }

    private static TrdmRouteMap.Model sampleRouteMap() {
        final List<TrdmRouteMap.Line> line2 = List.of(new TrdmRouteMap.Line(0xFFD100, "2号线", "Line 2"));
        final List<TrdmRouteMap.Line> line3 = List.of(new TrdmRouteMap.Line(0x00B5E2, "3号线", "Line 3"));
        final List<TrdmRouteMap.Line> line5 = List.of(new TrdmRouteMap.Line(0xF26522, "5号线", "Line 5"));
        final List<TrdmRouteMap.Line> line9 = List.of(new TrdmRouteMap.Line(0x7B2CBF, "9号线", "Line 9"));
        // 顺序和游戏里 MTR 的 stationDetails 一致：沥水道端是 0 号，往南孙庄方向序号增大。
        // 这里照 Frame 91 的样张：东海路是 0 号、天津站在另一端，本站东兴路。
        final List<TrdmRouteMap.Station> stations = List.of(
                new TrdmRouteMap.Station("东海路", "Donghai Road", List.of()),
                new TrdmRouteMap.Station("一号桥", "Yihaoqiao", List.of()),
                new TrdmRouteMap.Station("中山门", "Zhongshanmen", List.of()),
                new TrdmRouteMap.Station("东兴路", "Dongxing Road", List.of()),
                new TrdmRouteMap.Station("机场桥", "Jichangqiao", List.of()),
                new TrdmRouteMap.Station("直沽", "Zhigu", line5),
                new TrdmRouteMap.Station("踩踩呗", "Caicaibei", List.of()),
                new TrdmRouteMap.Station("天津站", "Tianjin Railway Station",
                        List.of(line2.get(0), line9.get(0))));
        return new TrdmRouteMap.Model(stations, 3, 0x015AAA, "9号线", "Line 9", true);
    }

    private static void writeRouteMap(Path outDir, String name, int style, TrdmRouteMap.Model model)
            throws Exception {
        writeRouteMapSized(outDir, name, style, model, 3728, 675);
    }

    private static void writeRouteMapSized(Path outDir, String name, int style, TrdmRouteMap.Model model,
                                           int width, int height) throws Exception {
        final BufferedImage image = TrdmJinbinRouteMap.render(width, height, style, model, 0.06F, 0.06F);
        final Path target = outDir.resolve(name + ".png");
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + image.getWidth() + "x" + image.getHeight());
    }

    private static void writeJinbinSized(Path outDir, String name, int style, TrdmHeaderTextures.Badge routeBadge,
                                         boolean interchange, boolean shortName, int width, int height)
            throws Exception {
        final BufferedImage image = TrdmJinbinHeaderPanel.render(width, height, style,
                shortName ? "直沽|Zhigu" : "十一经路|Shiyijing Road", routeBadge, interchange,
                "天津站|Tianjin Railway Station", "大王庄|Dawangzhuang",
                shortName ? "东兴路|Dongxing Road" : "直沽|Zhigu", false, true);
        final Path target = outDir.resolve(name + ".png");
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + image.getWidth() + "x" + image.getHeight());
    }

    private static void writeJinbin(Path outDir, String name, int style, TrdmHeaderTextures.Badge routeBadge,
                                    boolean interchange, boolean shortName, boolean terminal, boolean arrowLeft)
            throws Exception {
        final String destination = "天津站|Tianjin Railway Station";
        final String next = "大王庄|Dawangzhuang";
        final String previous = shortName ? "东兴路|Dongxing Road" : "直沽|Zhigu";
        final BufferedImage image = TrdmJinbinHeaderPanel.render(2718, 675, style,
                shortName ? "直沽|Zhigu" : "十一经路|Shiyijing Road", routeBadge, interchange,
                destination, next, previous, terminal, arrowLeft);
        final Path target = outDir.resolve(name + ".png");
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + image.getWidth() + "x" + image.getHeight());
    }

    private static void write(Path outDir, String name, String stationName,
                              List<TrdmHeaderTextures.Badge> badges, float aspect) throws Exception {
        final BufferedImage image = TrdmHeaderTextures.previewStationNamePanel(stationName, badges, aspect);
        final Path target = outDir.resolve(name + ".png");
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + image.getWidth() + "x" + image.getHeight());
    }
}
