package com.ormo.manager.auth

/**
 * 서버 연동 전 전용: 실패 화면을 확인할 때 넣는 값. 로그인은 아이디 칸에, 설정의 비밀번호 바꾸기는
 * 현재 비밀번호 칸에 넣는다. 그 밖에는 아무 값이나 된다.
 */
const val FAKE_FAILING_ID = "fail"

private const val MAX_FREE_TRIES = 5
private const val LOCK_SECONDS = 30

/**
 * 서버 연동 전 임시 구현: 어떤 아이디·비밀번호든 통과시킨다(계정 값을 코드에 두지 않는다).
 * 로그인은 아이디가 [FAKE_FAILING_ID]일 때만 실패하고, 5번째 실패부터는 잠금 응답을 낸다.
 * 앱을 켤 때마다 첫 로그인으로 보여 비밀번호 바꾸기 화면도 확인할 수 있다.
 */
class FakeAuthGateway : AuthGateway {

    private var firstLogin = true
    private var failures = 0

    override suspend fun login(id: String, password: String): Result<LoginResult> {
        if (id != FAKE_FAILING_ID) {
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

    override suspend fun changePasswordWithCurrent(
        currentPassword: String,
        newPassword: String,
    ): Result<PasswordChangeResult> = Result.success(
        if (currentPassword == FAKE_FAILING_ID) PasswordChangeResult.WrongCurrent else PasswordChangeResult.Changed,
    )

    override suspend fun logout() = Unit
}
