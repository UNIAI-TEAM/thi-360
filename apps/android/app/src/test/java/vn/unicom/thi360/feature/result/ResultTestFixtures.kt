package vn.unicom.thi360.feature.result

import kotlinx.serialization.builtins.ListSerializer
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.AppLogger
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.network.generated.model.CatalogOptionResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.ContractFixtureIndex

internal object ResultTestFixtures {
    const val ATTEMPT_UUID_1 = "00000000-0000-7000-8000-000000000001"
    const val ATTEMPT_UUID_2 = "00000000-0000-7000-8000-000000000002"
    const val ATTEMPT_UUID_3 = "00000000-0000-7000-8000-000000000003"

    val json = Serializer.kotlinxSerializationJson

    fun readIndexedRaw(operationId: String, statusCode: Int, variant: String? = null): String {
        val entry = if (variant != null) {
            ContractFixtureIndex.loadAll().firstOrNull {
                it.operationId == operationId && it.statusCode == statusCode && it.variant == variant
            } ?: error("No indexed fixture matching operationId=$operationId status=$statusCode exact variant=$variant")
        } else {
            ContractFixtureIndex.find(operationId, statusCode, null)
        }
        return entry.file.readText(Charsets.UTF_8)
    }

    fun loadResult(variant: String): AttemptResultResponse =
        json.decodeFromString(
            AttemptResultResponse.serializer(),
            readIndexedRaw("getResult", 200, variant),
        )

    fun loadReview(variant: String): AttemptReviewResponse =
        json.decodeFromString(
            AttemptReviewResponse.serializer(),
            readIndexedRaw("getReview", 200, variant),
        )

    fun loadCatalogOptions(variant: String): Map<String, CatalogOptionItem> {
        val raw = readIndexedRaw("getOptions", 200, variant)
        val dtos = json.decodeFromString(ListSerializer(CatalogOptionResponse.serializer()), raw)
        return dtos.associate { dto ->
            dto.code to CatalogOptionItem(
                code = dto.code,
                nameVi = dto.nameVi,
                nameEn = dto.nameEn ?: dto.nameVi,
            )
        }
    }

    fun encodeResult(response: AttemptResultResponse): String =
        json.encodeToString(AttemptResultResponse.serializer(), response)

    fun encodeReview(response: AttemptReviewResponse): String =
        json.encodeToString(AttemptReviewResponse.serializer(), response)

    /**
     * Plan A6 unproducible-response mutation documented per contracts/AND-RESULT.md Orchestrator Addendum:
     * Live backend always computes non-null scaleMax on SCORED; remove scaleMax (and optionally rawMaxScore)
     * from certified `getResult` 200 `scored` fixture.
     */
    fun scoredWithoutScaleMax(removeRawMaxScore: Boolean = false): AttemptResultResponse {
        val base = loadResult("scored")
        return base.copy(
            scaleMax = null,
            rawMaxScore = if (removeRawMaxScore) null else base.rawMaxScore,
        )
    }

    fun installCatalogRoutes(server: ContractFakeServer) {
        val examRaw = readIndexedRaw("getOptions", 200, "exam")
        val subjectRaw = readIndexedRaw("getOptions", 200, "subject")
        server.respondDynamic("GET", "/api/v1/catalog/options") { req ->
            val group = req.requestUrl?.queryParameter("group")?.uppercase()
            when (group) {
                "EXAM" -> ContractFakeServer.jsonResponse(200, examRaw)
                "SUBJECT" -> ContractFakeServer.jsonResponse(200, subjectRaw)
                else -> ContractFakeServer.jsonResponse(404, """{"code":"NOT_FOUND","message":"unknown group"}""")
            }
        }
    }

    fun createSignedInStack(
        server: ContractFakeServer,
        environment: Environment = Environment.DEV,
        accessToken: String = "test-access-token",
        refreshToken: String = "test-refresh-token",
        clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
        logger: AppLogger = NoOpLogger,
        sessionCleanup: SessionCleanup? = null,
    ): Thi360SessionStack = Thi360ApiFactory.createSessionStack(
        baseUrl = server.url("/"),
        environment = environment,
        tokenStore = InMemoryTokenStore(
            Tokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessExpiresIn = 3_600,
                refreshExpiresIn = 86_400,
                mustChangePassword = false,
            ),
        ),
        localeProvider = { AppLocale.VI },
        logger = logger,
        sessionCleanup = sessionCleanup,
        resolveAccountIdOnAuth = false,
        catalogOptionsClockMillis = clockMillis,
    )

    private object NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) = Unit
        override fun warn(tag: String, message: String) = Unit
    }
}
