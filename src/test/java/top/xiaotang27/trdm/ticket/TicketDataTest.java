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
        for (int index = 0; index < TicketData.MAX_TX + 5; index++) {
            TicketData.addTx(card, index, "topup", 100L, 100L * (index + 1));
        }
        assertEquals(TicketData.MAX_TX, TicketData.txCount(card));
        assertEquals(TicketData.MAX_TX + 4L, TicketData.txAt(card, 0).time());
    }

    @Test
    void blankCardCanBeInitialized() {
        final ItemStack card = new ItemStack(Items.PAPER);
        assertFalse(TicketData.hasData(card));
        TicketData.ensureCard(card, null, "minecraft:overworld");
        assertTrue(TicketData.hasData(card));
        assertEquals(TicketData.TYPE_CARD, TicketData.type(card));
        assertEquals(0L, TicketData.balance(card));
        assertEquals(TicketData.State.EMPTY, TicketData.state(card));

        TicketData.setBalance(card, 500L);
        TicketData.ensureCard(card, null, "minecraft:overworld");
        assertEquals(500L, TicketData.balance(card));
    }

    @Test
    void exitTicketKeepsStationAndExpiry() {
        final ItemStack stack = new ItemStack(Items.PAPER);
        TicketData.issueExitTicket(stack, station, "minecraft:overworld", 987654L);
        assertTrue(TicketData.isExitTicket(stack));
        assertEquals(7L, TicketData.exitStation(stack));
        assertEquals("Test Station", TicketData.exitName(stack));
        assertEquals("minecraft:overworld", TicketData.exitDimension(stack));
        assertEquals(987654L, TicketData.exitExpires(stack));
    }

    @Test
    void cardPoliciesRoundTrip() {
        final ItemStack card = new ItemStack(Items.PAPER);
        TicketData.setCardPolicies(card, java.util.List.of("senior", "student"));
        assertEquals(java.util.Set.of("senior", "student"), TicketData.cardPolicies(card));
        TicketData.setCardPolicies(card, java.util.List.of());
        assertTrue(TicketData.cardPolicies(card).isEmpty());
    }

    @Test
    void cardDayAndMonthUsageAccumulate() {
        TrdmClock.setBasis(TimeBasis.SYSTEM);
        final ItemStack card = new ItemStack(Items.PAPER);
        TicketData.addCardUsage(card, 250L);
        TicketData.addCardUsage(card, 300L);
        final TicketData.CardPeriodStats day = TicketData.cardDayStats(card);
        final TicketData.CardPeriodStats month = TicketData.cardMonthStats(card);
        assertEquals(2L, day.rides());
        assertEquals(550L, day.amountCents());
        assertEquals(2L, month.rides());
        assertEquals(550L, month.amountCents());
    }

    @Test
    void cardRuleStatsAccumulatePerRule() {
        final ItemStack card = new ItemStack(Items.PAPER);
        TicketData.addCardRuleTrip(card, "rule_a", 250L, 1200L);
        TicketData.addCardRuleTrip(card, "rule_a", 300L, 800L);
        TicketData.addCardRuleTrip(card, "rule_b", 100L, 300L);

        final top.xiaotang27.trdm.fare.CardRuleStats a =
                TicketData.cardRuleStats(card, "rule_a");
        assertEquals(2, a.rides());
        assertEquals(550L, a.amountCents());
        assertEquals(2000L, a.meters());
        assertEquals(1, TicketData.cardRuleStats(card, "rule_b").rides());
        assertEquals(2, TicketData.cardRuleStats(card).size());
    }

    @Test
    void passLifecycle() {
        final ItemStack pass = new ItemStack(Items.PAPER);
        TicketData.issuePass(pass, "day3", 3, "三日票", station, "minecraft:overworld");
        assertTrue(TicketData.isPass(pass));
        assertEquals("day3", TicketData.passDefinition(pass));
        assertEquals("三日票", TicketData.passName(pass));
        assertEquals(3, TicketData.passDays(pass));
        assertFalse(TicketData.passActive(pass));
        assertEquals(TicketData.State.EMPTY, TicketData.state(pass));

        TicketData.activatePass(pass, java.util.UUID.randomUUID(), "Alice", 1000L, 2000L);
        assertTrue(TicketData.passActive(pass));
        assertEquals(1000L, TicketData.passFrom(pass));
        assertEquals(2000L, TicketData.passUntil(pass));
        assertEquals("Alice", TicketData.passOwnerName(pass));

        TicketData.enter(pass, station, "minecraft:overworld");
        assertEquals(TicketData.State.ENTERED, TicketData.state(pass));
        TicketData.setPassExited(pass);
        assertEquals(TicketData.State.EXITED, TicketData.state(pass));
    }

    @Test
    void passSnapshotsScopeDiscountAndDailyUses() {
        final ItemStack pass = new ItemStack(Items.PAPER);
        TicketData.issuePass(pass, "limited", 2, "限定通票", station, "minecraft:overworld",
                75, 2, false, java.util.List.of("rule_a", "rule_b"));
        assertEquals(75, TicketData.passDiscount(pass));
        assertEquals(2, TicketData.passDailyLimit(pass));
        assertFalse(TicketData.passAllRules(pass));
        assertEquals(java.util.List.of("rule_a", "rule_b"), TicketData.passRuleIds(pass));

        assertEquals(0, TicketData.ensurePassDay(pass, 10L));
        TicketData.incrementPassDay(pass, 10L);
        TicketData.incrementPassDay(pass, 10L);
        assertEquals(2, TicketData.ensurePassDay(pass, 10L));
        assertEquals(0, TicketData.ensurePassDay(pass, 11L));
        assertEquals(0, TicketData.passDayCount(pass));
    }

    @Test
    void journeyTracksPaidTotal() {
        final ItemStack card = new ItemStack(Items.PAPER);
        TicketData.issueCard(card, 5000L, 18, station, "minecraft:overworld");
        TicketData.startJourney(card, station, "minecraft:overworld");
        assertEquals(TicketData.State.ENTERED, TicketData.state(card));
        assertEquals(7L, TicketData.journeyEntryStation(card));
        assertEquals(0L, TicketData.journeyPaid(card));
        assertFalse(TicketData.journeyContinuing(card));

        TicketData.setBalance(card, 4500L);
        TicketData.markExit(card, station, "minecraft:overworld", 500L, 500L, 1000L);
        assertEquals(TicketData.State.EXITED, TicketData.state(card));
        assertEquals(1000L, TicketData.lastExitTime(card));
        assertEquals(500L, TicketData.lastExitPaid(card));
        TicketData.setJourneyPaid(card, 500L);

        TicketData.continueJourney(card);
        assertTrue(TicketData.journeyContinuing(card));
        assertEquals(TicketData.State.ENTERED, TicketData.state(card));
        assertEquals(7L, TicketData.journeyEntryStation(card));
        assertEquals(500L, TicketData.journeyPaid(card));
    }

    @Test
    void blankStackHasNoData() {
        assertFalse(TicketData.hasData(new ItemStack(Items.PAPER)));
        assertFalse(TicketData.hasEntry(new ItemStack(Items.PAPER)));
    }
}
