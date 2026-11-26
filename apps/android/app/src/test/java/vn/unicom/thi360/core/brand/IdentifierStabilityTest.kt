package vn.unicom.thi360.core.brand

import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element
import vn.unicom.thi360.BuildConfig
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.biometric.AndroidBiometricKeystoreEngine
import vn.unicom.thi360.core.biometric.KeystoreBiometricVault
import vn.unicom.thi360.core.storage.AndroidKeystoreTokenCipher
import vn.unicom.thi360.core.storage.KeystoreTokenStore

@RunWith(RobolectricTestRunner::class)
class IdentifierStabilityTest {

    private fun resolveResDir(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate src/main/res")
    }

    private fun parseKeys(file: File): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val keys = linkedSetOf<String>()
        for (i in 0 until nodes.length) {
            keys += (nodes.item(i) as Element).getAttribute("name")
        }
        return keys
    }

    @Test
    fun applicationIdTokenStoreKeystoreAliasesAndRenamedStringKeysMatchBaseSha() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()

        // Application ID must remain unchanged across both dev and demo flavors.
        assertEquals("vn.unicom.thi360", BuildConfig.APPLICATION_ID)
        assertEquals("vn.unicom.thi360", context.packageName)

        // Token & biometric store filenames and Android Keystore aliases must remain identical to Phase 1.
        assertEquals("thi360_tokens.enc", KeystoreTokenStore.TOKEN_FILE_NAME)
        assertEquals("no_backup/thi360_tokens.enc", KeystoreTokenStore.BACKUP_RELATIVE_PATH)
        assertEquals("vn.unicom.thi360.tokens.aes_gcm", AndroidKeystoreTokenCipher.DEFAULT_KEY_ALIAS)
        assertEquals("thi360_biometric_token.enc", KeystoreBiometricVault.BIOMETRIC_FILE_NAME)
        assertEquals("no_backup/thi360_biometric_token.enc", KeystoreBiometricVault.BACKUP_RELATIVE_PATH)
        assertEquals("vn.unicom.thi360.biometric.refresh_token.aes_gcm", AndroidBiometricKeystoreEngine.DEFAULT_KEY_ALIAS)

        // All renamed string keys from base_sha remain present in both values and values-en.
        val resDir = resolveResDir()
        val expectedRenamedKeysByFile = mapOf(
            "strings_shell.xml" to setOf("app_name"),
            "strings_account.xml" to setOf(
                "account_logout_confirm_message",
                "biometric_enable_body",
                "biometric_os_prompt_unlock_title",
            ),
            "strings_recovery.xml" to setOf("register_terms_label"),
        )

        for ((fileName, expectedKeys) in expectedRenamedKeysByFile) {
            val viKeys = parseKeys(File(resDir, "values/$fileName"))
            val enKeys = parseKeys(File(resDir, "values-en/$fileName"))
            for (key in expectedKeys) {
                assertTrue("Renamed key '$key' missing from values/$fileName", key in viKeys)
                assertTrue("Renamed key '$key' missing from values-en/$fileName", key in enKeys)
            }
        }
    }
}
