package vn.unicom.thi360.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.time.OffsetDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.feature.attemptengine.AttemptCommandKind
import vn.unicom.thi360.feature.attemptengine.CommandState
import vn.unicom.thi360.feature.attemptengine.EditSessionState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class TakeoverDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun formatsVietnamTimezoneAndGuardsDoubleTapAndSuppressesOtherDialogs() {
        val originalTz = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
            val lastSeen = OffsetDateTime.parse("2026-11-20T10:30:45Z")
            var locale by mutableStateOf(AppLocale.VI)
            var command by mutableStateOf<CommandState>(CommandState.Idle)
            var takeoverCalls = 0
            var cancelCalls = 0

            composeRule.setContent {
                PlayerTestTheme(locale = locale) {
                    PlayerScreen(
                        attemptId = attempt.attemptId.toString(),
                        engineState = PlayerTestFixtures.activeEngineState(
                            attempt = attempt,
                            session = EditSessionState.OpenElsewhere(lastSeen),
                        ).copy(command = command),
                        localUi = PlayerLocalUi(dialog = PlayerDialog.Submit),
                        onTakeover = {
                            takeoverCalls += 1
                            command = CommandState.Sending(AttemptCommandKind.Takeover)
                        },
                        onBackToMyExams = { cancelCalls += 1 },
                    )
                }
            }

            composeRule.onNodeWithTag(PlayerTestTags.TAKEOVER_DIALOG).assertIsDisplayed()
            composeRule.onNodeWithTag(PlayerTestTags.SUBMIT_DIALOG).assertDoesNotExist()
            composeRule.onNodeWithTag(PlayerTestTags.TAKEOVER_LAST_SEEN)
                .assertTextContains("17:30:45", substring = true)
                .assertTextContains("20/11/2026", substring = true)

            composeRule.runOnIdle { locale = AppLocale.EN }
            composeRule.onNodeWithTag(PlayerTestTags.TAKEOVER_LAST_SEEN)
                .assertTextContains("17:30:45", substring = true)

            composeRule.onNodeWithTag(PlayerTestTags.TAKEOVER_CANCEL).performClick()
            assertEquals(1, cancelCalls)
            assertEquals(0, takeoverCalls)

            composeRule.onNodeWithTag(PlayerTestTags.TAKEOVER_CONFIRM).performClick()
            composeRule.onNodeWithTag(PlayerTestTags.TAKEOVER_CONFIRM).assertIsNotEnabled()
            composeRule.onNodeWithTag(PlayerTestTags.TAKEOVER_CONFIRM).performClick()
            assertEquals(1, takeoverCalls)
        } finally {
            TimeZone.setDefault(originalTz)
        }
    }
}
