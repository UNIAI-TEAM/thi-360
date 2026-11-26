package vn.unicom.thi360.core.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenStoreInstrumentedTest {

    private lateinit var context: Context
    private lateinit var storageFile: File
    private lateinit var store: KeystoreTokenStore

    private val testKeyAlias = "vn.unicom.thi360.tokens.aes_gcm.test"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        storageFile = File(context.noBackupFilesDir, "thi360_tokens_instrumented.enc")
        deleteKeyAlias(testKeyAlias)
        storageFile.delete()
        store = KeystoreTokenStore(
            storageFile = storageFile,
            cipher = AndroidKeystoreTokenCipher(keyAlias = testKeyAlias),
        )
    }

    @After
    fun tearDown() {
        store.clear()
        deleteKeyAlias(testKeyAlias)
    }

    @Test
    fun tokensAreEncryptedInAndroidKeystoreAndNeverWrittenInPlaintextOrSharedPrefs() {
        val sample = Tokens(
            accessToken = "secret-access-token-abc-987654",
            refreshToken = "secret-refresh-token-xyz-123456",
            accessExpiresIn = 300,
            refreshExpiresIn = 604800,
            mustChangePassword = false,
            username = "student.demo",
        )

        store.write(sample)

        assertTrue("Encrypted token file must exist inside noBackupFilesDir", storageFile.exists())
        assertEquals(context.noBackupFilesDir.canonicalPath, storageFile.parentFile?.canonicalPath)

        val keyStore = KeyStore.getInstance(AndroidKeystoreTokenCipher.ANDROID_KEYSTORE).apply { load(null) }
        assertTrue("AES key must exist in AndroidKeyStore", keyStore.containsAlias(testKeyAlias))

        val rawBytes = storageFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("Encrypted storage must not contain plaintext access token", rawBytes.contains(sample.accessToken))
        assertFalse("Encrypted storage must not contain plaintext refresh token", rawBytes.contains(sample.refreshToken))

        val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefsFiles = sharedPrefsDir.listFiles().orEmpty()
        for (file in prefsFiles) {
            val content = file.readText()
            assertFalse("SharedPreferences ${file.name} leaked access token", content.contains(sample.accessToken))
            assertFalse("SharedPreferences ${file.name} leaked refresh token", content.contains(sample.refreshToken))
        }

        val dataStoreDir = File(context.filesDir, "datastore")
        for (file in dataStoreDir.listFiles().orEmpty()) {
            val content = file.readBytes().toString(Charsets.ISO_8859_1)
            assertFalse("DataStore ${file.name} leaked access token", content.contains(sample.accessToken))
            assertFalse("DataStore ${file.name} leaked refresh token", content.contains(sample.refreshToken))
        }

        val restored = store.read()
        assertNotNull(restored)
        assertEquals(sample, restored)
    }

    @Test
    fun reinstallOrMissingKeystoreKeyLeavesNoReadableSession() {
        val sample = Tokens(
            accessToken = "reinstall-access-token",
            refreshToken = "reinstall-refresh-token",
            accessExpiresIn = 300,
            refreshExpiresIn = 604800,
            mustChangePassword = false,
            username = "student.demo",
        )

        store.write(sample)
        assertEquals(sample, store.read())

        // Simulate reinstall: OS deletes the app's AndroidKeyStore key even if stale file bytes remained.
        deleteKeyAlias(testKeyAlias)

        assertNull("Session must be unreadable after Keystore key removal/reinstall", store.read())
        assertFalse("Unreadable ciphertext file must be cleaned up", storageFile.exists())
    }

    @Test
    fun clearRemovesEncryptedTokenFileAndSession() {
        store.write(
            Tokens(
                accessToken = "clear-access",
                refreshToken = "clear-refresh",
                accessExpiresIn = 300,
                refreshExpiresIn = 604800,
                mustChangePassword = false,
                username = "student.demo",
            ),
        )
        assertNotNull(store.read())

        store.clear()

        assertNull(store.read())
        assertFalse(storageFile.exists())
    }

    private fun deleteKeyAlias(alias: String) {
        val keyStore = KeyStore.getInstance(AndroidKeystoreTokenCipher.ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }
}
