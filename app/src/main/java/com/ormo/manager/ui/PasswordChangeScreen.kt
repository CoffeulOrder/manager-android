package com.ormo.manager.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ormo.manager.R
import com.ormo.manager.domain.isValidPassword
import com.ormo.manager.ui.theme.OrmoColors
import com.ormo.manager.ui.theme.OrmoType

/**
 * 설정의 비밀번호 바꾸기. Figma에 이 화면의 시안은 아직 없어, 첫 로그인(0.2.1)의 입력 양식을 따른다.
 * 입력한 비밀번호는 화면에만 있고, 화면을 닫으면 버려진다.
 */
@Composable
fun PasswordChangeScreen(
    state: PasswordChangeUiState,
    onSubmit: (current: String, new: String, confirm: String) -> Unit,
    onInput: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** 왼쪽 아래에 계속 떠 있는 알림의 높이. 버튼이 가려지지 않게 비운다. */
    bottomInset: Dp = 0.dp,
) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var confirmFocused by remember { mutableStateOf(false) }
    val currentFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val mismatch = isConfirmMismatch(password, confirm, confirmFocused)
    val canSubmit = current.isNotEmpty() && isValidPassword(password) && password == confirm && !state.inProgress
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onSubmit(current, password, confirm)
        }
    }

    BackHandler(onBack = onClose)
    // 현재 비밀번호가 틀리면 그 칸만 비우고 커서를 옮긴다.
    LaunchedEffect(state.rejections) {
        if (state.rejections > 0) {
            current = ""
            currentFocus.requestFocus()
        }
    }

    // 화면보다 낮으면 가운데, 키보드가 올라와 좁아지면 스크롤한다.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .background(OrmoColors.BackgroundDefault)
            .padding(bottom = bottomInset)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(480.dp)) {
            Text(
                stringResource(R.string.settings_password),
                style = OrmoType.HeadlineBold,
                color = OrmoColors.TextStrong,
                modifier = Modifier.padding(bottom = 32.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthField(
                        label = stringResource(R.string.password_current),
                        value = current,
                        onValueChange = {
                            current = it.take(PASSWORD_LIMIT)
                            onInput()
                        },
                        invalid = state.error == PasswordChangeError.WRONG_CURRENT,
                        password = true,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                        modifier = Modifier.focusRequester(currentFocus),
                    )
                    if (state.error == PasswordChangeError.WRONG_CURRENT) {
                        FieldError(stringResource(R.string.password_wrong_current))
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthField(
                        label = stringResource(R.string.password_new),
                        value = password,
                        onValueChange = {
                            password = it.take(PASSWORD_LIMIT)
                            onInput()
                        },
                        invalid = false,
                        password = true,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
                    )
                    PasswordRules(password)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthField(
                        label = stringResource(R.string.password_confirm),
                        value = confirm,
                        onValueChange = {
                            confirm = it.take(PASSWORD_LIMIT)
                            onInput()
                        },
                        invalid = mismatch,
                        password = true,
                        imeAction = ImeAction.Done,
                        onImeAction = submit,
                        onFocusChange = { confirmFocused = it },
                    )
                    if (mismatch) FieldError(stringResource(R.string.password_mismatch))
                    if (state.error == PasswordChangeError.UNAVAILABLE) {
                        FieldError(stringResource(R.string.password_failed))
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 32.dp)
                    .fillMaxWidth(),
            ) {
                ActionButton(
                    text = stringResource(R.string.dialog_close),
                    tone = ButtonTone.NEUTRAL_WEAK,
                    onClick = onClose,
                    height = 56.dp,
                    radius = 16.dp,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = stringResource(R.string.password_submit),
                    tone = ButtonTone.BRAND,
                    onClick = submit,
                    enabled = canSubmit,
                    height = 56.dp,
                    radius = 16.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
