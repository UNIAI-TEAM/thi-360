package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer

@OptIn(ExperimentalCoroutinesApi::class)
class SaveQueueBatchingTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-batching-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun emptyAnswersNeverSerializeAsNull() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadAttempt50Fixture { it.withFutureDeadlines() }
        val leafItems = attempt.allLeafItems()
        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        val emptyAnswers = listOf(
            SingleChoiceAnswer(optionId = null),
            MultipleChoiceAnswer(optionIds = emptyList()),
            ShortAnswerValue(text = ""),
            ClozeAnswer(blanks = emptyMap()),
            TrueFalseAnswer(statements = emptyMap()),
            MatchingAnswer(pairs = emptyMap()),
            EssayAnswer(html = ""),
        )

        emptyAnswers.forEachIndexed { idx, value ->
            engine.answer(leafItems[idx].itemId!!, value)
        }
        advanceTimeBy(SAVE_DEBOUNCE_ESSAY_MS)
        runCurrent()

        val saveCalls = server.calls(AttemptOperation.SAVE_ANSWERS)
        assertTrue(saveCalls.isNotEmpty())
        val sentByItem = LinkedHashMap<String, String>()
        for (call in saveCalls) {
            val body = call.body!!
            assertFalse("Payload must never contain answer: null -> $body", body.contains("\"answer\":null"))
            val obj = Serializer.kotlinxSerializationJson.parseToJsonElement(body).jsonObject
            for (el in obj["changes"]?.jsonArray.orEmpty()) {
                val changeObj = el.jsonObject
                val answerEl = changeObj["answer"]
                assertNotNull(answerEl)
                assertFalse(answerEl is JsonNull)
                val type = answerEl!!.jsonObject["type"]!!.jsonPrimitive.content
                sentByItem[changeObj["itemId"]!!.jsonPrimitive.content] = type
            }
        }
        assertEquals("SINGLE_CHOICE", sentByItem[leafItems[0].itemId!!.toString()])
        assertEquals("MULTIPLE_CHOICE", sentByItem[leafItems[1].itemId!!.toString()])
        assertEquals("SHORT_ANSWER", sentByItem[leafItems[2].itemId!!.toString()])
        assertEquals("CLOZE", sentByItem[leafItems[3].itemId!!.toString()])
        assertEquals("TRUE_FALSE", sentByItem[leafItems[4].itemId!!.toString()])
        assertEquals("MATCHING", sentByItem[leafItems[5].itemId!!.toString()])
        assertEquals("ESSAY", sentByItem[leafItems[6].itemId!!.toString()])
        engine.close()
    }

    @Test
    fun batchesAt50And51ChangesAndFlagsWithPositionOnlyOnLastBatch() = runTest {
        val server = FakeAttemptServer()
        val base50 = server.loadAttempt50Fixture { it.withFutureDeadlines() }
        val openPart = base50.firstOpenPart()
        val openPartId = openPart.partId!!
        val extraItemId = UUID.fromString("40000000-0000-4000-8000-000000000099")
        val extraItem = openPart.items!!.first().copy(itemId = extraItemId, displayNo = 51)
        val attempt51 = base50.copy(
            parts = base50.parts!!.map { part ->
                if (part.partId == openPartId) {
                    part.copy(items = part.items.orEmpty() + extraItem)
                } else {
                    part
                }
            },
        )
        server.setAttemptState(attempt51)
        val leafItems = attempt51.allLeafItems()
        assertEquals(51, leafItems.mapNotNull { it.itemId }.distinct().size)

        val connectivity = MutableConnectivityMonitor(true)
        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            connectivity = connectivity,
        )
        runCurrent()

        connectivity.setOnline(false)
        runCurrent()
        for (i in 0 until 50) {
            engine.answer(leafItems[i].itemId!!, SingleChoiceAnswer("a"))
        }
        engine.position(openPartId, leafItems[49].itemId!!)
        server.resetRecordedCalls()

        connectivity.setOnline(true)
        runCurrent()

        val calls50 = server.calls(AttemptOperation.SAVE_ANSWERS)
        assertEquals(1, calls50.size)
        val batch50Json = Serializer.kotlinxSerializationJson.parseToJsonElement(calls50[0].body!!).jsonObject
        assertEquals(50, batch50Json["changes"]!!.jsonArray.size)
        assertNotNull(batch50Json["position"])

        connectivity.setOnline(false)
        runCurrent()
        for (i in 0 until 51) {
            val id = leafItems[i].itemId!!
            val currentFlagged = engine.state.value.answers[id]?.flagged ?: false
            engine.answer(id, SingleChoiceAnswer("b"))
            engine.flag(id, !currentFlagged)
        }
        engine.position(openPartId, leafItems[50].itemId!!)
        server.resetRecordedCalls()

        connectivity.setOnline(true)
        runCurrent()

        val calls51 = server.calls(AttemptOperation.SAVE_ANSWERS)
        assertEquals(2, calls51.size)
        assertEquals(1, server.maxConcurrentSaveCalls())

        val firstBatch = Serializer.kotlinxSerializationJson.parseToJsonElement(calls51[0].body!!).jsonObject
        val secondBatch = Serializer.kotlinxSerializationJson.parseToJsonElement(calls51[1].body!!).jsonObject
        assertEquals(50, firstBatch["changes"]!!.jsonArray.size)
        assertEquals(50, firstBatch["flags"]!!.jsonArray.size)
        assertTrue(firstBatch["position"] == null || firstBatch["position"] is JsonNull)

        assertEquals(1, secondBatch["changes"]!!.jsonArray.size)
        assertEquals(1, secondBatch["flags"]!!.jsonArray.size)
        assertNotNull(secondBatch["position"])
        assertEquals(
            leafItems[50].itemId!!.toString(),
            secondBatch["position"]!!.jsonObject["itemId"]!!.jsonPrimitive.content,
        )
        engine.close()
    }

    @Test
    fun thirtyRapidSelectionsOverTenSecondsNeverOverlapPutsAndCoalesceToLatestSeq() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadAttempt50Fixture { it.withFutureDeadlines() }
        val targetItem = attempt.firstLeafItem().itemId!!
        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        server.setEndpointDelay(AttemptOperation.SAVE_ANSWERS, 600L)
        server.resetRecordedCalls()

        val options = listOf("a", "b", "c", "d")
        for (i in 1..30) {
            engine.answer(targetItem, SingleChoiceAnswer(options[i % options.size]))
            advanceTimeBy(333L)
            runCurrent()
        }
        advanceTimeBy(5_000L)
        runCurrent()

        assertEquals("Never allow overlapping PUT .../answers", 1, server.maxConcurrentSaveCalls())
        val saveCalls = server.calls(AttemptOperation.SAVE_ANSWERS)
        assertTrue("Coalescing must produce far fewer calls than 30 (got ${saveCalls.size})", saveCalls.size < 30)

        val finalState = server.currentAttemptState()
        val serverItem = finalState.allLeafItems().first { it.itemId == targetItem }
        assertEquals(30L, serverItem.answerSeq)
        assertEquals(SingleChoiceAnswer(options[30 % options.size]), serverItem.answer)
        assertEquals(SaveStatus.Saved, engine.state.value.save)
        engine.close()
    }
}
