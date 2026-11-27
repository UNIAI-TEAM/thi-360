package vn.unicom.thi360.feature.question

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.feature.question.logic.OptionOrdering
import vn.unicom.thi360.feature.question.model.ChoiceOptionItem

class OptionOrderTest {

    @Test
    fun ordersItemsByOptionOrderAndAppendsUnlistedInOriginalOrder() {
        val options = listOf(
            ChoiceOptionItem("s1", "<p>1</p>"),
            ChoiceOptionItem("s2", "<p>2</p>"),
            ChoiceOptionItem("s3", "<p>3</p>"),
            ChoiceOptionItem("s4", "<p>4</p>"),
        )
        val ordered = OptionOrdering.orderById(options, listOf("s3", "s1", "s4", "s2")) { it.id }
        assertEquals(listOf("s3", "s1", "s4", "s2"), ordered.map { it.id })

        val partial = OptionOrdering.orderById(options, listOf("s4", "s2")) { it.id }
        assertEquals(listOf("s4", "s2", "s1", "s3"), partial.map { it.id })

        val empty = OptionOrdering.orderById(options, emptyList()) { it.id }
        assertEquals(listOf("s1", "s2", "s3", "s4"), empty.map { it.id })
    }

    @Test
    fun assignsLettersAndFormatsScoresPerLocale() {
        assertEquals("A", OptionOrdering.optionLetter(0))
        assertEquals("B", OptionOrdering.optionLetter(1))
        assertEquals("a", OptionOrdering.matchLetter(0))
        assertEquals("d", OptionOrdering.statementLetter(3))

        assertEquals("0,5", OptionOrdering.formatScore(BigDecimal("0.5"), AppLocale.VI))
        assertEquals("0,25", OptionOrdering.formatScore(BigDecimal("0.25"), AppLocale.VI))
        assertEquals("1", OptionOrdering.formatScore(BigDecimal("1.00"), AppLocale.VI))
        assertEquals("0.5", OptionOrdering.formatScore(BigDecimal("0.5"), AppLocale.EN))
        assertEquals("0.25", OptionOrdering.formatScore(BigDecimal("0.25"), AppLocale.EN))
    }
}
