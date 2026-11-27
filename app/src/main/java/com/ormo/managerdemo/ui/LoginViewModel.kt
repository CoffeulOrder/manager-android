package com.ormo.managerdemo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ormo.managerdemo.auth.AuthGateway
import com.ormo.managerdemo.auth.LoginResult
import com.ormo.managerdemo.domain.isValidPassword
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

/** 로그인(Figma 0.1)과 첫 로그인 비밀번호 바꾸기(0.2). 비밀번호는 상태에 담지 않는다. */
class LoginViewModel(
    private val gateway: AuthGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val state = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = state.asStateFlow()

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

    fun logout() {
        if (state.value.step != AuthStep.SIGNED_IN) return
        state.value = LoginUiState()
        viewModelScope.launch { gateway.logout() }
    }
}
