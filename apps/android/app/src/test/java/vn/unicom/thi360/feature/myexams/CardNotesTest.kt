package vn.unicom.thi360.feature.myexams

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.MyExamCard

class CardNotesTest {

    @Test
    fun classExhaustedWithAndWithoutHistorySelectsExactNote() {
        val withHistory = MyExamsTestFixtures.makeCard(
            code = "CLS-1",
            entitlementSource = ENTITLEMENT_SOURCE_CLASS,
            state = MyExamCard.State.EXHAUSTED,
            attemptsLeft = 0,
            actions = listOf(MyExamCard.Actions.HISTORY),
        )
        val withoutHistory = withHistory.copy(actions = emptyList())

        assertEquals(
            listOf(CardNote.CLASS_EXHAUSTED_WITH_HISTORY),
            CardNotes.of(withHistory).middleNotes,
        )
        assertEquals(
            listOf(CardNote.CLASS_EXHAUSTED),
            CardNotes.of(withoutHistory).middleNotes,
        )
    }

    @Test
    fun discontinuedAndUnavailablePurchasedCardsProduceExpectedNotes() {
        val unavailableCanStart = MyExamsTestFixtures.makeCard(
            code = "UNAVAIL-START",
            saleState = MyExamCard.SaleState.UNAVAILABLE,
            state = MyExamCard.State.CAN_START,
            attemptsLeft = 1,
            actions = listOf(MyExamCard.Actions.START),
        )
        val discontinuedExhaustedWithResult = MyExamsTestFixtures.makeCard(
            code = "DISC-EXH-RES",
            saleState = MyExamCard.SaleState.DISCONTINUED,
            state = MyExamCard.State.EXHAUSTED,
            attemptsLeft = 0,
            resultAttemptId = UUID.randomUUID(),
            actions = listOf(MyExamCard.Actions.VIEW_RESULT),
        )
        val discontinuedExhaustedNoHistory = discontinuedExhaustedWithResult.copy(
            resultAttemptId = null,
            actions = emptyList(),
        )

        assertEquals(
            listOf(CardNote.DISCONTINUED_CAN_START),
            CardNotes.of(unavailableCanStart).middleNotes,
        )
        assertEquals(
            listOf(CardNote.DISCONTINUED_EXHAUSTED_WITH_HISTORY),
            CardNotes.of(discontinuedExhaustedWithResult).middleNotes,
        )
        assertEquals(
            listOf(CardNote.DISCONTINUED_EXHAUSTED),
            CardNotes.of(discontinuedExhaustedNoHistory).middleNotes,
        )
    }

    @Test
    fun lastAttemptAndPendingTopUpCanCoexistOnInProgressCard() {
        val card = MyExamsTestFixtures.makeCard(
            code = "LAST-TOPUP",
            state = MyExamCard.State.IN_PROGRESS,
            attemptsLeft = 0,
            activeAttemptId = UUID.randomUUID(),
            hasPendingFulfillment = true,
            pendingOrderCode = " DH002 ",
            actions = listOf(MyExamCard.Actions.CONTINUE, MyExamCard.Actions.BUY_MORE),
        )

        val notes = CardNotes.of(card)

        assertEquals(listOf(CardNote.LAST_ATTEMPT), notes.middleNotes)
        assertEquals("DH002", notes.pendingTopUpOrderCode)
    }

    @Test
    fun onSaleCanStartCardHasNoNotes() {
        val card = MyExamsTestFixtures.makeCard(
            code = "CLEAN",
            state = MyExamCard.State.CAN_START,
            saleState = MyExamCard.SaleState.ON_SALE,
            attemptsLeft = 2,
            hasPendingFulfillment = false,
            actions = listOf(MyExamCard.Actions.START, MyExamCard.Actions.BUY_MORE),
        )

        val notes = CardNotes.of(card)

        assertTrue(notes.middleNotes.isEmpty())
        assertNull(notes.pendingTopUpOrderCode)
    }
}
