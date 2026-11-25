package vn.unicom.thi360.core.storage

import android.content.pm.ApplicationInfo
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import vn.unicom.thi360.R
import vn.unicom.thi360.Thi360Application

@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun parseBackupExclusions(xmlResId: Int): Map<String, Set<Pair<String, String>>> {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val parser: XmlResourceParser = context.resources.getXml(xmlResId)
        val sections = linkedMapOf<String, MutableSet<Pair<String, String>>>()
        var currentSection = "root"

        parser.use { xml ->
            var event = xml.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (xml.name) {
                        "full-backup-content", "cloud-backup", "device-transfer" -> {
                            currentSection = xml.name
                            sections.getOrPut(currentSection) { linkedSetOf() }
                        }

                        "exclude" -> {
                            val domain = xml.getAttributeValue(null, "domain").orEmpty()
                            val path = xml.getAttributeValue(null, "path").orEmpty()
                            sections.getOrPut(currentSection) { linkedSetOf() }
                                .add(domain to path)
                        }
                    }
                }
                event = xml.next()
            }
        }
        return sections
    }

    @Test
    fun manifestAndBackupRulesExcludeEncryptedTokenStoreFromCloudBackupAndDeviceTransfer() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals(
            "android:allowBackup must remain false",
            0,
            appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP,
        )

        val storageFile = KeystoreTokenStore.storageFile(context)
        assertEquals(
            "TokenStore file must reside inside noBackupFilesDir",
            context.noBackupFilesDir.canonicalPath,
            storageFile.parentFile?.canonicalPath,
        )
        assertEquals(KeystoreTokenStore.TOKEN_FILE_NAME, storageFile.name)

        val legacyRules = parseBackupExclusions(R.xml.backup_rules)["full-backup-content"].orEmpty()
        assertTrue(
            "backup_rules.xml must explicitly exclude ${KeystoreTokenStore.BACKUP_RELATIVE_PATH}",
            ("root" to KeystoreTokenStore.BACKUP_RELATIVE_PATH) in legacyRules,
        )
        assertTrue(
            "backup_rules.xml must explicitly exclude ${vn.unicom.thi360.core.biometric.KeystoreBiometricVault.BACKUP_RELATIVE_PATH}",
            ("root" to vn.unicom.thi360.core.biometric.KeystoreBiometricVault.BACKUP_RELATIVE_PATH) in legacyRules,
        )
        assertTrue(("root" to ".") in legacyRules)
        assertTrue(("sharedpref" to ".") in legacyRules)

        val extractionRules = parseBackupExclusions(R.xml.data_extraction_rules)
        val cloudBackup = extractionRules["cloud-backup"].orEmpty()
        val deviceTransfer = extractionRules["device-transfer"].orEmpty()
        assertTrue(
            "data_extraction_rules.xml cloud-backup must explicitly exclude ${KeystoreTokenStore.BACKUP_RELATIVE_PATH}",
            ("root" to KeystoreTokenStore.BACKUP_RELATIVE_PATH) in cloudBackup,
        )
        assertTrue(
            "data_extraction_rules.xml cloud-backup must explicitly exclude ${vn.unicom.thi360.core.biometric.KeystoreBiometricVault.BACKUP_RELATIVE_PATH}",
            ("root" to vn.unicom.thi360.core.biometric.KeystoreBiometricVault.BACKUP_RELATIVE_PATH) in cloudBackup,
        )
        assertTrue(
            "data_extraction_rules.xml device-transfer must explicitly exclude ${KeystoreTokenStore.BACKUP_RELATIVE_PATH}",
            ("root" to KeystoreTokenStore.BACKUP_RELATIVE_PATH) in deviceTransfer,
        )
        assertTrue(
            "data_extraction_rules.xml device-transfer must explicitly exclude ${vn.unicom.thi360.core.biometric.KeystoreBiometricVault.BACKUP_RELATIVE_PATH}",
            ("root" to vn.unicom.thi360.core.biometric.KeystoreBiometricVault.BACKUP_RELATIVE_PATH) in deviceTransfer,
        )
    }

    @Test
    fun keystoreTokenStoreEncryptsPayloadAtRestAndClearsOnMissingKey() {
        val storageFile = File(tempFolder.newFolder("no_backup"), KeystoreTokenStore.TOKEN_FILE_NAME)
        val activeKey = generateAes256Key()
        val rotatedKey = generateAes256Key()
        var currentKey: SecretKey = activeKey

        val jvmAesGcmCipher = object : TokenCipher {
            private val random = SecureRandom()
            override fun encrypt(plaintext: ByteArray): ByteArray {
                val iv = ByteArray(12).also { random.nextBytes(it) }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, currentKey, GCMParameterSpec(128, iv))
                return iv + cipher.doFinal(plaintext)
            }

            override fun decrypt(ciphertext: ByteArray): ByteArray {
                if (ciphertext.size <= 12) throw GeneralSecurityException("Truncated")
                val iv = ciphertext.copyOfRange(0, 12)
                val body = ciphertext.copyOfRange(12, ciphertext.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, currentKey, GCMParameterSpec(128, iv))
                return cipher.doFinal(body)
            }
        }

        val store = KeystoreTokenStore(storageFile = storageFile, cipher = jvmAesGcmCipher)
        assertNull(store.read())

        val tokens = Tokens(
            accessToken = "super-secret-access-token",
            refreshToken = "super-secret-refresh-token",
            accessExpiresIn = 300,
            refreshExpiresIn = 604800,
            mustChangePassword = false,
            username = "student.demo",
        )
        store.write(tokens)

        val rawDiskText = storageFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse("Ciphertext file must not contain accessToken plaintext", rawDiskText.contains(tokens.accessToken))
        assertFalse("Ciphertext file must not contain refreshToken plaintext", rawDiskText.contains(tokens.refreshToken))
        assertEquals(tokens, store.read())

        // Simulate reinstall / wiped Keystore key: decryption fails, unreadable file deleted, read returns null.
        currentKey = rotatedKey
        assertNull(store.read())
        assertFalse("Corrupt/unreadable token file must be purged after key loss", storageFile.exists())
    }

    private fun generateAes256Key(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
}
