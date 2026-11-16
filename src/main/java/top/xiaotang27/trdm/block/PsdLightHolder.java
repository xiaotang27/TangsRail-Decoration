package top.xiaotang27.trdm.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import top.xiaotang27.trdm.Trdm;

/**
 * 门楣 / 延伸板方块实体上的灯光数据。两个方块实体（门楣的 {@code TileEntityTrdmPSDTop}、
 * 延伸板的 {@code TileEntityTrdmPSDExtension}）各存一份：
 * <ul>
 *     <li>{@code tube}：模型 1「墙上的荧光灯管」，贴装在门楣朝站台那一侧的吊顶下；</li>
 *     <li>{@code top}：模型 2 / 3 的顶部灯，贴装在门楣朝轨道那一侧、底座贴天花板；</li>
 *     <li>{@code color}：模型 3 的<b>底座</b>颜色（0xRRGGBB），只对 {@link PsdLightKind#COLOR} 有意义。
 *         灯管本身始终是白色自发光，不跟着染色。</li>
 * </ul>
 * 两种灯在模型上分别位于门楣的两侧，互不重叠，所以可以同时存在。
 * <p>
 * 数据存在方块实体里而不是方块状态里：颜色是任意 24 位值，塞进方块状态会让门楣的状态数爆炸；
 * 渲染端用 BER 读方块实体画灯，也就不需要给每一组状态配一个模型。
 */
public interface PsdLightHolder {

    String KEY_TUBE_LIGHT = "psd_tube_light";
    String KEY_TOP_LIGHT = "psd_top_light";
    String KEY_TOP_LIGHT_COLOR = "psd_top_light_color";
    /** 可染色灯没设置过颜色时的默认色（纯白）。 */
    int DEFAULT_COLOR = 0xFFFFFF;

    boolean hasTubeLight();

    void setTubeLight(boolean value);

    PsdLightKind topLight();

    int topLightColor();

    void setTopLight(PsdLightKind kind, int rgb);

    void setTopLightColor(int rgb);

    default boolean hasAnyLight() {
        return hasTubeLight() || topLight() != PsdLightKind.NONE;
    }

    /** 把颜色夹到 24 位；拆包 / 旧存档里的越界值不会画成花屏。 */
    static int sanitizeColor(int rgb) {
        return rgb & 0xFFFFFF;
    }

    /**
     * 改了灯的数据之后必须调一次：方块实体标脏 + 通知这个区块重发方块实体数据。
     * 沿用站名牌 / PIS 的同步做法（{@code blockChanged} 会把 update tag 一并广播给附近客户端）。
     * 客户端本地预测时只 {@code setChanged()}，不会向服务端发包。
     */
    static void markLightChanged(BlockEntity blockEntity) {
        blockEntity.setChanged();
        if (blockEntity.getLevel() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(blockEntity.getBlockPos());
        }
    }

    /**
     * 拆除这一格门楣 / 延伸板上已经装好的灯：能拆的都拆下来，按原物品掉回世界。
     * 可染色灯拆下来后颜色丢失（物品本身不存颜色），重新装回去时是默认白色。
     *
     * @return 是否真的拆了东西（没装灯返回 false，调用方继续走原来的右键逻辑）
     */
    static boolean removeLights(Level level, BlockPos pos) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof PsdLightHolder holder)) {
            return false;
        }
        boolean removed = false;
        if (holder.hasTubeLight()) {
            holder.setTubeLight(false);
            Block.popResource(level, pos, new ItemStack(Trdm.WALL_LIGHT_ITEM));
            removed = true;
        }
        final PsdLightKind top = holder.topLight();
        if (top != PsdLightKind.NONE) {
            final int color = holder.topLightColor();
            holder.setTopLight(PsdLightKind.NONE, color);
            Block.popResource(level, pos, new ItemStack(
                    top == PsdLightKind.COLOR ? Trdm.PSD_TOP_LIGHT_COLOR_ITEM : Trdm.PSD_TOP_LIGHT_ITEM));
            removed = true;
        }
        return removed;
    }
}
