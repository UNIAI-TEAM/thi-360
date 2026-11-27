package vn.unicom.thi360.feature.catalog

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import vn.unicom.thi360.core.preferences.AppLocale

object CatalogFormatting {
    val VIETNAM_ZONE_ID: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")
    private val DAY_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT)

    fun discountPercent(listPrice: Long, price: Long): Int? {
        if (listPrice <= 0L || price >= listPrice) return null
        val pct = (((listPrice - price).toDouble() / listPrice.toDouble()) * 100.0).roundToInt()
        return maxOf(1, pct)
    }

    fun formatPriceAmount(amount: Long, locale: AppLocale): String {
        val symbols = decimalSymbols(locale)
        return DecimalFormat("#,##0", symbols).format(amount)
    }

    fun formatPrice(amount: Long, locale: AppLocale): String {
        val formatted = formatPriceAmount(amount, locale)
        return when (locale) {
            AppLocale.VI -> "$formatted đ"
            AppLocale.EN -> "$formatted VND"
        }
    }

    fun formatPromoDayMonth(isoInstant: String): String? {
        val instant = runCatching { Instant.parse(isoInstant) }
            .recoverCatching { OffsetDateTime.parse(isoInstant).toInstant() }
            .getOrNull()
            ?: return null
        return DAY_MONTH_FORMATTER.format(instant.atZone(VIETNAM_ZONE_ID))
    }

    fun formatLocaleNumber(value: Number, locale: AppLocale): String {
        val symbols = decimalSymbols(locale)
        return DecimalFormat("#,##0.##", symbols).format(value)
    }

    fun formatIntRange(range: IntRangeValue?, locale: AppLocale? = null): String? {
        if (range == null) return null
        val fmt: (Int) -> String = { n ->
            if (locale != null) formatLocaleNumber(n, locale) else n.toString()
        }
        return if (range.min == range.max) fmt(range.min) else "${fmt(range.min)}–${fmt(range.max)}"
    }

    fun formatDecimalRange(range: DecimalRangeValue?, locale: AppLocale): String? {
        if (range == null) return null
        val minText = formatBigDecimal(range.min, locale)
        val maxText = formatBigDecimal(range.max, locale)
        return if (range.min.compareTo(range.max) == 0) minText else "$minText–$maxText"
    }

    fun formatBigDecimal(value: BigDecimal, locale: AppLocale): String {
        val symbols = decimalSymbols(locale)
        return DecimalFormat("#,##0.##", symbols).format(value)
    }

    fun pickLocalizedName(nameVi: String?, nameEn: String?, locale: AppLocale): String =
        when (locale) {
            AppLocale.EN -> nameEn?.takeIf { it.isNotBlank() } ?: nameVi.orEmpty()
            AppLocale.VI -> nameVi?.takeIf { it.isNotBlank() } ?: nameEn.orEmpty()
        }

    fun resolveCoverUrl(apiBaseUrl: String, coverUrl: String?): String? {
        val trimmed = coverUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        return "${apiBaseUrl.trimEnd('/')}/${trimmed.trimStart('/')}"
    }

    fun mergeAnswerTypes(groups: List<AnswerTypeGroupData>): List<MergedAnswerType> {
        val totals = linkedMapOf<String, Int>()
        for (group in groups) {
            val type = group.childAnswerType?.takeIf { it.isNotBlank() } ?: group.questionType
            totals[type] = (totals[type] ?: 0) + group.questionCount
        }
        return totals.entries.map { (type, count) -> MergedAnswerType(type = type, count = count) }
    }

    fun hasEssay(detail: ProductDetailData): Boolean {
        val allGroups = buildList {
            detail.parts.forEach { addAll(it.answerTypes) }
            detail.choices.forEach { group ->
                group.options.forEach { option ->
                    addAll(option.answerTypes)
                    option.subjectAnswerTypes.forEach { addAll(it.answerTypes) }
                }
            }
        }
        return allGroups.any { group ->
            (group.childAnswerType?.takeIf { it.isNotBlank() } ?: group.questionType) == ESSAY_TYPE
        }
    }

    private fun decimalSymbols(locale: AppLocale): DecimalFormatSymbols =
        DecimalFormatSymbols(Locale.ROOT).apply {
            groupingSeparator = if (locale == AppLocale.EN) ',' else '.'
            decimalSeparator = if (locale == AppLocale.EN) '.' else ','
        }

    private const val ESSAY_TYPE = "ESSAY"
}

data class MergedAnswerType(
    val type: String,
    val count: Int,
)
