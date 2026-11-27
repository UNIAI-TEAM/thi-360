package vn.unicom.thi360.feature.richcontent.assets

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.feature.richcontent.model.AttemptAssetRef

class AttemptAssetBlockedException(message: String = "Blocked external or invalid attempt asset reference") :
    IOException(message)

class AttemptAssetLoadException(
    val statusCode: Int?,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class TempAssetFile(
    val file: File,
    private val onClose: (File) -> Unit = {},
) : Closeable {
    override fun close() {
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
        onClose(file)
    }
}

class AttemptAssetSource(
    val attemptId: String,
    baseUrl: String,
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val maxMemoryBytes: Long = DEFAULT_MAX_MEMORY_BYTES,
    sessionCleanup: SessionCleanup? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val cacheLock = Any()
    private val memoryCache = LinkedHashMap<AttemptAssetRef, ByteArray>(16, 0.75f, true)
    private var memoryBytesUsed: Long = 0L

    private val inFlight = ConcurrentHashMap<AttemptAssetRef, CompletableDeferred<Result<ByteArray>>>()
    private val activeTempFiles = ConcurrentHashMap.newKeySet<File>()
    private val cleanupRegistrations = mutableListOf<AutoCloseable>()

    init {
        if (sessionCleanup != null) {
            cleanupRegistrations += sessionCleanup.register { purgeAll() }
            cleanupRegistrations += sessionCleanup.registerAppStartCleaner { purgeAll() }
        }
    }

    fun resolveUrl(ref: AttemptAssetRef): String = when (ref) {
        is AttemptAssetRef.Attempt ->
            "$normalizedBaseUrl/api/v1/me/attempts/${encodeSegment(attemptId)}/assets/${encodeSegment(ref.assetId)}"
        is AttemptAssetRef.Solution ->
            "$normalizedBaseUrl/api/v1/me/attempts/${encodeSegment(attemptId)}/solution-assets/${encodeSegment(ref.assetId)}"
        AttemptAssetRef.Blocked -> throw AttemptAssetBlockedException()
    }

    suspend fun load(ref: AttemptAssetRef): ByteArray {
        if (ref === AttemptAssetRef.Blocked) {
            throw AttemptAssetBlockedException()
        }
        getCached(ref)?.let { return it }

        val newDeferred = CompletableDeferred<Result<ByteArray>>()
        val existing = inFlight.putIfAbsent(ref, newDeferred)
        if (existing != null) {
            return existing.await().getOrThrow()
        }

        return try {
            val bytes = fetchBytes(ref)
            putCached(ref, bytes)
            newDeferred.complete(Result.success(bytes))
            bytes
        } catch (cancelled: CancellationException) {
            newDeferred.cancel(cancelled)
            throw cancelled
        } catch (t: Throwable) {
            newDeferred.complete(Result.failure(t))
            throw t
        } finally {
            inFlight.remove(ref, newDeferred)
        }
    }

    suspend fun openForViewer(ref: AttemptAssetRef, extension: String? = null): TempAssetFile {
        val bytes = load(ref)
        return withContext(ioDispatcher) {
            val dir = tempAssetDirectory(cacheDir)
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Cannot create attempt asset temp directory: ${dir.absolutePath}")
            }
            val safeExt = extension
                ?.trim()
                ?.removePrefix(".")
                ?.takeIf { it.isNotEmpty() && SAFE_EXTENSION_REGEX.matches(it) }
            val fileName = if (safeExt != null) "${UUID.randomUUID()}.$safeExt" else UUID.randomUUID().toString()
            val tempFile = File(dir, fileName)
            FileOutputStream(tempFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            activeTempFiles.add(tempFile)
            TempAssetFile(tempFile) { closedFile ->
                activeTempFiles.remove(closedFile)
            }
        }
    }

    fun purgeAll() {
        synchronized(cacheLock) {
            memoryCache.clear()
            memoryBytesUsed = 0L
        }
        for ((_, deferred) in inFlight) {
            deferred.cancel()
        }
        inFlight.clear()
        for (file in activeTempFiles.toList()) {
            runCatching { file.delete() }
            activeTempFiles.remove(file)
        }
        purgeTempDirectory(cacheDir)
    }

    override fun close() {
        purgeAll()
        for (reg in cleanupRegistrations) {
            runCatching { reg.close() }
        }
        cleanupRegistrations.clear()
    }

    internal fun memoryCacheEntryCount(): Int = synchronized(cacheLock) { memoryCache.size }

    private fun getCached(ref: AttemptAssetRef): ByteArray? = synchronized(cacheLock) {
        memoryCache[ref]
    }

    private fun putCached(ref: AttemptAssetRef, bytes: ByteArray) {
        val size = bytes.size.toLong()
        if (size > maxMemoryBytes) return
        synchronized(cacheLock) {
            memoryCache.remove(ref)?.let { old ->
                memoryBytesUsed -= old.size.toLong()
            }
            memoryCache[ref] = bytes
            memoryBytesUsed += size
            val iterator = memoryCache.entries.iterator()
            while (memoryBytesUsed > maxMemoryBytes && iterator.hasNext()) {
                val eldest = iterator.next()
                memoryBytesUsed -= eldest.value.size.toLong()
                iterator.remove()
            }
        }
    }

    private suspend fun fetchBytes(ref: AttemptAssetRef): ByteArray = withContext(ioDispatcher) {
        val url = resolveUrl(ref)
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        val call = client.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw AttemptAssetLoadException(
                        statusCode = response.code,
                        message = "Failed to load attempt asset (HTTP ${response.code})",
                    )
                }
                response.body.bytes()
            }
        } catch (cancelled: CancellationException) {
            call.cancel()
            throw cancelled
        } catch (e: AttemptAssetLoadException) {
            throw e
        } catch (io: IOException) {
            throw AttemptAssetLoadException(statusCode = null, message = io.message ?: "Network error", cause = io)
        }
    }

    companion object {
        const val TEMP_DIR_NAME = "attempt-assets"
        const val DEFAULT_MAX_MEMORY_BYTES: Long = 16L * 1024L * 1024L

        private val SAFE_EXTENSION_REGEX = Regex("^[a-zA-Z0-9]{1,10}$")
        private val ASSET_ID_VALID_REGEX = Regex("^[0-9a-zA-Z_-]+$")
        private val UUID_REGEX = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE,
        )
        private val SOLUTION_ASSET_PATH_REGEX = Regex(
            "^/api/v1/me/attempts/[^/\\s]+/solution-assets/([0-9a-zA-Z_-]+)(?:[?#].*)?$",
        )
        private val ATTEMPT_ASSET_PATH_REGEX = Regex(
            "^/api/v1/me/attempts/[^/\\s]+/assets/([0-9a-zA-Z_-]+)(?:[?#].*)?$",
        )
        private val ADMIN_ASSET_URL_REGEX = Regex(
            "^(?:https?://[^/\"'\\s<>]+)?/api/v1/admin/question-assets/([0-9a-fA-F-]+)(?:[?#].*)?$",
            RegexOption.IGNORE_CASE,
        )

        fun tempAssetDirectory(cacheDir: File): File = File(cacheDir, TEMP_DIR_NAME)

        fun purgeTempDirectory(cacheDir: File) {
            val dir = tempAssetDirectory(cacheDir)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }

        fun resolveAssetRef(src: String?, dataAssetId: String?): AttemptAssetRef {
            val trimmedSrc = src?.trim().orEmpty()
            val trimmedId = dataAssetId?.trim().orEmpty()

            if (trimmedSrc.isNotEmpty()) {
                SOLUTION_ASSET_PATH_REGEX.matchEntire(trimmedSrc)?.let { match ->
                    val id = match.groupValues[1]
                    if (ASSET_ID_VALID_REGEX.matches(id)) return AttemptAssetRef.Solution(id)
                }
                ATTEMPT_ASSET_PATH_REGEX.matchEntire(trimmedSrc)?.let { match ->
                    val id = match.groupValues[1]
                    if (ASSET_ID_VALID_REGEX.matches(id)) return AttemptAssetRef.Attempt(id)
                }
                ADMIN_ASSET_URL_REGEX.matchEntire(trimmedSrc)?.let { match ->
                    val id = match.groupValues[1]
                    if (ASSET_ID_VALID_REGEX.matches(id)) return AttemptAssetRef.Attempt(id)
                }
            }

            if (trimmedId.isNotEmpty() && (UUID_REGEX.matches(trimmedId) || ASSET_ID_VALID_REGEX.matches(trimmedId))) {
                if (trimmedSrc.isEmpty() || isSafeInternalSrc(trimmedSrc)) {
                    return AttemptAssetRef.Attempt(trimmedId)
                }
            }

            return AttemptAssetRef.Blocked
        }

        fun isSafeInternalSrc(value: String): Boolean {
            val trimmed = value.trim()
            return trimmed.startsWith("/") && !trimmed.startsWith("//")
        }

        fun create(
            context: Context,
            attemptId: String,
            sessionStack: Thi360SessionStack,
            baseUrl: String = AppEnvironment.apiBaseUrl,
            sessionCleanup: SessionCleanup? = (context.applicationContext as? Thi360Application)?.sessionCleanup,
        ): AttemptAssetSource = AttemptAssetSource(
            attemptId = attemptId,
            baseUrl = baseUrl,
            client = sessionStack.authenticatedOkHttpClient,
            cacheDir = context.applicationContext.cacheDir,
            sessionCleanup = sessionCleanup,
        )

        private fun encodeSegment(segment: String): String =
            URLEncoder.encode(segment, StandardCharsets.UTF_8.name()).replace("+", "%20")
    }
}
