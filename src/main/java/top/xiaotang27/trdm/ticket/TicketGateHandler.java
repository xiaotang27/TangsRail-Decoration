package top.xiaotang27.trdm.ticket;

import mtr.data.Station;
import mtr.data.TicketSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.fare.FareConfig;
import top.xiaotang27.trdm.fare.FareMoney;
import top.xiaotang27.trdm.fare.FareQuote;
import top.xiaotang27.trdm.server.ServerFareRuntime;

/**
 * 闸机进出校验：只认主手的 TRDM 单程票 / 交通卡。
 * <p>
 * 返回 {@code null} 表示「不接管」，交给 MTR 原版逻辑（票务系统停用时）；
 * 返回 CLOSED/OPEN 时由 {@code TicketSystemMixin} 直接作为闸机结果。
 */
public final class TicketGateHandler {

    /** 低于这个余额（分）就在进出站提示后面加一句“余额较低”。 */
    private static final long LOW_BALANCE_CENTS = 1000L;

    private TicketGateHandler() {
    }

    public static TicketSystem.EnumTicketBarrierOpen handle(ServerLevel level, BlockPos pos, Player player,
                                                           boolean canEnter, boolean canExit) {
        final FareConfig config = ServerFareRuntime.config();
        if (!config.enabled) {
            return null;
        }
        final Station station = ServerFareRuntime.stationAt(level, pos);
        if (station == null) {
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final boolean entrance = resolveEntrance(player, canEnter, canExit);
        final ItemStack held = player.getMainHandItem();
        final TicketSystem.EnumTicketBarrierOpen result;
        if (held.is(Trdm.SINGLE_TICKET_ITEM)) {
            result = singleTicket(level, player, station, held, entrance);
        } else if (held.is(Trdm.TRANSIT_CARD_ITEM)) {
            result = transitCard(level, player, station, held, entrance);
        } else if (held.is(Trdm.EXIT_TICKET_ITEM)) {
            result = exitTicket(level, player, station, held, entrance);
        } else if (held.is(Trdm.VARIABLE_TICKET_ITEM)) {
            result = pass(level, player, station, held, entrance);
        } else {
            message(player, "gui.trdm.ticket.gate.no_ticket");
            result = TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (result == TicketSystem.EnumTicketBarrierOpen.OPEN) {
            level.playSound(null, pos, mtr.SoundEvents.TICKET_BARRIER, SoundSource.BLOCKS, 1F, 1F);
        }
        TicketSync.sync(player);
        return result;
    }

    /**
     * 双向闸机/检票机（canEnter 且 canExit）按票卡状态自动判方向，和 MTR 原版行为一致；
     * 单向设备直接按 canEnter。
     */
    private static boolean resolveEntrance(Player player, boolean canEnter, boolean canExit) {
        if (canEnter && canExit && player.getMainHandItem().is(Trdm.EXIT_TICKET_ITEM)) {
            return false;
        }
        if (canEnter && canExit) {
            return TicketData.state(player.getMainHandItem()) != TicketData.State.ENTERED;
        }
        return canEnter;
    }

    private static TicketSystem.EnumTicketBarrierOpen singleTicket(ServerLevel level, Player player,
                                                                  Station station, ItemStack ticket,
                                                                  boolean entrance) {
        if (!TicketData.hasData(ticket) || !TicketData.TYPE_SINGLE.equals(TicketData.type(ticket))) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (entrance) {
            if (TicketData.state(ticket) == TicketData.State.ENTERED) {
                message(player, "gui.trdm.ticket.gate.invalid_ticket");
                return TicketSystem.EnumTicketBarrierOpen.CLOSED;
            }
            TicketData.enter(ticket, station, ServerFareRuntime.dimensionId(level));
            message(player, "gui.trdm.ticket.gate.entry_ticket", stationName(station));
            return TicketSystem.EnumTicketBarrierOpen.OPEN;
        }
        if (TicketData.state(ticket) != TicketData.State.ENTERED || !TicketData.hasEntry(ticket)) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (!TicketData.entryDimension(ticket).equals(ServerFareRuntime.dimensionId(level))) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final long fare = fareTo(level, TicketData.entryStation(ticket), station.id);
        if (fare < 0L || TicketData.faceValue(ticket) < fare) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        ticket.shrink(1);
        message(player, "gui.trdm.ticket.gate.exit_ticket", stationName(station));
        return TicketSystem.EnumTicketBarrierOpen.OPEN;
    }

    private static TicketSystem.EnumTicketBarrierOpen transitCard(ServerLevel level, Player player,
                                                                  Station station, ItemStack card,
                                                                  boolean entrance) {
        // 空白交通卡（例如从创造模式物品栏拿的）也能用：先按 0 余额初始化。
        final String dimension = ServerFareRuntime.dimensionId(level);
        TicketData.ensureCard(card, station, dimension);
        final long minimumFare = ServerFareRuntime.config().minimumFareCents();
        if (entrance) {
            if (TicketData.state(card) == TicketData.State.ENTERED) {
                message(player, "gui.trdm.ticket.gate.already_entered");
                return TicketSystem.EnumTicketBarrierOpen.CLOSED;
            }
            final long balance = TicketData.balance(card);
            if (balance < minimumFare) {
                message(player, "gui.trdm.ticket.gate.entry_no_balance");
                return TicketSystem.EnumTicketBarrierOpen.CLOSED;
            }
            final long now = TrdmClock.now();
            final boolean continuous = JourneyRules.canContinue(
                    TicketData.lastExitStation(card), TicketData.lastExitName(card),
                    TicketData.lastExitZone(card), TicketData.lastExitTime(card),
                    TicketData.lastExitDimension(card),
                    station.id, station.name, station.zone, dimension, now);
            if (continuous) {
                TicketData.continueJourney(card);
            } else {
                TicketData.startJourney(card, station, dimension);
            }
            message(player, withLowBalance(Component.translatable("gui.trdm.ticket.gate.entry_card",
                    stationName(station), FareMoney.yuan(balance)), balance));
            return TicketSystem.EnumTicketBarrierOpen.OPEN;
        }
        if (TicketData.state(card) != TicketData.State.ENTERED || !TicketData.hasEntry(card)) {
            message(player, "gui.trdm.ticket.gate.no_entry");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final boolean continuous = TicketData.journeyContinuing(card);
        final String entryDimension = continuous
                ? TicketData.journeyEntryDimension(card) : TicketData.entryDimension(card);
        if (!entryDimension.equals(dimension)) {
            message(player, "gui.trdm.ticket.gate.cross_dimension");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final long origin = continuous
                ? TicketData.journeyEntryStation(card) : TicketData.entryStation(card);
        final long paid = continuous ? TicketData.journeyPaid(card) : 0L;
        final TicketData.CardPeriodStats dayStats = TicketData.cardDayStats(card);
        final TicketData.CardPeriodStats monthStats = TicketData.cardMonthStats(card);
        final FareQuote quote = ServerFareRuntime.quote(level, origin, station.id,
                TicketData.cardRuleStats(card),
                new top.xiaotang27.trdm.fare.QuoteEnvironment(
                        TrdmClock.hour(), TrdmClock.minute(), TrdmClock.weekday(),
                        dayStats.rides(), dayStats.amountCents(),
                        monthStats.rides(), monthStats.amountCents(),
                        TicketData.cardPolicies(card)));
        final long fare = quote.totalCents();
        if (!quote.connected() && quote.maxFareCapCents() <= 0L) {
            message(player, "gui.trdm.ticket.gate.no_fare");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final long remainingFare = fare - paid;
        final long balance = TicketData.balance(card);
        if (balance < remainingFare) {
            message(player, "gui.trdm.ticket.gate.exit_no_balance");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final long newBalance = balance - remainingFare;
        TicketData.setBalance(card, newBalance);
        TicketData.markExit(card, station, dimension, fare, remainingFare, TrdmClock.now());
        TicketData.setJourneyPaid(card, fare);
        TicketData.addCardUsage(card, Math.max(0L, remainingFare));
        for (final FareQuote.FareLeg leg : quote.legs()) {
            TicketData.addCardRuleTrip(card, leg.ruleId(), leg.fareCents(), leg.meters());
        }
        message(player, withLowBalance(Component.translatable("gui.trdm.ticket.gate.exit_card",
                stationName(station), FareMoney.yuan(fare), FareMoney.yuan(newBalance)), newBalance));
        return TicketSystem.EnumTicketBarrierOpen.OPEN;
    }

    /** 无票补票发出的出站票：只能在本站、有效期内出站一次。 */
    private static TicketSystem.EnumTicketBarrierOpen exitTicket(ServerLevel level, Player player,
                                                                 Station station, ItemStack ticket,
                                                                 boolean entrance) {
        if (entrance || !TicketData.isExitTicket(ticket)) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (TicketData.exitStation(ticket) != station.id
                || !TicketData.exitDimension(ticket).equals(ServerFareRuntime.dimensionId(level))) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (TicketData.exitExpires(ticket) < TrdmClock.now()) {
            message(player, "gui.trdm.ticket.gate.exit_ticket_expired");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        ticket.shrink(1);
        message(player, "gui.trdm.ticket.gate.exit_ticket", stationName(station));
        return TicketSystem.EnumTicketBarrierOpen.OPEN;
    }

    /** 通票：首次入闸激活并快照有效期；有效期内免费、不限次，仅限本人。 */
    private static TicketSystem.EnumTicketBarrierOpen pass(ServerLevel level, Player player,
                                                           Station station, ItemStack pass,
                                                           boolean entrance) {
        if (!TicketData.isPass(pass)) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final long now = TrdmClock.now();
        if (!TicketData.passActive(pass)) {
            if (!entrance) {
                message(player, "gui.trdm.ticket.gate.invalid_ticket");
                return TicketSystem.EnumTicketBarrierOpen.CLOSED;
            }
            final int days = TicketData.passDays(pass);
            if (days <= 0) {
                message(player, "gui.trdm.ticket.gate.invalid_ticket");
                return TicketSystem.EnumTicketBarrierOpen.CLOSED;
            }
            final long[] window = PassValidity.window(days, now);
            TicketData.activatePass(pass, player.getUUID(), player.getGameProfile().getName(),
                    window[0], window[1]);
            TicketData.enter(pass, station, ServerFareRuntime.dimensionId(level));
            TicketSync.sync(player);
            final String storedName = TicketData.passName(pass);
            final Component passName = !storedName.isEmpty()
                    ? Component.literal(storedName) : passName(pass);
            message(player, "gui.trdm.ticket.gate.pass_activated",
                    passName, PassValidity.format(window[1]));
            return TicketSystem.EnumTicketBarrierOpen.OPEN;
        }
        if (PassValidity.expired(TicketData.passUntil(pass), now)) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final String owner = TicketData.passOwner(pass);
        if (owner.isEmpty()) {
            TicketData.bindPassOwner(pass, player.getUUID(), player.getGameProfile().getName());
        } else if (!owner.equals(player.getUUID().toString())) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final long dayKey = TrdmClock.dayKey();
        final int usedToday = TicketData.ensurePassDay(pass, dayKey);
        final int dailyLimit = TicketData.passDailyLimit(pass);
        if (dailyLimit > 0 && usedToday >= dailyLimit) {
            message(player, "gui.trdm.ticket.gate.pass_daily_limit", dailyLimit);
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (entrance) {
            if (TicketData.state(pass) == TicketData.State.ENTERED) {
                message(player, "gui.trdm.ticket.gate.invalid_ticket");
                return TicketSystem.EnumTicketBarrierOpen.CLOSED;
            }
            TicketData.enter(pass, station, ServerFareRuntime.dimensionId(level));
            TicketSync.sync(player);
            message(player, "gui.trdm.ticket.gate.entry_ticket", stationName(station));
            return TicketSystem.EnumTicketBarrierOpen.OPEN;
        }
        if (TicketData.state(pass) != TicketData.State.ENTERED) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final FareQuote quote = ServerFareRuntime.quote(level,
                TicketData.entryStation(pass), station.id);
        if (!quote.connected()) {
            message(player, "gui.trdm.ticket.gate.invalid_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (!TicketData.passAllRules(pass)) {
            final java.util.List<String> scope = TicketData.passRuleIds(pass);
            for (final FareQuote.FareLeg leg : quote.legs()) {
                if (!scope.contains(leg.ruleId())) {
                    message(player, "gui.trdm.ticket.gate.pass_not_covered");
                    return TicketSystem.EnumTicketBarrierOpen.CLOSED;
                }
            }
        }
        final int discount = Math.max(0, Math.min(100, TicketData.passDiscount(pass)));
        long baseCents = 0L;
        for (final FareQuote.FareLeg leg : quote.legs()) {
            baseCents += leg.fareCents();
        }
        final long discountedBase = Math.round(baseCents * (100 - discount) / 100.0);
        final long due = Math.max(0L, discountedBase - quote.transferDiscountCents());
        if (due > 0L) {
            final ItemStack card = findCardWithBalance(player, due);
            if (card == null) {
                message(player, "gui.trdm.ticket.gate.pass_need_card", FareMoney.yuan(due));
                return TicketSystem.EnumTicketBarrierOpen.CLOSED;
            }
            final long balance = TicketData.balance(card);
            TicketData.setBalance(card, balance - due);
            TicketData.addTx(card, TrdmClock.now(), "fare", -due, balance - due);
            message(player, "gui.trdm.ticket.gate.pass_paid", FareMoney.yuan(due));
        }
        TicketData.incrementPassDay(pass, dayKey);
        TicketData.setPassExited(pass);
        TicketSync.sync(player);
        message(player, "gui.trdm.ticket.gate.exit_ticket", stationName(station));
        return TicketSystem.EnumTicketBarrierOpen.OPEN;
    }

    private static ItemStack findCardWithBalance(Player player, long due) {
        for (final ItemStack stack : player.getInventory().items) {
            if (stack.is(Trdm.TRANSIT_CARD_ITEM) && TicketData.hasData(stack)
                    && TicketData.balance(stack) >= due) {
                return stack;
            }
        }
        for (final ItemStack stack : player.getInventory().offhand) {
            if (stack.is(Trdm.TRANSIT_CARD_ITEM) && TicketData.hasData(stack)
                    && TicketData.balance(stack) >= due) {
                return stack;
            }
        }
        return null;
    }

    /** 旧通票没有 pass_name 时按定义 ID 回退显示名。 */
    private static Component passName(ItemStack pass) {
        final PassCatalog.Definition definition = PassCatalog.byId(TicketData.passDefinition(pass));
        return definition == null
                ? Component.translatable("item.trdm.variable_ticket")
                : Component.translatable(definition.nameKey());
    }

    /** 入站 → 出站票价。
     *
     * @return -1 表示不连通且没有配置最高票价，拒绝出站
     */
    private static long fareTo(ServerLevel level, long fromStationId, long toStationId) {
        final FareQuote quote = ServerFareRuntime.quote(level, fromStationId, toStationId);
        if (!quote.connected() && quote.maxFareCapCents() <= 0L) {
            return -1L;
        }
        return quote.totalCents();
    }

    private static Component withLowBalance(Component message, long balance) {
        if (balance >= LOW_BALANCE_CENTS) {
            return message;
        }
        return message.copy().append(Component.translatable("gui.trdm.ticket.gate.low_balance_suffix"));
    }

    private static void message(Player player, Component component) {
        player.displayClientMessage(component, true);
    }

    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private static String stationName(Station station) {
        return station == null || station.name == null ? "" : station.name;
    }
}
