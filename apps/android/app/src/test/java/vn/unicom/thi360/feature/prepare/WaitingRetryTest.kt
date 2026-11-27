package vn.unicom.thi360.feature.prepare

import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.generated.api.AttemptsApi
import vn.unicom.thi360.core.network.generated.model.PreChoiceInput
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.prepare.waiting.RetryPreChoiceResolution
import vn.unicom.thi360.feature.prepare.waiting.WaitingRetry
import vn.unicom.thi360.feature.prepare.waiting.WaitingRetryOutcome
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

class WaitingRetryTest {

    private lateinit var harness: PortalTestHarness
    private lateinit var api: PrepareApi
    private lateinit var store: StartContextStore

    private val entitlementId = UUID.fromString("00000000-0000-7000-8000-000000000001")
    private val oldAttemptId = UUID.fromString("00000000-0000-7000-8000-000000000301")
    private val newAttemptId = UUID.fromString("00000000-0000-7000-8000-000000000302")

    @Before
    fun setUp() {
        harness = PortalTestHarness.create(
            initialTokens = Tokens(
                accessToken = "test-access-token",
                refreshToken = "test-refresh-token",
                accessExpiresIn = 3600,
                refreshExpiresIn = 86400,
                mustChangePassword = false,
                accountId = "acc-waiting-retry",
                username = "hv01",
            ),
        )
        api = OpenApiPrepareApi(harness.stack.authenticatedService(AttemptsApi::class.java))
        store = StartContextStore(harness.accountScopedStoreProvider)
    }

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun resolvesPreparePreChoiceInContractPriorityOrder() {
        val inMemory = PreChoiceInput(optionCode = "MEM", subjectCodes = listOf("LY"))
        val saved = PreChoiceInput(optionCode = "SAVED", subjectCodes = null)
        val noPreChoice = PrepareTestFixtures.loadPrepare("per-part")
        val singleBranch = PrepareTestFixtures.loadPrepare("pre-choice")
        val multiOptions = PrepareTestFixtures.prepareWithSubjectChoice()
        val singleSubjectChoice = multiOptions.copy(
            preChoice = multiOptions.preChoice!!.copy(
                options = listOf(multiOptions.preChoice!!.options!!.first()),
            ),
        )

        assertEquals(
            RetryPreChoiceResolution.Direct(inMemory),
            WaitingRetry.resolvePreparePreChoice(inMemory, saved, multiOptions),
        )
        assertEquals(
            RetryPreChoiceResolution.Direct(saved),
            WaitingRetry.resolvePreparePreChoice(null, saved, multiOptions),
        )
        assertEquals(
            RetryPreChoiceResolution.Direct(null),
            WaitingRetry.resolvePreparePreChoice(null, null, noPreChoice),
        )
        assertEquals(
            RetryPreChoiceResolution.Direct(PreChoiceInput(optionCode = "CS", subjectCodes = null)),
            WaitingRetry.resolvePreparePreChoice(null, null, singleBranch),
        )
        assertEquals(
            RetryPreChoiceResolution.MustReturnToPrepare,
            WaitingRetry.resolvePreparePreChoice(null, null, multiOptions),
        )
        assertEquals(
            RetryPreChoiceResolution.MustReturnToPrepare,
            WaitingRetry.resolvePreparePreChoice(null, null, singleSubjectChoice),
        )
    }

    @Test
    fun prepareOriginRetriesWithNewIdempotencyKeyMovesContextAndHandlesReloadOrRefusal() = runBlocking {
        val start202 = PrepareTestFixtures.loadStart(202, "starting")
        val firstResponse = start202.copy(attemptId = oldAttemptId)
        val retryResponse = start202.copy(attemptId = newAttemptId)

        harness.server.respondDynamicSequence(
            "POST",
            "/api/v1/me/attempts",
            listOf(
                { _ -> ContractFakeServer.jsonResponse(202, PrepareTestFixtures.encodeStart(firstResponse)) },
                { _ -> ContractFakeServer.jsonResponse(202, PrepareTestFixtures.encodeStart(retryResponse)) },
                { _ -> ContractFakeServer.jsonResponse(409, PrepareTestFixtures.readStartRaw(409, "no-attempts-left")) },
            ),
        )

        val startCommand = StartCommand(api)
        val retry = WaitingRetry(startCommand, store)
        val initialPreChoice = PreChoiceInput(optionCode = "CS", subjectCodes = null)
        val ctx = StartContext(
            entitlementId = entitlementId.toString(),
            preChoice = initialPreChoice,
            examTitle = "SPT Tin học (thu nhỏ)",
            examKindLabel = "SPT",
            firstPartName = "Phần I · Trắc nghiệm",
            firstPartMinutes = 30,
            timing = "PER_PART",
        )

        val opened = startCommand.start(entitlementId, initialPreChoice)
        assertTrue(opened is StartOutcome.Opened)
        store.save(oldAttemptId, ctx)

        val restarted = retry.retry(
            attemptId = oldAttemptId,
            origin = WaitingOrigin.Prepare,
            entitlementId = entitlementId,
            prepareResponse = PrepareTestFixtures.loadPrepare("pre-choice"),
            inMemoryPreChoice = initialPreChoice,
            inMemoryContext = ctx,
        )

        assertTrue(restarted is WaitingRetryOutcome.Restarted)
        assertEquals(newAttemptId, (restarted as WaitingRetryOutcome.Restarted).newAttemptId)
        assertNull(store.read(oldAttemptId))
        assertEquals(ctx, store.read(newAttemptId))

        val refused = retry.retry(
            attemptId = newAttemptId,
            origin = WaitingOrigin.Prepare,
            entitlementId = entitlementId,
            prepareResponse = PrepareTestFixtures.loadPrepare("pre-choice"),
            inMemoryPreChoice = initialPreChoice,
            inMemoryContext = ctx,
        )
        assertEquals(WaitingRetryOutcome.Refused(StartRefusal.NoAttemptsLeft), refused)

        val requests = harness.server.requests("POST", "/api/v1/me/attempts")
        assertEquals(3, requests.size)
        val key1 = requests[0].getHeader("Idempotency-Key")
        val key2 = requests[1].getHeader("Idempotency-Key")
        assertNotNull(key1)
        assertNotNull(key2)
        assertNotEquals(key1, key2)

        val reloadOutcome = retry.retry(
            attemptId = UUID.fromString("00000000-0000-7000-8000-000000000399"),
            origin = WaitingOrigin.Prepare,
            entitlementId = entitlementId,
            prepareResponse = PrepareTestFixtures.prepareWithSubjectChoice(),
            inMemoryPreChoice = null,
            inMemoryContext = null,
        )
        assertEquals(WaitingRetryOutcome.ReloadPrepare, reloadOutcome)
        assertEquals(3, harness.server.requestCount("POST", "/api/v1/me/attempts"))
    }

    @Test
    fun playerOriginHandlesMissingContextChoiceRefusalsAndRestart() = runBlocking {
        val retryResponse = PrepareTestFixtures.loadStart(202, "starting").copy(attemptId = newAttemptId)
        harness.server.respondDynamicSequence(
            "POST",
            "/api/v1/me/attempts",
            listOf(
                { _ -> ContractFakeServer.jsonResponse(422, PrepareTestFixtures.readStartRaw(422, "choice-not-allowed")) },
                { _ -> ContractFakeServer.jsonResponse(422, PrepareTestFixtures.readStartRaw(422, "subjects-invalid")) },
                { _ -> ContractFakeServer.jsonResponse(202, PrepareTestFixtures.encodeStart(retryResponse)) },
            ),
        )

        val retry = WaitingRetry(StartCommand(api), store)

        val missingOutcome = retry.retry(
            attemptId = oldAttemptId,
            origin = WaitingOrigin.Player,
        )
        assertEquals(WaitingRetryOutcome.GoToMyExams, missingOutcome)
        assertEquals(0, harness.server.requestCount("POST", "/api/v1/me/attempts"))

        val savedCtx = StartContext(
            entitlementId = entitlementId.toString(),
            preChoice = PreChoiceInput(optionCode = "SCIENCE", subjectCodes = listOf("LY", "HOA", "SINH")),
            examTitle = "HSA đầy đủ (thu nhỏ)",
            examKindLabel = "HSA",
            firstPartName = "Toán học",
            firstPartMinutes = 5,
            timing = "PER_PART",
        )
        store.save(oldAttemptId, savedCtx)

        val choiceNotAllowed = retry.retry(attemptId = oldAttemptId, origin = WaitingOrigin.Player)
        assertEquals(WaitingRetryOutcome.OpenPrepare(entitlementId.toString()), choiceNotAllowed)

        val subjectsInvalid = retry.retry(attemptId = oldAttemptId, origin = WaitingOrigin.Player)
        assertEquals(WaitingRetryOutcome.OpenPrepare(entitlementId.toString()), subjectsInvalid)

        val restarted = retry.retry(attemptId = oldAttemptId, origin = WaitingOrigin.Player)
        assertTrue(restarted is WaitingRetryOutcome.Restarted)
        assertEquals(newAttemptId, (restarted as WaitingRetryOutcome.Restarted).newAttemptId)
        assertNull(store.read(oldAttemptId))
        assertEquals(savedCtx, store.read(newAttemptId))

        val body = Json.parseToJsonElement(
            harness.server.requests("POST", "/api/v1/me/attempts").last().body.readUtf8(),
        ).jsonObject
        assertEquals(entitlementId.toString(), body["entitlementId"]?.jsonPrimitive?.content)
        assertEquals("SCIENCE", body["preChoice"]?.jsonObject?.get("optionCode")?.jsonPrimitive?.content)
    }
}
