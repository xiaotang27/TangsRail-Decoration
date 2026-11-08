package top.xiaotang27.trdm.ticket;

import mtr.data.Station;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 车票/交通卡 NBT 状态机。需要一个最小的注册表环境。 */
class TicketDataTest {

    private static Station station;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        station = new Station(7L);
        station.name = "Test Station";
        station.zone = 1;
    }

    @Test
    void singleTicketLifecycle() {
        final ItemStack stack = new ItemStack(Items.PAPER);
        TicketData.issueSingle(stack, 250L, station, "minecraft:overworld");
        assertEquals(TicketData.State.EMPTY, TicketData.state(stack));
        assertEquals(250L, TicketData.faceValue(stack));
        assertEquals("Test Station", TicketData.soldName(stack));

        TicketData.enter(stack, station, "minecraft:overworld");
        assertEquals(TicketData.State.ENTERED, TicketData.state(stack));
        assertTrue(TicketData.hasEntry(stack));
        assertEquals(7L, TicketData.entryStation(stack));
        assertEquals("Test Station", TicketData.entryName(stack));
        assertEquals("minecraft:overworld", TicketData.entryDimension(stack));

        TicketData.setFaceValue(stack, 300L);
        assertEquals(300L, TicketData.faceValue(stack));
    }

    @Test
    void cardTransactionsAndBalance() {
        final ItemStack card = new ItemStack(Items.PAPER);
        TicketData.issueCard(card, 1000L, 18, station, "minecraft:overworld");
        assertEquals(1000L, TicketData.balance(card));
        assertEquals(18, TicketData.deposit(card));
        assertEquals(TicketData.State.EMPTY, TicketData.state(card));

        TicketData.setBalance(card, 500L);
        TicketData.addTx(card, 123L, "fare", -500L, 500L);
        assertEquals(500L, TicketData.balance(card));
        assertEquals(1, TicketData.txCount(card));

        final TicketData.Tx tx = TicketData.txAt(card, 0);
        assertEquals(123L, tx.time());
        assertEquals("fare", tx.type());
        assertEquals(-500L, tx.amountCents());
        assertEquals(500L, tx.balanceAfter());
    }

    @Test
    void transactionListIsCapped() {
        final ItemStack card = new ItemStack(Items.PAPER);
        TicketData.issueCard(card, 0L, 18, station, "minecraft:overworld");
        for (int index = 0; index < 15; index++) {
            TicketData.addTx(card, index, "topup", 100L, 100L * (index + 1));
        }
        assertEquals(TicketData.MAX_TX, TicketData.txCount(card));
        assertEquals(14L, TicketData.txAt(card, 0).time());
    }

    @Test
    void blankStackHasNoData() {
        assertFalse(TicketData.hasData(new ItemStack(Items.PAPER)));
        assertFalse(TicketData.hasEntry(new ItemStack(Items.PAPER)));
    }
}
