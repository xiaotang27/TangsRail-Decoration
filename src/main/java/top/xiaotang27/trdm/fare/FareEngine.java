package top.xiaotang27.trdm.fare;

import java.util.ArrayList;
import java.util.List;

/**
 * 报价引擎。
 * <p>
 * 两套模式：
 * <ul>
 *   <li>{@link FareConfig.Mode#MTR_VANILLA}：整程按 MTR 原版公式 {@code 2 + |收费区差|}；</li>
 *   <li>{@link FareConfig.Mode#CUSTOM}：求最短路径，按线路切段，每段用该线路绑定的规则算价；
 *       未绑定规则的线路回退 MTR 原版公式。各段相加后扣换乘优惠。</li>
 * </ul>
 * 每段用该规则自己的 {@code maxFare} 封顶；全局最高票价只用于无票补票，不封顶总价。
 */
public final class FareEngine {

    public static final long VANILLA_BASE_CENTS = 200L;
    public static final long VANILLA_ZONE_CENTS = 100L;

    private FareEngine() {
    }

    public static FareQuote quote(FareConfig config, FareGraph graph, long fromId, long toId, String dimension) {
        final long cap = config.maxFareCapCents();
        final FareStation from = graph.station(fromId);
        final FareStation to = graph.station(toId);
        if (from == null || to == null) {
            return new FareQuote(cap, cap, false, 0L, List.of());
        }
        if (config.mode == FareConfig.Mode.MTR_VANILLA) {
            final long fare = vanillaFare(from, to);
            return new FareQuote(fare, cap, true, 0L, List.of(vanillaLeg(fare)));
        }
        final FareGraph.Path path = graph.shortest(fromId, toId, config.metric);
        if (!path.connected()) {
            return new FareQuote(cap, cap, false, 0L, List.of());
        }
        if (path.edges().isEmpty()) {
            // 同站进出：没有线路可切段，按 MTR 原版起步价处理。
            final long fare = vanillaFare(from, to);
            return new FareQuote(fare, cap, true, 0L, List.of(vanillaLeg(fare)));
        }

        final List<Leg> legs = segments(path, config, dimension);
        long total = 0L;
        final List<FareQuote.FareLeg> result = new ArrayList<>();
        for (final Leg leg : legs) {
            final FareStation start = graph.station(leg.fromId);
            final FareStation end = graph.station(leg.toId);
            final int startZone = start == null ? 0 : start.zone();
            final int endZone = end == null ? 0 : end.zone();
            long fare = leg.rule.baseFareCents(leg.intervals, leg.meters, startZone, endZone);
            boolean capped = false;
            if (leg.rule.maxFareCents > 0L && fare > leg.rule.maxFareCents) {
                fare = leg.rule.maxFareCents;
                capped = true;
            }
            total += fare;
            result.add(new FareQuote.FareLeg(leg.routeId, leg.routeName, leg.rule.id, leg.rule.name,
                    leg.intervals, leg.meters, fare, capped));
        }
        final long discount = Math.min(total,
                config.transferDiscountCents * Math.max(0, result.size() - 1));
        return new FareQuote(total - discount, cap, true, discount, result);
    }

    private static long vanillaFare(FareStation from, FareStation to) {
        return VANILLA_BASE_CENTS + VANILLA_ZONE_CENTS * Math.abs(to.zone() - from.zone());
    }

    private static FareQuote.FareLeg vanillaLeg(long fare) {
        return new FareQuote.FareLeg(0L, "", FareRule.VANILLA_ID, FareRule.VANILLA_NAME, 0L, 0L, fare, false);
    }

    /** 先把相邻同线路的边合成一段，再按“同一规则”合并相邻段。 */
    private static List<Leg> segments(FareGraph.Path path, FareConfig config, String dimension) {
        final List<Leg> legs = new ArrayList<>();
        for (final FareGraph.Edge edge : path.edges()) {
            final Leg last = legs.isEmpty() ? null : legs.get(legs.size() - 1);
            if (last != null && last.routeId == edge.routeId()) {
                last.grow(edge);
                continue;
            }
            legs.add(new Leg(edge, ruleFor(config, dimension, edge)));
        }
        if (!config.mergeSameRule) {
            return legs;
        }
        final List<Leg> merged = new ArrayList<>();
        for (final Leg leg : legs) {
            final Leg last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && last.rule.id.equals(leg.rule.id)) {
                last.absorb(leg);
            } else {
                merged.add(leg);
            }
        }
        return merged;
    }

    private static FareRule ruleFor(FareConfig config, String dimension, FareGraph.Edge edge) {
        final FareRule rule = config.boundRule(dimension, edge.routeId());
        return rule == null ? FareRule.vanilla() : rule;
    }

    /** 报价过程中的分段草稿。 */
    private static final class Leg {

        private final long routeId;
        private String routeName;
        private long fromId;
        private long toId;
        private long intervals;
        private long meters;
        private final FareRule rule;

        private Leg(FareGraph.Edge edge, FareRule rule) {
            this.routeId = edge.routeId();
            this.routeName = edge.routeName();
            this.fromId = edge.from();
            this.toId = edge.to();
            this.intervals = 1L;
            this.meters = Math.max(0L, edge.meters());
            this.rule = rule;
        }

        private void grow(FareGraph.Edge edge) {
            toId = edge.to();
            intervals++;
            meters += Math.max(0L, edge.meters());
        }

        private void absorb(Leg other) {
            toId = other.toId;
            intervals += other.intervals;
            meters += other.meters;
            if (!routeName.equals(other.routeName)) {
                routeName = routeName + " / " + other.routeName;
            }
        }
    }
}
