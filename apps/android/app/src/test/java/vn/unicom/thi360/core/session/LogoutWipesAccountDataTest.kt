package vn.unicom.thi360.core.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.account.AccountProfileGateway
import vn.unicom.thi360.feature.account.AccountTestTags
import vn.unicom.thi360.feature.account.UserProfile
import vn.unicom.thi360.feature.lock.LockTestTags
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LogoutWipesAccountDataTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var rootDir: File
    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        rootDir = Files.createTempDirectory("thi360-logout-wipe-").toFile()
        server = ContractFakeServer().start()
        server.respondEmpty("POST", "/api/v1/auth/mobile/logout", 204)
    }

    @After
    fun tearDown() {
        server.shutdown()
        rootDir.deleteRecursively()
    }

    private fun seedTwoAccounts(): Pair<AccountScopedStore, AccountScopedStore> {
        val storeA = AccountScopedStore(rootDir, "account-A")
        val storeB = AccountScopedStore(rootDir, "account-B")
        assertTrue(storeA.writeString("drafts", "att-1", "account-A-secret-draft"))
        assertTrue(storeB.writeString("drafts", "att-1", "account-B-preserved-draft"))
        return storeA to storeB
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun accountScreenLogoutWipesAccountAAndKeepsAccountB() {
        val (_, storeB) = seedTwoAccounts()
        val cleanup = SessionCleanup(rootDir)
        val tokenStore = InMemoryTokenStore(
            Tokens("acc-a", "ref-a", 300, 604800, false, "student.a", accountId = "account-A"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            sessionCleanup = cleanup,
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                startDestination = Destination.Account.route,
                profileGateway = object : AccountProfileGateway {
                    override suspend fun fetchProfile(): ApiResult<UserProfile> =
                        ApiResult.Success(UserProfile("student.a", "Học viên A", "a@thi360.vn", true))
                },
            )
        }

        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.LOGOUT_CONFIRM_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.SignedOut
        }
        assertFalse(AccountScopedStore.accountDirectory(rootDir, "account-A").exists())
        assertEquals("account-B-preserved-draft", storeB.readString("drafts", "att-1"))
        assertNull(tokenStore.read())
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun lockScreenOtherAccountLogoutWipesAccountAAndKeepsAccountB() {
        val (_, storeB) = seedTwoAccounts()
        val cleanup = SessionCleanup(rootDir)
        val biometricController = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = FakeBiometricVault(initialWrappedToken = "ref-a"),
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val tokenStore = InMemoryTokenStore(
            Tokens("acc-a", "ref-a", 300, 604800, false, "student.a", accountId = "account-A"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { biometricController.clearForSessionEnd() },
            startLocked = true,
            sessionCleanup = cleanup,
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
                biometricController = biometricController,
                biometricAuthenticator = FakeBiometricAuthenticator(),
            )
        }

        composeRule.onNodeWithTag(LockTestTags.OTHER_ACCOUNT_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.SignedOut
        }
        assertFalse(AccountScopedStore.accountDirectory(rootDir, "account-A").exists())
        assertEquals("account-B-preserved-draft", storeB.readString("drafts", "att-1"))
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun forcedPasswordChangeLogoutWipesAccountAAndKeepsAccountB() {
        val (_, storeB) = seedTwoAccounts()
        val cleanup = SessionCleanup(rootDir)
        val tokenStore = InMemoryTokenStore(
            Tokens("acc-a", "ref-a", 300, 604800, mustChangePassword = true, username = "student.a", accountId = "account-A"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            sessionCleanup = cleanup,
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                validateOnLaunch = false,
            )
        }

        composeRule.onNodeWithTag(PasswordTestTags.LOGOUT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.SignedOut
        }
        assertFalse(AccountScopedStore.accountDirectory(rootDir, "account-A").exists())
        assertEquals("account-B-preserved-draft", storeB.readString("drafts", "att-1"))
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
    }

    @Test
    fun expiredOrPasswordResetClearSessionPreservesAccountScopedData() = runTest {
        val (storeA, storeB) = seedTwoAccounts()
        val cleanupRuns = AtomicInteger(0)
        val cleanup = SessionCleanup(rootDir).apply {
            register { cleanupRuns.incrementAndGet() }
        }
        val tokenStore = InMemoryTokenStore(
            Tokens("acc-a", "ref-a", 300, 604800, false, "student.a", accountId = "account-A"),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            sessionCleanup = cleanup,
        )

        stack.sessionManager.clearSession(SignOutReason.Expired)
        assertEquals(0, cleanupRuns.get())
        assertEquals("account-A-secret-draft", storeA.readString("drafts", "att-1"))
        assertEquals("account-B-preserved-draft", storeB.readString("drafts", "att-1"))
    }

    @Test
    fun doubleTap() = runBlocking {
        seedTwoAccounts()
        val cleanupCalls = AtomicInteger(0)
        val logoutApiCalls = AtomicInteger(0)
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()

        val cleanup = SessionCleanup(rootDir).apply {
            register {
                cleanupCalls.incrementAndGet()
                cleanupStarted.complete(Unit)
                releaseCleanup.await()
            }
        }
        val gateway = object : MobileAuthGateway {
            override suspend fun login(identifier: String, password: String): ApiResult<Tokens> = error("unused")
            override suspend fun refresh(refreshToken: String): ApiResult<Tokens> = error("unused")
            override suspend fun logout(refreshToken: String): ApiResult<Unit> {
                logoutApiCalls.incrementAndGet()
                return ApiResult.Success(Unit)
            }
        }
        val tokenStore = InMemoryTokenStore(
            Tokens("acc-a", "ref-a", 300, 604800, false, "student.a", accountId = "account-A"),
        )
        val manager = SessionManager(
            tokenStore = tokenStore,
            authGateway = gateway,
            sessionCleanup = cleanup,
        )

        val first = async { manager.logout() }
        cleanupStarted.await()
        val second = async { manager.logout() }
        releaseCleanup.complete(Unit)
        awaitAll(first, second)

        assertEquals("Double-tap logout must run SessionCleanup exactly once", 1, cleanupCalls.get())
        assertEquals("Double-tap logout must invoke mobile/logout exactly once", 1, logoutApiCalls.get())
        assertFalse(AccountScopedStore.accountDirectory(rootDir, "account-A").exists())
    }
}
