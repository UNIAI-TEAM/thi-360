package vn.unicom.thi360.feature.question.ui

import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.math.BigDecimal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.logic.OptionOrdering
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.ItemReview
import vn.unicom.thi360.feature.question.model.ItemReviewKey
import vn.unicom.thi360.feature.question.model.PartReviewResult
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine
import vn.unicom.thi360.feature.richcontent.model.RichParseOptions
import vn.unicom.thi360.feature.richcontent.parser.RichHtmlParser
import vn.unicom.thi360.feature.richcontent.ui.RichContentView

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClozeQuestionView(
    item: QuestionItem.Cloze,
    answer: ClozeAnswer?,
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
    val blanks = answer?.blanks.orEmpty()
    val filledCount = item.blanks.count { blank -> blanks[blank.id]?.trim().orEmpty().isNotEmpty() }
    val review = (mode as? QuestionMode.Review)?.review
    val blankStart = item.blankNumberStart ?: item.displayNo

    val passageDoc = remember(item.passage, blankStart) {
        RichHtmlParser.parse(
            item.passage,
            RichParseOptions(slotMarkers = true, blankNumberStart = blankStart),
        )
    }

    val latestBlanks by rememberUpdatedState(blanks)
    val latestOnAnswer by rememberUpdatedState(onAnswer)
    val isAnswering = mode is QuestionMode.Answering
    val isLocked = mode is QuestionMode.Locked

    val slots: Map<String, @Composable () -> Unit> = remember(
        item.id,
        item.blanks,
        blanks,
        mode,
        locale,
        blankStart,
    ) {
        val map = LinkedHashMap<String, @Composable () -> Unit>(item.blanks.size)
        item.blanks.forEachIndexed { ordinal, blank ->
            val number = blankStart + ordinal
            map[blank.id] = {
                ClozeInlineSlot(
                    blankId = blank.id,
                    questionNo = number,
                    ordinal = ordinal + 1,
                    value = blanks[blank.id].orEmpty(),
                    mode = mode,
                    partResult = review?.detail?.get(blank.id),
                    onValueCommitted = { nextValue ->
                        val updated = LinkedHashMap<String, String>(latestBlanks.size + 1)
                        updated.putAll(latestBlanks)
                        updated[blank.id] = nextValue
                        latestOnAnswer(AnswerValues.cloze(item, updated))
                    },
                )
            }
        }
        map
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isAnswering || isLocked) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.question_clozeHelp),
                        color = brand.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.question_clozeFilled,
                            OptionOrdering.formatInteger(filledCount, locale),
                            OptionOrdering.formatInteger(item.blanks.size, locale),
                        ),
                        color = brand.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag(QuestionTestTags.CLOZE_COUNTER),
                    )
                }
            }

            RichContentView(
                document = passageDoc,
                assets = assets,
                mode = mode.toRichContentMode(),
                slots = slots,
                blankNumberStart = blankStart,
                formulaEngine = formulaEngine,
            )

            if (isAnswering || isLocked) {
                Text(
                    text = stringResource(R.string.question_clozeBlankNote),
                    color = brand.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }

            if (review != null) {
                ClozeReviewTable(
                    item = item,
                    blanks = blanks,
                    review = review,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ClozeInlineSlot(
    blankId: String,
    questionNo: Int,
    ordinal: Int,
    value: String,
    mode: QuestionMode,
    partResult: PartReviewResult?,
    onValueCommitted: (String) -> Unit,
) {
    val brand = Thi360Theme.colors
    val status = Thi360Theme.statusColors
    val locale = LocalAppLocale.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val latestOnCommitted by rememberUpdatedState(onValueCommitted)
    val latestIsAnswering by rememberUpdatedState(mode is QuestionMode.Answering)

    val placeholder = stringResource(
        R.string.question_clozePlaceholder,
        OptionOrdering.formatInteger(ordinal, locale),
    )
    val ariaLabel = stringResource(
        R.string.question_clozeBlankAria,
        OptionOrdering.formatInteger(questionNo, locale),
        OptionOrdering.formatInteger(ordinal, locale),
    )

    val shape = RoundedCornerShape(8.dp)

    if (mode is QuestionMode.Review) {
        val bgColor: Color
        val borderColor: Color
        when (partResult?.outcome) {
            ItemOutcome.CORRECT -> {
                bgColor = status.successBg
                borderColor = status.success
            }
            ItemOutcome.PARTIAL -> {
                bgColor = status.warningBg
                borderColor = status.warning
            }
            ItemOutcome.WRONG -> {
                bgColor = brand.dangerBg
                borderColor = brand.dangerText
            }
            else -> {
                bgColor = brand.bgSubtle
                borderColor = brand.borderStrong
            }
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp, vertical = 2.dp),
        ) {
            Box(
                modifier = Modifier
                    .widthIn(min = 96.dp, max = 180.dp)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(bgColor)
                    .border(1.5.dp, borderColor, shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag(QuestionTestTags.clozeBlank(blankId)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.ifEmpty { placeholder },
                    color = if (value.isEmpty()) brand.textMuted else brand.text,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (partResult != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-5).dp)
                        .clip(CircleShape)
                        .background(brand.bgSurface)
                        .padding(2.dp),
                ) {
                    OutcomeGlyph(
                        outcome = partResult.outcome,
                        size = 13.dp,
                        withAccessibilityLabel = true,
                    )
                }
            }
        }
        return
    }

    var focused by remember(blankId) { mutableStateOf(false) }
    val isAnswering = mode is QuestionMode.Answering
    val isLocked = mode is QuestionMode.Locked
    val textArgb = brand.text.toArgb()
    val hintArgb = brand.textMuted.toArgb()
    val syncingFlag = remember(blankId) { booleanArrayOf(false) }
    val lastEmitted = remember(blankId) { arrayOf(value) }
    val extraClearancePx = with(density) { 48.dp.roundToPx() }

    Box(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .widthIn(min = 96.dp, max = 180.dp)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (isLocked) brand.bgSubtle else brand.bgSurface)
            .border(
                width = if (focused && isAnswering) 2.dp else 1.dp,
                color = if (focused && isAnswering) brand.primaryFill else brand.borderStrong,
                shape = shape,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier
                .widthIn(min = 80.dp, max = 164.dp)
                .heightIn(min = 40.dp)
                .testTag(QuestionTestTags.clozeBlank(blankId)),
            factory = { ctx ->
                EditText(ctx).apply {
                    hint = placeholder
                    contentDescription = ariaLabel
                    isSingleLine = true
                    maxLines = 1
                    background = null
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    setTextColor(textArgb)
                    setHintTextColor(hintArgb)
                    imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NEXT
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                    filters = emptyArray()
                    applyShortAnswerKeyListener(numeric = false, editable = isAnswering)

                    if (value.isNotEmpty()) {
                        syncingFlag[0] = true
                        setText(value)
                        setSelection(value.length)
                        syncingFlag[0] = false
                    }

                    setOnFocusChangeListener { v, hasFocus ->
                        focused = hasFocus
                        if (hasFocus) {
                            coroutineScope.launch {
                                bringIntoViewRequester.bringIntoView()
                                delay(160L)
                                bringIntoViewRequester.bringIntoView()
                            }
                            v.postDelayed(
                                {
                                    val rect = Rect(0, 0, v.width, v.height + extraClearancePx)
                                    v.requestRectangleOnScreen(rect, false)
                                },
                                150L,
                            )
                        }
                    }

                    addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int,
                            ) = Unit

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int,
                            ) = Unit

                            override fun afterTextChanged(s: Editable?) {
                                if (syncingFlag[0] || !latestIsAnswering) return
                                val committed = AnswerValues.stripNullChars(
                                    committedTextExcludingComposing(s),
                                )
                                if (hasActiveComposingSpan(s) && committed == lastEmitted[0]) {
                                    return
                                }
                                if (committed != lastEmitted[0]) {
                                    lastEmitted[0] = committed
                                    latestOnCommitted(committed)
                                }
                            }
                        },
                    )
                }
            },
            update = { view ->
                view.setTextColor(textArgb)
                view.setHintTextColor(hintArgb)
                view.hint = placeholder
                view.contentDescription = ariaLabel
                val currentlyEditable = view.keyListener != null
                if (currentlyEditable != isAnswering) {
                    view.applyShortAnswerKeyListener(numeric = false, editable = isAnswering)
                }
                if (!hasActiveComposingSpan(view.text)) {
                    val current = view.text?.toString().orEmpty()
                    if (current != value) {
                        syncingFlag[0] = true
                        val savedSel = view.selectionStart.coerceIn(0, value.length)
                        view.setText(value)
                        view.setSelection(savedSel)
                        lastEmitted[0] = value
                        syncingFlag[0] = false
                    }
                }
            },
        )
    }
}

@Composable
private fun ClozeReviewTable(
    item: QuestionItem.Cloze,
    blanks: Map<String, String>,
    review: ItemReview,
) {
    val brand = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val acceptedMap = (review.key as? ItemReviewKey.Cloze)?.blanks
    val showAnswers = review.showsAnswers && !acceptedMap.isNullOrEmpty()
    val hasPoints = item.blanks.any { blank -> review.detail[blank.id]?.earned != null }
    val blankStart = item.blankNumberStart ?: item.displayNo

    val itemCol = stringResource(R.string.question_itemCol)
    val youChoseCol = stringResource(R.string.question_youChoseCol)
    val acceptedCol = stringResource(R.string.question_acceptedCol)
    val pointsCol = stringResource(R.string.question_pointsCol)
    val blankTag = stringResource(R.string.question_blankFilledTag)

    val columns = buildList {
        add(ReviewTableColumn(itemCol, weight = 1f))
        add(ReviewTableColumn(youChoseCol, weight = 1.4f))
        if (showAnswers) add(ReviewTableColumn(acceptedCol, weight = 1.6f))
        if (hasPoints) add(ReviewTableColumn(pointsCol, weight = 1f))
    }

    val rows = item.blanks.mapIndexed { ordinal, blank ->
        val number = blankStart + ordinal
        val part = review.detail[blank.id]
        val text = blanks[blank.id].orEmpty()
        val accepted = acceptedMap?.get(blank.id).orEmpty()
        val itemLabel = stringResource(
            R.string.question_label,
            OptionOrdering.formatInteger(number, locale),
        )
        val pointsText = if (part?.earned != null) {
            stringResource(
                R.string.question_points,
                OptionOrdering.formatScore(part.earned, locale),
                OptionOrdering.formatScore(part.max ?: blank.points ?: BigDecimal.ONE, locale),
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
                        if (text.isEmpty()) {
                            Text(text = blankTag, color = brand.textMuted, fontSize = 14.sp)
                        } else {
                            Text(text = text, color = brand.text, fontSize = 14.sp)
                        }
                    }
                },
            )
            if (showAnswers) {
                add(
                    acceptedCol to {
                        Text(
                            text = if (accepted.isEmpty()) "—" else accepted.joinToString(" · "),
                            color = brand.text,
                            fontSize = 14.sp,
                        )
                    },
                )
            }
            if (hasPoints) {
                add(
                    pointsCol to {
                        Text(text = pointsText, color = brand.text, fontSize = 14.sp)
                    },
                )
            }
        }

        ReviewTableRowData(
            key = blank.id,
            primaryCell = {
                Text(
                    text = itemLabel,
                    color = brand.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            detailCells = detailCells,
        )
    }

    ResponsiveReviewTable(columns = columns, rows = rows)
}
