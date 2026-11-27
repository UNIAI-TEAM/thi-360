package vn.unicom.thi360.feature.attemptengine

import java.util.UUID
import java.util.WeakHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.session.SessionCleaner
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider

object AttemptStorageKeys {
    const val NAMESPACE = "attempt"

    fun draftKey(attemptId: UUID, itemId: UUID): String = "draft:$attemptId:$itemId"
    fun draftIndexKey(attemptId: UUID): String = "draftIndex:$attemptId"
    fun positionKey(attemptId: UUID): String = "position:$attemptId"
    fun sessionKey(attemptId: UUID): String = "session:$attemptId"
    fun startContextKey(attemptId: UUID): String = "startContext:$attemptId"
}

@Serializable
data class DraftRecord(
    val seq: Long,
    val answer: AnswerValue? = null,
    val flagged: Boolean = false,
    val dirtyFlag: Boolean? = null,
) {
    override fun toString(): String =
        "DraftRecord(seq=$seq, answer=[REDACTED], flagged=$flagged, dirtyFlag=$dirtyFlag)"
}

@Serializable
internal data class StoredPosition(
    val partId: String,
    val itemId: String? = null,
)

class DraftStore(
    private val store: AccountScopedStore,
) {
    private val json = Serializer.kotlinxSerializationJson
    private val lock = Any()

    val accountId: String
        get() = store.accountId

    fun readSessionId(attemptId: UUID): UUID? = synchronized(lock) {
        val raw = store.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.sessionKey(attemptId))
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        runCatching { UUID.fromString(raw) }.getOrNull()
    }

    fun writeSessionId(attemptId: UUID, sessionId: UUID): Boolean = synchronized(lock) {
        store.writeString(
            AttemptStorageKeys.NAMESPACE,
            AttemptStorageKeys.sessionKey(attemptId),
            sessionId.toString(),
        )
    }

    fun deleteSessionId(attemptId: UUID) = synchronized(lock) {
        store.delete(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.sessionKey(attemptId))
    }

    fun readPosition(attemptId: UUID): EnginePosition? = synchronized(lock) {
        val raw = store.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.positionKey(attemptId))
            ?: return null
        val parsed = runCatching {
            json.decodeFromString(StoredPosition.serializer(), raw)
        }.getOrNull() ?: return null
        val partId = runCatching { UUID.fromString(parsed.partId) }.getOrNull() ?: return null
        val itemId = parsed.itemId?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        EnginePosition(partId = partId, itemId = itemId)
    }

    fun writePosition(attemptId: UUID, position: EnginePosition): Boolean = synchronized(lock) {
        val encoded = json.encodeToString(
            StoredPosition.serializer(),
            StoredPosition(partId = position.partId.toString(), itemId = position.itemId?.toString()),
        )
        store.writeString(
            AttemptStorageKeys.NAMESPACE,
            AttemptStorageKeys.positionKey(attemptId),
            encoded,
        )
    }

    fun deletePosition(attemptId: UUID) = synchronized(lock) {
        store.delete(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.positionKey(attemptId))
    }

    fun readDraft(attemptId: UUID, itemId: UUID): DraftRecord? = synchronized(lock) {
        val raw = store.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.draftKey(attemptId, itemId))
            ?: return null
        runCatching { json.decodeFromString(DraftRecord.serializer(), raw) }.getOrNull()
    }

    fun readDrafts(attemptId: UUID, itemIds: Collection<UUID>): Map<UUID, DraftRecord> = synchronized(lock) {
        val candidateIds = LinkedHashSet<UUID>(itemIds.size)
        candidateIds.addAll(itemIds)
        candidateIds.addAll(readDraftIndexLocked(attemptId))
        val result = LinkedHashMap<UUID, DraftRecord>()
        for (itemId in candidateIds) {
            val raw = store.readString(
                AttemptStorageKeys.NAMESPACE,
                AttemptStorageKeys.draftKey(attemptId, itemId),
            ) ?: continue
            val record = runCatching {
                json.decodeFromString(DraftRecord.serializer(), raw)
            }.getOrNull() ?: continue
            result[itemId] = record
        }
        result
    }

    fun writeDraft(attemptId: UUID, itemId: UUID, record: DraftRecord): Boolean = synchronized(lock) {
        val encoded = json.encodeToString(DraftRecord.serializer(), record)
        val written = store.writeString(
            AttemptStorageKeys.NAMESPACE,
            AttemptStorageKeys.draftKey(attemptId, itemId),
            encoded,
        )
        if (written) {
            val index = readDraftIndexLocked(attemptId).toMutableSet()
            if (index.add(itemId)) {
                writeDraftIndexLocked(attemptId, index)
            }
        }
        written
    }

    fun deleteDraft(attemptId: UUID, itemId: UUID) = synchronized(lock) {
        store.delete(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.draftKey(attemptId, itemId))
        val index = readDraftIndexLocked(attemptId).toMutableSet()
        if (index.remove(itemId)) {
            if (index.isEmpty()) {
                store.delete(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.draftIndexKey(attemptId))
            } else {
                writeDraftIndexLocked(attemptId, index)
            }
        }
    }

    fun clearAttemptData(
        attemptId: UUID,
        knownItemIds: Collection<UUID> = emptyList(),
        keepStartContext: Boolean = false,
    ) = synchronized(lock) {
        val allDraftItemIds = LinkedHashSet<UUID>()
        allDraftItemIds.addAll(knownItemIds)
        allDraftItemIds.addAll(readDraftIndexLocked(attemptId))
        for (itemId in allDraftItemIds) {
            store.delete(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.draftKey(attemptId, itemId))
        }
        store.delete(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.draftIndexKey(attemptId))
        store.delete(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.positionKey(attemptId))
        store.delete(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.sessionKey(attemptId))
        if (!keepStartContext) {
            store.delete(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.startContextKey(attemptId))
        }
    }

    private fun readDraftIndexLocked(attemptId: UUID): Set<UUID> {
        val raw = store.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.draftIndexKey(attemptId))
            ?: return emptySet()
        val ids = runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), raw)
        }.getOrNull() ?: return emptySet()
        return ids.mapNotNullTo(LinkedHashSet()) { rawId ->
            runCatching { UUID.fromString(rawId) }.getOrNull()
        }
    }

    private fun writeDraftIndexLocked(attemptId: UUID, ids: Set<UUID>) {
        val encoded = json.encodeToString(
            ListSerializer(String.serializer()),
            ids.map { it.toString() },
        )
        store.writeString(
            AttemptStorageKeys.NAMESPACE,
            AttemptStorageKeys.draftIndexKey(attemptId),
            encoded,
        )
    }
}

class AttemptDataCleanup(
    private val storeProvider: AccountScopedStoreProvider,
) : SessionCleaner {
    override suspend fun clean(accountId: String) {
        if (accountId.isBlank()) return
        storeProvider.storeFor(accountId).deleteNamespace(AttemptStorageKeys.NAMESPACE)
    }

    fun registerInto(sessionCleanup: SessionCleanup): AutoCloseable =
        sessionCleanup.register(this)

    companion object {
        private val registeredCleanups = WeakHashMap<SessionCleanup, AutoCloseable>()

        fun ensureRegistered(
            sessionCleanup: SessionCleanup,
            storeProvider: AccountScopedStoreProvider,
        ): AutoCloseable = synchronized(registeredCleanups) {
            registeredCleanups.getOrPut(sessionCleanup) {
                AttemptDataCleanup(storeProvider).registerInto(sessionCleanup)
            }
        }
    }
}
