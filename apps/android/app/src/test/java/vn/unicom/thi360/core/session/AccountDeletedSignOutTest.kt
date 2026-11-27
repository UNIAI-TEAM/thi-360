package vn.unicom.thi360.core.session

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
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
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class AccountDeletedSignOutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var rootDir: File
    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        rootDir = Files.createTempDirectory("thi360-account-deleted-").toFile()
        server = ContractFakeServer().start()
        server.respondEmpty("POST", "/api/v1/auth/mobile/logout", 204)
    }

    @After
    fun tearDown() {
        server.shutdown()
        rootDir.deleteRecursively()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun accountDeletedWipesLocalDataSkipsLogoutApiShowsNoticeInViAndEnBlocksBackAndClearsNoticeOnRelaunch() {
        val accountId = "acc-deleted-1"
        val storeA = AccountScopedStore(rootDir, accountId)
        val storeOther = AccountScopedStore(rootDir, "acc-other-2")
        assertTrue(storeA.writeString("drafts", "att-1", "deleted-user-draft"))
        assertTrue(storeOther.writeString("drafts", "att-1", "other-user-draft"))

        val cleanerRuns = AtomicInteger(0)
        val sessionCleanup = SessionCleanup(rootDir).apply {
            register { cleanerRuns.incrementAndGet() }
        }
        val biometricVault = FakeBiometricVault(initialWrappedToken = "deleted-refresh")
        val biometricController = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = biometricVault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val tokenStore = InMemoryTokenStore(
            Tokens("deleted-access", "deleted-refresh", 300, 604800, false, "deleted.student", accountId = accountId),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            onSessionCleared = { biometricController.clearForSessionEnd() },
            sessionCleanup = sessionCleanup,
        )

        var locale by mutableStateOf(AppLocale.VI)
        var launchGeneration by mutableIntStateOf(1)
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            if (launchGeneration == 1) {
                AppRoot(
                    preferences = AppPreferences(locale = locale),
                    onSelectLocale = { locale = it },
                    onSelectThemeMode = {},
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                    biometricController = biometricController,
                )
            } else {
                val freshStack = Thi360ApiFactory.createSessionStack(
                    baseUrl = server.url("/"),
                    environment = Environment.DEV,
                    tokenStore = tokenStore,
                    localeProvider = { locale },
                    sessionCleanup = sessionCleanup,
                )
                AppRoot(
                    preferences = AppPreferences(locale = locale),
                    onSelectLocale = { locale = it },
                    onSelectThemeMode = {},
                    sessionManager = freshStack.sessionManager,
                    validateOnLaunch = false,
                    biometricController = biometricController,
                )
            }
        }

        // Trigger AccountDeleted logout.
        runBlocking {
            stack.sessionManager.logout(SignOutReason.AccountDeleted)
        }
        composeRule.waitForIdle()

        // 1. Zero calls to mobile/logout because server already revoked session.
        assertEquals(0, server.requestCount("POST", "/api/v1/auth/mobile/logout"))

        // 2. Full local cleanup ran: cleaners, account store wiped, tokens cleared, biometric disabled.
        assertEquals(1, cleanerRuns.get())
        assertFalse(AccountScopedStore.accountDirectory(rootDir, accountId).exists())
        assertEquals("other-user-draft", storeOther.readString("drafts", "att-1"))
        assertNull(tokenStore.read())
        assertEquals(BiometricState.Off, biometricController.state.value)
        assertFalse(biometricVault.hasWrappedToken())
        assertEquals(SessionState.SignedOut(SignOutReason.AccountDeleted), stack.sessionManager.state.value)

        // 3. Login screen shows exact VI notice.
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER)
            .assertIsDisplayed()
            .assertTextContains("Tài khoản của bạn đã được xóa.")
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()

        // 4. Switching locale shows exact EN notice.
        composeRule.runOnIdle { locale = AppLocale.EN }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER)
            .assertIsDisplayed()
            .assertTextContains("Your account has been deleted.")

        // 5. Pressing Back does NOT return to the signed-in shell.
        composeRule.runOnIdle { checkNotNull(backDispatcher).onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()

        // 6. Relaunching app (recreating Activity / fresh SessionManager from empty TokenStore) stays on Login without notice.
        composeRule.runOnIdle { launchGeneration = 2 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.NOTICE_BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()
    }
}
