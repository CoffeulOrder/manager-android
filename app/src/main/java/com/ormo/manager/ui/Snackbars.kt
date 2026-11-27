package com.ormo.manager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** 스낵바 앞 아이콘 */
enum class SnackIcon { POSITIVE, CRITICAL, BELL, NONE }

/**
 * Figma Snackbar: 어두운 알약 모양, 왼쪽 아이콘 + 문구 + (선택) 버튼.
 * 아래 16에서 떠오르며 나타나고, 왼쪽으로 폭의 30% 넘게 밀면 닫힌다.
 *
 * @param ringCount [SnackIcon.BELL]일 때, 값이 바뀔 때마다(알림음이 울릴 때마다) 종이 흔들린다.
 */
@Composable
fun OrmoSnackbar(
    message: String,
    icon: SnackIcon,
    onDismiss: () -> Unit,
    actionText: String? = null,
    onAction: () -> Unit = {},
    ringCount: Int = 0,
    /** false면 밀어도 닫히지 않는다. */
    swipeable: Boolean = true,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val currentDismiss by rememberUpdatedState(onDismiss)
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(250, easing = LinearOutSlowInEasing)) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .offset { IntOffset(offset.roundToInt(), 0) }
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 16.dp.toPx()
            }
            .pointerInput(swipeable) {
                if (!swipeable) return@pointerInput
                detectHorizontalDragGestures(
                    onDragEnd = { if (-offset > size.width * 0.3f) currentDismiss() else offset = 0f },
                    onDragCancel = { offset = 0f },
                ) { _, amount -> offset = (offset + amount).coerceAtMost(0f) }
            }
            .height(if (actionText == null) 48.dp else 52.dp)
            .background(OrmoColors.FillNeutral, CircleShape)
            .padding(
                start = if (icon == SnackIcon.NONE) 20.dp else 16.dp,
                end = if (actionText == null) 20.dp else 12.dp,
            ),
    ) {
        if (icon != SnackIcon.NONE) Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
            if (icon == SnackIcon.BELL) {
                Bell(ringCount)
            } else {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(Color.White, CircleShape),
                )
                Icon(
                    painter = painterResource(
                        if (icon == SnackIcon.CRITICAL) R.drawable.ic_alert else R.drawable.ic_check_circle,
                    ),
                    contentDescription = null,
                    tint = if (icon == SnackIcon.CRITICAL) OrmoColors.TextDanger else Color.Unspecified,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(message, style = OrmoType.BodyMdSemibold, color = OrmoColors.TextOnFill)
        if (actionText != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OrmoColors.SnackbarAction)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(actionText, style = OrmoType.BodySmSemibold, color = OrmoColors.TextOnFill)
            }
        }
    }
}

/** 종 흔들림: 0 → −14° → 14° → −8° → 0 (80ms씩), 1초 뒤 한 번 더 */
@Composable
private fun Bell(ringCount: Int) {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(ringCount) {
        repeat(2) { round ->
            if (round == 1) delay(1_000L)
            for (target in listOf(-14f, 14f, -8f, 0f)) {
                angle.animateTo(target, tween(80, easing = LinearEasing))
            }
        }
    }
    Image(
        painter = painterResource(R.drawable.ic_bell_ring),
        contentDescription = null,
        modifier = Modifier.graphicsLayer { rotationZ = angle.value },
    )
}
