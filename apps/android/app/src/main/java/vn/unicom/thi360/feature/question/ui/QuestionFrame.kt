package vn.unicom.thi360.feature.question.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.logic.OptionOrdering
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.model.QuestionType
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichContentMode
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

internal fun QuestionMode.toRichContentMode(): RichContentMode = when (this) {
    QuestionMode.Answering -> RichContentMode.Answering
    QuestionMode.Locked -> RichContentMode.Locked
    is QuestionMode.Review -> RichContentMode.Review
}

internal fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp,
    cornerRadius: Dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp,
): Modifier = drawBehind {
    val strokePx = strokeWidth.toPx()
    val radiusPx = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = Stroke(
            width = strokePx,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashLength.toPx(), gapLength.toPx()),
                0f,
            ),
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuestionFrame(
    item: QuestionItem,
    mode: QuestionMode,
    flagged: Boolean = false,
    onFlag: (() -> Unit)? = null,
    assets: AttemptAssetSource? = null,
    formulaEngine: FormulaEngine? = null,
    attachments: (@Composable () -> Unit)? = null,
    manualTag: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val review = (mode as? QuestionMode.Review)?.review
    val titleText = remember(item, locale) {
        if (item is QuestionItem.Cloze && item.blanks.size > 1) {
            val from = OptionOrdering.formatInteger(item.displayNo, locale)
            val to = OptionOrdering.formatInteger(item.displayNo + item.blanks.size - 1, locale)
            from to to
        } else {
            OptionOrdering.formatInteger(item.displayNo, locale) to null
        }
    }
    val resolvedTitle = if (titleText.second != null) {
        stringResource(R.string.question_rangeLabel, titleText.first, titleText.second!!)
    } else {
        stringResource(R.string.question_label, titleText.first)
    }

    val resolvedTypeLabel = if (item is QuestionItem.Cloze) {
        stringResource(
            R.string.question_type_clozeCount,
            OptionOrdering.formatInteger(item.blanks.size, locale),
        )
    } else {
        stringResource(typeLabelRes(item.type))
    }

    val flagDescription = stringResource(
        if (flagged) R.string.question_flagRemove else R.string.question_flagForReview,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(QuestionTestTags.FRAME_ROOT),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (attachments != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(QuestionTestTags.FRAME_ATTACHMENTS),
            ) {
                attachments()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = resolvedTitle,
                    color = brand.text,
                    fontSize = 18.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag(QuestionTestTags.FRAME_TITLE),
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(brand.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .semantics(mergeDescendants = true) {}
                        .testTag(QuestionTestTags.FRAME_TYPE_CHIP),
                ) {
                    Text(
                        text = resolvedTypeLabel,
                        color = brand.primaryText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (manualTag) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(status.infoBg)
                            .border(
                                width = 1.dp,
                                color = status.info.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .semantics(mergeDescendants = true) {}
                            .testTag(QuestionTestTags.FRAME_MANUAL_CHIP),
                    ) {
                        Text(
                            text = stringResource(R.string.question_manualGrading),
                            color = status.info,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (review?.outcome != null) {
                    OutcomeChip(outcome = review.outcome)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (review != null) {
                    ReviewPoints(
                        earned = review.earned,
                        max = review.max,
                    )
                }
                if (mode is QuestionMode.Answering && onFlag != null) {
                    IconButton(
                        onClick = onFlag,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = flagDescription
                                role = Role.Button
                                selected = flagged
                            }
                            .testTag(QuestionTestTags.FRAME_FLAG_BUTTON),
                    ) {
                        Icon(
                            imageVector = if (flagged) QuestionIcons.FlagFilled else QuestionIcons.FlagOutline,
                            contentDescription = null,
                            tint = if (flagged) status.warning else brand.textSecondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        if (item.stem.isNotBlank()) {
            val stemDoc = remember(item.stem) { RichHtmlParser.parse(item.stem) }
            RichContentView(
                document = stemDoc,
                assets = assets,
                mode = mode.toRichContentMode(),
                formulaEngine = formulaEngine,
            )
        }

        content()

        val solutionHtml = review?.effectiveSolutionHtml
        if (!solutionHtml.isNullOrBlank()) {
            SolutionBlock(
                html = solutionHtml,
                assets = assets,
                formulaEngine = formulaEngine,
            )
        }
    }
}

@Composable
fun OutcomeChip(
    outcome: ItemOutcome,
    modifier: Modifier = Modifier,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val shape = RoundedCornerShape(999.dp)
    val label = stringResource(outcomeLabelRes(outcome))

    val bgColor: Color
    val fgColor: Color
    val borderColor: Color
    val isDashed: Boolean

    when (outcome) {
        ItemOutcome.CORRECT -> {
            bgColor = status.success
            fgColor = brand.bgSurface
            borderColor = status.success
            isDashed = false
        }
        ItemOutcome.PARTIAL -> {
            bgColor = status.warningBg
            fgColor = status.warning
            borderColor = status.warning
            isDashed = false
        }
        ItemOutcome.WRONG -> {
            bgColor = brand.dangerBg
            fgColor = brand.dangerText
            borderColor = brand.dangerText
            isDashed = true
        }
        ItemOutcome.BLANK -> {
            bgColor = brand.bgSubtle
            fgColor = brand.textSecondary
            borderColor = brand.borderStrong
            isDashed = false
        }
        ItemOutcome.PENDING_REVIEW -> {
            bgColor = status.infoBg
            fgColor = status.info
            borderColor = status.info
            isDashed = false
        }
    }

    val borderMod = if (isDashed) {
        Modifier.dashedBorder(color = borderColor, strokeWidth = 1.dp, cornerRadius = 999.dp)
    } else {
        Modifier.border(BorderStroke(1.dp, borderColor), shape)
    }

    Row(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .then(borderMod)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(QuestionTestTags.FRAME_OUTCOME_CHIP),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutcomeGlyph(outcome = outcome, tint = fgColor, size = 14.dp)
        Text(
            text = label,
            color = fgColor,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun OutcomeGlyph(
    outcome: ItemOutcome,
    tint: Color? = null,
    size: Dp = 14.dp,
    modifier: Modifier = Modifier,
    withAccessibilityLabel: Boolean = false,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val resolvedTint = tint ?: when (outcome) {
        ItemOutcome.CORRECT -> status.success
        ItemOutcome.PARTIAL -> status.warning
        ItemOutcome.WRONG -> brand.dangerText
        ItemOutcome.BLANK -> brand.textSecondary
        ItemOutcome.PENDING_REVIEW -> status.info
    }
    val vector: ImageVector = when (outcome) {
        ItemOutcome.CORRECT -> QuestionIcons.Check
        ItemOutcome.PARTIAL -> QuestionIcons.Partial
        ItemOutcome.WRONG -> QuestionIcons.Close
        ItemOutcome.BLANK -> QuestionIcons.Dash
        ItemOutcome.PENDING_REVIEW -> QuestionIcons.Clock
    }
    val desc = if (withAccessibilityLabel) stringResource(outcomeLabelRes(outcome)) else null
    Icon(
        imageVector = vector,
        contentDescription = desc,
        tint = resolvedTint,
        modifier = modifier.size(size),
    )
}

@Composable
fun ReviewPoints(
    earned: BigDecimal?,
    max: BigDecimal,
    modifier: Modifier = Modifier,
) {
    val brand = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val maxFormatted = OptionOrdering.formatScore(max, locale)
    val text = if (earned == null) {
        "${stringResource(R.string.question_noPointsYet)} · ${stringResource(R.string.question_maxPoints, maxFormatted)}"
    } else {
        val earnedFormatted = OptionOrdering.formatScore(earned, locale)
        stringResource(R.string.question_points, earnedFormatted, maxFormatted)
    }
    Text(
        text = text,
        color = if (earned == null) brand.textSecondary else brand.text,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = if (earned == null) FontWeight.SemiBold else FontWeight.Bold,
        modifier = modifier.testTag(QuestionTestTags.FRAME_POINTS),
    )
}

@Composable
fun SolutionBlock(
    html: String,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
    modifier: Modifier = Modifier,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val doc = remember(html) { RichHtmlParser.parse(html) }
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(status.successBg)
            .border(1.dp, status.success.copy(alpha = 0.5f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(QuestionTestTags.FRAME_SOLUTION_BLOCK),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.question_solutionLabel),
            color = brand.primaryText,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        RichContentView(
            document = doc,
            assets = assets,
            mode = RichContentMode.Review,
            formulaEngine = formulaEngine,
        )
    }
}

private fun typeLabelRes(type: QuestionType): Int = when (type) {
    QuestionType.SINGLE_CHOICE -> R.string.question_type_singleChoice
    QuestionType.MULTIPLE_CHOICE -> R.string.question_type_multipleChoice
    QuestionType.SHORT_ANSWER -> R.string.question_type_shortAnswer
    QuestionType.CLOZE -> R.string.question_type_cloze
    QuestionType.QUESTION_SET -> R.string.question_type_questionSet
    QuestionType.TRUE_FALSE -> R.string.question_type_trueFalse
    QuestionType.MATCHING -> R.string.question_type_matching
    QuestionType.ESSAY -> R.string.question_type_essay
}

internal fun outcomeLabelRes(outcome: ItemOutcome): Int = when (outcome) {
    ItemOutcome.CORRECT -> R.string.question_outcome_correct
    ItemOutcome.PARTIAL -> R.string.question_outcome_partial
    ItemOutcome.WRONG -> R.string.question_outcome_wrong
    ItemOutcome.BLANK -> R.string.question_outcome_blank
    ItemOutcome.PENDING_REVIEW -> R.string.question_outcome_pendingReview
}
