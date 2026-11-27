package vn.unicom.thi360.feature.attemptengine

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.AppLogger
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.model.AnswerChange
import vn.unicom.thi360.core.network.generated.model.AttemptPosition
import vn.unicom.thi360.core.network.generated.model.ClaimSessionRequest
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.ConfirmPartChoiceRequest
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.FinalAnswerChange
import vn.unicom.thi360.core.network.generated.model.FinishPartRequest
import vn.unicom.thi360.core.network.generated.model.FlagChange
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.RetryPartRequest
import vn.unicom.thi360.core.network.generated.model.SaveAnswersRequest
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.SubmitAttemptRequest
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.storage.Tokens

class AttemptApiHttpTest {

    private lateinit var server: FakeAttemptServer
    private lateinit var api: AttemptApi

    @Before
    fun setUp() {
        server = FakeAttemptServer().start()
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = "http-test-access-token",
                refreshToken = "http-test-refresh-token",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.http",
                accountId = "account-http",
            ),
        )
        val noOpLogger = object : AppLogger {
            override fun debug(tag: String, message: String) = Unit
            override fun warn(tag: String, message: String) = Unit
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            logger = noOpLogger,
            resolveAccountIdOnAuth = false,
        )
        api = server.openApiAttemptApi(stack)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun realGeneratedClientDecodesAllAttemptStateVariantsAndSendsHeaders() = runBlocking {
        val variants = listOf(
            "in-progress-per-part",
            "in-progress-shared",
            "awaiting-choice",
            "generating",
            "generation-failed",
            "closed-submitted",
            "closed-time-up",
            "closed-choice-timeout",
            "aborted",
            "starting",
        )
        for (variant in variants) {
            val expected = server.loadStateFixture(variant)
            val result = api.getState(expected.attemptId)
            assertTrue("Expected Success decoding $variant, got $result", result is ApiResult.Success)
            assertEquals(expected.attemptId, (result as ApiResult.Success).data.attemptId)
        }

        val attempt50 = server.loadAttempt50Fixture()
        val result50 = api.getState(attempt50.attemptId)
        assertTrue(result50 is ApiResult.Success)
        assertEquals(50, (result50 as ApiResult.Success).data.allLeafItems().size)

        val getCall = server.calls(AttemptOperation.GET_STATE).last()
        assertEquals("Bearer http-test-access-token", getCall.headers["Authorization"])
        assertEquals("vi", getCall.headers["Accept-Language"])
    }

    @Test
    fun realGeneratedClientExecutesSessionHeartbeatSaveChoiceRetryFinishSubmitAndRetryAfter() = runBlocking {
        val attempt = server.loadAttempt50Fixture { it.withFutureDeadlines() }
        val openPart = attempt.firstOpenPart()
        val items = attempt.allLeafItems()
        val sessionId = UUID.fromString("aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee")

        val claimOk = api.claimSession(attempt.attemptId, ClaimSessionRequest(sessionId = sessionId))
        assertTrue(claimOk is ClaimSessionApiResult.Claimed)
        assertEquals(sessionId, (claimOk as ClaimSessionApiResult.Claimed).response.sessionId)

        val otherSid = UUID.fromString("bbbbbbbb-bbbb-4ccc-8ddd-eeeeeeeeeeee")
        val claimConflict = api.claimSession(attempt.attemptId, ClaimSessionRequest(sessionId = otherSid))
        assertTrue("Expected OpenElsewhere, got $claimConflict", claimConflict is ClaimSessionApiResult.OpenElsewhere)
        assertNotNull((claimConflict as ClaimSessionApiResult.OpenElsewhere).lastSeenAt)

        val hb = api.heartbeat(attempt.attemptId, sessionId)
        assertTrue(hb is ApiResult.Success)
        assertEquals(sessionId.toString(), server.calls(AttemptOperation.HEARTBEAT).single().attemptSessionHeader)

        val saveResp = api.saveAnswers(
            attemptId = attempt.attemptId,
            sessionId = sessionId,
            request = SaveAnswersRequest(
                changes = listOf(
                    AnswerChange(items[0].itemId!!, 1L, SingleChoiceAnswer("a")),
                    AnswerChange(items[1].itemId!!, 1L, MultipleChoiceAnswer(listOf("a", "b"))),
                    AnswerChange(items[2].itemId!!, 1L, ShortAnswerValue("42")),
                    AnswerChange(items[3].itemId!!, 1L, ClozeAnswer(mapOf("b1" to "hello"))),
                    AnswerChange(items[4].itemId!!, 1L, TrueFalseAnswer(mapOf("s1" to true))),
                    AnswerChange(items[5].itemId!!, 1L, MatchingAnswer(mapOf("l1" to "r1"))),
                    AnswerChange(items[6].itemId!!, 1L, EssayAnswer("<p>Essay</p>")),
                ),
                flags = listOf(FlagChange(flagged = true, itemId = items[0].itemId!!)),
                position = AttemptPosition(itemId = items[6].itemId, partId = openPart.partId),
            ),
        )
        assertTrue(saveResp is ApiResult.Success)
        assertEquals(7, (saveResp as ApiResult.Success).data.results.size)
        assertEquals(sessionId.toString(), server.calls(AttemptOperation.SAVE_ANSWERS).single().attemptSessionHeader)

        server.enqueueRateLimited(AttemptOperation.SAVE_ANSWERS, retryAfterHeader = "0")
        val rl0 = api.saveAnswers(attempt.attemptId, sessionId, SaveAnswersRequest())
        assertTrue(rl0 is ApiResult.HttpFailure)
        assertEquals(429, (rl0 as ApiResult.HttpFailure).statusCode)
        assertEquals(1L, rl0.retryAfterSeconds)

        server.enqueueRateLimited(AttemptOperation.SAVE_ANSWERS, retryAfterHeader = "5")
        val rl5 = api.saveAnswers(attempt.attemptId, sessionId, SaveAnswersRequest())
        assertEquals(5L, (rl5 as ApiResult.HttpFailure).retryAfterSeconds)

        server.enqueueRateLimited(AttemptOperation.SAVE_ANSWERS, retryAfterHeader = null)
        val rlNone = api.saveAnswers(attempt.attemptId, sessionId, SaveAnswersRequest())
        assertNull((rlNone as ApiResult.HttpFailure).retryAfterSeconds)

        val choiceKey = "choice-idem-key-1"
        val choiceResp = api.confirmPartChoice(
            attemptId = attempt.attemptId,
            partId = openPart.partId!!,
            sessionId = sessionId,
            idempotencyKey = choiceKey,
            request = ConfirmPartChoiceRequest(optionCode = "SCIENCE", subjectCodes = listOf("LY", "HOA", "SU")),
        )
        assertTrue(choiceResp is ApiResult.Success)
        val choiceCall = server.calls(AttemptOperation.CONFIRM_PART_CHOICE).single()
        assertEquals(sessionId.toString(), choiceCall.attemptSessionHeader)
        assertEquals(choiceKey, choiceCall.idempotencyKeyHeader)

        val retryKey = "retry-idem-key-1"
        val openPartId = openPart.partId
        val retryResp = api.retryPartGeneration(
            attemptId = attempt.attemptId,
            partId = openPartId,
            sessionId = sessionId,
            idempotencyKey = retryKey,
            request = RetryPartRequest(),
        )
        assertTrue(retryResp is ApiResult.Success)
        val retryCall = server.calls(AttemptOperation.RETRY_PART_GENERATION).single()
        assertEquals(sessionId.toString(), retryCall.attemptSessionHeader)
        assertEquals(retryKey, retryCall.idempotencyKeyHeader)

        val finishResp = api.finishPart(
            attemptId = attempt.attemptId,
            partId = openPartId,
            sessionId = sessionId,
            request = FinishPartRequest(
                finalAnswers = listOf(FinalAnswerChange(items[0].itemId!!, 2L, SingleChoiceAnswer("b"))),
            ),
        )
        assertTrue(finishResp is ApiResult.Success)
        val finishCall = server.calls(AttemptOperation.FINISH_PART).single()
        assertEquals(sessionId.toString(), finishCall.attemptSessionHeader)
        assertNull(finishCall.idempotencyKeyHeader)

        val submitKey = "submit-idem-key-1"
        val submitResp = api.submit(
            attemptId = attempt.attemptId,
            sessionId = sessionId,
            idempotencyKey = submitKey,
            request = SubmitAttemptRequest(
                finalAnswers = listOf(FinalAnswerChange(items[1].itemId!!, 2L, ShortAnswerValue("done"))),
            ),
        )
        assertTrue(submitResp is ApiResult.Success)
        val submitCall = server.calls(AttemptOperation.SUBMIT).single()
        assertEquals(sessionId.toString(), submitCall.attemptSessionHeader)
        assertEquals(submitKey, submitCall.idempotencyKeyHeader)
    }
}
