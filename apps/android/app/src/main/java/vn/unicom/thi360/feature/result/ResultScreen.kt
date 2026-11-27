package vn.unicom.thi360.feature.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.network.generated.model.AttemptResultUnitStats
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LoadStateView
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.ShellWidth
import vn.unicom.thi360.core.ui.StaleBanner

@Composable
fun ResultScreen(
    uiState: ResultUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onReloadFromTimeout: () -> Unit,
    onOpenReview: (String) -> Unit,
    onContinueAttempt: (String) -> Unit,
    onGoToMyExams: () -> Unit,
    onGoToHistory: () -> Unit,
    modifier: Modifier = Modifier,
    clickGuard: ResultClickGuard = remember { ResultClickGuard() },
) {
    val colors = Thi360Theme.colors
    val shellWidth = LocalShellWidth.current
    val horizontalPadding = when (shellWidth) {
        ShellWidth.Narrow -> 16.dp
        ShellWidth.Wide -> 24.dp
        ShellWidth.TwoColumn -> 28.dp
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(ResultTestTags.SCREEN),
    ) {
        Box(modifier = Modifier.testTag(ResultTestTags.screenFor(uiState.attemptId))) {
            ResultTopBar(
                title = stringResource(R.string.result_pageTitle),
                backContentDescription = stringResource(R.string.result_back),
                onBack = { clickGuard.tryRun(onBack) },
            )
        }

        val serverErrorTitle = stringResource(R.string.result_errorTitle)
        val serverErrorDescription = stringResource(R.string.result_errorDescription)

        when (val load = uiState.loadState) {
            LoadState.Loading -> {
                val loadingLabel = stringResource(R.string.result_loading)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = loadingLabel }
                        .testTag(ResultTestTags.LOADING),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = colors.primaryFill)
                        Text(
                            text = loadingLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                    }
                }
            }

            is LoadState.Failed -> {
                when {
                    load.kind is LoadFailureKind.NotFound -> {
                        ResultNotFoundView(
                            onGoToMyExams = { clickGuard.tryRun(onGoToMyExams) },
                            onGoToHistory = { clickGuard.tryRun(onGoToHistory) },
                        )
                    }

                    load.kind is LoadFailureKind.Conflict &&
                        load.kind.code == ResultViewModel.ATTEMPT_NOT_FINISHED_CODE -> {
                        ResultNotFinishedView(
                            onContinueAttempt = { clickGuard.tryRun { onContinueAttempt(uiState.attemptId) } },
                            onGoToHistory = { clickGuard.tryRun(onGoToHistory) },
                        )
                    }

                    else -> {
                        LoadStateView(
                            state = load,
                            onRetry = onRetry,
                            emptyContent = {},
                            customErrorTitle = { failed ->
                                if (failed.kind == LoadFailureKind.Offline) null else serverErrorTitle
                            },
                            customErrorDescription = { failed ->
                                if (failed.kind == LoadFailureKind.Offline) null else serverErrorDescription
                            },
                            content = {},
                        )
                    }
                }
            }

            LoadState.Empty -> Unit

            is LoadState.Loaded -> {
                val result = load.value
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (load.stale) {
                        StaleBanner(
                            message = serverErrorTitle,
                            onRetry = onRetry,
                            retryEnabled = !uiState.isRetrying,
                            modifier = Modifier
                                .widthIn(max = 960.dp)
                                .padding(horizontal = horizontalPadding, vertical = 8.dp),
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .widthIn(max = 960.dp)
                            .testTag(ResultTestTags.CONTENT_LIST),
                        contentPadding = PaddingValues(
                            horizontal = horizontalPadding,
                            vertical = 16.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(key = "header") {
                            ResultHeaderSection(
                                result = result,
                                examKinds = uiState.examKinds,
                            )
                        }

                        if (ResultFormat.showFaultBanner(result)) {
                            item(key = "fault_banner") {
                                ResultFaultBanner(result = result)
                            }
                        }

                        when (result.state) {
                            AttemptResultResponse.State.GRADING -> {
                                item(key = "grading_state") {
                                    ResultGradingSection(
                                        isTimedOut = uiState.isGradingTimedOut,
                                        isReloading = uiState.isReloadingFromTimeout,
                                        onReload = onReloadFromTimeout,
                                        onGoToHistory = { clickGuard.tryRun(onGoToHistory) },
                                    )
                                }
                            }

                            AttemptResultResponse.State.GRADING_ERROR -> {
                                item(key = "grading_error_state") {
                                    ResultGradingErrorSection(
                                        onGoToHistory = { clickGuard.tryRun(onGoToHistory) },
                                        onGoToMyExams = { clickGuard.tryRun(onGoToMyExams) },
                                    )
                                }
                            }

                            AttemptResultResponse.State.SCORED,
                            AttemptResultResponse.State.PENDING_REVIEW,
                            -> {
                                item(key = "score_summary") {
                                    ResultTotalScoreCard(result = result)
                                }

                                result.statistics?.let { stats ->
                                    item(key = "stats_card") {
                                        ResultStatisticsCard(stats = stats)
                                    }
                                }

                                item(key = "actions") {
                                    ResultActionsSection(
                                        result = result,
                                        onOpenReview = {
                                            clickGuard.tryRun { onOpenReview(uiState.attemptId) }
                                        },
                                        onGoToMyExams = { clickGuard.tryRun(onGoToMyExams) },
                                        onGoToHistory = { clickGuard.tryRun(onGoToHistory) },
                                    )
                                }
                            }

                            else -> Unit
                        }

                        item(key = "info_card") {
                            ResultInfoCard(
                                result = result,
                                examKinds = uiState.examKinds,
                                subjects = uiState.subjects,
                            )
                        }

                        if (result.state == AttemptResultResponse.State.SCORED ||
                            result.state == AttemptResultResponse.State.PENDING_REVIEW
                        ) {
                            val vactComponents = result.vactComponents.orEmpty()
                            if (ResultFormat.isVact(result) && vactComponents.isNotEmpty()) {
                                item(key = "vact_components") {
                                    VactComponentsSection(
                                        components = vactComponents,
                                        subjectCatalog = uiState.subjects,
                                    )
                                }
                            }

                            val parts = result.parts.orEmpty()
                            if (parts.isNotEmpty()) {
                                item(key = "parts_breakdown") {
                                    PartsBreakdownSection(
                                        parts = parts,
                                        attemptState = result.state,
                                        endReason = result.endReason,
                                    )
                                }
                            }

                            val segments = result.segments.orEmpty()
                            if (segments.isNotEmpty()) {
                                item(key = "segments_breakdown") {
                                    SegmentsBreakdownSection(
                                        segments = segments,
                                        attemptState = result.state,
                                    )
                                }
                            }

                            val subjects = result.subjects.orEmpty()
                            if (subjects.isNotEmpty() && !ResultFormat.isVact(result)) {
                                item(key = "subjects_breakdown") {
                                    SubjectsBreakdownSection(
                                        subjects = subjects,
                                        subjectCatalog = uiState.subjects,
                                        attemptState = result.state,
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
private fun ResultTopBar(
    title: String,
    backContentDescription: String,
    onBack: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Surface(
        color = colors.bgSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(ResultTestTags.BACK_BUTTON),
                ) {
                    Icon(
                        imageVector = ResultIcons.ArrowBack,
                        contentDescription = backContentDescription,
                        tint = colors.text,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = colors.border)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultHeaderSection(
    result: AttemptResultResponse,
    examKinds: Map<String, CatalogOptionItem>,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val examKindLabel = ResultFormat.resolveCatalogLabel(result.examKindCode, examKinds, locale)
    val scopeLabel = scopeDisplayLabel(result.scope)
    val statusText = statusDisplayLabel(result.state, result.regrading)
    val tone = ResultFormat.resolveStatusTone(result.state, result.regrading)
    val (statusBg, statusFg) = statusColorsForTone(tone)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ResultTestTags.HEADER),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.result_eyebrow),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.primaryText,
        )
        Text(
            text = result.examName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.result_pageTitle),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            modifier = Modifier
                .semantics { heading() }
                .testTag(ResultTestTags.EXAM_TITLE),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (examKindLabel.isNotBlank()) {
                ResultPill(
                    text = examKindLabel,
                    containerColor = colors.primaryContainer,
                    contentColor = colors.primaryText,
                    modifier = Modifier.testTag(ResultTestTags.EXAM_KIND_CHIP),
                )
            }
            if (scopeLabel.isNotBlank()) {
                ResultPill(
                    text = scopeLabel,
                    containerColor = colors.bgSubtle,
                    contentColor = colors.textSecondary,
                    modifier = Modifier.testTag(ResultTestTags.SCOPE_CHIP),
                )
            }
            result.attemptNo?.let { attemptNo ->
                ResultPill(
                    text = stringResource(R.string.result_attemptNo, attemptNo),
                    containerColor = colors.bgSubtle,
                    contentColor = colors.textSecondary,
                    modifier = Modifier.testTag(ResultTestTags.ATTEMPT_NO_CHIP),
                )
            }
            if (statusText.isNotBlank()) {
                ResultPill(
                    text = statusText,
                    containerColor = statusBg,
                    contentColor = statusFg,
                    modifier = Modifier.testTag(ResultTestTags.STATUS_CHIP),
                )
            }
            if (ResultFormat.isVact(result)) {
                ResultPill(
                    text = stringResource(R.string.result_vact_scoreLabel),
                    containerColor = statusColors.infoBg,
                    contentColor = statusColors.info,
                    modifier = Modifier.testTag(ResultTestTags.VACT_PRACTICE_BADGE),
                )
            }
        }
    }
}

@Composable
private fun ResultFaultBanner(
    result: AttemptResultResponse,
) {
    val statusColors = Thi360Theme.statusColors
    val isCompensated = result.compensated == true
    val bgColor = if (isCompensated) statusColors.infoBg else statusColors.warningBg
    val fgColor = if (isCompensated) statusColors.info else statusColors.warning
    val icon = if (isCompensated) ResultIcons.Info else ResultIcons.Warning
    val headline = stringResource(
        if (isCompensated) {
            R.string.result_fault_compensatedBanner
        } else {
            R.string.result_fault_faultNoticeBanner
        },
    )
    val reason = result.faultReason?.trim()?.takeIf { it.isNotEmpty() }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, fgColor.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag(ResultTestTags.FAULT_BANNER),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = fgColor,
                )
                if (reason != null) {
                    Text(
                        text = "${stringResource(R.string.result_fault_reasonLabel)} $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = fgColor,
                        modifier = Modifier.testTag(ResultTestTags.FAULT_REASON),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultGradingSection(
    isTimedOut: Boolean,
    isReloading: Boolean,
    onReload: () -> Unit,
    onGoToHistory: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    if (!isTimedOut) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = statusColors.infoBg,
            border = BorderStroke(1.dp, statusColors.info.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .testTag(ResultTestTags.GRADING_CARD),
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = statusColors.info,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.result_grading_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColors.info,
                    )
                    Text(
                        text = stringResource(R.string.result_grading_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                }
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = statusColors.warningBg,
            border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ResultTestTags.GRADING_TIMEOUT_CARD),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.result_grading_timeoutHeading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = statusColors.warning,
                )
                Text(
                    text = stringResource(R.string.result_grading_timeoutDescription),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.text,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onReload,
                        enabled = !isReloading,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryFill,
                            contentColor = colors.onPrimary,
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(ResultTestTags.GRADING_RELOAD_BUTTON),
                    ) {
                        if (isReloading) {
                            CircularProgressIndicator(
                                color = colors.onPrimary,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        Text(text = stringResource(R.string.result_grading_reload))
                    }
                    OutlinedButton(
                        onClick = onGoToHistory,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(ResultTestTags.HISTORY_BUTTON),
                    ) {
                        Text(text = stringResource(R.string.result_backToHistory))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultGradingErrorSection(
    onGoToHistory: () -> Unit,
    onGoToMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.dangerBg,
        border = BorderStroke(1.dp, colors.dangerText.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ResultTestTags.GRADING_ERROR_CARD),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.result_gradingError_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.dangerText,
            )
            Text(
                text = stringResource(R.string.result_gradingError_message),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onGoToHistory,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ResultTestTags.HISTORY_BUTTON),
                ) {
                    Text(text = stringResource(R.string.result_backToHistory))
                }
                OutlinedButton(
                    onClick = onGoToMyExams,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ResultTestTags.MY_EXAMS_BUTTON),
                ) {
                    Text(text = stringResource(R.string.result_actions_myExams))
                }
            }
        }
    }
}

@Composable
private fun ResultTotalScoreCard(
    result: AttemptResultResponse,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val resolvedScore = ResultFormat.resolveDisplayScore(result)
    val isPendingNonRegrading = result.state == AttemptResultResponse.State.PENDING_REVIEW && result.regrading != true
    val isRegrading = result.state == AttemptResultResponse.State.PENDING_REVIEW && result.regrading == true
    val isVact = ResultFormat.isVact(result)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ResultTestTags.TOTAL_SCORE_CARD),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.result_total_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textSecondary,
                modifier = Modifier.semantics { heading() },
            )

            if (isPendingNonRegrading || resolvedScore == null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColors.warningBg,
                    border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ResultTestTags.PENDING_TOTAL_BOX),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.result_total_pendingTitle),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = statusColors.warning,
                        )
                        Text(
                            text = stringResource(R.string.result_total_pendingDescription),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                        )
                        Text(
                            text = stringResource(R.string.result_total_pendingReturnNote),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            } else {
                val scoreFormatted = ResultFormat.formatScore(resolvedScore.score, locale)
                val maxFormatted = resolvedScore.max?.let { ResultFormat.formatScore(it, locale) }
                val scoreDisplay = if (maxFormatted != null) "$scoreFormatted/$maxFormatted" else scoreFormatted

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = scoreDisplay,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText,
                        modifier = Modifier.testTag(ResultTestTags.TOTAL_SCORE_VALUE),
                    )
                    if (isRegrading) {
                        ResultPill(
                            text = stringResource(R.string.result_states_REGRADING),
                            containerColor = statusColors.warningBg,
                            contentColor = statusColors.warning,
                        )
                    }
                }

                if (isVact) {
                    Text(
                        text = stringResource(R.string.result_vact_practiceNote),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = statusColors.info,
                        modifier = Modifier.testTag(ResultTestTags.VACT_PRACTICE_NOTE),
                    )
                    Text(
                        text = if (maxFormatted != null) {
                            stringResource(R.string.result_total_vactScaleDescription, maxFormatted)
                        } else {
                            stringResource(R.string.result_total_scaleDescription)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.result_total_scaleDescription),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }

                if (isRegrading) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = statusColors.warningBg,
                        border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ResultTestTags.REGRADING_NOTE),
                    ) {
                        Text(
                            text = stringResource(R.string.result_total_regradingNote),
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColors.warning,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultStatisticsCard(
    stats: AttemptResultUnitStats,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val correct = stats.correctCount ?: 0
    val partial = stats.partialCount ?: 0
    val wrong = stats.wrongCount ?: 0
    val blank = stats.blankCount ?: 0
    val pending = stats.pendingReviewCount ?: 0
    val total = stats.totalCount ?: (correct + partial + wrong + blank + pending)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ResultTestTags.STATS_CARD),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.result_stats_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatBucketTile(
                    label = stringResource(R.string.result_stats_correct),
                    value = ResultFormat.formatCount(correct, locale),
                    containerColor = statusColors.successBg,
                    valueColor = statusColors.success,
                    tag = ResultTestTags.STAT_CORRECT,
                )
                StatBucketTile(
                    label = stringResource(R.string.result_stats_partial),
                    value = ResultFormat.formatCount(partial, locale),
                    containerColor = statusColors.warningBg,
                    valueColor = statusColors.warning,
                    tag = ResultTestTags.STAT_PARTIAL,
                )
                StatBucketTile(
                    label = stringResource(R.string.result_stats_wrong),
                    value = ResultFormat.formatCount(wrong, locale),
                    containerColor = colors.dangerBg,
                    valueColor = colors.dangerText,
                    tag = ResultTestTags.STAT_WRONG,
                )
                StatBucketTile(
                    label = stringResource(R.string.result_stats_blank),
                    value = ResultFormat.formatCount(blank, locale),
                    containerColor = colors.bgSubtle,
                    valueColor = colors.textSecondary,
                    tag = ResultTestTags.STAT_BLANK,
                )
                if (pending > 0) {
                    StatBucketTile(
                        label = stringResource(R.string.result_stats_pending),
                        value = ResultFormat.formatCount(pending, locale),
                        containerColor = statusColors.warningBg,
                        valueColor = statusColors.warning,
                        borderColor = statusColors.warning,
                        tag = ResultTestTags.STAT_PENDING,
                    )
                }
            }

            StatsRatioBar(stats = stats)

            Text(
                text = stringResource(
                    R.string.result_stats_denominator,
                    ResultFormat.formatCount(total, locale),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
private fun StatBucketTile(
    label: String,
    value: String,
    containerColor: Color,
    valueColor: Color,
    tag: String,
    borderColor: Color? = null,
) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = borderColor?.let { BorderStroke(1.5.dp, it) },
        modifier = Modifier
            .widthIn(min = 104.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(tag),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = valueColor,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultActionsSection(
    result: AttemptResultResponse,
    onOpenReview: () -> Unit,
    onGoToMyExams: () -> Unit,
    onGoToHistory: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val canReview = ResultFormat.canOpenReview(result)
    val isPending = result.state == AttemptResultResponse.State.PENDING_REVIEW

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (canReview) {
                Button(
                    onClick = onOpenReview,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ResultTestTags.REVIEW_BUTTON),
                ) {
                    Text(
                        text = stringResource(
                            if (isPending) {
                                R.string.result_actions_reviewAutoGraded
                            } else {
                                R.string.result_actions_reviewFull
                            },
                        ),
                    )
                }
            }

            OutlinedButton(
                onClick = onGoToMyExams,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(ResultTestTags.MY_EXAMS_BUTTON),
            ) {
                Text(text = stringResource(R.string.result_actions_myExams))
            }

            OutlinedButton(
                onClick = onGoToHistory,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(ResultTestTags.HISTORY_BUTTON),
            ) {
                Text(text = stringResource(R.string.result_backToHistory))
            }
        }

        when {
            result.resultView == "SCORE" -> {
                Text(
                    text = stringResource(R.string.result_actions_scoreOnlyNote),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.testTag(ResultTestTags.SCORE_ONLY_NOTE),
                )
            }
            canReview && isPending -> {
                Text(
                    text = stringResource(R.string.result_actions_pendingReviewNote),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.testTag(ResultTestTags.PENDING_REVIEW_NOTE),
                )
            }
            !canReview -> {
                Text(
                    text = stringResource(R.string.result_actions_unavailableNote),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.testTag(ResultTestTags.UNAVAILABLE_NOTE),
                )
            }
        }
    }
}

@Composable
private fun ResultInfoCard(
    result: AttemptResultResponse,
    examKinds: Map<String, CatalogOptionItem>,
    subjects: Map<String, CatalogOptionItem>,
) {
    val colors = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val choiceLabels = mapOf(
        KnownChoiceOptionCode.ANH to stringResource(R.string.result_choiceOptions_ANH),
        KnownChoiceOptionCode.SCIENCE to stringResource(R.string.result_choiceOptions_SCIENCE),
        KnownChoiceOptionCode.ENGLISH to stringResource(R.string.result_choiceOptions_ENGLISH),
        KnownChoiceOptionCode.KH to stringResource(R.string.result_choiceOptions_KH),
        KnownChoiceOptionCode.TA to stringResource(R.string.result_choiceOptions_TA),
        KnownChoiceOptionCode.CS to stringResource(R.string.result_choiceOptions_CS),
        KnownChoiceOptionCode.ICT to stringResource(R.string.result_choiceOptions_ICT),
    )

    val rows = buildList {
        val kindLabel = ResultFormat.resolveCatalogLabel(result.examKindCode, examKinds, locale)
        if (kindLabel.isNotBlank()) {
            add(stringResource(R.string.result_info_examKind) to kindLabel)
        }
        val scopeLabel = scopeDisplayLabel(result.scope)
        if (scopeLabel.isNotBlank()) {
            add(stringResource(R.string.result_info_scope) to scopeLabel)
        }
        result.partCode?.takeIf { it.isNotBlank() }?.let { code ->
            add(stringResource(R.string.result_info_part) to code)
        }
        result.subjectCode?.takeIf { it.isNotBlank() }?.let { code ->
            val label = ResultFormat.resolveCatalogLabel(code, subjects, locale)
            add(stringResource(R.string.result_info_subject) to label)
        }
        result.attemptNo?.let { attemptNo ->
            add(stringResource(R.string.result_info_attemptNo) to ResultFormat.formatCount(attemptNo, locale))
        }
        ResultFormat.formatDateTime(result.startedAt)?.let { formatted ->
            add(stringResource(R.string.result_info_startedAt) to formatted)
        }
        ResultFormat.formatDateTime(result.finishedAt)?.let { formatted ->
            add(stringResource(R.string.result_info_finishedAt) to formatted)
        }
        val durationText = formatDurationText(
            ResultFormat.computeActualDuration(result.startedAt, result.finishedAt),
        )
        add(stringResource(R.string.result_info_duration) to durationText)
        result.endReason?.let { reason ->
            add(stringResource(R.string.result_info_endReason) to endReasonLabel(reason))
        }
        val formattedChoices = ResultFormat.formatAttemptChoices(
            choices = result.choices,
            subjectMap = subjects,
            locale = locale,
            translateOptionCode = { choiceLabels[it] ?: it.name },
        )
        val choicesDisplay = if (formattedChoices.isEmpty()) {
            stringResource(R.string.result_info_noChoices)
        } else {
            formattedChoices.joinToString(" · ")
        }
        add(stringResource(R.string.result_info_choices) to choicesDisplay)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ResultTestTags.INFO_CARD),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.result_info_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            rows.forEachIndexed { idx, (label, value) ->
                if (idx > 0) {
                    HorizontalDivider(color = colors.border)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.weight(0.45f),
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.text,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(0.55f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultNotFoundView(
    onGoToMyExams: () -> Unit,
    onGoToHistory: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(ResultTestTags.NOT_FOUND),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.result_notFound_eyebrow),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.dangerText,
            )
            Text(
                text = stringResource(R.string.result_notFound_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.result_notFound_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onGoToMyExams,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ResultTestTags.MY_EXAMS_BUTTON),
                ) {
                    Text(text = stringResource(R.string.result_notFound_toHome))
                }
                OutlinedButton(
                    onClick = onGoToHistory,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ResultTestTags.HISTORY_BUTTON),
                ) {
                    Text(text = stringResource(R.string.result_notFound_toHistory))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultNotFinishedView(
    onContinueAttempt: () -> Unit,
    onGoToHistory: () -> Unit,
) {
    val colors = Thi360Theme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag(ResultTestTags.NOT_FINISHED),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.result_notFinished_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.result_notFinished_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onContinueAttempt,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ResultTestTags.CONTINUE_ATTEMPT_BUTTON),
                ) {
                    Text(text = stringResource(R.string.result_notFinished_continueAttempt))
                }
                OutlinedButton(
                    onClick = onGoToHistory,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(ResultTestTags.HISTORY_BUTTON),
                ) {
                    Text(text = stringResource(R.string.result_notFinished_toHistory))
                }
            }
        }
    }
}

@Composable
internal fun scopeDisplayLabel(rawScope: String?): String = when (ResultScope.fromWire(rawScope)) {
    ResultScope.FULL -> stringResource(R.string.result_scopes_FULL)
    ResultScope.PART -> stringResource(R.string.result_scopes_PART)
    ResultScope.SUBJECT -> stringResource(R.string.result_scopes_SUBJECT)
    null -> rawScope?.trim().orEmpty()
}

@Composable
private fun statusDisplayLabel(
    state: AttemptResultResponse.State?,
    regrading: Boolean?,
): String {
    if (regrading == true) return stringResource(R.string.result_states_REGRADING)
    return when (state) {
        AttemptResultResponse.State.SCORED -> stringResource(R.string.result_states_SCORED)
        AttemptResultResponse.State.PENDING_REVIEW -> stringResource(R.string.result_states_PENDING_REVIEW)
        AttemptResultResponse.State.GRADING -> stringResource(R.string.result_states_GRADING)
        AttemptResultResponse.State.GRADING_ERROR -> stringResource(R.string.result_states_GRADING_ERROR)
        else -> ""
    }
}

@Composable
private fun statusColorsForTone(tone: ResultStatusTone): Pair<Color, Color> {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    return when (tone) {
        ResultStatusTone.SUCCESS -> statusColors.successBg to statusColors.success
        ResultStatusTone.WARNING -> statusColors.warningBg to statusColors.warning
        ResultStatusTone.INFO -> statusColors.infoBg to statusColors.info
        ResultStatusTone.DANGER -> colors.dangerBg to colors.dangerText
        ResultStatusTone.NEUTRAL -> colors.bgSubtle to colors.textSecondary
    }
}

@Composable
private fun endReasonLabel(reason: AttemptResultResponse.EndReason): String = when (reason) {
    AttemptResultResponse.EndReason.SUBMITTED -> stringResource(R.string.result_endReasons_SUBMITTED)
    AttemptResultResponse.EndReason.TIME_UP -> stringResource(R.string.result_endReasons_TIME_UP)
    AttemptResultResponse.EndReason.CHOICE_TIMEOUT -> stringResource(R.string.result_endReasons_CHOICE_TIMEOUT)
    AttemptResultResponse.EndReason.FAULT_CLOSED -> stringResource(R.string.result_endReasons_FAULT_CLOSED)
}

@Composable
private fun formatDurationText(parts: DurationParts?): String = when (parts) {
    null -> "—"
    DurationParts.Zero -> stringResource(R.string.result_info_durationZero)
    is DurationParts.Seconds -> stringResource(R.string.result_info_durationSeconds, parts.seconds)
    is DurationParts.Minutes -> stringResource(R.string.result_info_durationMinutes, parts.minutes)
    is DurationParts.HoursMinutes -> stringResource(
        R.string.result_info_durationHoursMinutes,
        parts.hours,
        parts.minutes,
    )
}
