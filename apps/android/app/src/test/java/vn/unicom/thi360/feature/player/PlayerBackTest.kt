package vn.unicom.thi360.feature.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.feature.attemptengine.SaveStatus
import vn.unicom.thi360.feature.attemptengine.UnsavedReason

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerBackTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savedExitsImmediatelyWhileUnsavedPromptsA86LeaveDialog() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        var saveStatus by mutableStateOf<SaveStatus>(SaveStatus.Saved)
        var localUi by mutableStateOf(PlayerLocalUi())
        var locale by mutableStateOf(AppLocale.VI)
        var closeCalls = 0

        composeRule.setContent {
            PlayerTestTheme(locale = locale) {
                PlayerScreen(
                    attemptId = attempt.attemptId.toString(),
                    engineState = PlayerTestFixtures.activeEngineState(attempt).copy(save = saveStatus),
                    localUi = localUi,
                    onOpenDialog = { localUi = localUi.copy(dialog = it) },
                    onDismissDialog = { localUi = localUi.copy(dialog = PlayerDialog.None) },
                    onCloseAttempt = { closeCalls += 1 },
                )
            }
        }

        // Saved -> closes immediately with no dialog
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_BACK).performClick()
        assertEquals(1, closeCalls)
        composeRule.onNodeWithTag(PlayerTestTags.LEAVE_DIALOG).assertDoesNotExist()

        // Unsaved -> shows A-86 leave confirmation dialog
        composeRule.runOnIdle { saveStatus = SaveStatus.Unsaved(UnsavedReason.NetworkError) }
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_BACK).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.LEAVE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.LEAVE_STAY).assertTextContains("Ở lại")
        composeRule.onNodeWithTag(PlayerTestTags.LEAVE_CONFIRM).assertTextContains("Rời màn")
        assertEquals(1, closeCalls)

        // English copy check (A-86)
        composeRule.runOnIdle { locale = AppLocale.EN }
        composeRule.onNodeWithTag(PlayerTestTags.LEAVE_STAY).assertTextContains("Stay")
        composeRule.onNodeWithTag(PlayerTestTags.LEAVE_CONFIRM).assertTextContains("Leave")

        // Stay dismisses without closing
        composeRule.onNodeWithTag(PlayerTestTags.LEAVE_STAY).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.LEAVE_DIALOG).assertDoesNotExist()
        assertEquals(1, closeCalls)

        // Confirm Leave closes attempt
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR_BACK).performClick()
        composeRule.onNodeWithTag(PlayerTestTags.LEAVE_CONFIRM).performClick()
        assertEquals(2, closeCalls)
    }
}
