package vn.unicom.thi360.feature.catalog

import java.math.BigDecimal
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.unicom.thi360.core.preferences.AppLocale

class PriceFormatTest {

    @Test
    fun formatsPricesInVietnameseAndEnglishIncludingZero() {
        assertEquals("0 đ", CatalogFormatting.formatPrice(0L, AppLocale.VI))
        assertEquals("0 VND", CatalogFormatting.formatPrice(0L, AppLocale.EN))
        assertEquals("200.000 đ", CatalogFormatting.formatPrice(200_000L, AppLocale.VI))
        assertEquals("200,000 VND", CatalogFormatting.formatPrice(200_000L, AppLocale.EN))
        assertEquals("1.250.000 đ", CatalogFormatting.formatPrice(1_250_000L, AppLocale.VI))
        assertEquals("1,250,000 VND", CatalogFormatting.formatPrice(1_250_000L, AppLocale.EN))
    }

    @Test
    fun calculatesDiscountPercentWithRoundingAndEdgeCases() {
        assertEquals(20, CatalogFormatting.discountPercent(250_000L, 200_000L))
        assertEquals(1, CatalogFormatting.discountPercent(100_000L, 99_900L))
        assertEquals(100, CatalogFormatting.discountPercent(100_000L, 0L))
        assertNull(CatalogFormatting.discountPercent(200_000L, 200_000L))
        assertNull(CatalogFormatting.discountPercent(200_000L, 250_000L))
        assertNull(CatalogFormatting.discountPercent(0L, 0L))
    }

    @Test
    fun formatsPromotionEndDateAlwaysInAsiaHoChiMinhUnderUtcAndNewYorkTimeZones() {
        val originalTz = TimeZone.getDefault()
        try {
            listOf("UTC", "America/New_York").forEach { tzId ->
                TimeZone.setDefault(TimeZone.getTimeZone(tzId))
                assertEquals(
                    "Unexpected date under default TZ=$tzId",
                    "21/11",
                    CatalogFormatting.formatPromoDayMonth("2026-11-20T18:30:00Z"),
                )
                assertEquals(
                    "Unexpected boundary date under default TZ=$tzId",
                    "20/11",
                    CatalogFormatting.formatPromoDayMonth("2026-11-20T16:59:59Z"),
                )
                assertEquals(
                    "05/03",
                    CatalogFormatting.formatPromoDayMonth("2026-03-05T00:05:00+07:00"),
                )
            }
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun formatsIntAndDecimalRangesAndMergesAnswerTypes() {
        assertEquals("50", CatalogFormatting.formatIntRange(IntRangeValue(50, 50), AppLocale.VI))
        assertEquals("48–50", CatalogFormatting.formatIntRange(IntRangeValue(48, 50), AppLocale.VI))
        assertEquals(
            "100",
            CatalogFormatting.formatDecimalRange(DecimalRangeValue(BigDecimal("100.00"), BigDecimal("100")), AppLocale.VI),
        )
        assertEquals(
            "95,5–100",
            CatalogFormatting.formatDecimalRange(DecimalRangeValue(BigDecimal("95.5"), BigDecimal("100")), AppLocale.VI),
        )
        assertEquals(
            "95.5–100",
            CatalogFormatting.formatDecimalRange(DecimalRangeValue(BigDecimal("95.5"), BigDecimal("100")), AppLocale.EN),
        )

        val merged = CatalogFormatting.mergeAnswerTypes(
            listOf(
                AnswerTypeGroupData("SINGLE_CHOICE", null, 30),
                AnswerTypeGroupData("QUESTION_SET", "SINGLE_CHOICE", 12),
                AnswerTypeGroupData("QUESTION_SET", null, 5),
            ),
        )
        assertEquals(
            listOf(
                MergedAnswerType("SINGLE_CHOICE", 42),
                MergedAnswerType("QUESTION_SET", 5),
            ),
            merged,
        )
    }
}
