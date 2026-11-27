package vn.unicom.thi360.feature.result

import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import vn.unicom.thi360.core.catalog.CatalogOptionItem
import vn.unicom.thi360.core.catalog.CatalogOptionType
import vn.unicom.thi360.core.catalog.CatalogOptionsCache
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.network.generated.api.MyAttemptsResultsApi
import vn.unicom.thi360.core.network.generated.model.AttemptResultResponse
import vn.unicom.thi360.core.network.generated.model.AttemptReviewResponse
import vn.unicom.thi360.core.network.safeApiCall
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.session.SessionState

interface ResultGateway {
    suspend fun getResult(attemptId: UUID): ApiResult<AttemptResultResponse>
    suspend fun getReview(attemptId: UUID): ApiResult<AttemptReviewResponse>
}

class OpenApiResultGateway(
    private val api: MyAttemptsResultsApi,
) : ResultGateway {
    override suspend fun getResult(attemptId: UUID): ApiResult<AttemptResultResponse> =
        safeApiCall { api.getResult(attemptId) }

    override suspend fun getReview(attemptId: UUID): ApiResult<AttemptReviewResponse> =
        safeApiCall { api.getReview(attemptId = attemptId, part = null) }
}

class ResultRepository(
    private val gateway: ResultGateway,
    private val catalogOptionsCache: CatalogOptionsCache,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class CachedResult(
        val response: AttemptResultResponse,
        val fetchedAtMillis: Long,
    )

    private val resultCache = ConcurrentHashMap<String, CachedResult>()

    suspend fun result(attemptId: String): ApiResult<AttemptResultResponse> {
        val uuid = ResultFormat.parseAttemptUuidOrNull(attemptId)
            ?: return ApiResult.HttpFailure(statusCode = 404, error = null)
        val outcome = gateway.getResult(uuid)
        if (outcome is ApiResult.Success) {
            val key = uuid.toString()
            resultCache[key] = CachedResult(
                response = outcome.data,
                fetchedAtMillis = clockMillis(),
            )
        }
        return outcome
    }

    suspend fun review(attemptId: String): ApiResult<AttemptReviewResponse> {
        val uuid = ResultFormat.parseAttemptUuidOrNull(attemptId)
            ?: return ApiResult.HttpFailure(statusCode = 404, error = null)
        return gateway.getReview(uuid)
    }

    fun cachedResultIfFresh(
        attemptId: String,
        maxAgeMillis: Long = REVIEW_RESULT_REUSE_WINDOW_MILLIS,
    ): AttemptResultResponse? {
        val key = ResultFormat.parseAttemptUuidOrNull(attemptId)?.toString() ?: return null
        val entry = resultCache[key] ?: return null
        val age = clockMillis() - entry.fetchedAtMillis
        return if (age in 0L..maxAgeMillis) entry.response else null
    }

    suspend fun loadExamKinds(): Map<String, CatalogOptionItem> =
        when (val outcome = catalogOptionsCache.get(CatalogOptionType.EXAM)) {
            is ApiResult.Success -> outcome.data.associateBy { it.code }
            else -> emptyMap()
        }

    suspend fun loadSubjects(): Map<String, CatalogOptionItem> =
        when (val outcome = catalogOptionsCache.get(CatalogOptionType.SUBJECT)) {
            is ApiResult.Success -> outcome.data.associateBy { it.code }
            else -> emptyMap()
        }

    fun clear() {
        resultCache.clear()
    }

    companion object {
        const val REVIEW_RESULT_REUSE_WINDOW_MILLIS: Long = 60_000L

        fun fromSessionStack(
            sessionStack: Thi360SessionStack,
            clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
        ): ResultRepository = ResultRepository(
            gateway = OpenApiResultGateway(
                sessionStack.authenticatedService(MyAttemptsResultsApi::class.java),
            ),
            catalogOptionsCache = sessionStack.catalogOptionsCache,
            clockMillis = clockMillis,
        )
    }
}

class ResultSessionMemory(
    private val sessionStack: Thi360SessionStack,
    clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val sessionManager = sessionStack.sessionManager
    val repository: ResultRepository = ResultRepository.fromSessionStack(
        sessionStack = sessionStack,
        clockMillis = clockMillis,
    )

    private var lastGeneration: Long = sessionManager.sessionGeneration.value
    private var lastAccountId: String? = sessionManager.currentAccountIdOrNull()

    fun syncAccountScope() {
        val currentGeneration = sessionManager.sessionGeneration.value
        val currentAccount = sessionManager.currentAccountIdOrNull()
        val isSignedOut = sessionManager.state.value is SessionState.SignedOut
        if (isSignedOut || currentGeneration != lastGeneration || currentAccount != lastAccountId) {
            lastGeneration = currentGeneration
            lastAccountId = currentAccount
            clear()
        }
    }

    fun clear() {
        repository.clear()
    }

    companion object {
        private val cache = WeakHashMap<SessionManager, ResultSessionMemory>()
        private val registeredCleanups = WeakHashMap<SessionCleanup, Boolean>()

        fun forSession(
            sessionStack: Thi360SessionStack,
            sessionCleanup: SessionCleanup? = null,
            clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
        ): ResultSessionMemory = synchronized(cache) {
            val manager = sessionStack.sessionManager
            val existing = cache[manager]
            if (existing != null) {
                existing.syncAccountScope()
                return@synchronized existing
            }
            val created = ResultSessionMemory(
                sessionStack = sessionStack,
                clockMillis = clockMillis,
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
