package com.ormo.manager.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.OrderLine
import com.ormo.manager.domain.RefundReason
import com.ormo.manager.domain.RejectReason
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

/** DS Action Button. 색 조합만 바꿔 쓴다. */
enum class ButtonTone(val background: Color, val content: Color) {
    BRAND(OrmoColors.FillBrand, OrmoColors.TextOnFill),
    NEUTRAL(OrmoColors.FillNeutral, OrmoColors.TextOnFill),
    NEUTRAL_WEAK(OrmoColors.FillNeutralWeak, OrmoColors.TextPrimary),
    DANGER(OrmoColors.FillDanger, OrmoColors.TextOnFill),
    DANGER_WEAK(OrmoColors.FillDangerWeak, OrmoColors.TextDanger),
}

@Composable
fun ActionButton(
    text: String,
    tone: ButtonTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    radius: Dp = 14.dp,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(radius))
            // DS Disabled: 색과 관계없이 연한 회색 바탕 + 흐린 글자
            .background(if (enabled) tone.background else OrmoColors.FillNeutralWeak)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            style = OrmoType.BodyLgSemibold,
            color = if (enabled) tone.content else OrmoColors.Placeholder,
            maxLines = 1,
        )
    }
}

/** DS Chip L(40): 고른 칩은 진한 회색 */
@Composable
fun SelectChip(
    text: String,
    selected: Boolean,
    textStyle: TextStyle = OrmoType.BodySmMedium,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) OrmoColors.FillNeutral else OrmoColors.FillNeutralWeak)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            style = textStyle,
            color = if (selected) OrmoColors.TextOnFill else OrmoColors.TextPrimary,
            maxLines = 1,
        )
    }
}

/** 메뉴 한 줄: "카페라떼 ×1" + "ㄴ HOT · 오리지널 원두". [soldOut]이면 이름 옆에 "품절" */
@Composable
fun MenuItemBlock(line: OrderLine, modifier: Modifier = Modifier, soldOut: Boolean = false) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.menu_line, line.name, line.qty),
                style = OrmoType.BodyLgSemibold,
                color = OrmoColors.TextPrimary,
            )
            if (soldOut) {
                Text(
                    text = stringResource(R.string.menu_sold_out),
                    style = OrmoType.BodyLgSemibold,
                    color = OrmoColors.TextDanger,
                )
            }
        }
        if (line.options.isNotEmpty()) {
            Text(
                text = stringResource(R.string.option_line, line.options.joinToString(" · ")),
                style = OrmoType.BodyMdRegular,
                color = OrmoColors.TextSecondary,
            )
        }
    }
}

/** DS Result(Empty): 원형 바탕(60) + 아이콘 + 제목 + (선택) 설명 */
@Composable
fun EmptyResult(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier.padding(start = 40.dp, end = 40.dp, top = 32.dp, bottom = 40.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(60.dp)
                .background(OrmoColors.FillNeutralWeak, CircleShape),
        ) {
            // 아이콘은 벡터의 원래 크기로 그린다.
            Image(painter = painterResource(icon), contentDescription = null)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = OrmoType.BodyLgSemibold,
                color = OrmoColors.TextStrong,
                textAlign = TextAlign.Center,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = OrmoType.BodyMdRegular,
                    color = OrmoColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** DS Badge: 상태를 알려주는 알약 모양 라벨 */
@Composable
fun StatusBadge(text: String, background: Color, content: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minHeight = 28.dp)
            .background(background, CircleShape)
            .padding(horizontal = 12.dp),
    ) {
        Text(text = text, style = OrmoType.BodySmMedium, color = content, maxLines = 1)
    }
}

fun rejectReasonRes(reason: RejectReason): Int = when (reason) {
    RejectReason.OUT_OF_STOCK -> R.string.reason_out_of_stock
    RejectReason.OVERLOAD -> R.string.reason_overload
    RejectReason.CLOSING_SOON -> R.string.reason_closing_soon
    RejectReason.STORE_ISSUE -> R.string.reason_store_issue
}

/** 표·상세처럼 좁은 곳에서 쓰는 짧은 사유 */
fun rejectReasonShortRes(reason: RejectReason): Int =
    if (reason == RejectReason.OUT_OF_STOCK) R.string.reason_out_of_stock_short else rejectReasonRes(reason)

fun refundReasonRes(reason: RefundReason): Int = when (reason) {
    RefundReason.WRONG_DRINK -> R.string.refund_wrong_drink
    RefundReason.HYGIENE -> R.string.refund_hygiene
    RefundReason.OTHER -> R.string.refund_other
}
