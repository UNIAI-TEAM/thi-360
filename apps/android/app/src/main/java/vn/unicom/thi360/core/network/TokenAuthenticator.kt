package vn.unicom.thi360.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import vn.unicom.thi360.core.session.RefreshOutcome
import vn.unicom.thi360.core.session.TokenRefresher

class TokenAuthenticator(
    private val tokenRefresher: TokenRefresher,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (PublicAuthPaths.isPublic(response.request.url.encodedPath)) {
            return null
        }
        if (responseCount(response) >= MAX_AUTH_RETRIES) {
            return null
        }

        val failedToken = response.request
            .header(AuthHeaderInterceptor.HEADER_AUTHORIZATION)
            ?.removePrefix(AuthHeaderInterceptor.BEARER_PREFIX)
            ?.takeIf { it.isNotBlank() }

        val outcome = runBlocking {
            tokenRefresher.refreshSession(failedAccessToken = failedToken)
        }
        return when (outcome) {
            is RefreshOutcome.Refreshed -> response.request.newBuilder()
                .header(
                    AuthHeaderInterceptor.HEADER_AUTHORIZATION,
                    "${AuthHeaderInterceptor.BEARER_PREFIX}${outcome.tokens.accessToken}",
                )
                .build()

            else -> null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        private const val MAX_AUTH_RETRIES = 2
    }
}
