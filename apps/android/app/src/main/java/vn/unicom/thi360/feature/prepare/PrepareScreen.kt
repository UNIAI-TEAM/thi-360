package vn.unicom.thi360.feature.prepare

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID
import vn.unicom.thi360.R
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.generated.model.AfterStartChoiceView
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.ChoiceOptionView
import vn.unicom.thi360.core.network.generated.model.GrantSourceView
import vn.unicom.thi360.core.network.generated.model.ListeningPolicyView
import vn.unicom.thi360.core.network.generated.model.OmittedGroupView
import vn.unicom.thi360.core.network.generated.model.PreparePartView
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.LoadStateView
import vn.unicom.thi360.core.ui.LocalShellWidth
import vn.unicom.thi360.core.ui.ShellWidth
import vn.unicom.thi360.core.ui.TwoColumnLayout
import vn.unicom.thi360.feature.prepare.waiting.AttemptWaitingContent
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichContentMode
import vn.unicom.thi360.feature.richcontent.model.RichParseOptions
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@Composable
fun PrepareScreen(
    state: PrepareScreenState,
    supportContact: SupportContact,
    onBack: () -> Unit,
    onMyExams: () -> Unit,
    onRetryLoad: () -> Unit,
    onSelectOption: (String) -> Unit,
    onToggleSubject: (String) -> Unit,
    onStartAttempt: () -> Unit,
    onContinueAttempt: (UUID) -> Unit,
    onBuyMoreAttempts: (String) -> Unit,
    onRetryWaiting: () -> Unit,
    onLeaveWaiting: () -> Unit,
    modifier: Modifier = Modifier,
    formulaEngine: FormulaEngine? = null,
) {
    val waitingPhase = state.waiting
    if (waitingPhase != null && state.waitingAttemptId != null) {
        val loadedResponse = (state.load as? LoadState.Loaded)?.value
        val ctx = state.waitingContext
        val orderedParts = remember(loadedResponse) {
            loadedResponse?.parts?.sortedBy { it.ord ?: 0 }.orEmpty()
        }
        val firstPartName = ctx?.firstPartName ?: orderedParts.firstOrNull()?.name
        val firstPartMinutes = ctx?.firstPartMinutes ?: loadedResponse?.let { resp ->
            if (resp.timing == AttemptPrepareResponse.Timing.SHARED) {
                PrepareMetrics.of(resp, state.selection).durationMinutes
            } else {
                orderedParts.firstOrNull()?.durationMinutes
            }
        }
        val isSharedTiming = ctx?.timing == AttemptPrepareResponse.Timing.SHARED.value ||
            loadedResponse?.timing == AttemptPrepareResponse.Timing.SHARED

        AttemptWaitingContent(
            examTitle = ctx?.examTitle?.ifBlank { null } ?: loadedResponse?.examName.orEmpty(),
            phase = waitingPhase,
            abortReason = (waitingPhase as? WaitingPhase.Failed)?.abortReason ?: state.waitingAbortReason,
            firstPartName = firstPartName,
            firstPartMinutes = firstPartMinutes,
            isSharedTiming = isSharedTiming,
            supportContact = supportContact,
            onBack = onLeaveWaiting,
            onMyExams = onMyExams,
            onRetry = onRetryWaiting,
            modifier = modifier,
        )
        return
    }

    val colors = Thi360Theme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgPage)
            .testTag(PrepareTestTags.SCREEN),
    ) {
        PrepareTopBar(onBack = onBack)

        LoadStateView(
            state = state.load,
            onRetry = onRetryLoad,
            emptyContent = {},
            failedContent = { failed ->
                PrepareFailedView(
                    failed = failed,
                    onMyExams = onMyExams,
                    onRetry = onRetryLoad,
                )
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { response ->
            PrepareLoadedBody(
                response = response,
                selection = state.selection,
                gate = state.gate,
                startState = state.start,
                supportContact = supportContact,
                onMyExams = onMyExams,
                onSelectOption = onSelectOption,
                onToggleSubject = onToggleSubject,
                onStartAttempt = onStartAttempt,
                onContinueAttempt = onContinueAttempt,
                onBuyMoreAttempts = onBuyMoreAttempts,
                formulaEngine = formulaEngine,
            )
        }
    }
}

@Composable
private fun PrepareTopBar(onBack: () -> Unit) {
    val colors = Thi360Theme.colors
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
                        .testTag(PrepareTestTags.BACK_BUTTON),
                ) {
                    Icon(
                        imageVector = PrepareIcons.ArrowBack,
                        contentDescription = stringResource(R.string.prepare_backToMyExams),
                        tint = colors.text,
                    )
                }
                Text(
                    text = stringResource(R.string.prepare_pageTitle),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PrepareTestTags.TOP_BAR_TITLE),
                )
            }
            HorizontalDivider(color = colors.border)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrepareFailedView(
    failed: LoadState.Failed,
    onMyExams: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val isNotFound = failed.kind == LoadFailureKind.NotFound
    val title = stringResource(
        if (isNotFound) R.string.prepare_notFound_title else R.string.prepare_errorLoad_title,
    )
    val description = stringResource(
        if (isNotFound) R.string.prepare_notFound_description else R.string.prepare_errorLoad_description,
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(
                if (isNotFound) PrepareTestTags.NOT_FOUND_STATE else PrepareTestTags.ERROR_LOAD_STATE,
            ),
        contentPadding = PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        item(key = "prepare_failed_card") {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.dangerBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = PrepareIcons.AlertTriangle,
                            contentDescription = null,
                            tint = colors.dangerText,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (isNotFound) {
                            Button(
                                onClick = onMyExams,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primaryFill,
                                    contentColor = colors.onPrimary,
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag(PrepareTestTags.ERROR_BACK_BUTTON),
                            ) {
                                Text(text = stringResource(R.string.prepare_backToMyExams))
                            }
                        } else {
                            OutlinedButton(
                                onClick = onMyExams,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colors.borderStrong),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag(PrepareTestTags.ERROR_BACK_BUTTON),
                            ) {
                                Text(
                                    text = stringResource(R.string.prepare_backToMyExams),
                                    color = colors.text,
                                )
                            }
                            Button(
                                onClick = onRetry,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primaryFill,
                                    contentColor = colors.onPrimary,
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag(PrepareTestTags.ERROR_RETRY_BUTTON),
                            ) {
                                Text(text = stringResource(R.string.common_retry))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrepareLoadedBody(
    response: AttemptPrepareResponse,
    selection: PreChoiceSelection,
    gate: StartGate,
    startState: StartState,
    supportContact: SupportContact,
    onMyExams: () -> Unit,
    onSelectOption: (String) -> Unit,
    onToggleSubject: (String) -> Unit,
    onStartAttempt: () -> Unit,
    onContinueAttempt: (UUID) -> Unit,
    onBuyMoreAttempts: (String) -> Unit,
    formulaEngine: FormulaEngine?,
) {
    val shellWidth = LocalShellWidth.current
    val locale = LocalAppLocale.current
    val horizontalPadding = when (shellWidth) {
        ShellWidth.Narrow -> 16.dp
        ShellWidth.Wide -> 24.dp
        ShellWidth.TwoColumn -> 28.dp
    }
    val orderedParts = remember(response.parts) {
        response.parts.sortedBy { it.ord ?: 0 }
    }
    val metrics = remember(response, selection) {
        PrepareMetrics.of(response, selection)
    }
    val showPreChoice = response.preChoice != null && gate !is StartGate.InProgress

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PrepareTestTags.LIST),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "prepare_header") {
            PrepareHeaderBlock(
                response = response,
                metrics = metrics,
                locale = locale,
            )
        }

        if (shellWidth == ShellWidth.TwoColumn) {
            item(key = "prepare_two_column") {
                TwoColumnLayout(
                    startWeight = 1.45f,
                    endWeight = 1f,
                    spacing = 20.dp,
                    start = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            if (showPreChoice) {
                                PreChoiceCard(
                                    response = response,
                                    selection = selection,
                                    metrics = metrics,
                                    supportContact = supportContact,
                                    locale = locale,
                                    onSelectOption = onSelectOption,
                                    onToggleSubject = onToggleSubject,
                                )
                            }
                            StructureCard(
                                response = response,
                                orderedParts = orderedParts,
                                locale = locale,
                            )
                            if (response.afterStartChoices.isNotEmpty()) {
                                AfterStartChoicesCard(
                                    choices = response.afterStartChoices,
                                    locale = locale,
                                )
                            }
                            InstructionsCard(
                                instructionsHtml = response.instructions,
                                formulaEngine = formulaEngine,
                            )
                        }
                    },
                    end = {
                        ReadyColumnCard(
                            response = response,
                            metrics = metrics,
                            gate = gate,
                            startState = startState,
                            supportContact = supportContact,
                            locale = locale,
                            onStartAttempt = onStartAttempt,
                            onContinueAttempt = onContinueAttempt,
                            onBuyMoreAttempts = onBuyMoreAttempts,
                            onMyExams = onMyExams,
                        )
                    },
                )
            }
        } else {
            if (showPreChoice) {
                item(key = "prepare_pre_choice") {
                    PreChoiceCard(
                        response = response,
                        selection = selection,
                        metrics = metrics,
                        supportContact = supportContact,
                        locale = locale,
                        onSelectOption = onSelectOption,
                        onToggleSubject = onToggleSubject,
                    )
                }
            }

            item(key = "prepare_ready") {
                ReadyColumnCard(
                    response = response,
                    metrics = metrics,
                    gate = gate,
                    startState = startState,
                    supportContact = supportContact,
                    locale = locale,
                    onStartAttempt = onStartAttempt,
                    onContinueAttempt = onContinueAttempt,
                    onBuyMoreAttempts = onBuyMoreAttempts,
                    onMyExams = onMyExams,
                )
            }

            item(key = "prepare_structure") {
                StructureCard(
                    response = response,
                    orderedParts = orderedParts,
                    locale = locale,
                )
            }

            if (response.afterStartChoices.isNotEmpty()) {
                item(key = "prepare_after_start_choices") {
                    AfterStartChoicesCard(
                        choices = response.afterStartChoices,
                        locale = locale,
                    )
                }
            }

            item(key = "prepare_instructions") {
                InstructionsCard(
                    instructionsHtml = response.instructions,
                    formulaEngine = formulaEngine,
                )
            }
        }

        item(key = "prepare_bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrepareHeaderBlock(
    response: AttemptPrepareResponse,
    metrics: SelectedPathMetrics,
    locale: AppLocale,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val examKindLabel = pickPrepareLocalizedName(
        nameVi = response.examKind.nameVi,
        nameEn = response.examKind.nameEn,
        code = response.examKind.code,
        locale = locale,
    )
    val baseScope = when (response.scope) {
        "FULL" -> stringResource(R.string.prepare_scope_FULL)
        "PART" -> stringResource(R.string.prepare_scope_PART)
        "SUBJECT" -> stringResource(R.string.prepare_scope_SUBJECT)
        else -> response.scope
    }
    val subjectLabel = response.subject?.let { sub ->
        pickPrepareLocalizedName(sub.nameVi, sub.nameEn, sub.code, locale)
    }.orEmpty()
    val scopeText = if (subjectLabel.isNotBlank()) "$baseScope · $subjectLabel" else baseScope

    val summaryParts = buildList {
        if (metrics.partCount > 0) {
            add(pluralStringResource(R.plurals.prepare_units_partCount, metrics.partCount, metrics.partCount))
        }
        metrics.questionCount?.let { count ->
            add(pluralStringResource(R.plurals.prepare_units_questions, count, count))
        }
        metrics.durationMinutes?.let { mins ->
            add(pluralStringResource(R.plurals.prepare_units_minutes, mins, mins))
        }
        metrics.maxPoints?.let { pts ->
            add(stringResource(R.string.prepare_units_points, formatPreparePoints(pts, locale)))
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (examKindLabel.isNotBlank()) {
                PrepareChip(
                    text = examKindLabel,
                    containerColor = colors.primaryContainer,
                    contentColor = colors.primaryText,
                    modifier = Modifier.testTag(PrepareTestTags.HEADER_KIND_CHIP),
                )
            }
            PrepareChip(
                text = scopeText,
                containerColor = colors.bgSubtle,
                contentColor = colors.textSecondary,
                modifier = Modifier.testTag(PrepareTestTags.HEADER_SCOPE_CHIP),
            )
            if (response.buildMode == PREPARE_BUILD_MODE_REVIEW) {
                PrepareChip(
                    text = stringResource(R.string.prepare_reviewBadge),
                    containerColor = statusColors.infoBg,
                    contentColor = statusColors.info,
                    modifier = Modifier.testTag(PrepareTestTags.HEADER_REVIEW_CHIP),
                )
            }
        }

        Text(
            text = response.examName,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
            modifier = Modifier
                .semantics { heading() }
                .testTag(PrepareTestTags.HEADER_TITLE),
        )

        if (summaryParts.isNotEmpty()) {
            Text(
                text = summaryParts.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.testTag(PrepareTestTags.HEADER_SUMMARY),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreChoiceCard(
    response: AttemptPrepareResponse,
    selection: PreChoiceSelection,
    metrics: SelectedPathMetrics,
    supportContact: SupportContact,
    locale: AppLocale,
    onSelectOption: (String) -> Unit,
    onToggleSubject: (String) -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val preChoice = response.preChoice ?: return
    val options = preChoice.options.orEmpty()
    val selectedOption = selection.selectedOption(preChoice)
    val requiredSubjects = PreChoiceSelection.requiredSubjectCount(selectedOption)

    PrepareSectionCard(
        title = stringResource(R.string.prepare_preChoice_title),
        modifier = Modifier.testTag(PrepareTestTags.PRE_CHOICE_SECTION),
    ) {
        if (options.isEmpty()) {
            val contactSummary = supportContact.formattedSummary()
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.dangerBg,
                border = BorderStroke(1.dp, colors.dangerText),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                    .testTag(PrepareTestTags.NO_SERVING_OPTION_ALERT),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = PrepareIcons.AlertTriangle,
                        contentDescription = null,
                        tint = colors.dangerText,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.prepare_noServingOption_title),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.dangerText,
                        )
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
        } else {
            Text(
                text = stringResource(R.string.prepare_preChoice_legend),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                options.forEach { option ->
                    val code = option.code.orEmpty()
                    val isSelected = selection.optionCode == code && code.isNotBlank()
                    PreChoiceOptionItem(
                        option = option,
                        selected = isSelected,
                        locale = locale,
                        onSelect = { if (code.isNotBlank()) onSelectOption(code) },
                    )
                }
            }

            if (requiredSubjects > 0 && selectedOption != null) {
                val subjects = selectedOption.subjects.orEmpty()
                val pickedCount = selection.subjectCodes.size
                val isComplete = pickedCount == requiredSubjects

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.bgPage,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PrepareTestTags.SUBJECT_SECTION),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.prepare_preChoice_subjectLegend),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.text,
                            )
                            PrepareChip(
                                text = stringResource(
                                    R.string.prepare_preChoice_chooseSubjectsProgress,
                                    pickedCount,
                                    requiredSubjects,
                                ),
                                containerColor = if (isComplete) statusColors.successBg else statusColors.warningBg,
                                contentColor = if (isComplete) statusColors.success else statusColors.warning,
                                modifier = Modifier.testTag(PrepareTestTags.SUBJECT_PROGRESS),
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            subjects.forEach { subject ->
                                val subCode = subject.code.orEmpty()
                                val checked = subCode in selection.subjectCodes
                                val limitReached = !checked && pickedCount >= requiredSubjects
                                val label = pickPrepareLocalizedName(
                                    nameVi = subject.nameVi,
                                    nameEn = subject.nameEn,
                                    code = subCode,
                                    locale = locale,
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (checked) colors.primaryContainer else colors.bgSurface,
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (checked) colors.primaryFill else colors.border,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .toggleable(
                                            value = checked,
                                            enabled = !limitReached,
                                            role = Role.Checkbox,
                                            onValueChange = { if (subCode.isNotBlank()) onToggleSubject(subCode) },
                                        )
                                        .testTag(PrepareTestTags.subjectCheckbox(subCode)),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = null,
                                            enabled = !limitReached,
                                        )
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
                                            ),
                                            color = if (limitReached) colors.textMuted else colors.text,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedOption != null && !selectedOption.code.isNullOrBlank()) {
                val pathSummaryParts = buildList {
                    metrics.questionCount?.let {
                        add(pluralStringResource(R.plurals.prepare_units_questions, it, it))
                    }
                    metrics.durationMinutes?.let {
                        add(pluralStringResource(R.plurals.prepare_units_minutes, it, it))
                    }
                    metrics.maxPoints?.let {
                        add(stringResource(R.string.prepare_units_points, formatPreparePoints(it, locale)))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.bgPage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(PrepareTestTags.SELECTED_PATH_SUMMARY),
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.prepare_preChoice_selectedPathSummary,
                                selectedOption.code,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted,
                        )
                        if (pathSummaryParts.isNotEmpty()) {
                            Text(
                                text = pathSummaryParts.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreChoiceOptionItem(
    option: ChoiceOptionView,
    selected: Boolean,
    locale: AppLocale,
    onSelect: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val code = option.code.orEmpty()
    val subjects = option.subjects.orEmpty()
    val requiredSubjects = PreChoiceSelection.requiredSubjectCount(option)
    val optionMetrics = buildList {
        option.questionCount?.let {
            add(pluralStringResource(R.plurals.prepare_units_questions, it, it))
        }
        option.durationMinutes?.let {
            add(pluralStringResource(R.plurals.prepare_units_minutes, it, it))
        }
        option.maxPoints?.let {
            add(stringResource(R.string.prepare_units_points, formatPreparePoints(it, locale)))
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.primaryContainer else colors.bgSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) colors.primaryFill else colors.border,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .testTag(PrepareTestTags.preChoiceOption(code)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (code.isNotBlank()) {
                        Text(
                            text = code,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.text,
                        )
                    }
                    if (!option.name.isNullOrBlank()) {
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                    if (requiredSubjects > 0 && subjects.isNotEmpty()) {
                        Text(
                            text = "(${
                                stringResource(
                                    R.string.prepare_preChoice_chooseSubjectsCount,
                                    requiredSubjects,
                                    subjects.size,
                                )
                            })",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }

                if (subjects.isNotEmpty() && option.kind != PREPARE_OPTION_KIND_SUBJECT_CHOICE) {
                    val subjectLine = subjects
                        .mapNotNull { sub ->
                            pickPrepareLocalizedName(sub.nameVi, sub.nameEn, sub.code, locale)
                                .takeIf { it.isNotBlank() }
                        }
                        .joinToString(" · ")
                    if (subjectLine.isNotEmpty()) {
                        Text(
                            text = subjectLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                        )
                    }
                }

                if (optionMetrics.isNotEmpty()) {
                    Text(
                        text = optionMetrics.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyColumnCard(
    response: AttemptPrepareResponse,
    metrics: SelectedPathMetrics,
    gate: StartGate,
    startState: StartState,
    supportContact: SupportContact,
    locale: AppLocale,
    onStartAttempt: () -> Unit,
    onContinueAttempt: (UUID) -> Unit,
    onBuyMoreAttempts: (String) -> Unit,
    onMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val grantSourceText = if (response.attemptsLeft > 0) {
        formatGrantSourceLabel(response.grantSource)
    } else {
        null
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PrepareTestTags.READY_SECTION),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.prepare_sidebar_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )

            ReadyStatRow(
                label = stringResource(R.string.prepare_sidebar_attemptsLeft),
                value = pluralStringResource(
                    R.plurals.prepare_sidebar_attemptsValue,
                    response.attemptsLeft,
                    response.attemptsLeft,
                ),
                valueColor = if (response.attemptsLeft <= 0) statusColors.warning else colors.text,
                valueTag = PrepareTestTags.READY_ATTEMPTS_LEFT,
            )

            if (grantSourceText != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.prepare_sidebar_grantSource),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Text(
                        text = grantSourceText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        modifier = Modifier.testTag(PrepareTestTags.READY_GRANT_SOURCE),
                    )
                }
            }

            ReadyStatRow(
                label = stringResource(R.string.prepare_sidebar_questions),
                value = metrics.questionCount?.let {
                    pluralStringResource(R.plurals.prepare_units_questions, it, it)
                } ?: "—",
                valueColor = colors.text,
                valueTag = PrepareTestTags.READY_QUESTIONS,
            )

            ReadyStatRow(
                label = stringResource(R.string.prepare_sidebar_duration),
                value = metrics.durationMinutes?.let {
                    pluralStringResource(R.plurals.prepare_units_minutes, it, it)
                } ?: "—",
                valueColor = colors.text,
                valueTag = PrepareTestTags.READY_DURATION,
            )

            ReadyStatRow(
                label = stringResource(R.string.prepare_sidebar_maxPoints),
                value = metrics.maxPoints?.let {
                    stringResource(R.string.prepare_units_points, formatPreparePoints(it, locale))
                } ?: "—",
                valueColor = colors.text,
                valueTag = PrepareTestTags.READY_MAX_POINTS,
            )

            if (gate is StartGate.InProgress) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColors.infoBg,
                    border = BorderStroke(1.dp, statusColors.info),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics(mergeDescendants = true) {}
                        .testTag(PrepareTestTags.IN_PROGRESS_BANNER),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
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
                            text = stringResource(R.string.prepare_inProgressBanner),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Button(
                    onClick = { onContinueAttempt(gate.attemptId) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(PrepareTestTags.CONTINUE_BUTTON),
                ) {
                    Text(text = stringResource(R.string.prepare_continueAttempt))
                }
            } else {
                if (gate is StartGate.Exhausted) {
                    val descRes = when (gate.descriptionKind) {
                        DescriptionKind.Class -> R.string.prepare_exhaustedDescriptionClass
                        DescriptionKind.Published -> R.string.prepare_exhaustedDescription
                        DescriptionKind.Discontinued -> R.string.prepare_exhaustedDescriptionDiscontinued
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.dp, colors.dangerText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .testTag(PrepareTestTags.EXHAUSTED_BANNER),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = PrepareIcons.AlertTriangle,
                                contentDescription = null,
                                tint = colors.dangerText,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.prepare_exhaustedTitle),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.dangerText,
                                )
                                Text(
                                    text = stringResource(descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }

                    if (gate.canBuyMore) {
                        OutlinedButton(
                            onClick = { onBuyMoreAttempts(response.examCode) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.borderStrong),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag(PrepareTestTags.BUY_MORE_BUTTON),
                        ) {
                            Text(
                                text = stringResource(R.string.prepare_buyMoreAttempts),
                                color = colors.text,
                            )
                        }
                    }
                }

                if (startState is StartState.Refused) {
                    val refusal = startState.refusal
                    val contactSummary = supportContact.formattedSummary()
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.dp, colors.dangerText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }
                            .testTag(PrepareTestTags.START_ERROR_BANNER),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = PrepareIcons.AlertTriangle,
                                contentDescription = null,
                                tint = colors.dangerText,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(18.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(StartErrorMessage.messageRes(refusal)),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.dangerText,
                                )
                                if (StartErrorMessage.includesSupportContact(refusal)) {
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

                val isSending = startState == StartState.Sending
                val startEnabled = gate == StartGate.Open && !isSending

                Button(
                    onClick = onStartAttempt,
                    enabled = startEnabled,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag(PrepareTestTags.START_BUTTON),
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = colors.onPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = PrepareIcons.Play,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(text = stringResource(R.string.prepare_startCta))
                }

                val hintText = when (gate) {
                    StartGate.NeedOption -> stringResource(R.string.prepare_preChoice_hintSelectOption)
                    is StartGate.NeedSubjects -> stringResource(
                        R.string.prepare_preChoice_hintSelectSubjects,
                        gate.count,
                    )

                    else -> null
                }
                if (hintText != null) {
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColors.warning,
                        modifier = Modifier.testTag(PrepareTestTags.START_HINT),
                    )
                }

                Text(
                    text = stringResource(R.string.prepare_sidebar_startNote),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.testTag(PrepareTestTags.READY_START_NOTE),
                )
            }

            TextButton(
                onClick = onMyExams,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(PrepareTestTags.READY_BACK_BUTTON),
            ) {
                Icon(
                    imageVector = PrepareIcons.ArrowBack,
                    contentDescription = null,
                    tint = colors.primaryText,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.prepare_backToMyExams),
                    color = colors.primaryText,
                )
            }
        }
    }
}

@Composable
private fun formatGrantSourceLabel(grantSource: GrantSourceView?): String? {
    if (grantSource == null) return null
    return when (grantSource.source) {
        GrantSourceView.Source.COMPENSATION -> stringResource(R.string.prepare_grantSource_compensation)
        GrantSourceView.Source.CLASS -> {
            val dateText = grantSource.grantedAt?.let(::formatPrepareDayMonth)
            if (!dateText.isNullOrBlank()) {
                stringResource(R.string.prepare_grantSource_classAssigned, dateText)
            } else {
                stringResource(R.string.prepare_grantSource_classAssignedNoDate)
            }
        }

        GrantSourceView.Source.PURCHASE,
        null,
        -> {
            val dateText = grantSource.grantedAt?.let(::formatPrepareDayMonth).orEmpty()
            val orderCode = grantSource.orderCode?.trim().orEmpty()
            if (orderCode.isNotEmpty()) {
                stringResource(R.string.prepare_grantSource_purchase, dateText, orderCode)
            } else {
                stringResource(R.string.prepare_grantSource_purchaseNoOrder, dateText)
            }
        }
    }
}

@Composable
private fun ReadyStatRow(
    label: String,
    value: String,
    valueColor: Color,
    valueTag: String,
) {
    val colors = Thi360Theme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .testTag(valueTag),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StructureCard(
    response: AttemptPrepareResponse,
    orderedParts: List<PreparePartView>,
    locale: AppLocale,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val timingDisplay = resolvePrepareTimingDisplay(response)
    val timingTitleRes = when (timingDisplay) {
        PrepareTimingDisplay.Free -> R.string.prepare_timing_FREE_title
        PrepareTimingDisplay.PerPart -> R.string.prepare_timing_PER_PART_title
        PrepareTimingDisplay.Shared -> R.string.prepare_timing_SHARED_title
    }
    val timingDescRes = when (timingDisplay) {
        PrepareTimingDisplay.Free -> R.string.prepare_timing_FREE_description
        PrepareTimingDisplay.PerPart -> R.string.prepare_timing_PER_PART_description
        PrepareTimingDisplay.Shared -> R.string.prepare_timing_SHARED_description
    }

    PrepareSectionCard(
        title = stringResource(R.string.prepare_structure_title),
        modifier = Modifier.testTag(PrepareTestTags.STRUCTURE_SECTION),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colors.bgPage,
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .testTag(PrepareTestTags.TIMING_BANNER),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    imageVector = PrepareIcons.Clock,
                    contentDescription = null,
                    tint = colors.primaryText,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(timingTitleRes),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(timingDescRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        if (orderedParts.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                orderedParts.forEachIndexed { index, part ->
                    StructurePartRow(
                        index = index,
                        part = part,
                        locale = locale,
                    )
                }
            }
        }

        if (response.answerFormats.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PrepareTestTags.ANSWER_FORMATS_SECTION),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.prepare_answerFormatsLabel),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                    color = colors.textMuted,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    response.answerFormats.forEach { code ->
                        val labelRes = AnswerFormatLabel.labelResOrNull(code)
                        val label = if (labelRes != null) stringResource(labelRes) else code
                        PrepareChip(
                            text = label,
                            containerColor = colors.bgSubtle,
                            contentColor = colors.textSecondary,
                        )
                    }
                }
            }
        }

        response.listeningPolicy?.let { policy ->
            ListeningPolicyBox(
                policy = policy,
                statusColors = statusColors,
            )
        }

        if (response.omittedGroups.isNotEmpty()) {
            OmittedGroupsBox(
                groups = response.omittedGroups,
                locale = locale,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StructurePartRow(
    index: Int,
    part: PreparePartView,
    locale: AppLocale,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val partMetrics = buildList {
        part.questionCount?.let {
            add(pluralStringResource(R.plurals.prepare_units_questions, it, it))
        }
        part.durationMinutes?.let {
            add(pluralStringResource(R.plurals.prepare_units_minutes, it, it))
        }
        part.maxPoints?.let {
            add(stringResource(R.string.prepare_units_points, formatPreparePoints(it, locale)))
        }
    }
    val isRequired = part.required == true

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(PrepareTestTags.partRow(index)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.primaryText,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = part.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (partMetrics.isNotEmpty()) {
                        Text(
                            text = partMetrics.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                    PrepareChip(
                        text = stringResource(
                            if (isRequired) R.string.prepare_required else R.string.prepare_optional,
                        ),
                        containerColor = if (isRequired) colors.bgSubtle else statusColors.warningBg,
                        contentColor = if (isRequired) colors.textSecondary else statusColors.warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningPolicyBox(
    policy: ListeningPolicyView,
    statusColors: vn.unicom.thi360.core.theme.StatusColors,
) {
    val colors = Thi360Theme.colors
    val maxPlays = policy.maxPlays ?: 2
    val maxPlaysText = stringResource(R.string.prepare_listeningPolicy_maxPlays, maxPlays)
    val fullText = if (policy.seekable == false) {
        "$maxPlaysText ${stringResource(R.string.prepare_listeningPolicy_noSeek)}"
    } else {
        maxPlaysText
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = statusColors.infoBg,
        border = BorderStroke(1.dp, statusColors.info),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(PrepareTestTags.LISTENING_POLICY_BANNER),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = PrepareIcons.Headphones,
                contentDescription = null,
                tint = statusColors.info,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(18.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.prepare_listeningPolicy_title),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColors.info,
                )
                Text(
                    text = fullText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun OmittedGroupsBox(
    groups: List<OmittedGroupView>,
    locale: AppLocale,
) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.bgPage,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .testTag(PrepareTestTags.OMITTED_GROUPS_SECTION),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.prepare_omittedGroupsLabel),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
            )
            groups.forEach { group ->
                val name = group.name.orEmpty()
                val pointsSuffix = group.points?.let { pts ->
                    " · ${stringResource(R.string.prepare_units_points, formatPreparePoints(pts, locale))}"
                }.orEmpty()
                Text(
                    text = "• $name$pointsSuffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AfterStartChoicesCard(
    choices: List<AfterStartChoiceView>,
    locale: AppLocale,
) {
    val colors = Thi360Theme.colors
    PrepareSectionCard(
        title = stringResource(R.string.prepare_afterStartChoices_title),
        modifier = Modifier.testTag(PrepareTestTags.AFTER_START_CHOICES_SECTION),
    ) {
        choices.forEach { choice ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!choice.partName.isNullOrBlank()) {
                        Text(
                            text = choice.partName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                        )
                    }
                    choice.options.orEmpty().forEach { opt ->
                        val optSubjects = opt.subjects.orEmpty()
                        val subjectLine = optSubjects
                            .mapNotNull { sub ->
                                pickPrepareLocalizedName(sub.nameVi, sub.nameEn, sub.code, locale)
                                    .takeIf { it.isNotBlank() }
                            }
                            .joinToString(", ")
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                if (!opt.code.isNullOrBlank()) {
                                    Text(
                                        text = opt.code,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = colors.text,
                                    )
                                }
                                if (!opt.name.isNullOrBlank()) {
                                    Text(
                                        text = opt.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = colors.text,
                                    )
                                }
                                val chooseCount = opt.chooseSubjects ?: 0
                                if (chooseCount > 0 && optSubjects.isNotEmpty()) {
                                    Text(
                                        text = "(${
                                            stringResource(
                                                R.string.prepare_afterStartChoices_chooseSubjects,
                                                chooseCount,
                                                optSubjects.size,
                                            )
                                        })",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted,
                                    )
                                }
                            }
                            if (subjectLine.isNotEmpty()) {
                                Text(
                                    text = subjectLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "• ${stringResource(R.string.prepare_afterStartChoices_deadlineNote)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Text(
                text = "• ${stringResource(R.string.prepare_afterStartChoices_timerNote)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun InstructionsCard(
    instructionsHtml: String?,
    formulaEngine: FormulaEngine?,
) {
    val colors = Thi360Theme.colors
    val trimmedInstructions = instructionsHtml?.trim().orEmpty()
    val parsedDocument = remember(trimmedInstructions) {
        if (trimmedInstructions.isEmpty()) {
            null
        } else {
            RichHtmlParser.parse(
                html = trimmedInstructions,
                options = RichParseOptions(allowImages = false),
            )
        }
    }

    PrepareSectionCard(
        title = stringResource(R.string.prepare_instructions_title),
        modifier = Modifier.testTag(PrepareTestTags.INSTRUCTIONS_SECTION),
    ) {
        if (parsedDocument != null && parsedDocument.blocks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PrepareTestTags.INSTRUCTIONS_RICH_CONTENT),
            ) {
                RichContentView(
                    document = parsedDocument,
                    assets = null,
                    mode = RichContentMode.Answering,
                    formulaEngine = formulaEngine,
                    textSizeSp = 15f,
                )
            }
        }

        val notes = listOf(
            R.string.prepare_instructions_noteAutoSave,
            R.string.prepare_instructions_noteClock,
            R.string.prepare_instructions_noteOffline,
            R.string.prepare_instructions_noteSingleDevice,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            notes.forEach { noteRes ->
                Text(
                    text = "• ${stringResource(noteRes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun PrepareSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.bgSurface,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.primaryFill),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )
            }
            content()
        }
    }
}

@Composable
private fun PrepareChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
