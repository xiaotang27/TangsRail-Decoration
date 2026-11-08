package top.xiaotang27.trdm.ticket;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** 改完物品 NBT 后把容器同步给客户端，否则手上的车票提示/余额要等下一次背包更新。 */
public final class TicketSync {

    private TicketSync() {
    }

    public static void sync(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
        }
    }
}
