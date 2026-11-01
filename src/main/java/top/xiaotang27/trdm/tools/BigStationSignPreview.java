package top.xiaotang27.trdm.tools;

import top.xiaotang27.trdm.render.TrdmBigStationSignPanel;
import top.xiaotang27.trdm.render.TrdmHeaderTextures;
import top.xiaotang27.trdm.render.TrdmSignAssets;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一次性小工具：不开游戏，直接烘焙几张「大站名牌」预览图，用来核对排版
 * （地铁标志 + 线路编号块 + 无障碍电梯图标 + 站名 + 出口字母，中文/英文字距与等比缩放）。
 */
public final class BigStationSignPreview {

    private BigStationSignPreview() {
    }

    public static void main(String[] args) throws Exception {
        final Path outDir = Path.of("build/preview");
        Files.createDirectories(outDir);
        TrdmSignAssets.primeFromDirectory(Path.of("src/main/resources/assets/trdm/textures/block/sign"));
        System.out.println("logo: " + (TrdmSignAssets.sprite(TrdmSignAssets.STATION_LOGO) == null ? "missing" : "loaded"));
        System.out.println("elevator icon: "
                + (TrdmSignAssets.sprite(TrdmSignAssets.ACCESSIBLE_ELEVATOR) == null ? "missing" : "loaded"));

        // 参考图那种横向大牌：5 格宽 → 宽高比 = 5 / 0.75
        write(outDir, "big_sign_5", 5F, "大学城|DAXUECHENG", new int[]{0x1B86B8}, new String[]{"3号线|Line 3"},
                "A", false, false);
        // 4 格宽、英文名长 → 中文字距会被拉开
        write(outDir, "big_sign_4_long", 4F, "梅江会展中心|MEIJIANG HUIZHANZHONGXIN", new int[]{0x8E2A9C},
                new String[]{"6号线|Line 6"}, "2", true, false);
        // 只有 1 格宽 → 元素等比缩小
        write(outDir, "big_sign_1", 1F, "北站|BEI ZHAN", new int[]{0x0075C2}, new String[]{"1号线|Line 1"},
                "B", true, false);
        // ZHAN 牌子：名字本来带「站」也要再写一遍（西站 → 西站站）
        write(outDir, "big_sign_zhan_double", 4F, "西站|XI ZHAN", new int[]{0xE0B015}, new String[]{"8号线|Line 8"},
                "C", true, false);
        // 换乘站：本站所有线路都要标注
        write(outDir, "big_sign_multi_route", 6F, "人民广场|PEOPLE'S SQUARE",
                new int[]{0xD4001C, 0x0075C2, 0x2E9E4F},
                new String[]{"1号线|Line 1", "2号线|Line 2", "8号线|Line 8"}, "A", false, false);
        // 无障碍电梯牌：站名后加「无障碍电梯」，线路标签后加图标
        write(outDir, "big_sign_elevator", 6F, "红旗南路|HONGQINANLU",
                new int[]{0xD4001C}, new String[]{"6号线|Line 6"}, "A", true, true);
        // 纯文字线路名（津静线）：浅底 + 细深边 + 线路色字，长条而不是正方形
        write(outDir, "big_sign_text_route", 6F, "梅江会展中心|MEIJIANG HUIZHANZHONGXIN",
                new int[]{0xE4007F}, new String[]{"津静线|JINJING Line"}, "B", false, false);
        // 数字线路 + 纯文字线路混排
        write(outDir, "big_sign_mixed_route", 6F, "天津南站|TIANJIN SOUTH",
                new int[]{0xD4001C, 0xE4007F}, new String[]{"5号线|Line 5", "津静线|JINJING Line"}, "C", false, false);
    }

    private static void write(Path outDir, String name, float blocks, String stationName, int[] routeColors,
                              String[] routeNames, String letter, boolean zhanStyle, boolean elevator) throws Exception {
        final float aspect = blocks / (12F / 16F);
        final TrdmHeaderTextures.TextureSize size = TrdmHeaderTextures.bigSignTextureSize(aspect);
        final String[] parts = stationName.split("\\|", 2);
        // ZHAN 牌子一律再补一个「站」；STATION 只在缺「站」时补（和服务端同一套规则）
        final String cjkBase = parts[0].isEmpty()
                ? parts[0]
                : zhanStyle || !parts[0].endsWith("站") ? parts[0] + "站" : parts[0];
        final String cjk = elevator ? cjkBase + "无障碍电梯" : cjkBase;
        final String base = parts.length > 1 ? parts[1] : parts[0];
        final String suffix = zhanStyle ? "ZHAN" : "STATION";
        final String upperBase = base.toUpperCase(java.util.Locale.ROOT);
        // 和中文同一套规则：ZHAN 一律再拼一次，STATION 只在没有时才拼
        final String latinBase = zhanStyle || !upperBase.endsWith(suffix)
                ? upperBase + " " + suffix
                : upperBase;
        final String latin = elevator ? latinBase + " Accessible Elevator" : latinBase;

        final java.util.List<TrdmHeaderTextures.Badge> badges = new java.util.ArrayList<>();
        for (int i = 0; i < routeNames.length; i++) {
            final String[] routeParts = routeNames[i].split("\\|", 2);
            badges.add(new TrdmHeaderTextures.Badge(routeColors[i % routeColors.length], routeParts[0],
                    routeParts.length > 1 ? routeParts[1] : ""));
        }

        final BufferedImage image = TrdmBigStationSignPanel.render(size.width(), size.height(), cjk, latin,
                badges, letter, TrdmSignAssets.sprite(TrdmSignAssets.STATION_LOGO),
                elevator ? TrdmSignAssets.sprite(TrdmSignAssets.ACCESSIBLE_ELEVATOR) : null);
        final Path target = outDir.resolve(name + ".png");
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + size.width() + "x" + size.height());
    }
}
