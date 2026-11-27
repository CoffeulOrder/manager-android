package com.ormo.managerdemo.ui

import com.ormo.managerdemo.data.RemovedMenu

/** 스낵바 종류. [millis]가 null이면 저절로 사라지지 않는다. */
enum class SnackKind(val millis: Long?) {
    /** 새 주문: 처리하거나 닫을 때까지 */
    NEW_ORDER(null),

    /** 잠시 중지·마감 안내: 영업을 다시 시작할 때까지. 밀어도 닫히지 않는다. */
    STORE(null),

    /** 태블릿 소리 꺼짐: 소리가 돌아올 때까지 */
    SOUND_OFF(null),

    /** 조리 완료 되돌리기: ViewModel이 10초를 센다 */
    UNDO(null),

    /** 버튼 없는 알림 */
    INFO(2_000L),

    /** 버튼 있는 알림(다시 시도, 주문 보기) */
    ACTION(4_000L),

    /** 메뉴 삭제 되돌리기(10초) */
    MENU_DELETED(10_000L),
}

data class Snack(
    /** 같은 key는 한 번만 떠 있고, 다시 넣으면 제자리에서 바뀐다. */
    val key: String,
    val kind: SnackKind,
    val message: String = "",
    val critical: Boolean = false,
    /** [SnackKind.ACTION]에서 다시 시도할 주문 */
    val orderNo: String? = null,
    /** [SnackKind.ACTION]의 버튼이 "주문 보기"인 경우 */
    val viewOrders: Boolean = false,
    /** [SnackKind.MENU_DELETED]에서 되돌릴 메뉴 */
    val removedMenu: RemovedMenu? = null,
)

/** 화면에 함께 떠 있는 스낵바는 최대 3개(Figma 1.2.4) */
const val MAX_SNACKS = 3

/**
 * 온 순서대로 쌓는다. 목록의 앞이 화면의 아래다.
 * 3개를 넘으면 가장 오래된 일시 알림을 없앤다. 계속 떠 있어야 하는 알림은 없애지 않는다.
 */
fun List<Snack>.push(snack: Snack): List<Snack> {
    if (any { it.key == snack.key }) return map { if (it.key == snack.key) snack else it }
    val next = this + snack
    if (next.size <= MAX_SNACKS) return next
    val victim = firstOrNull { it.kind.millis != null } ?: return next
    return next - victim
}

fun List<Snack>.dismiss(key: String): List<Snack> = filterNot { it.key == key }
