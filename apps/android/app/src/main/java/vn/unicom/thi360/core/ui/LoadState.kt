package vn.unicom.thi360.core.ui

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import vn.unicom.thi360.core.network.ApiResult

sealed interface LoadFailureKind {
    data object Offline : LoadFailureKind
    data object Server : LoadFailureKind
    data object NotFound : LoadFailureKind
    data object Gone : LoadFailureKind
    data object Forbidden : LoadFailureKind
    data class Conflict(val code: String) : LoadFailureKind
}

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>

    data class Loaded<T>(
        val value: T,
        val stale: Boolean = false,
    ) : LoadState<T>

    data object Empty : LoadState<Nothing>

    data class Failed(
        val kind: LoadFailureKind,
        val canRetry: Boolean = true,
        val retryAfter: Long? = null,
    ) : LoadState<Nothing>
}

fun ApiResult.HttpFailure.toLoadFailed(): LoadState.Failed = when (statusCode) {
    404 -> LoadState.Failed(
        kind = LoadFailureKind.NotFound,
        canRetry = false,
    )
    410 -> LoadState.Failed(
        kind = LoadFailureKind.Gone,
        canRetry = false,
    )
    403 -> LoadState.Failed(
        kind = LoadFailureKind.Forbidden,
        canRetry = false,
    )
    409 -> LoadState.Failed(
        kind = LoadFailureKind.Conflict(error?.code.orEmpty()),
        canRetry = false,
    )
    429 -> LoadState.Failed(
        kind = LoadFailureKind.Server,
        canRetry = true,
        retryAfter = retryAfterSeconds ?: error?.retryAfter,
    )
    else -> LoadState.Failed(
        kind = LoadFailureKind.Server,
        canRetry = true,
        retryAfter = retryAfterSeconds,
    )
}

fun ApiResult.NetworkFailure.toLoadFailed(isOnline: Boolean): LoadState.Failed {
    val isConnectivityCause = !isOnline ||
        cause is UnknownHostException ||
        cause is ConnectException ||
        cause is NoRouteToHostException ||
        cause is SocketTimeoutException ||
        cause is IOException
    return LoadState.Failed(
        kind = if (isConnectivityCause) LoadFailureKind.Offline else LoadFailureKind.Server,
        canRetry = true,
    )
}
