package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mtr.block.BlockPSDAPGBase;
import mtr.block.IBlock;
import mtr.client.ClientCache;
import mtr.client.ClientData;
import mtr.mappings.BlockEntityRendererMapper;
import mtr.render.MoreRenderLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

import top.xiaotang27.trdm.block.BlockTrdmPSDEndGate;
import top.xiaotang27.trdm.block.BlockTrdmPSDTop;
import top.xiaotang27.trdm.client.TrdmEndGateDrag;

/**
 * 屏蔽门端门的渲染器，管三件事（都从「铰链那一格的下半格」画一次）：
 * <ol>
 *     <li><b>门扇</b>：整段玻璃当一扇大门，绕着最外侧那根竖轴整体转 90°、摆向站台侧。
 *         一整扇 N 格宽的门转开之后已经跨到别的格子里，静态方块模型只能画自己那格，所以必须 BER；</li>
 *     <li><b>玻璃装饰条</b>：跟着门扇一起转 —— 关着时贴在玻璃面上，开着时贴在旋出来的门扇上；</li>
 *     <li><b>门楣灯</b>：门楣上只留这一盏灯（吊板 / 线路图 / 色带都不画），开门亮、关门灭，
 *         位置按整段长度居中。</li>
 * </ol>
 * 门楣那一侧（{@code RenderTrdmPSDTop}）见到下面是自己就会直接返回，避免两边重复画。
 */
public class RenderTrdmPSDEndGate<T extends BlockTrdmPSDEndGate.TileEntityTrdmPSDEndGate>
        extends BlockEntityRendererMapper<T> implements IBlock {

    /**
     * 端门的玻璃 / 门楣模型整体比普通 PSD 玻璃沿 <b>-x</b> 多挪了 2 像素
     * （普通玻璃是 x 2~4，端门是 x 0~2；门楣同理 0~4）。
     * 渲染端所有「离方块中心的距离」都要加上这个偏移，否则会和模型错开。
     */
    private static final float MODEL_X_OFFSET = 2F / 16F;

    /** 玻璃板中面离方块中心的距离（沿 facing 量）：玻璃 x 0~2，中面 1/16。 */
    private static final double PANE_MID_OFFSET = 0.5D - 1D / 16D;

    private static final float[] UP_AXIS = {0F, 1F, 0F};

    /** 一段门排最多几格（纯粹防死循环）。 */
    private static final int MAX_RUN = 64;

    // ===== 装饰条（版面与 RenderTrdmPSDTop 里玻璃装饰带完全一致，只是画法跟着门走）=====
    private static final float BAND_HEIGHT = TrdmDecalBand.HEIGHT_BLOCKS;
    /** 装饰带竖直中心：两格玻璃的中缝（下半格往上 1 格）再抬一点。 */
    private static final float BAND_CENTRE_Y = 1F + 2F / 16F;
    /** 玻璃两个面到方块中心的距离（法线朝 -facing 那面 / 朝 +facing 那面），已含端门那 2 像素偏移。 */
    private static final float GLASS_SURFACE_4 = 4F / 16F + MODEL_X_OFFSET;
    private static final float GLASS_SURFACE_6 = 6F / 16F + MODEL_X_OFFSET;
    private static final float BAND_NUDGE = 0.004F;
    private static final float BAND_END_INSET = 1F / 16F;

    // ===== 门楣灯 =====
    /** 灯的边长（格）。MTR 滑动门门灯本体是 1/16，太小看不清，这里用 3/16。 */
    private static final float LAMP_SIZE = 3F / 16F;
    /** 灯底边离门楣下沿多远（门楣是下半格往上第 2 格）。 */
    private static final float LAMP_FROM_BOTTOM = 3F / 16F;
    /** 和滑动门一样的门灯贴图（MTR 原版 light_on / light_off）。 */
    private static final ResourceLocation LAMP_ON = new ResourceLocation("mtr", "textures/block/light_on.png");
    private static final ResourceLocation LAMP_OFF = new ResourceLocation("mtr", "textures/block/light_off.png");

    // ===== 拖动时的开度指示条（门旁 3D 竖条）=====
    /** 纯白贴图，用来配合顶点色画纯色面。 */
    private static final ResourceLocation WHITE = new ResourceLocation("trdm", "textures/block/white.png");
    /** 指示条整体高度（格）、宽（半宽，格）、离地高度。 */
    private static final float INDICATOR_HEIGHT = 1.1F;
    private static final float INDICATOR_HALF_WIDTH = 3F / 16F;
    private static final float INDICATOR_BOTTOM = 4F / 16F;
    /** 底色（深灰）与填充色（天蓝）。 */
    private static final int INDICATOR_BACK_COLOR = 0xFF23272F;
    private static final int INDICATOR_FILL_COLOR = 0xFF48B0F7;

    public RenderTrdmPSDEndGate(BlockEntityRenderDispatcher dispatcher) {
        super(dispatcher);
    }

    @Override
    public void render(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers,
                       int light, int overlay) {
        final Level world = entity.getLevel();
        if (world == null) {
            return;
        }
        final BlockPos pos = entity.getBlockPos();
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmPSDEndGate)) {
            return;
        }
        // 门两格高，只在下半格那一次里画完
        if (IBlock.getStatePropertySafe(state, HALF) != DoubleBlockHalf.LOWER) {
            return;
        }

        final Direction facing = IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING);
        final int back = runLength(world, pos, facing, facing.getCounterClockWise());
        final int forth = runLength(world, pos, facing, facing.getClockWise());

        // 铰链放在「最外侧」那一端：那一侧再往外已经不是屏蔽门门排了（门排到头）
        final boolean ccwOuter = !psdRunContinues(world, pos.relative(facing.getCounterClockWise(), back + 1));
        final BlockPos hingeCell;
        final Direction intoRun;
        if (ccwOuter) {
            hingeCell = pos.relative(facing.getCounterClockWise(), back);
            intoRun = facing.getClockWise();
        } else {
            hingeCell = pos.relative(facing.getClockWise(), forth);
            intoRun = facing.getCounterClockWise();
        }
        if (!hingeCell.equals(pos)) {
            return;
        }

        final int step = BlockTrdmPSDEndGate.openStep(state);
        // 连续角度：拖动这一段时按鼠标位置现算（20 Hz 之外也能平滑），否则用状态里的量化角
        final float angleDegrees = TrdmEndGateDrag.angleDegrees(state, pos);

        // 铰链竖轴：沿 facing 让到玻璃板中面，沿门排落在铰链那一格的边界上
        double pivotX = hingeCell.getX() + 0.5D + facing.getStepX() * PANE_MID_OFFSET;
        double pivotZ = hingeCell.getZ() + 0.5D + facing.getStepZ() * PANE_MID_OFFSET;
        if (facing.getAxis() == Direction.Axis.Z) {
            // 门排沿 x（facing 是南北），边界在 x 方向
            pivotX = hingeCell.getX() + (intoRun.getStepX() > 0 ? 0D : 1D);
        } else {
            pivotZ = hingeCell.getZ() + (intoRun.getStepZ() > 0 ? 0D : 1D);
        }

        // 门扇 + 装饰条共用同一套变换：按当前角度绕铰链转（0° = 关着不动）
        matrices.pushPose();
        if (step > 0) {
            final float signedAngle = Math.copySign(angleDegrees, swingAngle(intoRun, facing));
            matrices.translate(pivotX - pos.getX(), 0D, pivotZ - pos.getZ());
            matrices.mulPose(Axis.YP.rotationDegrees(signedAngle));
            matrices.translate(-(pivotX - pos.getX()), 0D, -(pivotZ - pos.getZ()));
            renderLeaf(world, pos, facing, back, forth, matrices, vertexConsumers, light, overlay);
        }
        renderDecalBand(world, pos, facing, back, forth, matrices, vertexConsumers, light);
        matrices.popPose();

        // 灯挂在门楣上，不跟门转
        renderLamp(world, pos, facing, back, forth, step > 0, matrices, vertexConsumers, light);

        // 拖动时在门前画一根竖直开度指示条（跟着整段一起只由铰链那一格画）
        if (TrdmEndGateDrag.indicatorAt(pos)) {
            renderIndicator(pos, facing, back, forth, angleDegrees, matrices, vertexConsumers, light);
        }
    }

    /**
     * 拖动时的开度指示条：挂在门**前方**（站台那一侧）的竖直条 —— 不放进格子边缘，
     * 免得被旁边的墙 / 门楣挡住。底下一个深灰的槽、上面按开度从下往上填天蓝色。
     * 只在拖动那段时间出现，跟着整段门走（由铰链那一格画一次），两面都画，站哪边都看得见。
     */
    private void renderIndicator(BlockPos pos, Direction facing, int back, int forth, float angleDegrees,
                                 PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        final VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.entitySolid(WHITE));
        final Direction runDir = facing.getClockWise();
        final float[] runAxis = {runDir.getStepX(), 0F, runDir.getStepZ()};
        final float[] facingAxis = {facing.getStepX(), 0F, facing.getStepZ()};
        // 站台那一侧 = -facing；distance 变小就是往站台外走（见 MODEL_X_OFFSET 那段的坐标约定）
        final float distance = GLASS_SURFACE_4 - 0.55F;
        final float centreX = 0.5F + runAxis[0] * ((forth - back) / 2F) + facingAxis[0] * distance;
        final float centreZ = 0.5F + runAxis[2] * ((forth - back) / 2F) + facingAxis[2] * distance;
        final float[] backCentre = {centreX, INDICATOR_BOTTOM + INDICATOR_HEIGHT / 2F, centreZ};
        final float[] normal = {-facingAxis[0], 0F, -facingAxis[2]};
        final float[] opposite = {facingAxis[0], 0F, facingAxis[2]};
        for (final float[] side : new float[][]{normal, opposite}) {
            emitQuad(matrices.last().pose(), consumer, backCentre, runAxis, INDICATOR_HALF_WIDTH, UP_AXIS,
                    INDICATOR_HEIGHT / 2F, side, light, INDICATOR_BACK_COLOR);
        }

        final float fraction = Math.max(0F, Math.min(1F, angleDegrees / TrdmEndGateDrag.MAX_ANGLE));
        if (fraction > 0.001F) {
            final float fillHeight = INDICATOR_HEIGHT * fraction;
            final float[] fillCentre = {centreX, INDICATOR_BOTTOM + fillHeight / 2F, centreZ};
            for (final float[] side : new float[][]{normal, opposite}) {
                emitQuad(matrices.last().pose(), consumer, fillCentre, runAxis, INDICATOR_HALF_WIDTH, UP_AXIS,
                        fillHeight / 2F, side, light, INDICATOR_FILL_COLOR);
            }
        }
    }

    /** 整段门扇：逐格、上下两格各画一次「关着」那套 MTR 玻璃模型。 */
    private void renderLeaf(Level world, BlockPos pos, Direction facing, int back, int forth,
                            PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        for (int i = -back; i <= forth; i++) {
            final BlockPos cell = pos.relative(facing.getClockWise(), i);
            for (int dy = 0; dy <= 1; dy++) {
                final BlockPos at = cell.above(dy);
                final BlockState cellState = world.getBlockState(at);
                if (!(cellState.getBlock() instanceof BlockTrdmPSDEndGate)) {
                    continue;
                }
                matrices.pushPose();
                matrices.translate(at.getX() - pos.getX(), at.getY() - pos.getY(), at.getZ() - pos.getZ());
                // 画「关着」的状态：开着的状态在 blockstate 里指向空模型
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                        cellState.setValue(BlockTrdmPSDEndGate.OPEN, 0), matrices, vertexConsumers, light, overlay);
                matrices.popPose();
            }
        }
    }

    /**
     * 玻璃装饰条：线路色底 + 中央 slogan，**不画人字纹**（spec 第 5 条）。
     * 画在门扇同一套 pose 里，所以开门时它会跟着门一起转。
     */
    private void renderDecalBand(Level world, BlockPos pos, Direction facing, int back, int forth,
                                 PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        final BlockPos headerPos = pos.above(2);
        if (!(world.getBlockEntity(headerPos) instanceof BlockTrdmPSDTop.TileEntityTrdmPSDTop header)) {
            return;
        }
        final long platformId = header.getPlatformId(ClientData.PLATFORMS, ClientData.DATA_CACHE);
        if (platformId == 0) {
            return;
        }
        final List<ClientCache.PlatformRouteDetails> routes = ClientData.DATA_CACHE.requestPlatformIdToRoutes(platformId);
        if (routes == null || routes.isEmpty()) {
            return;
        }
        final BlockState headerState = world.getBlockState(headerPos);
        final int bandStyle = BlockTrdmPSDTop.bandStyle(headerState);
        final boolean arrowLeft = (IBlock.getStatePropertySafe(headerState, BlockTrdmPSDTop.ARROW_DIRECTION)
                & 0b01) == 0;
        final int runBlocks = back + forth + 1;
        final float span = Math.max(BAND_HEIGHT, runBlocks - BAND_END_INSET * 2);
        final ResourceLocation texture = TrdmHeaderTextures.getDecalBand(
                bandStyle, routes.get(0).routeColor, span / BAND_HEIGHT, arrowLeft, false, true);
        if (texture == null) {
            return;
        }

        final VertexConsumer consumer = vertexConsumers.getBuffer(RenderType.entityCutout(texture));
        final Direction runDir = facing.getClockWise();
        // 沿门排的中心（格数坐标里 pos 自己中心是 0）
        final float midAlong = (forth - back) / 2F;
        final float[] uAxis = {runDir.getStepX(), 0F, runDir.getStepZ()};
        // 玻璃两个面：朝站台那面（法线 = -facing）和朝轨道那面（法线 = facing）
        for (final boolean platformFace : new boolean[]{true, false}) {
            final float distance = platformFace ? GLASS_SURFACE_4 - BAND_NUDGE : GLASS_SURFACE_6 + BAND_NUDGE;
            final float[] normal = platformFace
                    ? new float[]{-facing.getStepX(), 0F, -facing.getStepZ()}
                    : new float[]{facing.getStepX(), 0F, facing.getStepZ()};
            final float[] centre = {
                    0.5F + uAxis[0] * midAlong + facing.getStepX() * distance,
                    BAND_CENTRE_Y,
                    0.5F + uAxis[2] * midAlong + facing.getStepZ() * distance};
            emitQuad(matrices.last().pose(), consumer, centre, uAxis, span / 2F, UP_AXIS, BAND_HEIGHT / 2F, normal,
                    light, 0xFFFFFFFF);
        }
    }

    /**
     * 门楣灯：和滑动门那块门灯<b>同一张贴图</b>（`mtr:textures/block/light_on|off.png`），
     * 画成一个小立方体，贴在门楣朝站台那面、沿整段居中；开门亮（打满亮度）、关门灭。
     * <p>
     * 渲染层只能用 {@code MoreRenderLayers.getExterior}（= {@code RenderType.entityCutout}，
     * 顶点格式 NEW_ENTITY，和 {@link #emitQuad} 写的属性对得上）。
     * <b>不能</b>用 {@code getLight}：它内部是 {@code RenderType.beaconBeam}，顶点格式是 BLOCK
     * （position/color/uv/lightmap），没有 overlay/normal，按 NEW_ENTITY 写进去就是乱码 —— 这就是
     * 之前灯一直「炸」的原因。亮的时候靠 {@code uv2 = 满亮度} 表现发光。
     */
    private void renderLamp(Level world, BlockPos pos, Direction facing, int back, int forth, boolean open,
                            PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        if (!(world.getBlockState(pos.above(2)).getBlock() instanceof BlockTrdmPSDTop)) {
            return;
        }
        final ResourceLocation texture = open ? LAMP_ON : LAMP_OFF;
        final VertexConsumer consumer = vertexConsumers.getBuffer(MoreRenderLayers.getExterior(texture));
        final Direction runDir = facing.getClockWise();
        final float[] runAxis = {runDir.getStepX(), 0F, runDir.getStepZ()};
        final float[] facingAxis = {facing.getStepX(), 0F, facing.getStepZ()};
        final float midAlong = (forth - back) / 2F;
        // 门楣模型厚 4/16（x 0~4），朝站台那面在离中心 4/16 处；灯再往外挪半个身位，整颗凸在面外
        final float distance = 2F / 16F + MODEL_X_OFFSET - LAMP_SIZE / 2F;
        final float[] centre = {
                0.5F + runAxis[0] * midAlong + facingAxis[0] * distance,
                2F + LAMP_FROM_BOTTOM + LAMP_SIZE / 2F,
                0.5F + runAxis[2] * midAlong + facingAxis[2] * distance};
        final float half = LAMP_SIZE / 2F;
        // 亮的时候打满亮度，看着才像在发光
        final int lampLight = open ? 0xF000F0 : light;
        // 六个面：±站台法线、±跑向、±竖直（上下两个面不能省，否则从下面看是空壳）
        emitQuad(matrices.last().pose(), consumer, centre, runAxis, half, UP_AXIS, half,
                new float[]{-facingAxis[0], 0F, -facingAxis[2]}, lampLight, 0xFFFFFFFF);
        emitQuad(matrices.last().pose(), consumer, centre, runAxis, half, UP_AXIS, half,
                facingAxis, lampLight, 0xFFFFFFFF);
        emitQuad(matrices.last().pose(), consumer, centre, facingAxis, half, UP_AXIS, half,
                runAxis, lampLight, 0xFFFFFFFF);
        emitQuad(matrices.last().pose(), consumer, centre, facingAxis, half, UP_AXIS, half,
                new float[]{-runAxis[0], 0F, -runAxis[2]}, lampLight, 0xFFFFFFFF);
        emitQuad(matrices.last().pose(), consumer, centre, runAxis, half, facingAxis, half,
                UP_AXIS, lampLight, 0xFFFFFFFF);
        emitQuad(matrices.last().pose(), consumer, centre, runAxis, half, facingAxis, half,
                new float[]{0F, -1F, 0F}, lampLight, 0xFFFFFFFF);
    }

    /**
     * 画一个矩形面：中心 {@code centre}，两条边方向 {@code uAxis}/{@code vAxis}，朝外法线 {@code normal}。
     * 绕序按右手定则现场判定，保证正面朝 {@code normal}。
     */
    private static void emitQuad(Matrix4f pose, VertexConsumer consumer, float[] centre,
                                 float[] uAxis, float halfU, float[] vAxis, float halfV,
                                 float[] normal, int light, int argb) {
        final float[][] corners = {
                {centre[0] + uAxis[0] * halfU + vAxis[0] * halfV,
                        centre[1] + uAxis[1] * halfU + vAxis[1] * halfV,
                        centre[2] + uAxis[2] * halfU + vAxis[2] * halfV, 1F, 0F},
                {centre[0] - uAxis[0] * halfU + vAxis[0] * halfV,
                        centre[1] - uAxis[1] * halfU + vAxis[1] * halfV,
                        centre[2] - uAxis[2] * halfU + vAxis[2] * halfV, 0F, 0F},
                {centre[0] - uAxis[0] * halfU - vAxis[0] * halfV,
                        centre[1] - uAxis[1] * halfU - vAxis[1] * halfV,
                        centre[2] - uAxis[2] * halfU - vAxis[2] * halfV, 0F, 1F},
                {centre[0] + uAxis[0] * halfU, centre[1] - halfV, centre[2] + uAxis[2] * halfU, 1F, 1F}};

        final float[] ab = {corners[1][0] - corners[0][0], corners[1][1] - corners[0][1], corners[1][2] - corners[0][2]};
        final float[] ac = {corners[2][0] - corners[0][0], corners[2][1] - corners[0][1], corners[2][2] - corners[0][2]};
        final float nx = ab[1] * ac[2] - ab[2] * ac[1];
        final float ny = ab[2] * ac[0] - ab[0] * ac[2];
        final float nz = ab[0] * ac[1] - ab[1] * ac[0];
        final boolean reversed = nx * normal[0] + ny * normal[1] + nz * normal[2] < 0;

        final int order = reversed ? -1 : 1;
        final int first = reversed ? 3 : 0;
        for (int i = 0; i < 4; i++) {
            final float[] corner = corners[Math.floorMod(first + order * i, 4)];
            consumer.vertex(pose, corner[0], corner[1], corner[2])
                    .color(argb)
                    .uv(corner[3], corner[4])
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(normal[0], normal[1], normal[2])
                    .endVertex();
        }
    }

    /**
     * 把「从铰链沿门排进去」的方向摆到**站台侧**所需的 90°。
     * <p>
     * 站台侧 = {@code facing} 的<b>反方向</b>：屏蔽门是用 {@code ItemTrdmPSD} 放的，它的
     * {@code FACING} 取的是<b>玩家朝向</b>（{@code BlockPlaceContext#getHorizontalDirection}），
     * 而实装时人是站在站台上朝轨道看放下去的 —— 所以 facing 指轨道侧，站台在它背面。
     * <p>
     * 角度的正负用 {@code Axis.YP} 自己的四元数试出来（和 {@code mulPose} 同一套变换），
     * 不靠手推坐标系符号。
     */
    private static float swingAngle(Direction intoRun, Direction facing) {
        final Direction platform = facing.getOpposite();
        final Vector3f direction = new Vector3f(intoRun.getStepX(), 0F, intoRun.getStepZ());
        direction.rotate(Axis.YP.rotationDegrees(90F));
        final boolean toPlatform = Math.round(direction.x()) == platform.getStepX()
                && Math.round(direction.z()) == platform.getStepZ();
        return toPlatform ? 90F : -90F;
    }

    /** 从 pos 沿 direction 数同方向端门格数（不含 pos 自己）。 */
    private static int runLength(Level world, BlockPos pos, Direction facing, Direction direction) {
        int length = 0;
        while (length < MAX_RUN) {
            final BlockState state = world.getBlockState(pos.relative(direction, length + 1));
            if (!(state.getBlock() instanceof BlockTrdmPSDEndGate)
                    || IBlock.getStatePropertySafe(state, HorizontalDirectionalBlock.FACING) != facing) {
                break;
            }
            length++;
        }
        return length;
    }

    /** 这一格外侧还算不算门排（继续有 PSD 门 / 玻璃 / 端头玻璃）。 */
    private static boolean psdRunContinues(Level world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockPSDAPGBase;
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        // 整扇门都由铰链那一格画，它被视锥剔除就会整扇门消失
        return true;
    }
}
