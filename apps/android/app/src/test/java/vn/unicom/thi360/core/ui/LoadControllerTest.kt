package vn.unicom.thi360.core.ui

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.MutableConnectivityMonitor

@OptIn(ExperimentalCoroutinesApi::class)
class LoadControllerTest {

    @Test
    fun failureKinds() = runTest {
        val monitor = MutableConnectivityMonitor(true)
        var nextResult: ApiResult<String> = ApiResult.HttpFailure(404, ApiError("NOT_FOUND"))
        val controller = LoadController(
            scope = this,
            connectivityMonitor = monitor,
            fetch = { nextResult },
        )

        controller.load().join()
        assertEquals(LoadState.Failed(LoadFailureKind.NotFound, canRetry = false), controller.state.value)

        nextResult = ApiResult.HttpFailure(410, ApiError("GONE"))
        controller.load().join()
        assertEquals(LoadState.Failed(LoadFailureKind.Gone, canRetry = false), controller.state.value)

        nextResult = ApiResult.HttpFailure(403, ApiError("FORBIDDEN"))
        controller.load().join()
        assertEquals(LoadState.Failed(LoadFailureKind.Forbidden, canRetry = false), controller.state.value)

        nextResult = ApiResult.HttpFailure(409, ApiError("ALREADY_PURCHASED"))
        controller.load().join()
        assertEquals(
            LoadState.Failed(LoadFailureKind.Conflict("ALREADY_PURCHASED"), canRetry = false),
            controller.state.value,
        )

        nextResult = ApiResult.HttpFailure(500, ApiError("SERVER_ERROR"))
        controller.load().join()
        assertEquals(LoadState.Failed(LoadFailureKind.Server, canRetry = true), controller.state.value)

        nextResult = ApiResult.HttpFailure(429, ApiError("RATE_LIMITED", retryAfter = 30L), retryAfterSeconds = 30L)
        controller.load().join()
        assertEquals(
            LoadState.Failed(LoadFailureKind.Server, canRetry = true, retryAfter = 30L),
            controller.state.value,
        )
        controller.close()
    }

    @Test
    fun offlineKeepsStaleData() = runTest {
        val monitor = MutableConnectivityMonitor(true)
        var nextResult: ApiResult<List<String>> = ApiResult.Success(listOf("Exam 1", "Exam 2"))
        val controller = LoadController(
            scope = this,
            connectivityMonitor = monitor,
            isEmpty = { it.isEmpty() },
            fetch = { nextResult },
        )

        controller.load().join()
        assertEquals(LoadState.Loaded(listOf("Exam 1", "Exam 2"), stale = false), controller.state.value)

        // Going offline keeps previously loaded data with stale = true.
        monitor.setOnline(false)
        controller.refresh().join()
        assertEquals(LoadState.Loaded(listOf("Exam 1", "Exam 2"), stale = true), controller.state.value)
        assertEquals(LoadFailureKind.Offline, controller.staleFailure.value?.kind)
        controller.close()
    }

    @Test
    fun autoRetryWhenOnline() = runTest {
        val monitor = MutableConnectivityMonitor(false)
        val calls = AtomicInteger(0)
        val controller = LoadController(
            scope = this,
            connectivityMonitor = monitor,
            fetch = {
                calls.incrementAndGet()
                ApiResult.Success("fresh-from-server")
            },
        )

        controller.load().join()
        assertEquals(LoadState.Failed(LoadFailureKind.Offline, canRetry = true), controller.state.value)
        assertEquals(0, calls.get())

        // OS reports online -> auto-retries exactly once without user tap.
        monitor.setOnline(true)
        advanceUntilIdle()
        assertEquals(1, calls.get())
        assertEquals(LoadState.Loaded("fresh-from-server", stale = false), controller.state.value)

        // Repeated online notification must not refetch.
        monitor.setOnline(true)
        advanceUntilIdle()
        assertEquals(1, calls.get())
        controller.close()
    }

    @Test
    fun pullToRefreshReplacesData() = runTest {
        val monitor = MutableConnectivityMonitor(true)
        var callIndex = 0
        val controller = LoadController(
            scope = this,
            connectivityMonitor = monitor,
            fetch = {
                callIndex++
                when (callIndex) {
                    1 -> ApiResult.Success(listOf("V1"))
                    2 -> ApiResult.Success(listOf("V2", "V2-B"))
                    else -> ApiResult.NetworkFailure(IOException("transient error"))
                }
            },
        )

        controller.load().join()
        assertEquals(LoadState.Loaded(listOf("V1"), stale = false), controller.state.value)

        controller.refresh().join()
        assertFalse(controller.isRefreshing.value)
        assertEquals(LoadState.Loaded(listOf("V2", "V2-B"), stale = false), controller.state.value)

        // Failed pull-to-refresh keeps previous data with stale = true.
        controller.refresh().join()
        assertFalse(controller.isRefreshing.value)
        assertEquals(LoadState.Loaded(listOf("V2", "V2-B"), stale = true), controller.state.value)
        assertTrue(controller.staleFailure.value != null)
        controller.close()
    }

    @Test
    fun staleDataClearedOnAccountChange() = runTest {
        val monitor = MutableConnectivityMonitor(true)
        val accountIdFlow = MutableStateFlow<String?>("account-A")
        val controller = LoadController(
            scope = this,
            connectivityMonitor = monitor,
            accountIdFlow = accountIdFlow,
            fetch = { ApiResult.Success("Secret data of ${accountIdFlow.value}") },
        )

        controller.load().join()
        assertEquals(LoadState.Loaded("Secret data of account-A", stale = false), controller.state.value)

        // Logout sets accountId = null -> immediately wipes in-memory cached data back to Loading.
        accountIdFlow.value = null
        advanceUntilIdle()
        assertEquals(LoadState.Loading, controller.state.value)

        // Account B logs in while offline -> must NOT see Account A's stale data!
        accountIdFlow.value = "account-B"
        advanceUntilIdle()
        monitor.setOnline(false)
        controller.load().join()
        assertEquals(LoadState.Failed(LoadFailureKind.Offline, canRetry = true), controller.state.value)

        // Account B loads online, then switches directly to Account C -> Account B data cleared immediately.
        monitor.setOnline(true)
        advanceUntilIdle()
        assertEquals(LoadState.Loaded("Secret data of account-B", stale = false), controller.state.value)

        accountIdFlow.value = "account-C"
        advanceUntilIdle()
        assertEquals(LoadState.Loading, controller.state.value)
        controller.close()
    }
}
