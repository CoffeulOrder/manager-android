package com.ormo.manager.ui

import com.ormo.manager.auth.AuthGateway
import com.ormo.manager.auth.FAKE_FAILING_ID
import com.ormo.manager.auth.FakeAuthGateway
import com.ormo.manager.auth.LoginResult
import com.ormo.manager.auth.PasswordChangeResult
import com.ormo.manager.domain.isValidPassword
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private var now = 1_000_000L

    /** 테스트용 값. 실제 계정이 아니다. */
    private val password = "abcd" + "1234"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(gateway: AuthGateway = FakeAuthGateway()) = LoginViewModel(gateway) { now }

    @Test
    fun passwordRule_needsEightCharactersWithLettersAndDigits() {
        assertTrue(isValidPassword(password))
        assertFalse(isValidPassword("abc123"))
        assertFalse(isValidPassword("abcdefgh"))
        assertFalse(isValidPassword("12345678"))
    }

    @Test
    fun firstLogin_changesPasswordOnce_thenSignsInDirectly() {
        val vm = viewModel()
        vm.login("any", "x")
        assertEquals(AuthStep.CHANGE_PASSWORD, vm.uiState.value.step)

        vm.changePassword("short", "short")
        vm.changePassword(password, password + "x")
        assertEquals(AuthStep.CHANGE_PASSWORD, vm.uiState.value.step)
        vm.changePassword(password, password)
        assertEquals(AuthStep.SIGNED_IN, vm.uiState.value.step)

        vm.logout()
        assertEquals(AuthStep.SIGNED_OUT, vm.uiState.value.step)
        vm.login("any", "x")
        assertEquals(AuthStep.SIGNED_IN, vm.uiState.value.step)
    }

    @Test
    fun emptyInput_isNotSent() {
        val vm = viewModel()
        vm.login(" ", password)
        vm.login("any", "")
        assertEquals(LoginUiState(), vm.uiState.value)
    }

    @Test
    fun rejection_thenLockFor30SecondsFromTheFifthFailure() {
        val vm = viewModel()
        vm.login(FAKE_FAILING_ID, password)
        assertEquals(LoginError.REJECTED, vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.rejections)
        vm.onInputChange()
        assertNull(vm.uiState.value.error)

        repeat(4) { vm.login(FAKE_FAILING_ID, password) }
        assertEquals(LoginError.LOCKED, vm.uiState.value.error)
        assertEquals(now + 30_000L, vm.uiState.value.lockedUntil)
        vm.onInputChange()
        vm.login("any", password)
        assertEquals("잠금 중에는 보내지 않는다", AuthStep.SIGNED_OUT, vm.uiState.value.step)

        now += 30_000L
        vm.onTick()
        assertNull(vm.uiState.value.error)
        vm.login("any", password)
        assertEquals(AuthStep.CHANGE_PASSWORD, vm.uiState.value.step)
    }

    private class StubGateway(var gate: CompletableDeferred<Result<LoginResult>>) : AuthGateway {
        var logins = 0

        override suspend fun login(id: String, password: String): Result<LoginResult> {
            logins += 1
            return gate.await()
        }

        override suspend fun changePassword(newPassword: String) = Result.success(Unit)

        /** 설정의 비밀번호 바꾸기 답. 시험에서 채우기 전에는 기다린다. */
        var changeGate = CompletableDeferred<Result<PasswordChangeResult>>()
        var changes = 0

        override suspend fun changePasswordWithCurrent(
            currentPassword: String,
            newPassword: String,
        ): Result<PasswordChangeResult> {
            changes += 1
            return changeGate.await()
        }

        override suspend fun logout() = Unit
    }

    private val newPassword = "wxyz" + "5678"

    private fun signedIn(gateway: AuthGateway = FakeAuthGateway()): LoginViewModel {
        val vm = viewModel(gateway)
        vm.login("any", "x")
        if (vm.uiState.value.step == AuthStep.CHANGE_PASSWORD) vm.changePassword(password, password)
        return vm
    }

    private fun signedInStub(): Pair<LoginViewModel, StubGateway> {
        val gateway = StubGateway(CompletableDeferred(Result.success(LoginResult.Success(firstLogin = false))))
        return signedIn(gateway) to gateway
    }

    @Test
    fun passwordChange_closesAndNotifiesOnce() {
        val vm = signedIn()
        vm.openPasswordChange()
        assertTrue(vm.passwordChange.value.open)

        vm.changeOwnPassword("", newPassword, newPassword)
        vm.changeOwnPassword(password, "short", "short")
        vm.changeOwnPassword(password, newPassword, newPassword + "x")
        assertEquals(PasswordChangeUiState(open = true), vm.passwordChange.value)

        vm.changeOwnPassword(password, newPassword, newPassword)
        assertEquals(PasswordChangeUiState(changed = true), vm.passwordChange.value)
        vm.onPasswordChangedShown()
        assertEquals(PasswordChangeUiState(), vm.passwordChange.value)
    }

    @Test
    fun passwordChange_wrongCurrent_staysOpenUntilTypedAgain() {
        val vm = signedIn()
        vm.openPasswordChange()
        vm.changeOwnPassword(FAKE_FAILING_ID, newPassword, newPassword)
        assertTrue(vm.passwordChange.value.open)
        assertEquals(PasswordChangeError.WRONG_CURRENT, vm.passwordChange.value.error)
        assertEquals(1, vm.passwordChange.value.rejections)

        vm.onPasswordChangeInput()
        assertNull(vm.passwordChange.value.error)
        vm.closePasswordChange()
        vm.openPasswordChange()
        assertEquals(PasswordChangeUiState(open = true), vm.passwordChange.value)
    }

    @Test
    fun passwordChange_sendsOnce_andKeepsWaitingAcrossReopen() {
        val (vm, gateway) = signedInStub()
        vm.openPasswordChange()
        vm.changeOwnPassword(password, newPassword, newPassword)
        vm.changeOwnPassword(password, newPassword, newPassword)
        vm.closePasswordChange()
        vm.openPasswordChange()
        vm.changeOwnPassword(password, newPassword, newPassword)
        assertEquals(1, gateway.changes)
        assertTrue(vm.passwordChange.value.inProgress)

        gateway.changeGate.complete(Result.failure(RuntimeException("network")))
        assertEquals(PasswordChangeError.UNAVAILABLE, vm.passwordChange.value.error)
        assertFalse(vm.passwordChange.value.inProgress)
    }

    @Test
    fun passwordChange_answeredAfterClosing_notifiesOnlyWhenChanged() {
        val (vm, gateway) = signedInStub()
        vm.openPasswordChange()
        vm.changeOwnPassword(password, newPassword, newPassword)
        vm.closePasswordChange()
        gateway.changeGate.complete(Result.success(PasswordChangeResult.WrongCurrent))
        assertEquals(PasswordChangeUiState(), vm.passwordChange.value)

        gateway.changeGate = CompletableDeferred()
        vm.openPasswordChange()
        vm.changeOwnPassword(password, newPassword, newPassword)
        vm.closePasswordChange()
        gateway.changeGate.complete(Result.success(PasswordChangeResult.Changed))
        assertTrue(vm.passwordChange.value.changed)
    }

    @Test
    fun logout_dropsAWaitingPasswordChange() {
        val (vm, gateway) = signedInStub()
        vm.openPasswordChange()
        vm.changeOwnPassword(password, newPassword, newPassword)
        vm.logout()
        gateway.changeGate.complete(Result.success(PasswordChangeResult.Changed))
        assertEquals(PasswordChangeUiState(), vm.passwordChange.value)
        vm.openPasswordChange()
        assertFalse("로그인 전에는 열리지 않는다", vm.passwordChange.value.open)
    }

    @Test
    fun waitingForAnswer_sendsOnlyOnce_andShowsFailureWhenThereIsNone() {
        val gate = CompletableDeferred<Result<LoginResult>>()
        val gateway = StubGateway(gate)
        val vm = viewModel(gateway)

        vm.login("any", password)
        vm.login("any", password)
        assertTrue(vm.uiState.value.inProgress)
        assertEquals(1, gateway.logins)

        gate.complete(Result.failure(RuntimeException("network")))
        assertEquals(LoginError.UNAVAILABLE, vm.uiState.value.error)
        assertFalse(vm.uiState.value.inProgress)
    }
}
