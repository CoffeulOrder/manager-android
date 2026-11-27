package com.ormo.manager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

/** Figma "PIN Dialog"(376): 제목 · 오류 문구 · 점 4개 · 숫자 키(왼쪽 아래 [닫기], 오른쪽 아래 [지우기]) */
@Composable
fun PinDialog(
    prompt: PinPrompt,
    now: Long,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
) {
    var digits by remember(prompt) { mutableStateOf("") }
    val errors = prompt.errors
    val lockedSeconds = (prompt as? PinPrompt.Verify)?.lockedUntil
        ?.let { ((it - now + 999) / 1000).toInt() }
        ?.takeIf { it > 0 }

    // 틀리면 점이 좌우로 흔들린다.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errors) {
        if (errors > 0) {
            for (target in listOf(-10f, 10f, -7f, 7f, -4f, 0f)) shake.animateTo(target, tween(50))
        }
    }

    DialogSurface(width = 376.dp, onDismiss = onClose) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        when (prompt) {
                            is PinPrompt.Enter, is PinPrompt.ChangeCurrent -> R.string.pin_enter_title
                            is PinPrompt.Create -> R.string.pin_create_title
                            is PinPrompt.ChangeNew -> R.string.pin_new_title
                            is PinPrompt.Confirm, is PinPrompt.ChangeConfirm -> R.string.pin_confirm_title
                        },
                    ),
                    style = OrmoType.TitleBold,
                    color = OrmoColors.TextStrong,
                    textAlign = TextAlign.Center,
                )
                val message = when {
                    lockedSeconds != null -> stringResource(R.string.pin_locked, lockedSeconds)
                    errors > 0 && digits.isEmpty() -> stringResource(R.string.pin_mismatch)
                    else -> null
                }
                if (message != null) {
                    Text(message, style = OrmoType.BodyMdMedium, color = OrmoColors.TextDanger)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.offset(x = shake.value.dp),
            ) {
                repeat(PIN_LENGTH) { index ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(
                                if (index < digits.length) OrmoColors.FillNeutral else OrmoColors.FillNeutralWeak,
                                CircleShape,
                            ),
                    )
                }
            }

            val enabled = lockedSeconds == null
            val press: (Char) -> Unit = { digit ->
                if (digits.length < PIN_LENGTH) {
                    digits += digit
                    if (digits.length == PIN_LENGTH) {
                        val entered = digits
                        digits = ""
                        onSubmit(entered)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("123", "456", "789").forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { digit ->
                            Key(digit.toString(), enabled, Modifier.weight(1f)) { press(digit) }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 잠금 중에도 [닫기]는 눌린다.
                    Key(stringResource(R.string.dialog_close), true, Modifier.weight(1f), onClose)
                    Key("0", enabled, Modifier.weight(1f)) { press('0') }
                    Key(stringResource(R.string.pin_delete), enabled, Modifier.weight(1f)) {
                        digits = digits.dropLast(1)
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = text,
            style = OrmoType.BodyXlBold,
            color = if (enabled) OrmoColors.TextPrimary else OrmoColors.Placeholder,
        )
    }
}
