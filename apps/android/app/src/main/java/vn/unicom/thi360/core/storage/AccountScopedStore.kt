package vn.unicom.thi360.core.storage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AccountScopedStore(
    private val rootDir: File,
    val accountId: String,
) {
    init {
        require(accountId.isNotBlank()) { "accountId must not be blank" }
    }

    private val lockKey: String = accountLockKey(rootDir, accountId)
    private val accountLock: ReentrantLock = lockFor(lockKey)
    private val wipeEpochAtCreation: Long = epochFor(lockKey).get()
    private val accountDir: File = accountDirectory(rootDir, accountId)

    fun read(namespace: String, key: String): ByteArray? {
        validateSegment(namespace, "namespace")
        validateSegment(key, "key")
        return accountLock.withLock {
            if (isInvalidated()) return@withLock null
            val target = entryFile(namespace, key)
            if (!target.isFile) return@withLock null
            runCatching { target.readBytes() }.getOrNull()
        }
    }

    fun readString(namespace: String, key: String): String? =
        read(namespace, key)?.toString(StandardCharsets.UTF_8)

    fun write(namespace: String, key: String, bytes: ByteArray): Boolean {
        validateSegment(namespace, "namespace")
        validateSegment(key, "key")
        return accountLock.withLock {
            if (isInvalidated()) return@withLock false
            val target = entryFile(namespace, key)
            val parent = target.parentFile ?: return@withLock false
            if (!parent.exists() && !parent.mkdirs()) {
                return@withLock false
            }
            val tempFile = File(parent, "${target.name}.${UUID.randomUUID()}.tmp")
            try {
                FileOutputStream(tempFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                    fos.fd.sync()
                }
                if (isInvalidated()) {
                    tempFile.delete()
                    return@withLock false
                }
                moveAtomically(tempFile, target)
                true
            } catch (error: Throwable) {
                tempFile.delete()
                throw error
            }
        }
    }

    fun writeString(namespace: String, key: String, value: String): Boolean =
        write(namespace, key, value.toByteArray(StandardCharsets.UTF_8))

    fun delete(namespace: String, key: String) {
        validateSegment(namespace, "namespace")
        validateSegment(key, "key")
        accountLock.withLock {
            val target = entryFile(namespace, key)
            if (target.exists()) {
                target.delete()
            }
        }
    }

    fun deleteNamespace(namespace: String) {
        validateSegment(namespace, "namespace")
        accountLock.withLock {
            val nsDir = namespaceDir(namespace)
            if (nsDir.exists()) {
                nsDir.deleteRecursively()
            }
        }
    }

    internal fun entryFileForTest(namespace: String, key: String): File = entryFile(namespace, key)

    private fun isInvalidated(): Boolean = epochFor(lockKey).get() != wipeEpochAtCreation

    private fun namespaceDir(namespace: String): File =
        File(accountDir, hashSegment(namespace))

    private fun entryFile(namespace: String, key: String): File =
        File(namespaceDir(namespace), "${hashSegment(key)}.dat")

    companion object {
        const val ROOT_DIR_NAME = "account_scoped"
        const val BACKUP_RELATIVE_PATH = "no_backup/$ROOT_DIR_NAME"

        private val accountLocks = ConcurrentHashMap<String, ReentrantLock>()
        private val accountWipeEpochs = ConcurrentHashMap<String, AtomicLong>()

        fun defaultRootDir(context: Context): File =
            File(context.applicationContext.noBackupFilesDir, ROOT_DIR_NAME)

        fun accountDirectory(rootDir: File, accountId: String): File =
            File(rootDir, hashSegment(accountId))

        fun wipeAccount(rootDir: File, accountId: String) {
            if (accountId.isBlank()) return
            val key = accountLockKey(rootDir, accountId)
            val lock = lockFor(key)
            lock.withLock {
                epochFor(key).incrementAndGet()
                val dir = accountDirectory(rootDir, accountId)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }
        }

        private fun accountLockKey(rootDir: File, accountId: String): String =
            "${rootDir.canonicalPath}::${hashSegment(accountId)}"

        private fun lockFor(key: String): ReentrantLock =
            accountLocks.computeIfAbsent(key) { ReentrantLock() }

        private fun epochFor(key: String): AtomicLong =
            accountWipeEpochs.computeIfAbsent(key) { AtomicLong(0L) }

        private fun validateSegment(value: String, label: String) {
            require(value.isNotBlank()) { "$label must not be blank" }
        }

        private fun hashSegment(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(StandardCharsets.UTF_8))
            return buildString(digest.size * 2) {
                for (b in digest) {
                    val v = b.toInt() and 0xFF
                    append(HEX_CHARS[v ushr 4])
                    append(HEX_CHARS[v and 0x0F])
                }
            }
        }

        private fun moveAtomically(source: File, target: File) {
            try {
                Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }

        private const val HEX_CHARS = "0123456789abcdef"
    }
}
