package com.ormo.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.ormo.manager.R
import com.ormo.manager.domain.DEFAULT_PAUSE_MINUTES
import com.ormo.manager.domain.Order
import com.ormo.manager.hideSystemBars
import com.ormo.manager.domain.OrderStatus
import com.ormo.manager.domain.PAUSE_MINUTE_OPTIONS
import com.ormo.manager.domain.PauseReason
import com.ormo.manager.domain.RefundReason
import com.ormo.manager.domain.RejectReason
import com.ormo.manager.domain.formatClock
import com.ormo.manager.domain.formatWon
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

/** 가운데 창 바탕: radius 24, Dim 20% */
@Composable
internal fun DialogSurface(
    width: Dp,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Dialog는 새 창이라 화면 비율에 맞춘 Density가 사라지므로 다시 넘겨준다.
    val density = LocalDensity.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(OrmoColors.Dim)
            // 새 창에서도 시스템 바를 숨긴 상태를 유지한다.
            window?.let(::hideSystemBars)
        }

        CompositionLocalProvider(LocalDensity provides density) {
            Box(
                Modifier
                    .width(width)
                    .background(OrmoColors.BackgroundDefault, RoundedCornerShape(24.dp))
                    .verticalScroll(rememberScrollState()),
            ) {
                content()
            }
        }
    }
}

/** 폭 560 Dialog: 제목·설명 + 내용 + XL 버튼(닫기 / 위험 동작) */
@Composable
internal fun FormDialog(
    title: String,
    description: String?,
    confirmText: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmTone: ButtonTone = ButtonTone.DANGER,
    content: @Composable () -> Unit,
) {
    DialogSurface(width = 560.dp, onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = OrmoType.TitleBold, color = OrmoColors.TextStrong)
                if (description != null) {
                    Text(description, style = OrmoType.BodyLgRegular, color = OrmoColors.TextSecondary)
                }
            }
            content()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(
                    text = stringResource(R.string.dialog_close),
                    tone = ButtonTone.NEUTRAL_WEAK,
                    onClick = onDismiss,
                    height = 56.dp,
                    radius = 16.dp,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = confirmText,
                    tone = confirmTone,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    height = 56.dp,
                    radius = 16.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** DS Alert Dialog(371): 제목 20 + 설명 16 + 버튼 48 */
@Composable
internal fun AlertBox(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    buttons: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    DialogSurface(width = 371.dp, onDismiss = onDismiss, dismissible = dismissible) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = OrmoType.BodyXlBold, color = OrmoColors.TextStrong)
                Text(body, style = OrmoType.BodyMdRegular, color = OrmoColors.TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = buttons)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = OrmoType.BodyMdSemibold, color = OrmoColors.TextSecondary)
}

/** 거절 사유 선택(Figma 1.4.1). 사유를 고르기 전에는 "거절하기"가 꺼져 있다. */
@Composable
fun RejectDialog(
    order: Order,
    onConfirm: (RejectReason, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<RejectReason?>(null) }
    val menus = order.lines.map { it.name }.distinct()
    // 품절을 고르면 메뉴마다 "오늘만 품절로 바꾸기"가 체크된 채로 보인다.
    var unchecked by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val amount = formatWon(order.totalPrice)

    FormDialog(
        title = stringResource(R.string.dialog_reject_title, order.pickupNo),
        description = stringResource(R.string.dialog_reject_description, amount),
        confirmText = stringResource(R.string.dialog_reject_confirm),
        confirmEnabled = selected != null,
        onConfirm = { selected?.let { onConfirm(it, menus - unchecked.toSet()) } },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.dialog_reject_label))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                // RejectReason 선언 순서 = 표시 순서
                RejectReason.entries.forEach { reason ->
                    SelectChip(stringResource(rejectReasonRes(reason)), selected == reason) { selected = reason }
                }
            }
            if (selected == RejectReason.OUT_OF_STOCK) {
                menus.forEach { menu ->
                    CheckRow(
                        text = stringResource(R.string.sold_out_today, menu),
                        checked = menu !in unchecked,
                        onToggle = { unchecked = if (menu in unchecked) unchecked - menu else unchecked + menu },
                    )
                }
            }
        }
        // 고객이 받을 알림 미리보기. 사유를 바꾸면 둘째 줄이 바뀐다.
        selected?.let { CustomerPreview(it, amount) }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    ) {
        val shape = CircleShape
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .then(
                    if (checked) {
                        Modifier.background(OrmoColors.FillBrand, shape)
                    } else {
                        Modifier.border(1.5.dp, OrmoColors.FieldBorder, shape)
                    },
                ),
        ) {
            if (checked) {
                Image(painter = painterResource(R.drawable.ic_check), contentDescription = null)
            }
        }
        Text(text, style = OrmoType.BodyMdRegular, color = OrmoColors.TextPrimary)
    }
}

@Composable
private fun CustomerPreview(reason: RejectReason, amount: String) {
    val bodyRes = when (reason) {
        RejectReason.OUT_OF_STOCK -> R.string.preview_out_of_stock
        RejectReason.OVERLOAD -> R.string.preview_overload
        RejectReason.CLOSING_SOON -> R.string.preview_closing_soon
        RejectReason.STORE_ISSUE -> R.string.preview_store_issue
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(OrmoColors.BackgroundPreview, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(stringResource(R.string.preview_title), style = OrmoType.BodyMdSemibold, color = OrmoColors.TextPrimary)
        Text(stringResource(bodyRes, amount), style = OrmoType.BodyMdRegular, color = OrmoColors.TextSecondary)
    }
}

/**
 * 환불(Figma 2.1.2). 전액만 환불한다. "기타"는 메모를 적어야 한다.
 * PIN/비밀번호는 요구하지 않는다.
 */
@Composable
fun RefundDialog(
    order: Order,
    onConfirm: (RefundReason) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf(RefundReason.WRONG_DRINK) }
    // 메모는 고객 알림용이다. 서버 연동 전이라 보내지 않는다.
    var memo by rememberSaveable { mutableStateOf("") }
    val time = formatClock(order.closedAt ?: order.updatedAt)
    // Figma: "13:30 미수령 정리 · 아이스티 ×1" (옵션은 적지 않는다)
    val first = order.lines.first()
    val firstMenu = stringResource(R.string.menu_line, first.name, first.qty)
    val menu = if (order.lines.size > 1) {
        stringResource(R.string.menu_summary_more, firstMenu, order.lines.size - 1)
    } else {
        firstMenu
    }

    FormDialog(
        title = stringResource(R.string.dialog_refund_title, order.pickupNo),
        description = stringResource(
            if (order.status == OrderStatus.NO_SHOW) R.string.refund_desc_no_show else R.string.refund_desc_completed,
            time,
            menu,
        ),
        confirmText = stringResource(R.string.dialog_refund_confirm, formatWon(order.totalPrice)),
        confirmEnabled = reason != RefundReason.OTHER || memo.isNotBlank(),
        onConfirm = { onConfirm(reason) },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.dialog_refund_label))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                RefundReason.entries.forEach {
                    SelectChip(stringResource(refundReasonRes(it)), reason == it) { reason = it }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.refund_memo_label))
            BasicTextField(
                value = memo,
                onValueChange = { memo = it.take(100) },
                singleLine = true,
                textStyle = OrmoType.BodyMdRegular.copy(color = OrmoColors.TextPrimary),
                decorationBox = { inner ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(1.dp, OrmoColors.FieldBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp),
                    ) {
                        if (memo.isEmpty()) {
                            Text(
                                stringResource(R.string.refund_memo_hint),
                                style = OrmoType.BodyMdRegular,
                                color = OrmoColors.Placeholder,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/** 미수령 정리 확인(Figma 1.5.1) */
@Composable
fun NoShowDialog(order: Order, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertBox(
        title = stringResource(R.string.dialog_no_show_title, order.pickupNo),
        body = stringResource(R.string.dialog_no_show_body),
        onDismiss = onDismiss,
    ) {
        ActionButton(
            text = stringResource(R.string.dialog_close),
            tone = ButtonTone.NEUTRAL_WEAK,
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            text = stringResource(R.string.action_clean_up),
            tone = ButtonTone.DANGER,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 0초에 수락했을 때(Figma 1.4.2). [확인]을 눌러야 닫힌다. */
@Composable
fun ExpiredDialog(order: Order, onConfirm: () -> Unit) {
    AlertBox(
        title = stringResource(R.string.dialog_expired_title, order.pickupNo),
        body = stringResource(R.string.dialog_expired_body),
        onDismiss = {},
        dismissible = false,
    ) {
        ActionButton(
            text = stringResource(R.string.dialog_confirm),
            tone = ButtonTone.BRAND,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 잠시 중지 설정(Figma 3.1.2). 쉬는 시간은 20분이 골라져 있고, 사유를 고르기 전에는 확인이 꺼져 있다. */
@Composable
fun PauseDialog(onConfirm: (Int?, PauseReason) -> Unit, onDismiss: () -> Unit) {
    // "직접 다시 열기"(null)도 저장할 수 있도록 선택지의 위치를 들고 있는다.
    var durationIndex by rememberSaveable {
        mutableStateOf(PAUSE_MINUTE_OPTIONS.indexOf(DEFAULT_PAUSE_MINUTES))
    }
    var reason by rememberSaveable { mutableStateOf<PauseReason?>(null) }

    FormDialog(
        title = stringResource(R.string.pause_title),
        description = stringResource(R.string.pause_description),
        confirmText = stringResource(R.string.pause_confirm),
        confirmEnabled = reason != null,
        confirmTone = ButtonTone.BRAND,
        onConfirm = { reason?.let { onConfirm(PAUSE_MINUTE_OPTIONS[durationIndex], it) } },
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.pause_duration_label))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                PAUSE_MINUTE_OPTIONS.forEachIndexed { index, minutes ->
                    val label = if (minutes == null) {
                        stringResource(R.string.pause_manual)
                    } else {
                        stringResource(R.string.pause_minutes, minutes)
                    }
                    SelectChip(label, durationIndex == index) { durationIndex = index }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.pause_reason_label))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.selectableGroup(),
            ) {
                PauseReason.entries.forEach {
                    SelectChip(stringResource(pauseReasonRes(it)), reason == it) { reason = it }
                }
            }
        }
    }
}

private fun pauseReasonRes(reason: PauseReason): Int = when (reason) {
    PauseReason.BUSY -> R.string.pause_reason_busy
    PauseReason.PREPARING -> R.string.pause_reason_preparing
    PauseReason.STORE_ISSUE -> R.string.pause_reason_store
}

/** 마감 확인(Figma 3.1.4) */
@Composable
fun CloseStoreDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertBox(
        title = stringResource(R.string.close_title),
        body = stringResource(R.string.close_body),
        onDismiss = onDismiss,
    ) {
        ActionButton(
            text = stringResource(R.string.dialog_close),
            tone = ButtonTone.NEUTRAL_WEAK,
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            text = stringResource(R.string.close_confirm),
            tone = ButtonTone.BRAND,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 접수 대기가 남아 있을 때의 마감 확인(Figma 3.1.3) */
@Composable
fun ClosePendingDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertBox(
        title = stringResource(R.string.close_pending_title, count),
        body = stringResource(R.string.close_pending_body),
        onDismiss = onDismiss,
    ) {
        ActionButton(
            text = stringResource(R.string.dialog_close),
            tone = ButtonTone.NEUTRAL_WEAK,
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            text = stringResource(R.string.close_pending_confirm),
            tone = ButtonTone.DANGER,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
        )
    }
}
