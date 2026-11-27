package vn.unicom.thi360.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.attemptengine.AttemptClosedReason
import vn.unicom.thi360.feature.attemptengine.AttemptEngine
import vn.unicom.thi360.feature.attemptengine.CommandState
import vn.unicom.thi360.feature.attemptengine.EngineEvent
import vn.unicom.thi360.feature.attemptengine.EngineState
import vn.unicom.thi360.feature.attemptengine.ItemId
import vn.unicom.thi360.feature.attemptengine.PartId
import vn.unicom.thi360.feature.prepare.PrepareApi
import vn.unicom.thi360.feature.prepare.StartContext
import vn.unicom.thi360.feature.prepare.StartContextStore
import vn.unicom.thi360.feature.prepare.WaitingOrigin
import vn.unicom.thi360.feature.prepare.waiting.AttemptWaitingScreen
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine

private val WIDE_LAYOUT_BREAKPOINT_DP = 840.dp
private val TABLET_SIDEBAR_BREAKPOINT_DP = 1100.dp
private val FOLD_SIDEBAR_WIDTH_DP = 236.dp
private val TABLET_SIDEBAR_WIDTH_DP = 320.dp

@Composable
fun PlayerHost(
    attemptId: String,
    engine: AttemptEngine,
    onCloseAttempt: () -> Unit,
    onBackToMyExams: () -> Unit,
    onViewResult: () -> Unit,
    onReplaceAttempt: (String) -> Unit,
    onOpenPrepare: (String) -> Unit,
    modifier: Modifier = Modifier,
    partChoiceLoader: PartChoiceOptionsLoader? = null,
    prepareApi: PrepareApi? = null,
    startContextStore: StartContextStore? = null,
    waitingClockMillis: () -> Long = System::currentTimeMillis,
    assets: AttemptAssetSource? = null,
    formulaEngine: FormulaEngine? = null,
    busyNoticeDelayMs: Long = GENERATION_BUSY_NOTICE_DELAY_MS,
) {
    val engineState by engine.state.collectAsStateWithLifecycle()
    val attachmentHost = LocalPlayerAttachmentHost.current

    var localUi by rememberSaveable(attemptId, stateSaver = PlayerLocalUi.Saver) {
        mutableStateOf(PlayerLocalUi())
    }
    var partChoiceLoad by remember(attemptId) {
        mutableStateOf<LoadState<PartChoiceMetadata>>(LoadState.Loading)
    }
    var choiceLoadRetryNonce by remember(attemptId) { mutableLongStateOf(0L) }
    var dismissedRefusal by remember(attemptId) { mutableStateOf<CommandState.Refused?>(null) }
    var scrollRequest by remember(attemptId) { mutableStateOf<ScrollToItemRequest?>(null) }

    LaunchedEffect(engine, attachmentHost) {
        if (attachmentHost == null) return@LaunchedEffect
        withContext(Dispatchers.Unconfined) {
            engine.events.collect { event ->
                if (event is EngineEvent.BeforeClose) {
                    attachmentHost.onBeforeClose(event.target)
                }
            }
        }
    }

    LaunchedEffect(engine) {
        engine.events.collect { event ->
            if (event is EngineEvent.PartClosed || event is EngineEvent.AttemptClosed) {
                localUi = localUi.copy(dialog = PlayerDialog.None, gridSheetOpen = false)
            }
        }
    }

    val needsChoiceMetadata = remember(engineState.attempt) {
        engineState.attempt?.parts.orEmpty().any { part ->
            part.state == AttemptPartStateView.State.AWAITING_CHOICE ||
                part.state == AttemptPartStateView.State.GENERATING ||
                part.state == AttemptPartStateView.State.GENERATION_FAILED ||
                !part.choice.isNullOrEmpty()
        }
    }

    LaunchedEffect(attemptId, needsChoiceMetadata, partChoiceLoader, choiceLoadRetryNonce) {
        if (!needsChoiceMetadata) return@LaunchedEffect
        val loader = partChoiceLoader ?: run {
            partChoiceLoad = LoadState.Failed(kind = LoadFailureKind.Server, canRetry = true)
            return@LaunchedEffect
        }
        if (choiceLoadRetryNonce > 0L || partChoiceLoad !is LoadState.Loaded) {
            partChoiceLoad = LoadState.Loading
            partChoiceLoad = loader.load(engine.attemptId)
        }
    }

    val startContext = remember(attemptId, startContextStore) {
        startContextStore?.read(attemptId)
    }

    PlayerScreen(
        attemptId = attemptId,
        engineState = engineState,
        localUi = localUi,
        partChoiceLoad = partChoiceLoad,
        dismissedRefusal = dismissedRefusal,
        scrollRequest = scrollRequest,
        startContext = startContext,
        prepareApi = prepareApi,
        startContextStore = startContextStore,
        waitingClockMillis = waitingClockMillis,
        assets = assets,
        formulaEngine = formulaEngine,
        busyNoticeDelayMs = busyNoticeDelayMs,
        onRetryLoad = engine::refresh,
        onWaitingReady = engine::refresh,
        onCloseAttempt = onCloseAttempt,
        onBackToMyExams = onBackToMyExams,
        onViewResult = onViewResult,
        onReplaceAttempt = onReplaceAttempt,
        onOpenPrepare = onOpenPrepare,
        onToggleCollapse = { localUi = localUi.copy(headerCollapsed = !localUi.headerCollapsed) },
        onSelectPart = { partId ->
            val targetPart = engineState.attempt?.parts.orEmpty().firstOrNull { it.partId == partId }
            val firstLeaf = targetPart?.let { vn.unicom.thi360.feature.attemptengine.PartSelection.leafItems(it).firstOrNull()?.itemId }
            engine.position(partId, firstLeaf)
            localUi = localUi.copy(gridFilter = GridFilter.All, gridSheetOpen = false)
        },
        onAnswer = engine::answer,
        onFlag = engine::flag,
        onVisibleItemChanged = { partId, itemId ->
            if (engineState.position?.partId != partId || engineState.position?.itemId != itemId) {
                engine.position(partId, itemId)
            }
        },
        onOpenGridSheet = { localUi = localUi.copy(gridSheetOpen = true) },
        onCloseGridSheet = { localUi = localUi.copy(gridSheetOpen = false) },
        onSelectGridFilter = { filter -> localUi = localUi.copy(gridFilter = filter) },
        onSelectGridItem = { itemId ->
            val partId = engineState.position?.partId ?: engineState.attempt?.parts.orEmpty()
                .firstOrNull { p -> p.items.orEmpty().any { it.itemId == itemId } }
                ?.partId
            if (partId != null) {
                engine.position(partId, itemId)
            }
            scrollRequest = ScrollToItemRequest(
                itemId = itemId,
                nonce = (scrollRequest?.nonce ?: 0L) + 1L,
            )
        },
        onOpenDialog = { dialog ->
            dismissedRefusal = engineState.command as? CommandState.Refused
            localUi = localUi.copy(dialog = dialog)
        },
        onDismissDialog = {
            dismissedRefusal = engineState.command as? CommandState.Refused
            localUi = localUi.copy(dialog = PlayerDialog.None)
        },
        onConfirmFinishPart = { partId ->
            dismissedRefusal = null
            engine.finishPart(partId)
        },
        onConfirmSubmit = {
            dismissedRefusal = null
            engine.submit()
        },
        onReconcile = engine::reconcile,
        onTakeover = {
            dismissedRefusal = null
            engine.takeover()
        },
        onSelectChoiceOption = { partId, optionCode ->
            dismissedRefusal = engineState.command as? CommandState.Refused
            localUi = localUi.copy(
                choiceDraftPartId = partId.toString(),
                choiceDraft = PartChoiceDraft(optionCode = optionCode, subjectCodes = emptyList()),
            )
        },
        onToggleChoiceSubject = { partId, subjectCode, maxSelect ->
            dismissedRefusal = engineState.command as? CommandState.Refused
            val current = if (localUi.choiceDraftPartId == partId.toString()) {
                localUi.choiceDraft
            } else {
                PartChoiceDraft()
            }
            val nextSubjects = if (subjectCode in current.subjectCodes) {
                current.subjectCodes - subjectCode
            } else if (maxSelect > 0 && current.subjectCodes.size < maxSelect) {
                current.subjectCodes + subjectCode
            } else {
                current.subjectCodes
            }
            localUi = localUi.copy(
                choiceDraftPartId = partId.toString(),
                choiceDraft = current.copy(subjectCodes = nextSubjects),
            )
        },
        onConfirmPartChoice = { partId, optionCode, subjects ->
            dismissedRefusal = null
            engine.choosePart(
                partId = partId,
                optionCode = optionCode,
                subjectCodes = subjects.takeIf { it.isNotEmpty() },
            )
        },
        onRetryLoadChoiceOptions = {
            choiceLoadRetryNonce += 1L
        },
        onRetryGeneration = { partId ->
            dismissedRefusal = null
            engine.retryGeneration(partId)
        },
        modifier = modifier,
    )
}

@Composable
fun PlayerScreen(
    attemptId: String,
    engineState: EngineState,
    localUi: PlayerLocalUi,
    modifier: Modifier = Modifier,
    partChoiceLoad: LoadState<PartChoiceMetadata> = LoadState.Loading,
    dismissedRefusal: CommandState.Refused? = null,
    scrollRequest: ScrollToItemRequest? = null,
    startContext: StartContext? = null,
    prepareApi: PrepareApi? = null,
    startContextStore: StartContextStore? = null,
    waitingClockMillis: () -> Long = System::currentTimeMillis,
    assets: AttemptAssetSource? = null,
    formulaEngine: FormulaEngine? = null,
    busyNoticeDelayMs: Long = GENERATION_BUSY_NOTICE_DELAY_MS,
    onRetryLoad: () -> Unit = {},
    onWaitingReady: () -> Unit = {},
    onCloseAttempt: () -> Unit = {},
    onBackToMyExams: () -> Unit = {},
    onViewResult: () -> Unit = {},
    onReplaceAttempt: (String) -> Unit = {},
    onOpenPrepare: (String) -> Unit = {},
    onToggleCollapse: () -> Unit = {},
    onSelectPart: (PartId) -> Unit = {},
    onAnswer: (ItemId, AnswerValue) -> Unit = { _, _ -> },
    onFlag: (ItemId, Boolean) -> Unit = { _, _ -> },
    onVisibleItemChanged: (PartId, ItemId) -> Unit = { _, _ -> },
    onOpenGridSheet: () -> Unit = {},
    onCloseGridSheet: () -> Unit = {},
    onSelectGridFilter: (GridFilter) -> Unit = {},
    onSelectGridItem: (ItemId) -> Unit = {},
    onOpenDialog: (PlayerDialog) -> Unit = {},
    onDismissDialog: () -> Unit = {},
    onConfirmFinishPart: (PartId) -> Unit = {},
    onConfirmSubmit: () -> Unit = {},
    onReconcile: () -> Unit = {},
    onTakeover: () -> Unit = {},
    onSelectChoiceOption: (PartId, String) -> Unit = { _, _ -> },
    onToggleChoiceSubject: (PartId, String, Int) -> Unit = { _, _, _ -> },
    onConfirmPartChoice: (PartId, String, List<String>) -> Unit = { _, _, _ -> },
    onRetryLoadChoiceOptions: () -> Unit = {},
    onRetryGeneration: (PartId) -> Unit = {},
) {
    val colors = Thi360Theme.colors
    val pageTitle = stringResource(R.string.attempt_player_pageTitle)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .semantics { paneTitle = pageTitle }
            .testTag(PlayerTestTags.SCREEN),
    ) {
        val layout = if (maxWidth >= WIDE_LAYOUT_BREAKPOINT_DP) {
            PlayerLayout.Wide
        } else {
            PlayerLayout.Compact
        }
        val sidebarWidth = if (maxWidth >= TABLET_SIDEBAR_BREAKPOINT_DP) {
            TABLET_SIDEBAR_WIDTH_DP
        } else {
            FOLD_SIDEBAR_WIDTH_DP
        }

        val ui = remember(engineState, layout, localUi, partChoiceLoad, dismissedRefusal) {
            PlayerUi.of(
                state = engineState,
                layout = layout,
                local = localUi,
                partChoiceLoad = partChoiceLoad,
                dismissedRefusal = dismissedRefusal,
            )
        }

        val handleBackNavigation: () -> Unit = {
            when {
                ui.gridSheetOpen -> onCloseGridSheet()
                ui.activeDialog != PlayerDialog.None -> {
                    val isBusy = ui.commandState is CommandState.Sending
                    if (!isBusy) onDismissDialog()
                }
                ui.showTakeoverDialog -> {
                    if (!ui.isTakeoverSending) onBackToMyExams()
                }
                ui.surface == PlayerPrimarySurface.Active && ui.hasUnsavedChanges -> {
                    onOpenDialog(PlayerDialog.LeaveConfirm)
                }
                else -> onCloseAttempt()
            }
        }

        if (ui.surface != PlayerPrimarySurface.Waiting) {
            BackHandler(onBack = handleBackNavigation)
        }

        when (ui.surface) {
            PlayerPrimarySurface.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .testTag(PlayerTestTags.LOADING),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.primaryFill)
                }
            }

            PlayerPrimarySurface.LoadError -> {
                PlayerLoadErrorScreen(
                    isNotFound = ui.loadFailureKind == LoadFailureKind.NotFound,
                    canRetry = ui.canRetryLoad,
                    onRetry = onRetryLoad,
                    onBackToMyExams = onBackToMyExams,
                )
            }

            PlayerPrimarySurface.Waiting -> {
                AttemptWaitingScreen(
                    attemptId = attemptId,
                    examTitle = ui.examTitle,
                    context = startContext,
                    origin = WaitingOrigin.Player,
                    onReady = { onWaitingReady() },
                    onRestarted = onReplaceAttempt,
                    onOpenPrepare = onOpenPrepare,
                    onMyExams = onBackToMyExams,
                    initialState = ui.waitingInitialState ?: AttemptStateResponse.State.STARTING,
                    initialAbortReason = ui.waitingAbortReason,
                    prepareApi = prepareApi,
                    startContextStore = startContextStore,
                    clockMillis = waitingClockMillis,
                    onBack = onCloseAttempt,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                )
            }

            PlayerPrimarySurface.Closed -> {
                PlayerClosedScreen(
                    reason = ui.closedReason,
                    examTitle = ui.examTitle,
                    onViewResult = onViewResult,
                    onBackToMyExams = onBackToMyExams,
                )
            }

            PlayerPrimarySurface.Evicted -> {
                PlayerEvictedScreen(
                    examTitle = ui.examTitle,
                    isSending = ui.isTakeoverSending,
                    errorCode = ui.takeoverErrorCode,
                    onTakeover = onTakeover,
                    onBackToMyExams = onBackToMyExams,
                )
            }

            PlayerPrimarySurface.Active -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    PlayerTopBar(
                        ui = ui,
                        onBack = handleBackNavigation,
                        onToggleCollapse = onToggleCollapse,
                        onActionClick = {
                            when (ui.topAction) {
                                is PlayerTopAction.FinishPart -> onOpenDialog(PlayerDialog.FinishPart)
                                PlayerTopAction.Submit -> onOpenDialog(PlayerDialog.Submit)
                                PlayerTopAction.None -> Unit
                            }
                        },
                        onSelectPart = onSelectPart,
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        when {
                            ui.choicePanel != null -> {
                                val panel = ui.choicePanel
                                PartChoicePanel(
                                    panel = panel,
                                    onSelectOption = { code -> onSelectChoiceOption(panel.partId, code) },
                                    onToggleSubject = { code ->
                                        onToggleChoiceSubject(panel.partId, code, panel.requiredSubjectCount)
                                    },
                                    onConfirm = {
                                        val code = panel.selectedOptionCode
                                        if (!code.isNullOrBlank()) {
                                            onConfirmPartChoice(
                                                panel.partId,
                                                code,
                                                panel.selectedSubjectCodes,
                                            )
                                        }
                                    },
                                    onRetryLoadOptions = onRetryLoadChoiceOptions,
                                    onBackToMyExams = onBackToMyExams,
                                )
                            }

                            ui.generationPanel != null -> {
                                val panel = ui.generationPanel
                                GenerationPanel(
                                    panel = panel,
                                    onRetry = { onRetryGeneration(panel.partId) },
                                    busyNoticeDelayMs = busyNoticeDelayMs,
                                )
                            }

                            layout == PlayerLayout.Wide -> {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    PlayerContent(
                                        ui = ui,
                                        answers = engineState.answers,
                                        scrollRequest = scrollRequest,
                                        assets = assets,
                                        formulaEngine = formulaEngine,
                                        onAnswer = onAnswer,
                                        onFlag = onFlag,
                                        onVisibleItemChanged = onVisibleItemChanged,
                                        modifier = Modifier.weight(1f),
                                    )
                                    QuestionGridSidebar(
                                        ui = ui,
                                        sidebarWidth = sidebarWidth,
                                        onSelectFilter = onSelectGridFilter,
                                        onSelectItem = onSelectGridItem,
                                    )
                                }
                            }

                            else -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    PlayerContent(
                                        ui = ui,
                                        answers = engineState.answers,
                                        scrollRequest = scrollRequest,
                                        assets = assets,
                                        formulaEngine = formulaEngine,
                                        onAnswer = onAnswer,
                                        onFlag = onFlag,
                                        onVisibleItemChanged = onVisibleItemChanged,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (ui.totalInActivePart > 0) {
                                        PlayerNarrowBottomBar(
                                            currentIndex = ui.currentDisplayIndex,
                                            answeredCount = ui.answeredInActivePart,
                                            totalCount = ui.totalInActivePart,
                                            onOpenGrid = onOpenGridSheet,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (ui.gridSheetOpen) {
                    QuestionGridSheet(
                        ui = ui,
                        onDismiss = onCloseGridSheet,
                        onSelectFilter = onSelectGridFilter,
                        onSelectItem = onSelectGridItem,
                    )
                }

                if (ui.showTakeoverDialog) {
                    TakeoverDialog(
                        examTitle = ui.examTitle,
                        lastSeenAt = ui.takeoverLastSeenAt,
                        isSending = ui.isTakeoverSending,
                        errorCode = ui.takeoverErrorCode,
                        onConfirm = onTakeover,
                        onCancel = onBackToMyExams,
                    )
                }

                val dialogRefusal = (ui.commandState as? CommandState.Refused)
                    ?.takeUnless { it == dismissedRefusal }

                when (ui.activeDialog) {
                    PlayerDialog.FinishPart -> {
                        val summary = ui.finishPartSummary
                        if (summary != null) {
                            FinishPartDialog(
                                summary = summary,
                                commandState = ui.commandState,
                                refusalError = dialogRefusal,
                                onConfirm = { onConfirmFinishPart(summary.partId) },
                                onReconcile = onReconcile,
                                onDismiss = onDismissDialog,
                            )
                        }
                    }

                    PlayerDialog.Submit -> {
                        val summary = ui.submitSummary
                        if (summary != null) {
                            SubmitDialog(
                                summary = summary,
                                commandState = ui.commandState,
                                refusalError = dialogRefusal,
                                onConfirm = onConfirmSubmit,
                                onReconcile = onReconcile,
                                onDismiss = onDismissDialog,
                            )
                        }
                    }

                    PlayerDialog.LeaveConfirm -> {
                        LeaveConfirmDialog(
                            onStay = onDismissDialog,
                            onLeave = {
                                onDismissDialog()
                                onCloseAttempt()
                            },
                        )
                    }

                    PlayerDialog.None -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerLoadErrorScreen(
    isNotFound: Boolean,
    canRetry: Boolean,
    onRetry: () -> Unit,
    onBackToMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag(PlayerTestTags.ERROR),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.dangerBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = PlayerIcons.Warning,
                        contentDescription = null,
                        tint = colors.dangerText,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Text(
                    text = stringResource(
                        if (isNotFound) R.string.attempt_errors_notFoundTitle else R.string.attempt_errors_loadTitle,
                    ),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag(PlayerTestTags.ERROR_TITLE),
                )

                Text(
                    text = stringResource(
                        if (isNotFound) {
                            R.string.attempt_errors_notFoundDescription
                        } else {
                            R.string.attempt_errors_loadDescription
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (canRetry && !isNotFound) {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryFill,
                                contentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(PlayerTestTags.ERROR_RETRY),
                        ) {
                            Text(text = stringResource(R.string.attempt_errors_retry))
                        }
                    }
                    OutlinedButton(
                        onClick = onBackToMyExams,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.ERROR_MY_EXAMS),
                    ) {
                        Text(
                            text = stringResource(R.string.attempt_errors_loadBackToMyExams),
                            color = colors.text,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerClosedScreen(
    reason: AttemptClosedReason?,
    examTitle: String,
    onViewResult: () -> Unit,
    onBackToMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    val headingRes = when (reason) {
        AttemptClosedReason.TimeUp -> R.string.attempt_closed_headingTimeUp
        AttemptClosedReason.ChoiceTimeout -> R.string.attempt_closed_headingChoiceTimeout
        else -> R.string.attempt_closed_heading
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag(PlayerTestTags.CLOSED_SCREEN),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(statusColors.successBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = PlayerIcons.CheckCircle,
                        contentDescription = null,
                        tint = statusColors.success,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Text(
                    text = stringResource(headingRes),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .semantics {
                            heading()
                            liveRegion = LiveRegionMode.Assertive
                        }
                        .testTag(PlayerTestTags.CLOSED_HEADING),
                )

                if (examTitle.isNotBlank()) {
                    Text(
                        text = examTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                Text(
                    text = stringResource(R.string.attempt_closed_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onViewResult,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryFill,
                            contentColor = colors.onPrimary,
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.CLOSED_VIEW_RESULT),
                    ) {
                        Text(text = stringResource(R.string.attempt_closed_viewResult))
                    }
                    OutlinedButton(
                        onClick = onBackToMyExams,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.CLOSED_MY_EXAMS),
                    ) {
                        Text(
                            text = stringResource(R.string.attempt_closed_backToMyExams),
                            color = colors.text,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerEvictedScreen(
    examTitle: String,
    isSending: Boolean,
    errorCode: String?,
    onTakeover: () -> Unit,
    onBackToMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag(PlayerTestTags.EVICTED_SCREEN),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(statusColors.warningBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = PlayerIcons.Warning,
                        contentDescription = null,
                        tint = statusColors.warning,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.attempt_evicted_heading),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .semantics {
                            heading()
                            liveRegion = LiveRegionMode.Assertive
                        }
                        .testTag(PlayerTestTags.EVICTED_HEADING),
                )

                if (examTitle.isNotBlank()) {
                    Text(
                        text = examTitle,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                Text(
                    text = stringResource(R.string.attempt_evicted_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )

                if (errorCode != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.dp, colors.dangerText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(PlayerTestTags.TAKEOVER_ERROR),
                    ) {
                        Text(
                            text = stringResource(R.string.attempt_takeover_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.dangerText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onTakeover,
                        enabled = !isSending,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryFill,
                            contentColor = colors.onPrimary,
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.EVICTED_TAKEOVER),
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                color = colors.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = stringResource(R.string.attempt_evicted_takeoverHere))
                    }
                    OutlinedButton(
                        onClick = onBackToMyExams,
                        enabled = !isSending,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.EVICTED_MY_EXAMS),
                    ) {
                        Text(
                            text = stringResource(R.string.attempt_evicted_backToMyExams),
                            color = colors.text,
                        )
                    }
                }
            }
        }
    }
}
