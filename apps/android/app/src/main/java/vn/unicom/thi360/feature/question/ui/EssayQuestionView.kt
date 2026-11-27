package vn.unicom.thi360.feature.question.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.logic.OptionOrdering
import vn.unicom.thi360.feature.question.logic.WordCounter
import vn.unicom.thi360.feature.question.model.EssayGradingState
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.ItemReview
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichContentMode
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

private const val ESSAY_COLLAPSE_CHAR_THRESHOLD = 600
private const val ESSAY_COLLAPSED_MAX_LINES = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EssayQuestionView(
    item: QuestionItem.Essay,
    answer: EssayAnswer?,
    mode: QuestionMode,
    flagged: Boolean,
    onAnswer: (AnswerValue) -> Unit,
    onFlag: (() -> Unit)?,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
    attachments: (@Composable () -> Unit)?,
    essayEditorReady: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val html = answer?.html.orEmpty()
    val count = remember(html) { WordCounter.count(html) }
    val minWords = item.wordLimit
    val underLimit = minWords != null && count < minWords
    val isLocked = mode is QuestionMode.Locked
    val review = (mode as? QuestionMode.Review)?.review

    QuestionFrame(
        item = item,
        mode = mode,
        flagged = flagged,
        onFlag = onFlag,
        assets = assets,
        formulaEngine = formulaEngine,
        attachments = attachments,
        manualTag = review != null,
        modifier = modifier,
    ) {
        if (review != null) {
            EssayReviewBody(
                item = item,
                html = html,
                review = review,
                assets = assets,
                formulaEngine = formulaEngine,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.question_essayEditorLabel),
                    color = brand.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                )

                EssayRichEditor(
                    itemId = item.id,
                    html = html,
                    locked = isLocked,
                    ready = essayEditorReady,
                    onHtmlCommitted = { nextHtml ->
                        onAnswer(AnswerValues.essay(nextHtml))
                    },
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val wordCountText = if (minWords == null) {
                        stringResource(
                            R.string.question_essayWordCount,
                            OptionOrdering.formatInteger(count, locale),
                        )
                    } else {
                        stringResource(
                            R.string.question_essayWordCountMin,
                            OptionOrdering.formatInteger(count, locale),
                            OptionOrdering.formatInteger(minWords, locale),
                        )
                    }
                    Text(
                        text = wordCountText,
                        color = brand.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(QuestionTestTags.ESSAY_WORD_COUNT),
                    )
                    if (minWords != null) {
                        val limitText = if (underLimit) {
                            stringResource(
                                R.string.question_wordsNeeded,
                                OptionOrdering.formatInteger(minWords - count, locale),
                            )
                        } else {
                            stringResource(R.string.question_wordLimitMet)
                        }
                        Text(
                            text = limitText,
                            color = if (underLimit) status.warning else status.success,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag(QuestionTestTags.ESSAY_WORD_STATUS),
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.question_essayNote),
                    color = brand.textMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun EssayReviewBody(
    item: QuestionItem.Essay,
    html: String,
    review: ItemReview,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val essayReview = review.essay
    val count = essayReview?.wordCount ?: if (html.isNotBlank()) WordCounter.count(html) else 0
    val minWords = item.wordLimit
    val boxShape = RoundedCornerShape(10.dp)

    if (review.outcome == ItemOutcome.BLANK) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(boxShape)
                .background(brand.bgSubtle)
                .border(1.dp, brand.border, boxShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.question_notAnswered),
                color = brand.textSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        }
        return
    }

    val isPending = essayReview?.state == EssayGradingState.PENDING ||
        (essayReview == null && review.outcome == ItemOutcome.PENDING_REVIEW)

    if (isPending) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(boxShape)
                    .background(status.infoBg)
                    .border(1.dp, status.info, boxShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag(QuestionTestTags.ESSAY_PENDING_NOTE),
            ) {
                Text(
                    text = stringResource(R.string.question_pendingReviewNote),
                    color = status.info,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
            CollapsibleEssayText(
                html = html,
                count = count,
                minWords = minWords,
                assets = assets,
                formulaEngine = formulaEngine,
            )
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (essayReview?.state == EssayGradingState.REGRADING) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(boxShape)
                    .background(status.warningBg)
                    .border(1.dp, status.warning.copy(alpha = 0.6f), boxShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.question_regrading),
                    color = status.warning,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        val wordCountText = if (minWords == null) {
            stringResource(
                R.string.question_essayWordCount,
                OptionOrdering.formatInteger(count, locale),
            )
        } else {
            stringResource(
                R.string.question_essayWordCountMin,
                OptionOrdering.formatInteger(count, locale),
                OptionOrdering.formatInteger(minWords, locale),
            )
        }
        Text(
            text = wordCountText,
            color = brand.textSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.question_yourEssayLabel),
                color = brand.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            CollapsibleEssayText(
                html = html,
                count = count,
                minWords = minWords,
                assets = assets,
                formulaEngine = formulaEngine,
            )
        }

        val criteria = if (review.level.showsSolution) essayReview?.criteria.orEmpty() else emptyList()
        if (criteria.isNotEmpty()) {
            val criterionCol = stringResource(R.string.question_criterionCol)
            val pointsCol = stringResource(R.string.question_pointsCol)
            val columns = listOf(
                ReviewTableColumn(criterionCol, weight = 2.4f),
                ReviewTableColumn(pointsCol, weight = 1f),
            )
            val rows = criteria.mapIndexed { idx, criterion ->
                val pointsText = if (criterion.earned != null) {
                    if (criterion.max != null) {
                        stringResource(
                            R.string.question_points,
                            OptionOrdering.formatScore(criterion.earned, locale),
                            OptionOrdering.formatScore(criterion.max, locale),
                        )
                    } else {
                        "${OptionOrdering.formatScore(criterion.earned, locale)} ${stringResource(R.string.question_pointsSuffix)}"
                    }
                } else {
                    "—"
                }
                ReviewTableRowData(
                    key = criterion.id ?: "c-$idx",
                    primaryCell = {
                        Text(
                            text = criterion.description,
                            color = brand.text,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    },
                    detailCells = listOf(
                        pointsCol to {
                            Text(
                                text = pointsText,
                                color = brand.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        },
                    ),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(QuestionTestTags.ESSAY_CRITERIA_TABLE),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.question_criteriaLabel),
                    color = brand.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                ResponsiveReviewTable(columns = columns, rows = rows)
            }
        }

        val comment = if (review.level.showsSolution) essayReview?.comment else null
        if (!comment.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(boxShape)
                    .background(brand.bgSubtle)
                    .border(1.dp, brand.border, boxShape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.question_gradingCommentLabel),
                    color = brand.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = comment,
                    color = brand.text,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun CollapsibleEssayText(
    html: String,
    count: Int,
    minWords: Int?,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    var expanded by remember(html) { mutableStateOf(false) }
    val plain = remember(html) { WordCounter.textForWordCount(html) }
    val collapsedDisplay = remember(html) { WordCounter.plainText(html) }
    val isLong = plain.length > ESSAY_COLLAPSE_CHAR_THRESHOLD

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(QuestionTestTags.ESSAY_REVIEW_BODY),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isLong && !expanded) {
            Text(
                text = collapsedDisplay,
                color = brand.text,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                maxLines = ESSAY_COLLAPSED_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        } else if (html.isNotBlank()) {
            val doc = remember(html) { RichHtmlParser.parse(html) }
            RichContentView(
                document = doc,
                assets = assets,
                mode = RichContentMode.Review,
                formulaEngine = formulaEngine,
            )
        }

        if (isLong) {
            val toggleText = stringResource(
                if (expanded) R.string.question_hideFullEssay else R.string.question_showFullEssay,
            )
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = 8.dp)
                    .testTag(QuestionTestTags.ESSAY_REVIEW_TOGGLE),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = toggleText,
                    color = brand.primaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (minWords != null && count < minWords) {
            Text(
                text = stringResource(
                    R.string.question_wordsNeeded,
                    OptionOrdering.formatInteger((minWords - count).coerceAtLeast(0), locale),
                ),
                color = status.warning,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
