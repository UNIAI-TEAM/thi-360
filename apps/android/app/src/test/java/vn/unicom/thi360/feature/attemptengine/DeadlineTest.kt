package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.time.OffsetDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer

@OptIn(ExperimentalCoroutinesApi::class)
class DeadlineTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-deadline-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun sharedDeadlineLocksAtExactZeroReloadsAfter500MsAndRejectsPostDeadlineSaves() = runTest {
        val server = FakeAttemptServer()
        val baseNow = OffsetDateTime.parse("2026-11-20T10:00:00Z")
        val attempt = server.loadStateFixture("in-progress-shared") {
            it.withFutureDeadlines(serverNow = baseNow, attemptRemainingSeconds = 5L)
        }
        val item1 = attempt.firstLeafItem().itemId!!

        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        val events = mutableListOf<EngineEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.events.collect { events.add(it) }
        }

        server.resetRecordedCalls()
        advanceTimeBy(4_999L)
        runCurrent()
        assertTrue(engine.state.value.answers[item1]?.sync != AnswerSyncStatus.Locked)

        server.serverNow = baseNow.plusSeconds(5)
        server.closeAttemptOnServer()
        advanceTimeBy(1L)
        runCurrent()

        assertTrue(engine.state.value.answers.values.all { it.sync == AnswerSyncStatus.Locked })
        assertTrue(events.contains(EngineEvent.AttemptClosed(AttemptClosedReason.TimeUp)))
        advanceTimeBy(499L)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.GET_STATE))

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.GET_STATE))
        assertEquals(AttemptClosedReason.TimeUp, engine.state.value.closed)

        engine.answer(item1, SingleChoiceAnswer("b"))
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))

        collector.cancel()
        engine.close()
    }

    @Test
    fun perPartDeadlineAdvancesToNextPartAndClosesOnLastPart() = runTest {
        val server = FakeAttemptServer()
        val baseNow = OffsetDateTime.parse("2026-11-20T10:00:00Z")
        val attempt = server.loadStateFixture("in-progress-per-part") {
            it.withFutureDeadlines(serverNow = baseNow, partRemainingSeconds = 4L)
        }
        val sortedParts = PartSelection.sortedParts(attempt)
        assertTrue("in-progress-per-part must have >= 2 parts", sortedParts.size >= 2)
        val part1 = sortedParts[0]
        val part2 = sortedParts[1]
        val part1Id = part1.partId!!
        val part2Id = part2.partId!!
        val part1Item = PartSelection.leafItems(part1).first().itemId!!

        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = File(storeRoot, "per-part-next").apply { mkdirs() },
        )
        runCurrent()
        val events = mutableListOf<EngineEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            engine.events.collect { events.add(it) }
        }

        server.serverNow = baseNow.plusSeconds(4)
        server.setPartState(part1Id, AttemptPartStateView.State.CLOSED, closedAt = server.serverNow)
        server.setPartState(
            part2Id,
            AttemptPartStateView.State.OPEN,
            deadlineAt = server.serverNow.plusSeconds(60),
        )
        advanceTimeBy(4_000L)
        runCurrent()

        assertEquals(AnswerSyncStatus.Locked, engine.state.value.answers[part1Item]?.sync)
        assertTrue(events.contains(EngineEvent.PartClosed(part1Id)))

        advanceTimeBy(500L)
        runCurrent()
        assertEquals(part2Id, engine.state.value.position?.partId)

        server.resetRecordedCalls()
        engine.answer(part1Item, SingleChoiceAnswer("c"))
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))

        collector.cancel()
        engine.close()

        val lastServer = FakeAttemptServer()
        val lastBase = lastServer.loadStateFixture("in-progress-per-part") {
            it.withFutureDeadlines(serverNow = baseNow, partRemainingSeconds = 3L)
        }
        val onlyPart = lastBase.firstOpenPart()
        lastServer.setAttemptState(lastBase.copy(parts = listOf(onlyPart)))
        val (lastEngine, _) = newEngineHarness(
            server = lastServer,
            storeRoot = File(storeRoot, "per-part-last").apply { mkdirs() },
        )
        runCurrent()

        lastServer.serverNow = baseNow.plusSeconds(3)
        lastServer.closeAttemptOnServer()
        advanceTimeBy(3_500L)
        runCurrent()
        assertEquals(AttemptClosedReason.TimeUp, lastEngine.state.value.closed)
        lastEngine.close()
    }

    @Test
    fun closedElsewhereDetectedBeforeNextHeartbeatOrOnNextSave() = runTest {
        val hbServer = FakeAttemptServer()
        hbServer.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val (hbEngine, _) = newEngineHarness(
            server = hbServer,
            storeRoot = File(storeRoot, "closed-hb").apply { mkdirs() },
        )
        runCurrent()

        hbServer.closeAttemptOnServer()
        advanceTimeBy(HEARTBEAT_INTERVAL_MS)
        runCurrent()
        assertNotNull("Closed elsewhere must be detected within 30s heartbeat", hbEngine.state.value.closed)
        hbEngine.close()

        val saveServer = FakeAttemptServer()
        val saveAttempt = saveServer.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val item1 = saveAttempt.firstLeafItem().itemId!!
        val (saveEngine, _) = newEngineHarness(
            server = saveServer,
            storeRoot = File(storeRoot, "closed-save").apply { mkdirs() },
        )
        runCurrent()

        saveServer.closeAttemptOnServer()
        saveEngine.answer(item1, SingleChoiceAnswer("d"))
        runCurrent()

        assertNotNull("Closed elsewhere must be detected immediately on next save", saveEngine.state.value.closed)
        val serverItem = saveServer.currentAttemptState().allLeafItems().first { it.itemId == item1 }
        assertTrue(
            "Server must not accept answer after attempt closed",
            serverItem.answer != SingleChoiceAnswer("d"),
        )
        saveEngine.close()
    }
}
