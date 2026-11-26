package vn.unicom.thi360.core.biometric

import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BiometricEnableTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun keystoreBiometricVaultEncryptsRefreshTokenAtRestAndWipesOnClear() {
        val storageFile = File(tempFolder.newFolder("bio_vault"), KeystoreBiometricVault.BIOMETRIC_FILE_NAME)
        val secretKey: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val random = SecureRandom()
        var deleteKeyCalls = 0

        val engine = object : BiometricCryptoEngine {
            override fun prepareEncryptCipher(): Cipher {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                return cipher
            }

            override fun encryptWithCipher(cipher: Cipher?, plaintext: ByteArray): ByteArray {
                val active = checkNotNull(cipher)
                val iv = active.iv ?: ByteArray(12).also { random.nextBytes(it) }
                return byteArrayOf(1, iv.size.toByte()) + iv + active.doFinal(plaintext)
            }

            override fun prepareDecryptCipher(ciphertext: ByteArray): Cipher {
                val ivLen = ciphertext[1].toInt() and 0xFF
                val iv = ciphertext.copyOfRange(2, 2 + ivLen)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                return cipher
            }

            override fun decryptWithCipher(cipher: Cipher?, ciphertext: ByteArray): ByteArray {
                val ivLen = ciphertext[1].toInt() and 0xFF
                val body = ciphertext.copyOfRange(2 + ivLen, ciphertext.size)
                return checkNotNull(cipher).doFinal(body)
            }

            override fun deleteKey() {
                deleteKeyCalls++
            }
        }

        val vault = KeystoreBiometricVault(storageFile = storageFile, engine = engine)
        val refreshToken = "super-secret-refresh-token-xyz"
        val encCipher = vault.prepareEncryptCipher()
        vault.commitEncryptedToken(encCipher, refreshToken)

        assertTrue(vault.hasWrappedToken())
        val rawText = storageFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("Wrapped biometric file must not store plaintext refresh token", rawText.contains(refreshToken))

        val decCipher = vault.prepareDecryptCipher()
        assertEquals(refreshToken, vault.decryptStoredToken(decCipher))

        vault.clear()
        assertFalse(vault.hasWrappedToken())
        assertFalse(storageFile.exists())
        assertEquals(1, deleteKeyCalls)
    }

    @Test
    fun enableSucceedsOnlyAfterOsConfirmationAndDisableStopsLockingUntilReconfirmed() = runTest {
        val vault = FakeBiometricVault()
        val memory = InMemoryBiometricPromptMemoryStore()
        val authenticator = FakeBiometricAuthenticator(
            nextResult = BiometricPromptResult.Failure(BiometricAuthFailureReason.CANCELLED),
        )
        val controller = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = vault,
            promptMemoryStore = memory,
        )

        val cancelledOutcome = controller.enable("refresh-1", authenticator)
        assertEquals(BiometricEnableOutcome.Failed(BiometricAuthFailureReason.CANCELLED), cancelledOutcome)
        assertEquals(BiometricState.Off, controller.state.value)
        assertNull(vault.storedToken)

        authenticator.nextResult = BiometricPromptResult.Failure(BiometricAuthFailureReason.FAILED)
        val failedOutcome = controller.enable("refresh-1", authenticator)
        assertEquals(BiometricEnableOutcome.Failed(BiometricAuthFailureReason.FAILED), failedOutcome)
        assertEquals(BiometricState.Off, controller.state.value)
        assertNull(vault.storedToken)

        authenticator.nextResult = BiometricPromptResult.Success(null)
        val enabledOutcome = controller.enable("refresh-1", authenticator)
        assertEquals(BiometricEnableOutcome.Enabled, enabledOutcome)
        assertEquals(BiometricState.On, controller.state.value)
        assertEquals("refresh-1", vault.storedToken)
        assertEquals(PromptAnswer.YES, memory.current().lastAnswer)
        assertTrue(controller.shouldStartLocked(hasStoredSession = true))

        controller.disable()
        assertEquals(BiometricState.Off, controller.state.value)
        assertNull(vault.storedToken)
        assertFalse(controller.shouldStartLocked(hasStoredSession = true))

        authenticator.nextResult = BiometricPromptResult.Failure(BiometricAuthFailureReason.CANCELLED)
        val reenableCancelled = controller.enable("refresh-1", authenticator)
        assertEquals(BiometricEnableOutcome.Failed(BiometricAuthFailureReason.CANCELLED), reenableCancelled)
        assertEquals(BiometricState.Off, controller.state.value)
        assertEquals(
            listOf(
                BiometricPromptMode.ENABLE,
                BiometricPromptMode.ENABLE,
                BiometricPromptMode.ENABLE,
                BiometricPromptMode.ENABLE,
            ),
            authenticator.calls,
        )
    }
}
