package vn.unicom.thi360.feature.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import vn.unicom.thi360.R
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.network.generated.model.AttemptResultPart
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.network.generated.model.AttemptResultSegment
import vn.unicom.thi360.core.network.generated.model.AttemptResultSubject
import vn.unicom.thi360.core.network.generated.model.AttemptResultUnitStats
import vn.unicom.thi360.core.network.generated.model.AttemptResultVactComponent
import vn.unicom.thi360.core.network.generated.model.AttemptResultVactSubsection
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme

@Composable
internal fun PartsBreakdownSection(
    parts: List<AttemptResultPart>,
    attemptState: AttemptResultResponse.State?,
    endReason: AttemptResultResponse.EndReason?,
    modifier: Modifier = Modifier,
) {
    if (parts.isEmpty()) return
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val isPendingAttempt = attemptState == AttemptResultResponse.State.PENDING_REVIEW ||
        parts.any { it.pendingReview == true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ResultTestTags.PARTS_SECTION),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.result_parts_title),
            description = stringResource(
                if (isPendingAttempt) {
                    R.string.result_parts_pendingDescription
                } else {
                    R.string.result_parts_description
                },
            ),
        )

        parts.forEachIndexed { index, part ->
            val partTitle = part.partName?.takeIf { it.isNotBlank() }
                ?: part.partCode?.takeIf { it.isNotBlank() }
                ?: "—"
            val isSkipped = part.skipped == true
            val isPending = part.pendingReview == true

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ResultTestTags.partCard(index)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = partTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                            modifier = Modifier.weight(1f),
                        )
                        when {
                            isSkipped -> ResultPill(
                                text = stringResource(R.string.result_parts_skippedBadge),
                                containerColor = colors.bgSubtle,
                                contentColor = colors.textSecondary,
                            )
                            isPending -> ResultPill(
                                text = stringResource(R.string.result_parts_manualChip),
                                containerColor = statusColors.warningBg,
                                contentColor = statusColors.warning,
                            )
                        }
                    }

                    when {
                        isSkipped -> {
                            val maxFormatted = part.maxScore?.let { ResultFormat.formatScore(it, locale) }
                            if (maxFormatted != null) {
                                Text(
                                    text = "${ResultFormat.formatScore(BigDecimal.ZERO, locale)}/$maxFormatted",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textSecondary,
                                )
                            }
                            val skipReason = when (endReason) {
                                AttemptResultResponse.EndReason.CHOICE_TIMEOUT ->
                                    stringResource(R.string.result_parts_skippedChoiceTimeout)
                                AttemptResultResponse.EndReason.FAULT_CLOSED ->
                                    stringResource(R.string.result_parts_skippedFaultClosed)
                                else ->
                                    stringResource(R.string.result_parts_skippedFallback)
                            }
                            Text(
                                text = skipReason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textMuted,
                            )
                        }

                        isPending -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ResultPill(
                                    text = stringResource(R.string.result_states_PENDING_REVIEW),
                                    containerColor = statusColors.warningBg,
                                    contentColor = statusColors.warning,
                                )
                                part.maxScore?.let { maxScore ->
                                    Text(
                                        text = stringResource(
                                            R.string.result_parts_maxScoreNote,
                                            ResultFormat.formatScore(maxScore, locale),
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                            part.stats?.let { stats ->
                                BreakdownStatChips(stats = stats, includePending = true)
                            }
                        }

                        else -> {
                            if (part.score != null && part.maxScore != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.result_parts_partScoreLabel),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary,
                                    )
                                    Text(
                                        text = "${ResultFormat.formatScore(part.score, locale)}/${ResultFormat.formatScore(part.maxScore, locale)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primaryText,
                                    )
                                }
                            }
                            part.stats?.let { stats ->
                                BreakdownStatChips(stats = stats, includePending = false)
                                StatsRatioBar(stats = stats)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SegmentsBreakdownSection(
    segments: List<AttemptResultSegment>,
    attemptState: AttemptResultResponse.State?,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) return
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val isPendingAttempt = attemptState == AttemptResultResponse.State.PENDING_REVIEW ||
        segments.any { it.pendingReview == true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ResultTestTags.SEGMENTS_SECTION),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.result_segments_title),
            description = stringResource(
                if (isPendingAttempt) {
                    R.string.result_segments_pendingDescription
                } else {
                    R.string.result_segments_description
                },
            ),
        )

        segments.forEachIndexed { index, segment ->
            val title = segment.name?.takeIf { it.isNotBlank() }
                ?: segment.code?.takeIf { it.isNotBlank() }
                ?: "—"
            val isPending = segment.pendingReview == true
            val rangeText = if (segment.fromPosition != null && segment.toPosition != null) {
                stringResource(R.string.result_segments_range, segment.fromPosition, segment.toPosition)
            } else {
                null
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ResultTestTags.segmentCard(index)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.text,
                            )
                            if (rangeText != null) {
                                Text(
                                    text = rangeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted,
                                )
                            }
                        }
                        if (isPending) {
                            ResultPill(
                                text = stringResource(R.string.result_segments_manualChip),
                                containerColor = statusColors.warningBg,
                                contentColor = statusColors.warning,
                            )
                        }
                    }

                    segment.stats?.let { stats ->
                        BreakdownStatChips(
                            stats = stats,
                            includePending = true,
                            correctLabel = stringResource(R.string.result_segments_columns_correct),
                            partialLabel = stringResource(R.string.result_segments_columns_partial),
                            wrongLabel = stringResource(R.string.result_segments_columns_wrong),
                            blankLabel = stringResource(R.string.result_segments_columns_blank),
                            pendingLabel = stringResource(R.string.result_segments_columns_pending),
                        )
                    }

                    if (isPending) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.result_segments_segmentScoreLabel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            ResultPill(
                                text = stringResource(R.string.result_states_PENDING_REVIEW),
                                containerColor = statusColors.warningBg,
                                contentColor = statusColors.warning,
                            )
                        }
                    } else if (segment.score != null) {
                        val scoreFormatted = ResultFormat.formatScore(segment.score, locale)
                        val valueText = if (segment.maxPoints != null) {
                            "$scoreFormatted/${ResultFormat.formatScore(segment.maxPoints, locale)}"
                        } else {
                            scoreFormatted
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.result_segments_segmentScoreLabel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SubjectsBreakdownSection(
    subjects: List<AttemptResultSubject>,
    subjectCatalog: Map<String, CatalogOptionItem>,
    attemptState: AttemptResultResponse.State?,
    modifier: Modifier = Modifier,
) {
    if (subjects.isEmpty()) return
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val isPendingAttempt = attemptState == AttemptResultResponse.State.PENDING_REVIEW ||
        subjects.any { it.pendingReview == true }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ResultTestTags.SUBJECTS_SECTION),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.result_subjects_title),
            description = stringResource(
                if (isPendingAttempt) {
                    R.string.result_subjects_pendingDescription
                } else {
                    R.string.result_subjects_description
                },
            ),
        )

        subjects.forEachIndexed { index, subject ->
            val label = ResultFormat.resolveSubjectLabel(subject, subjectCatalog, locale)
            val isPending = subject.pendingReview == true

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ResultTestTags.subjectCard(index)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                            modifier = Modifier.weight(1f),
                        )
                        if (isPending) {
                            ResultPill(
                                text = stringResource(R.string.result_subjects_manualChip),
                                containerColor = statusColors.warningBg,
                                contentColor = statusColors.warning,
                            )
                        }
                    }

                    subject.stats?.let { stats ->
                        BreakdownStatChips(
                            stats = stats,
                            includePending = true,
                            correctLabel = stringResource(R.string.result_subjects_columns_correct),
                            partialLabel = stringResource(R.string.result_subjects_columns_partial),
                            wrongLabel = stringResource(R.string.result_subjects_columns_wrong),
                            blankLabel = stringResource(R.string.result_subjects_columns_blank),
                            pendingLabel = stringResource(R.string.result_subjects_columns_pending),
                        )
                    }

                    if (isPending) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.result_subjects_subjectScoreLabel),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary,
                                )
                                subject.maxScore?.let { maxScore ->
                                    Text(
                                        text = stringResource(
                                            R.string.result_subjects_maxScoreNote,
                                            ResultFormat.formatScore(maxScore, locale),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted,
                                    )
                                }
                            }
                            ResultPill(
                                text = stringResource(R.string.result_states_PENDING_REVIEW),
                                containerColor = statusColors.warningBg,
                                contentColor = statusColors.warning,
                            )
                        }
                    } else if (subject.score != null && subject.maxScore != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.result_subjects_subjectScoreLabel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                            )
                            Text(
                                text = "${ResultFormat.formatScore(subject.score, locale)}/${ResultFormat.formatScore(subject.maxScore, locale)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun VactComponentsSection(
    components: List<AttemptResultVactComponent>,
    subjectCatalog: Map<String, CatalogOptionItem>,
    modifier: Modifier = Modifier,
) {
    if (components.isEmpty()) return
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val knownLabels = mapOf(
        KnownVactComponentCode.VIETNAMESE to stringResource(R.string.result_vact_components_VIETNAMESE),
        KnownVactComponentCode.ANH to stringResource(R.string.result_vact_components_ANH),
        KnownVactComponentCode.TOAN to stringResource(R.string.result_vact_components_TOAN),
        KnownVactComponentCode.SCIENTIFIC_THINKING to stringResource(R.string.result_vact_components_SCIENTIFIC_THINKING),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ResultTestTags.VACT_COMPONENTS_SECTION),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.result_vact_componentsTitle),
            description = stringResource(R.string.result_vact_componentsCaption),
        )

        components.forEach { component ->
            val codeKey = component.componentCode ?: component.name ?: "unknown"
            val title = ResultFormat.resolveVactComponentLabel(
                componentCode = component.componentCode,
                subjectMap = subjectCatalog,
                locale = locale,
                translateKnown = { knownLabels[it] ?: it.name },
            )
            val correct = component.correctCount ?: 0
            val total = component.totalCount ?: 0
            val rate = component.correctRate ?: computeRatePercent(correct, total)
            val rateFormatted = ResultFormat.formatScore(rate, locale)
            val ratioFraction = if (total > 0) (correct.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            val subsections = component.subsections.orEmpty()
            var expanded by rememberSaveable(codeKey) { mutableStateOf(false) }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.bgSurface,
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ResultTestTags.vactComponentCard(codeKey)),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                    )
                    Text(
                        text = stringResource(
                            R.string.result_vact_correctRatio,
                            correct,
                            total,
                            rateFormatted,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.bgSubtle),
                    ) {
                        if (ratioFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(ratioFraction)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(statusColors.success),
                            )
                        }
                    }

                    if (subsections.isNotEmpty()) {
                        TextButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(ResultTestTags.VACT_SUBSECTIONS_TOGGLE),
                        ) {
                            Text(
                                text = stringResource(
                                    if (expanded) {
                                        R.string.result_vact_collapseSubsections
                                    } else {
                                        R.string.result_vact_expandSubsections
                                    },
                                ),
                                color = colors.primaryText,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Icon(
                                imageVector = if (expanded) ResultIcons.ChevronUp else ResultIcons.ChevronDown,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        if (expanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(ResultTestTags.VACT_SUBSECTIONS_LIST),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                subsections.forEachIndexed { idx, sub ->
                                    VactSubsectionRow(
                                        sub = sub,
                                        index = idx,
                                        totalSubsections = subsections.size,
                                        locale = locale,
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
private fun VactSubsectionRow(
    sub: AttemptResultVactSubsection,
    index: Int,
    totalSubsections: Int,
    locale: AppLocale,
) {
    val colors = Thi360Theme.colors
    val isLogic = ResultFormat.isLogicDataSubsection(sub, index, totalSubsections)
    val subTitle = stringResource(
        if (isLogic) {
            R.string.result_vact_subsections_logicData
        } else {
            R.string.result_vact_subsections_scientificReasoning
        },
    )
    val qCount = sub.questionCount ?: sub.totalCount ?: 0
    val correct = sub.correctCount ?: 0
    val total = sub.totalCount ?: qCount
    val rate = sub.correctRate ?: computeRatePercent(correct, total)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colors.bgSubtle,
        border = BorderStroke(1.dp, colors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = pluralStringResource(R.plurals.result_vact_subsectionQuestions, qCount, qCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            Text(
                text = stringResource(
                    R.string.result_vact_correctRatio,
                    correct,
                    total,
                    ResultFormat.formatScore(rate, locale),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BreakdownStatChips(
    stats: AttemptResultUnitStats,
    includePending: Boolean,
    correctLabel: String = stringResource(R.string.result_stats_correct),
    partialLabel: String = stringResource(R.string.result_stats_partial),
    wrongLabel: String = stringResource(R.string.result_stats_wrong),
    blankLabel: String = stringResource(R.string.result_stats_blank),
    pendingLabel: String = stringResource(R.string.result_stats_pending),
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val correct = stats.correctCount ?: 0
    val partial = stats.partialCount ?: 0
    val wrong = stats.wrongCount ?: 0
    val blank = stats.blankCount ?: 0
    val pending = stats.pendingReviewCount ?: 0

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ResultPill(
            text = "$correctLabel: $correct",
            containerColor = statusColors.successBg,
            contentColor = statusColors.success,
        )
        if (partial > 0) {
            ResultPill(
                text = "$partialLabel: $partial",
                containerColor = statusColors.warningBg,
                contentColor = statusColors.warning,
            )
        }
        ResultPill(
            text = "$wrongLabel: $wrong",
            containerColor = colors.dangerBg,
            contentColor = colors.dangerText,
        )
        ResultPill(
            text = "$blankLabel: $blank",
            containerColor = colors.bgSubtle,
            contentColor = colors.textSecondary,
        )
        if (includePending && pending > 0) {
            ResultPill(
                text = "$pendingLabel: $pending",
                containerColor = statusColors.warningBg,
                contentColor = statusColors.warning,
            )
        }
    }
}

@Composable
internal fun StatsRatioBar(
    stats: AttemptResultUnitStats,
    modifier: Modifier = Modifier,
) {
    val colors = Thi360Theme.colors
    val statusColors = Thi360Theme.statusColors
    val correct = (stats.correctCount ?: 0).coerceAtLeast(0)
    val partial = (stats.partialCount ?: 0).coerceAtLeast(0)
    val wrong = (stats.wrongCount ?: 0).coerceAtLeast(0)
    val pending = (stats.pendingReviewCount ?: 0).coerceAtLeast(0)
    val blank = (stats.blankCount ?: 0).coerceAtLeast(0)
    val total = (stats.totalCount ?: (correct + partial + wrong + pending + blank)).coerceAtLeast(1)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.bgSubtle),
    ) {
        if (correct > 0) {
            Box(
                modifier = Modifier
                    .weight(correct.toFloat() / total.toFloat())
                    .height(8.dp)
                    .background(statusColors.success),
            )
        }
        if (partial > 0) {
            Box(
                modifier = Modifier
                    .weight(partial.toFloat() / total.toFloat())
                    .height(8.dp)
                    .background(statusColors.warning),
            )
        }
        if (wrong > 0) {
            Box(
                modifier = Modifier
                    .weight(wrong.toFloat() / total.toFloat())
                    .height(8.dp)
                    .background(colors.dangerText),
            )
        }
        if (pending > 0) {
            Box(
                modifier = Modifier
                    .weight(pending.toFloat() / total.toFloat())
                    .height(8.dp)
                    .background(statusColors.info),
            )
        }
        val remainder = (total - correct - partial - wrong - pending).coerceAtLeast(0)
        if (remainder > 0) {
            Box(
                modifier = Modifier
                    .weight(remainder.toFloat() / total.toFloat())
                    .height(8.dp)
                    .background(colors.borderStrong),
            )
        }
    }
}

@Composable
internal fun SectionHeader(
    title: String,
    description: String? = null,
) {
    val colors = Thi360Theme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.text,
            modifier = Modifier.semantics { heading() },
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
            )
        }
    }
}

@Composable
internal fun ResultPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    mergeSemantics: Boolean = true,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        modifier = if (mergeSemantics) modifier.semantics(mergeDescendants = true) {} else modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun computeRatePercent(correct: Int, total: Int): BigDecimal {
    if (total <= 0) return BigDecimal.ZERO
    return BigDecimal.valueOf(correct.toLong() * 100L)
        .divide(BigDecimal.valueOf(total.toLong()), 2, java.math.RoundingMode.HALF_UP)
}
