package vn.unicom.thi360.feature.prepare

import androidx.annotation.StringRes
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.ApiResult

object StartErrorMessage {
    fun fromApiFailure(failure: ApiResult<*>): StartRefusal = when (failure) {
        is ApiResult.HttpFailure -> fromHttpFailure(failure.statusCode, failure.error?.code)
        is ApiResult.NetworkFailure -> StartRefusal.Generic
        is ApiResult.Success -> StartRefusal.Generic
    }

    fun fromHttpFailure(statusCode: Int, errorCode: String?): StartRefusal {
        if (statusCode == HTTP_TOO_MANY_REQUESTS || errorCode == CODE_RATE_LIMITED) {
            return StartRefusal.RateLimited
        }
        return when (errorCode) {
            CODE_NO_ATTEMPTS_LEFT -> StartRefusal.NoAttemptsLeft
            CODE_CHOICE_NOT_ALLOWED -> StartRefusal.ChoiceNotAllowed
            CODE_NO_SERVING_OPTION -> StartRefusal.NoServingOption
            CODE_SUBJECTS_INVALID -> StartRefusal.SubjectsInvalid
            else -> StartRefusal.Generic
        }
    }

    @StringRes
    fun messageRes(refusal: StartRefusal): Int = when (refusal) {
        StartRefusal.NoAttemptsLeft -> R.string.prepare_startErrors_NO_ATTEMPTS_LEFT
        StartRefusal.ChoiceNotAllowed -> R.string.prepare_startErrors_CHOICE_NOT_ALLOWED
        StartRefusal.NoServingOption -> R.string.prepare_startErrors_NO_SERVING_OPTION
        StartRefusal.SubjectsInvalid -> R.string.prepare_startErrors_SUBJECTS_INVALID
        StartRefusal.RateLimited -> R.string.prepare_startErrors_RATE_LIMITED
        StartRefusal.Generic -> R.string.prepare_startErrors_generic
    }

    fun includesSupportContact(refusal: StartRefusal): Boolean =
        refusal == StartRefusal.NoServingOption

    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val CODE_NO_ATTEMPTS_LEFT = "NO_ATTEMPTS_LEFT"
    private const val CODE_CHOICE_NOT_ALLOWED = "CHOICE_NOT_ALLOWED"
    private const val CODE_NO_SERVING_OPTION = "NO_SERVING_OPTION"
    private const val CODE_SUBJECTS_INVALID = "SUBJECTS_INVALID"
    private const val CODE_RATE_LIMITED = "RATE_LIMITED"
}

object AbortReasonMessage {
    @StringRes
    fun messageRes(abortReason: String?): Int = when (abortReason) {
        "GENERATION_FAILED" -> R.string.attempt_abortReasons_GENERATION_FAILED
        "GENERATION_TIMEOUT" -> R.string.attempt_abortReasons_GENERATION_TIMEOUT
        "GENERATION_BUSY" -> R.string.attempt_abortReasons_GENERATION_BUSY
        "STALE_STARTING" -> R.string.attempt_abortReasons_STALE_STARTING
        "AUDIO_ASSET_MISSING" -> R.string.attempt_abortReasons_AUDIO_ASSET_MISSING
        "AUDIO_ASSET_INVALID" -> R.string.attempt_abortReasons_AUDIO_ASSET_INVALID
        else -> R.string.attempt_abortReasons_generic
    }
}

object AnswerFormatLabel {
    @StringRes
    fun labelResOrNull(code: String): Int? = when (code) {
        "SINGLE_CHOICE" -> R.string.prepare_answerFormats_SINGLE_CHOICE
        "MULTIPLE_CHOICE" -> R.string.prepare_answerFormats_MULTIPLE_CHOICE
        "TRUE_FALSE" -> R.string.prepare_answerFormats_TRUE_FALSE
        "SHORT_ANSWER" -> R.string.prepare_answerFormats_SHORT_ANSWER
        "CLOZE" -> R.string.prepare_answerFormats_CLOZE
        "MATCHING" -> R.string.prepare_answerFormats_MATCHING
        "ESSAY" -> R.string.prepare_answerFormats_ESSAY
        "QUESTION_SET" -> R.string.prepare_answerFormats_QUESTION_SET
        else -> null
    }
}
