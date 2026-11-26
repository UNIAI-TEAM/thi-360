package vn.unicom.thi360.feature.lock

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.biometric.BiometricAvailability
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.biometric.FakeBiometricAuthenticator
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LockBypassForResetLinkTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun resetIntent(token: String): Intent {
        val host = ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://$host/reset-password?token=$token"))
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun resetLinkBypassesLockAndSuccessfulResetClearsSessionAndBiometric() {
        val vault = FakeBiometricVault(initialWrappedToken = "locked-refresh")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val tokenStore = InMemoryTokenStore(
            Tokens("locked-access", "locked-refresh", 300, 604800, false, "student.locked"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
            startLocked = true,
        )
        val recoveryGateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        server.respondEmpty("POST", "/api/v1/auth/password/reset", 204)

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                recoveryGateway = recoveryGateway,
                biometricController = controller,
                biometricAuthenticator = FakeBiometricAuthenticator(),
                initialIntent = resetIntent("reset-while-locked-token"),
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PasswordTestTags.LINK_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertDoesNotExist()

        composeRule.onNodeWithTag(PasswordTestTags.NEW_PASSWORD_FIELD).performTextInput("StrongNew1!")
        composeRule.onNodeWithTag(PasswordTestTags.CONFIRM_PASSWORD_FIELD).performTextInput("StrongNew1!")
        composeRule.onNodeWithTag(PasswordTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value == SessionState.SignedOut(SignOutReason.PasswordReset)
        }
        composeRule.waitForIdle()

        assertNull("Tokens must be wiped after reset while locked", tokenStore.read())
        assertEquals("Biometric must be disabled after reset while locked", BiometricState.Off, controller.state.value)
        assertFalse("Biometric vault must be wiped after reset while locked", vault.hasWrappedToken())
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER)
            .assertTextContains("Đã đặt lại mật khẩu. Hãy đăng nhập bằng mật khẩu mới.")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun closingResetLinkWithoutCompletingReturnsToLockScreen() {
        val vault = FakeBiometricVault(initialWrappedToken = "locked-refresh")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("locked-access", "locked-refresh", 300, 604800, false, "student.locked"),
            ),
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
            startLocked = true,
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                biometricController = controller,
                biometricAuthenticator = FakeBiometricAuthenticator(),
                initialIntent = resetIntent("dismissed-token"),
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.CLOSE_BUTTON).performClick()
        composeRule.waitForIdle()

        assertTrue(stack.sessionManager.state.value is SessionState.Locked)
        assertEquals(BiometricState.On, controller.state.value)
        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
    }
}
