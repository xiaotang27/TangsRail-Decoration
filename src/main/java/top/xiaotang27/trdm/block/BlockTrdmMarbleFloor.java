package top.xiaotang27.trdm.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * 大理石地板：站厅 / 站台铺装用的石材质感整方块。
 * <p>
 * 没有 blockstate 属性、没有方块实体，模型直接用原版的 {@code minecraft:block/cube_all}
 * （见 {@code assets/trdm/models/block/marble_floor_*.json}），六面同一张贴图，
 * 两种样式（{@code marble_floor_1} 深灰斑点、{@code marble_floor_2} 米白）走同一个类，
 * 差别只在模型里的贴图路径。
 * <p>
 * 强度和抗爆跟石头砖一致（1.5 / 6.0），需要镐子采集（靠
 * {@code data/minecraft/tags/blocks/mineable/pickaxe.json} 归入可挖标签）。
 */
public class BlockTrdmMarbleFloor extends Block {

    public BlockTrdmMarbleFloor() {
        super(Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE));
    }
}
