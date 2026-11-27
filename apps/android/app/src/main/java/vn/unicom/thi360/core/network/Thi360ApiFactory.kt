package vn.unicom.thi360.core.network

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Response
import vn.unicom.thi360.core.catalog.CatalogOptionsCache
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.generated.api.AuthApi
import vn.unicom.thi360.core.network.generated.api.CatalogOptionsApi
import vn.unicom.thi360.core.network.generated.api.ProfileApi
import vn.unicom.thi360.core.network.generated.infrastructure.ApiClient
import vn.unicom.thi360.core.network.generated.model.MobileLoginRequest
import vn.unicom.thi360.core.network.generated.model.MobileLogoutRequest
import vn.unicom.thi360.core.network.generated.model.MobileRefreshRequest
import vn.unicom.thi360.core.network.generated.model.MobileTokenResponse
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.MobileAuthGateway
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.storage.TokenStore
import vn.unicom.thi360.core.storage.Tokens

suspend fun <T> safeApiCall(block: suspend () -> Response<T>): ApiResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
        val body = response.body()
        if (body != null) {
            ApiResult.Success(body)
        } else {
            ApiResult.HttpFailure(statusCode = response.code(), error = null)
        }
    } else {
        val rawError = response.errorBody()?.string()
        val parsedError = ApiErrorParser.parse(rawError)
        ApiResult.HttpFailure(
            statusCode = response.code(),
            error = parsedError,
            retryAfterSeconds = RetryAfter.parse(response.headers()["Retry-After"], parsedError),
        )
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (io: IOException) {
    ApiResult.NetworkFailure(io)
} catch (other: Throwable) {
    ApiResult.NetworkFailure(other)
}

suspend fun safeEmptyApiCall(block: suspend () -> Response<Unit>): ApiResult<Unit> = try {
    val response = block()
    if (response.isSuccessful) {
        ApiResult.Success(Unit)
    } else {
        val rawError = response.errorBody()?.string()
        val parsedError = ApiErrorParser.parse(rawError)
        ApiResult.HttpFailure(
            statusCode = response.code(),
            error = parsedError,
            retryAfterSeconds = RetryAfter.parse(response.headers()["Retry-After"], parsedError),
        )
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (io: IOException) {
    ApiResult.NetworkFailure(io)
} catch (other: Throwable) {
    ApiResult.NetworkFailure(other)
}

private fun MobileTokenResponse.toDomainTokens(): Tokens = Tokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
    accessExpiresIn = accessExpiresIn,
    refreshExpiresIn = refreshExpiresIn,
    mustChangePassword = mustChangePassword,
)

class OpenApiMobileAuthGateway(
    private val publicAuthApi: AuthApi,
) : MobileAuthGateway {

    override suspend fun login(identifier: String, password: String): ApiResult<Tokens> =
        when (val result = safeApiCall { publicAuthApi.mobileLogin(MobileLoginRequest(identifier, password)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomainTokens())
            is ApiResult.HttpFailure -> result
            is ApiResult.NetworkFailure -> result
        }

    override suspend fun refresh(refreshToken: String): ApiResult<Tokens> =
        when (val result = safeApiCall { publicAuthApi.mobileRefresh(MobileRefreshRequest(refreshToken)) }) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomainTokens())
            is ApiResult.HttpFailure -> result
            is ApiResult.NetworkFailure -> result
        }

    override suspend fun logout(refreshToken: String): ApiResult<Unit> =
        safeEmptyApiCall { publicAuthApi.mobileLogout(MobileLogoutRequest(refreshToken)) }
}

class Thi360SessionStack(
    val baseUrl: String,
    val tokenStore: TokenStore,
    val sessionManager: SessionManager,
    val publicAuthApi: AuthApi,
    val authenticatedAuthApi: AuthApi,
    val profileApi: ProfileApi,
    val catalogOptionsCache: CatalogOptionsCache,
    val publicOkHttpClient: OkHttpClient,
    val authenticatedOkHttpClient: OkHttpClient,
    private val publicApiClient: ApiClient,
    private val authenticatedApiClient: ApiClient,
) {
    fun <S> authenticatedService(serviceClass: Class<S>): S =
        authenticatedApiClient.createService(serviceClass)

    fun <S> publicService(serviceClass: Class<S>): S =
        publicApiClient.createService(serviceClass)
}

object Thi360ApiFactory {
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L

    fun createSessionStack(
        baseUrl: String,
        environment: Environment,
        tokenStore: TokenStore,
        localeProvider: () -> AppLocale,
        logger: AppLogger = AndroidAppLogger,
        onSessionCleared: () -> Unit = {},
        startLocked: Boolean = false,
        sessionCleanup: SessionCleanup? = null,
        resolveAccountIdOnAuth: Boolean = true,
        catalogOptionsClockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    ): Thi360SessionStack {
        val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val acceptLanguageInterceptor = AcceptLanguageInterceptor(localeProvider)
        val redactingLogger = RedactingHttpLoggingInterceptor(
            environment = environment,
            logger = logger,
        )

        val publicOkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(acceptLanguageInterceptor)
            .addInterceptor(redactingLogger)
            .build()

        val publicApiClient = ApiClient(
            baseUrl = normalizedBaseUrl,
            callFactory = publicOkHttpClient,
        )
        val publicAuthApi = publicApiClient.createService(AuthApi::class.java)
        val authGateway = OpenApiMobileAuthGateway(publicAuthApi)

        var authenticatedAuthApiRef: AuthApi? = null
        var catalogOptionsCacheRef: CatalogOptionsCache? = null
        val accountIdResolver: (suspend () -> String?)? = if (resolveAccountIdOnAuth) {
            {
                val api = authenticatedAuthApiRef
                if (api == null) {
                    null
                } else {
                    when (val meResult = safeApiCall { api.me() }) {
                        is ApiResult.Success -> meResult.data.id.toString()
                        else -> null
                    }
                }
            }
        } else {
            null
        }

        val sessionManager = SessionManager(
            tokenStore = tokenStore,
            authGateway = authGateway,
            onSessionCleared = {
                catalogOptionsCacheRef?.invalidateAll()
                onSessionCleared()
            },
            startLocked = startLocked,
            sessionCleanup = sessionCleanup,
            accountIdResolver = accountIdResolver,
        )

        val authenticatedOkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(acceptLanguageInterceptor)
            .addInterceptor(AuthHeaderInterceptor { sessionManager.currentAccessToken() })
            .addInterceptor(redactingLogger)
            .authenticator(TokenAuthenticator(sessionManager))
            .build()

        val authenticatedApiClient = ApiClient(
            baseUrl = normalizedBaseUrl,
            callFactory = authenticatedOkHttpClient,
        )
        val authenticatedAuthApi = authenticatedApiClient.createService(AuthApi::class.java)
        authenticatedAuthApiRef = authenticatedAuthApi
        val profileApi = authenticatedApiClient.createService(ProfileApi::class.java)
        val catalogOptionsApi = authenticatedApiClient.createService(CatalogOptionsApi::class.java)
        val catalogOptionsCache = CatalogOptionsCache.fromApi(
            api = catalogOptionsApi,
            clockMillis = catalogOptionsClockMillis,
        )
        catalogOptionsCacheRef = catalogOptionsCache
        sessionCleanup?.register { catalogOptionsCache.clear() }

        return Thi360SessionStack(
            baseUrl = normalizedBaseUrl,
            tokenStore = tokenStore,
            sessionManager = sessionManager,
            publicAuthApi = publicAuthApi,
            authenticatedAuthApi = authenticatedAuthApi,
            profileApi = profileApi,
            catalogOptionsCache = catalogOptionsCache,
            publicOkHttpClient = publicOkHttpClient,
            authenticatedOkHttpClient = authenticatedOkHttpClient,
            publicApiClient = publicApiClient,
            authenticatedApiClient = authenticatedApiClient,
        )
    }
}
