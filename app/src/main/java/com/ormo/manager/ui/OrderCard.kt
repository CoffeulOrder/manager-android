package com.ormo.manager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.Order
import com.ormo.manager.domain.PickupMethod
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

@Composable
fun pickupMethodText(method: PickupMethod): String = stringResource(
    when (method) {
        PickupMethod.DINE_IN -> R.string.method_dine_in
        PickupMethod.TAKEOUT -> R.string.method_takeout
    },
)

/** 카드 맨 아래의 빈 여백 */
internal val OrderCardBottomPadding = 20.dp

/**
 * Figma "Order Card". 머리 = 픽업 번호 + 수령 방법·이름 + 오른쪽 상태, 본문 = 메뉴, 아래 = 버튼.
 * 금액은 카드에 보이지 않는다. 하단 버튼 영역은 열마다 다르므로 [actions] 슬롯으로 받는다.
 */
@Composable
fun OrderCard(
    order: Order,
    statusText: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
    /** 고객이 취소한 모습: 메뉴에 취소선, 버튼 없음 */
    canceled: Boolean = false,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(OrmoColors.BackgroundDefault, RoundedCornerShape(16.dp)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = order.pickupNo.toString(),
                style = OrmoType.DisplayMdBold,
                color = OrmoColors.TextStrong,
            )
            Column {
                // 컵을 고를 때 바로 보이도록 테이크아웃을 진하게 표시한다.
                Text(
                    text = pickupMethodText(order.pickupMethod),
                    style = OrmoType.BodyLgBold,
                    color = if (order.pickupMethod == PickupMethod.TAKEOUT) {
                        OrmoColors.TextPrimary
                    } else {
                        OrmoColors.TextSecondary
                    },
                    maxLines = 1,
                )
                Text(
                    text = order.customerNickname,
                    style = OrmoType.BodySmRegular,
                    color = OrmoColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = statusText,
                style = OrmoType.BodyMdMedium,
                color = statusColor,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            order.lines.forEach { MenuItemBlock(it, struck = canceled) }
        }

        if (canceled) {
            Spacer(Modifier.height(16.dp))
            return@Column
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = OrderCardBottomPadding),
            content = actions,
        )
    }
}
