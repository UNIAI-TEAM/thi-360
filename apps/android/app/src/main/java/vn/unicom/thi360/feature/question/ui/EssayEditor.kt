package vn.unicom.thi360.feature.question.ui

import android.content.ClipboardManager
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
import android.text.style.StyleSpan
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs
import kotlin.math.roundToInt
import vn.unicom.thi360.R
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.logic.EssayHtml
import vn.unicom.thi360.feature.question.logic.EssayLineBreakSpan
import vn.unicom.thi360.feature.question.logic.EssayListItemSpan
import vn.unicom.thi360.feature.question.logic.EssayListKind

internal data class EssayToolbarState(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val listKind: EssayListKind? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

private data class EssaySnapshot(
    val html: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

internal class EssayEditText(context: Context) : EditText(context) {
    private val defaultKeyListener: KeyListener?
    private val indentPx: Int = (24f * context.resources.displayMetrics.density).roundToInt()

    private val undoStack = ArrayDeque<EssaySnapshot>()
    private val redoStack = ArrayDeque<EssaySnapshot>()
    private var currentCommitted = EssaySnapshot("", 0, 0)
    private var lastEmittedHtml: String = ""
    private var lastExternalHtml: String? = null
    private var isInternalChange: Boolean = false
    private var isLockedMode: Boolean = false
    private var pendingBold: Boolean = false
    private var pendingItalic: Boolean = false

    private var hadComposingSpan: Boolean = false
    private var lastComposingStart: Int = -1
    private var lastComposingEnd: Int = -1

    private var insertedStart: Int = -1
    private var insertedCount: Int = 0
    private var insertedNewline: Boolean = false

    var onCommittedHtmlChanged: ((String) -> Unit)? = null
    var onToolbarStateChanged: ((EssayToolbarState) -> Unit)? = null
    var onFocusUpdated: ((Boolean) -> Unit)? = null

    private val deferredFlushRunnable = Runnable {
        flushFinalizedComposing()
    }

    private val composingWatcher = object : SpanWatcher {
        override fun onSpanAdded(text: Spannable?, what: Any?, start: Int, end: Int) {
            if (isInternalChange) return
            recordComposingBounds((text as? Editable) ?: editableText)
        }

        override fun onSpanChanged(
            text: Spannable?,
            what: Any?,
            ostart: Int,
            oend: Int,
            nstart: Int,
            nend: Int,
        ) {
            if (isInternalChange) return
            recordComposingBounds((text as? Editable) ?: editableText)
        }

        override fun onSpanRemoved(text: Spannable?, what: Any?, start: Int, end: Int) {
            if (isInternalChange || !hadComposingSpan) return
            removeCallbacks(deferredFlushRunnable)
            post(deferredFlushRunnable)
        }
    }

    init {
        minLines = 10
        gravity = Gravity.TOP or Gravity.START
        background = null
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setLineSpacing(0f, 1.35f)
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        filters = emptyArray()
        defaultKeyListener = keyListener
        attachComposingWatcher(editableText)

        addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                    if (isInternalChange) return
                    insertedStart = start
                    insertedCount = after
                    insertedNewline = false
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    if (isInternalChange) return
                    if (count == 1 && s != null && start < s.length && s[start] == '\n') {
                        insertedNewline = true
                    }
                }

                override fun afterTextChanged(s: Editable?) {
                    if (isInternalChange || s == null || isLockedMode) return
                    val composing = hasActiveComposingSpan(s)
                    if (composing) {
                        recordComposingBounds(s)
                    } else {
                        hadComposingSpan = false
                        lastComposingStart = -1
                        lastComposingEnd = -1
                    }

                    if (!composing && insertedCount > 0 && insertedStart >= 0) {
                        val end = (insertedStart + insertedCount).coerceAtMost(s.length)
                        if (insertedNewline && insertedStart < s.length) {
                            handleNewlineInsertion(s, insertedStart)
                        } else if (end > insertedStart) {
                            applyPendingStylesIfNeeded(s, insertedStart, end)
                            ensureListSpanCoversParagraph(s, insertedStart)
                        }
                    }

                    val committedHtml = exportCommittedHtml(s)
                    if (composing) {
                        if (committedHtml != lastEmittedHtml) {
                            lastEmittedHtml = committedHtml
                            onCommittedHtmlChanged?.invoke(committedHtml)
                        }
                        notifyToolbarState()
                        return
                    }

                    if (committedHtml != currentCommitted.html) {
                        pushUndo(currentCommitted)
                        redoStack.clear()
                        currentCommitted = EssaySnapshot(
                            html = committedHtml,
                            selectionStart = selectionStart.coerceIn(0, s.length),
                            selectionEnd = selectionEnd.coerceIn(0, s.length),
                        )
                    }
                    if (committedHtml != lastEmittedHtml) {
                        lastEmittedHtml = committedHtml
                        onCommittedHtmlChanged?.invoke(committedHtml)
                    }
                    notifyToolbarState()
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
                    flushFinalizedComposing()
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
                    flushFinalizedComposing()
                }
                return handled
            }
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (!focused) {
            editableText?.let { BaseInputConnection.removeComposingSpans(it) }
            removeCallbacks(deferredFlushRunnable)
            flushFinalizedComposing()
        }
        onFocusUpdated?.invoke(focused)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(deferredFlushRunnable)
        super.onDetachedFromWindow()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!isInternalChange) {
            syncPendingStylesFromSelection()
            notifyToolbarState()
        }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (isLockedMode) {
            if (id == android.R.id.copy || id == android.R.id.selectAll) {
                return super.onTextContextMenuItem(id)
            }
            return false
        }
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            handlePaste(plainOnly = id == android.R.id.pasteAsPlainText)
            return true
        }
        return super.onTextContextMenuItem(id)
    }

    fun applyTextSizePx(textSizePx: Float) {
        if (abs(textSize - textSizePx) > 0.5f) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
        }
    }

    fun setLocked(locked: Boolean) {
        if (isLockedMode == locked) return
        isLockedMode = locked
        if (locked) {
            removeCallbacks(deferredFlushRunnable)
            editableText?.let { BaseInputConnection.removeComposingSpans(it) }
            hadComposingSpan = false
            lastComposingStart = -1
            lastComposingEnd = -1
            keyListener = null
            showSoftInputOnFocus = false
            isCursorVisible = false
            setTextIsSelectable(true)
            isFocusable = true
            isFocusableInTouchMode = true
        } else {
            setTextIsSelectable(false)
            keyListener = defaultKeyListener
            showSoftInputOnFocus = true
            isCursorVisible = true
            isFocusable = true
            isFocusableInTouchMode = true
        }
        notifyToolbarState()
    }

    fun syncExternalHtml(html: String) {
        if (hasActiveComposingSpan(editableText)) return
        if (html == lastExternalHtml) return
        lastExternalHtml = html
        val currentExport = editableText?.let { EssayHtml.export(it) }.orEmpty()
        if (currentExport == html || currentExport == EssayHtml.sanitizePastedHtml(html)) {
            lastEmittedHtml = currentExport
            return
        }
        isInternalChange = true
        try {
            removeCallbacks(deferredFlushRunnable)
            val loaded = EssayHtml.importToSpannable(html, indentPx)
            setText(loaded)
            setSelection(loaded.length)
            val canonical = EssayHtml.export(loaded)
            currentCommitted = EssaySnapshot(canonical, loaded.length, loaded.length)
            lastEmittedHtml = canonical
            hadComposingSpan = false
            lastComposingStart = -1
            lastComposingEnd = -1
            undoStack.clear()
            redoStack.clear()
            pendingBold = false
            pendingItalic = false
        } finally {
            isInternalChange = false
        }
        notifyToolbarState()
    }

    fun toggleBold() {
        if (isLockedMode) return
        toggleInlineStyle(Typeface.BOLD)
    }

    fun toggleItalic() {
        if (isLockedMode) return
        toggleInlineStyle(Typeface.ITALIC)
    }

    fun toggleList(kind: EssayListKind) {
        if (isLockedMode) return
        val s = editableText ?: return
        BaseInputConnection.removeComposingSpans(s)
        removeCallbacks(deferredFlushRunnable)
        flushFinalizedComposing()
        val selStart = selectionStart.coerceIn(0, s.length)
        val selEnd = selectionEnd.coerceIn(0, s.length)
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)

        val paragraphs = overlappingParagraphs(s, start, end)
        val allSameKind = paragraphs.isNotEmpty() && paragraphs.all { range ->
            EssayHtml.listSpanAtParagraph(s, range)?.kind == kind
        }

        val beforeSnapshot = EssaySnapshot(EssayHtml.export(s), selStart, selEnd)
        isInternalChange = true
        try {
            for (range in paragraphs) {
                val existing = s.getSpans(range.first, range.last, EssayListItemSpan::class.java)
                    .filter { span ->
                        val spS = s.getSpanStart(span)
                        val spE = s.getSpanEnd(span)
                        if (range.first == range.last) {
                            spS <= range.first && spE >= range.first
                        } else {
                            spS < range.last && spE > range.first
                        }
                    }
                for (span in existing) {
                    s.removeSpan(span)
                }
                if (!allSameKind) {
                    val flags = if (range.first == range.last) {
                        Spanned.SPAN_INCLUSIVE_INCLUSIVE
                    } else {
                        Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                    }
                    s.setSpan(
                        EssayListItemSpan(listStack = listOf(kind), ordinal = 1, indentPx = indentPx),
                        range.first,
                        range.last,
                        flags,
                    )
                }
            }
            EssayHtml.recomputeListOrdinals(s, indentPx)
        } finally {
            isInternalChange = false
        }

        commitManualMutation(beforeSnapshot, s)
    }

    fun performUndo() {
        if (isLockedMode || undoStack.isEmpty()) return
        val s = editableText ?: return
        BaseInputConnection.removeComposingSpans(s)
        hadComposingSpan = false
        redoStack.addLast(currentCommitted)
        val target = undoStack.removeLast()
        restoreSnapshot(target)
    }

    fun performRedo() {
        if (isLockedMode || redoStack.isEmpty()) return
        val s = editableText ?: return
        BaseInputConnection.removeComposingSpans(s)
        hadComposingSpan = false
        undoStack.addLast(currentCommitted)
        val target = redoStack.removeLast()
        restoreSnapshot(target)
    }

    fun pasteHtmlContent(rawHtml: String) {
        if (isLockedMode) return
        val sanitized = EssayHtml.sanitizePastedHtml(rawHtml)
        val imported = EssayHtml.importToSpannable(sanitized, indentPx)
        insertSanitizedSpannable(imported)
    }

    private fun attachComposingWatcher(editable: Editable?) {
        if (editable == null) return
        val existing = editable.getSpans(0, editable.length, SpanWatcher::class.java)
        if (existing.none { it === composingWatcher }) {
            editable.setSpan(composingWatcher, 0, editable.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    private fun recordComposingBounds(editable: Editable?) {
        if (editable == null) return
        val cs = BaseInputConnection.getComposingSpanStart(editable)
        val ce = BaseInputConnection.getComposingSpanEnd(editable)
        if (cs >= 0 && ce > cs && ce <= editable.length) {
            hadComposingSpan = true
            lastComposingStart = cs
            lastComposingEnd = ce
        }
    }

    private fun flushFinalizedComposing() {
        if (isInternalChange || isLockedMode || !hadComposingSpan) return
        val s = editableText ?: return
        if (hasActiveComposingSpan(s)) return
        hadComposingSpan = false
        val start = lastComposingStart
        val end = lastComposingEnd
        lastComposingStart = -1
        lastComposingEnd = -1

        if (start >= 0 && end > start && end <= s.length) {
            applyPendingStylesIfNeeded(s, start, end)
            ensureListSpanCoversParagraph(s, start)
        }

        val committedHtml = EssayHtml.export(s)
        if (committedHtml != currentCommitted.html) {
            pushUndo(currentCommitted)
            redoStack.clear()
            currentCommitted = EssaySnapshot(
                html = committedHtml,
                selectionStart = selectionStart.coerceIn(0, s.length),
                selectionEnd = selectionEnd.coerceIn(0, s.length),
            )
        }
        if (committedHtml != lastEmittedHtml) {
            lastEmittedHtml = committedHtml
            onCommittedHtmlChanged?.invoke(committedHtml)
        }
        notifyToolbarState()
    }

    private fun handlePaste(plainOnly: Boolean) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val item = clip.getItemAt(0)
        val html = if (!plainOnly) item.htmlText else null
        if (!html.isNullOrBlank()) {
            pasteHtmlContent(html)
            return
        }
        val plain = item.coerceToText(context)?.toString().orEmpty()
        val sanitizedHtml = EssayHtml.sanitizePlainText(plain)
        val imported = EssayHtml.importToSpannable(sanitizedHtml, indentPx)
        insertSanitizedSpannable(imported)
    }

    private fun insertSanitizedSpannable(imported: SpannableStringBuilder) {
        val s = editableText ?: return
        BaseInputConnection.removeComposingSpans(s)
        hadComposingSpan = false
        val selStart = selectionStart.coerceIn(0, s.length)
        val selEnd = selectionEnd.coerceIn(0, s.length)
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)
        val beforeSnapshot = EssaySnapshot(EssayHtml.export(s), selStart, selEnd)

        isInternalChange = true
        try {
            s.replace(start, end, imported)
            val newCursor = (start + imported.length).coerceIn(0, s.length)
            setSelection(newCursor)
            EssayHtml.recomputeListOrdinals(s, indentPx)
        } finally {
            isInternalChange = false
        }
        commitManualMutation(beforeSnapshot, s)
    }

    private fun toggleInlineStyle(style: Int) {
        val s = editableText ?: return
        BaseInputConnection.removeComposingSpans(s)
        removeCallbacks(deferredFlushRunnable)
        flushFinalizedComposing()
        val selStart = selectionStart.coerceIn(0, s.length)
        val selEnd = selectionEnd.coerceIn(0, s.length)
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)

        if (start == end) {
            if (style == Typeface.BOLD) {
                pendingBold = !pendingBold
            } else if (style == Typeface.ITALIC) {
                pendingItalic = !pendingItalic
            }
            notifyToolbarState()
            return
        }

        val beforeSnapshot = EssaySnapshot(EssayHtml.export(s), selStart, selEnd)
        val allHaveStyle = (start until end).all { idx -> hasStyleAt(s, idx, style) }

        isInternalChange = true
        try {
            val overlapping = s.getSpans(start, end, StyleSpan::class.java)
            for (span in overlapping) {
                val matches = span.style == style || span.style == Typeface.BOLD_ITALIC
                if (!matches) continue
                val spStart = s.getSpanStart(span)
                val spEnd = s.getSpanEnd(span)
                val oldStyle = span.style
                s.removeSpan(span)

                if (spStart < start) {
                    s.setSpan(StyleSpan(oldStyle), spStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (spEnd > end) {
                    s.setSpan(StyleSpan(oldStyle), end, spEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (oldStyle == Typeface.BOLD_ITALIC) {
                    val remaining = if (style == Typeface.BOLD) Typeface.ITALIC else Typeface.BOLD
                    val overlapStart = maxOf(spStart, start)
                    val overlapEnd = minOf(spEnd, end)
                    if (overlapEnd > overlapStart) {
                        s.setSpan(StyleSpan(remaining), overlapStart, overlapEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            if (!allHaveStyle) {
                s.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } finally {
            isInternalChange = false
        }

        commitManualMutation(beforeSnapshot, s)
    }

    private fun handleNewlineInsertion(s: Editable, newlinePos: Int) {
        val prevRange = EssayHtml.findParagraphRange(s, newlinePos)
        val prevSpan = EssayHtml.listSpanAtParagraph(s, prevRange) ?: return
        val prevTextEmpty = prevRange.first == newlinePos

        isInternalChange = true
        try {
            if (prevTextEmpty) {
                s.removeSpan(prevSpan)
                s.delete(newlinePos, newlinePos + 1)
                setSelection(newlinePos.coerceIn(0, s.length))
            } else {
                s.removeSpan(prevSpan)
                s.setSpan(
                    prevSpan.copy(),
                    prevRange.first,
                    newlinePos,
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE,
                )
                val nextStart = (newlinePos + 1).coerceAtMost(s.length)
                val nextRange = EssayHtml.findParagraphRange(s, nextStart)
                val flags = if (nextRange.first == nextRange.last) {
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                } else {
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                }
                s.setSpan(
                    EssayListItemSpan(listStack = prevSpan.listStack, ordinal = prevSpan.ordinal + 1, indentPx = indentPx),
                    nextRange.first,
                    nextRange.last,
                    flags,
                )
            }
            EssayHtml.recomputeListOrdinals(s, indentPx)
        } finally {
            isInternalChange = false
        }
    }

    private fun ensureListSpanCoversParagraph(s: Editable, pos: Int) {
        val range = EssayHtml.findParagraphRange(s, pos)
        val span = EssayHtml.listSpanAtParagraph(s, range) ?: return
        val spStart = s.getSpanStart(span)
        val spEnd = s.getSpanEnd(span)
        if (spStart != range.first || spEnd != range.last) {
            isInternalChange = true
            try {
                s.removeSpan(span)
                val flags = if (range.first == range.last) {
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                } else {
                    Spanned.SPAN_INCLUSIVE_EXCLUSIVE
                }
                s.setSpan(span.copy(), range.first, range.last, flags)
            } finally {
                isInternalChange = false
            }
        }
    }

    private fun applyPendingStylesIfNeeded(s: Editable, start: Int, end: Int) {
        if (!pendingBold && !pendingItalic) return
        isInternalChange = true
        try {
            if (pendingBold) {
                s.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (pendingItalic) {
                s.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } finally {
            isInternalChange = false
        }
    }

    private fun restoreSnapshot(snapshot: EssaySnapshot) {
        isInternalChange = true
        try {
            val loaded = EssayHtml.importToSpannable(snapshot.html, indentPx)
            setText(loaded)
            val selStart = snapshot.selectionStart.coerceIn(0, loaded.length)
            val selEnd = snapshot.selectionEnd.coerceIn(0, loaded.length)
            setSelection(selStart, selEnd)
            currentCommitted = snapshot
            lastEmittedHtml = snapshot.html
        } finally {
            isInternalChange = false
        }
        onCommittedHtmlChanged?.invoke(snapshot.html)
        syncPendingStylesFromSelection()
        notifyToolbarState()
    }

    private fun commitManualMutation(beforeSnapshot: EssaySnapshot, s: Editable) {
        val afterHtml = EssayHtml.export(s)
        if (afterHtml != beforeSnapshot.html) {
            pushUndo(beforeSnapshot)
            redoStack.clear()
            currentCommitted = EssaySnapshot(
                html = afterHtml,
                selectionStart = selectionStart.coerceIn(0, s.length),
                selectionEnd = selectionEnd.coerceIn(0, s.length),
            )
            if (afterHtml != lastEmittedHtml) {
                lastEmittedHtml = afterHtml
                onCommittedHtmlChanged?.invoke(afterHtml)
            }
        }
        syncPendingStylesFromSelection()
        notifyToolbarState()
    }

    private fun pushUndo(snapshot: EssaySnapshot) {
        undoStack.addLast(snapshot)
        while (undoStack.size > MAX_UNDO_HISTORY) {
            undoStack.removeFirst()
        }
    }

    private fun exportCommittedHtml(s: Editable): String {
        val compStart = BaseInputConnection.getComposingSpanStart(s)
        val compEnd = BaseInputConnection.getComposingSpanEnd(s)
        if (compStart >= 0 && compEnd > compStart && compEnd <= s.length) {
            val copy = SpannableStringBuilder(s)
            copy.delete(compStart, compEnd)
            return EssayHtml.export(copy)
        }
        return EssayHtml.export(s)
    }

    private fun overlappingParagraphs(s: Spanned, start: Int, end: Int): List<IntRange> {
        val lineBreakPositions = s.getSpans(0, s.length, EssayLineBreakSpan::class.java)
            .mapTo(HashSet()) { s.getSpanStart(it) }
        val result = ArrayList<IntRange>()
        var cursor = start
        while (true) {
            val range = EssayHtml.findParagraphRange(s, cursor)
            if (result.lastOrNull() != range) {
                result.add(range)
            }
            if (range.last >= end || range.last >= s.length) break
            var next = range.last + 1
            while (next < s.length && next in lineBreakPositions) {
                next += 1
            }
            cursor = next
        }
        return result
    }

    private fun syncPendingStylesFromSelection() {
        val s = editableText ?: return
        val selStart = selectionStart.coerceIn(0, s.length)
        val selEnd = selectionEnd.coerceIn(0, s.length)
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)
        if (start < end) {
            pendingBold = (start until end).all { hasStyleAt(s, it, Typeface.BOLD) }
            pendingItalic = (start until end).all { hasStyleAt(s, it, Typeface.ITALIC) }
        } else if (start > 0 && s[start - 1] != '\n') {
            pendingBold = hasStyleAt(s, start - 1, Typeface.BOLD)
            pendingItalic = hasStyleAt(s, start - 1, Typeface.ITALIC)
        } else if (start == 0) {
            if (s.isEmpty()) {
                return
            }
            pendingBold = false
            pendingItalic = false
        }
    }

    private fun hasStyleAt(s: Spanned, index: Int, style: Int): Boolean {
        if (index !in 0 until s.length) return false
        return s.getSpans(index, index + 1, StyleSpan::class.java).any { span ->
            val spS = s.getSpanStart(span)
            val spE = s.getSpanEnd(span)
            index in spS until spE && (span.style == style || span.style == Typeface.BOLD_ITALIC)
        }
    }

    private fun notifyToolbarState() {
        val s = editableText
        val listKind = if (s != null) {
            val range = EssayHtml.findParagraphRange(s, selectionStart.coerceIn(0, s.length))
            EssayHtml.listSpanAtParagraph(s, range)?.kind
        } else {
            null
        }
        onToolbarStateChanged?.invoke(
            EssayToolbarState(
                bold = pendingBold,
                italic = pendingItalic,
                listKind = listKind,
                canUndo = !isLockedMode && undoStack.isNotEmpty(),
                canRedo = !isLockedMode && redoStack.isNotEmpty(),
            ),
        )
    }

    companion object {
        private const val MAX_UNDO_HISTORY = 100
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EssayRichEditor(
    itemId: String,
    html: String,
    locked: Boolean,
    ready: Boolean,
    onHtmlCommitted: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = Thi360Theme.colors
    val density = LocalDensity.current
    val textSizePx = with(density) { 16.sp.toPx() }
    val shape = RoundedCornerShape(10.dp)
    val placeholder = stringResource(R.string.question_essayPlaceholder)
    val editorLabel = stringResource(R.string.question_essayEditorLabel)
    val toolbarLabel = stringResource(R.string.attempt_editor_toolbarLabel)

    if (!ready) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp)
                .clip(shape)
                .background(brand.bgSubtle)
                .border(1.dp, brand.border, shape)
                .padding(16.dp)
                .testTag(QuestionTestTags.ESSAY_LOADING),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.attempt_editor_loading),
                color = brand.textSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        }
        return
    }

    val editorRef = remember(itemId) { mutableStateOf<EssayEditText?>(null) }
    var toolbarState by remember(itemId) { mutableStateOf(EssayToolbarState()) }
    var focused by remember(itemId) { mutableStateOf(false) }
    val latestOnCommitted by rememberUpdatedState(onHtmlCommitted)
    val textArgb = brand.text.toArgb()
    val hintArgb = brand.textMuted.toArgb()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (locked) brand.bgSubtle else brand.bgSurface)
            .border(
                width = if (focused && !locked) 2.dp else 1.dp,
                color = if (focused && !locked) brand.primaryFill else brand.borderStrong,
                shape = shape,
            ),
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(brand.bgSubtle)
                .then(if (locked) Modifier.alpha(0.45f) else Modifier)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .semantics { contentDescription = toolbarLabel }
                .testTag(QuestionTestTags.ESSAY_TOOLBAR),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EssayTextFormatButton(
                label = "B",
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Normal,
                contentDescriptionText = stringResource(R.string.attempt_editor_bold),
                active = toolbarState.bold,
                enabled = !locked,
                tag = QuestionTestTags.ESSAY_BTN_BOLD,
                onClick = { editorRef.value?.toggleBold() },
            )
            EssayTextFormatButton(
                label = "I",
                fontWeight = FontWeight.SemiBold,
                fontStyle = FontStyle.Italic,
                contentDescriptionText = stringResource(R.string.attempt_editor_italic),
                active = toolbarState.italic,
                enabled = !locked,
                tag = QuestionTestTags.ESSAY_BTN_ITALIC,
                onClick = { editorRef.value?.toggleItalic() },
            )
            EssayIconFormatButton(
                icon = QuestionIcons.BulletList,
                contentDescriptionText = stringResource(R.string.attempt_editor_bulletList),
                active = toolbarState.listKind == EssayListKind.BULLET,
                enabled = !locked,
                tag = QuestionTestTags.ESSAY_BTN_BULLET,
                onClick = { editorRef.value?.toggleList(EssayListKind.BULLET) },
            )
            EssayIconFormatButton(
                icon = QuestionIcons.OrderedList,
                contentDescriptionText = stringResource(R.string.attempt_editor_orderedList),
                active = toolbarState.listKind == EssayListKind.ORDERED,
                enabled = !locked,
                tag = QuestionTestTags.ESSAY_BTN_ORDERED,
                onClick = { editorRef.value?.toggleList(EssayListKind.ORDERED) },
            )
            EssayIconFormatButton(
                icon = QuestionIcons.Undo,
                contentDescriptionText = stringResource(R.string.attempt_editor_undo),
                active = false,
                enabled = !locked && toolbarState.canUndo,
                tag = QuestionTestTags.ESSAY_BTN_UNDO,
                onClick = { editorRef.value?.performUndo() },
            )
            EssayIconFormatButton(
                icon = QuestionIcons.Redo,
                contentDescriptionText = stringResource(R.string.attempt_editor_redo),
                active = false,
                enabled = !locked && toolbarState.canRedo,
                tag = QuestionTestTags.ESSAY_BTN_REDO,
                onClick = { editorRef.value?.performRedo() },
            )
        }

        HorizontalDivider(color = brand.border, thickness = 1.dp)

        key(itemId) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag(QuestionTestTags.ESSAY_EDITOR),
                factory = { ctx ->
                    EssayEditText(ctx).apply {
                        hint = placeholder
                        contentDescription = editorLabel
                        applyTextSizePx(textSizePx)
                        setTextColor(textArgb)
                        setHintTextColor(hintArgb)
                        onFocusUpdated = { hasFocus -> focused = hasFocus }
                        onCommittedHtmlChanged = { nextHtml ->
                            latestOnCommitted(AnswerValues.stripNullChars(nextHtml))
                        }
                        onToolbarStateChanged = { state ->
                            toolbarState = state
                        }
                        syncExternalHtml(html)
                        setLocked(locked)
                        editorRef.value = this
                    }
                },
                update = { view ->
                    editorRef.value = view
                    view.applyTextSizePx(textSizePx)
                    view.hint = placeholder
                    view.contentDescription = editorLabel
                    view.setTextColor(textArgb)
                    view.setHintTextColor(hintArgb)
                    view.setLocked(locked)
                    view.syncExternalHtml(html)
                },
            )
        }
    }
}

@Composable
private fun EssayTextFormatButton(
    label: String,
    fontWeight: FontWeight,
    fontStyle: FontStyle,
    contentDescriptionText: String,
    active: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val brand = Thi360Theme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(if (active) brand.primaryContainerStrong else brand.bgSurface)
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (active) brand.primaryFill else brand.border,
                ),
                shape,
            )
            .then(if (!enabled) Modifier.alpha(0.4f) else Modifier)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier.semantics { disabled() }
                },
            )
            .semantics {
                contentDescription = contentDescriptionText
                role = Role.Button
                selected = active
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) brand.primaryText else brand.text,
            fontSize = 16.sp,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
        )
    }
}

@Composable
private fun EssayIconFormatButton(
    icon: ImageVector,
    contentDescriptionText: String,
    active: Boolean,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    val brand = Thi360Theme.colors
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .background(if (active) brand.primaryContainerStrong else brand.bgSurface)
            .border(
                BorderStroke(
                    width = 1.dp,
                    color = if (active) brand.primaryFill else brand.border,
                ),
                shape,
            )
            .then(if (!enabled) Modifier.alpha(0.4f) else Modifier)
            .then(
                if (enabled) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier.semantics { disabled() }
                },
            )
            .semantics {
                contentDescription = contentDescriptionText
                role = Role.Button
                selected = active
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) brand.primaryText else brand.text,
            modifier = Modifier.size(20.dp),
        )
    }
}
