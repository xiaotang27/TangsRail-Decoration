package top.xiaotang27.trdm.fare;

/**
 * 报价时的非规则上下文：时间与交通卡当日/当月统计。
 * <p>
 * 单程票、通票试算等没有交通卡时传 {@link #ZERO}。
 */
public record QuoteEnvironment(int hour, int minute, int weekday,
                               long cardDayRides, long cardDayAmountCents,
                               long cardMonthRides, long cardMonthAmountCents) {

    public static final QuoteEnvironment ZERO =
            new QuoteEnvironment(0, 0, 1, 0L, 0L, 0L, 0L);

    public QuoteEnvironment {
        hour = Math.max(0, Math.min(23, hour));
        minute = Math.max(0, Math.min(59, minute));
        weekday = Math.max(1, Math.min(7, weekday));
        cardDayRides = Math.max(0L, cardDayRides);
        cardDayAmountCents = Math.max(0L, cardDayAmountCents);
        cardMonthRides = Math.max(0L, cardMonthRides);
        cardMonthAmountCents = Math.max(0L, cardMonthAmountCents);
    }

    public boolean weekend() {
        return weekday >= 6;
    }

    public double cardDayAmountYuan() {
        return cardDayAmountCents / 100.0;
    }

    public double cardMonthAmountYuan() {
        return cardMonthAmountCents / 100.0;
    }
}
