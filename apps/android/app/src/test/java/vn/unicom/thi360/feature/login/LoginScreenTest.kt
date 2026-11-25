package vn.unicom.thi360.feature.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import vn.unicom.thi360.core.session.MobileAuthGateway
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.shell.AppRoot

@RunWith(RobolectricTestRunner::class)
class LoginScreenTest {

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
    fun showsAllControls() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        var preferences by mutableStateOf(AppPreferences(locale = AppLocale.VI))

        composeRule.setContent {
            AppRoot(
                preferences = preferences,
                onSelectLocale = { locale -> preferences = preferences.copy(locale = locale) },
                onSelectThemeMode = { mode -> preferences = preferences.copy(themeMode = mode) },
                sessionManager = stack.sessionManager,
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.SCREEN_PHONE).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_VISIBILITY_TOGGLE).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Hiện mật khẩu").assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.FORGOT_PASSWORD_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.REGISTER_LINK).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.LANGUAGE_TOGGLE).assertIsDisplayed()

        // Toggle password visibility updates accessible description immediately.
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_VISIBILITY_TOGGLE).performClick()
        composeRule.onNodeWithContentDescription("Ẩn mật khẩu").assertIsDisplayed()

        // Toggle language switches labels to English immediately without losing typed identifier.
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("minhanh.tran")
        composeRule.onNodeWithTag(LoginTestTags.LANGUAGE_TOGGLE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).assertTextContains("Sign in")
        composeRule.onNodeWithText("Forgot password?").assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertTextContains("minhanh.tran")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun tabletWidthShowsTwoColumnLoginLayout() {
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
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.SCREEN_TABLET).assertIsDisplayed()
        composeRule.onNodeWithText("Thi thử theo đúng cấu trúc đề").assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun validation() {
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
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_ERROR, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Vui lòng nhập tên đăng nhập hoặc email.")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_ERROR, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Vui lòng nhập mật khẩu.")
        assertEquals("Blank fields must not call backend API", 0, server.requestCount)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun submitsOnce() {
        val releaseLogin = CompletableDeferred<ApiResult<Tokens>>()
        var loginCalls = 0
        val gatedGateway = object : MobileAuthGateway {
            override suspend fun login(identifier: String, password: String): ApiResult<Tokens> {
                loginCalls++
                return releaseLogin.await()
            }

            override suspend fun refresh(refreshToken: String): ApiResult<Tokens> =
                error("unused")

            override suspend fun logout(refreshToken: String): ApiResult<Unit> =
                ApiResult.Success(Unit)
        }
        val sessionManager = SessionManager(
            tokenStore = InMemoryTokenStore(),
            authGateway = gatedGateway,
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = sessionManager,
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("student.demo")
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("SecretPass1!")
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) { loginCalls == 1 }
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_PROGRESS).assertIsDisplayed()

        // Second click while request is in flight must not trigger a second call.
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()
        assertEquals(1, loginCalls)

        releaseLogin.complete(
            ApiResult.HttpFailure(
                statusCode = 401,
                error = vn.unicom.thi360.core.network.ApiError(code = "INVALID_CREDENTIALS"),
            ),
        )
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, loginCalls)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun errorMessages() {
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
            )
        }

        val scenarios = listOf(
            Triple(
                401,
                ContractFixtureLoader.readAuthFixture("login-401-invalid-credentials.json"),
                "Tên đăng nhập hoặc mật khẩu không đúng.",
            ),
            Triple(
                403,
                ContractFixtureLoader.readAuthFixture("login-403-account-inactive.json"),
                "Tài khoản đã ngừng hoạt động. Vui lòng liên hệ quản trị viên.",
            ),
            Triple(
                429,
                ContractFixtureLoader.readAuthFixture("login-429-login-throttled.json"),
                "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.",
            ),
            Triple(
                429,
                ContractFixtureLoader.readAuthFixture("login-429-rate-limited.json"),
                "Quá nhiều yêu cầu từ địa chỉ của bạn. Vui lòng thử lại sau ít phút.",
            ),
            Triple(
                500,
                """{"code":"UNMAPPED_INTERNAL_999","message":"Raw stack detail","fieldErrors":[],"traceId":"t-1"}""",
                "Không thể đăng nhập. Vui lòng thử lại.",
            ),
        )

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).performTextInput("student.demo")

        for ((statusCode, body, expectedMessage) in scenarios) {
            server.enqueue(ContractFixtureLoader.jsonResponse(statusCode, body))
            composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD).performTextInput("WrongPass1!")
            composeRule.onNodeWithTag(LoginTestTags.SUBMIT_BUTTON).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(LoginTestTags.ERROR_BANNER)
                        .assertTextContains(expectedMessage, substring = true)
                    true
                }.getOrDefault(false)
            }

            composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD)
                .assertTextContains("student.demo")
            composeRule.onNodeWithTag(LoginTestTags.PASSWORD_FIELD)
                .assertTextContains("", substring = false)
            composeRule.onNodeWithText("UNMAPPED_INTERNAL_999", substring = true).assertDoesNotExist()
            composeRule.onNodeWithText("Raw stack detail", substring = true).assertDoesNotExist()
        }
    }
}
