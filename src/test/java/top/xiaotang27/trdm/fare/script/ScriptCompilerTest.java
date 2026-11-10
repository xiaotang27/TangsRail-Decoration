package top.xiaotang27.trdm.fare.script;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 积木脚本的校验与执行。金额在脚本层是“元”。 */
class ScriptCompilerTest {

    private static double eval(FareScript script, long intervals, long meters, int fromZone, int toZone) {
        final ScriptCompiler.Result result = ScriptCompiler.compile(script);
        assertTrue(result.errors().isEmpty(), () -> String.join("；", result.errors()));
        assertNotNull(result.compiled());
        return result.compiled().run(new ScriptContext(intervals, meters, fromZone, toZone));
    }

    private static FareScript flat(double yuan) {
        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        hat.next = "set";
        final ScriptNode set = new ScriptNode("set", "set_fare");
        set.inputs.put("value", "num");
        final ScriptNode num = new ScriptNode("num", "number");
        num.fields.put("value", Double.toString(yuan));
        script.hat = "hat";
        script.nodes.addAll(List.of(hat, set, num));
        return script;
    }

    @Test
    void flatScriptSetsFare() {
        assertEquals(2.0, eval(flat(2.0), 4, 1000, 0, 1), 1e-9);
    }

    @Test
    void intervalTableScript() {
        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        hat.next = "set";
        final ScriptNode set = new ScriptNode("set", "set_fare");
        set.inputs.put("value", "table");
        final ScriptNode table = new ScriptNode("table", "table_intervals");
        table.tiers.add(new ScriptTier(2.0, 2.0));
        table.tiers.add(new ScriptTier(9.0, 3.0));
        table.tiers.add(new ScriptTier(null, 4.0));
        script.hat = "hat";
        script.nodes.addAll(List.of(hat, set, table));

        assertEquals(2.0, eval(script, 2, 0, 0, 0), 1e-9);
        assertEquals(3.0, eval(script, 5, 0, 0, 0), 1e-9);
        assertEquals(4.0, eval(script, 20, 0, 0, 0), 1e-9);
    }

    @Test
    void mileageUsesKilometers() {
        final FareScript context = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        hat.next = "set";
        final ScriptNode set = new ScriptNode("set", "set_fare");
        set.inputs.put("value", "km");
        final ScriptNode km = new ScriptNode("km", "context_meters");
        context.hat = "hat";
        context.nodes.addAll(List.of(hat, set, km));
        assertEquals(1.5, eval(context, 0, 1500, 0, 0), 1e-9);

        final FareScript table = new FareScript();
        final ScriptNode tableHat = new ScriptNode("hat", "on_fare");
        tableHat.next = "set";
        final ScriptNode tableSet = new ScriptNode("set", "set_fare");
        tableSet.inputs.put("value", "tiers");
        final ScriptNode tiers = new ScriptNode("tiers", "table_meters");
        tiers.tiers.add(new ScriptTier(1.0, 2.0));
        tiers.tiers.add(new ScriptTier(3.0, 3.0));
        tiers.tiers.add(new ScriptTier(null, 4.0));
        table.hat = "hat";
        table.nodes.addAll(List.of(tableHat, tableSet, tiers));
        assertEquals(2.0, eval(table, 0, 500, 0, 0), 1e-9);
        assertEquals(3.0, eval(table, 0, 2500, 0, 0), 1e-9);
        assertEquals(4.0, eval(table, 0, 4000, 0, 0), 1e-9);
    }

    @Test
    void ifElseUsesCompare() {
        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        hat.next = "if";
        final ScriptNode branch = new ScriptNode("if", "if");
        branch.inputs.put("condition", "cmp");
        branch.next = "set_two";
        branch.otherwise = "set_three";
        final ScriptNode compare = new ScriptNode("cmp", "compare");
        compare.fields.put("op", "<=");
        compare.inputs.put("a", "iv");
        compare.inputs.put("b", "two");
        final ScriptNode intervals = new ScriptNode("iv", "context_intervals");
        final ScriptNode two = new ScriptNode("two", "number");
        two.fields.put("value", "2");
        final ScriptNode three = new ScriptNode("three", "number");
        three.fields.put("value", "3");
        final ScriptNode setTwo = new ScriptNode("set_two", "set_fare");
        setTwo.inputs.put("value", "two");
        final ScriptNode setThree = new ScriptNode("set_three", "set_fare");
        setThree.inputs.put("value", "three");
        script.hat = "hat";
        script.nodes.addAll(List.of(hat, branch, compare, intervals, two, three, setTwo, setThree));

        assertEquals(2.0, eval(script, 2, 0, 0, 0), 1e-9);
        assertEquals(3.0, eval(script, 3, 0, 0, 0), 1e-9);
    }

    @Test
    void arithmeticCapAndRounding() {
        final FareScript script = flat(2.34);
        final ScriptNode round = new ScriptNode("round", "round_fare");
        round.fields.put("mode", "HALF");
        round.fields.put("unit", "0.1");
        script.node("set").next = "round";
        script.nodes.add(round);

        assertEquals(2.3, eval(script, 0, 0, 0, 0), 1e-9);

        final FareScript capped = flat(2.0);
        final ScriptNode add = new ScriptNode("add", "add_fare");
        add.inputs.put("value", "one");
        final ScriptNode one = new ScriptNode("one", "number");
        one.fields.put("value", "1");
        final ScriptNode cap = new ScriptNode("cap", "cap_fare");
        cap.inputs.put("value", "capValue");
        final ScriptNode capValue = new ScriptNode("capValue", "number");
        capValue.fields.put("value", "2.5");
        capped.node("set").next = "add";
        add.next = "cap";
        capped.nodes.addAll(List.of(add, one, cap, capValue));

        assertEquals(2.5, eval(capped, 0, 0, 0, 0), 1e-9);
    }

    @Test
    void ifElseContinuesAfterCBlock() {
        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        hat.next = "if";
        final ScriptNode branch = new ScriptNode("if", "if");
        branch.inputs.put("condition", "cmp");
        branch.next = "set_two";
        branch.otherwise = "set_three";
        branch.after = "add_one";
        final ScriptNode compare = new ScriptNode("cmp", "compare");
        compare.fields.put("op", "<=");
        compare.inputs.put("a", "iv");
        compare.inputs.put("b", "two");
        final ScriptNode intervals = new ScriptNode("iv", "context_intervals");
        final ScriptNode two = new ScriptNode("two", "number");
        two.fields.put("value", "2");
        final ScriptNode three = new ScriptNode("three", "number");
        three.fields.put("value", "3");
        final ScriptNode one = new ScriptNode("one", "number");
        one.fields.put("value", "1");
        final ScriptNode setTwo = new ScriptNode("set_two", "set_fare");
        setTwo.inputs.put("value", "two");
        final ScriptNode setThree = new ScriptNode("set_three", "set_fare");
        setThree.inputs.put("value", "three");
        final ScriptNode addOne = new ScriptNode("add_one", "add_fare");
        addOne.inputs.put("value", "one");
        script.hat = "hat";
        script.nodes.addAll(List.of(hat, branch, compare, intervals, two, three, one,
                setTwo, setThree, addOne));

        assertEquals(3.0, eval(script, 2, 0, 0, 0), 1e-9);
        assertEquals(4.0, eval(script, 3, 0, 0, 0), 1e-9);
    }

    @Test
    void ifWithEmptyElseStillContinues() {
        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        hat.next = "if";
        final ScriptNode branch = new ScriptNode("if", "if");
        branch.inputs.put("condition", "cmp");
        branch.next = "set_two";
        branch.after = "add_one";
        final ScriptNode compare = new ScriptNode("cmp", "compare");
        compare.fields.put("op", ">");
        compare.inputs.put("a", "iv");
        compare.inputs.put("b", "two");
        final ScriptNode intervals = new ScriptNode("iv", "context_intervals");
        final ScriptNode two = new ScriptNode("two", "number");
        two.fields.put("value", "2");
        final ScriptNode one = new ScriptNode("one", "number");
        one.fields.put("value", "1");
        final ScriptNode setTwo = new ScriptNode("set_two", "set_fare");
        setTwo.inputs.put("value", "two");
        final ScriptNode addOne = new ScriptNode("add_one", "add_fare");
        addOne.inputs.put("value", "one");
        script.hat = "hat";
        script.nodes.addAll(List.of(hat, branch, compare, intervals, two, one,
                setTwo, addOne));

        assertEquals(1.0, eval(script, 1, 0, 0, 0), 1e-9);
        assertEquals(3.0, eval(script, 20, 0, 0, 0), 1e-9);
    }

    @Test
    void invalidScriptsReportErrors() {
        final ScriptCompiler.Result empty = ScriptCompiler.compile(new FareScript());
        assertNotNull(empty.errors());
        assertFalse(empty.errors().isEmpty());

        final FareScript noSet = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        noSet.hat = "hat";
        noSet.nodes.add(hat);
        final ScriptCompiler.Result noSetResult = ScriptCompiler.compile(noSet);
        assertFalse(noSetResult.errors().isEmpty());

        final FareScript cycle = flat(2.0);
        cycle.node("set").next = "hat";
        assertFalse(ScriptCompiler.compile(cycle).errors().isEmpty());

        final FareScript mismatch = flat(2.0);
        final ScriptNode compare = new ScriptNode("cmp", "compare");
        compare.fields.put("op", "<");
        compare.inputs.put("a", "num");
        compare.inputs.put("b", "num");
        mismatch.nodes.add(compare);
        mismatch.node("set").inputs.put("value", "cmp");
        assertFalse(ScriptCompiler.compile(mismatch).errors().isEmpty());
    }
}
