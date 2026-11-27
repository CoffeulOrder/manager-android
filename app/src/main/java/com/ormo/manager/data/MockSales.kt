package com.ormo.manager.data

import com.ormo.manager.domain.DaySales
import com.ormo.manager.domain.MenuSale
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.random.Random

/**
 * 지난 날짜의 예시 매출. 날짜를 씨앗으로 만들어 같은 날은 항상 같은 값이다.
 * 일요일과 120일보다 오래된 날은 주문이 없다. 서버 연동 시 제거한다.
 */
object MockSales {

    private const val HISTORY_DAYS = 120L

    /** 이름, 단가, 평일 평균 잔 수 */
    private val menus = listOf(
        Triple("아메리카노", 4_000, 38),
        Triple("카페라떼", 4_500, 24),
        Triple("바닐라라떼", 5_000, 15),
        Triple("아이스티", 3_000, 11),
        Triple("콜드브루", 4_500, 8),
        Triple("돌체라떼", 5_000, 5),
    )

    /** 8시~18시의 상대적인 손님 수 */
    private val hourWeights = listOf(4, 8, 7, 10, 15, 13, 6, 5, 6, 4, 2)

    fun of(date: LocalDate, today: LocalDate): DaySales {
        val past = date.isBefore(today) && !date.isBefore(today.minusDays(HISTORY_DAYS))
        if (!past || date.dayOfWeek == DayOfWeek.SUNDAY) return DaySales(date)

        val random = Random(date.toEpochDay())
        // 토요일은 평일의 절반쯤
        val scale = if (date.dayOfWeek == DayOfWeek.SATURDAY) 0.5 else 1.0
        val sold = menus.map { (name, price, average) ->
            val qty = (average * scale * (0.7 + random.nextDouble() * 0.6)).toInt().coerceAtLeast(1)
            MenuSale(name, qty, qty * price)
        }

        val total = sold.sumOf { it.amount }
        val weights = hourWeights.map { it * (0.8 + random.nextDouble() * 0.4) }
        val hourly = weights.dropLast(1)
            .map { (total * it / weights.sum()).toInt() / 500 * 500 }
            .let { it + (total - it.sum()) }

        val refunds = random.nextInt(0, 3)
        return DaySales(
            date = date,
            hourly = hourly.mapIndexed { index, amount -> 8 + index to amount }.toMap(),
            orderCount = (sold.sumOf { it.qty } * 0.9).toInt(),
            refundCount = refunds,
            refundAmount = refunds * 4_500,
            menus = sold,
        )
    }
}
