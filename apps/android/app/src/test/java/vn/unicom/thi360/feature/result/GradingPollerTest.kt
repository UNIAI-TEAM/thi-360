package vn.unicom.thi360.feature.result

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse

@OptIn(ExperimentalCoroutinesApi::class)
class GradingPollerTest {

    @Test
    fun pollsEveryThreeSecondsAndStopsWhenScoredOrPendingReview() = runTest {
        val poller = GradingPoller(
            clockMillis = { testScheduler.currentTime },
            intervalMillis = 3_000L,
            windowMillis = 120_000L,
        )
        val states = ArrayDeque(
            listOf(
                AttemptResultResponse.State.GRADING,
                AttemptResultResponse.State.GRADING,
                AttemptResultResponse.State.SCORED,
            ),
        )
        var polls = 0

        poller.onResultState(AttemptResultResponse.State.GRADING, this) {
            polls++
            states.removeFirst()
        }
        assertTrue(poller.phase.value is PollPhase.Polling)
        assertEquals(0, polls)

        advanceTimeBy(2_999L)
        runCurrent()
        assertEquals(0, polls)

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, polls)
        assertTrue(poller.phase.value is PollPhase.Polling)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(2, polls)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(3, polls)
        assertEquals(PollPhase.Idle, poller.phase.value)

        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(3, polls)
    }

    @Test
    fun stopsWhenTransitioningToPendingReviewAndOnLeave() = runTest {
        val poller = GradingPoller(clockMillis = { testScheduler.currentTime })
        var polls = 0
        poller.onResultState(AttemptResultResponse.State.GRADING, this) {
            polls++
            AttemptResultResponse.State.PENDING_REVIEW
        }
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(1, polls)
        assertEquals(PollPhase.Idle, poller.phase.value)

        var leavePolls = 0
        poller.onResultState(AttemptResultResponse.State.GRADING, this) {
            leavePolls++
            AttemptResultResponse.State.GRADING
        }
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(1, leavePolls)

        poller.stop()
        assertEquals(PollPhase.Idle, poller.phase.value)
        advanceTimeBy(15_000L)
        runCurrent()
        assertEquals(1, leavePolls)
    }

    @Test
    fun pausesInBackgroundAndResumesImmediatelyWithinOriginalDeadline() = runTest {
        val poller = GradingPoller(
            clockMillis = { testScheduler.currentTime },
            intervalMillis = 3_000L,
            windowMillis = 120_000L,
        )
        var polls = 0
        val fetcher: suspend () -> AttemptResultResponse.State? = {
            polls++
            AttemptResultResponse.State.GRADING
        }

        poller.onResultState(AttemptResultResponse.State.GRADING, this, fetcher)
        val initialDeadline = (poller.phase.value as PollPhase.Polling).deadlineMillis
        assertEquals(120_000L, initialDeadline)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(1, polls)

        poller.onBackground()
        assertTrue(poller.phase.value is PollPhase.Paused)
        assertEquals(initialDeadline, (poller.phase.value as PollPhase.Paused).deadlineMillis)

        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals(1, polls)

        poller.onForeground(this, fetcher)
        runCurrent()
        assertEquals(2, polls)
        val resumedPhase = poller.phase.value as PollPhase.Polling
        assertEquals(initialDeadline, resumedPhase.deadlineMillis)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(3, polls)

        advanceTimeBy(90_000L)
        runCurrent()
        assertEquals(PollPhase.TimedOut, poller.phase.value)
        val countAtTimeout = polls
        advanceTimeBy(15_000L)
        runCurrent()
        assertEquals(countAtTimeout, polls)
    }

    @Test
    fun timeoutAndReload() = runTest {
        val poller = GradingPoller(
            clockMillis = { testScheduler.currentTime },
            intervalMillis = 3_000L,
            windowMillis = 120_000L,
        )
        var polls = 0
        poller.onResultState(AttemptResultResponse.State.GRADING, this) {
            polls++
            AttemptResultResponse.State.GRADING
        }

        advanceTimeBy(120_000L)
        runCurrent()
        assertEquals(PollPhase.TimedOut, poller.phase.value)
        val pollsBeforeTimeout = polls
        assertTrue(pollsBeforeTimeout > 0)

        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(pollsBeforeTimeout, polls)

        val gate = CompletableDeferred<AttemptResultResponse.State?>()
        var reloadCalls = 0
        poller.reloadFromTimeout(this) {
            reloadCalls++
            gate.await()
        }
        runCurrent()
        assertEquals(1, reloadCalls)
        assertTrue(poller.isReloading.value)

        poller.reloadFromTimeout(this) {
            reloadCalls++
            AttemptResultResponse.State.GRADING
        }
        runCurrent()
        assertEquals(1, reloadCalls)

        val nowAtReload = testScheduler.currentTime
        gate.complete(AttemptResultResponse.State.GRADING)
        runCurrent()
        assertFalse(poller.isReloading.value)

        val renewed = poller.phase.value as PollPhase.Polling
        assertEquals(nowAtReload + 120_000L, renewed.deadlineMillis)

        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(2, reloadCalls)

        poller.stop()
    }
}
