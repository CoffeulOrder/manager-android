package com.ormo.manager.auth

/** 로그인 요청에 대한 서버의 답 */
sealed interface LoginResult {
    /** [firstLogin]이면 비밀번호를 바꾼 뒤에 들어간다. */
    data class Success(val firstLogin: Boolean) : LoginResult

    /** 아이디나 비밀번호가 틀렸다. 어느 쪽인지는 알리지 않는다. */
    data object Rejected : LoginResult

    /** 여러 번 틀려 [retryAfterSeconds]초 뒤에 다시 시도할 수 있다. 횟수와 시간은 서버가 센다. */
    data class Locked(val retryAfterSeconds: Int) : LoginResult
}

/**
 * 매장 계정 인증. 백엔드를 붙일 때 이 인터페이스의 구현만 바꾼다.
 * 실패(Result.failure)는 통신 오류처럼 답을 받지 못한 경우다.
 * 토큰 저장(30일 로그인 유지)도 실제 구현이 맡는다. 비밀번호는 저장하지 않는다.
 */
interface AuthGateway {
    suspend fun login(id: String, password: String): Result<LoginResult>

    suspend fun changePassword(newPassword: String): Result<Unit>

    suspend fun logout()
}
