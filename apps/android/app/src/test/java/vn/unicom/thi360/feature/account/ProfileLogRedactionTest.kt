package vn.unicom.thi360.feature.account

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.AppLogger
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.testing.ContractFakeServer

class ProfileLogRedactionTest {

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private suspend fun exerciseSensitiveEndpoints(environment: Environment): List<String> {
        val secretAccess = "eyJhbGciOiJIUzI1NiJ9.SECRET_ACCESS_TOKEN_PROFILE"
        val secretRefresh = "SECRET_REFRESH_TOKEN_PROFILE"
        val secretOtpVerify = "918273"
        val secretOtpConfirm = "647382"
        val secretChangePassword = "SuperSecretChangePass99!"
        val secretDeletePassword = "SuperSecretDeletePass88!"
        val uniqueImageMarker = "UNIQUE_RAW_AVATAR_MARKER_BYTES_998877"

        val logs = mutableListOf<String>()
        val logger = object : AppLogger {
            override fun debug(tag: String, message: String) {
                logs += "D/$tag: $message"
            }

            override fun warn(tag: String, message: String) {
                logs += "W/$tag: $message"
            }
        }

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = environment,
            tokenStore = InMemoryTokenStore(
                testSignedInTokens(
                    accessToken = secretAccess,
                    refreshToken = secretRefresh,
                ),
            ),
            localeProvider = { AppLocale.VI },
            logger = logger,
        )

        server.respondSequence(
            "PUT",
            "/api/v1/me/avatar",
            { ContractFakeServer.apiFixtureResponse(200, "me-avatar-put-200.json") },
            { ContractFakeServer.apiFixtureResponse(413, "me-avatar-put-413-avatar-too-large.json") },
        )
        server.respondSequence(
            "POST",
            "/api/v1/me/email/verify",
            { ContractFakeServer.apiFixtureResponse(422, "me-email-verify-422-email-code-invalid.json") },
        )
        server.respondSequence(
            "POST",
            "/api/v1/me/email/change",
            { ContractFakeServer.apiFixtureResponse(400, "me-email-change-400-wrong-old-password.json") },
        )
        server.respondSequence(
            "POST",
            "/api/v1/me/email/change/confirm",
            { ContractFakeServer.apiFixtureResponse(422, "me-email-change-confirm-422-email-code-invalid.json") },
        )
        server.respondSequence(
            "POST",
            "/api/v1/me/account/deletion",
            { ContractFakeServer.apiFixtureResponse(400, "me-account-deletion-400-wrong-old-password.json") },
            { okhttp3.mockwebserver.MockResponse().setResponseCode(204) },
        )

        val profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val deletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/"))

        val prepared = PreparedAvatar(
            bytes = uniqueImageMarker.toByteArray(Charsets.UTF_8),
            fileName = "avatar.jpg",
            mimeType = "image/jpeg",
        )
        assertFalse(prepared.toString().contains(uniqueImageMarker))

        assertTrue(profileGateway.uploadAvatar(prepared) is ProfileOperationResult.Success)
        assertTrue(profileGateway.uploadAvatar(prepared) is ProfileOperationResult.Failure)
        assertTrue(profileGateway.verify(secretOtpVerify) is ProfileOperationResult.Failure)
        assertTrue(
            profileGateway.requestEmailChange("new.student@thi360.vn", secretChangePassword)
                is ProfileOperationResult.Failure,
        )
        assertTrue(profileGateway.confirmEmailChange(secretOtpConfirm) is ProfileOperationResult.Failure)
        assertTrue(deletionGateway.delete(secretDeletePassword) is AccountDeletionOutcome.Failed)
        assertEquals(AccountDeletionOutcome.Deleted, deletionGateway.delete(secretDeletePassword))

        assertTrue("Expected HTTP logs to be captured for $environment", logs.isNotEmpty())
        val combined = logs.joinToString("\n")
        assertFalse("Access token leaked into $environment logs", combined.contains(secretAccess))
        assertFalse("Refresh token leaked into $environment logs", combined.contains(secretRefresh))
        assertFalse("Verify OTP code leaked into $environment logs", combined.contains(secretOtpVerify))
        assertFalse("Confirm OTP code leaked into $environment logs", combined.contains(secretOtpConfirm))
        assertFalse("Change-email currentPassword leaked into $environment logs", combined.contains(secretChangePassword))
        assertFalse("Account-deletion currentPassword leaked into $environment logs", combined.contains(secretDeletePassword))
        assertFalse("Avatar raw bytes leaked into $environment logs", combined.contains(uniqueImageMarker))
        return logs
    }

    @Test
    fun demoLogsRedactOtpPasswordsTokensAndAvatarBytes() = runTest {
        val demoLogs = exerciseSensitiveEndpoints(Environment.DEMO)
        assertTrue("DEMO must log error summaries without bodies", demoLogs.size >= 5)
    }

    @Test
    fun devLogsRedactOtpPasswordsTokensAndAvatarBytes() = runTest {
        val devLogs = exerciseSensitiveEndpoints(Environment.DEV)
        assertTrue("DEV must log request summaries without bodies", devLogs.size >= 7)
    }
}
