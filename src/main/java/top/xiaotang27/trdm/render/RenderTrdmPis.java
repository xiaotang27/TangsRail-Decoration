package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.block.IBlock;
import mtr.data.IGui;
import mtr.render.MoreRenderLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.block.BlockTrdmPis;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PIS 屏幕渲染器：<b>模型 + 镜像复制出来的另一半 + 铺在面板上的动态贴图</b>，全部在这里画。
 * <p>
 */
public class RenderTrdmPis<T extends BlockTrdmPis.TileEntityTrdmPis>
        implements BlockEntityRenderer<T>, IBlock, IGui {

    // ===== 模型尺寸（像素，1 格 = 16）=====

    /** 挡板外表面离方块中心 2 像素（模型 x = 6 / x = 10）。 */
    private static final float PLATE_FACE_PX = 2F;
    /** 屏幕板心高度：模型挡板中心 y = 4，方块中心是 y = 8。 */
    private static final float SCREEN_CENTRE_Y = -4F / 16F;
    /**
     * 屏幕贴图比面板小一圈（左右各留 1 像素、上下各留 0.8 像素的黑色边框），
     * 宽 22 像素、高按 16:9 = 12.375 像素。
     */
    private static final float SCREEN_HALF_WIDTH = 11F / 16F;
    private static final float SCREEN_HALF_HEIGHT = 11F / (16F / 9F) / 16F;
    /** 贴图往挡板外面挪一点，别和板面深度打架（0.02 像素）。 */
    private static final float SURFACE_GAP = 0.02F / 16F;
    /** 广告区在屏幕贴图里的位置（画布单位，和 TrdmPisPanel 的 AD_X/Y/W/H 一致）。 */
    private static final float AD_U_MIN = 36F / TrdmPisPanel.CANVAS_WIDTH;
    private static final float AD_U_MAX = 126F / TrdmPisPanel.CANVAS_WIDTH;
    private static final float AD_V_MIN = 2F / TrdmPisPanel.CANVAS_HEIGHT;
    private static final float AD_V_MAX = 56F / TrdmPisPanel.CANVAS_HEIGHT;
    /** 视频四边形在屏幕平面上的半宽 / 半高和中心偏移（相对屏幕中心）。 */
    private static final float AD_HALF_U = (AD_U_MAX - AD_U_MIN) * SCREEN_HALF_WIDTH;
    private static final float AD_HALF_V = (AD_V_MAX - AD_V_MIN) * SCREEN_HALF_HEIGHT;
    private static final float AD_CENTRE_U = ((AD_U_MIN + AD_U_MAX) / 2F - 0.5F) * 2F * SCREEN_HALF_WIDTH;
    private static final float AD_CENTRE_V = (0.5F - (AD_V_MIN + AD_V_MAX) / 2F) * 2F * SCREEN_HALF_HEIGHT;
    /** 视频那一层再往外面挪一丁点，压在屏幕贴图上。 */
    private static final float VIDEO_LAYER_GAP = 0.0008F;
    /** 吊杆从模型立杆一直伸到 1.5 格高（模型立杆本身只到 y = 16）。 */
    private static final float ROD_TOP_PX = 24F;
    /** 模型里立杆元素的名字：它的位置就是吊杆接口，交给 {@link #emitRods} 单独画。 */
    private static final String ROD_ELEMENT = "Pole";
    /** 立杆在方块里的位置：x = 8（居中）、z = 9（比方块中心偏 1 像素，和显示屏柱那个模型一致）。 */
    private static final float ROD_F_PX = 8F;
    private static final float ROD_Z_PX = 9F;
    /** 立杆粗细（像素）和底端高度（挡板顶 y = 11 起）。 */
    private static final float ROD_SIZE_PX = 1F;
    private static final float ROD_BOTTOM_PX = 9F;
    private static final ResourceLocation SHELL_METAL = new ResourceLocation("mtr", "textures/block/metal.png");

    /** 原版方块模型的六面明暗。 */
    private static final float SHADE_UP = 1.0F;
    private static final float SHADE_DOWN = 0.5F;
    private static final float SHADE_FRONT = 0.8F;
    private static final float SHADE_SIDE = 0.6F;

    private static final int MAX_LIGHT_GLOWING = 0xF000F0;

    /**
     * 版面贴图缩放：128×72 画布 × 6 = 768×432。
     * 每块屏要占「底图 + 合成 + NativeImage」三张这个尺寸的图，8 太吃内存，6 在游戏里看不出区别。
     */
    private static final int PLATE_SCALE = 6;
    private static final int PLATE_WIDTH = TrdmPisPanel.CANVAS_WIDTH * PLATE_SCALE;
    private static final int PLATE_HEIGHT = TrdmPisPanel.CANVAS_HEIGHT * PLATE_SCALE;
    /** 版面（背景 / 到站 / 时钟 / 跑马灯 / 车厢）重画的最低间隔：10 Hz。 */
    private static final long PLATE_INTERVAL_MS = 100L;
    /**
     * 动图广告贴帧的最高频率：<b>1000 / 30 ≈ 33 ms = 30 fps</b>。
     * 动图只在版面底图上贴一次广告区（几十微秒），不重画整张版面，所以 30fps 也不疼。
     */
    private static final long ANIMATED_INTERVAL_MS = 1000L / 30L;

    private static final Map<BlockPos, Holder> HOLDERS = new HashMap<>();
    private static int counter;

    private static final class Holder {
        DynamicTexture plate;
        ResourceLocation plateId;
        /** 真的那张 NativeImage（原地改像素后 upload，避免每帧新建 2.4MB）。 */
        NativeImage nativeImage;
        /** 不含广告的版面底图。 */
        BufferedImage base;
        long baseStamp;
        /** 版面 + 当前广告帧。 */
        BufferedImage composite;
        String plateKey = "";
        long plateStamp;
        /** 视频广告：单独一张贴图 + 单独一个四边形，帧到就传，不受版面合成节流。 */
        BufferedImage videoFrame;
        int videoSeq = -1;
        int videoUploadedSeq = -1;
        DynamicTexture videoTexture;
        ResourceLocation videoId;
        NativeImage videoNative;
    }

    public RenderTrdmPis() {
    }

    @Override
    public void render(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final Level world = entity.getLevel();
        if (world == null) {
            return;
        }
        final BlockPos pos = entity.getBlockPos();
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmPis)) {
            return;
        }
        final Direction facing = IBlock.getStatePropertySafe(state, BlockTrdmPis.FACING);
        // 整块屏占两格：左边那格统一画整份模型（居中在两格交界上），右边那格不画，免得叠两层
        final Direction rightDirection = BlockTrdmPis.rightOf(facing);
        final boolean hasRight = world.getBlockState(pos.relative(rightDirection)).getBlock() instanceof BlockTrdmPis;
        final boolean hasLeft = world.getBlockState(pos.relative(rightDirection.getOpposite())).getBlock() instanceof BlockTrdmPis;
        if (hasLeft && !hasRight) {
            return;
        }
        // 两格拼起来时整个模型（含动态贴图）挪到两格总中心：自身中心 + 半格
        final double pairShift = hasRight ? 0.5D : 0D;

        final long now = System.currentTimeMillis();
        final long platformId = TrdmPisData.resolvePlatformId(pos, entity.getPlatformId());
        final float tickerOffset = (now % 60_000L) / 1000F * TrdmPisPanel.TICKER_SPEED;
        // 跑马灯：先滚全局文字，再滚这块屏自己的（见 TrdmPisGlobal）
        final TrdmPisPanel.View view = TrdmPisData.view(platformId,
                TrdmPisGlobal.combined(entity.getTicker()), tickerOffset);

        final Holder holder = HOLDERS.computeIfAbsent(pos, ignored -> new Holder());
        final boolean adsEnabled = entity.isAdsEnabled();
        final TrdmPisAds.Ad ad = adsEnabled
                ? TrdmPisAds.current(resolveAdOrder(entity), entity.getAdEnabled(), entity.getAdTimes())
                : null;
        updatePlate(holder, view, ad, adsEnabled);
        if (holder.plateId == null) {
            return;
        }

        // ===== 坐标轴：模型 +X → facing，模型 +Z → -right（观众右手方向的反向），+Y → 上 =====
        final Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        final Vec3 up = new Vec3(0, 1, 0);
        final Vec3 right = up.cross(normal);
        // 模型、屏幕都挂在这个原点上，贴图自然跟着一起挪
        final Vec3 origin = new Vec3(0.5, 0.5, 0.5).add(right.scale(pairShift));
        final Matrix4f pose = matrices.last().pose();

        // 外壳：整份模型（含 UV / 贴图）画两遍，拼成完整屏幕
        emitModel(pose, vertexConsumers, TrdmModel.load(TrdmModel.PIDS), origin, normal, right, up, light);
        // 吊杆：每格一根，对准显示屏柱的立杆位置，往上接到 1.5 格
        emitRods(pose, vertexConsumers, origin, normal, right, up, light);

        // ===== 屏幕：盖在挡板外表面上，正面一块、背面一块 =====
        final Vec3 middle = origin.add(up.scale(SCREEN_CENTRE_Y));
        final double faceOffset = PLATE_FACE_PX / 16D + SURFACE_GAP;
        final VertexConsumer plate = vertexConsumers.getBuffer(MoreRenderLayers.getLight(holder.plateId, false));
        emitQuad(pose, plate, middle.add(normal.scale(faceOffset)), right, up,
                SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT, normal, IGui.ARGB_WHITE, MAX_LIGHT_GLOWING);
        emitQuad(pose, plate, middle.subtract(normal.scale(faceOffset)), right.scale(-1), up,
                SCREEN_HALF_WIDTH, SCREEN_HALF_HEIGHT, normal.scale(-1), IGui.ARGB_WHITE, MAX_LIGHT_GLOWING);

        // ===== 视频广告：单独一张贴图、单独一个四边形压在广告区上 =====
        // 不跟版面一起烘，所以视频帧一到就能上屏（不受版面 30Hz 合成节流），播 30fps 也顺
        if (holder.videoFrame != null && uploadVideoFrame(holder)) {
            final Vec3 adMiddle = middle.add(up.scale(AD_CENTRE_V));
            final double layer = faceOffset + VIDEO_LAYER_GAP;
            final VertexConsumer video = vertexConsumers.getBuffer(
                    MoreRenderLayers.getLight(holder.videoId, false));
            // 正面：u 轴向右，广告区中心在 +right 一侧
            emitQuad(pose, video, adMiddle.add(right.scale(AD_CENTRE_U)).add(normal.scale(layer)), right, up,
                    AD_HALF_U, AD_HALF_V, normal, IGui.ARGB_WHITE, MAX_LIGHT_GLOWING);
            // 背面：u 轴反过来，广告区中心也跟着翻到 -right 一侧，否则和版面里的广告位错开
            emitQuad(pose, video, adMiddle.subtract(right.scale(AD_CENTRE_U)).subtract(normal.scale(layer)),
                    right.scale(-1), up, AD_HALF_U, AD_HALF_V, normal.scale(-1),
                    IGui.ARGB_WHITE, MAX_LIGHT_GLOWING);
        }
    }

    /**
     * 视频帧有变化才上传（尺寸变了就重建贴图）。
     *
     * @return 现在这张视频贴图能不能用
     */
    private static boolean uploadVideoFrame(Holder holder) {
        final BufferedImage frame = holder.videoFrame;
        if (frame == null) {
            return false;
        }
        final boolean sameSize = holder.videoNative != null && holder.videoTexture != null
                && holder.videoNative.getWidth() == frame.getWidth()
                && holder.videoNative.getHeight() == frame.getHeight();
        if (sameSize) {
            if (holder.videoSeq == holder.videoUploadedSeq) {
                return true;
            }
            fillNativeImage(holder.videoNative, frame);
            holder.videoTexture.upload();
            holder.videoUploadedSeq = holder.videoSeq;
            return true;
        }
        if (holder.videoTexture != null) {
            Minecraft.getInstance().getTextureManager().release(holder.videoId);
        }
        holder.videoNative = new NativeImage(NativeImage.Format.RGBA, frame.getWidth(), frame.getHeight(), false);
        fillNativeImage(holder.videoNative, frame);
        holder.videoTexture = new DynamicTexture(holder.videoNative);
        holder.videoTexture.setFilter(true, false);
        holder.videoId = new ResourceLocation(Trdm.MOD_ID, "dynamic/pis_video_" + (counter++));
        Minecraft.getInstance().getTextureManager().register(holder.videoId, holder.videoTexture);
        holder.videoUploadedSeq = holder.videoSeq;
        return true;
    }

    /**
     * 把模型里的每个元素画两遍（左半块 / 右半块），每个面按模型文件里的 UV 和贴图取样。
     * <p>
     */
    private static void emitModel(Matrix4f pose, MultiBufferSource buffers, List<TrdmModel.Element> elements,
                                  Vec3 origin, Vec3 normal, Vec3 right, Vec3 up, int light) {
        final Map<ResourceLocation, VertexConsumer> consumers = new HashMap<>();
        for (final TrdmModel.Element element : elements) {
            if (ROD_ELEMENT.equals(element.name())) {
                // 立杆单独按格画（见 emitRods），这里跳过模型自带的那个
                continue;
            }
            final float top = element.y2();
            for (int half = 0; half < 2; half++) {
                // 挡板宽度方向（right）：right 增大 = 模型 z 减小
                final float r1 = half == 0 ? -element.z2() / 16F : element.z1() / 16F;
                final float r2 = half == 0 ? -element.z1() / 16F : element.z2() / 16F;
                final float f1 = (element.x1() - 8F) / 16F;
                final float f2 = (element.x2() - 8F) / 16F;
                final float h1 = (element.y1() - 8F) / 16F;
                final float h2 = (top - 8F) / 16F;

                final Vec3 centre = origin
                        .add(normal.scale((f1 + f2) / 2D))
                        .add(right.scale((r1 + r2) / 2D))
                        .add(up.scale((h1 + h2) / 2D));
                final float halfF = (f2 - f1) / 2F;
                final float halfR = Math.abs(r2 - r1) / 2F;
                final float halfH = (h2 - h1) / 2F;

                // 六个面：模型 +X = +normal、+Z = -right、+Y = +up
                emitModelFace(pose, buffers, consumers, centre.add(normal.scale(halfF)), right, up, halfR, halfH,
                        normal, element.faces().get(Direction.EAST), SHADE_FRONT, light);
                emitModelFace(pose, buffers, consumers, centre.add(normal.scale(-halfF)), right, up, halfR, halfH,
                        normal.scale(-1), element.faces().get(Direction.WEST), SHADE_FRONT, light);
                emitModelFace(pose, buffers, consumers, centre.add(right.scale(-halfR)), normal, up, halfF, halfH,
                        right.scale(-1), element.faces().get(Direction.SOUTH), SHADE_FRONT, light);
                emitModelFace(pose, buffers, consumers, centre.add(right.scale(halfR)), normal, up, halfF, halfH,
                        right, element.faces().get(Direction.NORTH), SHADE_FRONT, light);
                emitModelFace(pose, buffers, consumers, centre.add(up.scale(halfH)), right, normal.scale(-1), halfR, halfF,
                        up, element.faces().get(Direction.UP), SHADE_UP, light);
                emitModelFace(pose, buffers, consumers, centre.add(up.scale(-halfH)), right, normal, halfR, halfF,
                        up.scale(-1), element.faces().get(Direction.DOWN), SHADE_DOWN, light);
            }
        }
    }

    /**
     * 吊杆：<b>每格一根</b>，位置就取模型立杆在方块里的位置（x = 8、z = 9），
     * 也就是两格各自中心线，正好和玩家摆在上方的「显示屏柱」（同一个 z = 9）接得上；
     * 高度从挡板顶（y = 9）接到 {@link #ROD_TOP_PX}，伸进上面那格里。
     */
    private static void emitRods(Matrix4f pose, MultiBufferSource buffers, Vec3 origin, Vec3 normal, Vec3 right,
                                 Vec3 up, int light) {
        final VertexConsumer vertexConsumer = buffers.getBuffer(RenderType.entityCutout(SHELL_METAL));
        final float centerH = (ROD_BOTTOM_PX + ROD_TOP_PX) / 2F;
        final float halfH = (ROD_TOP_PX - ROD_BOTTOM_PX) / 2F;
        for (int cell = 0; cell < 2; cell++) {
            // 两格各自的中心线，再加上立杆相对方块中心的偏移（z = 9 ⇒ 往 -right 偏 1 像素）
            final float cellCentre = cell == 0 ? -0.5F : 0.5F;
            final float r = cellCentre + (8F - ROD_Z_PX) / 16F;
            final Vec3 centre = origin
                    .add(right.scale(r))
                    .add(up.scale((centerH - 8F) / 16F));
            emitBox(pose, vertexConsumer, centre, normal, right, up,
                    ROD_SIZE_PX / 2F / 16F, ROD_SIZE_PX / 2F / 16F, halfH / 16F, light);
        }
    }

    /** 一个轴对齐盒子的六个面（法线沿 {@code normal} / {@code right} / {@code up}），带原版那种逐面明暗。 */
    private static void emitBox(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 centre, Vec3 normal, Vec3 right,
                                Vec3 up, float halfF, float halfR, float halfH, int light) {
        final Vec3 back = normal.scale(-1);
        final Vec3 left = right.scale(-1);
        final Vec3 down = up.scale(-1);
        emitQuad(pose, vertexConsumer, centre.add(normal.scale(halfF)), right, up, halfR, halfH,
                normal, shade(SHADE_FRONT), light);
        emitQuad(pose, vertexConsumer, centre.add(back.scale(halfF)), right, up, halfR, halfH,
                back, shade(SHADE_FRONT), light);
        emitQuad(pose, vertexConsumer, centre.add(right.scale(halfR)), back, up, halfF, halfH,
                right, shade(SHADE_SIDE), light);
        emitQuad(pose, vertexConsumer, centre.add(left.scale(halfR)), back, up, halfF, halfH,
                left, shade(SHADE_SIDE), light);
        emitQuad(pose, vertexConsumer, centre.add(up.scale(halfH)), right, back, halfR, halfF,
                up, shade(SHADE_UP), light);
        emitQuad(pose, vertexConsumer, centre.add(down.scale(halfH)), right, normal, halfR, halfF,
                down, shade(SHADE_DOWN), light);
    }

    /** 模型的一个面：贴图、UV（模型里是 0~16）和明暗都按模型文件来。 */
    private static void emitModelFace(Matrix4f pose, MultiBufferSource buffers,
                                      Map<ResourceLocation, VertexConsumer> consumers, Vec3 centre, Vec3 uAxis,
                                      Vec3 vAxis, float halfU, float halfV, Vec3 outward, TrdmModel.Face face,
                                      float shade, int light) {
        if (face == null || face.texture() == null) {
            return;
        }
        final VertexConsumer vertexConsumer = consumers.computeIfAbsent(face.texture(),
                texture -> buffers.getBuffer(RenderType.entityCutout(texture)));
        emitQuad(pose, vertexConsumer, centre, uAxis, vAxis, halfU, halfV, outward,
                shade(shade), light, face.u1() / 16F, face.v1() / 16F, face.u2() / 16F, face.v2() / 16F);
    }

    /** 一片矩形：以 {@code centre} 为中心，{@code uAxis} 横向、{@code vAxis} 纵向（纹理 v=0 在上）。 */
    private static void emitQuad(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 centre, Vec3 uAxis, Vec3 vAxis,
                                 float halfU, float halfV, Vec3 outward, int argb, int light) {
        emitQuad(pose, vertexConsumer, centre, uAxis, vAxis, halfU, halfV, outward, argb, light, 0F, 0F, 1F, 1F);
    }

    private static void emitQuad(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 centre, Vec3 uAxis, Vec3 vAxis,
                                 float halfU, float halfV, Vec3 outward, int argb, int light,
                                 float u1, float v1, float u2, float v2) {
        final Vec3 topLeft = centre.add(uAxis.scale(-halfU)).add(vAxis.scale(halfV));
        final Vec3 topRight = centre.add(uAxis.scale(halfU)).add(vAxis.scale(halfV));
        final Vec3 bottomRight = centre.add(uAxis.scale(halfU)).add(vAxis.scale(-halfV));
        final Vec3 bottomLeft = centre.add(uAxis.scale(-halfU)).add(vAxis.scale(-halfV));
        final Vec3[] corners = {topLeft, topRight, bottomRight, bottomLeft};
        final float[] u = {u1, u2, u2, u1};
        final float[] v = {v1, v1, v2, v2};

        final Vec3 ab = topRight.subtract(topLeft);
        final Vec3 ac = bottomRight.subtract(topLeft);
        final Vec3 cross = new Vec3(ab.y * ac.z - ab.z * ac.y, ab.z * ac.x - ab.x * ac.z, ab.x * ac.y - ab.y * ac.x);
        final boolean reversed = cross.dot(outward) < 0;

        for (int index = 0; index < 4; index++) {
            final int actual = reversed ? 3 - index : index;
            final Vec3 corner = corners[actual];
            vertexConsumer.vertex(pose, (float) corner.x, (float) corner.y, (float) corner.z)
                    .color(argb)
                    .uv(u[actual], v[actual])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal((float) outward.x, (float) outward.y, (float) outward.z)
                    .endVertex();
        }
    }

    /** 按明暗系数给 ARGB 上色（原版方块模型那种六面明暗）。 */
    private static int shade(float factor) {
        final int value = Math.max(0, Math.min(255, Math.round(255F * factor)));
        return 0xFF000000 | (value << 16) | (value << 8) | value;
    }

    private static List<String> resolveAdOrder(BlockTrdmPis.TileEntityTrdmPis entity) {
        if (!entity.getAdOrder().isEmpty()) {
            return entity.getAdOrder();
        }
        final List<String> ids = new ArrayList<>();
        for (final TrdmPisAds.Ad ad : TrdmPisAds.list()) {
            ids.add(ad.id());
        }
        return ids;
    }

    /**
     * 三段式烘焙（跑马灯滚动丝不丝滑就看这个分工）：
     * <ol>
     */
    private static void updatePlate(Holder holder, TrdmPisPanel.View view, TrdmPisAds.Ad ad, boolean adsEnabled) {
        final long now = System.currentTimeMillis();
        // ===== 1. 版面底图（和广告 / 跑马灯都无关）=====
        if (holder.base == null) {
            holder.base = new BufferedImage(PLATE_WIDTH, PLATE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
            holder.baseStamp = 0L;
        }
        if (now - holder.baseStamp >= PLATE_INTERVAL_MS) {
            // 复用同一张图，避免每帧 new 一张（每块屏每秒 10 张会把 GC 压垮）
            TrdmPisPanel.renderBaseInto(holder.base, view,
                    TrdmPisAssets.image(TrdmPisAssets.CAR_HEAD),
                    TrdmPisAssets.image(TrdmPisAssets.CAR_MIDDLE),
                    TrdmPisAssets.image(TrdmPisAssets.CAR_TAIL));
            holder.baseStamp = now;
            holder.plateKey = "";
        }
        // ===== 2. 广告当前帧：静态图整张，gif / mp4 按时间取帧 =====
        // 视频解码器只保留「当前这条广告」：切走 / 关掉广告立刻释放；没视频时这里只是空 map 判断
        TrdmPisVideo.keepOnly(adsEnabled && ad != null ? ad.texture() : null);
        BufferedImage adImage = null;
        int adFrame = -1;
        if (adsEnabled && ad != null) {
            adFrame = TrdmPisAssets.adFrame(ad.texture(), now);
            adImage = TrdmPisAssets.ad(ad.texture(), now);
        }
        if (adsEnabled && adImage == null) {
            adImage = TrdmPisAssets.image(TrdmPisAssets.AD_DEFAULT);
            adFrame = -1;
        }
        // 视频广告：帧和帧号单独交给 render() 用独立贴图画，不烘进版面（视频才能出满帧率）
        final boolean videoAd = adsEnabled && TrdmPisVideo.isVideo(ad == null ? null : ad.texture());
        holder.videoFrame = videoAd ? adImage : null;
        holder.videoSeq = videoAd ? adFrame : -1;
        final String adKey;
        if (!adsEnabled) {
            adKey = "off";
        } else if (ad == null) {
            adKey = "default";
        } else if (videoAd) {
            adKey = ad.texture().toString();          // 视频帧不进版面，key 里不带帧号
        } else {
            adKey = ad.id() + "@" + ad.texture() + "#" + adFrame;
        }
        // 跑马灯偏移量化到 0.02 画布单位（≈1 像素），保证 30Hz 下每帧都有位移
        final String tickerKey = view.ticker() + "@" + Math.round(view.tickerOffset() * 50F);
        final String key = holder.baseStamp + "|" + adKey + "|" + tickerKey;
        if (holder.plate != null && key.equals(holder.plateKey) && now - holder.plateStamp < ANIMATED_INTERVAL_MS) {
            return;
        }
        // ===== 3. 合成（30 Hz）：底图 + 广告 + 跑马灯 =====
        if (holder.composite == null) {
            holder.composite = new BufferedImage(PLATE_WIDTH, PLATE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        }
        final java.awt.Graphics2D graphics = holder.composite.createGraphics();
        try {
            graphics.setComposite(java.awt.AlphaComposite.Src);
            graphics.drawImage(holder.base, 0, 0, null);
            graphics.setComposite(java.awt.AlphaComposite.SrcOver);
            // 视频走单独的四边形，版面里就不要再贴一遍
            TrdmPisPanel.drawAd(graphics, PLATE_WIDTH, videoAd ? null : adImage);
            TrdmPisPanel.drawTickerInto(graphics, PLATE_WIDTH, view.ticker(), view.tickerOffset());
        } finally {
            graphics.dispose();
        }
        if (holder.plate == null) {
            holder.nativeImage = new NativeImage(NativeImage.Format.RGBA, PLATE_WIDTH, PLATE_HEIGHT, false);
            fillNativeImage(holder.nativeImage, holder.composite);
            holder.plate = new DynamicTexture(holder.nativeImage);
            holder.plate.setFilter(true, false);
            holder.plateId = new ResourceLocation(Trdm.MOD_ID, "dynamic/pis_plate_" + (counter++));
            Minecraft.getInstance().getTextureManager().register(holder.plateId, holder.plate);
        } else {
            fillNativeImage(holder.nativeImage, holder.composite);
            holder.plate.upload();
        }
        holder.plateKey = key;
        holder.plateStamp = now;
    }

    /** 把 BufferedImage 的像素写进已有的 NativeImage（尺寸必须一致），省掉每帧新建。 */
    private static void fillNativeImage(NativeImage nativeImage, BufferedImage image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int argb = pixels[y * width + x];
                nativeImage.setPixelRGBA(x, y,
                        (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return true;
    }
}
