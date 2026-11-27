package com.ormo.managerdemo.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val PIN_LENGTH = 4
private const val MAX_FREE_TRIES = 5
private const val LOCK_MILLIS = 30_000L

/** 2차 비밀번호 창(Figma 0.3, 4.1.6) */
sealed interface PinPrompt {
    /** [errors]가 늘면 점이 흔들린다. */
    val errors: Int

    /** 지금 비밀번호를 확인하는 단계. [lockedUntil]까지는 키패드를 끈다. */
    sealed interface Verify : PinPrompt {
        val lockedUntil: Long?
    }

    /** 잠긴 화면에 들어갈 때 */
    data class Enter(override val errors: Int = 0, override val lockedUntil: Long? = null) : Verify

    /** 처음 정하기 */
    data class Create(override val errors: Int = 0) : PinPrompt

    /** 한 번 더 */
    data class Confirm(val first: String, override val errors: Int = 0) : PinPrompt

    /** 바꾸기 ① 지금 2차 비밀번호 */
    data class ChangeCurrent(override val errors: Int = 0, override val lockedUntil: Long? = null) : Verify

    /** 바꾸기 ② 새 2차 비밀번호 */
    data class ChangeNew(override val errors: Int = 0) : PinPrompt

    /** 바꾸기 ③ 한 번 더 */
    data class ChangeConfirm(val first: String, override val errors: Int = 0) : PinPrompt
}

/** 스낵바로 한 번 알릴 일 */
enum class PinNotice { CREATED, CHANGED }

data class PinUiState(
    val prompt: PinPrompt? = null,
    /** 잠긴 화면(매출)을 열어도 되는지. 화면을 나가면 다시 잠근다. */
    val unlocked: Boolean = false,
    val notice: PinNotice? = null,
)

/**
 * 2차 비밀번호. 사용자가 처음 필요할 때 직접 정하며 앱 메모리에만 둔다(코드에 값이 없다).
 * 실제 서비스에서는 서버가 저장·확인하고 틀린 횟수를 세야 한다.
 */
class PinViewModel(private val clock: () -> Long = System::currentTimeMillis) : ViewModel() {

    private val state = MutableStateFlow(PinUiState())
    val uiState: StateFlow<PinUiState> = state.asStateFlow()

    private var pin: String? = null
    private var failures = 0
    private var lockedUntil: Long? = null

    /** 처음 정한 뒤 바로 잠긴 화면을 열지. 설정에서 정할 때는 열지 않는다. */
    private var unlockAfterCreate = true

    /** 잠긴 화면에 들어가려 할 때. 이미 열려 있으면 아무것도 하지 않는다. */
    fun request() {
        if (state.value.unlocked) return
        unlockAfterCreate = true
        show(if (pin == null) PinPrompt.Create() else PinPrompt.Enter(lockedUntil = activeLock()))
    }

    /** 설정의 [2차 비밀번호 바꾸기]. 아직 정한 적이 없으면 처음 정하기를 연다. */
    fun requestChange() {
        unlockAfterCreate = false
        show(if (pin == null) PinPrompt.Create() else PinPrompt.ChangeCurrent(lockedUntil = activeLock()))
    }

    /** 4자리를 다 누르면 바로 확인한다. */
    fun submit(input: String) {
        if (input.length != PIN_LENGTH) return
        when (val prompt = state.value.prompt) {
            null -> Unit
            is PinPrompt.Create -> show(PinPrompt.Confirm(input))
            is PinPrompt.Confirm -> if (input == prompt.first) {
                pin = input
                state.value = PinUiState(unlocked = unlockAfterCreate, notice = PinNotice.CREATED)
            } else {
                // 처음 넣은 4자리는 두고 확인만 다시 받는다.
                show(prompt.copy(errors = prompt.errors + 1))
            }
            is PinPrompt.Enter -> verify(
                input,
                onMatch = { state.value = PinUiState(unlocked = true) },
                onMiss = { show(PinPrompt.Enter(prompt.errors + 1, lockedUntil)) },
            )
            is PinPrompt.ChangeCurrent -> verify(
                input,
                onMatch = { show(PinPrompt.ChangeNew()) },
                onMiss = { show(PinPrompt.ChangeCurrent(prompt.errors + 1, lockedUntil)) },
            )
            is PinPrompt.ChangeNew -> show(PinPrompt.ChangeConfirm(input))
            is PinPrompt.ChangeConfirm -> if (input == prompt.first) {
                pin = input
                state.value = PinUiState(notice = PinNotice.CHANGED)
            } else {
                show(prompt.copy(errors = prompt.errors + 1))
            }
        }
    }

    private fun show(prompt: PinPrompt) {
        state.value = state.value.copy(prompt = prompt)
    }

    private fun verify(input: String, onMatch: () -> Unit, onMiss: () -> Unit) {
        if (activeLock() != null) return
        if (input == pin) {
            failures = 0
            lockedUntil = null
            onMatch()
            return
        }
        // 5번째 실패부터는 틀릴 때마다 30초를 기다린다. 창을 닫아도 횟수는 이어진다.
        failures += 1
        if (failures >= MAX_FREE_TRIES) lockedUntil = clock() + LOCK_MILLIS
        onMiss()
    }

    private fun activeLock(): Long? = lockedUntil?.takeIf { clock() < it }

    /** [닫기]: 원래 화면에 그대로 있는다. 정하거나 바꾸던 중이면 버린다. */
    fun close() {
        state.value = state.value.copy(prompt = null)
    }

    /** 잠긴 화면을 나가면 다음에 다시 묻는다. */
    fun lock() {
        if (state.value.unlocked) state.value = state.value.copy(unlocked = false)
    }

    fun onNoticeShown() {
        state.value = state.value.copy(notice = null)
    }
}
