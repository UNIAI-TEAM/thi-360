package vn.unicom.thi360.feature.lock

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.biometric.BiometricAuthFailureReason
import vn.unicom.thi360.core.biometric.BiometricAvailability
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricPromptResult
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.biometric.FakeBiometricAuthenticator
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.biometric.PromptAnswer
import vn.unicom.thi360.core.biometric.PromptMemory
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.RefreshOutcome
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.password.ChangePasswordDraftViewModel
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LockPasswordFallbackTest {

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

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun failedBiometricStaysLockedFallbackPrefillsUsernameBackKeepsSessionAndValidLoginReplacesSessionIgnoringOldLogoutFailure() {
        val vault = FakeBiometricVault(initialWrappedToken = "old-refresh-token")
        val promptMemory = InMemoryBiometricPromptMemoryStore(PromptMemory(PromptAnswer.YES))
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = promptMemory,
        )
        val tokenStore = InMemoryTokenStore(
            Tokens("old-access-token", "old-refresh-token", 300, 604800, false, "minhanh.tran"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
            startLocked = true,
        )
        val authenticator = FakeBiometricAuthenticator(
            nextResult = BiometricPromptResult.Failure(BiometricAuthFailureReason.LOCKOUT),
        )
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                biometricController = controller,
                biometricAuthenticator = authenticator,
            )
        }

        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                composeRule.onNodeWithTag(LockTestTags.LOCK_ERROR_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertTrue(stack.sessionManager.state.value is SessionState.Locked)

        authenticator.nextResult = BiometricPromptResult.Failure(BiometricAuthFailureReason.CANCELLED)
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitForIdle()
        assertTrue(stack.sessionManager.state.value is SessionState.Locked)

        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD)
            .assertIsDisplayed()
            .assertTextContains("minhanh.tran")

        composeRule.runOnIdle { checkNotNull(backDispatcher).onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
        assertEquals("old-refresh-token", tokenStore.read()?.refreshToken)
        assertTrue(stack.sessionManager.state.value is SessionState.Locked)

        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON).performClick()
        composeRule.waitForIdle()
        server.respondSequence(
            "POST",
            "/api/v1/auth/mobile/login",
            { ContractFakeServer.authFixtureResponse(401, "login-401-invalid-credentials.json") },
            { ContractFakeServer.authFixtureResponse(200, "login-200.json") },
        )
        server.respond("POST", "/api/v1/auth/mobile/logout") {
            MockResponse()
                .setResponseCode(500)
                .setHeadersDelay(150, TimeUnit.MILLISECONDS)
        }

        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("WrongPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoginTestTags.ERROR_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals("old-refresh-token", tokenStore.read()?.refreshToken)
        assertTrue(stack.sessionManager.state.value is SessionState.Locked)

        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("CorrectPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active &&
                server.requestCount("POST", "/api/v1/auth/mobile/logout") == 1 &&
                runCatching {
                    composeRule.onNodeWithTag(LockTestTags.ENABLE_PROMPT_SCREEN).assertIsDisplayed()
                    true
                }.getOrDefault(false)
        }

        assertEquals("fixture-refresh-token", tokenStore.read()?.refreshToken)
        assertEquals("Old biometric state must be cleared before re-enable", BiometricState.Off, controller.state.value)
        assertFalse(vault.hasWrappedToken())

        val loginReq = server.lastRequest("POST", "/api/v1/auth/mobile/login")
        assertNotNull("Expected mobile/login request", loginReq)
        assertEquals("/api/v1/auth/mobile/login", loginReq!!.path)
        val oldLogoutReq = server.lastRequest("POST", "/api/v1/auth/mobile/logout")
        assertNotNull("Expected background mobile/logout request for old session", oldLogoutReq)
        assertEquals("/api/v1/auth/mobile/logout", oldLogoutReq!!.path)
        assertTrue(
            "Old session refreshToken must be sent to mobile/logout",
            oldLogoutReq.body.clone().readUtf8().contains("old-refresh-token"),
        )
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun fallbackValidLoginSkipsBiometricPromptWhenPromptMemoryIsLaterAndClearsActivePasswordDrafts() {
        val vault = FakeBiometricVault(initialWrappedToken = "old-refresh-token")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(PromptMemory(PromptAnswer.LATER)),
        )
        val tokenStore = InMemoryTokenStore(
            Tokens("old-access", "old-refresh-token", 300, 604800, false, "minhanh.tran"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
            startLocked = true,
        )
        val activeDraft = ChangePasswordDraftViewModel().apply {
            oldPasswordState.edit { append("OldSecret1!") }
            newPasswordState.edit { append("NewSecret1!") }
            confirmPasswordState.edit { append("NewSecret1!") }
        }

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                biometricController = controller,
                biometricAuthenticator = FakeBiometricAuthenticator(),
            )
        }

        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON).performClick()
        server.respondAuthFixture("POST", "/api/v1/auth/mobile/login", 200, "login-200.json")
        server.respondEmpty("POST", "/api/v1/auth/mobile/logout", 204)

        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("CorrectPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active &&
                server.requestCount("POST", "/api/v1/auth/mobile/logout") == 1
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LockTestTags.ENABLE_PROMPT_SCREEN).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
        assertEquals("", activeDraft.oldPasswordState.text.toString())
        assertEquals("", activeDraft.newPasswordState.text.toString())
        assertEquals("", activeDraft.confirmPasswordState.text.toString())
        assertNotNull(server.lastRequest("POST", "/api/v1/auth/mobile/login"))
        assertNotNull(server.lastRequest("POST", "/api/v1/auth/mobile/logout"))
    }

    @Test
    fun unlockAndRefreshWithMissingStoredTokensExitsLockedToSignedOutExpired() = runBlocking {
        var sessionClearedCount = 0
        val tokenStore = InMemoryTokenStore(
            Tokens("access-1", "refresh-1", 300, 604800, false, "student.locked"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { sessionClearedCount++ },
            startLocked = true,
        )
        assertTrue(stack.sessionManager.state.value is SessionState.Locked)

        tokenStore.clear()
        val outcome = stack.sessionManager.unlockAndRefresh()

        assertEquals(RefreshOutcome.SignedOut, outcome)
        assertEquals(SessionState.SignedOut(SignOutReason.Expired), stack.sessionManager.state.value)
        assertEquals(1, sessionClearedCount)
    }
}
