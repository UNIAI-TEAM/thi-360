package vn.unicom.thi360.feature.attemptengine

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.storage.AccountScopedStore

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineTest {

    private lateinit var storeRoot: File
    private lateinit var server: FakeAttemptServer

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-offline-")
        server = FakeAttemptServer()
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun offlineBuffersEditsInDraftsKeepsClockRunningAndReconcilesOnReconnect() = runTest {
        val attempt = server.loadStateFixture("in-progress-shared") {
            it.withFutureDeadlines(attemptRemainingSeconds = 600L)
        }
        val openPart = attempt.firstOpenPart()
        val items = attempt.allLeafItems()
        val item1 = items[0].itemId!!
        val item2 = items[1].itemId!!
        val connectivity = MutableConnectivityMonitor(true)
        val store = AccountScopedStore(storeRoot, "account-A")

        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            connectivity = connectivity,
        )
        runCurrent()
        assertEquals(NetworkStatus.Online, engine.state.value.network)
        val remainingBeforeOffline = engine.state.value.clock.remainingMs

        connectivity.setOnline(false)
        runCurrent()
        assertEquals(NetworkStatus.Offline, engine.state.value.network)

        server.resetRecordedCalls()
        engine.answer(item1, SingleChoiceAnswer("a"))
        engine.flag(item2, true)
        engine.position(openPart.partId!!, item2)
        advanceTimeBy(10_000L)
        runCurrent()

        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(AnswerSyncStatus.Pending, engine.state.value.answers[item1]?.sync)
        assertEquals(AnswerSyncStatus.Pending, engine.state.value.answers[item2]?.sync)
        assertEquals(SaveStatus.Offline, engine.state.value.save)
        assertNotNull(DraftStore(store).readDraft(attempt.attemptId, item1))
        val openPartId = openPart.partId
        assertEquals(EnginePosition(openPartId, item2), DraftStore(store).readPosition(attempt.attemptId))
        assertTrue(
            "Clock must keep counting down while offline",
            engine.state.value.clock.remainingMs <= remainingBeforeOffline - 9_000L,
        )

        connectivity.setOnline(true)
        runCurrent()

        assertEquals(NetworkStatus.Online, engine.state.value.network)
        assertEquals(1, server.callCount(AttemptOperation.GET_STATE))
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SaveStatus.Saved, engine.state.value.save)
        assertEquals(AnswerSyncStatus.Saved, engine.state.value.answers[item1]?.sync)
        assertEquals(SingleChoiceAnswer("a"), server.currentAttemptState().allLeafItems().first { it.itemId == item1 }.answer)
        assertEquals(true, server.currentAttemptState().allLeafItems().first { it.itemId == item2 }.flagged)
        assertEquals(EnginePosition(openPartId, item2), PartSelection.parseServerPosition(server.currentAttemptState()))
        engine.close()
    }

    @Test
    fun offlineInitialLoadRetriesGetBeforeClaimOnReconnectAndNeverClaimsClosedAttempt() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val item1 = attempt.firstLeafItem().itemId!!
        val openRoot = File(storeRoot, "offline-open").apply { mkdirs() }
        val openStore = AccountScopedStore(openRoot, "account-A")
        DraftStore(openStore).writeDraft(
            attempt.attemptId,
            item1,
            DraftRecord(seq = 1L, answer = SingleChoiceAnswer("b"), flagged = false),
        )

        val connectivity = MutableConnectivityMonitor(false)
        server.enqueueNetworkFailure(AttemptOperation.GET_STATE, count = 1)
        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = openRoot,
            connectivity = connectivity,
        )
        runCurrent()

        assertTrue(engine.state.value.load is vn.unicom.thi360.core.ui.LoadState.Failed)
        assertEquals(0, server.callCount(AttemptOperation.CLAIM_SESSION))
        assertEquals(null, DraftStore(openStore).readSessionId(attempt.attemptId))

        server.resetRecordedCalls()
        connectivity.setOnline(true)
        runCurrent()

        val opOrder = server.calls().map { it.operation }
        val getIdx = opOrder.indexOf(AttemptOperation.GET_STATE)
        val claimIdx = opOrder.indexOf(AttemptOperation.CLAIM_SESSION)
        assertTrue("GET_STATE must run before CLAIM_SESSION ($opOrder)", getIdx in 0 until claimIdx)
        assertTrue(engine.state.value.load is vn.unicom.thi360.core.ui.LoadState.Loaded)
        assertTrue(engine.state.value.session is EditSessionState.Editing)
        assertEquals(SaveStatus.Saved, engine.state.value.save)
        assertEquals(
            SingleChoiceAnswer("b"),
            server.currentAttemptState().allLeafItems().first { it.itemId == item1 }.answer,
        )
        engine.close()

        val closedServer = FakeAttemptServer()
        val closedAttempt = closedServer.loadStateFixture("closed-submitted")
        val closedRoot = File(storeRoot, "offline-closed").apply { mkdirs() }
        val closedStore = AccountScopedStore(closedRoot, "account-A")
        val closedConn = MutableConnectivityMonitor(false)
        closedServer.enqueueNetworkFailure(AttemptOperation.GET_STATE, count = 1)
        val (closedEngine, _) = newEngineHarness(
            server = closedServer,
            storeRoot = closedRoot,
            connectivity = closedConn,
        )
        runCurrent()
        assertEquals(0, closedServer.callCount(AttemptOperation.CLAIM_SESSION))

        closedConn.setOnline(true)
        runCurrent()
        assertEquals(0, closedServer.callCount(AttemptOperation.CLAIM_SESSION))
        assertEquals(null, DraftStore(closedStore).readSessionId(closedAttempt.attemptId))
        assertNotNull(closedEngine.state.value.closed)
        closedEngine.close()
    }
}
