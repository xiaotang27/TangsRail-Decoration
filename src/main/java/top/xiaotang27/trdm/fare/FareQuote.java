package top.xiaotang27.trdm.fare;

import java.util.List;

/**
 * 一次报价的结果。金额单位：分。
 *
 * @param totalCents            最终总价（已扣换乘优惠、已应用每段封顶）
 * @param maxFareCapCents       全局最高票价，只用于无票补票/UI 展示，不参与总价封顶
 * @param connected             起讫站在当前站点图里是否连通；不可达时总价按最高票价兜底
 * @param transferDiscountCents 本次实际减免的换乘优惠
 * @param legs                  分段明细
 */
public record FareQuote(long totalCents, long maxFareCapCents, boolean connected,
                        long transferDiscountCents, List<FareLeg> legs) {

    public FareQuote {
        legs = List.copyOf(legs);
    }

    /** 一段行程按一条线路的规则算出的票价。{@code intervals} 为区间数（= 站数 - 1）。 */
    public record FareLeg(long routeId, String routeName, String ruleId, String ruleName,
                          long intervals, long meters, long fareCents, boolean capped) {
    }

    /** 是否有分段触发了该规则的 {@code maxFare} 封顶。 */
    public boolean capped() {
        for (final FareLeg leg : legs) {
            if (leg.capped()) {
                return true;
            }
        }
        return false;
    }
}
