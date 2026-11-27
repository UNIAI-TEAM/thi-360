package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
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
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.biometric.BiometricAvailability
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.biometric.FakeBiometricAuthenticator
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.image.Thi360ImageLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class DeleteAccountSuccessTest {

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

    private fun runDeletionEndToEnd(
        deleteResponse: () -> MockResponse,
        twoPane: Boolean,
    ) {
        val avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200-avatar-pending-email.json")
        server.respond("GET", avatarPath) { pngMockResponse() }
        server.respondApiFixture("GET", "/api/v1/me/account/deletion-check", 200, "me-account-deletion-check-200.json")
        server.respond("POST", "/api/v1/me/account/deletion", deleteResponse)

        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val rootDir = File(context.filesDir, "delete_account_test_${System.nanoTime()}").apply { mkdirs() }
        val cleanup = SessionCleanup(rootDir)
        val tokens = testSignedInTokens()
        val tokenStore = InMemoryTokenStore(tokens)
        val accountStoreProvider = AccountScopedStoreProvider(
            rootDir = rootDir,
            accountIdProvider = { tokenStore.read()?.accountId },
        )
        runBlocking {
            checkNotNull(accountStoreProvider.current()).writeString("drafts", "k1", "sensitive-draft")
        }

        val vault = FakeBiometricVault(initialWrappedToken = "wrapped-refresh")
        val bio = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { bio.clearForSessionEnd() },
            sessionCleanup = cleanup,
        )
        val loader = Thi360ImageLoader.create(context, server.url("/"), stack, cleanup)
        val profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val profileStore = AccountProfileStore(profileGateway, sessionCleanup = cleanup)

        try {
            composeRule.setContent {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                    startDestination = Destination.Account.route,
                    profileGateway = profileGateway,
                    profileStore = profileStore,
                    accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                    imageLoader = loader.imageLoader,
                    apiBaseUrl = server.url("/"),
                    sessionCleanup = cleanup,
                    biometricController = bio,
                    biometricAuthenticator = FakeBiometricAuthenticator(),
                )
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_IMAGE).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            assertTrue((loader.imageLoader.memoryCache?.size ?: 0L) > 0L)

            composeRule.onNodeWithTag(AccountTestTags.DELETE_ACCOUNT_ROW).performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(DeleteAccountTestTags.PASSWORD_FIELD).performScrollTo().assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            composeRule.onNodeWithTag(DeleteAccountTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("MyPassword1!")
            composeRule.onNodeWithTag(DeleteAccountTestTags.ACKNOWLEDGE_ROW).performScrollTo().performClick()
            composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
            composeRule.onNodeWithTag(DeleteAccountTestTags.CONFIRM_ACTION_BUTTON).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                stack.sessionManager.state.value == SessionState.SignedOut(SignOutReason.AccountDeleted)
            }
            composeRule.waitForIdle()

            assertNull("Tokens must be wiped", tokenStore.read())
            assertEquals(BiometricState.Off, bio.state.value)
            assertFalse("Biometric vault must be wiped", vault.hasWrappedToken())
            assertNull("AccountProfileStore must be wiped", profileStore.currentProfileOrNull())
            assertEquals(LoadState.Loading, profileStore.state.value)
            assertEquals(0L, loader.imageLoader.memoryCache?.size ?: 0L)
            assertEquals(
                "Account deletion must skip /mobile/logout",
                0,
                server.requestCount("POST", "/api/v1/auth/mobile/logout"),
            )
            val remainingAccountFiles = rootDir.listFiles()?.filter { it.name != ".pending_cleanup" }.orEmpty()
            assertTrue("AccountScopedStore data must be wiped", remainingAccountFiles.isEmpty())

            composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
            composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER)
                .assertIsDisplayed()
                .assertTextContains("Tài khoản của bạn đã được xóa.")
            composeRule.onNodeWithTag(DeleteAccountTestTags.SCREEN).assertDoesNotExist()
            if (twoPane) {
                composeRule.onNodeWithTag(AccountTestTags.DETAIL_PANE).assertDoesNotExist()
            }
        } finally {
            profileStore.close()
            loader.close()
            rootDir.deleteRecursively()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun delete204SignsOutWipesAllStoresAndShowsDeletedNotice() {
        runDeletionEndToEnd(deleteResponse = { MockResponse().setResponseCode(204) }, twoPane = false)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun delete401AfterLostResponseMapsToAccountDeletedWithoutExpiredReason() {
        runDeletionEndToEnd(
            deleteResponse = { ContractFakeServer.apiFixtureResponse(401, "me-401-session-revoked.json") },
            twoPane = false,
        )
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun delete204FromTwoPaneDetailPaneExitsShellToLoginWithDeletedNotice() {
        runDeletionEndToEnd(deleteResponse = { MockResponse().setResponseCode(204) }, twoPane = true)
    }
}
