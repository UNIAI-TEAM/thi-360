package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.AppLogger
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.model.AnswerChange
import vn.unicom.thi360.core.network.generated.model.AttemptPosition
import vn.unicom.thi360.core.network.generated.model.ClaimSessionRequest
import vn.unicom.thi360.core.network.generated.model.SaveAnswersRequest
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.ContractFixtureIndex

class AttemptTokenRefreshTest {

    private lateinit var storeRoot: File
    private lateinit var server: FakeAttemptServer

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-attempt-refresh-")
        server = FakeAttemptServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        storeRoot.deleteRecursively()
    }

    @Test
    fun threeConcurrentAttemptCallsHitting401TriggerSingleRefreshAndAllSucceed() = runBlocking {
        val expiredToken = "expired-attempt-access-token"
        val refreshedToken = "fresh-attempt-access-token"
        val refreshCount = AtomicInteger(0)
        val unauthorizedBarrier = CountDownLatch(3)

        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val openPart = attempt.firstOpenPart()
        val itemId = attempt.firstLeafItem().itemId!!

        val refreshBody = ContractFixtureLoader.readAuthFixture("refresh-200.json")
            .replace("fixture-access-token", refreshedToken)
        val unauthorizedBody = ContractFixtureIndex.find("downloadAttemptAsset", 401, "unauthenticated")
            .file.readText(Charsets.UTF_8)

        server.contractServer.respondDynamic("POST", "/api/v1/auth/mobile/refresh") {
            refreshCount.incrementAndGet()
            Thread.sleep(100)
            ContractFakeServer.jsonResponse(200, refreshBody)
        }

        val tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = expiredToken,
                refreshToken = "valid-attempt-refresh-token",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
                accountId = "account-A",
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
        val api = server.openApiAttemptApi(stack)

        val sessionId = java.util.UUID.fromString("55555555-5555-4555-8555-555555555555")
        val claimed = api.claimSession(attempt.attemptId, ClaimSessionRequest(sessionId = sessionId))
        assertTrue(claimed is ClaimSessionApiResult.Claimed)

        val answersPath = "/api/v1/me/attempts/${attempt.attemptId}/answers"
        val heartbeatPath = "/api/v1/me/attempts/${attempt.attemptId}/heartbeat"
        val savedFixture = ContractFixtureIndex.find("saveAnswers", 200, "saved").file.readText(Charsets.UTF_8)
        val heartbeatFixture = ContractFixtureIndex.find("heartbeat", 200, null).file.readText(Charsets.UTF_8)

        server.contractServer.respondDynamic("PUT", answersPath) { req ->
            if (req.getHeader("Authorization") == "Bearer $refreshedToken") {
                ContractFakeServer.jsonResponse(200, savedFixture)
            } else {
                unauthorizedBarrier.countDown()
                unauthorizedBarrier.await(5, TimeUnit.SECONDS)
                ContractFakeServer.jsonResponse(401, unauthorizedBody)
            }
        }
        server.contractServer.respondDynamic("POST", heartbeatPath) { req ->
            if (req.getHeader("Authorization") == "Bearer $refreshedToken") {
                ContractFakeServer.jsonResponse(200, heartbeatFixture)
            } else {
                unauthorizedBarrier.countDown()
                unauthorizedBarrier.await(5, TimeUnit.SECONDS)
                ContractFakeServer.jsonResponse(401, unauthorizedBody)
            }
        }

        val results = listOf(
            async(Dispatchers.IO) {
                api.saveAnswers(
                    attemptId = attempt.attemptId,
                    sessionId = sessionId,
                    request = SaveAnswersRequest(
                        changes = listOf(AnswerChange(itemId = itemId, seq = 1L, answer = SingleChoiceAnswer("a"))),
                    ),
                )
            },
            async(Dispatchers.IO) {
                api.heartbeat(attemptId = attempt.attemptId, sessionId = sessionId)
            },
            async(Dispatchers.IO) {
                api.saveAnswers(
                    attemptId = attempt.attemptId,
                    sessionId = sessionId,
                    request = SaveAnswersRequest(
                        position = AttemptPosition(partId = openPart.partId, itemId = itemId),
                    ),
                )
            },
        ).awaitAll()

        assertEquals("Three concurrent 401 calls must trigger exactly one refresh", 1, refreshCount.get())
        assertTrue("All three retried calls must succeed", results.all { it is ApiResult.Success })
        assertEquals(refreshedToken, tokenStore.read()?.accessToken)
    }
}
