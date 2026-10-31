package top.xiaotang27.trdm.block;

import mtr.block.IBlock;

/**
 * 屏蔽门玻璃/门楣的横向侧别计算。语义与 MTR 模型一致：
 * left = 本格是最左端（左侧有立柱），right = 最右端，middle = 两侧都接同类方块，single = 孤立一格。
 * <p>
 * 参考 MTR 的 BlockPSDAPGGlassBase.connectGlass（private，无法直接调用）：
 * 只有左边有 → right；只有右边有 → left；两侧都有 → middle；都没有 → single。
 */
public final class TrdmPSDSide {

    private TrdmPSDSide() {
    }

    public static IBlock.EnumSide compute(boolean leftConnected, boolean rightConnected) {
        if (leftConnected && rightConnected) {
            return IBlock.EnumSide.MIDDLE;
        } else if (leftConnected) {
            return IBlock.EnumSide.RIGHT;
        } else if (rightConnected) {
            return IBlock.EnumSide.LEFT;
        } else {
            return IBlock.EnumSide.SINGLE;
        }
    }
}
