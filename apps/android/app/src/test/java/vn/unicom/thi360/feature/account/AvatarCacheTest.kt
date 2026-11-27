package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class AvatarCacheTest {

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
    fun sameAssetReusesMemoryCacheAndLogoutClearsCacheSoUserBNeverSeesUserA() {
        val avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"
        server.respondSequence(
            "GET",
            "/api/v1/me/profile",
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-200-avatar-pending-email.json") },
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-200-avatar-pending-email.json") },
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-200-no-email.json") },
        )
        server.respond("GET", avatarPath) { pngMockResponse() }
        server.respondEmpty("POST", "/api/v1/auth/mobile/logout", 204)
        server.respondAuthFixture("POST", "/api/v1/auth/mobile/login", 200, "login-200.json") {
            it.replace("student.demo", "student.noemail")
        }

        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val rootDir = File(context.filesDir, "avatar_cache_test_${System.nanoTime()}").apply { mkdirs() }
        val cleanup = SessionCleanup(rootDir)
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
            sessionCleanup = cleanup,
        )
        val loader = Thi360ImageLoader.create(
            context = context,
            apiBaseUrl = server.url("/"),
            sessionStack = stack,
            sessionCleanup = cleanup,
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
                    sessionCleanup = cleanup,
                )
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_IMAGE).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            assertEquals(1, server.requestCount("GET", avatarPath))
            assertTrue((loader.imageLoader.memoryCache?.size ?: 0L) > 0L)
            assertNull("Avatar ImageLoader must never enable disk cache", loader.imageLoader.diskCache)

            composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(ProfileTestTags.AVATAR_IMAGE).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            assertEquals("Same avatarAssetId must reuse memory cache without extra GET", 1, server.requestCount("GET", avatarPath))

            composeRule.onNodeWithTag(ProfileTestTags.BACK_BUTTON).performClick()
            composeRule.onNodeWithTag(AccountTestTags.LOGOUT_BUTTON).performScrollTo().performClick()
            composeRule.onNodeWithTag(AccountTestTags.LOGOUT_CONFIRM_BUTTON).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                stack.sessionManager.state.value is SessionState.SignedOut
            }
            composeRule.waitForIdle()
            assertEquals("Logout must clear memory cache", 0L, loader.imageLoader.memoryCache?.size ?: 0L)

            composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("student.noemail")
            composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("Secret123!")
            composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                stack.sessionManager.state.value is SessionState.Active
            }
            composeRule.onNodeWithTag(ShellTestTags.navItem(Destination.Account)).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_INITIALS).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }
            composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_IMAGE).assertDoesNotExist()
            assertEquals(1, server.requestCount("GET", avatarPath))
        } finally {
            loader.close()
            rootDir.deleteRecursively()
        }
    }
}
