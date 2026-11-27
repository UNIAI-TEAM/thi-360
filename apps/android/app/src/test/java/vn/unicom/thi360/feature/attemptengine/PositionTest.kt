package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.storage.AccountScopedStore

@OptIn(ExperimentalCoroutinesApi::class)
class PositionTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-position-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun positionPersistsImmediatelyDebounces400msAndFlushesImmediatelyWithDirtyChanges() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadAttempt50Fixture { it.withFutureDeadlines() }
        val openPart = attempt.firstOpenPart()
        val items = attempt.allLeafItems()
        val item1 = items[0].itemId!!
        val item5 = items[4].itemId!!
        val item12 = items[11].itemId!!
        val store = AccountScopedStore(storeRoot, "account-A")

        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        val openPartId = openPart.partId!!
        server.resetRecordedCalls()
        engine.position(openPartId, item5)
        assertEquals(EnginePosition(openPartId, item5), DraftStore(store).readPosition(attempt.attemptId))
        advanceTimeBy(399L)
        runCurrent()
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        val posBody = Serializer.kotlinxSerializationJson.parseToJsonElement(
            server.calls(AttemptOperation.SAVE_ANSWERS).single().body!!,
        ).jsonObject
        assertEquals(item5.toString(), posBody["position"]!!.jsonObject["itemId"]!!.jsonPrimitive.content)

        server.resetRecordedCalls()
        engine.answer(item1, ShortAnswerValue("42"))
        engine.position(openPartId, item12)
        runCurrent()

        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        val combinedBody = Serializer.kotlinxSerializationJson.parseToJsonElement(
            server.calls(AttemptOperation.SAVE_ANSWERS).single().body!!,
        ).jsonObject
        assertEquals(1, combinedBody["changes"]!!.jsonArray.size)
        assertEquals(item12.toString(), combinedBody["position"]!!.jsonObject["itemId"]!!.jsonPrimitive.content)

        val deviceBRoot = File(storeRoot, "deviceB").apply { mkdirs() }
        val (engineB, _) = newEngineHarness(
            server = server,
            storeRoot = deviceBRoot,
            accountId = "account-A",
        )
        runCurrent()
        assertTrue(engineB.state.value.session is EditSessionState.OpenElsewhere)
        engineB.takeover()
        runCurrent()
        assertTrue(engineB.state.value.session is EditSessionState.Editing)
        assertEquals(EnginePosition(openPartId, item12), engineB.state.value.position)
        engine.close()

        server.setPartState(openPartId, AttemptPartStateView.State.CLOSED)
        val part2Id = UUID.randomUUID()
        val part2 = openPart.copy(partId = part2Id, ord = 1, state = AttemptPartStateView.State.OPEN)
        server.setAttemptState(
            server.currentAttemptState().copy(
                parts = server.currentAttemptState().parts.orEmpty() + part2,
            ),
        )
        engineB.position(openPartId, item5)
        advanceTimeBy(POSITION_DEBOUNCE_MS)
        runCurrent()
        assertEquals(SaveStatus.Saved, engineB.state.value.save)
        engineB.close()

        val (reopened, _) = newEngineHarness(
            server = server,
            storeRoot = File(storeRoot, "reopen").apply { mkdirs() },
        )
        runCurrent()
        assertEquals(part2Id, reopened.state.value.position?.partId)
        reopened.close()
    }

    @Test
    fun partSelectionPureRulesKeepOpenOrUnfinishedAndFallbackPerR84() {
        val server = FakeAttemptServer()
        val failedAttempt = server.loadStateFixture("generation-failed")
        val failedPart = PartSelection.sortedParts(failedAttempt)
            .first { it.state == AttemptPartStateView.State.GENERATION_FAILED }
        val closedPart = PartSelection.sortedParts(failedAttempt)
            .first { it.state == AttemptPartStateView.State.CLOSED }

        val keepFailed = PartSelection.initial(
            attempt = failedAttempt,
            serverPos = null,
            localPos = EnginePosition(failedPart.partId!!, null),
        )
        assertEquals(failedPart.partId, keepFailed?.partId)

        val fallbackFromClosed = PartSelection.initial(
            attempt = failedAttempt,
            serverPos = EnginePosition(closedPart.partId!!, null),
            localPos = null,
        )
        assertEquals(failedPart.partId, fallbackFromClosed?.partId)
        assertNotNull(PartSelection.afterClose(failedAttempt))
    }
}
