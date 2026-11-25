package vn.unicom.thi360.feature.lock

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.biometric.RecentsPrivacyPolicy
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(AndroidJUnit4::class)
class RecentsPrivacyInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun flagSecureProtectsMainActivityWindowWhenLockedOrBiometricEnabledAndClearsWhenOff() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = composeRule.activity

        // Initially signed out and biometric off -> FLAG_SECURE must be clear.
        var initialFlags = 0
        instrumentation.runOnMainSync {
            initialFlags = activity.window.attributes.flags
        }
        assertEquals(
            "FLAG_SECURE must not be set when signed out and biometric is off",
            0,
            initialFlags and WindowManager.LayoutParams.FLAG_SECURE,
        )

        // When RecentsPrivacyPolicy requires protection (Locked or Biometric On), applyRecentsPrivacy sets FLAG_SECURE.
        assertTrue(
            RecentsPrivacyPolicy.shouldProtectWindow(
                biometricState = BiometricState.On,
                sessionState = SessionState.Locked(username = "student.demo"),
            ),
        )
        instrumentation.runOnMainSync {
            activity.applyRecentsPrivacy(protect = true)
        }
        var protectedFlags = 0
        instrumentation.runOnMainSync {
            protectedFlags = activity.window.attributes.flags
        }
        assertTrue(
            "FLAG_SECURE must be active on MainActivity window when locked/biometric enabled",
            (protectedFlags and WindowManager.LayoutParams.FLAG_SECURE) != 0,
        )

        // When protection is disabled (biometric Off + not Locked), FLAG_SECURE clears cleanly.
        instrumentation.runOnMainSync {
            activity.applyRecentsPrivacy(protect = false)
        }
        var clearedFlags = 0
        instrumentation.runOnMainSync {
            clearedFlags = activity.window.attributes.flags
        }
        assertEquals(
            "FLAG_SECURE must clear when biometric is off and session is not locked",
            0,
            clearedFlags and WindowManager.LayoutParams.FLAG_SECURE,
        )
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
    }
}
