package top.xiaotang27.trdm.client;

import net.minecraft.network.chat.Component;

/** 接收服务端交易结果的界面。 */
public interface TicketResultReceiver {

    void showTicketResult(boolean success, Component message);
}
