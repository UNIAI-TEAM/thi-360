package vn.unicom.thi360.core.network

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.EmailCodeErrorResponse
import vn.unicom.thi360.core.network.generated.model.MeResponse
import vn.unicom.thi360.core.network.generated.model.MobileTokenResponse
import vn.unicom.thi360.core.network.generated.model.PasswordForgotResponse
import vn.unicom.thi360.core.network.generated.model.ProfileResponse
import vn.unicom.thi360.core.network.generated.model.RegisterStartResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.storage.Tokens

class ContractFixturesTest {

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
    fun generatedClientCoversEveryOperationInOpenApiMobileSpecAndDeserializesAllFixtures() {
        val specText = ContractFixtureLoader.openApiSpecFile().readText(Charsets.UTF_8)
        val specJson = Json.parseToJsonElement(specText).jsonObject
        val paths = specJson["paths"]!!.jsonObject.keys
        assertEquals(
            setOf(
                "/api/v1/auth/change-password",
                "/api/v1/auth/mobile/login",
                "/api/v1/auth/mobile/logout",
                "/api/v1/auth/mobile/refresh",
                "/api/v1/auth/mobile/register/verify",
                "/api/v1/auth/password/forgot",
                "/api/v1/auth/password/reset",
                "/api/v1/auth/register",
                "/api/v1/auth/register/resend",
                "/api/v1/me",
                "/api/v1/me/profile",
            ),
            paths,
        )

        val authFixtures = ContractFixtureLoader.authFixtureFiles()
        val apiFixtures = ContractFixtureLoader.apiFixtureFiles()
        assertTrue("Expected at least 15 auth fixtures, found ${authFixtures.size}", authFixtures.size >= 15)
        assertTrue("Expected at least 19 api fixtures, found ${apiFixtures.size}", apiFixtures.size >= 19)

        val generatedJson = Serializer.kotlinxSerializationJson

        for (file in authFixtures) {
            val body = file.readText(Charsets.UTF_8)
            when {
                file.name.contains("-200") || file.name.contains("-201") -> {
                    val parsed = generatedJson.decodeFromString(MobileTokenResponse.serializer(), body)
                    assertTrue(parsed.accessToken.isNotBlank())
                    assertTrue(parsed.refreshToken.isNotBlank())
                    assertTrue(parsed.refreshExpiresIn > 0)
                }

                file.name.contains("email-code") -> {
                    val parsed = generatedJson.decodeFromString(EmailCodeErrorResponse.serializer(), body)
                    assertTrue(parsed.code.isNotBlank())
                    assertNotNull(ApiErrorParser.parse(body))
                }

                else -> {
                    val parsed = ApiErrorParser.parse(body)
                    assertNotNull("Failed to parse auth error fixture ${file.name}", parsed)
                    assertTrue(parsed!!.code.isNotBlank())
                }
            }
        }

        for (file in apiFixtures) {
            val body = file.readText(Charsets.UTF_8)
            when {
                file.name == "me-200.json" -> {
                    val parsed = generatedJson.decodeFromString(MeResponse.serializer(), body)
                    assertEquals(MeResponse.Access.EXAM, parsed.access)
                }

                file.name.startsWith("me-profile-200") -> {
                    val parsed = generatedJson.decodeFromString(ProfileResponse.serializer(), body)
                    assertTrue(parsed.username.isNotBlank())
                }

                file.name == "register-200.json" || file.name == "register-resend-200.json" -> {
                    val parsed = generatedJson.decodeFromString(RegisterStartResponse.serializer(), body)
                    assertTrue(parsed.resendAfterSeconds > 0)
                }

                file.name == "password-forgot-202.json" -> {
                    val parsed = generatedJson.decodeFromString(PasswordForgotResponse.serializer(), body)
                    assertTrue(parsed.message.isNotBlank())
                }

                else -> {
                    val parsed = ApiErrorParser.parse(body)
                    assertNotNull("Failed to parse api error fixture ${file.name}", parsed)
                    assertTrue(parsed!!.code.isNotBlank())
                }
            }
        }
    }

    @Test
    fun generatedApisSendAcceptLanguageAndBearerHeadersAgainstContractFixtures() = runTest {
        var locale = AppLocale.VI
        val store = InMemoryTokenStore(
            Tokens(
                accessToken = "bearer-token-123",
                refreshToken = "refresh-token-456",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
            ),
        )
        val noOpLogger = object : AppLogger {
            override fun debug(tag: String, message: String) = Unit
            override fun warn(tag: String, message: String) = Unit
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = store,
            localeProvider = { locale },
            logger = noOpLogger,
        )

        server.enqueue(ContractFixtureLoader.jsonResponse(200, ContractFixtureLoader.readAuthFixture("login-200.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(200, ContractFixtureLoader.readApiFixture("me-200.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(200, ContractFixtureLoader.readApiFixture("me-profile-200.json")))

        stack.sessionManager.login("student.demo", "SecretPass1!")
        val loginReq = server.takeRequest()
        assertEquals("vi", loginReq.getHeader("Accept-Language"))
        assertFalse("Public login must not attach Authorization header", loginReq.getHeader("Authorization") != null)

        locale = AppLocale.EN
        val meResp = stack.authenticatedAuthApi.me()
        assertTrue(meResp.isSuccessful)
        assertEquals("student.demo", meResp.body()?.username)
        val meReq = server.takeRequest()
        assertEquals("en", meReq.getHeader("Accept-Language"))
        assertEquals("Bearer fixture-access-token", meReq.getHeader("Authorization"))

        val profileResp = stack.profileApi.getMyProfile()
        assertTrue(profileResp.isSuccessful)
        assertEquals("student.demo@thi360.vn", profileResp.body()?.email)
        val profileReq = server.takeRequest()
        assertEquals("en", profileReq.getHeader("Accept-Language"))
        assertEquals("Bearer fixture-access-token", profileReq.getHeader("Authorization"))
    }

    @Test
    fun recoveryGatewayMatchesRegistrationOtpAndPasswordContractFixtures() = runTest {
        var locale = AppLocale.EN
        val store = InMemoryTokenStore(
            Tokens(
                accessToken = "bearer-change-pw",
                refreshToken = "refresh-change-pw",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
            ),
        )
        val noOpLogger = object : AppLogger {
            override fun debug(tag: String, message: String) = Unit
            override fun warn(tag: String, message: String) = Unit
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = Environment.DEV,
            tokenStore = store,
            localeProvider = { locale },
            logger = noOpLogger,
        )
        val gateway = vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway(
            publicAuthApi = stack.publicAuthApi,
            authenticatedAuthApi = stack.authenticatedAuthApi,
        )

        server.enqueue(ContractFixtureLoader.jsonResponse(200, ContractFixtureLoader.readApiFixture("register-200.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(429, ContractFixtureLoader.readApiFixture("register-resend-429-rate-limited.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(422, ContractFixtureLoader.readAuthFixture("register-verify-422-email-code-invalid.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(201, ContractFixtureLoader.readAuthFixture("register-verify-201.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(202, ContractFixtureLoader.readApiFixture("password-forgot-202.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(422, ContractFixtureLoader.readApiFixture("password-reset-422-reset-token-invalid.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(400, ContractFixtureLoader.readApiFixture("change-password-400-wrong-old-password.json")))

        val start = gateway.startRegistration("Nguyen Van An", "student.demo", "student.demo@thi360.vn", "Pass1234!", "2026-11")
        assertTrue(start is ApiResult.Success)
        val startData = (start as ApiResult.Success).data
        assertEquals(60L, startData.resendAfterSeconds)
        val regReq = server.takeRequest()
        assertEquals("en", regReq.getHeader("Accept-Language"))
        assertFalse(regReq.getHeader("Authorization") != null)

        val resend429 = gateway.resendRegistrationCode(startData.registrationId) as ApiResult.HttpFailure
        assertEquals(429, resend429.statusCode)
        assertEquals(60L, resend429.error?.retryAfter)
        server.takeRequest()

        val verifyInvalid = gateway.verifyRegistration(startData.registrationId, "000000", "student.demo") as ApiResult.HttpFailure
        assertEquals(422, verifyInvalid.statusCode)
        assertEquals("EMAIL_CODE_INVALID", verifyInvalid.error?.code)
        assertEquals(4, verifyInvalid.error?.remainingAttempts)
        server.takeRequest()

        val verifyOk = gateway.verifyRegistration(startData.registrationId, "123456", "student.demo") as ApiResult.Success
        assertEquals("fixture-access-token", verifyOk.data.accessToken)
        server.takeRequest()

        val forgotOk = gateway.forgotPassword("student.demo@thi360.vn")
        assertTrue(forgotOk is ApiResult.Success)
        server.takeRequest()

        val resetInvalid = gateway.resetPassword("expired", "NewPass1!") as ApiResult.HttpFailure
        assertEquals("RESET_TOKEN_INVALID", resetInvalid.error?.code)
        server.takeRequest()

        val changeWrong = gateway.changePassword("Wrong1!", "NewPass1!") as ApiResult.HttpFailure
        assertEquals("WRONG_OLD_PASSWORD", changeWrong.error?.code)
        val changeReq = server.takeRequest()
        assertEquals("Bearer bearer-change-pw", changeReq.getHeader("Authorization"))
    }
}
