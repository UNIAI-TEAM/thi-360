package vn.unicom.thi360.feature.prepare

import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.SessionManager

internal class PrepareSessionMemory private constructor() {
    data class EntryKey(
        val sessionGeneration: Long,
        val backStackEntryId: String,
        val entitlementId: String,
        val apiIdentity: Int,
    )

    private data class HeldEntry(
        val viewModel: PrepareViewModel,
        val scope: CoroutineScope,
    )

    private val entries = LinkedHashMap<EntryKey, HeldEntry>()

    @Synchronized
    fun getOrCreate(
        key: EntryKey,
        factory: (CoroutineScope) -> PrepareViewModel,
    ): PrepareViewModel {
        entries[key]?.let { return it.viewModel }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val vm = factory(scope)
        entries[key] = HeldEntry(vm, scope)
        return vm
    }

    @Synchronized
    fun remove(key: EntryKey) {
        val removed = entries.remove(key) ?: return
        removed.viewModel.clear()
        removed.scope.cancel()
    }

    @Synchronized
    fun clear() {
        val snapshot = entries.values.toList()
        entries.clear()
        snapshot.forEach { entry ->
            entry.viewModel.clear()
            entry.scope.cancel()
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
