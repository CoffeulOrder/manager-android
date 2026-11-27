package com.ormo.managerdemo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ormo.managerdemo.data.InMemoryMenuRepository
import com.ormo.managerdemo.data.InMemoryOrderRepository
import com.ormo.managerdemo.data.MockMenus
import com.ormo.managerdemo.data.MockOptions
import com.ormo.managerdemo.data.RemovedMenu
import com.ormo.managerdemo.data.MockOrders
import com.ormo.managerdemo.data.OrderRepository
import com.ormo.managerdemo.data.TransitionResult
import com.ormo.managerdemo.domain.AUTO_CANCEL_MILLIS
import com.ormo.managerdemo.domain.MenuItem
import com.ormo.managerdemo.domain.OptionGroup
import com.ormo.managerdemo.domain.NO_SHOW_MILLIS
import com.ormo.managerdemo.domain.Order
import com.ormo.managerdemo.domain.OrderStatus
import com.ormo.managerdemo.domain.OrderTransitions
import com.ormo.managerdemo.domain.PauseReason
import com.ormo.managerdemo.domain.RefundReason
import com.ormo.managerdemo.domain.RejectReason
import com.ormo.managerdemo.domain.StoreStatus
import com.ormo.managerdemo.payment.FakePaymentGateway
import com.ormo.managerdemo.payment.PaymentGateway
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 열려 있는 확인창 */
sealed interface OrderDialog {
    val order: Order

    data class Reject(override val order: Order) : OrderDialog
    data class Refund(override val order: Order) : OrderDialog
    data class NoShow(override val order: Order) : OrderDialog

    /** 수락/거절하려는데 이미 자동 취소된 주문. [확인]을 눌러야 닫힌다. */
    data class Expired(override val order: Order) : OrderDialog
}

/** 영업 상태를 바꿀 때 뜨는 확인창 */
sealed interface StoreDialog {
    data object PauseSetup : StoreDialog
    data object CloseConfirm : StoreDialog

    /** 마감하려는데 접수 대기가 남아 있을 때 */
    data class CloseWithPending(val count: Int) : StoreDialog
}

private data class StoreState(
    val status: StoreStatus = StoreStatus.Open,
    val dialog: StoreDialog? = null,
)

/** "조리 완료" 뒤 되돌릴 수 있는 시간. 이 동안은 아직 확정(저장)하지 않는다. */
data class UndoState(
    val orderNo: String,
    val pickupNo: Int,
    val secondsLeft: Int,
    val movedAt: Long,
)

data class OrderUiState(
    /** 접수 대기. 열 안은 모두 접수 시각 순(먼저 온 주문이 위) */
    val pending: List<Order> = emptyList(),
    /** 제조중 */
    val preparing: List<Order> = emptyList(),
    /** 픽업 대기 */
    val ready: List<Order> = emptyList(),
    /** 오늘 내역. 최근 주문이 위 */
    val history: List<Order> = emptyList(),
    val dialog: OrderDialog? = null,
    val undo: UndoState? = null,
    /** 아직 확인하지 않은 새 주문 수. 0보다 크면 알림음을 반복한다. */
    val newOrderCount: Int = 0,
    /** 새 주문이 도착할 때마다 늘어난다. 알림음을 바로 다시 울리는 데 쓴다. */
    val newOrderSeq: Int = 0,
    val storeStatus: StoreStatus = StoreStatus.Open,
    val storeDialog: StoreDialog? = null,
)

/** 스낵바로 보여줄 일회성 이벤트. 문구는 화면(strings.xml)에서 만든다. */
sealed interface OrderEvent {
    data class Accepted(val pickupNo: Int) : OrderEvent
    data class MenuDeleted(val removed: RemovedMenu) : OrderEvent
    data class Rejected(val pickupNo: Int) : OrderEvent
    data class PickedUp(val pickupNo: Int) : OrderEvent
    data class NoShowed(val pickupNo: Int) : OrderEvent
    data class AutoCanceled(val pickupNo: Int) : OrderEvent
    data class Refunded(val pickupNo: Int, val amount: Int) : OrderEvent
    /** 자동 취소 2분 전. 알림음을 한 번 울린다. */
    data class AutoCancelSoon(val pickupNo: Int) : OrderEvent

    /** 마감에서 영업 시작 */
    data object StoreStarted : OrderEvent

    /** 잠시 중지에서 다시 영업. [automatic]이면 시간이 끝나 자동으로 열렸다(알림음 한 번). */
    data class StoreResumed(val automatic: Boolean) : OrderEvent

    /** 잠시 중지·마감 중이라 새 주문을 받지 않았다. */
    data object OrdersNotAccepted : OrderEvent

    /** 방금 품절로 바꾼 메뉴가 접수 대기 주문에 [count]건 들어 있다. */
    data class SoldOutInPendingOrders(val count: Int) : OrderEvent

    /** [OrderViewModel.retry]로 다시 시도할 수 있다. */
    data class PaymentCancelFailed(val orderNo: String, val pickupNo: Int) : OrderEvent
}

class OrderViewModel(
    private val repository: OrderRepository,
    private val paymentGateway: PaymentGateway,
    private val clock: () -> Long = System::currentTimeMillis,
    private val menuRepository: InMemoryMenuRepository = InMemoryMenuRepository(),
) : ViewModel() {

    /** 매출 계산에 쓰는 전체 주문 */
    val allOrders: StateFlow<List<Order>> = repository.orders
    val menus: StateFlow<List<MenuItem>> = menuRepository.menus
    val optionGroups: StateFlow<List<OptionGroup>> = menuRepository.optionGroups

    private val dialog = MutableStateFlow<OrderDialog?>(null)
    private val undo = MutableStateFlow<UndoState?>(null)
    private val store = MutableStateFlow(StoreState())
    private var undoJob: Job? = null
    private val eventChannel = Channel<OrderEvent>(Channel.BUFFERED)

    /** 처리 중인 주문번호. 더블 탭으로 같은 주문을 두 번 처리하지 않도록 막는다. (메인 스레드에서만 접근) */
    private val inFlight = mutableSetOf<String>()

    /** 자동 취소의 결제 취소가 실패한 주문. 매초 다시 시도하지 않는다. */
    private val failedAutoCancels = mutableSetOf<String>()

    /** 처음 보는 접수 대기 주문 중 아직 확인하지 않은 것 */
    private val unseen = MutableStateFlow<Set<String>>(emptySet())

    /** 앱을 열 때 이미 있던 주문은 새 주문으로 알리지 않는다. 같은 주문으로 두 번 울리지 않는다. */
    private val knownOrderNos = repository.orders.value.map { it.orderNo }.toMutableSet()

    private var arrivalSeq = 0

    private val warnedAutoCancel = mutableSetOf<String>()

    /** 결제 취소에 실패해 다시 시도할 수 있는 요청 */
    private val failedRequests = mutableMapOf<String, () -> Unit>()

    // DEMO ONLY: "테스트 주문 추가" 전용 카운터
    private var testOrderSeq = 0

    val events: Flow<OrderEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.orders.collect { orders ->
                val arrived = orders
                    .filter { it.status == OrderStatus.REQUESTED && it.orderNo !in knownOrderNos }
                    .map { it.orderNo }
                knownOrderNos += orders.map { it.orderNo }
                if (arrived.isNotEmpty()) {
                    arrivalSeq += 1
                    unseen.value = unseen.value + arrived
                }
            }
        }
    }

    val uiState: StateFlow<OrderUiState> =
        combine(repository.orders, dialog, undo, unseen, store) { stored, dialog, undo, unseen, store ->
            // 되돌릴 수 있는 동안에도 카드는 바로 픽업 대기에 보여준다.
            val orders = stored.map {
                if (it.orderNo == undo?.orderNo) it.copy(status = OrderStatus.READY, readyAt = undo.movedAt) else it
            }
            fun column(status: OrderStatus) = orders.filter { it.status == status }.sortedBy { it.createdAt }
            OrderUiState(
                pending = column(OrderStatus.REQUESTED),
                preparing = column(OrderStatus.ACCEPTED),
                ready = column(OrderStatus.READY),
                history = orders.filter { !it.status.isOnBoard }.sortedByDescending { it.createdAt },
                dialog = dialog,
                undo = undo,
                newOrderCount = orders.count { it.status == OrderStatus.REQUESTED && it.orderNo in unseen },
                newOrderSeq = arrivalSeq,
                storeStatus = store.status,
                storeDialog = store.dialog,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, OrderUiState())

    // ---- 새 주문 알림 --------------------------------------------------------------------

    /** 스낵바를 닫거나 [주문 보기]를 누르면 알림음을 멈춘다. 주문은 접수 대기에 그대로다. */
    fun onNewOrdersSeen() {
        unseen.value = emptySet()
    }

    private fun markSeen(orderNo: String) {
        unseen.value = unseen.value - orderNo
    }

    // ---- 메뉴 품절 (Figma 3.2.1) ----------------------------------------------------------

    /** 바로 저장한다. 품절로 바꾼 메뉴가 접수 대기에 있으면 알려준다. */
    fun onSoldOutChange(menuName: String, soldOut: Boolean) {
        menuRepository.setSoldOut(menuName, soldOut)
        if (!soldOut) return
        val waiting = repository.orders.value.count { order ->
            order.status == OrderStatus.REQUESTED && order.lines.any { it.name == menuName }
        }
        if (waiting > 0) emit(OrderEvent.SoldOutInPendingOrders(waiting))
    }

    fun onOptionSoldOutChange(optionName: String, soldOut: Boolean) {
        menuRepository.setOptionSoldOut(optionName, soldOut)
    }

    // ---- 메뉴·옵션 추가 · 수정 · 삭제 (Figma 3.2) --------------------------------------------
    // 마감했을 때만 할 수 있다. 2차 비밀번호는 화면에서 먼저 묻는다.

    private val canEditMenus get() = store.value.status == StoreStatus.Closed

    fun onSaveMenu(original: String?, item: MenuItem): Boolean =
        canEditMenus && menuRepository.saveMenu(original, item)

    fun onDeleteMenu(name: String) {
        if (!canEditMenus) return
        menuRepository.removeMenu(name)?.let { emit(OrderEvent.MenuDeleted(it)) }
    }

    /** 삭제 되돌리기. 그 사이 영업을 시작했어도 되돌릴 수 있다. */
    fun onRestoreMenu(removed: RemovedMenu) {
        menuRepository.restoreMenu(removed)
    }

    fun onAddOptionGroup(group: OptionGroup, menuNames: Set<String>): Boolean =
        canEditMenus && menuRepository.addOptionGroup(group, menuNames)

    // ---- 영업 상태 (Figma 3.1) ------------------------------------------------------------

    /** 영업 중으로 바꾼다. 확인창은 없다. */
    fun onOpenStore() {
        val event = when (store.value.status) {
            StoreStatus.Open -> return
            StoreStatus.Closed -> OrderEvent.StoreStarted
            is StoreStatus.Paused -> OrderEvent.StoreResumed(automatic = false)
        }
        store.value = StoreState(StoreStatus.Open)
        emit(event)
    }

    fun onPauseClick() {
        store.value = store.value.copy(dialog = StoreDialog.PauseSetup)
    }

    fun onPauseConfirm(minutes: Int?, reason: PauseReason) {
        if (store.value.dialog != StoreDialog.PauseSetup) return
        val until = minutes?.let { clock() + it * 60_000L }
        store.value = StoreState(StoreStatus.Paused(until, reason))
    }

    fun onCloseClick() {
        if (store.value.status == StoreStatus.Closed) return
        store.value = store.value.copy(dialog = StoreDialog.CloseConfirm)
    }

    /** [마감하기]. 접수 대기가 남아 있으면 한 번 더 묻는다. */
    fun onCloseConfirm() {
        if (store.value.dialog != StoreDialog.CloseConfirm) return
        val waiting = repository.orders.value.count { it.status == OrderStatus.REQUESTED }
        store.value = if (waiting > 0) {
            store.value.copy(dialog = StoreDialog.CloseWithPending(waiting))
        } else {
            StoreState(StoreStatus.Closed)
        }
    }

    /** [모두 취소하기]: 접수 대기를 모두 취소(전액 환불)한 뒤 마감한다. 하나라도 실패하면 마감하지 않는다. */
    fun onCancelPendingAndClose() {
        if (store.value.dialog !is StoreDialog.CloseWithPending) return
        store.value = store.value.copy(dialog = null)
        val waiting = repository.orders.value.filter { it.status == OrderStatus.REQUESTED }
        viewModelScope.launch {
            var allCanceled = true
            for (order in waiting) {
                val canceled = cancelAndTransition(
                    orderNo = order.orderNo,
                    to = OrderStatus.REJECTED,
                    cancelReason = RejectReason.CLOSING_SOON.name,
                    successEvent = null,
                    rejectReason = RejectReason.CLOSING_SOON,
                )
                allCanceled = allCanceled && canceled
            }
            if (allCanceled) store.value = StoreState(StoreStatus.Closed)
        }
    }

    fun onStoreDialogDismiss() {
        store.value = store.value.copy(dialog = null)
    }

    private fun reopenWhenPauseEnds() {
        val status = store.value.status as? StoreStatus.Paused ?: return
        val until = status.until ?: return
        if (clock() < until) return
        store.value = store.value.copy(status = StoreStatus.Open)
        emit(OrderEvent.StoreResumed(automatic = true))
    }

    // ---- 접수 대기 -----------------------------------------------------------------------

    fun onAccept(orderNo: String) {
        markSeen(orderNo)
        val order = repository.get(orderNo) ?: return
        if (order.status == OrderStatus.REQUESTED && isExpired(order)) {
            dialog.value = OrderDialog.Expired(order)
            autoCancel(order)
            return
        }
        if (repository.transition(orderNo, OrderStatus.ACCEPTED) == TransitionResult.APPLIED) {
            emit(OrderEvent.Accepted(order.pickupNo))
        }
    }

    fun onRejectClick(orderNo: String) {
        markSeen(orderNo)
        val order = repository.get(orderNo) ?: return
        if (!OrderTransitions.canTransition(order.status, OrderStatus.REJECTED)) return
        dialog.value = OrderDialog.Reject(order)
    }

    fun onRejectConfirm(reason: RejectReason, soldOutMenus: List<String> = emptyList()) {
        val target = dialog.value as? OrderDialog.Reject ?: return
        dialog.value = null
        cancelPaymentThenTransition(
            orderNo = target.order.orderNo,
            to = OrderStatus.REJECTED,
            cancelReason = reason.name,
            rejectReason = reason,
            soldOutMenus = if (reason == RejectReason.OUT_OF_STOCK) soldOutMenus else emptyList(),
            successEvent = OrderEvent.Rejected(target.order.pickupNo),
        )
    }

    /** 화면에서 1초마다 부른다. 10분이 지난 접수 대기를 자동 취소하고, 2분 전에 한 번 알린다. */
    fun onTick() {
        reopenWhenPauseEnds()

        repository.orders.value
            .filter { it.status == OrderStatus.REQUESTED && !isExpired(it) && it.orderNo !in warnedAutoCancel }
            .filter { it.createdAt + AUTO_CANCEL_MILLIS - clock() <= AUTO_CANCEL_WARNING_MILLIS }
            .forEach {
                warnedAutoCancel += it.orderNo
                emit(OrderEvent.AutoCancelSoon(it.pickupNo))
            }

        repository.orders.value
            .filter { it.status == OrderStatus.REQUESTED && isExpired(it) && it.orderNo !in failedAutoCancels }
            .forEach { order ->
                // 거절 사유를 고르는 중이었다면 창을 바꿔서 알려준다.
                val open = dialog.value
                if (open is OrderDialog.Reject && open.order.orderNo == order.orderNo) {
                    dialog.value = OrderDialog.Expired(order)
                }
                autoCancel(order)
            }
    }

    private fun isExpired(order: Order) = clock() - order.createdAt >= AUTO_CANCEL_MILLIS

    private fun autoCancel(order: Order) {
        cancelPaymentThenTransition(
            orderNo = order.orderNo,
            to = OrderStatus.AUTO_CANCELED,
            cancelReason = AUTO_CANCEL_REASON_CODE,
            successEvent = OrderEvent.AutoCanceled(order.pickupNo),
            onFailure = { failedAutoCancels += order.orderNo },
        )
    }

    // ---- 제조중 --------------------------------------------------------------------------

    /** 조리 완료: 카드는 바로 옮기고, 10초 뒤에 확정한다. */
    fun onCooked(orderNo: String) {
        // 10초 안에 다른 카드를 처리하면 앞 건은 바로 확정한다.
        commitReady()
        val order = repository.get(orderNo) ?: return
        if (!OrderTransitions.canTransition(order.status, OrderStatus.READY)) return
        undo.value = UndoState(orderNo, order.pickupNo, UNDO_SECONDS, clock())
        undoJob = viewModelScope.launch {
            for (left in UNDO_SECONDS - 1 downTo 1) {
                delay(1_000)
                undo.value = undo.value?.copy(secondsLeft = left)
            }
            delay(1_000)
            commitReady()
        }
    }

    fun onUndoCooked() {
        undoJob?.cancel()
        undo.value = null
    }

    private fun commitReady() {
        val waiting = undo.value ?: return
        undoJob?.cancel()
        repository.transition(waiting.orderNo, OrderStatus.READY)
        undo.value = null
    }

    // ---- 픽업 대기 -----------------------------------------------------------------------

    fun onPickedUp(orderNo: String) {
        if (undo.value?.orderNo == orderNo) commitReady()
        val order = repository.get(orderNo) ?: return
        if (repository.transition(orderNo, OrderStatus.COMPLETED) == TransitionResult.APPLIED) {
            emit(OrderEvent.PickedUp(order.pickupNo))
        }
    }

    fun onNoShowClick(orderNo: String) {
        val order = repository.get(orderNo) ?: return
        if (!canMarkNoShow(order)) return
        dialog.value = OrderDialog.NoShow(order)
    }

    /** 미수령 정리는 환불하지 않으므로 결제 취소를 부르지 않는다. */
    fun onNoShowConfirm() {
        val target = dialog.value as? OrderDialog.NoShow ?: return
        dialog.value = null
        if (repository.transition(target.order.orderNo, OrderStatus.NO_SHOW) == TransitionResult.APPLIED) {
            emit(OrderEvent.NoShowed(target.order.pickupNo))
        }
    }

    private fun canMarkNoShow(order: Order): Boolean {
        val readyAt = order.readyAt ?: return false
        return OrderTransitions.canTransition(order.status, OrderStatus.NO_SHOW) &&
            clock() - readyAt >= NO_SHOW_MILLIS
    }

    // ---- 환불 (픽업 완료 · 미수령, 전액) ----------------------------------------------------

    fun onRefundClick(orderNo: String) {
        val order = repository.get(orderNo) ?: return
        if (!OrderTransitions.canTransition(order.status, OrderStatus.REFUNDED)) return
        dialog.value = OrderDialog.Refund(order)
    }

    fun onRefundConfirm(reason: RefundReason) {
        val target = dialog.value as? OrderDialog.Refund ?: return
        dialog.value = null
        cancelPaymentThenTransition(
            orderNo = target.order.orderNo,
            to = OrderStatus.REFUNDED,
            cancelReason = "$REFUND_REASON_CODE:${reason.name}",
            successEvent = OrderEvent.Refunded(target.order.pickupNo, target.order.totalPrice),
        )
    }

    fun onDialogDismiss() {
        dialog.value = null
    }

    /** 처리하지 못한 요청을 다시 보낸다(Figma 1.3.6). */
    fun retry(orderNo: String) {
        failedRequests.remove(orderNo)?.invoke()
    }

    // ---- DEMO ONLY ----------------------------------------------------------------------

    /** 데모용 mock 주문 1건을 접수 대기에 추가한다. 나중에 이 함수와 푸터의 버튼만 지우면 된다. */
    fun addTestOrder() {
        // 잠시 중지·마감 중에는 서버가 새 주문을 막는 것처럼 받지 않는다.
        if (store.value.status != StoreStatus.Open) {
            emit(OrderEvent.OrdersNotAccepted)
            return
        }
        // 고객은 품절·숨김·삭제한 메뉴와 품절 옵션을 주문할 수 없다: 해당 줄을 빼고, 남는 게 없으면 다음 예시를 쓴다.
        val unavailable = menuRepository.unavailableNames()
        val soldOutOptions = menuRepository.soldOutOptionNames()
        repeat(TEST_ORDER_TRIES) {
            val template = MockOrders.testOrder(testOrderSeq++, clock())
            val lines = template.lines.filter { line ->
                line.name !in unavailable && line.options.none { it in soldOutOptions }
            }
            if (lines.isNotEmpty()) {
                repository.add(template.copy(lines = lines, totalPrice = lines.sumOf { it.amount }))
                return
            }
        }
        emit(OrderEvent.OrdersNotAccepted)
    }

    // ---- 내부 ----------------------------------------------------------------------------

    private fun emit(event: OrderEvent) {
        eventChannel.trySend(event)
    }

    private fun cancelPaymentThenTransition(
        orderNo: String,
        to: OrderStatus,
        cancelReason: String,
        successEvent: OrderEvent,
        rejectReason: RejectReason? = null,
        soldOutMenus: List<String> = emptyList(),
        onFailure: () -> Unit = {},
    ) {
        viewModelScope.launch {
            cancelAndTransition(orderNo, to, cancelReason, successEvent, rejectReason, soldOutMenus, onFailure)
        }
    }

    /**
     * 결제 취소가 성공했을 때만 상태를 바꾼다. 실패하면 상태는 그대로 두고 알린다.
     *
     * @return 상태를 바꿨으면 true
     */
    private suspend fun cancelAndTransition(
        orderNo: String,
        to: OrderStatus,
        cancelReason: String,
        successEvent: OrderEvent?,
        rejectReason: RejectReason? = null,
        soldOutMenus: List<String> = emptyList(),
        onFailure: () -> Unit = {},
    ): Boolean {
        if (!inFlight.add(orderNo)) return false
        try {
            val order = repository.get(orderNo)
            if (order == null || !OrderTransitions.canTransition(order.status, to)) return false
            // 결제 취소 금액은 항상 주문 전액 (부분 취소/환불 없음)
            val cancel = paymentGateway.cancel(orderNo, cancelReason, order.totalPrice)
            if (cancel.isFailure) {
                onFailure()
                failedRequests[orderNo] = {
                    cancelPaymentThenTransition(
                        orderNo, to, cancelReason,
                        successEvent ?: OrderEvent.Rejected(order.pickupNo),
                        rejectReason, soldOutMenus, onFailure,
                    )
                }
                emit(OrderEvent.PaymentCancelFailed(orderNo, order.pickupNo))
                return false
            }
            val result = repository.transition(orderNo, to, rejectReason, soldOutMenus)
            if (result != TransitionResult.APPLIED) return false
            // 거절하며 "오늘만 품절로 바꾸기"를 고른 메뉴
            soldOutMenus.forEach { menuRepository.setSoldOut(it, true) }
            successEvent?.let(::emit)
            return true
        } finally {
            inFlight.remove(orderNo)
        }
    }

    companion object {
        const val UNDO_SECONDS = 10
        private const val TEST_ORDER_TRIES = 3
        private const val REFUND_REASON_CODE = "REFUND_BY_OWNER"
        private const val AUTO_CANCEL_REASON_CODE = "AUTO_CANCEL_TIMEOUT"
        const val AUTO_CANCEL_WARNING_MILLIS = 2 * 60_000L

        val Factory = viewModelFactory {
            initializer {
                val now = System.currentTimeMillis()
                OrderViewModel(
                    repository = InMemoryOrderRepository(MockOrders.initialOrders(now)),
                    // 실제 연동 시 여기서 BE 호출 구현으로 교체한다. (README 참고)
                    paymentGateway = FakePaymentGateway(),
                    menuRepository = InMemoryMenuRepository(MockMenus.initial, MockOptions.initial),
                )
            }
        }
    }
}
