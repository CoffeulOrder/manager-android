package com.ormo.manager.payment

/**
 * 서버 연동 전 임시 구현: 아무것도 호출하지 않고 즉시 성공한다. **실제 환불은 일어나지 않는다.**
 * 서버를 붙일 때 [PaymentGateway] 구현으로 교체한다.
 */
class FakePaymentGateway : PaymentGateway {
    override suspend fun cancel(orderId: String, reason: String, amount: Int): Result<Unit> =
        Result.success(Unit)
}
