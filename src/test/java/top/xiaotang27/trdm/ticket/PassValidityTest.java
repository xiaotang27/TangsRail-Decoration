package top.xiaotang27.trdm.ticket;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 通票有效期按自然日计算。 */
class PassValidityTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static long millis(int year, int month, int day, int hour, int minute) {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant().toEpochMilli();
    }

    @Test
    void oneDayPassEndsAtMidnight() {
        final long[] window = PassValidity.window(1, millis(2026, 11, 8, 15, 30), ZONE);
        assertEquals(millis(2026, 11, 8, 0, 0), window[0]);
        assertEquals(millis(2026, 11, 9, 0, 0) - 1L, window[1]);
        assertFalse(PassValidity.expired(window[1], millis(2026, 11, 8, 23, 59)));
        assertTrue(PassValidity.expired(window[1], millis(2026, 11, 9, 0, 1)));
    }

    @Test
    void threeDayPassIsConsecutive() {
        final long[] window = PassValidity.window(3, millis(2026, 11, 8, 15, 30), ZONE);
        assertEquals(millis(2026, 11, 8, 0, 0), window[0]);
        assertEquals(millis(2026, 11, 11, 0, 0) - 1L, window[1]);
    }

    @Test
    void fiveDayPassCoversFiveDays() {
        final long[] window = PassValidity.window(5, millis(2026, 11, 8, 15, 30), ZONE);
        assertEquals(millis(2026, 11, 13, 0, 0) - 1L, window[1]);
    }
}
