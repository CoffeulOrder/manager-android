package com.ormo.managerdemo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SnackbarStackTest {

    private fun List<Snack>.keys() = map { it.key }

    @Test
    fun snacks_stackInArrivalOrder_andSameKeyStaysInPlace() {
        val stack = emptyList<Snack>()
            .push(Snack("sound", SnackKind.SOUND_OFF))
            .push(Snack("new", SnackKind.NEW_ORDER))
            .push(Snack("sound", SnackKind.SOUND_OFF))
        assertEquals(listOf("sound", "new"), stack.keys())
        assertEquals(listOf("new"), stack.dismiss("sound").keys())
    }

    @Test
    fun fourthSnack_removesOldestTransientOne() {
        val stack = emptyList<Snack>()
            .push(Snack("sound", SnackKind.SOUND_OFF))
            .push(Snack("a", SnackKind.INFO))
            .push(Snack("b", SnackKind.INFO))
            .push(Snack("new", SnackKind.NEW_ORDER))
        assertEquals(listOf("sound", "b", "new"), stack.keys())
    }

    @Test
    fun persistentSnacks_areNeverPushedOut() {
        val stack = emptyList<Snack>()
            .push(Snack("store", SnackKind.STORE))
            .push(Snack("sound", SnackKind.SOUND_OFF))
            .push(Snack("new", SnackKind.NEW_ORDER))
            .push(Snack("info", SnackKind.INFO))
        assertEquals(listOf("store", "sound", "new", "info"), stack.keys())
    }
}
