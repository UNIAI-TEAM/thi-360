package vn.unicom.thi360.feature.question.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.SpanWatcher
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.method.KeyListener
import android.text.method.NumberKeyListener
import android.text.method.TextKeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.math.BigDecimal
import kotlin.math.abs
import vn.unicom.thi360.R
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.theme.LocalAppLocale
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.logic.OptionOrdering
import vn.unicom.thi360.feature.question.logic.SymbolInputRules
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.ItemReview
import vn.unicom.thi360.feature.question.model.ItemReviewKey
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.formula.FormulaEngine

internal class NumericAllowAllKeyListener : NumberKeyListener() {
    override fun getInputType(): Int =
        InputType.TYPE_CLASS_NUMBER or
            InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

    override fun getAcceptedChars(): CharArray = ACCEPTED_CHARS

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned,
        dstart: Int,
        dend: Int,
    ): CharSequence? = null

    companion object {
        private val ACCEPTED_CHARS = "0123456789-/,. ".toCharArray()
    }
}

internal class TextNoAutoCorrectKeyListener : TextKeyListener(Capitalize.NONE, false) {
    override fun getInputType(): Int =
        InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
}

internal fun committedTextExcludingComposing(editable: Editable?): String {
    if (editable == null) return ""
    val compStart = BaseInputConnection.getComposingSpanStart(editable)
    val compEnd = BaseInputConnection.getComposingSpanEnd(editable)
    if (compStart >= 0 && compEnd > compStart && compEnd <= editable.length) {
        val copy = SpannableStringBuilder(editable)
        copy.delete(compStart, compEnd)
        return copy.toString()
    }
    return editable.toString()
}

internal fun hasActiveComposingSpan(editable: Editable?): Boolean {
    if (editable == null) return false
    val compStart = BaseInputConnection.getComposingSpanStart(editable)
    val compEnd = BaseInputConnection.getComposingSpanEnd(editable)
    return compStart >= 0 && compEnd > compStart && compEnd <= editable.length
}

internal class CompositionAwareEditText(context: Context) : EditText(context) {
    private var isSyncingExternal: Boolean = false
    private var isEditableMode: Boolean = true
    private var hadComposingSpan: Boolean = false
    private var lastEmittedText: String = ""
    private var lastExternalText: String? = null

    var onCommittedTextChanged: ((String) -> Unit)? = null
    var onFocusUpdated: ((Boolean) -> Unit)? = null

    private val deferredFlushRunnable = Runnable {
        flushIfComposingFinalized()
    }

    private val composingWatcher = object : SpanWatcher {
        override fun onSpanAdded(text: Spannable?, what: Any?, start: Int, end: Int) {
            if (isSyncingExternal) return
            if (hasActiveComposingSpan((text as? Editable) ?: editableText)) {
                hadComposingSpan = true
            }
        }

        override fun onSpanChanged(
            text: Spannable?,
            what: Any?,
            ostart: Int,
            oend: Int,
            nstart: Int,
            nend: Int,
        ) {
            if (isSyncingExternal) return
            if (hasActiveComposingSpan((text as? Editable) ?: editableText)) {
                hadComposingSpan = true
            }
        }

        override fun onSpanRemoved(text: Spannable?, what: Any?, start: Int, end: Int) {
            if (isSyncingExternal || !hadComposingSpan) return
            removeCallbacks(deferredFlushRunnable)
            post(deferredFlushRunnable)
        }
    }

    init {
        isSingleLine = true
        maxLines = 1
        background = null
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        filters = emptyArray()
        attachComposingWatcher(editableText)

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
                    if (isSyncingExternal || !isEditableMode) return
                    val composing = hasActiveComposingSpan(s)
                    if (composing) {
                        hadComposingSpan = true
                    } else {
                        hadComposingSpan = false
                    }
                    val committed = AnswerValues.stripNullChars(
                        committedTextExcludingComposing(s),
                    )
                    if (composing && committed == lastEmittedText) {
                        return
                    }
                    if (committed != lastEmittedText) {
                        lastEmittedText = committed
                        onCommittedTextChanged?.invoke(committed)
                    }
                }
            },
        )
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        attachComposingWatcher(editableText)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val base = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(base, true) {
            private var batchDepth = 0
            private var pendingFinish = false

            override fun beginBatchEdit(): Boolean {
                batchDepth += 1
                return super.beginBatchEdit()
            }

            override fun finishComposingText(): Boolean {
                val handled = super.finishComposingText()
                if (batchDepth == 0) {
                    removeCallbacks(deferredFlushRunnable)
                    flushIfComposingFinalized()
                } else {
                    pendingFinish = true
                }
                return handled
            }

            override fun endBatchEdit(): Boolean {
                val handled = super.endBatchEdit()
                if (batchDepth > 0) batchDepth -= 1
                if (batchDepth == 0 && pendingFinish) {
                    pendingFinish = false
                    removeCallbacks(deferredFlushRunnable)
                    flushIfComposingFinalized()
                }
                return handled
            }
        }
    }

    override fun onEditorAction(actionCode: Int) {
        if (actionCode == EditorInfo.IME_ACTION_DONE || actionCode == EditorInfo.IME_ACTION_NEXT) {
            editableText?.let { BaseInputConnection.removeComposingSpans(it) }
            removeCallbacks(deferredFlushRunnable)
            flushIfComposingFinalized()
        }
        super.onEditorAction(actionCode)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!focused) {
            editableText?.let { BaseInputConnection.removeComposingSpans(it) }
            removeCallbacks(deferredFlushRunnable)
            flushIfComposingFinalized()
        }
        onFocusUpdated?.invoke(focused)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(deferredFlushRunnable)
        super.onDetachedFromWindow()
    }

    fun applyKeyListenerMode(numeric: Boolean, editable: Boolean) {
        isEditableMode = editable
        if (!editable) {
            removeCallbacks(deferredFlushRunnable)
            editableText?.let { BaseInputConnection.removeComposingSpans(it) }
            hadComposingSpan = false
        }
        applyShortAnswerKeyListener(numeric = numeric, editable = editable)
    }

    fun applyTextSizePx(textSizePx: Float) {
        if (abs(textSize - textSizePx) > 0.5f) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
        }
    }

    fun syncExternalText(externalText: String) {
        if (hasActiveComposingSpan(editableText)) return
        if (externalText == lastExternalText) return
        lastExternalText = externalText
        val current = editableText?.toString().orEmpty()
        if (current == externalText) {
            lastEmittedText = externalText
            return
        }
        isSyncingExternal = true
        try {
            removeCallbacks(deferredFlushRunnable)
            val savedSel = selectionStart.coerceIn(0, externalText.length)
            setText(externalText)
            setSelection(savedSel)
            lastEmittedText = externalText
            hadComposingSpan = false
        } finally {
            isSyncingExternal = false
        }
    }

    private fun attachComposingWatcher(editable: Editable?) {
        if (editable == null) return
        val existing = editable.getSpans(0, editable.length, SpanWatcher::class.java)
        if (existing.none { it === composingWatcher }) {
            editable.setSpan(composingWatcher, 0, editable.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    private fun flushIfComposingFinalized() {
        if (isSyncingExternal || !isEditableMode || !hadComposingSpan) return
        val s = editableText ?: return
        if (hasActiveComposingSpan(s)) return
        hadComposingSpan = false
        val committed = AnswerValues.stripNullChars(s.toString())
        if (committed != lastEmittedText) {
            lastEmittedText = committed
            onCommittedTextChanged?.invoke(committed)
        }
    }
}

internal fun EditText.applyShortAnswerKeyListener(numeric: Boolean, editable: Boolean) {
    if (!editable) {
        keyListener = null
        showSoftInputOnFocus = false
        isFocusable = false
        isFocusableInTouchMode = false
        isCursorVisible = false
        return
    }
    val listener: KeyListener = if (numeric) {
        NumericAllowAllKeyListener()
    } else {
        TextNoAutoCorrectKeyListener()
    }
    inputType = listener.inputType
    keyListener = listener
    showSoftInputOnFocus = true
    isFocusable = true
    isFocusableInTouchMode = true
    isCursorVisible = true
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShortAnswerQuestionView(
    item: QuestionItem.ShortAnswer,
    answer: ShortAnswerValue?,
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
    val density = LocalDensity.current
    val textSizePx = with(density) { 16.sp.toPx() }
    val text = answer?.text.orEmpty()
    val isNumeric = SymbolInputRules.isNumericFormat(item.answerFormat)
    val showSymbolBar = SymbolInputRules.shouldShowSymbolInput(item.answerFormat, item.inputHint)
    val isAnswering = mode is QuestionMode.Answering
    val isLocked = mode is QuestionMode.Locked
    val review = (mode as? QuestionMode.Review)?.review

    val hints = remember(item.inputHint, item.answerFormat) {
        listOfNotNull(
            item.inputHint?.takeIf { it.isNotBlank() },
            SymbolInputRules.formatHint(item.answerFormat),
        )
    }

    val editTextRef: MutableState<CompositionAwareEditText?> = remember(item.id) { mutableStateOf(null) }
    var focused by remember(item.id) { mutableStateOf(false) }
    val latestOnAnswer by rememberUpdatedState(onAnswer)
    val latestIsAnswering by rememberUpdatedState(isAnswering)

    val answerLabel = stringResource(R.string.question_answerLabel)
    val answerPlaceholder = stringResource(R.string.question_answerPlaceholder)

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
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = answerLabel,
                color = brand.text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            )

            if (review != null) {
                ShortAnswerReviewBody(
                    item = item,
                    text = text,
                    review = review,
                )
            } else {
                val inputShape = RoundedCornerShape(10.dp)
                val borderColor = when {
                    focused && isAnswering -> brand.primaryFill
                    else -> brand.borderStrong
                }
                val textArgb = brand.text.toArgb()
                val hintArgb = brand.textMuted.toArgb()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(inputShape)
                        .background(if (isLocked) brand.bgSubtle else brand.bgSurface)
                        .border(if (focused && isAnswering) 2.dp else 1.dp, borderColor, inputShape)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    key(item.id) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .testTag(QuestionTestTags.SHORT_ANSWER_INPUT),
                            factory = { ctx ->
                                CompositionAwareEditText(ctx).apply {
                                    hint = answerPlaceholder
                                    contentDescription = answerLabel
                                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                                    imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_DONE
                                    applyTextSizePx(textSizePx)
                                    setTextColor(textArgb)
                                    setHintTextColor(hintArgb)
                                    applyKeyListenerMode(numeric = isNumeric, editable = isAnswering)
                                    onFocusUpdated = { hasFocus -> focused = hasFocus }
                                    onCommittedTextChanged = { committed ->
                                        if (latestIsAnswering) {
                                            latestOnAnswer(AnswerValues.shortAnswer(committed))
                                        }
                                    }
                                    syncExternalText(text)
                                    editTextRef.value = this
                                }
                            },
                            update = { view ->
                                editTextRef.value = view
                                view.applyTextSizePx(textSizePx)
                                view.setTextColor(textArgb)
                                view.setHintTextColor(hintArgb)
                                view.hint = answerPlaceholder
                                view.contentDescription = answerLabel
                                val currentlyEditable = view.keyListener != null
                                val expectedNumericClass =
                                    if (isNumeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
                                val currentClass = view.inputType and InputType.TYPE_MASK_CLASS
                                if (currentlyEditable != isAnswering ||
                                    (isAnswering && currentClass != expectedNumericClass)
                                ) {
                                    view.applyKeyListenerMode(
                                        numeric = isNumeric,
                                        editable = isAnswering,
                                    )
                                }
                                view.syncExternalText(text)
                            },
                        )
                    }
                }

                val insertAtCursor: (String) -> Unit = { insertion ->
                    if (isAnswering) {
                        val target = editTextRef.value
                        val editable = target?.editableText
                        if (target != null && editable != null) {
                            BaseInputConnection.removeComposingSpans(editable)
                            val selStart = target.selectionStart.takeIf { it >= 0 } ?: editable.length
                            val selEnd = target.selectionEnd.takeIf { it >= 0 } ?: selStart
                            val start = minOf(selStart, selEnd).coerceIn(0, editable.length)
                            val end = maxOf(selStart, selEnd).coerceIn(0, editable.length)
                            editable.replace(start, end, insertion)
                            target.setSelection((start + insertion.length).coerceIn(0, editable.length))
                            if (!target.hasFocus()) {
                                target.requestFocus()
                            }
                        } else {
                            val result = SymbolInputRules.insertTextAtCursor(text, null, null, insertion)
                            latestOnAnswer(AnswerValues.shortAnswer(result.nextValue))
                        }
                    }
                }

                if (isNumeric) {
                    val numericGroupLabel = stringResource(R.string.question_numericInput_groupLabel)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isLocked) Modifier.alpha(0.45f) else Modifier)
                            .semantics { contentDescription = numericGroupLabel }
                            .testTag(QuestionTestTags.NUMERIC_KEY_ROW),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SymbolInputRules.NUMERIC_EXTRA_KEYS.forEach { key ->
                            val keyName = stringResource(key.nameResId)
                            AuxiliaryKeyButton(
                                label = key.label,
                                accessibilityName = keyName,
                                enabled = isAnswering,
                                tag = QuestionTestTags.numericKey(key.id),
                                onClick = { insertAtCursor(key.insert) },
                            )
                        }
                    }
                } else if (showSymbolBar) {
                    val symbolGroupLabel = stringResource(R.string.question_symbolInput_groupLabel)
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isLocked) Modifier.alpha(0.45f) else Modifier)
                            .semantics { contentDescription = symbolGroupLabel }
                            .testTag(QuestionTestTags.SYMBOL_BAR),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SymbolInputRules.DEFAULT_ION_SYMBOLS.forEach { sym ->
                            val symName = stringResource(sym.nameResId)
                            AuxiliaryKeyButton(
                                label = sym.label,
                                accessibilityName = symName,
                                enabled = isAnswering,
                                tag = QuestionTestTags.symbolKey(sym.id),
                                onClick = { insertAtCursor(sym.insert) },
                            )
                        }
                    }
                }

                val hintPrefix = stringResource(R.string.question_hintPrefix)
                val shortNote = stringResource(R.string.question_shortAnswerNote)
                Text(
                    text = buildAnnotatedString {
                        if (hints.isNotEmpty()) {
                            append(hintPrefix)
                            append(' ')
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = brand.text)) {
                                append(hints.joinToString(" · "))
                            }
                            append(". ")
                        }
                        append(shortNote)
                    },
                    color = brand.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun AuxiliaryKeyButton(
    label: String,
    accessibilityName: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val brand = Thi360Theme.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(brand.bgSurface)
            .border(BorderStroke(1.dp, brand.borderStrong), shape)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = accessibilityName,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = accessibilityName
                role = Role.Button
            }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = brand.text,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ShortAnswerReviewBody(
    item: QuestionItem.ShortAnswer,
    text: String,
    review: ItemReview,
) {
    val brand = Thi360Theme.colors
    val locale = LocalAppLocale.current
    val shape = RoundedCornerShape(10.dp)
    val acceptedTexts = (review.key as? ItemReviewKey.ShortAnswer)?.acceptedTexts.orEmpty()
    val showAnswers = review.showsAnswers && acceptedTexts.isNotEmpty()
    val notAnsweredLabel = stringResource(R.string.question_notAnswered)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(brand.bgSubtle)
                    .border(1.dp, brand.borderStrong, shape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag(QuestionTestTags.SHORT_ANSWER_REVIEW_VALUE),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (text.isEmpty()) notAnsweredLabel else text,
                    color = if (text.isEmpty()) brand.textMuted else brand.text,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = if (text.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                )
            }

            if (review.outcome != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(brand.bgSurface)
                        .border(1.dp, brand.border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    OutcomeGlyph(
                        outcome = review.outcome,
                        size = 16.dp,
                        withAccessibilityLabel = true,
                    )
                }
            }
        }

        if (review.outcome == ItemOutcome.BLANK || text.isBlank()) {
            Text(
                text = notAnsweredLabel,
                color = brand.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }

        if (showAnswers) {
            val prefix = stringResource(R.string.question_correctAnswerPrefix)
            val pointsSuffix = stringResource(R.string.question_pointsSuffix)
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = brand.text)) {
                        append(prefix)
                        append(' ')
                    }
                    acceptedTexts.forEachIndexed { index, accepted ->
                        if (index > 0) append(" · ")
                        withStyle(
                            SpanStyle(
                                fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (index == 0) brand.text else brand.textSecondary,
                            ),
                        ) {
                            append(accepted)
                        }
                    }
                    if (review.earned != null) {
                        val earnedFmt = OptionOrdering.formatScore(review.earned, locale)
                        val maxFmt = OptionOrdering.formatScore(
                            review.max.takeIf { it > BigDecimal.ZERO } ?: item.points ?: BigDecimal.ONE,
                            locale,
                        )
                        withStyle(SpanStyle(color = brand.textMuted)) {
                            append(" ($earnedFmt / $maxFmt $pointsSuffix)")
                        }
                    }
                },
                color = brand.textSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
