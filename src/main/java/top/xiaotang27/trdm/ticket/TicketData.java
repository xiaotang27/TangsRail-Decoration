package top.xiaotang27.trdm.ticket;

import mtr.data.Station;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

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

    public static final int MAX_TX = 10;

    private static final String KEY_SERIAL = "serial";
    private static final String KEY_TYPE = "type";
    private static final String KEY_FACE = "face";
    private static final String KEY_BALANCE = "balance";
    private static final String KEY_DEPOSIT = "deposit";
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
    private static final String KEY_TX = "tx";

    private TicketData() {
    }

    // ===== 发行 =====

    public static void issueSingle(ItemStack stack, long faceCents, Station sold, String soldDimension) {
        final CompoundTag root = new CompoundTag();
        root.putString(KEY_SERIAL, UUID.randomUUID().toString());
        root.putString(KEY_TYPE, TYPE_SINGLE);
        root.putLong(KEY_FACE, Math.max(0L, faceCents));
        root.putString(KEY_STATE, State.EMPTY.name());
        root.putLong(KEY_ISSUED_AT, System.currentTimeMillis());
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
        root.putLong(KEY_ISSUED_AT, System.currentTimeMillis());
        root.put(KEY_TX, new ListTag());
        putSold(root, sold, soldDimension);
        commit(stack, root);
    }

    // ===== 车站进出 =====

    /** 写入入站记录并切到 {@link State#ENTERED}。 */
    public static void enter(ItemStack stack, Station station, String dimension) {
        final CompoundTag root = copyRoot(stack);
        applyEntry(root, station, dimension, System.currentTimeMillis());
        commit(stack, root);
    }

    /** 交通卡出站：写 lastExit、扣费记录、切到 {@link State#EXITED}。 */
    public static void markExit(ItemStack stack, Station exit, String dimension, long fareCents) {
        final CompoundTag root = copyRoot(stack);
        final long now = System.currentTimeMillis();
        root.putString(KEY_STATE, State.EXITED.name());
        root.putLong(KEY_LAST_EXIT_STATION, exit.id);
        root.putString(KEY_LAST_EXIT_NAME, stationName(exit));
        root.putString(KEY_LAST_EXIT_DIM, dimension);
        root.putLong(KEY_LAST_EXIT_TIME, now);
        addTx(root, now, "fare", -Math.abs(fareCents), root.getLong(KEY_BALANCE));
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
