package vn.unicom.thi360.core.biometric

import android.content.Context
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricKeyInvalidationInstrumentedTest {

    private lateinit var context: Context
    private lateinit var storageFile: File
    private val testKeyAlias = "vn.unicom.thi360.biometric.refresh_token.instrumented_test"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        storageFile = File(context.noBackupFilesDir, "thi360_biometric_instrumented_test.enc")
        storageFile.delete()
        deleteKeyAlias(testKeyAlias)
    }

    @After
    fun tearDown() {
        storageFile.delete()
        deleteKeyAlias(testKeyAlias)
    }

    @Test
    fun keystoreSpecRequiresStrongBiometricAuthAndInvalidationOnEnrollmentChange() {
        val engine = AndroidBiometricKeystoreEngine(keyAlias = testKeyAlias)
        val spec = engine.buildKeyGenParameterSpec()

        assertTrue("Key must require user authentication", spec.isUserAuthenticationRequired)
        assertTrue(
            "Key must be invalidated when biometric enrollment changes",
            spec.isInvalidatedByBiometricEnrollment,
        )
        assertEquals(256, spec.keySize)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            assertEquals(0, spec.userAuthenticationValidityDurationSeconds)
            assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG, spec.userAuthenticationType)
        } else {
            @Suppress("DEPRECATION")
            assertEquals(-1, spec.userAuthenticationValidityDurationSeconds)
        }

        val canAuth = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
            val cipher = engine.prepareEncryptCipher()
            assertTrue(cipher.algorithm.startsWith("AES"))
            val keyStore = KeyStore.getInstance(AndroidBiometricKeystoreEngine.ANDROID_KEYSTORE).apply { load(null) }
            assertTrue(keyStore.containsAlias(testKeyAlias))
            val key = keyStore.getKey(testKeyAlias, null) as SecretKey
            val keyInfo = SecretKeyFactory.getInstance(key.algorithm, AndroidBiometricKeystoreEngine.ANDROID_KEYSTORE)
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            assertTrue(keyInfo.isUserAuthenticationRequired)
            assertTrue(keyInfo.isInvalidatedByBiometricEnrollment)
        }
    }

    @Test
    fun keyInvalidationDeletesWrappedFileInNoBackupDirAndTransitionsControllerToOff() = runBlocking {
        // Seed a wrapped payload file on real noBackupFilesDir backed by an engine that throws KeyPermanentlyInvalidatedException.
        storageFile.writeBytes(byteArrayOf(1, 12) + ByteArray(28) { it.toByte() })
        assertTrue(storageFile.exists())
        assertEquals(context.noBackupFilesDir.canonicalPath, storageFile.parentFile?.canonicalPath)

        val invalidatedEngine = object : BiometricCryptoEngine {
            override fun prepareEncryptCipher(): Cipher? = null
            override fun encryptWithCipher(cipher: Cipher?, plaintext: ByteArray): ByteArray = plaintext
            override fun prepareDecryptCipher(ciphertext: ByteArray): Cipher? {
                throw KeyPermanentlyInvalidatedException("Biometric enrollment changed on device")
            }
            override fun decryptWithCipher(cipher: Cipher?, ciphertext: ByteArray): ByteArray = ciphertext
            override fun deleteKey() {
                deleteKeyAlias(testKeyAlias)
            }
        }
        val vault = KeystoreBiometricVault(storageFile = storageFile, engine = invalidatedEngine)
        val controller = BiometricController(
            availabilityChecker = object : BiometricAvailabilityChecker {
                override fun check(): BiometricAvailability = BiometricAvailability.Available
            },
            vault = vault,
            promptMemoryStore = object : BiometricPromptMemoryStore {
                private val state = MutableStateFlow(PromptMemory(PromptAnswer.YES))
                override val memory: Flow<PromptMemory> = state
                override suspend fun current(): PromptMemory = state.value
                override suspend fun record(answer: PromptAnswer) {
                    state.value = PromptMemory(answer)
                }
            },
        )
        assertEquals(BiometricState.On, controller.state.value)

        val authenticator = object : BiometricAuthenticator {
            override suspend fun authenticate(mode: BiometricPromptMode, cipher: Cipher?): BiometricPromptResult =
                BiometricPromptResult.Success(cipher)
        }
        val outcome = controller.unlock(authenticator)

        assertEquals(BiometricUnlockOutcome.KeyInvalidated, outcome)
        assertEquals(BiometricState.Off, controller.state.value)
        assertFalse("Invalidated biometric token file must be deleted", storageFile.exists())
        assertFalse(vault.hasWrappedToken())
    }

    private fun deleteKeyAlias(alias: String) {
        val keyStore = KeyStore.getInstance(AndroidBiometricKeystoreEngine.ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
