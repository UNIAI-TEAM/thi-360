package vn.unicom.thi360.feature.prepare

import java.util.UUID
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.ChoiceOptionView
import vn.unicom.thi360.core.network.generated.model.GrantSourceView
import vn.unicom.thi360.core.network.generated.model.OpenAttemptView
import vn.unicom.thi360.core.network.generated.model.PreChoiceInput
import vn.unicom.thi360.core.network.generated.model.PreChoiceView
import vn.unicom.thi360.core.ui.LoadState

const val PREPARE_BUILD_MODE_REVIEW = "REVIEW"
const val PREPARE_SERVING_PUBLISHED = "PUBLISHED"
const val PREPARE_OPTION_KIND_SUBJECT_CHOICE = "SUBJECT_CHOICE"

data class PreChoiceSelection(
    val optionCode: String? = null,
    val subjectCodes: Set<String> = emptySet(),
) {
    fun selectOption(code: String): PreChoiceSelection =
        PreChoiceSelection(optionCode = code, subjectCodes = emptySet())

    fun toggleSubject(subjectCode: String, maxSelect: Int): PreChoiceSelection {
        if (subjectCode in subjectCodes) {
            return copy(subjectCodes = subjectCodes - subjectCode)
        }
        if (maxSelect <= 0 || subjectCodes.size >= maxSelect) {
            return this
        }
        return copy(subjectCodes = linkedSetOf<String>().apply {
            addAll(subjectCodes)
            add(subjectCode)
        })
    }

    fun selectedOption(preChoice: PreChoiceView?): ChoiceOptionView? {
        val code = optionCode?.takeIf { it.isNotBlank() } ?: return null
        return preChoice?.options.orEmpty().firstOrNull { it.code == code }
    }

    fun toPreChoiceInput(response: AttemptPrepareResponse): PreChoiceInput? {
        val preChoice = response.preChoice ?: return null
        val selected = selectedOption(preChoice) ?: return null
        val code = selected.code?.takeIf { it.isNotBlank() } ?: return null
        val requiredSubjects = requiredSubjectCount(selected)
        return if (requiredSubjects > 0) {
            PreChoiceInput(optionCode = code, subjectCodes = subjectCodes.toList())
        } else {
            PreChoiceInput(optionCode = code, subjectCodes = null)
        }
    }

    companion object {
        fun initial(preChoice: PreChoiceView?): PreChoiceSelection {
            val options = preChoice?.options.orEmpty()
            if (options.isEmpty()) return PreChoiceSelection()
            val preselected = preChoice?.preselectedCode?.takeIf { it.isNotBlank() }
            if (preselected != null) {
                val matched = options.firstOrNull { it.code == preselected }
                if (!matched?.code.isNullOrBlank()) {
                    return PreChoiceSelection(optionCode = matched.code)
                }
            }
            if (options.size == 1) {
                val singleCode = options.first().code?.takeIf { it.isNotBlank() }
                if (singleCode != null) {
                    return PreChoiceSelection(optionCode = singleCode)
                }
            }
            return PreChoiceSelection()
        }

        fun requiredSubjectCount(option: ChoiceOptionView?): Int {
            if (option == null) return 0
            return if (option.kind == PREPARE_OPTION_KIND_SUBJECT_CHOICE) {
                (option.chooseSubjects ?: 0).coerceAtLeast(0)
            } else {
                0
            }
        }
    }
}

enum class DescriptionKind {
    Class,
    Published,
    Discontinued,
}

sealed interface StartGate {
    data object Open : StartGate
    data class InProgress(val attemptId: UUID) : StartGate
    data class Exhausted(val descriptionKind: DescriptionKind, val canBuyMore: Boolean) : StartGate
    data object NoServingOption : StartGate
    data object NeedOption : StartGate
    data class NeedSubjects(val count: Int) : StartGate
}

fun resolveStartGate(
    response: AttemptPrepareResponse,
    selection: PreChoiceSelection,
): StartGate {
    val open = response.openAttempt
    if (open?.state == OpenAttemptView.State.IN_PROGRESS && open.attemptId != null) {
        return StartGate.InProgress(open.attemptId)
    }

    if (response.attemptsLeft <= 0) {
        val isClass = response.grantSource?.source == GrantSourceView.Source.CLASS
        val isPublished = response.servingStatus == PREPARE_SERVING_PUBLISHED
        val kind = when {
            isClass -> DescriptionKind.Class
            isPublished -> DescriptionKind.Published
            else -> DescriptionKind.Discontinued
        }
        return StartGate.Exhausted(
            descriptionKind = kind,
            canBuyMore = isPublished && !isClass,
        )
    }

    val preChoice = response.preChoice
    if (preChoice != null) {
        val options = preChoice.options.orEmpty()
        if (options.isEmpty()) {
            return StartGate.NoServingOption
        }
        val selected = selection.selectedOption(preChoice) ?: return StartGate.NeedOption
        val requiredSubjects = PreChoiceSelection.requiredSubjectCount(selected)
        if (requiredSubjects > 0 && selection.subjectCodes.size != requiredSubjects) {
            return StartGate.NeedSubjects(requiredSubjects)
        }
    }

    return StartGate.Open
}

sealed interface StartRefusal {
    data object NoAttemptsLeft : StartRefusal
    data object ChoiceNotAllowed : StartRefusal
    data object NoServingOption : StartRefusal
    data object SubjectsInvalid : StartRefusal
    data object RateLimited : StartRefusal
    data object Generic : StartRefusal
}

sealed interface StartState {
    data object Idle : StartState
    data object Sending : StartState
    data class Refused(val refusal: StartRefusal) : StartState
}

sealed interface StartOutcome {
    sealed interface OpenedState {
        data object Starting : OpenedState
        data object InProgress : OpenedState
        data class Aborted(val abortReason: String?) : OpenedState
        data object Closed : OpenedState
    }

    data class Opened(val attemptId: UUID, val state: OpenedState) : StartOutcome
    data class Refused(val refusal: StartRefusal) : StartOutcome
}

sealed interface WaitingPhase {
    data class Polling(val elapsedMs: Long) : WaitingPhase
    data object Busy : WaitingPhase
    data class Failed(val abortReason: String?) : WaitingPhase
    data object Retrying : WaitingPhase
    data class RetryRefused(val refusal: StartRefusal) : WaitingPhase
}

enum class WaitingOrigin {
    Prepare,
    Player,
}

enum class PrepareTimingDisplay {
    Free,
    PerPart,
    Shared,
}

fun resolvePrepareTimingDisplay(response: AttemptPrepareResponse): PrepareTimingDisplay = when {
    response.navigationMode == AttemptPrepareResponse.NavigationMode.FREE -> PrepareTimingDisplay.Free
    response.timing == AttemptPrepareResponse.Timing.PER_PART -> PrepareTimingDisplay.PerPart
    else -> PrepareTimingDisplay.Shared
}

data class PrepareScreenState(
    val load: LoadState<AttemptPrepareResponse> = LoadState.Loading,
    val selection: PreChoiceSelection = PreChoiceSelection(),
    val gate: StartGate = StartGate.Open,
    val start: StartState = StartState.Idle,
    val waiting: WaitingPhase? = null,
    val waitingAttemptId: UUID? = null,
    val waitingContext: StartContext? = null,
    val waitingInMemoryPreChoice: PreChoiceInput? = null,
    val waitingAbortReason: String? = null,
)
