package com.ormo.manager.ui

import com.ormo.manager.service.AppUpdate
import com.ormo.manager.service.InMemoryServiceStatusSource
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
class ServiceViewModelTest {

    private val source = InMemoryServiceStatusSource()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun requiredUpdate_waitsUntilPendingOrdersAreHandled_andCannotBePostponed() {
        val vm = ServiceViewModel(source)
        vm.onShopChange(closed = false, pendingCount = 2)
        val update = AppUpdate(required = true)
        source.update { it.copy(update = update) }

        assertNull(vm.uiState.value.updatePrompt)
        assertTrue(vm.uiState.value.updateAfterOrders)

        vm.onShopChange(closed = false, pendingCount = 0)
        assertEquals(update, vm.uiState.value.updatePrompt)
        vm.onUpdateLater()
        assertEquals(update, vm.uiState.value.updatePrompt)
    }

    @Test
    fun optionalUpdate_appearsOnceAfterClosing() {
        val vm = ServiceViewModel(source)
        val update = AppUpdate(required = false)
        source.update { it.copy(update = update) }
        assertNull("영업 중에는 방해하지 않는다", vm.uiState.value.updatePrompt)

        vm.onShopChange(closed = true, pendingCount = 0)
        assertEquals(update, vm.uiState.value.updatePrompt)
        vm.onUpdateLater()
        vm.onShopChange(closed = false, pendingCount = 0)
        vm.onShopChange(closed = true, pendingCount = 0)

        assertNull(vm.uiState.value.updatePrompt)
    }

    @Test
    fun updateThatCannotBeOpened_doesNotLockTheApp_untilTheNoticeChanges() {
        val vm = ServiceViewModel(source)
        source.update { it.copy(update = AppUpdate(required = true)) }

        vm.onUpdateUnavailable()
        assertNull(vm.uiState.value.updatePrompt)

        val withUrl = AppUpdate(required = true, url = "https://example.com/app")
        source.update { it.copy(update = withUrl) }
        assertEquals(withUrl, vm.uiState.value.updatePrompt)
    }

    @Test
    fun sessionExpiry_comesBeforeUpdate_andClearsOnLoginAgain() {
        val vm = ServiceViewModel(source)
        source.update { it.copy(sessionExpired = true, update = AppUpdate(required = true)) }

        assertTrue(vm.uiState.value.sessionExpired)
        assertNull(vm.uiState.value.updatePrompt)

        vm.onLoginAgain()

        assertFalse(vm.uiState.value.sessionExpired)
        assertEquals(AppUpdate(required = true), vm.uiState.value.updatePrompt)
    }
}
