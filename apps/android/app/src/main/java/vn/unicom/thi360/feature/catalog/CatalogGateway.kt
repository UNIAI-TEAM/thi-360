package vn.unicom.thi360.feature.catalog

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import coil3.ImageLoader
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Response
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.network.ApiErrorParser
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.RetryAfter
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.network.generated.api.ExamAccessApi
import vn.unicom.thi360.core.network.generated.api.StorefrontPublicApi
import vn.unicom.thi360.core.network.generated.infrastructure.ApiClient
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.ExamAccessQueryRequest
import vn.unicom.thi360.core.network.generated.model.ProductDetail
import vn.unicom.thi360.core.network.generated.model.ProductGoneResponse
import vn.unicom.thi360.core.network.safeApiCall
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionManager

sealed interface CatalogDetailResult {
    data class Success(val outcome: ProductOutcome) : CatalogDetailResult
    data class HttpFailure(
        val statusCode: Int,
        val retryAfterSeconds: Long? = null,
    ) : CatalogDetailResult
    data class NetworkFailure(val cause: Throwable) : CatalogDetailResult
}

interface CatalogGateway {
    suspend fun getFilters(): ApiResult<StorefrontFiltersData>
    suspend fun listProducts(params: CatalogQueryParams): ApiResult<ProductPageData>
    suspend fun getProductDetail(code: String): CatalogDetailResult
    suspend fun queryExamAccess(codes: List<String>): ApiResult<List<ExamAccessRow>>
}

internal class ProductSaleStateHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val segments = chain.request().url.pathSegments
        val isProductDetail = response.code == HTTP_OK &&
            segments.size >= 3 &&
            segments[segments.lastIndex - 1] == "products" &&
            segments[segments.lastIndex - 2] == "storefront"
        if (!isProductDetail) {
            return response
        }
        val saleState = runCatching {
            val raw = response.peekBody(MAX_PEEK_BYTES).string()
            val root = saleStateJson.parseToJsonElement(raw).jsonObject
            root["saleState"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return if (saleState != null) {
            response.newBuilder().header(HEADER_SALE_STATE, saleState).build()
        } else {
            response
        }
    }

    companion object {
        const val HEADER_SALE_STATE = "X-Thi360-Sale-State"
        private const val HTTP_OK = 200
        private const val MAX_PEEK_BYTES = 1_048_576L
        private val saleStateJson = Json { ignoreUnknownKeys = true }
    }
}

class OpenApiCatalogGateway(
    private val storefrontApi: StorefrontPublicApi,
    private val examAccessApi: ExamAccessApi,
) : CatalogGateway {

    override suspend fun getFilters(): ApiResult<StorefrontFiltersData> =
        when (val result = safeApiCall { storefrontApi.getStorefrontFilters() }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.HttpFailure -> result
            is ApiResult.NetworkFailure -> result
        }

    override suspend fun listProducts(params: CatalogQueryParams): ApiResult<ProductPageData> {
        val formatParam = params.format?.let { raw ->
            StorefrontPublicApi.FormatListStorefrontProducts.entries.firstOrNull { it.value == raw }
        }
        val sortParam = StorefrontPublicApi.SortListStorefrontProducts.entries
            .firstOrNull { it.value == params.sort }
            ?: StorefrontPublicApi.SortListStorefrontProducts.NEWEST
        return when (
            val result = safeApiCall {
                storefrontApi.listStorefrontProducts(
                    q = params.q,
                    kind = params.kind,
                    format = formatParam,
                    subject = params.subject,
                    part = params.part,
                    sort = sortParam,
                    page = params.page,
                    size = params.size,
                )
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.HttpFailure -> result
            is ApiResult.NetworkFailure -> result
        }
    }

    override suspend fun getProductDetail(code: String): CatalogDetailResult = try {
        val response = storefrontApi.getStorefrontProduct(code)
        val statusCode = response.code()
        when {
            response.isSuccessful -> {
                val body = response.body()
                if (body == null) {
                    CatalogDetailResult.HttpFailure(statusCode = statusCode)
                } else {
                    val domain = body.toDomain()
                    val saleState = response.headers()[ProductSaleStateHeaderInterceptor.HEADER_SALE_STATE]
                    val outcome = if (domain.isBundle) {
                        ProductOutcome.Bundle(
                            detail = domain,
                            sellable = !SALE_STATE_UNAVAILABLE.equals(saleState, ignoreCase = true),
                        )
                    } else {
                        ProductOutcome.Exam(domain)
                    }
                    CatalogDetailResult.Success(outcome)
                }
            }

            statusCode == HTTP_NOT_FOUND -> CatalogDetailResult.Success(ProductOutcome.NotFound)

            statusCode == HTTP_GONE -> {
                val rawError = response.errorBody()?.string()
                val gone = parseGoneProduct(rawError, fallbackCode = code)
                CatalogDetailResult.Success(gone)
            }

            statusCode == HTTP_CONFLICT -> {
                val rawError = response.errorBody()?.string()
                val parsed = parseConflictOutcome(rawError)
                if (parsed != null) {
                    CatalogDetailResult.Success(parsed)
                } else {
                    val apiError = ApiErrorParser.parse(rawError)
                    CatalogDetailResult.HttpFailure(
                        statusCode = statusCode,
                        retryAfterSeconds = RetryAfter.parse(response.headers()["Retry-After"], apiError),
                    )
                }
            }

            else -> {
                val rawError = response.errorBody()?.string()
                val apiError = ApiErrorParser.parse(rawError)
                CatalogDetailResult.HttpFailure(
                    statusCode = statusCode,
                    retryAfterSeconds = RetryAfter.parse(response.headers()["Retry-After"], apiError),
                )
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        CatalogDetailResult.NetworkFailure(io)
    } catch (other: Throwable) {
        CatalogDetailResult.NetworkFailure(other)
    }

    override suspend fun queryExamAccess(codes: List<String>): ApiResult<List<ExamAccessRow>> {
        val cleaned = codes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty()) return ApiResult.Success(emptyList())
        return when (
            val result = safeApiCall {
                examAccessApi.queryMyExamAccess(ExamAccessQueryRequest(codes = cleaned))
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.items.map { it.toDomain() })
            is ApiResult.HttpFailure -> result
            is ApiResult.NetworkFailure -> result
        }
    }

    private fun parseGoneProduct(rawError: String?, fallbackCode: String): ProductOutcome.Gone {
        if (!rawError.isNullOrBlank()) {
            val typed = runCatching {
                Serializer.kotlinxSerializationJson.decodeFromString(ProductGoneResponse.serializer(), rawError)
            }.getOrNull()
            if (typed != null) {
                val productCode = typed.product.code.trim().ifEmpty { fallbackCode }
                val productName = typed.product.name.trim().takeIf { it.isNotEmpty() }
                return ProductOutcome.Gone(code = productCode, name = productName)
            }
            val fallbackObj = runCatching {
                conflictJson.parseToJsonElement(rawError).jsonObject["product"]?.jsonObject
            }.getOrNull()
            if (fallbackObj != null) {
                val productCode = fallbackObj["code"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    .ifEmpty { fallbackCode }
                val productName = fallbackObj["name"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.takeIf { it.isNotEmpty() }
                return ProductOutcome.Gone(code = productCode, name = productName)
            }
        }
        return ProductOutcome.Gone(code = fallbackCode, name = null)
    }

    private fun parseConflictOutcome(rawError: String?): ProductOutcome? {
        if (rawError.isNullOrBlank()) return null
        val root = runCatching { conflictJson.parseToJsonElement(rawError).jsonObject }.getOrNull() ?: return null
        val errorCode = root["code"]?.jsonPrimitive?.contentOrNull
        if (errorCode != CODE_PRODUCT_UNAVAILABLE) return null
        val productElement = root["product"]
        if (productElement is JsonObject && isFullProductDetailJson(productElement)) {
            val decoded = runCatching {
                Serializer.kotlinxSerializationJson.decodeFromJsonElement(ProductDetail.serializer(), productElement)
            }.getOrNull()
            if (decoded != null) {
                val domain = decoded.toDomain()
                if (domain.isBundle) {
                    return ProductOutcome.Bundle(detail = domain, sellable = false)
                }
            }
        }
        return ProductOutcome.BundleUnavailable
    }

    private fun isFullProductDetailJson(obj: JsonObject): Boolean {
        val code = obj["code"]?.jsonPrimitive?.contentOrNull
        val name = obj["name"]?.jsonPrimitive?.contentOrNull
        val examKind = obj["examKind"] as? JsonObject
        val parts = obj["parts"]
        val choices = obj["choices"]
        val scoring = obj["scoring"] as? JsonObject
        val benefits = obj["benefits"] as? JsonObject
        val hasPrice = obj["price"] != null && obj["listPrice"] != null
        return !code.isNullOrBlank() &&
            !name.isNullOrBlank() &&
            examKind != null &&
            parts is JsonArray &&
            choices is JsonArray &&
            scoring != null &&
            benefits != null &&
            hasPrice
    }

    companion object {
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
        private const val HTTP_GONE = 410
        private const val CODE_PRODUCT_UNAVAILABLE = "PRODUCT_UNAVAILABLE"
        private const val SALE_STATE_UNAVAILABLE = "UNAVAILABLE"

        private val conflictJson = Json { ignoreUnknownKeys = true }

        fun fromSessionStack(sessionStack: Thi360SessionStack): OpenApiCatalogGateway =
            OpenApiCatalogGateway(
                storefrontApi = sessionStack.authenticatedService(StorefrontPublicApi::class.java),
                examAccessApi = sessionStack.authenticatedService(ExamAccessApi::class.java),
            )

        fun create(
            baseUrl: String,
            sessionStack: Thi360SessionStack,
        ): OpenApiCatalogGateway {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val detailClient = sessionStack.authenticatedOkHttpClient.newBuilder()
                .addInterceptor(ProductSaleStateHeaderInterceptor())
                .build()
            val storefrontApi = ApiClient(
                baseUrl = normalizedBaseUrl,
                callFactory = detailClient,
            ).createService(StorefrontPublicApi::class.java)
            val examAccessApi = sessionStack.authenticatedService(ExamAccessApi::class.java)
            return OpenApiCatalogGateway(
                storefrontApi = storefrontApi,
                examAccessApi = examAccessApi,
            )
        }
    }
}

data class CatalogDependencies(
    val gateway: CatalogGateway,
    val sessionManager: SessionManager,
    val connectivityMonitor: ConnectivityMonitor?,
    val apiBaseUrl: String,
    val imageLoader: ImageLoader?,
    val supportContact: SupportContact = SupportContact.fromBuildConfig(),
    val feedStore: CatalogFeedStore = CatalogFeedStore.Default,
    val debounceWaiter: suspend (Long) -> Unit = { ms -> delay(ms) },
    val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    companion object {
        private val defaultCleanupRegistered = AtomicBoolean(false)

        fun defaultFor(context: Context): CatalogDependencies {
            val appContext = context.applicationContext
            val app = appContext as? Thi360Application
            val stack = AppSessionContainer.get(appContext)
            if (app != null && defaultCleanupRegistered.compareAndSet(false, true)) {
                app.sessionCleanup.register { CatalogFeedStore.Default.clearAll() }
            }
            return CatalogDependencies(
                gateway = OpenApiCatalogGateway.create(
                    baseUrl = AppEnvironment.apiBaseUrl,
                    sessionStack = stack,
                ),
                sessionManager = stack.sessionManager,
                connectivityMonitor = app?.connectivityMonitor,
                apiBaseUrl = AppEnvironment.apiBaseUrl,
                imageLoader = app?.imageLoader?.imageLoader,
                supportContact = SupportContact.fromBuildConfig(),
                feedStore = CatalogFeedStore.Default,
            )
        }
    }
}

val LocalCatalogDependencies = compositionLocalOf<CatalogDependencies?> { null }
