package top.xiaotang27.trdm.fare;

/** 交通卡在某一计费规则下的累计乘车统计（用于常客优惠积木）。 */
public record CardRuleStats(int rides, long amountCents, long meters) {

    public static final CardRuleStats ZERO = new CardRuleStats(0, 0L, 0L);

    public CardRuleStats {
        rides = Math.max(0, rides);
        amountCents = Math.max(0L, amountCents);
        meters = Math.max(0L, meters);
    }

    public double amountYuan() {
        return amountCents / 100.0;
    }

    public double kilometers() {
        return meters / 1000.0;
    }
}
