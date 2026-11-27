package com.ormo.manager.domain

/** 영업 상태(Figma 3.1). 영업 중일 때만 새 주문을 받는다. */
sealed interface StoreStatus {
    data object Open : StoreStatus

    /** @param until 다시 받는 시각. null이면 직접 다시 열 때까지 */
    data class Paused(val until: Long?, val reason: PauseReason) : StoreStatus

    data object Closed : StoreStatus
}

/** 잠시 중지 사유. 선언 순서가 화면 표시 순서다. */
enum class PauseReason { BUSY, PREPARING, STORE_ISSUE }

/** 쉬는 시간 선택지(분). null은 "직접 다시 열기" */
val PAUSE_MINUTE_OPTIONS: List<Int?> = listOf(10, 20, 30, 60, null)

const val DEFAULT_PAUSE_MINUTES = 20
