package vn.unicom.thi360.feature.account

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LoadStateTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class ProfileScreenTest {

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
    fun inFlightRefreshIsDiscardedWhenReplaceFinishesFirst() = runTest {
        val gate = CompletableDeferred<ApiResult<UserProfile>>()
        val staleProfile = UserProfile("student.demo", "Stale Name", "stale@thi360.vn", false)
        val freshProfile = UserProfile("student.demo", "Fresh Name", "fresh@thi360.vn", true)
        val gateway = object : ProfileGateway {
            override suspend fun fetch(): ApiResult<UserProfile> = gate.await()
            override suspend fun update(fullName: String, phone: String?) = error("unused")
            override suspend fun uploadAvatar(avatar: PreparedAvatar) = error("unused")
            override suspend fun removeAvatar() = error("unused")
            override suspend fun sendVerification() = error("unused")
            override suspend fun verify(code: String) = error("unused")
            override suspend fun requestEmailChange(newEmail: String, currentPassword: String) = error("unused")
            override suspend fun confirmEmailChange(code: String) = error("unused")
        }
        val store = AccountProfileStore(gateway)

        val refreshJob = async { store.refresh() }
        testScheduler.advanceUntilIdle()
        store.replace(freshProfile)
        gate.complete(ApiResult.Success(staleProfile))
        refreshJob.await()

        assertEquals(LoadState.Loaded(freshProfile, stale = false), store.state.value)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun cachedReopenRefreshesInBackgroundKeepsDataOnErrorAndSupportsFirstOpenFailures() {
        server.respondSequence(
            "GET",
            "/api/v1/me/profile",
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-200.json") },
            { MockResponse().setResponseCode(500) },
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-update-200.json") },
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
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_CARD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.REFRESH_ERROR_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Văn An")

        composeRule.onNodeWithTag(ProfileTestTags.REFRESH_RETRY_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Minh Anh")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.REFRESH_ERROR_BANNER).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun firstOpenHttpErrorAndOfflineRenderLoadStateViewWithRetry() {
        server.respond("GET", "/api/v1/me/profile") { MockResponse().setResponseCode(500) }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val store = AccountProfileStore(gateway)

        composeRule.setContent {
            vn.unicom.thi360.core.theme.Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ProfileScreen(
                    dependencies = ProfileDependencies(
                        profileStore = store,
                        profileGateway = gateway,
                        accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                        sessionManager = stack.sessionManager,
                        imageLoader = coil3.SingletonImageLoader.get(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
                        apiBaseUrl = server.url("/"),
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoadStateTestTags.ERROR_CONTAINER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Không tải được hồ sơ").assertIsDisplayed()

        server.respond("GET", "/api/v1/me/profile") { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE)
                    .assertTextContains("Không có kết nối", substring = true)
                true
            }.getOrDefault(false)
        }

        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.INFO_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun contentBlocks() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val store = AccountProfileStore(gateway)

        composeRule.setContent {
            vn.unicom.thi360.core.theme.Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ProfileScreen(
                    dependencies = ProfileDependencies(
                        profileStore = store,
                        profileGateway = gateway,
                        accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                        sessionManager = stack.sessionManager,
                        imageLoader = coil3.SingletonImageLoader.get(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
                        apiBaseUrl = server.url("/"),
                    ),
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.INTRO).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_SECTION).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.INFO_SECTION).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.EMAIL_SECTION).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Đổi mật khẩu").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun emailBlockVariants() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val unverifiedProfile = UserProfile(
            username = "student.demo",
            fullName = "Nguyễn Văn An",
            email = "unverified@thi360.vn",
            emailVerified = false,
        )
        val verifiedProfile = unverifiedProfile.copy(emailVerified = true)
        val noEmailProfile = unverifiedProfile.copy(email = null, emailVerified = false)
        var currentVariant = unverifiedProfile
        val variantGateway = object : ProfileGateway by gateway {
            override suspend fun fetch(): ApiResult<UserProfile> = ApiResult.Success(currentVariant)
        }
        val store = AccountProfileStore(variantGateway).apply { replace(unverifiedProfile) }

        composeRule.setContent {
            vn.unicom.thi360.core.theme.Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ProfileScreen(
                    dependencies = ProfileDependencies(
                        profileStore = store,
                        profileGateway = variantGateway,
                        accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                        sessionManager = stack.sessionManager,
                        imageLoader = coil3.SingletonImageLoader.get(androidx.test.core.app.ApplicationProvider.getApplicationContext()),
                        apiBaseUrl = server.url("/"),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(ProfileTestTags.UNVERIFIED_BANNER).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.EMAIL_BADGE).performScrollTo().assertTextContains("Chưa xác minh")
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE).performScrollTo().assertTextContains("Đổi email")

        composeRule.runOnIdle {
            currentVariant = verifiedProfile
            store.replace(verifiedProfile)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProfileTestTags.UNVERIFIED_BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(ProfileTestTags.EMAIL_BADGE).performScrollTo().assertTextContains("Đã xác minh")
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE).performScrollTo().assertTextContains("Đổi email")

        composeRule.runOnIdle {
            currentVariant = noEmailProfile
            store.replace(noEmailProfile)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProfileTestTags.UNVERIFIED_BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(ProfileTestTags.EMAIL_NONE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE).performScrollTo().assertTextContains("Đổi email")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun systemBackDiscardsUnsavedDraftsAndClearsChangeEmailPasswordOnReentry() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        var backDispatcher: OnBackPressedDispatcher? = null

        composeRule.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
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

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_FULL_NAME).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("Abandoned Draft Name")
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().performTextInput("abandoned@thi360.vn")
        composeRule.onNodeWithTag(ProfileTestTags.CURRENT_PASSWORD_FIELD).performScrollTo().performTextInput("TempPass1!")

        composeRule.runOnIdle { backDispatcher?.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Văn An")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_FORM).assertDoesNotExist()
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().assertTextContains("")
        composeRule.onNodeWithTag(ProfileTestTags.CURRENT_PASSWORD_FIELD).performScrollTo().assertTextContains("")
    }

    @Test
    @Config(qualifiers = "w900dp-h720dp")
    fun twoPaneSwitchAndTabSwitchDiscardUnsavedDraftsAndResetOnReentry() {
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

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("Pane Draft Name")
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.CURRENT_PASSWORD_FIELD).performScrollTo().performTextInput("SecretPane1!")

        composeRule.onNodeWithTag(AccountTestTags.CHANGE_PASSWORD_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Văn An")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_FORM).assertDoesNotExist()

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("Tab Draft Name")
        composeRule.onNodeWithTag(vn.unicom.thi360.shell.ShellTestTags.navItem(Destination.Home)).performClick()
        composeRule.onNodeWithTag(vn.unicom.thi360.shell.ShellTestTags.navItem(Destination.Account)).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Văn An")
                true
            }.getOrDefault(false)
        }
    }
}
