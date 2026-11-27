package vn.unicom.thi360.feature.account

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.core.ui.toLoadFailed

class AccountProfileStore(
    private val gateway: ProfileGateway,
    private val isOnlineProvider: () -> Boolean = { true },
    sessionCleanup: SessionCleanup? = null,
) : AutoCloseable {

    private val _state = MutableStateFlow<LoadState<UserProfile>>(LoadState.Loading)
    val state: StateFlow<LoadState<UserProfile>> = _state.asStateFlow()

    private val _refreshError = MutableStateFlow<LoadState.Failed?>(null)
    val refreshError: StateFlow<LoadState.Failed?> = _refreshError.asStateFlow()

    private val epoch = AtomicLong(0L)
    private val lock = Any()
    private var inFlightRefresh: Deferred<ApiResult<UserProfile>>? = null
    private val cleanupRegistration: AutoCloseable? = sessionCleanup?.register { wipe() }

    fun currentProfileOrNull(): UserProfile? = (_state.value as? LoadState.Loaded)?.value

    suspend fun refresh(): ApiResult<UserProfile> {
        val created: CompletableDeferred<ApiResult<UserProfile>>
        val existingInFlight: Deferred<ApiResult<UserProfile>>?
        val startEpoch: Long
        val existingProfile: UserProfile?

        synchronized(lock) {
            existingInFlight = inFlightRefresh
            if (existingInFlight != null) {
                created = CompletableDeferred()
                startEpoch = 0L
                existingProfile = null
            } else {
                created = CompletableDeferred()
                inFlightRefresh = created
                startEpoch = epoch.get()
                existingProfile = currentProfileOrNull()
                if (existingProfile == null) {
                    _state.value = LoadState.Loading
                    _refreshError.value = null
                }
            }
        }

        if (existingInFlight != null) {
            return existingInFlight.await()
        }

        return try {
            val result = gateway.fetch()
            synchronized(lock) {
                if (epoch.get() == startEpoch) {
                    applyRefreshResultLocked(existingProfile, result)
                }
                if (inFlightRefresh === created) {
                    inFlightRefresh = null
                }
            }
            created.complete(result)
            result
        } catch (t: Throwable) {
            synchronized(lock) {
                if (inFlightRefresh === created) {
                    inFlightRefresh = null
                }
            }
            created.completeExceptionally(t)
            throw t
        }
    }

    private fun applyRefreshResultLocked(
        existing: UserProfile?,
        result: ApiResult<UserProfile>,
    ) {
        when (result) {
            is ApiResult.Success -> {
                _refreshError.value = null
                _state.value = LoadState.Loaded(result.data, stale = false)
            }

            is ApiResult.HttpFailure -> {
                val failed = result.toLoadFailed().copy(canRetry = true)
                if (existing != null) {
                    _state.value = LoadState.Loaded(existing, stale = true)
                    _refreshError.value = failed
                } else {
                    _refreshError.value = null
                    _state.value = failed
                }
            }

            is ApiResult.NetworkFailure -> {
                val failed = result.toLoadFailed(isOnline = isOnlineProvider()).copy(canRetry = true)
                if (existing != null) {
                    _state.value = LoadState.Loaded(existing, stale = true)
                    _refreshError.value = failed
                } else {
                    _refreshError.value = null
                    _state.value = failed
                }
            }
        }
    }

    fun replace(profile: UserProfile) {
        synchronized(lock) {
            epoch.incrementAndGet()
            inFlightRefresh = null
            _refreshError.value = null
            _state.value = LoadState.Loaded(profile, stale = false)
        }
    }

    fun wipe() {
        synchronized(lock) {
            epoch.incrementAndGet()
            inFlightRefresh = null
            _refreshError.value = null
            _state.value = LoadState.Loading
        }
    }

    override fun close() {
        cleanupRegistration?.close()
    }
}
