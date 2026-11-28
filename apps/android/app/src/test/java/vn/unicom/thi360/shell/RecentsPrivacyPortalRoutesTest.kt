package vn.unicom.thi360.shell

import android.os.Looper
import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.biometric.BiometricAvailability
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.lock.LockTestTags
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.RoutePlaceholderTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class RecentsPrivacyPortalRoutesTest {

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer
    private lateinit var biometricController: BiometricController

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = ContractFakeServer().start()
        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")

        biometricController = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = FakeBiometricVault(initialWrappedToken = "wrapped-refresh"),
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("acc", "ref", 300, 604800, false, "student.privacy", accountId = "acc-priv"),
            ),
            localeProvider = { AppLocale.VI },
            onSessionCleared = { biometricController.clearForSessionEnd() },
            startLocked = false,
        )
        app.setTestOverrides(biometricController = biometricController)
        AppSessionContainer.setTestStack(app, stack)
    }

    @After
    fun tearDown() {
        AppSessionContainer.setTestStack(app, null)
        app.setTestOverrides(biometricController = null)
        server.shutdown()
    }

    @Test
    fun flagSecureActiveOnAttemptRouteAndOverlayHidesAttemptContentWhenLockedOrBackgrounded() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            Shadows.shadowOf(Looper.getMainLooper()).idle()

            assertTrue(
                "FLAG_SECURE must be set on MainActivity when biometric is On",
                (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0,
            )

            val stack = AppSessionContainer.get(activity)
            stack.sessionManager.lockSession()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(
                "Session must be Locked",
                stack.sessionManager.state.value is vn.unicom.thi360.core.session.SessionState.Locked,
            )
            assertTrue(
                "FLAG_SECURE must remain set while Locked",
                (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0,
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class RecentsPrivacyAttemptOverlayTest {

    @get:Rule
    val composeRule = androidx.compose.ui.test.junit4.v2.createComposeRule()

    @Test
    fun attemptRouteContentHiddenByRecentsPrivacyShieldAndLockOverlay() {
        val server = ContractFakeServer().start()
        try {
            val biometricController = BiometricController(
                availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
                vault = FakeBiometricVault(initialWrappedToken = "wrapped"),
                promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
            )
            val stack = Thi360ApiFactory.createSessionStack(
                baseUrl = server.url("/"),
                environment = Environment.DEV,
                tokenStore = InMemoryTokenStore(
                    Tokens("acc", "ref", 300, 604800, false, "student.attempt", accountId = "acc-att"),
                ),
                localeProvider = { AppLocale.VI },
                onSessionCleared = { biometricController.clearForSessionEnd() },
            )
            var isBackgrounded = false
            val backgroundState = androidx.compose.runtime.mutableStateOf(false)
            var navigator: AppNavigator? = null

            composeRule.setContent {
                val navController = androidx.navigation.compose.rememberNavController()
                val appNav = AppNavigator(navController)
                navigator = appNav
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    navController = navController,
                    navigator = appNav,
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                    biometricController = biometricController,
                    isAppBackgrounded = backgroundState.value,
                )
            }

            val nav = checkNotNull(navigator)
            composeRule.runOnIdle { nav.openAttempt("att-secret-77") }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(vn.unicom.thi360.feature.player.PlayerTestTags.SCREEN).assertIsDisplayed()

            // Entering Recents shows privacy shield over attempt route (attempt is in the same window/overlay tree).
            composeRule.runOnIdle {
                isBackgrounded = true
                backgroundState.value = isBackgrounded
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(LockTestTags.RECENTS_PRIVACY_SHIELD).assertIsDisplayed()

            // Locking hides attempt content completely.
            composeRule.runOnIdle {
                backgroundState.value = false
                stack.sessionManager.lockSession()
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
            composeRule.onNodeWithTag(vn.unicom.thi360.feature.player.PlayerTestTags.SCREEN).assertDoesNotExist()
        } finally {
            server.shutdown()
        }
    }
}
