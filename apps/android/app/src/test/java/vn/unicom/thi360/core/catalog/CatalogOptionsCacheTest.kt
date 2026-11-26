package vn.unicom.thi360.core.catalog

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.AppLogger
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.api.CatalogOptionsApi
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.ContractFixtureIndex

class CatalogOptionsCacheTest {

    private lateinit var server: ContractFakeServer
    private lateinit var api: CatalogOptionsApi

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(),
            localeProvider = { AppLocale.VI },
            logger = object : AppLogger {
                override fun debug(tag: String, message: String) = Unit
                override fun warn(tag: String, message: String) = Unit
            },
        )
        api = stack.publicService(CatalogOptionsApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun cachesExamAndSubjectIndependentlyForFiveMinutesAndNeverCachesErrors() = runTest {
        var nowMillis = 1_000_000L

        val subjectFixtureBody = ContractFixtureIndex.find("getOptions", 200, "subject")
            .file.readText(Charsets.UTF_8)

        server.respondIndexed(
            operationId = "getOptions",
            statusCode = 200,
            pathOverride = "/api/v1/catalog/options?group=EXAM",
            fixtureNameFilter = "exam",
        )
        server.respondSequence(
            "GET",
            "/api/v1/catalog/options?group=SUBJECT",
            { ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR"}""") },
            { ContractFakeServer.jsonResponse(200, subjectFixtureBody) },
        )

        val cache = CatalogOptionsCache.fromApi(
            api = api,
            clockMillis = { nowMillis },
        )

        val exam1 = cache.get(CatalogOptionType.EXAM)
        assertTrue(exam1 is ApiResult.Success)
        val examItems = (exam1 as ApiResult.Success).data
        assertTrue(examItems.isNotEmpty())
        assertEquals("HSA", examItems.first().code)
        assertEquals(1, server.requestCount("GET", "/api/v1/catalog/options?group=EXAM"))

        // 4m59s (299_000 ms): still within TTL -> cache hit, zero extra call.
        nowMillis += 299_000L
        val exam2 = cache.get(CatalogOptionType.EXAM)
        assertTrue(exam2 is ApiResult.Success)
        assertEquals(1, server.requestCount("GET", "/api/v1/catalog/options?group=EXAM"))

        // Exact 5m00s (300_000 ms): TTL expired -> refetches from server.
        nowMillis += 1_000L
        val exam3 = cache.get(CatalogOptionType.EXAM)
        assertTrue(exam3 is ApiResult.Success)
        assertEquals(2, server.requestCount("GET", "/api/v1/catalog/options?group=EXAM"))

        // Failed SUBJECT call is NOT cached; immediate next call fetches again and succeeds.
        val subjErr = cache.get(CatalogOptionType.SUBJECT)
        assertTrue(subjErr is ApiResult.HttpFailure)
        assertEquals(1, server.requestCount("GET", "/api/v1/catalog/options?group=SUBJECT"))

        val subjOk = cache.get(CatalogOptionType.SUBJECT)
        assertTrue(subjOk is ApiResult.Success)
        val subjItems = (subjOk as ApiResult.Success).data
        assertTrue(subjItems.isNotEmpty())
        assertEquals("TOAN", subjItems.first().code)
        assertEquals(2, server.requestCount("GET", "/api/v1/catalog/options?group=SUBJECT"))

        // Immediate third SUBJECT call hits cache.
        val subjCached = cache.get(CatalogOptionType.SUBJECT)
        assertTrue(subjCached is ApiResult.Success)
        assertEquals(2, server.requestCount("GET", "/api/v1/catalog/options?group=SUBJECT"))
    }
}
