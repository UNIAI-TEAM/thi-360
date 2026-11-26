package vn.unicom.thi360.core.appearance

import android.content.Context
import android.os.SystemClock
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.RedactingHttpLoggingInterceptor
import vn.unicom.thi360.core.theme.BrandPalette

sealed interface AppearanceFetchResponse {
    data class Ok(val body: String, val etag: String?) : AppearanceFetchResponse
    data class NotModified(val etag: String?) : AppearanceFetchResponse
    data class HttpError(val statusCode: Int) : AppearanceFetchResponse
    data class NetworkError(val cause: Throwable) : AppearanceFetchResponse
}

interface AppearanceRemoteSource {
    suspend fun fetch(ifNoneMatch: String?): AppearanceFetchResponse
}

class OkHttpAppearanceRemoteSource(
    webBaseUrl: String,
    private val okHttpClient: OkHttpClient,
) : AppearanceRemoteSource {

    private val endpointUrl: String = buildEndpointUrl(webBaseUrl)

    override suspend fun fetch(ifNoneMatch: String?): AppearanceFetchResponse = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .get()
            .header("Accept", "application/json")
        if (!ifNoneMatch.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", ifNoneMatch)
        }
        try {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                when (response.code) {
                    HTTP_OK -> {
                        val body = response.body.string()
                        AppearanceFetchResponse.Ok(
                            body = body,
                            etag = response.header("ETag"),
                        )
                    }

                    HTTP_NOT_MODIFIED -> AppearanceFetchResponse.NotModified(
                        etag = response.header("ETag"),
                    )

                    else -> AppearanceFetchResponse.HttpError(response.code)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            AppearanceFetchResponse.NetworkError(io)
        } catch (other: Throwable) {
            AppearanceFetchResponse.NetworkError(other)
        }
    }

    companion object {
        const val APPEARANCE_PATH = "mobile/appearance-tokens.json"
        private const val HTTP_OK = 200
        private const val HTTP_NOT_MODIFIED = 304

        internal fun buildEndpointUrl(webBaseUrl: String): String {
            val normalized = if (webBaseUrl.endsWith("/")) webBaseUrl else "$webBaseUrl/"
            return "$normalized$APPEARANCE_PATH"
        }
    }
}

sealed interface AppearanceRefreshResult {
    data class Updated(val version: Int) : AppearanceRefreshResult
    data object NotModified : AppearanceRefreshResult
    data object SkippedThrottle : AppearanceRefreshResult
    data object IgnoredInvalid : AppearanceRefreshResult
    data class HttpError(val statusCode: Int) : AppearanceRefreshResult
    data object NetworkError : AppearanceRefreshResult
}

class AppearanceRepository(
    private val remoteSource: AppearanceRemoteSource,
    private val cache: AppearanceCache,
    private val clockMillis: () -> Long = SystemClock::elapsedRealtime,
) {
    private val refreshMutex = Mutex()

    @Volatile
    private var cachedSnapshot: CachedAppearance? = cache.read()

    @Volatile
    private var lastFetchAtMillis: Long? = null

    private val _current = MutableStateFlow(cachedSnapshot?.palette ?: BrandPalette.Default)
    val current: StateFlow<BrandPalette> = _current.asStateFlow()

    val currentVersion: Int?
        get() = cachedSnapshot?.version

    suspend fun refresh(force: Boolean = false): AppearanceRefreshResult = refreshMutex.withLock {
        val now = clockMillis()
        val lastFetch = lastFetchAtMillis
        if (!force && lastFetch != null && (now - lastFetch) < MIN_REFRESH_INTERVAL_MILLIS) {
            return AppearanceRefreshResult.SkippedThrottle
        }

        val currentCached = cachedSnapshot
        val ifNoneMatch = currentCached?.etag ?: currentCached?.version?.let { "\"$it\"" }
        val response = remoteSource.fetch(ifNoneMatch = ifNoneMatch)
        lastFetchAtMillis = clockMillis()

        when (response) {
            is AppearanceFetchResponse.Ok -> {
                val parsed = AppearanceTokensParser.parse(response.body)
                    ?: return AppearanceRefreshResult.IgnoredInvalid

                val resolvedEtag = response.etag?.trim()?.ifEmpty { null } ?: "\"${parsed.version}\""
                if (currentCached != null && currentCached.version == parsed.version) {
                    if (currentCached.etag != resolvedEtag) {
                        val refreshed = currentCached.copy(etag = resolvedEtag)
                        cachedSnapshot = refreshed
                        cache.write(refreshed)
                    }
                    AppearanceRefreshResult.NotModified
                } else {
                    val updated = CachedAppearance(
                        version = parsed.version,
                        etag = resolvedEtag,
                        rawJson = response.body,
                        palette = parsed.palette,
                    )
                    cachedSnapshot = updated
                    cache.write(updated)
                    _current.value = parsed.palette
                    AppearanceRefreshResult.Updated(parsed.version)
                }
            }

            is AppearanceFetchResponse.NotModified -> {
                val updatedEtag = response.etag?.trim()?.ifEmpty { null }
                if (currentCached != null && updatedEtag != null && currentCached.etag != updatedEtag) {
                    val refreshed = currentCached.copy(etag = updatedEtag)
                    cachedSnapshot = refreshed
                    cache.write(refreshed)
                }
                AppearanceRefreshResult.NotModified
            }

            is AppearanceFetchResponse.HttpError -> AppearanceRefreshResult.HttpError(response.statusCode)

            is AppearanceFetchResponse.NetworkError -> AppearanceRefreshResult.NetworkError
        }
    }

    suspend fun refreshOnResume(): AppearanceRefreshResult = refresh(force = false)

    companion object {
        const val MIN_REFRESH_INTERVAL_MILLIS: Long = 60_000L
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 15L

        fun create(
            context: Context,
            webBaseUrl: String = AppEnvironment.webBaseUrl,
            environment: Environment = AppEnvironment.environment,
        ): AppearanceRepository {
            val client = OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(RedactingHttpLoggingInterceptor(environment = environment))
                .build()
            return AppearanceRepository(
                remoteSource = OkHttpAppearanceRemoteSource(
                    webBaseUrl = webBaseUrl,
                    okHttpClient = client,
                ),
                cache = FileAppearanceCache.create(context),
            )
        }
    }
}
