package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
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
class ResendCodeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-update-200.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun cooldownJumpsWithWallClockAnnouncesOnceAtStartAndOnceAtEndAndHandlesResend429() {
        server.respondSequence(
            "POST",
            "/api/v1/me/email/verification",
            { okhttp3.mockwebserver.MockResponse().setResponseCode(202) },
            { ContractFakeServer.apiFixtureResponse(429, "me-email-verification-429-rate-limited.json") },
        )

        val clock = MutableTestClock(Instant.parse("2026-04-20T10:00:00Z"))
        val announcements = mutableListOf<String>()
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
                VerifyEmailScreen(
                    onBackToProfile = {},
                    dependencies = ProfileDependencies(
                        profileStore = store,
                        profileGateway = gateway,
                        accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                        sessionManager = stack.sessionManager,
                        imageLoader = SingletonImageLoader.get(ApplicationProvider.getApplicationContext()),
                        apiBaseUrl = server.url("/"),
                        clock = clock,
                        onAnnounceCooldown = { announcements += it },
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            announcements.size == 1 && runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.RESEND_COOLDOWN).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(VerifyEmailTestTags.RESEND_BUTTON).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(VerifyEmailTestTags.COOLDOWN_LIVE_REGION)
            .assertContentDescriptionContains("01:00", substring = true)

        clock.advanceSeconds(30L)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.RESEND_COOLDOWN)
                    .performScrollTo()
                    .assertTextContains("00:30", substring = true)
                true
            }.getOrDefault(false)
        }
        assertEquals("Countdown ticks must not re-announce per second", 1, announcements.size)

        clock.advanceSeconds(35L)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            announcements.size == 2 && runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.RESEND_BUTTON).performScrollTo().assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        assertEquals("Bạn có thể gửi lại mã bây giờ.", announcements[1])

        composeRule.onNodeWithTag(VerifyEmailTestTags.RESEND_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            announcements.size == 3 && runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.RESEND_COOLDOWN).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(2, server.requestCount("POST", "/api/v1/me/email/verification"))
    }
}
