package vn.unicom.thi360.feature.register

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.PasswordTestTags
import vn.unicom.thi360.feature.recovery.RecoveryGateway
import vn.unicom.thi360.feature.recovery.RegisterTestTags
import vn.unicom.thi360.feature.recovery.RegistrationStartData

@RunWith(RobolectricTestRunner::class)
class RegisterScreenTest {

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
    fun validationShowsFieldErrorsAndUpdatesPasswordRulesLiveWithoutNetworkCall() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                RegistrationFlowScreen(
                    recoveryGateway = gateway,
                    sessionManager = stack.sessionManager,
                    onBackToLogin = {},
                )
            }
        }

        // Submit empty form -> all required field errors appear, zero API requests.
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_ERROR, useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("Vui lòng nhập họ và tên.")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_ERROR, useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("Vui lòng nhập tên đăng nhập.")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_ERROR, useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("Vui lòng nhập email.")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_ERROR, useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("Vui lòng nhập mật khẩu.")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_ERROR)
            .performScrollTo()
            .assertTextContains("Bạn cần đồng ý với Điều khoản sử dụng để đăng ký.")
        assertEquals(0, server.requestCount)

        // Invalid username pattern, invalid email, weak password -> live rule indicators update.
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().performTextInput("Trần Minh Anh")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("ab")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().performTextInput("khong-phai-email")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("Matkhau1")

        composeRule.onNodeWithTag(PasswordTestTags.RULE_LENGTH)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Đã đạt"))
        composeRule.onNodeWithTag(PasswordTestTags.RULE_UPPER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Đã đạt"))
        composeRule.onNodeWithTag(PasswordTestTags.RULE_LOWER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Đã đạt"))
        composeRule.onNodeWithTag(PasswordTestTags.RULE_DIGIT)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Đã đạt"))
        composeRule.onNodeWithTag(PasswordTestTags.RULE_SPECIAL)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Chưa đạt"))

        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_ERROR, useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("Tên đăng nhập chưa đúng quy tắc.", substring = true)
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_ERROR, useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("Email chưa đúng định dạng.")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_ERROR, useUnmergedTree = true)
            .performScrollTo()
            .assertTextContains("Mật khẩu chưa đáp ứng đủ các điều kiện.")
        assertEquals(0, server.requestCount)

        // Typing special character updates special rule immediately.
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("!")
        composeRule.onNodeWithTag(PasswordTestTags.RULE_SPECIAL)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Đã đạt"))

        // Terms of Use dialog opens cleanly.
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_VIEW_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_DIALOG).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun usernameTakenAndEmailTakenShowFieldErrorsAndPreserveInput() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                409,
                ContractFixtureLoader.readApiFixture("register-409-username-taken.json"),
            ),
        )
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                409,
                ContractFixtureLoader.readApiFixture("register-409-email-taken.json"),
            ),
        )

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                RegistrationFlowScreen(
                    recoveryGateway = gateway,
                    sessionManager = stack.sessionManager,
                    onBackToLogin = {},
                )
            }
        }

        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().performTextInput("Trần Minh Anh")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("minhanh.tran")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().performTextInput("minhanh@example.com")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("Matkhau1!")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.USERNAME_ERROR, useUnmergedTree = true)
                    .performScrollTo()
                    .assertTextContains("Tên đăng nhập này đã được sử dụng. Hãy chọn tên khác.")
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Trần Minh Anh")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().assertTextContains("minhanh.tran")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().assertTextContains("minhanh@example.com")

        // Change username and submit again -> EMAIL_TAKEN shows under email while preserving all data.
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("minhanh.new")
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.EMAIL_ERROR, useUnmergedTree = true)
                    .performScrollTo()
                    .assertTextContains("Email này đã có tài khoản.", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Trần Minh Anh")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().assertTextContains("minhanh.new")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().assertTextContains("minhanh@example.com")
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun submitsOnceWhileInFlight() {
        val release = CompletableDeferred<ApiResult<RegistrationStartData>>()
        var calls = 0
        val gatedGateway = object : RecoveryGateway {
            override suspend fun startRegistration(
                fullName: String,
                username: String,
                email: String,
                password: String,
                termsVersion: String,
                registrationId: UUID?,
            ): ApiResult<RegistrationStartData> {
                calls++
                return release.await()
            }

            override suspend fun resendRegistrationCode(registrationId: UUID): ApiResult<RegistrationStartData> = error("unused")
            override suspend fun verifyRegistration(registrationId: UUID, code: String, username: String?): ApiResult<Tokens> = error("unused")
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
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                RegistrationFlowScreen(
                    recoveryGateway = gatedGateway,
                    sessionManager = stack.sessionManager,
                    onBackToLogin = {},
                )
            }
        }

        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().performTextInput("Trần Minh Anh")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("minhanh.tran")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().performTextInput("minhanh@example.com")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("Matkhau1!")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) { calls == 1 }
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performClick()
        assertEquals(1, calls)

        release.complete(
            ApiResult.HttpFailure(
                statusCode = 409,
                error = vn.unicom.thi360.core.network.ApiError(code = "USERNAME_TAKEN"),
            ),
        )
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, calls)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp", fontScale = 2.0f)
    fun registerLayoutWrapsCleanlyAt360dpAnd200PercentFontScale() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                RegistrationFlowScreen(
                    recoveryGateway = gateway,
                    sessionManager = stack.sessionManager,
                    onBackToLogin = {},
                )
            }
        }

        val loginLink = composeRule.onNodeWithTag(RegisterTestTags.LOGIN_LINK)
        loginLink.performScrollTo()
        loginLink.assertIsDisplayed()
        assertTrue(loginLink.getUnclippedBoundsInRoot().width >= 90.dp)
    }
}
