package top.xiaotang27.trdm.fare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 规则 JSON 的读写测试。 */
class FareJsonTest {

    @Test
    void defaultsRoundTrip() {
        final FareConfig parsed = FareJson.parse(FareJson.write(FareDefaults.config()));
        assertEquals(FareConfig.Mode.CUSTOM, parsed.mode);
        assertEquals(FareConfig.Metric.INTERVALS, parsed.metric);
        assertEquals(3, parsed.rules.size());
        assertEquals(200L, parsed.rules.get(0).flatFareCents);
        assertEquals(400L, parsed.maxFareCapCents());
        assertEquals(3, parsed.rules.get(1).tiers.size());
        assertNull(parsed.rules.get(1).tiers.get(2).upTo());
        assertEquals(400L, parsed.rules.get(1).tiers.get(2).fareCents());
        assertTrue(parsed.routeRules.isEmpty());
    }

    @Test
    void parsesDecimalMoneyAndBindings() {
        final String json = """
                {
                  "schemaVersion": 1,
                  "mode": "CUSTOM",
                  "pathMetric": "DISTANCE",
                  "transferDiscount": 1.5,
                  "rules": [
                    {"id":"x","name":"X","kind":"DISTANCE_TABLE","maxFare":9.9,
                     "tiers":[{"upTo":1000,"fare":2},{"fare":3.5}]}
                  ],
                  "routeRules": {"minecraft:overworld|7":"x"}
                }
                """;
        final FareConfig config = FareJson.parse(json);
        assertEquals(FareConfig.Metric.DISTANCE, config.metric);
        assertEquals(150L, config.transferDiscountCents);
        assertEquals(990L, config.rules.get(0).maxFareCents);
        assertEquals(200L, config.rules.get(0).tiers.get(0).fareCents());
        assertNull(config.rules.get(0).tiers.get(1).upTo());
        assertEquals(350L, config.rules.get(0).tiers.get(1).fareCents());
        assertEquals("x", config.routeRules.get("minecraft:overworld|7"));
    }

    @Test
    void saveWritesFileAndBackup(@TempDir Path dir) throws Exception {
        final Path path = dir.resolve("trdm-fares.json");
        FareJson.save(path, FareDefaults.config());
        assertTrue(Files.isRegularFile(path));
        assertNotNull(FareJson.tryLoad(path));

        FareJson.save(path, FareDefaults.config());
        assertTrue(Files.isRegularFile(dir.resolve("trdm-fares.json.bak")));
    }

    @Test
    void invalidJsonFallsBackToDefaults(@TempDir Path dir) throws Exception {
        final Path path = dir.resolve("trdm-fares.json");
        Files.writeString(path, "{ broken");
        assertEquals(3, FareJson.loadOrDefault(path).rules.size());
    }

    @Test
    void missingFileFallsBackToDefaults(@TempDir Path dir) {
        assertEquals(3, FareJson.loadOrDefault(dir.resolve("absent.json")).rules.size());
    }

    @Test
    void scriptRuleRoundTrip() {
        final top.xiaotang27.trdm.fare.script.FareScript script =
                new top.xiaotang27.trdm.fare.script.FareScript();
        final top.xiaotang27.trdm.fare.script.ScriptNode hat =
                new top.xiaotang27.trdm.fare.script.ScriptNode("hat", "on_fare");
        hat.next = "set";
        final top.xiaotang27.trdm.fare.script.ScriptNode set =
                new top.xiaotang27.trdm.fare.script.ScriptNode("set", "set_fare");
        set.inputs.put("value", "table");
        final top.xiaotang27.trdm.fare.script.ScriptNode table =
                new top.xiaotang27.trdm.fare.script.ScriptNode("table", "table_intervals");
        table.tiers.add(new top.xiaotang27.trdm.fare.script.ScriptTier(2.0, 2.0));
        table.tiers.add(new top.xiaotang27.trdm.fare.script.ScriptTier(null, 4.0));
        script.hat = "hat";
        script.nodes.add(hat);
        script.nodes.add(set);
        script.nodes.add(table);

        final FareConfig config = new FareConfig();
        final FareRule rule = new FareRule("script", "积木", FareRule.Kind.SCRIPT);
        rule.script = script;
        config.rules.add(rule);

        final FareConfig parsed = FareJson.parse(FareJson.write(config));
        final FareRule parsedRule = parsed.rules.get(0);
        assertEquals(FareRule.Kind.SCRIPT, parsedRule.kind);
        assertNotNull(parsedRule.script);
        assertEquals("hat", parsedRule.script.hat);
        assertEquals(3, parsedRule.script.nodes.size());
        assertEquals(Double.valueOf(2.0), parsedRule.script.node("table").tiers.get(0).upTo());
        assertNull(parsedRule.script.node("table").tiers.get(1).upTo());
    }

    @Test
    void moneyFormatting() {
        assertEquals("2", FareMoney.text(200L));
        assertEquals("2.50", FareMoney.text(250L));
        assertEquals("0", FareMoney.text(0L));
        assertEquals(3L, FareMoney.emeralds(250L));
        assertEquals(2L, FareMoney.emeralds(200L));
        assertEquals("2", FareMoney.input(200L));
        assertEquals("", FareMoney.input(250L));
    }
}
