package vn.unicom.thi360.feature.player

import androidx.compose.runtime.Immutable
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import vn.unicom.thi360.core.preferences.AppLocale

@Immutable
data class TimerParts(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
)

object TimerText {
    const val EXAM_URGENT_THRESHOLD_MS: Long = 60_000L
    const val CHOICE_URGENT_THRESHOLD_MS: Long = 30_000L
    val VIETNAM_ZONE_ID: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    private val viLastSeenFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))

    private val enLastSeenFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm:ss", Locale.US)

    fun ceilSeconds(remainingMs: Long): Long =
        if (remainingMs <= 0L) 0L else (remainingMs + 999L) / 1_000L

    fun parts(remainingMs: Long): TimerParts {
        val totalSeconds = ceilSeconds(remainingMs)
        val hours = (totalSeconds / 3_600L).toInt()
        val minutes = ((totalSeconds % 3_600L) / 60L).toInt()
        val seconds = (totalSeconds % 60L).toInt()
        return TimerParts(hours = hours, minutes = minutes, seconds = seconds)
    }

    fun format(remainingMs: Long): String {
        val (hours, minutes, seconds) = parts(remainingMs)
        return if (hours > 0) {
            "$hours:${minutes.pad2()}:${seconds.pad2()}"
        } else {
            "${minutes.pad2()}:${seconds.pad2()}"
        }
    }

    fun isUrgent(
        remainingMs: Long,
        thresholdMs: Long = EXAM_URGENT_THRESHOLD_MS,
    ): Boolean = remainingMs <= thresholdMs

    fun formatLastSeenAt(
        instant: OffsetDateTime,
        locale: AppLocale,
    ): String {
        val zoned = instant.atZoneSameInstant(VIETNAM_ZONE_ID)
        return when (locale) {
            AppLocale.VI -> zoned.format(viLastSeenFormatter)
            AppLocale.EN -> zoned.format(enLastSeenFormatter)
        }
    }

    private fun Int.pad2(): String = if (this < 10) "0$this" else toString()
}
