package vn.unicom.thi360.feature.recovery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import okhttp3.mockwebserver.MockWebServer
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
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.shell.AppRoot

@RunWith(RobolectricTestRunner::class)
class ForgotPasswordScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun identicalSentScreenForAnyValidEmailAndSixtySecondResendCooldown() {
        var fakeClockMillis by mutableLongStateOf(2_000_000L)
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        val forgotBody = ContractFixtureLoader.readApiFixture("password-forgot-202.json")
        server.enqueue(ContractFixtureLoader.jsonResponse(202, forgotBody))
        server.enqueue(ContractFixtureLoader.jsonResponse(202, forgotBody))
        server.enqueue(ContractFixtureLoader.jsonResponse(202, forgotBody))

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                recoveryGateway = gateway,
                clockMillis = { fakeClockMillis },
            )
        }

        // Open Forgot Password from Login screen.
        composeRule.onNodeWithTag(LoginTestTags.FORGOT_PASSWORD_LINK).performScrollTo().performClick()
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SCREEN_FORM).assertIsDisplayed()

        // Blank and invalid email fail client-side with zero requests.
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SUBMIT_BUTTON).performClick()
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_EMAIL_ERROR, useUnmergedTree = true)
            .assertTextContains("Vui lòng nhập email.")
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_EMAIL_FIELD).performTextInput("bad-email")
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SUBMIT_BUTTON).performClick()
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_EMAIL_ERROR, useUnmergedTree = true)
            .assertTextContains("Email chưa đúng định dạng.")
        assertEquals(0, server.requestCount)

        // Submit existing email -> identical "Hãy kiểm tra hộp thư" screen + 60s cooldown.
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_EMAIL_FIELD).performTextInput("@thi360.vn")
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SCREEN_SENT).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SENT_TITLE)
            .assertTextContains("Hãy kiểm tra hộp thư")
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SENT_MESSAGE)
            .assertTextContains("Nếu email này đủ điều kiện khôi phục tài khoản", substring = true)
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_RESEND_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_RESEND_COUNTDOWN)
            .assertTextContains("01:00", substring = true)

        // Advance 60s -> resend enabled; clicking keeps identical content & resets 60s timer.
        composeRule.runOnIdle { fakeClockMillis += 60_000L }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_RESEND_BUTTON).assertIsEnabled().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { server.requestCount == 2 }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SENT_TITLE)
            .assertTextContains("Hãy kiểm tra hộp thư")
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_RESEND_BUTTON).assertIsNotEnabled()

        // Navigate back to Login and open Forgot Password with a nonexistent email -> exact same screen.
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_BACK_TO_LOGIN_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(LoginTestTags.FORGOT_PASSWORD_LINK).performScrollTo().performClick()
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_EMAIL_FIELD).performTextInput("nobody@unknown.example")
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SCREEN_SENT).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SENT_TITLE)
            .assertTextContains("Hãy kiểm tra hộp thư")
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SENT_MESSAGE)
            .assertTextContains("Nếu email này đủ điều kiện khôi phục tài khoản", substring = true)
        assertEquals(3, server.requestCount)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun submitsOnceWhileInFlight() {
        val release = CompletableDeferred<ApiResult<Unit>>()
        var calls = 0
        val gatedGateway = object : RecoveryGateway {
            override suspend fun startRegistration(
                fullName: String,
                username: String,
                email: String,
                password: String,
                termsVersion: String,
                registrationId: UUID?,
            ): ApiResult<RegistrationStartData> = error("unused")

            override suspend fun resendRegistrationCode(registrationId: UUID): ApiResult<RegistrationStartData> = error("unused")
            override suspend fun verifyRegistration(registrationId: UUID, code: String, username: String?): ApiResult<Tokens> = error("unused")

            override suspend fun forgotPassword(email: String): ApiResult<Unit> {
                calls++
                return release.await()
            }

            override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> = error("unused")
            override suspend fun changePassword(oldPassword: String, newPassword: String): ApiResult<Unit> = error("unused")
        }

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ForgotPasswordScreen(
                    recoveryGateway = gatedGateway,
                    onBackToLogin = {},
                )
            }
        }

        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_EMAIL_FIELD).performTextInput("student@thi360.vn")
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SUBMIT_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) { calls == 1 }
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SUBMIT_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SUBMIT_PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SUBMIT_BUTTON).performClick()
        assertEquals(1, calls)

        release.complete(ApiResult.Success(Unit))
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                composeRule.onNodeWithTag(RecoveryTestTags.FORGOT_SCREEN_SENT).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, calls)
    }
}
