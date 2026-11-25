package vn.unicom.thi360.feature.login

import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(RobolectricTestRunner::class)
class LoginFlowTest {

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
    @Config(qualifiers = "w412dp-h915dp")
    fun successAndRelaunch() {
        val persistentTokenStore = InMemoryTokenStore()
        val firstRunStack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = persistentTokenStore,
            localeProvider = { AppLocale.VI },
        )
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readAuthFixture("login-200.json"),
            ),
        )
        // Cold relaunch refreshes the stored session in the background.
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readAuthFixture("refresh-200.json"),
            ),
        )

        var launchGeneration by mutableIntStateOf(1)

        composeRule.setContent {
            if (launchGeneration == 1) {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = firstRunStack.sessionManager,
                )
            } else {
                val relaunchStack = Thi360ApiFactory.createSessionStack(
                    baseUrl = server.url("/").toString(),
                    environment = Environment.DEV,
                    tokenStore = persistentTokenStore,
                    localeProvider = { AppLocale.VI },
                )
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = relaunchStack.sessionManager,
                )
            }
        }

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("student.demo")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("StudentPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            firstRunStack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).assertIsSelected()
        assertNotNull(persistentTokenStore.read())

        val loginReq = server.takeRequest()
        assertEquals("/api/v1/auth/mobile/login", loginReq.path)
        assertEquals("vi", loginReq.getHeader("Accept-Language"))

        // Simulate full process kill + cold relaunch reading only persisted tokens.
        composeRule.runOnIdle { launchGeneration = 2 }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertDoesNotExist()
        assertNotNull(persistentTokenStore.read())
    }

    @Test
    fun mustChangePasswordAdoptAndLogoutTransitionsMatchB13() = runTest {
        val store = InMemoryTokenStore()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = store,
            localeProvider = { AppLocale.EN },
        )
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readAuthFixture("login-200-must-change-password.json"),
            ),
        )
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(204))

        stack.sessionManager.login("student.forced", "TempPass1!")
        assertTrue(stack.sessionManager.state.value is SessionState.MustChangePassword)

        stack.sessionManager.markPasswordChanged()
        assertTrue(stack.sessionManager.state.value is SessionState.Active)
        assertEquals(false, store.read()?.mustChangePassword)

        stack.sessionManager.logout()
        assertEquals(SessionState.SignedOut(SignOutReason.None), stack.sessionManager.state.value)
        assertNull(store.read())

        stack.sessionManager.adoptSession(
            Tokens(
                accessToken = "otp-access",
                refreshToken = "otp-refresh",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "new.student",
            ),
        )
        assertEquals(SessionState.Active(username = "new.student", isOffline = false), stack.sessionManager.state.value)

        stack.sessionManager.clearSession(SignOutReason.PasswordReset)
        assertEquals(SessionState.SignedOut(SignOutReason.PasswordReset), stack.sessionManager.state.value)
        assertNull(store.read())
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun signedOutLoginPreservesTabStateBoundsBackStackAndClearsSavedStateAcrossSignOut() {
        val store = InMemoryTokenStore()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = store,
            localeProvider = { AppLocale.VI },
        )
        val userALogin = ContractFixtureLoader.readAuthFixture("login-200.json")
            .replace("student.demo", "student.alpha")
        val userBLogin = ContractFixtureLoader.readAuthFixture("login-200.json")
            .replace("student.demo", "student.beta")
        server.enqueue(ContractFixtureLoader.jsonResponse(200, userALogin))
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(204))
        server.enqueue(ContractFixtureLoader.jsonResponse(200, userBLogin))

        var observedNavController: NavHostController? = null

        composeRule.setContent {
            val navController = rememberNavController()
            observedNavController = navController
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                navController = navController,
                sessionManager = stack.sessionManager,
                accountContent = {
                    var draft by rememberSaveable { mutableStateOf("") }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.testTag("account_draft_input"),
                    )
                },
            )
        }

        // Cold signed-out login as User A.
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("student.alpha")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("StudentPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()

        // Home -> Account (type draft) -> MyExams -> Account -> Home.
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("account_draft_input").performTextInput("alpha-draft-note")
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.MyExams)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("account_draft_input").assertTextContains("alpha-draft-note")

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.waitForIdle()

        val controller = checkNotNull(observedNavController)
        val routesAfterSwitching = controller.currentBackStack.value
            .mapNotNull { it.destination.route }
        assertEquals(
            "Back stack must not accumulate duplicate shell tab entries",
            routesAfterSwitching.distinct(),
            routesAfterSwitching,
        )
        assertEquals(Destination.Home.route, controller.graph.startDestinationRoute)

        // Sign out User A and sign in as User B -> User A's Account draft must not survive.
        runBlocking { stack.sessionManager.logout() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("student.beta")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("StudentPass2!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value == SessionState.Active(username = "student.beta", isOffline = false)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("account_draft_input").assertTextContains("", substring = false)
    }
}
