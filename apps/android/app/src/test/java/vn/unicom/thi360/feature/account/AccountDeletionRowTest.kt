package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.After
import org.junit.Assert.assertTrue
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
class AccountDeletionRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        server.respondApiFixture("GET", "/api/v1/me/account/deletion-check", 200, "me-account-deletion-check-200.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @Config(qualifiers = "w390dp-h1200dp")
    fun deleteAccountRowIsBelowLogoutOpensDeleteScreenAndStaysBehindLockScreen() {
        val vault = FakeBiometricVault(initialWrappedToken = "wrapped-refresh")
        val bio = BiometricController(
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
                accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                apiBaseUrl = server.url("/"),
                biometricController = bio,
                biometricAuthenticator = FakeBiometricAuthenticator(),
            )
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        val logoutBounds = composeRule.onNodeWithTag(AccountTestTags.LOGOUT_BUTTON)
            .performScrollTo()
            .getUnclippedBoundsInRoot()
        val deleteBounds = composeRule.onNodeWithTag(AccountTestTags.DELETE_ACCOUNT_ROW)
            .performScrollTo()
            .assertTextContains("Xóa tài khoản")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Delete Account row must be placed below Logout button",
            deleteBounds.top > logoutBounds.bottom,
        )

        composeRule.onNodeWithTag(AccountTestTags.DELETE_ACCOUNT_ROW).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.runOnIdle { stack.sessionManager.lockSession() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.SCREEN).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun twoPaneDeleteAccountRowOpensInDetailPane() {
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
                accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                apiBaseUrl = server.url("/"),
            )
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.DELETE_ACCOUNT_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.DELETE_ACCOUNT_ROW).assertIsSelected()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }
}
