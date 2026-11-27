package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class DeleteAccountRefusalsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        server.respondApiFixture("GET", "/api/v1/me/account/deletion-check", 200, "me-account-deletion-check-200.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun refusal400ClearsPassword429And500AndOfflineKeepSessionAnd409SwitchesToBlocked() {
        server.respondApiFixture("POST", "/api/v1/me/account/deletion", 400, "me-account-deletion-400-wrong-old-password.json")

        val tokenStore = InMemoryTokenStore(testSignedInTokens())
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val deps = ProfileDependencies(
            profileStore = AccountProfileStore(profileGateway),
            profileGateway = profileGateway,
            accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
            sessionManager = stack.sessionManager,
            imageLoader = SingletonImageLoader.get(ApplicationProvider.getApplicationContext()),
            apiBaseUrl = server.url("/"),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                DeleteAccountScreen(dependencies = deps)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.PASSWORD_FIELD).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(DeleteAccountTestTags.ACKNOWLEDGE_ROW).performScrollTo().performClick()

        fun confirmDeletionWithPassword(pwd: String) {
            composeRule.onNodeWithTag(DeleteAccountTestTags.PASSWORD_FIELD).performScrollTo().performTextInput(pwd)
            composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
            composeRule.onNodeWithTag(DeleteAccountTestTags.CONFIRM_ACTION_BUTTON).performClick()
        }

        confirmDeletionWithPassword("WrongPass1!")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.PASSWORD_ERROR)
                    .performScrollTo()
                    .assertTextContains("chưa đúng", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()
        assertTrue(stack.sessionManager.state.value is SessionState.Active)
        assertNotNull(tokenStore.read())

        server.respondApiFixture("POST", "/api/v1/me/account/deletion", 429, "me-account-deletion-429-login-throttled.json")
        confirmDeletionWithPassword("RightPass1!")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.FORM_ERROR_BANNER)
                    .performScrollTo()
                    .assertTextContains("nhập sai nhiều lần", substring = true)
                true
            }.getOrDefault(false)
        }
        assertTrue(stack.sessionManager.state.value is SessionState.Active)

        server.respond("POST", "/api/v1/me/account/deletion") { MockResponse().setResponseCode(500) }
        composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(DeleteAccountTestTags.CONFIRM_ACTION_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.FORM_ERROR_BANNER)
                    .performScrollTo()
                    .assertTextContains("Chưa xóa được tài khoản", substring = true)
                true
            }.getOrDefault(false)
        }
        assertTrue(stack.sessionManager.state.value is SessionState.Active)
        assertNotNull(tokenStore.read())

        val beforeOffline = server.requestCount("POST", "/api/v1/me/account/deletion")
        server.respond("POST", "/api/v1/me/account/deletion") {
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(DeleteAccountTestTags.CONFIRM_ACTION_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("POST", "/api/v1/me/account/deletion") > beforeOffline && runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.FORM_ERROR_BANNER).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertTrue(stack.sessionManager.state.value is SessionState.Active)
        assertNotNull(tokenStore.read())

        server.respondApiFixture("POST", "/api/v1/me/account/deletion", 409, "me-account-deletion-409-account-deletion-blocked.json")
        composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(DeleteAccountTestTags.CONFIRM_ACTION_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_CARD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.BLOCKED_ATTEMPT_MESSAGE).performScrollTo().assertIsDisplayed()
        assertTrue(stack.sessionManager.state.value is SessionState.Active)
    }
}
