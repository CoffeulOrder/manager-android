package com.ormo.manager.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 서버 점검·장애. [until]은 끝나는 시각을 알 때만 있다. */
data class Maintenance(val until: Long? = null)

/** 새 버전. [required]면 미룰 수 없다. [url]은 받는 곳(서버가 알려준다). */
data class AppUpdate(val required: Boolean, val url: String? = null)

/** 서버가 알려주는, 매장 데이터와 상관없는 상태(Figma 5.2) */
data class ServiceStatus(
    val maintenance: Maintenance? = null,
    /** 로그인 유지 기간(30일)이 끝났다. */
    val sessionExpired: Boolean = false,
    val update: AppUpdate? = null,
)

/** 백엔드를 붙일 때 이 인터페이스를 구현한다. */
interface ServiceStatusSource {
    val status: StateFlow<ServiceStatus>

    /** 만료 안내를 보고 다시 로그인하러 간다. */
    fun clearSessionExpired()
}

/** 서버 연동 전: 아무 일도 없는 상태. 개발 빌드의 화면 확인 버튼이 [update]로 바꾼다. */
class InMemoryServiceStatusSource : ServiceStatusSource {

    private val state = MutableStateFlow(ServiceStatus())
    override val status: StateFlow<ServiceStatus> = state.asStateFlow()

    override fun clearSessionExpired() = update { it.copy(sessionExpired = false) }

    fun update(change: (ServiceStatus) -> ServiceStatus) = state.update(change)
}
