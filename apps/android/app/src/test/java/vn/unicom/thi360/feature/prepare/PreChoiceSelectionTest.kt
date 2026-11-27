package vn.unicom.thi360.feature.prepare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.ChoiceOptionView
import vn.unicom.thi360.core.network.generated.model.ChoiceSubjectView
import vn.unicom.thi360.core.network.generated.model.PreChoiceView

class PreChoiceSelectionTest {

    @Test
    fun selectsMatchingPreselectedCodeOnInitialOpen() {
        val response = PrepareTestFixtures.prepareWithSubjectChoice(preselectedCode = "ANH")
        val initial = PreChoiceSelection.initial(response.preChoice)
        assertEquals("ANH", initial.optionCode)
        assertEquals(emptySet<String>(), initial.subjectCodes)
    }

    @Test
    fun autoSelectsSingleOptionWhenPreselectedCodeAbsentOrUnmatched() {
        val response = PrepareTestFixtures.loadPrepare("pre-choice")
        val singleNoPreselect = response.preChoice!!.copy(preselectedCode = "UNKNOWN")
        val initial = PreChoiceSelection.initial(singleNoPreselect)
        assertEquals("CS", initial.optionCode)
    }

    @Test
    fun leavesSelectionEmptyWhenMultipleOptionsHaveNoPreselectedCode() {
        val response = PrepareTestFixtures.prepareWithSubjectChoice(preselectedCode = null)
        val initial = PreChoiceSelection.initial(response.preChoice)
        assertNull(initial.optionCode)
        assertEquals(StartGate.NeedOption, resolveStartGate(response, initial))
    }

    @Test
    fun handlesZeroOptionsAsNoServingOption() {
        val response = PrepareTestFixtures.prepareWithZeroPreChoiceOptions()
        val initial = PreChoiceSelection.initial(response.preChoice)
        assertNull(initial.optionCode)
        assertEquals(StartGate.NoServingOption, resolveStartGate(response, initial))
    }

    @Test
    fun locksSubjectSelectionAtLimitAndUnlocksOnUncheck() {
        val response = PrepareTestFixtures.prepareWithSubjectChoice(preselectedCode = "SCIENCE")
        var selection = PreChoiceSelection.initial(response.preChoice)
        assertEquals(StartGate.NeedSubjects(3), resolveStartGate(response, selection))

        selection = selection.toggleSubject("LY", maxSelect = 3)
        selection = selection.toggleSubject("HOA", maxSelect = 3)
        assertEquals(StartGate.NeedSubjects(3), resolveStartGate(response, selection))

        selection = selection.toggleSubject("SINH", maxSelect = 3)
        assertEquals(setOf("LY", "HOA", "SINH"), selection.subjectCodes)
        assertEquals(StartGate.Open, resolveStartGate(response, selection))

        val blocked = selection.toggleSubject("SU", maxSelect = 3)
        assertEquals(setOf("LY", "HOA", "SINH"), blocked.subjectCodes)

        val unchecked = blocked.toggleSubject("HOA", maxSelect = 3)
        assertEquals(setOf("LY", "SINH"), unchecked.subjectCodes)
        assertEquals(StartGate.NeedSubjects(3), resolveStartGate(response, unchecked))

        val repicked = unchecked.toggleSubject("SU", maxSelect = 3)
        assertEquals(setOf("LY", "SINH", "SU"), repicked.subjectCodes)
        assertEquals(StartGate.Open, resolveStartGate(response, repicked))
    }

    @Test
    fun switchingOptionClearsSubjectsAndOmitsSubjectCodesForNonSubjectChoice() {
        val response = PrepareTestFixtures.prepareWithSubjectChoice(preselectedCode = "SCIENCE")
        val picked = PreChoiceSelection(optionCode = "SCIENCE", subjectCodes = linkedSetOf("LY", "HOA", "SINH"))
        val sciencePayload = picked.toPreChoiceInput(response)!!
        assertEquals("SCIENCE", sciencePayload.optionCode)
        assertEquals(listOf("LY", "HOA", "SINH"), sciencePayload.subjectCodes)

        val switched = picked.selectOption("ANH")
        assertEquals("ANH", switched.optionCode)
        assertEquals(emptySet<String>(), switched.subjectCodes)
        assertEquals(StartGate.Open, resolveStartGate(response, switched))

        val anhPayload = switched.toPreChoiceInput(response)!!
        assertEquals("ANH", anhPayload.optionCode)
        assertNull(anhPayload.subjectCodes)
    }

    @Test
    fun ignoresChooseSubjectsWhenKindIsNotSubjectChoice() {
        val option = ChoiceOptionView(
            code = "BRANCH_A",
            name = "Nhánh A",
            kind = "BRANCH",
            chooseSubjects = 2,
            subjects = listOf(ChoiceSubjectView(code = "TOAN", nameVi = "Toán")),
        )
        val preChoice = PreChoiceView(partCode = "P1", kind = "BRANCH", options = listOf(option))
        assertEquals(0, PreChoiceSelection.requiredSubjectCount(option))
        assertEquals("BRANCH_A", PreChoiceSelection.initial(preChoice).optionCode)
    }
}
