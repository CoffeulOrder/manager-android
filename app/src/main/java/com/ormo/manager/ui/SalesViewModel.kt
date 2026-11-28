package com.ormo.manager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ormo.manager.data.InMemorySalesSource
import com.ormo.manager.data.SalesSource
import com.ormo.manager.domain.DaySales
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SalesLoad {
    data object Loading : SalesLoad

    /** [dates]의 매출. 화면이 보는 기간과 다르면 아직 불러오는 중으로 본다. */
    data class Loaded(val dates: List<LocalDate>, val days: List<DaySales>) : SalesLoad

    /** 불러오지 못했다. 0원을 대신 보여주지 않는다. */
    data object Failed : SalesLoad
}

/** 매출 조회(Figma 6.1) */
class SalesViewModel(private val source: SalesSource) : ViewModel() {

    private val state = MutableStateFlow<SalesLoad>(SalesLoad.Loading)
    val uiState: StateFlow<SalesLoad> = state.asStateFlow()

    private var request: Pair<List<LocalDate>, LocalDate>? = null
    private var job: Job? = null

    fun load(dates: List<LocalDate>, today: LocalDate) {
        request = dates to today
        job?.cancel()
        state.value = SalesLoad.Loading
        job = viewModelScope.launch {
            state.value = source.load(dates, today).fold(
                onSuccess = { SalesLoad.Loaded(dates, it) },
                onFailure = { SalesLoad.Failed },
            )
        }
    }

    /** [다시 시도] */
    fun retry() {
        val (dates, today) = request ?: return
        load(dates, today)
    }

    val failingForTest: Boolean get() = (source as? InMemorySalesSource)?.failing == true

    /** 개발 빌드의 화면 확인용: 다음 조회부터 실패하게(또는 다시 성공하게) 한다. */
    fun toggleFailureForTest() {
        (source as? InMemorySalesSource)?.let { it.failing = !it.failing }
    }
}
