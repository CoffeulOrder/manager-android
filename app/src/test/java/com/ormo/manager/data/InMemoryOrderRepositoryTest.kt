package com.ormo.manager.data

import com.ormo.manager.domain.Order
import com.ormo.manager.domain.OrderLine
import com.ormo.manager.domain.OrderStatus
import com.ormo.manager.domain.PickupMethod
import com.ormo.manager.domain.RejectReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryOrderRepositoryTest {

    private fun order(no: String, status: OrderStatus = OrderStatus.REQUESTED) = Order(
        orderNo = no,
        pickupNo = 1,
        pickupMethod = PickupMethod.DINE_IN,
        customerNickname = "닉네임",
        lines = listOf(OrderLine("아메리카노", 4_000, 1, listOf("ICE"))),
        totalPrice = 4_000,
        status = status,
        createdAt = 0L,
        updatedAt = 0L,
    )

    private fun repo(vararg orders: Order) = InMemoryOrderRepository(orders.toList()) { 1_000L }

    @Test
    fun fullFlow_recordsEachTime_andKeepsClosedStatusAfterRefund() {
        val r = repo(order("A"))
        for (to in listOf(OrderStatus.ACCEPTED, OrderStatus.READY, OrderStatus.COMPLETED, OrderStatus.REFUNDED)) {
            assertEquals(TransitionResult.APPLIED, r.transition("A", to))
        }
        val order = r.get("A")!!
        assertEquals(OrderStatus.REFUNDED, order.status)
        assertEquals(OrderStatus.COMPLETED, order.closedAs)
        assertTrue(order.acceptedAt!! < order.readyAt!!)
        assertTrue(order.readyAt!! < order.closedAt!!)
        assertTrue(order.closedAt!! < order.refundedAt!!)
    }

    @Test
    fun reject_requiresReason() {
        val r = repo(order("A"))
        assertEquals(TransitionResult.REASON_REQUIRED, r.transition("A", OrderStatus.REJECTED))
        assertEquals(
            TransitionResult.APPLIED,
            r.transition("A", OrderStatus.REJECTED, RejectReason.OVERLOAD),
        )
        assertEquals(RejectReason.OVERLOAD, r.get("A")!!.rejectReason)
    }

    @Test
    fun notAllowedTransitions_areIgnored() {
        val r = repo(order("A", OrderStatus.ACCEPTED))
        assertEquals(TransitionResult.NOT_ALLOWED, r.transition("A", OrderStatus.REFUNDED))
        assertEquals(TransitionResult.NOT_ALLOWED, r.transition("A", OrderStatus.COMPLETED))
        assertEquals(TransitionResult.NOT_ALLOWED, r.transition("A", OrderStatus.REJECTED, RejectReason.OVERLOAD))
        assertEquals(OrderStatus.ACCEPTED, r.get("A")!!.status)
        assertNull(r.get("A")!!.readyAt)
    }

    @Test
    fun unknownOrder_isNotFound() {
        assertEquals(TransitionResult.NOT_FOUND, repo().transition("X", OrderStatus.ACCEPTED))
    }

    @Test
    fun add_ignoresDuplicateOrderNo() {
        val r = repo(order("A"))
        r.add(order("A"))
        r.add(order("B"))
        assertEquals(listOf("A", "B"), r.orders.value.map { it.orderNo })
    }
}
