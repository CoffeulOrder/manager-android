package com.ormo.manager.domain

/** 비밀번호 규칙(고객 앱과 같다): 8자 이상 + 영문과 숫자 */
const val MIN_PASSWORD_LENGTH = 8

fun isLongEnoughPassword(password: String): Boolean = password.length >= MIN_PASSWORD_LENGTH

fun hasLetterAndDigit(password: String): Boolean =
    password.any { it in 'a'..'z' || it in 'A'..'Z' } && password.any { it in '0'..'9' }

fun isValidPassword(password: String): Boolean =
    isLongEnoughPassword(password) && hasLetterAndDigit(password)
