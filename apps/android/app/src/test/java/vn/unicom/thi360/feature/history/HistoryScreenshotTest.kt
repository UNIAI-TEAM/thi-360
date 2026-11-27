package vn.unicom.thi360.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.network.generated.model.AttemptChoiceSummary
import vn.unicom.thi360.core.network.generated.model.AttemptHistoryItem
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.ShellWidth

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HistoryScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val fixedLoadedAt = Instant.parse("2026-11-20T02:05:00Z").toEpochMilli()

    private val sampleSubjects = mapOf(
        "LY" to CatalogOptionItem("LY", "Vật lí", "Physics"),
        "HOA" to CatalogOptionItem("HOA", "Hóa học", "Chemistry"),
    )

    private fun sampleItems(): List<AttemptHistoryItem> = listOf(
        HistoryTestFixtures.makeAttempt(
            idSeed = "snap-1",
            examName = "Đề HSA tổng hợp 01",
            scope = "FULL",
            state = AttemptHistoryItem.State.IN_PROGRESS,
            attemptNo = 2,
            startedAt = OffsetDateTime.parse("2026-11-20T02:05:00Z"),
            displayScore = null,
            rawScore = null,
            action = AttemptHistoryItem.Action.CONTINUE,
        ),
        HistoryTestFixtures.makeAttempt(
            idSeed = "snap-2",
            examName = "Đề Tư duy định lượng HSA",
            scope = "PART",
            partCode = "P1",
            state = AttemptHistoryItem.State.SCORED,
            attemptNo = 1,
            startedAt = OffsetDateTime.parse("2026-11-19T07:30:00Z"),
            displayScore = BigDecimal("82.5"),
            scaleMax = BigDecimal("100"),
            choices = mapOf("p2" to AttemptChoiceSummary("KH", listOf("LY", "HOA"))),
            action = AttemptHistoryItem.Action.VIEW_RESULT,
        ),
    )

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        shellWidth: ShellWidth,
        feedState: HistoryFeedState,
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
                        HistoryScreen(
                            feedState = feedState,
                            showCompactBrandHeader = shellWidth == ShellWidth.Narrow,
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
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun defaultState() = HistoryFeedState(
        loadState = LoadState.Loaded(
            value = HistoryPage(sampleItems(), 0, 2, 1, false),
            stale = false,
        ),
        lastLoadedAtMillis = fixedLoadedAt,
        subjectMap = sampleSubjects,
    )

    private fun staleState() = defaultState().copy(
        loadState = LoadState.Loaded(
            value = HistoryPage(sampleItems(), 0, 2, 1, false),
            stale = true,
        ),
    )

    private fun emptyNoAttemptsState() = HistoryFeedState(loadState = LoadState.Empty)

    private fun emptyNoMatchState() = HistoryFeedState(
        searchInput = "vact",
        filter = HistoryFilter(q = "vact", scope = HistoryScope.PART),
        loadState = LoadState.Empty,
    )

    private fun errorState() = HistoryFeedState(
        loadState = LoadState.Failed(LoadFailureKind.Server, canRetry = true),
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun history_360x640_default_light() =
        capture("history_360x640_default_light", ThemeMode.LIGHT, ShellWidth.Narrow, defaultState())

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun history_360x640_default_dark() =
        capture("history_360x640_default_dark", ThemeMode.DARK, ShellWidth.Narrow, defaultState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun history_412x915_default_light() =
        capture("history_412x915_default_light", ThemeMode.LIGHT, ShellWidth.Narrow, defaultState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun history_412x915_default_dark() =
        capture("history_412x915_default_dark", ThemeMode.DARK, ShellWidth.Narrow, defaultState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun history_412x915_empty_light() =
        capture("history_412x915_empty_light", ThemeMode.LIGHT, ShellWidth.Narrow, emptyNoAttemptsState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun history_412x915_empty_dark() =
        capture("history_412x915_empty_dark", ThemeMode.DARK, ShellWidth.Narrow, emptyNoAttemptsState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun history_412x915_nomatch_light() =
        capture("history_412x915_nomatch_light", ThemeMode.LIGHT, ShellWidth.Narrow, emptyNoMatchState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun history_412x915_nomatch_dark() =
        capture("history_412x915_nomatch_dark", ThemeMode.DARK, ShellWidth.Narrow, emptyNoMatchState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun history_412x915_stale_light() =
        capture("history_412x915_stale_light", ThemeMode.LIGHT, ShellWidth.Narrow, staleState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun history_412x915_stale_dark() =
        capture("history_412x915_stale_dark", ThemeMode.DARK, ShellWidth.Narrow, staleState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun history_412x915_error_light() =
        capture("history_412x915_error_light", ThemeMode.LIGHT, ShellWidth.Narrow, errorState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun history_412x915_error_dark() =
        capture("history_412x915_error_dark", ThemeMode.DARK, ShellWidth.Narrow, errorState())

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun history_840x720_default_light() =
        capture("history_840x720_default_light", ThemeMode.LIGHT, ShellWidth.TwoColumn, defaultState())

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun history_840x720_default_dark() =
        capture("history_840x720_default_dark", ThemeMode.DARK, ShellWidth.TwoColumn, defaultState())

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun history_1280x800_default_light() =
        capture("history_1280x800_default_light", ThemeMode.LIGHT, ShellWidth.TwoColumn, defaultState())

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun history_1280x800_default_dark() =
        capture("history_1280x800_default_dark", ThemeMode.DARK, ShellWidth.TwoColumn, defaultState())
}
