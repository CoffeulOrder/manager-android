package com.ormo.manager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinViewModelTest {

    private var now = 1_000_000L
    private val vm = PinViewModel { now }

    private val prompt get() = vm.uiState.value.prompt

    private fun createPin() {
        vm.request()
        vm.submit("2580")
        vm.submit("2580")
        vm.onNoticeShown()
        vm.lock()
    }

    @Test
    fun firstUse_createsPinAfterTypingItTwice() {
        vm.request()
        assertTrue(prompt is PinPrompt.Create)
        vm.submit("2580")
        vm.submit("1111")
        assertEquals("다르면 확인만 다시", PinPrompt.Confirm("2580", errors = 1), prompt)

        vm.submit("2580")

        assertNull(prompt)
        assertTrue(vm.uiState.value.unlocked)
        assertEquals(PinNotice.CREATED, vm.uiState.value.notice)
    }

    @Test
    fun closingWhileCreating_discardsTheDigits() {
        vm.request()
        vm.submit("2580")
        vm.close()
        vm.request()
        assertTrue(prompt is PinPrompt.Create)
    }

    @Test
    fun afterLeaving_asksAgain_andRejectsWrongPin() {
        createPin()
        vm.request()
        vm.submit("0000")
        assertEquals(PinPrompt.Enter(errors = 1), prompt)
        vm.submit("2580")
        assertTrue(vm.uiState.value.unlocked)

        vm.request()
        assertNull("이미 열려 있으면 묻지 않는다", prompt)
    }

    @Test
    fun change_needsCurrentPin_thenNewPinTwice() {
        createPin()
        vm.requestChange()
        vm.submit("0000")
        assertEquals(PinPrompt.ChangeCurrent(errors = 1), prompt)
        vm.submit("2580")
        assertEquals(PinPrompt.ChangeNew(), prompt)
        vm.submit("1357")
        vm.submit("1358")
        assertEquals("다르면 ③만 다시", PinPrompt.ChangeConfirm("1357", errors = 1), prompt)

        vm.submit("1357")

        assertNull(prompt)
        assertEquals(PinNotice.CHANGED, vm.uiState.value.notice)
        assertTrue("바꿔도 매출은 열리지 않는다", !vm.uiState.value.unlocked)
        vm.request()
        vm.submit("2580")
        assertEquals(PinPrompt.Enter(errors = 1), prompt)
        vm.submit("1357")
        assertTrue(vm.uiState.value.unlocked)
    }

    @Test
    fun closingDuringChange_keepsTheOldPin() {
        createPin()
        vm.requestChange()
        vm.submit("2580")
        vm.submit("1357")
        vm.close()

        vm.request()
        vm.submit("2580")

        assertTrue(vm.uiState.value.unlocked)
    }

    @Test
    fun changeBeforeAnyPin_createsOne_withoutUnlocking() {
        vm.requestChange()
        assertTrue(prompt is PinPrompt.Create)
        vm.submit("2580")
        vm.submit("2580")

        assertEquals(PinNotice.CREATED, vm.uiState.value.notice)
        assertTrue(!vm.uiState.value.unlocked)
    }

    @Test
    fun wrongCurrentPin_sharesTheLockWithEntering() {
        createPin()
        vm.requestChange()
        repeat(5) { vm.submit("0000") }
        assertEquals(now + 30_000L, (prompt as PinPrompt.ChangeCurrent).lockedUntil)
        vm.submit("2580")
        assertTrue(prompt is PinPrompt.ChangeCurrent)

        vm.close()
        vm.request()
        assertEquals(now + 30_000L, (prompt as PinPrompt.Enter).lockedUntil)
    }

    @Test
    fun fifthFailure_locksFor30Seconds_evenAfterReopening() {
        createPin()
        vm.request()
        repeat(4) { vm.submit("0000") }
        assertNull((prompt as PinPrompt.Enter).lockedUntil)
        vm.submit("0000")
        assertEquals(now + 30_000L, (prompt as PinPrompt.Enter).lockedUntil)

        vm.close()
        vm.request()
        vm.submit("2580")
        assertTrue("잠금 중에는 맞는 번호도 받지 않는다", !vm.uiState.value.unlocked)

        now += 30_000L
        vm.submit("2580")
        assertTrue(vm.uiState.value.unlocked)
    }
}
