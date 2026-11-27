package vn.unicom.thi360.feature.question

import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.feature.question.logic.SymbolInputRules
import vn.unicom.thi360.feature.question.model.AnswerFormat
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.QuestionMode
import vn.unicom.thi360.feature.question.ui.QuestionTestTags
import vn.unicom.thi360.feature.question.ui.QuestionView

class SymbolBarRuleTest {

    @Test
    fun matchesWebSymbolDisplayRulesExactly() {
        assertTrue(SymbolInputRules.shouldShowSymbolInput(AnswerFormat.CHEM_FORMULA, null))
        assertTrue(SymbolInputRules.shouldShowSymbolInput(AnswerFormat.CHEM_EQUATION, null))
        assertTrue(SymbolInputRules.shouldShowSymbolInput(AnswerFormat.TEXT, null))

        assertFalse(SymbolInputRules.shouldShowSymbolInput(AnswerFormat.INTEGER, "Viết công thức ion"))
        assertFalse(SymbolInputRules.shouldShowSymbolInput(AnswerFormat.DECIMAL, "Viết công thức ion"))
        assertFalse(SymbolInputRules.shouldShowSymbolInput(AnswerFormat.FRACTION, "Viết ký hiệu"))

        assertTrue(SymbolInputRules.shouldShowSymbolInput(null, "Viết ion đồng"))
        assertFalse(SymbolInputRules.shouldShowSymbolInput(null, "cation"))
        assertFalse(SymbolInputRules.shouldShowSymbolInput(null, "anion"))
        assertTrue(SymbolInputRules.shouldShowSymbolInput(null, "KÝ HIỆU nguyên tố"))
        assertTrue(SymbolInputRules.shouldShowSymbolInput(null, "công thức phân tử"))
        assertTrue(SymbolInputRules.shouldShowSymbolInput(null, "Chemical formula"))
        assertTrue(SymbolInputRules.shouldShowSymbolInput(null, "Reaction equation"))
        assertTrue(SymbolInputRules.shouldShowSymbolInput(null, "Enter symbol"))
        assertFalse(SymbolInputRules.shouldShowSymbolInput(null, "Số nguyên, không ghi đơn vị"))
        assertFalse(SymbolInputRules.shouldShowSymbolInput(null, null))
    }

    @Test
    fun insertTextAtCursorHandlesMiddleCursorAndSelectionReplacement() {
        val mid = SymbolInputRules.insertTextAtCursor("FeCl", 2, 2, "2+")
        assertEquals("Fe2+Cl", mid.nextValue)
        assertEquals(4, mid.nextCursor)

        val replaced = SymbolInputRules.insertTextAtCursor("H2O", 1, 2, "3")
        assertEquals("H3O", replaced.nextValue)
        assertEquals(2, replaced.nextCursor)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SymbolBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allTwelveSymbolsRenderAndInsertExactAsciiAtCursor() {
        val item = QuestionItem.ShortAnswer(
            id = "sa-chem",
            displayNo = 15,
            stem = "<p>Nhập ion</p>",
            answerFormat = AnswerFormat.CHEM_FORMULA,
        )
        var current by mutableStateOf<AnswerValue?>(ShortAnswerValue("Fe"))
        var rootView: View? = null

        composeRule.setContent {
            rootView = LocalView.current
            QuestionTestHost {
                QuestionView(
                    item = item,
                    answer = current,
                    mode = QuestionMode.Answering,
                    onAnswer = { current = it },
                )
            }
        }

        composeRule.onNodeWithTag(QuestionTestTags.SYMBOL_BAR).assertIsDisplayed()
        composeRule.onNodeWithTag(QuestionTestTags.NUMERIC_KEY_ROW).assertDoesNotExist()
        assertEquals(12, SymbolInputRules.DEFAULT_ION_SYMBOLS.size)

        val editText = findFirstEditText(rootView!!)!!
        composeRule.runOnIdle {
            editText.requestFocus()
            editText.setSelection(2)
        }
        composeRule.onNodeWithTag(QuestionTestTags.symbolKey("charge-2plus")).performClick()
        composeRule.waitForIdle()
        assertEquals(ShortAnswerValue("Fe2+"), current)

        for (sym in SymbolInputRules.DEFAULT_ION_SYMBOLS) {
            composeRule.runOnIdle {
                editText.setText("X")
                editText.setSelection(1)
            }
            composeRule.onNodeWithTag(QuestionTestTags.symbolKey(sym.id))
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .performClick()
            composeRule.waitForIdle()
            assertEquals(ShortAnswerValue("X${sym.insert}"), current)
        }
    }

    @Test
    fun decimalFormatHidesSymbolBar() {
        val item = QuestionItem.ShortAnswer(
            id = "sa-dec",
            displayNo = 16,
            stem = "<p>Nhập số thập phân</p>",
            answerFormat = AnswerFormat.DECIMAL,
            inputHint = "Viết công thức ion",
        )
        composeRule.setContent {
            QuestionTestHost {
                QuestionView(
                    item = item,
                    answer = null,
                    mode = QuestionMode.Answering,
                )
            }
        }
        composeRule.onNodeWithTag(QuestionTestTags.SYMBOL_BAR).assertDoesNotExist()
        composeRule.onNodeWithTag(QuestionTestTags.NUMERIC_KEY_ROW).assertIsDisplayed()
    }
}
