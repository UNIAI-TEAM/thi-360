package vn.unicom.thi360.feature.payment.data

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response as OkHttpResponse
import retrofit2.Response
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import vn.unicom.thi360.core.network.ApiErrorParser
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.RetryAfter
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.network.generated.api.AuthApi
import vn.unicom.thi360.core.network.generated.api.ExamAccessApi
import vn.unicom.thi360.core.network.generated.api.LearnerOrdersApi
import vn.unicom.thi360.core.network.generated.api.PriceQuoteApi
import vn.unicom.thi360.core.network.generated.api.StorefrontPublicApi
import vn.unicom.thi360.core.network.generated.api.VNPAYCallbacksApi
import vn.unicom.thi360.core.network.generated.infrastructure.ApiClient
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.CreateOrderRequest
import vn.unicom.thi360.core.network.generated.model.CreateOrderResponse
import vn.unicom.thi360.core.network.generated.model.ExamAccessItem
import vn.unicom.thi360.core.network.generated.model.ExamAccessQueryRequest
import vn.unicom.thi360.core.network.generated.model.MeResponse
import vn.unicom.thi360.core.network.generated.model.OrderDetail
import vn.unicom.thi360.core.network.generated.model.OrderPriceChangedResponse
import vn.unicom.thi360.core.network.generated.model.PageResponseOrderSummary
import vn.unicom.thi360.core.network.generated.model.PayOrderResponse
import vn.unicom.thi360.core.network.generated.model.PriceQuote
import vn.unicom.thi360.core.network.generated.model.PriceQuoteRequest
import vn.unicom.thi360.core.network.generated.model.Product
import vn.unicom.thi360.core.network.generated.model.ProductDetail
import vn.unicom.thi360.core.network.generated.model.ProductGoneResponse
import vn.unicom.thi360.core.network.generated.model.VnpayReturnResponse
import vn.unicom.thi360.core.network.safeApiCall
import vn.unicom.thi360.feature.payment.domain.VoucherErrorKey
import vn.unicom.thi360.feature.payment.domain.VoucherErrorMapper

sealed interface ProductLookupResult {
    data class Success(val product: ProductDetail) : ProductLookupResult
    data object NotFound : ProductLookupResult
    data class Gone(val product: Product) : ProductLookupResult
    data class Unavailable(val product: ProductDetail? = null) : ProductLookupResult
    data class Failure(val error: ApiResult<Nothing>) : ProductLookupResult
}

sealed interface PriceQuoteResult {
    data class Success(val quote: PriceQuote) : PriceQuoteResult
    data class Gone(val product: Product) : PriceQuoteResult
    data object ProductUnavailable : PriceQuoteResult
    data object NotFound : PriceQuoteResult
    data class VoucherRejected(val key: VoucherErrorKey) : PriceQuoteResult
    data object RateLimited : PriceQuoteResult
    data object Unauthorized : PriceQuoteResult
    data class Failure(val error: ApiResult<Nothing>) : PriceQuoteResult
}

sealed interface ExamAccessResult {
    data class Success(val items: List<ExamAccessItem>) : ExamAccessResult
    data object Forbidden : ExamAccessResult
    data class Failure(val error: ApiResult<Nothing>) : ExamAccessResult
}

sealed interface CreateOrderOutcome {
    data class Success(val response: CreateOrderResponse) : CreateOrderOutcome
    data class PriceChanged(val quote: PriceQuote) : CreateOrderOutcome
    data object AccountChanged : CreateOrderOutcome
    data class ProductGone(val product: Product) : CreateOrderOutcome
    data object ProductUnavailable : CreateOrderOutcome
    data class VoucherRejected(val key: VoucherErrorKey) : CreateOrderOutcome
    data object PaymentAttemptsExceeded : CreateOrderOutcome
    data object RateLimited : CreateOrderOutcome
    data object Unauthorized : CreateOrderOutcome
    data class Failure(val error: ApiResult<Nothing>) : CreateOrderOutcome
}

sealed interface VnpayVerifyResult {
    data class Success(val response: VnpayReturnResponse) : VnpayVerifyResult
    data object InvalidSignature : VnpayVerifyResult
    data object OrderNotFound : VnpayVerifyResult
    data class Failure(val error: ApiResult<Nothing>) : VnpayVerifyResult
}

interface PaymentGateway {
    suspend fun fetchCurrentUser(): ApiResult<MeResponse>
    suspend fun loadProduct(code: String): ProductLookupResult
    suspend fun requestQuote(request: PriceQuoteRequest): PriceQuoteResult
    suspend fun queryExamAccess(codes: List<String>): ExamAccessResult
    suspend fun createOrder(
        request: CreateOrderRequest,
        idempotencyKey: String,
    ): ApiResult<CreateOrderOutcome>
    suspend fun verifyVnpayReturn(vnpParams: Map<String, String>): VnpayVerifyResult
    suspend fun listOrders(page: Int, size: Int = 20): ApiResult<PageResponseOrderSummary>
    suspend fun getOrder(orderCode: String): ApiResult<OrderDetail>
    suspend fun payOrder(orderCode: String, idempotencyKey: String): ApiResult<PayOrderResponse>
    suspend fun cancelOrder(orderCode: String, idempotencyKey: String): ApiResult<OrderDetail>
}

private val stringMapSerializer = MapSerializer(String.serializer(), String.serializer())
private val flatQueryJson = Json { ignoreUnknownKeys = true }

private class VnpayEncodedParamsMap(
    private val delegate: Map<String, String>,
) : Map<String, String> by delegate {
    override fun toString(): String =
        flatQueryJson.encodeToString(stringMapSerializer, delegate)
}

internal object VnpayReturnQueryFlatteningInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        val request = chain.request()
        val url = request.url
        if (url.encodedPath.endsWith("/vnpay/return") &&
            url.queryParameterNames.size == 1 &&
            url.queryParameterNames.contains("params")
        ) {
            val rawJson = url.queryParameter("params")
            if (rawJson != null && rawJson.startsWith("{")) {
                val decoded = runCatching {
                    flatQueryJson.decodeFromString(stringMapSerializer, rawJson)
                }.getOrNull()
                if (decoded != null) {
                    val rebuiltUrl = url.newBuilder()
                        .removeAllQueryParameters("params")
                        .apply {
                            decoded.forEach { (key, value) ->
                                addQueryParameter(key, value)
                            }
                        }
                        .build()
                    return chain.proceed(request.newBuilder().url(rebuiltUrl).build())
                }
            }
        }
        return chain.proceed(request)
    }
}

class OpenApiPaymentGateway(
    private val authApi: AuthApi,
    private val storefrontApi: StorefrontPublicApi,
    private val priceQuoteApi: PriceQuoteApi,
    private val examAccessApi: ExamAccessApi,
    private val ordersApi: LearnerOrdersApi,
    private val vnpayCallbacksApi: VNPAYCallbacksApi,
) : PaymentGateway {

    override suspend fun fetchCurrentUser(): ApiResult<MeResponse> =
        safeApiCall { authApi.me() }

    override suspend fun loadProduct(code: String): ProductLookupResult = try {
        val response = storefrontApi.getStorefrontProduct(code)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ProductLookupResult.Success(body)
            } else {
                ProductLookupResult.Failure(ApiResult.HttpFailure(response.code(), null))
            }
        } else {
            val rawError = response.errorBody()?.string()
            val parsedError = ApiErrorParser.parse(rawError)
            when {
                response.code() == 404 -> ProductLookupResult.NotFound
                response.code() == 410 -> {
                    val gone = decodeOrNull(ProductGoneResponse.serializer(), rawError)?.product
                        ?: Product(code = code, name = code)
                    ProductLookupResult.Gone(gone)
                }
                response.code() == 409 && parsedError?.code == "PRODUCT_UNAVAILABLE" -> {
                    val embeddedProduct = parseEmbeddedProductDetail(rawError)
                    ProductLookupResult.Unavailable(product = embeddedProduct)
                }
                else -> ProductLookupResult.Failure(toHttpFailure(response, rawError))
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        ProductLookupResult.Failure(ApiResult.NetworkFailure(io))
    } catch (other: Throwable) {
        ProductLookupResult.Failure(ApiResult.NetworkFailure(other))
    }

    override suspend fun requestQuote(request: PriceQuoteRequest): PriceQuoteResult = try {
        val response = priceQuoteApi.quoteMyPrice(request)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                PriceQuoteResult.Success(body)
            } else {
                PriceQuoteResult.Failure(ApiResult.HttpFailure(response.code(), null))
            }
        } else {
            val rawError = response.errorBody()?.string()
            val parsedError = ApiErrorParser.parse(rawError)
            val errCode = parsedError?.code
            when {
                response.code() == 401 -> PriceQuoteResult.Unauthorized
                response.code() == 404 -> PriceQuoteResult.NotFound
                response.code() == 410 -> {
                    val gone = decodeOrNull(ProductGoneResponse.serializer(), rawError)?.product
                        ?: Product(code = request.code, name = request.code)
                    PriceQuoteResult.Gone(gone)
                }
                response.code() == 409 && errCode == "PRODUCT_UNAVAILABLE" ->
                    PriceQuoteResult.ProductUnavailable
                response.code() == 429 || errCode == "RATE_LIMITED" ->
                    PriceQuoteResult.RateLimited
                response.code() == 422 || errCode?.startsWith("VOUCHER_") == true ->
                    PriceQuoteResult.VoucherRejected(VoucherErrorMapper.map(response.code(), errCode))
                else -> PriceQuoteResult.Failure(toHttpFailure(response, rawError))
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        PriceQuoteResult.Failure(ApiResult.NetworkFailure(io))
    } catch (other: Throwable) {
        PriceQuoteResult.Failure(ApiResult.NetworkFailure(other))
    }

    override suspend fun queryExamAccess(codes: List<String>): ExamAccessResult = try {
        val response = examAccessApi.queryMyExamAccess(ExamAccessQueryRequest(codes = codes))
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ExamAccessResult.Success(body.items)
            } else {
                ExamAccessResult.Failure(ApiResult.HttpFailure(response.code(), null))
            }
        } else if (response.code() == 403) {
            ExamAccessResult.Forbidden
        } else {
            val rawError = response.errorBody()?.string()
            ExamAccessResult.Failure(toHttpFailure(response, rawError))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        ExamAccessResult.Failure(ApiResult.NetworkFailure(io))
    } catch (other: Throwable) {
        ExamAccessResult.Failure(ApiResult.NetworkFailure(other))
    }

    override suspend fun createOrder(
        request: CreateOrderRequest,
        idempotencyKey: String,
    ): ApiResult<CreateOrderOutcome> = try {
        val response = ordersApi.createMyOrder(
            createOrderRequest = request,
            idempotencyKey = idempotencyKey,
        )
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(CreateOrderOutcome.Success(body))
            } else {
                ApiResult.HttpFailure(response.code(), null)
            }
        } else {
            val rawError = response.errorBody()?.string()
            val parsedError = ApiErrorParser.parse(rawError)
            val errCode = parsedError?.code
            val statusCode = response.code()
            val shouldPreserveKey = statusCode == 408 || statusCode == 429 || statusCode in 500..599
            if (shouldPreserveKey) {
                toHttpFailure(response, rawError)
            } else {
                val outcome = when {
                    statusCode == 401 -> CreateOrderOutcome.Unauthorized
                    statusCode == 410 -> {
                        val gone = decodeOrNull(ProductGoneResponse.serializer(), rawError)?.product
                            ?: Product(code = request.code, name = request.code)
                        CreateOrderOutcome.ProductGone(gone)
                    }
                    statusCode == 409 && errCode == "PRODUCT_UNAVAILABLE" ->
                        CreateOrderOutcome.ProductUnavailable
                    statusCode == 409 && errCode == "PRICE_CHANGED" -> {
                        val quote = decodeOrNull(OrderPriceChangedResponse.serializer(), rawError)?.quote
                        if (quote != null) {
                            CreateOrderOutcome.PriceChanged(quote)
                        } else {
                            CreateOrderOutcome.Failure(toHttpFailure(response, rawError))
                        }
                    }
                    statusCode == 409 && errCode == "ACCOUNT_CHANGED" ->
                        CreateOrderOutcome.AccountChanged
                    errCode == "PAYMENT_ATTEMPTS_EXCEEDED" ->
                        CreateOrderOutcome.PaymentAttemptsExceeded
                    statusCode == 422 && errCode?.startsWith("VOUCHER_") == true ->
                        CreateOrderOutcome.VoucherRejected(VoucherErrorMapper.map(statusCode, errCode))
                    errCode?.startsWith("VOUCHER_") == true ->
                        CreateOrderOutcome.VoucherRejected(VoucherErrorMapper.map(statusCode, errCode))
                    else -> CreateOrderOutcome.Failure(toHttpFailure(response, rawError))
                }
                ApiResult.Success(outcome)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        ApiResult.NetworkFailure(io)
    } catch (other: Throwable) {
        ApiResult.NetworkFailure(other)
    }

    override suspend fun verifyVnpayReturn(vnpParams: Map<String, String>): VnpayVerifyResult = try {
        val response = vnpayCallbacksApi.verifyVnpayReturn(VnpayEncodedParamsMap(vnpParams))
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                VnpayVerifyResult.Success(body)
            } else {
                VnpayVerifyResult.Failure(ApiResult.HttpFailure(response.code(), null))
            }
        } else {
            val rawError = response.errorBody()?.string()
            val parsedError = ApiErrorParser.parse(rawError)
            when {
                parsedError?.code == "VNPAY_SIGNATURE_INVALID" ||
                    response.code() == 422 ||
                    response.code() == 400 -> VnpayVerifyResult.InvalidSignature
                response.code() == 404 || parsedError?.code == "ORDER_NOT_FOUND" ->
                    VnpayVerifyResult.OrderNotFound
                else -> VnpayVerifyResult.Failure(toHttpFailure(response, rawError))
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: IOException) {
        VnpayVerifyResult.Failure(ApiResult.NetworkFailure(io))
    } catch (other: Throwable) {
        VnpayVerifyResult.Failure(ApiResult.NetworkFailure(other))
    }

    override suspend fun listOrders(page: Int, size: Int): ApiResult<PageResponseOrderSummary> =
        safeApiCall { ordersApi.listMyOrders(page = page, size = size) }

    override suspend fun getOrder(orderCode: String): ApiResult<OrderDetail> =
        safeApiCall { ordersApi.getMyOrder(orderCode) }

    override suspend fun payOrder(
        orderCode: String,
        idempotencyKey: String,
    ): ApiResult<PayOrderResponse> = safeApiCall {
        ordersApi.payMyOrder(code = orderCode, idempotencyKey = idempotencyKey)
    }

    override suspend fun cancelOrder(
        orderCode: String,
        idempotencyKey: String,
    ): ApiResult<OrderDetail> = safeApiCall {
        ordersApi.cancelMyOrder(code = orderCode, idempotencyKey = idempotencyKey)
    }

    private fun toHttpFailure(response: Response<*>, rawError: String?): ApiResult.HttpFailure {
        val parsed = ApiErrorParser.parse(rawError)
        return ApiResult.HttpFailure(
            statusCode = response.code(),
            error = parsed,
            retryAfterSeconds = RetryAfter.parse(response.headers()["Retry-After"], parsed),
        )
    }

    private fun <T> decodeOrNull(
        serializer: kotlinx.serialization.KSerializer<T>,
        rawBody: String?,
    ): T? {
        if (rawBody.isNullOrBlank()) return null
        return runCatching {
            Serializer.kotlinxSerializationJson.decodeFromString(serializer, rawBody)
        }.getOrNull()
    }

    private fun parseEmbeddedProductDetail(rawBody: String?): ProductDetail? {
        if (rawBody.isNullOrBlank()) return null
        return runCatching {
            val root = Serializer.kotlinxSerializationJson.parseToJsonElement(rawBody).jsonObject
            val productElement = root["product"] ?: return null
            Serializer.kotlinxSerializationJson.decodeFromJsonElement(
                ProductDetail.serializer(),
                productElement,
            )
        }.getOrNull()
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        private val paymentRequestJson = Json {
            serializersModule = Serializer.kotlinxSerializationAdapters
            encodeDefaults = false
            explicitNulls = false
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun fromSessionStack(stack: Thi360SessionStack): OpenApiPaymentGateway {
            val paymentConverters = listOf(
                ScalarsConverterFactory.create(),
                paymentRequestJson.asConverterFactory("application/json".toMediaType()),
            )
            val paymentAuthenticatedClient = ApiClient(
                baseUrl = stack.baseUrl,
                callFactory = stack.authenticatedOkHttpClient,
                converterFactories = paymentConverters,
            )
            val flatQueryClient = stack.publicOkHttpClient.newBuilder()
                .apply {
                    interceptors().add(0, VnpayReturnQueryFlatteningInterceptor)
                }
                .build()
            val vnpayApi = ApiClient(
                baseUrl = stack.baseUrl,
                callFactory = flatQueryClient,
            ).createService(VNPAYCallbacksApi::class.java)
            return OpenApiPaymentGateway(
                authApi = stack.authenticatedAuthApi,
                storefrontApi = stack.authenticatedService(StorefrontPublicApi::class.java),
                priceQuoteApi = paymentAuthenticatedClient.createService(PriceQuoteApi::class.java),
                examAccessApi = stack.authenticatedService(ExamAccessApi::class.java),
                ordersApi = paymentAuthenticatedClient.createService(LearnerOrdersApi::class.java),
                vnpayCallbacksApi = vnpayApi,
            )
        }
    }
}
