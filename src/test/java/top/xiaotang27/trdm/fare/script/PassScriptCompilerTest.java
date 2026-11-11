package top.xiaotang27.trdm.fare.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 通票积木的求值与校验。 */
class PassScriptCompilerTest {

    @Test
    void defaultsExposePriceDaysDiscountAndDailyLimit() {
        final PassSettings settings = PassScriptCompiler.evaluate(
                PassScripts.defaults(25, 3, 80, 4));
        assertEquals(25, settings.priceEmeralds());
        assertEquals(3, settings.days());
        assertEquals(80, settings.discountPercent());
        assertEquals(4, settings.dailyLimit());
    }

    @Test
    void missingPriceFails() {
        final FareScript script = PassScripts.empty();
        final ScriptNode days = new ScriptNode("s1", "pass_days");
        final ScriptNode number = new ScriptNode("v1", "number");
        number.fields.put("value", "1");
        days.inputs.put("value", number.id);
        script.node(script.hat).next = days.id;
        script.nodes.add(days);
        script.nodes.add(number);
        assertThrows(ScriptException.class, () -> PassScriptCompiler.evaluate(script));
    }
}
