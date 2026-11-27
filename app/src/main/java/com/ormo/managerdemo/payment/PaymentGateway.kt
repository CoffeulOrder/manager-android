package com.ormo.managerdemo.payment

/**
 * 결제 취소 게이트웨이.
 *
 * 주문 거절/환불은 "결제 취소"를 동반한다. 지금은 [FakePaymentGateway]가 즉시 성공을 돌려주지만,
 * 실제 서비스에서는 이 인터페이스의 구현을 **자사 서버(BE)를 호출하는 구현으로 교체**하고,
 * BE가 포트원(PortOne) 결제 취소 API를 호출해야 한다.
 *
 * 포트원 API 시크릿/키는 절대 앱에 넣지 않는다. 취소 API는 서버에서만 호출한다.
 */
interface PaymentGateway {
    /**
     * @param orderId 취소할 주문 식별자(주문번호)
     * @param reason  취소 사유 코드 (예: "OUT_OF_STOCK", "REFUND_BY_OWNER")
     * @param amount  취소 금액(원). 이 앱은 부분 취소 없이 항상 주문 전액을 넘긴다.
     */
    suspend fun cancel(orderId: String, reason: String, amount: Int): Result<Unit>
}
