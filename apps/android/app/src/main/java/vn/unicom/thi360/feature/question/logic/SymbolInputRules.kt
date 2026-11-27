package vn.unicom.thi360.feature.question.logic

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import java.text.Normalizer
import vn.unicom.thi360.R
import vn.unicom.thi360.feature.question.model.AnswerFormat

@Immutable
enum class SymbolGroup {
    SIGN,
    CHARGE,
    SUBSCRIPT,
    ARROW,
}

@Immutable
data class SymbolKey(
    val id: String,
    val label: String,
    val insert: String,
    val group: SymbolGroup,
    @param:StringRes val nameResId: Int,
)

@Immutable
data class NumericExtraKey(
    val id: String,
    val label: String,
    val insert: String,
    @param:StringRes val nameResId: Int,
)

@Immutable
data class TextInsertResult(
    val nextValue: String,
    val nextCursor: Int,
)

object SymbolInputRules {
    val DEFAULT_ION_SYMBOLS: List<SymbolKey> = listOf(
        SymbolKey("plus", "+", "+", SymbolGroup.SIGN, R.string.question_symbolInput_symbolNames_plus),
        SymbolKey("minus", "\u2212", "-", SymbolGroup.SIGN, R.string.question_symbolInput_symbolNames_minus),
        SymbolKey("lparen", "(", "(", SymbolGroup.SIGN, R.string.question_symbolInput_symbolNames_lparen),
        SymbolKey("rparen", ")", ")", SymbolGroup.SIGN, R.string.question_symbolInput_symbolNames_rparen),
        SymbolKey("charge-2plus", "\u00b2\u207a", "2+", SymbolGroup.CHARGE, R.string.question_symbolInput_symbolNames_charge2plus),
        SymbolKey("charge-3plus", "\u00b3\u207a", "3+", SymbolGroup.CHARGE, R.string.question_symbolInput_symbolNames_charge3plus),
        SymbolKey("charge-2minus", "\u00b2\u207b", "2-", SymbolGroup.CHARGE, R.string.question_symbolInput_symbolNames_charge2minus),
        SymbolKey("charge-3minus", "\u00b3\u207b", "3-", SymbolGroup.CHARGE, R.string.question_symbolInput_symbolNames_charge3minus),
        SymbolKey("sub-2", "\u2082", "2", SymbolGroup.SUBSCRIPT, R.string.question_symbolInput_symbolNames_sub2),
        SymbolKey("sub-3", "\u2083", "3", SymbolGroup.SUBSCRIPT, R.string.question_symbolInput_symbolNames_sub3),
        SymbolKey("sub-4", "\u2084", "4", SymbolGroup.SUBSCRIPT, R.string.question_symbolInput_symbolNames_sub4),
        SymbolKey("arrow-right", "\u2192", "->", SymbolGroup.ARROW, R.string.question_symbolInput_symbolNames_arrowRight),
    )

    val NUMERIC_EXTRA_KEYS: List<NumericExtraKey> = listOf(
        NumericExtraKey("minus", "\u2212", "-", R.string.question_symbolInput_symbolNames_minus),
        NumericExtraKey("slash", "/", "/", R.string.question_numericInput_slash),
        NumericExtraKey("comma", ",", ",", R.string.question_numericInput_comma),
        NumericExtraKey("dot", ".", ".", R.string.question_numericInput_dot),
    )

    private val COMBINING_DIACRITICS_REGEX = Regex("[\\u0300-\\u036f]")
    private val SYMBOL_HINT_REGEX = Regex(
        """(?:\bion\b|cong thuc|ky hieu|ki hieu|formula|equation|symbol)""",
        RegexOption.IGNORE_CASE,
    )

    fun foldDiacritics(raw: String): String {
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD)
        return COMBINING_DIACRITICS_REGEX.replace(decomposed, "")
            .replace('\u0111', 'd')
            .replace('\u0110', 'd')
    }

    fun isNumericFormat(answerFormat: AnswerFormat?): Boolean =
        answerFormat == AnswerFormat.INTEGER ||
            answerFormat == AnswerFormat.DECIMAL ||
            answerFormat == AnswerFormat.FRACTION

    fun formatHint(answerFormat: AnswerFormat?): String? = when (answerFormat) {
        AnswerFormat.INTEGER -> "0-9"
        AnswerFormat.DECIMAL, AnswerFormat.FRACTION -> "0-9 · ."
        AnswerFormat.CHEM_FORMULA, AnswerFormat.CHEM_EQUATION, AnswerFormat.TEXT, null -> null
    }

    fun shouldShowSymbolInput(
        answerFormat: AnswerFormat?,
        inputHint: String?,
    ): Boolean {
        if (answerFormat == AnswerFormat.CHEM_FORMULA ||
            answerFormat == AnswerFormat.CHEM_EQUATION ||
            answerFormat == AnswerFormat.TEXT
        ) {
            return true
        }
        if (isNumericFormat(answerFormat)) {
            return false
        }
        if (inputHint.isNullOrBlank()) return false
        return SYMBOL_HINT_REGEX.containsMatchIn(foldDiacritics(inputHint))
    }

    fun insertTextAtCursor(
        currentValue: String,
        selectionStart: Int?,
        selectionEnd: Int?,
        insertion: String,
    ): TextInsertResult {
        val len = currentValue.length
        val rawStart = (selectionStart ?: len).coerceIn(0, len)
        val rawEnd = (selectionEnd ?: rawStart).coerceIn(0, len)
        val start = minOf(rawStart, rawEnd)
        val end = maxOf(rawStart, rawEnd)
        val nextValue = buildString(len - (end - start) + insertion.length) {
            append(currentValue, 0, start)
            append(insertion)
            append(currentValue, end, len)
        }
        return TextInsertResult(
            nextValue = nextValue,
            nextCursor = start + insertion.length,
        )
    }
}
