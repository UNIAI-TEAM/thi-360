package vn.unicom.thi360.feature.result.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.result.ResultTestFixtures
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        model: ReviewModel,
        headerResult: AttemptResultResponse?,
        selectedPartIndex: Int = 0,
    ) {
        composeRule.setContent {
            Thi360Theme(
                preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Thi360Theme.colors.bgPage),
                ) {
                    ReviewScreen(
                        uiState = ReviewUiState(
                            attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
                            loadState = LoadState.Loaded(model),
                            headerResult = headerResult,
                            selectedPartIndex = selectedPartIndex,
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
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun fullSolutionModel() = ReviewMapper.map(ResultTestFixtures.loadReview("full-solution"))
    private fun scoreAndAnswersModel() = ReviewMapper.map(ResultTestFixtures.loadReview("score-and-answers"))
    private fun pendingReviewModel() = ReviewMapper.map(ResultTestFixtures.loadReview("pending-review"))
    private fun scoredHeader() = ResultTestFixtures.loadResult("scored")
    private fun pendingHeader() = ResultTestFixtures.loadResult("pending-review")

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun review_full_360x640_light() =
        capture("review_full_360x640_light", ThemeMode.LIGHT, fullSolutionModel(), scoredHeader())

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun review_full_360x640_dark() =
        capture("review_full_360x640_dark", ThemeMode.DARK, fullSolutionModel(), scoredHeader())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun review_full_412x915_light() =
        capture("review_full_412x915_light", ThemeMode.LIGHT, fullSolutionModel(), scoredHeader())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun review_full_412x915_dark() =
        capture("review_full_412x915_dark", ThemeMode.DARK, fullSolutionModel(), scoredHeader())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun review_score_and_answers_412x915_light() =
        capture("review_score_and_answers_412x915_light", ThemeMode.LIGHT, scoreAndAnswersModel(), scoredHeader())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun review_pending_412x915_light() =
        capture("review_pending_412x915_light", ThemeMode.LIGHT, pendingReviewModel(), pendingHeader(), selectedPartIndex = 1)

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun review_full_840x720_light() =
        capture("review_full_840x720_light", ThemeMode.LIGHT, fullSolutionModel(), scoredHeader())

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun review_full_840x720_dark() =
        capture("review_full_840x720_dark", ThemeMode.DARK, fullSolutionModel(), scoredHeader())

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun review_full_1280x800_light() =
        capture("review_full_1280x800_light", ThemeMode.LIGHT, fullSolutionModel(), scoredHeader())

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun review_full_1280x800_dark() =
        capture("review_full_1280x800_dark", ThemeMode.DARK, fullSolutionModel(), scoredHeader())
}
