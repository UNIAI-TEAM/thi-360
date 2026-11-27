package vn.unicom.thi360.feature.attemptengine

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.FinalAnswerResult
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.storage.AccountScopedStore

@OptIn(ExperimentalCoroutinesApi::class)
class SaveResultTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-save-result-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun savedCurrentSeqDeletesDraftAndOlderSeqKeepsItemPending() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val itemId = attempt.firstLeafItem().itemId!!
        val store = AccountScopedStore(storeRoot, "account-A")
        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        val firstGate = server.gateNext(AttemptOperation.SAVE_ANSWERS)
        engine.answer(itemId, SingleChoiceAnswer("a"))
        runCurrent()
        engine.answer(itemId, SingleChoiceAnswer("b"))
        assertEquals(2L, engine.state.value.answers[itemId]?.seq)

        val secondGate = server.gateNext(AttemptOperation.SAVE_ANSWERS)
        firstGate.complete(Unit)
        runCurrent()

        assertEquals(AnswerSyncStatus.Pending, engine.state.value.answers[itemId]?.sync)
        assertEquals(2L, engine.state.value.answers[itemId]?.seq)
        assertEquals(2, server.callCount(AttemptOperation.SAVE_ANSWERS))

        secondGate.complete(Unit)
        runCurrent()

        assertEquals(AnswerSyncStatus.Saved, engine.state.value.answers[itemId]?.sync)
        assertEquals(SaveStatus.Saved, engine.state.value.save)
        assertNull("Draft must be deleted after latest seq is SAVED", DraftStore(store).readDraft(attempt.attemptId, itemId))
        engine.close()
    }

    @Test
    fun staleReloadsStateAndAdoptsServerAnswerWhenServerSeqGeLocalSeq() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val itemId = attempt.firstLeafItem().itemId!!
        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        server.setServerItemState(itemId, seq = 5L, answer = SingleChoiceAnswer("b"))
        val getsBefore = server.callCount(AttemptOperation.GET_STATE)

        engine.answer(itemId, SingleChoiceAnswer("a"))
        runCurrent()

        assertTrue("STALE must trigger GET reload", server.callCount(AttemptOperation.GET_STATE) > getsBefore)
        assertEquals(SingleChoiceAnswer("b"), engine.state.value.answers[itemId]?.value)
        assertEquals(5L, engine.state.value.answers[itemId]?.seq)
        assertEquals(AnswerSyncStatus.Saved, engine.state.value.answers[itemId]?.sync)
        engine.close()
    }

    @Test
    fun rejectedInvalidAnswerAndNotInScopeDoNotResendUntilEdited() = runTest {
        for (reason in listOf("INVALID_ANSWER", "NOT_IN_SCOPE")) {
            val server = FakeAttemptServer()
            val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
            val items = attempt.allLeafItems()
            val item1 = items[0].itemId!!
            val item2 = items[1].itemId!!
            val (engine, _) = newEngineHarness(
                server = server,
                storeRoot = File(storeRoot, reason).apply { mkdirs() },
            )
            runCurrent()

            server.saveResultMode = FakeAttemptServer.SaveResultOverrideMode.FixedResult(
                status = FinalAnswerResult.Status.REJECTED,
                reason = reason,
                targetItemId = item1,
            )
            engine.answer(item1, SingleChoiceAnswer("bad"))
            runCurrent()

            assertEquals(AnswerSyncStatus.Rejected, engine.state.value.answers[item1]?.sync)
            assertEquals(SaveStatus.Unsaved(UnsavedReason.Rejected), engine.state.value.save)
            assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))

            server.saveResultMode = FakeAttemptServer.SaveResultOverrideMode.Stateful
            engine.answer(item2, SingleChoiceAnswer("a"))
            runCurrent()
            assertEquals(2, server.callCount(AttemptOperation.SAVE_ANSWERS))
            val secondBody = server.calls(AttemptOperation.SAVE_ANSWERS)[1].body!!
            assertTrue("Rejected item1 must not be resent until edited", !secondBody.contains(item1.toString()))
            assertEquals(SaveStatus.Saved, engine.state.value.save)

            server.saveResultMode = FakeAttemptServer.SaveResultOverrideMode.FixedResult(
                status = FinalAnswerResult.Status.REJECTED,
                reason = reason,
                targetItemId = item1,
            )
            engine.answer(item1, SingleChoiceAnswer("bad2"))
            runCurrent()
            assertEquals(SaveStatus.Unsaved(UnsavedReason.Rejected), engine.state.value.save)

            server.saveResultMode = FakeAttemptServer.SaveResultOverrideMode.Stateful
            val gate = server.gateNext(AttemptOperation.SAVE_ANSWERS)
            engine.answer(item1, SingleChoiceAnswer("a"))
            assertEquals(SaveStatus.Saving, engine.state.value.save)
            gate.complete(Unit)
            runCurrent()
            assertEquals(AnswerSyncStatus.Saved, engine.state.value.answers[item1]?.sync)
            assertEquals(SaveStatus.Saved, engine.state.value.save)
            engine.close()
        }
    }

    @Test
    fun rejectedPartClosedAndTimeUpLockItemEmitEventsAndReload() = runTest {
        val partServer = FakeAttemptServer()
        val pAttempt = partServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val openPart = pAttempt.firstOpenPart()
        val item1 = pAttempt.firstLeafItem().itemId!!
        val partEvents = mutableListOf<EngineEvent>()
        val (partEngine, _) = newEngineHarness(
            server = partServer,
            storeRoot = File(storeRoot, "part-closed").apply { mkdirs() },
        )
        val c1 = backgroundScope.launch { partEngine.events.collect { partEvents.add(it) } }
        runCurrent()

        val openPartId = openPart.partId!!
        partServer.setPartState(openPartId, AttemptPartStateView.State.CLOSED)
        val getsBeforePart = partServer.callCount(AttemptOperation.GET_STATE)
        partEngine.answer(item1, SingleChoiceAnswer("a"))
        runCurrent()

        assertEquals(AnswerSyncStatus.Locked, partEngine.state.value.answers[item1]?.sync)
        assertTrue(partEvents.any { it == EngineEvent.PartClosed(openPartId) })
        assertTrue(partServer.callCount(AttemptOperation.GET_STATE) > getsBeforePart)
        c1.cancel()
        partEngine.close()

        val timeServer = FakeAttemptServer()
        val tAttempt = timeServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val tItem = tAttempt.firstLeafItem().itemId!!
        val timeEvents = mutableListOf<EngineEvent>()
        val (timeEngine, _) = newEngineHarness(
            server = timeServer,
            storeRoot = File(storeRoot, "time-up").apply { mkdirs() },
        )
        val c2 = backgroundScope.launch { timeEngine.events.collect { timeEvents.add(it) } }
        runCurrent()

        timeServer.saveResultMode = FakeAttemptServer.SaveResultOverrideMode.FixedResult(
            status = FinalAnswerResult.Status.REJECTED,
            reason = OpenApiAttemptApi.CODE_TIME_UP,
        )
        timeServer.onRequest(AttemptOperation.SAVE_ANSWERS) {
            timeServer.closeAttemptOnServer(AttemptStateResponse.State.GRADING)
        }
        timeEngine.answer(tItem, SingleChoiceAnswer("a"))
        runCurrent()

        assertEquals(AnswerSyncStatus.Locked, timeEngine.state.value.answers[tItem]?.sync)
        assertTrue(timeEvents.any { it is EngineEvent.AttemptClosed })
        assertEquals(AttemptClosedReason.TimeUp, timeEngine.state.value.closed)
        c2.cancel()
        timeEngine.close()
    }
}
