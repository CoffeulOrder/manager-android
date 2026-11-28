package com.ormo.manager.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 영업일. 자정을 넘기는 영업은 지원하지 않으므로 태블릿 시간대의 날짜를 영업일로 본다. */
fun businessDateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** [millis]가 속한 영업일이 시작하는 시각 */
fun businessDayStartOf(millis: Long, zone: ZoneId): Long =
    businessDateOf(millis, zone).atStartOfDay(zone).toInstant().toEpochMilli()
