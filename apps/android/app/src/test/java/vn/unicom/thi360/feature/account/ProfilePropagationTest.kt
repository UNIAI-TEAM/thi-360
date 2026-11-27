package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import vn.unicom.thi360.core.biometric.FakeBiometricAuthenticator
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.lock.LockTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class ProfilePropagationTest {

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
    @Config(qualifiers = "w840dp-h720dp")
    fun infoSaveUpdatesAccountCardAndLockScreenDisplayNameWithZeroExtraProfileGet() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        server.respondApiFixture("PATCH", "/api/v1/me/profile", 200, "me-profile-update-200.json")

        val vault = FakeBiometricVault(initialWrappedToken = "wrapped-refresh")
        val biometricController = BiometricController(
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
                biometricController = biometricController,
                biometricAuthenticator = FakeBiometricAuthenticator(),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertTextContains("Nguyễn Văn An")
                composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        val getsBeforeSave = server.requestCount("GET", "/api/v1/me/profile")

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("Nguyễn Minh Anh")
        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertTextContains("Nguyễn Minh Anh")
                true
            }.getOrDefault(false)
        }

        assertEquals(
            "Saving profile must propagate to Account card without extra GET /me/profile",
            getsBeforeSave,
            server.requestCount("GET", "/api/v1/me/profile"),
        )
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_INITIALS).assertTextContains("MA")

        composeRule.runOnIdle { stack.sessionManager.lockSession() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(LockTestTags.LOCK_USER_NAME).assertTextContains("Nguyễn Minh Anh")
        assertEquals(getsBeforeSave, server.requestCount("GET", "/api/v1/me/profile"))
    }
}
