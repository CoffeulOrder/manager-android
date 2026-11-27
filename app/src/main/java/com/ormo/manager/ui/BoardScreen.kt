package com.ormo.manager.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.AUTO_CANCEL_MILLIS
import com.ormo.manager.domain.NO_SHOW_MILLIS
import com.ormo.manager.domain.Order
import com.ormo.manager.domain.OrderStatus
import com.ormo.manager.domain.elapsedMinutes
import com.ormo.manager.domain.formatCountdown
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType
import kotlinx.coroutines.launch

/** 자동 취소가 이 시간 이하로 남으면 빨간색으로 알린다. */
private const val ALERT_REMAINING_MILLIS = 2 * 60_000L

class BoardActions(
    val onReject: (String) -> Unit,
    val onAccept: (String) -> Unit,
    val onCooked: (String) -> Unit,
    val onPickedUp: (String) -> Unit,
    val onNoShow: (String) -> Unit,
)

/** 주문 보드(Figma 1.1.1): 접수 대기 | 제조중 | 픽업 대기 */
@Composable
fun BoardScreen(
    pending: List<Order>,
    preparing: List<Order>,
    ready: List<Order>,
    now: Long,
    actions: BoardActions,
    modifier: Modifier = Modifier,
    /** [주문 보기]로 왔을 때 접수 대기의 맨 아래(새 주문)까지 스크롤한다. */
    showNewestPending: Boolean = false,
    onNewestPendingShown: () -> Unit = {},
    /** 마감 중에는 접수 대기의 빈 문구가 다르다. */
    storeClosed: Boolean = false,
    /** 인터넷이 끊기면 카드 버튼을 흐리게 보인다. */
    offline: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .background(OrmoColors.BackgroundDefault)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        OrderColumn(
            title = stringResource(R.string.column_pending),
            orders = pending,
            // 손이 가야 하는 접수 대기만 1건 이상일 때 숫자를 파랗게 표시한다.
            countColor = if (pending.isEmpty()) OrmoColors.TextTertiary else OrmoColors.TextBrand,
            emptyIcon = R.drawable.ic_note,
            emptyText = stringResource(
                if (storeClosed) R.string.empty_pending_closed else R.string.empty_pending,
            ),
            scrollToEnd = showNewestPending,
            onScrolledToEnd = onNewestPendingShown,
        ) { order -> PendingCard(order, now, actions, offline) }

        OrderColumn(
            title = stringResource(R.string.column_preparing),
            orders = preparing,
            countColor = OrmoColors.TextTertiary,
            emptyIcon = R.drawable.ic_cup,
            emptyText = stringResource(R.string.empty_preparing),
        ) { order -> PreparingCard(order, now, actions, offline) }

        OrderColumn(
            title = stringResource(R.string.column_ready),
            orders = ready,
            countColor = OrmoColors.TextTertiary,
            emptyIcon = R.drawable.ic_tag,
            emptyText = stringResource(R.string.empty_ready),
        ) { order -> ReadyCard(order, now, actions, offline) }
    }
}

@Composable
private fun PendingCard(order: Order, now: Long, actions: BoardActions, offline: Boolean) {
    if (order.status == OrderStatus.CUSTOMER_CANCELED) {
        OrderCard(
            order = order,
            statusText = stringResource(R.string.status_customer_canceled),
            statusColor = OrmoColors.TextDanger,
            canceled = true,
        ) {}
        return
    }
    val remaining = (order.createdAt + AUTO_CANCEL_MILLIS - now).coerceIn(0L, AUTO_CANCEL_MILLIS)
    OrderCard(
        order = order,
        statusText = stringResource(R.string.auto_cancel_in, formatCountdown(remaining)),
        statusColor = if (remaining <= ALERT_REMAINING_MILLIS) OrmoColors.TextDanger else OrmoColors.TextBrand,
    ) {
        ActionButton(
            text = stringResource(R.string.action_reject),
            tone = ButtonTone.NEUTRAL_WEAK,
            onClick = { actions.onReject(order.orderNo) },
            dimmed = offline,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            text = stringResource(R.string.action_accept),
            tone = ButtonTone.BRAND,
            onClick = { actions.onAccept(order.orderNo) },
            dimmed = offline,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PreparingCard(order: Order, now: Long, actions: BoardActions, offline: Boolean) {
    val minutes = elapsedMinutes(order.acceptedAt ?: order.updatedAt, now).toInt()
    OrderCard(
        order = order,
        statusText = when {
            minutes < 1 -> stringResource(R.string.elapsed_just_now)
            minutes < 60 -> stringResource(R.string.elapsed_minutes, minutes)
            else -> stringResource(R.string.elapsed_hours, minutes / 60)
        },
        statusColor = OrmoColors.TextTertiary,
    ) {
        ActionButton(
            text = stringResource(R.string.action_cooked),
            tone = ButtonTone.NEUTRAL,
            onClick = { actions.onCooked(order.orderNo) },
            dimmed = offline,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReadyCard(order: Order, now: Long, actions: BoardActions, offline: Boolean) {
    val readyAt = order.readyAt ?: order.updatedAt
    val overdue = now - readyAt >= NO_SHOW_MILLIS
    val minutes = elapsedMinutes(readyAt, now).toInt()
    OrderCard(
        order = order,
        statusText = when {
            overdue -> stringResource(R.string.pickup_overdue)
            minutes < 1 -> stringResource(R.string.ready_just_now)
            else -> stringResource(R.string.ready_minutes, minutes)
        },
        statusColor = if (overdue) OrmoColors.TextDanger else OrmoColors.TextTertiary,
    ) {
        // [정리하기]는 미수령 기준 시간이 지나야 나타난다.
        if (overdue) {
            ActionButton(
                text = stringResource(R.string.action_clean_up),
                tone = ButtonTone.NEUTRAL_WEAK,
                onClick = { actions.onNoShow(order.orderNo) },
                dimmed = offline,
                modifier = Modifier.weight(1f),
            )
        }
        ActionButton(
            text = stringResource(R.string.action_picked_up),
            tone = ButtonTone.NEUTRAL,
            onClick = { actions.onPickedUp(order.orderNo) },
            dimmed = offline,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowScope.OrderColumn(
    title: String,
    orders: List<Order>,
    countColor: Color,
    @DrawableRes emptyIcon: Int,
    emptyText: String,
    scrollToEnd: Boolean = false,
    onScrolledToEnd: () -> Unit = {},
    card: @Composable (Order) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(OrmoColors.BackgroundLower, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp),
        ) {
            Text(text = title, style = OrmoType.BodyXlBold, color = OrmoColors.TextPrimary)
            Text(text = orders.size.toString(), style = OrmoType.BodyXlBold, color = countColor)
        }

        if (orders.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp),
            ) {
                EmptyResult(icon = emptyIcon, title = emptyText)
            }
        } else {
            ScrollableCards(title, orders, scrollToEnd, onScrolledToEnd, card)
        }
    }
}

/** 열마다 따로 스크롤한다. 아래에 안 보이는 카드가 있으면 [↓] 버튼을 띄운다(Figma 1.1.5). */
@Composable
private fun ScrollableCards(
    title: String,
    orders: List<Order>,
    scrollToEnd: Boolean,
    onScrolledToEnd: () -> Unit,
    card: @Composable (Order) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(scrollToEnd) {
        if (scrollToEnd) {
            listState.animateScrollToItem(orders.lastIndex)
            onScrolledToEnd()
        }
    }
    // 아래 여백만 넘치는 경우는 빼고, 마지막 카드가 다 보이지 않을 때만 버튼을 띄운다.
    val hasHiddenCards by remember(orders.size) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last != null &&
                (last.index < orders.lastIndex || last.offset + last.size > info.viewportEndOffset)
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // 끝까지 내리면 마지막 카드가 버튼에 가려지지 않게 아래 여백을 둔다.
            contentPadding = PaddingValues(bottom = if (orders.size > 1) 64.dp else 0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(orders, key = { it.orderNo }) { order ->
                // 빠지는 카드는 400ms 동안 흐려진다.
                Box(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = tween(400))) { card(order) }
            }
        }
        AnimatedVisibility(
            visible = hasHiddenCards,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
        ) {
            val label = stringResource(R.string.scroll_down, title)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrmoColors.BackgroundDefault)
                    .background(OrmoColors.FillBrandWeak)
                    .clickable(role = Role.Button, onClickLabel = label) {
                        scope.launch { listState.animateScrollToItem(orders.lastIndex) }
                    },
            ) {
                Image(painter = painterResource(R.drawable.ic_arrow_down), contentDescription = label)
            }
        }
    }
}
