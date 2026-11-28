package com.ormo.manager.ui

import com.ormo.manager.data.InMemorySalesSource
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModelTest {

    private val today = LocalDate.of(2026, 11, 27)
    private val source = InMemorySalesSource()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun failure_isShownInsteadOfZeros_andRetryLoadsTheSamePeriod() {
        val vm = SalesViewModel(source)
        val dates = listOf(today.minusDays(7), today.minusDays(6))
        source.failing = true

        vm.load(dates, today)
        assertEquals(SalesLoad.Failed, vm.uiState.value)

        source.failing = false
        vm.retry()

        val loaded = vm.uiState.value as SalesLoad.Loaded
        assertEquals(dates, loaded.dates)
        assertEquals(dates, loaded.days.map { it.date })
    }
}
