package com.ormo.manager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.HistoryFilter
import com.ormo.manager.domain.Order
import com.ormo.manager.domain.OrderStatus
import com.ormo.manager.domain.OrderTransitions
import com.ormo.manager.domain.filterHistory
import com.ormo.manager.domain.formatClock
import com.ormo.manager.domain.formatWon
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

private val NumberColumnWidth = 160.dp
private val NumberColumnWidthNarrow = 140.dp
private val OrderedAtColumnWidth = 120.dp
private val StatusColumnWidth = 140.dp
private val StatusColumnWidthNarrow = 120.dp
private val ProcessedColumnWidth = 200.dp

/**
 * 아래 여백이 20인 화면에서 카드를 알림 위에서 끝내려면 비워야 하는 높이.
 * 화면 여백(20)이 알림의 아래 여백(24)보다 좁아 4만큼 더 비운다.
 */
internal fun cardBottomInset(snackHeight: Dp): Dp = if (snackHeight > 0.dp) snackHeight + 4.dp else 0.dp

/**
 * 오늘 내역(Figma 2.1.x). 필터 칩과 번호 검색, 표, 오른쪽 상세.
 * 상세가 열리면 표는 번호·이름 / 메뉴 / 상태만 남기고 검색 칸은 숨긴다.
 */
@Composable
fun HistoryScreen(
    orders: List<Order>,
    selectedOrderNo: String?,
    onSelect: (String?) -> Unit,
    onRefund: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 인터넷이 끊기면 환불 버튼을 흐리게 보인다. */
    offline: Boolean = false,
    /** 왼쪽 아래에 계속 떠 있는 알림의 높이. 표가 그 위에서 끝나 마지막 줄이 가려지지 않게 한다. */
    bottomInset: Dp = 0.dp,
) {
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = orders.firstOrNull { it.orderNo == selectedOrderNo }
    val visible = filterHistory(orders, filter, query)

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .background(OrmoColors.BackgroundLower)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        // 알림은 표만 가리므로 오른쪽 상세는 줄이지 않는다.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(bottom = cardBottomInset(bottomInset))
                .clip(RoundedCornerShape(16.dp))
                .background(OrmoColors.BackgroundDefault),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .padding(horizontal = 20.dp),
            ) {
                HistoryFilter.entries.forEach {
                    SelectChip(stringResource(filterRes(it)), selected = filter == it) { filter = it }
                }
                Spacer(Modifier.weight(1f))
                if (selected == null) {
                    SearchField(query, onChange = { query = it })
                }
            }

            if (visible.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    EmptyHistory(filter, query)
                }
            } else {
                HistoryTable(visible, selected, onSelect)
            }
        }
        if (selected != null) {
            OrderDetail(order = selected, onClose = { onSelect(null) }, onRefund = onRefund, offline = offline)
        }
    }
}

private fun filterRes(filter: HistoryFilter): Int = when (filter) {
    HistoryFilter.ALL -> R.string.filter_all
    HistoryFilter.COMPLETED -> R.string.filter_completed
    HistoryFilter.CANCELED -> R.string.filter_canceled
    HistoryFilter.NO_SHOW -> R.string.filter_no_show
}

@Composable
private fun EmptyHistory(filter: HistoryFilter, query: String) {
    when {
        query.isNotEmpty() -> EmptyResult(
            icon = R.drawable.ic_note,
            title = stringResource(R.string.search_empty_title, query),
            description = stringResource(R.string.search_empty_body),
        )
        filter == HistoryFilter.ALL -> EmptyResult(
            icon = R.drawable.ic_note,
            title = stringResource(R.string.empty_history_title),
            description = stringResource(R.string.empty_history_body),
        )
        else -> EmptyResult(
            icon = R.drawable.ic_note,
            title = stringResource(
                when (filter) {
                    HistoryFilter.COMPLETED -> R.string.empty_filter_completed
                    HistoryFilter.CANCELED -> R.string.empty_filter_canceled
                    else -> R.string.empty_filter_no_show
                },
            ),
        )
    }
}

/** 픽업 번호 검색. 숫자를 넣을 때마다 바로 거른다. */
@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = { value -> onChange(value.filter(Char::isDigit).take(4)) },
        singleLine = true,
        textStyle = OrmoType.BodyMdRegular.copy(color = OrmoColors.TextPrimary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(335.dp)
                    .height(48.dp)
                    .border(1.dp, OrmoColors.FieldBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                    Image(painter = painterResource(R.drawable.ic_search), contentDescription = null)
                }
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            style = OrmoType.BodyMdRegular,
                            color = OrmoColors.Placeholder,
                        )
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable(role = Role.Button) { onChange("") },
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun HistoryTable(orders: List<Order>, selected: Order?, onSelect: (String) -> Unit) {
    val compact = selected != null
    val numberWidth = if (compact) NumberColumnWidthNarrow else NumberColumnWidth
    val statusWidth = if (compact) StatusColumnWidthNarrow else StatusColumnWidth

    Column {
        // 머리 행은 고정하고 목록만 스크롤한다.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
        ) {
            HeaderCell(stringResource(R.string.header_number_name), numberWidth)
            if (!compact) HeaderCell(stringResource(R.string.header_ordered_at), OrderedAtColumnWidth)
            HeaderCell(stringResource(R.string.header_menu))
            HeaderCell(stringResource(R.string.header_status), statusWidth)
            if (!compact) HeaderCell(stringResource(R.string.header_processed), ProcessedColumnWidth)
        }
        HorizontalDivider(color = OrmoColors.Border)

        LazyColumn(Modifier.fillMaxSize()) {
            items(orders, key = { it.orderNo }) { order ->
                val isSelected = order.orderNo == selected?.orderNo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(if (isSelected) OrmoColors.FillBrandWeak else Color.Transparent)
                        .clickable(role = Role.Button) { onSelect(order.orderNo) },
                ) {
                    Column(
                        modifier = Modifier
                            .width(numberWidth)
                            .padding(horizontal = 20.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.pickup_no, order.pickupNo),
                            style = OrmoType.BodyMdSemibold,
                            color = OrmoColors.TextPrimary,
                        )
                        Text(
                            text = order.customerNickname,
                            style = OrmoType.BodySmRegular,
                            color = OrmoColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!compact) TextCell(formatClock(order.createdAt), OrderedAtColumnWidth)
                    TextCell(menuSummary(order))
                    Box(
                        modifier = Modifier
                            .width(statusWidth)
                            .padding(horizontal = 20.dp),
                    ) {
                        HistoryBadge(order.status)
                    }
                    if (!compact) TextCell(processedText(order), ProcessedColumnWidth)
                }
                HorizontalDivider(color = OrmoColors.Border)
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, width: Dp? = null) {
    CellText(text, width, OrmoType.BodyMdMedium, OrmoColors.TextTertiary)
}

@Composable
private fun RowScope.TextCell(text: String, width: Dp? = null) {
    CellText(text, width, OrmoType.BodyMdRegular, OrmoColors.TextPrimary)
}

/** [width]가 없으면 남는 폭을 채운다. 넘치면 말줄임. */
@Composable
private fun RowScope.CellText(text: String, width: Dp?, style: TextStyle, color: Color) {
    val size = if (width != null) Modifier.width(width) else Modifier.weight(1f)
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = size.padding(horizontal = 20.dp),
    )
}

/** 한 줄이면 "메뉴 ×수량 · 옵션", 두 줄 이상이면 "첫 메뉴 외 N개" */
@Composable
fun menuSummary(order: Order): String {
    val first = order.lines.first()
    val head = stringResource(R.string.menu_line, first.name, first.qty)
    return when {
        order.lines.size > 1 -> stringResource(R.string.menu_summary_more, head, order.lines.size - 1)
        first.options.isEmpty() -> head
        else -> stringResource(R.string.menu_summary_options, head, first.options.joinToString(" · "))
    }
}

/** 표의 "처리" 칸 */
@Composable
private fun processedText(order: Order): String {
    val time = formatClock(order.closedAt ?: order.updatedAt)
    return when (order.status) {
        OrderStatus.COMPLETED -> stringResource(R.string.processed_picked_up, time)
        OrderStatus.NO_SHOW -> stringResource(R.string.processed_cleaned, time)
        OrderStatus.REFUNDED ->
            stringResource(R.string.processed_refunded, formatClock(order.refundedAt ?: order.updatedAt))
        OrderStatus.REJECTED -> order.rejectReason?.let {
            stringResource(R.string.processed_with_reason, time, stringResource(rejectReasonShortRes(it)))
        } ?: time
        OrderStatus.AUTO_CANCELED ->
            stringResource(R.string.processed_with_reason, time, stringResource(R.string.reason_timeout))
        OrderStatus.STORE_CANCELED ->
            stringResource(R.string.processed_with_reason, time, stringResource(R.string.reason_other))
        else -> time
    }
}

private enum class BadgeTone(val background: Color, val content: Color) {
    NEUTRAL(OrmoColors.FillNeutralWeak, OrmoColors.TextSecondary),
    DANGER(OrmoColors.FillDangerWeak, OrmoColors.TextDanger),
    WARNING(OrmoColors.FillWarningWeak, OrmoColors.TextWarning),
}

@Composable
private fun HistoryBadge(status: OrderStatus) {
    val (label, tone) = when (status) {
        OrderStatus.COMPLETED -> R.string.status_completed to BadgeTone.NEUTRAL
        OrderStatus.NO_SHOW -> R.string.status_no_show to BadgeTone.WARNING
        OrderStatus.REJECTED -> R.string.status_rejected to BadgeTone.DANGER
        OrderStatus.AUTO_CANCELED -> R.string.status_auto_canceled to BadgeTone.DANGER
        OrderStatus.CUSTOMER_CANCELED -> R.string.status_customer_canceled to BadgeTone.NEUTRAL
        OrderStatus.STORE_CANCELED -> R.string.status_store_canceled to BadgeTone.DANGER
        else -> R.string.status_refunded to BadgeTone.NEUTRAL
    }
    StatusBadge(stringResource(label), tone.background, tone.content)
}

/** 오른쪽 주문 상세(440): 상태 안내, 메뉴 전체, 처리 기록, 환불하기. 금액·결제 수단은 보여주지 않는다. */
@Composable
private fun OrderDetail(order: Order, onClose: () -> Unit, onRefund: (String) -> Unit, offline: Boolean) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .width(440.dp)
            .fillMaxHeight()
            .background(OrmoColors.BackgroundDefault, RoundedCornerShape(16.dp))
            .padding(24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(order.pickupNo.toString(), style = OrmoType.DisplayMdBold, color = OrmoColors.TextStrong)
            Column(Modifier.weight(1f)) {
                Text(
                    pickupMethodText(order.pickupMethod),
                    style = OrmoType.BodyLgBold,
                    color = OrmoColors.TextPrimary,
                )
                Text(order.customerNickname, style = OrmoType.BodyMdRegular, color = OrmoColors.TextSecondary)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onClose),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.detail_close),
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            StatusCallout(order)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                order.lines.forEach { MenuItemBlock(it, soldOut = it.name in order.soldOutMenus) }
            }
            HorizontalDivider(color = OrmoColors.Border)
            ProcessLog(order)
        }

        // 환불은 픽업 완료 · 미수령 주문만 (거절·취소는 이미 전액 환불됨)
        if (OrderTransitions.canTransition(order.status, OrderStatus.REFUNDED)) {
            ActionButton(
                text = stringResource(R.string.action_refund),
                tone = ButtonTone.DANGER_WEAK,
                onClick = { onRefund(order.orderNo) },
                dimmed = offline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatusCallout(order: Order) {
    val time = formatClock(order.closedAt ?: order.updatedAt)
    when (order.status) {
        OrderStatus.REJECTED -> {
            val reason = order.rejectReason?.let { stringResource(rejectReasonShortRes(it)) }.orEmpty()
            var body = stringResource(R.string.callout_rejected, time, reason, formatWon(order.totalPrice))
            order.soldOutMenus.forEach { body += "\n" + stringResource(R.string.callout_sold_out, it) }
            Callout(stringResource(R.string.status_rejected), body, BadgeTone.DANGER)
        }
        OrderStatus.AUTO_CANCELED -> Callout(
            stringResource(R.string.status_auto_canceled),
            stringResource(R.string.callout_auto_canceled, time),
            BadgeTone.DANGER,
        )
        OrderStatus.CUSTOMER_CANCELED -> Callout(
            stringResource(R.string.status_customer_canceled),
            stringResource(R.string.callout_customer_canceled, time),
            BadgeTone.NEUTRAL,
        )
        OrderStatus.STORE_CANCELED -> Callout(
            stringResource(R.string.status_store_canceled),
            stringResource(R.string.callout_store_canceled, time),
            BadgeTone.DANGER,
        )
        OrderStatus.NO_SHOW -> Callout(
            stringResource(R.string.status_no_show),
            stringResource(R.string.callout_no_show, time),
            BadgeTone.WARNING,
        )
        OrderStatus.REFUNDED -> Callout(
            stringResource(R.string.status_refunded),
            stringResource(
                R.string.callout_refunded,
                formatClock(order.refundedAt ?: order.updatedAt),
                formatWon(order.totalPrice),
            ),
            BadgeTone.NEUTRAL,
        )
        // 픽업 완료는 안내가 없다.
        else -> Unit
    }
}

@Composable
private fun Callout(title: String, body: String, tone: BadgeTone) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.background, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alert),
            contentDescription = null,
            tint = tone.content,
            modifier = Modifier.size(20.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = OrmoType.BodySmBold, color = tone.content)
            Text(body, style = OrmoType.BodySmRegular, color = OrmoColors.TextSecondary)
        }
    }
}

/** 처리 기록. 문제가 된 단계만 빨간색이다. */
@Composable
private fun ProcessLog(order: Order) {
    Column {
        LogRow(R.string.log_ordered, order.createdAt)
        order.acceptedAt?.let { LogRow(R.string.log_accepted, it) }
        order.readyAt?.let { LogRow(R.string.log_cooked, it) }
        val closedAt = order.closedAt
        val closedAs = order.closedAs
        if (closedAt != null && closedAs != null) {
            val (label, problem) = when (closedAs) {
                OrderStatus.COMPLETED -> R.string.log_picked_up to false
                OrderStatus.NO_SHOW -> R.string.log_cleaned to true
                OrderStatus.REJECTED -> R.string.log_rejected to true
                OrderStatus.AUTO_CANCELED -> R.string.log_auto_canceled to true
                OrderStatus.CUSTOMER_CANCELED -> R.string.log_customer_canceled to true
                else -> R.string.log_store_canceled to true
            }
            LogRow(label, closedAt, problem)
        }
        order.refundedAt?.let { LogRow(R.string.log_refunded, it) }
    }
}

@Composable
private fun LogRow(labelRes: Int, time: Long, problem: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(
            stringResource(labelRes),
            style = OrmoType.BodyLgMedium,
            color = OrmoColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatClock(time),
            style = OrmoType.BodyMdRegular,
            color = if (problem) OrmoColors.TextDanger else OrmoColors.TextSecondary,
        )
    }
}
