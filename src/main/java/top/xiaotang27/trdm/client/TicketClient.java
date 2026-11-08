package top.xiaotang27.trdm.client;

import mtr.data.Station;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** 售票机 / 客服终端的客户端入口：解析所在站，然后打开对应界面。 */
public final class TicketClient {

    private TicketClient() {
    }

    public static void openVending(BlockPos pos) {
        final Station station = TrdmControlClient.resolveStation(pos);
        if (station == null) {
            message("gui.trdm.ticket_machine.no_station");
            return;
        }
        Minecraft.getInstance().setScreen(new TicketVendingScreen(station.name, station.id, pos));
    }

    public static void openTerminal(BlockPos pos) {
        final Station station = TrdmControlClient.resolveStation(pos);
        if (station == null) {
            message("gui.trdm.ticket_machine.no_station");
            return;
        }
        Minecraft.getInstance().setScreen(new CustomerServiceTerminalScreen(station.name, station.id, pos));
    }

    private static void message(String key) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
