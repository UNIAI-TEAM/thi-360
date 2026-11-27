package vn.unicom.thi360.feature.history

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.network.generated.model.AttemptChoiceSummary
import vn.unicom.thi360.core.network.generated.model.AttemptHistoryItem
import vn.unicom.thi360.core.preferences.AppLocale

class HistoryRowFormatTest {

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun formatsStartedAtInHoChiMinhRegardlessOfDeviceTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        val formatted = HistoryRowFormat.formatStartedAt(OffsetDateTime.parse("2026-11-20T02:05:00Z"))
        assertEquals("09:05, 20/11/2026", formatted)
    }

    @Test
    fun resolvesStatusBadgesIncludingRegradingAndGradingError() {
        assertEquals(
            HistoryStatusBadge(HistoryStatusKey.REGRADING, HistoryStatusTone.WARNING),
            HistoryRowFormat.resolveStatus(AttemptHistoryItem.State.SCORED, regrading = true),
        )
        assertEquals(
            HistoryStatusBadge(HistoryStatusKey.SCORED, HistoryStatusTone.SUCCESS),
            HistoryRowFormat.resolveStatus(AttemptHistoryItem.State.SCORED, regrading = false),
        )
        assertEquals(
            HistoryStatusBadge(HistoryStatusKey.GRADING_ERROR, HistoryStatusTone.DANGER),
            HistoryRowFormat.resolveStatus(AttemptHistoryItem.State.GRADING_ERROR, regrading = false),
        )
        assertEquals(
            HistoryStatusBadge(HistoryStatusKey.GRADING_ERROR, HistoryStatusTone.DANGER),
            HistoryRowFormat.resolveStatus(AttemptHistoryItem.State.ABORTED, regrading = false),
        )
        assertEquals(
            HistoryStatusBadge(HistoryStatusKey.IN_PROGRESS, HistoryStatusTone.INFO),
            HistoryRowFormat.resolveStatus(AttemptHistoryItem.State.STARTING, regrading = false),
        )
    }

    @Test
    fun compensatedTakesPrecedenceOverSystemFault() {
        assertEquals(
            HistoryFaultBanner.COMPENSATED,
            HistoryRowFormat.resolveFaultBanner(compensated = true, faultFlag = "SYSTEM_FAULT"),
        )
        assertEquals(
            HistoryFaultBanner.SYSTEM_FAULT,
            HistoryRowFormat.resolveFaultBanner(compensated = false, faultFlag = "SYSTEM_FAULT"),
        )
        assertNull(HistoryRowFormat.resolveFaultBanner(compensated = false, faultFlag = null))
    }

    @Test
    fun formatsScoresByLocaleHandlesZeroAndHidesUnscoredStates() {
        val scored725 = HistoryTestFixtures.makeAttempt(
            idSeed = "s1",
            state = AttemptHistoryItem.State.SCORED,
            displayScore = BigDecimal("7.25"),
            scaleMax = BigDecimal("10"),
        )
        assertEquals("7,25/10", HistoryRowFormat.formatScore(scored725, AppLocale.VI))
        assertEquals("7.25/10", HistoryRowFormat.formatScore(scored725, AppLocale.EN))

        val zeroRaw = HistoryTestFixtures.makeAttempt(
            idSeed = "s2",
            state = AttemptHistoryItem.State.SCORED,
            displayScore = null,
            rawScore = BigDecimal.ZERO,
            scaleMax = null,
            maxScore = BigDecimal("40"),
        )
        assertEquals("0/40", HistoryRowFormat.formatScore(zeroRaw, AppLocale.VI))

        val pendingNoRegrade = HistoryTestFixtures.makeAttempt(
            idSeed = "s3",
            state = AttemptHistoryItem.State.PENDING_REVIEW,
            regrading = false,
            displayScore = BigDecimal("5"),
        )
        assertNull(HistoryRowFormat.formatScore(pendingNoRegrade, AppLocale.VI))

        val pendingRegrading = pendingNoRegrade.copy(regrading = true)
        assertEquals("5/100", HistoryRowFormat.formatScore(pendingRegrading, AppLocale.VI))

        val inProgress = HistoryTestFixtures.makeAttempt(
            idSeed = "s4",
            state = AttemptHistoryItem.State.IN_PROGRESS,
            displayScore = BigDecimal("0"),
        )
        assertNull(HistoryRowFormat.formatScore(inProgress, AppLocale.VI))

        val rounded = HistoryTestFixtures.makeAttempt(
            idSeed = "s5",
            state = AttemptHistoryItem.State.SCORED,
            displayScore = BigDecimal("8.333333"),
            scaleMax = null,
            maxScore = null,
        )
        assertEquals("8,33", HistoryRowFormat.formatScore(rounded, AppLocale.VI))
        assertEquals("8.33", HistoryRowFormat.formatScore(rounded, AppLocale.EN))
    }

    @Test
    fun formatsChoicesAndVactPracticeNoteAndScopeDetail() {
        val subjects = mapOf(
            "LY" to CatalogOptionItem(code = "LY", nameVi = "Vật lý", nameEn = "Physics"),
            "HOA" to CatalogOptionItem(code = "HOA", nameVi = "Hóa học", nameEn = "Chemistry"),
            "TOAN" to CatalogOptionItem(code = "TOAN", nameVi = "Toán", nameEn = "Mathematics"),
        )
        val choices = mapOf(
            "p2" to AttemptChoiceSummary(optionCode = "KH", subjectCodes = listOf("LY", "HOA")),
        )

        val formatted = HistoryRowFormat.formatChoices(choices, subjects, AppLocale.VI)
        assertEquals(1, formatted.size)
        assertEquals(FormattedChoiceOption.Known(KnownChoiceOptionCode.KH), formatted[0].option)
        assertEquals(listOf("Vật lý", "Hóa học"), formatted[0].subjectNames)

        assertTrue(HistoryRowFormat.formatChoices(emptyMap(), subjects, AppLocale.VI).isEmpty())

        val vactScored = HistoryTestFixtures.makeAttempt(
            idSeed = "vact",
            examKindCode = "VACT",
            state = AttemptHistoryItem.State.SCORED,
            displayScore = BigDecimal("900"),
        )
        val vactInProgress = vactScored.copy(state = AttemptHistoryItem.State.IN_PROGRESS)
        assertTrue(HistoryRowFormat.showVactPracticeNote(vactScored))
        assertFalse(HistoryRowFormat.showVactPracticeNote(vactInProgress))

        val partAttempt = HistoryTestFixtures.makeAttempt(idSeed = "p", scope = "PART", partCode = "P1")
        assertEquals(
            FormattedScopeDetail.Part("P1"),
            HistoryRowFormat.resolveScopeDetail(partAttempt, subjects, AppLocale.VI),
        )

        val subjectAttempt = HistoryTestFixtures.makeAttempt(idSeed = "sub", scope = "SUBJECT", subjectCode = "TOAN")
        assertEquals(
            FormattedScopeDetail.Subject("Toán"),
            HistoryRowFormat.resolveScopeDetail(subjectAttempt, subjects, AppLocale.VI),
        )
        assertEquals(
            FormattedScopeDetail.Subject("TOAN"),
            HistoryRowFormat.resolveScopeDetail(subjectAttempt, emptyMap(), AppLocale.VI),
        )
    }

    @Test
    fun pickCatalogNameFallsBackAcrossLocaleAndCode() {
        val viOnly = CatalogOptionItem(code = "HSA", nameVi = "Đánh giá năng lực", nameEn = "")
        val bothBlank = CatalogOptionItem(code = "HSA", nameVi = " ", nameEn = "")
        assertEquals("Đánh giá năng lực", HistoryRowFormat.pickCatalogName(viOnly, AppLocale.EN))
        assertEquals("HSA", HistoryRowFormat.pickCatalogName(bothBlank, AppLocale.EN))
    }
}
