package top.xiaotang27.trdm.fare.script;

import java.util.Set;

/** 报价时暴露给脚本的只读上下文。 */
public record ScriptContext(long intervals, long meters, int fromZone, int toZone,
                            long routeId, String routeName,
                            long cardRides, long cardAmountCents, long cardMeters,
                            int hour, int minute, int weekday,
                            long cardDayRides, long cardDayAmountCents,
                            long cardMonthRides, long cardMonthAmountCents,
                            Set<String> cardPolicies) {

    public ScriptContext(long intervals, long meters, int fromZone, int toZone,
                         long routeId, String routeName,
                         long cardRides, long cardAmountCents, long cardMeters,
                         int hour, int minute, int weekday,
                         long cardDayRides, long cardDayAmountCents,
                         long cardMonthRides, long cardMonthAmountCents) {
        this(intervals, meters, fromZone, toZone, routeId, routeName,
                cardRides, cardAmountCents, cardMeters, hour, minute, weekday,
                cardDayRides, cardDayAmountCents, cardMonthRides, cardMonthAmountCents, Set.of());
    }

    public ScriptContext(long intervals, long meters, int fromZone, int toZone,
                         long routeId, String routeName,
                         long cardRides, long cardAmountCents, long cardMeters) {
        this(intervals, meters, fromZone, toZone, routeId, routeName,
                cardRides, cardAmountCents, cardMeters, 0, 0, 1, 0L, 0L, 0L, 0L);
    }

    public ScriptContext(long intervals, long meters, int fromZone, int toZone,
                         long routeId, String routeName) {
        this(intervals, meters, fromZone, toZone, routeId, routeName, 0L, 0L, 0L);
    }

    public ScriptContext(long intervals, long meters, int fromZone, int toZone) {
        this(intervals, meters, fromZone, toZone, 0L, "", 0L, 0L, 0L);
    }

    public long stations() {
        return intervals + 1L;
    }

    /** 脚本里的“里程”统一用公里；底层路径仍以米保存。 */
    public double kilometers() {
        return meters / 1000.0;
    }

    /** 交通卡在本规则下的累计金额，脚本里用元。 */
    public double cardAmountYuan() {
        return cardAmountCents / 100.0;
    }

    /** 交通卡在本规则下的累计里程，脚本里用公里。 */
    public double cardKilometers() {
        return cardMeters / 1000.0;
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

    public boolean hasPolicy(String policyId) {
        return policyId != null && cardPolicies.contains(policyId);
    }

    public int zoneDiff() {
        return Math.abs(toZone - fromZone);
    }
}
