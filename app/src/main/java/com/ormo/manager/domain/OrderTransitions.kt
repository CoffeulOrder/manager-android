package com.ormo.manager.domain

import com.ormo.manager.domain.OrderStatus.ACCEPTED
import com.ormo.manager.domain.OrderStatus.AUTO_CANCELED
import com.ormo.manager.domain.OrderStatus.COMPLETED
import com.ormo.manager.domain.OrderStatus.CUSTOMER_CANCELED
import com.ormo.manager.domain.OrderStatus.NO_SHOW
import com.ormo.manager.domain.OrderStatus.READY
import com.ormo.manager.domain.OrderStatus.REFUNDED
import com.ormo.manager.domain.OrderStatus.REJECTED
import com.ormo.manager.domain.OrderStatus.REQUESTED
import com.ormo.manager.domain.OrderStatus.STORE_CANCELED

/** 접수 대기로 이 시간이 지나면 자동 취소(전액 환불)한다. */
const val AUTO_CANCEL_MILLIS = 10 * 60_000L

/** 픽업 대기로 이 시간이 지나면 [정리하기]가 나타난다. */
const val NO_SHOW_MILLIS = 20 * 60_000L

/**
 * 상태 전이 표. 허용되는 전이는 여기 한 곳에서만 정의한다.
 *
 *  REQUESTED(접수 대기) -> ACCEPTED | REJECTED | AUTO_CANCELED | CUSTOMER_CANCELED
 *  ACCEPTED(제조중)     -> READY
 *  READY(픽업 대기)     -> COMPLETED | NO_SHOW
 *  COMPLETED, NO_SHOW   -> REFUNDED
 *  나머지는 종료 상태
 */
object OrderTransitions {
    private val allowed: Map<OrderStatus, Set<OrderStatus>> = mapOf(
        REQUESTED to setOf(ACCEPTED, REJECTED, AUTO_CANCELED, CUSTOMER_CANCELED),
        ACCEPTED to setOf(READY),
        READY to setOf(COMPLETED, NO_SHOW),
        COMPLETED to setOf(REFUNDED),
        NO_SHOW to setOf(REFUNDED),
        REJECTED to emptySet(),
        AUTO_CANCELED to emptySet(),
        CUSTOMER_CANCELED to emptySet(),
        STORE_CANCELED to emptySet(),
        REFUNDED to emptySet(),
    )

    fun canTransition(from: OrderStatus, to: OrderStatus): Boolean =
        to in allowed.getValue(from)
}

/** 오늘 내역의 필터 칩 */
enum class HistoryFilter {
    ALL, COMPLETED, CANCELED, NO_SHOW;

    fun matches(status: OrderStatus): Boolean = when (this) {
        ALL -> true
        COMPLETED -> status == OrderStatus.COMPLETED
        CANCELED -> status == REJECTED || status == AUTO_CANCELED ||
            status == CUSTOMER_CANCELED || status == STORE_CANCELED
        NO_SHOW -> status == OrderStatus.NO_SHOW
    }
}

/** 필터 칩과 픽업 번호 검색을 함께 적용한다. */
fun filterHistory(orders: List<Order>, filter: HistoryFilter, numberQuery: String): List<Order> =
    orders.filter {
        filter.matches(it.status) && (numberQuery.isEmpty() || numberQuery in it.pickupNo.toString())
    }
