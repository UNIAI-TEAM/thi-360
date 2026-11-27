package vn.unicom.thi360.feature.account

import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.awaitCancellation
import okhttp3.mockwebserver.MockResponse
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
import vn.unicom.thi360.core.ui.LoadStateTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class VerifyEmailCodeTest {

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
    fun invalidExpiredGenericErrorsRetainCodeAnd204ReturnsToVerifiedProfile() {
        server.respondSequence(
            "GET",
            "/api/v1/me/profile",
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-update-200.json") },
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-update-200.json") },
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-update-200.json") },
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-200.json") },
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-200.json") },
        )
        server.respondEmpty("POST", "/api/v1/me/email/verification", 202)
        server.respondSequence(
            "POST",
            "/api/v1/me/email/verify",
            { ContractFakeServer.apiFixtureResponse(422, "me-email-verify-422-email-code-invalid.json") },
            { ContractFakeServer.apiFixtureResponse(422, "me-email-verify-422-email-code-expired.json") },
            { MockResponse().setResponseCode(500) },
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

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(AccountTestTags.PROFILE_CARD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_ROW).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.VERIFY_EMAIL_BUTTON).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.VERIFY_EMAIL_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_FIELD).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_FIELD).performScrollTo().performTextInput("123")
        composeRule.onNodeWithTag(VerifyEmailTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_ERROR)
            .performScrollTo()
            .assertTextContains("Vui lòng nhập mã gồm 6 chữ số.")
        assertEquals(0, server.requestCount("POST", "/api/v1/me/email/verify"))

        composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_FIELD).performTextInput("111111")
        composeRule.onNodeWithTag(VerifyEmailTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_ERROR)
                    .performScrollTo()
                    .assertTextContains("còn 4 lần thử", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_FIELD).performScrollTo().assertTextContains("111111")

        composeRule.onNodeWithTag(VerifyEmailTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.ALERT)
                    .performScrollTo()
                    .assertTextContains("không còn dùng được", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_FIELD).performScrollTo().assertTextContains("111111")

        composeRule.onNodeWithTag(VerifyEmailTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.ALERT)
                    .performScrollTo()
                    .assertTextContains("Không xác minh được email", substring = true)
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(VerifyEmailTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.EMAIL_BADGE)
                    .performScrollTo()
                    .assertTextContains("Đã xác minh")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.UNVERIFIED_BANNER).assertDoesNotExist()
        composeRule.onNodeWithTag(ProfileTestTags.BACK_BUTTON).performClick()
        composeRule.onNodeWithTag(AccountTestTags.PROFILE_EMAIL_BADGE).assertTextContains("Email đã xác minh")
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun codeFieldUsesNumericKeyboardAndLoadErrorLaterBackAndFootnoteReturnToProfile() {
        server.respondSequence(
            "GET",
            "/api/v1/me/profile",
            { MockResponse().setResponseCode(500) },
            { ContractFakeServer.apiFixtureResponse(200, "me-profile-update-200.json") },
        )
        server.respondEmpty("POST", "/api/v1/me/email/verification", 202)
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val store = AccountProfileStore(gateway)
        var backedOut = 0
        var capturedInputType: Int? = null

        composeRule.setContent {
            vn.unicom.thi360.core.theme.Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                InterceptPlatformTextInput(
                    interceptor = { request, _ ->
                        val info = EditorInfo()
                        request.createInputConnection(info)
                        capturedInputType = info.inputType
                        awaitCancellation()
                    },
                ) {
                    VerifyEmailScreen(
                        onBackToProfile = { backedOut++ },
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
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(LoadStateTestTags.ERROR_CONTAINER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithText("Không tải được màn hình").assertIsDisplayed()
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_FIELD).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_FIELD).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { capturedInputType != null }
        assertEquals(
            "Verification code field must request numeric keyboard (TYPE_CLASS_NUMBER)",
            InputType.TYPE_CLASS_NUMBER,
            capturedInputType!! and InputType.TYPE_MASK_CLASS,
        )

        composeRule.onNodeWithTag(VerifyEmailTestTags.LATER_BUTTON).performScrollTo().performClick()
        assertEquals(1, backedOut)

        composeRule.onNodeWithTag(VerifyEmailTestTags.BACK_BUTTON).performClick()
        assertEquals(2, backedOut)

        composeRule.onNodeWithTag(VerifyEmailTestTags.FOOTNOTE).performScrollTo().performFirstLinkClick()
        assertEquals(3, backedOut)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun alreadyVerifiedScreenHidesCodeControlsAndContinueReturnsToProfile() {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val store = AccountProfileStore(gateway)
        var continued = 0

        composeRule.setContent {
            vn.unicom.thi360.core.theme.Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                VerifyEmailScreen(
                    onBackToProfile = { continued++ },
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

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onNodeWithTag(VerifyEmailTestTags.ALREADY_VERIFIED_BANNER).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(VerifyEmailTestTags.CODE_FIELD).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyEmailTestTags.SUBMIT_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyEmailTestTags.RESEND_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyEmailTestTags.LATER_BUTTON).assertDoesNotExist()
        composeRule.onNodeWithTag(VerifyEmailTestTags.CONTINUE_BUTTON).performClick()
        assertEquals(1, continued)
    }
}
