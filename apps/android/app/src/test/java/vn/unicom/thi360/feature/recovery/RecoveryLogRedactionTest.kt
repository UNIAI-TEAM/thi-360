package vn.unicom.thi360.feature.recovery

import android.util.Log
import java.util.UUID
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
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
import vn.unicom.thi360.core.network.AndroidAppLogger
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class RecoveryLogRedactionTest {

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
    fun demoLogsDuringRegistrationOtpResetAndChangePasswordNeverContainSecrets() = runTest {
        val secretRegPassword = "RegSecretPass#2026!"
        val secretOldPassword = "OldSecretPass#2026!"
        val secretNewPassword = "NewSecretPass#2026!"
        val secretOtpCode = "739284"
        val secretResetToken = "reset-secret-link-token-777"
        val secretAccess = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzdHVkZW50In0.regAccessSecret111"
        val secretRefresh = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJyZWZyZXNoIn0.regRefreshSecret222"

        val verifyFixture = ContractFixtureLoader.readAuthFixture("register-verify-201.json")
            .replace("fixture-access-token", secretAccess)
            .replace("fixture-refresh-token", secretRefresh)

        server.respondApiFixture("POST", "/api/v1/auth/register", 200, "register-200.json")
        server.respondSequence(
            "POST",
            "/api/v1/auth/mobile/register/verify",
            { ContractFakeServer.authFixtureResponse(422, "register-verify-422-email-code-invalid.json") },
            { ContractFakeServer.jsonResponse(201, verifyFixture) },
        )
        server.respondApiFixture("POST", "/api/v1/auth/password/forgot", 202, "password-forgot-202.json")
        server.respondSequence(
            "POST",
            "/api/v1/auth/password/reset",
            { ContractFakeServer.apiFixtureResponse(422, "password-reset-422-reset-token-invalid.json") },
            { MockResponse().setResponseCode(204) },
        )
        server.respondSequence(
            "POST",
            "/api/v1/auth/change-password",
            { ContractFakeServer.apiFixtureResponse(400, "change-password-400-wrong-old-password.json") },
            { MockResponse().setResponseCode(200) },
        )

        val env = if (BuildConfig.FLAVOR == "demo") Environment.DEMO else AppEnvironment.environment
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = secretAccess,
                refreshToken = secretRefresh,
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = env,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        val regId = UUID.fromString("019320a0-0000-7000-8000-000000000002")

        gateway.startRegistration("Student Demo", "student.demo", "student.demo@thi360.vn", secretRegPassword, "2026-11-26")
        gateway.verifyRegistration(regId, secretOtpCode, "student.demo")
        gateway.verifyRegistration(regId, secretOtpCode, "student.demo")
        gateway.forgotPassword("student.demo@thi360.vn")
        gateway.resetPassword(secretResetToken, secretNewPassword)
        gateway.resetPassword(secretResetToken, secretNewPassword)
        gateway.changePassword(secretOldPassword, secretNewPassword)
        gateway.changePassword(secretOldPassword, secretNewPassword)

        AndroidAppLogger.warn(
            "Thi360Recovery",
            """{"password":"$secretRegPassword","oldPassword":"$secretOldPassword","newPassword":"$secretNewPassword","code":"$secretOtpCode","token":"$secretResetToken"} /reset-password?token=$secretResetToken""",
        )

        val shadowLogs = ShadowLog.getLogs()
        val httpLogs = shadowLogs.filter { it.tag == "Thi360Http" }
        assertTrue("Expected Thi360Http error entries for 4xx calls", httpLogs.any { it.msg.contains("-> 422") })
        assertTrue("Expected Thi360Http 400 entry", httpLogs.any { it.msg.contains("-> 400") })
        if (env == Environment.DEMO) {
            assertTrue(
                "Demo flavor must suppress < 400 HTTP success logs",
                httpLogs.none { it.msg.contains("-> 200") || it.msg.contains("-> 201") || it.msg.contains("-> 204") },
            )
        }
        assertTrue(shadowLogs.any { it.tag == "Thi360Recovery" && it.type == Log.WARN && it.msg.contains("[REDACTED]") })

        val allOutputs = shadowLogs.flatMap { item ->
            listOfNotNull(item.msg, item.throwable?.stackTraceToString())
        } + listOf(tokenStore.read().toString(), ResetLink(secretResetToken).toString())

        val forbiddenSecrets = listOf(
            secretRegPassword,
            secretOldPassword,
            secretNewPassword,
            secretOtpCode,
            secretResetToken,
            secretAccess,
            secretRefresh,
        )
        for (secret in forbiddenSecrets) {
            for (line in allOutputs) {
                assertFalse("Leaked recovery secret in output: $line", line.contains(secret))
            }
        }
    }
}
