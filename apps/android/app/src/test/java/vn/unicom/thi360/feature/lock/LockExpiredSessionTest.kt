package vn.unicom.thi360.feature.lock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import java.io.IOException
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
import vn.unicom.thi360.core.biometric.BiometricPromptResult
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.biometric.FakeBiometricAuthenticator
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.MobileAuthGateway
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LockExpiredSessionTest {

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
    fun onlineUnlockWithRevokedSessionSignsOutWithExpiredNoticeAndClearsBiometric() {
        val vault = FakeBiometricVault(initialWrappedToken = "revoked-refresh")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val tokenStore = InMemoryTokenStore(
            Tokens("revoked-access", "revoked-refresh", 300, 604800, false, "student.demo"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
            startLocked = true,
        )
        server.respondAuthFixture(
            "POST",
            "/api/v1/auth/mobile/refresh",
            401,
            "refresh-401-session-revoked.json",
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                biometricController = controller,
                biometricAuthenticator = FakeBiometricAuthenticator(BiometricPromptResult.Success(null)),
            )
        }

        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value == SessionState.SignedOut(SignOutReason.Expired)
        }
        composeRule.waitForIdle()

        assertNull(tokenStore.read())
        assertEquals(BiometricState.Off, controller.state.value)
        assertFalse(vault.hasWrappedToken())
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER)
            .assertIsDisplayed()
            .assertTextContains("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun offlineUnlockEntersAppNormallyWithoutSigningOut() {
        val vault = FakeBiometricVault(initialWrappedToken = "offline-refresh")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val tokenStore = InMemoryTokenStore(
            Tokens("offline-access", "offline-refresh", 300, 604800, false, "student.offline"),
        )
        val offlineGateway = object : MobileAuthGateway {
            override suspend fun login(identifier: String, password: String): ApiResult<Tokens> = error("unused")
            override suspend fun refresh(refreshToken: String): ApiResult<Tokens> =
                ApiResult.NetworkFailure(IOException("No network"))
            override suspend fun logout(refreshToken: String): ApiResult<Unit> = ApiResult.Success(Unit)
        }
        val sessionManager = SessionManager(
            tokenStore = tokenStore,
            authGateway = offlineGateway,
            onSessionCleared = { controller.clearForSessionEnd() },
            startLocked = true,
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = sessionManager,
                validateOnLaunch = false,
                biometricController = controller,
                biometricAuthenticator = FakeBiometricAuthenticator(BiometricPromptResult.Success(null)),
            )
        }

        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()

        assertEquals(SessionState.Active(username = "student.offline", isOffline = true), sessionManager.state.value)
        assertEquals(BiometricState.On, controller.state.value)
        assertTrue(vault.hasWrappedToken())
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.OFFLINE_BANNER).assertIsDisplayed()
    }
}
