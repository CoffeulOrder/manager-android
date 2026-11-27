package com.ormo.managerdemo.payment

/** 데모용: 아무것도 호출하지 않고 즉시 성공한다. 실제 연동 시 [PaymentGateway] 구현으로 교체. */
class FakePaymentGateway : PaymentGateway {
    override suspend fun cancel(orderId: String, reason: String, amount: Int): Result<Unit> =
        Result.success(Unit)
}
