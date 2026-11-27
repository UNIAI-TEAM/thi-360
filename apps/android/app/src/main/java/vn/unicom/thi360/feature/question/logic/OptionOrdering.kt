package vn.unicom.thi360.feature.question.logic

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import vn.unicom.thi360.core.preferences.AppLocale

object OptionOrdering {
    fun optionLetter(index: Int): String = ('A'.code + (index % 26)).toChar().toString()

    fun matchLetter(index: Int): String = ('a'.code + (index % 26)).toChar().toString()

    fun statementLetter(index: Int): String = matchLetter(index)

    fun <T> orderById(
        items: List<T>,
        order: List<String>,
        idSelector: (T) -> String,
    ): List<T> {
        if (order.isEmpty()) return items
        val position = HashMap<String, Int>(order.size)
        order.forEachIndexed { index, id ->
            position.putIfAbsent(id, index)
        }
        return items.withIndex()
            .sortedWith(
                compareBy<IndexedValue<T>>(
                    { position[idSelector(it.value)] == null },
                    { position[idSelector(it.value)] ?: Int.MAX_VALUE },
                    { it.index },
                ),
            )
            .map { it.value }
    }

    fun formatInteger(value: Int, appLocale: AppLocale): String {
        val formatter = NumberFormat.getIntegerInstance(appLocale.toJavaLocale())
        return formatter.format(value)
    }

    fun formatScore(value: BigDecimal, appLocale: AppLocale): String {
        val rounded = value.setScale(2, RoundingMode.HALF_UP)
        val formatter = NumberFormat.getNumberInstance(appLocale.toJavaLocale()).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
            roundingMode = RoundingMode.HALF_UP
        }
        return formatter.format(rounded.toDouble())
    }

    fun formatScore(value: Double, appLocale: AppLocale): String =
        formatScore(BigDecimal.valueOf(value), appLocale)

    fun javaLocaleFor(appLocale: AppLocale): Locale = appLocale.toJavaLocale()
}
