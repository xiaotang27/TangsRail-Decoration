package top.xiaotang27.trdm.ticket;

import mtr.data.Station;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import top.xiaotang27.trdm.fare.CardRuleStats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 单程票 / 交通卡的 NBT 读写，客户端和服务端共用。
 * <p>
 * 金额统一用“分”（{@code long}）；车站 ID 用 MTR 的 {@code Station.id}。
 * 所有字段都在物品根标签下的 {@code trdm_ticket} 子标签里，避免和其它模组/版本冲突。
 */
public final class TicketData {

    public static final String ROOT = "trdm_ticket";

    public static final String TYPE_SINGLE = "single";
    public static final String TYPE_CARD = "card";
    /** 无票补票后发的“出站票”：只能在一个指定车站出站一次。 */
    public static final String TYPE_EXIT = "exit";
    /** 通票（可变车票）：由 {@link PassCatalog} 的定义决定有效期和售价。 */
    public static final String TYPE_PASS = "pass";

    public enum State {
        /** 未使用。 */
        EMPTY,
        /** 已入闸，等待出闸。 */
        ENTERED,
        /** 已出闸（交通卡可以再次入闸）。 */
        EXITED
    }

    /** 一条交易记录。金额为分，支出为负数。 */
    public record Tx(long time, String type, long amountCents, long balanceAfter) {
    }

    public static final int MAX_TX = 50;

    private static final String KEY_SERIAL = "serial";
    private static final String KEY_TYPE = "type";
    private static final String KEY_FACE = "face";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_DEPOSIT = "deposit";
    private static final String KEY_CARD_RULE_STATS = "card_rule_stats";
    private static final String KEY_CARD_DAY_KEY = "card_day_key";
    private static final String KEY_CARD_DAY_RIDES = "card_day_rides";
    private static final String KEY_CARD_DAY_AMOUNT = "card_day_amount";
    private static final String KEY_CARD_MONTH_KEY = "card_month_key";
    private static final String KEY_CARD_MONTH_RIDES = "card_month_rides";
    private static final String KEY_CARD_MONTH_AMOUNT = "card_month_amount";
    private static final String KEY_STATE = "state";
    private static final String KEY_SOLD_STATION = "sold_station";
    private static final String KEY_SOLD_NAME = "sold_name";
    private static final String KEY_SOLD_DIM = "sold_dim";
    private static final String KEY_ISSUED_AT = "issued_at";
    private static final String KEY_ENTRY_STATION = "entry_station";
    private static final String KEY_ENTRY_NAME = "entry_name";
    private static final String KEY_ENTRY_DIM = "entry_dim";
    private static final String KEY_ENTRY_TIME = "entry_time";
    private static final String KEY_LAST_EXIT_STATION = "last_exit_station";
    private static final String KEY_LAST_EXIT_NAME = "last_exit_name";
    private static final String KEY_LAST_EXIT_DIM = "last_exit_dim";
    private static final String KEY_LAST_EXIT_TIME = "last_exit_time";
    private static final String KEY_LAST_EXIT_ZONE = "last_exit_zone";
    private static final String KEY_LAST_EXIT_PAID = "last_exit_paid";
    private static final String KEY_JOURNEY_ENTRY_STATION = "journey_entry_station";
    private static final String KEY_JOURNEY_ENTRY_NAME = "journey_entry_name";
    private static final String KEY_JOURNEY_ENTRY_DIM = "journey_entry_dim";
    private static final String KEY_JOURNEY_PAID = "journey_paid";
    private static final String KEY_JOURNEY_CONTINUING = "journey_continuing";
    private static final String KEY_TX = "tx";
    private static final String KEY_EXIT_STATION = "exit_station";
    private static final String KEY_EXIT_NAME = "exit_name";
    private static final String KEY_EXIT_DIM = "exit_dim";
    private static final String KEY_EXIT_EXPIRES = "exit_expires";
    private static final String KEY_PASS_DEF = "pass_def";
    private static final String KEY_PASS_NAME = "pass_name";
    private static final String KEY_PASS_DAYS = "pass_days";
    private static final String KEY_PASS_OWNER = "pass_owner";
    private static final String KEY_PASS_OWNER_NAME = "pass_owner_name";
    private static final String KEY_PASS_ACTIVE = "pass_active";
    private static final String KEY_PASS_FROM = "pass_from";
    private static final String KEY_PASS_UNTIL = "pass_until";
    private static final String KEY_PASS_DISCOUNT = "pass_discount";
    private static final String KEY_PASS_DAILY_LIMIT = "pass_daily_limit";
    private static final String KEY_PASS_ALL_RULES = "pass_all_rules";
    private static final String KEY_PASS_RULES = "pass_rules";
    private static final String KEY_PASS_DAY_KEY = "pass_day_key";
    private static final String KEY_PASS_DAY_COUNT = "pass_day_count";

    private TicketData() {
    }

    // ===== 发行 =====

    public static void issueSingle(ItemStack stack, long faceCents, Station sold, String soldDimension) {
        final CompoundTag root = new CompoundTag();
        root.putString(KEY_SERIAL, UUID.randomUUID().toString());
        root.putString(KEY_TYPE, TYPE_SINGLE);
        root.putLong(KEY_FACE, Math.max(0L, faceCents));
        root.putString(KEY_STATE, State.EMPTY.name());
        root.putLong(KEY_ISSUED_AT, TrdmClock.now());
        putSold(root, sold, soldDimension);
        commit(stack, root);
    }

    public static void issueCard(ItemStack stack, long balanceCents, int depositEmeralds,
                                 Station sold, String soldDimension) {
        final CompoundTag root = new CompoundTag();
        root.putString(KEY_SERIAL, UUID.randomUUID().toString());
        root.putString(KEY_TYPE, TYPE_CARD);
        root.putLong(KEY_BALANCE, Math.max(0L, balanceCents));
        root.putInt(KEY_DEPOSIT, Math.max(0, depositEmeralds));
        root.putString(KEY_STATE, State.EMPTY.name());
        root.putLong(KEY_ISSUED_AT, TrdmClock.now());
        root.put(KEY_TX, new ListTag());
        putSold(root, sold, soldDimension);
        commit(stack, root);
    }

    /** 无票补票后的出站票：只允许在 {@code station} 出站一次。 */
    public static void issueExitTicket(ItemStack stack, Station station, String dimension, long expiresAt) {
        final CompoundTag root = new CompoundTag();
        root.putString(KEY_SERIAL, UUID.randomUUID().toString());
        root.putString(KEY_TYPE, TYPE_EXIT);
        root.putLong(KEY_EXIT_STATION, station == null ? 0L : station.id);
        root.putString(KEY_EXIT_NAME, stationName(station));
        root.putString(KEY_EXIT_DIM, dimension == null ? "" : dimension);
        root.putLong(KEY_EXIT_EXPIRES, expiresAt);
        root.putLong(KEY_ISSUED_AT, TrdmClock.now());
        commit(stack, root);
    }

    /** 发售一张未激活通票（旧签名，名字留空时按定义 ID 回退）。 */
    public static void issuePass(ItemStack stack, String definitionId, int days,
                                 Station sold, String soldDimension) {
        issuePass(stack, definitionId, days, "", sold, soldDimension);
    }

    /** 发售一张未激活通票，名字写进 NBT，之后删模板也不影响已售出的票。 */
    public static void issuePass(ItemStack stack, String definitionId, int days, String name,
                                 Station sold, String soldDimension) {
        issuePass(stack, definitionId, days, name, sold, soldDimension, 100, 0, true, List.of());
    }

    /** 发售通票并快照折扣、每日次数和适用规则范围。 */
    public static void issuePass(ItemStack stack, String definitionId, int days, String name,
                                 Station sold, String soldDimension, int discountPercent,
                                 int dailyLimit, boolean allRules, Collection<String> ruleIds) {
        final CompoundTag root = new CompoundTag();
        root.putString(KEY_SERIAL, UUID.randomUUID().toString());
        root.putString(KEY_TYPE, TYPE_PASS);
        root.putString(KEY_PASS_DEF, definitionId == null ? "" : definitionId);
        root.putString(KEY_PASS_NAME, name == null ? "" : name);
        root.putInt(KEY_PASS_DAYS, Math.max(1, days));
        root.putInt(KEY_PASS_DISCOUNT, Math.max(0, Math.min(100, discountPercent)));
        root.putInt(KEY_PASS_DAILY_LIMIT, Math.max(0, dailyLimit));
        root.putBoolean(KEY_PASS_ALL_RULES, allRules);
        final ListTag rules = new ListTag();
        if (ruleIds != null) {
            for (final String ruleId : ruleIds) {
                if (ruleId != null && !ruleId.isBlank()) {
                    rules.add(StringTag.valueOf(ruleId));
                }
            }
        }
        root.put(KEY_PASS_RULES, rules);
        root.putBoolean(KEY_PASS_ACTIVE, false);
        root.putString(KEY_STATE, State.EMPTY.name());
        root.putLong(KEY_ISSUED_AT, TrdmClock.now());
        putSold(root, sold, soldDimension);
        commit(stack, root);
    }

    // ===== 车站进出 =====

    /** 写入入站记录并切到 {@link State#ENTERED}。 */
    public static void enter(ItemStack stack, Station station, String dimension) {
        final CompoundTag root = copyRoot(stack);
        applyEntry(root, station, dimension, TrdmClock.now());
        commit(stack, root);
    }

    /** 交通卡出站：写 lastExit、扣费记录、切到 {@link State#EXITED}。 */
    public static void markExit(ItemStack stack, Station exit, String dimension, long fareCents,
                                long chargedCents, long exitMillis) {
        final CompoundTag root = copyRoot(stack);
        final long now = exitMillis > 0L ? exitMillis : TrdmClock.now();
        root.putString(KEY_STATE, State.EXITED.name());
        root.putLong(KEY_LAST_EXIT_STATION, exit.id);
        root.putString(KEY_LAST_EXIT_NAME, stationName(exit));
        root.putString(KEY_LAST_EXIT_DIM, dimension);
        root.putLong(KEY_LAST_EXIT_TIME, now);
        root.putInt(KEY_LAST_EXIT_ZONE, exit.zone);
        root.putLong(KEY_LAST_EXIT_PAID, Math.max(0L, fareCents));
        addTx(root, now, "fare", -chargedCents, root.getLong(KEY_BALANCE));
        commit(stack, root);
    }

    /** 开始一段普通行程：journey 指向本次入站，已付 0，非连续。 */
    public static void startJourney(ItemStack stack, Station station, String dimension) {
        final CompoundTag root = copyRoot(stack);
        root.putLong(KEY_JOURNEY_ENTRY_STATION, station.id);
        root.putString(KEY_JOURNEY_ENTRY_NAME, stationName(station));
        root.putString(KEY_JOURNEY_ENTRY_DIM, dimension == null ? "" : dimension);
        root.putLong(KEY_JOURNEY_PAID, 0L);
        root.putBoolean(KEY_JOURNEY_CONTINUING, false);
        applyEntry(root, station, dimension, TrdmClock.now());
        commit(stack, root);
    }

    /** 连续行程：保留原始入站和已付金额，只切回 ENTERED。 */
    public static void continueJourney(ItemStack stack) {
        final CompoundTag root = copyRoot(stack);
        root.putBoolean(KEY_JOURNEY_CONTINUING, true);
        root.putString(KEY_STATE, State.ENTERED.name());
        commit(stack, root);
    }

    /** 补写 / 覆盖入站记录（客服补记录用）。 */
    public static void setEntry(ItemStack stack, Station station, String dimension, long time) {
        final CompoundTag root = copyRoot(stack);
        applyEntry(root, station, dimension, time);
        commit(stack, root);
    }

    private static void applyEntry(CompoundTag root, Station station, String dimension, long time) {
        root.putString(KEY_STATE, State.ENTERED.name());
        root.putLong(KEY_ENTRY_STATION, station.id);
        root.putString(KEY_ENTRY_NAME, stationName(station));
        root.putString(KEY_ENTRY_DIM, dimension == null ? "" : dimension);
        root.putLong(KEY_ENTRY_TIME, time);
    }

    /** 确保手上的交通卡有数据；空白卡（例如创造模式直接拿到的）按 0 余额初始化。 */
    public static void ensureCard(ItemStack stack, Station sold, String dimension) {
        if (hasData(stack) && TYPE_CARD.equals(type(stack))) {
            return;
        }
        issueCard(stack, 0L, 0, sold, dimension);
    }

    // ===== 读 =====

    /** 没有 {@code trdm_ticket} 标签（例如创造模式直接拿到的物品）时返回 false。 */
    public static boolean hasData(ItemStack stack) {
        return root(stack) != null;
    }

    public static String type(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_TYPE);
    }

    public static State state(ItemStack stack) {
        final CompoundTag root = root(stack);
        if (root == null) {
            return State.EMPTY;
        }
        try {
            return State.valueOf(root.getString(KEY_STATE));
        } catch (IllegalArgumentException exception) {
            return State.EMPTY;
        }
    }

    public static long faceValue(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : Math.max(0L, root.getLong(KEY_FACE));
    }

    public static long balance(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : Math.max(0L, root.getLong(KEY_BALANCE));
    }

    /** 交通卡在某一自然日/月内的累计乘车次数和实际支付金额。 */
    public record CardPeriodStats(long rides, long amountCents) {
        public static final CardPeriodStats ZERO = new CardPeriodStats(0L, 0L);

        public double amountYuan() {
            return amountCents / 100.0;
        }
    }

    public static CardPeriodStats cardDayStats(ItemStack stack) {
        final CompoundTag root = root(stack);
        if (root == null || !Long.toString(TrdmClock.dayKey()).equals(root.getString(KEY_CARD_DAY_KEY))) {
            return CardPeriodStats.ZERO;
        }
        return new CardPeriodStats(Math.max(0L, root.getLong(KEY_CARD_DAY_RIDES)),
                Math.max(0L, root.getLong(KEY_CARD_DAY_AMOUNT)));
    }

    public static CardPeriodStats cardMonthStats(ItemStack stack) {
        final CompoundTag root = root(stack);
        if (root == null || !TrdmClock.monthKey().equals(root.getString(KEY_CARD_MONTH_KEY))) {
            return CardPeriodStats.ZERO;
        }
        return new CardPeriodStats(Math.max(0L, root.getLong(KEY_CARD_MONTH_RIDES)),
                Math.max(0L, root.getLong(KEY_CARD_MONTH_AMOUNT)));
    }

    /** 每次成功出站调用一次；金额为本次实际支付金额。 */
    public static void addCardUsage(ItemStack stack, long paidCents) {
        final CompoundTag root = copyRoot(stack);
        final String dayKey = Long.toString(TrdmClock.dayKey());
        final String monthKey = TrdmClock.monthKey();
        final long dayRides = dayKey.equals(root.getString(KEY_CARD_DAY_KEY))
                ? Math.max(0L, root.getLong(KEY_CARD_DAY_RIDES)) : 0L;
        final long dayAmount = dayKey.equals(root.getString(KEY_CARD_DAY_KEY))
                ? Math.max(0L, root.getLong(KEY_CARD_DAY_AMOUNT)) : 0L;
        final long monthRides = monthKey.equals(root.getString(KEY_CARD_MONTH_KEY))
                ? Math.max(0L, root.getLong(KEY_CARD_MONTH_RIDES)) : 0L;
        final long monthAmount = monthKey.equals(root.getString(KEY_CARD_MONTH_KEY))
                ? Math.max(0L, root.getLong(KEY_CARD_MONTH_AMOUNT)) : 0L;
        root.putString(KEY_CARD_DAY_KEY, dayKey);
        root.putLong(KEY_CARD_DAY_RIDES, dayRides + 1L);
        root.putLong(KEY_CARD_DAY_AMOUNT, dayAmount + Math.max(0L, paidCents));
        root.putString(KEY_CARD_MONTH_KEY, monthKey);
        root.putLong(KEY_CARD_MONTH_RIDES, monthRides + 1L);
        root.putLong(KEY_CARD_MONTH_AMOUNT, monthAmount + Math.max(0L, paidCents));
        commit(stack, root);
    }

    /** 读取交通卡在各计费规则下的累计乘车统计。 */
    public static Map<String, CardRuleStats> cardRuleStats(ItemStack stack) {
        final Map<String, CardRuleStats> result = new LinkedHashMap<>();
        final CompoundTag root = root(stack);
        if (root == null || !root.contains(KEY_CARD_RULE_STATS, Tag.TAG_LIST)) {
            return result;
        }
        final ListTag list = root.getList(KEY_CARD_RULE_STATS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            final CompoundTag entry = list.getCompound(index);
            final String ruleId = entry.getString("rule");
            if (ruleId.isBlank()) {
                continue;
            }
            result.put(ruleId, new CardRuleStats(entry.getInt("rides"),
                    entry.getLong("amount"), entry.getLong("meters")));
        }
        return result;
    }

    public static CardRuleStats cardRuleStats(ItemStack stack, String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return CardRuleStats.ZERO;
        }
        return cardRuleStats(stack).getOrDefault(ruleId, CardRuleStats.ZERO);
    }

    /** 出站扣费成功后累加：乘车次数 +1、金额 +实际本段票价、里程 +本段米数。 */
    public static void addCardRuleTrip(ItemStack stack, String ruleId, long fareCents, long meters) {
        if (ruleId == null || ruleId.isBlank()) {
            return;
        }
        final Map<String, CardRuleStats> stats = cardRuleStats(stack);
        final CardRuleStats current = stats.getOrDefault(ruleId, CardRuleStats.ZERO);
        stats.put(ruleId, new CardRuleStats(current.rides() + 1,
                current.amountCents() + Math.max(0L, fareCents),
                current.meters() + Math.max(0L, meters)));
        final ListTag list = new ListTag();
        int count = 0;
        for (final Map.Entry<String, CardRuleStats> entry : stats.entrySet()) {
            if (count++ >= 64) {
                break;
            }
            final CompoundTag tag = new CompoundTag();
            tag.putString("rule", entry.getKey());
            tag.putInt("rides", entry.getValue().rides());
            tag.putLong("amount", entry.getValue().amountCents());
            tag.putLong("meters", entry.getValue().meters());
            list.add(tag);
        }
        final CompoundTag root = copyRoot(stack);
        root.put(KEY_CARD_RULE_STATS, list);
        commit(stack, root);
    }

    public static int deposit(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0 : root.getInt(KEY_DEPOSIT);
    }

    public static boolean hasEntry(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root != null && root.getLong(KEY_ENTRY_STATION) != 0L;
    }

    public static long entryStation(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_ENTRY_STATION);
    }

    public static String entryName(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_ENTRY_NAME);
    }

    public static String entryDimension(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_ENTRY_DIM);
    }

    public static String soldName(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_SOLD_NAME);
    }

    public static long soldStation(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_SOLD_STATION);
    }

    public static String lastExitName(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_LAST_EXIT_NAME);
    }

    public static long lastExitStation(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_LAST_EXIT_STATION);
    }

    public static String lastExitDimension(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_LAST_EXIT_DIM);
    }

    /** 上次出站时的游戏时间（tick），用于 10 分钟连续计费窗口。 */
    public static long lastExitTime(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_LAST_EXIT_TIME);
    }

    public static int lastExitZone(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0 : root.getInt(KEY_LAST_EXIT_ZONE);
    }

    public static long lastExitPaid(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_LAST_EXIT_PAID);
    }

    public static long journeyEntryStation(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_JOURNEY_ENTRY_STATION);
    }

    public static String journeyEntryName(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_JOURNEY_ENTRY_NAME);
    }

    public static String journeyEntryDimension(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_JOURNEY_ENTRY_DIM);
    }

    public static long journeyPaid(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : Math.max(0L, root.getLong(KEY_JOURNEY_PAID));
    }

    public static boolean journeyContinuing(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root != null && root.getBoolean(KEY_JOURNEY_CONTINUING);
    }

    public static void setJourneyPaid(ItemStack stack, long cents) {
        final CompoundTag root = copyRoot(stack);
        root.putLong(KEY_JOURNEY_PAID, Math.max(0L, cents));
        commit(stack, root);
    }

    public static boolean isExitTicket(ItemStack stack) {
        return hasData(stack) && TYPE_EXIT.equals(type(stack));
    }

    public static long exitStation(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_EXIT_STATION);
    }

    public static String exitName(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_EXIT_NAME);
    }

    public static String exitDimension(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_EXIT_DIM);
    }

    public static long exitExpires(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_EXIT_EXPIRES);
    }

    public static boolean isPass(ItemStack stack) {
        return hasData(stack) && TYPE_PASS.equals(type(stack));
    }

    public static String passDefinition(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_PASS_DEF);
    }

    /** 售出时写入的通票显示名；旧票可能为空，由调用方回退。 */
    public static String passName(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_PASS_NAME);
    }

    public static int passDays(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0 : root.getInt(KEY_PASS_DAYS);
    }

    public static boolean passActive(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root != null && root.getBoolean(KEY_PASS_ACTIVE);
    }

    public static String passOwner(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_PASS_OWNER);
    }

    public static String passOwnerName(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? "" : root.getString(KEY_PASS_OWNER_NAME);
    }

    public static long passFrom(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_PASS_FROM);
    }

    public static long passUntil(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_PASS_UNTIL);
    }

    public static int passDiscount(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 100 : root.getInt(KEY_PASS_DISCOUNT);
    }

    public static int passDailyLimit(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0 : root.getInt(KEY_PASS_DAILY_LIMIT);
    }

    public static boolean passAllRules(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null || !root.contains(KEY_PASS_ALL_RULES) || root.getBoolean(KEY_PASS_ALL_RULES);
    }

    public static List<String> passRuleIds(ItemStack stack) {
        final CompoundTag root = root(stack);
        if (root == null) {
            return List.of();
        }
        final List<String> result = new ArrayList<>();
        final ListTag list = root.getList(KEY_PASS_RULES, Tag.TAG_STRING);
        for (int index = 0; index < list.size(); index++) {
            result.add(list.getString(index));
        }
        return result;
    }

    public static long passDayKey(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0L : root.getLong(KEY_PASS_DAY_KEY);
    }

    public static int passDayCount(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0 : root.getInt(KEY_PASS_DAY_COUNT);
    }

    /** 跨日时重置每日次数；返回重置后的当日次数。 */
    public static int ensurePassDay(ItemStack stack, long dayKey) {
        final CompoundTag root = copyRoot(stack);
        if (root.getLong(KEY_PASS_DAY_KEY) != dayKey) {
            root.putLong(KEY_PASS_DAY_KEY, dayKey);
            root.putInt(KEY_PASS_DAY_COUNT, 0);
            commit(stack, root);
            return 0;
        }
        return root.getInt(KEY_PASS_DAY_COUNT);
    }

    public static void incrementPassDay(ItemStack stack, long dayKey) {
        final CompoundTag root = copyRoot(stack);
        if (root.getLong(KEY_PASS_DAY_KEY) != dayKey) {
            root.putLong(KEY_PASS_DAY_KEY, dayKey);
            root.putInt(KEY_PASS_DAY_COUNT, 0);
        }
        root.putInt(KEY_PASS_DAY_COUNT, root.getInt(KEY_PASS_DAY_COUNT) + 1);
        commit(stack, root);
    }

    /** 首次入闸：写入绑定玩家和有效期。 */
    public static void activatePass(ItemStack stack, UUID owner, String ownerName, long from, long until) {
        final CompoundTag root = copyRoot(stack);
        root.putBoolean(KEY_PASS_ACTIVE, true);
        root.putString(KEY_PASS_OWNER, owner == null ? "" : owner.toString());
        root.putString(KEY_PASS_OWNER_NAME, ownerName == null ? "" : ownerName);
        root.putLong(KEY_PASS_FROM, from);
        root.putLong(KEY_PASS_UNTIL, until);
        commit(stack, root);
    }

    /** 已激活但缺绑定玩家的旧数据：补写绑定。 */
    public static void bindPassOwner(ItemStack stack, UUID owner, String ownerName) {
        final CompoundTag root = copyRoot(stack);
        root.putString(KEY_PASS_OWNER, owner == null ? "" : owner.toString());
        root.putString(KEY_PASS_OWNER_NAME, ownerName == null ? "" : ownerName);
        commit(stack, root);
    }

    public static void setPassExited(ItemStack stack) {
        final CompoundTag root = copyRoot(stack);
        root.putString(KEY_STATE, State.EXITED.name());
        commit(stack, root);
    }

    // ===== 改 =====

    public static void setFaceValue(ItemStack stack, long cents) {
        final CompoundTag root = copyRoot(stack);
        root.putLong(KEY_FACE, Math.max(0L, cents));
        commit(stack, root);
    }

    public static void setBalance(ItemStack stack, long cents) {
        final CompoundTag root = copyRoot(stack);
        root.putLong(KEY_BALANCE, Math.max(0L, cents));
        commit(stack, root);
    }

    public static void addTx(ItemStack stack, long time, String type, long amountCents, long balanceAfter) {
        final CompoundTag root = copyRoot(stack);
        addTx(root, time, type, amountCents, balanceAfter);
        commit(stack, root);
    }

    public static int txCount(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? 0 : root.getList(KEY_TX, Tag.TAG_COMPOUND).size();
    }

    public static Tx txAt(ItemStack stack, int index) {
        final CompoundTag root = root(stack);
        if (root == null) {
            return null;
        }
        final ListTag list = root.getList(KEY_TX, Tag.TAG_COMPOUND);
        if (index < 0 || index >= list.size()) {
            return null;
        }
        final CompoundTag entry = list.getCompound(index);
        return new Tx(entry.getLong("t"), entry.getString("type"),
                entry.getLong("amount"), entry.getLong("balance"));
    }

    // ===== 内部 =====

    private static void addTx(CompoundTag root, long time, String type, long amountCents, long balanceAfter) {
        final ListTag previous = root.getList(KEY_TX, Tag.TAG_COMPOUND);
        final ListTag updated = new ListTag();
        final CompoundTag entry = new CompoundTag();
        entry.putLong("t", time);
        entry.putString("type", type);
        entry.putLong("amount", amountCents);
        entry.putLong("balance", balanceAfter);
        updated.add(entry);
        for (int index = 0; index < previous.size() && index < MAX_TX - 1; index++) {
            updated.add(previous.getCompound(index));
        }
        root.put(KEY_TX, updated);
    }

    private static void putSold(CompoundTag root, Station sold, String dimension) {
        if (sold != null) {
            root.putLong(KEY_SOLD_STATION, sold.id);
            root.putString(KEY_SOLD_NAME, stationName(sold));
        }
        root.putString(KEY_SOLD_DIM, dimension == null ? "" : dimension);
    }

    private static String stationName(Station station) {
        return station == null || station.name == null ? "" : station.name;
    }

    private static CompoundTag root(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        final CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ROOT, Tag.TAG_COMPOUND)) {
            return null;
        }
        return tag.getCompound(ROOT);
    }

    private static CompoundTag copyRoot(ItemStack stack) {
        final CompoundTag root = root(stack);
        return root == null ? new CompoundTag() : root.copy();
    }

    private static void commit(ItemStack stack, CompoundTag root) {
        final CompoundTag tag = stack.getOrCreateTag();
        tag.put(ROOT, root);
        stack.setTag(tag);
    }
}
