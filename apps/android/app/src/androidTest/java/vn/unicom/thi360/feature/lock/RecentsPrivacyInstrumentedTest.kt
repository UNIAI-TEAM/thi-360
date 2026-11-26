package vn.unicom.thi360.feature.lock

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(AndroidJUnit4::class)
class RecentsPrivacyInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @After
    fun tearDown() {
        runCatching {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            runBlocking {
                AppSessionContainer.get(context).sessionManager.clearSession()
            }
        }
    }

    @Test
    fun flagSecureReactsToRealSessionLockAndClearsWhenSessionEnds() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = composeRule.activity
        val stack = AppSessionContainer.get(activity)

        runBlocking {
            stack.sessionManager.clearSession()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()

        // Signed out + biometric off -> FLAG_SECURE must be clear.
        var initialFlags = 0
        instrumentation.runOnMainSync {
            initialFlags = activity.window.attributes.flags
        }
        assertEquals(
            "FLAG_SECURE must not be set when signed out and biometric is off",
            0,
            initialFlags and WindowManager.LayoutParams.FLAG_SECURE,
        )

        // Transition real SessionManager to Locked -> MainActivity reactively sets FLAG_SECURE and hides shell.
        runBlocking {
            stack.sessionManager.adoptSession(
                Tokens(
                    accessToken = "instrumented-access-token",
                    refreshToken = "instrumented-refresh-token",
                    accessExpiresIn = 300,
                    refreshExpiresIn = 604800,
                    mustChangePassword = false,
                    username = "student.instrumented",
                ),
            )
            stack.sessionManager.lockSession()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()

        var protectedFlags = 0
        instrumentation.runOnMainSync {
            protectedFlags = activity.window.attributes.flags
        }
        assertTrue(
            "FLAG_SECURE must be set reactively on MainActivity window while SessionState.Locked",
            (protectedFlags and WindowManager.LayoutParams.FLAG_SECURE) != 0,
        )
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()

        // Clearing the session reactively clears FLAG_SECURE and returns to Login.
        runBlocking {
            stack.sessionManager.clearSession()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()

        var clearedFlags = 0
        instrumentation.runOnMainSync {
            clearedFlags = activity.window.attributes.flags
        }
        assertEquals(
            "FLAG_SECURE must clear reactively when session signs out and biometric is off",
            0,
            clearedFlags and WindowManager.LayoutParams.FLAG_SECURE,
        )
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
    }
}
