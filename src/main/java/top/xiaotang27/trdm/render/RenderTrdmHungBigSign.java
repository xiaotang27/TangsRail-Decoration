package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.block.IBlock;
import mtr.data.IGui;
import mtr.render.RenderTrains;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import top.xiaotang27.trdm.block.BlockTrdmBigStationSign;
import top.xiaotang27.trdm.block.BlockTrdmHungBigSign;
import top.xiaotang27.trdm.block.SignRuns;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 悬挂式站外站名牌渲染器：一块深灰灯箱牌吊在天花板下面。
 * <p>
 */
public class RenderTrdmHungBigSign<T extends BlockTrdmHungBigSign.TileEntityTrdmHungBigSign>
        implements BlockEntityRenderer<T>, IBlock, IGui {

    /** 满亮（LightTexture.FULL_BRIGHT）：灯箱效果的关键。 */
    private static final int FULL_BRIGHT = 0xF000F0;

    /** 牌子厚度（格）：灯箱款比墙挂款厚一点，2/16。 */
    private static final float PLATE_THICKNESS = 2F / 16F;
    /** 牌子厚度的一半（格）。 */
    private static final float PLATE_HALF_DEPTH = PLATE_THICKNESS / 2F;
    /** 牌子中心离方块底面多高（格）：牌子贴着方块底，占下方 3/4。 */
    private static final float PLATE_CENTRE_Y = BlockTrdmHungBigSign.PLATE_BOTTOM
            + BlockTrdmBigStationSign.PLATE_HEIGHT / 2F;
    /** 贴图离牌子正面再让一丝，避免共面打架。 */
    private static final float PLATE_TEX_OFFSET = 0.01F;
    /** 吊杆截面的一半（格）：沿牌面方向 1/16、沿法线方向 1/16。 */
    private static final float ROD_HALF_RUN = 0.5F / 16F;
    private static final float ROD_HALF_DEPTH = 0.5F / 16F;
    /** 吊杆中心离方块底面多高（格）：牌子顶面 12/16 到顶面 1，中心在 14/16。 */
    private static final float ROD_CENTRE_Y = (BlockTrdmHungBigSign.PLATE_BOTTOM
            + BlockTrdmBigStationSign.PLATE_HEIGHT + 1F) / 2F;
    /** 吊杆离牌面两端各收多少格（半格 = 正好落在最外侧那一格的中心）。 */
    private static final float ROD_END_INSET = 0.5F;
    /** 吊杆半长（格）。 */
    private static final float ROD_HALF_LENGTH = (1F - (BlockTrdmHungBigSign.PLATE_BOTTOM
            + BlockTrdmBigStationSign.PLATE_HEIGHT)) / 2F;

    /** 贴图最大宽度，超过就把纹理像素密度降下来（不在渲染端裁图）。 */
    private static final float MAX_PLATE_WIDTH = 8F;

    /** 底板贴图（黑色混凝土）：牌面底色已经改成纯黑，背面/侧面跟着一致。 */
    private static final ResourceLocation BOARD_TEXTURE = new ResourceLocation("minecraft", "textures/block/black_concrete.png");

    /** 每个牌子上一次画出来的贴图：新贴图烘焙期间先用它顶着，不然会闪空白。 */
    private static final Map<BlockPos, ResourceLocation> LAST_TEXTURES = new HashMap<>();

    public RenderTrdmHungBigSign() {
    }

    @Override
    public void render(T entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        final Level world = entity.getLevel();
        if (world == null) {
            return;
        }
        final BlockPos pos = entity.getBlockPos();
        final BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockTrdmHungBigSign)) {
            return;
        }
        final Direction facing = IBlock.getStatePropertySafe(state, BlockTrdmHungBigSign.FACING);

        // 合并段：整段只由最左那格画一次
        final List<BlockPos> run = SignRuns.collect(world, pos, state.getBlock(), BlockTrdmHungBigSign.FACING, facing);
        if (SignRuns.indexOf(run, pos) != 0) {
            return;
        }
        final int rightCount = run.size() - 1;
        final float centreOffset = rightCount / 2F;
        final float plateWidth = Math.min(MAX_PLATE_WIDTH, rightCount + 1F);

        final TrdmBigSignContent.Content content = TrdmBigSignContent.resolve(world, run);
        if (content == null) {
            return;
        }

        final float aspect = plateWidth / BlockTrdmBigStationSign.PLATE_HEIGHT;
        ResourceLocation texture = TrdmHeaderTextures.getBigStationSign(content.stationName(), content.badges(),
                content.exitName(), content.zhanStyle(), content.elevator(), aspect);
        if (texture == null) {
            texture = LAST_TEXTURES.get(pos);
        }
        if (texture == null) {
            return;
        }
        if (LAST_TEXTURES.size() > 512) {
            LAST_TEXTURES.clear();
        }
        LAST_TEXTURES.put(pos, texture);

        final Vec3 normal = Vec3.atLowerCornerOf(facing.getNormal());
        final Vec3 right = new Vec3(0, 1, 0).cross(normal);
        final Vec3 up = new Vec3(0, 1, 0);
        final Vec3 base = Vec3.atCenterOf(pos).add(right.scale(centreOffset))
                .add(0, PLATE_CENTRE_Y - 0.5, 0);

        final float halfWidth = plateWidth / 2F;
        final float halfHeight = BlockTrdmBigStationSign.PLATE_HEIGHT / 2F;

        // 牌子本体 + 吊杆：都是深灰板，整块满亮
        RenderTrains.scheduleRender(BOARD_TEXTURE, false, RenderType::entityCutout, (matricesNew, vertexConsumer) -> {
            final Matrix4f pose = matricesNew.last().pose();
            emitBox(pose, vertexConsumer, base, right, normal, up, halfWidth, PLATE_HALF_DEPTH, halfHeight,
                    IGui.ARGB_WHITE, FULL_BRIGHT);
            // 吊杆只在牌子两端各一根，位置跟着<b>牌面</b>走（不是段首/段尾那两格）：
            // 牌宽最多 8 格，段再长牌也不变宽，吊杆必须跟着牌面往里收，否则会掉到牌子外面去。
            // 只有一格时两根重合，就画中间一根。
            final float rodOffset = Math.max(0F, halfWidth - ROD_END_INSET);
            final Vec3 rodBase = base.add(0, ROD_CENTRE_Y - PLATE_CENTRE_Y, 0);
            if (rodOffset <= 0F) {
                emitBox(pose, vertexConsumer, rodBase, right, normal, up,
                        ROD_HALF_RUN, ROD_HALF_DEPTH, ROD_HALF_LENGTH, IGui.ARGB_WHITE, FULL_BRIGHT);
            } else {
                emitBox(pose, vertexConsumer, rodBase.add(right.scale(rodOffset)), right, normal, up,
                        ROD_HALF_RUN, ROD_HALF_DEPTH, ROD_HALF_LENGTH, IGui.ARGB_WHITE, FULL_BRIGHT);
                emitBox(pose, vertexConsumer, rodBase.add(right.scale(-rodOffset)), right, normal, up,
                        ROD_HALF_RUN, ROD_HALF_DEPTH, ROD_HALF_LENGTH, IGui.ARGB_WHITE, FULL_BRIGHT);
            }
        });

        // 正面贴图：只在朝 facing 那一面画一次（背面留深灰）
        final Vec3 plateFront = base.add(normal.scale(PLATE_HALF_DEPTH + PLATE_TEX_OFFSET));
        RenderTrains.scheduleRender(texture, false, RenderType::entityCutout, (matricesNew, vertexConsumer) -> {
            final Matrix4f pose = matricesNew.last().pose();
            emitFace(pose, vertexConsumer, plateFront, right, up, halfWidth, halfHeight, normal,
                    IGui.ARGB_WHITE, FULL_BRIGHT);
        });
    }

    private static void emitFace(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 centre, Vec3 uAxis, Vec3 vAxis,                                 float halfU, float halfV, Vec3 outward, int argb, int light) {
        RenderTrdmBigStationSign.emitFace(pose, vertexConsumer, centre, uAxis, vAxis, halfU, halfV, outward, argb, light);
    }

    private static void emitBox(Matrix4f pose, VertexConsumer vertexConsumer, Vec3 centre, Vec3 right, Vec3 normal,
                                Vec3 up, float halfWidth, float halfDepth, float halfHeight, int argb, int light) {
        RenderTrdmBigStationSign.emitBox(pose, vertexConsumer, centre, right, normal, up,
                halfWidth, halfDepth, halfHeight, argb, light);
    }

    @Override
    public boolean shouldRenderOffScreen(T blockEntity) {
        return true;
    }
}
