package com.ormo.manager.domain

import com.ormo.manager.ui.SettingsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BusinessHoursTest {

    private val closed = DayHours(600, 900, false)

    @Test
    fun time_isTypedAsDigits_andOnlyValidClockTimesParse() {
        assertEquals("08", formatTimeInput("08"))
        assertEquals("08:3", formatTimeInput("083"))
        assertEquals("08:30", formatTimeInput("08:300"))
        assertEquals(510, parseTime("08:30"))
        assertEquals("08:30", formatTime(510))
        assertNull(parseTime("24:00"))
        assertNull(parseTime("08:60"))
        assertNull(parseTime("8:30"))
    }

    @Test
    fun summary_coversTheLongestRunOfIdenticalDays() {
        assertEquals(
            HoursSummary(HoursDay.MON, HoursDay.FRI, DayHours(510, 1080, true)),
            DEFAULT_BUSINESS_HOURS.summary(),
        )
        val lateMonday = DEFAULT_BUSINESS_HOURS + (HoursDay.MON to DayHours(540, 1080, true))
        assertEquals(HoursDay.TUE, lateMonday.summary()?.from)
        assertEquals(HoursDay.FRI, lateMonday.summary()?.to)
        val tuesdayOnly = HoursDay.entries.associateWith { closed } + (HoursDay.TUE to closed.copy(isOpen = true))
        assertEquals(HoursDay.TUE, tuesdayOnly.summary()?.from)
        assertEquals(HoursDay.TUE, tuesdayOnly.summary()?.to)
        assertNull(HoursDay.entries.associateWith { closed }.summary())
    }

    @Test
    fun save_ignoresOpenDayThatClosesBeforeItOpens() {
        val vm = SettingsViewModel()
        val bad = DEFAULT_BUSINESS_HOURS + (HoursDay.MON to DayHours(1080, 510, true))
        vm.saveBusinessHours(bad)
        assertEquals(DEFAULT_BUSINESS_HOURS, vm.businessHours.value)

        val good = DEFAULT_BUSINESS_HOURS + (HoursDay.MON to DayHours(540, 1080, true))
        vm.saveBusinessHours(good)
        assertEquals(good, vm.businessHours.value)
    }
}
