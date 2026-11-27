package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.time.OffsetDateTime
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer

@OptIn(ExperimentalCoroutinesApi::class)
class ServerClockTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-clock-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun sharedPerPartNoDeadlineReanchorAndWallClockJumpWithinOneSecond() = runTest {
        val sharedServer = FakeAttemptServer()
        val baseNow = OffsetDateTime.parse("2026-11-20T10:00:00Z")
        val sharedAttempt = sharedServer.loadStateFixture("in-progress-shared") { state ->
            state.copy(serverNow = baseNow, deadlineAt = baseNow.plusMinutes(30))
        }
        val (sharedEngine, sharedClock) = newEngineHarness(
            server = sharedServer,
            storeRoot = File(storeRoot, "shared").apply { mkdirs() },
        )
        runCurrent()

        assertEquals(CountdownTarget.Attempt, sharedEngine.state.value.clock.target)
        assertTrue(abs(sharedEngine.state.value.clock.remainingMs - 1_800_000L) <= 1_000L)

        sharedClock.shiftWallClockMs(+3_600_000L)
        advanceTimeBy(5_000L)
        runCurrent()
        assertTrue(abs(sharedEngine.state.value.clock.remainingMs - 1_795_000L) <= 1_000L)

        sharedClock.shiftWallClockMs(-7_200_000L)
        advanceTimeBy(5_000L)
        runCurrent()
        assertTrue(abs(sharedEngine.state.value.clock.remainingMs - 1_790_000L) <= 1_000L)

        sharedServer.serverNow = baseNow.plusSeconds(100)
        sharedEngine.answer(sharedAttempt.firstLeafItem().itemId!!, SingleChoiceAnswer("a"))
        runCurrent()
        assertTrue(abs(sharedEngine.state.value.clock.remainingMs - 1_700_000L) <= 1_000L)

        sharedServer.serverNow = baseNow.plusSeconds(200)
        advanceTimeBy(HEARTBEAT_INTERVAL_MS - 10_000L)
        runCurrent()
        assertEquals(1, sharedServer.callCount(AttemptOperation.HEARTBEAT))
        assertTrue(abs(sharedEngine.state.value.clock.remainingMs - 1_600_000L) <= 1_000L)
        sharedEngine.close()

        val perPartServer = FakeAttemptServer()
        val perPartAttempt = perPartServer.loadStateFixture("in-progress-per-part") { state ->
            state.copy(
                serverNow = baseNow,
                parts = state.parts?.map { part ->
                    part.copy(deadlineAt = baseNow.plusSeconds(300))
                },
            )
        }
        val openPartId = perPartAttempt.firstOpenPart().partId!!
        perPartServer.onRequest(AttemptOperation.CLAIM_SESSION) {
            perPartServer.serverNow = baseNow.plusSeconds(12)
        }
        val (perPartEngine, _) = newEngineHarness(
            server = perPartServer,
            storeRoot = File(storeRoot, "perpart").apply { mkdirs() },
        )
        runCurrent()
        assertEquals(CountdownTarget.Part(openPartId), perPartEngine.state.value.clock.target)
        assertTrue(abs(perPartEngine.state.value.clock.remainingMs - 288_000L) <= 1_000L)
        perPartEngine.close()

        val unlimitedServer = FakeAttemptServer()
        unlimitedServer.loadStateFixture("in-progress-shared") { state ->
            state.copy(
                serverNow = baseNow,
                deadlineAt = null,
                parts = state.parts?.map { it.copy(deadlineAt = null) },
            )
        }
        val (unlimitedEngine, _) = newEngineHarness(
            server = unlimitedServer,
            storeRoot = File(storeRoot, "unlimited").apply { mkdirs() },
        )
        runCurrent()
        assertEquals(CountdownTarget.None, unlimitedEngine.state.value.clock.target)
        assertEquals(0L, unlimitedEngine.state.value.clock.remainingMs)
        unlimitedEngine.close()
    }
}
