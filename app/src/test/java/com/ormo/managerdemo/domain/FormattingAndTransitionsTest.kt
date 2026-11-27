package com.ormo.managerdemo.domain

import com.ormo.managerdemo.data.MockOrders
import com.ormo.managerdemo.domain.OrderStatus.ACCEPTED
import com.ormo.managerdemo.domain.OrderStatus.AUTO_CANCELED
import com.ormo.managerdemo.domain.OrderStatus.COMPLETED
import com.ormo.managerdemo.domain.OrderStatus.CUSTOMER_CANCELED
import com.ormo.managerdemo.domain.OrderStatus.NO_SHOW
import com.ormo.managerdemo.domain.OrderStatus.READY
import com.ormo.managerdemo.domain.OrderStatus.REFUNDED
import com.ormo.managerdemo.domain.OrderStatus.REJECTED
import com.ormo.managerdemo.domain.OrderStatus.REQUESTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class FormattingAndTransitionsTest {

    @Test
    fun formatWon_groupsThousandsAndAppendsWon() {
        assertEquals("0원", formatWon(0))
        assertEquals("500원", formatWon(500))
        assertEquals("4,000원", formatWon(4_000))
        assertEquals("10,500원", formatWon(10_500))
        assertEquals("1,234,567원", formatWon(1_234_567))
        assertEquals("-1,500원", formatWon(-1_500))
    }

    @Test
    fun formatClock_is24Hour() {
        val seoul = ZoneId.of("Asia/Seoul")
        assertEquals("09:00", formatClock(0L, seoul))
        assertEquals("13:05", formatClock((4 * 60 + 5) * 60_000L, seoul))
    }

    @Test
    fun formatCountdown_roundsSecondsUp() {
        assertEquals("10:00", formatCountdown(600_000))
        assertEquals("6:40", formatCountdown(400_000))
        assertEquals("6:40", formatCountdown(399_001))
        assertEquals("0:01", formatCountdown(1))
        assertEquals("0:00", formatCountdown(0))
        assertEquals("0:00", formatCountdown(-5_000))
    }

    @Test
    fun elapsedMinutes_roundsDownAndNeverNegative() {
        val t = 1_000_000L
        assertEquals(0, elapsedMinutes(t, t + 59_999))
        assertEquals(1, elapsedMinutes(t, t + 60_000))
        assertEquals(0, elapsedMinutes(t, t - 5_000))
    }

    @Test
    fun transitionTable_allowsOnlyDefinedTransitions() {
        val allowed = setOf(
            REQUESTED to ACCEPTED,
            REQUESTED to REJECTED,
            REQUESTED to AUTO_CANCELED,
            REQUESTED to CUSTOMER_CANCELED,
            ACCEPTED to READY,
            READY to COMPLETED,
            READY to NO_SHOW,
            COMPLETED to REFUNDED,
            NO_SHOW to REFUNDED,
        )
        for (from in OrderStatus.entries) {
            for (to in OrderStatus.entries) {
                assertEquals("$from -> $to", (from to to) in allowed, OrderTransitions.canTransition(from, to))
            }
        }
    }

    @Test
    fun mockOrders_matchFigmaBoard_andTotals() {
        val orders = MockOrders.initialOrders(now = 1_000_000_000L)
        fun numbers(status: OrderStatus) =
            orders.filter { it.status == status }.sortedBy { it.createdAt }.map { it.pickupNo }

        assertEquals(listOf(30, 31), numbers(REQUESTED))
        assertEquals(listOf(28, 29), numbers(ACCEPTED))
        assertEquals(listOf(27, 26), numbers(READY))
        assertEquals(9_800, orders.first { it.pickupNo == 31 }.totalPrice)
        assertTrue(orders.all { it.totalPrice == it.lines.sumOf { l -> l.price * l.qty } })
        assertEquals(orders.size, orders.map { it.orderNo }.toSet().size)
        assertEquals(orders.size, orders.map { it.pickupNo }.toSet().size)
        assertTrue(orders.filter { !it.status.isOnBoard }.all { it.closedAt != null && it.closedAs != null })
    }

    @Test
    fun historyFilter_andNumberSearch() {
        val history = MockOrders.initialOrders(now = 1_000_000_000L).filter { !it.status.isOnBoard }
        fun numbers(filter: HistoryFilter, query: String = "") =
            filterHistory(history, filter, query).map { it.pickupNo }.sorted()

        assertEquals(history.size, numbers(HistoryFilter.ALL).size)
        assertEquals(listOf(23, 25), numbers(HistoryFilter.COMPLETED))
        assertEquals(listOf(19, 21, 22, 24), numbers(HistoryFilter.CANCELED))
        assertEquals(listOf(20), numbers(HistoryFilter.NO_SHOW))
        assertEquals(listOf(25), numbers(HistoryFilter.ALL, "25"))
        assertEquals(emptyList<Int>(), numbers(HistoryFilter.ALL, "45"))
        assertEquals(emptyList<Int>(), numbers(HistoryFilter.NO_SHOW, "25"))
    }
}
