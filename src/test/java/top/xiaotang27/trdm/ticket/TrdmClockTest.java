package top.xiaotang27.trdm.ticket;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 票务时钟：系统时间/游戏时间的映射与日编号。 */
class TrdmClockTest {

    @AfterEach
    void resetBasis() {
        TrdmClock.setBasis(TimeBasis.SYSTEM);
    }

    @Test
    void gameTimeMapsOneDayToTwentyFourHours() {
        TrdmClock.setBasis(TimeBasis.GAME);
        TrdmClock.updateGameTime(24_000L + 6_000L);
        assertEquals(1L, TrdmClock.dayKey());
        assertEquals(1L * TrdmClock.DAY_MILLIS + TrdmClock.DAY_MILLIS / 4L, TrdmClock.now());
    }

    @Test
    void systemTimeUsesRealClock() {
        TrdmClock.setBasis(TimeBasis.SYSTEM);
        assertTrue(TrdmClock.now() > 1_000_000_000_000L);
    }
}
