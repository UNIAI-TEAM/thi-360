package vn.unicom.thi360.feature.player

import java.time.OffsetDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.preferences.AppLocale

class TimerTextTest {

    @Test
    fun formatsMinutesSecondsAndHoursWithCeilRounding() {
        assertEquals("00:00", TimerText.format(-500L))
        assertEquals("00:00", TimerText.format(0L))
        assertEquals("00:01", TimerText.format(1L))
        assertEquals("00:01", TimerText.format(999L))
        assertEquals("00:01", TimerText.format(1_000L))
        assertEquals("00:02", TimerText.format(1_001L))
        assertEquals("00:59", TimerText.format(59_000L))
        assertEquals("01:00", TimerText.format(59_001L))
        assertEquals("01:00", TimerText.format(60_000L))
        assertEquals("01:01", TimerText.format(60_001L))
        assertEquals("59:59", TimerText.format(3_599_000L))
        assertEquals("1:00:00", TimerText.format(3_599_001L))
        assertEquals("1:00:00", TimerText.format(3_600_000L))
        assertEquals("1:00:01", TimerText.format(3_600_001L))
        assertEquals("2:05:09", TimerText.format((2 * 3600 + 5 * 60 + 9) * 1_000L))
    }

    @Test
    fun urgencyThresholdMatches60sExamAnd30sChoice() {
        assertTrue(TimerText.isUrgent(0L, TimerText.EXAM_URGENT_THRESHOLD_MS))
        assertTrue(TimerText.isUrgent(60_000L, TimerText.EXAM_URGENT_THRESHOLD_MS))
        assertFalse(TimerText.isUrgent(60_001L, TimerText.EXAM_URGENT_THRESHOLD_MS))

        assertTrue(TimerText.isUrgent(30_000L, TimerText.CHOICE_URGENT_THRESHOLD_MS))
        assertFalse(TimerText.isUrgent(30_001L, TimerText.CHOICE_URGENT_THRESHOLD_MS))
    }

    @Test
    fun formatsLastSeenAtInVietnamTimezoneRegardlessOfDeviceTimezone() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val instant = OffsetDateTime.parse("2026-11-20T10:30:45Z")
            val viFormatted = TimerText.formatLastSeenAt(instant, AppLocale.VI)
            val enFormatted = TimerText.formatLastSeenAt(instant, AppLocale.EN)

            assertTrue("Expected 17:30:45 (UTC+7) in VI: $viFormatted", viFormatted.contains("17:30:45"))
            assertTrue("Expected 20/11/2026 in VI: $viFormatted", viFormatted.contains("20/11/2026"))
            assertTrue("Expected 17:30:45 (UTC+7) in EN: $enFormatted", enFormatted.contains("17:30:45"))
            assertTrue("Expected 2026 in EN: $enFormatted", enFormatted.contains("2026"))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }
}
