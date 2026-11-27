package vn.unicom.thi360.feature.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessButtonsTest {

    @Test
    fun inProgressStateMapsContinueAndBuyMoreOrPendingTopUp() {
        val noPending = ExamAccessRow(
            code = "EX-1",
            state = ExamAccessStateValue.IN_PROGRESS,
            attemptsLeft = 2,
            activeAttemptId = "att-1",
            hasPendingFulfillment = false,
        )
        val cardModel = AccessButtons.forCard(noPending, "EX-1", 3, 200_000L, isBundle = false)
        assertEquals(AccessButton.Continue("att-1"), cardModel.primary)
        assertEquals(AccessButton.BuyMore("EX-1", 3, 200_000L), cardModel.secondary)
        assertEquals(AccessNote.ContinueInProgress, cardModel.leadNote)
        assertNull(cardModel.tailNote)
        assertTrue(cardModel.showViewDetail)

        val withPending = noPending.copy(hasPendingFulfillment = true, pendingOrderCode = "DH-100")
        val pendingCard = AccessButtons.forCard(withPending, "EX-1", 3, 200_000L, isBundle = false)
        assertEquals(AccessButton.Continue("att-1"), pendingCard.primary)
        assertNull(pendingCard.secondary)
        assertEquals(AccessNote.PendingTopUp("DH-100"), pendingCard.tailNote)
    }

    @Test
    fun canStartStateMapsEnterWithAttemptsLeftAndPendingLogic() {
        val row = ExamAccessRow(
            code = "EX-2",
            state = ExamAccessStateValue.CAN_START,
            attemptsLeft = 3,
            entitlementId = "ent-2",
            hasPendingFulfillment = false,
        )
        val detailModel = AccessButtons.forPricePanel(row, "EX-2", 2, 150_000L)
        assertEquals(AccessButton.Enter("ent-2"), detailModel.primary)
        assertEquals(AccessButton.BuyMore("EX-2", 2, 150_000L), detailModel.secondary)
        assertEquals(AccessNote.AttemptsLeft(3), detailModel.leadNote)
        assertFalse(detailModel.showViewDetail)

        val pending = row.copy(hasPendingFulfillment = true, pendingOrderCode = "DH-200")
        val detailPending = AccessButtons.forPricePanel(pending, "EX-2", 2, 150_000L)
        assertNull(detailPending.secondary)
        assertEquals(AccessNote.PendingTopUp("DH-200"), detailPending.tailNote)
    }

    @Test
    fun checkingStateDisablesActionAndShowsOrderNoteWhenOrderCodePresent() {
        val withCode = ExamAccessRow(
            code = "EX-3",
            state = ExamAccessStateValue.CHECKING,
            hasPendingFulfillment = true,
            pendingOrderCode = "DH-300",
        )
        val model = AccessButtons.forCard(withCode, "EX-3", 1, 100_000L, isBundle = false)
        assertEquals(AccessButton.Checking("DH-300"), model.primary)
        assertNull(model.secondary)
        assertEquals(AccessNote.CheckingOrder("DH-300"), model.tailNote)
        assertTrue(model.showViewDetail)

        val noCode = withCode.copy(pendingOrderCode = null)
        val modelNoCode = AccessButtons.forPricePanel(noCode, "EX-3", 1, 100_000L)
        assertEquals(AccessButton.Checking(null), modelNoCode.primary)
        assertNull(modelNoCode.tailNote)
    }

    @Test
    fun exhaustedStateDiffersBetweenCardAndPricePanel() {
        val row = ExamAccessRow(code = "EX-4", state = ExamAccessStateValue.EXHAUSTED)

        val cardExam = AccessButtons.forCard(row, "EX-4", 4, 180_000L, isBundle = false)
        assertEquals(AccessButton.Buy("EX-4", 4, 180_000L, isBundle = false), cardExam.primary)
        assertNull(cardExam.leadNote)
        assertTrue(cardExam.showViewDetail)

        val cardBundle = AccessButtons.forCard(row, "BD-1", 4, 300_000L, isBundle = true)
        assertEquals(AccessButton.Buy("BD-1", 4, 300_000L, isBundle = true), cardBundle.primary)

        val pricePanel = AccessButtons.forPricePanel(row, "EX-4", 4, 180_000L)
        assertEquals(AccessButton.BuyAgain("EX-4", 4, 180_000L), pricePanel.primary)
        assertEquals(AccessNote.Exhausted, pricePanel.leadNote)
        assertFalse(pricePanel.showViewDetail)
    }

    @Test
    fun noneStateAndChunkCodesBoundaries() {
        val row = ExamAccessRow.none("EX-5")
        val card = AccessButtons.forCard(row, "EX-5", 1, 90_000L, isBundle = false)
        assertEquals(AccessButton.Buy("EX-5", 1, 90_000L, isBundle = false), card.primary)

        val codes48 = (1..48).map { "EX-$it" }
        assertEquals(listOf(48), AccessButtons.chunkCodes(codes48).map { it.size })

        val codes50 = (1..50).map { "EX-$it" }
        assertEquals(listOf(48, 2), AccessButtons.chunkCodes(codes50).map { it.size })

        val codes60 = (1..60).map { "EX-$it" }
        assertEquals(listOf(48, 12), AccessButtons.chunkCodes(codes60).map { it.size })
    }
}
