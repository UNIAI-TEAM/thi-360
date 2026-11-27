package vn.unicom.thi360.feature.catalog

import vn.unicom.thi360.core.preferences.AppLocale

enum class CatalogFormat(val apiValue: String) {
    FULL("FULL"),
    PART("PART"),
    SUBJECT("SUBJECT"),
    BUNDLE("BUNDLE");

    companion object {
        fun fromApiOrNull(raw: String?): CatalogFormat? =
            entries.firstOrNull { it.apiValue == raw }
    }
}

enum class CatalogSort(val apiValue: String) {
    NEWEST("NEWEST"),
    PRICE_ASC("PRICE_ASC"),
    PRICE_DESC("PRICE_DESC");

    companion object {
        fun fromApiOrNull(raw: String?): CatalogSort? =
            entries.firstOrNull { it.apiValue == raw }
    }
}

data class CatalogQueryParams(
    val q: String?,
    val kind: String?,
    val format: String?,
    val part: String?,
    val subject: String?,
    val sort: String,
    val page: Int,
    val size: Int = CatalogQuery.CATALOG_PAGE_SIZE,
)

data class CatalogQuery(
    val q: String = "",
    val kind: String? = null,
    val format: CatalogFormat? = null,
    val part: String? = null,
    val subject: String? = null,
    val sort: CatalogSort = CatalogSort.NEWEST,
) {
    val isFiltered: Boolean
        get() = q.trim().isNotEmpty() ||
            !kind.isNullOrBlank() ||
            format != null ||
            !part.isNullOrBlank() ||
            !subject.isNullOrBlank() ||
            sort != CatalogSort.NEWEST

    val activeFilterSheetCount: Int
        get() {
            var count = 0
            if (format != null) count++
            if (!part.isNullOrBlank() || !subject.isNullOrBlank()) count++
            if (sort != CatalogSort.NEWEST) count++
            return count
        }

    fun withSearch(raw: String): CatalogQuery = copy(q = raw.trim())

    fun withKind(nextKind: String?): CatalogQuery {
        val normalized = nextKind?.trim()?.takeIf { it.isNotEmpty() }
        return copy(kind = normalized, part = null)
    }

    fun withFormat(nextFormat: CatalogFormat?): CatalogQuery = copy(format = nextFormat)

    fun withPart(kindCode: String, partCode: String): CatalogQuery =
        copy(
            kind = kindCode.trim().takeIf { it.isNotEmpty() },
            part = partCode.trim().takeIf { it.isNotEmpty() },
            subject = null,
        )

    fun withSubject(subjectCode: String?): CatalogQuery {
        val normalized = subjectCode?.trim()?.takeIf { it.isNotEmpty() }
        return copy(part = null, subject = normalized)
    }

    fun clearPartAndSubject(): CatalogQuery = copy(part = null, subject = null)

    fun withSort(nextSort: CatalogSort): CatalogQuery = copy(sort = nextSort)

    fun cleared(): CatalogQuery = CatalogQuery()

    fun toParams(page: Int): CatalogQueryParams {
        val normalizedKind = kind?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedPart = if (normalizedKind != null) part?.trim()?.takeIf { it.isNotEmpty() } else null
        return CatalogQueryParams(
            q = q.trim().takeIf { it.isNotEmpty() },
            kind = normalizedKind,
            format = format?.apiValue,
            part = normalizedPart,
            subject = subject?.trim()?.takeIf { it.isNotEmpty() },
            sort = sort.apiValue,
            page = page.coerceAtLeast(0),
            size = CATALOG_PAGE_SIZE,
        )
    }

    companion object {
        const val CATALOG_PAGE_SIZE = 12
    }
}

sealed interface PartSubjectOption {
    val key: String

    data object All : PartSubjectOption {
        override val key: String = "ALL"
    }

    data class Part(
        val kindCode: String,
        val partCode: String,
        val label: String,
    ) : PartSubjectOption {
        override val key: String = "P:$kindCode:$partCode"
    }

    data class Subject(
        val subjectCode: String,
        val label: String,
    ) : PartSubjectOption {
        override val key: String = "S:$subjectCode"
    }
}

object CatalogFilterOptions {
    fun kindChips(filters: StorefrontFiltersData?): List<KindFilterItem> {
        if (filters == null) return emptyList()
        return if (filters.catalogKinds.isNotEmpty()) filters.catalogKinds else filters.kinds
    }

    fun formats(filters: StorefrontFiltersData?): List<CatalogFormat> {
        if (filters == null) return emptyList()
        val available = filters.formats.mapTo(mutableSetOf()) { it.code }
        return CatalogFormat.entries.filter { it.apiValue in available }
    }

    fun partAndSubjectOptions(
        filters: StorefrontFiltersData?,
        selectedKind: String?,
        locale: AppLocale,
    ): List<PartSubjectOption> {
        val options = mutableListOf<PartSubjectOption>(PartSubjectOption.All)
        if (filters == null) return options

        filters.parts
            .filter { selectedKind.isNullOrBlank() || it.kindCode == selectedKind }
            .forEach { part ->
                val label = if (selectedKind.isNullOrBlank()) {
                    "${part.kindCode} · ${part.name}"
                } else {
                    part.name
                }
                options += PartSubjectOption.Part(
                    kindCode = part.kindCode,
                    partCode = part.code,
                    label = label,
                )
            }

        filters.subjects.forEach { subject ->
            val label = CatalogFormatting.pickLocalizedName(subject.nameVi, subject.nameEn, locale)
                .ifBlank { subject.code }
            options += PartSubjectOption.Subject(
                subjectCode = subject.code,
                label = label,
            )
        }

        return options
    }

    fun selectedPartSubjectKey(query: CatalogQuery): String = when {
        !query.part.isNullOrBlank() && !query.kind.isNullOrBlank() -> "P:${query.kind}:${query.part}"
        !query.subject.isNullOrBlank() -> "S:${query.subject}"
        else -> PartSubjectOption.All.key
    }
}
