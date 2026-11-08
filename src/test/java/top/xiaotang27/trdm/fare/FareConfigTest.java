package top.xiaotang27.trdm.fare;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 最低票价（入闸余额门槛）的计算。 */
class FareConfigTest {

    @Test
    void defaultsUseVanillaBaseFare() {
        assertEquals(200L, FareDefaults.config().minimumFareCents());
    }

    @Test
    void emptyConfigFallsBackToVanillaBaseFare() {
        assertEquals(200L, new FareConfig().minimumFareCents());
    }

    @Test
    void picksLowestEnabledRuleFare() {
        final FareConfig config = new FareConfig();
        final FareRule flat = new FareRule("flat", "flat", FareRule.Kind.FLAT);
        flat.flatFareCents = 500L;
        final FareRule tier = new FareRule("tier", "tier", FareRule.Kind.INTERVAL_TABLE);
        tier.tiers.add(new FareRule.Tier(2L, 300L));
        tier.tiers.add(new FareRule.Tier(null, 400L));
        config.rules.add(flat);
        config.rules.add(tier);
        assertEquals(300L, config.minimumFareCents());
    }

    @Test
    void disabledRulesAreIgnored() {
        final FareConfig config = new FareConfig();
        final FareRule cheap = new FareRule("cheap", "cheap", FareRule.Kind.FLAT);
        cheap.flatFareCents = 100L;
        cheap.enabled = false;
        final FareRule normal = new FareRule("normal", "normal", FareRule.Kind.FLAT);
        normal.flatFareCents = 400L;
        config.rules.add(cheap);
        config.rules.add(normal);
        assertEquals(400L, config.minimumFareCents());
    }
}
