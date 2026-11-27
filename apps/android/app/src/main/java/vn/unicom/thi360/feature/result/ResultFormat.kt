package vn.unicom.thi360.feature.result

import androidx.compose.runtime.Immutable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToLong
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.network.generated.model.AttemptChoiceSummary
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.network.generated.model.AttemptResultSubject
import vn.unicom.thi360.core.network.generated.model.AttemptResultVactSubsection
import vn.unicom.thi360.core.preferences.AppLocale

@Immutable
sealed interface DurationParts {
    data object Zero : DurationParts
    data class Seconds(val seconds: Long) : DurationParts
    data class Minutes(val minutes: Long) : DurationParts
    data class HoursMinutes(val hours: Long, val minutes: Long) : DurationParts
}

@Immutable
data class ResolvedScore(
    val score: BigDecimal,
    val max: BigDecimal?,
)

enum class KnownChoiceOptionCode {
    ANH,
    SCIENCE,
    ENGLISH,
    KH,
    TA,
    CS,
    ICT,
    ;

    companion object {
        fun fromWire(raw: String?): KnownChoiceOptionCode? =
            entries.firstOrNull { it.name == raw?.trim() }
    }
}

enum class KnownVactComponentCode {
    VIETNAMESE,
    ANH,
    TOAN,
    SCIENTIFIC_THINKING,
    ;

    companion object {
        fun fromWire(raw: String?): KnownVactComponentCode? =
            entries.firstOrNull { it.name == raw?.trim() }
    }
}

enum class ResultScope {
    FULL,
    PART,
    SUBJECT,
    ;

    companion object {
        fun fromWire(raw: String?): ResultScope? =
            entries.firstOrNull { it.name == raw?.trim() }
    }
}

enum class ResultStatusTone {
    SUCCESS,
    WARNING,
    INFO,
    DANGER,
    NEUTRAL,
}

object ResultFormat {
    val HO_CHI_MINH_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val DATE_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy", Locale.US)

    private val UUID_REGEX = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        RegexOption.IGNORE_CASE,
    )

    fun parseAttemptUuidOrNull(raw: String?): UUID? {
        val trimmed = raw?.trim().orEmpty()
        if (!UUID_REGEX.matches(trimmed)) return null
        return runCatching { UUID.fromString(trimmed) }.getOrNull()
    }

    fun formatDateTime(timestamp: OffsetDateTime?): String? {
        val instant = timestamp?.toInstant() ?: return null
        return DATE_TIME_FORMATTER.format(instant.atZone(HO_CHI_MINH_ZONE))
    }

    fun formatDateTime(iso: String?): String? {
        val instant = parseInstantOrNull(iso) ?: return null
        return DATE_TIME_FORMATTER.format(instant.atZone(HO_CHI_MINH_ZONE))
    }

    fun formatScore(value: BigDecimal, locale: AppLocale): String {
        val rounded = value.setScale(2, RoundingMode.HALF_UP)
        val normalized = if (rounded.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else rounded
        val symbols = DecimalFormatSymbols(Locale.US).apply {
            if (locale == AppLocale.VI) {
                decimalSeparator = ','
                groupingSeparator = '.'
            } else {
                decimalSeparator = '.'
                groupingSeparator = ','
            }
        }
        val formatter = DecimalFormat("#,##0.##", symbols).apply {
            roundingMode = RoundingMode.HALF_UP
        }
        return formatter.format(normalized)
    }

    fun formatScore(value: Double, locale: AppLocale): String =
        formatScore(BigDecimal.valueOf(value), locale)

    fun formatCount(value: Int, locale: AppLocale): String =
        formatScore(BigDecimal.valueOf(value.toLong()), locale)

    fun computeActualDuration(
        startedAt: OffsetDateTime?,
        finishedAt: OffsetDateTime?,
    ): DurationParts? = computeActualDuration(startedAt?.toInstant(), finishedAt?.toInstant())

    fun computeActualDuration(
        startedAtIso: String?,
        finishedAtIso: String?,
    ): DurationParts? = computeActualDuration(
        parseInstantOrNull(startedAtIso),
        parseInstantOrNull(finishedAtIso),
    )

    private fun computeActualDuration(start: Instant?, end: Instant?): DurationParts? {
        if (start == null || end == null) return null
        val diffMillis = end.toEpochMilli() - start.toEpochMilli()
        val totalSeconds = max(0L, (diffMillis / 1_000.0).roundToLong())
        if (totalSeconds == 0L) return DurationParts.Zero
        if (totalSeconds < 60L) return DurationParts.Seconds(totalSeconds)
        val totalMinutes = totalSeconds / 60L
        if (totalMinutes < 60L) return DurationParts.Minutes(totalMinutes)
        return DurationParts.HoursMinutes(hours = totalMinutes / 60L, minutes = totalMinutes % 60L)
    }

    fun shouldShowAttemptScore(
        state: AttemptResultResponse.State?,
        regrading: Boolean?,
        displayScore: BigDecimal?,
        rawScore: BigDecimal?,
    ): Boolean {
        if (displayScore == null && rawScore == null) return false
        return state == AttemptResultResponse.State.SCORED ||
            (state == AttemptResultResponse.State.PENDING_REVIEW && regrading == true)
    }

    fun resolveDisplayScore(result: AttemptResultResponse): ResolvedScore? {
        if (!shouldShowAttemptScore(result.state, result.regrading, result.displayScore, result.rawScore)) {
            return null
        }
        val score = result.displayScore ?: result.rawScore ?: return null
        val max = result.scaleMax ?: result.rawMaxScore
        return ResolvedScore(score = score, max = max)
    }

    fun resolveStatusTone(
        state: AttemptResultResponse.State?,
        regrading: Boolean?,
    ): ResultStatusTone {
        if (regrading == true) return ResultStatusTone.WARNING
        return when (state) {
            AttemptResultResponse.State.SCORED -> ResultStatusTone.SUCCESS
            AttemptResultResponse.State.PENDING_REVIEW -> ResultStatusTone.WARNING
            AttemptResultResponse.State.GRADING -> ResultStatusTone.INFO
            AttemptResultResponse.State.GRADING_ERROR -> ResultStatusTone.DANGER
            else -> ResultStatusTone.NEUTRAL
        }
    }

    fun canOpenReview(result: AttemptResultResponse): Boolean =
        result.reviewAvailable == true &&
            result.resultView != "SCORE" &&
            (result.state == AttemptResultResponse.State.SCORED ||
                result.state == AttemptResultResponse.State.PENDING_REVIEW)

    fun showFaultBanner(result: AttemptResultResponse): Boolean =
        result.compensated == true ||
            result.faultFlag == "SYSTEM_FAULT" ||
            result.endReason == AttemptResultResponse.EndReason.FAULT_CLOSED

    fun isVact(result: AttemptResultResponse): Boolean = result.examKindCode == "VACT"

    fun resolveCatalogLabel(
        code: String?,
        catalogMap: Map<String, CatalogOptionItem>,
        locale: AppLocale,
    ): String {
        val normalized = code?.trim().orEmpty()
        if (normalized.isEmpty()) return ""
        val option = catalogMap[normalized] ?: return normalized
        val localized = if (locale == AppLocale.EN) option.nameEn else option.nameVi
        return localized.ifBlank { option.nameVi.ifBlank { normalized } }
    }

    fun resolveSubjectLabel(
        subject: AttemptResultSubject,
        subjectMap: Map<String, CatalogOptionItem>,
        locale: AppLocale,
    ): String {
        val code = subject.code?.trim().orEmpty()
        if (code.isNotEmpty() && subjectMap.containsKey(code)) {
            return resolveCatalogLabel(code, subjectMap, locale)
        }
        val name = subject.name?.trim().orEmpty()
        if (name.isNotEmpty()) return name
        return code.ifEmpty { "—" }
    }

    fun resolveVactComponentLabel(
        componentCode: String?,
        subjectMap: Map<String, CatalogOptionItem>,
        locale: AppLocale,
        translateKnown: (KnownVactComponentCode) -> String,
    ): String {
        val code = componentCode?.trim().orEmpty()
        if (code.isEmpty()) return "—"
        val known = KnownVactComponentCode.fromWire(code)
        if (known != null) return translateKnown(known)
        return resolveCatalogLabel(code, subjectMap, locale)
    }

    fun formatAttemptChoices(
        choices: Map<String, AttemptChoiceSummary>?,
        subjectMap: Map<String, CatalogOptionItem>,
        locale: AppLocale,
        translateOptionCode: (KnownChoiceOptionCode) -> String,
    ): List<String> {
        if (choices.isNullOrEmpty()) return emptyList()
        val formatted = mutableListOf<String>()
        for (summary in choices.values) {
            val rawOption = summary.optionCode.trim()
            if (rawOption.isEmpty()) continue
            val known = KnownChoiceOptionCode.fromWire(rawOption)
            val optionLabel = if (known != null) {
                translateOptionCode(known)
            } else {
                resolveCatalogLabel(rawOption, subjectMap, locale)
            }
            val subjectLabels = summary.subjectCodes.orEmpty()
                .map { resolveCatalogLabel(it, subjectMap, locale) }
                .filter { it.isNotEmpty() }
            if (subjectLabels.isNotEmpty()) {
                formatted.add("$optionLabel: ${subjectLabels.joinToString(", ")}")
            } else {
                formatted.add(optionLabel)
            }
        }
        return formatted
    }

    fun isLogicDataSubsection(
        sub: AttemptResultVactSubsection,
        index: Int,
        totalCount: Int,
    ): Boolean = sub.questionCount == 12 || (sub.questionCount != 18 && totalCount == 2 && index == 0)

    private fun parseInstantOrNull(iso: String?): Instant? {
        val trimmed = iso?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(trimmed).toInstant() }
            .recoverCatching { Instant.parse(trimmed) }
            .getOrNull()
    }
}

class ResultClickGuard(
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var lastTriggerMillis: Long = Long.MIN_VALUE

    fun tryRun(action: () -> Unit): Boolean {
        val now = clockMillis()
        if (lastTriggerMillis != Long.MIN_VALUE && (now - lastTriggerMillis) < debounceMillis) {
            return false
        }
        lastTriggerMillis = now
        action()
        return true
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MILLIS: Long = 500L
    }
}
