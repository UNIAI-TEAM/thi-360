package vn.unicom.thi360.feature.attemptengine

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.core.storage.AccountScopedStore

@OptIn(ExperimentalCoroutinesApi::class)
class SaveQueueTimingTest {

    private lateinit var storeRoot: File
    private lateinit var server: FakeAttemptServer

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-save-timing-")
        server = FakeAttemptServer()
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun immediateOptimisticStateDraftBeforeCallAndTypeSchedules() = runTest {
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val items = attempt.allLeafItems()
        val item1 = items[0].itemId!!
        val item2 = items[1].itemId!!
        val item3 = items[2].itemId!!
        val item4 = items[3].itemId!!
        val item5 = items[4].itemId!!
        val store = AccountScopedStore(storeRoot, "account-A")

        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        server.setEndpointDelay(AttemptOperation.SAVE_ANSWERS, 3_000L)
        var draftPresentBeforeRequestCompleted = false
        server.onRequest(AttemptOperation.SAVE_ANSWERS) {
            draftPresentBeforeRequestCompleted = DraftStore(store).readDraft(attempt.attemptId, item1) != null
        }

        engine.answer(item1, SingleChoiceAnswer("a"))
        assertEquals(SingleChoiceAnswer("a"), engine.state.value.answers[item1]?.value)
        assertEquals(1L, engine.state.value.answers[item1]?.seq)
        assertEquals(AnswerSyncStatus.Pending, engine.state.value.answers[item1]?.sync)
        assertEquals(SaveStatus.Saving, engine.state.value.save)
        val persistedDraft = DraftStore(store).readDraft(attempt.attemptId, item1)
        assertNotNull(persistedDraft)
        assertEquals(1L, persistedDraft!!.seq)
        assertEquals(SingleChoiceAnswer("a"), persistedDraft.answer)

        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertTrue(draftPresentBeforeRequestCompleted)
        advanceTimeBy(2_999L)
        runCurrent()
        assertEquals(SingleChoiceAnswer("a"), engine.state.value.answers[item1]?.value)
        assertEquals(SaveStatus.Saving, engine.state.value.save)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(AnswerSyncStatus.Saved, engine.state.value.answers[item1]?.sync)
        assertEquals(SaveStatus.Saved, engine.state.value.save)

        server.setEndpointDelay(AttemptOperation.SAVE_ANSWERS, 0L)
        server.resetRecordedCalls()
        engine.answer(item2, MultipleChoiceAnswer(listOf("a", "b")))
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))

        engine.answer(item3, TrueFalseAnswer(mapOf("s1" to true)))
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.SAVE_ANSWERS))

        engine.answer(item4, MatchingAnswer(mapOf("l1" to "r1")))
        runCurrent()
        assertEquals(3, server.callCount(AttemptOperation.SAVE_ANSWERS))

        server.resetRecordedCalls()
        engine.answer(item1, ShortAnswerValue("1"))
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))
        engine.answer(item1, ShortAnswerValue("12"))
        advanceTimeBy(799L)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))

        server.resetRecordedCalls()
        engine.answer(item2, ClozeAnswer(mapOf("b1" to "x")))
        advanceTimeBy(799L)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))

        server.resetRecordedCalls()
        engine.answer(item5, EssayAnswer(html = "<p>a</p>"))
        advanceTimeBy(1_999L)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))

        server.resetRecordedCalls()
        engine.answer(item5, EssayAnswer(html = "<p>t0</p>"))
        for (sec in 1..14) {
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals("Must not flush before 15s while typing continuously (sec=$sec)", 0, server.callCount(AttemptOperation.SAVE_ANSWERS))
            engine.answer(item5, EssayAnswer(html = "<p>t$sec</p>"))
        }
        advanceTimeBy(999L)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))
        advanceTimeBy(1L)
        runCurrent()
        assertEquals("Essay must flush at 15s max-wait ceiling", 1, server.callCount(AttemptOperation.SAVE_ANSWERS))

        server.resetRecordedCalls()
        engine.flag(item1, true)
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        val flagBody = Serializer.kotlinxSerializationJson.parseToJsonElement(
            server.calls(AttemptOperation.SAVE_ANSWERS).single().body!!,
        ).jsonObject
        val flagsArray = flagBody["flags"]!!.jsonArray
        assertEquals(1, flagsArray.size)
        assertEquals(item1.toString(), flagsArray[0].jsonObject["itemId"]?.jsonPrimitive?.content)
        assertEquals("true", flagsArray[0].jsonObject["flagged"]?.jsonPrimitive?.content)

        server.resetRecordedCalls()
        engine.flag(item1, false)
        engine.flag(item1, true)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SaveStatus.Saved, engine.state.value.save)

        engine.close()
    }
}
