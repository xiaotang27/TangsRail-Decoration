package top.xiaotang27.trdm.ticket;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 连续计费判定：10 分钟、同名、不同付费区。 */
class JourneyRulesTest {

    private static final String DIM = "minecraft:overworld";

    @Test
    void withinWindowSameNameDifferentZone() {
        assertTrue(JourneyRules.canContinue(1L, "刘园|LIYUAN", 0, 1000L, DIM,
                2L, "刘园|LIYUAN2", 1, DIM, 13_000L));
    }

    @Test
    void windowIsInclusiveAtTenMinutes() {
        assertTrue(JourneyRules.canContinue(1L, "A", 0, 1000L, DIM, 2L, "A", 1, DIM, 13_000L));
        assertFalse(JourneyRules.canContinue(1L, "A", 0, 1000L, DIM, 2L, "A", 1, DIM, 13_001L));
    }

    @Test
    void sameStationIsNotContinuation() {
        assertFalse(JourneyRules.canContinue(1L, "A", 0, 1000L, DIM, 1L, "A", 1, DIM, 5000L));
    }

    @Test
    void sameZoneIsNotContinuation() {
        assertFalse(JourneyRules.canContinue(1L, "A", 1, 1000L, DIM, 2L, "A", 1, DIM, 5000L));
    }

    @Test
    void differentNameIsNotContinuation() {
        assertFalse(JourneyRules.canContinue(1L, "A", 0, 1000L, DIM, 2L, "B", 1, DIM, 5000L));
    }

    @Test
    void differentDimensionIsNotContinuation() {
        assertFalse(JourneyRules.canContinue(1L, "A", 0, 1000L, DIM, 2L, "A", 1, "other", 5000L));
    }

    @Test
    void nameSuffixIsNormalized() {
        assertTrue(JourneyRules.canContinue(1L, "刘园|LIYUAN", 0, 1000L, DIM,
                2L, "刘园|Liyuan", 1, DIM, 5000L));
    }
}
