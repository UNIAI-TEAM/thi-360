package vn.unicom.thi360.feature.prepare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.ShellWidth
import vn.unicom.thi360.feature.prepare.waiting.AttemptWaitingContent
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PrepareScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val supportContact = SupportContact(
        phone = "1900 1234",
        email = "hotro@thi360.vn",
        hours = "08:00-21:00",
    )

    private fun loadedState(
        response: vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse,
        selection: PreChoiceSelection = PreChoiceSelection.initial(response.preChoice),
    ): PrepareScreenState = PrepareScreenState(
        load = LoadState.Loaded(response),
        selection = selection,
        gate = resolveStartGate(response, selection),
        start = StartState.Idle,
    )

    private fun capturePrepare(
        name: String,
        themeMode: ThemeMode,
        shellWidth: ShellWidth,
        state: PrepareScreenState,
        fontScale: Float = 1.0f,
    ) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
                LocalShellWidth provides shellWidth,
                LocalFormulaEngine provides SourceBoxFormulaEngine(),
            ) {
                Thi360Theme(
                    preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Thi360Theme.colors.bgPage),
                    ) {
                        PrepareScreen(
                            state = state,
                            supportContact = supportContact,
                            onBack = {},
                            onMyExams = {},
                            onRetryLoad = {},
                            onSelectOption = {},
                            onToggleSubject = {},
                            onStartAttempt = {},
                            onContinueAttempt = {},
                            onBuyMoreAttempts = {},
                            onRetryWaiting = {},
                            onLeaveWaiting = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun captureWaiting(
        name: String,
        themeMode: ThemeMode,
        phase: WaitingPhase,
        isSharedTiming: Boolean = false,
        fontScale: Float = 1.0f,
    ) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
            ) {
                Thi360Theme(
                    preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode),
                ) {
                    AttemptWaitingContent(
                        examTitle = "HSA đầy đủ (thu nhỏ)",
                        phase = phase,
                        abortReason = (phase as? WaitingPhase.Failed)?.abortReason,
                        firstPartName = "Toán học và xử lí số liệu",
                        firstPartMinutes = if (isSharedTiming) 90 else 5,
                        isSharedTiming = isSharedTiming,
                        supportContact = supportContact,
                        onBack = {},
                        onMyExams = {},
                        onRetry = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun prepare_free_360x640_light() = capturePrepare(
        name = "prepare_free_360x640_light",
        themeMode = ThemeMode.LIGHT,
        shellWidth = ShellWidth.Narrow,
        state = loadedState(PrepareTestFixtures.loadPrepare("free-navigation")),
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun prepare_free_360x640_dark() = capturePrepare(
        name = "prepare_free_360x640_dark",
        themeMode = ThemeMode.DARK,
        shellWidth = ShellWidth.Narrow,
        state = loadedState(PrepareTestFixtures.loadPrepare("free-navigation")),
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun prepare_free_360x640_fs130_light() = capturePrepare(
        name = "prepare_free_360x640_fs130_light",
        themeMode = ThemeMode.LIGHT,
        shellWidth = ShellWidth.Narrow,
        state = loadedState(PrepareTestFixtures.loadPrepare("free-navigation")),
        fontScale = 1.3f,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_per_part_412x915_light() = capturePrepare(
        name = "prepare_per_part_412x915_light",
        themeMode = ThemeMode.LIGHT,
        shellWidth = ShellWidth.Narrow,
        state = loadedState(PrepareTestFixtures.loadPrepare("per-part")),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_per_part_412x915_dark() = capturePrepare(
        name = "prepare_per_part_412x915_dark",
        themeMode = ThemeMode.DARK,
        shellWidth = ShellWidth.Narrow,
        state = loadedState(PrepareTestFixtures.loadPrepare("per-part")),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_shared_412x915_light() = capturePrepare(
        name = "prepare_shared_412x915_light",
        themeMode = ThemeMode.LIGHT,
        shellWidth = ShellWidth.Narrow,
        state = loadedState(PrepareTestFixtures.loadPrepare("shared")),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_shared_412x915_dark() = capturePrepare(
        name = "prepare_shared_412x915_dark",
        themeMode = ThemeMode.DARK,
        shellWidth = ShellWidth.Narrow,
        state = loadedState(PrepareTestFixtures.loadPrepare("shared")),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_prechoice_412x915_light() {
        val resp = PrepareTestFixtures.prepareWithSubjectChoice(preselectedCode = "SCIENCE")
        val sel = PreChoiceSelection(optionCode = "SCIENCE", subjectCodes = linkedSetOf("LY", "HOA"))
        capturePrepare(
            name = "prepare_prechoice_412x915_light",
            themeMode = ThemeMode.LIGHT,
            shellWidth = ShellWidth.Narrow,
            state = loadedState(resp, sel),
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_prechoice_412x915_dark() {
        val resp = PrepareTestFixtures.prepareWithSubjectChoice(preselectedCode = "SCIENCE")
        val sel = PreChoiceSelection(optionCode = "SCIENCE", subjectCodes = linkedSetOf("LY", "HOA"))
        capturePrepare(
            name = "prepare_prechoice_412x915_dark",
            themeMode = ThemeMode.DARK,
            shellWidth = ShellWidth.Narrow,
            state = loadedState(resp, sel),
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_exhausted_412x915_light() = capturePrepare(
        name = "prepare_exhausted_412x915_light",
        themeMode = ThemeMode.LIGHT,
        shellWidth = ShellWidth.Narrow,
        state = loadedState(PrepareTestFixtures.loadPrepare("exhausted-purchase")),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_exhausted_412x915_dark() = capturePrepare(
        name = "prepare_exhausted_412x915_dark",
        themeMode = ThemeMode.DARK,
        shellWidth = ShellWidth.Narrow,
        state = loadedState(PrepareTestFixtures.loadPrepare("exhausted-purchase")),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_open_in_progress_412x915_light() = capturePrepare(
        name = "prepare_open_in_progress_412x915_light",
        themeMode = ThemeMode.LIGHT,
        shellWidth = ShellWidth.Narrow,
        state = loadedState(PrepareTestFixtures.loadPrepare("open-in-progress")),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_error_412x915_light() = capturePrepare(
        name = "prepare_error_412x915_light",
        themeMode = ThemeMode.LIGHT,
        shellWidth = ShellWidth.Narrow,
        state = PrepareScreenState(load = LoadState.Failed(LoadFailureKind.Server, canRetry = true)),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_error_412x915_dark() = capturePrepare(
        name = "prepare_error_412x915_dark",
        themeMode = ThemeMode.DARK,
        shellWidth = ShellWidth.Narrow,
        state = PrepareScreenState(load = LoadState.Failed(LoadFailureKind.Server, canRetry = true)),
    )

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun prepare_per_part_840x720_light() = capturePrepare(
        name = "prepare_per_part_840x720_light",
        themeMode = ThemeMode.LIGHT,
        shellWidth = ShellWidth.TwoColumn,
        state = loadedState(PrepareTestFixtures.loadPrepare("per-part")),
    )

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun prepare_per_part_840x720_dark() = capturePrepare(
        name = "prepare_per_part_840x720_dark",
        themeMode = ThemeMode.DARK,
        shellWidth = ShellWidth.TwoColumn,
        state = loadedState(PrepareTestFixtures.loadPrepare("per-part")),
    )

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun prepare_prechoice_1280x800_light() {
        val resp = PrepareTestFixtures.prepareWithSubjectChoice(preselectedCode = "SCIENCE")
        val sel = PreChoiceSelection(optionCode = "SCIENCE", subjectCodes = linkedSetOf("LY", "HOA", "SINH"))
        capturePrepare(
            name = "prepare_prechoice_1280x800_light",
            themeMode = ThemeMode.LIGHT,
            shellWidth = ShellWidth.TwoColumn,
            state = loadedState(resp, sel),
        )
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun prepare_prechoice_1280x800_dark() {
        val resp = PrepareTestFixtures.prepareWithSubjectChoice(preselectedCode = "SCIENCE")
        val sel = PreChoiceSelection(optionCode = "SCIENCE", subjectCodes = linkedSetOf("LY", "HOA", "SINH"))
        capturePrepare(
            name = "prepare_prechoice_1280x800_dark",
            themeMode = ThemeMode.DARK,
            shellWidth = ShellWidth.TwoColumn,
            state = loadedState(resp, sel),
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun prepare_waiting_polling_360x640_light() = captureWaiting(
        name = "prepare_waiting_polling_360x640_light",
        themeMode = ThemeMode.LIGHT,
        phase = WaitingPhase.Polling(2_000L),
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun prepare_waiting_polling_360x640_fs130_light() = captureWaiting(
        name = "prepare_waiting_polling_360x640_fs130_light",
        themeMode = ThemeMode.LIGHT,
        phase = WaitingPhase.Polling(2_000L),
        fontScale = 1.3f,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_waiting_polling_412x915_light() = captureWaiting(
        name = "prepare_waiting_polling_412x915_light",
        themeMode = ThemeMode.LIGHT,
        phase = WaitingPhase.Polling(2_000L),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_waiting_polling_412x915_dark() = captureWaiting(
        name = "prepare_waiting_polling_412x915_dark",
        themeMode = ThemeMode.DARK,
        phase = WaitingPhase.Polling(2_000L),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_waiting_busy_412x915_light() = captureWaiting(
        name = "prepare_waiting_busy_412x915_light",
        themeMode = ThemeMode.LIGHT,
        phase = WaitingPhase.Busy,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_waiting_busy_412x915_dark() = captureWaiting(
        name = "prepare_waiting_busy_412x915_dark",
        themeMode = ThemeMode.DARK,
        phase = WaitingPhase.Busy,
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_waiting_failed_412x915_light() = captureWaiting(
        name = "prepare_waiting_failed_412x915_light",
        themeMode = ThemeMode.LIGHT,
        phase = WaitingPhase.Failed("STALE_STARTING"),
    )

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun prepare_waiting_failed_412x915_dark() = captureWaiting(
        name = "prepare_waiting_failed_412x915_dark",
        themeMode = ThemeMode.DARK,
        phase = WaitingPhase.Failed("STALE_STARTING"),
    )

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun prepare_waiting_busy_840x720_light() = captureWaiting(
        name = "prepare_waiting_busy_840x720_light",
        themeMode = ThemeMode.LIGHT,
        phase = WaitingPhase.Busy,
        isSharedTiming = true,
    )

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun prepare_waiting_failed_1280x800_dark() = captureWaiting(
        name = "prepare_waiting_failed_1280x800_dark",
        themeMode = ThemeMode.DARK,
        phase = WaitingPhase.Failed("GENERATION_TIMEOUT"),
    )
}
