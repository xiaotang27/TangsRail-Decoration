package top.xiaotang27.trdm.fare;

import top.xiaotang27.trdm.fare.script.ScriptContext;
import top.xiaotang27.trdm.fare.script.ScriptRuntime;
import top.xiaotang27.trdm.ticket.PassRule;

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
    /** 可配置的通票模板（名称/售价/天数）。 */
    public final List<PassRule> passRules = new ArrayList<>();

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

    /**
     * 全网最低票价（分）：所有启用规则里可能出现的最低票价。
     * <p>
     * 入闸时用它判断“余额是否至少够坐一次车”；没有规则时按 MTR 原版起步价 2 元。
     */
    public long minimumFareCents() {
        long min = Long.MAX_VALUE;
        for (final FareRule rule : rules) {
            if (!rule.enabled || rule.isVanilla()) {
                continue;
            }
            long fare = switch (rule.kind) {
                case FLAT -> rule.flatFareCents;
                case INTERVAL_TABLE, DISTANCE_TABLE -> {
                    long lowest = Long.MAX_VALUE;
                    for (final FareRule.Tier tier : rule.tiers) {
                        lowest = Math.min(lowest, tier.fareCents());
                    }
                    yield lowest == Long.MAX_VALUE ? 0L : lowest;
                }
                case MTR_VANILLA -> FareEngine.VANILLA_BASE_CENTS;
                // 积木脚本按“零区间/零里程”试算一个最低票价；失败按原版起步价。
                case SCRIPT -> {
                    if (rule.script == null) {
                        yield FareEngine.VANILLA_BASE_CENTS;
                    }
                    try {
                        final double yuan = ScriptRuntime.evaluate(rule.script,
                                new ScriptContext(0L, 0L, 0, 0));
                        yield Math.max(0L, Math.round(yuan * 100.0));
                    } catch (RuntimeException exception) {
                        yield FareEngine.VANILLA_BASE_CENTS;
                    }
                }
            };
            min = Math.min(min, Math.max(0L, fare));
        }
        return min == Long.MAX_VALUE ? FareEngine.VANILLA_BASE_CENTS : min;
    }

    public void touch() {
        revision++;
    }

    /**
     * 用一个新配置覆盖当前对象（保留对象引用，界面上缓存的 {@code rules} 列表仍然有效）。
     * 用于服务端同步快照。
     */
    public void copyFrom(FareConfig other) {
        if (other == null || other == this) {
            return;
        }
        schemaVersion = other.schemaVersion;
        revision = other.revision;
        enabled = other.enabled;
        mode = other.mode;
        metric = other.metric;
        transferDiscountCents = other.transferDiscountCents;
        mergeSameRule = other.mergeSameRule;
        rules.clear();
        for (final FareRule rule : other.rules) {
            rules.add(rule.copy());
        }
        routeRules.clear();
        routeRules.putAll(other.routeRules);
        passRules.clear();
        for (final PassRule pass : other.passRules) {
            passRules.add(pass.copy());
        }
    }
}
