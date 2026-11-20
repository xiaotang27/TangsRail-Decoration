package top.xiaotang27.trdm.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * 天花板：站厅吊顶用的穿孔金属板，普通整方块。
 * <p>
 * 贴图是 64×64 的方孔网格（每格 4 像素一个孔，一格方块 16×16 个孔），
 * 四边严格接续，所以连成一片吊顶时看不出格子接缝。
 * <p>
 * 模型用原版 {@code minecraft:block/cube_all}；顶面朝下也同样是穿孔面，
 * 从下面抬头看就是吊顶的样子。材质按金属处理（敲击声、需要镐子采集）。
 */
public class BlockTrdmCeiling extends Block {

    public BlockTrdmCeiling() {
        super(Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_GRAY)
                .strength(1.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
    }
}
