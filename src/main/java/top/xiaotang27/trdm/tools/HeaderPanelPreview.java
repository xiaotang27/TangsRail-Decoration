package top.xiaotang27.trdm.tools;

import top.xiaotang27.trdm.render.TrdmHeaderTextures;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次性小工具：不开游戏，直接烘焙几张「门楣站名吊板」预览图，用来对
 * 站名中/英字号比例（英文只比中文略小）、换乘站线路标志（含纯文字线路的长条底牌）和留白。
 * <p>
 * 用法：{@code ./gradlew previewHeaderPanel}，输出到 {@code build/preview/header_panel_*.png}。
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
    }

    private static void write(Path outDir, String name, String stationName,
                              List<TrdmHeaderTextures.Badge> badges, float aspect) throws Exception {
        final BufferedImage image = TrdmHeaderTextures.previewStationNamePanel(stationName, badges, aspect);
        final Path target = outDir.resolve(name + ".png");
        ImageIO.write(image, "PNG", target.toFile());
        System.out.println("wrote " + target.toAbsolutePath() + "  " + image.getWidth() + "x" + image.getHeight());
    }
}
