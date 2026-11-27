package com.ormo.manager.domain

/**
 * 주문 상태(Figma "사장님 웹").
 * 보드: REQUESTED(접수 대기) · ACCEPTED(제조중) · READY(픽업 대기). 나머지는 오늘 내역에 쌓인다.
 */
enum class OrderStatus {
    REQUESTED,
    ACCEPTED,
    READY,
    COMPLETED,
    NO_SHOW,
    REJECTED,
    AUTO_CANCELED,
    CUSTOMER_CANCELED,
    /** 관리자 웹에서 취소한 주문. 이 앱에서는 만들 수 없다. */
    STORE_CANCELED,
    REFUNDED;

    val isOnBoard: Boolean get() = this == REQUESTED || this == ACCEPTED || this == READY
}

/** 거절 사유(Figma 1.4.1). 선언 순서가 화면 표시 순서다. 화면 문구는 strings.xml에 있다. */
enum class RejectReason { OUT_OF_STOCK, OVERLOAD, CLOSING_SOON, STORE_ISSUE }

/** 환불 사유(Figma 2.1.2). 선언 순서가 화면 표시 순서다. */
enum class RefundReason { WRONG_DRINK, HYGIENE, OTHER }

/** 수령 방법 */
enum class PickupMethod { DINE_IN, TAKEOUT }

data class OrderLine(
    val name: String,
    /** 단가(원) */
    val price: Int,
    val qty: Int,
    val options: List<String> = emptyList(),
) {
    /** 줄 금액 = 단가 x 수량 */
    val amount: Int get() = price * qty
}

data class Order(
    /** 내부 주문 식별자. 결제 취소에 넘긴다. 예: "BW-114068" */
    val orderNo: String,
    /** 화면에 크게 보여주는 픽업 번호. 예: 30 */
    val pickupNo: Int,
    val pickupMethod: PickupMethod,
    val customerNickname: String,
    val lines: List<OrderLine>,
    val totalPrice: Int,
    val status: OrderStatus,
    val rejectReason: RejectReason? = null,
    /** 접수 시각(epoch millis) */
    val createdAt: Long,
    /** 마지막 상태 변경 시각(epoch millis) */
    val updatedAt: Long,
    val acceptedAt: Long? = null,
    /** 픽업 대기가 확정된 시각 */
    val readyAt: Long? = null,
    /** 픽업 완료·미수령·거절·취소로 보드에서 빠진 시각 */
    val closedAt: Long? = null,
    /** 보드에서 빠질 때의 상태. 환불해도 유지해서 처리 기록에 쓴다. */
    val closedAs: OrderStatus? = null,
    val refundedAt: Long? = null,
    /** 거절하며 "오늘만 품절로 바꾸기"를 한 메뉴 이름 */
    val soldOutMenus: List<String> = emptyList(),
)
