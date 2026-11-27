package vn.unicom.thi360.core.network

import android.util.Log
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import vn.unicom.thi360.BuildConfig
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class LogRedactionTest {

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        ShadowLog.clear()
        server = ContractFakeServer().start()
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

        server.respondSequence(
            "POST",
            "/api/v1/auth/mobile/login",
            { ContractFakeServer.jsonResponse(200, loginFixture) },
            {
                ContractFakeServer.authFixtureResponse(
                    401,
                    "login-401-invalid-credentials.json",
                )
            },
            {
                ContractFakeServer.jsonResponse(
                    503,
                    """{"code":"SERVICE_UNAVAILABLE","message":"Temporarily unavailable"}""",
                )
            },
        )
        server.respondJson("POST", "/api/v1/auth/mobile/refresh", 200, refreshFixture)
        server.respondApiFixture("GET", "/api/v1/me", 200, "me-200.json")

        val env = if (BuildConfig.FLAVOR == "demo") Environment.DEMO else AppEnvironment.environment
        val tokenStore = InMemoryTokenStore()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = env,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )

        stack.sessionManager.login("student.demo", secretPassword)
        stack.sessionManager.login("student.demo", secretPassword)
        stack.sessionManager.login("student.demo", secretPassword)
        stack.sessionManager.refreshSession()
        stack.authenticatedAuthApi.me()

        AndroidAppLogger.warn(
            "Thi360Auth",
            """Authorization: Bearer $secretAccessToken {"password":"$secretPassword","refreshToken":"$secretRefreshToken","voucherCode":"SECRET-VOUCHER-99"} /reset-password?token=resetSecret123&vnp_SecureHash=vnpHash999&vnp_TxnRef=txnSecret888""",
        )

        val shadowLogs = ShadowLog.getLogs()
        val httpLogs = shadowLogs.filter { it.tag == "Thi360Http" }
        assertTrue("Expected Thi360Http log entries for >= 400 responses", httpLogs.isNotEmpty())
        assertTrue(
            "Expected 503 response to emit a WARN entry via AndroidAppLogger",
            httpLogs.any { it.type == Log.WARN && it.msg.contains("-> 503") },
        )
        assertTrue(
            "Expected 401 response to emit an HTTP log entry via AndroidAppLogger",
            httpLogs.any { it.msg.contains("-> 401") },
        )
        if (env == Environment.DEMO) {
            assertTrue(
                "Demo flavor must suppress < 400 HTTP success logs",
                httpLogs.none { it.msg.contains("-> 200") },
            )
        }

        val redactedAuthLog = shadowLogs.last { it.tag == "Thi360Auth" }.msg
        val tokensString = tokenStore.read().toString()

        val allOutputs = shadowLogs.flatMap { item ->
            listOfNotNull(item.msg, item.throwable?.stackTraceToString())
        } + listOf(tokensString)

        val forbiddenSecrets = listOf(
            secretPassword,
            secretAccessToken,
            secretRefreshToken,
            "resetSecret123",
            "SECRET-VOUCHER-99",
            "vnpHash999",
            "txnSecret888",
        )

        for (secret in forbiddenSecrets) {
            for (line in allOutputs) {
                assertFalse("Leaked secret in log/string output: $line", line.contains(secret))
            }
        }
        assertTrue(tokensString.contains("[REDACTED]"))
        assertTrue(redactedAuthLog.contains("[REDACTED]"))
    }
}
