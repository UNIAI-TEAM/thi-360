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
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer

@OptIn(ExperimentalCoroutinesApi::class)
class SaveErrorTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-save-error-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun rateLimited429HandlesRetryAfterZeroOnePositiveAndMissingHeader() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val itemId = attempt.firstLeafItem().itemId!!
        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        server.enqueueRateLimited(AttemptOperation.SAVE_ANSWERS, retryAfterHeader = "5")
        engine.answer(itemId, SingleChoiceAnswer("a"))
        runCurrent()
        assertEquals(SaveStatus.Unsaved(UnsavedReason.RateLimited(5)), engine.state.value.save)
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))

        advanceTimeBy(4_999L)
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SaveStatus.Saved, engine.state.value.save)

        server.resetRecordedCalls()
        server.enqueueRateLimited(AttemptOperation.SAVE_ANSWERS, retryAfterHeader = "0")
        engine.answer(itemId, SingleChoiceAnswer("b"))
        runCurrent()
        assertEquals(SaveStatus.Unsaved(UnsavedReason.RateLimited(1)), engine.state.value.save)
        advanceTimeBy(999L)
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SaveStatus.Saved, engine.state.value.save)

        server.resetRecordedCalls()
        server.enqueueRateLimited(AttemptOperation.SAVE_ANSWERS, retryAfterHeader = "1")
        engine.answer(itemId, SingleChoiceAnswer("a"))
        runCurrent()
        assertEquals(SaveStatus.Unsaved(UnsavedReason.RateLimited(1)), engine.state.value.save)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SaveStatus.Saved, engine.state.value.save)

        server.resetRecordedCalls()
        server.enqueueRateLimited(AttemptOperation.SAVE_ANSWERS, retryAfterHeader = null)
        server.enqueueRateLimited(AttemptOperation.SAVE_ANSWERS, retryAfterHeader = null)
        engine.answer(itemId, SingleChoiceAnswer("b"))
        runCurrent()
        assertEquals(SaveStatus.Unsaved(UnsavedReason.RateLimited(1)), engine.state.value.save)

        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SaveStatus.Unsaved(UnsavedReason.RateLimited(2)), engine.state.value.save)

        advanceTimeBy(1_999L)
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.SAVE_ANSWERS))
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(3, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SaveStatus.Saved, engine.state.value.save)
        engine.close()
    }

    @Test
    fun backoffProgressionCapsAt30sAndResetsOnSuccessOrReconnect() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val itemId = attempt.firstLeafItem().itemId!!
        val connectivity = MutableConnectivityMonitor(true)
        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            connectivity = connectivity,
        )
        runCurrent()

        server.enqueueServerError(AttemptOperation.SAVE_ANSWERS, 503)
        server.enqueueHttpError(AttemptOperation.SAVE_ANSWERS, 400, "BAD_REQUEST")
        server.enqueueNetworkFailure(AttemptOperation.SAVE_ANSWERS, count = 1)
        repeat(4) { server.enqueueServerError(AttemptOperation.SAVE_ANSWERS, 503) }

        engine.answer(itemId, SingleChoiceAnswer("a"))
        runCurrent()
        assertEquals(SaveStatus.Unsaved(UnsavedReason.ServerError), engine.state.value.save)

        val steps = listOf(
            1_000L to UnsavedReason.NetworkError,
            2_000L to UnsavedReason.NetworkError,
            4_000L to UnsavedReason.ServerError,
            8_000L to UnsavedReason.ServerError,
            16_000L to UnsavedReason.ServerError,
            30_000L to UnsavedReason.ServerError,
        )
        var expectedCalls = 1
        for ((delayMs, expectedReason) in steps) {
            advanceTimeBy(delayMs - 1L)
            runCurrent()
            assertEquals(expectedCalls, server.callCount(AttemptOperation.SAVE_ANSWERS))
            advanceTimeBy(1L)
            runCurrent()
            expectedCalls += 1
            assertEquals(expectedCalls, server.callCount(AttemptOperation.SAVE_ANSWERS))
            assertEquals(SaveStatus.Unsaved(expectedReason), engine.state.value.save)
        }

        advanceTimeBy(29_999L)
        runCurrent()
        assertEquals(expectedCalls, server.callCount(AttemptOperation.SAVE_ANSWERS))
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(expectedCalls + 1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SaveStatus.Saved, engine.state.value.save)

        server.resetRecordedCalls()
        server.enqueueServerError(AttemptOperation.SAVE_ANSWERS, 503)
        engine.answer(itemId, SingleChoiceAnswer("b"))
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SaveStatus.Saved, engine.state.value.save)

        server.resetRecordedCalls()
        server.enqueueServerError(AttemptOperation.SAVE_ANSWERS, 503)
        server.enqueueServerError(AttemptOperation.SAVE_ANSWERS, 503)
        engine.answer(itemId, SingleChoiceAnswer("a"))
        runCurrent()
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.SAVE_ANSWERS))
        connectivity.setOnline(false)
        runCurrent()
        server.enqueueServerError(AttemptOperation.SAVE_ANSWERS, 503)
        connectivity.setOnline(true)
        runCurrent()
        assertEquals(3, server.callCount(AttemptOperation.SAVE_ANSWERS))
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(4, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SaveStatus.Saved, engine.state.value.save)
        engine.close()
    }

    @Test
    fun conflict409VariantsReloadOrEvictWithSessionConflict() = runTest {
        for (code in listOf(
            OpenApiAttemptApi.CODE_TIME_UP,
            OpenApiAttemptApi.CODE_ATTEMPT_NOT_OPEN,
            OpenApiAttemptApi.CODE_PART_CLOSED,
        )) {
            val server = FakeAttemptServer()
            val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
            val itemId = attempt.firstLeafItem().itemId!!
            val (engine, _) = newEngineHarness(
                server = server,
                storeRoot = File(storeRoot, code).apply { mkdirs() },
            )
            runCurrent()

            server.enqueueHttpError(AttemptOperation.SAVE_ANSWERS, 409, code)
            if (code != OpenApiAttemptApi.CODE_PART_CLOSED) {
                server.onRequest(AttemptOperation.SAVE_ANSWERS) {
                    server.closeAttemptOnServer(AttemptStateResponse.State.GRADING)
                }
            }
            val getsBefore = server.callCount(AttemptOperation.GET_STATE)
            engine.answer(itemId, SingleChoiceAnswer("a"))
            runCurrent()
            assertTrue("$code must reload attempt state", server.callCount(AttemptOperation.GET_STATE) > getsBefore)
            if (code != OpenApiAttemptApi.CODE_PART_CLOSED) {
                assertNotNull(engine.state.value.closed)
            }
            engine.close()
        }

        val conflictServer = FakeAttemptServer()
        val attempt = conflictServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val (conflictEngine, _) = newEngineHarness(
            server = conflictServer,
            storeRoot = File(storeRoot, "session-conflict").apply { mkdirs() },
        )
        runCurrent()
        conflictServer.simulateTakeoverByOtherDevice()
        conflictEngine.answer(attempt.firstLeafItem().itemId!!, SingleChoiceAnswer("a"))
        runCurrent()
        assertEquals(EditSessionState.Evicted, conflictEngine.state.value.session)
        assertEquals(SaveStatus.Unsaved(UnsavedReason.SessionConflict), conflictEngine.state.value.save)
        conflictEngine.close()
    }
}
