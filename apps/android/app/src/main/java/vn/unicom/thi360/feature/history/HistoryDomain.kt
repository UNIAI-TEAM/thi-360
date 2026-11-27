package vn.unicom.thi360.feature.history

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.network.generated.model.AttemptChoiceSummary
import vn.unicom.thi360.core.network.generated.model.AttemptHistoryItem
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.feature.myexams.DISPLAY_TIME_ZONE
import vn.unicom.thi360.feature.myexams.pickLocalizedOrCode

private val HISTORY_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy")
private val HISTORY_DATE_DISPLAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val ISO_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

enum class HistoryScope(val wireValue: String) {
    FULL("FULL"),
    PART("PART"),
    SUBJECT("SUBJECT");

    companion object {
        fun fromWireValue(raw: String?): HistoryScope? =
            entries.firstOrNull { it.wireValue == raw }
    }
}

data class HistoryApiParams(
    val q: String?,
    val kind: String?,
    val scope: String?,
    val from: String?,
    val to: String?,
    val page: Int,
    val size: Int = HistoryFilter.HISTORY_PAGE_SIZE,
)

data class HistoryFilter(
    val q: String = "",
    val kind: String? = null,
    val scope: HistoryScope? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
) {
    val trimmedQuery: String
        get() = q.trim()

    val isEmpty: Boolean
        get() = trimmedQuery.isEmpty() && kind.isNullOrBlank() && scope == null && from == null && to == null

    fun toParams(page: Int): HistoryApiParams = HistoryApiParams(
        q = trimmedQuery.takeIf { it.isNotEmpty() },
        kind = kind?.takeIf { it.isNotBlank() },
        scope = scope?.wireValue,
        from = from?.format(ISO_DATE_FORMATTER),
        to = to?.format(ISO_DATE_FORMATTER),
        page = page,
        size = HISTORY_PAGE_SIZE,
    )

    companion object {
        const val HISTORY_PAGE_SIZE = 20
        val Empty = HistoryFilter()
    }
}

data class HistoryPage(
    val items: List<AttemptHistoryItem>,
    val currentPage: Int,
    val totalItems: Long,
    val totalPages: Int,
    val hasMore: Boolean,
)

enum class HistoryStatusKey {
    REGRADING,
    SCORED,
    PENDING_REVIEW,
    IN_PROGRESS,
    GRADING,
    GRADING_ERROR,
}

enum class HistoryStatusTone {
    SUCCESS,
    WARNING,
    INFO,
    DANGER,
}

data class HistoryStatusBadge(
    val key: HistoryStatusKey,
    val tone: HistoryStatusTone,
)

enum class HistoryFaultBanner {
    COMPENSATED,
    SYSTEM_FAULT,
}

enum class KnownChoiceOptionCode(val wireValue: String) {
    ANH("ANH"),
    SCIENCE("SCIENCE"),
    ENGLISH("ENGLISH"),
    KH("KH"),
    TA("TA"),
    CS("CS"),
    ICT("ICT");

    companion object {
        fun fromCode(raw: String): KnownChoiceOptionCode? =
            entries.firstOrNull { it.wireValue == raw }
    }
}

sealed interface FormattedChoiceOption {
    data class Known(val code: KnownChoiceOptionCode) : FormattedChoiceOption
    data class Resolved(val label: String) : FormattedChoiceOption
}

data class FormattedChoiceEntry(
    val option: FormattedChoiceOption,
    val subjectNames: List<String>,
)

sealed interface FormattedScopeDetail {
    data class Part(val partCode: String) : FormattedScopeDetail
    data class Subject(val subjectName: String) : FormattedScopeDetail
}

object HistoryRowFormat {
    fun resolveStatus(
        state: AttemptHistoryItem.State?,
        regrading: Boolean?,
    ): HistoryStatusBadge {
        if (regrading == true) {
            return HistoryStatusBadge(HistoryStatusKey.REGRADING, HistoryStatusTone.WARNING)
        }
        return when (state) {
            AttemptHistoryItem.State.SCORED ->
                HistoryStatusBadge(HistoryStatusKey.SCORED, HistoryStatusTone.SUCCESS)
            AttemptHistoryItem.State.PENDING_REVIEW ->
                HistoryStatusBadge(HistoryStatusKey.PENDING_REVIEW, HistoryStatusTone.WARNING)
            AttemptHistoryItem.State.STARTING,
            AttemptHistoryItem.State.IN_PROGRESS,
            -> HistoryStatusBadge(HistoryStatusKey.IN_PROGRESS, HistoryStatusTone.INFO)
            AttemptHistoryItem.State.GRADING ->
                HistoryStatusBadge(HistoryStatusKey.GRADING, HistoryStatusTone.INFO)
            AttemptHistoryItem.State.GRADING_ERROR,
            AttemptHistoryItem.State.ABORTED,
            null,
            -> HistoryStatusBadge(HistoryStatusKey.GRADING_ERROR, HistoryStatusTone.DANGER)
        }
    }

    fun resolveFaultBanner(
        compensated: Boolean?,
        faultFlag: String?,
    ): HistoryFaultBanner? = when {
        compensated == true -> HistoryFaultBanner.COMPENSATED
        faultFlag == SYSTEM_FAULT_FLAG -> HistoryFaultBanner.SYSTEM_FAULT
        else -> null
    }

    fun shouldShowScore(item: AttemptHistoryItem): Boolean {
        if (item.displayScore == null && item.rawScore == null) return false
        return item.state == AttemptHistoryItem.State.SCORED ||
            (item.state == AttemptHistoryItem.State.PENDING_REVIEW && item.regrading == true)
    }

    fun formatScoreNumber(value: BigDecimal, locale: AppLocale): String {
        val rounded = value.setScale(2, RoundingMode.HALF_UP)
        val normalized = if (rounded.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else rounded
        val numberLocale = if (locale == AppLocale.EN) Locale.US else Locale.forLanguageTag("vi-VN")
        val formatter = NumberFormat.getNumberInstance(numberLocale).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
            roundingMode = RoundingMode.HALF_UP
        }
        return formatter.format(normalized)
    }

    fun formatScore(item: AttemptHistoryItem, locale: AppLocale): String? {
        if (!shouldShowScore(item)) return null
        val score = item.displayScore ?: item.rawScore ?: return null
        val max = item.scaleMax ?: item.maxScore
        val formattedScore = formatScoreNumber(score, locale)
        return if (max != null) {
            "$formattedScore/${formatScoreNumber(max, locale)}"
        } else {
            formattedScore
        }
    }

    fun showVactPracticeNote(item: AttemptHistoryItem): Boolean =
        item.examKindCode == VACT_EXAM_KIND_CODE && shouldShowScore(item)

    fun resolveScopeDetail(
        item: AttemptHistoryItem,
        subjectMap: Map<String, CatalogOptionItem>,
        locale: AppLocale,
    ): FormattedScopeDetail? {
        if (item.scope == HistoryScope.PART.wireValue && !item.partCode.isNullOrBlank()) {
            return FormattedScopeDetail.Part(item.partCode)
        }
        if (item.scope == HistoryScope.SUBJECT.wireValue && !item.subjectCode.isNullOrBlank()) {
            val subjectName = resolveCatalogLabel(item.subjectCode, subjectMap, locale)
            return FormattedScopeDetail.Subject(subjectName)
        }
        return null
    }

    fun formatChoices(
        choices: Map<String, AttemptChoiceSummary>?,
        subjectMap: Map<String, CatalogOptionItem>,
        locale: AppLocale,
    ): List<FormattedChoiceEntry> {
        if (choices.isNullOrEmpty()) return emptyList()
        val result = mutableListOf<FormattedChoiceEntry>()
        for (summary in choices.values) {
            val optionCode = summary.optionCode.trim()
            if (optionCode.isEmpty()) continue
            val known = KnownChoiceOptionCode.fromCode(optionCode)
            val option = if (known != null) {
                FormattedChoiceOption.Known(known)
            } else {
                FormattedChoiceOption.Resolved(resolveCatalogLabel(optionCode, subjectMap, locale))
            }
            val subjects = summary.subjectCodes.orEmpty()
                .map { resolveCatalogLabel(it, subjectMap, locale) }
                .filter { it.isNotBlank() }
            result.add(FormattedChoiceEntry(option = option, subjectNames = subjects))
        }
        return result
    }

    fun formatStartedAt(startedAt: OffsetDateTime?): String? {
        if (startedAt == null) return null
        return startedAt.atZoneSameInstant(DISPLAY_TIME_ZONE)
            .format(HISTORY_DATE_TIME_FORMATTER)
    }

    fun formatStaleTime(epochMillis: Long?): String? {
        if (epochMillis == null || epochMillis <= 0L) return null
        return Instant.ofEpochMilli(epochMillis)
            .atZone(DISPLAY_TIME_ZONE)
            .format(HISTORY_DATE_TIME_FORMATTER)
    }

    fun formatFilterDate(date: LocalDate): String = date.format(HISTORY_DATE_DISPLAY_FORMATTER)

    fun pickCatalogName(item: CatalogOptionItem, locale: AppLocale): String =
        pickLocalizedOrCode(item.nameVi, item.nameEn, item.code, locale)

    fun resolveCatalogLabel(
        code: String,
        catalogMap: Map<String, CatalogOptionItem>,
        locale: AppLocale,
    ): String {
        val option = catalogMap[code] ?: return code
        return pickCatalogName(option, locale)
    }

    fun localDateToUtcMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun utcMillisToLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun stableKey(item: AttemptHistoryItem, fallbackIndex: Int): String =
        item.attemptId?.toString() ?: "attempt-fallback-$fallbackIndex"

    private const val SYSTEM_FAULT_FLAG = "SYSTEM_FAULT"
    private const val VACT_EXAM_KIND_CODE = "VACT"
}
