package vn.unicom.thi360.feature.prepare

import java.math.BigDecimal
import java.time.OffsetDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.ChoiceOptionView
import vn.unicom.thi360.core.network.generated.model.PreparePartView
import vn.unicom.thi360.core.preferences.AppLocale

class PrepareMetricsTest {

    @Test
    fun fallsBackToPartsTotalsAndPrefersTopLevelDuration() {
        val base = PrepareTestFixtures.loadPrepare("per-part").copy(
            durationMinutes = 195,
            parts = listOf(
                PreparePartView(
                    partCode = "P1",
                    ord = 0,
                    name = "Toán",
                    required = true,
                    durationMinutes = 75,
                    questionCount = 50,
                    maxPoints = BigDecimal("50"),
                ),
                PreparePartView(
                    partCode = "P2",
                    ord = 1,
                    name = "Văn",
                    required = true,
                    durationMinutes = 60,
                    questionCount = 50,
                    maxPoints = BigDecimal("50"),
                ),
                PreparePartView(
                    partCode = "P3",
                    ord = 2,
                    name = "Khoa học",
                    required = false,
                    durationMinutes = 60,
                    questionCount = 50,
                    maxPoints = BigDecimal("50"),
                ),
            ),
        )

        val metrics = PrepareMetrics.of(base, PreChoiceSelection())
        assertEquals(3, metrics.partCount)
        assertEquals(150, metrics.questionCount)
        assertEquals(195, metrics.durationMinutes)
        assertEquals(BigDecimal("150"), metrics.maxPoints)
    }

    @Test
    fun sumsPartDurationsWhenTopLevelDurationIsNull() {
        val base = PrepareTestFixtures.loadPrepare("per-part").copy(
            durationMinutes = null,
            parts = listOf(
                PreparePartView(partCode = "P1", ord = 0, name = "A", durationMinutes = 30, questionCount = 10, maxPoints = BigDecimal("2.5")),
                PreparePartView(partCode = "P2", ord = 1, name = "B", durationMinutes = 45, questionCount = 15, maxPoints = BigDecimal("7.5")),
            ),
        )

        val metrics = PrepareMetrics.of(base)
        assertEquals(2, metrics.partCount)
        assertEquals(25, metrics.questionCount)
        assertEquals(75, metrics.durationMinutes)
        assertEquals(BigDecimal("10"), metrics.maxPoints)
    }

    @Test
    fun returnsNullMetricsWhenPartsAndTopLevelHaveNoNumbers() {
        val base = PrepareTestFixtures.loadPrepare("per-part").copy(
            durationMinutes = null,
            parts = listOf(
                PreparePartView(partCode = "P1", ord = 0, name = "A", durationMinutes = null, questionCount = null, maxPoints = null),
            ),
        )

        val metrics = PrepareMetrics.of(base)
        assertEquals(1, metrics.partCount)
        assertNull(metrics.questionCount)
        assertNull(metrics.durationMinutes)
        assertNull(metrics.maxPoints)
    }

    @Test
    fun overridesWithSelectedPreChoiceOptionMetricsAndPreservesFallbackForNullFields() {
        val response = PrepareTestFixtures.loadPrepare("pre-choice")
        val withTwoOptions = response.copy(
            durationMinutes = 60,
            preChoice = response.preChoice!!.copy(
                options = listOf(
                    ChoiceOptionView(
                        code = "OPT_FULL",
                        name = "Đủ số",
                        kind = "BRANCH",
                        questionCount = 40,
                        durationMinutes = 90,
                        maxPoints = BigDecimal("10.250"),
                    ),
                    ChoiceOptionView(
                        code = "OPT_PARTIAL",
                        name = "Thiếu thời gian",
                        kind = "BRANCH",
                        questionCount = 20,
                        durationMinutes = null,
                        maxPoints = null,
                    ),
                ),
            ),
        )

        val fullMetrics = PrepareMetrics.of(withTwoOptions, PreChoiceSelection(optionCode = "OPT_FULL"))
        assertEquals(2, fullMetrics.partCount)
        assertEquals(40, fullMetrics.questionCount)
        assertEquals(90, fullMetrics.durationMinutes)
        assertEquals(BigDecimal("10.25"), fullMetrics.maxPoints)

        val partialMetrics = PrepareMetrics.of(withTwoOptions, PreChoiceSelection(optionCode = "OPT_PARTIAL"))
        assertEquals(20, partialMetrics.questionCount)
        assertEquals(60, partialMetrics.durationMinutes)
        assertEquals(BigDecimal("2.25"), partialMetrics.maxPoints)
    }

    @Test
    fun roundsDecimalPointsToThreePlacesWithoutTrailingZeros() {
        val base = PrepareTestFixtures.loadPrepare("per-part").copy(
            parts = listOf(
                PreparePartView(partCode = "P1", ord = 0, name = "A", maxPoints = BigDecimal("0.1")),
                PreparePartView(partCode = "P2", ord = 1, name = "B", maxPoints = BigDecimal("0.2")),
                PreparePartView(partCode = "P3", ord = 2, name = "C", maxPoints = BigDecimal("0.3334")),
            ),
        )

        val metrics = PrepareMetrics.of(base)
        assertEquals(BigDecimal("0.633"), metrics.maxPoints)
        assertEquals("0,633", formatPreparePoints(metrics.maxPoints!!, AppLocale.VI))
        assertEquals("0.633", formatPreparePoints(metrics.maxPoints!!, AppLocale.EN))
        assertEquals("10", formatPreparePoints(BigDecimal("10.000"), AppLocale.VI))
    }

    @Test
    fun formatsDayMonthInHoChiMinhTimezone() {
        val utcLateEvening = OffsetDateTime.parse("2026-11-20T18:30:00Z")
        assertEquals("21/11", formatPrepareDayMonth(utcLateEvening))
    }
}
