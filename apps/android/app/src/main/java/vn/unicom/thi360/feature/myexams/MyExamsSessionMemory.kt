package vn.unicom.thi360.feature.myexams

import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.network.generated.api.MyAttemptsResultsApi
import vn.unicom.thi360.core.network.generated.api.MyExamsApi
import vn.unicom.thi360.core.network.safeApiCall
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.feature.history.HistoryFeed

data class LearnerGreetingIdentity(
    val fullName: String? = null,
    val username: String? = null,
    val fetchedFromMe: Boolean = false,
)

class MyExamsSessionMemory(
    private val sessionStack: Thi360SessionStack,
    connectivityMonitor: ConnectivityMonitor,
    wallClockMillis: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val sessionManager = sessionStack.sessionManager
    private val myExamsApi: MyExamsApi = sessionStack.authenticatedService(MyExamsApi::class.java)
    private val attemptsApi: MyAttemptsResultsApi =
        sessionStack.authenticatedService(MyAttemptsResultsApi::class.java)

    val myExamsFeed: MyExamsFeed = MyExamsFeed.fromApi(
        api = myExamsApi,
        connectivityMonitor = connectivityMonitor,
        onForbidden = { sessionManager.refreshSession() },
        wallClockMillis = wallClockMillis,
        scope = scope,
    )

    val historyFeed: HistoryFeed = HistoryFeed.fromApi(
        api = attemptsApi,
        catalogOptionsCache = sessionStack.catalogOptionsCache,
        connectivityMonitor = connectivityMonitor,
        onForbidden = { sessionManager.refreshSession() },
        wallClockMillis = wallClockMillis,
        scope = scope,
    )

    private val _greetingIdentity = MutableStateFlow(initialIdentity())
    val greetingIdentity: StateFlow<LearnerGreetingIdentity> = _greetingIdentity.asStateFlow()

    private val identityLock = Any()
    private var lastGeneration: Long = sessionManager.sessionGeneration.value
    private var lastAccountId: String? = sessionManager.currentAccountIdOrNull()
    private var identityFetchJob: Job? = null

    fun syncAccountScope() {
        val currentGeneration = sessionManager.sessionGeneration.value
        val currentAccount = sessionManager.currentAccountIdOrNull()
        val isSignedOut = sessionManager.state.value is SessionState.SignedOut
        if (isSignedOut || currentGeneration != lastGeneration || currentAccount != lastAccountId) {
            lastGeneration = currentGeneration
            lastAccountId = currentAccount
            clear()
        } else if (_greetingIdentity.value.username.isNullOrBlank()) {
            val activeUsername = (sessionManager.state.value as? SessionState.Active)?.username
                ?: sessionStack.tokenStore.read()?.username
            if (!activeUsername.isNullOrBlank()) {
                _greetingIdentity.value = _greetingIdentity.value.copy(username = activeUsername)
            }
        }
    }

    fun ensureGreetingIdentityLoaded() {
        syncAccountScope()
        if (sessionManager.state.value !is SessionState.Active) return
        val jobToStart = synchronized(identityLock) {
            if (_greetingIdentity.value.fetchedFromMe || identityFetchJob?.isActive == true) {
                return
            }
            val expectedGeneration = lastGeneration
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val currentJob = coroutineContext[Job]
                try {
                    when (val result = safeApiCall { sessionStack.authenticatedAuthApi.me() }) {
                        is ApiResult.Success -> {
                            if (sessionManager.sessionGeneration.value == expectedGeneration) {
                                _greetingIdentity.value = LearnerGreetingIdentity(
                                    fullName = result.data.fullName,
                                    username = result.data.username,
                                    fetchedFromMe = true,
                                )
                            }
                        }
                        else -> Unit
                    }
                } finally {
                    synchronized(identityLock) {
                        if (identityFetchJob === currentJob) {
                            identityFetchJob = null
                        }
                    }
                }
            }
            identityFetchJob = job
            job
        }
        jobToStart.start()
    }

    fun clear() {
        myExamsFeed.clear()
        historyFeed.clear()
        synchronized(identityLock) {
            identityFetchJob?.cancel()
            identityFetchJob = null
        }
        _greetingIdentity.value = initialIdentity()
    }

    private fun initialIdentity(): LearnerGreetingIdentity {
        val activeUsername = (sessionManager.state.value as? SessionState.Active)?.username
            ?: sessionStack.tokenStore.read()?.username
        return LearnerGreetingIdentity(
            fullName = null,
            username = activeUsername,
            fetchedFromMe = false,
        )
    }

    companion object {
        private val cache = WeakHashMap<SessionManager, MyExamsSessionMemory>()
        private val registeredCleanups = WeakHashMap<SessionCleanup, Boolean>()

        fun forSession(
            sessionStack: Thi360SessionStack,
            connectivityMonitor: ConnectivityMonitor,
            sessionCleanup: SessionCleanup? = null,
            wallClockMillis: () -> Long = System::currentTimeMillis,
        ): MyExamsSessionMemory = synchronized(cache) {
            val manager = sessionStack.sessionManager
            val existing = cache[manager]
            if (existing != null) {
                existing.syncAccountScope()
                return@synchronized existing
            }
            val created = MyExamsSessionMemory(
                sessionStack = sessionStack,
                connectivityMonitor = connectivityMonitor,
                wallClockMillis = wallClockMillis,
            )
            cache[manager] = created
            if (sessionCleanup != null && registeredCleanups[sessionCleanup] != true) {
                registeredCleanups[sessionCleanup] = true
                sessionCleanup.register {
                    synchronized(cache) {
                        cache.values.forEach { it.clear() }
                    }
                }
            }
            created
        }

        internal fun clearAllForTesting() {
            synchronized(cache) {
                cache.values.forEach { it.clear() }
                cache.clear()
            }
        }
    }
}
