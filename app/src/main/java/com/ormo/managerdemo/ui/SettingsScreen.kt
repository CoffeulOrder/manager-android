package com.ormo.managerdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ormo.managerdemo.R
import com.ormo.managerdemo.domain.BusinessHours
import com.ormo.managerdemo.domain.DayHours
import com.ormo.managerdemo.domain.HoursDay
import com.ormo.managerdemo.domain.StoreStatus
import com.ormo.managerdemo.domain.formatTime
import com.ormo.managerdemo.domain.formatTimeInput
import com.ormo.managerdemo.domain.parseTime
import com.ormo.managerdemo.domain.summary
import com.ormo.managerdemo.ui.theme.OrmoColors
import com.ormo.managerdemo.ui.theme.OrmoType

private enum class SettingsDialog { HOURS, LOGOUT_BLOCKED, LOGOUT_CONFIRM }

/** 설정(Figma 4.1.1). 비밀번호 바꾸기 화면은 아직 없다([onNotReady]). */
@Composable
fun SettingsScreen(
    businessHours: BusinessHours,
    onSaveBusinessHours: (BusinessHours) -> Unit,
    storeStatus: StoreStatus,
    onCloseStore: () -> Unit,
    onChangePin: () -> Unit,
    onLogout: () -> Unit,
    onNotReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .background(OrmoColors.BackgroundLower)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Section(stringResource(R.string.settings_section_store)) {
            // 알림음은 고를 수 없어서 누를 수도, 화살표도 없다.
            SettingRow(
                title = stringResource(R.string.settings_alert_sound),
                value = stringResource(R.string.settings_alert_default),
            )
            SettingRow(
                title = stringResource(R.string.settings_hours),
                value = hoursSummaryText(businessHours),
                onClick = { dialog = SettingsDialog.HOURS },
            )
        }
        Section(stringResource(R.string.settings_section_account)) {
            SettingRow(stringResource(R.string.settings_password), chevron = true, onClick = onNotReady)
            SettingRow(stringResource(R.string.settings_pin), chevron = true, onClick = onChangePin)
            SettingRow(
                title = stringResource(R.string.settings_logout),
                chevron = true,
                onClick = {
                    // 영업 중 · 잠시 중지에는 로그아웃할 수 없다(주문을 아무도 못 보는 상황을 막는다).
                    dialog = if (storeStatus == StoreStatus.Closed) {
                        SettingsDialog.LOGOUT_CONFIRM
                    } else {
                        SettingsDialog.LOGOUT_BLOCKED
                    }
                },
            )
        }
    }

    val dismiss = { dialog = null }
    when (dialog) {
        SettingsDialog.HOURS -> HoursDialog(
            initial = businessHours,
            onSave = {
                onSaveBusinessHours(it)
                dismiss()
            },
            onDismiss = dismiss,
        )
        SettingsDialog.LOGOUT_BLOCKED -> LogoutDialog(
            title = stringResource(R.string.logout_blocked_title),
            body = stringResource(R.string.logout_blocked_body),
            confirmText = stringResource(R.string.close_confirm),
            onConfirm = {
                dismiss()
                onCloseStore()
            },
            onDismiss = dismiss,
        )
        SettingsDialog.LOGOUT_CONFIRM -> LogoutDialog(
            title = stringResource(R.string.logout_title),
            body = stringResource(R.string.logout_body),
            confirmText = stringResource(R.string.settings_logout),
            onConfirm = {
                dismiss()
                onLogout()
            },
            onDismiss = dismiss,
        )
        null -> Unit
    }
}

@Composable
private fun Section(title: String, rows: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OrmoColors.BackgroundDefault),
    ) {
        Text(
            text = title,
            style = OrmoType.BodyLgSemibold,
            color = OrmoColors.TextSecondary,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        )
        rows()
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp),
    ) {
        Text(title, style = OrmoType.BodyMdMedium, color = OrmoColors.TextPrimary, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = OrmoType.BodyMdRegular, color = OrmoColors.TextSecondary)
        }
        if (chevron) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = OrmoColors.ChevronWeak,
                    modifier = Modifier
                        .size(width = 11.dp, height = 7.dp)
                        .rotate(-90f),
                )
            }
        }
    }
}

@Composable
private fun dayName(day: HoursDay): String = if (day == HoursDay.HOLIDAY) {
    stringResource(R.string.hours_holiday)
} else {
    stringArrayResource(R.array.weekdays_from_monday)[day.ordinal]
}

/** "월~금 08:30~18:00" */
@Composable
private fun hoursSummaryText(hours: BusinessHours): String {
    val summary = hours.summary() ?: return stringResource(R.string.hours_all_closed)
    val days = if (summary.from == summary.to) {
        dayName(summary.from)
    } else {
        stringResource(R.string.hours_day_range, dayName(summary.from), dayName(summary.to))
    }
    return stringResource(
        R.string.hours_summary,
        days,
        formatTime(summary.hours.openMinute),
        formatTime(summary.hours.closeMinute),
    )
}

private data class HoursDraft(val open: String, val close: String, val isOpen: Boolean) {
    /** 저장할 수 있는 값이면 [DayHours], 아니면 null. 쉬는 날의 칸은 고칠 수 없어 늘 올바르다. */
    fun toDayHours(): DayHours? {
        val openMinute = parseTime(open) ?: return null
        val closeMinute = parseTime(close) ?: return null
        if (isOpen && openMinute >= closeMinute) return null
        return DayHours(openMinute, closeMinute, isOpen)
    }
}

/** 영업시간(Figma 4.1.3). [저장하기]를 눌러야 반영되고, [닫기]는 버린다. */
@Composable
private fun HoursDialog(initial: BusinessHours, onSave: (BusinessHours) -> Unit, onDismiss: () -> Unit) {
    var drafts by remember {
        mutableStateOf(
            HoursDay.entries.associateWith {
                val hours = initial.getValue(it)
                HoursDraft(formatTime(hours.openMinute), formatTime(hours.closeMinute), hours.isOpen)
            },
        )
    }

    FormDialog(
        title = stringResource(R.string.settings_hours),
        description = null,
        confirmText = stringResource(R.string.hours_save),
        confirmEnabled = drafts.values.all { it.toDayHours() != null },
        confirmTone = ButtonTone.BRAND,
        onConfirm = { onSave(drafts.mapValues { (_, draft) -> checkNotNull(draft.toDayHours()) }) },
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OrmoColors.BackgroundPreview, RoundedCornerShape(12.dp)),
        ) {
            HoursDay.entries.forEach { day ->
                HoursRow(
                    day = dayName(day),
                    draft = drafts.getValue(day),
                    onChange = { drafts = drafts + (day to it) },
                )
            }
        }
    }
}

@Composable
private fun HoursRow(day: String, draft: HoursDraft, onChange: (HoursDraft) -> Unit) {
    val invalid = draft.toDayHours() == null
    val focusManager = LocalFocusManager.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 20.dp, end = 16.dp),
    ) {
        Text(day, style = OrmoType.BodyLgMedium, color = OrmoColors.TextPrimary, modifier = Modifier.weight(1f))
        TimeField(
            text = draft.open,
            label = stringResource(R.string.hours_open_label, day),
            enabled = draft.isOpen,
            invalid = invalid,
            onChange = { onChange(draft.copy(open = it)) },
        )
        Text(
            "~",
            style = OrmoType.BodyLgRegular,
            color = OrmoColors.TextTertiary,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        TimeField(
            text = draft.close,
            label = stringResource(R.string.hours_close_label, day),
            enabled = draft.isOpen,
            invalid = invalid,
            onChange = { onChange(draft.copy(close = it)) },
        )
        Box(Modifier.padding(start = 12.dp)) {
            SaleSwitch(
                checked = draft.isOpen,
                onCheckedChange = { isOpen ->
                    focusManager.clearFocus()
                    // 쉬는 날로 바꿀 때 쓰다 만 값은 버리고 기본 시각을 넣는다.
                    onChange(
                        if (!isOpen && invalid) {
                            HoursDraft(DEFAULT_OFF_OPEN, DEFAULT_OFF_CLOSE, false)
                        } else {
                            draft.copy(isOpen = isOpen)
                        },
                    )
                },
            )
        }
    }
}

private const val DEFAULT_OFF_OPEN = "10:00"
private const val DEFAULT_OFF_CLOSE = "15:00"

/** 폭 96 · 높이 48 입력칸. 24시간 "08:30". 누르면 전체가 선택돼 바로 새로 칠 수 있다. */
@Composable
private fun TimeField(
    text: String,
    label: String,
    enabled: Boolean,
    invalid: Boolean,
    onChange: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(text)) }
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(14.dp)

    LaunchedEffect(text) {
        if (value.text != text) value = TextFieldValue(text, TextRange(text.length))
    }
    LaunchedEffect(focused) {
        if (focused) value = value.copy(selection = TextRange(0, value.text.length))
    }

    BasicTextField(
        value = value,
        onValueChange = { new ->
            if (new.text == value.text) {
                value = new
            } else {
                // 꽉 찬 칸에 숫자를 더 치면 방금 친 숫자부터 새로 쓴다.
                val overflow = new.text.count { it.isDigit() } > 4
                val input = if (overflow) new.text.take(new.selection.start).takeLast(1) else new.text
                val formatted = formatTimeInput(input)
                value = TextFieldValue(formatted, TextRange(formatted.length))
                onChange(formatted)
            }
        },
        enabled = enabled,
        singleLine = true,
        textStyle = OrmoType.BodyMdRegular.copy(
            color = if (enabled) OrmoColors.TextPrimary else OrmoColors.Placeholder,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .width(96.dp)
                    .height(48.dp)
                    .background(
                        if (enabled) OrmoColors.BackgroundDefault else OrmoColors.FillNeutralWeak,
                        shape,
                    )
                    .border(
                        1.dp,
                        when {
                            !enabled -> OrmoColors.Border
                            invalid && !focused -> OrmoColors.FillDanger
                            focused -> OrmoColors.FillBrand
                            else -> OrmoColors.FieldBorder
                        },
                        shape,
                    )
                    .padding(horizontal = 16.dp),
            ) {
                inner()
            }
        },
    )
}

/** 로그아웃 안내 · 확인(Figma 4.1.4, 4.1.5) */
@Composable
private fun LogoutDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertBox(title = title, body = body, onDismiss = onDismiss) {
        ActionButton(
            text = stringResource(R.string.dialog_close),
            tone = ButtonTone.NEUTRAL_WEAK,
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            text = confirmText,
            tone = ButtonTone.BRAND,
            onClick = onConfirm,
            modifier = Modifier.weight(1f),
        )
    }
}
