package vn.unicom.thi360.feature.payment

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.feature.payment.domain.OrderPoller
import vn.unicom.thi360.feature.payment.domain.PollDirective

@OptIn(ExperimentalCoroutinesApi::class)
class OrderPollerTest {

    @Test
    fun pollsEvery2SecondsSingleFlightAndTimesOutAt60Seconds() {
        val scope = TestScope(StandardTestDispatcher())
        val poller = OrderPoller(
            intervalMillis = 2_000L,
            windowMillis = 60_000L,
            clockMillis = { scope.testScheduler.currentTime },
        )
        var calls = 0
        var activeCalls = 0
        var maxConcurrent = 0
        var timedOut = false

        poller.start(
            scope = scope,
            onPoll = {
                calls++
                activeCalls++
                maxConcurrent = maxOf(maxConcurrent, activeCalls)
                delay(1_000L) // slow call: next 2s must start AFTER this finishes
                activeCalls--
                PollDirective.CONTINUE
            },
            onTimeout = { timedOut = true },
        )

        // At 1.9s -> 0 calls
        scope.advanceTimeBy(1_900L)
        scope.runCurrent()
        assertEquals(0, calls)

        // At 2.0s -> call 1 starts (finishes at 3.0s)
        scope.advanceTimeBy(100L)
        scope.runCurrent()
        assertEquals(1, calls)

        // At 4.9s -> still only 1 call (2s after 3.0s = 5.0s!)
        scope.advanceTimeBy(2_900L)
        scope.runCurrent()
        assertEquals(1, calls)

        // At 5.0s -> call 2 starts
        scope.advanceTimeBy(100L)
        scope.runCurrent()
        assertEquals(2, calls)
        assertEquals(1, maxConcurrent)

        // At 59.9s -> not timed out yet
        scope.advanceTimeBy(54_900L)
        scope.runCurrent()
        assertFalse("Must NOT time out at 59.9s", timedOut)

        // At 60.0s -> times out and stops
        scope.advanceTimeBy(100L)
        scope.runCurrent()
        assertTrue("Must time out at 60.0s", timedOut)
        assertFalse(poller.isRunning)

        val callsAtTimeout = calls
        scope.advanceTimeBy(10_000L)
        scope.runCurrent()
        assertEquals(callsAtTimeout, calls)
    }

    @Test
    fun pauseStopsCallsAndResumeReadsImmediatelyAndRespectsOriginal60sWindow() {
        val scope = TestScope(StandardTestDispatcher())
        val poller = OrderPoller(
            intervalMillis = 2_000L,
            windowMillis = 60_000L,
            clockMillis = { scope.testScheduler.currentTime },
        )
        var calls = 0
        var timedOut = false

        poller.start(
            scope = scope,
            onPoll = {
                calls++
                PollDirective.CONTINUE
            },
            onTimeout = { timedOut = true },
        )

        scope.advanceTimeBy(2_000L)
        scope.runCurrent()
        assertEquals(1, calls)

        // Pause at t=2s, advance 30s -> ZERO calls while paused
        poller.onPause()
        scope.advanceTimeBy(30_000L)
        scope.runCurrent()
        assertEquals(1, calls)
        assertFalse(timedOut)

        // Resume at t=32s -> immediate poll (call 2), then continues until t=60s
        poller.onResume()
        scope.runCurrent()
        assertEquals(2, calls)
        assertFalse(timedOut)

        scope.advanceTimeBy(28_000L)
        scope.runCurrent()
        assertTrue(timedOut)

        // Restart window resets 60s timer and polls immediately
        timedOut = false
        val beforeRestart = calls
        poller.restartWindow(
            scope = scope,
            onPoll = {
                calls++
                PollDirective.CONTINUE
            },
            onTimeout = { timedOut = true },
        )
        scope.runCurrent()
        assertEquals(beforeRestart + 1, calls)
        assertFalse(timedOut)

        scope.advanceTimeBy(60_000L)
        scope.runCurrent()
        assertTrue(timedOut)
    }
}
