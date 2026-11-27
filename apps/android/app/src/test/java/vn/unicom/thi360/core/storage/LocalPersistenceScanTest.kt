package vn.unicom.thi360.core.storage

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPersistenceScanTest {

    private fun resolveMainKotlinDir(): File {
        val candidates = listOf(
            File("src/main/java/vn/unicom/thi360"),
            File("app/src/main/java/vn/unicom/thi360"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate src/main/java/vn/unicom/thi360")
    }

    /**
     * Allowlist of files permitted to write local files or preferences outside AccountScopedStore
     * (each entry has a concrete justification per A-30):
     * - core/storage/AccountScopedStore.kt: the account-scoped store implementation itself
     * - core/storage/AccountScopedStoreProvider.kt: locates noBackupFilesDir for AccountScopedStore
     * - core/storage/TokenStore.kt: Keystore-encrypted session tokens (Phase 1)
     * - core/biometric/BiometricVault.kt: Keystore-encrypted biometric refresh token (Phase 1)
     * - core/biometric/BiometricPromptMemory.kt: device-level biometric prompt preference DataStore (Phase 1)
     * - core/preferences/AppPreferences.kt: device-level theme & locale DataStore (Phase 1)
     * - core/appearance/AppearanceCache.kt: public appearance tokens cache (Phase 1)
     * - core/session/SessionCleanup.kt: writes/deletes pending cleanup marker during logout/startup
     * - feature/richcontent/assets/AttemptAssetSource.kt: ephemeral viewer temp files in cacheDir/attempt-assets/ purged on close/leave/logout/start (R-177, P2-D-44)
     */
    private val allowlist = setOf(
        "core/storage/AccountScopedStore.kt",
        "core/storage/AccountScopedStoreProvider.kt",
        "core/storage/KeystoreTokenStore.kt",
        "core/biometric/BiometricVault.kt",
        "core/biometric/BiometricPromptMemoryStore.kt",
        "core/preferences/AppPreferences.kt",
        "core/appearance/AppearanceCache.kt",
        "core/session/SessionCleanup.kt",
        "feature/richcontent/assets/AttemptAssetSource.kt",
    )

    private val persistencePatterns = listOf(
        "SharedPreferences" to Regex("""\bgetSharedPreferences\s*\(|\bSharedPreferences\b"""),
        "preferencesDataStore" to Regex("""\bpreferencesDataStore\s*\("""),
        "FileOutputStream" to Regex("""\bFileOutputStream\s*\("""),
        "openFileOutput" to Regex("""\bopenFileOutput\s*\("""),
        "File.writeText/writeBytes" to Regex("""\.(writeText|writeBytes|appendText|appendBytes)\s*\("""),
        "Files.write" to Regex("""\bFiles\.write(String|Bytes)?\s*\("""),
        "RoomDatabase" to Regex("""\bRoomDatabase\b|\bSQLiteDatabase\b"""),
    )

    @Test
    fun noPortalCodeWritesFilesOrPreferencesOutsideAccountScopedStoreAndAllowlist() {
        val root = resolveMainKotlinDir()
        val violations = mutableListOf<String>()

        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val rel = file.relativeTo(root).path.replace('\\', '/')
                if (rel in allowlist) return@forEach
                file.readLines(Charsets.UTF_8).forEachIndexed { idx, line ->
                    for ((label, regex) in persistencePatterns) {
                        if (regex.containsMatchIn(line)) {
                            violations += "$rel:${idx + 1} [$label]: ${line.trim()}"
                        }
                    }
                }
            }

        assertTrue(
            "Found disallowed local file/preference writes outside AccountScopedStore and allowlist:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }
}
