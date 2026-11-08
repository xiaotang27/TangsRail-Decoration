package top.xiaotang27.trdm.ticket;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 通票有效期计算。
 * <p>
 * 按现实日期（服务器时区）从首次入闸当天起算：一日票到当天 24:00，三日票到第 3 天 24:00，
 * 多日票因此天然连续。
 */
public final class PassValidity {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private PassValidity() {
    }

    /** @return {起始毫秒, 结束毫秒}，结束为最后一天 24:00 前一毫秒。 */
    public static long[] window(int days, long nowMillis) {
        return window(days, nowMillis, ZoneId.systemDefault());
    }

    public static long[] window(int days, long nowMillis, ZoneId zone) {
        final LocalDate date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        final long from = date.atStartOfDay(zone).toInstant().toEpochMilli();
        final long until = date.plusDays(Math.max(1, days)).atStartOfDay(zone)
                .toInstant().toEpochMilli() - 1L;
        return new long[]{from, until};
    }

    public static boolean expired(long validUntilMillis, long nowMillis) {
        return nowMillis > validUntilMillis;
    }

    public static String format(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(FORMAT);
    }
}
