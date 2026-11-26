package vn.unicom.thi360.core.catalog

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vn.unicom.thi360.core.network.ApiResult

enum class CatalogOptionType(val queryValue: String) {
    EXAM("EXAM"),
    SUBJECT("SUBJECT"),
}

data class CatalogOptionItem(
    val code: String,
    val nameVi: String,
    val nameEn: String,
)

class CatalogOptionsCache(
    private val fetcher: suspend (CatalogOptionType) -> ApiResult<List<CatalogOptionItem>>,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class CacheEntry(
        val items: List<CatalogOptionItem>,
        val fetchedAtMillis: Long,
    )

    private val mutexes = CatalogOptionType.entries.associateWith { Mutex() }
    private val entries = mutableMapOf<CatalogOptionType, CacheEntry>()

    suspend fun get(
        type: CatalogOptionType,
        forceRefresh: Boolean = false,
    ): ApiResult<List<CatalogOptionItem>> {
        val mutex = mutexes.getValue(type)
        return mutex.withLock {
            val now = clockMillis()
            val cached = entries[type]
            if (!forceRefresh && cached != null && (now - cached.fetchedAtMillis) < ttlMillis) {
                return@withLock ApiResult.Success(cached.items)
            }
            when (val result = fetcher(type)) {
                is ApiResult.Success -> {
                    entries[type] = CacheEntry(
                        items = result.data,
                        fetchedAtMillis = clockMillis(),
                    )
                    result
                }
                is ApiResult.HttpFailure -> result
                is ApiResult.NetworkFailure -> result
            }
        }
    }

    suspend fun clear() {
        for (type in CatalogOptionType.entries) {
            mutexes.getValue(type).withLock {
                entries.remove(type)
            }
        }
    }

    companion object {
        const val DEFAULT_TTL_MILLIS: Long = 5L * 60L * 1_000L
    }
}
