package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mtr.block.BlockPSDAPGBase;
import mtr.block.IBlock;
import mtr.client.ClientCache;
import mtr.client.ClientData;
import mtr.mappings.BlockEntityRendererMapper;
import mtr.mappings.ModelDataWrapper;
import mtr.mappings.ModelMapper;
import mtr.render.MoreRenderLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.block.BlockTrdmEndGateModels;
import top.xiaotang27.trdm.block.BlockTrdmPSDEndGate;
import top.xiaotang27.trdm.block.BlockTrdmPSDTop;
import top.xiaotang27.trdm.client.TrdmEndGateAnim;
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

    /**
     * 玻璃板中面离方块中心的距离（沿 facing 量）。门扇模型整体转了 180°（前后对调），
     * 所以玻璃现在贴在格子的<b>另一侧</b>：模型 x 14~16，中面 15/16，偏移要取<b>负</b>。
     */
    private static final double PANE_MID_OFFSET = -(0.5D - 1D / 16D);

    private static final float[] UP_AXIS = {0F, 1F, 0F};

    /** 一段门排最多几格（纯粹防死循环）。 */
    private static final int MAX_RUN = 64;

    // ===== 装饰条（版面与 RenderTrdmPSDTop 里玻璃装饰带完全一致，只是画法跟着门走）=====
    private static final float BAND_HEIGHT = TrdmDecalBand.HEIGHT_BLOCKS;
    /** 装饰带竖直中心：两格玻璃的中缝（下半格往上 1 格）再抬一点。 */
    /**
     * 装饰带竖直中心：新端门贴图的透明窗从 y=5 像素一直到 30.5 像素（上下两格合起来），
     * 取窗口正中 = 17.75 像素 = 1.109375 格。
     */
    private static final float BAND_CENTRE_Y = 17.75F / 16F;
    /** 玻璃两个面到方块中心的距离（法线朝 -facing 那面 / 朝 +facing 那面），已含端门那 2 像素偏移。 */
    private static final float GLASS_SURFACE_4 = 4F / 16F + MODEL_X_OFFSET;
    private static final float GLASS_SURFACE_6 = 6F / 16F + MODEL_X_OFFSET;
    private static final float BAND_NUDGE = 0.004F;
    /**
     * 装饰带两端各往里收多少：新贴图的透明窗在每格 z 3/16 ~ 13/16（两侧各 3 像素边框），
     * 收到 3/16 正好贴在透明部分里、不会压到边框。
     */
    private static final float BAND_END_INSET = 3F / 16F;

    // ===== 门楣灯 =====
    /** 灯底边离门楣下沿多远（门楣是下半格往上第 2 格）。灯本身尺寸固定写在 {@link #MODEL_LAMP} 里。 */
    private static final float LAMP_FROM_BOTTOM = 3F / 16F;
    /** 和滑动门一样的门灯贴图（MTR 原版 light_on / light_off）。 */
    private static final ResourceLocation LAMP_ON = new ResourceLocation("mtr", "textures/block/light_on.png");
    private static final ResourceLocation LAMP_OFF = new ResourceLocation("mtr", "textures/block/light_off.png");

    /**
     * 门灯专用的固定模型：<b>3 × 1 × 1 模型像素</b>（就是滑动门左右两盏门灯拼起来的那个大小），
     * 盒子以自身中心为原点、底面在 y = 0，方便直接摆位。
     */
    private static final ModelSingleCube MODEL_LAMP =
            new ModelSingleCube(16, 16, 6.5F, 16F, 7.5F, 3, 1, 1);

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
        // 铰链竖轴：沿 facing 让到玻璃板中面，沿门排落在铰链那一格的边界上
        double pivotX = hingeCell.getX() + 0.5D + facing.getStepX() * PANE_MID_OFFSET;
        double pivotZ = hingeCell.getZ() + 0.5D + facing.getStepZ() * PANE_MID_OFFSET;
        if (facing.getAxis() == Direction.Axis.Z) {
            // 门排沿 x（facing 是南北），边界在 x 方向
            pivotX = hingeCell.getX() + (intoRun.getStepX() > 0 ? 0D : 1D);
        } else {
            pivotZ = hingeCell.getZ() + (intoRun.getStepZ() > 0 ? 0D : 1D);
        }
        // 显示角度：拖动时跟手；单击开 / 关时按固定角速度缓动（所以两个方向都有动画）
        final float angleDegrees = TrdmEndGateAnim.angleDegrees(state, pos);
        final float signedAngle = Math.copySign(angleDegrees, swingAngle(intoRun, facing));
        final float pivotDx = (float) (pivotX - pos.getX());
        final float pivotDz = (float) (pivotZ - pos.getZ());

        // 门扇：新模型本身就是 2 格高的一整扇（玻璃板在 x 0~2，和端门的偏移一致），
        // 所以不再需要那 2 像素平移，且每格只需画一次
        matrices.pushPose();
        matrices.translate(pivotDx, 0F, pivotDz);
        matrices.mulPose(Axis.YP.rotationDegrees(signedAngle));
        matrices.translate(-pivotDx, 0F, -pivotDz);
        renderLeaf(world, pos, facing, back, forth, matrices, vertexConsumers, light, overlay);
        matrices.popPose();

        // 装饰条：位置按端门玻璃面算过（GLASS_SURFACE_*），只跟着门转
        matrices.pushPose();
        matrices.translate(pivotDx, 0F, pivotDz);
        matrices.mulPose(Axis.YP.rotationDegrees(signedAngle));
        matrices.translate(-pivotDx, 0F, -pivotDz);
        renderDecalBand(world, pos, facing, back, forth, matrices, vertexConsumers, light);
        matrices.popPose();

        // 灯挂在门楣上，不跟门转
        renderLamp(world, pos, facing, back, forth, angleDegrees > 1F, matrices, vertexConsumers, light, overlay);
    }

    /**
     * 端门门扇模型对应的技术方块状态：{@code side} 选模型（每格用**自己**的拼接状态，
     * 也就是 MTR {@code connectGlass} 维护的那个值），{@code facing} 让方块状态表按 MTR 玻璃
     * 同样的 {@code y} 旋转表出图（渲染器不再自己算旋转，免得坐标系搞反）。
     */
    private static BlockState leafModelState(Direction facing, mtr.block.IBlock.EnumSide side) {
        return Trdm.PSD_END_GATE_MODELS.defaultBlockState()
                .setValue(BlockTrdmEndGateModels.SIDE, side)
                .setValue(HorizontalDirectionalBlock.FACING, facing);
    }

    /**
     * 整段门扇：逐格画一次**整扇 2 格高**的门扇模型（模型自己就盖住上下两格）。
     * 朝向和左右段选型都在方块状态里，这里只做位置平移。
     */
    private void renderLeaf(Level world, BlockPos pos, Direction facing, int back, int forth,
                            PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        for (int i = -back; i <= forth; i++) {
            final BlockPos cell = pos.relative(facing.getClockWise(), i);
            final BlockState cellState = world.getBlockState(cell);
            if (!(cellState.getBlock() instanceof BlockTrdmPSDEndGate)) {
                continue;
            }
            final BlockState modelState = leafModelState(facing,
                    IBlock.getStatePropertySafe(cellState, IBlock.SIDE_EXTENDED));
            matrices.pushPose();
            matrices.translate(cell.getX() - pos.getX(), cell.getY() - pos.getY(), cell.getZ() - pos.getZ());
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    modelState, matrices, vertexConsumers, light, overlay);
            matrices.popPose();
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
            // 模型翻转后门扇在格子另一侧：平台面是 8/16 那个面、轨道面是 6/16 那个面，偏移取负
            final float distance = platformFace ? -(GLASS_SURFACE_6 + BAND_NUDGE) : -(GLASS_SURFACE_4 - BAND_NUDGE);
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
     * 门楣灯：<b>专用固定模型</b>（{@link #MODEL_LAMP}，3×1×1 像素 = 滑动门左右两盏门灯拼起来那个尺寸），
     * 用 {@link ModelSingleCube} + {@code ModelPart#renderToBuffer} 画 —— 滑动门门灯就是这条路径，
     * 顶点属性由原版 ModelPart 自己按渲染层的格式写，不会像手写四边形那样错位。
     * <p>
     * 位置：门楣（厚 4/16）朝站台那面、下沿往上 {@link #LAMP_FROM_BOTTOM}、整段合并后按长度居中。
     * 亮灯时把亮度打成满格（{@code 0xF000F0}），看着才像在发光。
     */
    private void renderLamp(Level world, BlockPos pos, Direction facing, int back, int forth, boolean open,
                            PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        if (!(world.getBlockState(pos.above(2)).getBlock() instanceof BlockTrdmPSDTop)) {
            return;
        }
        final ResourceLocation texture = open ? LAMP_ON : LAMP_OFF;
        final VertexConsumer consumer = vertexConsumers.getBuffer(MoreRenderLayers.getExterior(texture));
        final Direction runDir = facing.getClockWise();
        final float midAlong = (forth - back) / 2F;
        // 门楣朝站台那面在离中心 4/16，灯厚 1 像素 → 再往外凸半像素
        final float distance = 2F / 16F + MODEL_X_OFFSET - 0.5F / 16F;
        matrices.pushPose();
        matrices.translate(
                0.5F + runDir.getStepX() * midAlong + facing.getStepX() * distance,
                2F + LAMP_FROM_BOTTOM,
                0.5F + runDir.getStepZ() * midAlong + facing.getStepZ() * distance);
        // 让模型的 +x（灯的 3 像素长边）对准门排方向
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        MODEL_LAMP.renderToBuffer(matrices, consumer, open ? 0xF000F0 : light, OverlayTexture.NO_OVERLAY,
                1F, 1F, 1F, 1F);
        matrices.popPose();
    }

    /** 单个长方体模型（照搬滑动门渲染器里的那个小工具类）。 */
    private static class ModelSingleCube extends EntityModel<Entity> {

        private final ModelMapper cube;

        private ModelSingleCube(int textureWidth, int textureHeight, float x, float y, float z,
                                int length, int height, int depth) {
            final ModelDataWrapper modelDataWrapper = new ModelDataWrapper(this, textureWidth, textureHeight);
            cube = new ModelMapper(modelDataWrapper);
            cube.texOffs(0, 0).addBox(x - 8F, y - 16F, z - 8F, length, height, depth, 0, false);
            modelDataWrapper.setModelPart(textureWidth, textureHeight);
            cube.setModelPart();
        }

        @Override
        public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int packedLight, int packedOverlay,
                                   float red, float green, float blue, float alpha) {
            cube.render(matrices, vertices, 0, 0, 0, packedLight, packedOverlay);
        }

        @Override
        public void setupAnim(Entity entity, float limbAngle, float limbDistance, float animationProgress,
                              float headYaw, float headPitch) {
        }
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
