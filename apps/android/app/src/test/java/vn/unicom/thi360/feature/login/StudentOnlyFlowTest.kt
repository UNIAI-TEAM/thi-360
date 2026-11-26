package vn.unicom.thi360.feature.login

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
import vn.unicom.thi360.core.session.RefreshOutcome
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(RobolectricTestRunner::class)
class StudentOnlyFlowTest {

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
    @Config(qualifiers = "w390dp-h844dp")
    fun loginAppStudentOnlyShowsRoleNoticeWithUsernameAndStoresNoTokens() {
        val tokenStore = InMemoryTokenStore()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                403,
                ContractFixtureLoader.readAuthFixture("login-403-app-student-only.json"),
            ),
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("quantri.noidung")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("AdminPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value == SessionState.SignedOut(
                SignOutReason.StudentOnly("quantri.noidung"),
            )
        }
        composeRule.waitForIdle()

        assertNull("APP_STUDENT_ONLY on login must never persist tokens", tokenStore.read())
        assertEquals("APP_STUDENT_ONLY on login must never write TokenStore", 0, tokenStore.writeCount)

        composeRule.onNodeWithTag(LoginTestTags.ROLE_NOTICE_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.ROLE_NOTICE_TITLE)
            .assertTextContains("Ứng dụng này dành cho học viên")
        composeRule.onNodeWithTag(LoginTestTags.ROLE_NOTICE_BODY)
            .assertTextContains("quantri.noidung", substring = true)
        composeRule.onNodeWithTag(ShellTestTags.BOTTOM_BAR).assertDoesNotExist()

        composeRule.onNodeWithTag(LoginTestTags.ROLE_NOTICE_SWITCH_ACCOUNT).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun refreshAppStudentOnlyClearsStoredSessionAndShowsRoleNotice() {
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "old-student-access",
                refreshToken = "old-student-refresh",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "promoted.admin",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                403,
                ContractFixtureLoader.readAuthFixture("refresh-403-app-student-only.json"),
            ),
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

        val outcome = runBlocking { stack.sessionManager.refreshSession() }
        assertEquals(RefreshOutcome.StudentOnly, outcome)
        assertNull("APP_STUDENT_ONLY on refresh must clear TokenStore", tokenStore.read())
        assertEquals(
            SessionState.SignedOut(SignOutReason.StudentOnly("promoted.admin")),
            stack.sessionManager.state.value,
        )

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoginTestTags.ROLE_NOTICE_SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(LoginTestTags.ROLE_NOTICE_TITLE)
            .assertTextContains("Ứng dụng này dành cho học viên")
        composeRule.onNodeWithTag(LoginTestTags.ROLE_NOTICE_BODY)
            .assertTextContains("promoted.admin", substring = true)
    }
}
