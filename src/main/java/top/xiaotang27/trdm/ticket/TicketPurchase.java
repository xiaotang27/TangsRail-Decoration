package top.xiaotang27.trdm.ticket;

import mtr.data.Station;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.fare.FareMoney;
import top.xiaotang27.trdm.fare.FareQuote;
import top.xiaotang27.trdm.server.ServerFareRuntime;

/**
 * 服务端交易：买票、买卡、充值、补车费。
 * <p>
 * 所有金额和站点都由服务端重新计算/校验，客户端只发“想买什么”。
 */
public final class TicketPurchase {

    /** 交通卡押金（绿宝石，固定 18，不退）。 */
    public static final int CARD_DEPOSIT_EMERALDS = 18;

    private static final double DEVICE_RANGE_SQR = 8.0 * 8.0;

    /** 交易结果；{@code key} 是翻译键，客户端用 {@code value} 填充 {@code %s}。 */
    public record Result(boolean success, String key, long value) {
    }

    private TicketPurchase() {
    }

    // ===== 买票 =====

    public static Result buySingle(ServerPlayer player, BlockPos pos, long destStationId,
                                   long faceCents, int quantity, boolean byPrice) {
        final ServerLevel level = player.serverLevel();
        final Result invalid = validate(player, pos, Trdm.TICKET_VENDING_MACHINE, Trdm.CUSTOMER_SERVICE_TERMINAL);
        if (invalid != null) {
            return invalid;
        }
        final Station station = ServerFareRuntime.stationAt(level, pos);
        if (station == null) {
            return error("gui.trdm.ticket.error.no_station");
        }
        final long face;
        if (byPrice) {
            face = Math.max(100L, Math.min(99_900L, faceCents));
        } else {
            if (!ServerFareRuntime.hasStation(level, destStationId)) {
                return error("gui.trdm.ticket.error.no_destination");
            }
            final FareQuote quote = ServerFareRuntime.quote(level, station.id, destStationId);
            if (!quote.connected()) {
                return error("gui.trdm.ticket.error.no_destination");
            }
            face = quote.totalCents();
        }
        final int count = Math.max(1, Math.min(64, quantity));
        final long cost = FareMoney.emeralds(face) * count;
        if (!takeEmeralds(player, cost)) {
            return new Result(false, "gui.trdm.ticket.error.no_emerald", cost);
        }
        final String dimension = ServerFareRuntime.dimensionId(level);
        for (int index = 0; index < count; index++) {
            final ItemStack ticket = new ItemStack(Trdm.SINGLE_TICKET_ITEM);
            TicketData.issueSingle(ticket, face, station, dimension);
            give(player, ticket);
        }
        TicketSync.sync(player);
        return new Result(true, "gui.trdm.ticket.buy.single_success", count);
    }

    // ===== 买卡 / 充值 =====

    public static Result buyCard(ServerPlayer player, BlockPos pos, int topUpEmeralds) {
        final ServerLevel level = player.serverLevel();
        final Result invalid = validate(player, pos, Trdm.TICKET_VENDING_MACHINE, Trdm.CUSTOMER_SERVICE_TERMINAL);
        if (invalid != null) {
            return invalid;
        }
        final Station station = ServerFareRuntime.stationAt(level, pos);
        if (station == null) {
            return error("gui.trdm.ticket.error.no_station");
        }
        final int topUp = Math.max(0, Math.min(999, topUpEmeralds));
        final long cost = CARD_DEPOSIT_EMERALDS + topUp;
        if (!takeEmeralds(player, cost)) {
            return new Result(false, "gui.trdm.ticket.error.no_emerald", cost);
        }
        final ItemStack card = new ItemStack(Trdm.TRANSIT_CARD_ITEM);
        TicketData.issueCard(card, topUp * 100L, CARD_DEPOSIT_EMERALDS, station,
                ServerFareRuntime.dimensionId(level));
        give(player, card);
        TicketSync.sync(player);
        return new Result(true, "gui.trdm.ticket.buy.card_success", topUp);
    }

    public static Result topUpCard(ServerPlayer player, BlockPos pos, int amountEmeralds) {
        final ServerLevel level = player.serverLevel();
        final Result invalid = validate(player, pos, Trdm.CUSTOMER_SERVICE_TERMINAL);
        if (invalid != null) {
            return invalid;
        }
        final ItemStack held = player.getMainHandItem();
        if (!held.is(Trdm.TRANSIT_CARD_ITEM)) {
            return error("gui.trdm.ticket.error.no_card");
        }
        // 空白交通卡（例如从物品栏里拿到的）也可以直接充值，充完就是一张可用的卡。
        TicketData.ensureCard(held, null, ServerFareRuntime.dimensionId(level));
        final int amount = Math.max(1, Math.min(999, amountEmeralds));
        if (!takeEmeralds(player, amount)) {
            return new Result(false, "gui.trdm.ticket.error.no_emerald", amount);
        }
        final long added = amount * 100L;
        final long balance = TicketData.balance(held) + added;
        TicketData.setBalance(held, balance);
        TicketData.addTx(held, TrdmClock.now(), "topup", added, balance);
        TicketSync.sync(player);
        return new Result(true, "gui.trdm.ticket.topup.success", amount);
    }

    // ===== 补车费 =====

    public static Result payFare(ServerPlayer player, BlockPos pos) {
        final ServerLevel level = player.serverLevel();
        final Result invalid = validate(player, pos, Trdm.CUSTOMER_SERVICE_TERMINAL);
        if (invalid != null) {
            return invalid;
        }
        final Station station = ServerFareRuntime.stationAt(level, pos);
        if (station == null) {
            return error("gui.trdm.ticket.error.no_station");
        }
        final ItemStack held = player.getMainHandItem();
        if (held.is(Trdm.SINGLE_TICKET_ITEM)) {
            return payFareSingle(level, player, station, held);
        }
        if (held.is(Trdm.TRANSIT_CARD_ITEM)) {
            return payFareCard(level, player, station, held);
        }
        return error("gui.trdm.ticket.error.no_item");
    }

    private static Result payFareSingle(ServerLevel level, ServerPlayer player, Station station, ItemStack ticket) {
        if (!TicketData.hasData(ticket) || !TicketData.TYPE_SINGLE.equals(TicketData.type(ticket))) {
            return error("gui.trdm.ticket.error.invalid_ticket");
        }
        if (TicketData.state(ticket) != TicketData.State.ENTERED || !TicketData.hasEntry(ticket)) {
            return error("gui.trdm.ticket.error.no_entry");
        }
        if (!TicketData.entryDimension(ticket).equals(ServerFareRuntime.dimensionId(level))) {
            return error("gui.trdm.ticket.error.cross_dimension");
        }
        final long fare = ServerFareRuntime.quote(level, TicketData.entryStation(ticket), station.id)
                .totalCents();
        final long face = TicketData.faceValue(ticket);
        final long due = Math.max(0L, fare - face);
        if (due <= 0L) {
            return new Result(true, "gui.trdm.ticket.fare.nothing_due", 0L);
        }
        final long emeralds = FareMoney.emeralds(due);
        if (!takeEmeralds(player, emeralds)) {
            return new Result(false, "gui.trdm.ticket.error.no_emerald", emeralds);
        }
        TicketData.setFaceValue(ticket, face + due);
        TicketSync.sync(player);
        return new Result(true, "gui.trdm.ticket.fare.paid", emeralds);
    }

    private static Result payFareCard(ServerLevel level, ServerPlayer player, Station station, ItemStack card) {
        final String dimension = ServerFareRuntime.dimensionId(level);
        TicketData.ensureCard(card, station, dimension);
        if (TicketData.state(card) != TicketData.State.ENTERED || !TicketData.hasEntry(card)) {
            return error("gui.trdm.ticket.error.no_entry");
        }
        final boolean continuous = TicketData.journeyContinuing(card);
        final String entryDimension = continuous
                ? TicketData.journeyEntryDimension(card) : TicketData.entryDimension(card);
        if (!entryDimension.equals(dimension)) {
            return error("gui.trdm.ticket.error.cross_dimension");
        }
        final long origin = continuous
                ? TicketData.journeyEntryStation(card) : TicketData.entryStation(card);
        final long paid = continuous ? TicketData.journeyPaid(card) : 0L;
        final long fare = ServerFareRuntime.quote(level, origin, station.id).totalCents();
        final long balance = TicketData.balance(card);
        final long due = Math.max(0L, fare - paid - balance);
        if (due <= 0L) {
            return new Result(true, "gui.trdm.ticket.fare.nothing_due", 0L);
        }
        final long emeralds = FareMoney.emeralds(due);
        if (!takeEmeralds(player, emeralds)) {
            return new Result(false, "gui.trdm.ticket.error.no_emerald", emeralds);
        }
        final long updated = balance + due;
        TicketData.setBalance(card, updated);
        TicketData.addTx(card, TrdmClock.now(), "topup", due, updated);
        TicketSync.sync(player);
        return new Result(true, "gui.trdm.ticket.fare.paid", emeralds);
    }

    // ===== 通票 =====

    public static Result buyPass(ServerPlayer player, BlockPos pos, String definitionId, int quantity) {
        final ServerLevel level = player.serverLevel();
        final Result invalid = validate(player, pos, Trdm.TICKET_VENDING_MACHINE, Trdm.CUSTOMER_SERVICE_TERMINAL);
        if (invalid != null) {
            return invalid;
        }
        final Station station = ServerFareRuntime.stationAt(level, pos);
        if (station == null) {
            return error("gui.trdm.ticket.error.no_station");
        }
        final PassRule rule = PassRules.byId(ServerFareRuntime.config().passRules, definitionId);
        if (rule == null) {
            return error("gui.trdm.ticket.error.unknown_pass");
        }
        final int count = Math.max(1, Math.min(64, quantity));
        final long cost = (long) rule.priceEmeralds * count;
        if (!takeEmeralds(player, cost)) {
            return new Result(false, "gui.trdm.ticket.error.no_emerald", cost);
        }
        final String dimension = ServerFareRuntime.dimensionId(level);
        for (int index = 0; index < count; index++) {
            final ItemStack pass = new ItemStack(Trdm.VARIABLE_TICKET_ITEM);
            TicketData.issuePass(pass, rule.id, rule.days, rule.name, station, dimension);
            give(player, pass);
        }
        TicketSync.sync(player);
        return new Result(true, "gui.trdm.ticket.buy.pass_success", count);
    }

    // ===== 补出入记录 / 无票出站票 =====

    public static Result recordEntry(ServerPlayer player, BlockPos pos, long entryStationId) {
        final ServerLevel level = player.serverLevel();
        final Result invalid = validate(player, pos, Trdm.CUSTOMER_SERVICE_TERMINAL);
        if (invalid != null) {
            return invalid;
        }
        final Station station = ServerFareRuntime.stationById(level, entryStationId);
        if (station == null) {
            return error("gui.trdm.ticket.error.no_destination");
        }
        final ItemStack held = player.getMainHandItem();
        final String dimension = ServerFareRuntime.dimensionId(level);
        if (held.is(Trdm.SINGLE_TICKET_ITEM)) {
            if (!TicketData.hasData(held) || !TicketData.TYPE_SINGLE.equals(TicketData.type(held))) {
                return error("gui.trdm.ticket.error.invalid_ticket");
            }
            if (TicketData.state(held) == TicketData.State.ENTERED) {
                return error("gui.trdm.ticket.error.already_entry");
            }
            TicketData.setEntry(held, station, dimension, TrdmClock.now());
            TicketSync.sync(player);
            return new Result(true, "gui.trdm.terminal.record_entry_success", 0L);
        }
        if (held.is(Trdm.TRANSIT_CARD_ITEM)) {
            TicketData.ensureCard(held, station, dimension);
            if (TicketData.state(held) == TicketData.State.ENTERED) {
                return error("gui.trdm.ticket.error.already_entry");
            }
            TicketData.startJourney(held, station, dimension);
            TicketSync.sync(player);
            return new Result(true, "gui.trdm.terminal.record_entry_success", 0L);
        }
        if (held.is(Trdm.VARIABLE_TICKET_ITEM)) {
            if (!TicketData.isPass(held)) {
                return error("gui.trdm.ticket.error.invalid_ticket");
            }
            final long now = TrdmClock.now();
            if (TicketData.passActive(held)) {
                if (PassValidity.expired(TicketData.passUntil(held), now)) {
                    return error("gui.trdm.ticket.error.invalid_ticket");
                }
                final String owner = TicketData.passOwner(held);
                if (owner.isEmpty()) {
                    TicketData.bindPassOwner(held, player.getUUID(), player.getGameProfile().getName());
                } else if (!owner.equals(player.getUUID().toString())) {
                    return error("gui.trdm.ticket.error.invalid_ticket");
                }
                if (TicketData.state(held) == TicketData.State.ENTERED) {
                    return error("gui.trdm.ticket.error.already_entry");
                }
            } else {
                final int days = TicketData.passDays(held);
                if (days <= 0) {
                    return error("gui.trdm.ticket.error.invalid_ticket");
                }
                final long[] window = PassValidity.window(days, now);
                TicketData.activatePass(held, player.getUUID(), player.getGameProfile().getName(),
                        window[0], window[1]);
            }
            TicketData.enter(held, station, dimension);
            TicketSync.sync(player);
            return new Result(true, "gui.trdm.terminal.record_entry_success", 0L);
        }
        return error("gui.trdm.ticket.error.no_item");
    }

    public static Result recordExit(ServerPlayer player, BlockPos pos, long exitStationId) {
        final ServerLevel level = player.serverLevel();
        final Result invalid = validate(player, pos, Trdm.CUSTOMER_SERVICE_TERMINAL);
        if (invalid != null) {
            return invalid;
        }
        final Station station = ServerFareRuntime.stationById(level, exitStationId);
        if (station == null) {
            return error("gui.trdm.ticket.error.no_destination");
        }
        final ItemStack held = player.getMainHandItem();
        if (held.is(Trdm.SINGLE_TICKET_ITEM)) {
            return recordExitSingle(level, player, station, held);
        }
        if (held.is(Trdm.TRANSIT_CARD_ITEM)) {
            return recordExitCard(level, player, station, held);
        }
        if (held.is(Trdm.VARIABLE_TICKET_ITEM)) {
            return recordExitPass(level, player, held);
        }
        return error("gui.trdm.ticket.error.no_item");
    }

    private static Result recordExitPass(ServerLevel level, ServerPlayer player, ItemStack pass) {
        if (!TicketData.isPass(pass)) {
            return error("gui.trdm.ticket.error.invalid_ticket");
        }
        if (!TicketData.passActive(pass)) {
            return error("gui.trdm.ticket.error.no_entry");
        }
        if (PassValidity.expired(TicketData.passUntil(pass), TrdmClock.now())) {
            return error("gui.trdm.ticket.error.invalid_ticket");
        }
        final String owner = TicketData.passOwner(pass);
        if (owner.isEmpty()) {
            TicketData.bindPassOwner(pass, player.getUUID(), player.getGameProfile().getName());
        } else if (!owner.equals(player.getUUID().toString())) {
            return error("gui.trdm.ticket.error.invalid_ticket");
        }
        if (TicketData.state(pass) != TicketData.State.ENTERED || !TicketData.hasEntry(pass)) {
            return error("gui.trdm.ticket.error.no_entry");
        }
        if (!TicketData.entryDimension(pass).equals(ServerFareRuntime.dimensionId(level))) {
            return error("gui.trdm.ticket.error.cross_dimension");
        }
        TicketData.setPassExited(pass);
        TicketSync.sync(player);
        return new Result(true, "gui.trdm.terminal.record_exit_success", 0L);
    }

    private static Result recordExitSingle(ServerLevel level, ServerPlayer player, Station station,
                                           ItemStack ticket) {
        if (!TicketData.hasData(ticket) || !TicketData.TYPE_SINGLE.equals(TicketData.type(ticket))) {
            return error("gui.trdm.ticket.error.invalid_ticket");
        }
        if (TicketData.state(ticket) != TicketData.State.ENTERED || !TicketData.hasEntry(ticket)) {
            return error("gui.trdm.ticket.error.no_entry");
        }
        if (!TicketData.entryDimension(ticket).equals(ServerFareRuntime.dimensionId(level))) {
            return error("gui.trdm.ticket.error.cross_dimension");
        }
        final long fare = ServerFareRuntime.quote(level, TicketData.entryStation(ticket), station.id)
                .totalCents();
        final long due = Math.max(0L, fare - TicketData.faceValue(ticket));
        if (due > 0L) {
            final long emeralds = FareMoney.emeralds(due);
            if (!takeEmeralds(player, emeralds)) {
                return new Result(false, "gui.trdm.ticket.error.no_emerald", emeralds);
            }
        }
        ticket.shrink(1);
        TicketSync.sync(player);
        return new Result(true, "gui.trdm.terminal.record_exit_success", 0L);
    }

    private static Result recordExitCard(ServerLevel level, ServerPlayer player, Station station,
                                         ItemStack card) {
        final String dimension = ServerFareRuntime.dimensionId(level);
        TicketData.ensureCard(card, station, dimension);
        if (TicketData.state(card) != TicketData.State.ENTERED || !TicketData.hasEntry(card)) {
            return error("gui.trdm.ticket.error.no_entry");
        }
        final boolean continuous = TicketData.journeyContinuing(card);
        final String entryDimension = continuous
                ? TicketData.journeyEntryDimension(card) : TicketData.entryDimension(card);
        if (!entryDimension.equals(dimension)) {
            return error("gui.trdm.ticket.error.cross_dimension");
        }
        final long origin = continuous
                ? TicketData.journeyEntryStation(card) : TicketData.entryStation(card);
        final long paid = continuous ? TicketData.journeyPaid(card) : 0L;
        final long fare = ServerFareRuntime.quote(level, origin, station.id).totalCents();
        long balance = TicketData.balance(card);
        final long remainingFare = fare - paid;
        final long due = Math.max(0L, remainingFare - balance);
        if (due > 0L) {
            final long emeralds = FareMoney.emeralds(due);
            if (!takeEmeralds(player, emeralds)) {
                return new Result(false, "gui.trdm.ticket.error.no_emerald", emeralds);
            }
            balance += due;
        }
        final long newBalance = balance - remainingFare;
        TicketData.setBalance(card, newBalance);
        TicketData.markExit(card, station, dimension, fare, remainingFare, TrdmClock.now());
        TicketData.setJourneyPaid(card, fare);
        TicketSync.sync(player);
        return new Result(true, "gui.trdm.terminal.record_exit_success", 0L);
    }

    public static Result buyExitTicket(ServerPlayer player, BlockPos pos) {
        final ServerLevel level = player.serverLevel();
        final Result invalid = validate(player, pos, Trdm.CUSTOMER_SERVICE_TERMINAL);
        if (invalid != null) {
            return invalid;
        }
        final Station station = ServerFareRuntime.stationAt(level, pos);
        if (station == null) {
            return error("gui.trdm.ticket.error.no_station");
        }
        final long cap = ServerFareRuntime.config().maxFareCapCents();
        if (cap <= 0L) {
            return error("gui.trdm.terminal.no_fare_rule");
        }
        final long cost = FareMoney.emeralds(cap);
        if (!takeEmeralds(player, cost)) {
            return new Result(false, "gui.trdm.ticket.error.no_emerald", cost);
        }
        final ItemStack exitTicket = new ItemStack(Trdm.EXIT_TICKET_ITEM);
        TicketData.issueExitTicket(exitTicket, station, ServerFareRuntime.dimensionId(level),
                TrdmClock.now() + 10L * 60L * 1000L);
        give(player, exitTicket);
        TicketSync.sync(player);
        return new Result(true, "gui.trdm.ticket.exit_ticket.success", 0L);
    }

    // ===== 工具 =====

    private static Result validate(ServerPlayer player, BlockPos pos, Block... devices) {
        final ServerLevel level = player.serverLevel();
        boolean matched = false;
        for (final Block device : devices) {
            if (level.getBlockState(pos).is(device)) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            return error("gui.trdm.ticket.error.no_machine");
        }
        if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > DEVICE_RANGE_SQR) {
            return error("gui.trdm.ticket.error.too_far");
        }
        if (!ServerFareRuntime.config().enabled) {
            return error("gui.trdm.ticket.error.disabled");
        }
        return null;
    }

    private static Result error(String key) {
        return new Result(false, key, 0L);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static boolean takeEmeralds(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        final int needed = (int) Math.min(Integer.MAX_VALUE, amount);
        if (countEmeralds(player) < needed) {
            return false;
        }
        int remaining = needed;
        final Inventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize() && remaining > 0; index++) {
            final ItemStack stack = inventory.getItem(index);
            if (stack.is(Items.EMERALD)) {
                final int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                if (stack.isEmpty()) {
                    inventory.setItem(index, ItemStack.EMPTY);
                }
                remaining -= take;
            }
        }
        return remaining == 0;
    }

    private static int countEmeralds(ServerPlayer player) {
        int total = 0;
        final Inventory inventory = player.getInventory();
        for (int index = 0; index < inventory.getContainerSize(); index++) {
            final ItemStack stack = inventory.getItem(index);
            if (stack.is(Items.EMERALD)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
