package com.ormo.managerdemo.ui

import com.ormo.managerdemo.data.InMemoryMenuRepository
import com.ormo.managerdemo.data.InMemoryOrderRepository
import com.ormo.managerdemo.domain.AUTO_CANCEL_MILLIS
import com.ormo.managerdemo.domain.MenuCategory
import com.ormo.managerdemo.domain.MenuItem
import com.ormo.managerdemo.domain.MenuOption
import com.ormo.managerdemo.domain.OptionGroup
import com.ormo.managerdemo.domain.countUsing
import com.ormo.managerdemo.domain.NO_SHOW_MILLIS
import com.ormo.managerdemo.domain.Order
import com.ormo.managerdemo.domain.OrderLine
import com.ormo.managerdemo.domain.OrderStatus
import com.ormo.managerdemo.domain.PauseReason
import com.ormo.managerdemo.domain.PickupMethod
import com.ormo.managerdemo.domain.RefundReason
import com.ormo.managerdemo.domain.RejectReason
import com.ormo.managerdemo.domain.StoreStatus
import com.ormo.managerdemo.payment.PaymentGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OrderViewModelTest {

    private class RecordingGateway(
        var result: Result<Unit> = Result.success(Unit),
        var gate: CompletableDeferred<Unit>? = null,
    ) : PaymentGateway {
        data class Call(val orderId: String, val reason: String, val amount: Int)

        val calls = mutableListOf<Call>()

        override suspend fun cancel(orderId: String, reason: String, amount: Int): Result<Unit> {
            calls += Call(orderId, reason, amount)
            gate?.await()
            return result
        }
    }

    private companion object {
        const val START = 100_000_000L
        const val PICKUP_NO = 31
    }

    private var now = START
    private val gateway = RecordingGateway()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun order(
        no: String,
        status: OrderStatus = OrderStatus.REQUESTED,
        createdAt: Long = START,
        total: Int = 10_500,
        readyAt: Long? = null,
    ) = Order(
        orderNo = no,
        pickupNo = PICKUP_NO,
        pickupMethod = PickupMethod.TAKEOUT,
        customerNickname = "닉네임",
        lines = listOf(OrderLine("아메리카노", total, 1, listOf("ICE"))),
        totalPrice = total,
        status = status,
        createdAt = createdAt,
        updatedAt = createdAt,
        readyAt = readyAt,
    )

    private fun viewModel(vararg orders: Order): OrderViewModel {
        val repository = InMemoryOrderRepository(orders.toList()) { now }
        return OrderViewModel(repository, gateway, { now })
    }

    private fun TestScope.collectEvents(vm: OrderViewModel): List<OrderEvent> {
        val events = mutableListOf<OrderEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        return events
    }

    private fun OrderViewModel.status(no: String): OrderStatus =
        with(uiState.value) { pending + preparing + ready + history }.first { it.orderNo == no }.status

    // ---- 접수 대기 ----

    @Test
    fun accept_movesToPreparing_andDoubleTapEmitsOnce() = runTest {
        val vm = viewModel(order("A"))
        val events = collectEvents(vm)

        vm.onAccept("A")
        vm.onAccept("A")

        assertEquals(listOf("A"), vm.uiState.value.preparing.map { it.orderNo })
        assertEquals(listOf<OrderEvent>(OrderEvent.Accepted(PICKUP_NO)), events)
        assertEquals("수락은 결제 취소를 호출하지 않는다", 0, gateway.calls.size)
    }

    @Test
    fun reject_withReason_cancelsFullAmount_andKeepsSoldOutMenus() = runTest {
        val vm = viewModel(order("A", total = 9_800))

        vm.onRejectClick("A")
        assertEquals("창만 열고 상태는 그대로", OrderStatus.REQUESTED, vm.status("A"))
        vm.onRejectConfirm(RejectReason.OUT_OF_STOCK, listOf("아메리카노"))

        val rejected = vm.uiState.value.history.single()
        assertEquals(OrderStatus.REJECTED, rejected.status)
        assertEquals(RejectReason.OUT_OF_STOCK, rejected.rejectReason)
        assertEquals(listOf("아메리카노"), rejected.soldOutMenus)
        assertEquals(listOf(RecordingGateway.Call("A", "OUT_OF_STOCK", 9_800)), gateway.calls)
    }

    @Test
    fun reject_otherReason_ignoresSoldOut_andDismissChangesNothing() = runTest {
        val vm = viewModel(order("A"), order("B"))
        vm.onRejectClick("A")
        vm.onDialogDismiss()
        assertEquals(OrderStatus.REQUESTED, vm.status("A"))
        assertEquals(0, gateway.calls.size)

        vm.onRejectClick("B")
        vm.onRejectConfirm(RejectReason.OVERLOAD, listOf("아메리카노"))
        assertEquals(emptyList<String>(), vm.uiState.value.history.single().soldOutMenus)
    }

    // ---- 자동 취소 ----

    @Test
    fun tick_autoCancelsOnlyAfterTenMinutes_withFullRefund() = runTest {
        val vm = viewModel(order("A", total = 4_000))
        val events = collectEvents(vm)

        now = START + AUTO_CANCEL_MILLIS - 1
        vm.onTick()
        assertEquals(OrderStatus.REQUESTED, vm.status("A"))

        now = START + AUTO_CANCEL_MILLIS
        vm.onTick()
        vm.onTick()

        assertEquals(OrderStatus.AUTO_CANCELED, vm.status("A"))
        assertEquals(listOf(RecordingGateway.Call("A", "AUTO_CANCEL_TIMEOUT", 4_000)), gateway.calls)
        assertEquals(
            listOf(OrderEvent.AutoCancelSoon(PICKUP_NO), OrderEvent.AutoCanceled(PICKUP_NO)),
            events,
        )
    }

    @Test
    fun accept_afterDeadline_showsExpiredDialog() = runTest {
        val vm = viewModel(order("A"))
        now = START + AUTO_CANCEL_MILLIS

        vm.onAccept("A")

        assertTrue(vm.uiState.value.dialog is OrderDialog.Expired)
        assertEquals(OrderStatus.AUTO_CANCELED, vm.status("A"))
    }

    @Test
    fun tick_doesNotRetryFailedAutoCancelEverySecond() = runTest {
        gateway.result = Result.failure(RuntimeException("network"))
        val vm = viewModel(order("A"))
        now = START + AUTO_CANCEL_MILLIS

        vm.onTick()
        vm.onTick()

        assertEquals(1, gateway.calls.size)
        assertEquals(OrderStatus.REQUESTED, vm.status("A"))
    }

    // ---- 조리 완료 · 되돌리기 ----

    @Test
    fun cooked_movesImmediately_andUndoRestores() = runTest {
        val vm = viewModel(order("A", OrderStatus.ACCEPTED))

        vm.onCooked("A")
        assertEquals(OrderStatus.READY, vm.status("A"))
        assertEquals(10, vm.uiState.value.undo?.secondsLeft)

        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(7, vm.uiState.value.undo?.secondsLeft)

        vm.onUndoCooked()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(OrderStatus.ACCEPTED, vm.status("A"))
        assertNull(vm.uiState.value.undo)
    }

    @Test
    fun cooked_isCommittedAfterTenSeconds() = runTest {
        val vm = viewModel(order("A", OrderStatus.ACCEPTED))
        vm.onCooked("A")

        advanceTimeBy(10_000)
        runCurrent()

        assertNull(vm.uiState.value.undo)
        assertEquals(OrderStatus.READY, vm.status("A"))
        vm.onUndoCooked()
        assertEquals("확정 뒤에는 되돌릴 수 없다", OrderStatus.READY, vm.status("A"))
    }

    @Test
    fun cooked_anotherCard_commitsPreviousOne() = runTest {
        val vm = viewModel(order("A", OrderStatus.ACCEPTED), order("B", OrderStatus.ACCEPTED))
        vm.onCooked("A")
        vm.onCooked("B")

        assertEquals("B", vm.uiState.value.undo?.orderNo)
        vm.onUndoCooked()
        assertEquals(OrderStatus.READY, vm.status("A"))
        assertEquals(OrderStatus.ACCEPTED, vm.status("B"))
    }

    // ---- 픽업 대기 ----

    @Test
    fun pickedUp_movesToHistory() = runTest {
        val vm = viewModel(order("A", OrderStatus.ACCEPTED))
        vm.onCooked("A")
        vm.onPickedUp("A")

        assertEquals(OrderStatus.COMPLETED, vm.status("A"))
        assertNull(vm.uiState.value.undo)
    }

    @Test
    fun noShow_onlyAfterTwentyMinutes_withoutRefund() = runTest {
        val vm = viewModel(order("A", OrderStatus.READY, readyAt = START))

        now = START + NO_SHOW_MILLIS - 1
        vm.onNoShowClick("A")
        assertNull(vm.uiState.value.dialog)

        now = START + NO_SHOW_MILLIS
        vm.onNoShowClick("A")
        vm.onNoShowConfirm()

        assertEquals(OrderStatus.NO_SHOW, vm.status("A"))
        assertEquals(0, gateway.calls.size)
    }

    // ---- 환불 ----

    @Test
    fun refund_completedAndNoShow_fullAmount() = runTest {
        val vm = viewModel(
            order("C", OrderStatus.COMPLETED, total = 15_000),
            order("N", OrderStatus.NO_SHOW, total = 3_000),
        )
        val events = collectEvents(vm)

        vm.onRefundClick("C")
        vm.onRefundConfirm(RefundReason.WRONG_DRINK)
        vm.onRefundClick("N")
        vm.onRefundConfirm(RefundReason.OTHER)

        assertEquals(OrderStatus.REFUNDED, vm.status("C"))
        assertEquals(OrderStatus.REFUNDED, vm.status("N"))
        assertEquals(
            listOf(
                RecordingGateway.Call("C", "REFUND_BY_OWNER:WRONG_DRINK", 15_000),
                RecordingGateway.Call("N", "REFUND_BY_OWNER:OTHER", 3_000),
            ),
            gateway.calls,
        )
        assertEquals(OrderEvent.Refunded(PICKUP_NO, 15_000), events.first())
    }

    @Test
    fun refund_isNotAllowedForBoardOrCanceledOrders() = runTest {
        val statuses = listOf(
            OrderStatus.REQUESTED, OrderStatus.ACCEPTED, OrderStatus.READY, OrderStatus.REJECTED,
            OrderStatus.AUTO_CANCELED, OrderStatus.CUSTOMER_CANCELED, OrderStatus.REFUNDED,
        )
        val vm = viewModel(*statuses.map { order(it.name, it) }.toTypedArray())

        statuses.forEach {
            vm.onRefundClick(it.name)
            assertNull("$it", vm.uiState.value.dialog)
        }
        assertEquals(0, gateway.calls.size)
    }

    @Test
    fun refund_doubleTapWhilePaymentIsInFlight_callsGatewayOnce() = runTest {
        gateway.gate = CompletableDeferred()
        val vm = viewModel(order("A", OrderStatus.COMPLETED))

        vm.onRefundClick("A")
        vm.onRefundConfirm(RefundReason.HYGIENE) // 결제 취소 응답 대기 중
        vm.onRefundClick("A")
        vm.onRefundConfirm(RefundReason.HYGIENE) // inFlight 가드로 무시되어야 한다

        assertEquals(1, gateway.calls.size)
        gateway.gate!!.complete(Unit)
        assertEquals(OrderStatus.REFUNDED, vm.status("A"))
    }

    @Test
    fun paymentCancelFailure_keepsStatus_andEmitsFailureEvent() = runTest {
        gateway.result = Result.failure(RuntimeException("network"))
        val vm = viewModel(order("A", OrderStatus.COMPLETED))
        val events = collectEvents(vm)

        vm.onRefundClick("A")
        vm.onRefundConfirm(RefundReason.WRONG_DRINK)

        assertEquals(OrderStatus.COMPLETED, vm.status("A"))
        assertEquals(listOf<OrderEvent>(OrderEvent.PaymentCancelFailed("A", PICKUP_NO)), events)
    }

    // ---- 새 주문 알림 ----

    @Test
    fun newOrders_areCounted_butExistingOnesAreNot() = runTest {
        val vm = viewModel(order("A"))
        assertEquals("앱을 열 때 있던 주문은 알리지 않는다", 0, vm.uiState.value.newOrderCount)

        vm.addTestOrder()
        vm.addTestOrder()
        assertEquals(2, vm.uiState.value.newOrderCount)
        assertEquals(2, vm.uiState.value.newOrderSeq)

        // 수락/거절을 누른 주문은 알림에서 빠진다.
        val (first, second) = vm.uiState.value.pending.filter { it.orderNo != "A" }
        vm.onAccept(first.orderNo)
        assertEquals(1, vm.uiState.value.newOrderCount)
        vm.onRejectClick(second.orderNo)
        assertEquals(0, vm.uiState.value.newOrderCount)
    }

    @Test
    fun newOrderAlert_canBeDismissed_andComesBackForTheNextOrder() = runTest {
        val vm = viewModel()
        vm.addTestOrder()
        vm.onNewOrdersSeen()
        assertEquals(0, vm.uiState.value.newOrderCount)
        assertEquals("주문은 접수 대기에 그대로", 1, vm.uiState.value.pending.size)

        vm.addTestOrder()
        assertEquals(1, vm.uiState.value.newOrderCount)
    }

    @Test
    fun tick_warnsOnce_twoMinutesBeforeAutoCancel() = runTest {
        val vm = viewModel(order("A"))
        val events = collectEvents(vm)

        now = START + AUTO_CANCEL_MILLIS - OrderViewModel.AUTO_CANCEL_WARNING_MILLIS - 1
        vm.onTick()
        assertEquals(emptyList<OrderEvent>(), events)

        now += 1
        vm.onTick()
        vm.onTick()
        assertEquals(listOf<OrderEvent>(OrderEvent.AutoCancelSoon(PICKUP_NO)), events)
    }

    @Test
    fun retry_afterPaymentFailure_processesTheOrder() = runTest {
        gateway.result = Result.failure(RuntimeException("network"))
        val vm = viewModel(order("A"))
        vm.onRejectClick("A")
        vm.onRejectConfirm(RejectReason.OVERLOAD)
        assertEquals(OrderStatus.REQUESTED, vm.status("A"))

        gateway.result = Result.success(Unit)
        vm.retry("A")

        assertEquals(OrderStatus.REJECTED, vm.status("A"))
        assertEquals(2, gateway.calls.size)
        vm.retry("A")
        assertEquals("한 번 성공한 요청은 다시 보내지 않는다", 2, gateway.calls.size)
    }

    // ---- 메뉴 품절 ----

    private fun menuViewModel(menus: InMemoryMenuRepository, vararg orders: Order) = OrderViewModel(
        InMemoryOrderRepository(orders.toList()) { now },
        gateway,
        { now },
        menus,
    )

    private fun menuRepository(vararg names: String) =
        InMemoryMenuRepository(names.map { MenuItem(it, 4_000, MenuCategory.COFFEE) })

    @Test
    fun soldOut_warnsWhenPendingOrdersContainTheMenu() = runTest {
        val menus = menuRepository("아메리카노", "카페라떼")
        val vm = menuViewModel(menus, order("A"), order("B"), order("C", OrderStatus.ACCEPTED))
        val events = collectEvents(vm)

        vm.onSoldOutChange("카페라떼", true)
        vm.onSoldOutChange("아메리카노", true)
        vm.onSoldOutChange("아메리카노", false)

        assertEquals(listOf<OrderEvent>(OrderEvent.SoldOutInPendingOrders(2)), events)
        assertEquals(listOf("카페라떼"), vm.menus.value.filter { it.soldOut }.map { it.name })
    }

    @Test
    fun rejectWithSoldOutChecked_marksTheMenuSoldOut() = runTest {
        val menus = menuRepository("아메리카노")
        val vm = menuViewModel(menus, order("A"))

        vm.onRejectClick("A")
        vm.onRejectConfirm(RejectReason.OUT_OF_STOCK, listOf("아메리카노"))

        assertEquals(setOf("아메리카노"), menus.soldOutNames())
    }

    @Test
    fun testOrder_leavesOutSoldOutMenus() = runTest {
        val menus = menuRepository("아메리카노", "바닐라라떼")
        val vm = menuViewModel(menus)
        vm.onSoldOutChange("바닐라라떼", true)

        vm.addTestOrder()

        val added = vm.uiState.value.pending.single()
        assertEquals(listOf("아메리카노"), added.lines.map { it.name })
        assertEquals(added.lines.sumOf { it.amount }, added.totalPrice)
    }

    @Test
    fun soldOutOption_isReflected_andLeftOutOfTestOrders() = runTest {
        val menus = InMemoryMenuRepository(
            initialOptions = listOf(
                OptionGroup(
                    "샷", required = false,
                    options = listOf(MenuOption("1샷 추가", 900), MenuOption("ICE", 0)),
                ),
            ),
        )
        val vm = menuViewModel(menus)

        vm.onOptionSoldOutChange("1샷 추가", true)
        assertEquals(setOf("1샷 추가"), menus.soldOutOptionNames())
        assertEquals(true, vm.optionGroups.value.single().options.first().soldOut)

        // 첫 예시 주문의 둘째 줄(바닐라라떼 ICE · 1샷 추가)은 빠진다.
        vm.addTestOrder()
        assertEquals(listOf("아메리카노"), vm.uiState.value.pending.single().lines.map { it.name })

        vm.onOptionSoldOutChange("1샷 추가", false)
        assertEquals(emptySet<String>(), menus.soldOutOptionNames())
    }

    // ---- 메뉴·옵션 추가 · 수정 · 삭제 ----

    private fun OrderViewModel.closeStore() {
        onCloseClick()
        onCloseConfirm()
    }

    private fun item(name: String, price: Int = 4_000) = MenuItem(name, price, MenuCategory.COFFEE)

    @Test
    fun menuEditing_isRejectedUnlessClosed() = runTest {
        val menus = menuRepository("아메리카노")
        val vm = menuViewModel(menus)

        assertEquals(false, vm.onSaveMenu(null, item("라떼")))
        vm.onDeleteMenu("아메리카노")
        val group = OptionGroup("우유", required = true, options = listOf(MenuOption("오트", 500)))
        assertEquals(false, vm.onAddOptionGroup(group, emptySet()))

        assertEquals(listOf("아메리카노"), vm.menus.value.map { it.name })
        assertTrue(vm.optionGroups.value.isEmpty())
    }

    @Test
    fun saveMenu_needsNameAndPrice_andRejectsDuplicates_keepingSoldOut() = runTest {
        val menus = menuRepository("아메리카노", "카페라떼")
        val vm = menuViewModel(menus)
        vm.onSoldOutChange("카페라떼", true)
        vm.closeStore()

        assertEquals(false, vm.onSaveMenu(null, item(" ")))
        assertEquals(false, vm.onSaveMenu(null, item("모카", price = 0)))
        assertEquals(false, vm.onSaveMenu("카페라떼", item("아메리카노")))
        assertTrue(vm.onSaveMenu("카페라떼", item("오트라떼", 5_000)))
        assertTrue(vm.onSaveMenu(null, item("모카")))

        assertEquals(listOf("아메리카노", "오트라떼", "모카"), vm.menus.value.map { it.name })
        assertEquals(setOf("오트라떼"), menus.soldOutNames())
        assertTrue("바뀌기 전 이름은 주문할 수 없다", "카페라떼" in menus.unavailableNames())
    }

    @Test
    fun deletedMenu_canBeRestoredToItsPlace() = runTest {
        val menus = menuRepository("아메리카노", "카페라떼", "모카")
        val vm = menuViewModel(menus)
        val events = collectEvents(vm)
        vm.closeStore()

        vm.onDeleteMenu("카페라떼")
        assertEquals(listOf("아메리카노", "모카"), vm.menus.value.map { it.name })
        assertTrue("카페라떼" in menus.unavailableNames())

        vm.onRestoreMenu((events.last() as OrderEvent.MenuDeleted).removed)

        assertEquals(listOf("아메리카노", "카페라떼", "모카"), vm.menus.value.map { it.name })
        assertEquals(emptySet<String>(), menus.unavailableNames())
    }

    @Test
    fun hiddenMenu_isLeftOutOfTestOrders() = runTest {
        val menus = menuRepository("아메리카노", "바닐라라떼")
        val vm = menuViewModel(menus)
        vm.closeStore()
        vm.onSaveMenu("바닐라라떼", item("바닐라라떼").copy(hidden = true))
        vm.onOpenStore()

        vm.addTestOrder()

        assertEquals(listOf("아메리카노"), vm.uiState.value.pending.single().lines.map { it.name })
    }

    @Test
    fun newOptionGroup_isLinkedToChosenMenus_andNamesMustBeUnique() = runTest {
        val menus = menuRepository("아메리카노", "카페라떼")
        val vm = menuViewModel(menus)
        vm.closeStore()
        val milk = OptionGroup(
            "우유", required = true,
            options = listOf(MenuOption("일반 우유", 0), MenuOption("오트 우유", 500)),
        )

        assertTrue(vm.onAddOptionGroup(milk, setOf("카페라떼")))
        assertEquals(false, vm.onAddOptionGroup(milk, emptySet()))
        assertEquals(
            "다른 그룹의 항목 이름도 겹치면 안 된다",
            false,
            vm.onAddOptionGroup(milk.copy(name = "두유", options = listOf(MenuOption("오트 우유", 0))), emptySet()),
        )

        assertEquals(listOf(emptyList(), listOf("우유")), vm.menus.value.map { it.optionGroups })
        assertEquals(1, vm.menus.value.countUsing("우유"))
    }

    // ---- 영업 상태 ----

    @Test
    fun pause_blocksNewOrders_andReopensWhenTimeIsUp() = runTest {
        val vm = viewModel()
        val events = collectEvents(vm)
        vm.onPauseClick()
        vm.onPauseConfirm(20, PauseReason.BUSY)
        assertEquals(StoreStatus.Paused(START + 20 * 60_000L, PauseReason.BUSY), vm.uiState.value.storeStatus)

        vm.addTestOrder()
        assertEquals(0, vm.uiState.value.pending.size)

        now = START + 20 * 60_000L - 1
        vm.onTick()
        assertTrue(vm.uiState.value.storeStatus is StoreStatus.Paused)
        now += 1
        vm.onTick()

        assertEquals(StoreStatus.Open, vm.uiState.value.storeStatus)
        assertEquals(
            listOf(OrderEvent.OrdersNotAccepted, OrderEvent.StoreResumed(automatic = true)),
            events,
        )
    }

    @Test
    fun pauseWithoutDuration_staysUntilReopened() = runTest {
        val vm = viewModel()
        val events = collectEvents(vm)
        vm.onPauseClick()
        vm.onPauseConfirm(null, PauseReason.PREPARING)
        now = START + 24 * 60 * 60_000L
        vm.onTick()
        assertTrue(vm.uiState.value.storeStatus is StoreStatus.Paused)

        vm.onOpenStore()
        vm.onOpenStore()
        assertEquals(listOf<OrderEvent>(OrderEvent.StoreResumed(automatic = false)), events)
    }

    @Test
    fun close_withoutPendingOrders_closesAfterOneConfirm() = runTest {
        val vm = viewModel(order("A", OrderStatus.ACCEPTED))
        val events = collectEvents(vm)
        vm.onCloseClick()
        assertEquals(StoreStatus.Open, vm.uiState.value.storeStatus)
        vm.onCloseConfirm()
        assertEquals(StoreStatus.Closed, vm.uiState.value.storeStatus)

        // 마감 뒤에도 받은 주문은 계속 처리할 수 있다.
        vm.onCooked("A")
        assertEquals(OrderStatus.READY, vm.status("A"))

        vm.onOpenStore()
        assertEquals(listOf<OrderEvent>(OrderEvent.StoreStarted), events)
    }

    @Test
    fun close_withPendingOrders_asksAgain_thenRefundsAllAndCloses() = runTest {
        val vm = viewModel(order("A", total = 1_000), order("B", total = 2_000))
        vm.onCloseClick()
        vm.onCloseConfirm()
        assertEquals(StoreDialog.CloseWithPending(2), vm.uiState.value.storeDialog)
        assertEquals(0, gateway.calls.size)

        vm.onCancelPendingAndClose()

        assertEquals(StoreStatus.Closed, vm.uiState.value.storeStatus)
        assertEquals(
            listOf(
                RecordingGateway.Call("A", "CLOSING_SOON", 1_000),
                RecordingGateway.Call("B", "CLOSING_SOON", 2_000),
            ),
            gateway.calls,
        )
        assertTrue(vm.uiState.value.history.all { it.rejectReason == RejectReason.CLOSING_SOON })
    }

    @Test
    fun close_isNotApplied_whenARefundFails() = runTest {
        gateway.result = Result.failure(RuntimeException("network"))
        val vm = viewModel(order("A"))
        vm.onCloseClick()
        vm.onCloseConfirm()
        vm.onCancelPendingAndClose()

        assertEquals(StoreStatus.Open, vm.uiState.value.storeStatus)
        assertEquals(OrderStatus.REQUESTED, vm.status("A"))
    }

    // ---- 정렬 · 데모 ----

    @Test
    fun columns_areOldestFirst_andHistoryNewestFirst() = runTest {
        val vm = viewModel(
            order("N", createdAt = START + 3),
            order("O", createdAt = START + 1),
            order("M", createdAt = START + 2),
            order("H1", OrderStatus.COMPLETED, createdAt = START - 10),
            order("H2", OrderStatus.COMPLETED, createdAt = START - 5),
        )
        assertEquals(listOf("O", "M", "N"), vm.uiState.value.pending.map { it.orderNo })
        assertEquals(listOf("H2", "H1"), vm.uiState.value.history.map { it.orderNo })

        vm.onAccept("N")
        vm.onAccept("O")
        assertEquals(listOf("O", "N"), vm.uiState.value.preparing.map { it.orderNo })
    }

    @Test
    fun addTestOrder_addsRequestedOrders_withUniqueNumbers() = runTest {
        val vm = viewModel()
        vm.addTestOrder()
        vm.addTestOrder()

        val pending = vm.uiState.value.pending
        assertEquals(2, pending.size)
        assertEquals(2, pending.map { it.orderNo }.toSet().size)
        assertEquals(2, pending.map { it.pickupNo }.toSet().size)
    }
}
