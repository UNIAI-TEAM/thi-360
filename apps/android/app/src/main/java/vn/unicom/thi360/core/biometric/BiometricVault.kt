package vn.unicom.thi360.core.biometric

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricKeyInvalidatedException(
    message: String,
    cause: Throwable? = null,
) : GeneralSecurityException(message, cause)

interface BiometricCryptoEngine {
    fun prepareEncryptCipher(): Cipher?
    fun encryptWithCipher(cipher: Cipher?, plaintext: ByteArray): ByteArray
    fun prepareDecryptCipher(ciphertext: ByteArray): Cipher?
    fun decryptWithCipher(cipher: Cipher?, ciphertext: ByteArray): ByteArray
    fun deleteKey()
}

class AndroidBiometricKeystoreEngine(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : BiometricCryptoEngine {

    override fun prepareEncryptCipher(): Cipher {
        deleteKey()
        val secretKey = generateBiometricSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher
    }

    override fun encryptWithCipher(cipher: Cipher?, plaintext: ByteArray): ByteArray {
        val activeCipher = checkNotNull(cipher) { "Biometric encrypt cipher is required" }
        val iv = activeCipher.iv
        require(iv.size in MIN_IV_BYTES..MAX_IV_BYTES) { "Unexpected GCM IV length: ${iv.size}" }
        val encrypted = try {
            activeCipher.doFinal(plaintext)
        } catch (cause: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidatedException("Biometric key permanently invalidated", cause)
        }
        return ByteArray(HEADER_BYTES + iv.size + encrypted.size).apply {
            this[0] = FORMAT_VERSION
            this[1] = iv.size.toByte()
            iv.copyInto(this, destinationOffset = HEADER_BYTES)
            encrypted.copyInto(this, destinationOffset = HEADER_BYTES + iv.size)
        }
    }

    override fun prepareDecryptCipher(ciphertext: ByteArray): Cipher {
        val iv = extractIv(ciphertext)
        val secretKey = loadExistingSecretKey()
            ?: throw BiometricKeyInvalidatedException("Biometric Keystore key missing for alias: $keyAlias")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        try {
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        } catch (cause: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidatedException("Biometric enrollment changed; key invalidated", cause)
        } catch (cause: InvalidKeyException) {
            throw BiometricKeyInvalidatedException("Biometric key is no longer valid", cause)
        } catch (cause: UnrecoverableKeyException) {
            throw BiometricKeyInvalidatedException("Biometric key cannot be recovered", cause)
        }
        return cipher
    }

    override fun decryptWithCipher(cipher: Cipher?, ciphertext: ByteArray): ByteArray {
        val activeCipher = checkNotNull(cipher) { "Biometric decrypt cipher is required" }
        val ivLength = validateHeaderAndGetIvLength(ciphertext)
        val encrypted = ciphertext.copyOfRange(HEADER_BYTES + ivLength, ciphertext.size)
        return try {
            activeCipher.doFinal(encrypted)
        } catch (cause: KeyPermanentlyInvalidatedException) {
            throw BiometricKeyInvalidatedException("Biometric enrollment changed; key invalidated", cause)
        }
    }

    override fun deleteKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        }
    }

    internal fun buildKeyGenParameterSpec(): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        return builder.build()
    }

    private fun generateBiometricSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGenerator.init(buildKeyGenParameterSpec())
        return keyGenerator.generateKey()
    }

    private fun loadExistingSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return try {
            keyStore.getKey(keyAlias, null) as? SecretKey
        } catch (cause: UnrecoverableKeyException) {
            throw BiometricKeyInvalidatedException("Biometric key cannot be recovered", cause)
        }
    }

    private fun validateHeaderAndGetIvLength(ciphertext: ByteArray): Int {
        if (ciphertext.size <= HEADER_BYTES + MIN_IV_BYTES) {
            throw GeneralSecurityException("Encrypted biometric payload is truncated")
        }
        if (ciphertext[0] != FORMAT_VERSION) {
            throw GeneralSecurityException("Unsupported biometric payload version: ${ciphertext[0]}")
        }
        val ivLength = ciphertext[1].toInt() and 0xFF
        if (ivLength !in MIN_IV_BYTES..MAX_IV_BYTES || ciphertext.size <= HEADER_BYTES + ivLength) {
            throw GeneralSecurityException("Invalid GCM IV length in biometric payload: $ivLength")
        }
        return ivLength
    }

    private fun extractIv(ciphertext: ByteArray): ByteArray {
        val ivLength = validateHeaderAndGetIvLength(ciphertext)
        return ciphertext.copyOfRange(HEADER_BYTES, HEADER_BYTES + ivLength)
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "vn.unicom.thi360.biometric.refresh_token.aes_gcm"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
        private const val HEADER_BYTES = 2
        private const val MIN_IV_BYTES = 12
        private const val MAX_IV_BYTES = 16
        private const val GCM_TAG_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}

interface BiometricVault {
    fun hasWrappedToken(): Boolean
    fun prepareEncryptCipher(): Cipher?
    fun commitEncryptedToken(cipher: Cipher?, refreshToken: String)
    fun prepareDecryptCipher(): Cipher?
    fun decryptStoredToken(cipher: Cipher?): String
    fun clear()
}

class KeystoreBiometricVault(
    private val storageFile: File,
    private val engine: BiometricCryptoEngine = AndroidBiometricKeystoreEngine(),
) : BiometricVault {

    private val lock = Any()

    override fun hasWrappedToken(): Boolean = synchronized(lock) {
        storageFile.isFile && storageFile.length() > 0L
    }

    override fun prepareEncryptCipher(): Cipher? = synchronized(lock) {
        engine.prepareEncryptCipher()
    }

    override fun commitEncryptedToken(cipher: Cipher?, refreshToken: String) = synchronized(lock) {
        val parent = storageFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val plaintext = refreshToken.toByteArray(StandardCharsets.UTF_8)
        val encrypted = engine.encryptWithCipher(cipher, plaintext)
        val tempFile = tempStorageFile()
        tempFile.writeBytes(encrypted)
        if (!tempFile.renameTo(storageFile)) {
            storageFile.writeBytes(encrypted)
            tempFile.delete()
        }
    }

    override fun prepareDecryptCipher(): Cipher? = synchronized(lock) {
        if (!storageFile.isFile) {
            throw BiometricKeyInvalidatedException("Wrapped biometric token file missing")
        }
        val ciphertext = try {
            storageFile.readBytes()
        } catch (cause: IOException) {
            throw BiometricKeyInvalidatedException("Wrapped biometric token file unreadable", cause)
        }
        engine.prepareDecryptCipher(ciphertext)
    }

    override fun decryptStoredToken(cipher: Cipher?): String = synchronized(lock) {
        if (!storageFile.isFile) {
            throw BiometricKeyInvalidatedException("Wrapped biometric token file missing")
        }
        val ciphertext = try {
            storageFile.readBytes()
        } catch (cause: IOException) {
            throw BiometricKeyInvalidatedException("Wrapped biometric token file unreadable", cause)
        }
        val decrypted = engine.decryptWithCipher(cipher, ciphertext).toString(StandardCharsets.UTF_8)
        if (decrypted.isBlank()) {
            throw BiometricKeyInvalidatedException("Decrypted biometric token is blank")
        }
        decrypted
    }

    override fun clear() = synchronized(lock) {
        if (storageFile.exists()) {
            storageFile.delete()
        }
        val tempFile = tempStorageFile()
        if (tempFile.exists()) {
            tempFile.delete()
        }
        engine.deleteKey()
    }

    private fun tempStorageFile(): File = File(storageFile.parentFile, "${storageFile.name}.tmp")

    companion object {
        const val BIOMETRIC_FILE_NAME = "thi360_biometric_token.enc"
        const val BACKUP_RELATIVE_PATH = "no_backup/$BIOMETRIC_FILE_NAME"

        fun storageFile(context: Context): File =
            File(context.applicationContext.noBackupFilesDir, BIOMETRIC_FILE_NAME)

        fun create(context: Context): KeystoreBiometricVault =
            KeystoreBiometricVault(storageFile = storageFile(context))
    }
}
