package vn.unicom.thi360.feature.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.RegisterTestTags
import vn.unicom.thi360.shell.AppRoot

@RunWith(RobolectricTestRunner::class)
class RegisterFlowTest {

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
    fun submitGoesToOtp() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.EN },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readApiFixture("register-200.json"),
            ),
        )

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.EN),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                recoveryGateway = gateway,
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.REGISTER_LINK).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(RegisterTestTags.SCREEN_FORM).assertIsDisplayed()

        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().performTextInput("Nguyen Van An")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("student.demo")
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
        composeRule.onNodeWithTag(RegisterTestTags.OTP_EMAIL_TEXT)
            .assertTextContains("student.demo@thi360.vn", substring = true)

        val request = server.takeRequest()
        assertEquals("/api/v1/auth/register", request.path)
        assertEquals("en", request.getHeader("Accept-Language"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("2026-11", body["termsVersion"]?.jsonPrimitive?.content)
        assertEquals("student.demo", body["username"]?.jsonPrimitive?.content)
        assertEquals("Nguyen Van An", body["fullName"]?.jsonPrimitive?.content)
        assertEquals("student.demo@thi360.vn", body["email"]?.jsonPrimitive?.content)
        assertEquals("true", body["acceptTerms"]?.jsonPrimitive?.content)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun backKeepsData() {
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
                ContractFixtureLoader.readApiFixture("register-200.json"),
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

        composeRule.onNodeWithTag(LoginTestTags.REGISTER_LINK).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().performTextInput("Trần Minh Anh")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("minhanh.tran")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().performTextInput("student.demo@thi360.vn")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("SecretPass1!")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.SCREEN_OTP).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        server.takeRequest()

        composeRule.onNodeWithTag(RegisterTestTags.OTP_BACK_TO_EDIT_BUTTON).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RegisterTestTags.SCREEN_FORM).assertIsDisplayed()
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Trần Minh Anh")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().assertTextContains("minhanh.tran")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().assertTextContains("student.demo@thi360.vn")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().assertTextContains("", substring = false)

        // Re-submitting with a new password forwards registrationId from the pending session.
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("NewSecret2!")
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.SCREEN_OTP).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        val secondReqBody = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(
            "019320a0-0000-7000-8000-000000000002",
            secondReqBody["registrationId"]?.jsonPrimitive?.content,
        )
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun processDeathReturnsToLogin() {
        val tokenStore = InMemoryTokenStore()
        val firstStack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val firstGateway = OpenApiRecoveryGateway(firstStack.publicAuthApi, firstStack.authenticatedAuthApi)
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readApiFixture("register-200.json"),
            ),
        )

        var processGeneration by mutableIntStateOf(1)

        composeRule.setContent {
            if (processGeneration == 1) {
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = firstStack.sessionManager,
                    recoveryGateway = firstGateway,
                )
            } else {
                val relaunchedStack = Thi360ApiFactory.createSessionStack(
                    baseUrl = server.url("/").toString(),
                    environment = Environment.DEV,
                    tokenStore = tokenStore,
                    localeProvider = { AppLocale.VI },
                )
                val relaunchedGateway = OpenApiRecoveryGateway(
                    relaunchedStack.publicAuthApi,
                    relaunchedStack.authenticatedAuthApi,
                )
                AppRoot(
                    preferences = AppPreferences(locale = AppLocale.VI),
                    onSelectLocale = {},
                    onSelectThemeMode = {},
                    sessionManager = relaunchedStack.sessionManager,
                    recoveryGateway = relaunchedGateway,
                )
            }
        }

        composeRule.onNodeWithTag(LoginTestTags.REGISTER_LINK).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().performTextInput("Trần Minh Anh")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("minhanh.tran")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().performTextInput("student.demo@thi360.vn")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("SecretPass1!")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.SCREEN_OTP).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        // Simulate process death & cold relaunch: OTP step must not be restored; app lands on Login.
        composeRule.runOnIdle { processGeneration = 2 }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(RegisterTestTags.SCREEN_OTP).assertDoesNotExist()
        assertTrue(tokenStore.read() == null)
    }
}
