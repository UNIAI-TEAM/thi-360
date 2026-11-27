package vn.unicom.thi360.feature.attemptengine

import java.io.File
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
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse

@OptIn(ExperimentalCoroutinesApi::class)
class HeartbeatTest {

    private lateinit var storeRoot: File
    private lateinit var server: FakeAttemptServer

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-heartbeat-")
        server = FakeAttemptServer()
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun periodicHeartbeatReanchorsClockIgnoresTransientErrorsAndStopsOnClosedState() = runTest {
        val attempt = server.loadStateFixture("in-progress-shared") { it.withFutureDeadlines(attemptRemainingSeconds = 600L) }
        val events = mutableListOf<EngineEvent>()
        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        val collector = backgroundScope.launch { engine.events.collect { events.add(it) } }
        runCurrent()

        assertTrue(engine.state.value.session is EditSessionState.Editing)
        assertEquals(0, server.callCount(AttemptOperation.HEARTBEAT))

        advanceTimeBy(29_999L)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.HEARTBEAT))

        server.serverNow = attempt.serverNow.plusSeconds(35L)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.HEARTBEAT))
        val remainingAfterReanchor = engine.state.value.clock.remainingMs
        assertTrue(
            "Clock should reflect serverNow +35s (expected ~565s, got ${remainingAfterReanchor}ms)",
            kotlin.math.abs(remainingAfterReanchor - 565_000L) <= 1_000L,
        )

        server.enqueueNetworkFailure(AttemptOperation.HEARTBEAT, count = 1)
        server.enqueueServerError(AttemptOperation.HEARTBEAT, statusCode = 503)

        val sessionBeforeErrors = engine.state.value.session
        advanceTimeBy(HEARTBEAT_INTERVAL_MS)
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.HEARTBEAT))
        assertEquals(SaveStatus.Saved, engine.state.value.save)
        assertEquals(sessionBeforeErrors, engine.state.value.session)

        advanceTimeBy(HEARTBEAT_INTERVAL_MS)
        runCurrent()
        assertEquals(3, server.callCount(AttemptOperation.HEARTBEAT))
        assertEquals(SaveStatus.Saved, engine.state.value.save)
        assertEquals(sessionBeforeErrors, engine.state.value.session)

        server.closeAttemptOnServer(AttemptStateResponse.State.GRADING)
        val getsBefore = server.callCount(AttemptOperation.GET_STATE)
        advanceTimeBy(HEARTBEAT_INTERVAL_MS)
        runCurrent()

        assertEquals(4, server.callCount(AttemptOperation.HEARTBEAT))
        assertTrue(server.callCount(AttemptOperation.GET_STATE) > getsBefore)
        assertTrue(events.any { it is EngineEvent.AttemptClosed })

        advanceTimeBy(120_000L)
        runCurrent()
        assertEquals(4, server.callCount(AttemptOperation.HEARTBEAT))

        collector.cancel()
        engine.close()
    }
}
