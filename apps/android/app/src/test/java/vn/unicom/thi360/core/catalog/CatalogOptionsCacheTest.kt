package vn.unicom.thi360.core.catalog

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.safeApiCall
import vn.unicom.thi360.testing.ContractFakeServer

class CatalogOptionsCacheTest {

    private interface OptionsProbeApi {
        @GET("/api/v1/catalog/options")
        suspend fun options(@Query("type") type: String): Response<String>
    }

    @Serializable
    private data class WireOption(val code: String, val nameVi: String, val nameEn: String)

    private lateinit var server: ContractFakeServer
    private lateinit var api: OptionsProbeApi
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(OptionsProbeApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun cachesExamAndSubjectIndependentlyForFiveMinutesAndNeverCachesErrors() = runTest {
        var nowMillis = 1_000_000L

        server.respondJson(
            "GET",
            "/api/v1/catalog/options?type=EXAM",
            200,
            """[{"code":"TOEIC","nameVi":"TOEIC","nameEn":"TOEIC"}]""",
        )
        server.respondSequence(
            "GET",
            "/api/v1/catalog/options?type=SUBJECT",
            { ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR"}""") },
            { ContractFakeServer.jsonResponse(200, """[{"code":"MATH","nameVi":"Toán","nameEn":"Math"}]""") },
        )

        val cache = CatalogOptionsCache(
            fetcher = { type ->
                when (val raw = safeApiCall { api.options(type.queryValue) }) {
                    is ApiResult.Success -> {
                        val decoded = json.decodeFromString<List<WireOption>>(raw.data)
                            .map { CatalogOptionItem(it.code, it.nameVi, it.nameEn) }
                        ApiResult.Success(decoded)
                    }
                    is ApiResult.HttpFailure -> raw
                    is ApiResult.NetworkFailure -> raw
                }
            },
            clockMillis = { nowMillis },
        )

        // First EXAM read calls server once.
        val exam1 = cache.get(CatalogOptionType.EXAM)
        assertTrue(exam1 is ApiResult.Success)
        assertEquals("TOEIC", (exam1 as ApiResult.Success).data.single().code)
        assertEquals(1, server.requestCount("GET", "/api/v1/catalog/options?type=EXAM"))

        // At 4m59s (299_000 ms), still served from cache -> zero extra call.
        nowMillis += 299_000L
        val exam2 = cache.get(CatalogOptionType.EXAM)
        assertTrue(exam2 is ApiResult.Success)
        assertEquals(1, server.requestCount("GET", "/api/v1/catalog/options?type=EXAM"))

        // At exact 5m00s (300_000 ms), TTL expires -> calls server again.
        nowMillis += 1_000L
        val exam3 = cache.get(CatalogOptionType.EXAM)
        assertTrue(exam3 is ApiResult.Success)
        assertEquals(2, server.requestCount("GET", "/api/v1/catalog/options?type=EXAM"))

        // Failed SUBJECT call is NOT cached; immediate next call fetches again and succeeds.
        val subjErr = cache.get(CatalogOptionType.SUBJECT)
        assertTrue(subjErr is ApiResult.HttpFailure)
        assertEquals(1, server.requestCount("GET", "/api/v1/catalog/options?type=SUBJECT"))

        val subjOk = cache.get(CatalogOptionType.SUBJECT)
        assertTrue(subjOk is ApiResult.Success)
        assertEquals("MATH", (subjOk as ApiResult.Success).data.single().code)
        assertEquals(2, server.requestCount("GET", "/api/v1/catalog/options?type=SUBJECT"))

        // Immediate third SUBJECT call hits cache.
        val subjCached = cache.get(CatalogOptionType.SUBJECT)
        assertTrue(subjCached is ApiResult.Success)
        assertEquals(2, server.requestCount("GET", "/api/v1/catalog/options?type=SUBJECT"))
    }
}
