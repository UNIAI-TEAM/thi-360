package vn.unicom.thi360.feature.question.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.logic.OptionOrdering
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.ItemReview
import vn.unicom.thi360.feature.question.model.ItemReviewKey
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.model.TrueFalseScoringMethod
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichContentMode
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

private val TF_INLINE_BREAKPOINT_DP = 480.dp

@Composable
internal fun TrueFalseQuestionView(
    item: QuestionItem.TrueFalse,
    answer: TrueFalseAnswer?,
    mode: QuestionMode,
    flagged: Boolean,
    onAnswer: (AnswerValue) -> Unit,
    onFlag: (() -> Unit)?,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
    attachments: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val brand = Thi360Theme.colors
    val statements = answer?.statements.orEmpty()
    val isAnswering = mode is QuestionMode.Answering
    val isLocked = mode is QuestionMode.Locked

    QuestionFrame(
        item = item,
        mode = mode,
        flagged = flagged,
        onFlag = onFlag,
        assets = assets,
        formulaEngine = formulaEngine,
        attachments = attachments,
        modifier = modifier,
    ) {
        if (mode is QuestionMode.Review) {
            TrueFalseReviewBody(
                item = item,
                statements = statements,
                review = mode.review,
                assets = assets,
                formulaEngine = formulaEngine,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.question_trueFalseHelp),
                    color = brand.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )

                item.statements.forEachIndexed { index, stmt ->
                    val letter = OptionOrdering.statementLetter(index)
                    val picked = statements[stmt.id]
                    val shape = RoundedCornerShape(10.dp)
                    val rowBg = if (picked == null) brand.bgSurface else brand.bgSubtle

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(rowBg)
                            .border(1.dp, brand.border, shape)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag(QuestionTestTags.tfRow(stmt.id)),
                    ) {
                        val inline = maxWidth >= TF_INLINE_BREAKPOINT_DP
                        val stmtDoc = remember(stmt.content) { RichHtmlParser.parse(stmt.content) }

                        val onPick: (Boolean) -> Unit = { target ->
                            val updated = LinkedHashMap<String, Boolean?>(statements.size + 1)
                            updated.putAll(statements)
                            if (picked == target) {
                                updated[stmt.id] = null
                            } else {
                                updated[stmt.id] = target
                            }
                            onAnswer(AnswerValues.trueFalse(item, updated))
                        }

                        if (inline) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "$letter)",
                                        color = brand.text,
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    RichContentView(
                                        document = stmtDoc,
                                        assets = assets,
                                        mode = mode.toRichContentMode(),
                                        formulaEngine = formulaEngine,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                TrueFalseTogglePair(
                                    statementId = stmt.id,
                                    picked = picked,
                                    enabled = isAnswering,
                                    dimmed = isLocked,
                                    onPick = onPick,
                                )
                            }
                        } else {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        text = "$letter)",
                                        color = brand.text,
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    RichContentView(
                                        document = stmtDoc,
                                        assets = assets,
                                        mode = mode.toRichContentMode(),
                                        formulaEngine = formulaEngine,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                TrueFalseTogglePair(
                                    statementId = stmt.id,
                                    picked = picked,
                                    enabled = isAnswering,
                                    dimmed = isLocked,
                                    onPick = onPick,
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
private fun TrueFalseTogglePair(
    statementId: String,
    picked: Boolean?,
    enabled: Boolean,
    dimmed: Boolean,
    onPick: (Boolean) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrueFalseToggleButton(
            label = stringResource(R.string.question_trueOption),
            isSelected = picked == true,
            enabled = enabled,
            dimmed = dimmed,
            tag = QuestionTestTags.tfTrue(statementId),
            onClick = { onPick(true) },
        )
        TrueFalseToggleButton(
            label = stringResource(R.string.question_falseOption),
            isSelected = picked == false,
            enabled = enabled,
            dimmed = dimmed,
            tag = QuestionTestTags.tfFalse(statementId),
            onClick = { onPick(false) },
        )
    }
}

@Composable
private fun TrueFalseToggleButton(
    label: String,
    isSelected: Boolean,
    enabled: Boolean,
    dimmed: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val brand = Thi360Theme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 84.dp)
            .clip(shape)
            .background(if (isSelected) brand.primaryContainer else brand.bgSurface)
            .border(
                BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) brand.primaryFill else brand.borderStrong,
                ),
                shape,
            )
            .then(if (dimmed) Modifier.alpha(0.55f) else Modifier)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.RadioButton, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics { selected = isSelected }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (isSelected) brand.primaryFill else brand.borderStrong,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(brand.primaryFill),
                )
            }
        }
        Text(
            text = label,
            color = if (isSelected) brand.primaryText else brand.text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TrueFalseReviewBody(
    item: QuestionItem.TrueFalse,
    statements: Map<String, Boolean>,
    review: ItemReview,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
) {
    val brand = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val expectedMap = (review.key as? ItemReviewKey.TrueFalse)?.statements
    val showAnswers = review.showsAnswers && !expectedMap.isNullOrEmpty()
    val effectiveMethod = review.scoringMethod ?: item.scoringMethod
    val perStatement = effectiveMethod == TrueFalseScoringMethod.PER_STATEMENT

    val itemCol = stringResource(R.string.question_itemCol)
    val youChoseCol = stringResource(R.string.question_youChoseCol)
    val answerCol = stringResource(R.string.question_answerCol)
    val pointsCol = stringResource(R.string.question_pointsCol)
    val trueLabel = stringResource(R.string.question_trueOption)
    val falseLabel = stringResource(R.string.question_falseOption)
    val notAnsweredLabel = stringResource(R.string.question_notAnswered)

    val columns = buildList {
        add(ReviewTableColumn(itemCol, weight = 2.2f))
        add(ReviewTableColumn(youChoseCol, weight = 1.2f))
        if (showAnswers) add(ReviewTableColumn(answerCol, weight = 1f))
        add(ReviewTableColumn(pointsCol, weight = 1f))
    }

    val rows = item.statements.mapIndexed { index, stmt ->
        val letter = OptionOrdering.statementLetter(index)
        val part = review.detail[stmt.id]
        val picked = statements[stmt.id]
        val expected = expectedMap?.get(stmt.id)
        val pointsText = if (part?.earned != null) {
            stringResource(
                R.string.question_points,
                OptionOrdering.formatScore(part.earned, locale),
                OptionOrdering.formatScore(part.max ?: stmt.points ?: BigDecimal.ONE, locale),
            )
        } else {
            "—"
        }

        val detailCells = buildList<Pair<String, @Composable () -> Unit>> {
            add(
                youChoseCol to {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (part != null) {
                            OutcomeGlyph(outcome = part.outcome, withAccessibilityLabel = true)
                        }
                        when (picked) {
                            true -> Text(
                                text = trueLabel,
                                color = brand.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            false -> Text(
                                text = falseLabel,
                                color = brand.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            null -> Text(
                                text = notAnsweredLabel,
                                color = brand.textMuted,
                                fontSize = 14.sp,
                            )
                        }
                    }
                },
            )
            if (showAnswers) {
                add(
                    answerCol to {
                        Text(
                            text = when (expected) {
                                true -> trueLabel
                                false -> falseLabel
                                null -> "—"
                            },
                            color = brand.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    },
                )
            }
            add(
                pointsCol to {
                    Text(
                        text = pointsText,
                        color = brand.text,
                        fontSize = 14.sp,
                    )
                },
            )
        }

        ReviewTableRowData(
            key = stmt.id,
            primaryCell = {
                val stmtDoc = remember(stmt.content) { RichHtmlParser.parse(stmt.content) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "$letter)",
                        color = brand.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    RichContentView(
                        document = stmtDoc,
                        assets = assets,
                        mode = RichContentMode.Review,
                        formulaEngine = formulaEngine,
                        textSizeSp = 14f,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
            detailCells = detailCells,
        )
    }

    val correctCount = item.statements.count { stmt ->
        review.detail[stmt.id]?.outcome == ItemOutcome.CORRECT
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ResponsiveReviewTable(columns = columns, rows = rows)
        if (!perStatement && review.earned != null) {
            Text(
                text = stringResource(
                    R.string.question_countTableSummary,
                    OptionOrdering.formatInteger(correctCount, locale),
                    OptionOrdering.formatInteger(item.statements.size, locale),
                    OptionOrdering.formatScore(review.earned, locale),
                ),
                color = brand.text,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
