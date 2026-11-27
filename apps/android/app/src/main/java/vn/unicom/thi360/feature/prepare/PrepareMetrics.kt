package vn.unicom.thi360.feature.prepare

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.ChoiceOptionView
import vn.unicom.thi360.core.preferences.AppLocale

val PREPARE_DISPLAY_TIME_ZONE: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
private val PREPARE_DAY_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

data class SelectedPathMetrics(
    val partCount: Int,
    val questionCount: Int?,
    val durationMinutes: Int?,
    val maxPoints: BigDecimal?,
)

object PrepareMetrics {
    fun of(
        response: AttemptPrepareResponse,
        selection: PreChoiceSelection,
    ): SelectedPathMetrics {
        val selectedOption = response.preChoice?.options
            ?.firstOrNull { !it.code.isNullOrBlank() && it.code == selection.optionCode }
        return of(response, selectedOption)
    }

    fun of(
        response: AttemptPrepareResponse,
        selectedOption: ChoiceOptionView? = null,
    ): SelectedPathMetrics {
        val partCount = response.parts.size
        var questionSum = 0
        var hasQuestionCount = false
        var pointsSum = BigDecimal.ZERO
        var hasMaxPoints = false
        var durationSum = 0
        var hasPartDuration = false

        for (part in response.parts) {
            part.questionCount?.let {
                questionSum += it
                hasQuestionCount = true
            }
            part.maxPoints?.let {
                pointsSum = pointsSum.add(it)
                hasMaxPoints = true
            }
            part.durationMinutes?.let {
                durationSum += it
                hasPartDuration = true
            }
        }

        val fallbackQuestions = if (hasQuestionCount) questionSum else null
        val fallbackPoints = if (hasMaxPoints) normalizePoints(pointsSum) else null
        val fallbackDuration = response.durationMinutes ?: if (hasPartDuration) durationSum else null

        return SelectedPathMetrics(
            partCount = partCount,
            questionCount = selectedOption?.questionCount ?: fallbackQuestions,
            durationMinutes = selectedOption?.durationMinutes ?: fallbackDuration,
            maxPoints = selectedOption?.maxPoints?.let(::normalizePoints) ?: fallbackPoints,
        )
    }

    internal fun normalizePoints(value: BigDecimal): BigDecimal {
        val rounded = value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
        return when {
            rounded.compareTo(BigDecimal.ZERO) == 0 -> BigDecimal.ZERO
            rounded.scale() < 0 -> rounded.setScale(0)
            else -> rounded
        }
    }
}

fun formatPreparePoints(value: BigDecimal, locale: AppLocale): String {
    val normalized = PrepareMetrics.normalizePoints(value)
    val javaLocale = if (locale == AppLocale.EN) Locale.US else Locale.forLanguageTag("vi-VN")
    val formatter = NumberFormat.getNumberInstance(javaLocale).apply {
        maximumFractionDigits = 3
        minimumFractionDigits = 0
        isGroupingUsed = false
        roundingMode = RoundingMode.HALF_UP
    }
    return formatter.format(normalized)
}

fun formatPrepareDayMonth(timestamp: OffsetDateTime): String =
    timestamp.atZoneSameInstant(PREPARE_DISPLAY_TIME_ZONE).format(PREPARE_DAY_MONTH_FORMATTER)

fun pickPrepareLocalizedName(
    nameVi: String?,
    nameEn: String?,
    code: String?,
    locale: AppLocale,
): String {
    val vi = nameVi?.trim().orEmpty()
    val en = nameEn?.trim().orEmpty()
    val fallbackCode = code?.trim().orEmpty()
    return when (locale) {
        AppLocale.EN -> en.ifEmpty { vi.ifEmpty { fallbackCode } }
        AppLocale.VI -> vi.ifEmpty { en.ifEmpty { fallbackCode } }
    }
}
