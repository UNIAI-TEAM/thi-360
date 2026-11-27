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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.math.BigDecimal
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.logic.OptionOrdering
import vn.unicom.thi360.feature.question.model.ItemReview
import vn.unicom.thi360.feature.question.model.ItemReviewKey
import vn.unicom.thi360.feature.question.model.MatchingSideItem
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichContentMode
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MatchingQuestionView(
    item: QuestionItem.Matching,
    answer: MatchingAnswer?,
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
    val locale = LocalAppLocale.current
    val pairs = answer?.pairs.orEmpty()
    val orderedRight = remember(item.right, item.optionOrder) {
        OptionOrdering.orderById(item.right, item.optionOrder) { it.id }
    }
    val rightById = remember(orderedRight) {
        orderedRight.mapIndexed { index, rightItem ->
            rightItem.id to (OptionOrdering.matchLetter(index) to rightItem)
        }.toMap()
    }
    val rightUsedBy = remember(item.left, pairs) {
        val map = HashMap<String, MutableList<String>>()
        for (leftItem in item.left) {
            val picked = pairs[leftItem.id] ?: continue
            map.getOrPut(picked) { ArrayList() }.add(leftItem.id)
        }
        map
    }
    val doneCount = item.left.count { pairs[it.id] != null }
    val isAnswering = mode is QuestionMode.Answering
    val isLocked = mode is QuestionMode.Locked

    var pickerLeftId by remember(item.id) { mutableStateOf<String?>(null) }

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
            MatchingReviewTable(
                item = item,
                orderedRight = orderedRight,
                pairs = pairs,
                review = mode.review,
                assets = assets,
                formulaEngine = formulaEngine,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (item.reuseRight) {
                                R.string.question_matchReusable
                            } else {
                                R.string.question_matchExclusive
                            },
                        ),
                        color = brand.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.question_matchDone,
                            OptionOrdering.formatInteger(doneCount, locale),
                            OptionOrdering.formatInteger(item.left.size, locale),
                        ),
                        color = brand.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag(QuestionTestTags.MATCH_COUNTER),
                    )
                }

                item.left.forEachIndexed { index, leftItem ->
                    val pickedRightId = pairs[leftItem.id]
                    val pickedEntry = pickedRightId?.let { rightById[it] }
                    val rowShape = RoundedCornerShape(10.dp)
                    val selectorShape = RoundedCornerShape(10.dp)
                    val leftLabel = stringResource(
                        R.string.question_matchPickLeft,
                        OptionOrdering.formatInteger(index + 1, locale),
                    )
                    val clearLabel = stringResource(
                        R.string.question_matchClear,
                        OptionOrdering.formatInteger(index + 1, locale),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(rowShape)
                            .background(brand.bgSurface)
                            .border(1.dp, brand.border, rowShape)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(brand.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = OptionOrdering.formatInteger(index + 1, locale),
                                    color = brand.primaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            val leftDoc = remember(leftItem.content) { RichHtmlParser.parse(leftItem.content) }
                            RichContentView(
                                document = leftDoc,
                                assets = assets,
                                mode = mode.toRichContentMode(),
                                formulaEngine = formulaEngine,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.question_matchWith),
                                color = brand.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(selectorShape)
                                    .background(if (isLocked) brand.bgSubtle else brand.bgSurface)
                                    .border(BorderStroke(1.dp, brand.borderStrong), selectorShape)
                                    .then(if (isLocked) Modifier.alpha(0.6f) else Modifier)
                                    .then(
                                        if (isAnswering) {
                                            Modifier.clickable(
                                                role = Role.DropdownList,
                                                onClick = { pickerLeftId = leftItem.id },
                                            )
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .semantics { contentDescription = leftLabel }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag(QuestionTestTags.matchSelector(leftItem.id)),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (pickedEntry != null) {
                                        MatchOptionRichLabel(
                                            letter = pickedEntry.first,
                                            html = pickedEntry.second.content,
                                            assets = assets,
                                            mode = mode.toRichContentMode(),
                                            formulaEngine = formulaEngine,
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(R.string.question_matchPlaceholder),
                                            color = brand.textMuted,
                                            fontSize = 15.sp,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = QuestionIcons.ChevronDown,
                                    contentDescription = null,
                                    tint = brand.textMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            if (pickedRightId != null) {
                                IconButton(
                                    onClick = {
                                        if (isAnswering) {
                                            val updated = LinkedHashMap<String, String?>(pairs.size)
                                            updated.putAll(pairs)
                                            updated[leftItem.id] = null
                                            onAnswer(AnswerValues.matching(item, updated))
                                        }
                                    },
                                    enabled = isAnswering,
                                    modifier = Modifier
                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                        .then(if (isLocked) Modifier.alpha(0.45f) else Modifier)
                                        .semantics {
                                            contentDescription = clearLabel
                                            role = Role.Button
                                        }
                                        .testTag(QuestionTestTags.matchClear(leftItem.id)),
                                ) {
                                    Icon(
                                        imageVector = QuestionIcons.Close,
                                        contentDescription = null,
                                        tint = brand.textSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val openLeftId = pickerLeftId
    if (openLeftId != null && isAnswering) {
        val currentPicked = pairs[openLeftId]
        val leftIndex = item.left.indexOfFirst { it.id == openLeftId }
        val pickerTitle = if (leftIndex >= 0) {
            stringResource(
                R.string.question_matchPickLeft,
                OptionOrdering.formatInteger(leftIndex + 1, locale),
            )
        } else {
            stringResource(R.string.question_rightColumn)
        }

        Dialog(onDismissRequest = { pickerLeftId = null }) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = brand.bgSurface,
                border = BorderStroke(1.dp, brand.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .testTag(QuestionTestTags.MATCH_PICKER_DIALOG),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = pickerTitle,
                            color = brand.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        IconButton(
                            onClick = { pickerLeftId = null },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        ) {
                            Icon(
                                imageVector = QuestionIcons.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = brand.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        orderedRight.forEachIndexed { optIndex, rightItem ->
                            val letter = OptionOrdering.matchLetter(optIndex)
                            val isSelected = currentPicked == rightItem.id
                            val usedByOther = !item.reuseRight &&
                                !isSelected &&
                                (rightUsedBy[rightItem.id]?.isNotEmpty() == true)
                            val optShape = RoundedCornerShape(10.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(optShape)
                                    .background(
                                        when {
                                            isSelected -> brand.primaryContainer
                                            usedByOther -> brand.bgSubtle
                                            else -> brand.bgSurface
                                        },
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) brand.primaryFill else brand.border,
                                        shape = optShape,
                                    )
                                    .then(if (usedByOther) Modifier.alpha(0.45f) else Modifier)
                                    .then(
                                        if (!usedByOther) {
                                            Modifier.clickable(
                                                role = Role.RadioButton,
                                                onClick = {
                                                    val updated = LinkedHashMap<String, String?>(pairs.size + 1)
                                                    updated.putAll(pairs)
                                                    updated[openLeftId] = rightItem.id
                                                    onAnswer(AnswerValues.matching(item, updated))
                                                    pickerLeftId = null
                                                },
                                            )
                                        } else {
                                            Modifier.semantics { disabled() }
                                        },
                                    )
                                    .semantics { selected = isSelected }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag(QuestionTestTags.matchPickerOption(rightItem.id)),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    MatchOptionRichLabel(
                                        letter = letter,
                                        html = rightItem.content,
                                        assets = assets,
                                        mode = RichContentMode.Answering,
                                        formulaEngine = formulaEngine,
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = QuestionIcons.Check,
                                        contentDescription = null,
                                        tint = brand.primaryText,
                                        modifier = Modifier.size(16.dp),
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
private fun MatchOptionRichLabel(
    letter: String,
    html: String,
    assets: AttemptAssetSource?,
    mode: RichContentMode,
    formulaEngine: FormulaEngine?,
    textSizeSp: Float = 15f,
) {
    val brand = Thi360Theme.colors
    val doc = remember(html) { RichHtmlParser.parse(html) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "$letter.",
            color = brand.text,
            fontSize = textSizeSp.sp,
            lineHeight = (textSizeSp + 6f).sp,
            fontWeight = FontWeight.SemiBold,
        )
        RichContentView(
            document = doc,
            assets = assets,
            mode = mode,
            formulaEngine = formulaEngine,
            textSizeSp = textSizeSp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MatchingReviewTable(
    item: QuestionItem.Matching,
    orderedRight: List<MatchingSideItem>,
    pairs: Map<String, String>,
    review: ItemReview,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
) {
    val brand = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val expectedPairs = (review.key as? ItemReviewKey.Matching)?.pairs
    val showAnswers = review.showsAnswers && !expectedPairs.isNullOrEmpty()
    val rightById = remember(orderedRight) {
        orderedRight.mapIndexed { index, rightItem ->
            rightItem.id to (OptionOrdering.matchLetter(index) to rightItem)
        }.toMap()
    }

    val itemCol = stringResource(R.string.question_itemCol)
    val youChoseCol = stringResource(R.string.question_youChoseCol)
    val answerCol = stringResource(R.string.question_answerCol)
    val pointsCol = stringResource(R.string.question_pointsCol)
    val notMatchedLabel = stringResource(R.string.question_notMatched)

    val columns = buildList {
        add(ReviewTableColumn(itemCol, weight = 1.6f))
        add(ReviewTableColumn(youChoseCol, weight = 1.5f))
        if (showAnswers) add(ReviewTableColumn(answerCol, weight = 1.5f))
        add(ReviewTableColumn(pointsCol, weight = 1f))
    }

    val rows = item.left.mapIndexed { index, leftItem ->
        val part = review.detail[leftItem.id]
        val pickedId = pairs[leftItem.id]
        val picked = pickedId?.let { rightById[it] }
        val expectedId = expectedPairs?.get(leftItem.id)
        val expected = expectedId?.let { rightById[it] }
        val pointsText = if (part?.earned != null) {
            stringResource(
                R.string.question_points,
                OptionOrdering.formatScore(part.earned, locale),
                OptionOrdering.formatScore(part.max ?: BigDecimal.ONE, locale),
            )
        } else {
            "—"
        }

        val detailCells = buildList<Pair<String, @Composable () -> Unit>> {
            add(
                youChoseCol to {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (part != null) {
                            OutcomeGlyph(
                                outcome = part.outcome,
                                withAccessibilityLabel = true,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        if (picked != null) {
                            Box(modifier = Modifier.weight(1f)) {
                                MatchOptionRichLabel(
                                    letter = picked.first,
                                    html = picked.second.content,
                                    assets = assets,
                                    mode = RichContentMode.Review,
                                    formulaEngine = formulaEngine,
                                    textSizeSp = 14f,
                                )
                            }
                        } else {
                            Text(
                                text = notMatchedLabel,
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
                        if (expected != null) {
                            MatchOptionRichLabel(
                                letter = expected.first,
                                html = expected.second.content,
                                assets = assets,
                                mode = RichContentMode.Review,
                                formulaEngine = formulaEngine,
                                textSizeSp = 14f,
                            )
                        } else {
                            Text(text = "—", color = brand.textMuted, fontSize = 14.sp)
                        }
                    },
                )
            }
            add(
                pointsCol to {
                    Text(text = pointsText, color = brand.text, fontSize = 14.sp)
                },
            )
        }

        ReviewTableRowData(
            key = leftItem.id,
            primaryCell = {
                val leftDoc = remember(leftItem.content) { RichHtmlParser.parse(leftItem.content) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "${OptionOrdering.formatInteger(index + 1, locale)}.",
                        color = brand.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    RichContentView(
                        document = leftDoc,
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

    ResponsiveReviewTable(columns = columns, rows = rows)
}
