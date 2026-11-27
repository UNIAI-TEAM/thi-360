package vn.unicom.thi360.feature.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.preferences.AppLocale

class CatalogQueryTest {

    private val sampleFilters = StorefrontFiltersData(
        catalogKinds = listOf(
            KindFilterItem("HSA", "Đánh giá năng lực HSA", "HSA"),
            KindFilterItem("QDA", "Đánh giá năng lực QDA", "QDA"),
        ),
        kinds = listOf(
            KindFilterItem("HSA", "Đánh giá năng lực HSA", "HSA", count = 5),
        ),
        formats = listOf(
            FormatFilterItem("FULL", 3),
            FormatFilterItem("BUNDLE", 2),
        ),
        parts = listOf(
            PartFilterItem("HSA", "P1", "Tư duy định lượng", 2),
            PartFilterItem("QDA", "Q1", "Toán QDA", 1),
        ),
        subjects = listOf(
            SubjectFilterItem("TOAN", "Toán học", "Mathematics", 4),
        ),
    )

    @Test
    fun defaultQueryIsNotFilteredAndProducesNewestPageZeroSize12() {
        val query = CatalogQuery()
        assertFalse(query.isFiltered)
        assertEquals(0, query.activeFilterSheetCount)

        val params = query.toParams(page = 0)
        assertNull(params.q)
        assertNull(params.kind)
        assertNull(params.format)
        assertNull(params.part)
        assertNull(params.subject)
        assertEquals("NEWEST", params.sort)
        assertEquals(0, params.page)
        assertEquals(12, params.size)
    }

    @Test
    fun changingKindClearsPartButKeepsSubject() {
        val withPart = CatalogQuery().withPart("HSA", "P1")
        assertEquals("HSA", withPart.kind)
        assertEquals("P1", withPart.part)
        assertNull(withPart.subject)

        val changedKind = withPart.withKind("QDA")
        assertEquals("QDA", changedKind.kind)
        assertNull(changedKind.part)

        val withSubjectAndKind = CatalogQuery().withKind("HSA").withSubject("TOAN")
        val changedKindKeepingSubject = withSubjectAndKind.withKind("QDA")
        assertEquals("QDA", changedKindKeepingSubject.kind)
        assertEquals("TOAN", changedKindKeepingSubject.subject)
    }

    @Test
    fun selectingPartSetsKindAndClearsSubjectAndSelectingSubjectClearsPart() {
        val start = CatalogQuery().withSubject("TOAN").withKind("QDA")
        val afterPart = start.withPart("HSA", "P1")
        assertEquals("HSA", afterPart.kind)
        assertEquals("P1", afterPart.part)
        assertNull(afterPart.subject)

        val afterSubject = afterPart.withSubject("TOAN")
        assertEquals("HSA", afterSubject.kind)
        assertNull(afterSubject.part)
        assertEquals("TOAN", afterSubject.subject)
    }

    @Test
    fun searchTrimsWhitespaceAndPreservesOverlongQueryForServer() {
        val blank = CatalogQuery().withSearch("   \t  ")
        assertFalse(blank.isFiltered)
        assertNull(blank.toParams(0).q)

        val longText = "A".repeat(140)
        val trimmed = CatalogQuery().withSearch("  $longText  ")
        assertTrue(trimmed.isFiltered)
        assertEquals(140, trimmed.q.length)
        assertEquals(longText, trimmed.toParams(0).q)
    }

    @Test
    fun kindChipsPreferCatalogKindsAndFallbackToKinds() {
        assertEquals(listOf("HSA", "QDA"), CatalogFilterOptions.kindChips(sampleFilters).map { it.code })

        val noCatalogKinds = sampleFilters.copy(catalogKinds = emptyList())
        assertEquals(listOf("HSA"), CatalogFilterOptions.kindChips(noCatalogKinds).map { it.code })

        val emptyAll = sampleFilters.copy(catalogKinds = emptyList(), kinds = emptyList())
        assertTrue(CatalogFilterOptions.kindChips(emptyAll).isEmpty())
    }

    @Test
    fun partAndSubjectOptionsPrefixKindWhenNoKindSelectedAndFilterWhenKindSelected() {
        val allOptions = CatalogFilterOptions.partAndSubjectOptions(sampleFilters, selectedKind = null, locale = AppLocale.VI)
        val partsWhenUnfiltered = allOptions.filterIsInstance<PartSubjectOption.Part>()
        assertEquals(listOf("HSA · Tư duy định lượng", "QDA · Toán QDA"), partsWhenUnfiltered.map { it.label })

        val hsaOptions = CatalogFilterOptions.partAndSubjectOptions(sampleFilters, selectedKind = "HSA", locale = AppLocale.EN)
        val partsWhenHsa = hsaOptions.filterIsInstance<PartSubjectOption.Part>()
        assertEquals(listOf("Tư duy định lượng"), partsWhenHsa.map { it.label })
        val subjectsEn = hsaOptions.filterIsInstance<PartSubjectOption.Subject>()
        assertEquals(listOf("Mathematics"), subjectsEn.map { it.label })
    }

    @Test
    fun formatsOnlyReturnValuesPresentInFilters() {
        assertEquals(listOf(CatalogFormat.FULL, CatalogFormat.BUNDLE), CatalogFilterOptions.formats(sampleFilters))
    }

    @Test
    fun clearResetsAllFiltersAndSort() {
        val dirty = CatalogQuery()
            .withSearch("HSA")
            .withKind("HSA")
            .withFormat(CatalogFormat.BUNDLE)
            .withSubject("TOAN")
            .withSort(CatalogSort.PRICE_DESC)
        assertTrue(dirty.isFiltered)
        assertEquals(3, dirty.activeFilterSheetCount)

        val cleared = dirty.cleared()
        assertFalse(cleared.isFiltered)
        assertEquals(CatalogQuery(), cleared)
    }
}
