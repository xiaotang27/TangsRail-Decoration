package top.xiaotang27.trdm.render;

import com.mojang.blaze3d.vertex.PoseStack;
import mtr.block.BlockPSDAPGDoorBase;
import mtr.block.BlockPlatform;
import mtr.render.RenderTrains;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.VoxelShape;
import top.xiaotang27.trdm.Trdm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站台门脚下那排「上下车引导箭头」。
 * <p>
 * 在 MTR 里这排箭头不是屏蔽门画出来的，而是<b>站台方块</b>（{@code mtr:platform}）模型的一部分：
 * 方块状态的 {@code door_type} / {@code side} 由 {@code BlockPlatform} 按「上方是不是门扇、
 * 左右两边还有没有门扇」现算，multipart 再叠一层 {@code mtr:block/platform_arrow_1..4}
 * （一格一块的半格贴图）。所以 TRD 的屏蔽门放在 MTR 站台方块上时有箭头，
 * 放在大理石地面、台阶这些别的方块上就没有。
 * <p>
 * 这里把同一套箭头补画到门下的任意方块上：按同样的规则算出 MTR 会用的 {@code side} / {@code facing}，
 * 直接借 {@code mtr:platform} 这份烘焙好的模型取出箭头那几张四边形，摆到方块顶面上。
 * 贴图、UV、镜像、旋转全由 MTR 的模型自己决定（资源包换贴图也跟着换），这里只管位置，
 * 所以画出来和原版站台方块上的箭头一模一样。
 * <p>
 * 三格的分工和 MTR 一致：门扇脚下那格画箭头的一半，沿站台方向相邻的两格画门边的小箭头；
 * 站台方块上不重复画（MTR 自己会画）。
 */
public final class TrdmPlatformArrows {

    /**
     * 画箭头时绑的贴图：方块图集本身。四边形是从烘焙好的方块模型里拿的，
     * 顶点 UV 是<b>图集坐标</b>（和原版方块渲染一致），所以不能像手写四边形那样只绑 platform_arrow.png。
     */
    private static final ResourceLocation ATLAS_TEXTURE = TextureAtlas.LOCATION_BLOCKS;
    /** 箭头贴图在图集里的名字：用来从站台方块那串四边形里认出箭头本身。 */
    private static final ResourceLocation ARROW_SPRITE = new ResourceLocation("mtr", "block/platform_arrow");
    /** 箭头在模型里画在 16.1/16 高：比站台方块顶面高 0.1/16。 */
    private static final float ARROW_PLANE = 16.1F / 16F;
    private static final float ARROW_LIFT = ARROW_PLANE - 1F;

    /** side(1..4) × 水平朝向 → 箭头四边形。资源重载后清空。 */
    private static final Map<Integer, List<BakedQuad>> CACHE = new HashMap<>();
    /** 取不到模型只报一次日志，别每帧刷屏。 */
    private static boolean warned;

    private TrdmPlatformArrows() {
    }

    /**
     * 给一个门扇方块补画地面箭头。{@code doorPos} 是门的下半个方块（地面就是它的下一格）。
     */
    public static void render(Level world, BlockPos doorPos, Direction facing, int overlay) {
        final BlockPos floor = doorPos.below();
        drawAt(world, floor, facing, true, overlay);
        drawAt(world, floor.relative(facing.getClockWise()), facing, false, overlay);
        drawAt(world, floor.relative(facing.getCounterClockWise()), facing, false, overlay);
    }

    /** 资源重载（F3+T、换资源包）后清缓存：烘焙模型和贴图都换了，旧的四边形不能再用。 */
    public static void clearCache() {
        CACHE.clear();
        warned = false;
    }

    private static void drawAt(Level world, BlockPos floorPos, Direction facing, boolean underDoor, int overlay) {
        // MTR 站台方块的模型里自带箭头，同一个位置别再画第二遍（会闪）
        if (world.getBlockState(floorPos).getBlock() instanceof BlockPlatform) {
            return;
        }
        final BlockState above = world.getBlockState(floorPos.above());
        // 门扇脚下那一格归这扇门；相邻的两格如果上方也是门扇，就交给那一扇门去画
        if (!underDoor && above.getBlock() instanceof BlockPSDAPGDoorBase) {
            return;
        }
        final Direction arrowFacing = above.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? above.getValue(BlockStateProperties.HORIZONTAL_FACING) : facing;
        final int side = arrowSide(world, floorPos, above, arrowFacing);
        if (side == 0) {
            return;
        }
        final List<BakedQuad> quads = arrowQuads(side, arrowFacing);
        if (quads.isEmpty()) {
            return;
        }
        // 落在方块顶面上：模型里箭头高度是 ARROW_PLANE（= 顶面 + ARROW_LIFT），先减掉再按实际顶面加回来，
        // 这样台阶、装饰板这种半格高的地面也能贴住
        final double x = floorPos.getX();
        final double y = floorPos.getY() + surfaceTop(world, floorPos) + ARROW_LIFT - ARROW_PLANE;
        final double z = floorPos.getZ();
        final int light = LevelRenderer.getLightColor(world, floorPos.above());
        // MTR 的 EXTERIOR 层就是 RenderType.entityCutout(texture)：带 alpha 裁剪，透明处不会画成黑块
        RenderTrains.scheduleRender(ATLAS_TEXTURE, false, RenderType::entityCutout, (matrices, vertexConsumer) -> {
            matrices.pushPose();
            matrices.translate(x, y, z);
            final PoseStack.Pose pose = matrices.last();
            for (final BakedQuad quad : quads) {
                vertexConsumer.putBulkData(pose, quad, 1F, 1F, 1F, light, overlay);
            }
            matrices.popPose();
        });
    }

    /** 行走面在方块内的相对高度：满方块是 1，台阶 / 装饰板 / 地毯按实际形状顶面算。 */
    private static double surfaceTop(Level world, BlockPos pos) {
        final VoxelShape shape = world.getBlockState(pos).getShape(world, pos);
        if (shape.isEmpty()) {
            return 1D;
        }
        return Math.min(1D, Math.max(0D, shape.max(Direction.Axis.Y)));
    }

    /**
     * 复刻 MTR {@code BlockPlatform} 里现算 {@code side} 的那段逻辑，返回值就是
     * {@code platform.json} 里 side 属性的取值，0 表示这一格不画箭头。
     */
    private static int arrowSide(Level world, BlockPos floorPos, BlockState above, Direction facing) {
        final boolean leafAbove = above.getBlock() instanceof BlockPSDAPGDoorBase;
        final boolean clockwise = isLeaf(world, floorPos.above().relative(facing.getClockWise()));
        final boolean counterClockwise = isLeaf(world, floorPos.above().relative(facing.getCounterClockWise()));
        if (leafAbove && clockwise) {
            // 上方是门扇，顺时针那边还有一扇 → 两块拼成一对门里的右半
            return 2;
        }
        if (leafAbove && counterClockwise) {
            // 上方是门扇，逆时针那边还有一扇 → 拼成一对门里的左半（镜像）
            return 3;
        }
        if (clockwise) {
            // 门边外侧那一格：贴图左半
            return 1;
        }
        if (counterClockwise) {
            // 另一侧门边外侧：贴图左半镜像
            return 4;
        }
        return 0;
    }

    private static boolean isLeaf(Level world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockPSDAPGDoorBase;
    }

    /**
     * 借 MTR 站台方块的烘焙模型取箭头四边形：把方块状态的 side / facing 设成 MTR 算出来的值，
     * 再按贴图把箭头那几张四边形挑出来——同一份模型里还有站台本体（{@code platform_hk_1} 的立方体）。
     */
    private static List<BakedQuad> arrowQuads(int side, Direction facing) {
        final int key = side * 4 + facing.get2DDataValue();
        final List<BakedQuad> cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        List<BakedQuad> quads = List.of();
        final Block platform = BuiltInRegistries.BLOCK.get(new ResourceLocation("mtr", "platform"));
        if (platform == Blocks.AIR) {
            warnOnce("找不到 mtr:platform，地面箭头不画");
        } else {
            try {
                final BlockState state = platform.defaultBlockState()
                        .setValue(BlockPlatform.SIDE, side)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                final BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
                final List<BakedQuad> found = new ArrayList<>(1);
                for (final BakedQuad quad : dispatcher.getBlockModel(state)
                        .getQuads(state, Direction.UP, RandomSource.create(42L))) {
                    // 按贴图名认箭头：同一份模型里还有站台本体的立方体，它们用的是别的贴图
                    if (quad.getSprite() != null && ARROW_SPRITE.equals(quad.getSprite().contents().name())) {
                        found.add(quad);
                    }
                }
                if (found.isEmpty()) {
                    warnOnce("mtr:platform 的模型里没找到 " + ARROW_SPRITE + " 的四边形，地面箭头不画");
                }
                quads = List.copyOf(found);
            } catch (Exception e) {
                warnOnce("取 MTR 站台箭头模型失败：" + e);
            }
        }
        CACHE.put(key, quads);
        return quads;
    }

    private static void warnOnce(String message) {
        if (!warned) {
            warned = true;
            Trdm.LOGGER.warn("[psd arrows] {}", message);
        }
    }
}
