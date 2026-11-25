package vn.unicom.thi360.core.storage

interface TokenCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray
}
