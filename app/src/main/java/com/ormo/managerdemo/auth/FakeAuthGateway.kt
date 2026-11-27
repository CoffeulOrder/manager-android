package com.ormo.managerdemo.auth

/** DEMO ONLY: 실패 화면을 디자인 QA할 때 아이디 칸에 넣는 값. 비밀번호는 아무 값이나 된다. */
const val DEMO_FAILING_ID = "fail"

private const val MAX_FREE_TRIES = 5
private const val LOCK_SECONDS = 30

/**
 * 서버가 없는 데모: 어떤 아이디·비밀번호든 통과시킨다(계정 값을 코드에 두지 않는다).
 * 아이디가 [DEMO_FAILING_ID]일 때만 실패하고, 5번째 실패부터는 잠금 응답을 낸다.
 * 앱을 켤 때마다 첫 로그인으로 보여 비밀번호 바꾸기 화면도 확인할 수 있다.
 */
class FakeAuthGateway : AuthGateway {

    private var firstLogin = true
    private var failures = 0

    override suspend fun login(id: String, password: String): Result<LoginResult> {
        if (id != DEMO_FAILING_ID) {
            failures = 0
            return Result.success(LoginResult.Success(firstLogin))
        }
        failures += 1
        val result = if (failures >= MAX_FREE_TRIES) LoginResult.Locked(LOCK_SECONDS) else LoginResult.Rejected
        return Result.success(result)
    }

    override suspend fun changePassword(newPassword: String): Result<Unit> {
        firstLogin = false
        return Result.success(Unit)
    }

    override suspend fun logout() = Unit
}
