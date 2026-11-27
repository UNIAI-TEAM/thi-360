package vn.unicom.thi360.core.legal

import android.content.Context
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import vn.unicom.thi360.core.preferences.AppLocale

fun interface LegalAssetSource {
    fun open(assetPath: String): InputStream
}

class LegalRepository(
    private val assetSource: LegalAssetSource,
) {
    fun load(id: LegalId, locale: AppLocale): Result<LegalDocument> {
        val assetPath = "$ASSET_DIR/${id.assetFileName(locale.languageTag)}"
        val rawJson = try {
            assetSource.open(assetPath).use { stream ->
                stream.readBytes().toString(StandardCharsets.UTF_8)
            }
        } catch (notFound: FileNotFoundException) {
            return Result.failure(LegalLoadError.Missing(assetPath, notFound))
        } catch (io: IOException) {
            return Result.failure(LegalLoadError.Missing(assetPath, io))
        }

        return try {
            val document = legalJson.decodeFromString(LegalDocument.serializer(), rawJson)
            if (document.title.isBlank() || document.version.isBlank()) {
                Result.failure(LegalLoadError.Malformed(assetPath))
            } else {
                Result.success(document)
            }
        } catch (error: SerializationException) {
            Result.failure(LegalLoadError.Malformed(assetPath, error))
        } catch (error: IllegalArgumentException) {
            Result.failure(LegalLoadError.Malformed(assetPath, error))
        }
    }

    fun termsVersion(): Result<String> =
        load(LegalId.TERMS, AppLocale.VI).map { it.version }

    fun termsVersionOrFallback(): String =
        termsVersion().getOrNull()?.takeIf { it.isNotBlank() } ?: FALLBACK_TERMS_VERSION

    companion object {
        const val ASSET_DIR = "legal"
        private const val FALLBACK_TERMS_VERSION = "unavailable"

        internal val legalJson = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
        }

        fun create(context: Context): LegalRepository {
            val assets = context.applicationContext.assets
            return LegalRepository { path -> assets.open(path) }
        }
    }
}
