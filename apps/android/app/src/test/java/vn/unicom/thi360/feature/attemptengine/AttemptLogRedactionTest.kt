package vn.unicom.thi360.feature.attemptengine

import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLog
import vn.unicom.thi360.BuildConfig
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.AndroidAppLogger
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.model.AnswerChange
import vn.unicom.thi360.core.network.generated.model.ClaimSessionRequest
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.FinalAnswerChange
import vn.unicom.thi360.core.network.generated.model.SaveAnswersRequest
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SubmitAttemptRequest
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.storage.Tokens

@RunWith(RobolectricTestRunner::class)
class AttemptLogRedactionTest {

    private lateinit var storeRoot: File
    private lateinit var server: FakeAttemptServer

    @Before
    fun setUp() {
        ShadowLog.clear()
        storeRoot = createTempStoreRoot("thi360-log-redaction-")
        server = FakeAttemptServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        storeRoot.deleteRecursively()
    }

    @Test
    fun demoLogsDuringClaimSaveAndSubmitNeverContainAnswersTokensOrAttemptSessionHeader() = runBlocking {
        val secretAccessToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJkZW1vLXN0dWRlbnQifQ.secretAccessSig987"
        val secretRefreshToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJkZW1vLXJlZnJlc2gifQ.secretRefreshSig654"
        val secretSessionId = UUID.fromString("99999999-8888-4777-8666-555555555555")
        val secretShortAnswer = "TOP_SECRET_SHORT_ANSWER_2026"
        val secretEssayHtml = "<p>TOP_SECRET_ESSAY_HTML_CONTENT_999</p>"
        val secretFinalAnswer = "TOP_SECRET_FINAL_SUBMIT_ANSWER_777"

        val attempt = server.loadStateFixture("in-progress-shared") { it.withFutureDeadlines() }
        val items = attempt.allLeafItems()
        val item1 = items[0].itemId!!
        val item2 = items[1].itemId!!

        val env = if (BuildConfig.FLAVOR == "demo") Environment.DEMO else AppEnvironment.environment
        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = secretAccessToken,
                refreshToken = secretRefreshToken,
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
                accountId = "account-demo",
            ),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = env,
            tokenStore = tokenStore,
            localeProvider = { AppLocale.VI },
            resolveAccountIdOnAuth = false,
        )
        val api = server.openApiAttemptApi(stack)

        api.getState(attempt.attemptId)
        api.claimSession(attempt.attemptId, ClaimSessionRequest(sessionId = secretSessionId))
        api.heartbeat(attempt.attemptId, secretSessionId)

        server.enqueueRateLimited(AttemptOperation.SAVE_ANSWERS, retryAfterHeader = "2")
        server.enqueueServerError(AttemptOperation.SAVE_ANSWERS, 503)
        val saveReq = SaveAnswersRequest(
            changes = listOf(
                AnswerChange(itemId = item1, seq = 1L, answer = ShortAnswerValue(text = secretShortAnswer)),
                AnswerChange(itemId = item2, seq = 1L, answer = EssayAnswer(html = secretEssayHtml)),
            ),
        )
        api.saveAnswers(attempt.attemptId, secretSessionId, saveReq)
        api.saveAnswers(attempt.attemptId, secretSessionId, saveReq)
        api.saveAnswers(attempt.attemptId, secretSessionId, saveReq)

        api.submit(
            attemptId = attempt.attemptId,
            sessionId = secretSessionId,
            idempotencyKey = UUID.randomUUID().toString(),
            request = SubmitAttemptRequest(
                finalAnswers = listOf(
                    FinalAnswerChange(
                        itemId = item1,
                        seq = 2L,
                        answer = ShortAnswerValue(text = secretFinalAnswer),
                    ),
                ),
            ),
        )

        val rawDiagnostic =
            """Authorization: Bearer $secretAccessToken X-Attempt-Session: $secretSessionId """ +
                """{"sessionId":"$secretSessionId","answer":{"type":"SHORT_ANSWER","text":"$secretShortAnswer"},""" +
                """"finalAnswers":[{"itemId":"$item1","seq":2,"answer":{"type":"ESSAY","html":"$secretEssayHtml"}}]}"""
        val redactedDiagnostic = AttemptLogRedactor.redact(rawDiagnostic)
        AndroidAppLogger.warn("Thi360Attempt", redactedDiagnostic)

        val localAnswerStr = LocalAnswer(
            value = ShortAnswerValue(text = secretShortAnswer),
            seq = 1L,
            flagged = false,
            sync = AnswerSyncStatus.Pending,
        ).toString()
        val sessionStateStr = EditSessionState.Editing(secretSessionId).toString()
        val engineStateStr = EngineState(
            attempt = attempt,
            answers = mapOf(
                item1 to LocalAnswer(
                    value = EssayAnswer(html = secretEssayHtml),
                    seq = 1L,
                    flagged = false,
                    sync = AnswerSyncStatus.Pending,
                ),
            ),
            session = EditSessionState.Editing(secretSessionId),
        ).toString()

        val shadowLogs = ShadowLog.getLogs()
        val httpLogs = shadowLogs.filter { it.tag == "Thi360Http" }
        assertTrue("Expected Thi360Http logs for >= 400 responses", httpLogs.isNotEmpty())
        assertTrue(
            "Expected 429 response logged by Thi360Http",
            httpLogs.any { it.msg.contains("-> 429") },
        )
        assertTrue(
            "Expected 503 response logged as WARN by Thi360Http",
            httpLogs.any { it.type == Log.WARN && it.msg.contains("-> 503") },
        )
        if (env == Environment.DEMO) {
            assertTrue(
                "DEMO flavor must suppress < 400 HTTP logs",
                httpLogs.none { it.msg.contains("-> 200") },
            )
        }

        val allOutputs = shadowLogs.flatMap { entry ->
            listOfNotNull(entry.msg, entry.throwable?.stackTraceToString())
        } + listOf(redactedDiagnostic, localAnswerStr, sessionStateStr, engineStateStr, tokenStore.read().toString())

        val forbiddenSecrets = listOf(
            secretAccessToken,
            secretRefreshToken,
            secretSessionId.toString(),
            secretShortAnswer,
            secretEssayHtml,
            "TOP_SECRET_ESSAY_HTML_CONTENT_999",
            secretFinalAnswer,
        )

        for (secret in forbiddenSecrets) {
            for (output in allOutputs) {
                assertFalse("Leaked secret '$secret' in output: $output", output.contains(secret))
            }
        }
        assertTrue(localAnswerStr.contains("[REDACTED]"))
        assertTrue(sessionStateStr.contains("[REDACTED]"))
        assertTrue(redactedDiagnostic.contains("[REDACTED]"))
    }
}
