package top.xiaotang27.trdm.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import top.xiaotang27.trdm.net.TicketNetwork;

/** 票务交易的客户端发送 / 结果接收。 */
public final class TicketClientNetwork {

    private TicketClientNetwork() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TicketNetwork.RESULT,
                (client, handler, buf, responseSender) -> {
                    final boolean success = buf.readBoolean();
                    final String key = buf.readUtf(200);
                    final long value = buf.readLong();
                    client.execute(() -> {
                        final Component message = Component.translatable(key, value);
                        if (client.screen instanceof TicketResultReceiver receiver) {
                            receiver.showTicketResult(success, message);
                        } else if (client.player != null) {
                            client.player.displayClientMessage(message, true);
                        }
                    });
                });
    }

    public static void buySingle(BlockPos pos, long destination, long faceCents, int quantity, boolean byPrice) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeLong(destination);
        buf.writeLong(faceCents);
        buf.writeVarInt(quantity);
        buf.writeBoolean(byPrice);
        ClientPlayNetworking.send(TicketNetwork.BUY_SINGLE, buf);
    }

    public static void buyCard(BlockPos pos, int topUpEmeralds) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeVarInt(topUpEmeralds);
        ClientPlayNetworking.send(TicketNetwork.BUY_CARD, buf);
    }

    public static void topUp(BlockPos pos, int amountEmeralds) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeVarInt(amountEmeralds);
        ClientPlayNetworking.send(TicketNetwork.TOPUP, buf);
    }

    public static void payFare(BlockPos pos) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(TicketNetwork.PAY_FARE, buf);
    }

    public static void recordEntry(BlockPos pos, long entryStationId) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeLong(entryStationId);
        ClientPlayNetworking.send(TicketNetwork.RECORD_ENTRY, buf);
    }

    public static void recordExit(BlockPos pos, long exitStationId) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeLong(exitStationId);
        ClientPlayNetworking.send(TicketNetwork.RECORD_EXIT, buf);
    }

    public static void buyExitTicket(BlockPos pos) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(TicketNetwork.EXIT_TICKET, buf);
    }
}
