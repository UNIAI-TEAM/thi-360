package vn.unicom.thi360.core.image

import android.content.Context
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.network.TokenAuthenticator
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.TokenRefresher

internal class HostScopedBearerInterceptor(
    private val apiHost: String,
    private val accessTokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder().removeHeader(AUTHORIZATION_HEADER)
        if (original.url.host.equals(apiHost, ignoreCase = true)) {
            val token = accessTokenProvider()
            if (!token.isNullOrBlank()) {
                builder.header(AUTHORIZATION_HEADER, "Bearer $token")
            }
        }
        return chain.proceed(builder.build())
    }

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
    }
}

internal class HostScopedTokenAuthenticator(
    private val apiHost: String,
    private val delegate: TokenAuthenticator,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (!response.request.url.host.equals(apiHost, ignoreCase = true)) {
            return null
        }
        return delegate.authenticate(route, response)
    }
}

class Thi360ImageLoader(
    val imageLoader: ImageLoader,
    val okHttpClient: OkHttpClient,
    private val cleanupRegistration: AutoCloseable? = null,
) : AutoCloseable {

    fun clearMemoryCache() {
        imageLoader.memoryCache?.clear()
    }

    override fun close() {
        cleanupRegistration?.close()
        imageLoader.shutdown()
    }

    companion object {
        fun create(
            context: Context,
            apiBaseUrl: String,
            sessionStack: Thi360SessionStack,
            sessionCleanup: SessionCleanup? = null,
        ): Thi360ImageLoader = create(
            context = context,
            apiBaseUrl = apiBaseUrl,
            baseOkHttpClient = sessionStack.publicOkHttpClient,
            accessTokenProvider = { sessionStack.sessionManager.currentAccessToken() },
            tokenRefresher = sessionStack.sessionManager,
            sessionCleanup = sessionCleanup,
        )

        fun create(
            context: Context,
            apiBaseUrl: String,
            baseOkHttpClient: OkHttpClient,
            accessTokenProvider: () -> String?,
            tokenRefresher: TokenRefresher? = null,
            sessionCleanup: SessionCleanup? = null,
        ): Thi360ImageLoader {
            val apiHost = apiBaseUrl.toHttpUrlOrNull()?.host
                ?: error("Invalid apiBaseUrl for ImageLoader: $apiBaseUrl")
            val clientBuilder = baseOkHttpClient.newBuilder()
                .cache(null)
                .addInterceptor(HostScopedBearerInterceptor(apiHost, accessTokenProvider))
            if (tokenRefresher != null) {
                clientBuilder.authenticator(
                    HostScopedTokenAuthenticator(apiHost, TokenAuthenticator(tokenRefresher)),
                )
            }
            val imageOkHttpClient = clientBuilder.build()

            val coilLoader = ImageLoader.Builder(context.applicationContext)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { imageOkHttpClient }))
                }
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(context.applicationContext, 0.20)
                        .build()
                }
                .diskCache(null)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()

            val registration = sessionCleanup?.register {
                coilLoader.memoryCache?.clear()
            }

            return Thi360ImageLoader(
                imageLoader = coilLoader,
                okHttpClient = imageOkHttpClient,
                cleanupRegistration = registration,
            )
        }
    }
}
