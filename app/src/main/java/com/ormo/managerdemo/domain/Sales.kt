package com.ormo.managerdemo.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

data class MenuSale(val name: String, val qty: Int, val amount: Int)

/** 하루(영업일) 매출. 금액은 결제 기준이고 환불은 따로 센다(실 매출 = 매출 − 환불). */
data class DaySales(
    val date: LocalDate,
    /** 시(0~23) -> 그 시간의 매출 */
    val hourly: Map<Int, Int> = emptyMap(),
    val orderCount: Int = 0,
    val refundCount: Int = 0,
    val refundAmount: Int = 0,
    val menus: List<MenuSale> = emptyList(),
) {
    val total: Int get() = hourly.values.sum()
}

data class SalesSummary(val total: Int, val orderCount: Int, val refundCount: Int, val refundAmount: Int)

fun List<DaySales>.summary() = SalesSummary(
    total = sumOf { it.total },
    orderCount = sumOf { it.orderCount },
    refundCount = sumOf { it.refundCount },
    refundAmount = sumOf { it.refundAmount },
)

/** 많이 팔린 메뉴: 잔 수가 많은 순, 같으면 금액 순 */
fun List<DaySales>.topMenus(limit: Int = 5): List<MenuSale> =
    flatMap { it.menus }
        .groupBy { it.name }
        .map { (name, sales) -> MenuSale(name, sales.sumOf { it.qty }, sales.sumOf { it.amount }) }
        .sortedWith(compareByDescending<MenuSale> { it.qty }.thenByDescending { it.amount })
        .take(limit)

/** 월요일부터 일요일까지 */
fun weekOf(date: LocalDate): List<LocalDate> {
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L..6L).map { monday.plusDays(it) }
}

fun daysOf(month: YearMonth): List<LocalDate> = (1..month.lengthOfMonth()).map { month.atDay(it) }

/** 결제되어 매출로 잡히는 상태(수락한 주문). 수락 전 거절·취소는 매출이 아니다. */
private val paidStatuses = setOf(
    OrderStatus.ACCEPTED,
    OrderStatus.READY,
    OrderStatus.COMPLETED,
    OrderStatus.NO_SHOW,
    OrderStatus.REFUNDED,
)

/**
 * 오늘 영업일의 매출은 앱에 있는 주문에서 계산한다. 환불하면 환불 칸에 바로 반영된다.
 *
 * @param hourOf 접수 시각(epoch millis)의 시(0~23)
 */
fun todaySales(today: LocalDate, orders: List<Order>, hourOf: (Long) -> Int): DaySales {
    val paid = orders.filter { it.status in paidStatuses }
    val refunded = paid.filter { it.status == OrderStatus.REFUNDED }
    return DaySales(
        date = today,
        hourly = paid.groupBy { hourOf(it.createdAt) }.mapValues { (_, list) -> list.sumOf { it.totalPrice } },
        orderCount = paid.size,
        refundCount = refunded.size,
        refundAmount = refunded.sumOf { it.totalPrice },
        menus = paid.flatMap { it.lines }
            .groupBy { it.name }
            .map { (name, lines) -> MenuSale(name, lines.sumOf { it.qty }, lines.sumOf { it.amount }) },
    )
}
