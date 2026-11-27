package vn.unicom.thi360.feature.player

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.ChoiceOptionView
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.attemptengine.OpenApiAttemptApi

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PartChoicePanel(
    panel: ChoicePanelUi,
    onSelectOption: (String) -> Unit,
    onToggleSubject: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetryLoadOptions: () -> Unit,
    onBackToMyExams: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .testTag(PlayerTestTags.CHOICE_PANEL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter,
        ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.bgSurface,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.attempt_partChoice_title),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.text,
                                modifier = Modifier.semantics { heading() },
                            )
                            Text(
                                text = stringResource(R.string.attempt_partChoice_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                        }

                        if (panel.isTimerVisible) {
                            ChoiceCountdownBanner(panel = panel)
                        }

                        when {
                            panel.metadataState is LoadState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = colors.primaryFill)
                                }
                            }

                            panel.metadataState is LoadState.Failed || panel.options.isEmpty() -> {
                                ChoiceOptionsLoadErrorCard(
                                    onRetry = onRetryLoadOptions,
                                    onBackToMyExams = onBackToMyExams,
                                )
                            }

                            else -> {
                                Text(
                                    text = stringResource(R.string.attempt_partChoice_legendOption),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.text,
                                )

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    panel.options.forEach { option ->
                                        val code = option.code.orEmpty()
                                        val isSelected = code.isNotEmpty() && code == panel.selectedOptionCode
                                        ChoiceOptionCard(
                                            option = option,
                                            selected = isSelected,
                                            enabled = !panel.isExpired && !panel.isSending,
                                            subjectsByCode = panel.subjectsByCode,
                                            onClick = { if (code.isNotEmpty()) onSelectOption(code) },
                                        )
                                    }
                                }

                                val activeOption = panel.options.firstOrNull { it.code == panel.selectedOptionCode }
                                if (panel.requiredSubjectCount > 0 && activeOption != null) {
                                    val availableSubjects = activeOption.subjects.orEmpty()
                                    val limitReached = panel.selectedSubjectCodes.size >= panel.requiredSubjectCount

                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.attempt_partChoice_legendSubjects),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.text,
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = if (limitReached) statusColors.successBg else colors.bgSubtle,
                                                modifier = Modifier
                                                    .semantics(mergeDescendants = true) {}
                                                    .testTag(PlayerTestTags.CHOICE_SUBJECT_PROGRESS),
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.attempt_partChoice_selectedProgress,
                                                        panel.selectedSubjectCodes.size,
                                                        panel.requiredSubjectCount,
                                                    ),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (limitReached) statusColors.success else colors.textSecondary,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                )
                                            }
                                        }

                                        if (limitReached) {
                                            Text(
                                                text = stringResource(
                                                    R.string.attempt_partChoice_subjectsLimitReached,
                                                    panel.requiredSubjectCount,
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = statusColors.info,
                                                modifier = Modifier.testTag(PlayerTestTags.CHOICE_SUBJECT_LIMIT),
                                            )
                                        }

                                        availableSubjects.forEach { subj ->
                                            val subjCode = subj.code.orEmpty()
                                            val isChecked = subjCode in panel.selectedSubjectCodes
                                            val canToggle = !panel.isExpired && !panel.isSending && (isChecked || !limitReached)
                                            val subjLabel = SubjectNames.resolveLocalized(
                                                code = subjCode,
                                                subjectsByCode = panel.subjectsByCode,
                                            )
                                            val shape = RoundedCornerShape(12.dp)
                                            Surface(
                                                shape = shape,
                                                color = if (isChecked) colors.primaryContainer else colors.bgSurface,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isChecked) colors.primaryFill else colors.border,
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .clip(shape)
                                                    .clickable(
                                                        enabled = canToggle,
                                                        role = Role.Checkbox,
                                                        onClick = { onToggleSubject(subjCode) },
                                                    )
                                                    .testTag(PlayerTestTags.choiceSubject(subjCode)),
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                ) {
                                                    Checkbox(
                                                        checked = isChecked,
                                                        onCheckedChange = null,
                                                        enabled = canToggle,
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = colors.primaryFill,
                                                            checkmarkColor = colors.onPrimary,
                                                        ),
                                                    )
                                                    Text(
                                                        text = subjLabel,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal,
                                                        ),
                                                        color = if (canToggle || isChecked) colors.text else colors.textMuted,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (panel.errorCode != null) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = colors.dangerBg,
                                        border = BorderStroke(1.dp, colors.dangerText),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Assertive }
                                            .testTag(PlayerTestTags.CHOICE_ERROR_BANNER),
                                    ) {
                                        Text(
                                            text = partChoiceErrorMessage(panel.errorCode),
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = colors.dangerText,
                                            modifier = Modifier.padding(12.dp),
                                        )
                                    }
                                }

                                Button(
                                    onClick = onConfirm,
                                    enabled = panel.canConfirm,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.primaryFill,
                                        contentColor = colors.onPrimary,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag(PlayerTestTags.CHOICE_CONFIRM),
                                ) {
                                    if (panel.isSending) {
                                        CircularProgressIndicator(
                                            color = colors.onPrimary,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = stringResource(R.string.attempt_partChoice_confirmCta),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    )
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun ChoiceCountdownBanner(panel: ChoicePanelUi) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val urgentOrExpired = panel.isUrgent || panel.isExpired

    val bg = if (urgentOrExpired) colors.dangerBg else statusColors.warningBg
    val border = if (urgentOrExpired) colors.dangerText else statusColors.warning.copy(alpha = 0.5f)
    val fg = if (urgentOrExpired) colors.dangerText else statusColors.warning

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag(PlayerTestTags.CHOICE_TIMER),
            ) {
                Icon(
                    imageVector = PlayerIcons.Clock,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.attempt_partChoice_timerLabel),
                    style = MaterialTheme.typography.labelLarge,
                    color = fg,
                )
                Text(
                    text = TimerText.format(panel.remainingMs),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = fg,
                )
            }
            if (panel.isExpired) {
                Text(
                    text = stringResource(R.string.attempt_partChoice_deadlineExpired),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.dangerText,
                    modifier = Modifier.testTag(PlayerTestTags.CHOICE_DEADLINE_EXPIRED),
                )
            } else {
                Text(
                    text = stringResource(R.string.attempt_partChoice_deadlineWarning),
                    style = MaterialTheme.typography.bodySmall,
                    color = fg,
                    modifier = Modifier.testTag(PlayerTestTags.CHOICE_DEADLINE_WARNING),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceOptionCard(
    option: ChoiceOptionView,
    selected: Boolean,
    enabled: Boolean,
    subjectsByCode: Map<String, vn.unicom.thi360.core.network.generated.model.ChoiceSubjectView>,
    onClick: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val code = option.code.orEmpty()
    val shape = RoundedCornerShape(12.dp)

    Surface(
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.bgSurface,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) colors.primaryFill else colors.border,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(shape)
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .testTag(PlayerTestTags.choiceOption(code)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
                colors = RadioButtonDefaults.colors(
                    selectedColor = colors.primaryFill,
                    unselectedColor = colors.borderStrong,
                ),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = option.name?.takeIf { it.isNotBlank() } ?: code,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    option.questionCount?.takeIf { it > 0 }?.let { qCount ->
                        Text(
                            text = stringResource(R.string.attempt_partChoice_optionQuestions, qCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                    option.durationMinutes?.takeIf { it > 0 }?.let { mins ->
                        Text(
                            text = stringResource(R.string.attempt_partChoice_optionMinutes, mins),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                    }
                }

                val counts = option.countsByPick.orEmpty()
                if (counts.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.attempt_partChoice_countsPerSubject,
                            counts.joinToString("-"),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }

                val isFixedSubjects = option.kind != "SUBJECT_CHOICE" && !option.subjects.isNullOrEmpty()
                if (isFixedSubjects) {
                    val resolvedNames = option.subjects.orEmpty()
                        .mapNotNull { it.code }
                        .map { subjCode -> SubjectNames.resolveLocalized(subjCode, subjectsByCode) }
                        .filter { it.isNotBlank() }
                    if (resolvedNames.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.attempt_partChoice_fixedSubjects,
                                resolvedNames.joinToString(", "),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceOptionsLoadErrorCard(
    onRetry: () -> Unit,
    onBackToMyExams: () -> Unit,
) {
    val colors = Thi360Theme.colors

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.dangerBg,
        border = BorderStroke(1.dp, colors.dangerText.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PlayerTestTags.CHOICE_LOAD_ERROR),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.prepare_errorLoad_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.dangerText,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.prepare_errorLoad_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryFill,
                        contentColor = colors.onPrimary,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(PlayerTestTags.CHOICE_LOAD_RETRY),
                ) {
                    Text(text = stringResource(R.string.common_retry))
                }
                OutlinedButton(
                    onClick = onBackToMyExams,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, colors.borderStrong),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(PlayerTestTags.CHOICE_LOAD_MY_EXAMS),
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

@Composable
internal fun partChoiceErrorMessage(code: String?): String = when (code) {
    OpenApiAttemptApi.CODE_ATTEMPT_SESSION_REVOKED ->
        stringResource(R.string.attempt_partChoice_errors_ATTEMPT_SESSION_REVOKED)
    OpenApiAttemptApi.CODE_CHOICE_LOCKED ->
        stringResource(R.string.attempt_partChoice_errors_CHOICE_LOCKED)
    "CHOICE_NOT_ALLOWED" ->
        stringResource(R.string.attempt_partChoice_errors_CHOICE_NOT_ALLOWED)
    OpenApiAttemptApi.CODE_CHOICE_WINDOW_CLOSED ->
        stringResource(R.string.attempt_partChoice_errors_CHOICE_WINDOW_CLOSED)
    OpenApiAttemptApi.CODE_PART_GENERATING ->
        stringResource(R.string.attempt_partChoice_errors_PART_GENERATING)
    OpenApiAttemptApi.CODE_PART_NOT_AWAITING ->
        stringResource(R.string.attempt_partChoice_errors_PART_NOT_AWAITING)
    OpenApiAttemptApi.CODE_RATE_LIMITED ->
        stringResource(R.string.attempt_partChoice_errors_RATE_LIMITED)
    "SUBJECTS_INVALID" ->
        stringResource(R.string.attempt_partChoice_errors_SUBJECTS_INVALID)
    else -> stringResource(R.string.attempt_partChoice_errors_generic)
}
