package vn.unicom.thi360.feature.attemptengine

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.storage.AccountScopedStore

@OptIn(ExperimentalCoroutinesApi::class)
class DraftReconcileTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-reconcile-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun higherDraftSeqWinsEqualOrLowerServerWinsFlagsDiffSentAndClosedPartDropsDrafts() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val openPart = attempt.firstOpenPart()
        val openItems = PartSelection.leafItems(openPart)
        val itemHigher = openItems[0].itemId!!
        val itemEqual = openItems[1].itemId!!
        val itemLower = openItems[2].itemId!!
        val itemFlagDiff = openItems[3].itemId!!
        val itemFlagSame = openItems[4].itemId!!

        server.setServerItemState(itemHigher, seq = 2L, answer = SingleChoiceAnswer("a"), flagged = false)
        server.setServerItemState(itemEqual, seq = 3L, answer = SingleChoiceAnswer("a"), flagged = false)
        server.setServerItemState(itemLower, seq = 5L, answer = SingleChoiceAnswer("b"), flagged = false)
        server.setServerItemState(itemFlagDiff, seq = 1L, answer = SingleChoiceAnswer("a"), flagged = false)
        server.setServerItemState(itemFlagSame, seq = 1L, answer = SingleChoiceAnswer("a"), flagged = true)

        val store = AccountScopedStore(storeRoot, "account-A")
        val draftStore = DraftStore(store)
        draftStore.writeDraft(attempt.attemptId, itemHigher, DraftRecord(seq = 4L, answer = SingleChoiceAnswer("b"), flagged = false))
        draftStore.writeDraft(attempt.attemptId, itemEqual, DraftRecord(seq = 3L, answer = SingleChoiceAnswer("b"), flagged = false))
        draftStore.writeDraft(attempt.attemptId, itemLower, DraftRecord(seq = 2L, answer = SingleChoiceAnswer("a"), flagged = false))
        draftStore.writeDraft(attempt.attemptId, itemFlagDiff, DraftRecord(seq = 1L, answer = SingleChoiceAnswer("a"), flagged = true, dirtyFlag = true))
        draftStore.writeDraft(attempt.attemptId, itemFlagSame, DraftRecord(seq = 1L, answer = SingleChoiceAnswer("a"), flagged = true, dirtyFlag = true))

        val claimGate = server.gateNext(AttemptOperation.CLAIM_SESSION)
        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        assertEquals(SingleChoiceAnswer("b"), engine.state.value.answers[itemHigher]?.value)
        assertEquals(4L, engine.state.value.answers[itemHigher]?.seq)
        assertEquals(AnswerSyncStatus.Pending, engine.state.value.answers[itemHigher]?.sync)

        assertEquals(SingleChoiceAnswer("a"), engine.state.value.answers[itemEqual]?.value)
        assertEquals(AnswerSyncStatus.Saved, engine.state.value.answers[itemEqual]?.sync)
        assertNull(draftStore.readDraft(attempt.attemptId, itemEqual))

        assertEquals(SingleChoiceAnswer("b"), engine.state.value.answers[itemLower]?.value)
        assertEquals(5L, engine.state.value.answers[itemLower]?.seq)
        assertEquals(AnswerSyncStatus.Saved, engine.state.value.answers[itemLower]?.sync)
        assertNull(draftStore.readDraft(attempt.attemptId, itemLower))

        assertEquals(true, engine.state.value.answers[itemFlagDiff]?.flagged)
        assertEquals(AnswerSyncStatus.Pending, engine.state.value.answers[itemFlagDiff]?.sync)
        assertEquals(true, engine.state.value.answers[itemFlagSame]?.flagged)
        assertEquals(AnswerSyncStatus.Saved, engine.state.value.answers[itemFlagSame]?.sync)
        assertNull(draftStore.readDraft(attempt.attemptId, itemFlagSame))

        claimGate.complete(Unit)
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        val body = server.calls(AttemptOperation.SAVE_ANSWERS).single().body!!
        assertTrue(body.contains(itemHigher.toString()))
        assertTrue(body.contains(itemFlagDiff.toString()))
        assertTrue(!body.contains(itemEqual.toString()))
        assertTrue(!body.contains(itemLower.toString()))
        assertTrue(!body.contains(itemFlagSame.toString()))
        assertEquals(SaveStatus.Saved, engine.state.value.save)
        engine.close()

        val offlineServer = FakeAttemptServer()
        val oAttempt = offlineServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val oPart = oAttempt.firstOpenPart()
        val oItem = oAttempt.firstLeafItem().itemId!!
        val conn = MutableConnectivityMonitor(true)
        val oRoot = File(storeRoot, "closed-offline").apply { mkdirs() }
        val oStore = AccountScopedStore(oRoot, "account-A")
        val (oEngine, _) = newEngineHarness(
            server = offlineServer,
            storeRoot = oRoot,
            connectivity = conn,
        )
        runCurrent()

        conn.setOnline(false)
        runCurrent()
        oEngine.answer(oItem, SingleChoiceAnswer("b"))
        assertEquals(AnswerSyncStatus.Pending, oEngine.state.value.answers[oItem]?.sync)

        offlineServer.setServerItemState(oItem, seq = 1L, answer = SingleChoiceAnswer("a"))
        offlineServer.setPartState(oPart.partId!!, AttemptPartStateView.State.CLOSED)
        offlineServer.resetRecordedCalls()

        conn.setOnline(true)
        runCurrent()

        assertEquals(SingleChoiceAnswer("a"), oEngine.state.value.answers[oItem]?.value)
        assertEquals(AnswerSyncStatus.Locked, oEngine.state.value.answers[oItem]?.sync)
        assertNull(DraftStore(oStore).readDraft(oAttempt.attemptId, oItem))
        assertEquals(0, offlineServer.callCount(AttemptOperation.SAVE_ANSWERS))
        oEngine.close()
    }
}
