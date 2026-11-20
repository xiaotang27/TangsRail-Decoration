package top.xiaotang27.trdm.block;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * 装饰半砖：大理石地板 / 天花板的半格（8px）版本，直接继承原版 {@link SlabBlock}。
 * <p>
 * 之所以不自己写形状而不如原版：继承 {@link SlabBlock} 就白拿了完整的半砖语义——
 * 上下两半的放置、两片叠一起合成整块（{@code type=double}）、含水（waterlogged），
 * 玩家用起来和石台阶完全一致。
 * <p>
 * 模型直接套原版的 {@code minecraft:block/slab} / {@code slab_top}，只换贴图，
 * 合成整块时用整方块自己的模型（见 {@code blockstates/*_slab.json} 的 {@code type=double}）。
 * 两个工厂方法对应两套手感：石材（大理石）与金属（穿孔吊顶板）。
 */
public class BlockTrdmDecorativeSlab extends SlabBlock {

    private BlockTrdmDecorativeSlab(MapColor mapColor, SoundType sound) {
        super(Properties.of()
                .mapColor(mapColor)
                .strength(1.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(sound));
    }

    /** 大理石地板的半砖：石材质感，镐子采集。 */
    public static BlockTrdmDecorativeSlab stone() {
        return new BlockTrdmDecorativeSlab(MapColor.STONE, SoundType.STONE);
    }

    /** 穿孔吊顶板的半砖：金属质感。 */
    public static BlockTrdmDecorativeSlab metal() {
        return new BlockTrdmDecorativeSlab(MapColor.COLOR_LIGHT_GRAY, SoundType.METAL);
    }
}
