package vn.unicom.thi360.feature.attemptengine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.UUID
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider

class AttemptEngineViewModel(
    attemptId: UUID,
    accountId: String,
    api: AttemptApi,
    store: AccountScopedStore,
    clock: EngineClock = SystemEngineClock,
    connectivity: ConnectivityMonitor,
    sessionCleanup: SessionCleanup? = null,
    storeProvider: AccountScopedStoreProvider? = null,
    uuidGenerator: () -> UUID = UUID::randomUUID,
) : ViewModel() {

    init {
        if (sessionCleanup != null && storeProvider != null) {
            AttemptDataCleanup.ensureRegistered(sessionCleanup, storeProvider)
        }
    }

    val engine: AttemptEngine = AttemptEngine(
        attemptId = attemptId,
        accountId = accountId,
        api = api,
        store = store,
        clock = clock,
        connectivity = connectivity,
        scope = viewModelScope,
        uuidGenerator = uuidGenerator,
    )

    override fun onCleared() {
        engine.close()
        super.onCleared()
    }

    class Factory(
        private val attemptId: UUID,
        private val accountId: String,
        private val api: AttemptApi,
        private val store: AccountScopedStore,
        private val clock: EngineClock = SystemEngineClock,
        private val connectivity: ConnectivityMonitor,
        private val sessionCleanup: SessionCleanup? = null,
        private val storeProvider: AccountScopedStoreProvider? = null,
        private val uuidGenerator: () -> UUID = UUID::randomUUID,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AttemptEngineViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            return AttemptEngineViewModel(
                attemptId = attemptId,
                accountId = accountId,
                api = api,
                store = store,
                clock = clock,
                connectivity = connectivity,
                sessionCleanup = sessionCleanup,
                storeProvider = storeProvider,
                uuidGenerator = uuidGenerator,
            ) as T
        }
    }
}
