package vn.unicom.thi360.feature.prepare

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.generated.model.AttemptPrepareResponse
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.StartAttemptRequest
import vn.unicom.thi360.core.network.generated.model.StartAttemptResponse
import vn.unicom.thi360.feature.prepare.waiting.WaitingPoller

@OptIn(ExperimentalCoroutinesApi::class)
class WaitingPollerTest {

    private val attemptId = UUID.fromString("00000000-0000-7000-8000-000000000001")

    private class FakePollApi(
        private val onPoll: suspend (Int) -> ApiResult<AttemptStateResponse>,
    ) : PrepareApi {
        val callCount = AtomicInteger(0)
        val concurrentCount = AtomicInteger(0)
        val maxConcurrent = AtomicInteger(0)

        override suspend fun prepare(entitlementId: UUID): ApiResult<AttemptPrepareResponse> =
            error("unused")

        override suspend fun start(
            idempotencyKey: String,
            request: StartAttemptRequest,
        ): ApiResult<StartAttemptResponse> = error("unused")

        override suspend fun getAttemptState(attemptId: UUID): ApiResult<AttemptStateResponse> {
            val active = concurrentCount.incrementAndGet()
            maxConcurrent.accumulateAndGet(active, ::maxOf)
            return try {
                val n = callCount.incrementAndGet()
                onPoll(n)
            } finally {
                concurrentCount.decrementAndGet()
            }
        }
    }

    @Test
    fun pollsSequentiallyWithoutOverlapHandlesErrorsSwitchesToBusyAt90sAndStopsOnReady() = runTest {
        val startingState = PrepareTestFixtures.loadAttemptState("starting")
        val readyState = PrepareTestFixtures.loadAttemptState("in-progress-per-part")
        val pollTimes = mutableListOf<Long>()
        var readyAttempt: UUID? = null

        val api = FakePollApi { callNo ->
            pollTimes += testScheduler.currentTime
            when (callNo) {
                1 -> {
                    delay(3_000L)
                    ApiResult.Success(startingState)
                }

                2 -> ApiResult.NetworkFailure(RuntimeException("offline"))
                3 -> ApiResult.HttpFailure(503, ApiError(code = "UNAVAILABLE", message = "busy"))
                else -> ApiResult.Success(startingState)
            }
        }

        val poller = WaitingPoller(
            attemptId = attemptId,
            clockMillis = { testScheduler.currentTime },
            api = api,
            onReady = { readyAttempt = it },
        )

        poller.start(this)
        runCurrent()
        assertTrue(poller.phase.value is WaitingPhase.Polling)

        advanceTimeBy(4_000L)
        runCurrent()
        assertEquals(1, api.callCount.get())
        assertEquals(1, api.maxConcurrent.get())
        assertEquals(1_000L, pollTimes[0])

        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(3, api.callCount.get())
        assertTrue(poller.phase.value is WaitingPhase.Polling)

        val elapsedUntil89s = 89_000L - currentTime
        advanceTimeBy(elapsedUntil89s)
        runCurrent()
        assertTrue("At 89s must still be Polling, was ${poller.phase.value}", poller.phase.value is WaitingPhase.Polling)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(WaitingPhase.Busy, poller.phase.value)

        val countAt90s = api.callCount.get()
        advanceTimeBy(15_000L)
        runCurrent()
        val countAt105s = api.callCount.get()
        assertEquals(3, countAt105s - countAt90s)
        assertEquals(WaitingPhase.Busy, poller.phase.value)

        poller.stop()

        val readyApi = FakePollApi { callNo ->
            if (callNo < 2) ApiResult.Success(startingState) else ApiResult.Success(readyState)
        }
        val readyPoller = WaitingPoller(
            attemptId = attemptId,
            clockMillis = { testScheduler.currentTime },
            api = readyApi,
            onReady = { readyAttempt = it },
        )
        readyPoller.start(this)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(attemptId, readyAttempt)
        val countAfterReady = readyApi.callCount.get()
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(countAfterReady, readyApi.callCount.get())
    }

    @Test
    fun stopsOnAbortedAndEmitsReason() = runTest {
        val aborted = PrepareTestFixtures.loadAttemptState("aborted")
        var observedReason: String? = "unset"
        val api = FakePollApi { ApiResult.Success(aborted) }

        val poller = WaitingPoller(
            attemptId = attemptId,
            clockMillis = { testScheduler.currentTime },
            api = api,
            onAborted = { observedReason = it },
        )

        poller.start(this)
        advanceTimeBy(1_000L)
        runCurrent()

        assertEquals("STALE_STARTING", observedReason)
        assertEquals(WaitingPhase.Failed("STALE_STARTING"), poller.phase.value)
        assertEquals(1, api.callCount.get())

        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(1, api.callCount.get())
    }

    @Test
    fun backgroundAndBack() = runTest {
        val startingState = PrepareTestFixtures.loadAttemptState("starting")
        val api = FakePollApi { ApiResult.Success(startingState) }

        val poller = WaitingPoller(
            attemptId = attemptId,
            clockMillis = { testScheduler.currentTime },
            api = api,
        )

        poller.start(this)
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(2, api.callCount.get())

        poller.setForeground(false)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(2, api.callCount.get())

        poller.setForeground(true)
        runCurrent()
        assertEquals(3, api.callCount.get())

        poller.stop()
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(3, api.callCount.get())
    }
}
