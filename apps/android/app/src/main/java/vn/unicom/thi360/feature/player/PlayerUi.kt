package vn.unicom.thi360.feature.player

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import java.time.OffsetDateTime
import java.util.UUID
import vn.unicom.thi360.core.network.generated.model.AfterStartChoiceView
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.ChoiceOptionView
import vn.unicom.thi360.core.network.generated.model.ChoiceSubjectView
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.attemptengine.AnswerSyncStatus
import vn.unicom.thi360.feature.attemptengine.AttemptClosedReason
import vn.unicom.thi360.feature.attemptengine.AttemptCommandKind
import vn.unicom.thi360.feature.attemptengine.CommandState
import vn.unicom.thi360.feature.attemptengine.CountdownTarget
import vn.unicom.thi360.feature.attemptengine.EditSessionState
import vn.unicom.thi360.feature.attemptengine.EngineState
import vn.unicom.thi360.feature.attemptengine.ItemId
import vn.unicom.thi360.feature.attemptengine.NetworkStatus
import vn.unicom.thi360.feature.attemptengine.PartId
import vn.unicom.thi360.feature.attemptengine.PartSelection
import vn.unicom.thi360.feature.attemptengine.ReadOnlyReason
import vn.unicom.thi360.feature.attemptengine.SaveStatus
import vn.unicom.thi360.feature.attemptengine.UnconfirmedCommandKind
import vn.unicom.thi360.feature.question.model.QuestionMode

enum class PlayerLayout {
    Compact,
    Wide,
}

enum class GridFilter {
    All,
    Unanswered,
    Flagged,
}

enum class PlayerDialog {
    None,
    FinishPart,
    Submit,
    LeaveConfirm,
}

@Immutable
data class PartChoiceDraft(
    val optionCode: String? = null,
    val subjectCodes: List<String> = emptyList(),
)

@Immutable
data class PlayerLocalUi(
    val headerCollapsed: Boolean = false,
    val gridSheetOpen: Boolean = false,
    val gridFilter: GridFilter = GridFilter.All,
    val dialog: PlayerDialog = PlayerDialog.None,
    val choiceDraft: PartChoiceDraft = PartChoiceDraft(),
    val choiceDraftPartId: String? = null,
) {
    companion object {
        val Saver: Saver<PlayerLocalUi, List<Any?>> = Saver(
            save = { ui ->
                listOf(
                    ui.headerCollapsed,
                    ui.gridSheetOpen,
                    ui.gridFilter.name,
                    ui.dialog.name,
                    ui.choiceDraft.optionCode,
                    ui.choiceDraft.subjectCodes,
                    ui.choiceDraftPartId,
                )
            },
            restore = { raw ->
                @Suppress("UNCHECKED_CAST")
                PlayerLocalUi(
                    headerCollapsed = (raw.getOrNull(0) as? Boolean) ?: false,
                    gridSheetOpen = (raw.getOrNull(1) as? Boolean) ?: false,
                    gridFilter = (raw.getOrNull(2) as? String)
                        ?.let { name -> GridFilter.entries.firstOrNull { it.name == name } }
                        ?: GridFilter.All,
                    dialog = (raw.getOrNull(3) as? String)
                        ?.let { name -> PlayerDialog.entries.firstOrNull { it.name == name } }
                        ?: PlayerDialog.None,
                    choiceDraft = PartChoiceDraft(
                        optionCode = raw.getOrNull(4) as? String,
                        subjectCodes = (raw.getOrNull(5) as? List<String>).orEmpty(),
                    ),
                    choiceDraftPartId = raw.getOrNull(6) as? String,
                )
            },
        )
    }
}

enum class PlayerPrimarySurface {
    Loading,
    LoadError,
    Waiting,
    Closed,
    Evicted,
    Active,
}

enum class ReadOnlyBannerKind {
    None,
    Acquiring,
    SessionUnavailable,
    PartClosed,
}

@Immutable
sealed interface PlayerTopAction {
    @Immutable
    data class FinishPart(val partId: PartId) : PlayerTopAction

    @Immutable
    data object Submit : PlayerTopAction

    @Immutable
    data object None : PlayerTopAction
}

@Immutable
data class TimerUi(
    val visible: Boolean,
    val isShared: Boolean,
    val partOrd: Int?,
    val remainingMs: Long,
    val formatted: String,
    val parts: TimerParts,
    val isUrgent: Boolean,
)

@Immutable
data class PartTabUi(
    val partId: PartId,
    val ord: Int,
    val name: String,
    val state: AttemptPartStateView.State,
    val isSelected: Boolean,
    val isClickable: Boolean,
    val answeredCount: Int,
    val totalCount: Int,
)

@Immutable
data class GridCellUi(
    val itemId: ItemId,
    val displayNo: Int,
    val completion: ItemCompletion,
    val flagged: Boolean,
    val isCurrent: Boolean,
    val isUnsaved: Boolean,
    val subjectCode: String?,
)

@Immutable
data class FinishPartSummaryUi(
    val partId: PartId,
    val partName: String,
    val answeredCount: Int,
    val partialCount: Int,
    val missingCount: Int,
    val totalCount: Int,
    val nextPartNeedsChoice: Boolean,
    val hasNextPart: Boolean,
)

@Immutable
data class PartMissingRowUi(
    val partId: PartId,
    val ord: Int,
    val partName: String,
    val answeredCount: Int,
    val totalCount: Int,
    val missingCount: Int,
)

@Immutable
data class EssayStatusSummaryUi(
    val totalWords: Int,
    val hasUnsavedWords: Boolean,
)

@Immutable
data class EssayUnderLimitUi(
    val itemId: ItemId,
    val displayNo: Int,
    val wordCount: Int,
    val wordLimit: Int,
)

@Immutable
data class SubmitSummaryUi(
    val answeredCount: Int,
    val partialCount: Int,
    val blankCount: Int,
    val flaggedCount: Int,
    val totalCount: Int,
    val partBreakdown: List<PartMissingRowUi>,
    val essayStatus: EssayStatusSummaryUi?,
    val essayWarnings: List<EssayUnderLimitUi>,
)

@Immutable
data class ChoicePanelUi(
    val partId: PartId,
    val partOrd: Int,
    val partName: String,
    val metadataState: LoadState<PartChoiceMetadata>,
    val matchedChoice: AfterStartChoiceView?,
    val options: List<ChoiceOptionView>,
    val subjectsByCode: Map<String, ChoiceSubjectView>,
    val remainingMs: Long,
    val isTimerVisible: Boolean,
    val isUrgent: Boolean,
    val isExpired: Boolean,
    val selectedOptionCode: String?,
    val selectedSubjectCodes: List<String>,
    val requiredSubjectCount: Int,
    val canConfirm: Boolean,
    val isSending: Boolean,
    val errorCode: String?,
    val retryAfterSeconds: Long?,
)

@Immutable
data class GenerationPanelUi(
    val partId: PartId,
    val partName: String,
    val isFailed: Boolean,
    val chosenOptionName: String?,
    val isRetrying: Boolean,
    val errorCode: String?,
)

@Immutable
data class PlayerUi(
    val surface: PlayerPrimarySurface,
    val layout: PlayerLayout,
    val loadFailureKind: LoadFailureKind?,
    val canRetryLoad: Boolean,
    val attempt: AttemptStateResponse?,
    val examTitle: String,
    val closedReason: AttemptClosedReason?,
    val waitingInitialState: AttemptStateResponse.State?,
    val waitingAbortReason: String?,
    val showTakeoverDialog: Boolean,
    val takeoverLastSeenAt: OffsetDateTime?,
    val isTakeoverSending: Boolean,
    val takeoverErrorCode: String?,
    val isOffline: Boolean,
    val headerCollapsed: Boolean,
    val saveStatus: SaveStatus,
    val hasUnsavedChanges: Boolean,
    val timer: TimerUi,
    val topAction: PlayerTopAction,
    val topActionEnabled: Boolean,
    val readOnlyBanner: ReadOnlyBannerKind,
    val canEdit: Boolean,
    val questionMode: QuestionMode,
    val activePart: AttemptPartStateView?,
    val activePartDisplayName: String,
    val partTabs: List<PartTabUi>,
    val showPartTabs: Boolean,
    val unitGroups: List<UnitGroup>,
    val allGridCells: List<GridCellUi>,
    val filteredGridCells: List<GridCellUi>,
    val gridGroups: List<GridGroup>,
    val gridFilter: GridFilter,
    val gridSheetOpen: Boolean,
    val currentItemId: ItemId?,
    val currentDisplayIndex: Int,
    val answeredInActivePart: Int,
    val totalInActivePart: Int,
    val choicePanel: ChoicePanelUi?,
    val generationPanel: GenerationPanelUi?,
    val activeDialog: PlayerDialog,
    val finishPartSummary: FinishPartSummaryUi?,
    val submitSummary: SubmitSummaryUi?,
    val commandState: CommandState,
    val subjectsByCode: Map<String, ChoiceSubjectView>,
) {
    companion object {
        private const val OPTION_KIND_SUBJECT_CHOICE = "SUBJECT_CHOICE"

        fun of(
            state: EngineState,
            layout: PlayerLayout,
            local: PlayerLocalUi,
            partChoiceLoad: LoadState<PartChoiceMetadata> = LoadState.Loading,
            dismissedRefusal: CommandState.Refused? = null,
        ): PlayerUi {
            val attempt = state.attempt
            val surface = resolvePrimarySurface(state)
            val loadFailed = state.load as? LoadState.Failed
            val sortedParts = attempt?.let(PartSelection::sortedParts).orEmpty()

            val activePart = resolveActivePart(attempt, sortedParts, state.position?.partId)
            val activePartState = activePart?.state
            val isEditingSession = state.session is EditSessionState.Editing
            val canEdit = surface == PlayerPrimarySurface.Active &&
                isEditingSession &&
                activePartState == AttemptPartStateView.State.OPEN &&
                state.closed == null &&
                attempt?.state == AttemptStateResponse.State.IN_PROGRESS

            val readOnlyBanner = when {
                surface != PlayerPrimarySurface.Active -> ReadOnlyBannerKind.None
                state.session is EditSessionState.Acquiring -> ReadOnlyBannerKind.Acquiring
                state.session is EditSessionState.ReadOnly -> when (state.session.reason) {
                    ReadOnlyReason.PartClosed -> ReadOnlyBannerKind.PartClosed
                    ReadOnlyReason.SessionUnavailable -> ReadOnlyBannerKind.SessionUnavailable
                    ReadOnlyReason.AttemptNotInProgress,
                    ReadOnlyReason.AttemptClosed,
                    -> ReadOnlyBannerKind.None
                }
                activePartState == AttemptPartStateView.State.CLOSED -> ReadOnlyBannerKind.PartClosed
                else -> ReadOnlyBannerKind.None
            }

            val isFreeNavigation = attempt?.navigationMode == AttemptStateResponse.NavigationMode.FREE
            val partTabs = sortedParts.mapNotNull { part ->
                val partId = part.partId ?: return@mapNotNull null
                val pState = part.state ?: AttemptPartStateView.State.PENDING
                val leaves = PartSelection.leafItems(part)
                val answered = leaves.count { leaf ->
                    val id = leaf.itemId ?: return@count false
                    Completion.of(leaf, state.answers[id]?.value) == ItemCompletion.Answered
                }
                val isSelected = partId == activePart?.partId
                val isClickable = if (isFreeNavigation) {
                    PartSelection.isSelectableState(pState)
                } else {
                    isSelected
                }
                PartTabUi(
                    partId = partId,
                    ord = part.ord ?: 0,
                    name = part.partName.orEmpty(),
                    state = pState,
                    isSelected = isSelected,
                    isClickable = isClickable,
                    answeredCount = answered,
                    totalCount = leaves.size,
                )
            }

            val topAction = resolveTopAction(attempt, sortedParts, activePart)
            val isCommandSending = state.command is CommandState.Sending
            val topActionEnabled = topAction != PlayerTopAction.None &&
                canEdit &&
                !isCommandSending

            val timer = buildExamTimerUi(state, sortedParts, activePart)

            val unitGroups = if (activePart != null &&
                (activePartState == AttemptPartStateView.State.OPEN ||
                    activePartState == AttemptPartStateView.State.CLOSED)
            ) {
                UnitGrouping.buildGroups(activePart)
            } else {
                emptyList()
            }

            val activeLeaves = activePart?.let(PartSelection::leafItems).orEmpty()
            val currentItemId = resolveCurrentItemId(activeLeaves, state.position?.itemId)
            val allGridCells = activeLeaves.mapNotNull { leaf ->
                val id = leaf.itemId ?: return@mapNotNull null
                val localAns = state.answers[id]
                val completion = Completion.of(leaf, localAns?.value)
                val isUnsaved = localAns?.sync == AnswerSyncStatus.Pending ||
                    localAns?.sync == AnswerSyncStatus.Rejected
                GridCellUi(
                    itemId = id,
                    displayNo = leaf.displayNo ?: 0,
                    completion = completion,
                    flagged = localAns?.flagged == true,
                    isCurrent = id == currentItemId,
                    isUnsaved = isUnsaved,
                    subjectCode = leaf.componentCode?.trim()?.takeIf(String::isNotBlank),
                )
            }

            val filteredGridCells = when (local.gridFilter) {
                GridFilter.All -> allGridCells
                GridFilter.Unanswered -> allGridCells.filter { it.completion != ItemCompletion.Answered }
                GridFilter.Flagged -> allGridCells.filter { it.flagged }
            }

            val gridGroups = if (activePart != null) {
                UnitGrouping.groupGridCells(filteredGridCells, allGridCells, activePart)
            } else {
                emptyList()
            }

            val currentDisplayIndex = allGridCells.indexOfFirst { it.itemId == currentItemId }
                .let { idx -> if (idx >= 0) idx + 1 else if (allGridCells.isNotEmpty()) 1 else 0 }
            val answeredInActivePart = allGridCells.count { it.completion == ItemCompletion.Answered }

            val loadedMetadata = (partChoiceLoad as? LoadState.Loaded)?.value
            val subjectsByCode = loadedMetadata?.subjectsByCode.orEmpty()

            val effectiveRefusal = (state.command as? CommandState.Refused)
                ?.takeUnless { it == dismissedRefusal }

            val choicePanel = if (surface == PlayerPrimarySurface.Active &&
                activePart != null &&
                activePartState == AttemptPartStateView.State.AWAITING_CHOICE
            ) {
                buildChoicePanelUi(
                    state = state,
                    part = activePart,
                    local = local,
                    partChoiceLoad = partChoiceLoad,
                    refusal = effectiveRefusal,
                )
            } else {
                null
            }

            val generationPanel = if (surface == PlayerPrimarySurface.Active &&
                activePart != null &&
                (activePartState == AttemptPartStateView.State.GENERATING ||
                    activePartState == AttemptPartStateView.State.GENERATION_FAILED)
            ) {
                buildGenerationPanelUi(
                    state = state,
                    part = activePart,
                    metadata = loadedMetadata,
                    refusal = effectiveRefusal,
                )
            } else {
                null
            }

            val showTakeoverDialog = surface == PlayerPrimarySurface.Active &&
                state.session is EditSessionState.OpenElsewhere

            val effectiveDialog = when {
                showTakeoverDialog -> PlayerDialog.None
                state.command is CommandState.Unconfirmed -> when (state.command.kind) {
                    UnconfirmedCommandKind.FinishPart -> PlayerDialog.FinishPart
                    UnconfirmedCommandKind.Submit -> PlayerDialog.Submit
                }
                else -> local.dialog
            }

            val finishPartSummary = if (effectiveDialog == PlayerDialog.FinishPart && activePart != null) {
                buildFinishPartSummary(activePart, sortedParts, state)
            } else {
                null
            }

            val submitSummary = if (effectiveDialog == PlayerDialog.Submit && attempt != null) {
                buildSubmitSummary(sortedParts, state)
            } else {
                null
            }

            val activePartDisplayName = resolveActivePartLabel(activePart, loadedMetadata)

            val hasUnsavedChanges = state.save is SaveStatus.Saving ||
                state.save is SaveStatus.Unsaved ||
                state.answers.values.any {
                    it.sync == AnswerSyncStatus.Pending || it.sync == AnswerSyncStatus.Rejected
                }

            val isTakeoverSending = state.command is CommandState.Sending &&
                state.command.kind == AttemptCommandKind.Takeover

            return PlayerUi(
                surface = surface,
                layout = layout,
                loadFailureKind = loadFailed?.kind,
                canRetryLoad = loadFailed?.canRetry == true,
                attempt = attempt,
                examTitle = attempt?.examName.orEmpty(),
                closedReason = state.closed,
                waitingInitialState = attempt?.state?.takeIf {
                    it == AttemptStateResponse.State.STARTING || it == AttemptStateResponse.State.ABORTED
                },
                waitingAbortReason = attempt?.abortReason?.value,
                showTakeoverDialog = showTakeoverDialog,
                takeoverLastSeenAt = (state.session as? EditSessionState.OpenElsewhere)?.lastSeenAt,
                isTakeoverSending = isTakeoverSending,
                takeoverErrorCode = if (showTakeoverDialog || surface == PlayerPrimarySurface.Evicted) {
                    effectiveRefusal?.code
                } else {
                    null
                },
                isOffline = state.network == NetworkStatus.Offline,
                headerCollapsed = local.headerCollapsed,
                saveStatus = state.save,
                hasUnsavedChanges = hasUnsavedChanges,
                timer = timer,
                topAction = topAction,
                topActionEnabled = topActionEnabled,
                readOnlyBanner = readOnlyBanner,
                canEdit = canEdit,
                questionMode = if (canEdit) QuestionMode.Answering else QuestionMode.Locked,
                activePart = activePart,
                activePartDisplayName = activePartDisplayName,
                partTabs = partTabs,
                showPartTabs = partTabs.size > 1,
                unitGroups = unitGroups,
                allGridCells = allGridCells,
                filteredGridCells = filteredGridCells,
                gridGroups = gridGroups,
                gridFilter = local.gridFilter,
                gridSheetOpen = layout == PlayerLayout.Compact && local.gridSheetOpen,
                currentItemId = currentItemId,
                currentDisplayIndex = currentDisplayIndex,
                answeredInActivePart = answeredInActivePart,
                totalInActivePart = allGridCells.size,
                choicePanel = choicePanel,
                generationPanel = generationPanel,
                activeDialog = effectiveDialog,
                finishPartSummary = finishPartSummary,
                submitSummary = submitSummary,
                commandState = state.command,
                subjectsByCode = subjectsByCode,
            )
        }

        private fun resolvePrimarySurface(state: EngineState): PlayerPrimarySurface {
            val attempt = state.attempt
            if (attempt == null) {
                return when (state.load) {
                    is LoadState.Failed -> PlayerPrimarySurface.LoadError
                    else -> PlayerPrimarySurface.Loading
                }
            }
            return when {
                attempt.state == AttemptStateResponse.State.STARTING ||
                    attempt.state == AttemptStateResponse.State.ABORTED -> PlayerPrimarySurface.Waiting
                state.closed != null || PartSelection.isClosedState(attempt.state) ->
                    PlayerPrimarySurface.Closed
                state.session is EditSessionState.Evicted -> PlayerPrimarySurface.Evicted
                else -> PlayerPrimarySurface.Active
            }
        }

        private fun resolveActivePart(
            attempt: AttemptStateResponse?,
            sortedParts: List<AttemptPartStateView>,
            positionPartId: PartId?,
        ): AttemptPartStateView? {
            if (attempt == null || sortedParts.isEmpty()) return null
            if (positionPartId != null) {
                sortedParts.firstOrNull { it.partId == positionPartId }?.let { return it }
            }
            return PartSelection.afterClose(attempt)
        }

        private fun resolveCurrentItemId(
            leaves: List<AttemptItemView>,
            preferredId: ItemId?,
        ): ItemId? {
            if (leaves.isEmpty()) return null
            if (preferredId != null) {
                leaves.firstOrNull { it.itemId == preferredId }?.itemId?.let { return it }
            }
            return leaves.firstOrNull()?.itemId
        }

        private fun resolveTopAction(
            attempt: AttemptStateResponse?,
            sortedParts: List<AttemptPartStateView>,
            activePart: AttemptPartStateView?,
        ): PlayerTopAction {
            if (attempt == null || activePart == null) return PlayerTopAction.None
            if (activePart.state != AttemptPartStateView.State.OPEN) return PlayerTopAction.None
            val activePartId = activePart.partId ?: return PlayerTopAction.None
            if (attempt.navigationMode == AttemptStateResponse.NavigationMode.FREE) {
                return PlayerTopAction.Submit
            }
            val activeOrd = activePart.ord ?: 0
            val hasRemainingNextPart = sortedParts.any { part ->
                val ord = part.ord ?: 0
                ord > activeOrd &&
                    part.state != AttemptPartStateView.State.CLOSED &&
                    part.state != AttemptPartStateView.State.SKIPPED
            }
            return if (hasRemainingNextPart) {
                PlayerTopAction.FinishPart(activePartId)
            } else {
                PlayerTopAction.Submit
            }
        }

        private fun buildExamTimerUi(
            state: EngineState,
            sortedParts: List<AttemptPartStateView>,
            activePart: AttemptPartStateView?,
        ): TimerUi {
            val target = state.clock.target
            val visible = target is CountdownTarget.Attempt || target is CountdownTarget.Part
            val remainingMs = if (visible) state.clock.remainingMs.coerceAtLeast(0L) else 0L
            val displayOrd = if (target is CountdownTarget.Part && activePart != null) {
                val idx = sortedParts.indexOfFirst { it.partId == activePart.partId }
                if (idx >= 0) idx + 1 else ((activePart.ord ?: 0).takeIf { it > 0 } ?: 1)
            } else {
                null
            }
            return TimerUi(
                visible = visible,
                isShared = target is CountdownTarget.Attempt,
                partOrd = displayOrd,
                remainingMs = remainingMs,
                formatted = TimerText.format(remainingMs),
                parts = TimerText.parts(remainingMs),
                isUrgent = visible && TimerText.isUrgent(remainingMs, TimerText.EXAM_URGENT_THRESHOLD_MS),
            )
        }

        private fun buildChoicePanelUi(
            state: EngineState,
            part: AttemptPartStateView,
            local: PlayerLocalUi,
            partChoiceLoad: LoadState<PartChoiceMetadata>,
            refusal: CommandState.Refused?,
        ): ChoicePanelUi {
            val partId = part.partId ?: UUID(0L, 0L)
            val metadata = (partChoiceLoad as? LoadState.Loaded)?.value
            val matched = metadata?.choiceForPart(part)
            val options = matched?.options.orEmpty()
            val draftForPart = if (local.choiceDraftPartId == partId.toString()) {
                local.choiceDraft
            } else {
                PartChoiceDraft()
            }
            val selectedCode = draftForPart.optionCode
                ?.takeIf { code -> options.any { it.code == code } }
                ?: if (options.size == 1) options.first().code else null
            val selectedOption = options.firstOrNull { it.code == selectedCode }
            val requiredSubjects = if (selectedOption?.kind == OPTION_KIND_SUBJECT_CHOICE) {
                (selectedOption.chooseSubjects ?: 0).coerceAtLeast(0)
            } else {
                0
            }
            val validSubjectSet = selectedOption?.subjects.orEmpty()
                .mapNotNull { it.code }
                .toSet()
            val selectedSubjects = if (requiredSubjects > 0) {
                draftForPart.subjectCodes.filter { it in validSubjectSet }
            } else {
                emptyList()
            }

            val isChoiceCountdown = state.clock.target is CountdownTarget.Choice
            val remainingMs = if (isChoiceCountdown) state.clock.remainingMs.coerceAtLeast(0L) else 0L
            val isExpired = isChoiceCountdown && remainingMs <= 0L
            val isSending = state.command is CommandState.Sending &&
                state.command.kind == AttemptCommandKind.Choice
            val canConfirm = state.session is EditSessionState.Editing &&
                !isExpired &&
                !isSending &&
                selectedOption != null &&
                !selectedCode.isNullOrBlank() &&
                (requiredSubjects == 0 || selectedSubjects.size == requiredSubjects)

            return ChoicePanelUi(
                partId = partId,
                partOrd = part.ord ?: 3,
                partName = part.partName.orEmpty(),
                metadataState = partChoiceLoad,
                matchedChoice = matched,
                options = options,
                subjectsByCode = metadata?.subjectsByCode.orEmpty(),
                remainingMs = remainingMs,
                isTimerVisible = isChoiceCountdown || part.choiceDeadlineAt != null,
                isUrgent = isChoiceCountdown && TimerText.isUrgent(remainingMs, TimerText.CHOICE_URGENT_THRESHOLD_MS),
                isExpired = isExpired,
                selectedOptionCode = selectedCode,
                selectedSubjectCodes = selectedSubjects,
                requiredSubjectCount = requiredSubjects,
                canConfirm = canConfirm,
                isSending = isSending,
                errorCode = refusal?.code,
                retryAfterSeconds = refusal?.retryAfterSeconds,
            )
        }

        private fun buildGenerationPanelUi(
            state: EngineState,
            part: AttemptPartStateView,
            metadata: PartChoiceMetadata?,
            refusal: CommandState.Refused?,
        ): GenerationPanelUi {
            val partId = part.partId ?: UUID(0L, 0L)
            val parsedChoice = UnitGrouping.parseChoice(part.choice)
            val chosenName = parsedChoice.optionCode?.let { code ->
                metadata?.choiceForPart(part)
                    ?.options
                    .orEmpty()
                    .firstOrNull { it.code == code }
                    ?.name
                    ?: code
            }
            val isRetrying = state.command is CommandState.Sending &&
                state.command.kind == AttemptCommandKind.Retry
            return GenerationPanelUi(
                partId = partId,
                partName = part.partName.orEmpty(),
                isFailed = part.state == AttemptPartStateView.State.GENERATION_FAILED,
                chosenOptionName = chosenName,
                isRetrying = isRetrying,
                errorCode = refusal?.code,
            )
        }

        private fun resolveActivePartLabel(
            activePart: AttemptPartStateView?,
            metadata: PartChoiceMetadata?,
        ): String {
            if (activePart == null) return ""
            val parsed = UnitGrouping.parseChoice(activePart.choice)
            val optionName = parsed.optionCode?.let { code ->
                metadata?.choiceForPart(activePart)
                    ?.options
                    .orEmpty()
                    .firstOrNull { it.code == code }
                    ?.name
            }
            val baseName = activePart.partName.orEmpty()
            return if (!optionName.isNullOrBlank() && !baseName.contains(optionName, ignoreCase = true)) {
                "$baseName · $optionName"
            } else {
                baseName
            }
        }

        private fun buildFinishPartSummary(
            activePart: AttemptPartStateView,
            sortedParts: List<AttemptPartStateView>,
            state: EngineState,
        ): FinishPartSummaryUi {
            val leaves = PartSelection.leafItems(activePart)
            var answered = 0
            var partial = 0
            for (leaf in leaves) {
                val id = leaf.itemId ?: continue
                when (Completion.of(leaf, state.answers[id]?.value)) {
                    ItemCompletion.Answered -> answered += 1
                    ItemCompletion.Partial -> partial += 1
                    ItemCompletion.Blank -> Unit
                }
            }
            val total = leaves.size
            val missing = (total - answered).coerceAtLeast(0)
            val activeOrd = activePart.ord ?: 0
            val nextPart = sortedParts.firstOrNull { (it.ord ?: 0) > activeOrd }
            val nextNeedsChoice = nextPart?.state == AttemptPartStateView.State.AWAITING_CHOICE ||
                (nextPart != null && nextPart.items.isNullOrEmpty() && nextPart.choiceDeadlineAt != null) ||
                (nextPart?.state == AttemptPartStateView.State.PENDING && (nextPart.questionCount ?: 0) == 0 && nextPart.items.isNullOrEmpty())

            return FinishPartSummaryUi(
                partId = activePart.partId ?: UUID(0L, 0L),
                partName = activePart.partName.orEmpty(),
                answeredCount = answered,
                partialCount = partial,
                missingCount = missing,
                totalCount = total,
                nextPartNeedsChoice = nextNeedsChoice,
                hasNextPart = nextPart != null,
            )
        }

        private fun buildSubmitSummary(
            sortedParts: List<AttemptPartStateView>,
            state: EngineState,
        ): SubmitSummaryUi {
            var answered = 0
            var partial = 0
            var blank = 0
            var flagged = 0
            var total = 0

            val partBreakdown = mutableListOf<PartMissingRowUi>()
            var hasEssay = false
            var totalEssayWords = 0
            var hasUnsavedEssay = false
            val essayWarnings = mutableListOf<EssayUnderLimitUi>()

            for (part in sortedParts) {
                val leaves = PartSelection.leafItems(part)
                if (leaves.isEmpty()) continue
                var partAnswered = 0
                for (leaf in leaves) {
                    val id = leaf.itemId ?: continue
                    val localAns = state.answers[id]
                    total += 1
                    if (localAns?.flagged == true) flagged += 1
                    when (Completion.of(leaf, localAns?.value)) {
                        ItemCompletion.Answered -> {
                            answered += 1
                            partAnswered += 1
                        }
                        ItemCompletion.Partial -> partial += 1
                        ItemCompletion.Blank -> blank += 1
                    }

                    val essayInfo = Completion.essayLimit(leaf, localAns?.value)
                    if (essayInfo.isEssay) {
                        hasEssay = true
                        totalEssayWords += essayInfo.wordCount
                        if (localAns?.sync == AnswerSyncStatus.Pending ||
                            localAns?.sync == AnswerSyncStatus.Rejected
                        ) {
                            hasUnsavedEssay = true
                        }
                        if (essayInfo.underLimit && essayInfo.wordLimit != null) {
                            essayWarnings.add(
                                EssayUnderLimitUi(
                                    itemId = id,
                                    displayNo = leaf.displayNo ?: 0,
                                    wordCount = essayInfo.wordCount,
                                    wordLimit = essayInfo.wordLimit,
                                ),
                            )
                        }
                    }
                }
                val partMissing = (leaves.size - partAnswered).coerceAtLeast(0)
                if (partMissing > 0) {
                    partBreakdown.add(
                        PartMissingRowUi(
                            partId = part.partId ?: UUID(0L, 0L),
                            ord = part.ord ?: 0,
                            partName = part.partName.orEmpty(),
                            answeredCount = partAnswered,
                            totalCount = leaves.size,
                            missingCount = partMissing,
                        ),
                    )
                }
            }

            return SubmitSummaryUi(
                answeredCount = answered,
                partialCount = partial,
                blankCount = blank,
                flaggedCount = flagged,
                totalCount = total,
                partBreakdown = partBreakdown,
                essayStatus = if (hasEssay) {
                    EssayStatusSummaryUi(
                        totalWords = totalEssayWords,
                        hasUnsavedWords = hasUnsavedEssay,
                    )
                } else {
                    null
                },
                essayWarnings = essayWarnings,
            )
        }
    }
}
