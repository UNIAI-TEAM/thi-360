package vn.unicom.thi360.feature.attemptengine

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.storage.AccountScopedStore

@OptIn(ExperimentalCoroutinesApi::class)
class EngineLifetimeTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-lifetime-")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        storeRoot.deleteRecursively()
    }

    @Test
    fun survivesConfigChangeWithoutDuplicatingInFlightSubmitAndStopsOnRouteCleared() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val store = AccountScopedStore(storeRoot, "account-A")
        val connectivity = MutableConnectivityMonitor(true)
        val clock = VirtualEngineClock(this)

        val viewModelStore = ViewModelStore()
        val factory = AttemptEngineViewModel.Factory(
            attemptId = attempt.attemptId,
            accountId = "account-A",
            api = server.virtualTimeApi(),
            store = store,
            clock = clock,
            connectivity = connectivity,
        )

        val vm1 = ViewModelProvider(viewModelStore, factory)[AttemptEngineViewModel::class.java]
        runCurrent()
        assertTrue(vm1.engine.state.value.session is EditSessionState.Editing)

        val owner1 = object : LifecycleOwner {
            val registry = LifecycleRegistry.createUnsafe(this)
            override val lifecycle: Lifecycle get() = registry
        }
        owner1.registry.currentState = Lifecycle.State.STARTED
        val lifecycle1 = EngineLifecycle(
            engine = vm1.engine,
            lifecycle = owner1.lifecycle,
            isChangingConfigurations = { true },
        ).attach()

        val submitGate = server.gateNext(AttemptOperation.SUBMIT)
        vm1.engine.submit()
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SUBMIT))
        assertEquals(CommandState.Sending(AttemptCommandKind.Submit), vm1.engine.state.value.command)

        owner1.registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycle1.close()

        val vm2 = ViewModelProvider(viewModelStore, factory)[AttemptEngineViewModel::class.java]
        assertSame("Configuration change must keep the exact same ViewModel instance", vm1, vm2)
        assertSame("Configuration change must keep the exact same AttemptEngine instance", vm1.engine, vm2.engine)
        runCurrent()
        assertEquals("In-flight submit must NOT be duplicated across configuration change", 1, server.callCount(AttemptOperation.SUBMIT))

        submitGate.complete(Unit)
        runCurrent()
        assertEquals(AttemptClosedReason.Submitted, vm2.engine.state.value.closed)

        val activeServer = FakeAttemptServer()
        val activeAttempt = activeServer.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val activeVmStore = ViewModelStore()
        val activeFactory = AttemptEngineViewModel.Factory(
            attemptId = activeAttempt.attemptId,
            accountId = "account-A",
            api = activeServer.virtualTimeApi(),
            store = AccountScopedStore(File(storeRoot, "route-pop").apply { mkdirs() }, "account-A"),
            clock = VirtualEngineClock(this),
            connectivity = MutableConnectivityMonitor(true),
        )
        val activeVm = ViewModelProvider(activeVmStore, activeFactory)[AttemptEngineViewModel::class.java]
        runCurrent()
        assertTrue(activeVm.engine.state.value.session is EditSessionState.Editing)

        activeVmStore.clear()
        activeServer.resetRecordedCalls()
        advanceTimeBy(120_000L)
        runCurrent()
        assertEquals("Cleared route ViewModel must emit ZERO calls", 0, activeServer.calls().size)
        viewModelStore.clear()
    }

    @Test
    fun processDeathAndForceStopRestoreSessionIdDraftsAndPositionWithoutOpenElsewhere() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val openPart = attempt.firstOpenPart()
        val items = PartSelection.leafItems(openPart)
        val item1 = items[0].itemId!!
        val item2 = items[1].itemId!!
        val store = AccountScopedStore(storeRoot, "account-A")

        val connectivity = MutableConnectivityMonitor(true)
        val (engine1, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            connectivity = connectivity,
        )
        runCurrent()
        val originalSessionId = (engine1.state.value.session as EditSessionState.Editing).sessionId

        val openPartId = openPart.partId!!
        connectivity.setOnline(false)
        runCurrent()
        engine1.answer(item1, SingleChoiceAnswer("a"))
        engine1.answer(item2, ShortAnswerValue("persisted-before-kill"))
        engine1.position(openPartId, item2)
        runCurrent()

        engine1.close()

        val draftStore = DraftStore(store)
        assertEquals(originalSessionId, draftStore.readSessionId(attempt.attemptId))
        assertNotNull(draftStore.readDraft(attempt.attemptId, item1))
        assertNotNull(draftStore.readDraft(attempt.attemptId, item2))
        assertEquals(EnginePosition(openPartId, item2), draftStore.readPosition(attempt.attemptId))

        server.resetRecordedCalls()
        val (engine2, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            connectivity = MutableConnectivityMonitor(true),
        )
        runCurrent()

        assertTrue(
            "Recreated engine must reclaim Editing with persisted sessionId (no OpenElsewhere)",
            engine2.state.value.session is EditSessionState.Editing,
        )
        assertEquals(originalSessionId, (engine2.state.value.session as EditSessionState.Editing).sessionId)
        assertEquals(item2, engine2.state.value.position?.itemId)
        assertEquals(SingleChoiceAnswer("a"), engine2.state.value.answers[item1]?.value)
        assertEquals(ShortAnswerValue("persisted-before-kill"), engine2.state.value.answers[item2]?.value)
        assertEquals(SaveStatus.Saved, engine2.state.value.save)
        engine2.close()
    }
}
