package com.ormo.manager.domain

/** 영업시간을 따로 저장하는 날(Figma 4.1.3). 선언 순서 = 표시 순서 */
enum class HoursDay { MON, TUE, WED, THU, FRI, SAT, SUN, HOLIDAY }

/** 하루 영업시간. 시각은 0시부터 센 분. [isOpen]이 false면 쉬는 날이다. */
data class DayHours(val openMinute: Int, val closeMinute: Int, val isOpen: Boolean)

/**
 * 고객 앱 안내용 영업시간. 시간이 돼도 매장을 자동으로 열거나 닫지 않는다.
 * 모든 [HoursDay]에 값이 있어야 한다.
 */
typealias BusinessHours = Map<HoursDay, DayHours>

/** 초기값(Figma 예시). 실제로는 서버의 매장 설정에서 불러온다. */
val DEFAULT_BUSINESS_HOURS: BusinessHours = HoursDay.entries.associateWith {
    when (it) {
        HoursDay.SAT -> DayHours(10 * 60, 15 * 60, true)
        HoursDay.SUN, HoursDay.HOLIDAY -> DayHours(10 * 60, 15 * 60, false)
        else -> DayHours(8 * 60 + 30, 18 * 60, true)
    }
}

private val TIME_PATTERN = Regex("""([01]\d|2[0-3]):([0-5]\d)""")

/** 24시간 "08:30" 형식만 받는다. 아니면 null. */
fun parseTime(text: String): Int? {
    val match = TIME_PATTERN.matchEntire(text) ?: return null
    val (hour, minute) = match.destructured
    return hour.toInt() * 60 + minute.toInt()
}

fun formatTime(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

/** 숫자만 눌러도 "08:30" 모양이 되도록 ':'를 넣는다(최대 4자리). */
fun formatTimeInput(input: String): String {
    val digits = input.filter { it.isDigit() }.take(4)
    return if (digits.length > 2) digits.take(2) + ":" + digits.drop(2) else digits
}

/** 쉬는 날이 아니면 여는 시각이 닫는 시각보다 빨라야 한다(자정을 넘기는 영업은 없다고 본다). */
fun BusinessHours.isValid(): Boolean =
    keys.containsAll(HoursDay.entries) && values.all { !it.isOpen || it.openMinute < it.closeMinute }

/** 설정 목록에 보여줄 한 줄. [from]~[to]가 같은 시간으로 연다. */
data class HoursSummary(val from: HoursDay, val to: HoursDay, val hours: DayHours)

/**
 * 시간이 똑같이 이어지는 여는 요일 묶음 중 가장 긴 것(길이가 같으면 앞의 것).
 * 월~일이 모두 쉬면 null. 공휴일은 넣지 않는다.
 */
fun BusinessHours.summary(): HoursSummary? {
    val runs = mutableListOf<HoursSummary>()
    HoursDay.entries.filter { it != HoursDay.HOLIDAY }.forEach { day ->
        val hours = getValue(day)
        val last = runs.lastOrNull()
        when {
            !hours.isOpen -> Unit
            last != null && last.hours == hours && last.to.ordinal == day.ordinal - 1 ->
                runs[runs.lastIndex] = last.copy(to = day)
            else -> runs += HoursSummary(day, day, hours)
        }
    }
    return runs.maxByOrNull { it.to.ordinal - it.from.ordinal }
}
