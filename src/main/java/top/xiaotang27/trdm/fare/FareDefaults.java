package top.xiaotang27.trdm.fare;

import top.xiaotang27.trdm.fare.script.FareScript;
import top.xiaotang27.trdm.fare.script.ScriptTier;
import top.xiaotang27.trdm.fare.script.Scripts;
import top.xiaotang27.trdm.ticket.PassRules;

import java.util.List;

/**
 * 默认票务配置：三条基础规则 + 三种默认通票模板，线路默认不绑定（未绑定 = MTR 原版公式）。
 * <p>
 * 三条预设规则本身就是积木脚本（统一票价、区间阶梯、里程阶梯），
 * 不经过旧的固定字段类型；旧存档仍由 {@link FareJson} 在读取时迁移。
 */
public final class FareDefaults {

    private FareDefaults() {
    }

    public static FareConfig config() {
        final FareConfig config = new FareConfig();
        config.mode = FareConfig.Mode.CUSTOM;
        config.metric = FareConfig.Metric.INTERVALS;
        config.transferDiscountCents = 0L;
        config.mergeSameRule = true;

        config.rules.add(scriptRule("flat", "统一票价", Scripts.flat(2.0), 200L));
        config.rules.add(scriptRule("interval", "按区间阶梯计费",
                Scripts.table("table_intervals", List.of(
                        new ScriptTier(2.0, 2.0),
                        new ScriptTier(9.0, 3.0),
                        new ScriptTier(null, 4.0))), 400L));
        config.rules.add(scriptRule("distance", "按里程阶梯计费",
                Scripts.table("table_meters", List.of(
                        new ScriptTier(1000.0, 2.0),
                        new ScriptTier(3000.0, 3.0),
                        new ScriptTier(null, 4.0))), 400L));

        config.passRules.addAll(PassRules.defaults());
        return config;
    }

    private static FareRule scriptRule(String id, String name,
                                       FareScript script, long maxFareCents) {
        final FareRule rule = new FareRule(id, name, FareRule.Kind.SCRIPT);
        rule.script = script;
        rule.maxFareCents = maxFareCents;
        return rule;
    }
}
