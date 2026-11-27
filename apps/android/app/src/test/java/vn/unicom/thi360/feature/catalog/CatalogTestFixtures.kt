package vn.unicom.thi360.feature.catalog

import android.content.Context
import coil3.ImageLoader
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ContractFixtureLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.ExamAccessQueryResponse
import vn.unicom.thi360.core.network.generated.model.PageResponseProductCard
import vn.unicom.thi360.core.network.generated.model.ProductDetail
import vn.unicom.thi360.core.network.generated.model.StorefrontFilters
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

internal object CatalogTestFixtures {
    val json = Json { ignoreUnknownKeys = true }

    fun activeStudentTokens(accountId: String = "acc-catalog-1"): Tokens = Tokens(
        accessToken = "catalog-access-token",
        refreshToken = "catalog-refresh-token",
        accessExpiresIn = 300,
        refreshExpiresIn = 604800,
        mustChangePassword = false,
        username = "student.catalog",
        accountId = accountId,
    )

    fun filtersJson(): String = ContractFixtureLoader.readApiFixture("storefront-filters-200.json")

    fun filtersMutated(transform: (MutableMap<String, JsonElement>) -> Unit): String {
        val root = json.parseToJsonElement(filtersJson()).jsonObject.toMutableMap()
        transform(root)
        return JsonObject(root).toString()
    }

    fun filtersWithPartsJson(): String = filtersMutated { root ->
        root["parts"] = buildJsonArray {
            add(
                buildJsonObject {
                    put("kindCode", "HSA")
                    put("code", "P1")
                    put("name", "Tư duy định lượng")
                    put("count", 4)
                },
            )
            add(
                buildJsonObject {
                    put("kindCode", "QDA")
                    put("code", "Q1")
                    put("name", "Toán QDA")
                    put("count", 2)
                },
            )
        }
    }

    fun productsListJson(): String = ContractFixtureLoader.readApiFixture("storefront-products-list-200.json")

    fun productsEmptyJson(): String = ContractFixtureLoader.readApiFixture("storefront-products-list-200-empty.json")

    fun rateLimited429Json(): String =
        ContractFixtureLoader.readApiFixture("storefront-products-list-429-rate-limited.json")

    fun examDetailJson(transform: (MutableMap<String, JsonElement>) -> Unit = {}): String {
        val raw = ContractFixtureLoader.readApiFixture("storefront-products-detail-200-exam.json")
        val root = json.parseToJsonElement(raw).jsonObject.toMutableMap()
        transform(root)
        return JsonObject(root).toString()
    }

    fun bundleDetailJson(transform: (MutableMap<String, JsonElement>) -> Unit = {}): String {
        val raw = ContractFixtureLoader.readApiFixture("storefront-products-detail-200-bundle.json")
        val root = json.parseToJsonElement(raw).jsonObject.toMutableMap()
        transform(root)
        return JsonObject(root).toString()
    }

    // Plan A6 exception (AND-CAT test data): live backend does not emit 200 with saleState=UNAVAILABLE;
    // mutates only "saleState" on certified storefront-products-detail-200-bundle.json.
    fun bundleDetail200UnavailableJson(): String = bundleDetailJson { root ->
        root["saleState"] = JsonPrimitive("UNAVAILABLE")
    }

    fun gone410Json(blankName: Boolean = false): String {
        val raw = ContractFixtureLoader.readApiFixture("storefront-products-detail-410-product-discontinued.json")
        if (!blankName) return raw
        val root = json.parseToJsonElement(raw).jsonObject.toMutableMap()
        val product = root["product"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
        product["name"] = JsonPrimitive("")
        root["product"] = JsonObject(product)
        return JsonObject(root).toString()
    }

    fun notFound404Json(): String =
        ContractFixtureLoader.readApiFixture("storefront-products-detail-404-not-found.json")

    fun conflict409WithoutProductJson(): String =
        ContractFixtureLoader.readApiFixture("orders-create-409-product-unavailable.json")

    fun conflict409WithBundleProductJson(): String {
        val conflict = json.parseToJsonElement(conflict409WithoutProductJson()).jsonObject.toMutableMap()
        val bundle = json.parseToJsonElement(bundleDetailJson()).jsonObject
        conflict["product"] = bundle
        return JsonObject(conflict).toString()
    }

    fun examAccess200Json(): String = ContractFixtureLoader.readApiFixture("exam-access-query-200.json")

    fun examAccess400Json(): String =
        ContractFixtureLoader.readApiFixture("exam-access-query-400-validation-failed.json")

    fun productsPageFromCertifiedCard(
        codes: List<String>,
        page: Int,
        totalItems: Long,
        totalPages: Int,
        size: Int = CatalogQuery.CATALOG_PAGE_SIZE,
        cardMutator: (index: Int, MutableMap<String, JsonElement>) -> Unit = { _, _ -> },
    ): String {
        val certifiedList = json.parseToJsonElement(productsListJson()).jsonObject
        val templateCard = certifiedList["items"]!!.jsonArray[0].jsonObject
        val cards = codes.mapIndexed { idx, code ->
            val obj = templateCard.toMutableMap()
            obj["code"] = JsonPrimitive(code)
            obj["name"] = JsonPrimitive("Đề thi $code")
            cardMutator(idx, obj)
            JsonObject(obj)
        }
        return buildJsonObject {
            put("items", JsonArray(cards))
            put("page", page)
            put("size", size)
            put("totalItems", totalItems)
            put("totalPages", totalPages)
        }.toString()
    }

    fun accessResponseForCodes(
        codes: List<String>,
        override: (code: String, MutableMap<String, JsonElement>) -> Unit = { _, _ -> },
    ): String {
        val template = json.parseToJsonElement(examAccess200Json()).jsonObject["items"]!!.jsonArray[0].jsonObject
        val rows = codes.map { code ->
            val obj = template.toMutableMap()
            obj["code"] = JsonPrimitive(code)
            obj["state"] = JsonPrimitive("NONE")
            obj["attemptsLeft"] = JsonPrimitive(0)
            obj["entitlementId"] = JsonNull
            obj["activeAttemptId"] = JsonNull
            obj["hasPendingFulfillment"] = JsonPrimitive(false)
            obj["pendingOrderCode"] = JsonNull
            obj["items"] = JsonNull
            override(code, obj)
            JsonObject(obj)
        }
        return buildJsonObject {
            put("items", JsonArray(rows))
        }.toString()
    }

    fun parseAccessRequestCodes(bodyUtf8: String): List<String> {
        val root = json.parseToJsonElement(bodyUtf8).jsonObject
        return root["codes"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    }

    fun installDefaultStorefrontRoutes(server: ContractFakeServer) {
        server.respondApiFixture("GET", "/api/v1/public/storefront/filters", 200, "storefront-filters-200.json")
        server.respondApiFixture("GET", "/api/v1/public/storefront/products", 200, "storefront-products-list-200.json")
        server.respondApiFixture(
            "GET",
            "/api/v1/public/storefront/products/EXAM-DEMO-01",
            200,
            "storefront-products-detail-200-exam.json",
        )
        server.respondApiFixture(
            "GET",
            "/api/v1/public/storefront/products/BUNDLE-DEMO-01",
            200,
            "storefront-products-detail-200-bundle.json",
        )
        server.respondDynamic("POST", "/api/v1/me/exam-access/query") { req ->
            val requested = parseAccessRequestCodes(req.body.clone().readUtf8())
            val certified = json.parseToJsonElement(examAccess200Json()).jsonObject["items"]!!.jsonArray
                .associateBy { it.jsonObject["code"]!!.jsonPrimitive.content }
            val body = accessResponseForCodes(requested) { code, obj ->
                val hit = certified[code]?.jsonObject
                if (hit != null) {
                    hit.forEach { (k, v) -> obj[k] = v }
                }
            }
            ContractFakeServer.jsonResponse(200, body)
        }
    }

    fun createDependencies(
        harness: PortalTestHarness,
        context: Context? = null,
        imageLoader: ImageLoader? = null,
        supportContact: SupportContact = SupportContact(
            phone = "1900 1234",
            email = "support@thi360.vn",
            hours = "08:00 - 21:00",
        ),
        feedStore: CatalogFeedStore = CatalogFeedStore(),
        debounceWaiter: suspend (Long) -> Unit = { ms -> delay(ms) },
        clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    ): CatalogDependencies = CatalogDependencies(
        gateway = OpenApiCatalogGateway.create(
            baseUrl = harness.server.url("/"),
            sessionStack = harness.stack,
        ),
        sessionManager = harness.stack.sessionManager,
        connectivityMonitor = harness.connectivityMonitor,
        apiBaseUrl = harness.server.url("/"),
        imageLoader = imageLoader,
        supportContact = supportContact,
        feedStore = feedStore,
        debounceWaiter = debounceWaiter,
        clockMillis = clockMillis,
    )

    fun createDeterministicFixtureDependencies(): CatalogDependencies {
        val filters = Serializer.kotlinxSerializationJson
            .decodeFromString(StorefrontFilters.serializer(), filtersJson())
            .toDomain()
        val page = Serializer.kotlinxSerializationJson
            .decodeFromString(PageResponseProductCard.serializer(), productsListJson())
            .toDomain()
        val examDetail = Serializer.kotlinxSerializationJson
            .decodeFromString(ProductDetail.serializer(), examDetailJson())
            .toDomain()
        val bundleDetail = Serializer.kotlinxSerializationJson
            .decodeFromString(ProductDetail.serializer(), bundleDetailJson())
            .toDomain()
        val accessRows = Serializer.kotlinxSerializationJson
            .decodeFromString(ExamAccessQueryResponse.serializer(), examAccess200Json())
            .items
            .map { it.toDomain() }
            .associateBy { it.code }

        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = "http://localhost/",
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(activeStudentTokens()),
            localeProvider = { AppLocale.VI },
            resolveAccountIdOnAuth = false,
        )
        val gateway = object : CatalogGateway {
            override suspend fun getFilters(): ApiResult<StorefrontFiltersData> = ApiResult.Success(filters)
            override suspend fun listProducts(params: CatalogQueryParams): ApiResult<ProductPageData> =
                ApiResult.Success(page)

            override suspend fun getProductDetail(code: String): CatalogDetailResult = when (code) {
                bundleDetail.code -> CatalogDetailResult.Success(ProductOutcome.Bundle(bundleDetail, sellable = true))
                else -> CatalogDetailResult.Success(ProductOutcome.Exam(examDetail.copy(code = code)))
            }

            override suspend fun queryExamAccess(codes: List<String>): ApiResult<List<ExamAccessRow>> =
                ApiResult.Success(codes.map { code -> accessRows[code] ?: ExamAccessRow.none(code) })
        }
        return CatalogDependencies(
            gateway = gateway,
            sessionManager = stack.sessionManager,
            connectivityMonitor = MutableConnectivityMonitor(initialOnline = true),
            apiBaseUrl = "http://localhost/",
            imageLoader = null,
            supportContact = SupportContact(
                phone = "1900 1234",
                email = "support@thi360.vn",
                hours = "08:00 - 21:00",
            ),
            feedStore = CatalogFeedStore(),
        )
    }
}
