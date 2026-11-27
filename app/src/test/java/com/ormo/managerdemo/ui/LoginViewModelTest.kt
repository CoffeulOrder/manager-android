package com.ormo.managerdemo.ui

import com.ormo.managerdemo.auth.AuthGateway
import com.ormo.managerdemo.auth.DEMO_FAILING_ID
import com.ormo.managerdemo.auth.FakeAuthGateway
import com.ormo.managerdemo.auth.LoginResult
import com.ormo.managerdemo.domain.isValidPassword
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
        vm.login(DEMO_FAILING_ID, password)
        assertEquals(LoginError.REJECTED, vm.uiState.value.error)
        assertEquals(1, vm.uiState.value.rejections)
        vm.onInputChange()
        assertNull(vm.uiState.value.error)

        repeat(4) { vm.login(DEMO_FAILING_ID, password) }
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

        override suspend fun logout() = Unit
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
