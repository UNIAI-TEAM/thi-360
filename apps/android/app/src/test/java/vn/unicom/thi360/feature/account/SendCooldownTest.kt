package vn.unicom.thi360.feature.account

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendCooldownTest {

    @Test
    fun thresholdBoundariesAt1_3599_3600AndNullFallback() {
        val base = Instant.parse("2026-04-20T10:00:00Z")
        val clock = MutableTestClock(base)

        val oneSec = SendCooldown.start(1L, clock)
        assertTrue(oneSec is SendCooldown.Counting)
        assertEquals(1L, oneSec.remainingSeconds(clock))
        assertEquals("00:01", SendCooldown.formatClock(oneSec.remainingSeconds(clock)))

        val belowHour = SendCooldown.start(3599L, clock)
        assertTrue(belowHour is SendCooldown.Counting)
        assertEquals(3599L, belowHour.remainingSeconds(clock))
        assertEquals("59:59", SendCooldown.formatClock(belowHour.remainingSeconds(clock)))

        val exactHour = SendCooldown.start(3600L, clock)
        assertTrue(exactHour is SendCooldown.DailyLimit)
        assertEquals(base.plusSeconds(3600L), (exactHour as SendCooldown.DailyLimit).until)

        val missingRetryAfter = SendCooldown.start(null, clock)
        assertTrue(missingRetryAfter is SendCooldown.Counting)
        assertEquals(60L, missingRetryAfter.remainingSeconds(clock))
    }

    @Test
    fun dailyLimitFormatsInAsiaHoChiMinhRegardlessOfDeviceTimezone() {
        val targetUtc = Instant.parse("2026-04-20T18:30:00Z")
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("21/4 01:30", SendCooldown.formatDailyLimit(targetUtc))

            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals("21/4 01:30", SendCooldown.formatDailyLimit(targetUtc))

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
            assertEquals("21/4 01:30", SendCooldown.formatDailyLimit(targetUtc))
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun wallClockAdvanceJumpsCountdownCleanlyAndResolvesToReady() {
        val base = Instant.parse("2026-04-20T10:00:00Z")
        val clock = MutableTestClock(base)
        val cooldown = SendCooldown.start(60L, clock)

        clock.advanceSeconds(30L)
        assertEquals(30L, cooldown.remainingSeconds(clock))
        assertEquals("00:30", SendCooldown.formatClock(cooldown.remainingSeconds(clock)))
        assertTrue(cooldown.resolve(clock) is SendCooldown.Counting)

        clock.advanceSeconds(30L)
        assertEquals(0L, cooldown.remainingSeconds(clock))
        assertEquals(SendCooldown.Ready, cooldown.resolve(clock))
    }
}
