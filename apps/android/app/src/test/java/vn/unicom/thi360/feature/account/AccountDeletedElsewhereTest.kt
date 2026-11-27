package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class AccountDeletedElsewhereTest {

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
    fun api401FollowedByRefresh401WipesSessionProfileAndAvatarCacheAndReturnsToLogin() {
        val avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"
        server.respondSequence(
            "GET",
            "/api/v1/me/profile",
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-200-avatar-pending-email.json") },
            { ContractFakeServer.apiFixtureResponse(401, "me-401-session-revoked.json") },
        )
        server.respond("GET", avatarPath) { pngMockResponse() }
        server.respondApiFixture("POST", "/api/v1/auth/mobile/refresh", 401, "me-401-session-revoked.json")

        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val rootDir = File(context.filesDir, "deleted_elsewhere_test_${System.nanoTime()}").apply { mkdirs() }
        val cleanup = SessionCleanup(rootDir)
        val tokenStore = InMemoryTokenStore(testSignedInTokens())
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            sessionCleanup = cleanup,
        )
        val loader = Thi360ImageLoader.create(context, server.url("/"), stack, cleanup)
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val profileStore = AccountProfileStore(gateway, sessionCleanup = cleanup)

        try {
            composeRule.setContent {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = stack.sessionManager,
                    validateOnLaunch = false,
                    startDestination = Destination.Account.route,
                    profileGateway = gateway,
                    profileStore = profileStore,
                    imageLoader = loader.imageLoader,
                    apiBaseUrl = server.url("/"),
                    sessionCleanup = cleanup,
                )
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_IMAGE).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            assertTrue((loader.imageLoader.memoryCache?.size ?: 0L) > 0L)

            composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performScrollTo().performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                stack.sessionManager.state.value is SessionState.SignedOut
            }
            composeRule.waitForIdle()

            assertNull(tokenStore.read())
            assertNull(profileStore.currentProfileOrNull())
            assertEquals(0L, loader.imageLoader.memoryCache?.size ?: 0L)
            composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        } finally {
            profileStore.close()
            loader.close()
            rootDir.deleteRecursively()
        }
    }
}
