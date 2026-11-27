package vn.unicom.thi360.feature.myexams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.Instant
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.IntRange as ApiIntRange
import vn.unicom.thi360.core.network.generated.model.LocalizedName
import vn.unicom.thi360.core.network.generated.model.MyExamCard
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
class MyExamsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleSupport = SupportContact(
        phone = "1900 1234",
        email = "hotro@thi360.vn",
        hours = "08:00-21:00",
    )

    private val fixedLoadedAt = Instant.parse("2026-11-20T02:05:00Z").toEpochMilli()

    private fun sampleCards(): List<MyExamCard> {
        val bundleX = MyExamsTestFixtures.makeBundle("SNAP-X", "BUNDLE-HSA-01", "Bộ đề luyện thi HSA")
        val checking = MyExamsTestFixtures.makeCard(
            code = "EX-CHK",
            name = "Đề HSA tổng hợp 01",
            entitlementId = null,
            state = MyExamCard.State.CHECKING,
            pendingOrderCode = "DH261126000001",
            hasPendingFulfillment = true,
            actions = listOf(MyExamCard.Actions.CHECKING),
        )
        val inProgress = MyExamsTestFixtures.makeCard(
            code = "EX-B1",
            name = "Đề Tư duy định lượng HSA 01",
            state = MyExamCard.State.IN_PROGRESS,
            format = "PART",
            scopeLabel = LocalizedName(nameVi = "Tư duy định lượng", nameEn = "Quantitative"),
            questionCount = ApiIntRange(50, 50),
            durationMinutes = ApiIntRange(75, 75),
            attemptsLeft = 1,
            activeAttemptId = UUID.fromString("00000000-0000-7000-8000-000000000801"),
            resultAttemptId = UUID.fromString("00000000-0000-7000-8000-000000000802"),
            actions = listOf(
                MyExamCard.Actions.CONTINUE,
                MyExamCard.Actions.VIEW_RESULT,
                MyExamCard.Actions.HISTORY,
                MyExamCard.Actions.BUY_MORE,
            ),
            bundles = listOf(bundleX),
        )
        val classCard = MyExamsTestFixtures.makeCard(
            code = "EX-CLS",
            name = "Đề Toán lớp 12A1",
            entitlementSource = ENTITLEMENT_SOURCE_CLASS,
            className = "12A1",
            state = MyExamCard.State.CAN_START,
            format = "SUBJECT",
            scopeLabel = LocalizedName(nameVi = "Toán học", nameEn = "Mathematics"),
            questionCount = ApiIntRange(50, 50),
            durationMinutes = ApiIntRange(90, 90),
            attemptsLeft = 2,
            actions = listOf(MyExamCard.Actions.START),
            bundles = emptyList(),
        )
        return listOf(checking, inProgress, classCard)
    }

    private fun capture(
        name: String,
        themeMode: ThemeMode,
        shellWidth: ShellWidth,
        feedState: MyExamsFeedState,
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
                        MyExamsScreen(
                            feedState = feedState,
                            greetingName = "Nguyễn Văn An",
                            supportContact = sampleSupport,
                            showCompactBrandHeader = shellWidth == ShellWidth.Narrow,
                            onPullToRefresh = {},
                            onRetry = {},
                            onExploreExams = {},
                            onOpenOrder = {},
                            onOpenProduct = {},
                            onContinueAttempt = {},
                            onStartPrepare = {},
                            onOpenResult = {},
                            onOpenHistoryReset = {},
                            onOpenCheckout = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    private fun defaultState() = MyExamsFeedState(
        loadState = LoadState.Loaded(sampleCards(), stale = false),
        lastLoadedAtMillis = fixedLoadedAt,
    )

    private fun staleState() = MyExamsFeedState(
        loadState = LoadState.Loaded(sampleCards(), stale = true),
        lastLoadedAtMillis = fixedLoadedAt,
    )

    private fun emptyState() = MyExamsFeedState(loadState = LoadState.Empty)

    private fun errorState() = MyExamsFeedState(
        loadState = LoadState.Failed(LoadFailureKind.Server, canRetry = true),
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun my_exams_360x640_default_light() =
        capture("my_exams_360x640_default_light", ThemeMode.LIGHT, ShellWidth.Narrow, defaultState())

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun my_exams_360x640_default_dark() =
        capture("my_exams_360x640_default_dark", ThemeMode.DARK, ShellWidth.Narrow, defaultState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun my_exams_412x915_default_light() =
        capture("my_exams_412x915_default_light", ThemeMode.LIGHT, ShellWidth.Narrow, defaultState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun my_exams_412x915_default_dark() =
        capture("my_exams_412x915_default_dark", ThemeMode.DARK, ShellWidth.Narrow, defaultState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun my_exams_412x915_empty_light() =
        capture("my_exams_412x915_empty_light", ThemeMode.LIGHT, ShellWidth.Narrow, emptyState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun my_exams_412x915_empty_dark() =
        capture("my_exams_412x915_empty_dark", ThemeMode.DARK, ShellWidth.Narrow, emptyState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun my_exams_412x915_stale_light() =
        capture("my_exams_412x915_stale_light", ThemeMode.LIGHT, ShellWidth.Narrow, staleState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun my_exams_412x915_stale_dark() =
        capture("my_exams_412x915_stale_dark", ThemeMode.DARK, ShellWidth.Narrow, staleState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun my_exams_412x915_error_light() =
        capture("my_exams_412x915_error_light", ThemeMode.LIGHT, ShellWidth.Narrow, errorState())

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun my_exams_412x915_error_dark() =
        capture("my_exams_412x915_error_dark", ThemeMode.DARK, ShellWidth.Narrow, errorState())

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun my_exams_840x720_default_light() =
        capture("my_exams_840x720_default_light", ThemeMode.LIGHT, ShellWidth.TwoColumn, defaultState())

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun my_exams_840x720_default_dark() =
        capture("my_exams_840x720_default_dark", ThemeMode.DARK, ShellWidth.TwoColumn, defaultState())

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun my_exams_1280x800_default_light() =
        capture("my_exams_1280x800_default_light", ThemeMode.LIGHT, ShellWidth.TwoColumn, defaultState())

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun my_exams_1280x800_default_dark() =
        capture("my_exams_1280x800_default_dark", ThemeMode.DARK, ShellWidth.TwoColumn, defaultState())
}
