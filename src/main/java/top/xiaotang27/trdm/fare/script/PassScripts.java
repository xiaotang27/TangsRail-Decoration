package top.xiaotang27.trdm.fare.script;

/** 通票脚本构造辅助。 */
public final class PassScripts {

    private PassScripts() {
    }

    public static FareScript empty() {
        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_pass");
        script.hat = hat.id;
        script.nodes.add(hat);
        return script;
    }

    /** 帽子 + 售价 + 有效期 + 折扣 + 每日次数（0 为不限）。 */
    public static FareScript defaults(int priceEmeralds, int days, int discountPercent, int dailyLimit) {
        final FareScript script = empty();
        ScriptNode previous = script.node(script.hat);
        previous.next = append(script, "pass_price", priceEmeralds);
        previous = script.node(previous.next);
        previous.next = append(script, "pass_days", days);
        previous = script.node(previous.next);
        previous.next = append(script, "pass_discount", discountPercent);
        previous = script.node(previous.next);
        previous.next = append(script, "pass_daily_limit", dailyLimit);
        return script;
    }

    private static String append(FareScript script, String type, int value) {
        final ScriptNode node = new ScriptNode(Scripts.uniqueId(script, "s"), type);
        final ScriptNode number = new ScriptNode(Scripts.uniqueId(script, "v"), "number");
        number.fields.put("value", Integer.toString(value));
        node.inputs.put("value", number.id);
        script.nodes.add(node);
        script.nodes.add(number);
        return node.id;
    }

    public static void ensureHat(FareScript script) {
        if (script.node(script.hat) != null && "on_pass".equals(script.node(script.hat).type)) {
            return;
        }
        for (final ScriptNode node : script.nodes) {
            if ("on_pass".equals(node.type)) {
                script.hat = node.id;
                return;
            }
        }
        final ScriptNode hat = new ScriptNode(Scripts.uniqueId(script, "hat"), "on_pass");
        script.hat = hat.id;
        script.nodes.add(0, hat);
    }
}
