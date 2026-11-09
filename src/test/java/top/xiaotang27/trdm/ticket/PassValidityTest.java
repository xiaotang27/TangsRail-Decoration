package top.xiaotang27.trdm.ticket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 通票有效期从激活时刻起按 24/72/120 小时计算。 */
class PassValidityTest {

    private static final long HOUR = 60L * 60L * 1000L;

    @Test
    void oneDayPassLastsTwentyFourHours() {
        final long now = 1_700_000_000_000L;
        final long[] window = PassValidity.window(1, now);
        assertEquals(now, window[0]);
        assertEquals(now + 24L * HOUR, window[1]);
        assertFalse(PassValidity.expired(window[1], now + 24L * HOUR));
        assertTrue(PassValidity.expired(window[1], now + 24L * HOUR + 1L));
    }

    @Test
    void threeDayPassLastsSeventyTwoHours() {
        final long now = 1_700_000_000_000L;
        assertEquals(now + 72L * HOUR, PassValidity.window(3, now)[1]);
    }

    @Test
    void fiveDayPassLastsOneHundredTwentyHours() {
        final long now = 1_700_000_000_000L;
        assertEquals(now + 120L * HOUR, PassValidity.window(5, now)[1]);
    }
}
