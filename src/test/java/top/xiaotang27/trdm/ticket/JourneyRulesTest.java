package top.xiaotang27.trdm.ticket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 连续计费判定：10 分钟、同名、不同付费区。 */
class JourneyRulesTest {

    private static final String DIM = "minecraft:overworld";
    private static final long BASE = 1_700_000_000_000L;

    @Test
    void withinWindowSameNameDifferentZone() {
        assertTrue(JourneyRules.canContinue(1L, "刘园|LIYUAN", 0, BASE, DIM,
                2L, "刘园|LIYUAN2", 1, DIM, BASE + JourneyRules.WINDOW_MILLIS));
    }

    @Test
    void windowIsInclusiveAtTenMinutes() {
        assertTrue(JourneyRules.canContinue(1L, "A", 0, BASE, DIM, 2L, "A", 1, DIM,
                BASE + JourneyRules.WINDOW_MILLIS));
        assertFalse(JourneyRules.canContinue(1L, "A", 0, BASE, DIM, 2L, "A", 1, DIM,
                BASE + JourneyRules.WINDOW_MILLIS + 1L));
    }

    @Test
    void sameStationIsNotContinuation() {
        assertFalse(JourneyRules.canContinue(1L, "A", 0, BASE, DIM, 1L, "A", 1, DIM, BASE + 5_000L));
    }

    @Test
    void sameZoneIsNotContinuation() {
        assertFalse(JourneyRules.canContinue(1L, "A", 1, BASE, DIM, 2L, "A", 1, DIM, BASE + 5_000L));
    }

    @Test
    void differentNameIsNotContinuation() {
        assertFalse(JourneyRules.canContinue(1L, "A", 0, BASE, DIM, 2L, "B", 1, DIM, BASE + 5_000L));
    }

    @Test
    void differentDimensionIsNotContinuation() {
        assertFalse(JourneyRules.canContinue(1L, "A", 0, BASE, DIM, 2L, "A", 1, "other", BASE + 5_000L));
    }

    @Test
    void nameSuffixIsNormalized() {
        assertTrue(JourneyRules.canContinue(1L, "刘园|LIYUAN", 0, BASE, DIM,
                2L, "刘园|Liyuan", 1, DIM, BASE + 5_000L));
    }
}
