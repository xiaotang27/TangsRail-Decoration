package top.xiaotang27.trdm.fare;

import top.xiaotang27.trdm.fare.script.FareScript;
import top.xiaotang27.trdm.fare.script.ScriptContext;
import top.xiaotang27.trdm.fare.script.ScriptNode;
import top.xiaotang27.trdm.fare.script.ScriptRuntime;
import top.xiaotang27.trdm.fare.script.ScriptTier;
import top.xiaotang27.trdm.fare.script.Scripts;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条计费规则。
 * <p>
 * 金额单位统一为“分”（{@code long}），显示时再换算成元，避免浮点误差。
 * 规则不持有玩家/世界状态，报价时只读。
 */
public final class FareRule {

    /** 规则类型。 */
    public enum Kind {
        /** MTR 原版：2 元 + 每跨一个收费区 1 元。 */
        MTR_VANILLA,
        /** 统一票价。 */
        FLAT,
        /** 按区间数查阶梯表。 */
        INTERVAL_TABLE,
        /** 按里程（米）查阶梯表。 */
        DISTANCE_TABLE,
        /** 图形化积木脚本。 */
        SCRIPT
    }

    /**
     * 阶梯表的一行。
     *
     * @param upTo    上限（含）；{@code null} 表示“其它/不设上限”，应放在最后一行
     * @param fareCents 该档票价（分）
     */
    public record Tier(Long upTo, long fareCents) {
    }

    public static final String VANILLA_ID = "mtr_vanilla";
    public static final String VANILLA_NAME = "MTR 原版计费";

    public String id;
    public String name;
    public boolean enabled = true;
    /** 该规则的最高票价（分）。0 = 不封顶，同时不参与全局最高票价。 */
    public long maxFareCents;
    public Kind kind;
    /** {@link Kind#FLAT} 使用的统一票价（分）。 */
    public long flatFareCents;
    /** 阶梯表，按 {@code upTo} 升序，最后一行可以是开放式。 */
    public final List<Tier> tiers = new ArrayList<>();
    /** {@link Kind#SCRIPT} 使用的积木脚本；其它类型为 {@code null}。 */
    public FareScript script;

    public FareRule(String id, String name, Kind kind) {
        this.id = id;
        this.name = name;
        this.kind = kind;
    }

    public static FareRule vanilla() {
        return new FareRule(VANILLA_ID, VANILLA_NAME, Kind.MTR_VANILLA);
    }

    /** 深拷贝，供规则编辑器“取消/保存”使用（{@link Tier} 不可变，直接共享）。 */
    public FareRule copy() {
        final FareRule copy = new FareRule(id, name, kind);
        copy.enabled = enabled;
        copy.maxFareCents = maxFareCents;
        copy.flatFareCents = flatFareCents;
        copy.tiers.addAll(tiers);
        copy.script = script == null ? null : script.copy();
        return copy;
    }

    public boolean isVanilla() {
        return kind == Kind.MTR_VANILLA;
    }

    /** 未应用 {@code maxFare} 封顶的票价（分）。 */
    public long baseFareCents(long intervals, long meters, int fromZone, int toZone) {
        return baseFareCents(intervals, meters, fromZone, toZone, null);
    }

    public long baseFareCents(long intervals, long meters, int fromZone, int toZone,
                              top.xiaotang27.trdm.fare.script.RuleResolver resolver) {
        return switch (kind) {
            case MTR_VANILLA -> vanillaFareCents(fromZone, toZone);
            case FLAT -> Math.max(0L, flatFareCents);
            case INTERVAL_TABLE -> tierFareCents(intervals);
            case DISTANCE_TABLE -> tierFareCents(meters);
            case SCRIPT -> scriptFareCents(intervals, meters, fromZone, toZone, resolver);
        };
    }

    private long vanillaFareCents(int fromZone, int toZone) {
        return FareEngine.VANILLA_BASE_CENTS
                + FareEngine.VANILLA_ZONE_CENTS * Math.abs(toZone - fromZone);
    }

    /** 执行积木脚本；脚本缺失/编译失败/运行失败时回退 MTR 原版公式。 */
    private long scriptFareCents(long intervals, long meters, int fromZone, int toZone,
                                 top.xiaotang27.trdm.fare.script.RuleResolver resolver) {
        if (script == null || script.isEmpty()) {
            return vanillaFareCents(fromZone, toZone);
        }
        try {
            final double yuan = ScriptRuntime.evaluate(script,
                    new ScriptContext(intervals, meters, fromZone, toZone), resolver);
            if (!Double.isFinite(yuan)) {
                return vanillaFareCents(fromZone, toZone);
            }
            return Math.max(0L, Math.round(yuan * 100.0));
        } catch (RuntimeException exception) {
            return vanillaFareCents(fromZone, toZone);
        }
    }

    /** 在阶梯表里按数值取票价；超出所有行时退回最后一行的票价。 */
    public long tierFareCents(long value) {
        if (tiers.isEmpty()) {
            return 0L;
        }
        for (final Tier tier : tiers) {
            if (tier.upTo() == null || value <= tier.upTo()) {
                return Math.max(0L, tier.fareCents());
            }
        }
        return Math.max(0L, tiers.get(tiers.size() - 1).fareCents());
    }

    /**
     * 把旧的“统一/区间/里程”预设转成等价的积木脚本。
     * <p>MTR 原版和已经是积木的规则返回 {@code null}。
     */
    public FareScript toScript() {
        if (kind == Kind.MTR_VANILLA) {
            return null;
        }
        if (kind == Kind.SCRIPT) {
            return script == null ? null : script.copy();
        }
        if ((kind == Kind.INTERVAL_TABLE || kind == Kind.DISTANCE_TABLE) && tiers.isEmpty()) {
            return null;
        }
        final FareScript result = Scripts.empty();
        final ScriptNode hat = result.node(result.hat);
        if (hat == null) {
            return null;
        }
        final ScriptNode set = new ScriptNode(Scripts.uniqueId(result, "s"), "set_fare");
        hat.next = set.id;
        result.nodes.add(set);

        if (kind == Kind.FLAT) {
            final ScriptNode number = new ScriptNode(Scripts.uniqueId(result, "v"), "number");
            number.fields.put("value", Double.toString(Math.max(0L, flatFareCents) / 100.0));
            set.inputs.put("value", number.id);
            result.nodes.add(number);
        } else {
            final ScriptNode table = new ScriptNode(Scripts.uniqueId(result, "v"),
                    kind == Kind.INTERVAL_TABLE ? "table_intervals" : "table_meters");
            for (final Tier tier : tiers) {
                final Double upTo = tier.upTo() == null ? null
                        : kind == Kind.DISTANCE_TABLE
                        ? (double) tier.upTo() / 1000.0
                        : (double) tier.upTo();
                table.tiers.add(new ScriptTier(upTo, Math.max(0L, tier.fareCents()) / 100.0));
            }
            set.inputs.put("value", table.id);
            result.nodes.add(table);
        }
        return result;
    }

    /** 把旧预设原地升级成积木规则；已经是积木/原版返回 false。 */
    public boolean migrateToScript() {
        if (kind == Kind.MTR_VANILLA || kind == Kind.SCRIPT) {
            return false;
        }
        final FareScript converted = toScript();
        if (converted == null) {
            return false;
        }
        script = converted;
        kind = Kind.SCRIPT;
        return true;
    }
}
