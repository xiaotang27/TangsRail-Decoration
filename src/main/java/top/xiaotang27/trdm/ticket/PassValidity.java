package top.xiaotang27.trdm.ticket;

/**
 * 通票有效期计算。
 * <p>
 * 从首次激活时刻起算，一日票 24 小时、三日票 72 小时、青春18 120 小时；
 * 多日票因此天然连续。时间戳按 {@link TrdmClock} 的当前时间依据解释。
 */
public final class PassValidity {

    private PassValidity() {
    }

    /** @return {激活时间毫秒, 到期时间毫秒}。 */
    public static long[] window(int days, long nowMillis) {
        final long duration = Math.max(1, days) * TrdmClock.DAY_MILLIS;
        return new long[]{nowMillis, nowMillis + duration};
    }

    public static boolean expired(long validUntilMillis, long nowMillis) {
        return nowMillis > validUntilMillis;
    }

    public static String format(long millis) {
        return TrdmClock.format(millis);
    }
}
