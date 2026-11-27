package com.ormo.manager.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * 금액 표시 형식: 10500 -> "10,500원".
 * 로케일에 영향받지 않도록 직접 3자리 구분한다. ("원"은 통화 단위 표기이며 화면 문구가 아니다.)
 */
fun formatWon(amount: Int): String {
    val digits = abs(amount.toLong()).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    val sign = if (amount < 0) "-" else ""
    return "$sign${grouped}원"
}

private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 24시간 "13:05" 형식 */
fun formatClock(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    clockFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** 남은 시간 "6:40". 초는 올림이라 0:00은 다 지났을 때만 나온다. */
fun formatCountdown(remainingMillis: Long): String {
    val seconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/** 접수 후 경과 분(내림). 시계가 뒤로 가도 음수가 되지 않는다. */
fun elapsedMinutes(createdAt: Long, now: Long): Long =
    ((now - createdAt).coerceAtLeast(0L)) / 60_000L
