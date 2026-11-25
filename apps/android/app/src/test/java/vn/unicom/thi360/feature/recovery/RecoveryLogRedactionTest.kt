package vn.unicom.thi360.feature.recovery

import android.util.Log
import java.util.UUID
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
import vn.unicom.thi360.feature.register.TERMS_VERSION

@RunWith(RobolectricTestRunner::class)
class RecoveryLogRedactionTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        ShadowLog.clear()
        server = MockWebServer()
        server.start()
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

        server.enqueue(ContractFixtureLoader.jsonResponse(200, ContractFixtureLoader.readApiFixture("register-200.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(422, ContractFixtureLoader.readAuthFixture("register-verify-422-email-code-invalid.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(201, verifyFixture))
        server.enqueue(ContractFixtureLoader.jsonResponse(202, ContractFixtureLoader.readApiFixture("password-forgot-202.json")))
        server.enqueue(ContractFixtureLoader.jsonResponse(422, ContractFixtureLoader.readApiFixture("password-reset-422-reset-token-invalid.json")))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(ContractFixtureLoader.jsonResponse(400, ContractFixtureLoader.readApiFixture("change-password-400-wrong-old-password.json")))
        server.enqueue(MockResponse().setResponseCode(200))

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
            baseUrl = server.url("/").toString(),
            environment = env,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiRecoveryGateway(stack.publicAuthApi, stack.authenticatedAuthApi)
        val regId = UUID.fromString("019320a0-0000-7000-8000-000000000002")

        gateway.startRegistration("Student Demo", "student.demo", "student.demo@thi360.vn", secretRegPassword, TERMS_VERSION)
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
