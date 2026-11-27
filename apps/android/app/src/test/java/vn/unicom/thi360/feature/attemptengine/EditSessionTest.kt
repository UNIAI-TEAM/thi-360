package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.storage.AccountScopedStore

@OptIn(ExperimentalCoroutinesApi::class)
class EditSessionTest {

    private lateinit var storeRoot: File
    private lateinit var server: FakeAttemptServer

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-edit-session-")
        server = FakeAttemptServer()
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun claimPersistsBeforeCall() = runTest {
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val openPart = attempt.firstOpenPart()
        val firstItem = attempt.firstLeafItem()
        val fixedSessionId = UUID.fromString("11111111-1111-4111-8111-111111111111")
        val store = AccountScopedStore(storeRoot, "account-A")

        var persistedAtClaimTime: String? = null
        val claimGate = server.gateNext(AttemptOperation.CLAIM_SESSION)
        server.onRequest(AttemptOperation.CLAIM_SESSION) {
            persistedAtClaimTime = store.readString(
                AttemptStorageKeys.NAMESPACE,
                AttemptStorageKeys.sessionKey(attempt.attemptId),
            )
        }

        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            accountId = "account-A",
            uuidGenerator = { fixedSessionId },
        )
        runCurrent()

        assertEquals(
            "sessionId must be persisted in AccountScopedStore BEFORE POST .../session is dispatched",
            fixedSessionId.toString(),
            persistedAtClaimTime,
        )
        assertEquals(EditSessionState.Acquiring, engine.state.value.session)

        val firstItemId = firstItem.itemId!!
        val openPartId = openPart.partId!!
        engine.answer(firstItemId, SingleChoiceAnswer("a"))
        engine.finishPart(openPartId)
        engine.submit()
        engine.choosePart(openPartId, "SCIENCE")
        engine.retryGeneration(openPartId)
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(0, server.callCount(AttemptOperation.HEARTBEAT))
        assertEquals(0, server.callCount(AttemptOperation.FINISH_PART))
        assertEquals(0, server.callCount(AttemptOperation.SUBMIT))
        assertEquals(0, server.callCount(AttemptOperation.CONFIRM_PART_CHOICE))
        assertEquals(0, server.callCount(AttemptOperation.RETRY_PART_GENERATION))
        assertNotNull(
            DraftStore(store).readDraft(attempt.attemptId, firstItemId),
        )

        claimGate.complete(Unit)
        runCurrent()

        assertEquals(EditSessionState.Editing(fixedSessionId), engine.state.value.session)
        assertEquals(1, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(
            fixedSessionId.toString(),
            server.calls(AttemptOperation.SAVE_ANSWERS).single().attemptSessionHeader,
        )
        engine.close()

        server.resetRecordedCalls()
        val (recreated, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            accountId = "account-A",
            uuidGenerator = { UUID.fromString("22222222-2222-4222-8222-222222222222") },
        )
        runCurrent()

        assertEquals(EditSessionState.Editing(fixedSessionId), recreated.state.value.session)
        val claimBody = Serializer.kotlinxSerializationJson.parseToJsonElement(
            server.calls(AttemptOperation.CLAIM_SESSION).single().body!!,
        ).jsonObject
        assertEquals(fixedSessionId.toString(), claimBody["sessionId"]?.jsonPrimitive?.content)
        recreated.close()
    }

    @Test
    fun headerOnEveryCommand() = runTest {
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val parts = PartSelection.sortedParts(attempt)
        val openPart = parts.first { it.state == AttemptPartStateView.State.OPEN }
        val choicePartId = UUID.fromString("33333333-3333-4333-8333-333333333333")
        val retryPartId = UUID.fromString("44444444-4444-4444-8444-444444444444")
        val augmented = attempt.copy(
            parts = parts + listOf(
                openPart.copy(
                    partId = choicePartId,
                    ord = 1,
                    state = AttemptPartStateView.State.AWAITING_CHOICE,
                    items = emptyList(),
                ),
                openPart.copy(
                    partId = retryPartId,
                    ord = 2,
                    state = AttemptPartStateView.State.GENERATION_FAILED,
                    items = emptyList(),
                ),
            ),
        )
        server.setAttemptState(augmented)
        val fixedSessionId = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            uuidGenerator = { fixedSessionId },
        )
        runCurrent()
        assertEquals(EditSessionState.Editing(fixedSessionId), engine.state.value.session)

        engine.answer(openPart.items!!.first().itemId!!, SingleChoiceAnswer("a"))
        runCurrent()

        advanceTimeBy(HEARTBEAT_INTERVAL_MS)
        runCurrent()

        engine.choosePart(choicePartId, "SCIENCE", listOf("LY", "HOA", "SU"))
        runCurrent()

        engine.retryGeneration(retryPartId)
        runCurrent()

        engine.finishPart(openPart.partId!!)
        runCurrent()

        engine.submit()
        runCurrent()

        val expected = fixedSessionId.toString()
        listOf(
            AttemptOperation.SAVE_ANSWERS,
            AttemptOperation.HEARTBEAT,
            AttemptOperation.CONFIRM_PART_CHOICE,
            AttemptOperation.RETRY_PART_GENERATION,
            AttemptOperation.FINISH_PART,
            AttemptOperation.SUBMIT,
        ).forEach { op ->
            val calls = server.calls(op)
            assertTrue("Expected at least one call for $op", calls.isNotEmpty())
            assertTrue(
                "Every $op call must carry X-Attempt-Session=$expected",
                calls.all { it.attemptSessionHeader == expected },
            )
        }
        engine.close()
    }

    @Test
    fun claimFailures() = runTest {
        val timeUpAttempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        server.enqueueFixtureError(AttemptOperation.CLAIM_SESSION, "claimSession", 409, "time-up")
        server.onRequest(AttemptOperation.CLAIM_SESSION) {
            server.closeAttemptOnServer(AttemptStateResponse.State.GRADING)
        }
        val (timeUpEngine, _) = newEngineHarness(server = server, storeRoot = storeRoot)
        runCurrent()
        assertEquals(AttemptClosedReason.TimeUp, timeUpEngine.state.value.closed)
        assertTrue(server.callCount(AttemptOperation.GET_STATE) >= 2)
        timeUpEngine.close()

        val notOpenServer = FakeAttemptServer()
        notOpenServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        notOpenServer.enqueueFixtureError(AttemptOperation.CLAIM_SESSION, "claimSession", 409, "attempt-not-open")
        notOpenServer.onRequest(AttemptOperation.CLAIM_SESSION) {
            notOpenServer.closeAttemptOnServer(AttemptStateResponse.State.SCORED)
        }
        val (notOpenEngine, _) = newEngineHarness(
            server = notOpenServer,
            storeRoot = createTempStoreRoot("thi360-not-open-"),
        )
        runCurrent()
        assertNotNull(notOpenEngine.state.value.closed)
        assertTrue(notOpenServer.callCount(AttemptOperation.GET_STATE) >= 2)
        notOpenEngine.close()

        val retryServer = FakeAttemptServer()
        val retryAttempt = retryServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val openPart = retryAttempt.firstOpenPart()
        val firstItem = retryAttempt.firstLeafItem()
        val fixedSid = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
        val connectivity = MutableConnectivityMonitor(true)

        retryServer.enqueueNetworkFailure(AttemptOperation.CLAIM_SESSION, count = 1)
        repeat(7) { retryServer.enqueueServerError(AttemptOperation.CLAIM_SESSION, 503) }

        val retryRoot = createTempStoreRoot("thi360-claim-backoff-")
        try {
            val (retryEngine, _) = newEngineHarness(
                server = retryServer,
                storeRoot = retryRoot,
                connectivity = connectivity,
                uuidGenerator = { fixedSid },
            )
            runCurrent()

            assertEquals(1, retryServer.callCount(AttemptOperation.CLAIM_SESSION))
            assertEquals(EditSessionState.Acquiring, retryEngine.state.value.session)
            assertEquals(SaveStatus.Unsaved(UnsavedReason.NetworkError), retryEngine.state.value.save)

            retryEngine.answer(firstItem.itemId!!, SingleChoiceAnswer("b"))
            retryEngine.finishPart(openPart.partId!!)
            retryEngine.submit()
            runCurrent()
            assertEquals(0, retryServer.callCount(AttemptOperation.SAVE_ANSWERS))
            assertEquals(0, retryServer.callCount(AttemptOperation.FINISH_PART))
            assertEquals(0, retryServer.callCount(AttemptOperation.SUBMIT))

            val expectedDelays = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L)
            for ((index, delayMs) in expectedDelays.withIndex()) {
                val callsBefore = retryServer.callCount(AttemptOperation.CLAIM_SESSION)
                advanceTimeBy(delayMs - 1L)
                runCurrent()
                assertEquals(
                    "Claim must not fire early before ${delayMs}ms (step $index)",
                    callsBefore,
                    retryServer.callCount(AttemptOperation.CLAIM_SESSION),
                )
                advanceTimeBy(1L)
                runCurrent()
                assertEquals(
                    "Claim must fire at exact ${delayMs}ms (step $index)",
                    callsBefore + 1,
                    retryServer.callCount(AttemptOperation.CLAIM_SESSION),
                )
                assertEquals(EditSessionState.Acquiring, retryEngine.state.value.session)
                assertEquals(SaveStatus.Unsaved(UnsavedReason.ServerError), retryEngine.state.value.save)
            }

            connectivity.setOnline(false)
            runCurrent()
            assertEquals(SaveStatus.Offline, retryEngine.state.value.save)

            connectivity.setOnline(true)
            runCurrent()

            assertEquals(9, retryServer.callCount(AttemptOperation.CLAIM_SESSION))
            assertEquals(EditSessionState.Editing(fixedSid), retryEngine.state.value.session)
            assertEquals(1, retryServer.callCount(AttemptOperation.SAVE_ANSWERS))
            assertEquals(SaveStatus.Saved, retryEngine.state.value.save)

            val sentSids = retryServer.calls(AttemptOperation.CLAIM_SESSION).map { call ->
                Serializer.kotlinxSerializationJson.parseToJsonElement(call.body!!)
                    .jsonObject["sessionId"]?.jsonPrimitive?.content
            }
            assertTrue(sentSids.all { it == fixedSid.toString() })
            retryEngine.close()
        } finally {
            retryRoot.deleteRecursively()
        }
    }

    @Test
    fun openElsewhereAndTakeover() = runTest {
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val openPart = attempt.firstOpenPart()
        val firstItem = attempt.firstLeafItem()
        val otherSeenAt = OffsetDateTime.parse("2026-11-20T09:58:00Z")
        val otherSid = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc")
        val localSid = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd")
        server.simulateTakeoverByOtherDevice(otherSessionId = otherSid, seenAt = otherSeenAt)
        server.takeoverGeneratesNewSessionId = true

        val (engine, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            uuidGenerator = { localSid },
        )
        runCurrent()

        assertEquals(EditSessionState.OpenElsewhere(otherSeenAt), engine.state.value.session)

        advanceTimeBy(600_000L)
        runCurrent()
        assertEquals(EditSessionState.OpenElsewhere(otherSeenAt), engine.state.value.session)
        assertEquals(1, server.callCount(AttemptOperation.CLAIM_SESSION))

        val beforeAnswers = engine.state.value.answers
        val firstItemId = firstItem.itemId!!
        engine.answer(firstItemId, SingleChoiceAnswer("a"))
        engine.flag(firstItemId, true)
        engine.choosePart(openPart.partId!!, "SCIENCE")
        runCurrent()
        assertEquals(beforeAnswers, engine.state.value.answers)
        assertEquals(0, server.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(0, server.callCount(AttemptOperation.CONFIRM_PART_CHOICE))

        val takeoverGate = server.gateNext(AttemptOperation.CLAIM_SESSION)
        engine.takeover()
        engine.takeover()
        runCurrent()
        assertEquals(2, server.callCount(AttemptOperation.CLAIM_SESSION))
        assertEquals(CommandState.Sending(AttemptCommandKind.Takeover), engine.state.value.command)

        val takeoverReq = Serializer.kotlinxSerializationJson.parseToJsonElement(
            server.calls(AttemptOperation.CLAIM_SESSION).last().body!!,
        ).jsonObject
        assertEquals("true", takeoverReq["takeover"]?.jsonPrimitive?.content)
        assertEquals(localSid.toString(), takeoverReq["sessionId"]?.jsonPrimitive?.content)

        val getCountBefore = server.callCount(AttemptOperation.GET_STATE)
        takeoverGate.complete(Unit)
        runCurrent()

        val editing = engine.state.value.session as EditSessionState.Editing
        assertFalse("Server generated new sessionId should be adopted", editing.sessionId == otherSid)
        assertEquals(
            editing.sessionId,
            DraftStore(AccountScopedStore(storeRoot, "account-A")).readSessionId(attempt.attemptId),
        )
        assertTrue("Successful takeover must reload attempt state", server.callCount(AttemptOperation.GET_STATE) > getCountBefore)
        assertEquals(CommandState.Idle, engine.state.value.command)

        val closedPart = attempt.parts!!.first { it.state != AttemptPartStateView.State.OPEN }
        val closedPartItemId = closedPart.items?.firstOrNull()?.itemId
        if (closedPartItemId != null) {
            val savesBefore = server.callCount(AttemptOperation.SAVE_ANSWERS)
            engine.answer(closedPartItemId, SingleChoiceAnswer("a"))
            engine.flag(closedPartItemId, true)
            runCurrent()
            assertEquals(savesBefore, server.callCount(AttemptOperation.SAVE_ANSWERS))
        }
        engine.close()

        val closedServer = FakeAttemptServer()
        val closedAttempt = closedServer.loadStateFixture("closed-submitted")
        val closedItem = closedAttempt.allLeafItems().first()
        val (closedEngine, _) = newEngineHarness(
            server = closedServer,
            storeRoot = createTempStoreRoot("thi360-closed-ro-"),
        )
        runCurrent()
        val closedItemId = closedItem.itemId!!
        closedEngine.answer(closedItemId, SingleChoiceAnswer("a"))
        closedEngine.flag(closedItemId, true)
        closedEngine.choosePart(closedAttempt.parts!!.first().partId!!, "SCIENCE")
        runCurrent()
        assertEquals(0, closedServer.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(0, closedServer.callCount(AttemptOperation.CONFIRM_PART_CHOICE))
        closedEngine.close()
    }
}
