package top.xiaotang27.trdm.net;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import top.xiaotang27.trdm.Trdm;
import top.xiaotang27.trdm.ticket.TicketPurchase;

/** 票务交易网络包：C2S 四个请求 + S2C 一个结果。 */
public final class TicketNetwork {

    /** C2S：BlockPos + 目的站 id + 票面额 + 数量 + 是否按价格购买。 */
    public static final ResourceLocation BUY_SINGLE = Trdm.id("ticket_buy_single");

    /** C2S：BlockPos + 充值金额（绿宝石）。 */
    public static final ResourceLocation BUY_CARD = Trdm.id("ticket_buy_card");

    /** C2S：BlockPos + 充值金额（绿宝石），要求主手持交通卡。 */
    public static final ResourceLocation TOPUP = Trdm.id("ticket_topup");

    /** C2S：BlockPos + 通票定义 ID + 数量。 */
    public static final ResourceLocation BUY_PASS = Trdm.id("ticket_buy_pass");

    /** C2S：BlockPos（客服终端），按主手车票/交通卡补车费。 */
    public static final ResourceLocation PAY_FARE = Trdm.id("ticket_pay_fare");

    /** C2S：BlockPos + 入闸站 id，补写主手票卡的入站记录。 */
    public static final ResourceLocation RECORD_ENTRY = Trdm.id("ticket_record_entry");

    /** C2S：BlockPos + 出闸站 id，补写主手票卡的出站记录。 */
    public static final ResourceLocation RECORD_EXIT = Trdm.id("ticket_record_exit");

    /** C2S：BlockPos（客服终端），无票补票并领取出站票。 */
    public static final ResourceLocation EXIT_TICKET = Trdm.id("ticket_exit_ticket");

    /** S2C：成功标志 + 翻译键 + 数值。 */
    public static final ResourceLocation RESULT = Trdm.id("ticket_result");

    private TicketNetwork() {
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(BUY_SINGLE, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final long destination = buf.readLong();
            final long faceCents = buf.readLong();
            final int quantity = buf.readVarInt();
            final boolean byPrice = buf.readBoolean();
            server.execute(() -> send(player,
                    TicketPurchase.buySingle(player, pos, destination, faceCents, quantity, byPrice)));
        });
        ServerPlayNetworking.registerGlobalReceiver(BUY_CARD, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final int topUp = buf.readVarInt();
            server.execute(() -> send(player, TicketPurchase.buyCard(player, pos, topUp)));
        });
        ServerPlayNetworking.registerGlobalReceiver(TOPUP, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final int amount = buf.readVarInt();
            server.execute(() -> send(player, TicketPurchase.topUpCard(player, pos, amount)));
        });
        ServerPlayNetworking.registerGlobalReceiver(BUY_PASS, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final String definitionId = buf.readUtf(64);
            final int quantity = buf.readVarInt();
            server.execute(() -> send(player, TicketPurchase.buyPass(player, pos, definitionId, quantity)));
        });
        ServerPlayNetworking.registerGlobalReceiver(PAY_FARE, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            server.execute(() -> send(player, TicketPurchase.payFare(player, pos)));
        });
        ServerPlayNetworking.registerGlobalReceiver(RECORD_ENTRY, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final long station = buf.readLong();
            server.execute(() -> send(player, TicketPurchase.recordEntry(player, pos, station)));
        });
        ServerPlayNetworking.registerGlobalReceiver(RECORD_EXIT, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            final long station = buf.readLong();
            server.execute(() -> send(player, TicketPurchase.recordExit(player, pos, station)));
        });
        ServerPlayNetworking.registerGlobalReceiver(EXIT_TICKET, (server, player, handler, buf, responseSender) -> {
            final BlockPos pos = buf.readBlockPos();
            server.execute(() -> send(player, TicketPurchase.buyExitTicket(player, pos)));
        });
    }

    public static void send(ServerPlayer player, TicketPurchase.Result result) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(result.success());
        buf.writeUtf(result.key(), 200);
        buf.writeLong(result.value());
        ServerPlayNetworking.send(player, RESULT, buf);
    }
}
