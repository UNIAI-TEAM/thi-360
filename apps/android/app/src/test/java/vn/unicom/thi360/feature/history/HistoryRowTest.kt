package vn.unicom.thi360.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.network.generated.model.AttemptChoiceSummary
import vn.unicom.thi360.core.network.generated.model.AttemptHistoryItem
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h915dp")
class HistoryRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun rendersAllRowVariantsWithLocalizedScoreChoicesFaultAndTimeZone() {
        val subjects = mapOf(
            "LY" to CatalogOptionItem("LY", "Vật lý", "Physics"),
            "HOA" to CatalogOptionItem("HOA", "Hóa học", "Chemistry"),
        )
        val partScored = HistoryTestFixtures.makeAttempt(
            idSeed = "r1",
            examName = "Đề phần P1",
            scope = "PART",
            partCode = "P1",
            state = AttemptHistoryItem.State.SCORED,
            attemptNo = 2,
            startedAt = OffsetDateTime.parse("2026-11-20T02:05:00Z"),
            displayScore = BigDecimal("7.25"),
            scaleMax = BigDecimal("10"),
            choices = mapOf("p2" to AttemptChoiceSummary("KH", listOf("LY", "HOA"))),
        )
        val regradingRow = HistoryTestFixtures.makeAttempt(
            idSeed = "r2",
            examName = "Đề chấm lại VACT",
            examKindCode = "VACT",
            state = AttemptHistoryItem.State.SCORED,
            regrading = true,
            displayScore = null,
            rawScore = BigDecimal.ZERO,
            scaleMax = null,
            maxScore = BigDecimal("40"),
            compensated = true,
            faultFlag = "SYSTEM_FAULT",
        )
        val gradingErrorRow = HistoryTestFixtures.makeAttempt(
            idSeed = "r3",
            examName = "Đề lỗi chấm",
            state = AttemptHistoryItem.State.GRADING_ERROR,
            displayScore = null,
            rawScore = null,
        )
        val pendingRow = HistoryTestFixtures.makeAttempt(
            idSeed = "r4",
            examName = "Đề chờ chấm",
            state = AttemptHistoryItem.State.PENDING_REVIEW,
            regrading = false,
            displayScore = BigDecimal("8.333333"),
            scaleMax = null,
            maxScore = null,
        )
        val roundedRow = HistoryTestFixtures.makeAttempt(
            idSeed = "r5",
            examName = "Đề làm tròn",
            state = AttemptHistoryItem.State.SCORED,
            displayScore = BigDecimal("8.333333"),
            scaleMax = null,
            maxScore = null,
        )

        val rows = listOf(partScored, regradingRow, gradingErrorRow, pendingRow, roundedRow)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                HistoryScreen(
                    feedState = HistoryFeedState(
                        loadState = LoadState.Loaded(HistoryPage(rows, 0, rows.size.toLong(), 1, false)),
                        subjectMap = subjects,
                    ),
                    showCompactBrandHeader = false,
                    onSearchInputChanged = {},
                    onSearchSubmit = {},
                    onSelectKind = {},
                    onSelectScope = {},
                    onSelectFromDate = {},
                    onSelectToDate = {},
                    onClearAllFilters = {},
                    onLoadNextPage = {},
                    onRetry = {},
                    onGoToMyExams = {},
                    onContinueAttempt = {},
                    onOpenResult = {},
                )
            }
        }

        val r1Id = partScored.attemptId.toString()
        composeRule.onNodeWithTag(HistoryTestTags.LIST).performScrollToNode(hasTestTag(HistoryTestTags.rowCard(r1Id)))
        composeRule.onNodeWithText("Bài thi theo phần · Phần P1").assertIsDisplayed()
        composeRule.onNodeWithText("Đã chấm").assertIsDisplayed()
        composeRule.onNodeWithTag(HistoryTestTags.rowAttemptStarted(r1Id)).assertTextEquals("Lần 2 · 09:05, 20/11/2026")
        composeRule.onNodeWithTag(HistoryTestTags.rowScore(r1Id)).assertTextEquals("7,25/10")
        composeRule.onNodeWithTag(HistoryTestTags.rowChoices(r1Id)).assertTextEquals("Khoa học: Vật lý, Hóa học")

        val r2Id = regradingRow.attemptId.toString()
        composeRule.onNodeWithTag(HistoryTestTags.LIST).performScrollToNode(hasTestTag(HistoryTestTags.rowCard(r2Id)))
        composeRule.onNodeWithText("Đang chấm lại").assertIsDisplayed()
        composeRule.onNodeWithText("Điểm trước khi chấm lại").assertIsDisplayed()
        composeRule.onNodeWithText("Điểm luyện tập").assertIsDisplayed()
        composeRule.onNodeWithTag(HistoryTestTags.rowScore(r2Id)).assertTextEquals("0/40")
        composeRule.onNodeWithTag(HistoryTestTags.rowChoices(r2Id)).assertTextEquals("—")
        composeRule.onNodeWithTag(HistoryTestTags.rowFault(r2Id))
            .assertTextEquals("Lượt bị ảnh hưởng bởi lỗi hệ thống, đã cấp bù 1 lượt")
        composeRule.onNodeWithText("Lượt kết thúc do lỗi hệ thống").assertDoesNotExist()

        val r3Id = gradingErrorRow.attemptId.toString()
        composeRule.onNodeWithTag(HistoryTestTags.LIST).performScrollToNode(hasTestTag(HistoryTestTags.rowCard(r3Id)))
        composeRule.onNodeWithText("Đang xử lý kết quả").assertIsDisplayed()

        val r4Id = pendingRow.attemptId.toString()
        composeRule.onNodeWithTag(HistoryTestTags.LIST).performScrollToNode(hasTestTag(HistoryTestTags.rowCard(r4Id)))
        composeRule.onNodeWithTag(HistoryTestTags.rowScore(r4Id)).assertTextEquals("Chưa có điểm")

        val r5Id = roundedRow.attemptId.toString()
        composeRule.onNodeWithTag(HistoryTestTags.LIST).performScrollToNode(hasTestTag(HistoryTestTags.rowCard(r5Id)))
        composeRule.onNodeWithTag(HistoryTestTags.rowScore(r5Id)).assertTextEquals("8,33")
    }

    @Test
    fun englishLocaleUsesDotDecimalSeparator() {
        val scored = HistoryTestFixtures.makeAttempt(
            idSeed = "en-1",
            state = AttemptHistoryItem.State.SCORED,
            displayScore = BigDecimal("7.25"),
            scaleMax = BigDecimal("10"),
        )
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.EN)) {
                HistoryScreen(
                    feedState = HistoryFeedState(
                        loadState = LoadState.Loaded(HistoryPage(listOf(scored), 0, 1, 1, false)),
                    ),
                    showCompactBrandHeader = false,
                    onSearchInputChanged = {},
                    onSearchSubmit = {},
                    onSelectKind = {},
                    onSelectScope = {},
                    onSelectFromDate = {},
                    onSelectToDate = {},
                    onClearAllFilters = {},
                    onLoadNextPage = {},
                    onRetry = {},
                    onGoToMyExams = {},
                    onContinueAttempt = {},
                    onOpenResult = {},
                )
            }
        }
        composeRule.onNodeWithTag(HistoryTestTags.rowScore(scored.attemptId.toString()))
            .assertTextEquals("7.25/10")
    }
}
