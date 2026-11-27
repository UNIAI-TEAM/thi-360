package vn.unicom.thi360.feature.account

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
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
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class ChangeEmailFormTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun mountProfile(clock: MutableTestClock = MutableTestClock(Instant.parse("2026-04-20T10:00:00Z"))) {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val store = AccountProfileStore(gateway)
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ProfileScreen(
                    dependencies = ProfileDependencies(
                        profileStore = store,
                        profileGateway = gateway,
                        accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                        sessionManager = stack.sessionManager,
                        imageLoader = SingletonImageLoader.get(ApplicationProvider.getApplicationContext()),
                        apiBaseUrl = server.url("/"),
                        clock = clock,
                    ),
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun validationPasswordToggleErrorsAnd202OpensPendingEmailCard() {
        val clock = MutableTestClock(Instant.parse("2026-04-20T10:00:00Z"))
        val releaseSuccess = CountDownLatch(1)
        server.respondDynamicSequence(
            "POST",
            "/api/v1/me/email/change",
            listOf(
                { ContractFakeServer.apiFixtureResponse(409, "me-email-change-409-email-taken.json") },
                { ContractFakeServer.apiFixtureResponse(400, "me-email-change-400-wrong-old-password.json") },
                { ContractFakeServer.apiFixtureResponse(400, "me-email-change-400-new-email.json") },
                { ContractFakeServer.apiFixtureResponse(429, "me-email-change-429-login-throttled.json") },
                {
                    ContractFakeServer.apiFixtureResponse(429, "me-email-change-429-rate-limited.json") {
                        it.replace("\"retryAfter\" : 60,", "")
                    }
                },
                { MockResponse().setResponseCode(500) },
                {
                    releaseSuccess.await(5, TimeUnit.SECONDS)
                    MockResponse().setResponseCode(202)
                },
            ),
        )

        mountProfile(clock)

        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_FORM).performScrollTo().assertIsDisplayed()

        val initialDesc = composeRule.onNodeWithTag(ProfileTestTags.CURRENT_PASSWORD_TOGGLE)
            .performScrollTo()
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
        assertTrue(initialDesc.any { it.contains("Hiện mật khẩu") })
        composeRule.onNodeWithTag(ProfileTestTags.CURRENT_PASSWORD_TOGGLE).performClick()
        val toggledDesc = composeRule.onNodeWithTag(ProfileTestTags.CURRENT_PASSWORD_TOGGLE)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
        assertTrue(toggledDesc.any { it.contains("Ẩn mật khẩu") })

        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_SEND).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_ERROR).performScrollTo().assertTextContains("Vui lòng nhập email mới.")
        composeRule.onNodeWithTag(ProfileTestTags.CURRENT_PASSWORD_ERROR).performScrollTo().assertTextContains("Vui lòng nhập mật khẩu hiện tại.")

        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().performTextInput("bad-email")
        composeRule.onNodeWithTag(ProfileTestTags.CURRENT_PASSWORD_FIELD).performScrollTo().performTextInput("SecretPass1!")
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_SEND).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_ERROR).performScrollTo().assertTextContains("Email chưa đúng định dạng.")
        assertEquals(0, server.requestCount("POST", "/api/v1/me/email/change"))

        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performTextInput("student.new@thi360.vn")

        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_SEND).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_ERROR).performScrollTo().assertTextContains("tài khoản khác", substring = true)
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_SEND).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.CURRENT_PASSWORD_ERROR).performScrollTo().assertTextContains("không đúng", substring = true)
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_SEND).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_ERROR).performScrollTo().assertTextContains("chưa đúng định dạng", substring = true)
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_SEND).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_ALERT).performScrollTo().assertTextContains("quá nhanh", substring = true)
                true
            }.getOrDefault(false)
        }
        clock.advanceSeconds(65L)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_COOLDOWN).assertDoesNotExist()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_SEND).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_COOLDOWN).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        clock.advanceSeconds(65L)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_COOLDOWN).assertDoesNotExist()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_SEND).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_ALERT).performScrollTo().assertTextContains("Không gửi được mã xác minh", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().assertTextContains("student.new@thi360.vn")

        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_SEND).performScrollTo().performClick()
        runCatching { composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_SEND).performClick() }
        releaseSuccess.countDown()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.PENDING_EMAIL_CARD).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_FORM).assertDoesNotExist()
        composeRule.onNodeWithTag(ProfileTestTags.PENDING_EMAIL_TITLE).performScrollTo().assertTextContains("student.new@thi360.vn", substring = true)
        composeRule.onNodeWithTag(ProfileTestTags.PENDING_COOLDOWN).performScrollTo().assertIsDisplayed()
        assertEquals(7, server.requestCount("POST", "/api/v1/me/email/change"))
    }
}
