package top.xiaotang27.trdm.block;

import mtr.block.IBlock;

/**
 * 屏蔽门玻璃/门楣的横向侧别计算。语义与 MTR 模型一致：
 * left = 本格是最左端（左侧有立柱），right = 最右端，middle = 两侧都接同类方块，single = 孤立一格。
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
