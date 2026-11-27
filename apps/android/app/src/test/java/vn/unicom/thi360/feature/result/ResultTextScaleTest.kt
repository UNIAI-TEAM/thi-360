package vn.unicom.thi360.feature.result

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.result.review.ReviewMapper
import vn.unicom.thi360.feature.result.review.ReviewScreen
import vn.unicom.thi360.feature.result.review.ReviewTestTags
import vn.unicom.thi360.feature.result.review.ReviewUiState
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ResultTextScaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val examCatalog by lazy { ResultTestFixtures.loadCatalogOptions("exam") }
    private val subjectCatalog by lazy { ResultTestFixtures.loadCatalogOptions("subject") }

    @Test
    fun resultScreenAtFontScale130And200HasNoHorizontalOverflowAnd48dpTouchTargets() {
        val result = ResultTestFixtures.loadResult("scored")
        var currentScale by mutableFloatStateOf(1.3f)

        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = currentScale)) {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    ResultScreen(
                        uiState = ResultUiState(
                            attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                            loadState = LoadState.Loaded(result),
                            examKinds = examCatalog,
                            subjects = subjectCatalog,
                        ),
                        onBack = {},
                        onRetry = {},
                        onReloadFromTimeout = {},
                        onOpenReview = {},
                        onContinueAttempt = {},
                        onGoToMyExams = {},
                        onGoToHistory = {},
                    )
                }
            }
        }

        listOf(1.3f, 2.0f).forEach { scale ->
            currentScale = scale
            composeRule.waitForIdle()

            val backNode = composeRule.onNodeWithTag(ResultTestTags.BACK_BUTTON)
            backNode.assertIsDisplayed()
            backNode.assertWidthIsAtLeast(48.dp)
            backNode.assertHeightIsAtLeast(48.dp)
            val backDesc = backNode.fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
            assertTrue("Back button must have TalkBack description", backDesc.isNotEmpty())

            composeRule.onNodeWithTag(ResultTestTags.CONTENT_LIST)
                .performScrollToNode(hasTestTag(ResultTestTags.HEADER))
            val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
            val headerBounds = composeRule.onNodeWithTag(ResultTestTags.HEADER).getUnclippedBoundsInRoot()
            assertTrue(
                "Header overflows width at fontScale=$scale: ${headerBounds.right} > ${rootBounds.right}",
                headerBounds.right <= rootBounds.right + 1.dp,
            )

            composeRule.onNodeWithTag(ResultTestTags.CONTENT_LIST)
                .performScrollToNode(hasTestTag(ResultTestTags.REVIEW_BUTTON))
            composeRule.onNodeWithTag(ResultTestTags.REVIEW_BUTTON).assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithTag(ResultTestTags.MY_EXAMS_BUTTON).assertHeightIsAtLeast(48.dp)
            composeRule.onNodeWithTag(ResultTestTags.HISTORY_BUTTON).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun reviewScreenAtFontScale130And200Keeps48dpControlsAndTalkBackLabels() {
        val model = ReviewMapper.map(ResultTestFixtures.loadReview("full-solution"))
        val header = ResultTestFixtures.loadResult("scored")
        var currentScale by mutableFloatStateOf(1.3f)

        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = currentScale)) {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    ReviewScreen(
                        uiState = ReviewUiState(
                            attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                            loadState = LoadState.Loaded(model),
                            headerResult = header,
                        ),
                        onBack = {},
                        onRetry = {},
                        onSelectPart = {},
                        onSelectGroup = {},
                        onPreviousGroup = { null },
                        onNextGroup = { null },
                        onGoToMyExams = {},
                        onGoToHistory = {},
                        formulaEngine = SourceBoxFormulaEngine(),
                    )
                }
            }
        }

        listOf(1.3f, 2.0f).forEach { scale ->
            currentScale = scale
            composeRule.waitForIdle()

            listOf(
                ReviewTestTags.BACK_BUTTON,
                ReviewTestTags.PREV_BUTTON,
                ReviewTestTags.NEXT_BUTTON,
                ReviewTestTags.paletteCell(0),
            ).forEach { tag ->
                val node = composeRule.onNodeWithTag(tag)
                node.assertIsDisplayed()
                node.assertWidthIsAtLeast(48.dp)
                node.assertHeightIsAtLeast(48.dp)
                val sem = node.fetchSemanticsNode().config
                assertTrue(
                    "Interactive control $tag must have TalkBack description",
                    sem.contains(SemanticsProperties.ContentDescription),
                )
            }
        }
    }
}
