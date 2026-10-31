package top.xiaotang27.trdm.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import top.xiaotang27.trdm.Trdm;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Optional;

/**
 * 吊板用的图标素材（箭头 / 地铁标识）。
 * <p>
 * 素材路径固定在 {@code assets/trdm/textures/block/sign/} 下，读成一张「黑色 + 透明度」的蒙版后再整体着色：
 * <ul>
 *   <li>图里有透明像素 → 直接拿 alpha 当蒙版（黑色箭头、白色图标都能用）；</li>
 *   <li>图是不透明背景（白底黑图 / 黑底白图）→ 按亮度判正负：平均亮度亮的取「暗=不透明」，暗的反过来。</li>
 * </ul>
 * 素材缺失时返回 null，调用方自行退回矢量绘制。
 * <p>
 * 必须在渲染线程第一次调用（{@link #getArrow()} / {@link #getSubway()}），读到的图会缓存给后台烘焙线程用。
 */
final class TrdmArrowAsset {

    private static final ResourceLocation ARROW_LOCATION = new ResourceLocation(Trdm.MOD_ID, "textures/block/sign/arrow.png");

    private static BufferedImage arrowMask;
    private static boolean arrowLoaded;

    private TrdmArrowAsset() {
    }

    static synchronized BufferedImage getArrow() {
        if (!arrowLoaded) {
            arrowLoaded = true;
            arrowMask = load(ARROW_LOCATION);
        }
        return arrowMask;
    }

    private static BufferedImage load(ResourceLocation location) {
        try {
            final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isPresent()) {
                try (final InputStream stream = resource.get().open()) {
                    return toMask(ImageIO.read(stream));
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static BufferedImage toMask(BufferedImage source) {
        if (source == null) {
            return null;
        }
        final int width = source.getWidth();
        final int height = source.getHeight();

        boolean transparent = false;
        long luminanceSum = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int argb = source.getRGB(x, y);
                if ((argb >>> 24) < 250) {
                    transparent = true;
                }
                luminanceSum += luminance(argb);
            }
        }
        // 不透明图按平均亮度判正负：亮的平均 → 图标是暗的；反之图标是亮的。
        // 带透明的图直接拿 alpha 当蒙版（图形本身就编码在透明度里），颜色不参与。
        final boolean darkIsIcon = (double) luminanceSum / Math.max(1, (long) width * height) >= 128;

        final BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int argb = source.getRGB(x, y);
                final int sourceAlpha = (argb >>> 24) & 0xFF;
                final int alpha = transparent
                        ? sourceAlpha
                        : (darkIsIcon ? 255 - luminance(argb) : luminance(argb)) * sourceAlpha / 255;
                result.setRGB(x, y, alpha << 24);
            }
        }
        return result;
    }

    private static int luminance(int argb) {
        return Math.round(0.299F * ((argb >> 16) & 0xFF) + 0.587F * ((argb >> 8) & 0xFF) + 0.114F * (argb & 0xFF));
    }
}
