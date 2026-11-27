package com.ormo.managerdemo.data

import com.ormo.managerdemo.domain.Order
import com.ormo.managerdemo.domain.OrderStatus
import com.ormo.managerdemo.domain.OrderTransitions
import com.ormo.managerdemo.domain.RejectReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TransitionResult { APPLIED, NOT_FOUND, NOT_ALLOWED, REASON_REQUIRED }

interface OrderRepository {
    val orders: StateFlow<List<Order>>

    fun get(orderNo: String): Order?

    fun add(order: Order)

    /**
     * 주문 상태를 바꾼다. 허용되지 않은 전이는 [TransitionResult.NOT_ALLOWED]로 무시한다.
     * REJECTED로 바꿀 때는 [rejectReason]이 필수다.
     */
    fun transition(
        orderNo: String,
        to: OrderStatus,
        rejectReason: RejectReason? = null,
        soldOutMenus: List<String> = emptyList(),
    ): TransitionResult
}

/** 앱 메모리에만 저장하는 Repository. 상태 전이 검증은 [OrderTransitions]를 따른다. */
class InMemoryOrderRepository(
    initial: List<Order> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
) : OrderRepository {

    private val lock = Any()
    private val state = MutableStateFlow(initial)

    // updatedAt이 항상 단조 증가하도록 보장해서, 같은 밀리초에 처리한 주문도 순서가 유지된다.
    private var lastStamp: Long = initial.maxOfOrNull { it.updatedAt } ?: Long.MIN_VALUE

    override val orders: StateFlow<List<Order>> = state.asStateFlow()

    override fun get(orderNo: String): Order? = state.value.firstOrNull { it.orderNo == orderNo }

    override fun add(order: Order) = synchronized(lock) {
        if (state.value.none { it.orderNo == order.orderNo }) {
            state.value = state.value + order
        }
    }

    override fun transition(
        orderNo: String,
        to: OrderStatus,
        rejectReason: RejectReason?,
        soldOutMenus: List<String>,
    ): TransitionResult = synchronized(lock) {
        val current = state.value.firstOrNull { it.orderNo == orderNo }
            ?: return@synchronized TransitionResult.NOT_FOUND
        if (!OrderTransitions.canTransition(current.status, to)) {
            return@synchronized TransitionResult.NOT_ALLOWED
        }
        if (to == OrderStatus.REJECTED && rejectReason == null) {
            return@synchronized TransitionResult.REASON_REQUIRED
        }
        val stamp = maxOf(clock(), lastStamp + 1)
        lastStamp = stamp
        val closes = !to.isOnBoard && to != OrderStatus.REFUNDED
        val updated = current.copy(
            status = to,
            updatedAt = stamp,
            rejectReason = if (to == OrderStatus.REJECTED) rejectReason else current.rejectReason,
            soldOutMenus = if (to == OrderStatus.REJECTED) soldOutMenus else current.soldOutMenus,
            acceptedAt = if (to == OrderStatus.ACCEPTED) stamp else current.acceptedAt,
            readyAt = if (to == OrderStatus.READY) stamp else current.readyAt,
            closedAt = if (closes) stamp else current.closedAt,
            closedAs = if (closes) to else current.closedAs,
            refundedAt = if (to == OrderStatus.REFUNDED) stamp else current.refundedAt,
        )
        state.value = state.value.map { if (it.orderNo == orderNo) updated else it }
        TransitionResult.APPLIED
    }
}
