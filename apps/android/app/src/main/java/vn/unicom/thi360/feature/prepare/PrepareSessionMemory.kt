package vn.unicom.thi360.feature.prepare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.SessionManager

internal class PrepareRouteHolderViewModel : ViewModel() {
    private var activeKey: PrepareSessionMemory.EntryKey? = null
    private var activeViewModel: PrepareViewModel? = null
    private var activeScope: CoroutineScope? = null
    private var registeredMemory: PrepareSessionMemory? = null

    @Synchronized
    fun getOrCreate(
        memory: PrepareSessionMemory,
        key: PrepareSessionMemory.EntryKey,
        factory: (CoroutineScope) -> PrepareViewModel,
    ): PrepareViewModel {
        val existing = activeViewModel
        if (activeKey == key && existing != null) {
            return existing
        }
        clearCurrent()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val viewModel = factory(scope)
        activeKey = key
        activeViewModel = viewModel
        activeScope = scope
        registeredMemory = memory
        memory.track(this)
        return viewModel
    }

    @Synchronized
    fun clearCurrent() {
        val viewModel = activeViewModel
        val scope = activeScope
        val memory = registeredMemory
        activeKey = null
        activeViewModel = null
        activeScope = null
        registeredMemory = null
        memory?.untrack(this)
        viewModel?.clear()
        scope?.cancel()
    }

    override fun onCleared() {
        clearCurrent()
    }

    companion object {
        val FACTORY: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PrepareRouteHolderViewModel() as T
        }
    }
}

internal class PrepareSessionMemory private constructor() {
    data class EntryKey(
        val sessionGeneration: Long,
        val backStackEntryId: String,
        val entitlementId: String,
        val apiIdentity: Int,
    )

    private val holders = LinkedHashSet<PrepareRouteHolderViewModel>()

    @Synchronized
    fun track(holder: PrepareRouteHolderViewModel) {
        holders.add(holder)
    }

    @Synchronized
    fun untrack(holder: PrepareRouteHolderViewModel) {
        holders.remove(holder)
    }

    @Synchronized
    fun activeCount(): Int = holders.size

    fun clear() {
        val snapshot = synchronized(this) {
            val copy = holders.toList()
            holders.clear()
            copy
        }
        snapshot.forEach { holder ->
            holder.clearCurrent()
        }
    }

    companion object {
        private val cache = WeakHashMap<SessionManager, PrepareSessionMemory>()
        private val registeredCleanups = WeakHashMap<SessionCleanup, Boolean>()

        fun forSession(
            sessionManager: SessionManager,
            sessionCleanup: SessionCleanup? = null,
        ): PrepareSessionMemory = synchronized(cache) {
            val existing = cache[sessionManager]
            if (existing != null) return@synchronized existing
            val created = PrepareSessionMemory()
            cache[sessionManager] = created
            if (sessionCleanup != null && registeredCleanups[sessionCleanup] != true) {
                registeredCleanups[sessionCleanup] = true
                sessionCleanup.register {
                    val memories = synchronized(cache) { cache.values.toList() }
                    memories.forEach { it.clear() }
                }
            }
            created
        }

        internal fun activeEntryCountForTesting(): Int = synchronized(cache) {
            cache.values.sumOf { it.activeCount() }
        }

        internal fun clearAllForTesting() {
            val memories = synchronized(cache) {
                val copy = cache.values.toList()
                cache.clear()
                copy
            }
            memories.forEach { it.clear() }
        }
    }
}
