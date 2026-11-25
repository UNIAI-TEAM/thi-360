package vn.unicom.thi360.core.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.storage.TokenStore
import vn.unicom.thi360.core.storage.Tokens

sealed interface LoginOutcome {
    data object Authenticated : LoginOutcome
    data class StudentOnly(val username: String) : LoginOutcome
    data class Error(val kind: LoginErrorKind) : LoginOutcome
}

sealed interface RefreshOutcome {
    data class Refreshed(val tokens: Tokens) : RefreshOutcome
    data object Expired : RefreshOutcome
    data object StudentOnly : RefreshOutcome
    data object Offline : RefreshOutcome
    data object TransientFailure : RefreshOutcome
    data object SignedOut : RefreshOutcome
}

interface TokenRefresher {
    suspend fun refreshSession(failedAccessToken: String? = null): RefreshOutcome
}

class SessionManager(
    private val tokenStore: TokenStore,
    private val authGateway: MobileAuthGateway,
    private val onSessionCleared: () -> Unit = {},
    startLocked: Boolean = false,
) : TokenRefresher {

    private data class InFlightRefresh(
        val refreshToken: String,
        val deferred: CompletableDeferred<RefreshOutcome>,
    )

    private val refreshMutex = Mutex()
    private var inFlightRefresh: InFlightRefresh? = null

    private val _state = MutableStateFlow(initialStateFrom(tokenStore.read(), startLocked))
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _sessionGeneration = MutableStateFlow(0L)
    val sessionGeneration: StateFlow<Long> = _sessionGeneration.asStateFlow()

    fun currentAccessToken(): String? = tokenStore.read()?.accessToken

    fun currentRefreshToken(): String? = tokenStore.read()?.refreshToken

    fun lockSession() {
        val current = _state.value
        val stored = tokenStore.read() ?: return
        if (current is SessionState.Active || current is SessionState.MustChangePassword) {
            val username = when (current) {
                is SessionState.Active -> current.username ?: stored.username
                is SessionState.MustChangePassword -> current.username ?: stored.username
                is SessionState.Locked, is SessionState.SignedOut -> stored.username
            }
            _state.value = SessionState.Locked(username = username)
        }
    }

    suspend fun login(identifier: String, password: String): LoginOutcome {
        val trimmedIdentifier = identifier.trim()
        return when (val result = authGateway.login(trimmedIdentifier, password)) {
            is ApiResult.Success -> {
                val resolvedUsername = result.data.username ?: trimmedIdentifier
                val stored = result.data.copy(username = resolvedUsername)
                refreshMutex.withLock {
                    inFlightRefresh = null
                    onSessionCleared()
                    tokenStore.write(stored)
                    _sessionGeneration.value += 1L
                    _state.value = stateForTokens(stored, isOffline = false)
                }
                LoginOutcome.Authenticated
            }

            is ApiResult.HttpFailure -> {
                if (result.statusCode == HTTP_FORBIDDEN &&
                    result.error?.code == CODE_APP_STUDENT_ONLY
                ) {
                    refreshMutex.withLock {
                        inFlightRefresh = null
                        onSessionCleared()
                        _state.value = SessionState.SignedOut(
                            SignOutReason.StudentOnly(username = trimmedIdentifier),
                        )
                    }
                    LoginOutcome.StudentOnly(trimmedIdentifier)
                } else {
                    LoginOutcome.Error(AuthErrorMessage.fromApiErrorCode(result.error?.code))
                }
            }

            is ApiResult.NetworkFailure -> LoginOutcome.Error(LoginErrorKind.NETWORK)
        }
    }

    suspend fun replaceSessionFromLock(identifier: String, password: String): LoginOutcome {
        val trimmedIdentifier = identifier.trim()
        return when (val result = authGateway.login(trimmedIdentifier, password)) {
            is ApiResult.Success -> {
                val resolvedUsername = result.data.username ?: trimmedIdentifier
                val stored = result.data.copy(username = resolvedUsername)
                val previousRefreshToken = refreshMutex.withLock {
                    val oldToken = tokenStore.read()?.refreshToken
                    inFlightRefresh = null
                    onSessionCleared()
                    tokenStore.write(stored)
                    _sessionGeneration.value += 1L
                    _state.value = stateForTokens(stored, isOffline = false)
                    oldToken
                }
                if (previousRefreshToken != null && previousRefreshToken != stored.refreshToken) {
                    withContext(NonCancellable) {
                        runCatching { authGateway.logout(previousRefreshToken) }
                    }
                }
                LoginOutcome.Authenticated
            }

            is ApiResult.HttpFailure -> {
                if (result.statusCode == HTTP_FORBIDDEN &&
                    result.error?.code == CODE_APP_STUDENT_ONLY
                ) {
                    val previousRefreshToken = refreshMutex.withLock {
                        val oldToken = tokenStore.read()?.refreshToken
                        inFlightRefresh = null
                        tokenStore.clear()
                        onSessionCleared()
                        _sessionGeneration.value += 1L
                        _state.value = SessionState.SignedOut(
                            SignOutReason.StudentOnly(username = trimmedIdentifier),
                        )
                        oldToken
                    }
                    if (previousRefreshToken != null) {
                        withContext(NonCancellable) {
                            runCatching { authGateway.logout(previousRefreshToken) }
                        }
                    }
                    LoginOutcome.StudentOnly(trimmedIdentifier)
                } else {
                    LoginOutcome.Error(AuthErrorMessage.fromApiErrorCode(result.error?.code))
                }
            }

            is ApiResult.NetworkFailure -> LoginOutcome.Error(LoginErrorKind.NETWORK)
        }
    }

    suspend fun unlockAndRefresh(): RefreshOutcome {
        val outcome = refreshSession(failedAccessToken = null)
        refreshMutex.withLock {
            if (_state.value !is SessionState.Locked) {
                return outcome
            }
            when (outcome) {
                is RefreshOutcome.Refreshed -> {
                    _state.value = stateForTokens(outcome.tokens, isOffline = false)
                }

                RefreshOutcome.Offline, RefreshOutcome.TransientFailure -> {
                    val stored = tokenStore.read()
                    if (stored != null) {
                        _state.value = stateForTokens(stored, isOffline = true)
                    } else {
                        onSessionCleared()
                        _state.value = SessionState.SignedOut(SignOutReason.Expired)
                    }
                }

                RefreshOutcome.Expired, RefreshOutcome.StudentOnly, RefreshOutcome.SignedOut -> Unit
            }
        }
        return outcome
    }

    override suspend fun refreshSession(failedAccessToken: String?): RefreshOutcome {
        var existingDeferred: CompletableDeferred<RefreshOutcome>? = null
        var ownedInFlight: InFlightRefresh? = null
        var storedUsername: String? = null

        refreshMutex.withLock {
            val stored = tokenStore.read()
            if (stored == null) {
                return RefreshOutcome.SignedOut
            }
            if (failedAccessToken != null && stored.accessToken != failedAccessToken) {
                return RefreshOutcome.Refreshed(stored)
            }
            val currentInFlight = inFlightRefresh
            if (currentInFlight != null && currentInFlight.refreshToken == stored.refreshToken) {
                existingDeferred = currentInFlight.deferred
            } else {
                val created = InFlightRefresh(
                    refreshToken = stored.refreshToken,
                    deferred = CompletableDeferred(),
                )
                inFlightRefresh = created
                ownedInFlight = created
                storedUsername = stored.username
            }
        }

        existingDeferred?.let { return it.await() }

        val owned = checkNotNull(ownedInFlight)
        // Run the single-flight refresh non-cancellably so one cancelled caller cannot strand concurrent waiters.
        return withContext(NonCancellable) {
            val outcome = try {
                executeSingleFlightRefresh(
                    refreshToken = owned.refreshToken,
                    fallbackUsername = storedUsername,
                )
            } catch (cancelled: CancellationException) {
                RefreshOutcome.TransientFailure
            } catch (_: Throwable) {
                RefreshOutcome.TransientFailure
            } finally {
                refreshMutex.withLock {
                    if (inFlightRefresh === owned) {
                        inFlightRefresh = null
                    }
                }
            }
            owned.deferred.complete(outcome)
            outcome
        }
    }

    private suspend fun executeSingleFlightRefresh(
        refreshToken: String,
        fallbackUsername: String?,
    ): RefreshOutcome = when (val result = authGateway.refresh(refreshToken)) {
        is ApiResult.Success -> {
            refreshMutex.withLock {
                val stillStored = tokenStore.read()
                if (stillStored == null || stillStored.refreshToken != refreshToken) {
                    return RefreshOutcome.SignedOut
                }
                val resolvedUsername = result.data.username ?: stillStored.username ?: fallbackUsername
                val updated = result.data.copy(username = resolvedUsername)
                tokenStore.write(updated)
                _state.value = when {
                    _state.value is SessionState.Locked -> SessionState.Locked(
                        username = resolvedUsername,
                    )
                    updated.mustChangePassword -> SessionState.MustChangePassword(
                        username = resolvedUsername,
                        isOffline = false,
                    )
                    else -> SessionState.Active(
                        username = resolvedUsername,
                        isOffline = false,
                    )
                }
                RefreshOutcome.Refreshed(updated)
            }
        }

        is ApiResult.HttpFailure -> {
            when {
                result.statusCode == HTTP_FORBIDDEN &&
                    result.error?.code == CODE_APP_STUDENT_ONLY -> {
                    refreshMutex.withLock {
                        val stillStored = tokenStore.read()
                        if (stillStored == null || stillStored.refreshToken != refreshToken) {
                            return RefreshOutcome.SignedOut
                        }
                        val username = stillStored.username ?: fallbackUsername.orEmpty()
                        tokenStore.clear()
                        onSessionCleared()
                        _sessionGeneration.value += 1L
                        _state.value = SessionState.SignedOut(SignOutReason.StudentOnly(username))
                        RefreshOutcome.StudentOnly
                    }
                }

                result.statusCode == HTTP_UNAUTHORIZED -> {
                    refreshMutex.withLock {
                        val stillStored = tokenStore.read()
                        if (stillStored == null || stillStored.refreshToken != refreshToken) {
                            return RefreshOutcome.SignedOut
                        }
                        tokenStore.clear()
                        onSessionCleared()
                        _sessionGeneration.value += 1L
                        _state.value = SessionState.SignedOut(SignOutReason.Expired)
                        RefreshOutcome.Expired
                    }
                }

                else -> {
                    refreshMutex.withLock {
                        val stillStored = tokenStore.read()
                        if (stillStored == null || stillStored.refreshToken != refreshToken) {
                            return RefreshOutcome.SignedOut
                        }
                        markOfflineIfSignedInLocked()
                        RefreshOutcome.TransientFailure
                    }
                }
            }
        }

        is ApiResult.NetworkFailure -> {
            refreshMutex.withLock {
                val stillStored = tokenStore.read()
                if (stillStored == null || stillStored.refreshToken != refreshToken) {
                    return RefreshOutcome.SignedOut
                }
                markOfflineIfSignedInLocked()
                RefreshOutcome.Offline
            }
        }
    }

    private fun markOfflineIfSignedInLocked() {
        _state.value = when (val current = _state.value) {
            is SessionState.Active -> current.copy(isOffline = true)
            is SessionState.MustChangePassword -> current.copy(isOffline = true)
            is SessionState.Locked, is SessionState.SignedOut -> current
        }
    }

    suspend fun validateSessionOnLaunch(): RefreshOutcome {
        if (tokenStore.read() == null) return RefreshOutcome.SignedOut
        return refreshSession(failedAccessToken = null)
    }

    suspend fun retryConnection(): RefreshOutcome = refreshSession(failedAccessToken = null)

    fun acknowledgeRoleNotice() {
        if (_state.value is SessionState.SignedOut) {
            _state.value = SessionState.SignedOut(SignOutReason.None)
        }
    }

    suspend fun adoptSession(tokens: Tokens) {
        refreshMutex.withLock {
            inFlightRefresh = null
            onSessionCleared()
            tokenStore.write(tokens)
            _sessionGeneration.value += 1L
            _state.value = stateForTokens(tokens, isOffline = false)
        }
    }

    suspend fun markPasswordChanged() {
        refreshMutex.withLock {
            val stored = tokenStore.read() ?: return
            val updated = stored.copy(mustChangePassword = false)
            tokenStore.write(updated)
            _state.value = SessionState.Active(username = updated.username, isOffline = false)
        }
    }

    suspend fun clearSession(reason: SignOutReason = SignOutReason.None) {
        refreshMutex.withLock {
            inFlightRefresh = null
            tokenStore.clear()
            onSessionCleared()
            _sessionGeneration.value += 1L
            _state.value = SessionState.SignedOut(reason)
        }
    }

    suspend fun logout() {
        val refreshToken = refreshMutex.withLock {
            inFlightRefresh = null
            val existing = tokenStore.read()?.refreshToken
            tokenStore.clear()
            onSessionCleared()
            _sessionGeneration.value += 1L
            _state.value = SessionState.SignedOut(SignOutReason.None)
            existing
        }
        if (refreshToken != null) {
            withContext(NonCancellable) {
                runCatching { authGateway.logout(refreshToken) }
            }
        }
    }

    companion object {
        const val CODE_APP_STUDENT_ONLY = "APP_STUDENT_ONLY"
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403

        private fun initialStateFrom(tokens: Tokens?, startLocked: Boolean): SessionState =
            when {
                tokens == null -> SessionState.SignedOut(SignOutReason.None)
                startLocked -> SessionState.Locked(username = tokens.username)
                else -> stateForTokens(tokens, isOffline = false)
            }

        private fun stateForTokens(tokens: Tokens, isOffline: Boolean): SessionState =
            if (tokens.mustChangePassword) {
                SessionState.MustChangePassword(username = tokens.username, isOffline = isOffline)
            } else {
                SessionState.Active(username = tokens.username, isOffline = isOffline)
            }
    }
}
