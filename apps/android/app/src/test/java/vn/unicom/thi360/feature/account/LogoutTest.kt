package vn.unicom.thi360.feature.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
import vn.unicom.thi360.core.biometric.PromptAnswer
import vn.unicom.thi360.core.biometric.PromptMemory
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.session.MobileAuthGateway
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LogoutTest {

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
    fun confirmLogoutCallsApiOnceClearsSessionAndBiometricPreservesPrefsAndIsolatesNextUser() {
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "user-a-access",
                refreshToken = "user-a-refresh",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.alpha",
            ),
        )
        val biometricVault = FakeBiometricVault(initialWrappedToken = "user-a-refresh")
        val promptMemory = InMemoryBiometricPromptMemoryStore(PromptMemory(PromptAnswer.LATER))
        val biometricController = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = biometricVault,
            promptMemoryStore = promptMemory,
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.EN },
            onSessionCleared = { biometricController.clearForSessionEnd() },
        )
        var profileCalls = 0
        val profileGateway = object : AccountProfileGateway {
            override suspend fun fetchProfile(): ApiResult<UserProfile> {
                profileCalls++
                return if (profileCalls == 1) {
                    ApiResult.Success(UserProfile("student.alpha", "Alpha Student", "alpha@thi360.vn", true))
                } else {
                    ApiResult.Success(UserProfile("student.beta", "Beta Student", "beta@thi360.vn", false))
                }
            }
        }
        var preferences by mutableStateOf(AppPreferences(locale = AppLocale.EN, themeMode = ThemeMode.DARK))

        composeRule.setContent {
            AppRoot(
                preferences = preferences,
                onSelectLocale = { preferences = preferences.copy(locale = it) },
                onSelectThemeMode = { preferences = preferences.copy(themeMode = it) },
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                startDestination = Destination.Account.route,
                profileGateway = profileGateway,
                biometricController = biometricController,
                biometricAuthenticator = FakeBiometricAuthenticator(),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertTextContains("Alpha Student")
                true
            }.getOrDefault(false)
        }
        assertEquals(BiometricState.On, biometricController.state.value)

        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_CANCEL_BUTTON).performClick()
        assertEquals(0, server.requestCount("POST", "/api/v1/auth/mobile/logout"))
        assertEquals(BiometricState.On, biometricController.state.value)

        server.respondEmpty("POST", "/api/v1/auth/mobile/logout", 204)
        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_CONFIRM_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.SignedOut &&
                server.requestCount("POST", "/api/v1/auth/mobile/logout") == 1
        }
        composeRule.waitForIdle()

        assertNull("Tokens must be cleared on logout", tokenStore.read())
        assertEquals("Biometric state must reset to Off on logout", BiometricState.Off, biometricController.state.value)
        assertFalse("Biometric vault must be wiped on logout", biometricVault.hasWrappedToken())
        assertEquals("Locale must survive logout", AppLocale.EN, preferences.locale)
        assertEquals("Theme must survive logout", ThemeMode.DARK, preferences.themeMode)

        val logoutReq = server.requests("POST", "/api/v1/auth/mobile/logout").single()
        assertEquals("/api/v1/auth/mobile/logout", logoutReq.path)
        assertEquals(1, server.requestCount("POST", "/api/v1/auth/mobile/logout"))
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()

        server.respondAuthFixture("POST", "/api/v1/auth/mobile/login", 200, "login-200.json") {
            it.replace("student.demo", "student.beta")
        }

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("student.beta")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("BetaPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertTextContains("Beta Student")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Alpha Student", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("alpha@thi360.vn", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun offlineLogoutClearsLocalSessionAndDoubleTapSendsSingleRequest() {
        val logoutCalls = AtomicInteger(0)
        val releaseLogout = CompletableDeferred<ApiResult<Unit>>()
        val gatedGateway = object : MobileAuthGateway {
            override suspend fun login(identifier: String, password: String): ApiResult<Tokens> = error("unused")
            override suspend fun refresh(refreshToken: String): ApiResult<Tokens> = error("unused")
            override suspend fun logout(refreshToken: String): ApiResult<Unit> {
                logoutCalls.incrementAndGet()
                return releaseLogout.await()
            }
        }
        val tokenStore = InMemoryTokenStore(
            Tokens("a", "r-offline", 300, 604800, false, "student.offline"),
        )
        val vault = FakeBiometricVault(initialWrappedToken = "r-offline")
        val biometricController = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val sessionManager = SessionManager(
            tokenStore = tokenStore,
            authGateway = gatedGateway,
            onSessionCleared = { biometricController.clearForSessionEnd() },
        )
        val profileGateway = object : AccountProfileGateway {
            override suspend fun fetchProfile(): ApiResult<UserProfile> =
                ApiResult.NetworkFailure(IOException("Offline"))
        }

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = sessionManager,
                validateOnLaunch = false,
                startDestination = Destination.Account.route,
                profileGateway = profileGateway,
                biometricController = biometricController,
                biometricAuthenticator = FakeBiometricAuthenticator(),
            )
        }

        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_CONFIRM_BUTTON).performClick()
        runCatching { composeRule.onNodeWithTag(AccountTestTags.LOGOUT_CONFIRM_BUTTON).performClick() }

        releaseLogout.complete(ApiResult.NetworkFailure(IOException("No network")))

        composeRule.waitUntil(timeoutMillis = 5_000) {
            sessionManager.state.value is SessionState.SignedOut
        }
        composeRule.waitForIdle()

        assertEquals("Double-tap logout must only invoke mobile/logout once", 1, logoutCalls.get())
        assertNull("Offline logout must still clear stored tokens", tokenStore.read())
        assertEquals("Offline logout must still disable biometric", BiometricState.Off, biometricController.state.value)
        assertFalse(vault.hasWrappedToken())
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
    }
}
