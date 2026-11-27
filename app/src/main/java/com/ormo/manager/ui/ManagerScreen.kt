package com.ormo.manager.ui

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ormo.manager.R
import com.ormo.manager.domain.BusinessInfo
import com.ormo.manager.domain.StoreStatus
import com.ormo.manager.domain.formatClock
import com.ormo.manager.domain.formatWon
import com.ormo.manager.ui.theme.OrmoColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 확인하지 않은 새 주문의 알림음 간격 */
private const val ALERT_REPEAT_MILLIS = 3_000L

private const val NEW_ORDER_KEY = "new-order"
private const val SOUND_OFF_KEY = "sound-off"
private const val UNDO_KEY = "undo"
private const val STORE_KEY = "store"
private const val CONNECTION_KEY = "connection"
private const val OFFLINE_BLOCKED_KEY = "offline-blocked"

@Composable
fun ManagerScreen(
    viewModel: OrderViewModel,
    pinViewModel: PinViewModel,
    settingsViewModel: SettingsViewModel,
    businessInfo: BusinessInfo,
    onLogout: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by rememberClock()
    val resources = LocalResources.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val sound = remember { DeviceSound(context.applicationContext) }
    val soundOff by rememberSoundOff(sound)
    val menus by viewModel.menus.collectAsStateWithLifecycle()
    val allOrders by viewModel.allOrders.collectAsStateWithLifecycle()
    val pin by pinViewModel.uiState.collectAsStateWithLifecycle()
    val businessHours by settingsViewModel.businessHours.collectAsStateWithLifecycle()
    val optionGroups by viewModel.optionGroups.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(ManagerTab.ORDERS) }
    var selectedOrderNo by rememberSaveable { mutableStateOf<String?>(null) }
    var snacks by remember { mutableStateOf(emptyList<Snack>()) }
    var snackSeq by remember { mutableIntStateOf(0) }
    var ringCount by remember { mutableIntStateOf(0) }
    var soundOffDismissed by remember { mutableStateOf(false) }
    var showNewestPending by remember { mutableStateOf(false) }
    var wantsSales by remember { mutableStateOf(false) }
    var menuEditing by remember { mutableStateOf(false) }
    var monthView by rememberSaveable { mutableStateOf(MonthView.GRAPH) }
    var statusMenuOpen by remember { mutableStateOf(false) }
    var statusButtonX by remember { mutableFloatStateOf(0f) }

    fun showSnack(
        message: String,
        critical: Boolean = false,
        retryOrderNo: String? = null,
        viewOrders: Boolean = false,
    ) {
        snackSeq += 1
        val kind = if (retryOrderNo != null || viewOrders) SnackKind.ACTION else SnackKind.INFO
        snacks = snacks.push(Snack("snack-$snackSeq", kind, message, critical, retryOrderNo, viewOrders))
    }

    DisposableEffect(sound) { onDispose { sound.stop() } }

    // 2차 비밀번호를 맞히면 매출로 이동한다. 매출을 나가면 다시 잠근다.
    LaunchedEffect(pin.unlocked, wantsSales) {
        if (pin.unlocked && wantsSales) {
            wantsSales = false
            tab = ManagerTab.SALES
        }
    }
    LaunchedEffect(tab) {
        // 앱이 다시 만들어져 잠긴 채 매출 탭이 복원되면 주문 탭으로 보낸다.
        if (tab == ManagerTab.SALES && !pinViewModel.uiState.value.unlocked) tab = ManagerTab.ORDERS
        if (tab != ManagerTab.SALES) pinViewModel.lock()
    }
    LaunchedEffect(pin.notice) {
        val notice = pin.notice ?: return@LaunchedEffect
        showSnack(
            resources.getString(
                when (notice) {
                    PinNotice.CREATED -> R.string.snackbar_pin_created
                    PinNotice.CHANGED -> R.string.snackbar_pin_changed
                },
            ),
        )
        pinViewModel.onNoticeShown()
    }

    // 자동 취소 확인(1초마다)
    LaunchedEffect(viewModel) {
        while (true) {
            viewModel.onTick()
            delay(1_000L)
        }
    }

    // 확인하지 않은 새 주문이 있는 동안 알림음 반복. 화면이 보일 때만 울린다.
    val hasNewOrders = state.newOrderCount > 0
    LaunchedEffect(hasNewOrders, state.newOrderSeq) {
        if (!hasNewOrders) {
            sound.stop()
            return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                sound.playAlert()
                ringCount += 1
                delay(ALERT_REPEAT_MILLIS)
            }
        }
    }

    fun showOfflineBlocked(forOrders: Boolean) {
        val message = resources.getString(
            if (forOrders) R.string.snackbar_offline_blocked_orders else R.string.snackbar_offline_blocked_change,
        )
        // 여러 번 눌러도 하나만 띄운다.
        snacks = snacks.push(Snack(OFFLINE_BLOCKED_KEY, SnackKind.INFO, message, critical = true))
    }

    // 계속 떠 있는 알림: 상태에 맞춰 넣고 뺀다.
    LaunchedEffect(state.offline) {
        snacks = if (state.offline) {
            snacks.pushToBottom(Snack(CONNECTION_KEY, SnackKind.CONNECTION))
        } else {
            snacks.dismiss(CONNECTION_KEY).dismiss(OFFLINE_BLOCKED_KEY)
        }
    }
    LaunchedEffect(state.newOrderCount) {
        snacks = if (state.newOrderCount > 0) {
            snacks.push(Snack(NEW_ORDER_KEY, SnackKind.NEW_ORDER))
        } else {
            snacks.dismiss(NEW_ORDER_KEY)
        }
    }
    LaunchedEffect(state.storeStatus) {
        snacks = if (state.storeStatus == StoreStatus.Open) {
            snacks.dismiss(STORE_KEY)
        } else {
            snacks.push(Snack(STORE_KEY, SnackKind.STORE))
        }
    }
    LaunchedEffect(soundOff) {
        // 소리가 돌아오면 다음에 꺼졌을 때 다시 알려준다.
        if (!soundOff) soundOffDismissed = false
        snacks = if (soundOff && !soundOffDismissed) {
            snacks.push(Snack(SOUND_OFF_KEY, SnackKind.SOUND_OFF))
        } else {
            snacks.dismiss(SOUND_OFF_KEY)
        }
    }
    LaunchedEffect(state.undo?.orderNo, state.undo?.movedAt) {
        val cleared = snacks.dismiss(UNDO_KEY)
        snacks = if (state.undo != null) cleared.push(Snack(UNDO_KEY, SnackKind.UNDO)) else cleared
    }

    // 일회성 이벤트
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is OrderEvent.MenuDeleted -> {
                    snackSeq += 1
                    snacks = snacks.push(
                        Snack(
                            key = "snack-$snackSeq",
                            kind = SnackKind.MENU_DELETED,
                            message = resources.getString(R.string.snackbar_menu_deleted, event.removed.item.name),
                            removedMenu = event.removed,
                        ),
                    )
                }
                is OrderEvent.Accepted ->
                    showSnack(resources.getString(R.string.snackbar_accepted, event.pickupNo))
                is OrderEvent.Rejected ->
                    showSnack(resources.getString(R.string.snackbar_rejected, event.pickupNo), critical = true)
                is OrderEvent.PickedUp ->
                    showSnack(resources.getString(R.string.snackbar_picked_up, event.pickupNo))
                is OrderEvent.NoShowed ->
                    showSnack(resources.getString(R.string.snackbar_no_show, event.pickupNo))
                is OrderEvent.AutoCanceled ->
                    showSnack(resources.getString(R.string.snackbar_auto_canceled, event.pickupNo), critical = true)
                is OrderEvent.AutoCancelSoon -> sound.playAlert()
                OrderEvent.StoreStarted -> showSnack(resources.getString(R.string.snackbar_store_started))
                is OrderEvent.StoreResumed -> {
                    showSnack(resources.getString(R.string.snackbar_store_resumed))
                    // 자리를 비웠을 수 있어 자동으로 열리면 소리로도 알린다.
                    if (event.automatic) sound.playAlert()
                }
                OrderEvent.OrdersNotAccepted ->
                    showSnack(resources.getString(R.string.snackbar_not_accepting), critical = true)
                is OrderEvent.Refunded -> showSnack(
                    resources.getString(R.string.snackbar_refunded, event.pickupNo, formatWon(event.amount)),
                )
                is OrderEvent.SoldOutInPendingOrders -> showSnack(
                    resources.getString(R.string.snackbar_sold_out_pending, event.count),
                    critical = true,
                    viewOrders = true,
                )
                is OrderEvent.BlockedOffline -> showOfflineBlocked(event.forOrders)
                OrderEvent.Reconnected -> showSnack(resources.getString(R.string.snackbar_reconnected))
                is OrderEvent.PaymentCancelFailed -> showSnack(
                    resources.getString(R.string.snackbar_payment_failed, event.pickupNo),
                    critical = true,
                    retryOrderNo = event.orderNo,
                )
            }
        }
    }

    val notReadyMessage = stringResource(R.string.not_ready_yet)
    val menuLockedMessage = stringResource(R.string.menu_locked_while_open)

    Box(
        Modifier
            .fillMaxSize()
            .background(OrmoColors.BackgroundDefault),
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            containerColor = OrmoColors.BackgroundDefault,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopBar(
                    selectedTab = tab,
                    onSelectTab = {
                        if (it == ManagerTab.SALES && tab != ManagerTab.SALES) {
                            wantsSales = true
                            // 메뉴 수정에서 풀어 둔 상태로는 넘어가지 않고 다시 묻는다.
                            pinViewModel.lock()
                            pinViewModel.request()
                        } else {
                            tab = it
                        }
                    },
                    pendingCount = state.pending.size,
                    now = now,
                    soundOff = soundOff,
                    storeStatus = state.storeStatus,
                    onStatusClick = { statusMenuOpen = !statusMenuOpen },
                    onStatusPositioned = { statusButtonX = it },
                )
            },
            // 고정 푸터: 목록 스크롤과 무관하게 항상 화면 맨 아래에 있다.
            bottomBar = {
                // 테스트 주문 버튼은 개발(debug) 빌드에만 보인다.
                val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                val addTestOrder: (() -> Unit)? = if (debuggable) viewModel::addTestOrder else null
                BusinessFooter(info = businessInfo, onAddTestOrder = addTestOrder)
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                if (tab == ManagerTab.SETTINGS) {
                    SettingsScreen(
                        businessHours = businessHours,
                        onSaveBusinessHours = {
                            if (state.offline) {
                                showOfflineBlocked(forOrders = false)
                            } else {
                                settingsViewModel.saveBusinessHours(it)
                            }
                        },
                        storeStatus = state.storeStatus,
                        onCloseStore = viewModel::onCloseClick,
                        onChangePin = pinViewModel::requestChange,
                        onLogout = onLogout,
                        onNotReady = { showSnack(notReadyMessage) },
                    )
                } else if (tab == ManagerTab.SALES) {
                    SalesScreen(
                        orders = allOrders,
                        now = now,
                        monthView = monthView,
                        onMonthViewChange = { monthView = it },
                    )
                } else if (tab == ManagerTab.MENU) {
                    MenuScreen(
                        menus = menus,
                        optionGroups = optionGroups,
                        access = MenuAccess(
                            canEdit = state.storeStatus == StoreStatus.Closed,
                            unlocked = pin.unlocked,
                            onRequestUnlock = pinViewModel::request,
                            onLocked = { showSnack(menuLockedMessage) },
                        ),
                        actions = MenuActions(
                            onSoldOutChange = viewModel::onSoldOutChange,
                            onOptionSoldOutChange = viewModel::onOptionSoldOutChange,
                            onOptionMenuSoldOutChange = viewModel::onOptionMenuSoldOutChange,
                            onSaveMenu = viewModel::onSaveMenu,
                            onDeleteMenu = viewModel::onDeleteMenu,
                            onAddOptionGroup = viewModel::onAddOptionGroup,
                            onEditorOpenChange = { menuEditing = it },
                        ),
                    )
                } else if (tab == ManagerTab.HISTORY) {
                    HistoryScreen(
                        orders = state.history,
                        selectedOrderNo = selectedOrderNo,
                        onSelect = { selectedOrderNo = it },
                        onRefund = viewModel::onRefundClick,
                        offline = state.offline,
                    )
                } else {
                    BoardScreen(
                        pending = state.pending,
                        preparing = state.preparing,
                        ready = state.ready,
                        now = now,
                        actions = BoardActions(
                            onReject = viewModel::onRejectClick,
                            onAccept = viewModel::onAccept,
                            onCooked = viewModel::onCooked,
                            onPickedUp = viewModel::onPickedUp,
                            onNoShow = viewModel::onNoShowClick,
                        ),
                        showNewestPending = showNewestPending,
                        onNewestPendingShown = { showNewestPending = false },
                        storeClosed = state.storeStatus == StoreStatus.Closed,
                        offline = state.offline,
                    )
                }

                // 알림은 왼쪽 아래에 온 순서대로 위로 쌓인다(최대 3개).
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 24.dp, bottom = 24.dp),
                ) {
                    // 마감 안내는 메뉴 편집 패널의 아래 버튼을 가려서, 패널이 열린 동안에는 숨긴다.
                    snacks.asReversed().filterNot { menuEditing && it.kind == SnackKind.STORE }.forEach { snack ->
                        key(snack.key) {
                            val millis = snack.kind.millis
                            if (millis != null) {
                                LaunchedEffect(Unit) {
                                    delay(millis)
                                    snacks = snacks.dismiss(snack.key)
                                }
                            }
                            val dismiss = { snacks = snacks.dismiss(snack.key) }
                            when (snack.kind) {
                                SnackKind.MENU_DELETED -> OrmoSnackbar(
                                    message = snack.message,
                                    icon = SnackIcon.POSITIVE,
                                    onDismiss = dismiss,
                                    actionText = stringResource(R.string.snackbar_undo),
                                    onAction = {
                                        snack.removedMenu?.let(viewModel::onRestoreMenu)
                                        dismiss()
                                    },
                                )
                                SnackKind.CONNECTION -> OrmoSnackbar(
                                    message = stringResource(R.string.snackbar_offline),
                                    icon = SnackIcon.CRITICAL,
                                    swipeable = false,
                                    onDismiss = {},
                                )
                                SnackKind.STORE -> {
                                    val status = state.storeStatus
                                    val until = (status as? StoreStatus.Paused)?.until
                                    OrmoSnackbar(
                                        message = when {
                                            status == StoreStatus.Closed -> stringResource(R.string.snackbar_closed)
                                            until != null ->
                                                stringResource(R.string.snackbar_paused_until, formatClock(until))
                                            else -> stringResource(R.string.snackbar_paused)
                                        },
                                        icon = SnackIcon.NONE,
                                        swipeable = false,
                                        onDismiss = {},
                                        actionText = stringResource(
                                            if (status == StoreStatus.Closed) {
                                                R.string.snackbar_start_store
                                            } else {
                                                R.string.snackbar_take_orders
                                            },
                                        ),
                                        onAction = viewModel::onOpenStore,
                                    )
                                }
                                SnackKind.NEW_ORDER -> {
                                    val onBoard = tab == ManagerTab.ORDERS
                                    OrmoSnackbar(
                                        message = stringResource(R.string.snackbar_new_orders, state.newOrderCount),
                                        icon = SnackIcon.BELL,
                                        ringCount = ringCount,
                                        // 밀거나 [닫기]를 누르면 알림음이 멈춘다.
                                        onDismiss = viewModel::onNewOrdersSeen,
                                        actionText = stringResource(
                                            if (onBoard) R.string.dialog_close else R.string.snackbar_view_orders,
                                        ),
                                        onAction = {
                                            if (!onBoard) {
                                                tab = ManagerTab.ORDERS
                                                showNewestPending = true
                                            }
                                            viewModel.onNewOrdersSeen()
                                        },
                                    )
                                }
                                SnackKind.SOUND_OFF -> OrmoSnackbar(
                                    message = stringResource(R.string.snackbar_sound_off),
                                    icon = SnackIcon.CRITICAL,
                                    onDismiss = {
                                        soundOffDismissed = true
                                        dismiss()
                                    },
                                    actionText = stringResource(R.string.snackbar_sound_on_action),
                                    onAction = {
                                        sound.turnUp()
                                        sound.playAlert()
                                    },
                                )
                                SnackKind.UNDO -> state.undo?.let { undo ->
                                    OrmoSnackbar(
                                        message = stringResource(
                                            R.string.snackbar_cooked,
                                            undo.pickupNo,
                                            undo.secondsLeft,
                                        ),
                                        icon = SnackIcon.POSITIVE,
                                        // 밀어서 닫아도 10초 뒤 확정은 그대로 진행된다.
                                        onDismiss = dismiss,
                                        actionText = stringResource(R.string.snackbar_undo),
                                        onAction = viewModel::onUndoCooked,
                                    )
                                }
                                SnackKind.INFO, SnackKind.ACTION -> OrmoSnackbar(
                                    message = snack.message,
                                    icon = if (snack.critical) SnackIcon.CRITICAL else SnackIcon.POSITIVE,
                                    onDismiss = dismiss,
                                    actionText = when {
                                        snack.viewOrders -> stringResource(R.string.snackbar_view_orders)
                                        snack.orderNo != null -> stringResource(R.string.snackbar_retry)
                                        else -> null
                                    },
                                    onAction = {
                                        if (snack.viewOrders) tab = ManagerTab.ORDERS
                                        snack.orderNo?.let(viewModel::retry)
                                        dismiss()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 영업 상태 메뉴: 모달이 아니라 버튼 바로 아래에 펼치고, 바깥을 누르면 닫는다.
    // (같은 화면 위에 그려야 화면 비율 Density가 유지된다.)
    if (statusMenuOpen) {
        BackHandler { statusMenuOpen = false }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { statusMenuOpen = false } },
        )
        StoreStatusMenu(
            status = state.storeStatus,
            onSelectOpen = {
                statusMenuOpen = false
                viewModel.onOpenStore()
            },
            onSelectPause = {
                statusMenuOpen = false
                viewModel.onPauseClick()
            },
            onSelectClose = {
                statusMenuOpen = false
                viewModel.onCloseClick()
            },
            modifier = Modifier.offset { IntOffset(statusButtonX.roundToInt(), 72.dp.roundToPx()) },
        )
    }

    pin.prompt?.let { prompt ->
        PinDialog(
            prompt = prompt,
            now = now,
            onSubmit = pinViewModel::submit,
            onClose = {
                wantsSales = false
                pinViewModel.close()
            },
        )
    }

    when (val storeDialog = state.storeDialog) {
        StoreDialog.PauseSetup -> PauseDialog(
            onConfirm = viewModel::onPauseConfirm,
            onDismiss = viewModel::onStoreDialogDismiss,
        )
        StoreDialog.CloseConfirm -> CloseStoreDialog(
            onConfirm = viewModel::onCloseConfirm,
            onDismiss = viewModel::onStoreDialogDismiss,
        )
        is StoreDialog.CloseWithPending -> ClosePendingDialog(
            count = storeDialog.count,
            onConfirm = viewModel::onCancelPendingAndClose,
            onDismiss = viewModel::onStoreDialogDismiss,
        )
        null -> Unit
    }

    when (val dialog = state.dialog) {
        is OrderDialog.Reject -> RejectDialog(
            order = dialog.order,
            onConfirm = viewModel::onRejectConfirm,
            onDismiss = viewModel::onDialogDismiss,
        )
        is OrderDialog.Refund -> RefundDialog(
            order = dialog.order,
            onConfirm = viewModel::onRefundConfirm,
            onDismiss = viewModel::onDialogDismiss,
        )
        is OrderDialog.NoShow -> NoShowDialog(
            order = dialog.order,
            onConfirm = viewModel::onNoShowConfirm,
            onDismiss = viewModel::onDialogDismiss,
        )
        is OrderDialog.Expired -> ExpiredDialog(order = dialog.order, onConfirm = viewModel::onDialogDismiss)
        null -> Unit
    }
}

/** 태블릿 소리가 꺼졌는지 1초마다 확인한다. */
@Composable
private fun rememberSoundOff(sound: DeviceSound): State<Boolean> = produceState(sound.isOff(), sound) {
    while (true) {
        delay(1_000L)
        value = sound.isOff()
    }
}

/** 현재 시각. 1초마다 바뀌어 "자동 취소까지 m:ss"를 줄인다. */
@Composable
private fun rememberClock(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(1_000L)
        value = System.currentTimeMillis()
    }
}
