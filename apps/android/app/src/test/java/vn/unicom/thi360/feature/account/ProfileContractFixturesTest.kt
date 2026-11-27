package vn.unicom.thi360.feature.account

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.ApiErrorParser
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.generated.api.AvatarApi
import vn.unicom.thi360.core.network.generated.api.MeApi
import vn.unicom.thi360.core.network.generated.api.ProfileApi
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AccountDeletionBlockedResponse
import vn.unicom.thi360.core.network.generated.model.AccountDeletionCheckResponse
import vn.unicom.thi360.core.network.generated.model.EmailCodeErrorResponse
import vn.unicom.thi360.core.network.generated.model.ProfileResponse

class ProfileContractFixturesTest {

    private val json = Serializer.kotlinxSerializationJson

    @Test
    fun generatedClientExposesAllEightProfileOperationsAndTwoAccountDeletionOperations() {
        val profileMethods = ProfileApi::class.java.declaredMethods.map { it.name }.toSet()
        val avatarMethods = AvatarApi::class.java.declaredMethods.map { it.name }.toSet()
        val meMethods = MeApi::class.java.declaredMethods.map { it.name }.toSet()

        assertTrue("getMyProfile missing", "getMyProfile" in profileMethods)
        assertTrue("updateMyProfile missing", "updateMyProfile" in profileMethods)
        assertTrue("replaceMyAvatar missing", "replaceMyAvatar" in avatarMethods)
        assertTrue("removeMyAvatar missing", "removeMyAvatar" in avatarMethods)
        assertTrue("sendVerification missing", "sendVerification" in meMethods)
        assertTrue("verify missing", "verify" in meMethods)
        assertTrue("requestChange missing", "requestChange" in meMethods)
        assertTrue("confirmChange missing", "confirmChange" in meMethods)
        assertTrue("checkMyAccountDeletion missing", "checkMyAccountDeletion" in meMethods)
        assertTrue("deleteMyAccount missing", "deleteMyAccount" in meMethods)
    }

    @Test
    fun allProfileAvatarEmailAndDeletionFixturesDecodeCleanly() {
        val apiDir = File(ContractFixtureLoader.openApiSpecFile().parentFile, "api")
        val targetFixtures = apiDir.listFiles { f ->
            f.isFile && f.name.endsWith(".json") && (
                f.name.startsWith("me-profile") ||
                    f.name.startsWith("me-avatar") ||
                    f.name.startsWith("me-email") ||
                    f.name.startsWith("me-account-deletion")
                )
        }?.sortedBy { it.name }.orEmpty()

        assertTrue("Expected at least 25 profile/avatar/email/deletion fixtures", targetFixtures.size >= 25)

        val profile200 = json.decodeFromString(
            ProfileResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-profile-200.json"),
        ).toUserProfile()
        assertEquals("student.demo", profile200.username)
        assertTrue(profile200.emailVerified)
        assertNull(profile200.avatarAssetId)
        assertNull(profile200.pendingEmail)

        val profileAvatarPending = json.decodeFromString(
            ProfileResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-profile-200-avatar-pending-email.json"),
        ).toUserProfile()
        assertEquals("0912345678", profileAvatarPending.phone)
        assertEquals("00000000-0000-7000-8000-000000000001", profileAvatarPending.avatarAssetId)
        assertEquals("/api/v1/avatars/00000000-0000-7000-8000-000000000001", profileAvatarPending.avatarUrl)
        assertEquals("student.new@thi360.vn", profileAvatarPending.pendingEmail)

        val profileNoEmail = json.decodeFromString(
            ProfileResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-profile-200-no-email.json"),
        ).toUserProfile()
        assertNull(profileNoEmail.email)
        assertFalse(profileNoEmail.emailVerified)

        val profileUpdate200 = json.decodeFromString(
            ProfileResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-profile-update-200.json"),
        ).toUserProfile()
        assertEquals("0912345678", profileUpdate200.phone)

        val avatar200 = json.decodeFromString(
            ProfileResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-avatar-put-200.json"),
        ).toUserProfile()
        assertNotNull(avatar200.avatarAssetId)
        assertNotNull(avatar200.avatarUrl)

        val emailRateLimited = json.decodeFromString(
            EmailCodeErrorResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-email-verification-429-rate-limited.json"),
        )
        assertEquals("RATE_LIMITED", emailRateLimited.code)
        assertEquals(60L, emailRateLimited.retryAfter)

        val emailInvalid = json.decodeFromString(
            EmailCodeErrorResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-email-verify-422-email-code-invalid.json"),
        )
        assertEquals("EMAIL_CODE_INVALID", emailInvalid.code)
        assertEquals(4, emailInvalid.remainingAttempts)

        val check200 = json.decodeFromString(
            AccountDeletionCheckResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-account-deletion-check-200.json"),
        )
        assertTrue(check200.canDelete)
        assertTrue(check200.blockers.isEmpty())
        assertEquals(0, check200.unusedAttempts)
        assertFalse(check200.pendingGrading)

        val checkUnused = json.decodeFromString(
            AccountDeletionCheckResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-account-deletion-check-200-unused-attempts.json"),
        )
        assertEquals(3, checkUnused.unusedAttempts)

        val checkPending = json.decodeFromString(
            AccountDeletionCheckResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-account-deletion-check-200-pending-grading.json"),
        )
        assertTrue(checkPending.pendingGrading)

        val checkBlocked = json.decodeFromString(
            AccountDeletionCheckResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-account-deletion-check-200-blocked.json"),
        )
        assertFalse(checkBlocked.canDelete)
        assertEquals(
            listOf(
                AccountDeletionCheckResponse.Blockers.ATTEMPT_IN_PROGRESS,
                AccountDeletionCheckResponse.Blockers.ORDER_BEING_CHECKED,
                AccountDeletionCheckResponse.Blockers.ORDER_PENDING_PAYMENT,
            ),
            checkBlocked.blockers,
        )

        val deleteBlocked = json.decodeFromString(
            AccountDeletionBlockedResponse.serializer(),
            ContractFixtureLoader.readApiFixture("me-account-deletion-409-account-deletion-blocked.json"),
        )
        assertEquals("ACCOUNT_DELETION_BLOCKED", deleteBlocked.code)
        assertEquals(3, deleteBlocked.blockers.size)

        listOf(
            "me-profile-update-400-phone.json" to "VALIDATION_FAILED",
            "me-avatar-put-413-avatar-too-large.json" to "AVATAR_TOO_LARGE",
            "me-avatar-put-413-upload-too-large.json" to "UPLOAD_TOO_LARGE",
            "me-avatar-put-422-avatar-dimensions-exceeded.json" to "AVATAR_DIMENSIONS_EXCEEDED",
            "me-avatar-put-422-avatar-invalid-image.json" to "AVATAR_INVALID_IMAGE",
            "me-email-change-400-new-email.json" to "VALIDATION_FAILED",
            "me-email-change-400-wrong-old-password.json" to "WRONG_OLD_PASSWORD",
            "me-email-change-409-email-taken.json" to "EMAIL_TAKEN",
            "me-email-change-429-login-throttled.json" to "LOGIN_THROTTLED",
            "me-email-change-429-rate-limited.json" to "RATE_LIMITED",
            "me-email-change-confirm-422-email-code-invalid.json" to "EMAIL_CODE_INVALID",
            "me-email-verify-422-email-code-expired.json" to "EMAIL_CODE_EXPIRED",
            "me-account-deletion-400-wrong-old-password.json" to "WRONG_OLD_PASSWORD",
            "me-account-deletion-403-account-deletion-not-allowed.json" to "ACCOUNT_DELETION_NOT_ALLOWED",
            "me-account-deletion-429-login-throttled.json" to "LOGIN_THROTTLED",
            "me-account-deletion-check-403-account-deletion-not-allowed.json" to "ACCOUNT_DELETION_NOT_ALLOWED",
        ).forEach { (fixtureName, expectedCode) ->
            val parsed = ApiErrorParser.parse(ContractFixtureLoader.readApiFixture(fixtureName))
            assertNotNull("Failed to parse $fixtureName", parsed)
            assertEquals("Code mismatch in $fixtureName", expectedCode, parsed?.code)
        }
    }
}
