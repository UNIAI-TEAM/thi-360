package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.time.OffsetDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView

@OptIn(ExperimentalCoroutinesApi::class)
class PartChoiceCommandTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-part-choice-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun choosePartIdempotencyKeyRulesAndErrorCodes() = runTest {
        val server = FakeAttemptServer()
        val baseNow = OffsetDateTime.parse("2026-11-20T10:00:00Z")
        val attempt = server.loadStateFixture("awaiting-choice") { state ->
            state.copy(
                serverNow = baseNow,
                parts = state.parts?.map { part ->
                    if (part.state == AttemptPartStateView.State.AWAITING_CHOICE) {
                        part.copy(choiceDeadlineAt = baseNow.plusMinutes(15))
                    } else {
                        part
                    }
                },
            )
        }
        val choicePart = PartSelection.sortedParts(attempt)
            .first { it.state == AttemptPartStateView.State.AWAITING_CHOICE }
        val choicePartId = choicePart.partId!!
        val (engine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()

        server.enqueueNetworkFailure(AttemptOperation.CONFIRM_PART_CHOICE, count = 1)
        engine.choosePart(choicePartId, "SCIENCE", listOf("LY", "HOA", "SU"))
        runCurrent()
        assertEquals(CommandState.Refused("generic"), engine.state.value.command)

        server.enqueueNetworkFailure(AttemptOperation.CONFIRM_PART_CHOICE, count = 1)
        engine.choosePart(choicePartId, "SCIENCE", listOf("LY", "HOA", "SU"))
        runCurrent()

        val firstTwo = server.calls(AttemptOperation.CONFIRM_PART_CHOICE)
        assertEquals(2, firstTwo.size)
        assertNotNull(firstTwo[0].idempotencyKeyHeader)
        assertEquals(firstTwo[0].idempotencyKeyHeader, firstTwo[1].idempotencyKeyHeader)

        engine.choosePart(choicePartId, "SCIENCE", listOf("LY", "HOA", "DIA"))
        runCurrent()
        val thirdCall = server.calls(AttemptOperation.CONFIRM_PART_CHOICE)[2]
        assertNotEquals(firstTwo[0].idempotencyKeyHeader, thirdCall.idempotencyKeyHeader)
        val reqJson = Serializer.kotlinxSerializationJson.parseToJsonElement(thirdCall.body!!).jsonObject
        assertEquals("SCIENCE", reqJson["optionCode"]!!.jsonPrimitive.content)
        assertEquals(3, reqJson["subjectCodes"]!!.jsonArray.size)
        assertEquals(CommandState.Idle, engine.state.value.command)
        engine.close()

        val errCodes = listOf(
            OpenApiAttemptApi.CODE_CHOICE_LOCKED,
            OpenApiAttemptApi.CODE_PART_NOT_AWAITING,
            OpenApiAttemptApi.CODE_PART_GENERATING,
            OpenApiAttemptApi.CODE_CHOICE_WINDOW_CLOSED,
        )
        for (code in errCodes) {
            val errServer = FakeAttemptServer()
            val errAttempt = errServer.loadStateFixture("awaiting-choice") { state ->
                state.copy(
                    serverNow = baseNow,
                    parts = state.parts?.map { part ->
                        if (part.state == AttemptPartStateView.State.AWAITING_CHOICE) {
                            part.copy(choiceDeadlineAt = baseNow.plusMinutes(15))
                        } else {
                            part
                        }
                    },
                )
            }
            val errPartId = PartSelection.sortedParts(errAttempt)
                .first { it.state == AttemptPartStateView.State.AWAITING_CHOICE }.partId!!
            val (errEngine, _) = newEngineHarness(
                server = errServer,
                storeRoot = File(storeRoot, code).apply { mkdirs() },
            )
            runCurrent()

            when (code) {
                OpenApiAttemptApi.CODE_CHOICE_LOCKED ->
                    errServer.enqueueFixtureError(AttemptOperation.CONFIRM_PART_CHOICE, "confirmPartChoice", 409, "choice-locked")
                OpenApiAttemptApi.CODE_PART_NOT_AWAITING ->
                    errServer.enqueueFixtureError(AttemptOperation.CONFIRM_PART_CHOICE, "confirmPartChoice", 409, "part-not-awaiting")
                OpenApiAttemptApi.CODE_PART_GENERATING ->
                    errServer.enqueueChoice409PartGeneratingA6()
                OpenApiAttemptApi.CODE_CHOICE_WINDOW_CLOSED -> {
                    errServer.enqueueFixtureError(AttemptOperation.CONFIRM_PART_CHOICE, "confirmPartChoice", 409, "choice-window-closed")
                    errServer.onRequest(AttemptOperation.CONFIRM_PART_CHOICE) {
                        errServer.closeAttemptOnServer()
                    }
                }
            }
            val getsBefore = errServer.callCount(AttemptOperation.GET_STATE)
            errEngine.choosePart(errPartId, "SCIENCE", listOf("LY", "HOA", "SU"))
            runCurrent()

            assertTrue("$code must reload attempt state", errServer.callCount(AttemptOperation.GET_STATE) > getsBefore)
            assertEquals(CommandState.Refused(code), errEngine.state.value.command)
            if (code == OpenApiAttemptApi.CODE_CHOICE_WINDOW_CLOSED) {
                assertEquals(AttemptClosedReason.ChoiceTimeout, errEngine.state.value.closed)
            }
            errEngine.close()
        }
    }

    @Test
    fun choiceDeadlinePollingGeneratingPollingAndGenerationFailedRetry() = runTest {
        val choiceServer = FakeAttemptServer()
        val baseNow = OffsetDateTime.parse("2026-11-20T10:00:00Z")
        val choiceAttempt = choiceServer.loadStateFixture("awaiting-choice") { state ->
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
            storeRoot = File(storeRoot, "choice-poll").apply { mkdirs() },
        )
        runCurrent()
        val getsInitial = choiceServer.callCount(AttemptOperation.GET_STATE)

        advanceTimeBy(2_499L)
        runCurrent()
        assertEquals(getsInitial, choiceServer.callCount(AttemptOperation.GET_STATE))

        advanceTimeBy(1L)
        runCurrent()
        assertEquals(getsInitial + 1, choiceServer.callCount(AttemptOperation.GET_STATE))

        advanceTimeBy(1_999L)
        runCurrent()
        assertEquals(getsInitial + 1, choiceServer.callCount(AttemptOperation.GET_STATE))
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(getsInitial + 2, choiceServer.callCount(AttemptOperation.GET_STATE))

        choiceServer.closeAttemptOnServer()
        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(AttemptClosedReason.ChoiceTimeout, choiceEngine.state.value.closed)
        val getsAfterClose = choiceServer.callCount(AttemptOperation.GET_STATE)
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(getsAfterClose, choiceServer.callCount(AttemptOperation.GET_STATE))
        choiceEngine.close()

        val genServer = FakeAttemptServer()
        val genAttempt = genServer.loadStateFixture("generating")
        val genPartId = PartSelection.sortedParts(genAttempt)
            .first { it.state == AttemptPartStateView.State.GENERATING }.partId!!
        val (genEngine, _) = newEngineHarness(
            server = genServer,
            storeRoot = File(storeRoot, "gen-poll").apply { mkdirs() },
        )
        runCurrent()
        val genGetsInitial = genServer.callCount(AttemptOperation.GET_STATE)

        advanceTimeBy(1_499L)
        runCurrent()
        assertEquals(genGetsInitial, genServer.callCount(AttemptOperation.GET_STATE))
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(genGetsInitial + 1, genServer.callCount(AttemptOperation.GET_STATE))

        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(genGetsInitial + 2, genServer.callCount(AttemptOperation.GET_STATE))

        genServer.setPartState(genPartId, AttemptPartStateView.State.OPEN)
        advanceTimeBy(1_500L)
        runCurrent()
        val getsAfterOpen = genServer.callCount(AttemptOperation.GET_STATE)
        advanceTimeBy(6_000L)
        runCurrent()
        assertEquals(getsAfterOpen, genServer.callCount(AttemptOperation.GET_STATE))
        genEngine.close()

        val failServer = FakeAttemptServer()
        val failAttempt = failServer.loadStateFixture("generation-failed")
        val failPartId = PartSelection.sortedParts(failAttempt)
            .first { it.state == AttemptPartStateView.State.GENERATION_FAILED }.partId!!
        val (failEngine, _) = newEngineHarness(
            server = failServer,
            storeRoot = File(storeRoot, "gen-failed").apply { mkdirs() },
        )
        runCurrent()
        val getsOnFailed = failServer.callCount(AttemptOperation.GET_STATE)
        advanceTimeBy(30_000L)
        runCurrent()
        assertEquals("GENERATION_FAILED must never auto-poll (A-88)", getsOnFailed, failServer.callCount(AttemptOperation.GET_STATE))

        failServer.enqueueServerError(AttemptOperation.RETRY_PART_GENERATION, 503)
        failEngine.retryGeneration(failPartId)
        runCurrent()
        failEngine.retryGeneration(failPartId)
        runCurrent()
        val retryCalls = failServer.calls(AttemptOperation.RETRY_PART_GENERATION)
        assertEquals(2, retryCalls.size)
        assertEquals(retryCalls[0].idempotencyKeyHeader, retryCalls[1].idempotencyKeyHeader)

        failServer.setPartState(failPartId, AttemptPartStateView.State.GENERATION_FAILED)
        failEngine.refresh()
        runCurrent()
        failEngine.retryGeneration(failPartId)
        runCurrent()
        val thirdRetry = failServer.calls(AttemptOperation.RETRY_PART_GENERATION)[2]
        assertNotEquals(retryCalls[0].idempotencyKeyHeader, thirdRetry.idempotencyKeyHeader)
        failEngine.close()
    }
}
