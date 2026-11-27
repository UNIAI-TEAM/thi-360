package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
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
import vn.unicom.thi360.core.ui.LoadStateTestTags
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class DeleteAccountScreenTest {

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

    private fun mountDeleteScreen() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
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
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun unusedAttempts3ShowsWarningAndEnablesSubmitOnlyWithPasswordAndAcknowledge() {
        server.respondApiFixture(
            "GET",
            "/api/v1/me/account/deletion-check",
            200,
            "me-account-deletion-check-200-unused-attempts.json",
        )
        mountDeleteScreen()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.EXPLANATION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.UNUSED_ATTEMPTS_WARNING)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("3 lượt thi", substring = true)
        composeRule.onNodeWithTag(DeleteAccountTestTags.PENDING_GRADING_WARNING).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithTag(DeleteAccountTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("MyPassword1!")
        composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithTag(DeleteAccountTestTags.ACKNOWLEDGE_ROW).performScrollTo().performClick()
        composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).performScrollTo().assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun unusedAttempts1AndPendingGradingAndZeroWarnings() {
        server.respondApiFixture(
            "GET",
            "/api/v1/me/account/deletion-check",
            200,
            "me-account-deletion-check-200-pending-grading.json",
        ) {
            it.replace("\"unusedAttempts\" : 0", "\"unusedAttempts\" : 1")
        }
        mountDeleteScreen()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.PENDING_GRADING_WARNING).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.UNUSED_ATTEMPTS_WARNING)
            .performScrollTo()
            .assertTextContains("1 lượt thi", substring = true)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun check500AndOfflineRenderRetryAndRecoverToCleanReadyState() {
        server.respondSequence(
            "GET",
            "/api/v1/me/account/deletion-check",
            { MockResponse().setResponseCode(500) },
            { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START) },
            { ContractFakeServer.apiFixtureResponse(200, "me-account-deletion-check-200.json") },
        )
        mountDeleteScreen()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoadStateTestTags.ERROR_CONTAINER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE)
                    .assertTextContains("Không có kết nối", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(DeleteAccountTestTags.SUBMIT_BUTTON).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(DeleteAccountTestTags.UNUSED_ATTEMPTS_WARNING).assertDoesNotExist()
        composeRule.onNodeWithTag(DeleteAccountTestTags.PENDING_GRADING_WARNING).assertDoesNotExist()
    }
}
