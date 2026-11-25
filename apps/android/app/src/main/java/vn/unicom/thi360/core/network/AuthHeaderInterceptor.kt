package vn.unicom.thi360.core.network

import okhttp3.Interceptor
import okhttp3.Response

object PublicAuthPaths {
    private val paths = setOf(
        "/api/v1/auth/mobile/login",
        "/api/v1/auth/mobile/refresh",
        "/api/v1/auth/mobile/logout",
        "/api/v1/auth/mobile/register/verify",
        "/api/v1/auth/register",
        "/api/v1/auth/register/resend",
        "/api/v1/auth/password/forgot",
        "/api/v1/auth/password/reset",
    )

    fun isPublic(encodedPath: String): Boolean = encodedPath in paths
}

class AuthHeaderInterceptor(
    private val accessTokenProvider: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (PublicAuthPaths.isPublic(original.url.encodedPath) ||
            original.header(HEADER_AUTHORIZATION) != null
        ) {
            return chain.proceed(original)
        }
        val token = accessTokenProvider() ?: return chain.proceed(original)
        val authenticated = original.newBuilder()
            .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$token")
            .build()
        return chain.proceed(authenticated)
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
