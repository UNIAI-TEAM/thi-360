package vn.unicom.thi360.feature.account

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class ProfileFoldTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
    @Config(qualifiers = "w900dp-h720dp")
    fun twoPaneToSinglePaneAndBackViaQualifiersAndRecreatePreservesProfileDrafts() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val profileStore = AccountProfileStore(profileGateway)
        val deletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/"))

        fun mountAppRoot() {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    AppRoot(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        onSelectLocale = {},
                        onSelectThemeMode = {},
                        sessionManager = stack.sessionManager,
                        validateOnLaunch = false,
                        startDestination = Destination.Account.route,
                        profileGateway = profileGateway,
                        profileStore = profileStore,
                        accountDeletionGateway = deletionGateway,
                        apiBaseUrl = server.url("/"),
                    )
                }
            }
        }

        mountAppRoot()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_TWO_PANE).assertIsDisplayed()
                composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("Nguyễn Draft Tên")
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performScrollTo().performTextInput("0912345678")
        composeRule.onNodeWithTag(ProfileTestTags.CHANGE_EMAIL_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().performTextInput("draft@thi360.vn")

        RuntimeEnvironment.setQualifiers("w390dp-h844dp")
        composeRule.activityRule.scenario.recreate()
        mountAppRoot()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.BACK_BUTTON).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Draft Tên")
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performScrollTo().assertTextContains("0912345678")
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().assertTextContains("draft@thi360.vn")

        RuntimeEnvironment.setQualifiers("w900dp-h720dp")
        composeRule.activityRule.scenario.recreate()
        mountAppRoot()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ShellTestTags.ACCOUNT_TWO_PANE).assertIsDisplayed()
                composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Draft Tên")
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performScrollTo().assertTextContains("0912345678")
        composeRule.onNodeWithTag(ProfileTestTags.NEW_EMAIL_FIELD).performScrollTo().assertTextContains("draft@thi360.vn")
    }

    @Test
    @Config(qualifiers = "w900dp-h720dp")
    fun verifyEmailOpenedFromTwoPaneSurvivesFoldAndUnfoldWithoutDuplicateAutoSend() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-update-200.json")
        server.respondEmpty("POST", "/api/v1/me/email/verification", 202)
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val profileStore = AccountProfileStore(profileGateway)
        val deletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/"))

        fun mountAppRoot() {
            composeRule.activityRule.scenario.onActivity { activity ->
                activity.setContent {
                    AppRoot(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        onSelectLocale = {},
                        onSelectThemeMode = {},
                        sessionManager = stack.sessionManager,
                        validateOnLaunch = false,
                        startDestination = Destination.Account.route,
                        profileGateway = profileGateway,
                        profileStore = profileStore,
                        accountDeletionGateway = deletionGateway,
                        apiBaseUrl = server.url("/"),
                    )
                }
            }
        }

        mountAppRoot()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.VERIFY_EMAIL_BUTTON).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(ProfileTestTags.VERIFY_EMAIL_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            server.requestCount("POST", "/api/v1/me/email/verification") == 1 && runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        RuntimeEnvironment.setQualifiers("w390dp-h844dp")
        composeRule.activityRule.scenario.recreate()
        mountAppRoot()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("POST", "/api/v1/me/email/verification"))

        RuntimeEnvironment.setQualifiers("w900dp-h720dp")
        composeRule.activityRule.scenario.recreate()
        mountAppRoot()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.SCREEN).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("POST", "/api/v1/me/email/verification"))
    }
}
