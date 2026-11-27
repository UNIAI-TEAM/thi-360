package vn.unicom.thi360.feature.myexams

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.MyExamCard

class CardButtonsTest {

    private val entId = UUID.fromString("00000000-0000-7000-8000-000000000101")
    private val activeId = UUID.fromString("00000000-0000-7000-8000-000000000102")
    private val resultId = UUID.fromString("00000000-0000-7000-8000-000000000103")

    @Test
    fun inProgressPurchasedCardShowsContinueResultHistoryBuyMoreAndNoStart() {
        val card = MyExamsTestFixtures.makeCard(
            code = "EX-1",
            entitlementId = entId,
            state = MyExamCard.State.IN_PROGRESS,
            activeAttemptId = activeId,
            resultAttemptId = resultId,
            saleState = MyExamCard.SaleState.ON_SALE,
            hasPendingFulfillment = false,
            actions = listOf(
                MyExamCard.Actions.CONTINUE,
                MyExamCard.Actions.START,
                MyExamCard.Actions.VIEW_RESULT,
                MyExamCard.Actions.HISTORY,
                MyExamCard.Actions.BUY_MORE,
            ),
        )

        val buttons = CardButtons.of(card)

        assertEquals(activeId.toString(), buttons.continueAttemptId)
        assertNull(buttons.startEntitlementId)
        assertFalse(buttons.showRebuy)
        assertEquals(resultId.toString(), buttons.resultAttemptId)
        assertTrue(buttons.showHistory)
        assertTrue(buttons.showBuyMore)
    }

    @Test
    fun continueWithoutActiveAttemptIdFallsBackToStartWhenAvailable() {
        val card = MyExamsTestFixtures.makeCard(
            code = "EX-2",
            entitlementId = entId,
            activeAttemptId = null,
            actions = listOf(MyExamCard.Actions.CONTINUE, MyExamCard.Actions.START),
        )

        val buttons = CardButtons.of(card)

        assertNull(buttons.continueAttemptId)
        assertEquals(entId.toString(), buttons.startEntitlementId)
    }

    @Test
    fun classCardNeverShowsPurchaseButtonsEvenIfActionsContainThem() {
        val card = MyExamsTestFixtures.makeCard(
            code = "EX-CLASS",
            entitlementSource = ENTITLEMENT_SOURCE_CLASS,
            saleState = MyExamCard.SaleState.ON_SALE,
            hasPendingFulfillment = false,
            actions = listOf(
                MyExamCard.Actions.START,
                MyExamCard.Actions.REBUY,
                MyExamCard.Actions.BUY_MORE,
                MyExamCard.Actions.HISTORY,
            ),
        )

        val buttons = CardButtons.of(card)

        assertFalse(buttons.showRebuy)
        assertFalse(buttons.showBuyMore)
        assertTrue(buttons.showHistory)
    }

    @Test
    fun discontinuedUnavailableAndPendingFulfillmentHidePurchaseButtons() {
        val discontinued = MyExamsTestFixtures.makeCard(
            code = "EX-DISC",
            saleState = MyExamCard.SaleState.DISCONTINUED,
            actions = listOf(MyExamCard.Actions.REBUY, MyExamCard.Actions.BUY_MORE),
        )
        val unavailable = MyExamsTestFixtures.makeCard(
            code = "EX-UNAVAIL",
            saleState = MyExamCard.SaleState.UNAVAILABLE,
            actions = listOf(MyExamCard.Actions.REBUY, MyExamCard.Actions.BUY_MORE),
        )
        val pending = MyExamsTestFixtures.makeCard(
            code = "EX-PEND",
            saleState = MyExamCard.SaleState.ON_SALE,
            hasPendingFulfillment = true,
            pendingOrderCode = "DH009",
            actions = listOf(MyExamCard.Actions.REBUY, MyExamCard.Actions.BUY_MORE),
        )

        assertFalse(CardButtons.of(discontinued).showRebuy)
        assertFalse(CardButtons.of(discontinued).showBuyMore)
        assertFalse(CardButtons.of(unavailable).showRebuy)
        assertFalse(CardButtons.of(unavailable).showBuyMore)
        assertFalse(CardButtons.of(pending).showRebuy)
        assertFalse(CardButtons.of(pending).showBuyMore)
    }

    @Test
    fun viewResultHiddenWhenResultAttemptIdMissing() {
        val card = MyExamsTestFixtures.makeCard(
            code = "EX-NORES",
            resultAttemptId = null,
            actions = listOf(MyExamCard.Actions.VIEW_RESULT, MyExamCard.Actions.REBUY),
        )

        val buttons = CardButtons.of(card)

        assertNull(buttons.resultAttemptId)
        assertTrue(buttons.showRebuy)
    }
}
