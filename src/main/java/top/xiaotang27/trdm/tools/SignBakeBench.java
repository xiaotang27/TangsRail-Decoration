package top.xiaotang27.trdm.tools;

import com.mojang.blaze3d.platform.NativeImage;
import top.xiaotang27.trdm.render.TrdmBigStationSignPanel;
import top.xiaotang27.trdm.render.TrdmHeaderTextures;
import top.xiaotang27.trdm.render.TrdmSignAssets;
import top.xiaotang27.trdm.render.TrdmStationSignPanel;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * 一次性小工具：量站名牌贴图「烘一张要多久」。
 * <p>
 */
public final class SignBakeBench {

    private SignBakeBench() {
    }

    public static void main(String[] args) {
        TrdmSignAssets.primeFromDirectory(Path.of("src/main/resources/assets/trdm/textures/block/sign"));
        final List<TrdmHeaderTextures.Badge> badges = List.of(
                new TrdmHeaderTextures.Badge(0xD4001C, "1号线", "Line 1"),
                new TrdmHeaderTextures.Badge(0x0075C2, "2号线", "Line 2"),
                new TrdmHeaderTextures.Badge(0x2E9E4F, "8号线", "Line 8"));

        // 牌宽 1..8 格（8 格就是渲染端 MAX_PLATE_WIDTH 的上限）
        for (int blocks = 1; blocks <= 8; blocks++) {
            final float aspect = blocks / (12F / 16F);
            final TrdmHeaderTextures.TextureSize size = TrdmHeaderTextures.bigSignTextureSize(aspect);
            long best = Long.MAX_VALUE;
            for (int iteration = 0; iteration < 5; iteration++) {
                final long start = System.nanoTime();
                final BufferedImage image = TrdmBigStationSignPanel.render(size.width(), size.height(),
                        "人民广场站", "PEOPLE'S SQUARE STATION", badges, "A1",
                        TrdmSignAssets.sprite(TrdmSignAssets.STATION_LOGO), null);
                toNative(image);
                if (iteration >= 2) {
                    best = Math.min(best, System.nanoTime() - start);
                }
            }
            System.out.printf("big sign %d block(s): %4dx%-3d -> %.1f ms%n",
                    blocks, size.width(), size.height(), best / 1e6);
        }

        long best = Long.MAX_VALUE;
        for (int iteration = 0; iteration < 5; iteration++) {
            final long start = System.nanoTime();
            final BufferedImage image = TrdmStationSignPanel.render(512, 384, "人民广场|PEOPLE'S SQUARE",
                    "鼓楼|GULOU", 0xD4001C, false, "下一站", "Next station");
            toNative(image);
            if (iteration >= 2) {
                best = Math.min(best, System.nanoTime() - start);
            }
        }
        System.out.printf("station sign 512x384 -> %.1f ms%n", best / 1e6);
    }

    /** 和 {@code TrdmHeaderTextures#toNativeImage} 同一套像素搬运（ARGB → 交换 R/B → NativeImage）。 */
    private static void toNative(BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        final NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int argb = pixels[y * width + x];
                    nativeImage.setPixelRGBA(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
                }
            }
        } finally {
            nativeImage.close();
        }
    }
}
