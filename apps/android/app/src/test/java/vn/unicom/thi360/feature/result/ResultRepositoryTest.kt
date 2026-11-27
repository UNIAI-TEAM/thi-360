package vn.unicom.thi360.feature.result

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.testing.ContractFakeServer

class ResultRepositoryTest {

    private lateinit var server: ContractFakeServer
    private var nowMillis: Long = 10_000L

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        nowMillis = 10_000L
        ResultSessionMemory.clearAllForTesting()
    }

    @After
    fun tearDown() {
        ResultSessionMemory.clearAllForTesting()
        server.shutdown()
    }

    @Test
    fun resultAlwaysCallsServerFreshWhileCatalogOptionsCacheReusesWithinFiveMinutes() = runTest {
        ResultTestFixtures.installCatalogRoutes(server)
        val scoredRaw = ResultTestFixtures.readIndexedRaw("getResult", 200, "scored")
        server.respondJson("GET", "/api/v1/me/attempts/${ResultTestFixtures.ATTEMPT_UUID_1}/result", 200, scoredRaw)
        server.respondJson("GET", "/api/v1/me/attempts/${ResultTestFixtures.ATTEMPT_UUID_2}/result", 200, scoredRaw)

        val stack = ResultTestFixtures.createSignedInStack(
            server = server,
            clockMillis = { nowMillis },
        )
        val repository = ResultRepository.fromSessionStack(
            sessionStack = stack,
            clockMillis = { nowMillis },
        )

        val first = repository.result(ResultTestFixtures.ATTEMPT_UUID_1)
        assertTrue(first is ApiResult.Success)
        val exams1 = repository.loadExamKinds()
        val subjects1 = repository.loadSubjects()
        assertTrue(exams1.containsKey("HSA"))
        assertTrue(subjects1.containsKey("TOAN"))

        nowMillis += 30_000L
        assertTrue(repository.result(ResultTestFixtures.ATTEMPT_UUID_1) is ApiResult.Success)
        assertTrue(repository.result(ResultTestFixtures.ATTEMPT_UUID_2) is ApiResult.Success)
        assertEquals(exams1, repository.loadExamKinds())
        assertEquals(subjects1, repository.loadSubjects())

        assertEquals(
            2,
            server.requestCount("GET", "/api/v1/me/attempts/${ResultTestFixtures.ATTEMPT_UUID_1}/result"),
        )
        assertEquals(
            1,
            server.requestCount("GET", "/api/v1/me/attempts/${ResultTestFixtures.ATTEMPT_UUID_2}/result"),
        )
        val catalogCalls = server.requests("GET", "/api/v1/catalog/options")
        assertEquals(2, catalogCalls.size)
        assertEquals(setOf("EXAM", "SUBJECT"), catalogCalls.map { it.requestUrl?.queryParameter("group") }.toSet())

        nowMillis += 301_000L
        assertTrue(repository.loadExamKinds().containsKey("HSA"))
        assertEquals(3, server.requestCount("GET", "/api/v1/catalog/options"))
    }

    @Test
    fun reviewResultCacheExpiresAfterSixtySecondsAndClearsOnSessionCleanup() = runTest {
        ResultTestFixtures.installCatalogRoutes(server)
        val scoredRaw = ResultTestFixtures.readIndexedRaw("getResult", 200, "scored")
        server.respondJson("GET", "/api/v1/me/attempts/${ResultTestFixtures.ATTEMPT_UUID_1}/result", 200, scoredRaw)

        val cleanupRoot = java.nio.file.Files.createTempDirectory("result-cleanup-test").toFile()
        val cleanup = SessionCleanup(cleanupRoot)
        try {
            val stack = ResultTestFixtures.createSignedInStack(
                server = server,
                clockMillis = { nowMillis },
                sessionCleanup = cleanup,
            )
            val memory = ResultSessionMemory.forSession(
                sessionStack = stack,
                sessionCleanup = cleanup,
                clockMillis = { nowMillis },
            )

            assertTrue(memory.repository.result(ResultTestFixtures.ATTEMPT_UUID_1) is ApiResult.Success)
            assertNotNull(memory.repository.cachedResultIfFresh(ResultTestFixtures.ATTEMPT_UUID_1))

            nowMillis += 60_000L
            assertNotNull(memory.repository.cachedResultIfFresh(ResultTestFixtures.ATTEMPT_UUID_1))

            nowMillis += 1L
            assertNull(memory.repository.cachedResultIfFresh(ResultTestFixtures.ATTEMPT_UUID_1))

            assertTrue(memory.repository.result(ResultTestFixtures.ATTEMPT_UUID_1) is ApiResult.Success)
            assertNotNull(memory.repository.cachedResultIfFresh(ResultTestFixtures.ATTEMPT_UUID_1))

            cleanup.onLogout("acc-1")
            assertNull(memory.repository.cachedResultIfFresh(ResultTestFixtures.ATTEMPT_UUID_1))
        } finally {
            cleanupRoot.deleteRecursively()
        }
    }

    @Test
    fun invalidAttemptIdReturns404WithoutNetworkCall() = runTest {
        val stack = ResultTestFixtures.createSignedInStack(server = server)
        val repository = ResultRepository.fromSessionStack(stack)

        val res = repository.result("not-a-uuid")
        assertTrue(res is ApiResult.HttpFailure && res.statusCode == 404)
        val rev = repository.review("not-a-uuid")
        assertTrue(rev is ApiResult.HttpFailure && rev.statusCode == 404)
        assertEquals(0, server.totalRequestCount)
    }
}
