package vn.unicom.thi360.feature.player

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import java.nio.file.Files
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
import vn.unicom.thi360.core.biometric.BiometricPromptResult
import vn.unicom.thi360.core.biometric.FakeBiometricAuthenticator
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.attemptengine.AttemptOperation
import vn.unicom.thi360.feature.attemptengine.FakeAttemptServer
import vn.unicom.thi360.feature.lock.LockTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.LocalPlayerRouteOverrides
import vn.unicom.thi360.shell.routes.PlayerRouteOverrides
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class PlayerLockResumeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var authServer: ContractFakeServer
    private lateinit var attemptServer: FakeAttemptServer
    private lateinit var storeRoot: java.io.File

    @Before
    fun setUp() {
        authServer = ContractFakeServer().start()
        authServer.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
        attemptServer = FakeAttemptServer().start()
        storeRoot = Files.createTempDirectory("thi360-player-lock-").toFile()
    }

    @After
    fun tearDown() {
        attemptServer.shutdown()
        authServer.shutdown()
        storeRoot.deleteRecursively()
    }

    @Test
    fun lockShieldHidesPlayerAndUnlockRestoresAndHandlesTimeUpDuringLock() {
        val attempt = PlayerTestFixtures.withFutureDeadlines(PlayerTestFixtures.attemptState("in-progress-per-part"))
        attemptServer.setAttemptState(attempt)

        val vault = FakeBiometricVault(initialWrappedToken = "refresh-lock")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = authServer.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("acc-tok", "refresh-lock", 300, 604800, false, "student.lock", accountId = "acc-lock"),
            ),
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
        )
        val store = AccountScopedStore(storeRoot, "acc-lock")
        val overrides = PlayerRouteOverrides(
            attemptApi = attemptServer.virtualTimeApi(),
            store = store,
            accountId = "acc-lock",
            connectivity = MutableConnectivityMonitor(true),
        )
        val backgrounded = mutableStateOf(false)
        var navigator: AppNavigator? = null

        composeRule.setContent {
            val navController = rememberNavController()
            val appNav = AppNavigator(navController)
            navigator = appNav
            CompositionLocalProvider(LocalPlayerRouteOverrides provides overrides) {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    navController = navController,
                    navigator = appNav,
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                    biometricController = controller,
                    biometricAuthenticator = FakeBiometricAuthenticator(BiometricPromptResult.Success(null)),
                    isAppBackgrounded = backgrounded.value,
                )
            }
        }

        val nav = navigator!!
        composeRule.runOnIdle { nav.openAttempt(attempt.attemptId.toString()) }
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodes(hasTestTag(PlayerTestTags.TOP_BAR)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR).assertIsDisplayed()

        // Background shows recents privacy shield
        composeRule.runOnIdle { backgrounded.value = true }
        composeRule.onNodeWithTag(LockTestTags.RECENTS_PRIVACY_SHIELD).assertIsDisplayed()

        // 5-min timeout boundary locks session and hides player content completely
        val t0 = 10_000L
        controller.onAppBackgrounded(t0)
        assertTrue(controller.shouldLockOnForeground(t0 + 300_000L))
        composeRule.runOnIdle {
            backgrounded.value = false
            stack.sessionManager.lockSession()
        }
        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PlayerTestTags.TOP_BAR).assertDoesNotExist()

        // Server expires attempt while device was locked
        val closedTimeUp = PlayerTestFixtures.attemptState("closed-time-up").copy(attemptId = attempt.attemptId)
        attemptServer.setAttemptState(closedTimeUp)
        val getCallsBeforeUnlock = attemptServer.callCount(AttemptOperation.GET_STATE)

        // Unlock -> restores player route, fetches fresh server state, and displays Closed (TimeUp)
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitUntil(10_000L) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitUntil(10_000L) {
            composeRule.onAllNodes(hasTestTag(PlayerTestTags.CLOSED_SCREEN)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "Unlock must trigger fresh GET state refresh",
            attemptServer.callCount(AttemptOperation.GET_STATE) > getCallsBeforeUnlock,
        )
        composeRule.onNodeWithTag(PlayerTestTags.CLOSED_HEADING).assertTextContains("Đã hết giờ làm bài")
    }
}
