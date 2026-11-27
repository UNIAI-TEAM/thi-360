package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.storage.AccountScopedStore

@OptIn(ExperimentalCoroutinesApi::class)
class FinishPartCommandTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-finish-part-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun emitsBeforeCloseBeforeRequestCancelsTimersOmitsIdempotencyKeyAndReloads() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val sortedParts = PartSelection.sortedParts(attempt)
        val part1 = sortedParts[0]
        val part1Id = part1.partId!!
        val part1Item = PartSelection.leafItems(part1).first().itemId!!
        val store = AccountScopedStore(storeRoot, "account-A")

        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()
        val expectedSessionId = (engine.state.value.session as EditSessionState.Editing).sessionId

        val timeline = mutableListOf<String>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.events.collect { event ->
                when (event) {
                    is EngineEvent.BeforeClose -> timeline.add("BeforeClose:${event.target}")
                    is EngineEvent.PartClosed -> timeline.add("PartClosed:${event.partId}")
                    else -> Unit
                }
            }
        }
        server.onRequest(AttemptOperation.FINISH_PART) {
            timeline.add("HTTP:FINISH_PART")
        }

        server.resetRecordedCalls()
        engine.answer(part1Item, ShortAnswerValue("42"))
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))

        engine.finishPart(part1Id)
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals("Pending save debounce timer must be cancelled", 0, server.callCount(AttemptOperation.SAVE_ANSWERS))
        val finishCall = server.calls(AttemptOperation.FINISH_PART).single()
        assertEquals(expectedSessionId.toString(), finishCall.attemptSessionHeader)
        assertNull("finishPart must NOT send Idempotency-Key", finishCall.idempotencyKeyHeader)

        val finishJson = Serializer.kotlinxSerializationJson.parseToJsonElement(finishCall.body!!).jsonObject
        val finals = finishJson["finalAnswers"]!!.jsonArray
        assertEquals(1, finals.size)
        assertEquals(part1Item.toString(), finals[0].jsonObject["itemId"]!!.jsonPrimitive.content)

        val beforeCloseIdx = timeline.indexOf("BeforeClose:${CloseTarget.Part(part1Id)}")
        val httpIdx = timeline.indexOf("HTTP:FINISH_PART")
        val partClosedIdx = timeline.indexOf("PartClosed:$part1Id")
        assertTrue("BeforeClose must precede HTTP call ($timeline)", beforeCloseIdx in 0 until httpIdx)
        assertTrue("PartClosed must follow HTTP call ($timeline)", partClosedIdx > httpIdx)

        assertEquals(CommandState.Idle, engine.state.value.command)
        assertNull("Draft of saved final answer must be deleted", DraftStore(store).readDraft(attempt.attemptId, part1Item))
        assertEquals(1, server.callCount(AttemptOperation.GET_STATE))
        if (sortedParts.size > 1) {
            assertEquals(sortedParts[1].partId, engine.state.value.position?.partId)
        }

        collector.cancel()
        engine.close()
    }

    @Test
    fun finalAnswersIncludesOnlyTargetPartAndCapsAt500Vs501InQueueOrder() = runTest {
        val server = FakeAttemptServer()
        val base = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val sortedParts = PartSelection.sortedParts(base)
        val part1 = sortedParts[0]
        val part1Id = part1.partId!!
        val templateItem = PartSelection.leafItems(part1).first()

        val generatedItems = (1..501).map { idx ->
            val id = UUID.fromString("60000000-0000-4000-8000-%012d".format(idx))
            templateItem.copy(itemId = id, displayNo = idx)
        }
        val otherPartId = UUID.fromString("61000000-0000-4000-8000-000000000002")
        val otherPartItemId = UUID.fromString("61000000-0000-4000-8000-000000000999")
        val otherPart = part1.copy(
            partId = otherPartId,
            ord = (part1.ord ?: 1) + 1,
            state = AttemptPartStateView.State.OPEN,
            items = listOf(templateItem.copy(itemId = otherPartItemId, displayNo = 1)),
        )
        val attempt501 = base.copy(
            parts = listOf(
                part1.copy(items = generatedItems),
                otherPart,
            ),
        )
        server.setAttemptState(attempt501)

        val connectivity = MutableConnectivityMonitor(true)
        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            connectivity = connectivity,
        )
        runCurrent()

        connectivity.setOnline(false)
        runCurrent()
        engine.answer(otherPartItemId, SingleChoiceAnswer("b"))
        for (i in 0 until 500) {
            engine.answer(generatedItems[i].itemId!!, ShortAnswerValue("v$i"))
        }

        server.enqueueServerError(AttemptOperation.FINISH_PART, 503)
        server.resetRecordedCalls()
        engine.finishPart(part1Id)
        runCurrent()
        val call500 = server.calls(AttemptOperation.FINISH_PART).single()
        val array500 = Serializer.kotlinxSerializationJson.parseToJsonElement(call500.body!!)
            .jsonObject["finalAnswers"]!!.jsonArray
        assertEquals(500, array500.size)
        assertEquals(generatedItems[0].itemId.toString(), array500[0].jsonObject["itemId"]!!.jsonPrimitive.content)
        assertEquals(generatedItems[499].itemId.toString(), array500[499].jsonObject["itemId"]!!.jsonPrimitive.content)
        assertFalse(
            "Items from another part must not be included in finishPart finalAnswers",
            array500.any { it.jsonObject["itemId"]!!.jsonPrimitive.content == otherPartItemId.toString() },
        )

        engine.answer(generatedItems[500].itemId!!, ShortAnswerValue("v500"))
        server.resetRecordedCalls()
        engine.finishPart(part1Id)
        runCurrent()
        val call501 = server.calls(AttemptOperation.FINISH_PART).single()
        val array501 = Serializer.kotlinxSerializationJson.parseToJsonElement(call501.body!!)
            .jsonObject["finalAnswers"]!!.jsonArray
        assertEquals(500, array501.size)
        val sentIds = array501.map { it.jsonObject["itemId"]!!.jsonPrimitive.content }
        assertEquals(generatedItems[0].itemId.toString(), sentIds.first())
        assertEquals(generatedItems[499].itemId.toString(), sentIds.last())
        assertFalse(
            "501st queued item must NOT be included in finalAnswers",
            sentIds.contains(generatedItems[500].itemId.toString()),
        )
        engine.close()
    }

    @Test
    fun handles4xxRefusedEvictionAndUnconfirmedReconcileOnManualOrReconnect() = runTest {
        val errorVariants = listOf(
            "time-up" to "TIME_UP",
            "part-closed" to "PART_CLOSED",
            "attempt-part-is-last" to "ATTEMPT_PART_IS_LAST",
            "part-finish-not-allowed" to "PART_FINISH_NOT_ALLOWED",
        )
        for ((variant, expectedCode) in errorVariants) {
            val server = FakeAttemptServer()
            val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
            val part1Id = attempt.firstOpenPart().partId!!
            val (engine, _) = newEngineHarness(
                server = server,
                storeRoot = File(storeRoot, variant).apply { mkdirs() },
            )
            runCurrent()

            server.enqueueFixtureError(AttemptOperation.FINISH_PART, "finishPart", 409, variant)
            val getsBefore = server.callCount(AttemptOperation.GET_STATE)
            engine.finishPart(part1Id)
            runCurrent()

            assertTrue("$variant must reload state", server.callCount(AttemptOperation.GET_STATE) > getsBefore)
            val cmd = engine.state.value.command
            assertTrue("Expected Refused for $variant, got $cmd", cmd is CommandState.Refused)
            assertEquals(expectedCode, (cmd as CommandState.Refused).code)
            engine.close()
        }

        val evictServer = FakeAttemptServer()
        val evictAttempt = evictServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val evictPartId = evictAttempt.firstOpenPart().partId!!
        val (evictEngine, _) = newEngineHarness(
            server = evictServer,
            storeRoot = File(storeRoot, "evict").apply { mkdirs() },
        )
        runCurrent()
        val evictEvents = mutableListOf<EngineEvent>()
        val evictCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            evictEngine.events.collect { evictEvents.add(it) }
        }
        evictServer.enqueueFixtureError(AttemptOperation.FINISH_PART, "finishPart", 409, "attempt-session-revoked")
        evictEngine.finishPart(evictPartId)
        runCurrent()
        assertEquals(EditSessionState.Evicted, evictEngine.state.value.session)
        assertEquals(CommandState.Idle, evictEngine.state.value.command)
        assertTrue(evictEvents.contains(EngineEvent.SessionLost))
        evictCollector.cancel()
        evictEngine.close()

        val retryServer = FakeAttemptServer()
        val retryAttempt = retryServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val retryPartId = retryAttempt.firstOpenPart().partId!!
        val (retryEngine, _) = newEngineHarness(
            server = retryServer,
            storeRoot = File(storeRoot, "unconfirmed-open").apply { mkdirs() },
        )
        runCurrent()

        retryServer.enqueueNetworkFailure(AttemptOperation.FINISH_PART)
        retryEngine.finishPart(retryPartId)
        runCurrent()
        assertEquals(
            CommandState.Unconfirmed(UnconfirmedCommandKind.FinishPart),
            retryEngine.state.value.command,
        )

        retryEngine.reconcile()
        runCurrent()
        assertEquals(CommandState.Idle, retryEngine.state.value.command)
        retryEngine.finishPart(retryPartId)
        runCurrent()
        assertEquals(CommandState.Idle, retryEngine.state.value.command)
        assertEquals(2, retryServer.callCount(AttemptOperation.FINISH_PART))
        retryEngine.close()

        val reconServer = FakeAttemptServer()
        val reconAttempt = reconServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val reconPartId = reconAttempt.firstOpenPart().partId!!
        val reconConn = MutableConnectivityMonitor(true)
        val (reconEngine, _) = newEngineHarness(
            server = reconServer,
            storeRoot = File(storeRoot, "unconfirmed-closed").apply { mkdirs() },
            connectivity = reconConn,
        )
        runCurrent()
        val reconEvents = mutableListOf<EngineEvent>()
        val reconCollector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            reconEngine.events.collect { reconEvents.add(it) }
        }

        reconServer.enqueueServerError(AttemptOperation.FINISH_PART, 503)
        reconEngine.finishPart(reconPartId)
        runCurrent()
        assertEquals(
            CommandState.Unconfirmed(UnconfirmedCommandKind.FinishPart),
            reconEngine.state.value.command,
        )

        reconServer.setPartState(reconPartId, AttemptPartStateView.State.CLOSED)
        reconConn.setOnline(false)
        runCurrent()
        reconConn.setOnline(true)
        runCurrent()

        assertEquals(CommandState.Idle, reconEngine.state.value.command)
        assertNotNull(reconEvents.firstOrNull { it == EngineEvent.PartClosed(reconPartId) })
        reconCollector.cancel()
        reconEngine.close()
    }
}
