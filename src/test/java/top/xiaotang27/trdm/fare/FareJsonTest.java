package top.xiaotang27.trdm.fare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import top.xiaotang27.trdm.fare.script.ScriptNode;

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
        assertEquals(FareRule.Kind.SCRIPT, parsed.rules.get(0).kind);
        assertNotNull(parsed.rules.get(0).script);
        assertEquals(400L, parsed.maxFareCapCents());
        assertEquals(FareRule.Kind.SCRIPT, parsed.rules.get(1).kind);
        final ScriptNode intervalTable = findNode(parsed.rules.get(1), "table_intervals");
        assertNotNull(intervalTable);
        assertEquals(3, intervalTable.tiers.size());
        assertNull(intervalTable.tiers.get(2).upTo());
        assertEquals(4.0, intervalTable.tiers.get(2).fareYuan(), 1e-9);
        assertEquals(FareRule.Kind.SCRIPT, parsed.rules.get(2).kind);
        final ScriptNode distanceTable = findNode(parsed.rules.get(2), "table_meters");
        assertNotNull(distanceTable);
        assertEquals(1.0, distanceTable.tiers.get(0).upTo(), 1e-9);
        assertEquals(3.0, distanceTable.tiers.get(1).upTo(), 1e-9);
        assertNull(distanceTable.tiers.get(2).upTo());
        assertTrue(parsed.routeRules.isEmpty());
    }

    private static ScriptNode findNode(FareRule rule, String type) {
        if (rule.script == null) {
            return null;
        }
        for (final ScriptNode node : rule.script.nodes) {
            if (type.equals(node.type)) {
                return node;
            }
        }
        return null;
    }

    @Test
    void oldMeterScriptMigratesToKilometers() {
        final String json = """
                {
                  "schemaVersion": 2,
                  "rules": [
                    {"id":"r","name":"R","kind":"SCRIPT","script":{
                      "schemaVersion":1,
                      "hat":"hat",
                      "nodes":[
                        {"id":"hat","type":"on_fare","next":"set"},
                        {"id":"set","type":"set_fare","inputs":{"value":"t"}},
                        {"id":"t","type":"table_meters","tiers":[
                          {"upTo":1000,"fare":2},{"upTo":3000,"fare":3},{"fare":4}]}
                      ]
                    }}
                  ]
                }
                """;
        final FareRule rule = FareJson.parse(json).rules.get(0);
        assertEquals(2, rule.script.schemaVersion);
        final ScriptNode table = findNode(rule, "table_meters");
        assertNotNull(table);
        assertEquals(1.0, table.tiers.get(0).upTo(), 1e-9);
        assertEquals(3.0, table.tiers.get(1).upTo(), 1e-9);
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
        assertEquals(FareRule.Kind.SCRIPT, config.rules.get(0).kind);
        assertEquals(990L, config.rules.get(0).maxFareCents);
        assertEquals(200L, config.rules.get(0).tiers.get(0).fareCents());
        assertNull(config.rules.get(0).tiers.get(1).upTo());
        assertEquals(350L, config.rules.get(0).tiers.get(1).fareCents());
        final ScriptNode migrated = findNode(config.rules.get(0), "table_meters");
        assertNotNull(migrated);
        assertEquals(1.0, migrated.tiers.get(0).upTo(), 1e-9);
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
    void scriptIfAfterRoundTrip() {
        final top.xiaotang27.trdm.fare.script.FareScript script =
                new top.xiaotang27.trdm.fare.script.FareScript();
        final top.xiaotang27.trdm.fare.script.ScriptNode hat =
                new top.xiaotang27.trdm.fare.script.ScriptNode("hat", "on_fare");
        hat.next = "if";
        final top.xiaotang27.trdm.fare.script.ScriptNode branch =
                new top.xiaotang27.trdm.fare.script.ScriptNode("if", "if");
        branch.inputs.put("condition", "cmp");
        branch.next = "a";
        branch.otherwise = "b";
        branch.after = "c";
        branch.fields.put("else", "1");
        script.hat = "hat";
        script.nodes.addAll(java.util.List.of(hat, branch,
                new top.xiaotang27.trdm.fare.script.ScriptNode("cmp", "compare"),
                new top.xiaotang27.trdm.fare.script.ScriptNode("a", "set_fare"),
                new top.xiaotang27.trdm.fare.script.ScriptNode("b", "set_fare"),
                new top.xiaotang27.trdm.fare.script.ScriptNode("c", "add_fare")));

        final FareConfig config = new FareConfig();
        final FareRule rule = new FareRule("script", "积木", FareRule.Kind.SCRIPT);
        rule.script = script;
        config.rules.add(rule);

        final FareRule parsed = FareJson.parse(FareJson.write(config)).rules.get(0);
        assertEquals("a", parsed.script.node("if").next);
        assertEquals("b", parsed.script.node("if").otherwise);
        assertEquals("c", parsed.script.node("if").after);
        assertEquals("1", parsed.script.node("if").fields.get("else"));
    }

    @Test
    void passRulesRoundTrip() {
        final FareConfig config = FareDefaults.config();
        assertEquals(3, config.passRules.size());
        config.passRules.add(new top.xiaotang27.trdm.ticket.PassRule("custom", "自定义通票", 7, 2));

        final FareConfig parsed = FareJson.parse(FareJson.write(config));
        assertEquals(4, parsed.passRules.size());
        assertEquals("自定义通票", parsed.passRules.get(3).name);
        assertEquals(7, parsed.passRules.get(3).priceEmeralds);
        assertEquals(2, parsed.passRules.get(3).days);
    }

    @Test
    void missingPassesFallBackToDefaults() {
        final FareConfig config = FareJson.parse("{\"schemaVersion\":1,\"rules\":[]}");
        assertEquals(3, config.passRules.size());
        assertEquals("day1", config.passRules.get(0).id);
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
