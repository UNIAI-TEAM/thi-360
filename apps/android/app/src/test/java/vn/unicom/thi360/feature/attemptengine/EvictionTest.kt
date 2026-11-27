package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer

@OptIn(ExperimentalCoroutinesApi::class)
class EvictionTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-eviction-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun evictionAcrossAllSixCommandsReportSessionRevokedAndTakeoverFromEvicted() = runTest {
        val triggers = listOf(
            "heartbeat_revoked",
            "heartbeat_open_elsewhere_a6",
            "save_revoked",
            "save_open_elsewhere_a6",
            "choice_revoked",
            "retry_revoked",
            "finish_revoked",
            "submit_revoked",
            "audio_report_revoked",
        )

        for (trigger in triggers) {
            val server = FakeAttemptServer()
            val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
            val openPart = attempt.firstOpenPart()
            val choicePartId = UUID.randomUUID()
            val retryPartId = UUID.randomUUID()
            server.setAttemptState(
                attempt.copy(
                    parts = attempt.parts.orEmpty() + listOf(
                        openPart.copy(partId = choicePartId, ord = 1, state = AttemptPartStateView.State.AWAITING_CHOICE, items = emptyList()),
                        openPart.copy(partId = retryPartId, ord = 2, state = AttemptPartStateView.State.GENERATION_FAILED, items = emptyList()),
                    ),
                ),
            )
            val firstItem = attempt.firstLeafItem()
            val subRoot = File(storeRoot, trigger).apply { mkdirs() }
            val events = mutableListOf<EngineEvent>()
            val (engine, _) = newEngineHarness(server = server, storeRoot = subRoot)
            val collector = backgroundScope.launch { engine.events.collect { events.add(it) } }
            runCurrent()
            assertTrue("$trigger: expected Editing", engine.state.value.session is EditSessionState.Editing)

            when (trigger) {
                "heartbeat_revoked" -> {
                    server.simulateTakeoverByOtherDevice()
                    advanceTimeBy(HEARTBEAT_INTERVAL_MS)
                }
                "heartbeat_open_elsewhere_a6" -> {
                    server.enqueueOpenElsewhereConflictA6(AttemptOperation.HEARTBEAT)
                    advanceTimeBy(HEARTBEAT_INTERVAL_MS)
                }
                "save_revoked" -> {
                    server.simulateTakeoverByOtherDevice()
                    engine.answer(firstItem.itemId!!, SingleChoiceAnswer("a"))
                }
                "save_open_elsewhere_a6" -> {
                    server.enqueueOpenElsewhereConflictA6(AttemptOperation.SAVE_ANSWERS)
                    engine.answer(firstItem.itemId!!, SingleChoiceAnswer("a"))
                }
                "choice_revoked" -> {
                    server.simulateTakeoverByOtherDevice()
                    engine.choosePart(choicePartId, "SCIENCE")
                }
                "retry_revoked" -> {
                    server.simulateTakeoverByOtherDevice()
                    engine.retryGeneration(retryPartId)
                }
                "finish_revoked" -> {
                    server.simulateTakeoverByOtherDevice()
                    engine.finishPart(openPart.partId!!)
                }
                "submit_revoked" -> {
                    server.simulateTakeoverByOtherDevice()
                    engine.submit()
                }
                "audio_report_revoked" -> {
                    server.simulateTakeoverByOtherDevice()
                    engine.reportSessionRevoked()
                }
            }
            runCurrent()

            assertEquals("$trigger: session must be Evicted", EditSessionState.Evicted, engine.state.value.session)
            assertEquals("$trigger: command must be Idle", CommandState.Idle, engine.state.value.command)
            assertEquals("$trigger: SessionLost must be emitted once", 1, events.count { it is EngineEvent.SessionLost })

            val callsAtEviction = server.calls().size
            advanceTimeBy(90_000L)
            engine.answer(firstItem.itemId!!, SingleChoiceAnswer("b"))
            runCurrent()
            assertEquals("$trigger: zero calls allowed after Evicted", callsAtEviction, server.calls().size)

            engine.takeover()
            runCurrent()
            assertTrue("$trigger: takeover from Evicted must return to Editing", engine.state.value.session is EditSessionState.Editing)

            collector.cancel()
            engine.close()
        }
    }

    @Test
    fun takeoverDetectedBefore30SecondsAndWhileSaveIsInFlight() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val firstItem = attempt.firstLeafItem()
        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        advanceTimeBy(10_000L)
        runCurrent()
        server.simulateTakeoverByOtherDevice()

        advanceTimeBy(20_000L)
        runCurrent()
        assertEquals(EditSessionState.Evicted, engine.state.value.session)
        engine.close()

        val concurrentServer = FakeAttemptServer()
        val cAttempt = concurrentServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val cItem = cAttempt.firstLeafItem()
        val (cEngine, _) = newEngineHarness(
            server = concurrentServer,
            storeRoot = File(storeRoot, "inflight").apply { mkdirs() },
        )
        runCurrent()

        val cItemId = cItem.itemId!!
        val saveGate = concurrentServer.gateNext(AttemptOperation.SAVE_ANSWERS)
        cEngine.answer(cItemId, SingleChoiceAnswer("a"))
        runCurrent()
        assertEquals(1, concurrentServer.callCount(AttemptOperation.SAVE_ANSWERS))

        concurrentServer.simulateTakeoverByOtherDevice()
        cEngine.answer(cItemId, SingleChoiceAnswer("b"))
        saveGate.complete(Unit)
        runCurrent()

        assertEquals(EditSessionState.Evicted, cEngine.state.value.session)
        assertEquals(SaveStatus.Unsaved(UnsavedReason.SessionConflict), cEngine.state.value.save)
        assertEquals(
            "Must not issue a second PUT after first PUT returned session conflict",
            1,
            concurrentServer.callCount(AttemptOperation.SAVE_ANSWERS),
        )
        cEngine.close()
    }

    @Test
    fun evictedEngineIgnoresPositionAndDeadlineExpirationWithZeroCalls() = runTest {
        val server = FakeAttemptServer()
        val baseNow = java.time.OffsetDateTime.parse("2026-11-20T10:00:00Z")
        val attempt = server.loadStateFixture("in-progress-shared") {
            it.withFutureDeadlines(serverNow = baseNow, attemptRemainingSeconds = 5L)
        }
        val openPartId = attempt.firstOpenPart().partId!!
        val itemId = attempt.firstLeafItem().itemId!!
        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = File(storeRoot, "evict-clock").apply { mkdirs() },
        )
        runCurrent()

        engine.reportSessionRevoked()
        runCurrent()
        assertEquals(EditSessionState.Evicted, engine.state.value.session)

        server.resetRecordedCalls()
        engine.position(openPartId, itemId)
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(0, server.calls().size)
        assertEquals(EditSessionState.Evicted, engine.state.value.session)
        engine.close()
    }
}
