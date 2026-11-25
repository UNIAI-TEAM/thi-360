package vn.unicom.thi360.feature.register

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
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.RecoveryGateway
import vn.unicom.thi360.feature.recovery.RegisterTestTags
import vn.unicom.thi360.feature.recovery.RegistrationStartData
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.shell.ShellTestTags

@RunWith(RobolectricTestRunner::class)
class OtpScreenTest {

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

    private fun navigateToOtpStep() {
        composeRule.onNodeWithTag(LoginTestTags.REGISTER_LINK).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().performTextInput("Nguyễn Văn An")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("student.new")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().performTextInput("student.demo@thi360.vn")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("StudentPass1!")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.SCREEN_OTP).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun validOtpSignsInAndInvalidAndExpiredShowExpectedGuidance() {
        val tokenStore = InMemoryTokenStore()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readApiFixture("register-200.json"),
            ),
        )
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                422,
                ContractFixtureLoader.readAuthFixture("register-verify-422-email-code-invalid.json"),
            ),
        )
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                422,
                ContractFixtureLoader.readAuthFixture("register-verify-422-email-code-expired.json"),
            ),
        )
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                201,
                ContractFixtureLoader.readAuthFixture("register-verify-201.json"),
            ),
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                recoveryGateway = gateway,
            )
        }

        navigateToOtpStep()

        // Blank / incomplete OTP blocks submission without API call.
        composeRule.onNodeWithTag(RegisterTestTags.OTP_CODE_FIELD).performTextInput("123")
        composeRule.onNodeWithTag(RegisterTestTags.OTP_SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_CODE_ERROR)
            .assertTextContains("Vui lòng nhập mã gồm 6 chữ số.")
        assertEquals(1, server.requestCount)

        // Wrong 6-digit code -> remaining attempts message.
        composeRule.onNodeWithTag(RegisterTestTags.OTP_CODE_FIELD).performTextClearance()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_CODE_FIELD).performTextInput("000000")
        composeRule.onNodeWithTag(RegisterTestTags.OTP_SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.OTP_CODE_ERROR)
                    .assertTextContains("Mã chưa đúng. Bạn còn 4 lần thử.")
                true
            }.getOrDefault(false)
        }

        // Expired code -> resend guidance.
        composeRule.onNodeWithTag(RegisterTestTags.OTP_CODE_FIELD).performTextClearance()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_CODE_FIELD).performTextInput("111111")
        composeRule.onNodeWithTag(RegisterTestTags.OTP_SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.OTP_ERROR_BANNER)
                    .assertTextContains("Mã này không còn dùng được. Bấm Gửi lại mã để nhận mã mới.")
                true
            }.getOrDefault(false)
        }

        // Valid code -> adopts session and enters Home shell.
        composeRule.onNodeWithTag(RegisterTestTags.OTP_CODE_FIELD).performTextClearance()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_CODE_FIELD).performTextInput("407123")
        composeRule.onNodeWithTag(RegisterTestTags.OTP_SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ShellTestTags.screenTitle(Destination.Home)).assertIsDisplayed()
        assertNotNull(tokenStore.read())
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun resendCooldown() {
        var fakeClockMillis by mutableLongStateOf(1_000_000L)
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readApiFixture("register-200.json"),
            ),
        )
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readApiFixture("register-resend-200.json"),
            ),
        )
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                429,
                ContractFixtureLoader.readApiFixture("register-resend-429-rate-limited.json")
                    .replace("\"retryAfter\": 60", "\"retryAfter\": 90"),
            ),
        )

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

        navigateToOtpStep()

        // Locked immediately for resendAfterSeconds = 60.
        composeRule.onNodeWithTag(RegisterTestTags.OTP_RESEND_BUTTON).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_RESEND_COUNTDOWN)
            .performScrollTo()
            .assertTextContains("01:00", substring = true)

        // Advance 30s -> still locked at 00:30.
        composeRule.runOnIdle { fakeClockMillis += 30_000L }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_RESEND_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_RESEND_COUNTDOWN)
            .assertTextContains("00:30", substring = true)

        // Advance remaining 30s -> unlocked; click resend -> success notice & new cooldown.
        composeRule.runOnIdle { fakeClockMillis += 30_000L }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_RESEND_BUTTON).assertIsEnabled().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.OTP_RESENT_BANNER)
                    .performScrollTo()
                    .assertTextContains("Đã gửi mã mới. Mã cũ không còn dùng được.")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(RegisterTestTags.OTP_RESEND_BUTTON).performScrollTo().assertIsNotEnabled()

        // Wait out 60s -> hit 429 with retryAfter = 90 -> locks for 01:30.
        composeRule.runOnIdle { fakeClockMillis += 60_000L }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_RESEND_BUTTON).assertIsEnabled().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.OTP_RESEND_COUNTDOWN)
                    .performScrollTo()
                    .assertTextContains("01:30", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(RegisterTestTags.OTP_RESEND_BUTTON).assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun verifySubmitsOnceWhileInFlight() {
        val releaseVerify = CompletableDeferred<ApiResult<Tokens>>()
        var verifyCalls = 0
        val regId = UUID.fromString("019320a0-0000-7000-8000-000000000002")
        val gatedGateway = object : RecoveryGateway {
            override suspend fun startRegistration(
                fullName: String,
                username: String,
                email: String,
                password: String,
                termsVersion: String,
                registrationId: UUID?,
            ): ApiResult<RegistrationStartData> = ApiResult.Success(
                RegistrationStartData(regId, email, 60L),
            )

            override suspend fun resendRegistrationCode(registrationId: UUID): ApiResult<RegistrationStartData> = error("unused")

            override suspend fun verifyRegistration(
                registrationId: UUID,
                code: String,
                username: String?,
            ): ApiResult<Tokens> {
                verifyCalls++
                return releaseVerify.await()
            }

            override suspend fun forgotPassword(email: String): ApiResult<Unit> = error("unused")
            override suspend fun resetPassword(token: String, newPassword: String): ApiResult<Unit> = error("unused")
            override suspend fun changePassword(oldPassword: String, newPassword: String): ApiResult<Unit> = error("unused")
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                recoveryGateway = gatedGateway,
            )
        }

        navigateToOtpStep()

        composeRule.onNodeWithTag(RegisterTestTags.OTP_CODE_FIELD).performTextInput("123456")
        composeRule.onNodeWithTag(RegisterTestTags.OTP_SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) { verifyCalls == 1 }
        composeRule.onNodeWithTag(RegisterTestTags.OTP_SUBMIT_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(RegisterTestTags.OTP_SUBMIT_BUTTON).performClick()
        assertEquals(1, verifyCalls)

        releaseVerify.complete(
            ApiResult.Success(
                Tokens("a", "r", 300, 604800, false, "student.new"),
            ),
        )
        composeRule.waitUntil(timeoutMillis = 3_000) {
            stack.sessionManager.state.value is SessionState.Active
        }
        assertEquals(1, verifyCalls)
    }
}
