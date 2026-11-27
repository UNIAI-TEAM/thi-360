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
class SubmitCommandTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-submit-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun emitsBeforeCloseSingleFlightRetainsIdempotencyKeyAndCleansLocalData() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val item1 = attempt.allLeafItems()[0].itemId!!
        val store = AccountScopedStore(storeRoot, "account-A")
        store.writeString(
            AttemptStorageKeys.NAMESPACE,
            AttemptStorageKeys.startContextKey(attempt.attemptId),
            """{"entitlementId":"ent-1"}""",
        )

        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()
        val expectedSessionId = (engine.state.value.session as EditSessionState.Editing).sessionId

        val timeline = mutableListOf<String>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.events.collect { event ->
                when (event) {
                    is EngineEvent.BeforeClose -> timeline.add("BeforeClose:${event.target}")
                    is EngineEvent.AttemptClosed -> timeline.add("AttemptClosed:${event.reason}")
                    else -> Unit
                }
            }
        }
        server.onRequest(AttemptOperation.SUBMIT) {
            timeline.add("HTTP:SUBMIT")
        }

        server.resetRecordedCalls()
        engine.answer(item1, ShortAnswerValue("final-answer"))
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))

        server.enqueueServerError(AttemptOperation.SUBMIT, 503)
        engine.submit()
        runCurrent()
        assertEquals(CommandState.Unconfirmed(UnconfirmedCommandKind.Submit), engine.state.value.command)
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))

        engine.reconcile()
        runCurrent()
        assertEquals(CommandState.Idle, engine.state.value.command)

        val submitGate = server.gateNext(AttemptOperation.SUBMIT)
        engine.submit()
        runCurrent()
        assertEquals(CommandState.Sending(AttemptCommandKind.Submit), engine.state.value.command)
        engine.submit()
        runCurrent()
        assertEquals("Duplicate submit while in-flight must not send extra call", 2, server.callCount(AttemptOperation.SUBMIT))

        submitGate.complete(Unit)
        runCurrent()
        advanceTimeBy(2_000L)
        runCurrent()

        val submitCalls = server.calls(AttemptOperation.SUBMIT)
        assertEquals(2, submitCalls.size)
        assertEquals(expectedSessionId.toString(), submitCalls[0].attemptSessionHeader)
        assertEquals(expectedSessionId.toString(), submitCalls[1].attemptSessionHeader)
        assertNotNull(submitCalls[0].idempotencyKeyHeader)
        assertEquals(
            "Submit retry after 5xx must keep the exact same Idempotency-Key",
            submitCalls[0].idempotencyKeyHeader,
            submitCalls[1].idempotencyKeyHeader,
        )

        val beforeCloseIdx = timeline.indexOf("BeforeClose:${CloseTarget.Attempt}")
        val httpIdx = timeline.indexOf("HTTP:SUBMIT")
        val closedIdx = timeline.indexOf("AttemptClosed:${AttemptClosedReason.Submitted}")
        assertTrue("BeforeClose must precede HTTP submit ($timeline)", beforeCloseIdx in 0 until httpIdx)
        assertTrue("AttemptClosed must follow HTTP submit ($timeline)", closedIdx > httpIdx)

        assertEquals(AttemptClosedReason.Submitted, engine.state.value.closed)
        assertEquals(CommandState.Idle, engine.state.value.command)
        assertNull("Draft must be cleared after submit", DraftStore(store).readDraft(attempt.attemptId, item1))
        assertNull("Session must be cleared after submit", DraftStore(store).readSessionId(attempt.attemptId))
        assertNull("Position must be cleared after submit", DraftStore(store).readPosition(attempt.attemptId))
        assertNull(
            "startContext must be cleared after submit",
            store.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.startContextKey(attempt.attemptId)),
        )

        collector.cancel()
        engine.close()
    }

    @Test
    fun finalAnswersCoversAllOpenPartsCappedAt500() = runTest {
        val server = FakeAttemptServer()
        val base = server.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val openPart = base.firstOpenPart()
        val templateItem = PartSelection.leafItems(openPart).first()

        val part1Items = (1..300).map { idx ->
            templateItem.copy(
                itemId = UUID.fromString("70000000-0000-4000-8000-%012d".format(idx)),
                displayNo = idx,
            )
        }
        val part2Items = (301..501).map { idx ->
            templateItem.copy(
                itemId = UUID.fromString("70000000-0000-4000-8000-%012d".format(idx)),
                displayNo = idx,
            )
        }
        val part2 = openPart.copy(
            partId = UUID.fromString("71000000-0000-4000-8000-000000000002"),
            ord = (openPart.ord ?: 1) + 1,
            state = AttemptPartStateView.State.OPEN,
            items = part2Items,
        )
        server.setAttemptState(
            base.copy(parts = listOf(openPart.copy(items = part1Items), part2)),
        )

        val connectivity = MutableConnectivityMonitor(true)
        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot, connectivity = connectivity)
        runCurrent()

        connectivity.setOnline(false)
        runCurrent()
        val allItems = part1Items + part2Items
        for (item in allItems) {
            engine.answer(item.itemId!!, SingleChoiceAnswer("a"))
        }
        server.resetRecordedCalls()
        engine.submit()
        runCurrent()

        val submitBody = server.calls(AttemptOperation.SUBMIT).single().body!!
        val finals = Serializer.kotlinxSerializationJson.parseToJsonElement(submitBody)
            .jsonObject["finalAnswers"]!!.jsonArray
        assertEquals(500, finals.size)
        val sentIds = finals.map { it.jsonObject["itemId"]!!.jsonPrimitive.content }
        assertTrue(sentIds.contains(part1Items.first().itemId.toString()))
        assertTrue(sentIds.contains(part2Items.first().itemId.toString()))
        assertFalse("501st item must not be sent", sentIds.contains(allItems[500].itemId.toString()))
        engine.close()
    }

    @Test
    fun handles409ConflictsEvictionAndProcessDeathMidSubmit() = runTest {
        val partNotLastServer = FakeAttemptServer()
        partNotLastServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val (partNotLastEngine, _) = newEngineHarness(
            server = partNotLastServer,
            storeRoot = File(storeRoot, "part-not-last").apply { mkdirs() },
        )
        runCurrent()
        partNotLastServer.enqueueFixtureError(AttemptOperation.SUBMIT, "submit", 409, "part-not-last")
        val getsBefore = partNotLastServer.callCount(AttemptOperation.GET_STATE)
        partNotLastEngine.submit()
        runCurrent()
        assertTrue(partNotLastServer.callCount(AttemptOperation.GET_STATE) > getsBefore)
        assertEquals(CommandState.Refused("PART_NOT_LAST"), partNotLastEngine.state.value.command)
        partNotLastEngine.close()

        val timeUpServer = FakeAttemptServer()
        timeUpServer.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val (timeUpEngine, _) = newEngineHarness(
            server = timeUpServer,
            storeRoot = File(storeRoot, "time-up-a6").apply { mkdirs() },
        )
        runCurrent()
        timeUpServer.enqueueSubmit409TimeUpA6()
        timeUpServer.onRequest(AttemptOperation.SUBMIT) { timeUpServer.closeAttemptOnServer() }
        timeUpEngine.submit()
        runCurrent()
        assertEquals(CommandState.Refused(OpenApiAttemptApi.CODE_TIME_UP), timeUpEngine.state.value.command)
        assertEquals(AttemptClosedReason.TimeUp, timeUpEngine.state.value.closed)
        timeUpEngine.close()

        val evictServer = FakeAttemptServer()
        evictServer.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val (evictEngine, _) = newEngineHarness(
            server = evictServer,
            storeRoot = File(storeRoot, "evict").apply { mkdirs() },
        )
        runCurrent()
        evictServer.enqueueFixtureError(AttemptOperation.SUBMIT, "submit", 409, "attempt-session-revoked")
        evictEngine.submit()
        runCurrent()
        assertEquals(EditSessionState.Evicted, evictEngine.state.value.session)
        evictEngine.close()

        val deathSubmittedRoot = File(storeRoot, "death-submitted").apply { mkdirs() }
        val deathServerA = FakeAttemptServer()
        val attemptA = deathServerA.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val itemA = attemptA.firstLeafItem().itemId!!
        val (engineA1, _) = newEngineHarness(server = deathServerA, storeRoot = deathSubmittedRoot)
        runCurrent()
        engineA1.answer(itemA, ShortAnswerValue("before-death"))
        val gateA = deathServerA.gateNext(AttemptOperation.SUBMIT)
        engineA1.submit()
        runCurrent()
        deathServerA.closeAttemptOnServer()
        engineA1.close()
        gateA.complete(Unit)

        val (engineA2, _) = newEngineHarness(server = deathServerA, storeRoot = deathSubmittedRoot)
        runCurrent()
        assertNotNull("Reopen after server accepted submit must show closed", engineA2.state.value.closed)
        assertNull(
            "Reopen of submitted attempt must clean remaining draft",
            DraftStore(AccountScopedStore(deathSubmittedRoot, "account-A")).readDraft(attemptA.attemptId, itemA),
        )
        engineA2.close()

        val deathOpenRoot = File(storeRoot, "death-open").apply { mkdirs() }
        val deathServerB = FakeAttemptServer()
        val attemptB = deathServerB.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val itemB = attemptB.firstLeafItem().itemId!!
        val (engineB1, _) = newEngineHarness(server = deathServerB, storeRoot = deathOpenRoot)
        runCurrent()
        engineB1.answer(itemB, ShortAnswerValue("survives-death"))
        val gateB = deathServerB.gateNext(AttemptOperation.SUBMIT)
        engineB1.submit()
        runCurrent()
        engineB1.close()
        gateB.complete(Unit)

        val (engineB2, _) = newEngineHarness(server = deathServerB, storeRoot = deathOpenRoot)
        runCurrent()
        assertNull(engineB2.state.value.closed)
        assertTrue(engineB2.state.value.session is EditSessionState.Editing)
        assertEquals(ShortAnswerValue("survives-death"), engineB2.state.value.answers[itemB]?.value)
        assertEquals(SaveStatus.Saved, engineB2.state.value.save)
        engineB2.close()
    }
}
