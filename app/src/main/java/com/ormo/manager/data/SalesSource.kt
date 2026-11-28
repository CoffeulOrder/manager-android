package com.ormo.manager.data

import com.ormo.manager.domain.DaySales
import java.time.LocalDate

/** 매출 조회. 백엔드를 붙일 때 이 인터페이스를 구현한다. 받지 못하면 [Result.failure]. */
interface SalesSource {
    /** [dates] 순서대로 하루 매출을 돌려준다. */
    suspend fun load(dates: List<LocalDate>, today: LocalDate): Result<List<DaySales>>
}

/**
 * 서버 연동 전: 지난 날짜는 예시 값([MockSales]), 오늘은 빈 값이다(화면이 앱 안의 주문으로 채운다).
 * [failing]은 개발 빌드의 화면 확인용이다.
 */
class InMemorySalesSource(var failing: Boolean = false) : SalesSource {

    override suspend fun load(dates: List<LocalDate>, today: LocalDate): Result<List<DaySales>> =
        if (failing) {
            Result.failure(IllegalStateException("sales unavailable"))
        } else {
            Result.success(dates.map { MockSales.of(it, today) })
        }
}
