package vn.unicom.thi360.feature.account

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

sealed interface SendCooldown {
    data object Ready : SendCooldown
    data class Counting(val until: Instant) : SendCooldown
    data class DailyLimit(val until: Instant) : SendCooldown

    fun remainingSeconds(clock: Clock): Long = when (this) {
        Ready -> 0L
        is Counting -> remainingUntil(until, clock)
        is DailyLimit -> remainingUntil(until, clock)
    }

    fun resolve(clock: Clock): SendCooldown = when (this) {
        Ready -> Ready
        is Counting -> if (remainingUntil(until, clock) <= 0L) Ready else this
        is DailyLimit -> if (remainingUntil(until, clock) <= 0L) Ready else this
    }

    companion object {
        const val DEFAULT_SECONDS = 60L
        const val DAILY_LIMIT_THRESHOLD_SECONDS = 3_600L

        val HO_CHI_MINH_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
        private val DAILY_LIMIT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M HH:mm")

        fun start(retryAfterSeconds: Long?, clock: Clock): SendCooldown {
            val seconds = (retryAfterSeconds ?: DEFAULT_SECONDS).coerceAtLeast(1L)
            val until = clock.instant().plusSeconds(seconds)
            return if (seconds >= DAILY_LIMIT_THRESHOLD_SECONDS) {
                DailyLimit(until)
            } else {
                Counting(until)
            }
        }

        fun formatClock(seconds: Long): String {
            val clamped = seconds.coerceAtLeast(0L)
            val minutes = clamped / 60L
            val secs = clamped % 60L
            return "%02d:%02d".format(minutes, secs)
        }

        fun formatDailyLimit(until: Instant): String =
            DAILY_LIMIT_FORMATTER.format(until.atZone(HO_CHI_MINH_ZONE))

        private fun remainingUntil(until: Instant, clock: Clock): Long {
            val millis = Duration.between(clock.instant(), until).toMillis()
            if (millis <= 0L) return 0L
            return ceil(millis / 1000.0).toLong()
        }
    }
}
