package top.xiaotang27.trdm.render;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import top.xiaotang27.trdm.Trdm;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 屏蔽门玻璃上的装饰带（参考图样式的「人字纹 + 中央图文」条带）。
 * <p>
 * 不是方块也不是物品：由门楣 BE 在玻璃的竖直中缝高度上叠一层四边形，所以玻璃一放下去就自动出现，
 * 数量一多就跟着整段延长（一段玻璃只画一张，接缝处不会有重复叠画）。
 * <p>
 * 版面（全部以「装饰带高度」为单位，数值量自参考图 6222x512）：
 * <ul>
 *   <li>底：线路色整条铺满；</li>
 *   <li>两端：白色人字纹，节距 0.264、横向笔画宽 0.113、上下各伸 0.377、斜度 0.423，</li>
 *       从两端边缘往里排，排到中央图文两侧的留白为止；</li>
 *   <li>正中：一张图（{@link #CENTRE_TEXTURE}），按不透明部分居中，高度 0.70，不随方向镜像。</li>
 * </ul>
 * 人字朝左还是朝右由门楣的 {@code ARROW_DIRECTION} 决定（整条装饰带跟着吊板换向），中央图永远正向。
 * <p>
 * 还有一种<b>只有人字纹</b>的样式（{@link #render} 的 {@code chevronOnly}）：不铺底色带、不画中央图文，
 * 人字本身画成线路色；人字的形状、节距、两端起点和样式1 一模一样。
 */
public final class TrdmDecalBand {

    /**
     * 装饰带高度（格）。原来是 4/16（0.25 格），按要求上下收窄 1/3，现在只有 2/3：
     * 4/16 × 2/3 = 1/6 格。
     * <p>
     * 带内所有元素（两端人字、正中「文字 + logo」图）的尺寸都是<b>相对带宽高度的比例</b>算的，
     * 所以改这一个数，箭头和 slogan 会跟着一起等比缩小，不会被裁掉也不会保持不变。
     */
    public static final float HEIGHT_BLOCKS = 4F / 16F * 2F / 3F;

    /**
     * 中央「文字 + logo」图，一张 PNG（透明底，白色字 + 红色 logo）。
     * 换成自己的原图时保持同名同路径即可。
     */
    public static final String CENTRE_TEXTURE = "textures/block/band/band_center.png";

    // ===== 版面比例（单位：装饰带高度）=====
    private static final double CHEVRON_PITCH = 0.264;
    private static final double CHEVRON_STROKE = 0.113;
    private static final double CHEVRON_HALF = 0.377;
    private static final double CHEVRON_SLOPE = 0.423;
    private static final double CHEVRON_MARGIN = 0.162;
    /** 中央图文两侧各留多少空（单位：带高）。留得比参考图更宽一点，人字和 slogan 之间一眼就能看出空白。 */
    private static final double CENTRE_GAP = 1.8;
    private static final double CENTRE_HEIGHT = 0.70;
    private static final double CENTRE_MAX_WIDTH = 0.66;

    private static volatile TrdmSignAssets.Sprite centre;
    private static volatile TrdmSignAssets.Sprite centreMirrored;
    private static volatile boolean centreMissing;

    private TrdmDecalBand() {
    }

    /**
     * 预读中央图（渲染线程调用；烘焙线程只读缓存）。
     */
    public static synchronized void primeCentre() {
        if (centre != null || centreMissing) {
            return;
        }
        try {
            final ResourceLocation location = new ResourceLocation(Trdm.MOD_ID, CENTRE_TEXTURE);
            final Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
            if (resource.isPresent()) {
                try (final InputStream stream = resource.get().open()) {
                    final BufferedImage image = ImageIO.read(stream);
                    if (image != null) {
                        centre = TrdmSignAssets.toSprite(image);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        centreMissing = centre == null;
        centreMirrored = centre == null ? null : TrdmSignAssets.mirror(centre);
    }

    /**
     * 离线预览用：直接从磁盘读中央图。
     */
    public static synchronized void primeCentreFromFile(Path file) {
        if (centre != null) {
            return;
        }
        try {
            if (file != null && Files.isRegularFile(file)) {
                final BufferedImage image = ImageIO.read(file.toFile());
                if (image != null) {
                    centre = TrdmSignAssets.toSprite(image);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        centreMissing = centre == null;
        centreMirrored = centre == null ? null : TrdmSignAssets.mirror(centre);
    }

    public static TrdmSignAssets.Sprite centreSprite() {
        return centre;
    }

    /**
     * 中央图文。<b>不镜像</b>的那份给站台侧，<b>水平镜像</b>的那份给轨道侧 ——
     * 两面看过去 slogan 都是正的，人字的方向不受影响（人字是另外画的，不跟着镜像）。
     */
    public static TrdmSignAssets.Sprite centreSprite(boolean mirrored) {
        return mirrored ? centreMirrored : centre;
    }

    /**
     * 烘焙一整条装饰带。
     *
     * @param width       贴图像素宽
     * @param height      贴图像素高
     * @param routeColor  线路色（0xRRGGBB）
     * @param pointLeft   人字是否朝左
     * @param centre      中央图文（可为 null，那就只画人字）
     * @param chevronOnly true = 只有人字纹的样式：不铺底色带、不画中央图文，人字画成线路色；
     *                    人字的位置、形状、节距和有 slogan 的版本完全一致
     */
    public static BufferedImage render(int width, int height, int routeColor, boolean pointLeft,
                                       TrdmSignAssets.Sprite centre, boolean chevronOnly) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 底：线路色整条（只画人字的样式没有底，整张图透明，玻璃本色透出来）
        if (!chevronOnly) {
            graphics.setColor(new Color(routeColor | 0xFF000000, true));
            graphics.fillRect(0, 0, width, height);
        }

        final double h = height;
        final double midY = height / 2D;

        // 正中图文：按不透明部分的高度定尺度，太宽就压到带宽的 66%
        double centreWidth = 0;
        if (!chevronOnly && centre != null && centre.inkW() > 0 && centre.inkH() > 0) {
            final double scale = Math.min(CENTRE_HEIGHT * h / centre.inkH(),
                    CENTRE_MAX_WIDTH * width / centre.inkW());
            centreWidth = centre.inkW() * scale;
            TrdmSignAssets.drawSpriteInk(graphics, centre,
                    (float) (width / 2D - centreWidth / 2D), (float) (midY - centre.inkH() * scale / 2D),
                    (float) centreWidth, (float) (centre.inkH() * scale));
        }

        // 两端人字纹：只画人字的样式就地染成线路色
        final double margin = CHEVRON_MARGIN * h;
        final double gap = centreWidth > 0 ? CENTRE_GAP * h : 0;
        // 只画人字时中间没有 slogan，两边各往里多让一个人字的宽度（两块同色同形，重叠看不出来），
        // 免得正中间留下一条不到一个人字宽的缝
        final double seam = chevronOnly ? chevronExtent(h) : 0;
        final double leftZoneEnd = width / 2D - centreWidth / 2D - gap + seam;
        final double rightZoneStart = width / 2D + centreWidth / 2D + gap - seam;
        graphics.setColor(new Color((chevronOnly ? routeColor : 0xFFFFFF) | 0xFF000000, true));
        drawChevronZone(graphics, margin, leftZoneEnd, h, midY, pointLeft, false);
        drawChevronZone(graphics, rightZoneStart, width - margin, h, midY, pointLeft, true);

        graphics.dispose();
        return image;
    }

    private static double chevronExtent(double h) {
        return (CHEVRON_SLOPE * CHEVRON_HALF + CHEVRON_STROKE) * h;
    }

    /**
     * 在一段可用区间里按节距摆人字纹。
     *
     * @param anchorRight true = 从右端（外端）往左排
     */
    private static void drawChevronZone(Graphics2D graphics, double from, double to, double h, double midY,
                                        boolean pointLeft, boolean anchorRight) {
        if (to <= from) {
            return;
        }
        final double pitch = CHEVRON_PITCH * h;
        final double extent = chevronExtent(h);
        if (anchorRight) {
            for (double position = to - pitch / 2D;; position -= pitch) {
                final double tip = pointLeft ? position - extent : position;
                final double shapeLeft = pointLeft ? tip : tip - extent;
                if (shapeLeft < from) {
                    break;
                }
                drawChevron(graphics, tip, midY, h, pointLeft);
            }
        } else {
            for (double position = from + pitch / 2D;; position += pitch) {
                final double tip = pointLeft ? position : position + extent;
                final double shapeRight = pointLeft ? tip + extent : tip;
                if (shapeRight > to) {
                    break;
                }
                drawChevron(graphics, tip, midY, h, pointLeft);
            }
        }
    }

    /**
     * 单个「人字」：尖角在 {@code tipX}，笔画沿水平方向的截面宽 {@code CHEVRON_STROKE}，
     * 上下各伸 {@code CHEVRON_HALF}（被装饰带上下边缘直角裁切，和参考图一致）。
     */
    private static void drawChevron(Graphics2D graphics, double tipX, double midY, double h, boolean pointLeft) {
        final double s = pointLeft ? 1 : -1;
        final double dy = CHEVRON_HALF * h;
        final double dx = CHEVRON_SLOPE * dy;
        final double stroke = CHEVRON_STROKE * h;
        final Path2D path = new Path2D.Double();
        path.moveTo(tipX + s * dx, midY - dy);
        path.lineTo(tipX + s * (dx + stroke), midY - dy);
        path.lineTo(tipX + s * stroke, midY);
        path.lineTo(tipX + s * (dx + stroke), midY + dy);
        path.lineTo(tipX + s * dx, midY + dy);
        path.lineTo(tipX, midY);
        path.closePath();
        graphics.fill(path);
    }

    /**
     * 预览工具用：把一条装饰带存成 PNG（不含 Minecraft 依赖的调用都在 {@link #render} 里）。
     */
    public static BufferedImage preview(int width, int height, int routeColor, boolean pointLeft, boolean chevronOnly) {
        return render(width, height, routeColor, pointLeft, centre, chevronOnly);
    }
}
