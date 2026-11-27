package vn.unicom.thi360.feature.attemptengine

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.io.File
import java.time.OffsetDateTime
import kotlin.math.abs
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
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.shell.routes.InboundLinkRouter

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-foreground-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun backgroundFlushesDraftStopsLoopsAndBiometricUnlockResumeReanchorsClock() = runTest {
        val server = FakeAttemptServer()
        val baseNow = OffsetDateTime.parse("2026-11-20T10:00:00Z")
        val attempt = server.loadStateFixture("in-progress-shared") {
            it.withFutureDeadlines(serverNow = baseNow, attemptRemainingSeconds = 600L)
        }
        val items = attempt.allLeafItems()
        val item1 = items[0].itemId!!
        val item2 = items[1].itemId!!
        val store = AccountScopedStore(storeRoot, "account-A")

        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner.registry.currentState = Lifecycle.State.STARTED
        val router = InboundLinkRouter { "thi360.vn" }
        router.onSessionStateChanged(SessionState.Active(username = "student.demo"))
        val lifecycleHook = EngineLifecycle(
            engine = engine,
            lifecycle = owner.lifecycle,
            inboundLinkRouter = router,
        ).attach()

        server.setEndpointDelay(AttemptOperation.SAVE_ANSWERS, 1_500L)
        var draftOnDiskBeforeCallDone = false
        server.onRequest(AttemptOperation.SAVE_ANSWERS) {
            draftOnDiskBeforeCallDone = DraftStore(store).readDraft(attempt.attemptId, item1) != null
        }
        server.resetRecordedCalls()

        engine.answer(item1, EssayAnswer(html = "<p>background flush</p>"))
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))

        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        runCurrent()
        assertEquals("setForeground(false) must flush debounced work immediately", 1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertTrue("Draft must be persisted before save call completes", draftOnDiskBeforeCallDone)

        advanceTimeBy(1_500L)
        runCurrent()
        server.setEndpointDelay(AttemptOperation.SAVE_ANSWERS, 0L)
        server.resetRecordedCalls()

        advanceTimeBy(120_000L)
        runCurrent()
        assertEquals("Background must emit ZERO heartbeats", 0, server.callCount(AttemptOperation.HEARTBEAT))
        assertEquals("Background must emit ZERO state polls", 0, server.callCount(AttemptOperation.GET_STATE))

        server.serverNow = baseNow.plusSeconds(122)
        DraftStore(store).writeDraft(
            attempt.attemptId,
            item2,
            DraftRecord(seq = 1L, answer = ShortAnswerValue("resumed-draft"), flagged = false),
        )
        router.onSessionStateChanged(SessionState.Locked(username = "student.demo"))
        owner.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        router.notifyForegroundReturn()
        runCurrent()
        assertEquals("Must wait for biometric unlock before resuming", 0, server.callCount(AttemptOperation.HEARTBEAT))

        router.onSessionStateChanged(SessionState.Active(username = "student.demo"))
        runCurrent()

        assertEquals("Foreground resume must send immediate heartbeat", 1, server.callCount(AttemptOperation.HEARTBEAT))
        assertEquals("Foreground resume must call GET state", 1, server.callCount(AttemptOperation.GET_STATE))
        assertEquals("Foreground resume must flush remaining reconciled unsaved changes", 1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        val expectedRemainingMs = (600L - 122L) * 1_000L
        assertTrue(
            "Clock after resume must be within 1s of server remaining (expected=$expectedRemainingMs, actual=${engine.state.value.clock.remainingMs})",
            abs(engine.state.value.clock.remainingMs - expectedRemainingMs) <= 1_000L,
        )

        lifecycleHook.close()
        engine.close()
    }

    @Test
    fun backgroundStopsGeneratingAndChoicePolling() = runTest {
        val genServer = FakeAttemptServer()
        genServer.loadStateFixture("generating")
        val (genEngine, _) = newEngineHarness(
            server = genServer,
            storeRoot = File(storeRoot, "bg-gen").apply { mkdirs() },
        )
        runCurrent()

        genEngine.setForeground(false)
        genServer.resetRecordedCalls()
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals("GENERATING poll must stop in background", 0, genServer.callCount(AttemptOperation.GET_STATE))
        genEngine.close()

        val choiceServer = FakeAttemptServer()
        val baseNow = OffsetDateTime.parse("2026-11-20T10:00:00Z")
        choiceServer.loadStateFixture("awaiting-choice") { state ->
            state.copy(
                serverNow = baseNow,
                parts = state.parts?.map { part ->
                    if (part.state == AttemptPartStateView.State.AWAITING_CHOICE) {
                        part.copy(choiceDeadlineAt = baseNow.plusSeconds(2))
                    } else {
                        part
                    }
                },
            )
        }
        val (choiceEngine, _) = newEngineHarness(
            server = choiceServer,
            storeRoot = File(storeRoot, "bg-choice").apply { mkdirs() },
        )
        runCurrent()

        choiceEngine.setForeground(false)
        choiceServer.resetRecordedCalls()
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals("AWAITING_CHOICE poll must stop in background", 0, choiceServer.callCount(AttemptOperation.GET_STATE))
        choiceEngine.close()
    }

    @Test
    fun expiredOrEvictedInBackgroundNeverSendsUnsavedChangesOnResume() = runTest {
        val expServer = FakeAttemptServer()
        val expAttempt = expServer.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val expItem = expAttempt.firstLeafItem().itemId!!
        val expRoot = File(storeRoot, "bg-expired").apply { mkdirs() }
        val (expEngine, _) = newEngineHarness(server = expServer, storeRoot = expRoot)
        runCurrent()

        expEngine.setForeground(false)
        runCurrent()
        DraftStore(AccountScopedStore(expRoot, "account-A")).writeDraft(
            expAttempt.attemptId,
            expItem,
            DraftRecord(seq = 1L, answer = SingleChoiceAnswer("a"), flagged = false),
        )
        expServer.closeAttemptOnServer()
        expServer.resetRecordedCalls()

        expEngine.setForeground(true)
        runCurrent()

        assertNotNull("Attempt expired in background must be closed on resume", expEngine.state.value.closed)
        assertEquals("Zero save calls may be accepted for expired attempt", 0, expServer.callCount(AttemptOperation.SAVE_ANSWERS))
        expEngine.close()

        val evictServer = FakeAttemptServer()
        val evictAttempt = evictServer.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val evictItem = evictAttempt.firstLeafItem().itemId!!
        val evictRoot = File(storeRoot, "bg-evicted").apply { mkdirs() }
        val (evictEngine, _) = newEngineHarness(server = evictServer, storeRoot = evictRoot)
        runCurrent()
        val evictEvents = mutableListOf<EngineEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            evictEngine.events.collect { evictEvents.add(it) }
        }

        evictEngine.setForeground(false)
        runCurrent()
        DraftStore(AccountScopedStore(evictRoot, "account-A")).writeDraft(
            evictAttempt.attemptId,
            evictItem,
            DraftRecord(seq = 1L, answer = ShortAnswerValue("must-not-send"), flagged = false),
        )
        evictServer.simulateTakeoverByOtherDevice()
        evictServer.resetRecordedCalls()

        evictEngine.setForeground(true)
        runCurrent()

        assertEquals(1, evictServer.callCount(AttemptOperation.HEARTBEAT))
        assertEquals(EditSessionState.Evicted, evictEngine.state.value.session)
        assertTrue(evictEvents.contains(EngineEvent.SessionLost))
        assertEquals("Evicted engine on resume must NOT send unsaved changes", 0, evictServer.callCount(AttemptOperation.SAVE_ANSWERS))
        collector.cancel()
        evictEngine.close()
    }

    @Test
    fun resumesForegroundAfterBackgroundConfigChangeOrBiometricLockUnmount() = runTest {
        val server = FakeAttemptServer()
        server.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = File(storeRoot, "bg-recreate").apply { mkdirs() },
        )
        runCurrent()

        val router = InboundLinkRouter { "thi360.vn" }
        var sessionUnlocked = true
        router.onSessionStateChanged(SessionState.Active(username = "student.demo"))

        val owner1 = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner1.registry.currentState = Lifecycle.State.STARTED
        val hook1 = EngineLifecycle(
            engine = engine,
            lifecycle = owner1.lifecycle,
            inboundLinkRouter = router,
            isSessionUnlocked = { sessionUnlocked },
        ).attach()

        owner1.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        hook1.close()
        server.resetRecordedCalls()

        val owner2 = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner2.registry.currentState = Lifecycle.State.STARTED
        val hook2 = EngineLifecycle(
            engine = engine,
            lifecycle = owner2.lifecycle,
            inboundLinkRouter = router,
            isSessionUnlocked = { sessionUnlocked },
        ).attach()
        runCurrent()

        assertEquals(1, server.callCount(AttemptOperation.HEARTBEAT))
        assertEquals(1, server.callCount(AttemptOperation.GET_STATE))
        advanceTimeBy(HEARTBEAT_INTERVAL_MS)
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.HEARTBEAT))

        owner2.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        sessionUnlocked = false
        router.onSessionStateChanged(SessionState.Locked(username = "student.demo"))
        hook2.close()
        server.resetRecordedCalls()

        val ownerLocked = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        ownerLocked.registry.currentState = Lifecycle.State.STARTED
        val hookLocked = EngineLifecycle(
            engine = engine,
            lifecycle = ownerLocked.lifecycle,
            inboundLinkRouter = router,
            isSessionUnlocked = { sessionUnlocked },
        ).attach()
        runCurrent()
        assertEquals("Must not resume while session is locked", 0, server.callCount(AttemptOperation.HEARTBEAT))
        hookLocked.close()

        router.notifyForegroundReturn()
        sessionUnlocked = true
        router.onSessionStateChanged(SessionState.Active(username = "student.demo"))
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.HEARTBEAT))

        val owner3 = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner3.registry.currentState = Lifecycle.State.STARTED
        val hook3 = EngineLifecycle(
            engine = engine,
            lifecycle = owner3.lifecycle,
            inboundLinkRouter = router,
            isSessionUnlocked = { sessionUnlocked },
        ).attach()
        runCurrent()

        assertEquals(1, server.callCount(AttemptOperation.HEARTBEAT))
        assertEquals(1, server.callCount(AttemptOperation.GET_STATE))
        hook3.close()
        engine.close()
    }
}
