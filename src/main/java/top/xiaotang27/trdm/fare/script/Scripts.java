package top.xiaotang27.trdm.fare.script;

/** 脚本的构造辅助（界面默认模板、测试）。 */
public final class Scripts {

    private Scripts() {
    }

    /** 只有帽子块的空脚本。 */
    public static FareScript empty() {
        final FareScript script = new FareScript();
        final ScriptNode hat = new ScriptNode("hat", "on_fare");
        script.hat = hat.id;
        script.nodes.add(hat);
        return script;
    }

    /** 帽子块 + 设置一个统一票价（元）。 */
    public static FareScript flat(double yuan) {
        final FareScript script = empty();
        final ScriptNode set = new ScriptNode(uniqueId(script, "s"), "set_fare");
        final ScriptNode number = new ScriptNode(uniqueId(script, "v"), "number");
        number.fields.put("value", Double.toString(yuan));
        set.inputs.put("value", number.id);
        script.node(script.hat).next = set.id;
        script.nodes.add(set);
        script.nodes.add(number);
        return script;
    }

    /** 确保脚本有可用的帽子块（手改 JSON 漏了帽子时兜底）。 */
    public static void ensureHat(FareScript script) {
        if (script.node(script.hat) != null && "on_fare".equals(script.node(script.hat).type)) {
            return;
        }
        for (final ScriptNode node : script.nodes) {
            if ("on_fare".equals(node.type)) {
                script.hat = node.id;
                return;
            }
        }
        final ScriptNode hat = new ScriptNode(uniqueId(script, "hat"), "on_fare");
        script.hat = hat.id;
        script.nodes.add(0, hat);
    }

    public static String uniqueId(FareScript script, String prefix) {
        int index = 1;
        while (script.node(prefix + index) != null) {
            index++;
        }
        return prefix + index;
    }
}
