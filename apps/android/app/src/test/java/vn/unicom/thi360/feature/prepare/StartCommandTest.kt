package vn.unicom.thi360.feature.prepare

import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
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
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

class StartCommandTest {

    private lateinit var harness: PortalTestHarness
    private lateinit var api: PrepareApi
    private val entitlementId = UUID.fromString("00000000-0000-7000-8000-000000000001")

    @Before
    fun setUp() {
        harness = PortalTestHarness.create(
            initialTokens = Tokens(
                accessToken = "test-access-token",
                refreshToken = "test-refresh-token",
                accessExpiresIn = 3600,
                refreshExpiresIn = 86400,
                mustChangePassword = false,
                accountId = "acc-start-cmd",
                username = "hv01",
            ),
        )
        api = OpenApiPrepareApi(harness.stack.authenticatedService(AttemptsApi::class.java))
    }

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun keyBodyAndSingleFlight() = runBlocking {
        val startedLatch = CountDownLatch(1)
        val releaseLatch = CountDownLatch(1)

        harness.server.respondDynamicSequence(
            "POST",
            "/api/v1/me/attempts",
            listOf(
                { _ ->
                    startedLatch.countDown()
                    releaseLatch.await(3, TimeUnit.SECONDS)
                    ContractFakeServer.jsonResponse(202, PrepareTestFixtures.readStartRaw(202, "starting"))
                },
                { _ ->
                    MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setHeader("Content-Length", 128)
                        .setBody("{\"attemptId\":")
                        .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                },
                { _ ->
                    ContractFakeServer.jsonResponse(
                        408,
                        """{"code":"REQUEST_TIMEOUT","message":"timeout"}""",
                        headers = mapOf("Retry-After" to "5"),
                    )
                },
                { _ -> ContractFakeServer.jsonResponse(429, PrepareTestFixtures.readRateLimitedRaw()) },
                { _ -> ContractFakeServer.jsonResponse(503, """{"code":"SERVICE_UNAVAILABLE","message":"down"}""") },
                { _ -> ContractFakeServer.jsonResponse(409, PrepareTestFixtures.readStartRaw(409, "no-attempts-left")) },
                { _ -> ContractFakeServer.jsonResponse(422, PrepareTestFixtures.readStartRaw(422, "choice-not-allowed")) },
                { _ -> ContractFakeServer.jsonResponse(200, PrepareTestFixtures.readStartRaw(200, "resumed")) },
            ),
        )

        val command = StartCommand(api)
        val subjectChoice = PreChoiceInput(optionCode = "SCIENCE", subjectCodes = listOf("LY", "HOA", "SINH"))

        val firstJob = async(Dispatchers.IO) { command.start(entitlementId, subjectChoice) }
        assertTrue(startedLatch.await(3, TimeUnit.SECONDS))
        assertTrue(command.isInFlight)
        val duplicate = command.start(entitlementId, subjectChoice)
        assertNull(duplicate)

        releaseLatch.countDown()
        val firstOutcome = firstJob.await()
        assertTrue(firstOutcome is StartOutcome.Opened)
        assertEquals(StartOutcome.OpenedState.Starting, (firstOutcome as StartOutcome.Opened).state)
        assertEquals(1, harness.server.requestCount("POST", "/api/v1/me/attempts"))

        val firstReq = harness.server.requests("POST", "/api/v1/me/attempts")[0]
        val firstKey = firstReq.getHeader("Idempotency-Key")
        assertNotNull(firstKey)
        UUID.fromString(firstKey)

        val firstBody = Json.parseToJsonElement(firstReq.body.readUtf8()).jsonObject
        assertEquals(entitlementId.toString(), firstBody["entitlementId"]?.jsonPrimitive?.content)
        val preChoiceJson = firstBody["preChoice"]!!.jsonObject
        assertEquals("SCIENCE", preChoiceJson["optionCode"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("LY", "HOA", "SINH"),
            preChoiceJson["subjectCodes"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        val netFail = command.start(entitlementId, PreChoiceInput(optionCode = "ANH", subjectCodes = null))
        assertEquals(StartOutcome.Refused(StartRefusal.Generic), netFail)
        val keyAfterNetFail = command.currentKey
        assertNotNull(keyAfterNetFail)
        assertNotEquals(firstKey, keyAfterNetFail)

        val timeoutFail = command.start(entitlementId, PreChoiceInput(optionCode = "ANH", subjectCodes = null))
        assertEquals(StartOutcome.Refused(StartRefusal.Generic), timeoutFail)
        assertEquals(keyAfterNetFail, command.currentKey)

        val rateFail = command.start(entitlementId, PreChoiceInput(optionCode = "ANH", subjectCodes = null))
        assertEquals(StartOutcome.Refused(StartRefusal.RateLimited), rateFail)
        assertEquals(keyAfterNetFail, command.currentKey)

        val serverFail = command.start(entitlementId, PreChoiceInput(optionCode = "ANH", subjectCodes = null))
        assertEquals(StartOutcome.Refused(StartRefusal.Generic), serverFail)
        assertEquals(keyAfterNetFail, command.currentKey)

        val conflict409 = command.start(entitlementId, PreChoiceInput(optionCode = "ANH", subjectCodes = null))
        assertEquals(StartOutcome.Refused(StartRefusal.NoAttemptsLeft), conflict409)
        assertNull(command.currentKey)

        val unprocessable422 = command.start(entitlementId, null)
        assertEquals(StartOutcome.Refused(StartRefusal.ChoiceNotAllowed), unprocessable422)
        assertNull(command.currentKey)

        val resumed = command.start(entitlementId, null)
        assertTrue(resumed is StartOutcome.Opened)
        assertEquals(StartOutcome.OpenedState.InProgress, (resumed as StartOutcome.Opened).state)
        assertNull(command.currentKey)

        val allRequests = harness.server.requests("POST", "/api/v1/me/attempts")
        assertEquals(8, allRequests.size)
        val keys = allRequests.map { it.getHeader("Idempotency-Key")!! }
        assertNotEquals(keys[0], keys[1])
        assertEquals(keys[1], keys[2])
        assertEquals(keys[2], keys[3])
        assertEquals(keys[3], keys[4])
        assertEquals(keys[4], keys[5])
        assertNotEquals(keys[5], keys[6])
        assertNotEquals(keys[6], keys[7])

        val anhBody = Json.parseToJsonElement(allRequests[2].body.readUtf8()).jsonObject
        val anhPreChoice = anhBody["preChoice"]!!.jsonObject
        assertEquals("ANH", anhPreChoice["optionCode"]?.jsonPrimitive?.content)
        val subjectCodesNode = anhPreChoice["subjectCodes"]
        assertTrue(subjectCodesNode == null || subjectCodesNode is JsonNull || (subjectCodesNode is JsonArray && subjectCodesNode.isEmpty()))

        val noChoiceBody = Json.parseToJsonElement(allRequests[7].body.readUtf8()).jsonObject
        assertEquals(entitlementId.toString(), noChoiceBody["entitlementId"]?.jsonPrimitive?.content)
        val noChoiceNode = noChoiceBody["preChoice"]
        assertTrue(noChoiceNode == null || noChoiceNode is JsonNull)
    }
}
