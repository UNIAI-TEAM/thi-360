package vn.unicom.thi360.core.session

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import vn.unicom.thi360.core.storage.AccountScopedStore

fun interface SessionCleaner {
    suspend fun clean(accountId: String)
}

class SessionCleanup(
    private val accountStoreRootDir: File,
) {
    private val cleaners = CopyOnWriteArrayList<SessionCleaner>()
    private val appStartCleaners = CopyOnWriteArrayList<suspend () -> Unit>()

    private val pendingDir: File
        get() = File(accountStoreRootDir, PENDING_DIR_NAME)

    fun register(cleaner: SessionCleaner): AutoCloseable {
        cleaners.add(cleaner)
        return AutoCloseable { cleaners.remove(cleaner) }
    }

    fun registerAppStartCleaner(cleaner: suspend () -> Unit): AutoCloseable {
        appStartCleaners.add(cleaner)
        return AutoCloseable { appStartCleaners.remove(cleaner) }
    }

    suspend fun onLogout(accountId: String) {
        val marker = if (accountId.isNotBlank()) writePendingMarker(accountId) else null
        runCleanersAndWipe(accountId)
        marker?.delete()
    }

    suspend fun onAppStart() {
        val markers = pendingDir.listFiles { file -> file.isFile && file.name.endsWith(MARKER_SUFFIX) }
            ?.sortedBy { it.name }
            .orEmpty()
        for (marker in markers) {
            val accountId = runCatching {
                marker.readText(StandardCharsets.UTF_8).trim()
            }.getOrDefault("")
            if (accountId.isNotBlank()) {
                runCleanersAndWipe(accountId)
            }
            marker.delete()
        }

        for (cleaner in appStartCleaners) {
            try {
                cleaner()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Keep app startup resilient even if one registered cleaner fails.
            }
        }
    }

    internal fun pendingMarkers(): List<File> =
        pendingDir.listFiles { file -> file.isFile && file.name.endsWith(MARKER_SUFFIX) }
            ?.sortedBy { it.name }
            .orEmpty()

    private suspend fun runCleanersAndWipe(accountId: String) {
        for (cleaner in cleaners) {
            try {
                cleaner.clean(accountId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Isolate cleaner errors so remaining cleaners and account wipe still run.
            }
        }
        if (accountId.isNotBlank()) {
            runCatching {
                AccountScopedStore.wipeAccount(accountStoreRootDir, accountId)
            }
        }
    }

    private fun writePendingMarker(accountId: String): File {
        if (!pendingDir.exists()) {
            pendingDir.mkdirs()
        }
        val markerFile = File(pendingDir, "${hashAccountId(accountId)}$MARKER_SUFFIX")
        FileOutputStream(markerFile).use { fos ->
            fos.write(accountId.toByteArray(StandardCharsets.UTF_8))
            fos.flush()
            fos.fd.sync()
        }
        return markerFile
    }

    companion object {
        private const val PENDING_DIR_NAME = ".pending_cleanup"
        private const val MARKER_SUFFIX = ".pending"
        private const val HEX_CHARS = "0123456789abcdef"

        fun create(context: Context): SessionCleanup =
            SessionCleanup(AccountScopedStore.defaultRootDir(context)).also { cleanup ->
                val attemptAssetsDir = File(context.cacheDir, ATTEMPT_ASSETS_DIR_NAME)
                cleanup.register {
                    attemptAssetsDir.deleteRecursively()
                }
                cleanup.registerAppStartCleaner {
                    attemptAssetsDir.deleteRecursively()
                }
            }

        private const val ATTEMPT_ASSETS_DIR_NAME = "attempt-assets"

        private fun hashAccountId(accountId: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(accountId.toByteArray(StandardCharsets.UTF_8))
            return buildString(digest.size * 2) {
                for (b in digest) {
                    val v = b.toInt() and 0xFF
                    append(HEX_CHARS[v ushr 4])
                    append(HEX_CHARS[v and 0x0F])
                }
            }
        }
    }
}
