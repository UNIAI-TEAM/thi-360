package vn.unicom.thi360.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.OffsetDateTime
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.attemptengine.AttemptCommandKind
import vn.unicom.thi360.feature.attemptengine.CommandState
import vn.unicom.thi360.feature.attemptengine.UnconfirmedCommandKind

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TakeoverDialog(
    examTitle: String,
    lastSeenAt: OffsetDateTime?,
    isSending: Boolean,
    errorCode: String?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val locale = LocalAppLocale.current

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .testTag(PlayerTestTags.TAKEOVER_DIALOG),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.attempt_takeover_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )

                Text(
                    text = stringResource(R.string.attempt_takeover_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.bgSubtle,
                    border = BorderStroke(1.dp, colors.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (examTitle.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.attempt_takeover_attemptLabel),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                )
                                Text(
                                    text = examTitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.text,
                                )
                            }
                        }

                        if (lastSeenAt != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.attempt_takeover_lastSeenLabel),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted,
                                )
                                Text(
                                    text = TimerText.formatLastSeenAt(lastSeenAt, locale),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = colors.text,
                                    modifier = Modifier.testTag(PlayerTestTags.TAKEOVER_LAST_SEEN),
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColors.warningBg,
                    border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "• ${stringResource(R.string.attempt_takeover_noteClock)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColors.warning,
                        )
                        Text(
                            text = "• ${stringResource(R.string.attempt_takeover_noteUnsaved)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColors.warning,
                        )
                    }
                }

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
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = !isSending,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.TAKEOVER_CANCEL),
                    ) {
                        Text(
                            text = stringResource(R.string.attempt_takeover_cancel),
                            color = colors.text,
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !isSending,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryFill,
                            contentColor = colors.onPrimary,
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.TAKEOVER_CONFIRM),
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                color = colors.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = stringResource(R.string.attempt_takeover_confirm))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FinishPartDialog(
    summary: FinishPartSummaryUi,
    commandState: CommandState,
    refusalError: CommandState.Refused?,
    onConfirm: () -> Unit,
    onReconcile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val isSending = commandState is CommandState.Sending && commandState.kind == AttemptCommandKind.FinishPart
    val isUnconfirmed = commandState is CommandState.Unconfirmed &&
        commandState.kind == UnconfirmedCommandKind.FinishPart

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isSending,
            dismissOnClickOutside = !isSending,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .testTag(PlayerTestTags.FINISH_PART_DIALOG),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (isUnconfirmed) {
                        stringResource(R.string.attempt_dialogs_finishPart_unconfirmedTitle)
                    } else {
                        stringResource(R.string.attempt_dialogs_finishPart_title)
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )

                if (isUnconfirmed) {
                    Text(
                        text = stringResource(R.string.attempt_dialogs_finishPart_unconfirmedDesc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.attempt_dialogs_finishPart_warningIrreversible),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.bgSubtle,
                        border = BorderStroke(1.dp, colors.border),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (summary.partName.isNotBlank()) {
                                Text(
                                    text = summary.partName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.text,
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.attempt_dialogs_finishPart_missingSummary,
                                    summary.missingCount,
                                    summary.totalCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = if (summary.missingCount > 0) statusColors.warning else statusColors.success,
                            )
                            if (summary.partialCount > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.attempt_dialogs_finishPart_partialSummary,
                                        summary.partialCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }

                    if (summary.hasNextPart) {
                        val nextNote = if (summary.nextPartNeedsChoice) {
                            stringResource(R.string.attempt_dialogs_finishPart_nextPartChoiceNote)
                        } else {
                            stringResource(R.string.attempt_dialogs_finishPart_nextPartOpensNote)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColors.infoBg,
                            border = BorderStroke(1.dp, statusColors.info.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = nextNote,
                                style = MaterialTheme.typography.bodySmall,
                                color = statusColors.info,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }

                if (refusalError != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.dp, colors.dangerText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(PlayerTestTags.FINISH_PART_ERROR),
                    ) {
                        Text(
                            text = stringResource(R.string.attempt_dialogs_finishPart_genericError),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.dangerText,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSending,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.FINISH_PART_CANCEL),
                    ) {
                        Text(
                            text = stringResource(R.string.attempt_dialogs_finishPart_cancel),
                            color = colors.text,
                        )
                    }

                    if (isUnconfirmed) {
                        Button(
                            onClick = onReconcile,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryFill,
                                contentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(PlayerTestTags.FINISH_PART_RECONCILE),
                        ) {
                            Text(text = stringResource(R.string.attempt_dialogs_finishPart_reconcile))
                        }
                    } else {
                        Button(
                            onClick = onConfirm,
                            enabled = !isSending,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryFill,
                                contentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(PlayerTestTags.FINISH_PART_CONFIRM),
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    color = colors.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = stringResource(R.string.attempt_dialogs_finishPart_confirm))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SubmitDialog(
    summary: SubmitSummaryUi,
    commandState: CommandState,
    refusalError: CommandState.Refused?,
    onConfirm: () -> Unit,
    onReconcile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val isSending = commandState is CommandState.Sending && commandState.kind == AttemptCommandKind.Submit
    val isUnconfirmed = commandState is CommandState.Unconfirmed &&
        commandState.kind == UnconfirmedCommandKind.Submit

    Dialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isSending,
            dismissOnClickOutside = !isSending,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 580.dp)
                .fillMaxWidth()
                .testTag(PlayerTestTags.SUBMIT_DIALOG),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (isUnconfirmed) {
                        stringResource(R.string.attempt_dialogs_submitConfirm_unconfirmedTitle)
                    } else {
                        stringResource(R.string.attempt_dialogs_submitConfirm_title)
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )

                Text(
                    text = if (isUnconfirmed) {
                        stringResource(R.string.attempt_dialogs_submitConfirm_unconfirmedDesc)
                    } else {
                        stringResource(R.string.attempt_dialogs_submitConfirm_description)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                if (!isUnconfirmed) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SummaryCountTile(
                                text = stringResource(
                                    R.string.attempt_dialogs_submitConfirm_answeredCount,
                                    summary.answeredCount,
                                    summary.totalCount,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            SummaryCountTile(
                                text = stringResource(
                                    R.string.attempt_dialogs_submitConfirm_partialCount,
                                    summary.partialCount,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SummaryCountTile(
                                text = stringResource(
                                    R.string.attempt_dialogs_submitConfirm_blankCount,
                                    summary.blankCount,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            SummaryCountTile(
                                text = stringResource(
                                    R.string.attempt_dialogs_submitConfirm_flaggedCount,
                                    summary.flaggedCount,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    if (summary.partBreakdown.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(PlayerTestTags.SUBMIT_PART_BREAKDOWN),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.attempt_dialogs_submitConfirm_partBreakdownTitle),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textSecondary,
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.bgSurface,
                                border = BorderStroke(1.dp, colors.border),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    summary.partBreakdown.forEachIndexed { idx, row ->
                                        if (idx > 0) HorizontalDivider(color = colors.border)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = row.partName,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = colors.text,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(
                                                    R.string.attempt_dialogs_submitConfirm_partMissingRow,
                                                    row.missingCount,
                                                    row.totalCount,
                                                ),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontFeatureSettings = "tnum",
                                                ),
                                                color = colors.textMuted,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    summary.essayStatus?.let { essay ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.bgSubtle,
                            border = BorderStroke(1.dp, colors.border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(PlayerTestTags.SUBMIT_ESSAY_SECTION),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.attempt_dialogs_submitConfirm_essaySectionTitle),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = colors.text,
                                )
                                Text(
                                    text = if (essay.hasUnsavedWords) {
                                        stringResource(
                                            R.string.attempt_dialogs_submitConfirm_essayUnsaved,
                                            essay.totalWords,
                                        )
                                    } else {
                                        stringResource(
                                            R.string.attempt_dialogs_submitConfirm_essaySaved,
                                            essay.totalWords,
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (essay.hasUnsavedWords) statusColors.warning else colors.textSecondary,
                                )
                            }
                        }
                    }

                    if (summary.essayWarnings.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColors.warningBg,
                            border = BorderStroke(1.dp, statusColors.warning),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(PlayerTestTags.SUBMIT_ESSAY_WARNING),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Icon(
                                        imageVector = PlayerIcons.Warning,
                                        contentDescription = null,
                                        tint = statusColors.warning,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.attempt_dialogs_submitConfirm_essayUnderLimitTitle),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = statusColors.warning,
                                    )
                                }
                                summary.essayWarnings.forEach { warning ->
                                    Text(
                                        text = "• ${
                                            stringResource(
                                                R.string.attempt_dialogs_submitConfirm_essayUnderLimitItem,
                                                warning.displayNo,
                                                warning.wordCount,
                                                warning.wordLimit,
                                            )
                                        }",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = statusColors.warning,
                                    )
                                }
                            }
                        }
                    }
                }

                if (refusalError != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.dangerBg,
                        border = BorderStroke(1.dp, colors.dangerText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(PlayerTestTags.SUBMIT_ERROR),
                    ) {
                        Text(
                            text = stringResource(R.string.attempt_dialogs_submitConfirm_genericError),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.dangerText,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSending,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.SUBMIT_CANCEL),
                    ) {
                        Text(
                            text = stringResource(R.string.attempt_dialogs_submitConfirm_cancel),
                            color = colors.text,
                        )
                    }

                    if (isUnconfirmed) {
                        Button(
                            onClick = onReconcile,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryFill,
                                contentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(PlayerTestTags.SUBMIT_RECONCILE),
                        ) {
                            Text(text = stringResource(R.string.attempt_dialogs_submitConfirm_reconcile))
                        }
                    } else {
                        Button(
                            onClick = onConfirm,
                            enabled = !isSending,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primaryFill,
                                contentColor = colors.onPrimary,
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(PlayerTestTags.SUBMIT_CONFIRM),
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    color = colors.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = stringResource(R.string.attempt_dialogs_submitConfirm_confirm))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LeaveConfirmDialog(
    onStay: () -> Unit,
    onLeave: () -> Unit,
) {
    val colors = Thi360Theme.colors

    Dialog(
        onDismissRequest = onStay,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.bgSurface,
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .testTag(PlayerTestTags.LEAVE_DIALOG),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.attempt_dialogs_leaveConfirm_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    modifier = Modifier.semantics { heading() },
                )

                Text(
                    text = stringResource(R.string.attempt_dialogs_leaveConfirm_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onLeave,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.borderStrong),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.LEAVE_CONFIRM),
                    ) {
                        Text(
                            text = stringResource(R.string.attempt_dialogs_leaveConfirm_leave),
                            color = colors.dangerText,
                        )
                    }
                    Button(
                        onClick = onStay,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primaryFill,
                            contentColor = colors.onPrimary,
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag(PlayerTestTags.LEAVE_STAY),
                    ) {
                        Text(text = stringResource(R.string.attempt_dialogs_leaveConfirm_stay))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCountTile(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.bgSubtle,
        border = BorderStroke(1.dp, colors.border),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = colors.text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
