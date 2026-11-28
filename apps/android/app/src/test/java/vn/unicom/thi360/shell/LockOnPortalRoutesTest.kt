package vn.unicom.thi360.shell

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.After
import org.junit.Assert.assertFalse
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
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.lock.LockTestTags
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.RoutePlaceholderTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LockOnPortalRoutesTest {

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
    fun fourMinutes59DoesNotLockAndFiveMinutesLocksPortalSubRouteAndAttemptRestoringStateOnUnlock() {
        val vault = FakeBiometricVault(initialWrappedToken = "refresh-portal")
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("access-portal", "refresh-portal", 300, 604800, false, "student.lock", accountId = "acc-lock"),
            ),
            localeProvider = { AppLocale.VI },
            onSessionCleared = { controller.clearForSessionEnd() },
        )
        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")

        var navigator: AppNavigator? = null

        composeRule.setContent {
            val navController = rememberNavController()
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
                biometricController = controller,
                biometricAuthenticator = FakeBiometricAuthenticator(BiometricPromptResult.Success(null)),
                homeExtraRoutes = {
                    composable("home/lock_draft/{code}") { entry ->
                        val code = entry.arguments?.getString("code").orEmpty()
                        var answer by rememberSaveable { mutableStateOf("") }
                        Column {
                            Text("draft:$code", modifier = Modifier.testTag("lock_sub_param"))
                            OutlinedTextField(
                                value = answer,
                                onValueChange = { answer = it },
                                modifier = Modifier.testTag("lock_sub_input"),
                            )
                        }
                    }
                },
            )
        }

        val nav = checkNotNull(navigator)
        composeRule.runOnIdle { nav.navController.navigate("home/lock_draft/P-42") }
        composeRule.onNodeWithTag("lock_sub_param").assertTextContains("draft:P-42")
        composeRule.onNodeWithTag("lock_sub_input").performTextInput("dap-an-dang-go")

        // 4 minutes 59 seconds (299_000 ms) must NOT lock.
        val baseMillis = 100_000L
        controller.onAppBackgrounded(nowMillis = baseMillis)
        assertFalse(controller.shouldLockOnForeground(nowMillis = baseMillis + 299_000L))
        composeRule.onNodeWithTag("lock_sub_input").assertIsDisplayed().assertTextContains("dap-an-dang-go")

        // Exact 5 minutes (300_000 ms) boundary locks and hides portal sub-route.
        controller.onAppBackgrounded(nowMillis = baseMillis)
        assertTrue(controller.shouldLockOnForeground(nowMillis = baseMillis + 300_000L))
        composeRule.runOnIdle { stack.sessionManager.lockSession() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag("lock_sub_input").assertDoesNotExist()

        // Unlock restores exact sub-route, param, and typed draft!
        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("lock_sub_param").assertIsDisplayed().assertTextContains("draft:P-42")
        composeRule.onNodeWithTag("lock_sub_input").assertIsDisplayed().assertTextContains("dap-an-dang-go")

        // Same lock/unlock protection applies to chromeless attempt/{attemptId} route.
        composeRule.runOnIdle { nav.openAttempt("att-lock-9") }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.player.PlayerTestTags.SCREEN).assertIsDisplayed()
        composeRule.runOnIdle { stack.sessionManager.lockSession() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LockTestTags.LOCK_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.player.PlayerTestTags.SCREEN).assertDoesNotExist()

        composeRule.onNodeWithTag(LockTestTags.UNLOCK_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(vn.unicom.thi360.feature.player.PlayerTestTags.SCREEN).assertIsDisplayed()
        org.junit.Assert.assertEquals(
            "att-lock-9",
            nav.navController.currentBackStackEntry?.arguments?.getString(vn.unicom.thi360.shell.routes.AppRoute.ARG_ATTEMPT_ID),
        )
    }
}
