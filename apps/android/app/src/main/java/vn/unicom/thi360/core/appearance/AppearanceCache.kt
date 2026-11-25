package vn.unicom.thi360.core.appearance

import android.content.Context
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import vn.unicom.thi360.core.theme.BrandPalette

data class CachedAppearance(
    val version: Int,
    val etag: String,
    val rawJson: String,
    val palette: BrandPalette,
)

interface AppearanceCache {
    fun read(): CachedAppearance?
    fun write(snapshot: CachedAppearance)
}

@Serializable
private data class StoredAppearanceEnvelope(
    val etag: String,
    val rawJson: String,
)

class FileAppearanceCache(
    private val cacheFile: File,
) : AppearanceCache {

    private val lock = Any()

    override fun read(): CachedAppearance? = synchronized(lock) {
        if (!cacheFile.isFile) return null
        val text = runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val envelope = runCatching {
            json.decodeFromString(StoredAppearanceEnvelope.serializer(), text)
        }.getOrNull() ?: return null
        val parsed = AppearanceTokensParser.parse(envelope.rawJson) ?: return null
        val normalizedEtag = envelope.etag.trim().ifEmpty { "\"${parsed.version}\"" }
        CachedAppearance(
            version = parsed.version,
            etag = normalizedEtag,
            rawJson = envelope.rawJson,
            palette = parsed.palette,
        )
    }

    override fun write(snapshot: CachedAppearance) = synchronized(lock) {
        runCatching {
            val parent = cacheFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val encoded = json.encodeToString(
                StoredAppearanceEnvelope.serializer(),
                StoredAppearanceEnvelope(
                    etag = snapshot.etag,
                    rawJson = snapshot.rawJson,
                ),
            )
            val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            tempFile.writeText(encoded, Charsets.UTF_8)
            if (!tempFile.renameTo(cacheFile)) {
                cacheFile.writeText(encoded, Charsets.UTF_8)
                tempFile.delete()
            }
        }
        Unit
    }

    companion object {
        private const val CACHE_DIR_NAME = "appearance"
        private const val CACHE_FILE_NAME = "appearance_tokens_cache.json"

        private val json = Json {
            ignoreUnknownKeys = true
        }

        fun create(context: Context): FileAppearanceCache =
            FileAppearanceCache(File(File(context.applicationContext.filesDir, CACHE_DIR_NAME), CACHE_FILE_NAME))
    }
}
