package vn.unicom.thi360.feature.prepare

import java.util.UUID
import kotlinx.serialization.Serializable
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.PreChoiceInput
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider

@Serializable
data class StartContext(
    val entitlementId: String,
    val preChoice: PreChoiceInput? = null,
    val examTitle: String,
    val examKindLabel: String,
    val firstPartName: String? = null,
    val firstPartMinutes: Int? = null,
    val timing: String? = null,
)

class StartContextStore(
    private val storeProvider: () -> AccountScopedStore?,
) {
    constructor(store: AccountScopedStore) : this({ store })

    constructor(provider: AccountScopedStoreProvider) : this({ provider.current() })

    private val json = Serializer.kotlinxSerializationJson

    fun save(attemptId: String, context: StartContext): Boolean {
        val normalizedId = attemptId.trim()
        if (normalizedId.isEmpty()) return false
        val store = storeProvider() ?: return false
        val encoded = json.encodeToString(StartContext.serializer(), context)
        return store.writeString(NAMESPACE, keyFor(normalizedId), encoded)
    }

    fun save(attemptId: UUID, context: StartContext): Boolean =
        save(attemptId.toString(), context)

    fun read(attemptId: String): StartContext? {
        val normalizedId = attemptId.trim()
        if (normalizedId.isEmpty()) return null
        val store = storeProvider() ?: return null
        val raw = store.readString(NAMESPACE, keyFor(normalizedId)) ?: return null
        return runCatching { json.decodeFromString(StartContext.serializer(), raw) }.getOrNull()
    }

    fun read(attemptId: UUID): StartContext? = read(attemptId.toString())

    fun moveTo(
        oldAttemptId: String,
        newAttemptId: String,
        updatedContext: StartContext? = null,
    ): Boolean {
        val oldId = oldAttemptId.trim()
        val newId = newAttemptId.trim()
        if (newId.isEmpty()) return false
        val context = updatedContext ?: read(oldId) ?: return false
        val saved = save(newId, context)
        if (saved && oldId.isNotEmpty() && oldId != newId) {
            delete(oldId)
        }
        return saved
    }

    fun moveTo(
        oldAttemptId: UUID,
        newAttemptId: UUID,
        updatedContext: StartContext? = null,
    ): Boolean = moveTo(oldAttemptId.toString(), newAttemptId.toString(), updatedContext)

    fun delete(attemptId: String) {
        val normalizedId = attemptId.trim()
        if (normalizedId.isEmpty()) return
        storeProvider()?.delete(NAMESPACE, keyFor(normalizedId))
    }

    fun delete(attemptId: UUID) = delete(attemptId.toString())

    companion object {
        const val NAMESPACE = "attempt"

        fun keyFor(attemptId: String): String = "startContext:$attemptId"
        fun keyFor(attemptId: UUID): String = keyFor(attemptId.toString())
    }
}
