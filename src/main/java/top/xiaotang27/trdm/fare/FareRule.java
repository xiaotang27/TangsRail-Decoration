package top.xiaotang27.trdm.fare;

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
        DISTANCE_TABLE
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

    public FareRule(String id, String name, Kind kind) {
        this.id = id;
        this.name = name;
        this.kind = kind;
    }

    public static FareRule vanilla() {
        return new FareRule(VANILLA_ID, VANILLA_NAME, Kind.MTR_VANILLA);
    }

    public boolean isVanilla() {
        return kind == Kind.MTR_VANILLA;
    }

    /** 未应用 {@code maxFare} 封顶的票价（分）。 */
    public long baseFareCents(long intervals, long meters, int fromZone, int toZone) {
        return switch (kind) {
            case MTR_VANILLA -> FareEngine.VANILLA_BASE_CENTS
                    + FareEngine.VANILLA_ZONE_CENTS * Math.abs(toZone - fromZone);
            case FLAT -> Math.max(0L, flatFareCents);
            case INTERVAL_TABLE -> tierFareCents(intervals);
            case DISTANCE_TABLE -> tierFareCents(meters);
        };
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
}
