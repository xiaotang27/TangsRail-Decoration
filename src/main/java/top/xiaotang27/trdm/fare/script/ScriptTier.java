package top.xiaotang27.trdm.fare.script;

/**
 * 阶梯表的一行：{@code upTo} 为空表示“其它/不设上限”。
 * 金额单位为元（脚本层用 double，最终换算成分）。
 */
public record ScriptTier(Double upTo, double fareYuan) {
}
