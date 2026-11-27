package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import okhttp3.mockwebserver.MockResponse
import org.junit.After
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
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class PendingEmailTest {

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
    @Config(qualifiers = "w840dp-h720dp")
    fun pendingCardSurvivesReloadMapsErrorsOpensPrefilledResendAndUpdatesCardOn204() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200-avatar-pending-email.json")
        server.respondSequence(
            "POST",
            "/api/v1/me/email/change/confirm",
            { ContractFakeServer.apiFixtureResponse(422, "me-email-change-confirm-422-email-code-invalid.json") },
            { ContractFakeServer.apiFixtureResponse(422, "me-email-verify-422-email-code-expired.json") },
            { ContractFakeServer.apiFixtureResponse(409, "me-email-change-409-email-taken.json") },
            { MockResponse().setResponseCode(503) },
            { MockResponse().setResponseCode(204) },
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
                apiBaseUrl = server.url("/"),
            )
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.PENDING_EMAIL_CARD).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.PENDING_RESEND_BUTTON).performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().assertTextContains("student.new@thi360.vn")
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_CANCEL).performScrollTo().performClick()

        composeRule.onNodeWithTag(ProfileTestTags.PENDING_CODE_FIELD).performScrollTo().performTextInput("123456")

        composeRule.onNodeWithTag(ProfileTestTags.PENDING_CONFIRM_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.PENDING_CODE_ERROR)
                    .performScrollTo()
                    .assertTextContains("còn 4 lần thử", substring = true)
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.PENDING_CONFIRM_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.PENDING_EMAIL_ALERT)
                    .performScrollTo()
                    .assertTextContains("không còn dùng được", substring = true)
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.PENDING_CONFIRM_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.PENDING_EMAIL_ALERT)
                    .performScrollTo()
                    .assertTextContains("tài khoản khác", substring = true)
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.PENDING_CONFIRM_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.PENDING_EMAIL_ALERT)
                    .performScrollTo()
                    .assertTextContains("email hiện tại giữ nguyên", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_EMAIL).assertTextContains("student.demo@thi360.vn")

        composeRule.onNodeWithTag(ProfileTestTags.PENDING_CONFIRM_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.SNACKBAR)
                    .assertTextContains("Đã đổi email.")
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_EMAIL)
                    .assertTextContains("student.new@thi360.vn")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.PENDING_EMAIL_CARD).assertDoesNotExist()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_EMAIL_BADGE).assertTextContains("Email đã xác minh")
    }
}
