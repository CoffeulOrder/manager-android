package com.ormo.manager.ui

import androidx.lifecycle.ViewModel
import com.ormo.manager.domain.BusinessHours
import com.ormo.manager.domain.DEFAULT_BUSINESS_HOURS
import com.ormo.manager.domain.isValid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 매장 설정. 지금은 앱 메모리에만 두며, 서버를 붙이면 서버의 매장 설정을 읽고 쓴다. */
class SettingsViewModel : ViewModel() {

    private val hours = MutableStateFlow(DEFAULT_BUSINESS_HOURS)
    val businessHours: StateFlow<BusinessHours> = hours.asStateFlow()

    /** 잘못된 값은 저장하지 않는다. */
    fun saveBusinessHours(new: BusinessHours) {
        if (new.isValid()) hours.value = new
    }
}
