package com.ormo.manager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ormo.manager.auth.AuthGateway
import com.ormo.manager.auth.LoginResult
import com.ormo.manager.auth.PasswordChangeResult
import com.ormo.manager.domain.isValidPassword
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthStep { SIGNED_OUT, CHANGE_PASSWORD, SIGNED_IN }

enum class LoginError { REJECTED, LOCKED, UNAVAILABLE }

data class LoginUiState(
    val step: AuthStep = AuthStep.SIGNED_OUT,
    /** 응답을 기다리는 중. 한 번만 보낸다. */
    val inProgress: Boolean = false,
    val error: LoginError? = null,
    /** [LoginError.LOCKED]가 풀리는 시각 */
    val lockedUntil: Long? = null,
    /** 틀릴 때마다 늘어난다. 화면은 비밀번호 칸을 비우고 커서를 옮긴다. */
    val rejections: Int = 0,
    val passwordChangeFailed: Boolean = false,
)

enum class PasswordChangeError { WRONG_CURRENT, UNAVAILABLE }

/** 설정의 비밀번호 바꾸기 */
data class PasswordChangeUiState(
    val open: Boolean = false,
    /** 응답을 기다리는 중. 화면을 닫았다 다시 열어도 한 번만 보낸다. */
    val inProgress: Boolean = false,
    val error: PasswordChangeError? = null,
    /** 현재 비밀번호가 틀릴 때마다 늘어난다. 화면은 그 칸을 비우고 커서를 옮긴다. */
    val rejections: Int = 0,
    /** 바꿨다는 알림을 아직 띄우지 않았다. */
    val changed: Boolean = false,
)

/**
 * 로그인(Figma 0.1), 첫 로그인 비밀번호 바꾸기(0.2), 설정의 비밀번호 바꾸기.
 * 비밀번호는 상태에 담지 않는다.
 */
class LoginViewModel(
    private val gateway: AuthGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val state = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = state.asStateFlow()

    private val password = MutableStateFlow(PasswordChangeUiState())
    val passwordChange: StateFlow<PasswordChangeUiState> = password.asStateFlow()
    private var passwordJob: Job? = null

    fun login(id: String, password: String) {
        onTick()
        val current = state.value
        if (current.step != AuthStep.SIGNED_OUT || current.inProgress) return
        if (current.error == LoginError.LOCKED || id.isBlank() || password.isEmpty()) return
        state.value = current.copy(inProgress = true)
        viewModelScope.launch {
            val result = gateway.login(id.trim(), password).getOrNull()
            val before = state.value.copy(inProgress = false)
            state.value = when (result) {
                is LoginResult.Success -> LoginUiState(
                    step = if (result.firstLogin) AuthStep.CHANGE_PASSWORD else AuthStep.SIGNED_IN,
                )
                LoginResult.Rejected -> before.copy(
                    error = LoginError.REJECTED,
                    rejections = before.rejections + 1,
                )
                is LoginResult.Locked -> before.copy(
                    error = LoginError.LOCKED,
                    lockedUntil = clock() + result.retryAfterSeconds * 1_000L,
                )
                null -> before.copy(error = LoginError.UNAVAILABLE)
            }
        }
    }

    /** 다시 입력하기 시작하면 오류 표시를 지운다. 잠금은 시간이 지나야 풀린다. */
    fun onInputChange() {
        val current = state.value
        if (current.error != null && current.error != LoginError.LOCKED) {
            state.value = current.copy(error = null)
        }
    }

    /** 잠금 시간이 지났으면 푼다. */
    fun onTick() {
        val until = state.value.lockedUntil ?: return
        if (clock() >= until) state.value = state.value.copy(error = null, lockedUntil = null)
    }

    fun changePassword(newPassword: String, confirm: String) {
        val current = state.value
        if (current.step != AuthStep.CHANGE_PASSWORD || current.inProgress) return
        if (!isValidPassword(newPassword) || newPassword != confirm) return
        state.value = current.copy(inProgress = true, passwordChangeFailed = false)
        viewModelScope.launch {
            val changed = gateway.changePassword(newPassword).isSuccess
            state.value = if (changed) {
                LoginUiState(step = AuthStep.SIGNED_IN)
            } else {
                state.value.copy(inProgress = false, passwordChangeFailed = true)
            }
        }
    }

    fun openPasswordChange() {
        if (state.value.step != AuthStep.SIGNED_IN) return
        val current = password.value
        password.value = PasswordChangeUiState(open = true, inProgress = current.inProgress, changed = current.changed)
    }

    /** 닫아도 이미 보낸 요청은 이어진다. 바뀌면 알려주고, 실패하면 비밀번호는 그대로다. */
    fun closePasswordChange() {
        password.value = password.value.copy(open = false, error = null)
    }

    fun onPasswordChangeInput() {
        if (password.value.error != null) password.value = password.value.copy(error = null)
    }

    fun changeOwnPassword(currentPassword: String, newPassword: String, confirm: String) {
        val current = password.value
        if (state.value.step != AuthStep.SIGNED_IN || !current.open || current.inProgress) return
        if (currentPassword.isEmpty() || !isValidPassword(newPassword) || newPassword != confirm) return
        password.value = current.copy(inProgress = true, error = null)
        passwordJob = viewModelScope.launch {
            val result = gateway.changePasswordWithCurrent(currentPassword, newPassword).getOrNull()
            val before = password.value.copy(inProgress = false)
            password.value = when {
                result == PasswordChangeResult.Changed -> PasswordChangeUiState(changed = true)
                !before.open -> before
                result == PasswordChangeResult.WrongCurrent -> before.copy(
                    error = PasswordChangeError.WRONG_CURRENT,
                    rejections = before.rejections + 1,
                )
                else -> before.copy(error = PasswordChangeError.UNAVAILABLE)
            }
        }
    }

    fun onPasswordChangedShown() {
        password.value = password.value.copy(changed = false)
    }

    fun logout() {
        if (state.value.step != AuthStep.SIGNED_IN) return
        // 늦게 온 답이 다음 로그인 화면에 섞이지 않게 한다.
        passwordJob?.cancel()
        password.value = PasswordChangeUiState()
        state.value = LoginUiState()
        viewModelScope.launch { gateway.logout() }
    }
}
