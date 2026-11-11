package top.xiaotang27.trdm.fare;

import org.junit.jupiter.api.Test;
import top.xiaotang27.trdm.fare.script.FareScript;
import top.xiaotang27.trdm.fare.script.ScriptNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 计费引擎的纯逻辑测试，不依赖 Minecraft。 */
class FareEngineTest {

    private static final String DIM = "minecraft:overworld";

    /** A(区0) - B(区0) - C(区0) - D(区1) - E(区1)，相邻 500 米；G 是孤立站。 */
    private static final List<FareStation> STATIONS = List.of(
            new FareStation(1, "A", 0, 0, 0),
            new FareStation(2, "B", 0, 500, 0),
            new FareStation(3, "C", 0, 1000, 0),
            new FareStation(4, "D", 1, 1500, 0),
            new FareStation(5, "E", 1, 2000, 0),
            new FareStation(7, "G", 0, 10000, 0));

    private static final List<FareRoute> ROUTES = List.of(
            new FareRoute(10, "A线", List.of(1L, 2L, 3L), false),
            new FareRoute(20, "B线", List.of(3L, 4L, 5L), false));

    private static FareGraph graph() {
        return graph(ROUTES);
    }

    private static FareGraph graph(List<FareRoute> routes) {
        return FareGraph.build(new FareNetwork() {
            @Override
            public List<FareStation> stations() {
                return STATIONS;
            }

            @Override
            public List<FareRoute> routes() {
                return routes;
            }
        });
    }

    private static FareRule intervalRule(long maxFareCents) {
        final FareRule rule = new FareRule("interval", "区间阶梯", FareRule.Kind.INTERVAL_TABLE);
        rule.tiers.add(new FareRule.Tier(2L, 200L));
        rule.tiers.add(new FareRule.Tier(9L, 300L));
        rule.tiers.add(new FareRule.Tier(null, 400L));
        rule.maxFareCents = maxFareCents;
        return rule;
    }

    private static FareRule flatRule() {
        final FareRule rule = new FareRule("flat", "统一票价", FareRule.Kind.FLAT);
        rule.flatFareCents = 200L;
        rule.maxFareCents = 200L;
        return rule;
    }

    private static FareRule distanceRule() {
        final FareRule rule = new FareRule("distance", "里程阶梯", FareRule.Kind.DISTANCE_TABLE);
        rule.tiers.add(new FareRule.Tier(1000L, 200L));
        rule.tiers.add(new FareRule.Tier(3000L, 300L));
        rule.tiers.add(new FareRule.Tier(null, 400L));
        rule.maxFareCents = 400L;
        return rule;
    }

    private static void bind(FareConfig config, long routeId, FareRule rule) {
        if (!config.rules.contains(rule)) {
            config.rules.add(rule);
        }
        config.routeRules.put(FareConfig.bindingKey(DIM, routeId), rule.id);
    }

    private static FareScript flatScript(double yuan) {        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        hat.next = "set";
        final ScriptNode set = new ScriptNode("set", "set_fare");
        set.inputs.put("value", "num");
        final ScriptNode num = new ScriptNode("num", "number");
        num.fields.put("value", Double.toString(yuan));
        script.hat = "hat";
        script.nodes.add(hat);
        script.nodes.add(set);
        script.nodes.add(num);
        return script;
    }

    @Test
    void scriptRuleEvaluates() {
        final FareConfig config = new FareConfig();
        final FareRule rule = new FareRule("script", "积木规则", FareRule.Kind.SCRIPT);
        rule.maxFareCents = 500L;
        rule.script = flatScript(3.0);
        bind(config, 10L, rule);
        bind(config, 20L, rule);

        assertEquals(300L, FareEngine.quote(config, graph(), 1, 5, DIM).totalCents());
        assertEquals(300L, FareEngine.quote(config, graph(), 1, 3, DIM).totalCents());
    }

    @Test
    void cardRuleStatsFeedScript() {
        final FareConfig config = new FareConfig();
        final FareRule rule = new FareRule("loyalty", "常客规则", FareRule.Kind.SCRIPT);
        rule.maxFareCents = 5000L;
        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        hat.next = "set";
        final ScriptNode set = new ScriptNode("set", "set_fare");
        set.inputs.put("value", "rides");
        script.hat = "hat";
        script.nodes.add(hat);
        script.nodes.add(set);
        script.nodes.add(new ScriptNode("rides", "card_rule_rides"));
        rule.script = script;
        bind(config, 10L, rule);
        bind(config, 20L, rule);

        final FareQuote quote = FareEngine.quote(config, graph(), 1, 3, DIM,
                Map.of("loyalty", new CardRuleStats(2, 0L, 0L)));
        assertEquals(200L, quote.totalCents());
    }

    @Test
    void invalidScriptFallsBackToVanilla() {
        final FareConfig config = new FareConfig();
        final FareRule rule = new FareRule("bad", "坏脚本", FareRule.Kind.SCRIPT);
        rule.maxFareCents = 500L;
        rule.script = new FareScript();
        bind(config, 10L, rule);
        bind(config, 20L, rule);

        assertEquals(300L, FareEngine.quote(config, graph(), 1, 5, DIM).totalCents());
    }

    @Test
    void vanillaModeUsesZoneDifference() {
        final FareConfig config = new FareConfig();
        config.mode = FareConfig.Mode.MTR_VANILLA;
        final FareQuote quote = FareEngine.quote(config, graph(), 1, 5, DIM);
        assertEquals(300L, quote.totalCents());
        assertTrue(quote.connected());
        assertEquals(1, quote.legs().size());
    }

    @Test
    void unboundLinesFallBackToVanillaFormula() {
        final FareConfig config = new FareConfig();
        final FareQuote quote = FareEngine.quote(config, graph(), 1, 5, DIM);
        assertEquals(300L, quote.totalCents());
        assertEquals(1, quote.legs().size());
        assertEquals(FareRule.VANILLA_ID, quote.legs().get(0).ruleId());
    }

    @Test
    void intervalRuleAppliesPerJourney() {
        final FareConfig config = new FareConfig();
        final FareRule rule = intervalRule(400L);
        bind(config, 10L, rule);
        bind(config, 20L, rule);

        // 两段都绑同一规则 → 合并后 4 区间，按 9 区间档 3 元。
        final FareQuote merged = FareEngine.quote(config, graph(), 1, 5, DIM);
        assertEquals(300L, merged.totalCents());
        assertEquals(1, merged.legs().size());
        assertEquals(4L, merged.legs().get(0).intervals());

        // 1 → 3 只有 2 区间，走最低档。
        assertEquals(200L, FareEngine.quote(config, graph(), 1, 3, DIM).totalCents());
    }

    @Test
    void differentRulesAreSummedPerSegment() {
        final FareConfig config = new FareConfig();
        bind(config, 10L, intervalRule(400L));
        bind(config, 20L, flatRule());

        final FareQuote quote = FareEngine.quote(config, graph(), 1, 5, DIM);
        assertEquals(2, quote.legs().size());
        assertEquals(200L, quote.legs().get(0).fareCents());
        assertEquals(200L, quote.legs().get(1).fareCents());
        assertEquals(400L, quote.totalCents());
    }

    @Test
    void transferDiscountIsAppliedOncePerTransfer() {
        final FareConfig config = new FareConfig();
        config.transferDiscountCents = 100L;
        bind(config, 10L, intervalRule(400L));
        bind(config, 20L, flatRule());

        final FareQuote quote = FareEngine.quote(config, graph(), 1, 5, DIM);
        assertEquals(100L, quote.transferDiscountCents());
        assertEquals(300L, quote.totalCents());
    }

    @Test
    void distanceRuleUsesStraightLineMeters() {
        final FareConfig config = new FareConfig();
        config.metric = FareConfig.Metric.DISTANCE;
        bind(config, 10L, distanceRule());
        bind(config, 20L, distanceRule());

        // 1 → 5 的直线里程 2000 米，落 3000 米档。
        final FareQuote quote = FareEngine.quote(config, graph(), 1, 5, DIM);
        assertEquals(300L, quote.totalCents());
        assertEquals(2000L, quote.legs().get(0).meters());
    }

    @Test
    void perRuleMaxFareCapsEachSegment() {
        final FareConfig config = new FareConfig();
        bind(config, 10L, intervalRule(250L));
        bind(config, 20L, intervalRule(250L));
        final FareQuote quote = FareEngine.quote(config, graph(), 1, 5, DIM);
        assertEquals(250L, quote.totalCents());
        assertTrue(quote.capped());
    }

    @Test
    void unreachableJourneyFallsBackToNetworkMaxFare() {
        final FareConfig config = new FareConfig();
        config.rules.add(intervalRule(400L));

        final FareQuote quote = FareEngine.quote(config, graph(), 1, 99, DIM);
        assertFalse(quote.connected());
        assertEquals(400L, quote.totalCents());
        assertEquals(400L, quote.maxFareCapCents());
    }

    @Test
    void sameStationChargesBaseFare() {
        final FareConfig config = new FareConfig();
        final FareQuote quote = FareEngine.quote(config, graph(), 1, 1, DIM);
        assertTrue(quote.connected());
        assertEquals(200L, quote.totalCents());
    }

    @Test
    void circularRouteAddsClosingEdge() {
        final FareGraph circular = graph(List.of(new FareRoute(10, "环线", List.of(1L, 2L, 3L), true)));
        final FareConfig config = new FareConfig();
        bind(config, 10L, intervalRule(400L));

        final FareQuote quote = FareEngine.quote(config, circular, 1, 3, DIM);
        assertEquals(1L, quote.legs().get(0).intervals());
        assertEquals(200L, quote.totalCents());
    }

    @Test
    void sameRuleAcrossLinesCanBeMergedOrKeptSeparate() {
        final FareConfig merged = new FareConfig();
        bind(merged, 10L, intervalRule(400L));
        bind(merged, 20L, intervalRule(400L));
        assertEquals(300L, FareEngine.quote(merged, graph(), 1, 5, DIM).totalCents());

        final FareConfig separate = new FareConfig();
        separate.mergeSameRule = false;
        bind(separate, 10L, intervalRule(400L));
        bind(separate, 20L, intervalRule(400L));
        assertEquals(400L, FareEngine.quote(separate, graph(), 1, 5, DIM).totalCents());
    }

    @Test
    void lineConditionReceivesCurrentRoute() {
        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        hat.next = "if";
        final ScriptNode condition = new ScriptNode("if", "if");
        final ScriptNode line = new ScriptNode("line", "line_is");
        line.fields.put("line", "10");
        condition.inputs.put("condition", "line");
        condition.next = "setA";
        condition.otherwise = "setB";
        final ScriptNode setA = new ScriptNode("setA", "set_fare");
        setA.inputs.put("value", "numA");
        final ScriptNode numA = new ScriptNode("numA", "number");
        numA.fields.put("value", "1");
        final ScriptNode setB = new ScriptNode("setB", "set_fare");
        setB.inputs.put("value", "numB");
        final ScriptNode numB = new ScriptNode("numB", "number");
        numB.fields.put("value", "3");
        script.hat = "hat";
        script.nodes.addAll(List.of(hat, condition, line, setA, numA, setB, numB));

        final FareRule rule = new FareRule("route", "Route", FareRule.Kind.SCRIPT);
        rule.script = script;
        final FareConfig config = new FareConfig();
        bind(config, 10L, rule);
        assertEquals(100L, FareEngine.quote(config, graph(), 1, 2, DIM).totalCents());
    }

    @Test
    void scriptCanReferenceAnotherRule() {        final FareRule base = new FareRule("base", "基础规则", FareRule.Kind.FLAT);
        base.flatFareCents = 500L;
        base.maxFareCents = 500L;

        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        hat.next = "use";
        final ScriptNode use = new ScriptNode("use", "use_rule");
        use.fields.put("rule", "base");
        use.fields.put("rule_name", "基础规则");
        script.hat = "hat";
        script.nodes.add(hat);
        script.nodes.add(use);

        final FareRule referencing = new FareRule("referencing", "引用规则", FareRule.Kind.SCRIPT);
        referencing.script = script;
        final FareConfig config = new FareConfig();
        bind(config, 10L, base);
        bind(config, 10L, referencing);
        config.routeRules.put(FareConfig.bindingKey(DIM, 10L), "referencing");

        assertEquals(500L, FareEngine.quote(config, graph(), 1, 2, DIM).totalCents());
    }

    @Test
    void networkMaxFareIsTheLargestEnabledRuleCap() {
        final FareConfig config = new FareConfig();
        final FareRule small = intervalRule(200L);
        final FareRule large = intervalRule(500L);
        config.rules.add(small);
        config.rules.add(large);
        assertEquals(500L, config.maxFareCapCents());

        large.enabled = false;
        assertEquals(200L, config.maxFareCapCents());
    }
}
