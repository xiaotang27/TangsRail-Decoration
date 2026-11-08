package top.xiaotang27.trdm.fare;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    @Test
    void copyFromKeepsRuleListIdentity() {
        final FareConfig target = FareDefaults.config();
        final List<FareRule> rules = target.rules;

        final FareConfig source = new FareConfig();
        source.enabled = false;
        final FareRule flat = new FareRule("x", "X", FareRule.Kind.FLAT);
        flat.flatFareCents = 123L;
        source.rules.add(flat);
        source.routeRules.put("minecraft:overworld|7", "x");

        target.copyFrom(source);
        assertFalse(target.enabled);
        assertSame(rules, target.rules);
        assertEquals(1, target.rules.size());
        assertEquals("x", target.rules.get(0).id);
        assertEquals("x", target.routeRules.get("minecraft:overworld|7"));
    }
}
