package vn.unicom.thi360.feature.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
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
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.ShellWidth

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ResultScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val examCatalog by lazy { ResultTestFixtures.loadCatalogOptions("exam") }
    private val subjectCatalog by lazy { ResultTestFixtures.loadCatalogOptions("subject") }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        shellWidth: ShellWidth,
        uiState: ResultUiState,
    ) {
        composeRule.setContent {
            Thi360Theme(
                preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode),
            ) {
                CompositionLocalProvider(LocalShellWidth provides shellWidth) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Thi360Theme.colors.bgPage),
                    ) {
                        ResultScreen(
                            uiState = uiState,
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
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun loadedState(
        result: AttemptResultResponse,
        pollPhase: PollPhase = PollPhase.Idle,
    ) = ResultUiState(
        attemptId = ResultTestFixtures.ATTEMPT_UUID_1,
        loadState = LoadState.Loaded(result),
        pollPhase = pollPhase,
        examKinds = examCatalog,
        subjects = subjectCatalog,
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun result_scored_360x640_light() =
        capture("result_scored_360x640_light", ThemeMode.LIGHT, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("scored")))

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun result_scored_360x640_dark() =
        capture("result_scored_360x640_dark", ThemeMode.DARK, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("scored")))

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun result_scored_412x915_light() =
        capture("result_scored_412x915_light", ThemeMode.LIGHT, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("scored")))

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun result_scored_412x915_dark() =
        capture("result_scored_412x915_dark", ThemeMode.DARK, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("scored")))

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun result_vact_412x915_light() =
        capture("result_vact_412x915_light", ThemeMode.LIGHT, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("vact")))

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun result_vact_412x915_dark() =
        capture("result_vact_412x915_dark", ThemeMode.DARK, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("vact")))

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun result_pending_review_412x915_light() =
        capture("result_pending_review_412x915_light", ThemeMode.LIGHT, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("pending-review")))

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun result_regrading_412x915_light() =
        capture("result_regrading_412x915_light", ThemeMode.LIGHT, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("regrading")))

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun result_grading_412x915_light() =
        capture("result_grading_412x915_light", ThemeMode.LIGHT, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("grading")))

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun result_grading_timeout_412x915_light() =
        capture(
            "result_grading_timeout_412x915_light",
            ThemeMode.LIGHT,
            ShellWidth.Narrow,
            loadedState(ResultTestFixtures.loadResult("grading"), pollPhase = PollPhase.TimedOut),
        )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun result_grading_error_412x915_light() =
        capture("result_grading_error_412x915_light", ThemeMode.LIGHT, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("grading-error")))

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun result_fault_compensated_412x915_light() =
        capture("result_fault_compensated_412x915_light", ThemeMode.LIGHT, ShellWidth.Narrow, loadedState(ResultTestFixtures.loadResult("compensated")))

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun result_scored_840x720_light() =
        capture("result_scored_840x720_light", ThemeMode.LIGHT, ShellWidth.TwoColumn, loadedState(ResultTestFixtures.loadResult("scored")))

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun result_scored_840x720_dark() =
        capture("result_scored_840x720_dark", ThemeMode.DARK, ShellWidth.TwoColumn, loadedState(ResultTestFixtures.loadResult("scored")))

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun result_vact_1280x800_light() =
        capture("result_vact_1280x800_light", ThemeMode.LIGHT, ShellWidth.TwoColumn, loadedState(ResultTestFixtures.loadResult("vact")))

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun result_vact_1280x800_dark() =
        capture("result_vact_1280x800_dark", ThemeMode.DARK, ShellWidth.TwoColumn, loadedState(ResultTestFixtures.loadResult("vact")))
}
