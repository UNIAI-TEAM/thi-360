package vn.unicom.thi360.feature.recovery

import android.content.Intent
import android.net.Uri
import java.net.URI

data class ResetLink(val token: String) {
    init {
        require(token.isNotBlank()) { "Reset token must not be blank" }
    }

    override fun toString(): String = "ResetLink(token=[REDACTED])"

    companion object {
        const val RESET_PASSWORD_PATH = "/reset-password"
        const val TOKEN_QUERY_PARAM = "token"

        fun allowedHostFromWebBaseUrl(webBaseUrl: String): String =
            URI.create(webBaseUrl).host
                ?: error("Invalid webBaseUrl without host: $webBaseUrl")

        fun parse(uri: Uri?, allowedHost: String): ResetLink? =
            (parseUri(uri, allowedHost) as? ResetLinkIntentResult.Valid)?.link

        fun parseUri(uri: Uri?, allowedHost: String): ResetLinkIntentResult {
            if (uri == null) return ResetLinkIntentResult.Ignored
            val scheme = uri.scheme ?: return ResetLinkIntentResult.Ignored
            if (!scheme.equals("https", ignoreCase = true)) return ResetLinkIntentResult.Ignored
            val host = uri.host ?: return ResetLinkIntentResult.Ignored
            if (!host.equals(allowedHost, ignoreCase = true)) return ResetLinkIntentResult.Ignored
            if (uri.path != RESET_PASSWORD_PATH) return ResetLinkIntentResult.Ignored

            val rawToken = uri.getQueryParameter(TOKEN_QUERY_PARAM)?.trim()
            return if (rawToken.isNullOrEmpty()) {
                ResetLinkIntentResult.MissingToken
            } else {
                ResetLinkIntentResult.Valid(ResetLink(rawToken))
            }
        }

        fun parseIntent(intent: Intent?, allowedHost: String): ResetLinkIntentResult {
            if (intent == null || intent.action != Intent.ACTION_VIEW) {
                return ResetLinkIntentResult.Ignored
            }
            return parseUri(intent.data, allowedHost)
        }
    }
}

sealed interface ResetLinkIntentResult {
    data object Ignored : ResetLinkIntentResult

    data class Valid(val link: ResetLink) : ResetLinkIntentResult {
        override fun toString(): String = "Valid(link=[REDACTED])"
    }

    data object MissingToken : ResetLinkIntentResult
}
