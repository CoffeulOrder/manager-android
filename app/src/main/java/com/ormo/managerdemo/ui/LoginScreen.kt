package com.ormo.managerdemo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ormo.managerdemo.R
import com.ormo.managerdemo.domain.BusinessInfo
import com.ormo.managerdemo.domain.hasLetterAndDigit
import com.ormo.managerdemo.domain.isLongEnoughPassword
import com.ormo.managerdemo.domain.isValidPassword
import com.ormo.managerdemo.ui.theme.OrmoColors
import com.ormo.managerdemo.ui.theme.OrmoType
import kotlinx.coroutines.delay

private const val ID_LIMIT = 50
private const val PASSWORD_LIMIT = 64

/** 로그인 전 화면. 사업자 정보 푸터는 여기에서도 항상 보인다. */
@Composable
fun AuthScreen(viewModel: LoginViewModel, businessInfo: BusinessInfo) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(OrmoColors.BackgroundDefault)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = OrmoColors.BackgroundDefault,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { BusinessFooter(info = businessInfo) },
    ) { padding ->
        // 화면보다 낮으면 가운데, 키보드가 올라와 좁아지면 스크롤한다.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(480.dp)) {
                if (state.step == AuthStep.CHANGE_PASSWORD) {
                    ChangePasswordForm(state, viewModel::changePassword)
                } else {
                    SignInForm(state, viewModel::login, viewModel::onInputChange, viewModel::onTick)
                }
            }
        }
    }
}

/** Figma 0.1.1 로그인 · 0.1.2 실패 · 0.1.3 5번 실패 */
@Composable
private fun ColumnScope.SignInForm(
    state: LoginUiState,
    onLogin: (String, String) -> Unit,
    onInputChange: () -> Unit,
    onTick: () -> Unit,
) {
    var id by remember { mutableStateOf("") }
    // 비밀번호는 화면에만 둔다(rememberSaveable로 저장하지 않는다).
    var password by remember { mutableStateOf("") }
    val passwordFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val lockedUntil = state.lockedUntil
    // 남은 초를 1초마다 줄인다.
    val now by produceState(System.currentTimeMillis(), lockedUntil) {
        value = System.currentTimeMillis()
        while (lockedUntil != null) {
            delay(200L)
            value = System.currentTimeMillis()
            onTick()
        }
    }
    val lockedSeconds = lockedUntil?.let { ((it - now + 999) / 1000).toInt() }?.takeIf { it > 0 }
    val canSubmit = id.isNotBlank() && password.isNotEmpty() && !state.inProgress && lockedSeconds == null
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onLogin(id, password)
        }
    }

    // 틀리면 아이디는 두고 비밀번호만 비운 뒤 커서를 옮긴다.
    LaunchedEffect(state.rejections) {
        if (state.rejections > 0) {
            password = ""
            passwordFocus.requestFocus()
        }
    }

    Head(stringResource(R.string.login_title))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AuthField(
            label = stringResource(R.string.login_id),
            value = id,
            onValueChange = {
                id = it.take(ID_LIMIT)
                onInputChange()
            },
            invalid = state.error == LoginError.REJECTED || state.error == LoginError.LOCKED,
            password = false,
            imeAction = ImeAction.Next,
            onImeAction = { passwordFocus.requestFocus() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthField(
                label = stringResource(R.string.login_password),
                value = password,
                onValueChange = {
                    password = it.take(PASSWORD_LIMIT)
                    onInputChange()
                },
                invalid = state.error == LoginError.REJECTED || state.error == LoginError.LOCKED,
                password = true,
                imeAction = ImeAction.Done,
                onImeAction = submit,
                modifier = Modifier.focusRequester(passwordFocus),
            )
            when (state.error) {
                LoginError.REJECTED -> FieldError(stringResource(R.string.login_rejected))
                LoginError.LOCKED -> FieldError(stringResource(R.string.login_locked))
                LoginError.UNAVAILABLE -> FieldError(stringResource(R.string.login_unavailable))
                null -> Unit
            }
        }
    }
    ActionButton(
        text = if (lockedSeconds != null) {
            stringResource(R.string.login_locked_button, lockedSeconds)
        } else {
            stringResource(R.string.login_button)
        },
        tone = ButtonTone.BRAND,
        onClick = submit,
        enabled = canSubmit,
        height = 56.dp,
        radius = 16.dp,
        modifier = Modifier
            .padding(top = 32.dp)
            .fillMaxWidth(),
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(top = 8.dp)
            .height(40.dp),
    ) {
        Text(
            stringResource(R.string.login_help),
            style = OrmoType.BodyMdRegular,
            color = OrmoColors.TextTertiary,
        )
    }
}

/** Figma 0.2.1 첫 로그인 · 비밀번호 바꾸기 */
@Composable
private fun ChangePasswordForm(state: LoginUiState, onChange: (String, String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var confirmFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    // 치는 중에는 앞부분이 같으면 아직 다르다고 하지 않는다.
    val mismatch = confirm.isNotEmpty() && confirm != password &&
        (!confirmFocused || !password.startsWith(confirm))
    val canSubmit = isValidPassword(password) && password == confirm && !state.inProgress
    val submit = {
        if (canSubmit) {
            focusManager.clearFocus()
            onChange(password, confirm)
        }
    }

    Head(stringResource(R.string.password_title))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthField(
                label = stringResource(R.string.password_new),
                value = password,
                onValueChange = { password = it.take(PASSWORD_LIMIT) },
                invalid = false,
                password = true,
                imeAction = ImeAction.Next,
                onImeAction = { focusManager.moveFocus(FocusDirection.Down) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Rule(stringResource(R.string.password_rule_length), isLongEnoughPassword(password))
                Rule(stringResource(R.string.password_rule_mix), hasLetterAndDigit(password))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthField(
                label = stringResource(R.string.password_confirm),
                value = confirm,
                onValueChange = { confirm = it.take(PASSWORD_LIMIT) },
                invalid = mismatch,
                password = true,
                imeAction = ImeAction.Done,
                onImeAction = submit,
                onFocusChange = { confirmFocused = it },
            )
            if (mismatch) FieldError(stringResource(R.string.password_mismatch))
            if (state.passwordChangeFailed) FieldError(stringResource(R.string.password_failed))
        }
    }
    ActionButton(
        text = stringResource(R.string.password_start),
        tone = ButtonTone.BRAND,
        onClick = submit,
        enabled = canSubmit,
        height = 56.dp,
        radius = 16.dp,
        modifier = Modifier
            .padding(top = 32.dp)
            .fillMaxWidth(),
    )
}

/** 로고 + 가운데 제목 */
@Composable
private fun Head(title: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 32.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_ormo_logo),
            contentDescription = stringResource(R.string.logo_description),
            modifier = Modifier.size(width = 87.dp, height = 37.dp),
        )
        Text(title, style = OrmoType.HeadlineBold, color = OrmoColors.TextStrong, textAlign = TextAlign.Center)
    }
}

/** 라벨 + 높이 56 입력칸. 비밀번호 칸은 눈 아이콘으로 보기를 켜고 끈다. */
@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    invalid: Boolean,
    password: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusChange: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = OrmoType.BodySmSemibold, color = OrmoColors.TextSecondary)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = OrmoType.BodyMdRegular.copy(color = OrmoColors.TextPrimary),
            visualTransformation = if (password && !shown) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Ascii,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }),
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChange(it.isFocused)
                }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .border(
                            if (focused || invalid) 2.dp else 1.dp,
                            when {
                                invalid -> OrmoColors.FillDanger
                                focused -> OrmoColors.FillBrand
                                else -> OrmoColors.FieldBorder
                            },
                            shape,
                        )
                        .padding(start = 18.dp, end = if (password) 10.dp else 18.dp),
                ) {
                    Box(Modifier.weight(1f)) { inner() }
                    if (password) {
                        val description = stringResource(
                            if (shown) R.string.password_hide else R.string.password_show,
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { shown = !shown }
                                .semantics { contentDescription = description },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_eye),
                                contentDescription = null,
                                // 보는 중이거나 입력 중이면 진하게
                                tint = if (shown || focused) OrmoColors.TextSecondary else OrmoColors.ChevronWeak,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun FieldError(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_alert),
            contentDescription = null,
            tint = OrmoColors.TextDanger,
            modifier = Modifier.size(16.dp),
        )
        Text(text, style = OrmoType.BodySmRegular, color = OrmoColors.TextDanger)
    }
}

/** 비밀번호 규칙 한 개. 맞추면 파란 체크로 바뀐다. */
@Composable
private fun Rule(text: String, met: Boolean) {
    val color = if (met) OrmoColors.TextBrand else OrmoColors.TextTertiary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = color,
            )
        }
        Text(text, style = OrmoType.BodySmMedium, color = color)
    }
}
