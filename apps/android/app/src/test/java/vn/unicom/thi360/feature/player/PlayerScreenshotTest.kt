package vn.unicom.thi360.feature.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.OffsetDateTime
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.OptionView
import vn.unicom.thi360.core.network.generated.model.QuestionSet
import vn.unicom.thi360.core.network.generated.model.SingleChoice
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.attemptengine.AnswerSyncStatus
import vn.unicom.thi360.feature.attemptengine.AttemptClosedReason
import vn.unicom.thi360.feature.attemptengine.Countdown
import vn.unicom.thi360.feature.attemptengine.CountdownTarget
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.EngineState
import vn.unicom.thi360.feature.attemptengine.LocalAnswer
import vn.unicom.thi360.feature.attemptengine.PartSelection

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun foldQuestionSetAttempt() =
        PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part")).let { base ->
            val parts = PartSelection.sortedParts(base)
            val setId = UUID(0L, 100L)
            val c1 = UUID(0L, 101L)
            val c2 = UUID(0L, 102L)
            val openPart = parts.first { it.state == AttemptPartStateView.State.OPEN }.copy(
                items = listOf(
                    AttemptItemView(
                        itemId = setId,
                        displayNo = 1,
                        questionType = AttemptItemView.QuestionType.QUESTION_SET,
                        questionBody = QuestionSet(
                            passage = "<p>Đọc đoạn văn sau và trả lời các câu hỏi từ 1 đến 2. Mạng lưới giao thông đô thị thông minh giúp giảm thiểu ùn tắc và khí thải.</p>",
                        ),
                    ),
                    AttemptItemView(
                        itemId = c1,
                        parentItemId = setId,
                        displayNo = 1,
                        content = "<p>Ý chính của đoạn văn trên là gì?</p>",
                        questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                        questionBody = SingleChoice(
                            options = listOf(
                                OptionView(id = "a", content = "Lợi ích của giao thông thông minh"),
                                OptionView(id = "b", content = "Chi phí xây dựng cầu đường"),
                            ),
                        ),
                    ),
                    AttemptItemView(
                        itemId = c2,
                        parentItemId = setId,
                        displayNo = 2,
                        content = "<p>Tác động tích cực nào được nhắc tới?</p>",
                        questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                        questionBody = SingleChoice(
                            options = listOf(
                                OptionView(id = "c", content = "Giảm ùn tắc và khí thải"),
                                OptionView(id = "d", content = "Tăng tốc độ xe máy"),
                            ),
                        ),
                    ),
                ),
            )
            val patchedParts = parts.map { if (it.partId == openPart.partId) openPart else it }
            base.copy(parts = patchedParts)
        }

    private fun capture(
        name: String,
        engineState: EngineState,
        localUi: PlayerLocalUi = PlayerLocalUi(),
        themeMode: ThemeMode = ThemeMode.LIGHT,
        fontScale: Float = 1.0f,
        partChoiceLoad: LoadState<PartChoiceMetadata> = LoadState.Loading,
    ) {
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, fontScale = fontScale),
            ) {
                PlayerTestTheme(themeMode = themeMode) {
                    PlayerScreen(
                        attemptId = engineState.attempt?.attemptId?.toString() ?: "00000000-0000-7000-8000-000000000001",
                        engineState = engineState,
                        localUi = localUi,
                        partChoiceLoad = partChoiceLoad,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun phoneLight() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture("player_phone_light", PlayerTestFixtures.activeEngineState(attempt), themeMode = ThemeMode.LIGHT)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun phoneDark() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture("player_phone_dark", PlayerTestFixtures.activeEngineState(attempt), themeMode = ThemeMode.DARK)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun phoneNarrowLight() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture("player_phone_narrow_light", PlayerTestFixtures.activeEngineState(attempt))
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun phoneFont130() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture("player_phone_font130", PlayerTestFixtures.activeEngineState(attempt), fontScale = 1.3f)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun phoneFont200() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture("player_phone_font200", PlayerTestFixtures.activeEngineState(attempt), fontScale = 2.0f)
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun foldThreeColumnLight() {
        val attempt = foldQuestionSetAttempt()
        val state = PlayerTestFixtures.activeEngineState(
            attempt = attempt,
            answersOverride = mapOf(
                UUID(0L, 101L) to LocalAnswer(SingleChoiceAnswer("a"), 1L, flagged = true, sync = AnswerSyncStatus.Saved),
            ),
        )
        capture("player_fold_3col_light", state, themeMode = ThemeMode.LIGHT)
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp-xhdpi")
    fun foldThreeColumnDark() {
        val attempt = foldQuestionSetAttempt()
        val state = PlayerTestFixtures.activeEngineState(
            attempt = attempt,
            answersOverride = mapOf(
                UUID(0L, 101L) to LocalAnswer(SingleChoiceAnswer("a"), 1L, flagged = true, sync = AnswerSyncStatus.Saved),
            ),
        )
        capture("player_fold_3col_dark", state, themeMode = ThemeMode.DARK)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tabletLight() {
        val attempt = foldQuestionSetAttempt()
        capture("player_tablet_light", PlayerTestFixtures.activeEngineState(attempt), themeMode = ThemeMode.LIGHT)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun tabletDark() {
        val attempt = foldQuestionSetAttempt()
        capture("player_tablet_dark", PlayerTestFixtures.activeEngineState(attempt), themeMode = ThemeMode.DARK)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun partChoiceLight() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("awaiting-choice"))
        val metadata = PlayerTestFixtures.partChoiceMetadata("per-part")
        val choicePart = PartSelection.sortedParts(attempt)
            .first { it.state == AttemptPartStateView.State.AWAITING_CHOICE }
        capture(
            name = "player_part_choice_light",
            engineState = PlayerTestFixtures.activeEngineState(attempt).copy(
                clock = Countdown(CountdownTarget.Choice(choicePart.partId!!), 14 * 60_000L),
            ),
            partChoiceLoad = LoadState.Loaded(metadata),
            themeMode = ThemeMode.LIGHT,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun partChoiceDark() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("awaiting-choice"))
        val metadata = PlayerTestFixtures.partChoiceMetadata("per-part")
        val choicePart = PartSelection.sortedParts(attempt)
            .first { it.state == AttemptPartStateView.State.AWAITING_CHOICE }
        capture(
            name = "player_part_choice_dark",
            engineState = PlayerTestFixtures.activeEngineState(attempt).copy(
                clock = Countdown(CountdownTarget.Choice(choicePart.partId!!), 25_000L),
            ),
            partChoiceLoad = LoadState.Loaded(metadata),
            themeMode = ThemeMode.DARK,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun gridSheetLight() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_grid_sheet_light",
            engineState = PlayerTestFixtures.activeEngineState(attempt),
            localUi = PlayerLocalUi(gridSheetOpen = true),
            themeMode = ThemeMode.LIGHT,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun gridSheetDark() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_grid_sheet_dark",
            engineState = PlayerTestFixtures.activeEngineState(attempt),
            localUi = PlayerLocalUi(gridSheetOpen = true),
            themeMode = ThemeMode.DARK,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun generatingLight() {
        val generating = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("generating"))
        val metadata = PlayerTestFixtures.partChoiceMetadata("per-part")
        capture("player_generating_light", PlayerTestFixtures.activeEngineState(generating), partChoiceLoad = LoadState.Loaded(metadata), themeMode = ThemeMode.LIGHT)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun generatingDark() {
        val generating = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("generating"))
        val metadata = PlayerTestFixtures.partChoiceMetadata("per-part")
        capture("player_generating_dark", PlayerTestFixtures.activeEngineState(generating), partChoiceLoad = LoadState.Loaded(metadata), themeMode = ThemeMode.DARK)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun generationFailedLight() {
        val failed = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("generation-failed"))
        val metadata = PlayerTestFixtures.partChoiceMetadata("per-part")
        capture("player_generation_failed_light", PlayerTestFixtures.activeEngineState(failed), partChoiceLoad = LoadState.Loaded(metadata), themeMode = ThemeMode.LIGHT)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun generationFailedDark() {
        val failed = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("generation-failed"))
        val metadata = PlayerTestFixtures.partChoiceMetadata("per-part")
        capture("player_generation_failed_dark", PlayerTestFixtures.activeEngineState(failed), partChoiceLoad = LoadState.Loaded(metadata), themeMode = ThemeMode.DARK)
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun dialogTakeoverLight() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_dialog_takeover_light",
            engineState = PlayerTestFixtures.activeEngineState(
                attempt = attempt,
                session = EditSessionState.OpenElsewhere(OffsetDateTime.parse("2026-11-20T10:30:45Z")),
            ),
            themeMode = ThemeMode.LIGHT,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun dialogTakeoverDark() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_dialog_takeover_dark",
            engineState = PlayerTestFixtures.activeEngineState(
                attempt = attempt,
                session = EditSessionState.OpenElsewhere(OffsetDateTime.parse("2026-11-20T10:30:45Z")),
            ),
            themeMode = ThemeMode.DARK,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun dialogFinishLight() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_dialog_finish_light",
            engineState = PlayerTestFixtures.activeEngineState(attempt),
            localUi = PlayerLocalUi(dialog = PlayerDialog.FinishPart),
            themeMode = ThemeMode.LIGHT,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun dialogFinishDark() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_dialog_finish_dark",
            engineState = PlayerTestFixtures.activeEngineState(attempt),
            localUi = PlayerLocalUi(dialog = PlayerDialog.FinishPart),
            themeMode = ThemeMode.DARK,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun dialogSubmitLight() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_dialog_submit_light",
            engineState = PlayerTestFixtures.activeEngineState(attempt),
            localUi = PlayerLocalUi(dialog = PlayerDialog.Submit),
            themeMode = ThemeMode.LIGHT,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun dialogSubmitDark() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_dialog_submit_dark",
            engineState = PlayerTestFixtures.activeEngineState(attempt),
            localUi = PlayerLocalUi(dialog = PlayerDialog.Submit),
            themeMode = ThemeMode.DARK,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun dialogLeaveLight() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_dialog_leave_light",
            engineState = PlayerTestFixtures.activeEngineState(attempt),
            localUi = PlayerLocalUi(dialog = PlayerDialog.LeaveConfirm),
            themeMode = ThemeMode.LIGHT,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun dialogLeaveDark() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_dialog_leave_dark",
            engineState = PlayerTestFixtures.activeEngineState(attempt),
            localUi = PlayerLocalUi(dialog = PlayerDialog.LeaveConfirm),
            themeMode = ThemeMode.DARK,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun closedLight() {
        val closed = PlayerTestFixtures.attemptState("closed-time-up")
        capture(
            name = "player_closed_light",
            engineState = EngineState(
                load = LoadState.Loaded(Unit),
                attempt = closed,
                closed = AttemptClosedReason.TimeUp,
            ),
            themeMode = ThemeMode.LIGHT,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun closedDark() {
        val closed = PlayerTestFixtures.attemptState("closed-time-up")
        capture(
            name = "player_closed_dark",
            engineState = EngineState(
                load = LoadState.Loaded(Unit),
                attempt = closed,
                closed = AttemptClosedReason.TimeUp,
            ),
            themeMode = ThemeMode.DARK,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun evictedLight() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_evicted_light",
            engineState = PlayerTestFixtures.activeEngineState(
                attempt = attempt,
                session = EditSessionState.Evicted,
            ),
            themeMode = ThemeMode.LIGHT,
        )
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xhdpi")
    fun evictedDark() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        capture(
            name = "player_evicted_dark",
            engineState = PlayerTestFixtures.activeEngineState(
                attempt = attempt,
                session = EditSessionState.Evicted,
            ),
            themeMode = ThemeMode.DARK,
        )
    }
}
