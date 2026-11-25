package vn.unicom.thi360.core.storage

import android.content.Context
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class KeystoreTokenStore(
    private val storageFile: File,
    private val cipher: TokenCipher = AndroidKeystoreTokenCipher(),
    private val json: Json = defaultJson,
) : TokenStore {

    private val lock = Any()

    override fun read(): Tokens? = synchronized(lock) {
        // Return before accessing AndroidKeyStore so cold starts with no session never initialize crypto keys.
        if (!storageFile.exists()) return null
        return try {
            val encrypted = storageFile.readBytes()
            val plaintext = cipher.decrypt(encrypted).toString(StandardCharsets.UTF_8)
            json.decodeFromString(Tokens.serializer(), plaintext)
        } catch (_: GeneralSecurityException) {
            clearUnlocked()
            null
        } catch (_: SerializationException) {
            clearUnlocked()
            null
        } catch (_: IllegalArgumentException) {
            clearUnlocked()
            null
        } catch (_: IOException) {
            clearUnlocked()
            null
        }
    }

    override fun write(tokens: Tokens) = synchronized(lock) {
        val parent = storageFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val plaintext = json.encodeToString(Tokens.serializer(), tokens).toByteArray(StandardCharsets.UTF_8)
        val encrypted = cipher.encrypt(plaintext)
        val tempFile = tempStorageFile()
        tempFile.writeBytes(encrypted)
        if (!tempFile.renameTo(storageFile)) {
            storageFile.writeBytes(encrypted)
            tempFile.delete()
        }
    }

    override fun clear() = synchronized(lock) {
        clearUnlocked()
    }

    private fun clearUnlocked() {
        if (storageFile.exists()) {
            storageFile.delete()
        }
        val tempFile = tempStorageFile()
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    private fun tempStorageFile(): File = File(storageFile.parentFile, "${storageFile.name}.tmp")

    companion object {
        const val TOKEN_FILE_NAME = "thi360_tokens.enc"
        const val BACKUP_RELATIVE_PATH = "no_backup/$TOKEN_FILE_NAME"

        private val defaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun storageFile(context: Context): File =
            File(context.applicationContext.noBackupFilesDir, TOKEN_FILE_NAME)

        fun create(context: Context): KeystoreTokenStore =
            KeystoreTokenStore(storageFile = storageFile(context))
    }
}
