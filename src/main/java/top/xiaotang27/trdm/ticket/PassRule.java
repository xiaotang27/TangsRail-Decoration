package top.xiaotang27.trdm.ticket;

import top.xiaotang27.trdm.fare.script.FareScript;
import top.xiaotang27.trdm.fare.script.PassSettings;
import top.xiaotang27.trdm.fare.script.PassScripts;
import top.xiaotang27.trdm.fare.script.ScriptRuntime;

import java.util.LinkedHashSet;
import java.util.Set;

/** 可配置的通票模板：稳定 ID、显示名、积木脚本和适用的票务规则范围。 */
public final class PassRule {

    public String id;
    public String name;
    /** 旧字段，保留给未迁移配置和快速展示；以脚本求值为准。 */
    public int priceEmeralds;
    /** 旧字段，保留给未迁移配置和快速展示；以脚本求值为准。 */
    public int days;
    public FareScript script;
    /** true = 适用于全部票务规则。 */
    public boolean allRules = true;
    public final Set<String> ruleIds = new LinkedHashSet<>();

    public PassRule(String id, String name, int priceEmeralds, int days) {
        this.id = id;
        this.name = name;
        this.priceEmeralds = Math.max(0, priceEmeralds);
        this.days = Math.max(1, days);
        this.script = PassScripts.defaults(this.priceEmeralds, this.days, 100, 0);
    }

    public PassRule copy() {
        final PassRule copy = new PassRule(id, name, priceEmeralds, days);
        copy.script = script == null ? null : script.copy();
        copy.allRules = allRules;
        copy.ruleIds.addAll(ruleIds);
        return copy;
    }

    public void ensureScript() {
        if (script == null || script.isEmpty()) {
            script = PassScripts.defaults(priceEmeralds, days, 100, 0);
        } else {
            PassScripts.ensureHat(script);
        }
    }

    /** 执行通票脚本；失败时回退到旧的价格/天数与免费不限次。 */
    public PassSettings settings() {
        ensureScript();
        try {
            final PassSettings settings = ScriptRuntime.evaluatePass(script);
            priceEmeralds = settings.priceEmeralds();
            days = settings.days();
            return settings;
        } catch (RuntimeException exception) {
            return new PassSettings(Math.max(0, priceEmeralds), Math.max(1, days), 100, 0);
        }
    }

    public boolean appliesTo(String ruleId) {
        return allRules || (ruleId != null && ruleIds.contains(ruleId));
    }

    public void applySettings(PassSettings settings) {
        priceEmeralds = settings.priceEmeralds();
        days = settings.days();
    }
}
