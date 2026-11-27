package vn.unicom.thi360.feature.prepare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult

class StartErrorMessageTest {

    @Test
    fun mapsAllStartErrorCodesAndSupportContactFlag() {
        val cases = listOf(
            Triple(409, "NO_ATTEMPTS_LEFT", StartRefusal.NoAttemptsLeft),
            Triple(422, "CHOICE_NOT_ALLOWED", StartRefusal.ChoiceNotAllowed),
            Triple(409, "NO_SERVING_OPTION", StartRefusal.NoServingOption),
            Triple(422, "SUBJECTS_INVALID", StartRefusal.SubjectsInvalid),
            Triple(429, "RATE_LIMITED", StartRefusal.RateLimited),
            Triple(429, null, StartRefusal.RateLimited),
            Triple(400, "RATE_LIMITED", StartRefusal.RateLimited),
            Triple(500, "INTERNAL_ERROR", StartRefusal.Generic),
            Triple(400, "UNKNOWN_CODE", StartRefusal.Generic),
        )

        cases.forEach { (status, code, expected) ->
            val refusal = StartErrorMessage.fromHttpFailure(status, code)
            assertEquals("status=$status code=$code", expected, refusal)
        }

        assertEquals(StartRefusal.Generic, StartErrorMessage.fromApiFailure(ApiResult.NetworkFailure(RuntimeException("offline"))))
        assertEquals(
            StartRefusal.NoServingOption,
            StartErrorMessage.fromApiFailure(
                ApiResult.HttpFailure(409, ApiError(code = "NO_SERVING_OPTION", message = "none")),
            ),
        )

        assertEquals(R.string.prepare_startErrors_NO_ATTEMPTS_LEFT, StartErrorMessage.messageRes(StartRefusal.NoAttemptsLeft))
        assertEquals(R.string.prepare_startErrors_CHOICE_NOT_ALLOWED, StartErrorMessage.messageRes(StartRefusal.ChoiceNotAllowed))
        assertEquals(R.string.prepare_startErrors_NO_SERVING_OPTION, StartErrorMessage.messageRes(StartRefusal.NoServingOption))
        assertEquals(R.string.prepare_startErrors_SUBJECTS_INVALID, StartErrorMessage.messageRes(StartRefusal.SubjectsInvalid))
        assertEquals(R.string.prepare_startErrors_RATE_LIMITED, StartErrorMessage.messageRes(StartRefusal.RateLimited))
        assertEquals(R.string.prepare_startErrors_generic, StartErrorMessage.messageRes(StartRefusal.Generic))

        assertTrue(StartErrorMessage.includesSupportContact(StartRefusal.NoServingOption))
        assertFalse(StartErrorMessage.includesSupportContact(StartRefusal.NoAttemptsLeft))
        assertFalse(StartErrorMessage.includesSupportContact(StartRefusal.ChoiceNotAllowed))
        assertFalse(StartErrorMessage.includesSupportContact(StartRefusal.SubjectsInvalid))
        assertFalse(StartErrorMessage.includesSupportContact(StartRefusal.RateLimited))
        assertFalse(StartErrorMessage.includesSupportContact(StartRefusal.Generic))
    }

    @Test
    fun mapsAllAbortReasonsAndUnknownToGeneric() {
        assertEquals(R.string.attempt_abortReasons_GENERATION_FAILED, AbortReasonMessage.messageRes("GENERATION_FAILED"))
        assertEquals(R.string.attempt_abortReasons_GENERATION_TIMEOUT, AbortReasonMessage.messageRes("GENERATION_TIMEOUT"))
        assertEquals(R.string.attempt_abortReasons_GENERATION_BUSY, AbortReasonMessage.messageRes("GENERATION_BUSY"))
        assertEquals(R.string.attempt_abortReasons_STALE_STARTING, AbortReasonMessage.messageRes("STALE_STARTING"))
        assertEquals(R.string.attempt_abortReasons_AUDIO_ASSET_MISSING, AbortReasonMessage.messageRes("AUDIO_ASSET_MISSING"))
        assertEquals(R.string.attempt_abortReasons_AUDIO_ASSET_INVALID, AbortReasonMessage.messageRes("AUDIO_ASSET_INVALID"))
        assertEquals(R.string.attempt_abortReasons_generic, AbortReasonMessage.messageRes("SOMETHING_NEW"))
        assertEquals(R.string.attempt_abortReasons_generic, AbortReasonMessage.messageRes(null))
    }
}
