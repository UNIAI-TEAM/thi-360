package vn.unicom.thi360.feature.result

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.preferences.AppLocale

class ResultFormatTest {

    @Test
    fun dateTimeAlwaysUsesHoChiMinhTimezoneRegardlessOfDeviceZone() {
        val originalTz = TimeZone.getDefault()
        try {
            listOf("UTC", "America/New_York", "Asia/Tokyo").forEach { zoneId ->
                TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
                val started = OffsetDateTime.parse("2026-11-26T01:05:00Z")
                val finished = OffsetDateTime.parse("2026-11-26T02:20:30Z")
                assertEquals("08:05, 26/11/2026", ResultFormat.formatDateTime(started))
                assertEquals("09:20, 26/11/2026", ResultFormat.formatDateTime(finished))
                assertEquals("08:05, 26/11/2026", ResultFormat.formatDateTime("2026-11-26T01:05:00Z"))
                assertEquals(
                    DurationParts.HoursMinutes(hours = 1, minutes = 15),
                    ResultFormat.computeActualDuration(started, finished),
                )
            }
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }

    @Test
    fun durationCoversAllBucketsAndMissingTimestamps() {
        val t0 = OffsetDateTime.parse("2026-11-26T01:05:00Z")
        assertEquals(DurationParts.Zero, ResultFormat.computeActualDuration(t0, t0))
        assertEquals(
            DurationParts.Zero,
            ResultFormat.computeActualDuration(t0, t0.minusSeconds(10)),
        )
        assertEquals(
            DurationParts.Seconds(45),
            ResultFormat.computeActualDuration(t0, t0.plusSeconds(45)),
        )
        assertEquals(
            DurationParts.Minutes(12),
            ResultFormat.computeActualDuration(t0, t0.plusMinutes(12).plusSeconds(20)),
        )
        assertEquals(
            DurationParts.HoursMinutes(hours = 1, minutes = 15),
            ResultFormat.computeActualDuration(
                OffsetDateTime.parse("2026-11-26T01:05:00Z"),
                OffsetDateTime.parse("2026-11-26T02:20:30Z"),
            ),
        )
        assertNull(ResultFormat.computeActualDuration(t0, null))
        assertNull(ResultFormat.computeActualDuration(null, t0))
        assertNull(ResultFormat.formatDateTime(null as OffsetDateTime?))
    }

    @Test
    fun scoreFormatsPerLocaleWithUpToTwoDecimalsAndNoTrailingZeros() {
        assertEquals("7,5", ResultFormat.formatScore(BigDecimal("7.5"), AppLocale.VI))
        assertEquals("7.5", ResultFormat.formatScore(BigDecimal("7.5"), AppLocale.EN))

        assertEquals("7,5", ResultFormat.formatScore(BigDecimal("7.50"), AppLocale.VI))
        assertEquals("7.5", ResultFormat.formatScore(BigDecimal("7.50"), AppLocale.EN))

        assertEquals("10", ResultFormat.formatScore(BigDecimal("10.00"), AppLocale.VI))
        assertEquals("10", ResultFormat.formatScore(BigDecimal("10.00"), AppLocale.EN))

        assertEquals("3,46", ResultFormat.formatScore(BigDecimal("3.456"), AppLocale.VI))
        assertEquals("3.46", ResultFormat.formatScore(BigDecimal("3.456"), AppLocale.EN))

        assertEquals("1.200", ResultFormat.formatScore(BigDecimal("1200.0"), AppLocale.VI))
        assertEquals("1,200", ResultFormat.formatScore(BigDecimal("1200.0"), AppLocale.EN))

        assertEquals("0", ResultFormat.formatScore(BigDecimal("0.00"), AppLocale.VI))
        assertEquals("0", ResultFormat.formatScore(BigDecimal("-0.00"), AppLocale.EN))
    }

    @Test
    fun scoreFallbackUsesRawScoreAndRawMaxScoreAndHandlesZeroAndMissingMax() {
        val scoredZero = ResultTestFixtures.loadResult("zero-score")
        val resolvedZero = ResultFormat.resolveDisplayScore(scoredZero)
        assertNotNull(resolvedZero)
        assertEquals("0", ResultFormat.formatScore(resolvedZero!!.score, AppLocale.VI))
        assertEquals("5", ResultFormat.formatScore(resolvedZero.max!!, AppLocale.VI))

        // Plan A6 documented mutation: remove displayScore to verify rawScore fallback.
        val missingDisplay = ResultTestFixtures.loadResult("scored").copy(
            displayScore = null,
            rawScore = BigDecimal("7.50"),
        )
        val resolvedFromRaw = ResultFormat.resolveDisplayScore(missingDisplay)
        assertNotNull(resolvedFromRaw)
        assertEquals("7,5", ResultFormat.formatScore(resolvedFromRaw!!.score, AppLocale.VI))
        assertEquals("7.5", ResultFormat.formatScore(resolvedFromRaw.score, AppLocale.EN))

        // Plan A6 documented mutation per Orchestrator Addendum: remove scaleMax on certified scored fixture.
        val missingScaleMax = ResultTestFixtures.scoredWithoutScaleMax(removeRawMaxScore = false)
        val resolvedRawMax = ResultFormat.resolveDisplayScore(missingScaleMax)
        assertNotNull(resolvedRawMax)
        assertEquals("5", ResultFormat.formatScore(resolvedRawMax!!.max!!, AppLocale.VI))

        // Plan A6 documented mutation: remove both scaleMax and rawMaxScore -> max must be null (no "/...").
        val missingBothMax = ResultTestFixtures.scoredWithoutScaleMax(removeRawMaxScore = true)
        val resolvedNoMax = ResultFormat.resolveDisplayScore(missingBothMax)
        assertNotNull(resolvedNoMax)
        assertNull(resolvedNoMax!!.max)

        val pendingNonRegrading = ResultTestFixtures.loadResult("pending-review")
        assertNull(ResultFormat.resolveDisplayScore(pendingNonRegrading))

        val regrading = ResultTestFixtures.loadResult("regrading")
        val resolvedRegrading = ResultFormat.resolveDisplayScore(regrading)
        assertNotNull(resolvedRegrading)
        assertEquals("45,5", ResultFormat.formatScore(resolvedRegrading!!.score, AppLocale.VI))
        assertEquals("47,75", ResultFormat.formatScore(resolvedRegrading.max!!, AppLocale.VI))

        val grading = ResultTestFixtures.loadResult("grading")
        assertNull(ResultFormat.resolveDisplayScore(grading))
        assertEquals(AttemptResultResponse.State.GRADING, grading.state)
    }
}
