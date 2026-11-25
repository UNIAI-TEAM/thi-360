package vn.unicom.thi360.core.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import vn.unicom.thi360.core.config.Environment

interface AppLogger {
    fun debug(tag: String, message: String)
    fun warn(tag: String, message: String)
}

object AndroidAppLogger : AppLogger {
    override fun debug(tag: String, message: String) {
        Log.d(tag, LogRedactor.redact(message))
    }

    override fun warn(tag: String, message: String) {
        Log.w(tag, LogRedactor.redact(message))
    }
}

object LogRedactor {
    private const val REDACTED = "[REDACTED]"

    private val bearerRegex = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    private val jwtRegex = Regex("""\beyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\b""")
    private val jsonSecretRegex = Regex(
        """(?i)"(password|oldPassword|newPassword|accessToken|refreshToken|token|code)"\s*:\s*"[^"]*"""",
    )
    private val querySecretRegex = Regex(
        """(?i)([?&](?:token|password|accessToken|refreshToken|code)=)[^&\s]+""",
    )

    fun redact(input: String): String {
        var result = bearerRegex.replace(input, "Bearer $REDACTED")
        result = jwtRegex.replace(result, REDACTED)
        result = jsonSecretRegex.replace(result) { match ->
            "\"${match.groupValues[1]}\":\"$REDACTED\""
        }
        result = querySecretRegex.replace(result) { match ->
            "${match.groupValues[1]}$REDACTED"
        }
        return result
    }
}

class RedactingHttpLoggingInterceptor(
    private val environment: Environment,
    private val logger: AppLogger = AndroidAppLogger,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val safePath = request.url.encodedPath
        val startNanos = System.nanoTime()
        return try {
            val response = chain.proceed(request)
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            logRequestOutcome(
                method = request.method,
                path = safePath,
                statusCode = response.code,
                elapsedMs = elapsedMs,
            )
            response
        } catch (cause: Throwable) {
            val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
            logger.warn(
                TAG,
                LogRedactor.redact("${request.method} $safePath failed (${cause::class.simpleName}) in ${elapsedMs}ms"),
            )
            throw cause
        }
    }

    private fun logRequestOutcome(
        method: String,
        path: String,
        statusCode: Int,
        elapsedMs: Long,
    ) {
        val line = LogRedactor.redact("$method $path -> $statusCode (${elapsedMs}ms)")
        if (environment == Environment.DEMO && statusCode < 400) {
            return
        }
        if (statusCode >= 500) {
            logger.warn(TAG, line)
        } else {
            logger.debug(TAG, line)
        }
    }

    companion object {
        private const val TAG = "Thi360Http"
    }
}
