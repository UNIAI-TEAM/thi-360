package vn.unicom.thi360.feature.prepare.waiting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.UUID
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.api.AttemptsApi
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.prepare.AbortReasonMessage
import vn.unicom.thi360.feature.prepare.OpenApiPrepareApi
import vn.unicom.thi360.feature.prepare.PrepareApi
import vn.unicom.thi360.feature.prepare.PrepareIcons
import vn.unicom.thi360.feature.prepare.PrepareTestTags
import vn.unicom.thi360.feature.prepare.StartCommand
import vn.unicom.thi360.feature.prepare.StartContext
import vn.unicom.thi360.feature.prepare.StartContextStore
import vn.unicom.thi360.feature.prepare.StartErrorMessage
import vn.unicom.thi360.feature.prepare.StartOutcome
import vn.unicom.thi360.feature.prepare.StartRefusal
import vn.unicom.thi360.feature.prepare.WaitingOrigin
import vn.unicom.thi360.feature.prepare.WaitingPhase

@Composable
fun AttemptWaitingScreen(
    attemptId: String,
    examTitle: String,
    context: StartContext?,
    origin: WaitingOrigin,
    onReady: (String) -> Unit,
    onRestarted: (String) -> Unit,
    onOpenPrepare: (String) -> Unit,
    onMyExams: () -> Unit,
    modifier: Modifier = Modifier,
    initialState: AttemptStateResponse.State = AttemptStateResponse.State.STARTING,
    initialAbortReason: String? = null,
    supportContact: SupportContact = AppEnvironment.supportContact,
    prepareApi: PrepareApi? = null,
    startContextStore: StartContextStore? = null,
    clockMillis: () -> Long = System::currentTimeMillis,
    onBack: (() -> Unit)? = null,
) {
    val androidContext = LocalContext.current
    val resolvedApi = remember(prepareApi, androidContext) {
        prepareApi ?: OpenApiPrepareApi(
            AppSessionContainer.get(androidContext).authenticatedService(AttemptsApi::class.java),
        )
    }
    val resolvedStore = remember(startContextStore, androidContext) {
        startContextStore ?: StartContextStore(
            storeProvider = {
                (androidContext.applicationContext as? Thi360Application)
                    ?.accountScopedStoreProvider
                    ?.current()
            },
        )
    }
    val startCommand = remember(resolvedApi, attemptId) { StartCommand(resolvedApi) }
    val waitingRetry = remember(startCommand, resolvedStore) {
        WaitingRetry(startCommand = startCommand, startContextStore = resolvedStore)
    }
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnRestarted by rememberUpdatedState(onRestarted)
    val latestOnOpenPrepare by rememberUpdatedState(onOpenPrepare)
    val latestOnMyExams by rememberUpdatedState(onMyExams)
    val latestOnBack by rememberUpdatedState(onBack ?: onMyExams)

    var savedKind by rememberSaveable(attemptId) {
        mutableStateOf(
            if (initialState == AttemptStateResponse.State.ABORTED) "failed" else "polling",
        )
    }
    var savedElapsedMs by rememberSaveable(attemptId) { mutableLongStateOf(0L) }
    var savedAbortReason by rememberSaveable(attemptId) { mutableStateOf(initialAbortReason) }

    var phase by remember(attemptId) {
        val initialPhase: WaitingPhase = when (savedKind) {
            "failed" -> WaitingPhase.Failed(savedAbortReason)
            "busy" -> WaitingPhase.Busy
            else -> WaitingPhase.Polling(savedElapsedMs)
        }
        mutableStateOf(initialPhase)
    }

    val effectiveContext = remember(attemptId, context, resolvedStore) {
        context ?: resolvedStore.read(attemptId)
    }

    val parsedAttemptUuid = remember(attemptId) {
        runCatching { UUID.fromString(attemptId.trim()) }.getOrNull()
    }

    val poller = remember(attemptId, resolvedApi, clockMillis, parsedAttemptUuid) {
        if (parsedAttemptUuid == null || savedKind == "failed") {
            null
        } else {
            WaitingPoller(
                attemptId = parsedAttemptUuid,
                clockMillis = clockMillis,
                api = resolvedApi,
                initialElapsedMs = if (savedKind == "busy") {
                    maxOf(savedElapsedMs, WAITING_BUSY_THRESHOLD_MS)
                } else {
                    savedElapsedMs
                },
                onReady = { readyUuid -> latestOnReady(readyUuid.toString()) },
                onAborted = { reason ->
                    savedKind = "failed"
                    savedAbortReason = reason
                    phase = WaitingPhase.Failed(reason)
                },
                onPhaseChanged = { next ->
                    when (next) {
                        is WaitingPhase.Polling -> {
                            savedKind = "polling"
                            savedElapsedMs = next.elapsedMs
                        }

                        WaitingPhase.Busy -> {
                            savedKind = "busy"
                            savedElapsedMs = WAITING_BUSY_THRESHOLD_MS
                        }

                        is WaitingPhase.Failed -> {
                            savedKind = "failed"
                            savedAbortReason = next.abortReason
                        }

                        WaitingPhase.Retrying,
                        is WaitingPhase.RetryRefused,
                        -> Unit
                    }
                    if (phase != WaitingPhase.Retrying && phase !is WaitingPhase.RetryRefused) {
                        phase = next
                    }
                },
            )
        }
    }

    LaunchedEffect(poller) {
        if (poller != null) {
            val isStarted = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            poller.start(coroutineScope = coroutineScope, initiallyForeground = isStarted)
        }
    }

    DisposableEffect(lifecycleOwner, poller) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> poller?.setForeground(true)
                Lifecycle.Event.ON_STOP -> {
                    poller?.let { savedElapsedMs = it.currentElapsedMs() }
                    poller?.setForeground(false)
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            poller?.let { savedElapsedMs = it.currentElapsedMs() }
            poller?.stop()
        }
    }

    val handleLeave: () -> Unit = {
        poller?.stop()
        latestOnBack()
    }

    val handleMyExams: () -> Unit = {
        poller?.stop()
        latestOnMyExams()
    }

    val handleRetry: () -> Unit = {
        val uuid = parsedAttemptUuid
        if (uuid == null) {
            handleMyExams()
        } else if (phase != WaitingPhase.Retrying && !startCommand.isInFlight) {
            phase = WaitingPhase.Retrying
            coroutineScope.launch {
                val outcome = waitingRetry.retry(
                    attemptId = uuid,
                    origin = origin,
                    inMemoryContext = effectiveContext,
                )
                when (outcome) {
                    WaitingRetryOutcome.AlreadyInFlight -> Unit

                    is WaitingRetryOutcome.Restarted -> {
                        poller?.stop()
                        phase = WaitingPhase.Failed(savedAbortReason)
                        latestOnRestarted(outcome.newAttemptId.toString())
                    }

                    is WaitingRetryOutcome.Refused -> {
                        phase = WaitingPhase.RetryRefused(outcome.refusal)
                    }

                    is WaitingRetryOutcome.OpenPrepare -> {
                        poller?.stop()
                        phase = WaitingPhase.Failed(savedAbortReason)
                        latestOnOpenPrepare(outcome.entitlementId)
                    }

                    WaitingRetryOutcome.ReloadPrepare -> {
                        poller?.stop()
                        phase = WaitingPhase.Failed(savedAbortReason)
                        val entId = effectiveContext?.entitlementId
                        if (!entId.isNullOrBlank()) {
                            latestOnOpenPrepare(entId)
                        } else {
                            latestOnMyExams()
                        }
                    }

                    WaitingRetryOutcome.GoToMyExams -> {
                        poller?.stop()
                        phase = WaitingPhase.Failed(savedAbortReason)
                        latestOnMyExams()
                    }
                }
            }
        }
    }

    AttemptWaitingContent(
        examTitle = examTitle.ifBlank { effectiveContext?.examTitle.orEmpty() },
        phase = phase,
        abortReason = (phase as? WaitingPhase.Failed)?.abortReason ?: savedAbortReason,
        firstPartName = effectiveContext?.firstPartName,
        firstPartMinutes = effectiveContext?.firstPartMinutes,
        isSharedTiming = effectiveContext?.timing == "SHARED",
        supportContact = supportContact,
        onBack = handleLeave,
        onMyExams = handleMyExams,
        onRetry = handleRetry,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttemptWaitingContent(
    examTitle: String,
    phase: WaitingPhase,
    abortReason: String?,
    firstPartName: String?,
    firstPartMinutes: Int?,
    isSharedTiming: Boolean,
    supportContact: SupportContact,
    onBack: () -> Unit,
    onMyExams: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val displayTitle = examTitle.ifBlank { stringResource(R.string.attempt_waiting_title) }
    val waitingPaneTitle = stringResource(R.string.attempt_waiting_pageTitle)
    val hasMetadata = !firstPartName.isNullOrBlank() || firstPartMinutes != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .semantics { paneTitle = waitingPaneTitle }
            .testTag(PrepareTestTags.WAITING_SCREEN),
    ) {
        Surface(
            color = colors.bgSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag(PrepareTestTags.WAITING_BACK_BUTTON),
                    ) {
                        Icon(
                            imageVector = PrepareIcons.ArrowBack,
                            contentDescription = stringResource(R.string.attempt_waiting_backToMyExams),
                            tint = colors.text,
                        )
                    }
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(PrepareTestTags.WAITING_TITLE_BAR),
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.bgSubtle,
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .semantics(mergeDescendants = true) {}
                            .testTag(PrepareTestTags.WAITING_CLOCK_PAUSED),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = PrepareIcons.PauseCircle,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.attempt_waiting_clockPaused),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.border)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(PrepareTestTags.WAITING_LIST),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "waiting_body") {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.bgSurface,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            when (phase) {
                                is WaitingPhase.Polling -> {
                                    WaitingPollingBody(
                                        hasMetadata = hasMetadata,
                                        firstPartName = firstPartName,
                                        firstPartMinutes = firstPartMinutes,
                                        isSharedTiming = isSharedTiming,
                                        onMyExams = onMyExams,
                                    )
                                }

                                WaitingPhase.Busy -> {
                                    WaitingBusyBody(
                                        hasMetadata = hasMetadata,
                                        firstPartName = firstPartName,
                                        firstPartMinutes = firstPartMinutes,
                                        isSharedTiming = isSharedTiming,
                                        onMyExams = onMyExams,
                                    )
                                }

                                is WaitingPhase.Failed,
                                WaitingPhase.Retrying,
                                is WaitingPhase.RetryRefused,
                                -> {
                                    WaitingFailedBody(
                                        abortReason = (phase as? WaitingPhase.Failed)?.abortReason ?: abortReason,
                                        isRetrying = phase == WaitingPhase.Retrying,
                                        retryRefusal = (phase as? WaitingPhase.RetryRefused)?.refusal,
                                        supportContact = supportContact,
                                        onMyExams = onMyExams,
                                        onRetry = onRetry,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingPollingBody(
    hasMetadata: Boolean,
    firstPartName: String?,
    firstPartMinutes: Int?,
    isSharedTiming: Boolean,
    onMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PrepareTestTags.WAITING_POLLING_SECTION),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = colors.primaryFill,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp),
            )
        }

        Text(
            text = stringResource(R.string.attempt_waiting_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )

        Text(
            text = stringResource(R.string.attempt_waiting_description),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 440.dp),
        )

        if (hasMetadata) {
            WaitingMetadataCard(
                firstPartName = firstPartName,
                firstPartMinutes = firstPartMinutes,
                isSharedTiming = isSharedTiming,
            )
        }

        val linkText = stringResource(R.string.attempt_waiting_reassureNoteLink)
        val fullNote = stringResource(R.string.attempt_waiting_reassureNote, linkText)
        val annotatedNote = remember(fullNote, linkText, statusColors.info) {
            buildAnnotatedString {
                val startIdx = fullNote.indexOf(linkText)
                if (startIdx >= 0) {
                    append(fullNote.substring(0, startIdx))
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ) {
                        append(linkText)
                    }
                    append(fullNote.substring(startIdx + linkText.length))
                } else {
                    append(fullNote)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = statusColors.infoBg,
            border = BorderStroke(1.dp, statusColors.info.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = linkText,
                    onClick = onMyExams,
                )
                .testTag(PrepareTestTags.WAITING_REASSURE_NOTE),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = PrepareIcons.Info,
                    contentDescription = null,
                    tint = statusColors.info,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(18.dp),
                )
                Text(
                    text = annotatedNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun WaitingBusyBody(
    hasMetadata: Boolean,
    firstPartName: String?,
    firstPartMinutes: Int?,
    isSharedTiming: Boolean,
    onMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PrepareTestTags.WAITING_BUSY_SECTION),
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
                imageVector = PrepareIcons.Clock,
                contentDescription = null,
                tint = statusColors.warning,
                modifier = Modifier.size(28.dp),
            )
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = statusColors.warningBg,
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .testTag(PrepareTestTags.WAITING_BUSY_NOTICE),
        ) {
            Text(
                text = stringResource(R.string.attempt_waiting_busyNoticeLabel),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = statusColors.warning,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Text(
            text = stringResource(R.string.attempt_waiting_busyTitle),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                }
                .testTag(PrepareTestTags.WAITING_BUSY_TITLE),
        )

        Text(
            text = stringResource(R.string.attempt_waiting_busyDescription),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .testTag(PrepareTestTags.WAITING_BUSY_DESCRIPTION),
        )

        if (hasMetadata) {
            WaitingMetadataCard(
                firstPartName = firstPartName,
                firstPartMinutes = firstPartMinutes,
                isSharedTiming = isSharedTiming,
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedButton(
            onClick = onMyExams,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.borderStrong),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag(PrepareTestTags.WAITING_BUSY_BACK_BUTTON),
        ) {
            Text(
                text = stringResource(R.string.attempt_waiting_backToMyExams),
                color = colors.text,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WaitingFailedBody(
    abortReason: String?,
    isRetrying: Boolean,
    retryRefusal: StartRefusal?,
    supportContact: SupportContact,
    onMyExams: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = Thi360Theme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PrepareTestTags.WAITING_FAILED_SECTION),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = stringResource(R.string.attempt_waiting_failedNoticeLabel),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(PrepareTestTags.WAITING_FAILED_NOTICE),
        )

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.dangerBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PrepareIcons.AlertTriangle,
                contentDescription = null,
                tint = colors.dangerText,
                modifier = Modifier.size(28.dp),
            )
        }

        Text(
            text = stringResource(R.string.attempt_waiting_failedHeading),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics {
                    heading()
                    liveRegion = LiveRegionMode.Assertive
                }
                .testTag(PrepareTestTags.WAITING_FAILED_HEADING),
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.dangerBg,
            border = BorderStroke(1.dp, colors.dangerText),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(AbortReasonMessage.messageRes(abortReason)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.dangerText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(PrepareTestTags.WAITING_FAILED_REASON),
                )
                Text(
                    text = stringResource(R.string.attempt_waiting_failedRefundNote),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(PrepareTestTags.WAITING_FAILED_REFUND_NOTE),
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onMyExams,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.borderStrong),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(PrepareTestTags.WAITING_FAILED_BACK_BUTTON),
            ) {
                Text(
                    text = stringResource(R.string.attempt_waiting_backToMyExams),
                    color = colors.text,
                )
            }

            Button(
                onClick = onRetry,
                enabled = !isRetrying,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primaryFill,
                    contentColor = colors.onPrimary,
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(PrepareTestTags.WAITING_RETRY_BUTTON),
            ) {
                if (isRetrying) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = PrepareIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(text = stringResource(R.string.attempt_waiting_retry))
            }
        }

        if (retryRefusal != null) {
            val contactSummary = supportContact.formattedSummary()
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.dangerBg,
                border = BorderStroke(1.dp, colors.dangerText),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }
                    .testTag(PrepareTestTags.WAITING_RETRY_ERROR),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(StartErrorMessage.messageRes(retryRefusal)),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.dangerText,
                    )
                    if (StartErrorMessage.includesSupportContact(retryRefusal)) {
                        Text(
                            text = if (contactSummary != null) {
                                stringResource(
                                    R.string.prepare_noServingOption_descriptionWithContact,
                                    contactSummary,
                                )
                            } else {
                                stringResource(R.string.prepare_noServingOption_contactPending)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingMetadataCard(
    firstPartName: String?,
    firstPartMinutes: Int?,
    isSharedTiming: Boolean,
) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.bgPage,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PrepareTestTags.WAITING_METADATA_CARD),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!firstPartName.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = stringResource(R.string.attempt_waiting_firstPartLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = firstPartName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag(PrepareTestTags.WAITING_FIRST_PART),
                    )
                }
            }
            if (firstPartMinutes != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = stringResource(
                            if (isSharedTiming) {
                                R.string.attempt_waiting_durationSharedLabel
                            } else {
                                R.string.attempt_waiting_durationLabel
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(PrepareTestTags.WAITING_DURATION_LABEL),
                    )
                    Text(
                        text = stringResource(R.string.attempt_waiting_minutesValue, firstPartMinutes),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(PrepareTestTags.WAITING_DURATION_VALUE),
                    )
                }
            }
        }
    }
}
