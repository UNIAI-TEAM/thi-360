package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.image.Thi360ImageLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class AvatarRemoveTest {

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
    fun removeButtonVisibilityErrorRetentionAndSingleFlightSuccessPropagatesToCard() {
        val avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"
        val releaseSuccessDelete = CountDownLatch(1)
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200-avatar-pending-email.json")
        server.respond("GET", avatarPath) { pngMockResponse() }
        server.respondDynamicSequence(
            "DELETE",
            "/api/v1/me/avatar",
            listOf(
                { MockResponse().setResponseCode(500) },
                {
                    releaseSuccessDelete.await(5, TimeUnit.SECONDS)
                    MockResponse().setResponseCode(204)
                },
            ),
        )

        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val loader = Thi360ImageLoader.create(context, server.url("/"), stack)

        try {
            composeRule.setContent {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                    startDestination = Destination.Account.route,
                    profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/")),
                    imageLoader = loader.imageLoader,
                    apiBaseUrl = server.url("/"),
                )
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_IMAGE).assertIsDisplayed()
                    composeRule.onNodeWithTag(ProfileTestTags.AVATAR_REMOVE_BUTTON).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            composeRule.onNodeWithTag(ProfileTestTags.AVATAR_REMOVE_BUTTON).performScrollTo().performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(ProfileTestTags.AVATAR_ERROR)
                        .performScrollTo()
                        .assertTextContains("Không cập nhật được ảnh đại diện", substring = true)
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_IMAGE).assertIsDisplayed()

            composeRule.onNodeWithTag(ProfileTestTags.AVATAR_REMOVE_BUTTON).performScrollTo().performClick()
            runCatching { composeRule.onNodeWithTag(ProfileTestTags.AVATAR_REMOVE_BUTTON).performClick() }
            releaseSuccessDelete.countDown()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(ProfileTestTags.SNACKBAR)
                        .assertTextContains("Đã xóa ảnh đại diện.")
                    composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_INITIALS).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            composeRule.onNodeWithTag(ProfileTestTags.AVATAR_REMOVE_BUTTON).assertDoesNotExist()
            composeRule.onNodeWithTag(ProfileTestTags.AVATAR_INITIALS).assertIsDisplayed()
            assertEquals("Double-tap remove must only send 1 extra DELETE", 2, server.requestCount("DELETE", "/api/v1/me/avatar"))
        } finally {
            loader.close()
        }
    }
}
