package vn.unicom.thi360.feature.question.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.logic.OptionOrdering
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichParseOptions
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

private val SET_TWO_COLUMN_BREAKPOINT_DP = 840.dp
private val SET_UNBOUNDED_PANE_MAX_HEIGHT = 560.dp

@Composable
fun QuestionSetView(
    item: QuestionItem.QuestionSet,
    answers: Map<String, AnswerValue>,
    mode: QuestionMode,
    flaggedIds: Set<String> = emptySet(),
    onAnswer: (childId: String, value: AnswerValue) -> Unit = { _, _ -> },
    onFlag: ((childId: String) -> Unit)? = null,
    assets: AttemptAssetSource? = null,
    formulaEngine: FormulaEngine? = null,
    attachments: (@Composable () -> Unit)? = null,
    childAttachments: (childId: String) -> (@Composable () -> Unit)? = { null },
    modifier: Modifier = Modifier,
) {
    val brand = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val review = (mode as? QuestionMode.Review)?.review
    val fromNo = item.children.minOfOrNull { it.displayNo } ?: item.displayNo
    val toNo = item.children.maxOfOrNull { it.displayNo } ?: item.displayNo
    val numberStart = item.blankNumberStart ?: 1
    val passageDoc = remember(item.passage, numberStart) {
        RichHtmlParser.parse(item.passage, RichParseOptions(blankNumberStart = numberStart))
    }

    val fromFmt = OptionOrdering.formatInteger(fromNo, locale)
    val toFmt = OptionOrdering.formatInteger(toNo, locale)
    val caption = stringResource(R.string.question_setPassageCaption, fromFmt, toFmt)
    val rangeLabel = stringResource(R.string.question_rangeLabel, fromFmt, toFmt)
    val passageScrollState = rememberScrollState()
    val childrenScrollState = rememberScrollState()

    var rowHeightPx by remember(item.id) { mutableIntStateOf(0) }
    var passageHeightPx by remember(item.id) { mutableIntStateOf(0) }
    var stickyRawOffsetPx by remember(item.id) { mutableIntStateOf(0) }
    var visibleViewportHeightPx by remember(item.id, screenHeightDp) { mutableIntStateOf(0) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag(QuestionTestTags.SET_ROOT),
    ) {
        val isTwoColumn = maxWidth >= SET_TWO_COLUMN_BREAKPOINT_DP
        val hasBoundedHeight = constraints.hasBoundedHeight && maxHeight != Dp.Infinity
        val defaultUnboundedLimit = (screenHeightDp - 96.dp).coerceIn(240.dp, SET_UNBOUNDED_PANE_MAX_HEIGHT)
        val observedViewportLimit = if (visibleViewportHeightPx > 0) {
            with(density) { visibleViewportHeightPx.toDp() }.coerceIn(200.dp, defaultUnboundedLimit)
        } else {
            defaultUnboundedLimit
        }
        val paneHeightLimit = if (hasBoundedHeight) {
            (maxHeight - 80.dp).coerceAtLeast(200.dp)
        } else {
            observedViewportLimit
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
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

            Text(
                text = caption.uppercase(),
                color = brand.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )

            if (item.stem.isNotBlank()) {
                val stemDoc = remember(item.stem) { RichHtmlParser.parse(item.stem) }
                RichContentView(
                    document = stemDoc,
                    assets = assets,
                    mode = mode.toRichContentMode(),
                    formulaEngine = formulaEngine,
                )
            }

            if (isTwoColumn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val rawPosY = coords.positionInWindow().y
                            val clipped = coords.boundsInWindow()
                            val measuredRowHeight = coords.size.height
                            rowHeightPx = measuredRowHeight

                            var ancestor = coords.parentLayoutCoordinates
                            var smallestViewport = Int.MAX_VALUE
                            while (ancestor != null) {
                                val h = ancestor.size.height
                                if (h in 1 until measuredRowHeight && h < smallestViewport) {
                                    smallestViewport = h
                                }
                                ancestor = ancestor.parentLayoutCoordinates
                            }
                            if (smallestViewport != Int.MAX_VALUE) {
                                visibleViewportHeightPx = smallestViewport
                            } else {
                                val visibleHeight = clipped.height.roundToInt()
                                if (visibleHeight > visibleViewportHeightPx) {
                                    visibleViewportHeightPx = visibleHeight
                                }
                            }

                            if (clipped.height > 0f) {
                                stickyRawOffsetPx = (clipped.top - rawPosY).roundToInt().coerceAtLeast(0)
                            } else if (rawPosY >= 0f) {
                                stickyRawOffsetPx = 0
                            }
                        }
                        .testTag(QuestionTestTags.SET_TWO_COLUMN),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    val passageShape = RoundedCornerShape(12.dp)
                    Column(
                        modifier = Modifier
                            .weight(7f)
                            .offset {
                                val y = if (hasBoundedHeight) {
                                    0
                                } else {
                                    stickyRawOffsetPx.coerceIn(
                                        0,
                                        (rowHeightPx - passageHeightPx).coerceAtLeast(0),
                                    )
                                }
                                IntOffset(0, y)
                            }
                            .testTag(QuestionTestTags.SET_PASSAGE_PANE)
                            .onSizeChanged { passageHeightPx = it.height }
                            .heightIn(max = paneHeightLimit)
                            .clip(passageShape)
                            .background(brand.bgSurface)
                            .border(1.dp, brand.border, passageShape)
                            .padding(16.dp)
                            .verticalScroll(passageScrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(brand.primaryFill),
                            )
                            Text(
                                text = rangeLabel.uppercase(),
                                color = brand.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        HorizontalDivider(color = brand.border, thickness = 1.dp)
                        RichContentView(
                            document = passageDoc,
                            assets = assets,
                            mode = mode.toRichContentMode(),
                            blankNumberStart = numberStart,
                            formulaEngine = formulaEngine,
                        )
                    }

                    val childrenPaneModifier = if (hasBoundedHeight) {
                        Modifier
                            .weight(8f)
                            .heightIn(max = paneHeightLimit)
                            .verticalScroll(childrenScrollState)
                    } else {
                        Modifier.weight(8f)
                    }
                    Column(
                        modifier = childrenPaneModifier.testTag(QuestionTestTags.SET_CHILDREN_PANE),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        QuestionSetChildrenList(
                            children = item.children,
                            answers = answers,
                            mode = mode,
                            flaggedIds = flaggedIds,
                            onAnswer = onAnswer,
                            onFlag = onFlag,
                            assets = assets,
                            formulaEngine = formulaEngine,
                            childAttachments = childAttachments,
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(QuestionTestTags.SET_SINGLE_COLUMN),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val passageShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(passageShape)
                            .background(brand.bgSurface)
                            .border(1.dp, brand.border, passageShape)
                            .padding(16.dp)
                            .testTag(QuestionTestTags.SET_PASSAGE_PANE),
                    ) {
                        RichContentView(
                            document = passageDoc,
                            assets = assets,
                            mode = mode.toRichContentMode(),
                            blankNumberStart = numberStart,
                            formulaEngine = formulaEngine,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(QuestionTestTags.SET_CHILDREN_PANE),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        QuestionSetChildrenList(
                            children = item.children,
                            answers = answers,
                            mode = mode,
                            flaggedIds = flaggedIds,
                            onAnswer = onAnswer,
                            onFlag = onFlag,
                            assets = assets,
                            formulaEngine = formulaEngine,
                            childAttachments = childAttachments,
                        )
                    }
                }
            }

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
}

@Composable
private fun QuestionSetChildrenList(
    children: List<QuestionItem>,
    answers: Map<String, AnswerValue>,
    mode: QuestionMode,
    flaggedIds: Set<String>,
    onAnswer: (childId: String, value: AnswerValue) -> Unit,
    onFlag: ((childId: String) -> Unit)?,
    assets: AttemptAssetSource?,
    formulaEngine: FormulaEngine?,
    childAttachments: (childId: String) -> (@Composable () -> Unit)?,
) {
    val parentReview = (mode as? QuestionMode.Review)?.review
    children.forEach { child ->
        key(child.id) {
            val childMode = if (parentReview != null) {
                QuestionMode.Review(parentReview.childReview(child.id))
            } else {
                mode
            }
            QuestionView(
                item = child,
                answer = answers[child.id],
                mode = childMode,
                flagged = child.id in flaggedIds,
                onAnswer = { value -> onAnswer(child.id, value) },
                onFlag = onFlag?.let { callback -> { callback(child.id) } },
                assets = assets,
                formulaEngine = formulaEngine,
                attachments = childAttachments(child.id),
            )
        }
    }
}
