package vn.unicom.thi360.core.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreTokenCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : TokenCipher {

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        require(iv.size in MIN_IV_BYTES..MAX_IV_BYTES) { "Unexpected GCM IV length: ${iv.size}" }
        val encrypted = cipher.doFinal(plaintext)
        return ByteArray(HEADER_BYTES + iv.size + encrypted.size).apply {
            this[0] = FORMAT_VERSION
            this[1] = iv.size.toByte()
            iv.copyInto(this, destinationOffset = HEADER_BYTES)
            encrypted.copyInto(this, destinationOffset = HEADER_BYTES + iv.size)
        }
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size <= HEADER_BYTES + MIN_IV_BYTES) {
            throw GeneralSecurityException("Encrypted token payload is truncated")
        }
        if (ciphertext[0] != FORMAT_VERSION) {
            throw GeneralSecurityException("Unsupported encrypted token format version: ${ciphertext[0]}")
        }
        val ivLength = ciphertext[1].toInt() and 0xFF
        if (ivLength !in MIN_IV_BYTES..MAX_IV_BYTES || ciphertext.size <= HEADER_BYTES + ivLength) {
            throw GeneralSecurityException("Invalid GCM IV length in token payload: $ivLength")
        }
        val secretKey = loadExistingSecretKey()
            ?: throw GeneralSecurityException("Android Keystore key missing for alias: $keyAlias")
        val iv = ciphertext.copyOfRange(HEADER_BYTES, HEADER_BYTES + ivLength)
        val encrypted = ciphertext.copyOfRange(HEADER_BYTES + ivLength, ciphertext.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted)
    }

    private fun loadExistingSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(keyAlias, null) as? SecretKey
    }

    private fun getOrCreateSecretKey(): SecretKey {
        loadExistingSecretKey()?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DEFAULT_KEY_ALIAS = "vn.unicom.thi360.tokens.aes_gcm"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
        private const val HEADER_BYTES = 2
        private const val MIN_IV_BYTES = 12
        private const val MAX_IV_BYTES = 16
        private const val GCM_TAG_BITS = 128
        private const val KEY_SIZE_BITS = 256
    }
}
