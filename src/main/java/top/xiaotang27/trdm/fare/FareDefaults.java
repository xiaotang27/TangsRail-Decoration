package top.xiaotang27.trdm.fare;

import top.xiaotang27.trdm.ticket.PassRules;

/**
 * 默认票务配置：三条基础规则 + 三种默认通票模板，线路默认不绑定（未绑定 = MTR 原版公式）。
 * <p>
 * 规则本身是通用模板，不含任何示例线路/车站；管理员在票务设置页把规则绑到真实线路上。
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

        final FareRule flat = new FareRule("flat", "统一票价", FareRule.Kind.FLAT);
        flat.flatFareCents = 200L;
        flat.maxFareCents = 200L;

        final FareRule interval = new FareRule("interval", "按区间阶梯计费", FareRule.Kind.INTERVAL_TABLE);
        interval.tiers.add(new FareRule.Tier(2L, 200L));
        interval.tiers.add(new FareRule.Tier(9L, 300L));
        interval.tiers.add(new FareRule.Tier(null, 400L));
        interval.maxFareCents = 400L;

        final FareRule distance = new FareRule("distance", "按里程阶梯计费", FareRule.Kind.DISTANCE_TABLE);
        distance.tiers.add(new FareRule.Tier(1000L, 200L));
        distance.tiers.add(new FareRule.Tier(3000L, 300L));
        distance.tiers.add(new FareRule.Tier(null, 400L));
        distance.maxFareCents = 400L;

        config.rules.add(flat);
        config.rules.add(interval);
        config.rules.add(distance);
        config.passRules.addAll(PassRules.defaults());
        return config;
    }
}
