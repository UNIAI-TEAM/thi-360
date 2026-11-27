package vn.unicom.thi360.feature.question.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.logic.OptionOrdering
import vn.unicom.thi360.feature.question.model.ChoiceOptionItem
import vn.unicom.thi360.feature.question.model.ItemReviewKey
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@Composable
internal fun SingleChoiceQuestionView(
    item: QuestionItem.SingleChoice,
    answer: SingleChoiceAnswer?,
    mode: QuestionMode,
    flagged: Boolean,
    onAnswer: (AnswerValue) -> Unit,
    onFlag: (() -> Unit)?,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
    attachments: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val selectedIds = remember(answer) {
        listOfNotNull(answer?.optionId)
    }
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
        ChoiceOptionsList(
            options = item.options,
            optionOrder = item.optionOrder,
            selectedIds = selectedIds,
            multiple = false,
            mode = mode,
            assets = assets,
            formulaEngine = formulaEngine,
            onSelect = { optionId ->
                val nextId = if (answer?.optionId == optionId) null else optionId
                onAnswer(AnswerValues.singleChoice(item, nextId))
            },
        )
    }
}

@Composable
internal fun MultipleChoiceQuestionView(
    item: QuestionItem.MultipleChoice,
    answer: MultipleChoiceAnswer?,
    mode: QuestionMode,
    flagged: Boolean,
    onAnswer: (AnswerValue) -> Unit,
    onFlag: (() -> Unit)?,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
    attachments: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val selectedIds = answer?.optionIds.orEmpty()
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
        ChoiceOptionsList(
            options = item.options,
            optionOrder = item.optionOrder,
            selectedIds = selectedIds,
            multiple = true,
            mode = mode,
            assets = assets,
            formulaEngine = formulaEngine,
            onSelect = { optionId ->
                val nextIds = if (optionId in selectedIds) {
                    selectedIds.filterNot { it == optionId }
                } else {
                    selectedIds + optionId
                }
                onAnswer(AnswerValues.multipleChoice(item, nextIds))
            },
        )
    }
}

private enum class ReviewTagTone {
    CORRECT,
    WRONG,
    PARTIAL,
    NEUTRAL,
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceOptionsList(
    options: List<ChoiceOptionItem>,
    optionOrder: List<String>,
    selectedIds: List<String>,
    multiple: Boolean,
    mode: QuestionMode,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
    onSelect: (String) -> Unit,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val ordered = remember(options, optionOrder) {
        OptionOrdering.orderById(options, optionOrder) { it.id }
    }
    val review = (mode as? QuestionMode.Review)?.review
    val correctIds = (review?.key as? ItemReviewKey.Choice)?.correctOptionIds
    val showAnswers = review != null && review.showsAnswers && !correctIds.isNullOrEmpty()
    val isAnswering = mode is QuestionMode.Answering
    val isLocked = mode is QuestionMode.Locked

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (multiple && review == null) {
            Text(
                text = stringResource(R.string.question_chooseMultiple),
                color = brand.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }

        ordered.forEachIndexed { index, option ->
            val checked = option.id in selectedIds
            val isCorrect = showAnswers && option.id in correctIds.orEmpty()
            val letter = OptionOrdering.optionLetter(index)
            val shape = RoundedCornerShape(12.dp)

            val bgColor: Color
            val borderColor: Color
            val borderWidth: Int
            val isDashed: Boolean

            if (review != null) {
                when {
                    checked && isCorrect -> {
                        bgColor = status.successBg
                        borderColor = status.success
                        borderWidth = 2
                        isDashed = false
                    }
                    checked && !isCorrect && showAnswers -> {
                        bgColor = brand.dangerBg
                        borderColor = brand.dangerText
                        borderWidth = 2
                        isDashed = true
                    }
                    checked -> {
                        bgColor = brand.primaryContainer
                        borderColor = brand.primaryFill
                        borderWidth = 2
                        isDashed = false
                    }
                    isCorrect -> {
                        bgColor = status.successBg
                        borderColor = status.success
                        borderWidth = 2
                        isDashed = false
                    }
                    else -> {
                        bgColor = brand.bgSurface
                        borderColor = brand.border
                        borderWidth = 1
                        isDashed = false
                    }
                }
            } else {
                bgColor = if (checked) brand.primaryContainer else brand.bgSurface
                borderColor = if (checked) brand.primaryFill else brand.border
                borderWidth = if (checked) 2 else 1
                isDashed = false
            }

            val borderMod = if (isDashed) {
                Modifier.dashedBorder(color = borderColor, strokeWidth = borderWidth.dp, cornerRadius = 12.dp)
            } else {
                Modifier.border(BorderStroke(borderWidth.dp, borderColor), shape)
            }

            val rowModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(bgColor)
                .then(borderMod)
                .then(
                    if (isAnswering) {
                        Modifier.clickable(
                            role = if (multiple) Role.Checkbox else Role.RadioButton,
                            onClick = { onSelect(option.id) },
                        )
                    } else {
                        Modifier
                    },
                )
                .semantics {
                    selected = checked
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag(QuestionTestTags.choiceOption(option.id))

            Column(
                modifier = rowModifier,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ChoiceMarker(
                        multiple = multiple,
                        checked = checked,
                        review = review != null,
                        isCorrect = isCorrect,
                        showAnswers = showAnswers,
                        dimmed = isLocked,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = "$letter.",
                        color = brand.text,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val optionDoc = remember(option.content) { RichHtmlParser.parse(option.content) }
                    RichContentView(
                        document = optionDoc,
                        assets = assets,
                        mode = mode.toRichContentMode(),
                        formulaEngine = formulaEngine,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (review != null) {
                    val hasTags = checked || (showAnswers && isCorrect)
                    if (hasTags) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 30.dp),
                        ) {
                            if (checked) {
                                val wrongPick = showAnswers && !isCorrect
                                ChoiceReviewTag(
                                    label = stringResource(
                                        if (wrongPick) {
                                            R.string.question_yourPickWrongTag
                                        } else {
                                            R.string.question_yourPickTag
                                        },
                                    ),
                                    tone = if (wrongPick) ReviewTagTone.WRONG else ReviewTagTone.NEUTRAL,
                                )
                            }
                            if (showAnswers && isCorrect) {
                                ChoiceReviewTag(
                                    label = stringResource(R.string.question_correctAnswerTag),
                                    tone = ReviewTagTone.CORRECT,
                                )
                            }
                            if (showAnswers && isCorrect && !checked && multiple) {
                                ChoiceReviewTag(
                                    label = stringResource(R.string.question_notPickedTag),
                                    tone = ReviewTagTone.PARTIAL,
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
private fun ChoiceMarker(
    multiple: Boolean,
    checked: Boolean,
    review: Boolean,
    isCorrect: Boolean,
    showAnswers: Boolean,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val activeColor = when {
        review && showAnswers && checked && !isCorrect -> brand.dangerText
        review && showAnswers && isCorrect -> status.success
        else -> brand.primaryFill
    }
    val inactiveBorder = if (review && showAnswers && isCorrect) status.success else brand.borderStrong
    val alphaModifier = if (dimmed) Modifier.alpha(0.55f) else Modifier

    if (multiple) {
        val boxShape = RoundedCornerShape(5.dp)
        Box(
            modifier = modifier
                .then(alphaModifier)
                .size(20.dp)
                .clip(boxShape)
                .background(if (checked) activeColor else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (checked) activeColor else inactiveBorder,
                    shape = boxShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = QuestionIcons.Check,
                    contentDescription = null,
                    tint = brand.bgSurface,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .then(alphaModifier)
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (checked) activeColor else inactiveBorder,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(activeColor),
                )
            }
        }
    }
}

@Composable
private fun ChoiceReviewTag(
    label: String,
    tone: ReviewTagTone,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val shape = RoundedCornerShape(6.dp)

    val bg: Color
    val fg: Color
    val border: Color
    val dashed: Boolean
    when (tone) {
        ReviewTagTone.CORRECT -> {
            bg = status.success
            fg = brand.bgSurface
            border = status.success
            dashed = false
        }
        ReviewTagTone.WRONG -> {
            bg = brand.bgSurface
            fg = brand.dangerText
            border = brand.dangerText
            dashed = true
        }
        ReviewTagTone.PARTIAL -> {
            bg = brand.bgSurface
            fg = status.warning
            border = status.warning
            dashed = true
        }
        ReviewTagTone.NEUTRAL -> {
            bg = brand.bgSurface
            fg = brand.text
            border = brand.borderStrong
            dashed = false
        }
    }

    val borderMod = if (dashed) {
        Modifier.dashedBorder(color = border, strokeWidth = 1.dp, cornerRadius = 6.dp)
    } else {
        Modifier.border(1.dp, border, shape)
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .then(borderMod)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
