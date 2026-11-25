package vn.unicom.thi360.feature.lock

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(RobolectricTestRunner::class)
class LockPasswordFallbackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
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
            baseUrl = server.url("/").toString(),
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

        // Biometric lockout keeps app locked and displays lockout guidance.
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                composeRule.onNodeWithTag(LockTestTags.LOCK_ERROR_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertTrue(stack.sessionManager.state.value is SessionState.Locked)

        // Biometric cancelled also keeps app locked.
        authenticator.nextResult = BiometricPromptResult.Failure(BiometricAuthFailureReason.CANCELLED)
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitForIdle()
        assertTrue(stack.sessionManager.state.value is SessionState.Locked)

        // Tap "Nhập mật khẩu" -> opens Login pre-filled with locked username.
        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD)
            .assertIsDisplayed()
            .assertTextContains("minhanh.tran")

        // System Back returns to Lock screen while keeping held session intact.
        composeRule.runOnIdle { checkNotNull(backDispatcher).onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
        assertEquals("old-refresh-token", tokenStore.read()?.refreshToken)
        assertTrue(stack.sessionManager.state.value is SessionState.Locked)

        // Open fallback again; invalid credentials show login error and keep old locked session.
        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON).performClick()
        composeRule.waitForIdle()
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                401,
                ContractFixtureLoader.readAuthFixture("login-401-invalid-credentials.json"),
            ),
        )
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
        server.takeRequest()

        // Valid login replaces session, calls mobile/logout on old session (ignoring 500 error),
        // clears old biometric data, and re-prompts per PromptMemory (lastAnswer == YES -> prompt shown!).
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readAuthFixture("login-200.json"),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500))

        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("CorrectPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active &&
                runCatching {
                    composeRule.onNodeWithTag(LockTestTags.ENABLE_PROMPT_SCREEN).assertIsDisplayed()
                    true
                }.getOrDefault(false)
        }

        assertEquals("fixture-refresh-token", tokenStore.read()?.refreshToken)
        assertEquals("Old biometric state must be cleared before re-enable", BiometricState.Off, controller.state.value)
        assertFalse(vault.hasWrappedToken())

        val loginReq = server.takeRequest()
        assertEquals("/api/v1/auth/mobile/login", loginReq.path)
        val oldLogoutReq = server.takeRequest()
        assertEquals("/api/v1/auth/mobile/logout", oldLogoutReq.path)
        assertTrue(
            "Old session refreshToken must be sent to mobile/logout",
            oldLogoutReq.body.readUtf8().contains("old-refresh-token"),
        )
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun fallbackValidLoginSkipsBiometricPromptWhenPromptMemoryIsLater() {
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
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = tokenStore,
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
            )
        }

        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON).performClick()
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readAuthFixture("login-200.json"),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("CorrectPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LockTestTags.ENABLE_PROMPT_SCREEN).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
    }
}
