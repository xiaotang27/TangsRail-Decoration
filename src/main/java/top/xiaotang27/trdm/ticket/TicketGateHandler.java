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
        } else if (held.is(Trdm.VARIABLE_TICKET_ITEM)) {
            message(player, "gui.trdm.ticket.gate.pass_todo");
            result = TicketSystem.EnumTicketBarrierOpen.CLOSED;
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
        if (canEnter && canExit) {
            return TicketData.state(player.getMainHandItem()) != TicketData.State.ENTERED;
        }
        return canEnter;
    }

    private static TicketSystem.EnumTicketBarrierOpen singleTicket(ServerLevel level, Player player,
                                                                  Station station, ItemStack ticket,
                                                                  boolean entrance) {
        if (!TicketData.hasData(ticket) || !TicketData.TYPE_SINGLE.equals(TicketData.type(ticket))) {
            message(player, "gui.trdm.ticket.gate.bad_ticket");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (entrance) {
            if (TicketData.state(ticket) == TicketData.State.ENTERED) {
                message(player, "gui.trdm.ticket.gate.already_entered");
                return TicketSystem.EnumTicketBarrierOpen.CLOSED;
            }
            TicketData.enter(ticket, station, ServerFareRuntime.dimensionId(level));
            message(player, "gui.trdm.ticket.gate.entry_ticket", stationName(station));
            return TicketSystem.EnumTicketBarrierOpen.OPEN;
        }
        if (TicketData.state(ticket) != TicketData.State.ENTERED || !TicketData.hasEntry(ticket)) {
            message(player, "gui.trdm.ticket.gate.no_entry");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (!TicketData.entryDimension(ticket).equals(ServerFareRuntime.dimensionId(level))) {
            message(player, "gui.trdm.ticket.gate.cross_dimension");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final long fare = fareTo(level, TicketData.entryStation(ticket), station.id);
        if (fare < 0L) {
            message(player, "gui.trdm.ticket.gate.no_fare");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (TicketData.faceValue(ticket) < fare) {
            message(player, "gui.trdm.ticket.gate.underpaid");
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
        TicketData.ensureCard(card, station, ServerFareRuntime.dimensionId(level));
        if (entrance) {
            if (TicketData.state(card) == TicketData.State.ENTERED) {
                message(player, "gui.trdm.ticket.gate.already_entered");
                return TicketSystem.EnumTicketBarrierOpen.CLOSED;
            }
            TicketData.enter(card, station, ServerFareRuntime.dimensionId(level));
            message(player, "gui.trdm.ticket.gate.entry_card", stationName(station),
                    FareMoney.yuan(TicketData.balance(card)));
            return TicketSystem.EnumTicketBarrierOpen.OPEN;
        }
        if (TicketData.state(card) != TicketData.State.ENTERED || !TicketData.hasEntry(card)) {
            message(player, "gui.trdm.ticket.gate.no_entry");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        if (!TicketData.entryDimension(card).equals(ServerFareRuntime.dimensionId(level))) {
            message(player, "gui.trdm.ticket.gate.cross_dimension");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final long fare = fareTo(level, TicketData.entryStation(card), station.id);
        if (fare < 0L) {
            message(player, "gui.trdm.ticket.gate.no_fare");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        final long balance = TicketData.balance(card);
        if (balance < fare) {
            message(player, "gui.trdm.ticket.gate.low_balance");
            return TicketSystem.EnumTicketBarrierOpen.CLOSED;
        }
        TicketData.setBalance(card, balance - fare);
        TicketData.markExit(card, station, ServerFareRuntime.dimensionId(level), fare);
        message(player, "gui.trdm.ticket.gate.exit_card", stationName(station),
                FareMoney.yuan(fare), FareMoney.yuan(TicketData.balance(card)));
        return TicketSystem.EnumTicketBarrierOpen.OPEN;
    }

    /**
     * 入站 → 出站票价。
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

    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private static String stationName(Station station) {
        return station == null || station.name == null ? "" : station.name;
    }
}
