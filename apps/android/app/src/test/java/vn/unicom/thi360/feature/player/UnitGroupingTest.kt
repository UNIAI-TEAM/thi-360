package vn.unicom.thi360.feature.player

import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.AttemptDisplaySegmentView
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.QuestionSet
import vn.unicom.thi360.core.network.generated.model.SingleChoice
import vn.unicom.thi360.feature.question.model.QuestionItem

class UnitGroupingTest {

    @Test
    fun splitsQuestionSetAcrossDisplaySegmentsWithPositionOffset() {
        val setId = UUID.randomUUID()
        val c1 = UUID.randomUUID()
        val c2 = UUID.randomUUID()
        val leaf3 = UUID.randomUUID()

        val part = AttemptPartStateView(
            partId = UUID.randomUUID(),
            ord = 2,
            partName = "Phần 2",
            state = AttemptPartStateView.State.OPEN,
            displaySegments = listOf(
                AttemptDisplaySegmentView(
                    code = "SEG_A",
                    name = "Đoạn A",
                    fromPosition = 1,
                    toPosition = 1,
                    commandCount = 1,
                    maxPoints = BigDecimal("2.0"),
                ),
                AttemptDisplaySegmentView(
                    code = "SEG_B",
                    name = "Đoạn B",
                    fromPosition = 2,
                    toPosition = 3,
                    commandCount = 2,
                    maxPoints = BigDecimal("4.0"),
                ),
            ),
            items = listOf(
                AttemptItemView(
                    itemId = setId,
                    displayNo = 51,
                    questionType = AttemptItemView.QuestionType.QUESTION_SET,
                    questionBody = QuestionSet(passage = "<p>Ngữ liệu chung</p>"),
                ),
                AttemptItemView(
                    itemId = c1,
                    parentItemId = setId,
                    displayNo = 51,
                    questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                    questionBody = SingleChoice(options = emptyList()),
                ),
                AttemptItemView(
                    itemId = c2,
                    parentItemId = setId,
                    displayNo = 52,
                    questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                    questionBody = SingleChoice(options = emptyList()),
                ),
                AttemptItemView(
                    itemId = leaf3,
                    displayNo = 53,
                    questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                    questionBody = SingleChoice(options = emptyList()),
                ),
            ),
        )

        val groups = UnitGrouping.buildGroups(part)
        assertEquals(2, groups.size)

        val segA = groups[0].header as UnitGroupHeader.Segment
        assertEquals("SEG_A", segA.code)
        assertEquals(51, segA.fromDisplayNo)
        assertEquals(51, segA.toDisplayNo)
        assertEquals(1, groups[0].units.size)
        val setSliceA = groups[0].units[0].questionItem as QuestionItem.QuestionSet
        assertEquals(listOf(c1.toString()), setSliceA.children.map { it.id })
        assertEquals("<p>Ngữ liệu chung</p>", setSliceA.passage)

        val segB = groups[1].header as UnitGroupHeader.Segment
        assertEquals("SEG_B", segB.code)
        assertEquals(52, segB.fromDisplayNo)
        assertEquals(53, segB.toDisplayNo)
        assertEquals(2, groups[1].units.size)
        val setSliceB = groups[1].units[0].questionItem as QuestionItem.QuestionSet
        assertEquals(listOf(c2.toString()), setSliceB.children.map { it.id })
        assertEquals("<p>Ngữ liệu chung</p>", setSliceB.passage)
        assertEquals(leaf3.toString(), groups[1].units[1].questionItem.id)
    }

    @Test
    fun groupsBySubjectWhenTwoOrMoreComponentCodesAndLeavesSingleSubjectUngrouped() {
        val multiPart = AttemptPartStateView(
            partId = UUID.randomUUID(),
            ord = 3,
            partName = "Khoa học",
            state = AttemptPartStateView.State.OPEN,
            choice = mapOf(
                "optionCode" to JsonPrimitive("SCIENCE"),
                "subjectCodes" to JsonArray(listOf(JsonPrimitive("LY"), JsonPrimitive("HOA"))),
            ),
            items = listOf(
                AttemptItemView(
                    itemId = UUID.randomUUID(),
                    displayNo = 101,
                    componentCode = "LY",
                    questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                    questionBody = SingleChoice(options = emptyList()),
                ),
                AttemptItemView(
                    itemId = UUID.randomUUID(),
                    displayNo = 102,
                    componentCode = "LY",
                    questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                    questionBody = SingleChoice(options = emptyList()),
                ),
                AttemptItemView(
                    itemId = UUID.randomUUID(),
                    displayNo = 103,
                    componentCode = "HOA",
                    questionType = AttemptItemView.QuestionType.SINGLE_CHOICE,
                    questionBody = SingleChoice(options = emptyList()),
                ),
            ),
        )

        val groups = UnitGrouping.buildGroups(multiPart)
        assertEquals(2, groups.size)
        val lyHeader = groups[0].header as UnitGroupHeader.Subject
        assertEquals("LY", lyHeader.subjectCode)
        assertEquals(101, lyHeader.fromDisplayNo)
        assertEquals(102, lyHeader.toDisplayNo)
        assertEquals(2, lyHeader.questionCount)

        val hoaHeader = groups[1].header as UnitGroupHeader.Subject
        assertEquals("HOA", hoaHeader.subjectCode)
        assertEquals(103, hoaHeader.fromDisplayNo)
        assertEquals(103, hoaHeader.toDisplayNo)
        assertEquals(1, hoaHeader.questionCount)

        val singleSubjectPart = multiPart.copy(
            choice = null,
            items = multiPart.items!!.take(2),
        )
        val ungrouped = UnitGrouping.buildGroups(singleSubjectPart)
        assertEquals(1, ungrouped.size)
        assertTrue(ungrouped[0].header is UnitGroupHeader.None)
    }

    @Test
    fun groupGridCellsDropsEmptySegmentsAndKeepsOutOfRangeBucket() {
        val part = AttemptPartStateView(
            partId = UUID.randomUUID(),
            ord = 1,
            displaySegments = listOf(
                AttemptDisplaySegmentView(code = "S1", name = "S1", fromPosition = 1, toPosition = 2),
                AttemptDisplaySegmentView(code = "S2", name = "S2", fromPosition = 3, toPosition = 4),
            ),
        )
        val allCells = listOf(
            GridCellUi(UUID.randomUUID(), 1, ItemCompletion.Answered, false, false, false, null),
            GridCellUi(UUID.randomUUID(), 3, ItemCompletion.Blank, true, false, false, null),
            GridCellUi(UUID.randomUUID(), 9, ItemCompletion.Blank, true, false, false, null),
        )
        val flaggedOnly = allCells.filter { it.flagged }
        val grouped = UnitGrouping.groupGridCells(flaggedOnly, allCells, part)

        assertEquals(2, grouped.size)
        assertEquals("S2", (grouped[0].header as UnitGroupHeader.Segment).code)
        assertTrue((grouped[1].header as UnitGroupHeader.Segment).isUngrouped)
    }
}
