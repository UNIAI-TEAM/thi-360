package vn.unicom.thi360.core.biometric

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
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
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.account.AccountProfileGateway
import vn.unicom.thi360.feature.account.AccountTestTags
import vn.unicom.thi360.feature.account.UserProfile
import vn.unicom.thi360.feature.lock.LockTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(RobolectricTestRunner::class)
class BiometricInvalidationStateTest {

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
    fun invalidatedKeyClearsVaultTransitionsStateToOffAndRequiresPassword() = runTest {
        val vault = FakeBiometricVault(initialWrappedToken = "wrapped-refresh-token")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(PromptMemory(PromptAnswer.YES)),
        )
        assertEquals(BiometricState.On, controller.state.value)

        vault.simulateKeyInvalidated = true
        val authenticator = FakeBiometricAuthenticator(BiometricPromptResult.Success(null))

        val outcome = controller.unlock(authenticator)
        assertEquals(BiometricUnlockOutcome.KeyInvalidated, outcome)
        assertEquals(BiometricState.Off, controller.state.value)
        assertNull("Invalidated wrapped token must be deleted", vault.storedToken)
        assertFalse(vault.hasWrappedToken())
        assertTrue("OS biometric prompt must not even open when key is already invalidated", authenticator.calls.isEmpty())
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun lockScreenDemandsPasswordOnInvalidationAndAccountToggleShowsOffUntilReenabled() {
        val vault = FakeBiometricVault(initialWrappedToken = "wrapped-refresh")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(PromptMemory(PromptAnswer.LATER)),
        )
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
            startLocked = true,
        )
        val fakeProfileGateway = object : AccountProfileGateway {
            override suspend fun fetchProfile(): ApiResult<UserProfile> = ApiResult.Success(
                UserProfile("student.demo", "Nguyễn Văn An", "an@thi360.vn", true),
            )
        }
        val authenticator = FakeBiometricAuthenticator(BiometricPromptResult.Success(null))
        vault.simulateKeyInvalidated = true

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                profileGateway = fakeProfileGateway,
                biometricController = controller,
                biometricAuthenticator = authenticator,
            )
        }

        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            controller.state.value == BiometricState.Off
        }
        composeRule.waitForIdle()

        assertTrue(stack.sessionManager.state.value is SessionState.Locked)
        composeRule.onNodeWithTag(LockTestTags.LOCK_ERROR_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON).assertIsDisplayed()

        // After signing in again via password (simulating unlock by password), Account switch is Off.
        server.enqueue(
            vn.unicom.thi360.core.network.ContractFixtureLoader.jsonResponse(
                200,
                vn.unicom.thi360.core.network.ContractFixtureLoader.readAuthFixture("login-200.json"),
            ),
        )
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(204))

        composeRule.onNodeWithTag(LockTestTags.ENTER_PASSWORD_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.login.LoginTestTags.PASSWORD_FIELD)
            .performTextInput("SecretPass1!")
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.login.LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(AccountTestTags.BIOMETRIC_SWITCH).assertIsOff()
    }
}
