package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.Instant
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.biometric.BiometricAvailability
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricPromptResult
import vn.unicom.thi360.core.biometric.FakeBiometricAuthenticator
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.ui.LoadStateTestTags
import vn.unicom.thi360.feature.lock.LockTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class VerifyEmailAutoSendTest {

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
    fun autoSendsOncePerOpenSurvivesLockUnlockAndResendsOnFreshReopen() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-update-200.json")
        server.respondEmpty("POST", "/api/v1/me/email/verification", 202)
        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")

        val clock = MutableTestClock(Instant.parse("2026-04-20T10:00:00Z"))
        val vault = FakeBiometricVault(initialWrappedToken = "test-refresh-token")
        val bioController = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                startDestination = Destination.Account.route,
                profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/")),
                apiBaseUrl = server.url("/"),
                clock = clock,
                biometricController = bioController,
                biometricAuthenticator = FakeBiometricAuthenticator(BiometricPromptResult.Success(null)),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_CARD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.VERIFY_EMAIL_BUTTON).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.VERIFY_EMAIL_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("POST", "/api/v1/me/email/verification") == 1
        }
        composeRule.onNodeWithTag(VerifyEmailTestTags.RESEND_COOLDOWN).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(VerifyEmailTestTags.RESEND_BUTTON).performScrollTo().assertIsNotEnabled()

        composeRule.runOnIdle { stack.sessionManager.lockSession() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Locked
        }
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(VerifyEmailTestTags.SCREEN).assertIsDisplayed()
        assertEquals(
            "Lock -> unlock must never trigger a duplicate auto-send",
            1,
            server.requestCount("POST", "/api/v1/me/email/verification"),
        )

        composeRule.onNodeWithTag(VerifyEmailTestTags.BACK_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.VERIFY_EMAIL_BUTTON).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.VERIFY_EMAIL_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("POST", "/api/v1/me/email/verification") == 2
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun dailyLimitAndLoadRetryAndAlreadyVerifiedZeroSend() {
        server.respondSequence(
            "GET",
            "/api/v1/me/profile",
            { MockResponse().setResponseCode(500) },
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-update-200.json") },
        )
        server.respondApiFixture("POST", "/api/v1/me/email/verification", 429, "me-email-verification-429-rate-limited.json") {
            it.replace("\"retryAfter\" : 60", "\"retryAfter\" : 7200")
        }

        val clock = MutableTestClock(Instant.parse("2026-04-20T10:00:00Z"))
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val store = AccountProfileStore(gateway)

        composeRule.setContent {
            vn.unicom.thi360.core.theme.Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                VerifyEmailScreen(
                    onBackToProfile = {},
                    dependencies = ProfileDependencies(
                        profileStore = store,
                        profileGateway = gateway,
                        accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                        sessionManager = stack.sessionManager,
                        imageLoader = coil3.SingletonImageLoader.get(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
                        apiBaseUrl = server.url("/"),
                        clock = clock,
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoadStateTestTags.ERROR_CONTAINER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(0, server.requestCount("POST", "/api/v1/me/email/verification"))

        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.DAILY_LIMIT_BANNER).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(VerifyEmailTestTags.DAILY_LIMIT_BANNER)
            .assertTextContains("20/4 19:00", substring = true)
        assertEquals(1, server.requestCount("POST", "/api/v1/me/email/verification"))
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun alreadyVerifiedEmailDoesNotAutoSend() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val store = AccountProfileStore(gateway)

        composeRule.setContent {
            vn.unicom.thi360.core.theme.Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                VerifyEmailScreen(
                    onBackToProfile = {},
                    dependencies = ProfileDependencies(
                        profileStore = store,
                        profileGateway = gateway,
                        accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                        sessionManager = stack.sessionManager,
                        imageLoader = coil3.SingletonImageLoader.get(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
                        apiBaseUrl = server.url("/"),
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.ALREADY_VERIFIED_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(0, server.requestCount("POST", "/api/v1/me/email/verification"))
    }
}
