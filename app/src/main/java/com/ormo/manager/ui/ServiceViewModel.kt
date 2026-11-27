package com.ormo.manager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ormo.manager.service.AppUpdate
import com.ormo.manager.service.InMemoryServiceStatusSource
import com.ormo.manager.service.Maintenance
import com.ormo.manager.service.ServiceStatus
import com.ormo.manager.service.ServiceStatusSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ServiceUiState(
    val maintenance: Maintenance? = null,
    val sessionExpired: Boolean = false,
    /** 지금 띄울 업데이트 창. 없으면 null */
    val updatePrompt: AppUpdate? = null,
    /** 꼭 필요한 업데이트인데 접수 대기가 남아 있어 창을 미루는 중 */
    val updateAfterOrders: Boolean = false,
)

private data class Shop(val closed: Boolean = false, val pendingCount: Int = 0)

/**
 * 서비스 점검 · 로그인 만료 · 업데이트(Figma 5.2).
 * - 꼭 필요한 업데이트: 접수 대기를 다 처리하면 띄운다(업데이트하는 동안에는 알림이 오지 않는다).
 * - 선택 업데이트: 마감한 뒤 한 번만 띄운다.
 */
class ServiceViewModel(private val source: ServiceStatusSource) : ViewModel() {

    private val shop = MutableStateFlow(Shop())
    private val updateDismissed = MutableStateFlow(false)

    /** 받을 곳을 열지 못한 업데이트. 같은 안내로는 다시 막지 않는다. */
    private val unopenable = MutableStateFlow<AppUpdate?>(null)

    val uiState: StateFlow<ServiceUiState> =
        combine(source.status, shop, updateDismissed, unopenable, ::toUiState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, ServiceUiState())

    private fun toUiState(
        status: ServiceStatus,
        shop: Shop,
        dismissed: Boolean,
        failed: AppUpdate?,
    ): ServiceUiState {
        // 로그인이 만료되면 다른 안내보다 먼저 다시 로그인하게 한다.
        val update = status.update.takeUnless { status.sessionExpired || it == failed }
        val prompt = update?.takeIf {
            if (it.required) shop.pendingCount == 0 else shop.closed && !dismissed
        }
        return ServiceUiState(
            maintenance = status.maintenance,
            sessionExpired = status.sessionExpired,
            updatePrompt = prompt,
            updateAfterOrders = update?.required == true && shop.pendingCount > 0,
        )
    }

    fun onShopChange(closed: Boolean, pendingCount: Int) {
        shop.value = Shop(closed, pendingCount)
    }

    /** [나중에]: 선택 업데이트만 닫힌다. */
    fun onUpdateLater() {
        updateDismissed.value = true
    }

    /** 받을 곳이 없거나 열 수 없으면, 필수 업데이트라도 창을 닫아 주문을 처리할 수 있게 한다. */
    fun onUpdateUnavailable() {
        unopenable.value = source.status.value.update
    }

    fun onLoginAgain() = source.clearSessionExpired()

    /** 개발 빌드의 화면 확인용 */
    fun changeForTest(change: (ServiceStatus) -> ServiceStatus) {
        updateDismissed.value = false
        unopenable.value = null
        (source as? InMemoryServiceStatusSource)?.update(change)
    }
}
