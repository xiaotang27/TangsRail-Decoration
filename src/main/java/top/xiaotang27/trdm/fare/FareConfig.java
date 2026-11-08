package top.xiaotang27.trdm.fare;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 票务设置 + 规则 + 线路绑定。
 * <p>
 * 与 {@link FareJson} 对应；字段含义见 {@code TRDM票务系统实现规划.md} 第 4 章。
 * 一条线路只能绑定一条规则：{@link #routeRules} 的键是“维度|线路 ID”，值是该规则的 ID。
 */
public final class FareConfig {

    public static final int SCHEMA_VERSION = 1;
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";

    /** 计费模式。 */
    public enum Mode {
        /** MTR 原版公式：2 + |收费区差|。 */
        MTR_VANILLA,
        /** 自定义规则；未绑定规则的线路仍回退 MTR 原版公式。 */
        CUSTOM
    }

    /** 求最短路径时的权重。 */
    public enum Metric {
        /** 区间数。 */
        INTERVALS,
        /** 里程（米）。 */
        DISTANCE
    }

    public int schemaVersion = SCHEMA_VERSION;
    public long revision;
    public boolean enabled = true;
    public Mode mode = Mode.CUSTOM;
    public Metric metric = Metric.INTERVALS;
    /** 每次换乘减多少钱（分）；0 = 不优惠。 */
    public long transferDiscountCents;
    /** 相邻分段使用同一规则时合并后再算，避免重复收起步价。 */
    public boolean mergeSameRule = true;

    public final List<FareRule> rules = new ArrayList<>();
    public final Map<String, String> routeRules = new LinkedHashMap<>();

    public static String bindingKey(String dimension, long routeId) {
        return (dimension == null || dimension.isBlank() ? DEFAULT_DIMENSION : dimension) + "|" + routeId;
    }

    public FareRule ruleById(String id) {
        if (id == null) {
            return null;
        }
        for (final FareRule rule : rules) {
            if (id.equals(rule.id)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 查线路绑定的规则。
     * <p>
     * 未绑定、绑到已删除的规则或规则被停用时返回 {@code null}，由报价引擎回退 MTR 原版公式。
     */
    public FareRule boundRule(String dimension, long routeId) {
        final FareRule rule = ruleById(routeRules.get(bindingKey(dimension, routeId)));
        return rule != null && rule.enabled ? rule : null;
    }

    /** 全局最高票价（分）：所有启用规则 {@code maxFare} 的最大值；没有规则时为 0。 */
    public long maxFareCapCents() {
        long max = 0L;
        for (final FareRule rule : rules) {
            if (rule.enabled && !rule.isVanilla() && rule.maxFareCents > max) {
                max = rule.maxFareCents;
            }
        }
        return max;
    }

    public void touch() {
        revision++;
    }
}
