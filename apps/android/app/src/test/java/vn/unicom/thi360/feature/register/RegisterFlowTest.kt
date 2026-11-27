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
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.legal.LegalRepository
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.feature.login.LoginTestTags
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.RegisterTestTags
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class RegisterFlowTest {

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
    fun submitGoesToOtp() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val legalRepository = LegalRepository.create(context)
        val expectedTermsVersion = legalRepository.termsVersionOrFallback()

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.EN },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        server.respondApiFixture("POST", "/api/v1/auth/register", 200, "register-200.json")

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.EN),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                recoveryGateway = gateway,
                legalRepository = legalRepository,
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

        val request = server.requests("POST", "/api/v1/auth/register").single()
        assertEquals("/api/v1/auth/register", request.path)
        assertEquals("en", request.getHeader("Accept-Language"))
        val body = Json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject
        assertEquals(expectedTermsVersion, body["termsVersion"]?.jsonPrimitive?.content)
        assertEquals("student.demo", body["username"]?.jsonPrimitive?.content)
        assertEquals("Nguyen Van An", body["fullName"]?.jsonPrimitive?.content)
        assertEquals("student.demo@thi360.vn", body["email"]?.jsonPrimitive?.content)
        assertEquals("true", body["acceptTerms"]?.jsonPrimitive?.content)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun termsVersionFromBundledTerms() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val legalRepository = LegalRepository.create(context)
        val bundledVersion = legalRepository.termsVersion().getOrThrow()
        assertTrue("Bundled terms.vi.json version must not be blank", bundledVersion.isNotBlank())
        assertNotEquals("Hardcoded Phase-1 TERMS_VERSION 2026-11 must not be used", "2026-11", bundledVersion)

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        server.respondApiFixture("POST", "/api/v1/auth/register", 200, "register-200.json")

        composeRule.setContent {
            AppRoot(
                preferences = AppPreferences(locale = AppLocale.VI),
                onSelectLocale = {},
                onSelectThemeMode = {},
                sessionManager = stack.sessionManager,
                recoveryGateway = gateway,
                legalRepository = legalRepository,
            )
        }

        composeRule.onNodeWithTag(LoginTestTags.REGISTER_LINK).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().performTextInput("Nguyễn Văn An")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().performTextInput("student.terms")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().performTextInput("terms@thi360.vn")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("StudentPass1!")
        composeRule.onNodeWithTag(RegisterTestTags.TERMS_CHECKBOX).performScrollTo().performClick()
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("POST", "/api/v1/auth/register") == 1
        }
        val req = server.requests("POST", "/api/v1/auth/register").single()
        val body = Json.parseToJsonElement(req.body.clone().readUtf8()).jsonObject
        assertEquals(bundledVersion, body["termsVersion"]?.jsonPrimitive?.content)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun backKeepsData() {
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        server.respondApiFixture("POST", "/api/v1/auth/register", 200, "register-200.json")

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

        composeRule.onNodeWithTag(RegisterTestTags.OTP_BACK_TO_EDIT_BUTTON).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(RegisterTestTags.SCREEN_FORM).assertIsDisplayed()
        composeRule.onNodeWithTag(RegisterTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Trần Minh Anh")
        composeRule.onNodeWithTag(RegisterTestTags.USERNAME_FIELD).performScrollTo().assertTextContains("minhanh.tran")
        composeRule.onNodeWithTag(RegisterTestTags.EMAIL_FIELD).performScrollTo().assertTextContains("student.demo@thi360.vn")
        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().assertTextContains("", substring = false)

        composeRule.onNodeWithTag(RegisterTestTags.PASSWORD_FIELD).performScrollTo().performTextInput("NewSecret2!")
        composeRule.onNodeWithTag(RegisterTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(RegisterTestTags.SCREEN_OTP).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        val secondReq = server.requests("POST", "/api/v1/auth/register")[1]
        val secondReqBody = Json.parseToJsonElement(secondReq.body.clone().readUtf8()).jsonObject
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
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val firstGateway = OpenApiRecoveryGateway(firstStack.publicAuthApi, firstStack.authenticatedAuthApi)
        server.respondApiFixture("POST", "/api/v1/auth/register", 200, "register-200.json")

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
                    baseUrl = server.url("/"),
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

        composeRule.runOnIdle { processGeneration = 2 }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LoginTestTags.IDENTIFIER_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(RegisterTestTags.SCREEN_OTP).assertDoesNotExist()
        assertTrue(tokenStore.read() == null)
    }
}
