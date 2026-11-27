package com.ormo.managerdemo.data

import com.ormo.managerdemo.domain.Order
import com.ormo.managerdemo.domain.OrderLine
import com.ormo.managerdemo.domain.OrderStatus
import com.ormo.managerdemo.domain.PickupMethod
import com.ormo.managerdemo.domain.PickupMethod.DINE_IN
import com.ormo.managerdemo.domain.PickupMethod.TAKEOUT
import com.ormo.managerdemo.domain.RejectReason

/** 데모용 mock 데이터(Figma 화면의 예시와 같다). 서버 연동 시 "테스트 주문 추가" 버튼과 함께 제거한다. */
object MockOrders {

    private const val SECOND = 1_000L
    private const val MINUTE = 60_000L

    private fun americano(vararg options: String, price: Int = 4_000, qty: Int = 1) =
        OrderLine("아메리카노", price, qty, options.toList())

    private fun latte(vararg options: String, qty: Int = 1) = OrderLine("카페라떼", 5_000, qty, options.toList())

    private fun vanilla(vararg options: String) = OrderLine("바닐라라떼", 5_300, 1, options.toList())

    private fun icedTea(vararg options: String) = OrderLine("아이스티", 3_000, 1, options.toList())

    private fun dolce(vararg options: String) = OrderLine("돌체라떼", 5_500, 1, options.toList())

    /** 시각은 모두 "지금부터 몇 밀리초 전"으로 받는다. */
    private fun order(
        now: Long,
        pickupNo: Int,
        method: PickupMethod,
        name: String,
        lines: List<OrderLine>,
        status: OrderStatus,
        createdAgo: Long,
        acceptedAgo: Long? = null,
        readyAgo: Long? = null,
        closedAgo: Long? = null,
        closedAs: OrderStatus? = if (!status.isOnBoard) status else null,
        refundedAgo: Long? = null,
        reason: RejectReason? = null,
    ): Order {
        val updatedAgo = refundedAgo ?: closedAgo ?: readyAgo ?: acceptedAgo ?: createdAgo
        return Order(
            orderNo = "BW-${114038 + pickupNo}",
            pickupNo = pickupNo,
            pickupMethod = method,
            customerNickname = name,
            lines = lines,
            totalPrice = lines.sumOf { it.amount },
            status = status,
            rejectReason = reason,
            createdAt = now - createdAgo,
            updatedAt = now - updatedAgo,
            acceptedAt = acceptedAgo?.let { now - it },
            readyAt = readyAgo?.let { now - it },
            closedAt = closedAgo?.let { now - it },
            closedAs = closedAs,
            refundedAt = refundedAgo?.let { now - it },
        )
    }

    /** 접수 대기 2 · 제조중 2 · 픽업 대기 2, 나머지는 오늘 내역 */
    fun initialOrders(now: Long): List<Order> = listOf(
        // 접수 대기: 자동 취소까지 6:40 / 9:48
        order(
            now, 30, DINE_IN, "민준",
            listOf(latte("HOT", "오리지널 원두"), vanilla("ICE", "1샷 추가")),
            OrderStatus.REQUESTED, createdAgo = 200 * SECOND,
        ),
        order(
            now, 31, TAKEOUT, "가을",
            listOf(americano("ICE", "오리지널 원두", "사이즈업", price = 4_900, qty = 2)),
            OrderStatus.REQUESTED, createdAgo = 12 * SECOND,
        ),
        // 제조중
        order(
            now, 28, DINE_IN, "지우",
            listOf(americano("HOT", "블렌드 원두"), latte("ICE", "오리지널 원두")),
            OrderStatus.ACCEPTED, createdAgo = 8 * MINUTE, acceptedAgo = 7 * MINUTE,
        ),
        order(
            now, 29, TAKEOUT, "서연",
            listOf(icedTea("ICE", "사이즈업")),
            OrderStatus.ACCEPTED, createdAgo = 6 * MINUTE, acceptedAgo = 5 * MINUTE,
        ),
        // 픽업 대기: 26번은 20분이 지나 [정리하기]가 보인다. (열 순서는 접수 시각 순)
        order(
            now, 27, TAKEOUT, "하린",
            listOf(latte("ICE", "사이즈업")),
            OrderStatus.READY, createdAgo = 40 * MINUTE, acceptedAgo = 39 * MINUTE, readyAgo = 6 * MINUTE,
        ),
        order(
            now, 26, DINE_IN, "도윤",
            listOf(americano("ICE", "오리지널 원두")),
            OrderStatus.READY, createdAgo = 38 * MINUTE, acceptedAgo = 37 * MINUTE, readyAgo = 25 * MINUTE,
        ),
        // 오늘 내역
        order(
            now, 25, DINE_IN, "수아",
            listOf(latte("HOT", "오리지널 원두", qty = 2)),
            OrderStatus.COMPLETED, createdAgo = 45 * MINUTE, acceptedAgo = 44 * MINUTE,
            readyAgo = 40 * MINUTE, closedAgo = 33 * MINUTE,
        ),
        order(
            now, 24, TAKEOUT, "예준",
            listOf(americano("ICE", "오리지널 원두"), latte("ICE", "오리지널 원두")),
            OrderStatus.STORE_CANCELED, createdAgo = 52 * MINUTE, acceptedAgo = 51 * MINUTE,
            closedAgo = 48 * MINUTE,
        ),
        order(
            now, 23, DINE_IN, "지민",
            listOf(dolce("ICE", "오리지널 원두")),
            OrderStatus.COMPLETED, createdAgo = 59 * MINUTE, acceptedAgo = 58 * MINUTE,
            readyAgo = 55 * MINUTE, closedAgo = 50 * MINUTE,
        ),
        order(
            now, 22, TAKEOUT, "서준",
            listOf(vanilla("ICE", "1샷 추가")),
            OrderStatus.AUTO_CANCELED, createdAgo = 69 * MINUTE, closedAgo = 59 * MINUTE,
        ),
        order(
            now, 21, DINE_IN, "유나",
            listOf(americano("HOT", "블렌드 원두")),
            OrderStatus.CUSTOMER_CANCELED, createdAgo = 80 * MINUTE, closedAgo = 79 * MINUTE,
        ),
        order(
            now, 20, TAKEOUT, "시우",
            listOf(icedTea("ICE", "사이즈업")),
            OrderStatus.NO_SHOW, createdAgo = 89 * MINUTE, acceptedAgo = 88 * MINUTE,
            readyAgo = 82 * MINUTE, closedAgo = 50 * MINUTE,
        ),
        order(
            now, 19, DINE_IN, "하준",
            listOf(latte("ICE", "오리지널 원두"), americano("ICE", "블렌드 원두")),
            OrderStatus.REJECTED, createdAgo = 97 * MINUTE, closedAgo = 96 * MINUTE,
            reason = RejectReason.OUT_OF_STOCK,
        ),
        order(
            now, 18, TAKEOUT, "다은",
            listOf(vanilla("ICE", "사이즈업")),
            OrderStatus.REFUNDED, createdAgo = 110 * MINUTE, acceptedAgo = 109 * MINUTE,
            readyAgo = 104 * MINUTE, closedAgo = 100 * MINUTE, closedAs = OrderStatus.COMPLETED,
            refundedAgo = 90 * MINUTE,
        ),
    )

    private val testOrderTemplates: List<Pair<String, List<OrderLine>>> = listOf(
        "현우" to listOf(americano("ICE", "오리지널 원두", qty = 2), vanilla("ICE", "1샷 추가")),
        "채원" to listOf(latte("HOT", "블렌드 원두")),
        "지호" to listOf(dolce("ICE", "사이즈업"), icedTea("ICE")),
    )

    /** "테스트 주문 추가" 버튼용. [seq]가 같으면 같은 주문번호가 되므로 호출할 때마다 증가시킨다. */
    fun testOrder(seq: Int, now: Long): Order {
        val (name, lines) = testOrderTemplates[seq % testOrderTemplates.size]
        return Order(
            orderNo = "BW-${114100 + seq}",
            pickupNo = 32 + seq,
            pickupMethod = if (seq % 2 == 0) TAKEOUT else DINE_IN,
            customerNickname = name,
            lines = lines,
            totalPrice = lines.sumOf { it.amount },
            status = OrderStatus.REQUESTED,
            createdAt = now,
            updatedAt = now,
        )
    }
}
