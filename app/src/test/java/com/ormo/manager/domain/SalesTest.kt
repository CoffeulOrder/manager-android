package com.ormo.manager.domain

import com.ormo.manager.data.MockSales
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SalesTest {

    private val today = LocalDate.of(2026, 11, 27)

    private fun order(status: OrderStatus, total: Int, createdAt: Long = 9L) = Order(
        orderNo = "$status$total",
        pickupNo = 1,
        pickupMethod = PickupMethod.TAKEOUT,
        customerNickname = "닉네임",
        lines = listOf(OrderLine("아메리카노", total, 1)),
        totalPrice = total,
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun todaySales_countsAcceptedOrders_andKeepsRefundsSeparate() {
        val day = todaySales(
            today,
            listOf(
                order(OrderStatus.COMPLETED, 4_000, 9),
                order(OrderStatus.ACCEPTED, 5_000, 12),
                order(OrderStatus.REFUNDED, 3_000, 12),
                order(OrderStatus.REQUESTED, 10_000),
                order(OrderStatus.REJECTED, 20_000),
            ),
            dateOf = { today },
            hourOf = { it.toInt() },
        )

        assertEquals(12_000, day.total)
        assertEquals(mapOf(9 to 4_000, 12 to 8_000), day.hourly)
        assertEquals(3, day.orderCount)
        assertEquals(1, day.refundCount)
        assertEquals(3_000, day.refundAmount)
    }

    @Test
    fun todaySales_leavesOutOrdersFromEarlierDays() {
        val day = todaySales(
            today,
            listOf(order(OrderStatus.COMPLETED, 4_000, 9), order(OrderStatus.COMPLETED, 5_000, 12)),
            // 접수 시각 9는 어제, 12는 오늘로 본다.
            dateOf = { if (it == 9L) today.minusDays(1) else today },
            hourOf = { it.toInt() },
        )

        assertEquals(5_000, day.total)
        assertEquals(1, day.orderCount)
    }

    @Test
    fun week_startsOnMonday_andMonthHasEveryDay() {
        val week = weekOf(today)
        assertEquals(LocalDate.of(2026, 11, 23), week.first())
        assertEquals(LocalDate.of(2026, 11, 29), week.last())
        assertEquals(30, daysOf(YearMonth.of(2026, 11)).size)
    }

    @Test
    fun mockSales_isStable_hourlyMatchesMenus_andSundayOrFutureIsEmpty() {
        val friday = LocalDate.of(2026, 11, 20)
        val day = MockSales.of(friday, today)
        assertEquals(day, MockSales.of(friday, today))
        assertEquals(day.menus.sumOf { it.amount }, day.total)
        assertTrue(day.hourly.values.all { it >= 0 })

        assertEquals(0, MockSales.of(LocalDate.of(2026, 11, 22), today).total)
        assertEquals(0, MockSales.of(today.plusDays(1), today).total)
    }

    @Test
    fun topMenus_addsUpAcrossDays_andSortsByCups() {
        val days = listOf(
            DaySales(today, menus = listOf(MenuSale("A", 1, 100), MenuSale("B", 3, 300))),
            DaySales(today, menus = listOf(MenuSale("A", 5, 500))),
        )
        assertEquals(listOf(MenuSale("A", 6, 600), MenuSale("B", 3, 300)), days.topMenus())
    }
}
