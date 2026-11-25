package vn.unicom.thi360.core.network

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.BuildConfig
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.storage.Tokens

class LogRedactionTest {

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
    fun demoLogsDuringLoginRefreshAndAuthenticatedCallsNeverContainSecrets() = runTest {
        val secretPassword = "MyVerySecretPassword#2026"
        val secretAccessToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzdHVkZW50In0.sigAccessSecret999"
        val secretRefreshToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJyZWZyZXNoIn0.sigRefreshSecret888"

        val loginFixture = ContractFixtureLoader.readAuthFixture("login-200.json")
            .replace("fixture-access-token", secretAccessToken)
            .replace("fixture-refresh-token", secretRefreshToken)
        val refreshFixture = ContractFixtureLoader.readAuthFixture("refresh-200.json")
            .replace("fixture-access-token", "${secretAccessToken}New")
            .replace("fixture-refresh-token", "${secretRefreshToken}New")

        server.enqueue(ContractFixtureLoader.jsonResponse(200, loginFixture))
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                401,
                ContractFixtureLoader.readAuthFixture("login-401-invalid-credentials.json"),
            ),
        )
        server.enqueue(ContractFixtureLoader.jsonResponse(200, refreshFixture))
        server.enqueue(
            ContractFixtureLoader.jsonResponse(
                200,
                ContractFixtureLoader.readApiFixture("me-200.json"),
            ),
        )

        val capturedLogs = mutableListOf<String>()
        val recordingLogger = object : AppLogger {
            override fun debug(tag: String, message: String) {
                capturedLogs += "D/$tag: $message"
            }

            override fun warn(tag: String, message: String) {
                capturedLogs += "W/$tag: $message"
            }
        }

        val env = if (BuildConfig.FLAVOR == "demo") Environment.DEMO else AppEnvironment.environment
        val tokenStore = InMemoryTokenStore()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/").toString(),
            environment = env,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            logger = recordingLogger,
        )

        stack.sessionManager.login("student.demo", secretPassword)
        stack.sessionManager.login("student.demo", secretPassword)
        stack.sessionManager.refreshSession()
        stack.authenticatedAuthApi.me()

        val tokensString = tokenStore.read().toString()
        val redactedSample = LogRedactor.redact(
            """Authorization: Bearer $secretAccessToken {"password":"$secretPassword","refreshToken":"$secretRefreshToken"} /reset-password?token=resetSecret123""",
        )

        val allOutputs = capturedLogs + listOf(tokensString, redactedSample)
        val forbiddenSecrets = listOf(
            secretPassword,
            secretAccessToken,
            secretRefreshToken,
            "resetSecret123",
        )

        for (secret in forbiddenSecrets) {
            for (line in allOutputs) {
                assertFalse("Leaked secret in log/string output: $line", line.contains(secret))
            }
        }
        assertTrue(tokensString.contains("[REDACTED]"))
        assertTrue(redactedSample.contains("[REDACTED]"))
    }
}
