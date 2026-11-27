package vn.unicom.thi360.feature.account

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class AvatarImageTest {

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
    fun nullAvatarRendersInitialsWithCurrentEmptyLabel() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
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
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_INITIALS).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_INITIALS).assertTextContains("VA")
        val desc = composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_INITIALS)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
        assertTrue(desc.any { it.contains("Chưa có ảnh đại diện") })
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun nonNullAvatarIssuesSingleBearerGetAndExposesCurrentLabel() {
        val avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200-avatar-pending-email.json")
        server.respond("GET", avatarPath) { pngMockResponse() }

        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens(accessToken = "avatar-bearer-token")),
            localeProvider = { AppLocale.VI },
        )
        val loader = Thi360ImageLoader.create(
            context = context,
            apiBaseUrl = server.url("/"),
            sessionStack = stack,
        )

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
                    composeRule.onNodeWithTag(ProfileTestTags.AVATAR_IMAGE).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            val avatarRequests = server.requests("GET", avatarPath)
            assertEquals("Simultaneous Account card + Profile must issue one avatar GET", 1, avatarRequests.size)
            assertEquals("Bearer avatar-bearer-token", avatarRequests.single().getHeader("Authorization"))

            val desc = composeRule.onNodeWithTag(ProfileTestTags.AVATAR_IMAGE)
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
            assertTrue(desc.any { it.contains("Ảnh đại diện hiện tại") })
        } finally {
            loader.close()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun avatar404AndOfflineStayOnInitialsWithNoErrorAndRetryOnceOnReopen() {
        val avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200-avatar-pending-email.json")
        server.respond("GET", avatarPath) { MockResponse().setResponseCode(404) }

        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val loader = Thi360ImageLoader.create(
            context = context,
            apiBaseUrl = server.url("/"),
            sessionStack = stack,
        )

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
                server.requestCount("GET", avatarPath) >= 1
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_INITIALS).assertIsDisplayed()
            composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_IMAGE).assertDoesNotExist()
            val afterFirstFailure = server.requestCount("GET", avatarPath)

            server.respond("GET", avatarPath) { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START) }
            composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                server.requestCount("GET", avatarPath) > afterFirstFailure
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(ProfileTestTags.AVATAR_INITIALS).assertIsDisplayed()
            composeRule.onNodeWithTag(ProfileTestTags.AVATAR_ERROR).assertDoesNotExist()
            val afterOfflineFailure = server.requestCount("GET", avatarPath)

            server.respond("GET", avatarPath) { pngMockResponse() }
            composeRule.onNodeWithTag(ProfileTestTags.BACK_BUTTON).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_IMAGE).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            assertEquals(afterOfflineFailure + 1, server.requestCount("GET", avatarPath))
        } finally {
            loader.close()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun avatar401WithFailedRefreshTransitionsSessionToSignedOut() {
        val avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200-avatar-pending-email.json")
        server.respondApiFixture("GET", avatarPath, 401, "me-401-session-revoked.json")
        server.respondApiFixture("POST", "/api/v1/auth/mobile/refresh", 401, "me-401-session-revoked.json")

        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val loader = Thi360ImageLoader.create(
            context = context,
            apiBaseUrl = server.url("/"),
            sessionStack = stack,
        )

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
                stack.sessionManager.state.value is SessionState.SignedOut
            }
        } finally {
            loader.close()
        }
    }
}
