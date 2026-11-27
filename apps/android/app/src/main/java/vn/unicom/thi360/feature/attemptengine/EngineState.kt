package vn.unicom.thi360.feature.attemptengine

import java.time.OffsetDateTime
import java.util.UUID
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.core.ui.LoadState

typealias ItemId = UUID
typealias PartId = UUID

enum class AnswerSyncStatus {
    Saved,
    Pending,
    Rejected,
    Locked,
}

data class LocalAnswer(
    val value: AnswerValue?,
    val seq: Long,
    val flagged: Boolean,
    val sync: AnswerSyncStatus,
    val rejectedReason: String? = null,
) {
    override fun toString(): String =
        "LocalAnswer(value=[REDACTED], seq=$seq, flagged=$flagged, sync=$sync, rejectedReason=$rejectedReason)"
}

data class EnginePosition(
    val partId: PartId,
    val itemId: ItemId?,
)

sealed interface EditSessionState {
    data object Acquiring : EditSessionState

    data class Editing(val sessionId: UUID) : EditSessionState {
        override fun toString(): String = "Editing(sessionId=[REDACTED])"
    }

    data class OpenElsewhere(val lastSeenAt: OffsetDateTime? = null) : EditSessionState

    data object Evicted : EditSessionState

    data class ReadOnly(val reason: ReadOnlyReason) : EditSessionState
}

enum class ReadOnlyReason {
    AttemptNotInProgress,
    AttemptClosed,
    PartClosed,
    SessionUnavailable,
}

sealed interface CountdownTarget {
    data object Attempt : CountdownTarget
    data class Part(val partId: PartId) : CountdownTarget
    data class Choice(val partId: PartId) : CountdownTarget
    data object None : CountdownTarget
}

data class Countdown(
    val target: CountdownTarget = CountdownTarget.None,
    val remainingMs: Long = 0L,
)

sealed interface UnsavedReason {
    data object NetworkError : UnsavedReason
    data object ServerError : UnsavedReason
    data class RateLimited(val retryAfterSeconds: Long) : UnsavedReason
    data object Rejected : UnsavedReason
    data object SessionConflict : UnsavedReason
}

sealed interface SaveStatus {
    data object Saved : SaveStatus
    data object Saving : SaveStatus
    data class Unsaved(val reason: UnsavedReason) : SaveStatus
    data object Offline : SaveStatus
}

enum class NetworkStatus {
    Online,
    Offline,
}

enum class AttemptCommandKind {
    FinishPart,
    Submit,
    Choice,
    Retry,
    Takeover,
}

enum class UnconfirmedCommandKind {
    FinishPart,
    Submit,
}

sealed interface CommandState {
    data object Idle : CommandState
    data class Sending(val kind: AttemptCommandKind) : CommandState
    data class Unconfirmed(val kind: UnconfirmedCommandKind) : CommandState
    data class Refused(
        val code: String,
        val retryAfterSeconds: Long? = null,
    ) : CommandState
}

sealed interface AttemptClosedReason {
    data object TimeUp : AttemptClosedReason
    data object Submitted : AttemptClosedReason
    data object ChoiceTimeout : AttemptClosedReason
    data class Aborted(val reason: AttemptStateResponse.AbortReason? = null) : AttemptClosedReason
    data object NotOpen : AttemptClosedReason
}

sealed interface CloseTarget {
    data class Part(val partId: PartId) : CloseTarget
    data object Attempt : CloseTarget
}

sealed interface EngineEvent {
    data class BeforeClose(val target: CloseTarget) : EngineEvent
    data class PartClosed(val partId: PartId) : EngineEvent
    data class AttemptClosed(val reason: AttemptClosedReason) : EngineEvent
    data object SessionLost : EngineEvent
}

sealed interface SaveSchedule {
    data object Immediate : SaveSchedule
    data class Debounced(val delayMs: Long) : SaveSchedule
    data class Essay(val debounceMs: Long, val maxWaitMs: Long) : SaveSchedule
}

fun AnswerValue.saveSchedule(): SaveSchedule = when (this) {
    is SingleChoiceAnswer,
    is MultipleChoiceAnswer,
    is TrueFalseAnswer,
    is MatchingAnswer,
    -> SaveSchedule.Immediate
    is ShortAnswerValue,
    is ClozeAnswer,
    -> SaveSchedule.Debounced(delayMs = SAVE_DEBOUNCE_SHORT_MS)
    is EssayAnswer -> SaveSchedule.Essay(
        debounceMs = SAVE_DEBOUNCE_ESSAY_MS,
        maxWaitMs = SAVE_ESSAY_MAX_WAIT_MS,
    )
}

data class EngineState(
    val load: LoadState<Unit> = LoadState.Loading,
    val attempt: AttemptStateResponse? = null,
    val answers: Map<ItemId, LocalAnswer> = emptyMap(),
    val position: EnginePosition? = null,
    val session: EditSessionState = EditSessionState.Acquiring,
    val clock: Countdown = Countdown(),
    val save: SaveStatus = SaveStatus.Saved,
    val network: NetworkStatus = NetworkStatus.Online,
    val command: CommandState = CommandState.Idle,
    val closed: AttemptClosedReason? = null,
) {
    override fun toString(): String =
        "EngineState(load=$load, attemptId=${attempt?.attemptId}, state=${attempt?.state}, " +
            "answersCount=${answers.size}, position=$position, session=$session, clock=$clock, " +
            "save=$save, network=$network, command=$command, closed=$closed)"
}

internal const val SAVE_DEBOUNCE_SHORT_MS = 800L
internal const val SAVE_DEBOUNCE_ESSAY_MS = 2_000L
internal const val SAVE_ESSAY_MAX_WAIT_MS = 15_000L
internal const val POSITION_DEBOUNCE_MS = 400L
internal const val SAVE_BATCH_SIZE = 50
internal const val FINAL_ANSWERS_LIMIT = 500
internal const val HEARTBEAT_INTERVAL_MS = 30_000L
internal const val DEADLINE_RELOAD_DELAY_MS = 500L
internal const val CHOICE_EXPIRED_INITIAL_POLL_MS = 500L
internal const val CHOICE_EXPIRED_REPEAT_POLL_MS = 2_000L
internal const val GENERATING_POLL_INTERVAL_MS = 1_500L
internal const val CLOCK_TICK_INTERVAL_MS = 1_000L
